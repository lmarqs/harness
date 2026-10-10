# Standing context: `context` in `.cook/config.yaml`

## Objective

**Problem.** A repository keeps guidance its coding agents should follow, such as a coding standard, a checklist for
database migrations or rules for time zones, but a coding agent loads that guidance only when its model judges it
relevant. Developers retype the same requests at the start of their sessions, and guidance nobody remembers to ask for
is not followed. Writing all of it into the instructions every coding agent reads instead fills every session's
context, mostly with guidance that does not apply to the task at hand.

**Goal.** Known guidance reaches every session where it applies without anyone asking for it, and adds nothing to the
sessions where it does not.

## Overview

Standing context is guidance a repository declares once, as a skill or a file, that `the-cook` puts into a
[worker](workers.md)'s context whenever a declared trigger matches: every session, the first time the worker reads or
edits a matching file, or when the person's message contains a listed word. Worker hooks deliver the entries; an entry
declared `static: true` is written into `AGENTS.md` instead, so it reaches every worker that reads `AGENTS.md` without
the binary.

The triggers are deterministic: the same session, files and words give the same context on every machine. Entries come
only from files and skills committed in the repository, and nothing is fetched when a hook runs.

Commands are named `the-cook <command>` on this page. The examples run them through the repository's wrapper,
`./cook <command>`, from the root of a checkout; with a global install, `the-cook <command>` is equivalent
([wrapper](wrapper.md)).

## When to use it

| You want | Use |
| --- | --- |
| A coding-guidelines skill applied in every session, without invoking it | an `always` entry |
| A SQL review skill applied whenever the worker touches migrations or `.sql` files | a `when` entry with `paths` |
| A timezone skill applied when the person mentions DST or time zones | a `when` entry with `prompt` |
| Directives only attempts receive, such as "never touch the shared database" | an `always` entry with `for: [unattended]` |
| A directive that must hold even on a machine without the pinned binary, or after `the-cook remove --eject` | an `always` entry with `static: true` |
| A skill a coding agent loads only when it judges it relevant | nothing here: install the skill and let the coding agent match its description ([skills](skills.md)) |
| Repository prose a person writes and maintains: architecture, conventions, commands | `AGENTS.md`, outside the managed blocks |
| A formatter or linter run after each edit | a `post_edit` hook ([hooks](hooks.md)) |
| A rule that allows or denies commands | the [policy](policy.md); context never grants or removes a permission |

Hook delivery needs `modules.hooks: true` in the [profile](profile.md). Static entries do not: they are part of
`AGENTS.md`.

## How it works

### Entries and triggers

Each entry names one source, a skill installed under `.agents/skills/<name>/` or a repository file, and one trigger:

| Trigger | Fires | Injected | Default delivery |
| --- | --- | --- | --- |
| `always` | at session start, and again each time the worker fires its session-start event in the same session | every time it fires | `content`: the text itself |
| `when.paths` | the first time in the session the worker reads or edits a file that matches one of the globs | once per session | `pointer`: one line naming the file to read |
| `when.prompt` | the first time in the session a message from the person contains one of the words or phrases, compared case-insensitively as whole words | once per session | `pointer` |

A `pointer` is one line. For a skill:
`Apply the timezone-handling skill now: read .agents/skills/timezone-handling/SKILL.md`. For a file:
`Read .agents/directives/house-rules.md now and apply it.` A `content` entry is the skill's `SKILL.md` body without its
front matter, or the file as written.

Every injected entry starts with one line that names it and what triggered it, so the worker and a person reading the
transcript see why it is there:

```text
the-cook context: sql-quality-check (path src/migration/1700000000000-add-invoice-index.ts)
```

`the-cook` records which `when` entries a session has received in `<state.dir>/context/<session-id>.json` (default
`.work/context/`), so each one is injected once per session. That file is ignored state.

### Roles

