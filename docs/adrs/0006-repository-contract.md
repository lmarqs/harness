# 0006. A repository holds declarative data, never engine code

## Objective

**Problem.** When a practice spreads by copying its engine code into each repository, every copy becomes a fork: a fix
made in one never reaches the others. Each repository also has facts only it knows, which must live with it. Where that
line falls decides whether a fix reaches every repository.

**Goal.** A fix to the workflow reaches every repository through one release, while each repository keeps and controls
the facts only it knows.

## Context

- Engine code copied into repositories diverges. Of three hand-ported copies of one agent workflow, one carries a fork
  of the configuration renderer that differs from the original by 108 lines, and fixes made in one copy do not reach
  the others.
- Repository-specific facts vary and must live in the repository: the stack, the commands to run, which paths need which
  checks, permissions, hooks, what happens at each step of the development lifecycle, and which guidance agents receive.
- People, agents and reviewers read these files, and agents edit them ([0002](0002-division-of-work.md)).
- Permission changes carry a distinct review concern: reviewers look for what an agent may now do that it could not do
  before.
- A clone must obtain the pinned release before any binary exists on the machine ([0017](0017-engine-version.md)).
- Agents and task runners call commands by path.
- The repository-facing contract and the implementation language ([0003](0003-implementation-language.md)) are the
  hardest decisions of this project to reverse: once many repositories depend on the contract, every change to it is a
  migration in each one.

## Decision

1. A repository holds data; the engine lives in the binary.
2. The data is a small set of YAML files in the tool's configuration directory ([0014](0014-directory-layout.md)).
   Each file has one concern and one audience: the repository's facts for the engine, including its standing-context
   declarations ([0020](0020-standing-context.md)); the permission policy; and the agent-neutral hook declarations
   together with the lifecycle and notification actions ([0015](0015-development-lifecycle.md),
   [0019](0019-action-kinds.md)).
3. Each file carries a schema version and is validated before use ([0005](0005-schema-compatibility.md)).
4. No engine code is copied into a repository. The tool-owned executable content in a repository is limited to one-line
   entry points that call the tool and to the wrapper ([0017](0017-engine-version.md)), which only resolves, verifies
   and executes the pinned binary.
5. Commands a repository declares are its own data.

## Reasons

- **1.** With the engine in one binary, a fix ships as one release and reaches a repository through one reviewed change
  to its pin ([0017](0017-engine-version.md)). There is no copy to edit locally, which is how the 108-line fork arose. A
  repository changes only when its own facts change.
- **2.** YAML reads well in diffs, takes comments, and parses without a runtime ([0004](0004-runtime-dependencies.md));
  agents edit it reliably. One concern per file lets a permission change be reviewed as a diff of a file that holds only
  permissions. Hook declarations and actions share a file because both state what runs when an event occurs. The
  standing-context declarations sit with the repository's facts because they describe the repository, not what runs.
  The trade: several schemas to version.
- **3.** Validation turns a mistake into an error before anything is written, and the schema version is the
  compatibility contract between files and releases ([0005](0005-schema-compatibility.md)).
- **4.** One-line entry points exist because agents and task runners call commands by path; they contain no logic. The
  wrapper exists because a clone must obtain the pinned release before the binary is on the machine; limiting it to
  resolve, verify and execute keeps every behaviour in the binary. Anything more would be engine code in the repository
  and would diverge like the copies did.
- **5.** The repository's build, test and lint commands belong to its stack. The binary runs them as declared and never
  interprets them, so they are data even though they execute.

## Rejected alternatives

### Vendored engine per repository, locked by hash and updated by bot pull requests

At its best, it works offline, agents can read the engine inside the repository, and pinning comes free. It loses
because every fix becomes one change per repository, copies get edited locally, which is how the 108-line fork
happened, each repository needs the engine's runtime, and the update volume is high while the engine changes often.

### Hand-copied engine

At its best, there is no investment and each repository tailors freely. It loses because it produces the measured
divergence and the lost safety settings.

### One combined file

At its best, there is one file to find and to version. It loses because permissions have a distinct review concern, and
a permission change is easiest to review as a change to a file that holds only permissions.

### Configuration as code

A scripting language or a DSL in the repository. At its best, it is expressive. It loses because it needs a runtime, it
cannot be validated or rendered deterministically ([0002](0002-division-of-work.md)), and readers must execute it in
their heads to know its effect.

## Consequences

Costs:

- Agents cannot read engine source inside the repository; the embedded documentation compensates
  ([0002](0002-division-of-work.md)).
- The schemas are a public contract; a breaking change needs a migration ([0005](0005-schema-compatibility.md)).

Must be maintained:

- The schemas and their documentation.

## Confirmation

- Validation fixtures cover each file, including invalid content and an unknown schema.
- A test asserts that the manifest of an adopted fixture lists no executable file other than one-line entry points and
  the wrapper.
