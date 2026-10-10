# 0008. The tool owns only named keys inside shared native files

## Objective

**Problem.** The files agents read their permissions and hooks from are shared with people, who keep their own settings
there. Rewriting such a file as a whole destroys those settings. Editing it without clear ownership leaves nobody able
to tell the tool's content from a person's.

**Goal.** People keep every setting they put in a shared native file, and the tool's content in that file can always be
told apart.

## Context

- Agents read permissions and hooks from native files at fixed locations. Claude Code (2.1.x) reads
  `.claude/settings.json`. Codex CLI (0.16x) reads `.codex/config.toml`, `.codex/hooks.json` and every file in
  `.codex/rules/`.
- Where an agent reads a whole directory of rule files, a file can be added to it without touching the others.
  Claude Code's project settings file has no include mechanism: everything it holds lives in one file.
- These files are shared with people. Claude Code's project settings file holds permissions, hooks and keys for the
  environment, the model, Claude Code's own plugins, the status line and a schema reference, and people write any of
  them by hand. Example: the `.claude/settings.json` of `acme/api` holds 40 hand-written allow and deny rules, a hook
  that formats each edited file and an environment variable for the test database.
- Personal override files (`.claude/settings.local.json`, `*.local.toml`) hold one person's choices and are usually
  untracked. Example: a teammate's `.claude/settings.local.json` in `acme/api` allows a deploy command that the team
  never agreed to give agents.
- JSON has no comments, so ownership of a key inside a JSON file cannot be marked in the file itself
  ([0007](0007-ownership.md)).
- Rendering an owned key writes its whole content from the repository's data. A hand-written entry that is not in the
  data is gone after that write.

## Decision

1. Where an agent reads a directory of configuration files, the tool owns one separate whole file in that directory.
2. Where an agent reads one shared file, the tool owns only named keys and entries: the permission lists, and the hook
   and notification entries whose command is the tool's own entry point. Every other key and entry is preserved byte
   for byte.
3. The owned set of every native file is recorded in the manifest, under the name of the plugin that renders it
   ([0007](0007-ownership.md), [0022](0022-tool-integration.md)).
4. Personal override files are never written and never recorded. They are read only during adoption, and only when a
   person chooses to import them ([0016](0016-permission-adoption.md)).
5. Before the tool takes ownership of a key that already holds hand-written entries, those entries are imported into
   the repository's data ([0006](0006-repository-contract.md)) without loss, so they keep their effect
   ([0016](0016-permission-adoption.md)).

## Reasons

- **1.** A whole file is the clearest ownership there is: one path, one hash, and removal deletes one file. Where the
  agent reads a directory, this costs nothing, because people's files in the same directory are never opened.
- **2.** Claude Code's project settings cannot include another file, and people keep permissions, hooks, environment,
  model, Claude Code plugin and status-line keys in it. Owning the whole file would destroy those keys in every
  repository that has one, and owning nothing would leave the policy without effect. The permission lists and the
  entries that call the tool are the narrowest set that still makes the rendered policy and the declared hooks and
  notifications ([0019](0019-action-kinds.md)) take effect. Byte-for-byte preservation keeps the tool's diff to its own
  keys, so a reviewer sees only what the tool changed.
- **3.** Ownership inside a JSON file cannot be marked in the file. The manifest is where the drift check and removal
  learn which keys to compare and which to take out ([0007](0007-ownership.md)). Recording the plugin lets a repository
  stop using a harness plugin and lose exactly the keys it rendered.
- **4.** Writing an override file changes one person's setup through a change meant for the team, and recording it
  puts a personal, usually untracked file into a committed manifest. Importing it is a choice because it publishes one
  person's rules to the team ([0016](0016-permission-adoption.md)).
- **5.** Rendering an owned key overwrites its content with what the data holds. A hand-written rule that is not
  imported first vanishes at that moment: a lost deny widens what agents may do, and a lost allow stalls unattended
  runs on prompts. Importing before owning keeps every rule in effect.

## Rejected alternatives

### Own native files wholesale

At its best, rendering and checking are simplest: the tool writes the file and compares the file. It loses because it
destroys every key people set, such as the rules, hook and environment variable of the `acme/api` example in Context.

### Write only to the personal override file

At its best, nothing committed is touched and adoption cannot disturb a team. It loses because the file is personal
and usually untracked: the team, CI and reviewers never see what the tool rendered, and each person renders their own.

### A separate owned file that the native file includes

At its best, ownership is a whole file everywhere and no key is shared. It loses as a general rule because it depends
on the agent supporting inclusion, and Claude Code's project settings do not. Where the agent reads a directory, this
is the rule (point 1).

## Consequences

Costs:

- Preservation is as exact as the serializer's round trip of JSON and TOML formatting; formatting the serializer
  cannot reproduce is a residual risk.
- A hand edit to an owned key is drift and is overwritten on the next render; the change belongs in the source.
- People and the tool write the same file. A merge conflict in it is resolved by resolving the keys people own and
  rendering again.

Must be maintained:

- Round-trip tests for every native format, and the owned key set of every harness plugin
  ([0009](0009-agent-support.md)).

## Confirmation

- Fixtures carry foreign keys, unusual key order and TOML comments; after rendering, every byte outside the owned keys
  is unchanged.
- In a directory the agent reads, rendering creates or changes only the tool's own file.
- Fixtures with personal override files assert that the files are never written, never recorded in the manifest, and
  are opened only when an adoption imports them.
- A fixture whose owned key holds hand-written entries renders those entries after adoption.
