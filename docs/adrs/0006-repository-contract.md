# 0006. A repository holds declarative data, never engine code

## Objective

**Problem.** When a practice spreads by copying its engine code into each repository, every copy becomes a fork: a fix
made in one never reaches the others. Each repository also has facts only it knows, which must live with it. Where the
line between shared engine and local facts falls decides whether a fix reaches every repository.

**Goal.** A fix to a practice's engine reaches every repository through one release, while each repository keeps and
controls the facts only it knows.

## Context

- Engine code copied into repositories diverges. Example: acme copies its agent workflow, including the script that
  renders agent configuration, by hand from `acme/api` into `acme/web` and `acme/infra`. A teammate patches the renderer
  in `acme/web` for a local need, and within a year it differs from the one in `acme/api` by a hundred lines. A bug
  fixed in `acme/api` then reaches neither copy: the `acme/web` renderer differs too much to take the patch, and nobody
  goes back to `acme/infra`.
- Repository-specific facts vary and must live in the repository: the stack, the commands to run, which paths need which
  checks, permissions, hooks, what happens at each step of the development lifecycle, and which guidance workers
  receive.
- People, coding agents and reviewers read the files that hold these facts, and coding agents edit them
  ([0002](0002-division-of-work.md)).
- Permission changes carry a distinct review concern: reviewers look for what a worker may now do that it could not do
  before.
- A clone must obtain the pinned release before any binary exists on the machine ([0017](0017-engine-version.md)).
- A program is described by an ingredient, and the description is the same in every repository that uses that program
  ([0022](0022-tool-integration.md)).
- Worker programs run hooks on events, and each hook entry is one command ([0009](0009-agent-support.md)).
- The repository-facing contract and the implementation language ([0003](0003-implementation-language.md)) are the
  hardest decisions of this project to reverse: once many repositories depend on the contract, every change to it is a
  migration in each one.

## Decision

1. A repository holds data; the engine lives in the binary.
2. The data is a small set of YAML files in the tool's configuration directory ([0014](0014-directory-layout.md)). Each
   file has one concern and one audience: the repository's facts for the engine, including its standing-context
   declarations ([0020](0020-standing-context.md)); the permission policy; and the worker-neutral hook declarations
   together with the lifecycle and notification actions ([0015](0015-development-lifecycle.md),
   [0019](0019-action-kinds.md)). Each recipe the repository holds is one more file of that data, for one kind of
   change, with the template files it carries beside it ([0025](0025-recipes.md)).
3. Each file carries a schema version and is validated before use ([0005](0005-schema-compatibility.md)).
4. No engine code is copied into a repository. The tool-owned executable content in a repository is limited to the
   wrapper ([0017](0017-engine-version.md)), which only resolves, verifies and executes the pinned binary, and to hook
   entries that call it.
5. Commands a repository declares are its own data.
6. Ingredients and stations are data the binary interprets, never engine code in the repository
   ([0022](0022-tool-integration.md), [0023](0023-core-and-contracts.md), [0024](0024-ingredients-and-stations.md)). The
   repository's data names every ingredient it uses and the stations it applied, and pins each one from the cookbook
   that publishes it, by version and content hash; neither is stored in the repository
   ([0014](0014-directory-layout.md)). A recipe is stored in the repository: once copied, it is data the repository
   owns, and the binary runs it ([0013](0013-organization-content.md), [0025](0025-recipes.md)).

## Reasons

- **1.** With the engine in one binary, a fix ships as one release and reaches a repository through one reviewed change
  to its pin ([0017](0017-engine-version.md)). There is no copy to edit locally, which is how the `acme/web` renderer in
  Context forks. A repository changes only when its own facts change.
- **2.** YAML reads well in diffs, takes comments, and parses without a runtime.
  Coding agents edit it reliably. One concern per file lets a permission change be reviewed as a diff of a file that
  holds only permissions. Hook declarations and actions share a file because both state what runs when an event occurs.
  The standing-context declarations sit with the repository's facts because they describe the repository, not what runs.
  The trade: there are several schemas to version.
- **3.** Validation turns a mistake into an error before anything is written, and the schema version is the
  compatibility contract between files and releases ([0005](0005-schema-compatibility.md)).
- **4.** Hook entries exist because worker programs run hooks on events; each is one command that calls the wrapper and
  contains no logic. The wrapper exists because a clone must obtain the pinned release before the binary is on the
  machine. Limiting it to resolve, verify and execute keeps every behaviour in the binary. Anything more would be engine
  code in the repository and would diverge like the copied renderer in Context.
- **5.** The repository's build, test and lint commands belong to its stack. The binary runs them as declared and never
  interprets them, so they are data even though they execute.
- **6.** An ingredient describes a program as data the binary interprets, and a station is data too, so the engine stays
  in the binary for every program a repository uses and every way of working it applies
  ([0022](0022-tool-integration.md)). Executable ingredients or stations would be engine code beside the binary, with
  the divergence of point 4 and a runtime the binary does not have.
  - Which ingredients and stations a repository uses, and at which version and hash, is a fact only that repository
    knows, so it lives in its data and changes by a reviewed diff. An ingredient is shared by every repository that uses
    the program (Context) and fixed by its hash, so a copy in each repository would be one more copy to edit locally,
    the way the `acme/web` renderer in Context forks. What a station adds is in the repository's own files once it is
    applied ([0024](0024-ingredients-and-stations.md)), so a copy of the station would hold it twice.
  - A recipe is a practice the repository adapts, and its copy is the only one that acts ([0025](0025-recipes.md)), so
    it lives with the repository's other data. It holds data, not engine code: how a recipe runs (its stages, its kinds
    of step and its guards) is in the binary, so a fix to that reaches every repository with one release (point 1). A
    newer version of a recipe's own content reaches a copy through a merge that keeps the repository's changes
    ([0013](0013-organization-content.md)).
  - The trade: a reader of the repository sees which ingredient is pinned, not its description.

## Rejected alternatives

### Vendored engine per repository, locked by hash and updated by bot pull requests

At its best, it works offline, coding agents can read the engine inside the repository, and pinning comes free. It loses
because every fix becomes one change per repository and copies get edited locally, which is how the `acme/web` renderer
in Context forks. Each repository also needs the engine's runtime, and the update volume is high while the engine
changes often.

### Hand-copied engine

At its best, there is no investment and each repository tailors freely. It loses because it produces the divergence
shown in Context, and copies that lose their safety settings ([0011](0011-safety-invariants.md)).

### One combined file

At its best, there is one file to find and to version. It loses because permissions have a distinct review concern, and
a permission change is easiest to review as a change to a file that holds only permissions.

### Configuration as code

The repository holds its configuration in a scripting language or a DSL. At its best, it is expressive. It loses because
it needs a runtime, it cannot be validated or rendered deterministically, and readers
must execute it in their heads to know its effect.

## Consequences

Costs:

- Coding agents cannot read engine source inside the repository; the embedded documentation compensates.
- The schemas are a public contract; a breaking change needs a migration ([0005](0005-schema-compatibility.md)).

Must be maintained:

- The schemas and their documentation.

## Confirmation

- Validation fixtures cover each file, including invalid content and an unknown schema.
- A fixture that uses an ingredient and has applied a station holds the name, cookbook, version and hash of each in the
  repository's data and no copy of either. A fixture that added a recipe holds the copy, with its origin, in the tool's
  configuration directory.
- A test asserts that the manifest of an adopted fixture lists no executable file other than the wrapper, and that every
  owned hook entry is one command that calls it.
