# 0014. Native output and recipe ownership are the only files Cook writes

## Objective

**Problem.** Setup changes touch repositories that already contain program configuration, shared agent content and
local state. Misplacing those files prevents programs from finding their settings, and a tool that leaves its own
files behind adds state every repository must ignore, clean up or explain.

**Goal.** Programs find the configuration intended for them, reviewers can inspect shared changes, and every file
Cook writes in a repository is one that repository commits, without relocating unrelated files.

## Context

- [Ingredients](../features/glossary.md) model native program files and configuration. Recipes declare destination
  targets; Cook reviews and performs their file operations
  ([architecture](../architecture.md#ingredients-recipes-and-cookbooks)).
- In a worked example, `acme/api` keeps shared skills under `.agents/skills/` with a directory symlink from a native
  skills location. `acme/web` instead keeps a real skills directory containing hand-written files. Both layouts
  predate a recipe invocation.
- A repository commits its configuration, and its version control keeps every earlier version of it. Its current
  working directory is the destination, regardless of the recipe or executable location.
- Sources have origins distinct from destination targets. A recipe served over HTTPS can refer to a template beside
  itself at its URL while proposing output inside the destination.
- Cook's wrapper obtains and locally caches a complete pinned jpackage package containing Java. That executable
  cache lives outside every destination.

## Decision

1. Native output lives at the destination paths declared by the recipes for the configured programs to consume.
   Shared content can use locations such as `.agents/`. Unrelated real directories, files and existing links remain in
   place.
2. Declared writes and deletions stay inside the destination, as [0012](0012-write-boundary.md) decides. An existing
   directory inside it is accepted as a target even when Cook did not create it.
3. Cook writes nothing in the destination beyond declared native output and `cook.lock`: no local state directory,
   record or cache.
4. Recipe ownership lives in the committed `cook.lock` at the repository root ([0007](0007-ownership.md)). Cooking
   evaluates supplied recipes against current content and works without the lock.
5. The engine pin and its cache follow [0017](0017-engine-version.md).
6. Cookbook repositories and source origins follow [0013](0013-organization-content.md).
7. Configuring native program files does not make Cook the owner of those programs' state or workspaces. Recipes do
   not implicitly relocate that state.

## Reasons

- **1.** Native paths let programs consume their configuration directly. Declared scope preserves unrelated content
  without forcing every repository into one skills layout.
- **2.** A symlinked skills directory, as in the `acme/api` example, makes an apparently local path able to reach
  outside the destination; [0012](0012-write-boundary.md) gives the reasons for the boundary. Accepting existing
  directories lets both example layouts receive declared changes as they are.
- **3, 4.** The repository's version control already keeps the history of every file Cook writes, so a local record
  of Cook's own would repeat it in one clone. Leaving no local state means nothing to ignore and nothing to lose.
  Ownership is shared review material, so it lives in one committed file that every clone reads and pull requests
  show.
- **5, 6.** A committed engine pin and cookbook sources answer different questions with different lifetimes; the
  linked ADRs give their reasons.
- **7.** Existing state and workspace conventions belong to their programs and users, so the layout leaves their files
  where they are.

## Rejected alternatives

### An ignored local directory of batch records

At its best, a record of each batch keeps the patches and sources needed to reverse it later. It loses because it
duplicates the repository's version control in one clone, needs an ignore entry in every repository, and is lost with
that clone; [0007](0007-ownership.md) gives the reasons.

### A committed ownership/configuration directory

At its best, one directory contains everything reviewers need to describe managed output. It loses because shared
ownership needs only the single committed `cook.lock`, and a directory invites state that no reviewer reads.

### Ingredients and recipes only in a per-user content-hash cache

At its best, many repositories share one verified copy of each ingredient or recipe source. It loses as a universal
layout requirement because recipes can use local cookbook sources inside the repository, and a source cache would be
confused with the sources a reviewer reads in the repository. An engine package cache serves the pinned wrapper without imposing
that cache model on cookbook content.

### Replace native skills directories with whole-directory links

At its best, a single link exposes all shared skills. It loses as a default because an existing real directory may
hold unrelated files. An explicitly declared symlink operation still uses the ordinary batch review and destination
boundary.

## Consequences

Costs:

- Shared native configuration, a committed engine pin and local engine packages have different lifetimes; a missing
  package requires obtaining the pinned package before cooking recipes.
- Earlier content comes only from the repository's version control.

Must be maintained:

- Recipes and program models account for the native paths they configure and the extent of declared replacements.
- `cook.lock` stays committed at the repository root, and Cook adds no other file of its own.

## Confirmation

- Review the two skills-layout examples: do declared changes preserve unrelated content in both?
- Review an HTTPS recipe's batch: does the source URL stay the base for relative references, and is a local recipe
  outside the repository refused?
- Review a new path Cook writes: is it either native output at a declared target or `cook.lock`?
