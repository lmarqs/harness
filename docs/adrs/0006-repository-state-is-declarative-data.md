# 0006. Repository state is versioned declarative data, never engine code

- Status: Accepted
- Date: 2026-10-09

## Context

- Engine code copied into repositories diverges. In the evidence behind this project, one copy carried a fork of
  the configuration renderer that differed from the original by 108 lines, and fixes made in one copy never
  reached the others.
- Repository-specific facts do vary and must live in the repository: the stack, the commands to run, which paths
  need which checks, permissions and hooks.
- People, agents and reviewers read these files; agents edit them ([0002](0002-deterministic-work-in-the-binary.md)).
- The repository-facing contract is one of the two hardest decisions to reverse in this project. Once many
  repositories depend on it, every change is a migration in each of them.

## Decision

A repository holds data; the engine lives in the binary.

The data is a small set of YAML files in the canonical agent directory
([0014](0014-directory-layout-follows-third-party-discovery.md)). Each file has one concern and one audience: the
repository's facts for the engine, the permission policy, and agent-neutral hook declarations together with the
lifecycle actions ([0015](0015-lifecycle-events-with-declared-actions.md)). Each file carries an integer schema
version ([0005](0005-global-installation-and-schema-versioning.md)), and the binary validates every
file against its published schema before acting on it.

No engine code is copied into a repository. The only tool-owned executable content in a repository is one-line
entry points that call the binary ([0010](0010-optional-and-transparent-adoption.md)). Commands a repository
declares are its own data.

## Alternatives considered

### Vendored engine per repository, locked by hash and updated by bot pull requests

At its best, it works offline, agents can read the engine inside the repository, and pinning comes free. Not
chosen: every fix becomes one change per repository; copies get edited locally, which is how the 108-line fork
happened; each repository needs the engine's runtime; and the update volume is high while the engine changes daily.

### Hand-copied engine (the baseline)

No investment. Not chosen: it produced the measured divergence and the lost safety settings.

### One combined file

At its best, there is one file to find and to version. Not chosen: permissions and hooks each have a distinct
reviewer concern, and a permission change is easiest to review as a change to one file that holds only permissions.

### Configuration as code

A scripting language or DSL in the repository. At its best, it is expressive. Not chosen: it needs a runtime, it
cannot be validated or rendered deterministically, and readers must execute it mentally to know its effect.

## Consequences

Positive:

- A fix ships as one release; a repository changes only when its own data changes.
- Permission changes appear as diffs in one file.

Negative:

- Agents cannot read engine source inside the repository; the embedded documentation compensates
  ([0002](0002-deterministic-work-in-the-binary.md)).
- The schemas are a public contract; breaking changes need migrations.

Must be maintained:

- The schemas, their documentation and migrations between versions.

## Confirmation

- Schema validation fixtures cover each file, including invalid and unknown-schema cases.
- A test asserts that the ownership manifest of an adopted fixture lists no executable file other than entry points.
