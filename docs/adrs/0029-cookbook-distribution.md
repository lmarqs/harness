# 0029. Cookbooks release by git tag, with jars as optional packaging

## Objective

**Problem.** A cookbook author shares models and recipes with other repositories. When a release requires a build
plugin, a package repository and hosting for it, sharing one recipe costs more than copying it by hand, and the copies
drift. A repository that cooks a release needs to know that the content it cooks is the content it reviewed.

**Goal.** An author releases a cookbook with the tools that already hold its source, and a repository cooking a
release can tell when the content under that release changes.

## Context

- A [cookbook](../features/glossary.md) is a repository, or a directory in one, with `cookbook.toml` at its root
  naming its `group` and release `version`; each topic directory is one artifact holding models, recipes, resources and
  a README ([cookbook](../../cookbook/README.md)).
- The engine compiles an artifact's sources when one of its recipes is cooked. The jars that the cookbook Gradle
  plugin builds hold those sources, resources and README, not compiled classes
  ([`CookbookPlugin`](../../plugin/src/main/groovy/thecook/gradle/CookbookPlugin.groovy)), so a checkout of the
  cookbook at its release carries the same content as its jars.
- Today the engine reads a jar from a Maven-layout repository listed in the kitchen's `cook.toml`, or from Maven
  Central, and records the sha256 of the jar's bytes in `cook.lock`
  ([`CookbookRepositories`](../../core/src/main/groovy/thecook/cookbook/CookbookRepositories.groovy)).
- A git tag names one commit. It can be moved: `git tag -f` repoints it, and a force push publishes the move. A
  commit hash identifies the content of the whole repository, not of one directory in it.
- Prior art: Go modules map a version to a git tag, and a module in a subdirectory prefixes its tags with that
  directory, so the module in `gopls` tags version `v0.4.0` as `gopls/v0.4.0`.
- Worked example: acme keeps its cookbook in `acme/setup` under `cookbook/`, beside a service. Its teams use git
  hosting with their own credentials and run no package repository.

## Decision

1. A cookbook release is a git tag on the repository that holds the cookbook: `v<version>` when `cookbook.toml` is at
   the repository root, and `<dir>/v<version>` when the cookbook is in the directory `<dir>`.
2. A repository entry in the kitchen's `cook.toml` is a Maven-layout repository, an `https://` URL or a path, or a
   git repository, written `{ git = "<url>", dir = "<dir>" }` with `dir` optional. Entries are tried in order.
3. For a git entry, the engine reads `cookbook.toml` at the release tag. An entry whose `group` differs from the
   coordinate's group is skipped. The artifact is the cookbook's directory named like it.
4. A jar in a Maven-layout repository holds the same sources as the tag. Publishing jars is optional packaging, for
   organizations that run a package repository.
5. `cook.lock` records the sha256 of the artifact's sources: each path and its content, in path order. One release
   has one hash whichever kind of repository supplied it. A different hash under a version the lock records is shown
   as a changed source before the diff.
6. Coordinates name exact releases. Branches, commits, snapshots and version ranges are refused.
7. The engine fetches a tag with the machine's `git`, which uses the credentials git already has, and caches the
   fetched sources by commit for offline reuse.
8. Without a `cook.toml`, the engine resolves from Maven Central.

## Reasons

- **1.** A tag is the release step an author already takes with the source, so a release needs no build and no
  package hosting. The subdirectory prefix follows the Go convention in Context, which keeps a cookbook beside other
  code, as in the acme example, without its tags colliding with the other code's releases. The trade is a tag naming
  rule the author must follow.
- **2, 3.** Listing repositories in the kitchen keeps the sources a repository trusts in one reviewed file. Matching
  on the `group` in `cookbook.toml` lets a coordinate stay the same whichever entry holds it. The trade is that a git
  entry is read before the engine knows whether it holds the group.
- **4.** The jars carry the sources already, so a package repository serves organizations that have one, at no cost
  to those that do not. The trade is two forms to keep equivalent, which decision 5 makes checkable.
- **5.** A jar's bytes and a commit hash both identify more or less than the artifact: two builds of one jar can
  differ in bytes, and a commit covers the whole repository. A hash of the artifact's sources identifies exactly what
  the engine compiles. Because a tag can move, comparing that hash with the lock is how a repository notices that a
  release's content changed. The trade is a hash that no other tool computes.
- **6.** A branch or a range selects different content over time under the same coordinates, so the lock could not
  say which content produced the ownership it records ([0025](0025-recipes.md)).
- **7.** Private cookbooks then need no credential handling in the engine. The trade is that the machine needs `git`,
  and the first use of a release needs the network.
- **8.** A kitchen without a `cook.toml` keeps working with published cookbooks.

## Rejected alternatives

### Maven repository only

At its best, one resolution path, with a mature format, checksums and existing hosting. It loses because every author
then needs a build plugin, a package repository and its credentials to share one recipe, which costs more than the
copy-by-hand it replaces.

### A branch or commit as the version

At its best, an author shares work without a release step. It loses because a branch moves under the same name, and a
raw commit says nothing about compatibility; decision 6 keeps coordinates to releases the author chose.

### A bundled git client

At its best, the engine fetches without depending on the machine. It loses because a bundled client does not use the
credential helpers people already configure for their git hosting, so private cookbooks would need credentials
handled by the engine.

## Consequences

Costs:

- The engine depends on `git` on the machine for git entries.
- The lock's source hash differs from the jar sha256 that package tools report.

Must be maintained:

- The source hash definition, identical for jars and tags.
- The tag naming rule in the cookbook documentation.

## Confirmation

- Review a release of the same sources as a tag and as a jar: does `cook.lock` record the same hash for both?
- Review a moved tag: does the next run show a changed source before the diff?
- Review a cookbook in a subdirectory: does it release as `<dir>/v<version>`, beside unrelated tags in the repository?
