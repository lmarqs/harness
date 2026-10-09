# 0014. The directory layout is dictated by where third-party tools look

- Status: Accepted
- Date: 2026-10-09

## Context

Constraints from tools outside this project:

- Claude Code (2.1.x) reads project settings, hooks and skills from `.claude/` at the repository root.
- Codex CLI (0.16x) reads `.codex/config.toml`, `.codex/hooks.json` and `.codex/rules/` at the repository root.
- A widely used skills installer writes to `.agents/skills/`.
- An unattended loop runner (0.12, checked against the installed binary) reads its configuration only from a fixed
  directory under its working directory and offers no flag to move it.
- A local task tracker (1.3.1, checked against the installed binary) finds its directory by walking up from the
  working directory; an environment variable can move it.

Evidence from 51 surveyed repositories:

- 12 already use `.agents/` as the canonical agent directory, linked from the native agent directories.
- `.claude/skills` is a whole-directory link in 13 repositories, a real directory in 10, and a set of per-entry links
  in 1.
- An agent's own worktree directory exists under `.claude/` in 28.

A single new directory holding everything was proposed for this project and withdrawn once these constraints were
shown.

## Decision

- `.agents/` is the one canonical agent directory. It holds the agent-neutral sources
  ([0006](0006-repository-state-is-declarative-data.md)), the ownership manifest
  ([0007](0007-recorded-ownership-and-committed-artefacts.md)), shared skills and knowledge.
- Native agent directories stay where agents look. They hold rendered configuration
  ([0008](0008-merge-rendered-native-configuration.md)) and links into `.agents/`. Links are made per entry; existing
  real directories and existing whole-directory links stay as they are.
- `.work/` is the one root for the tool's own clone-local state. It is never committed.
- Third-party state stays where its tool requires it: the task tracker's directory at the root of the primary
  checkout, found by its upward walk from nested working copies; the loop runner's configuration only inside working
  copies the tool creates for it, because that location cannot be moved.
- No third-party state is relocated through environment variables or path overrides.
- The ignore rules for the state roots form a managed block that omits any path the repository tracks.

## Alternatives considered

### One directory for everything

At its best, every agent-related file is in one place. Not chosen: agents do not read it, the loop runner cannot be
pointed at it, and every other tool would need links or overrides, breaking direct use.

### Relocate third-party state through overrides

At its best, the repository root stays tidy. Not chosen: every invocation needs the override, and people who run
those tools directly would see different state.

### Whole-directory links from native directories

At its best, one link per directory. Not chosen: in 10 surveyed repositories the target is a real directory, and
replacing it moves files people own.

### Tool state inside `.agents/`

At its best, one tool directory. Not chosen: it mixes committed sources with uncommitted state and needs ignore
rules inside a committed tree.

## Consequences

Positive:

- Every tool finds its files without configuration, and direct use of each third-party tool keeps working.

Negative:

- The repository root holds several dot-directories.
- The layout is set from outside; a change in a third-party tool's discovery rule forces a change here.
- Per-entry links need a filesystem with symbolic links.

Must be maintained:

- The table of third-party locations and the tool versions they were checked against.

## Confirmation

- The tool's health check verifies that every link resolves.
- Fixtures with each of the three skills-directory shapes keep their shape after adoption.
- A test asserts that loop-runner configuration is written only inside working copies the tool created.
