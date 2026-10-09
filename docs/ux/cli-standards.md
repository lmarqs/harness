# CLI standards

## Objective

**Problem.** People type the tool's commands, scripts and agents read what the commands print, and CI pipelines and
unattended agents run them with nobody watching. When each command picks its own names, flags, output and exit codes,
what a person learns on one command fails on the next, a script breaks when a message is reworded, and an agent cannot
tell from a failure whether to retry, fix something or stop and ask a person. A command that waits for an answer nobody
is there to give stalls an unattended run.

**Goal.** Anyone who knows one command can predict how any other is invoked and what its output and exit code mean. A
script, a pipeline or an unattended agent can run every command with nobody at a terminal and choose its next step from
the result alone.

## Overview

Rules for every command, flag, output stream and exit code of `harness`. The [reference surface](#reference-surface)
below is the command set these rules govern; a new command or flag MUST follow them, and a deviation is a change to
this standard, with its reason ([README.md](README.md#changing-a-standard)).

## Entry points

A repository runs harness through the committed wrapper script `harnessw` at its root. The wrapper resolves the
version pinned in `.harness/wrapper.yaml` from the machine cache, downloads and verifies it on first use, and execs it
with the arguments ([compatibility-standards.md](compatibility-standards.md#installation-and-version-pinning);
reference: [wrapper](../features/wrapper.md)). Why: a committed wrapper makes every machine and pipeline run the
version the repository chose, with no installation step before the first command
([ADR 0017](../adrs/0017-engine-version.md)).

| Entry point | Kind | Behaviour |
| --- | --- | --- |
| `./harnessw <command>` | explicit, the documented invocation | runs the pinned version; downloads it over HTTPS and verifies its checksum when it is not cached; exits non-zero naming the cause when it cannot: 1 for a failed download or a checksum mismatch, 3 when a person must act first (no downloader or sha256 tool, or a pending pin whose release is not cached) |
| `harness <command>` | explicit, optional global install | inside a repository whose `.harness/wrapper.yaml` pins a different version, execs the pinned version from the cache, downloading it through the same verified path (never from a hook); under a pending pin whose release is not cached, runs `harness wrapper` itself, the command that completes the pin, and exits 3 for any other command, because nothing can be downloaded against a pending pin; elsewhere runs itself |
| task shims (`front_door: mise`) | explicit | one-line mise tasks that exec `./harnessw <command>` |
| `./harnessw --no-download <command>` | implicit: rendered agent hooks | runs only a cached binary and never downloads; when none is cached, prints one stderr line naming `./harnessw`, and for the session-start event also the one context line below, then exits 0 |

- Implicit entry points MUST NOT download. Only an invocation a person or agent makes on purpose fetches a binary.
  Why: a hook fires on every matching agent event; a download there stalls the session and fails offline.
- When the pinned version is not cached, the rendered session-start hook prints its stderr line and also adds one line
  to the agent's session context: `harness <version> is not cached on this machine; run ./harnessw version once to
  fetch it.` Every other implicit entry point prints only its stderr line. The wrapper prints both lines itself, as
  fixed text from its template, selected by the event the hook names. Why: the agent learns once per session why hooks,
  notifications and standing context are absent, without a message on every tool call, and only the wrapper is present
  to say it ([ADR 0017](../adrs/0017-engine-version.md)).
- Documentation, help examples, the `AGENTS.md` and `README.md` `commands` blocks and the harness skill write commands
  that run inside a repository as `./harnessw <command>`, and show `harness <command>` as the equivalent when harness
  is installed globally. Commands that span repositories (`harness fleet …`) are written with `harness`. Why: a
  command copied from the docs runs as written in a fresh clone without a global install.
- Next-step commands in messages use the same documented form, for the same reason.
- These standards name commands `harness <command>` (the program); the rules apply identically through every entry
  point, because every entry point runs the same binary.
- The wrapper is a POSIX `sh` script, because a shell is the one interpreter every supported machine has. Windows is
  not supported.

### Every spelling is the same command

An agent may invoke a command as `./harnessw <command>`, `harness <command>`, `harnessw <command>` or through a task
shim. These four are the rendered spellings: a policy entry that names a harness command is written once, as
`[harness, …]`, and rendered for each of them ([configuration-standards.md](configuration-standards.md#policy-entries)).
Why: an entry rendered for one spelling would leave the others open.

Guards never rely on those entries alone. The binary itself refuses `harness lifecycle run` inside a Ralph worker or
an `agent` action (the launcher sets `HARNESS_WORKER=1` in the agent's environment, and the binary checks it), and it
enforces the `deliver` guards itself. Why: an absolute path or `sh harnessw` slips past any prefix rule, so policy
entries are defence in depth and the binary is the enforcement ([ADR 0011](../adrs/0011-safety-invariants.md)).

## Reference surface

```text
harness scaffold [--adopt] [--import <scope>[,<scope>]...] [--preset go|node|terraform|python|none]
                 [--front-door mise|binary] [--offline] [--yes] [--dry-run]
harness remove [--module <name>]... [--force] [--dry-run]
harness remove --eject [--force] [--dry-run]
harness render [--check] [--dry-run]
harness preset list
harness preset show <name>
harness preset apply <name>... [--yes] [--dry-run]
harness doctor [--json]
harness explain <topic>
harness context show [--role interactive|worker] [--path <file>]... [--prompt <text>] [--json]
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
harness wrapper [--version <tag> | --latest] [--dry-run]

Wrapper only:
./harnessw --no-download <command> [command args...]

Internal:
harness hook
harness ralph-worker <agent> [agent args...]
```

| Command or flag | Behaviour governed by these standards |
| --- | --- |
| `scaffold` | refuses, exit 3, where a `.harness/` directory exists without a harness `config.yaml` or `manifest.json` (another product's directory), naming the conflict, because harness never writes into a directory it did not create. Pins the running binary's own version: it fetches that release's signed checksum file over HTTPS, verifies the signature with the keys embedded in the running binary, and writes `wrapper.yaml` only then; it exits 1 when the file is unreachable or the signature does not verify, and writes nothing. With `--offline` it makes no network request and writes the pin with `checksums: pending`, which admits no download until `harness wrapper --version <tag>` completes it; a pending pin is opt-in so that a network failure never produces one unnoticed. Detects the repository's stacks and applies their presets to the files it seeds, as `preset apply` does; `--preset <name>` applies that preset instead of detection, and `--preset none` applies none |
| `scaffold --adopt --import <scope>[,<scope>]...` | imports existing permission and hook configuration into `policy.yaml` and `hooks.yaml`. `--import` takes a comma-separated set from the closed enumeration `project`, `local`, `user`, `none`; the default is `project` with `--adopt` and `none` without it. Imports from `local` and `user` are marked for review in the to-do ([compatibility-standards.md](compatibility-standards.md#adoption-equivalence)). A harness pin found in a task runner's tool list is copied into `wrapper.yaml`, and removing it from that file is listed as a to-do; harness never edits the task runner's file |
| `remove` | undo: deletes owned content (without `--module`, `harnessw`, `wrapper.yaml` and the manifest included) and the seeded repository files in `.harness/` (listed first in its output); deletes a seeded file outside `.harness/`, such as a memory skeleton file, only while it matches its recorded hash; lists and leaves any file the manifest does not record, and deletes `.harness/` once nothing else is in it; removes every managed block and owned native key and entry; writes imported project-scope settings back as plain native settings; leaves the state directories in place and lists them ([generated-content-standards.md](generated-content-standards.md#removal)) |
| `remove --eject` | eject: stops managing and keeps the result. Convention blocks lose their markers and stay; tool blocks are removed; `.gitignore` entries stay; rendered native files and keys stay as hand-owned content without their headers; `raw` hooks stay; hooks that call harness, permission entries that name a harness command and task shims that exec `harnessw` are removed and listed; the harness skill and its links, `harnessw`, and the owned and seeded files in `.harness/` are removed; a file the manifest does not record is listed and left, and `.harness/` is deleted once nothing else is in it. Not combined with `--module`; accepts `--force` |
| `wrapper` | without flags, prints the pin, whether it is cached and the newest release, and writes nothing |
| `wrapper --version <tag>`, `wrapper --latest` | the running binary fetches the target release's signed checksum file and verifies the signature with its embedded keys; only then does it download this platform's asset, verify its sha256, and have the new release write `version` and `checksums` into `.harness/wrapper.yaml` and render the repository: `harnessw`, the harness skill and every other artefact the new release renders differently. Any failure exits 1 and writes nothing. The pin and its rendered effects reach review as one diff ([ADR 0017](../adrs/0017-engine-version.md)). `harness fleet wrapper --version <tag> --deliver` does the same across repositories |
| `explain <topic>` | prints the built-in documentation for one topic: `layout`, `profile`, `policy`, `hooks`, `lifecycle`, `context`, `adoption`, `verify`, `ralph`, `invariants`, `blocks`, `presets`, `wrapper`, `safety`, `pitfalls` |
| `context show` | prints, without writing anything, the standing context that would be injected for a role (`interactive` or `worker`, default `interactive`), given the files read or edited (`--path`) and a prompt (`--prompt`), with the entry and trigger behind each item. It is a deterministic preview of what the hooks inject |
| `preset list` | prints the presets of the running release, each with the stack it fits and whether that stack's marker files are present in the repository; writes nothing |
| `preset show <name>` | prints the entries the preset holds, as the YAML it would add, grouped by file; writes nothing; an unknown name exits 1 |
| `preset apply <name>...` | applies the named presets, in order, to an adopted repository: needs `.harness/config.yaml` (otherwise exit 1, naming `harness scaffold`); adds to `.harness/config.yaml` and `.harness/hooks.yaml` only what they lack, never replacing an item the repository names and reporting each item it kept ([configuration-standards.md](configuration-standards.md#presets)); shows the additions as a diff and asks; then writes them and renders the repository as `render` does. Nothing to add exits 0; `--json` lists the entries added and the items kept |
| `lifecycle list` | prints every lifecycle event (the out-of-band `blocked` included), its actions, their kinds, `auto` flags and scopes |
| `lifecycle run <event>` | runs the event's declared actions, in declaration order, for one scope; never asks for confirmation; refuses, exit 3, inside a Ralph worker or an `agent` action |
| `fleet <command> --deliver` | after the command, commits the tool-owned changes on a new local branch `<branch_prefix><command>` cut from the fetched default branch, runs `harness verify` (no lanes are needed for a change outside `app_paths`), then fires `deliver` (scope `change`) in that repository, running every declared `deliver` action for that scope |

The binary contains no logic for the platform that hosts the team's work: no command and no flag pushes, opens a pull
request or writes to a hosting platform by itself ([ADR 0012](../adrs/0012-write-boundary.md)). Those effects exist
only as declared actions ([principles.md](principles.md#7-nothing-leaves-the-clone-undeclared)). Why: delivery
conventions differ per organization and platform, and a declared action is reviewed where the convention is decided.

## Command grammar

Three shapes exist. A new command MUST take one of them, because a user who knows the shapes can guess where a
command lives without reading help.

| Shape | Used for | Commands |
| --- | --- | --- |
| `harness <verb>` | an operation on the repository as a whole | `scaffold`, `remove`, `render`, `doctor`, `verify`, `explain`, `version`, `wrapper` |
| `harness <noun> <verb>` | an operation on one kind of object the repository holds | `worktree create\|remove`, `task claim\|note\|complete\|release`, `lifecycle list\|run`, `context show`, `preset list\|show\|apply`, `ralph run\|resume\|clean`, `skills sync` |
| `harness fleet <command>` | a repository-level command run across a declared list of repositories | `fleet render`, `fleet doctor`, `fleet scaffold`, `fleet wrapper`, … |

Internal commands are called by agents or by files harness generates, not by people: `harness hook` (reads an agent
hook payload on stdin) and `harness ralph-worker` (the agent command in a coordinator's ralph-tui configuration).
They appear in help under a separate **Internal** heading, their interface is a contract with the adapters, and their
I/O follows the calling agent's protocol rather than the rules in this page where the two differ, because the agent,
not harness, defines what a hook must print and return.

Rules:

- A verb that applies to the whole repository goes at the top level. A verb that applies to one object goes under
  that object's noun, so related operations are listed together in help.
- A noun is singular when it is a kind of object whose instances commands pick by name or id (`worktree <name>`,
  `task <id>`, `preset <name>`, a `lifecycle` event, the `context` of one session) and plural when the operation always
  covers the whole set (`skills sync`, every declared source), so the name says whether a command can pick what it
  touches.
- Operands identify the target (`<name>`, `<id>`, `<topic>`, `<session-id>`); flags modify how the operation runs.
  An operand is never optional in one command and positional-required in its sibling, because siblings are learned
  together.
- Inverse operations use the established pairs: `scaffold`/`remove`, `create`/`remove`, `claim`/`release`,
  `run`/`clean`. New commands reuse these verbs before introducing new ones (`preset list` and `preset show` reuse
  `list` and `show`); `delete`, `rm`, `destroy`, `init`, `install` and `uninstall` are not used. Why: one verb per
  meaning keeps the inverse of every command guessable.
- `apply` writes entries into the repository's own files and has no inverse. Why: the entries it writes become the
  repository's own, like entries it wrote itself, so they are changed or removed by editing the file, and removing
  harness deletes them with the seeded files in `.harness/` ([ADR 0013](../adrs/0013-organization-content.md)).
- Checking is a flag, not a verb: `render --check`, `verify --check`. A command whose only job is to check is
  `doctor`. Why: a check and the operation it checks share their inputs, so they share a command.

## Naming

- Command names, flag names and enumerated values are lower case ASCII. Multi-word names use hyphens:
  `ralph-worker`, `--check-update`, `--continue-on-error`, `--no-bootstrap`. Why: one case and one separator remove a
  class of typos that agents and people both make.
- Names are full words. Abbreviations are limited to terms users already type elsewhere (`--prd`, `--json`, `-C`),
  because an invented abbreviation must be looked up.
- No aliases. Each command and flag has exactly one spelling ([principles.md](principles.md#6-one-way-to-do-each-thing)),
  because an alias doubles what documentation, policy entries and agents must know.
- A flag means the same thing on every command that accepts it. `--base <ref>` is always a git ref; `--agent <name>`
  is always an agent name from the profile. Why: a flag learned once must not change meaning on the next command.

## Flags

### Global flags

| Flag | Meaning | Accepted by |
| --- | --- | --- |
| `-C <dir>` | run as if started in `<dir>` (same semantics as `git -C`) | every command |
| `--json` | write the result as JSON on stdout | commands whose output is data |
| `--dry-run` | print the change set and write nothing | every command that mutates the repository or its state, or runs declared actions |
| `--yes` | answer every confirmation with yes | every command that can ask for confirmation |
| `--verbose` | add detail on stderr | every command |

- Global flags MAY appear before or after the command path, because both orders occur in scripts and both are
  unambiguous.
- A global flag given to a command where it has no meaning is an error (exit 1) naming the flag and the command,
  never silently ignored. Example: `harness doctor --dry-run` fails because `doctor` writes nothing. Why: an ignored
  `--dry-run` would let a caller believe a mutation was previewed.
- `--verbose` MUST NOT change stdout or the exit code, so adding it while debugging never changes what a script sees.

### Long and short forms

- Every flag has a long form. `-C` is the only short flag besides `-h` (help). A new short flag requires a change to
  this standard, with its reason. Why: short flags are a scarce namespace and are unreadable in committed scripts and
  policy entries.

### Booleans

- A boolean flag is true when present: `--check`, `--full`, `--headless`, `--force`.
- A `--no-<x>` flag exists only when the default is on: `--no-bootstrap`, and the wrapper's `--no-download`, which
  belongs to the wrapper rather than the binary and is its first argument (`./harnessw --no-download hook <event>`). Why: the
  wrapper reads its own flag before it knows whether a binary exists to receive the rest.
- `--x=false` is not a documented form, because one spelling per state keeps policy entries and docs exact.

### Values

- Enumerated values form a closed set listed in help: `--tracker beads|prd|none`, `--front-door mise|binary`,
  `--role interactive|worker`. An invalid value is an error that lists the valid ones, so the fix is in the message.
- Paths in flags are relative to the working directory after `-C`. Paths in output are relative to the repository
  root, with forward slashes. Why: output paths must be identical on every machine and pasteable as input.
- Placeholders in help use angle brackets: `<name>`, `<ref>`, `<path>`.

### Repeatable flags

- A flag that takes several values is repeated, never comma-separated: `--module hooks --module ralph`,
  `--path src/a.sql --path src/b.sql`. Why: values such as paths and patterns may contain commas.
- The one exception is a flag whose value is a set drawn from a small closed enumeration: it takes a comma-separated
  list and is not repeated (`--import project,local`), because its values cannot contain commas.
- A keyed value uses `name=value`: `--lane unit=src/foo/**`, `--skip e2e="no route changed"`. The name is everything
  before the first `=`; the value is everything after it and MAY contain `=`.
- The order of repeated flags does not change the result. Repeating the same `name` twice with different values is an
  error unless the flag's help says values accumulate. Why: a silent last-one-wins hides a mistake.

### Precedence

When the same setting can come from several places: command-line flag, then `HARNESS_*` environment variable, then
repository file, then built-in default. Built-in invariants sit above all of them and are not settings. Why: the
narrower the scope of a source, the more deliberate the choice it expresses.

## Output

### Streams

| Stream | Carries |
| --- | --- |
| stdout | the result: human text by default, one JSON document with `--json` |
| stderr | diagnostics: errors, warnings, progress, update notices, `--verbose` detail |

- stdout MUST contain only the result, so `harness doctor --json | jq` and `$(harness …)` work.
- With `--json`, stdout carries either one complete JSON document or nothing, so a parser never meets half a
  document. Errors still go to stderr in the human format
  ([messages-and-docs.md](messages-and-docs.md#error-messages)); the exit code says which case occurred.

### Human output

- Default output is for a person reading a terminal: short lines, aligned columns for lists, a one-line summary at the
  end of a mutation (`3 files updated, 1 block updated, 0 removed`), so the outcome is readable at a glance.
- A mutation that changes nothing says so in one line (`up to date`), and exits 0, because "nothing to do" is
  success.
- Each status is spelled as a word (`ok`, `drift`, `missing`, `blocked`, `skipped`). Color MAY reinforce a word, never
  replace it, because color is lost in logs and invisible to some readers.

### Color and terminal control

- Color and cursor control are used only when the stream is a TTY, `NO_COLOR` is unset or empty, and `TERM` is not
  `dumb`. Each stream is tested on its own, because stdout and stderr are often redirected separately.
- Output written to a pipe or file contains no ANSI escape sequences, so logs and agent transcripts stay readable.

### JSON

- Keys are `snake_case`, matching the YAML files (`required_when`, `app_paths`), so one name means one thing in both.
- The JSON shape of each command is part of the public contract ([compatibility-standards.md](compatibility-standards.md)):
  fields are added, never renamed or removed, within a major version, because agents and scripts parse it.
- Arrays have a documented, deterministic order, so two runs over the same state compare equal.
- Paths are repository-relative with forward slashes. Timestamps are RFC 3339 in UTC, so values are identical across
  machines and time zones.
- Absent and empty are distinct: a field that does not apply is omitted; an empty list is `[]`. Why: "does not apply"
  and "applies, with nothing in it" lead to different next steps.

### Progress

- Progress goes to stderr, so it never mixes with the result.
- On a TTY, a command that runs longer than about two seconds shows what it is doing (current step or repository), so
  a person can tell a slow command from a stuck one.
- Off a TTY, progress is plain lines at milestones (one per lane, task or repository), never spinners or carriage
  returns, because those corrupt logs.
- Long-running commands (`verify --full`, `ralph run`, `lifecycle run`, `fleet …`) end with a summary listing each
  unit and its outcome. `fleet … --deliver` reports `committed, not delivered` for a repository that declares no
  `deliver` actions. Why: a caller that skimmed the progress still learns every outcome.

## Exit codes

| Code | Meaning | Examples |
| --- | --- | --- |
| 0 | success; nothing to report or nothing left to do | `render` wrote or found everything up to date; `doctor` found nothing beyond notes; every action of a `lifecycle run` passed, or the event declares no actions |
| 1 | error: the command could not do its job | invalid usage, unknown key in a repository file, a state path inside `.harness/` or `.agents/`, I/O failure; an unknown preset, or `preset apply` in a repository without `.harness/config.yaml`; an explicit entry point that cannot obtain the pinned binary (download failure, checksum mismatch); `wrapper --version`, or `scaffold` without `--offline`, meeting a release whose signed checksum file is unreachable or does not verify |
| 2 | a check ran and failed | `render --check` found drift, `doctor` reported a finding (a second harness pin in a task runner's file, a locked skill whose hash does not match, a pinned release whose signed checksum file no longer matches `wrapper.yaml`), `verify --check` or `task complete` met an invalid receipt; a lifecycle action exited non-zero; `scaffold --adopt` found a project-scope rule no longer in effect |
| 3 | blocked: the next step is a person's decision | confirmation needed with no TTY and no `--yes`; a prerequisite tool a person must install is missing (Node for `skills sync`, `ralph-tui`, `bd`, the wrapper's downloader or sha256 tool); an explicit entry point meets a pending pin whose release is not cached; `remove` refused a hand-edited file without `--force`; `scaffold` found a `.harness/` directory harness did not create; the repository schema is newer than the binary (only when the pin is bypassed); a lifecycle guard refused (`deliver` on the default branch, with uncommitted changes, or, when the verify module is enabled, without a valid receipt; any event inside a Ralph worker or an `agent` action) |

- Codes 2 and 3 tell an agent what to do next: 2 means "fix the finding and run again"; 3 means "stop and ask a
  person". A new failure mode MUST map to one of the four codes; no other code is used. Why: an agent acts on the
  code, and four codes with fixed meanings are all it needs to choose between retry, repair and escalation.
- A missing prerequisite tool exits 3, not 1, because installing software on a machine is a person's decision that an
  agent cannot make; the message names the tool and the command that installs it.
- `doctor` notes (an `AGENTS.md` budget overrun, a standing-context budget overrun, an agent that lacks an event a hook
  needs, an automatic `agent` action, a `harness` on `PATH` that is another program, a running binary other than the
  pinned release, a newer release than the pin) never change the exit code, because they inform a choice rather than
  report a failure.
- Invalid usage exits 1, so that 2 stays unambiguous for CI.
- When a harness command run without a TTY exits 3 outside a worker, harness fires the out-of-band `blocked`
  lifecycle event (scope of that command's work, or `change`) with `HARNESS_REASON` set to the one-line refusal. Why:
  an unattended run that stops for a person must reach that person, and `blocked` is where a repository declares how.
- `harness fleet` reports each repository's code in its summary and exits 0 only when every repository exited 0.
- `harness lifecycle run` exits 2 if any action failed. With `on_failure: stop` (the default), the first failure
  stops the event with exit 2. With `on_failure: continue`, the failure is reported in the summary and in the
  lifecycle log, the remaining actions run, and the event still exits 2. A guard refusal exits 3 before any action
  runs. Why: continuing past a failure is a choice about the remaining actions, not a reason to report success.
- A failing automatic event never changes the exit code of the command that fired it: it is reported on stderr and
  recorded in the lifecycle log (`lifecycle.jsonl` in the state directory of the primary checkout). Dry runs are not
  recorded. Why: the command did its own job; the action's failure belongs to the action.
- `harness hook` follows the calling agent's hook contract for its exit code and output, as defined by each adapter,
  because the agent interprets them.
- Entry points without the binary ([principles.md](principles.md#1-optional-and-transparent)): an explicit entry point
  (`./harnessw <command>`, a task shim, a global `harness`) that cannot obtain the pinned version exits non-zero naming
  the cause (1 for a failed download, 3 for a missing downloader or sha256 tool or a pending pin); an implicit entry
  point (a rendered agent hook entry, `./harnessw --no-download`) with no cached binary exits 0 after one stderr line
  naming `./harnessw`.

## Errors

Every error states what failed, why, and the exact command to run next, so the reader can act without searching.
Format and wording: [messages-and-docs.md](messages-and-docs.md#error-messages).

- An error about a file names the path and, when known, the line and column, so an editor or agent can jump to it.
- Validation reports every problem it finds in one run, not only the first, so a fix does not take one run per
  mistake.
- Stack traces and internal identifiers appear only with `--verbose`, because they mean nothing to the person who
  must act.

## Confirmations, `--yes` and `--force`

- A command asks for confirmation only before an action that cannot be undone from the repository's own history,
  that reaches beyond the current repository (`fleet … --deliver`), or that writes into the repository's own files
  (`scaffold`, `preset apply`, a schema migration). Why: everything else is reversible from git, and needless questions
  train people to answer yes without reading; what harness writes into the repository's own files becomes the
  repository's, with nothing marking that harness wrote it, so the person accepts it while it is still shown as a
  change.
- `harness lifecycle run` never asks for confirmation, and a lifecycle action fired automatically at its trigger point
  (`auto: true`) never asks either. The gate for agents is the policy: scaffolded repositories seed
  `[harness, lifecycle, run, deliver]` under `ask`, rendered for every rendered spelling; it is a seeded entry the
  repository owns and may change ([ADR 0015](../adrs/0015-development-lifecycle.md)). Why: the declared action is the
  reviewed decision; the policy decides who may trigger it.
- A confirmation is asked only when stdin and stderr are TTYs. Otherwise the command stops before acting and exits 3,
  naming `--yes`, because a prompt nobody can answer would hang an unattended run.
- `--yes` answers confirmations. It never overrides a safety refusal, so scripted runs stay safe.
- `--force` overrides a named safety refusal (for example deleting a hand-edited tool-owned file). It exists only on
  commands with such a refusal, every refusal message names it, and it never overrides a built-in invariant. Why: a
  refusal a person cannot override blocks legitimate work; an override that reaches the invariants removes them.

## Dry run

- Every command that writes to the repository, its state directories or another repository MUST accept `--dry-run`,
  because every write must be inspectable before it happens ([principles.md](principles.md#5-never-destroy-always-show-what-will-change)).
- A dry run performs every read and validation the real run would, writes nothing, and prints the complete change
  set: paths created, updated and removed; blocks and native keys changed; unified diffs on request with `--verbose`.
  Why: a preview that skips validation can promise a change the real run refuses.
- A dry run exits with the code the real run would have for validation and checks (1, 2 or 3), and 0 otherwise, so a
  dry run in CI predicts the real outcome.
- With `--json`, the change set is the JSON result.
- For a command that runs declared actions (`lifecycle run`, `fleet … --deliver`), the dry run also applies the
  lifecycle guards and prints each action in order with its kind and the `HARNESS_*` variables it would receive, and
  runs none. It never prints the values of `url_env` or `headers_env` variables, because those carry secrets.

## Idempotency

- Running a mutation again after it succeeded changes nothing and exits 0, because agents and scripts retry.
- A command interrupted part way leaves the repository in a state the same command can complete by running again, so
  an interruption never needs a manual repair.
- Operations on objects follow the same rule: claiming a task the caller already holds, or creating a worktree that
  already exists with the same base, succeeds without change; a conflicting state (another holder, a different base)
  is an error naming the conflict.

## Non-interactive operation

Agents and CI run harness without a terminal. For them:

- No command requires a TTY. Every input a prompt could collect is also available as a flag, so an unattended caller
  can always supply it.
- No command opens a pager or an editor, because nobody is there to close it.
- Behaviour is the same with and without a TTY except for color, progress style and confirmations (which exit 3), so
  what a person tries at the terminal is what an agent gets.
- Output does not depend on terminal width when stdout is not a TTY, so captured output is reproducible.

## Environment variables

### Variables harness reads

Every variable harness or its wrapper reads is prefixed `HARNESS_` and documented in help and in the features
reference, so the prefix alone tells a reader the variable belongs to harness.

| Variable | Read by | Effect |
| --- | --- | --- |
| `HARNESS_HOME` | the wrapper, and a global `harness` deferring to a pin | cache directory for downloaded releases, the update-check result and the result of `harness doctor`'s signed-checksum check, and nothing else; default `$XDG_CACHE_HOME/harness`, falling back to `~/.cache/harness`; each release at `<home>/versions/<version>/harness` |
| `HARNESS_BINARY` | the wrapper | path of a binary to exec instead of the pinned version (air-gapped machines, development of harness itself); bypasses the pin and its checksum |
| `HARNESS_NO_UPDATE_CHECK` | the binary | when on, disables the daily release check |
| `HARNESS_NO_CONTEXT` | the binary | when on, disables standing-context injection for the process tree it is set in, for debugging what an agent sees without it |
| `HARNESS_WORKER` | the binary | set to `1` by the launcher in the environment of a Ralph worker and of an `agent` action; the binary then refuses `lifecycle run` and the other commands workers may not run |

- Standard variables are honoured with their usual meaning: `NO_COLOR`, `TERM`, the XDG base directories for
  machine-level configuration and cache, because users already set them for every tool.
- No environment variable changes the bytes a given binary writes into a repository, so rendering stays identical
  across machines. `HARNESS_BINARY` chooses which binary runs; the others affect presentation, notices, cache location,
  context injection or refusals.
- A boolean variable is on when set to `1` or `true` and off when unset, empty, `0` or `false`, so every boolean reads
  the same way.

### Values passed to user-declared commands and actions

The placeholders `{file}` and `{pattern}` in profile and hook commands are the only substitutions harness performs in
command text, and harness shell-quotes each value
([configuration-standards.md](configuration-standards.md#values-and-placeholders)). Actions get values only through
environment variables, so a task title, branch name or agent message cannot inject shell syntax. Any new value is an
environment variable, never a new placeholder.

How each action kind receives them ([configuration-standards.md](configuration-standards.md#declared-actions)):

| Kind | Receives values |
| --- | --- |
| `run` | runs with `sh -c` in the worktree and inherits the environment of the process that fired it, plus the variables below; the shell expands them, harness substitutes nothing |
| `http` | in `json`, every `$NAME` inside a string is replaced by the JSON-encoded value of the environment variable `NAME` (an unset variable becomes an empty string and a note); the URL and header values come only from the variables named by `url_env` and `headers_env` |
| `agent` | in `prompt`, every `$NAME` is replaced by the variable's value as plain text |

Variables set for lifecycle actions:

| Variable | Value |
| --- | --- |
| `HARNESS_EVENT` | the lifecycle event (`start`, `verified`, `completed`, `deliver`, `landed`, `blocked`) |
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
| `HARNESS_REASON` | the one-line refusal or stop reason, for `blocked` |

Variables set for notification actions (`attention`, `turn_end` in `hooks.yaml`):

| Variable | Value |
| --- | --- |
| `HARNESS_EVENT` | `attention` or `turn_end` |
| `HARNESS_AGENT` | the agent that fired the event (`claude`, `codex`) |
| `HARNESS_MESSAGE` | the agent's own notification text; untrusted, because the agent writes it |
| `HARNESS_REPO` | absolute path of the primary checkout |
| `HARNESS_WORKTREE` | absolute path of the checkout the agent runs in |
| `HARNESS_BRANCH` | that checkout's branch |

A variable that does not apply to the scope or event is unset, not empty, so an action can tell "not applicable" from
"empty". These names and meanings are part of the public contract
([compatibility-standards.md](compatibility-standards.md#versioning)).

## Help text

Every command and command group answers `--help` (and `-h`) on stdout with exit 0, in this order, so a reader finds
the same information in the same place on every command:

1. One-line summary in the imperative (`Render native agent files from .harness/`).
2. Usage line with operands and flags in the grammar above.
3. A short description: what the command reads, what it writes, what it never touches.
4. Flags: name, value placeholder, default, allowed values.
5. Examples: at least one complete, runnable invocation, written as `./harnessw <command>`.
6. Exit codes that apply, when the command uses more than 0 and 1.
7. See also: the matching `harness explain <topic>` and related commands.

Invalid usage prints the usage line and the hint `run: ./harnessw <command> --help` to stderr and exits 1, so the
caller sees the correct form and where to read more.

## Version and compatibility output

- `harness version` prints the binary version (semantic versioning) and, per repository file, the schema versions it
  reads. With `--json`, the same as data. Why: a schema refusal names the file's schema, the newest schema the binary
  reads and the pinned release that reads the file
  ([configuration-standards.md](configuration-standards.md#schema-versioning)), so the user needs to see what the
  binary in hand reads.
- `harness version --check-update` checks for a newer release on request. Moving the repository to it is
  `harness wrapper --version <tag>`, because the version a repository runs is a reviewed change, not a machine update.
- Agent and tool version support (Claude Code, Codex, `bd`, `ralph-tui`) is reported by `harness doctor`, which names
  the version found and the supported range, so an unsupported version is found before it renders wrong output.
- Rules for what a version promises: [compatibility-standards.md](compatibility-standards.md).

## Deprecation

- A deprecated command, flag, operand, value or environment variable keeps working for the window in
  [compatibility-standards.md](compatibility-standards.md#deprecation) and prints one stderr line per invocation:
  `harness: --old is deprecated; use --new` (with the exact replacement). Why: scripts and policy entries that use the
  old form keep working while their owners learn the new one.
- Deprecated items stay in help under a **Deprecated** heading until removal, so a reader of an old script can still
  look them up.
- A deprecation warning never changes stdout, the JSON result or the exit code, because a warning must not break the
  scripts it warns.
