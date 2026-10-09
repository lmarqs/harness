# Standing context: `context` in `.harness/config.yaml`

Standing context is guidance a repository declares once, as a skill or a file, that `harness` puts into an agent's
context whenever a declared trigger matches: every session, the first time the agent reads or edits a matching file,
or when the person's message contains a listed word. Nobody has to remember to invoke the skill, and the agent does
not have to recognise from a skill's description that it applies. Agent hooks deliver the entries; an entry declared
`static: true` is written into `AGENTS.md` instead, so it reaches agents without the binary.

The triggers are deterministic: the same session, files and words give the same context on every machine. Entries
come only from files committed in the repository and from installed skills that match their lock, and nothing is
fetched when a hook runs.

Commands are named `harness <command>` on this page. The examples run them through the repository's wrapper,
`./harnessw <command>`, from the root of a checkout; with a global install, `harness <command>` is equivalent
([wrapper](wrapper.md)).

## When to use it

| You want | Use |
| --- | --- |
| A coding-guidelines skill applied in every session, without invoking it | an `always` entry |
| A SQL review skill applied whenever the agent touches migrations or `.sql` files | a `when` entry with `paths` |
| A timezone skill applied when the person mentions DST or time zones | a `when` entry with `prompt` |
| Directives only unattended workers receive, such as "never touch the shared database" | an `always` entry with `for: [worker]` |
| A directive that must hold even on a machine without the pinned binary, or after `harness remove --eject` | an `always` entry with `static: true` |
| A skill the agent loads only when it judges it relevant | nothing here: install the skill and let the agent match its description ([skills](skills.md)) |
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
| `always` | at session start, and again each time the agent fires its session-start event in the same session | every time it fires | `content`: the text itself |
| `when.paths` | the first time in the session the agent reads or edits a file that matches one of the globs | once per session | `pointer`: one line naming the file to read |
| `when.prompt` | the first time in the session a message from the person contains one of the words or phrases, compared case-insensitively as whole words | once per session | `pointer` |

A `pointer` is one line. For a skill:
`Apply the timezone-handling skill now: read .agents/skills/timezone-handling/SKILL.md`. For a file:
`Read .agents/directives/house-rules.md now and apply it.` A `content` entry is the skill's `SKILL.md` body without its
front matter, or the file as written.

Every injected entry starts with one line that names it and what triggered it, so the agent and a person reading the
transcript see why it is there:

```text
harness context: sql-quality-check (path src/migration/1700000000000-add-invoice-index.ts)
```

`harness` records which `when` entries a session has received in `<state.dir>/context/<session-id>.json` (default
`.work/context/`), so each one is injected once per session. That file is ignored state.

### Roles

