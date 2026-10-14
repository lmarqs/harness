# 0005. Repository files, ingredients and stations carry a schema version the binary enforces

## Objective

**Problem.** Many repositories depend on the shape of the tool's files and of the ingredients and stations they use, and
binaries of one release meet files written for another. A binary that misreads a newer file can drop a deny rule or a
guard without noticing, then rewrite the repository with outdated rules. Output that changes with every release makes
every upgrade look like drift.

**Goal.** A binary either reads a repository file, an ingredient or a station correctly or refuses it, naming the
release to run, and an upgrade by itself never shows as drift.

## Context

- The repository's data files are a public contract that many repositories depend on. A change to their shape is a
  migration in each of them.
- Each repository pins the release that serves it ([0017](0017-engine-version.md)). Releases still meet files that were
  not written for them: a global binary too old to defer to the pin, an environment override that runs a locally built
  binary, a branch whose pin differs from the checkout a person is in, and every upgrade that moves the pin forward.
- A binary that reads a file written for a newer shape can misread or drop fields it does not know, then rewrite that
  file and the files rendered from it with outdated rules. A dropped field can be a deny rule or a guard.
- Generated artefacts are committed and drift-checked ([0007](0007-ownership.md)). Any value in them that changes with
  the binary's release shows as drift after every upgrade.
- Ingredients and stations are data the binary interprets, published in cookbooks and never shipped with the binary. An
  ingredient can mark deny rules as floor ([0022](0022-tool-integration.md)), and a station's entries, which the binary
  writes into the repository's files when the station is applied, can hold deny rules and guards
  ([0024](0024-ingredients-and-stations.md)). Each is pinned by its content hash, so its bytes cannot change without a
  new pin ([0026](0026-cookbook-trust.md)).
- A recipe is published in cookbooks and, once copied, is a file of the repository that its team edits; no hash fixes
  its bytes there ([0013](0013-organization-content.md), [0025](0025-recipes.md)). Its steps and requirements include
  the lanes a change must pass.
- An ingredient that fills a contract relies on that contract's operations and output kinds
  ([0023](0023-core-and-contracts.md)). A contract can gain an operation while the ingredient format stays the same.
- Most releases change behaviour without changing the shape of any file. For example, a release that fixes a bug in a
  check reads and writes the same files, with the same fields, as the release before it.

## Decision

1. Each repository file the tool reads carries an integer schema version, and the binary validates the file against that
   schema before acting on it.
2. The binary refuses a file whose schema is newer than it supports, with a message naming the release needed.
3. The binary migrates a file with an older schema and shows the diff.
4. Generated artefacts record the schema version and never the binary version.
5. Schema changes are rare. Migrations exist from every schema inside a documented support window.
6. Every ingredient and every station carries the schema version of the format it is written in and, when an ingredient
   fills a contract, the version of that contract it fills. The binary refuses an ingredient or a station whose schema
   or contract version is newer than it supports, naming the release needed, and reads one of an older schema or
   contract version inside the support window without rewriting it.
7. Every recipe carries the schema version of its format. Copied into a repository, it is a repository file, under
   points 1 to 3. The binary refuses to copy a published recipe, or to merge one into a copy, when its schema is newer
   than it supports, naming the release needed.

## Reasons

- **1.** An integer that versions the file's shape separates the contract from the release. Because most releases keep
  the shape, most upgrades touch no repository file. Validation before action stops a malformed file before anything is
  written.
- **2.** Refusal is the only safe response to fields the binary does not know: proceeding would misread or drop them and
  carry the loss into rendered permissions. Naming the release turns the refusal into one step a person can take. The
  trade: a person with an older binary is blocked on that repository until they update.
- **3.** Migration keeps older repositories usable after an upgrade, and showing the diff keeps the change reviewable
  like every other generated change ([0007](0007-ownership.md)).
- **4.** A version stamp would change every committed artefact on every upgrade, burying the changes that matter in
  ones that do not. With only the schema recorded, an artefact changes only where the new release renders it
  differently, and that change lands in the upgrade's own diff ([0017](0017-engine-version.md)). The pin is the one
  committed record of a release.
- **5.** Each schema change costs one migration in every adopting repository and one more code path in the binary.
  Keeping changes rare bounds the first cost, and a support window bounds the second.
- **6.** A binary that misread a newer ingredient could drop a floor rule, and one that misread a newer station could
  drop a deny rule or a guard from the entries it writes, the way it could drop a deny rule from a repository file, so
  the same refusal applies (point 2). A contract carries a version of its own because a contract can change while the
  format does not (Context): an ingredient written against a newer contract could rely on an operation the binary lacks,
  and refusing it names the release instead of failing in the middle of a use case. Neither can be migrated in place,
  because the hash its repository pins fixes its bytes (Context); reading older schemas and contract versions keeps it
  usable across releases until its cookbook publishes a newer version. The trade: the binary keeps a reader for every
  ingredient and station schema and every contract version in the support window.
- **7.** A binary that misread a newer recipe could drop a required lane or a requirement, the way it could drop a guard
  from a repository file, so the refusal of point 2 applies, before the recipe's bytes enter the repository. Once
  copied, nothing fixes a recipe's bytes (Context), so unlike an ingredient or a station it migrates in place with a
  diff, like any file the repository holds. The trade: the binary keeps a migration for every recipe schema in the
  support window, as it does for every repository file.

## Rejected alternatives

### The binary version as the contract

Files record the release that wrote them, and a binary refuses files from newer releases. At its best, there is no
second number to manage. It loses because every upgrade shows as drift, and a release that changes no file shape still
blocks every teammate who has not upgraded.

### Tolerant reading: unknown fields are ignored

At its best, older binaries keep working with newer files. It loses because an ignored field can be a deny rule or a
guard. Ignoring it silently widens what workers may do, and the next render writes the weaker result back.

### No version contract

The latest binary is assumed everywhere. At its best, there is nothing to maintain. It loses because an older binary
silently rewrites a newer repository's files with outdated rules.

## Consequences

Costs:

- A person with an older binary cannot act on a repository with a newer schema until they update.
- A schema change is a migration in every adopting repository.

Must be maintained:

- The published schemas, migrations from every schema in the support window, and the window itself.

## Confirmation

- Validation fixtures cover each file, including invalid content and an unknown schema.
- Fixtures at the previous schema migrate and print a diff; fixtures at a newer schema are refused with a message that
  names the release.
- A test asserts that no generated artefact contains the binary version.
- A fixture ingredient and a fixture station at a newer schema, and a fixture ingredient that declares a newer contract
  version, are each refused with a message that names the release; a fixture ingredient and a fixture station at the
  oldest schema and contract version in the support window load and stay byte-identical.
- A published fixture recipe at a newer schema is refused when it is added and when it is merged, with a message that
  names the release; a copied fixture recipe at the previous schema migrates and prints a diff.
