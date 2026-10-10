# 0005. Every repository file and plugin carries a schema version the binary enforces

## Objective

**Problem.** Many repositories depend on the shape of the tool's files and of the plugins they use, and binaries of one
release meet files written for another. A binary that misreads a newer file can drop a deny rule or a guard without
noticing, then rewrite the repository with outdated rules. Output that changes with every release makes every upgrade
look like drift.

**Goal.** A binary either reads a repository file or a plugin correctly or refuses it, naming the release to run, and an
upgrade by itself never shows as drift.

## Context

- The repository's data files are a public contract that many repositories depend on
  ([0006](0006-repository-contract.md)). A change to their shape is a migration in each of them.
- Each repository pins the release that serves it ([0017](0017-engine-version.md)). Releases still meet files that were
  not written for them: a global binary too old to defer to the pin, an environment override that runs a locally built
  binary, a branch whose pin differs from the checkout a person is in, and every upgrade that moves the pin forward.
- A binary that reads a file written for a newer shape can misread or drop fields it does not know, then rewrite that
  file and the files rendered from it with outdated rules. A dropped field can be a deny rule or a guard.
- Generated artefacts are committed and drift-checked ([0007](0007-ownership.md)). Any value in them that changes with
  the binary's release shows as drift after every upgrade.
- A plugin is data the binary interprets, written by the project or by others, and it can mark deny rules as floor
  ([0022](0022-tool-integration.md)). A plugin from an outside source is pinned by its content hash, so its bytes cannot
  change without a new pin ([0021](0021-release-trust.md)).
- Most releases change behaviour without changing the shape of any file. For example, a release that fixes a bug in a
  check reads and writes the same files, with the same fields, as the release before it.

## Decision

1. Each repository file the tool reads carries an integer schema version, and the binary validates the file against that
   schema before acting on it.
2. The binary refuses a file whose schema is newer than it supports, with a message naming the release needed.
3. The binary migrates a file with an older schema and shows the diff.
4. Generated artefacts record the schema version and never the binary version.
5. Schema changes are rare. Migrations exist from every schema inside a documented support window.
6. Every plugin carries the schema version of the plugin format it is written in. The binary refuses a plugin whose
   schema is newer than it supports, naming the release needed, and reads a plugin of an older schema inside the support
   window without rewriting it.

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
- **6.** A binary that misread a newer plugin could drop a floor rule the way it could drop a deny rule from a
  repository file, so the same refusal applies (point 2). A plugin from an outside source cannot be migrated in place,
  because the hash its repository pins fixes its bytes (Context); reading older schemas keeps it usable across releases
  until its author publishes a newer version. The trade: the binary keeps a reader for every plugin schema in the
  support window.

## Rejected alternatives

### The binary version as the contract

Files record the release that wrote them, and a binary refuses files from newer releases. At its best, there is no
second number to manage. It loses because every upgrade shows as drift, and a release that changes no file shape still
blocks every teammate who has not upgraded.

### Tolerant reading: unknown fields are ignored

At its best, older binaries keep working with newer files. It loses because an ignored field can be a deny rule or a
guard. Ignoring it silently widens what agents may do, and the next render writes the weaker result back.

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
- A fixture plugin at a newer plugin schema is refused with a message that names the release; a fixture plugin at the
  oldest schema in the support window loads and stays byte-identical.
