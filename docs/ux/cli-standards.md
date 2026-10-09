# CLI standards

Rules for every command, flag, output stream and exit code of `harness`. The [reference surface](#reference-surface)
below is the command set these rules govern; a new command or flag MUST follow them, and a deviation needs a
[UX record](README.md#proposing-a-change-the-ux-record).

## Reference surface

```text
harness scaffold [--adopt] [--import <scope>[,<scope>]...] [--preset go|node|terraform|python|generic]
                 [--front-door mise|binary] [--yes] [--dry-run]
harness remove [--module <name>]... [--force] [--dry-run]
harness render [--check] [--refresh] [--dry-run]
harness doctor [--json]
harness explain <topic>
harness lifecycle list [--json]
harness lifecycle run <event> [--action <name>]... [--scope task|run|change] [--task <id>] [--session <id>]
                      [--dry-run]
harness worktree create <name> [--base <ref>] [--tracker beads|prd|none] [--task <id>] [--prd <path>] [--no-bootstrap]
harness worktree remove <name>
harness verify [--lane <name>=<pattern>]... [--skip <name>=<reason>]... [--full] [--base <ref>] [--check]
harness task claim|note|complete|release <id> [--note <text>]
harness ralph run [--agent <name>] [--tracker beads|prd] [--prd <path>] [--epic <id>]... [--workers <n>]
                  [--model <id>] [--effort <level>] [--iterations <n>] [--headless]
harness ralph resume <session-id> [--agent <name>] [--model <id>] [--effort <level>] [--iterations <n>] [--headless]
harness ralph clean
harness skills sync
harness fleet <command> [command args...] [--repos <file>] [--deliver] [--continue-on-error]
harness version [--check-update]

Internal:
harness hook
harness ralph-worker <agent> [agent args...]
```

| Command or flag | Behaviour governed by these standards |
| --- | --- |
| `scaffold --adopt --import <scope>[,<scope>]...` | imports existing permission and hook configuration into `policy.yaml` and `hooks.yaml`. `--import` takes a comma-separated set from the closed enumeration `project`, `local`, `user`, `none`; the default is `project` with `--adopt` and `none` without it. Imports from `local` and `user` are marked for review in the to-do ([compatibility-standards.md](compatibility-standards.md#adoption-equivalence)) |
| `explain <topic>` | prints the built-in documentation for one topic: `layout`, `profile`, `policy`, `hooks`, `lifecycle`, `adoption`, `verify`, `ralph`, `invariants`, `blocks`, `presets`, `safety`, `pitfalls` |
| `render --refresh` | re-resolves presets whose `extends` has no `@<ref>` and records the new commits in the manifest; plain `render` and `render --check` reuse the recorded commits |
| `lifecycle list` | prints every lifecycle event, its actions, their `auto` flags, scopes and source (repository or preset) |
| `lifecycle run <event>` | runs the event's declared actions, in declaration order, for one scope; never asks for confirmation |
| `fleet <command> --deliver` | after the command, commits the tool-owned changes on a new local branch `<branch_prefix><command>` cut from the fetched default branch, runs `harness verify` (no lanes are needed for a change outside `app_paths`), then fires `deliver` (scope `change`) in that repository, running every declared `deliver` action for that scope |

The binary contains no hosting-platform logic: no command and no flag pushes, opens a pull request or writes to a
hosting platform by itself. Those effects exist only as declared lifecycle actions
([principles.md](principles.md#7-nothing-leaves-the-clone-undeclared)).

## Command grammar

Three shapes exist. A new command MUST take one of them.

| Shape | Used for | Commands |
| --- | --- | --- |
| `harness <verb>` | an operation on the repository as a whole | `scaffold`, `remove`, `render`, `doctor`, `verify`, `explain`, `version` |
| `harness <noun> <verb>` | an operation on one kind of object the repository holds | `worktree create\|remove`, `task claim\|note\|complete\|release`, `lifecycle list\|run`, `ralph run\|resume\|clean`, `skills sync` |
| `harness fleet <command>` | a repository-level command run across a declared list of repositories | `fleet render`, `fleet doctor`, `fleet scaffold`, … |

Internal commands are called by agents or by files harness generates, not by people: `harness hook` (reads an agent
hook payload on stdin) and `harness ralph-worker` (the agent command in a coordinator's ralph-tui configuration).
They appear in help under a separate **Internal** heading, their interface is a contract with the adapters, and their
I/O follows the calling agent's protocol rather than the rules in this page where the two differ.

Rules:

- A verb that applies to the whole repository goes at the top level. A verb that applies to one object goes under
  that object's noun.
- A noun is singular when each invocation targets one instance (`worktree`, `task`, a `lifecycle` event) and plural
  when the operation covers a set (`skills`).
- Operands identify the target (`<name>`, `<id>`, `<topic>`, `<session-id>`); flags modify how the operation runs.
  An operand is never optional in one command and positional-required in its sibling.
- Inverse operations use the established pairs: `scaffold`/`remove`, `create`/`remove`, `claim`/`release`,
  `run`/`clean`. New commands reuse these verbs before introducing new ones; `delete`, `rm`, `destroy`, `init`,
  `install` and `uninstall` are not used.
- Checking is a flag, not a verb: `render --check`, `verify --check`. A command whose only job is to check is `doctor`.

## Naming

- Command names, flag names and enumerated values are lower case ASCII. Multi-word names use hyphens:
  `ralph-worker`, `--check-update`, `--continue-on-error`, `--no-bootstrap`.
- Names are full words. Abbreviations are limited to terms users already type elsewhere (`--prd`, `--json`, `-C`).
- No aliases. Each command and flag has exactly one spelling ([principles.md](principles.md#6-one-way-to-do-each-thing)).
- A flag means the same thing on every command that accepts it. `--base <ref>` is always a git ref; `--agent <name>`
  is always an agent name from the profile.

## Flags

### Global flags

| Flag | Meaning | Accepted by |
| --- | --- | --- |
| `-C <dir>` | run as if started in `<dir>` (same semantics as `git -C`) | every command |
| `--json` | write the result as JSON on stdout | commands whose output is data |
| `--dry-run` | print the change set and write nothing | every command that mutates the repository or its state, or runs declared actions |
| `--yes` | answer every confirmation with yes | every command that can ask for confirmation |
| `--verbose` | add detail on stderr | every command |

- Global flags MAY appear before or after the command path.
- A global flag given to a command where it has no meaning is an error (exit 1) naming the flag and the command,
  never silently ignored. Example: `harness doctor --dry-run` fails because `doctor` writes nothing.
- `--verbose` MUST NOT change stdout or the exit code.

### Long and short forms

- Every flag has a long form. `-C` is the only short flag besides `-h` (help). A new short flag requires a UX record.

### Booleans

- A boolean flag is true when present: `--check`, `--full`, `--headless`, `--force`.
- A `--no-<x>` flag exists only when the default is on: `--no-bootstrap`.
- `--x=false` is not a documented form.

### Values

- Enumerated values form a closed set listed in help: `--tracker beads|prd|none`, `--front-door mise|binary`. An
  invalid value is an error that lists the valid ones.
- Paths in flags are relative to the working directory after `-C`. Paths in output are relative to the repository
  root, with forward slashes.
- Placeholders in help use angle brackets: `<name>`, `<ref>`, `<path>`.

### Repeatable flags

- A flag that takes several values is repeated, never comma-separated: `--module hooks --module ralph`.
- The one exception is a flag whose value is a set drawn from a small closed enumeration: it takes a comma-separated
  list and is not repeated (`--import project,local`).
- A keyed value uses `name=value`: `--lane unit=src/foo/**`, `--skip e2e="no route changed"`. The name is everything
  before the first `=`; the value is everything after it and MAY contain `=`.
- The order of repeated flags does not change the result. Repeating the same `name` twice with different values is an
  error unless the flag's help says values accumulate.

### Precedence

When the same setting can come from several places: command-line flag, then `HARNESS_*` environment variable, then
repository file, then built-in default. Built-in invariants sit above all of them and are not settings.

## Output

### Streams

| Stream | Carries |
| --- | --- |
| stdout | the result: human text by default, one JSON document with `--json` |
| stderr | diagnostics: errors, warnings, progress, update notices, `--verbose` detail |

- stdout MUST contain only the result, so `harness doctor --json | jq` and `$(harness …)` work.
- With `--json`, stdout carries either one complete JSON document or nothing. Errors still go to stderr in the human
  format ([messages-and-docs.md](messages-and-docs.md#error-messages)); the exit code says which case occurred.

### Human output

- Default output is for a person reading a terminal: short lines, aligned columns for lists, a one-line summary at the
  end of a mutation (`3 files updated, 1 block updated, 0 removed`).
- A mutation that changes nothing says so in one line (`up to date`), and exits 0.
- Each status is spelled as a word (`ok`, `drift`, `missing`, `blocked`, `skipped`). Color MAY reinforce a word, never
  replace it.

### Color and terminal control

- Color and cursor control are used only when the stream is a TTY, `NO_COLOR` is unset or empty, and `TERM` is not
  `dumb`. Each stream is tested on its own.
- Output written to a pipe or file contains no ANSI escape sequences.

### JSON

- Keys are `snake_case`, matching the YAML files (`required_when`, `app_paths`).
- The JSON shape of each command is part of the public contract ([compatibility-standards.md](compatibility-standards.md)):
  fields are added, never renamed or removed, within a major version.
- Arrays have a documented, deterministic order.
- Paths are repository-relative with forward slashes. Timestamps are RFC 3339 in UTC.
- Absent and empty are distinct: a field that does not apply is omitted; an empty list is `[]`.

### Progress

- Progress goes to stderr.
- On a TTY, a command that runs longer than about two seconds shows what it is doing (current step or repository).
- Off a TTY, progress is plain lines at milestones (one per lane, task or repository), never spinners or carriage
  returns.
- Long-running commands (`verify --full`, `ralph run`, `lifecycle run`, `fleet …`) end with a summary listing each
  unit and its outcome. `fleet … --deliver` reports `committed, not delivered` for a repository that declares no
  `deliver` actions.

## Exit codes

| Code | Meaning | Examples |
| --- | --- | --- |
| 0 | success; nothing to report or nothing left to do | `render` wrote or found everything up to date; `doctor` found nothing; every action of a `lifecycle run` passed, or the event declares no actions |
| 1 | error: the command could not do its job | invalid usage, unknown key in a repository file, missing prerequisite tool, I/O failure; a mise wrapper run without the binary |
| 2 | a check ran and failed | `render --check` found drift, `doctor` reported a finding, `verify --check` or `task complete` met an invalid receipt; a lifecycle action exited non-zero; `scaffold --adopt` found a project-scope rule no longer in effect |
| 3 | blocked: the next step is a person's decision | confirmation needed with no TTY and no `--yes`; `remove` refused a hand-edited file without `--force`; the repository schema is newer than the binary; a lifecycle guard refused (`deliver` on the default branch, with uncommitted changes, or, when the verify module is enabled, without a valid receipt) |

- Codes 2 and 3 tell an agent what to do next: 2 means "fix the finding and run again"; 3 means "stop and ask a
  person". A new failure mode MUST map to one of the four codes; no other code is used.
- Invalid usage exits 1, so that 2 stays unambiguous for CI.
- `harness fleet` reports each repository's code in its summary and exits 0 only when every repository exited 0.
- `harness lifecycle run` exits 2 if any action failed. With `on_failure: stop` (the default), the first failure
  stops the event with exit 2. With `on_failure: continue`, the failure is reported in the summary and in
  `.work/lifecycle.jsonl`, the remaining actions run, and the event still exits 2. A guard refusal exits 3 before any
  action runs.
- A failing automatic event never changes the exit code of the command that fired it: it is reported on stderr and
  recorded in `.work/lifecycle.jsonl` in the primary checkout. Dry runs are not recorded.
- `harness hook` follows the calling agent's hook contract for its exit code and output, as defined by each adapter.
- Entry points without the binary ([principles.md](principles.md#1-optional-and-transparent)): an explicit entry point
  (a mise wrapper a person or agent runs on purpose) exits 1 after naming the install command; an implicit entry point
  (a rendered agent hook entry) exits 0 after one stderr line.

## Errors

Every error states what failed, why, and the exact command to run next. Format and wording:
[messages-and-docs.md](messages-and-docs.md#error-messages).

- An error about a file names the path and, when known, the line and column.
- Validation reports every problem it finds in one run, not only the first.
- Stack traces and internal identifiers appear only with `--verbose`.

## Confirmations, `--yes` and `--force`

- A command asks for confirmation only before an action that cannot be undone from the repository's own history, or
  that reaches beyond the current repository (`fleet … --deliver`).
- `harness lifecycle run` never asks for confirmation, and a lifecycle action fired automatically at its trigger point
  (`auto: true`) never asks either. The gate for agents is the policy: scaffolded repositories seed
  `harness lifecycle run deliver` under `ask`.
- A confirmation is asked only when stdin and stderr are TTYs. Otherwise the command stops before acting and exits 3,
  naming `--yes`.
- `--yes` answers confirmations. It never overrides a safety refusal.
- `--force` overrides a named safety refusal (for example deleting a hand-edited tool-owned file). It exists only on
  commands with such a refusal, every refusal message names it, and it never overrides a built-in invariant.

## Dry run

- Every command that writes to the repository, its state roots or another repository MUST accept `--dry-run`.
- A dry run performs every read and validation the real run would, writes nothing, and prints the complete change
  set: paths created, updated and removed; blocks and native keys changed; unified diffs on request with `--verbose`.
- A dry run exits with the code the real run would have for validation and checks (1, 2 or 3), and 0 otherwise.
- With `--json`, the change set is the JSON result.
- For a command that runs declared actions (`lifecycle run`, `fleet … --deliver`), the dry run also applies the
  lifecycle guards and prints each action in order with the `HARNESS_*` variables it would receive, and runs none.

## Idempotency

- Running a mutation again after it succeeded changes nothing and exits 0.
- A command interrupted part way leaves the repository in a state the same command can complete by running again.
- Operations on objects follow the same rule: claiming a task the caller already holds, or creating a worktree that
  already exists with the same base, succeeds without change; a conflicting state (another holder, a different base)
  is an error naming the conflict.

## Non-interactive operation

Agents and CI run harness without a terminal. For them:

- No command requires a TTY. Every input a prompt could collect is also available as a flag.
- No command opens a pager or an editor.
- Behaviour is the same with and without a TTY except for color, progress style and confirmations (which exit 3).
- Output does not depend on terminal width when stdout is not a TTY.

## Environment variables

### Variables harness reads

- Every variable harness reads is prefixed `HARNESS_` and documented in help and in the features reference.
  Example: `HARNESS_NO_UPDATE_CHECK=1` disables the release check.
- Standard variables are honoured with their usual meaning: `NO_COLOR`, `TERM`, the XDG base directories for
  machine-level configuration and cache.
- An environment variable affects only machine-local behaviour (color, notices, cache, verbosity). It MUST NOT change
  the bytes harness writes into a repository, so rendering stays identical across machines.
- A boolean variable is on when set to `1` or `true` and off when unset, empty, `0` or `false`.

### Values passed to user-declared commands

The placeholders `{file}` and `{pattern}` in profile and hook commands are the only substitutions harness performs,
and harness shell-quotes each value ([configuration-standards.md](configuration-standards.md#values-and-placeholders)).
Lifecycle actions get values only through environment variables, so a task title or branch name cannot inject shell
syntax. Any new value is an environment variable, never a new placeholder.

Lifecycle actions run with `sh -c` in the worktree and inherit the environment of the process that fired them, plus:

| Variable | Value |
| --- | --- |
| `HARNESS_EVENT` | the lifecycle event (`start`, `verified`, `completed`, `deliver`, `landed`) |
| `HARNESS_SCOPE` | `task`, `run` or `change` |
| `HARNESS_REPO` | absolute path of the primary checkout |
| `HARNESS_WORKTREE` | absolute path of the worktree the action runs in |
| `HARNESS_WORKTREE_NAME` | the worktree's name |
| `HARNESS_BRANCH` | the work's branch |
| `HARNESS_BASE` | the base ref the work started from |
| `HARNESS_DEFAULT_BRANCH` | the detected default branch (`origin/HEAD`) |
| `HARNESS_TASK` | the task id, for scope `task` |
| `HARNESS_SESSION` | the Ralph session id, for scope `run` |
| `HARNESS_RECEIPT` | path to the receipt JSON |
| `HARNESS_SUMMARY` | one-line verification summary |

A variable that does not apply to the scope is unset, not empty. These names and meanings are part of the public
contract ([compatibility-standards.md](compatibility-standards.md#versioning)).

## Help text

Every command and command group answers `--help` (and `-h`) on stdout with exit 0, in this order:

1. One-line summary in the imperative (`Render native agent files from .agents/`).
2. Usage line with operands and flags in the grammar above.
3. A short description: what the command reads, what it writes, what it never touches.
4. Flags: name, value placeholder, default, allowed values.
5. Examples: at least one complete, runnable invocation.
6. Exit codes that apply, when the command uses more than 0 and 1.
7. See also: the matching `harness explain <topic>` and related commands.

Invalid usage prints the usage line and the hint `run: harness <command> --help` to stderr and exits 1.

## Version and compatibility output

- `harness version` prints the binary version (semantic versioning) and, per repository file, the schema versions it
  reads. With `--json`, the same as data.
- `harness version --check-update` checks for a newer release on request.
- Agent and tool version support (Claude Code, Codex, `bd`, `ralph-tui`) is reported by `harness doctor`, which names
  the version found and the supported range.
- Rules for what a version promises: [compatibility-standards.md](compatibility-standards.md).

## Deprecation

- A deprecated command, flag, operand, value or environment variable keeps working for the window in
  [compatibility-standards.md](compatibility-standards.md#deprecation) and prints one stderr line per invocation:
  `harness: --old is deprecated; use --new` (with the exact replacement).
- Deprecated items stay in help under a **Deprecated** heading until removal.
- A deprecation warning never changes stdout, the JSON result or the exit code.
