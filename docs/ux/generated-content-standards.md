# Generated-content standards

## Objective

**Problem.** A tool that writes into a repository shares its files with developers, reviewers, other tools and teammates
who never use it. When nobody can tell the tool's content from a person's, a hand edit is silently overwritten, or
someone's work is deleted with the tool's. A reviewer cannot judge a change hidden among lines that move on every run, a
convention only the tool knows disappears when it leaves, and a team that stops using it cannot get a working repository
back.

**Goal.** Anyone reading an adopted repository, with or without the tool, can tell which content the tool wrote, and
every change to that content, by the tool or by hand, is visible in review. When the tool leaves, the repository works,
has lost nothing a person wrote, and keeps the conventions the team chose to keep.

## Overview

These rules apply to anything harness writes into a repository.

## Kinds of generated content

| Kind | Example | How ownership is marked |
| --- | --- | --- |
| Owned whole file that allows comments | the wrapper `harnessw` (after its `#!` line), `.harness/wrapper.yaml`, `.codex/rules/harness.rules`, the harness skill `.agents/skills/harness/SKILL.md` (as a YAML comment right after the opening `---` of its front matter) | header line, plus the manifest |
| Owned whole file that allows no comments | `.harness/manifest.json` and other JSON files | the manifest |
| Seeded file | `.harness/config.yaml`, `.harness/policy.yaml`, `.harness/hooks.yaml`, the `.agents/memory/` skeleton | the manifest records it as seeded, with the hash it was created with: written once, then the repository's |
| Template file | a file a preset's template wrote, such as `.ralph-tui/config.toml` from the `ralph-tui` preset | nothing: seeded content the manifest does not record, written once and only where the file was absent, then the repository's |
| Managed block inside a human file | the [block ids](#block-ids) in `AGENTS.md`, `README.md`, `CLAUDE.md` and `.gitignore` | opening and closing markers, plus the manifest |
| Owned keys inside a native file | `permissions.{allow,ask,deny}`, harness hook entries (agent hooks, notification and context hooks) and rendered raw hook entries in `.claude/settings.json`; the equivalent keys and entries in `.codex/config.toml` (its `notify` key when harness renders it) and `.codex/hooks.json` | the manifest |
| Link | `.claude/skills/harness -> ../../.agents/skills/harness` | the manifest |
| State | the paths the profile sets (`state.dir`, `worktrees.dir`; under `.work/` by default), including the lifecycle log `lifecycle.jsonl` in `state.dir` | gitignored by managed blocks; never committed, never listed as content, never inside `.harness/` or `.agents/` |

The manifest (`.harness/manifest.json`) is the record of ownership. Content not listed in it is not harness's, whatever
it looks like. Why: a header or a marker can be copied into a file harness never wrote, and only the record says what
harness created.

Owned content is rendered and replaced on every render, and a hand edit to it is drift. Seeded content is written once
and then edited by people and agents. harness never re-renders it, and adds to the repository files only when a person
applies a preset (`harness preset apply`), and then only what they lack. Why: the seeded files hold the repository's
choices and knowledge, which harness cannot recompute.

A template is seeded content of its own kind: a whole file in a preset or in `scaffold`, rendered once with values from
the profile, written only when the file is absent, and from then on the repository's own file. harness never renders it
again and never removes it, and the manifest does not record it. Why: a template is usually a file another tool reads,
such as a loop runner's configuration, which the team then edits for that tool, and a record of where it came from
would be a record of which presets the repository applied
([configuration-standards.md](configuration-standards.md#presets)).

`harness scaffold` seeds the repository files and applies the presets of the detected stacks as it seeds them
([configuration-standards.md](configuration-standards.md#presets)). The `.agents/memory/` skeleton is seeded when the
memory module is enabled, by `harness scaffold` or by the first `harness render` after the module is enabled, and is
never re-rendered. The recorded creation hash is what lets undo (`harness remove`) tell an untouched skeleton from
content someone wrote ([ADR 0007](../adrs/0007-ownership.md)).

## Header

Owned files that allow comments carry exactly one header line, at the first position the format allows: the first
line; after the `#!` line in a script; right after the opening `---` of YAML front matter in a Markdown file such as
the harness skill. Why: the file must keep working for the program that reads it, so the header goes where that program
ignores it ([ADR 0007](../adrs/0007-ownership.md)).

Rendered files carry:

```text
# generated by harness (schema 1): edit .harness/<source> and run ./harnessw render
```

The pin `.harness/wrapper.yaml`, which no source renders, carries the operation that changes it:

```text
# generated by harness: change with ./harnessw wrapper --version <tag>
```

- The comment syntax adapts to the file type; the words do not, so a search for `generated by harness` finds every
  generated file.
- `<source>` names the repository file that produces this output, so a reader knows where to make the change.
- `manifest.json` is JSON and carries no header; the manifest itself is its record.
- The header carries the schema, never the binary version, date or user, so an upgrade or another author produces no
  diff.

## Managed blocks

A block opens with `harness:<id>` and closes with `/harness:<id>`, in the comment syntax of the file:

```markdown
<!-- harness:worktrees -->
Worktrees in this project are created in `.work/worktrees/<name>`, on branch `work/<name>`, from committed code.
<!-- /harness:worktrees -->
```

```gitignore
# harness:worktrees
/.work/worktrees/
# /harness:worktrees
```

- Markdown uses HTML comments (`<!-- harness:<id> -->` … `<!-- /harness:<id> -->`); `.gitignore`, YAML, TOML and
  shell files use `#` comments (`# harness:<id>` … `# /harness:<id>`). Why: markers in the file's own comment syntax
  never change how the file reads or parses.
- Block ids come from a documented, closed set. An id is never renamed except through a schema migration, because
  the manifest and the markers already committed in repositories refer to it.
- A file holds at most one block per id, so an id names exactly one place.
- harness owns everything between the markers and replaces it on render. Everything outside them is never read
  for writing, reordered or reformatted, because it belongs to whoever wrote it.
- A new block is appended at the end of the file. Once present, a block may be moved anywhere in the file by a person,
  and harness updates it where it is, so a team can arrange its own file.
- Block text comes only from the binary's templates and the repository's values: fixed sentences that state a
  convention with its configured values, tables, lists, one-line imports, and, in the `context` block, the text of the
  repository files and installed skills that `static` entries name. It is never free prose, opinion or knowledge,
  because the binary does only deterministic work ([ADR 0002](../adrs/0002-division-of-work.md)). No source
  outside the repository adds text to a block, because text there acts in every session while its source would sit
  where nobody working in the repository reads it ([ADR 0013](../adrs/0013-organization-content.md)).
- A `.gitignore` block omits any path the repository tracks, so ignoring never hides a committed file.
- Each module writes its own `.gitignore` entries, even when another module's entry already covers them (for example
  `/.work/` and `/.work/worktrees/`). Why: removing one module must never un-ignore another module's paths.

### Block classes

Every block belongs to one of two classes. Why: some of harness's text states facts that stay true without it, and some
only explains how to use it. Eject (`harness remove --eject`) must keep the first and must not leave the second behind
([ADR 0018](../adrs/0018-conventions.md)).

| Class | States | Mentions harness commands | On `harness remove --eject` |
| --- | --- | --- | --- |
| Convention block | facts that hold without harness: where things are, how work is tracked, what is permitted | never | markers removed, text kept |
| Tool block | how to use harness: its commands, its configuration, how it verifies and delivers | MAY, as `./harnessw <command>` | removed |

- A convention block MUST read correctly after eject, so it never names `harnessw`, `harness` or `.harness/`.
- The `policy` convention block describes the effective permissions in the native files, never `policy.yaml`, so it
  stays true after eject.

### The `AGENTS.md` budget

All harness blocks in `AGENTS.md` together stay within 40 lines by default (`blocks.agents_md_budget` in the profile),
and each block within 8 lines. Detail lives in `harness explain <topic>` and the harness skill. `harness doctor` reports
a note when the budget is exceeded, for example by a `lifecycle` block listing many actions. Why: agents read
`AGENTS.md` at the start of every session, so every line harness adds is paid in every session and dilutes the
repository's own instructions.

Static standing-context entries in the `context` block count against `context.budget`
([configuration-standards.md](configuration-standards.md#standing-context)), not against this budget, because their
length is the repository's own choice of directives.

### Block ids

Block ids are per module or convention. A module owns its blocks: turning the module off, or removing it with
`harness remove --module <name>`, removes them, because a block that states a convention nobody follows any more is
wrong. These blocks belong to every adopted repository, whatever the modules: `commands`, `layout`, `worktrees`,
`lifecycle` and `policy` in `AGENTS.md`; `commands` in `README.md` when it exists; `import` in `CLAUDE.md` unless it
already imports `@AGENTS.md`; `state` and `worktrees` in `.gitignore`. Why: worktrees and lifecycle events exist in
every adopted repository, so their conventions and conditions are stated in every one.

| File | Block id | Class | States |
| --- | --- | --- | --- |
| `AGENTS.md` | `commands` | tool | how to run things: `./harnessw <command>` |
| `AGENTS.md` | `layout` | tool | what `.harness/`, `.agents/` and the state directories hold |
| `AGENTS.md` | `verify` | tool | the repository's checks: lint, build, the test lanes and when each is required |
| `AGENTS.md` | `lifecycle` | tool | the conditions work meets before it leaves the clone, and each event's actions in order |
| `AGENTS.md` | `worktrees` | convention | where managed worktrees are created, on which branch, from which base |
| `AGENTS.md` | `policy` | convention | the effective permissions by decision, with the entries only Claude enforces |
| `AGENTS.md` | `memory` | convention | the index of `.agents/memory/` |
| `AGENTS.md` | `context` | convention | the standing directives declared `static`, inline or as pointer lines |
| `README.md` | `commands` | tool | how to run things, as in `AGENTS.md` |
| `CLAUDE.md` | `import` | convention | one line: `@AGENTS.md` |
| `.gitignore` | `state` | convention | `/<state.dir>/` |
| `.gitignore` | `worktrees` | convention | `/<worktrees.dir>/` |

### Conventions in blocks

Every convention a module establishes is written where people and other tools read it, so it remains true without
harness ([principles.md](principles.md#8-conventions-outlive-the-tool)):

- In `AGENTS.md`, as a convention block of plain-language statements: where worktrees are created, what is permitted,
  which directives always apply.
- For each path, in a managed block of `.gitignore`.
- The text names the configured value of each path, never a default the profile overrode, because a reader acts on
  what the text says.
- A block's text describes the convention as fact. It never says it is managed by harness beyond the marker, and it
  never depends on the binary to be true, so people, agents and the other tools in the repository can rely on it after
  harness is ejected.

## Owned keys in native files

Why: native files are shared with people and other tools, so harness owns the keys it renders and nothing else
([ADR 0008](../adrs/0008-native-configuration.md)).

- harness changes only the keys it owns. Every other key, its value, its order and its formatting are preserved byte
  for byte.
- Owned permission lists belong to harness entirely: an entry added there by hand is drift. To keep it, add it to
  `policy.yaml`. Adoption does this for entries that existed before harness. Why: two writers of one list cannot both be
  right after the next render. For example, `Bash(make lint *)` typed into `permissions.allow` in
  `.claude/settings.json` is drift, while `[make, lint]` under `shared.allow` in `policy.yaml` renders as that same rule
  for Claude Code and as a `prefix_rule` for Codex.
- Hook entries are owned one by one. harness owns the entries that run the harness hook command (agent hooks,
  notification hooks such as Claude Code's `Notification` and `Stop`, and the hooks that inject standing context) and
  the native entries it renders from `raw` declarations in `hooks.yaml`. Hooks added by other tools or by hand are
  preserved where they are. Codex's `notify` setting in `.codex/config.toml` is owned only when it is absent or
  already the harness hook command, because it holds one program. When a repository has its own `notify` program,
  harness leaves it in place and does not render `turn_end` for Codex, and `harness doctor` notes it.
- A raw hook renders as a native entry that runs its own command, not the harness hook command, so it keeps working
  without the binary. It is recorded in the manifest as owned: a hand edit to it is drift, `harness remove` removes
  it, and `harness remove --eject` keeps it as hand-owned.
- A hook entry that runs the harness hook command calls the wrapper at the repository root with `--no-download`
  (`harnessw --no-download hook <event>`), so it never downloads.
- Local override files (`.claude/settings.local.json`, `*.local.toml`) are never written or listed. They are read only
  by `harness scaffold --import local`, because they are personal and usually untracked.

## Determinism

Why: two teammates rendering the same commit must produce the same diff, or every render is noise in review.

- The same repository files and pinned release produce the same bytes on every supported operating system, with no
  network access and an empty per-user cache, because nothing else is an input
  ([ADR 0013](../adrs/0013-organization-content.md)). A new release may render differently, the harness skill and
  `harnessw` included. `harness wrapper --version <tag>` renders the repository with the new release in the same change
  as the pin, so every such difference reaches review in the upgrade's diff
  ([compatibility-standards.md](compatibility-standards.md#rendered-output-across-releases),
  [ADR 0017](../adrs/0017-engine-version.md)). Rendered content never records the release that rendered it
  ([ADR 0005](../adrs/0005-schema-compatibility.md)).
- Generated content contains no timestamps, binary versions, usernames, hostnames, absolute paths or random values.
- Order follows the source file's declaration order where one exists, and a documented sort otherwise.
- Text files are UTF-8, use LF line endings and end with one newline.

## Idempotency and drift

- Running `harness render` twice in a row changes nothing the second time, so render is safe to run whenever in doubt.
- A hand edit to owned content is drift. `harness render --check` exits 2 and names each file, block and key that
  drifted. `harness render` replaces the drifted content and lists what it replaced; `--dry-run` shows it first. Why:
  the hand edit belongs in the source, where the next render keeps it.
- Drift is detected by comparing against the manifest's hashes, so it is reported even when the repository files
  have not changed.
- The pinned release runs the drift check, and no owned file is exempt, the harness skill included. The pinned
  release renders the skill the repository has, because upgrades render in their own change. A binary other than the
  pinned release (`HARNESS_BINARY`) gets a `harness doctor` note naming both versions, because its drift results may
  differ. Why: an exemption would hide exactly the hand edits the check exists to catch
  ([ADR 0007](../adrs/0007-ownership.md)).

## Review

- Generated content is committed so that every change reaches review as a diff and a clone works without the binary.
- A change to one repository file produces the smallest rendered diff that expresses it; unrelated lines do not move,
  so a reviewer reads only the effect of the change.
- The binary commits in a repository in two cases only: `harness worker --commit` commits a task's verified work in the
  checkout it ran in, and `harness fleet <command> --deliver`, which runs a command across a listed set of repositories,
  commits the tool-owned changes on a new local branch `<branch_prefix><command>`, cut from the fetched default branch,
  then fires that repository's `deliver` actions. Any other commit, push or pull request is a declared action. Why: a
  commit is the start of delivery, and delivery is the repository's decision. The two exceptions commit only work the
  binary itself verified or rendered, in a checkout its caller chose for that work or on a branch it created.
- Reviewers judge the source change (`policy.yaml`, `hooks.yaml`, `config.yaml`); the rendered diff shows its effect
  on each agent.
- `harness preset apply` writes its additions and renders them in the same change, so the entries, the files its
  templates wrote and their rendered effect reach review as one diff, and the drift check passes on it.
- A version upgrade reaches review as one diff: `wrapper.yaml` (the version and its checksums) and every artefact the
  new release renders differently, `harnessw` and the harness skill included. The checksums were written only after
  the running binary verified the release's signed checksum file, so reviewers confirm that the version is the one
  they expect ([compatibility-standards.md](compatibility-standards.md#release-trust)).

## Removal

Removal has two modes. Undo takes harness and what it wrote out of the repository. Eject stops harness managing the
repository and keeps what it wrote. Reference: [`harness remove`](../features/remove.md). Why: a trial adoption must be
fully reversible, and a team that leaves harness must keep the conventions, permissions and hooks it came to rely on.

Both modes:

- act only on what the manifest lists, and never delete any other file: a file the manifest does not record, even
  inside `.harness/`, is listed and left, and `.harness/` is deleted once
  nothing else is in it, so what removal touches is known before it runs ([ADR 0007](../adrs/0007-ownership.md));
- refuse a listed owned file whose content no longer matches its recorded hash (it was edited by hand), name it, and
  exit 3 unless `--force` is given, because the edit may be someone's only copy of their work;
- accept `--dry-run`, which lists every path, block, entry and key they would change;
- leave text and keys outside owned content byte for byte, so `git status` afterwards shows only changes to content
  harness owned or seeded.

### Undo: `harness remove`

`harness remove` (or `harness remove --module <name>` for one module) returns the repository to a working state
without harness:

- It deletes the owned files and links, removes every managed block including its markers and text, and removes owned
  keys and entries from native files. Without `--module`, it also deletes `harnessw`, `wrapper.yaml`, the manifest and
  the seeded repository files in `.harness/`, listing the seeded files first, and then `.harness/` once nothing else
  is in it. Their history stays in version control. Entries a preset added are part of those files and go with them.
  Why: the seeded files have no meaning without harness.
- A seeded file outside `.harness/` (the `.agents/memory/` skeleton) is deleted only while it still matches the hash
  the manifest recorded when it was seeded; an edited one is kept and listed. Why: an untouched skeleton is harness
  output, while a file someone wrote into is the repository's knowledge, which harness never removes
  ([ADR 0007](../adrs/0007-ownership.md)).
- Permission entries and hooks that adoption imported from project-scope native files are written back as plain
  native settings, so the agents keep behaving as before. Entries imported from the `local` or `user` scope are not
  written back, because their source files were never changed and still hold them.
- State directories are left in place and listed in the output, for a person to delete, because worktrees can hold
  uncommitted work. Their `.gitignore` blocks are gone, so they show as untracked until deleted.

### Eject: `harness remove --eject`

`harness remove --eject` stops managing the repository and keeps the result as the repository's own content:

- It strips the markers of every convention block and keeps its text, and keeps the `.gitignore` entries, so the
  conventions stay written ([principles.md](principles.md#8-conventions-outlive-the-tool)).
- It removes every tool block, so no instruction names a command that no longer exists.
- It keeps rendered native files and owned keys as hand-owned content, removing their generated-file headers.
- It removes hook entries that call harness, and permission entries that name a harness command in any rendered
  spelling, and lists them; raw hooks keep working. Why: they name a program that no longer exists in the repository.
- It removes the harness skill and its links, because the skill teaches commands that no longer exist.
- It deletes `harnessw` and the owned and seeded files in `.harness/`, the manifest included, then `.harness/` once
  nothing else is in it.
- It keeps the `.agents/memory/` skeleton and whatever people wrote into it, because it is the repository's knowledge.

After eject, the repository keeps its conventions, permissions, hooks and static standing directives, and every file a
template wrote. A tool a preset wired keeps its own configuration; an entry in it that runs `./harnessw` is the
repository's to change.