`for` limits an entry to roles: `interactive` for every session a person starts, `worker` for Ralph workers and
[`agent` actions](lifecycle.md#action-kinds). The default is both. A worker session is the one the worker launcher
starts with `HARNESS_WORKER=1`; every other session is interactive.

For a worker, the declarations and every file they name are read from the session's base commit, not from the
worker's writable tree, so a worker cannot change its own instructions between attempts
([safety](safety.md#standing-context)).

### Delivery per agent

`harness render` writes one hook entry per event the declarations need; `harness hook` decides what to inject.

| Trigger | Claude Code 2.1.x | Codex CLI 0.16x |
| --- | --- | --- |
| `always` | `SessionStart`, which fires on startup, resume, `/clear` and after compaction, so always-on content survives a compaction | `SessionStart` |
| `when.paths` | `PostToolUse` after `Read`, `Edit`, `Write` and `MultiEdit`, on the file path the tool names | `PostToolUse` after `apply_patch`, on the files the patch touches |
| `when.prompt` | `UserPromptSubmit`, on the person's message | `UserPromptSubmit`, on the person's message |

The text reaches the agent as additional context of that event. An agent that has no event for a trigger gets nothing
rendered for it, and [`harness doctor`](doctor.md) reports the gap as a note. Codex reads files through shell commands,
which name no file to match, so a Codex session fires a `paths` entry only when it patches a matching file.

In a worker session, the "person's message" is the task prompt the launcher sends.

### Static entries

An `always` entry with `static: true` is not injected by a hook. `harness render` writes it into the `context` block
of `AGENTS.md`, as its content or as its pointer line. Claude Code reads it through the `import` block of `CLAUDE.md`,
Codex reads `AGENTS.md` directly, and a machine where the pinned binary is not cached gets it like every other line of
`AGENTS.md`.

```markdown
<!-- harness:context -->
Apply the karpathy-guidelines skill to every change: read `.agents/skills/karpathy-guidelines/SKILL.md`.
<!-- /harness:context -->
```

The `context` block is a convention block: its text never names a `harness` command, so after
[`harness remove --eject`](remove.md#eject) the markers go and the directive stays. A static entry reaches every
session that reads `AGENTS.md`, so it cannot be limited with `for`: a static entry with `for` narrower than both roles
is a validation error.

### Sources and trust

| Source | Rule |
| --- | --- |
| `skill: <name>` | `.agents/skills/<name>/SKILL.md` must exist. When `skills-lock.json` lists the skill, its content must match the hash the lock records; a skill that does not match is not injected, and `harness doctor` reports it as a finding |
| `file: <path>` | a path inside the repository, relative to its root. Not inside the state directories. An uncommitted file reaches interactive sessions only, because workers read from the base commit |

Nothing is downloaded or fetched when a hook runs. A skill reaches the repository through the
[`skills` CLI](skills.md#declared-skill-sources) or a commit, both reviewed like any other change.

### Budget

`context.budget` caps the lines of always-on content one session receives, per role: `content` entries count their
lines, pointer lines count one, and static entries count too. `harness doctor` reports a note when a role exceeds the
budget, listing every entry with its line count. Injection does not stop at the budget; the note is the signal to
trim, move an entry to a trigger, or switch it to a pointer.

### When the binary is absent

Hook-delivered context is an implicit entry point: on a machine where the pinned release is not cached, hooks do
nothing beyond one stderr line, and no hook entry is injected. The session-start hook then adds one line to the
session instead: `harness v0.9.0 is not cached on this machine; run ./harnessw version once to fetch it.`
([hooks](hooks.md#the-missing-binary-guard)). Declare as `static` the directives a session must never miss.

`HARNESS_NO_CONTEXT=1` turns injection off for every process started from that environment, for debugging a session
without standing context. Static entries stay, because they are part of `AGENTS.md`.

The reasoning behind this design is in [ADR 0020](../adrs/0020-standing-context.md).

## Reference

### `context`

```yaml
context:
  always:
    - skill: karpathy-guidelines
    - file: .agents/directives/worker-rules.md
      for: [worker]
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
| `<entry>.for` | list of `interactive`, `worker` | both | The roles the entry reaches |
| `<entry>.inject` | `content` \| `pointer` | `content` for `always`, `pointer` for `when` | The text itself, or one line naming the file to read |
| `always[].static` | boolean | `false` | Write the entry into the `context` block of `AGENTS.md` instead of injecting it; requires `for` to cover both roles |
| `when[].paths` | list of globs | one of `paths`, `prompt` | Repository-relative globs matched against the files the agent reads or edits |
| `when[].prompt` | list of strings | one of `paths`, `prompt` | Words and phrases matched against the person's message, case-insensitively, as whole words |

Validation errors (exit 1 from `render`, `doctor` and `context show`): an entry with both or neither of `skill` and
`file`; a `when` entry with both or neither of `paths` and `prompt`; `static` on a `when` entry, or with a narrower
`for`; a `file` outside the repository or inside a state directory; an unknown role.

Every entry a session receives is declared in this section of the repository's own profile
([ADR 0013](../adrs/0013-organization-content.md)). Directives an organization wants in all its repositories are
written into each profile, typically by an agent following a skill the organization publishes, and reviewed there
like any other change.

### `harness context show`

```text
harness context show [--role interactive|worker] [--path <file>]... [--prompt <text>] [--json]
```

Prints what a session would receive and why: every `always` entry for the role, and the `when` entries the given paths
and prompt would trigger, each with its source, delivery, trigger and line count, followed by the role's always-on
total against the budget. Entries that would not be injected, such as a skill that does not match its lock, are listed
with the reason. It reads the same files the hooks read and writes nothing.

| Flag | Default | Meaning |
| --- | --- | --- |
| `--role interactive\|worker` | `interactive` | The role to evaluate. `worker` reads the declarations and files from `HEAD`, as a worker reads them from its base commit |
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
| `HARNESS_NO_CONTEXT=1` | Hooks inject nothing in this environment and every process started from it |
| `HARNESS_WORKER=1` | Set by the worker launcher; selects the `worker` role |

Files read: `.harness/config.yaml`, the skills and files the entries name, `skills-lock.json`, the hook payload, and
for workers the same files at the base commit. Files written: `<state.dir>/context/<session-id>.json`; by `render`, the
hook entries in the native agent files and the `context` block of `AGENTS.md`.

## Examples

### A coding-guidelines skill in every session

```yaml
# .harness/config.yaml
context:
  always:
    - skill: karpathy-guidelines
```

Every session, interactive or worker, starts with the skill's instructions, and Claude Code receives them again after
each compaction. Nobody types the skill's name.

### A SQL skill when the agent touches migrations

```yaml
context:
  when:
    - paths: ["**/*.sql", "src/migration/**"]
      skill: sql-quality-check
```

The first time in a session the agent reads or edits a migration, its next step carries one line:

```text
harness context: sql-quality-check (path src/migration/1700000000000-add-invoice-index.ts)
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

### Directives for workers only

```yaml
context:
  always:
    - file: .agents/directives/worker-rules.md
      for: [worker]
```

```markdown
<!-- .agents/directives/worker-rules.md -->
You run unattended. Never reset or seed the shared database; e2e tests create their own fixtures.
When a task needs a decision only a person can make, stop and say so in one line.
```

Interactive sessions never see these lines. Workers read them from the run's base commit.

### A must-have that holds without the binary

```yaml
context:
  always:
    - file: .agents/directives/house-rules.md
      inject: pointer
      static: true
```

`harness render` writes the pointer into `AGENTS.md`:

```markdown
<!-- harness:context -->
Read `.agents/directives/house-rules.md` and apply it to every change.
<!-- /harness:context -->
```

A teammate who never runs `./harnessw` still gets it, and it stays after an eject.

### Previewing a session

```sh
./harnessw context show --path src/migration/1700000000000-add-invoice-index.ts --prompt "fix the DST bug in reminders"
```

```text
role interactive
always  karpathy-guidelines  content  .agents/skills/karpathy-guidelines/SKILL.md      61 lines
always  house-rules.md       static   AGENTS.md, block context                         1 line
path    sql-quality-check    pointer  src/migration/1700000000000-add-invoice-index.ts matches src/migration/**
prompt  timezone-handling    pointer  "DST"
always-on: 62 of 120 lines
```

## Guidelines

- Do keep always-on content short. Every line is read at the start of every session and competes with the task for
  the agent's attention; a long skill is better as a pointer, or behind a `paths` or `prompt` trigger.
- Do prefer `paths` triggers to `prompt` triggers. A file the agent touches is evidence the guidance applies; a word
  in a message is a guess.
- Do choose specific prompt words. `date` matches far more messages than `DST` or `time zone`.
- Do declare as `static` only what every session must receive even without the binary; static entries cost tokens in
  every session, for every role.
- Do write skills so they read correctly when injected: short, self-contained, with their instructions first
  ([skills standards](../ux/skills-standards.md)).
- Do review `context` changes like policy changes: an entry steers every agent session in the repository.
- Do run `./harnessw context show` after changing an entry, with the paths and words you expect to trigger it.
- Don't inject files that untrusted text can reach, such as generated reports or copies of issue text; injected
  content is read as instructions.
- Don't use context to grant or forbid commands; only the [policy](policy.md) decides permissions.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| A skill is never injected; `doctor` reports a lock mismatch | The installed skill differs from the hash in `skills-lock.json`: edited by hand, or installed outside the `skills` CLI | Reinstall it with `./harnessw skills sync`, or update it through the `skills` CLI so the lock matches |
| No standing context; the session shows `harness v0.9.0 is not cached on this machine; run ./harnessw version once to fetch it.` | Hooks never download, and the pinned release is not cached here | Run `./harnessw version` once; declare must-haves `static: true` |
| A `paths` entry never fires in Codex | Codex read the file through a shell command, which names no file | Rely on edits for Codex, or make the entry `always` |
| A `prompt` entry fires on unrelated messages | A word that is too common | Use more specific words or phrases, or a `paths` trigger |
| The agent loads a skill twice | The skill was injected and the agent also matched its description and loaded it | Inject it as a `pointer`, or narrow the skill's description |
| A worker ignores a new directive | The directive is not committed; workers read from the run's base commit | Commit it, then start the run |
| `doctor` notes the budget is exceeded | Always-on content for a role is over `context.budget` | Shorten entries, switch them to `pointer`, or move them to a trigger |
| Nothing is injected anywhere | `HARNESS_NO_CONTEXT=1` is set, or `modules.hooks` is off | Unset the variable; turn the module on and render |
| `render` exits 1: static entry with `for` | A static entry reaches every session that reads `AGENTS.md` | Remove `for`, or remove `static` and let the hook deliver it |
