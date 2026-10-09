# 0008. Native agent configuration is merge-rendered: the tool owns specific keys and preserves everything else

- Status: Accepted
- Date: 2026-10-09

## Context

- Agents read permissions and hooks from native files at fixed locations. Claude Code (2.1.x) reads
  `.claude/settings.json`. Codex CLI (0.16x) reads `.codex/config.toml`, `.codex/hooks.json` and the files in
  `.codex/rules/`.
- These files are shared with people. Of 51 surveyed repositories, 8 have a project settings file; 7 of those hold
  hand-written permissions (4 to 102 rules), 5 hold hooks, and one carries a schema reference key. People also set
  environment, model, plugin and status-line keys there.
- Personal override files (`settings.local.json`, `*.local.toml`) exist in 20 of the 51 repositories. They are
  personal and usually untracked.
- Where an agent reads a whole directory of rule files, a separate file can be added without touching others. The
  project settings file of Claude Code has no include mechanism.

## Decision

- Where an agent reads a directory of configuration files, the tool owns one separate whole file in it.
- Where an agent reads one shared file, the tool owns only named keys and entries: the permission lists, and the
  hook entries whose command is the tool's own entry point. Every other key and entry is preserved byte for byte.
  The owned set is recorded in the manifest ([0007](0007-recorded-ownership-and-committed-artefacts.md)).
- Personal override files are never written or recorded. They are read only at adoption, when a person chooses
  to import them ([0016](0016-lossless-adoption-of-native-permissions.md)).
- When the tool takes ownership of a key that already holds hand-written entries, those entries are imported into
  the declarative sources ([0006](0006-repository-state-is-declarative-data.md)) without loss, so they keep their
  effect ([0016](0016-lossless-adoption-of-native-permissions.md)).

## Alternatives considered

### Own native files wholesale

At its best, rendering and checking are simplest. Not chosen: it destroys keys people set, which in the survey
means permissions, hooks or other settings in every repository that has the file.

### Write only to the personal override file

At its best, nothing committed is touched. Not chosen: the file is personal and usually untracked, so the team, CI
and reviewers never see it.

### A separate owned file included by the native file

At its best, ownership is a whole file everywhere. Not chosen as the general rule: it depends on the agent
supporting inclusion, and Claude Code's project settings do not. It is the rule where the agent reads a directory.

## Consequences

Positive:

- People keep editing their own keys in native files.
- Personal overrides stay personal.

Negative:

- Preservation is as exact as the serializer's round trip of JSON and TOML formatting; formatting the serializer
  cannot reproduce is a residual risk.
- A hand edit to an owned key is drift and is overwritten on the next render; the change belongs in the source.

Must be maintained:

- Round-trip tests per native format, and the owned key set per adapter
  ([0009](0009-agent-neutral-sources-and-adapters.md)).

## Confirmation

- Fixtures carry foreign keys, unusual key order and TOML comments; after rendering, every byte outside the owned
  keys is unchanged.
- Fixtures with personal override files assert that the files are never written, and are opened only when an
  adoption imports them.
