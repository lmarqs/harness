# Architecture decision records

An architecture decision record (ADR) records one decision that shapes the structure of this project: the forces
that led to it, the decision, the alternatives that were weighed, and what the project must maintain because of it.
Each ADR stands alone and is numbered in the order it was accepted.

## When to write one

Write an ADR for a decision that is hard to reverse or that constrains how other parts are built. Examples: the
implementation language, the contract between the tool and the repositories that adopt it, what the tool may and
may not write, how ownership of generated files is recorded.

Do not write an ADR for a feature, a command, a flag, a file field, a task or a plan. Those belong in the feature
and user documentation. If a proposed ADR reads as a description of something the tool does, it is not an ADR.

## Status and changes

| Status | Meaning |
| --- | --- |
| Proposed | Under discussion; not yet binding. |
| Accepted | Binding. Code and documentation follow it. |
| Superseded by NNNN | Replaced by a later ADR, which links back to it. |
| Deprecated | No longer applies, and nothing replaces it. |

An accepted ADR is never rewritten. A changed decision gets a new ADR that supersedes the old one; the old one keeps
its text and changes only its status line.

The initial corpus, ADRs 0001 to 0016, was settled together on 2026-10-09; from now on, a change is a new ADR that
supersedes.

## Template

```markdown
# NNNN. <The decision, stated as a sentence>

- Status: Proposed | Accepted | Superseded by NNNN | Deprecated
- Date: YYYY-MM-DD

## Context

The forces at play and the constraints, each with its source outside this project (a tool's documented
behaviour, a measured observation, a prior decision). Measured facts carry their numbers; targets are stated as requirements.

## Decision

What is decided, in the present tense. Name a mechanism only where the mechanism is the decision.

## Alternatives considered

### <Alternative>

The alternative stated at its best, then why it was not chosen.

## Consequences

Positive:

Negative:

Must be maintained:

## Confirmation

How compliance is checked: a test, a CI job, a review rule or a measurement.
```

## Index

| ADR | Decision | Status |
| --- | --- | --- |
| [0001](0001-meta-harness-never-calls-a-model.md) | The tool is a meta-harness: it configures agent harnesses and never acts as one | Accepted |
| [0002](0002-deterministic-work-in-the-binary.md) | Deterministic work lives in the binary; judgement is delegated to agents guided by shipped documentation | Accepted |
| [0003](0003-single-static-binary-in-go.md) | The tool is a single binary written in Go | Accepted |
| [0004](0004-zero-runtime-dependencies.md) | The binary has zero runtime dependencies | Accepted |
| [0005](0005-global-installation-and-schema-versioning.md) | Global installation by default; version skew is handled by schema versioning and an update notice | Accepted |
| [0006](0006-repository-state-is-declarative-data.md) | Repository state is versioned declarative data, never engine code | Accepted |
| [0007](0007-recorded-ownership-and-committed-artefacts.md) | Ownership is recorded in a manifest; generated artefacts are committed and drift-checked; removal follows the manifest | Accepted |
| [0008](0008-merge-rendered-native-configuration.md) | Native agent configuration is merge-rendered: the tool owns specific keys and preserves everything else | Accepted |
| [0009](0009-agent-neutral-sources-and-adapters.md) | Agent-specific configuration is produced by adapters from agent-neutral sources | Accepted |
| [0010](0010-optional-and-transparent-adoption.md) | Adoption is optional and transparent: nothing in a repository depends on the binary being present | Accepted |
| [0011](0011-built-in-safety-invariants.md) | Safety invariants are built into the binary and cannot be expressed or removed as configuration | Accepted |
| [0012](0012-coexistence-with-team-record-and-third-party-state.md) | The binary contains no hosting-platform logic, never writes the team's record, and never reconfigures third-party state or files it did not create | Accepted |
| [0013](0013-organization-neutrality-and-presets.md) | The binary carries no organization-specific content; organizations layer presets through an extension mechanism | Accepted |
| [0014](0014-directory-layout-follows-third-party-discovery.md) | The directory layout is dictated by where third-party tools look | Accepted |
| [0015](0015-lifecycle-events-with-declared-actions.md) | The development lifecycle is a fixed set of events; the binary fires them and enforces guards, and repositories and presets declare the actions | Accepted |
| [0016](0016-lossless-adoption-of-native-permissions.md) | Adoption is lossless: the permission source expresses every native rule, and adoption proves equivalence before writing | Accepted |