`for` limits an entry to roles: `interactive` for every session a person starts, `unattended` for
[attempts](attempts.md#the-attempt-contract), the unattended runs `the-cook attempt` and
[`attempt` actions](lifecycle.md#action-kinds) start, one per task. The default is both. An unattended session is one
started with `COOK_ATTEMPT=1`, which `the-cook attempt` and `attempt` actions set for the worker they start; every
other session is interactive.

For an attempt, the declarations and every file they name are read from its base commit, the head commit of the
checkout when `the-cook attempt` started, through the version-control ingredient, not from the worker's writable tree,
so a worker cannot change its own instructions between attempts ([safety](safety.md#standing-context)).

### Delivery per worker

`the-cook render` writes one hook entry per event the declarations need; `the-cook hook` decides what to inject. Which
native event carries each trigger, and where in the payload the file paths and the message are, comes from the
worker's [ingredient](workers.md#the-worker-contract), which maps the payload onto a neutral event
([payload normalisation](hooks.md#payload-normalisation)). A worker ingredient maps each trigger to the native event
that carries it:

| Trigger | Native event the worker ingredient maps it to |
| --- | --- |
| `always` | the program's session-start event; where the program fires it again after a compaction, always-on content survives the compaction |
| `when.paths` | the event after a tool reads, edits or patches files, on the file paths the payload names |
| `when.prompt` | the event on the person's message |

The text reaches the worker as additional context of that event. A trigger the ingredient maps to no native event gets
nothing rendered for that worker, and [`the-cook doctor`](doctor.md) reports the gap as a note. A worker whose
ingredient declares no hook payload mapping receives no hook-delivered entry at all. A program that reads files
through shell commands names no file to match, so its sessions fire a `paths` entry only when they edit a matching
file.

In an attempt, the "person's message" is the task prompt the worker receives: what the loop runner passed to
`the-cook attempt`, or an `attempt` action's prompt.

### Static entries

An `always` entry with `static: true` is not injected by a hook. `the-cook render` writes it into the `context` block of
`AGENTS.md`, as its content or as its pointer line. Each worker reads `AGENTS.md` as its ingredient's instructions item
says, directly or through an `import` block the ingredient renders in the program's own instructions file, and a
machine where the pinned binary is not cached gets it like every other line of `AGENTS.md`. A worker whose ingredient
declares no instructions item gets static entries only if its program reads `AGENTS.md` on its own.

```markdown
<!-- cook:context -->
Apply the coding-guidelines skill to every change: read `.agents/skills/coding-guidelines/SKILL.md`.
<!-- /cook:context -->
```

The `context` block is a convention block: its text never names a `the-cook` command, so after
[`the-cook remove --eject`](remove.md#eject) the markers go and the directive stays. A static entry reaches every
session that reads `AGENTS.md`, so it cannot be limited with `for`. A static entry with `for` narrower than both roles
is a validation error.

### Sources and trust

| Source | Rule |
| --- | --- |
| `skill: <name>` | `.agents/skills/<name>/SKILL.md` must exist; `the-cook doctor` reports a skill named here that does not exist as a finding. The skill is delivered as the file committed there; an attempt reads it from its base commit |
| `file: <path>` | a path inside the repository, relative to its root. Not inside the state directories. An uncommitted file reaches interactive sessions only, because attempts read from the base commit |

Nothing is downloaded or fetched when a hook runs. A skill reaches the repository as a commit under `.agents/skills/`,
whether a person wrote it or installed it with a tool of the team's choice ([shared skills](skills.md#shared-skills)),
and is reviewed like any other change.

### Budget

`context.budget` caps the lines of always-on content one session receives, per role: `content` entries count their
lines, pointer lines count one, and static entries count too. `the-cook doctor` reports a note when a role exceeds the
budget, listing every entry with its line count. Injection does not stop at the budget. The note is the signal to trim,
move an entry to a trigger, or switch it to a pointer.

### When the binary is absent

Hook-delivered context is an implicit entry point: on a machine where the pinned release is not cached, hooks do
nothing beyond one stderr line, and no hook entry is injected. The session-start hook then adds one line to the
session instead: `the-cook v0.9.0 is not cached on this machine; run ./cook version once to fetch it.`
([hooks](hooks.md#the-missing-binary-guard)). Declare as `static` the directives a session must never miss.

`COOK_NO_CONTEXT=1` turns injection off for every process started from that environment, for debugging a session
without standing context. Static entries stay, because they are part of `AGENTS.md`.

The reasoning behind this design is in [ADR 0020](../adrs/0020-standing-context.md).

## Reference

### `context`

```yaml
context:
  always:
    - skill: coding-guidelines
    - file: .agents/directives/unattended-rules.md
      for: [unattended]
  when:
    - paths: ["**/*.sql", "src/migration/**"]
      skill: sql-quality-check
    - prompt: [timezone, DST, "time zone"]
      skill: timezone-handling
  budget: 120
```

| Field | Type | Default | Meaning |
| --- | --- | --- | --- |
| `context.always` | list of entries | `[]` | Entries injected at every session start |
| `context.when` | list of entries | `[]` | Entries injected once per session when their trigger matches |
| `context.budget` | integer | `120` | Lines of always-on content per session and role before `doctor` reports a note |
| `<entry>.skill` | skill name | one of `skill`, `file` | A skill installed at `.agents/skills/<name>/` |
| `<entry>.file` | repository path | one of `skill`, `file` | A file in the repository |
| `<entry>.for` | list of `interactive`, `unattended` | both | The roles the entry reaches |
| `<entry>.inject` | `content` \| `pointer` | `content` for `always`, `pointer` for `when` | The text itself, or one line naming the file to read |
| `always[].static` | boolean | `false` | Write the entry into the `context` block of `AGENTS.md` instead of injecting it; requires `for` to cover both roles |
| `when[].paths` | list of globs | one of `paths`, `prompt` | Repository-relative globs matched against the files the worker reads or edits |
| `when[].prompt` | list of strings | one of `paths`, `prompt` | Words and phrases matched against the person's message, case-insensitively, as whole words |

Validation errors (exit 1 from `render`, `doctor` and `context show`): an entry with both or neither of `skill` and
`file`; a `when` entry with both or neither of `paths` and `prompt`; `static` on a `when` entry, or with a narrower
`for`; a `file` outside the repository or inside a state directory; an unknown role.

Every entry a session receives is declared in this section of the repository's own profile
([ADR 0013](../adrs/0013-organization-content.md)). Directives an organization wants in all its repositories are written
into each profile, typically by a coding agent following a skill the organization publishes or by applying a station
from the organization's cookbook, and reviewed there like any other change. For example, a coding agent following
acme's skill adds the same `always` entry and directive file to `acme/api` and to `acme/web`, and each repository
reviews and merges that change on its own.

### `the-cook context show`

```text
the-cook context show [--role interactive|unattended] [--path <file>]... [--prompt <text>] [--json]
```

Prints what a session would receive and why: every `always` entry for the role, and the `when` entries the given paths
and prompt would trigger, each with its source, delivery, trigger and line count, followed by the role's always-on total
against the budget. Entries that would not be injected, such as a skill that does not exist, are listed with the reason.
It reads the same files the hooks read and writes nothing.

| Flag | Default | Meaning |
| --- | --- | --- |
| `--role interactive\|unattended` | `interactive` | The role to evaluate. `unattended` reads the declarations and files from the checkout's head commit, as an attempt reads them from its base commit |
| `--path <file>` | none | A file the session reads or edits. Repeatable |
| `--prompt <text>` | none | A message from the person |
| `--json` | off | Print the result as JSON |

| Exit code | Meaning |
| --- | --- |
| 0 | Printed |
| 1 | Error: invalid `context` declarations, a missing skill or file, an unknown role |

### Environment

| Variable | Effect |
| --- | --- |
| `COOK_NO_CONTEXT=1` | Hooks inject nothing in this environment and every process started from it |
| `COOK_ATTEMPT=1` | Set by `the-cook attempt` and by `attempt` actions for the worker they start; selects the `unattended` role |

Files read: `.cook/config.yaml`, the skills and files the entries name, the hook payload, and for attempts the same
files at the base commit. Files written: `<state.dir>/context/<session-id>.json`; by `render`, the hook entries the
worker ingredients render in the programs' native files, and the `context` block of `AGENTS.md`.

## Examples

### A coding-guidelines skill in every session

```yaml
# .cook/config.yaml
context:
  always:
    - skill: coding-guidelines
```

Every session, interactive or unattended, starts with the skill's instructions, and receives them again whenever its
program fires the session-start event after a compaction. Nobody types the skill's name.

### A SQL skill when a worker touches migrations

```yaml
context:
  when:
    - paths: ["**/*.sql", "src/migration/**"]
      skill: sql-quality-check
```

The first time in a session the worker reads or edits a migration, its next step carries one line:

```text
the-cook context: sql-quality-check (path src/migration/1700000000000-add-invoice-index.ts)
Apply the sql-quality-check skill now: read .agents/skills/sql-quality-check/SKILL.md
```

### A timezone skill when the person mentions it

```yaml
context:
  when:
    - prompt: [timezone, DST, "time zone", UTC]
      skill: timezone-handling
```

"Fix the DST bug in the reminder job" triggers it; "update the reminder copy" does not.

### Directives for attempts only

```yaml
context:
  always:
    - file: .agents/directives/unattended-rules.md
      for: [unattended]
```

```markdown
<!-- .agents/directives/unattended-rules.md -->
You run unattended. Never reset or seed the shared database; e2e tests create their own fixtures.
When a task needs a decision only a person can make, stop and say so in one line.
```

Interactive sessions never see these lines. Attempts read them from their base commit.

### A must-have that holds without the binary

```yaml
context:
  always:
    - file: .agents/directives/house-rules.md
      inject: pointer
      static: true
```

`the-cook render` writes the pointer into `AGENTS.md`:

```markdown
<!-- cook:context -->
Read `.agents/directives/house-rules.md` and apply it to every change.
<!-- /cook:context -->
```

A teammate who never runs `./cook` still gets it, and it stays after an eject.

### Previewing a session

```sh
./cook context show --path src/migration/1700000000000-add-invoice-index.ts --prompt "fix the DST bug in reminders"
```

```text
role interactive
always  coding-guidelines    content  .agents/skills/coding-guidelines/SKILL.md        61 lines
always  house-rules.md       static   AGENTS.md, block context                         1 line
path    sql-quality-check    pointer  src/migration/1700000000000-add-invoice-index.ts matches src/migration/**
prompt  timezone-handling    pointer  "DST"
always-on: 62 of 120 lines
```

## Guidelines

- Do keep always-on content short. Every line is read at the start of every session and competes with the task for
  the worker's attention; a long skill is better as a pointer, or behind a `paths` or `prompt` trigger.
- Do prefer `paths` triggers to `prompt` triggers. A file the worker touches is evidence the guidance applies; a word
  in a message is a guess.
- Do choose specific prompt words. `date` matches far more messages than `DST` or `time zone`.
- Do declare as `static` only what every session must receive even without the binary; static entries cost tokens in
  every session, for every role.
- Do write skills so they read correctly when injected: short, self-contained, with their instructions first
  ([skills standards](../ux/skills-standards.md)).
- Do review `context` changes like policy changes: an entry steers every worker session in the repository.
- Do run `./cook context show` after changing an entry, with the paths and words you expect to trigger it.
- Don't inject files that untrusted text can reach, such as generated reports or copies of issue text; injected
  content is read as instructions.
- Don't use context to grant or forbid commands; only the [policy](policy.md) decides permissions.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| A skill is never injected; `doctor` reports it missing | No `.agents/skills/<name>/SKILL.md` exists for the name in `context`, or it is not committed and the session is an attempt | Install or write the skill, commit it, or fix the name |
| No standing context; the session shows `the-cook v0.9.0 is not cached on this machine; run ./cook version once to fetch it.` | Hooks never download, and the pinned release is not cached here | Run `./cook version` once; declare must-haves `static: true` |
| A `paths` entry never fires for one worker | The program read the file through a shell command, which names no file | Rely on edits for that worker, or make the entry `always` |
| A worker never receives any hook-delivered entry | Its ingredient declares no hook payload mapping | Expected for such a program; declare what it must receive as `static`, if it reads `AGENTS.md` |
| A `prompt` entry fires on unrelated messages | A word that is too common | Use more specific words or phrases, or a `paths` trigger |
| A coding agent loads a skill twice | The skill was injected and the coding agent also matched its description and loaded it | Inject it as a `pointer`, or narrow the skill's description |
| An attempt ignores a new directive | The directive is not committed; attempts read from their base commit | Commit it, then start the run |
| `doctor` notes the budget is exceeded | Always-on content for a role is over `context.budget` | Shorten entries, switch them to `pointer`, or move them to a trigger |
| Nothing is injected anywhere | `COOK_NO_CONTEXT=1` is set, or `modules.hooks` is off | Unset the variable; turn the module on and render |
| `render` exits 1: static entry with `for` | A static entry reaches every session that reads `AGENTS.md` | Remove `for`, or remove `static` and let the hook deliver it |
