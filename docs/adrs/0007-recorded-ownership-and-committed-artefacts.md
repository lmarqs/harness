# 0007. Ownership is recorded in a manifest; generated artefacts are committed and drift-checked; removal follows the manifest

- Status: Accepted
- Date: 2026-10-09

## Context

- Withdrawing a practice from a repository must cost as little as adding it. Before this project, withdrawal was a
  manual pass through each repository.
- The tool's output lands in three kinds of places: whole files it creates, blocks of text inside files people
  write (instruction files, READMEs, ignore files), and keys inside native configuration files it shares with
  people ([0008](0008-merge-rendered-native-configuration.md)).
- A clone without the binary must work ([0010](0010-optional-and-transparent-adoption.md)), and reviewers must see
  permission changes as diffs.
- A hand edit to generated output must be detected. Otherwise the next render discards it silently, or a check
  passes over it.
- JSON allows no comments, so ownership inside a JSON file cannot be marked in the file itself.

## Decision

- Ownership is recorded, not inferred. A manifest committed in the repository lists every tool-owned path, every
  managed block and every owned key set, each with a content hash, plus the schema version.
- Generated artefacts are committed.
- Inside files people write, owned text sits between begin and end markers that carry a block id, written in the
  file format's comment syntax. Text outside the markers is never written. Owned whole files that allow comments
  start with a header naming the source to edit.
- A check compares the repository with what the sources render and with the manifest, and fails on any difference.
- Removal is deterministic: it deletes exactly what the manifest lists, removes managed blocks and owned keys, and
  refuses any path whose hash changed since it was rendered unless the person forces it.

## Alternatives considered

### Infer ownership from headers and markers alone

At its best, there is no extra file. Not chosen: owned keys inside JSON cannot be marked; generated and hand-edited
content cannot be told apart; and removal has to guess.

### Generate on demand without committing

Render at session start or in CI. At its best, generated files never appear in diffs and cannot drift. Not chosen: a
clone without the binary has no agent configuration, reviewers lose permission diffs, and harnesses read their
configuration when a session starts.

### Own whole directories

At its best, the ownership rule is one line. Not chosen: the directories agents read are shared with people and
other tools ([0014](0014-directory-layout-follows-third-party-discovery.md)).

## Consequences

Positive:

- Removal and upgrade are mechanical and reviewable.
- Hand edits to owned content are reported instead of lost.

Negative:

- Generated files appear in diffs and can conflict in merges; the resolution is to render again, not to merge by hand.
- The manifest is one more committed file, and a hand edit to it is itself drift.

Must be maintained:

- The marker syntax for each file format and the manifest format, both part of the public contract
  ([0006](0006-repository-state-is-declarative-data.md)).

## Confirmation

- Fixture tests: an edit inside a managed block fails the check and names the file; an edit outside passes.
- After removal on an adopted fixture, the working tree differs from the pre-adoption state only in paths the
  manifest listed.
- Removal refuses a path with a changed hash unless forced.
