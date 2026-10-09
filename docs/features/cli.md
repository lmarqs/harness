# CLI reference

Every `harness` command, flag, exit code and environment variable on one page. Each command links to its feature page
for behaviour and examples.

In an adopted repository every command runs through the committed [wrapper](wrapper.md), as `./harnessw <command>`,
which runs the release the repository pins; with a global install, `harness <command>` is equivalent. Synopses on this
page use `harness`.

## Synopsis

```text
harness scaffold [--adopt] [--import <scope>[,<scope>]...] [--preset <go|node|terraform|python|none>]
                 [--front-door mise|binary] [--offline] [--yes] [--dry-run]
harness remove [--module <name>]... [--force] [--dry-run]
harness remove --eject [--force] [--dry-run]
harness render [--check] [--dry-run]
harness preset list
harness preset show <name>
harness preset apply <name>... [--yes] [--dry-run]
harness wrapper [--version <tag> | --latest] [--yes] [--dry-run]
harness doctor [--json]
harness explain <topic>
harness context show [--role interactive|worker] [--path <file>]... [--prompt <text>] [--json]
harness hook <session-start|post-tool-use|prompt-submit|attention|turn-end>
harness lifecycle list [--json]
harness lifecycle run <event> [--action <name>]... [--scope task|run|change] [--task <id>] [--session <id>]
                      [--dry-run]
harness worktree create <name> [--base <ref>] [--tracker beads|prd|none] [--task <id>] [--prd <path>] [--no-bootstrap]
harness worktree remove <name>
harness verify [--lane <name>=<pattern>]... [--skip <name>=<reason>]... [--full] [--base <ref>] [--check]
harness task <claim|note|complete|release> <id> [--note <text>]
harness ralph run [--agent <name>] [--tracker beads|prd] [--prd <path>] [--epic <id>]... [--workers <n>]
                  [--model <id>] [--effort <level>] [--iterations <n>] [--headless]
harness ralph resume <session-id> [--agent <name>] [--model <id>] [--effort <level>] [--iterations <n>] [--headless]
harness ralph clean
harness ralph-worker <agent> [agent args...]
harness skills sync
harness fleet <command> [command args...] [--repos <file>] [--deliver] [--continue-on-error]
harness version [--check-update]
```

## The wrapper

```text
./harnessw [--no-download] <command> [args...]
```

`harnessw` resolves the release pinned in `.harness/wrapper.yaml`, downloads and verifies it when it is not cached,
and execs `harness` with the remaining arguments. `--no-download`, which must come first, runs only a cached release:
when none is cached it prints one line naming `./harnessw` on stderr, and for `hook session-start` also one line of
agent context on stdout, then exits 0. Rendered agent hooks use it.

| Exit code | Meaning |
| --- | --- |
| any | the exit code of `harness`, which the wrapper execs |
| 0 | `--no-download`, and the pinned release is not cached; nothing ran |
| 1 | the wrapper could not run `harness`: invalid `wrapper.yaml`, unsupported platform, a failed download, a checksum mismatch |
| 3 | a person must act first: install `curl` or `wget`, or `sha256sum` or `shasum`; or complete a [pending pin](wrapper.md#a-pending-pin) whose release is not cached |

A global `harness` defers to the pin: inside a repository whose `wrapper.yaml` pins another release, it execs that
release, downloading it through the same verified path only when the command was typed, never from a hook. See
[wrapper](wrapper.md#a-global-install-defers-to-the-pin).

## Global flags

| Flag | Applies to | Meaning |
| --- | --- | --- |
| `-C <dir>` | every command | Run as if started in `<dir>` |
| `--json` | commands whose output is data: `doctor`, `context show`, `lifecycle list`, `preset apply` (entries added and items kept), `fleet`, `scaffold` (the to-do), `version` | Print one JSON document instead of text |
| `--dry-run` | commands that change files or run actions: `scaffold`, `remove`, `render`, `wrapper`, `preset apply`, `worktree`, `lifecycle run`, `skills sync`, `fleet` (passed to each repository's command; no branch, commit or delivery) | Print what would change or run; change nothing |
| `--yes` | commands that ask for confirmation: `scaffold`, `remove` (with or without `--eject`), `render` and `wrapper` (schema migration), `preset apply`, `fleet` (passed through) | Answer yes; required without a terminal |
| `--verbose` | every command | Print each command `harness` runs and why |

Commands never prompt without a terminal: where confirmation is needed and `--yes` is absent, they stop with exit 3.
`harness lifecycle run` never asks for confirmation; the gate for agents is the policy
([the seeded entry](policy.md#the-seeded-entry)), and inside a Ralph worker the binary refuses it whatever the policy
says.

## Exit codes

| Code | Meaning | Typical causes |
| --- | --- | --- |
| 0 | Success | |
| 1 | Error | invalid flags or files, a failed external command, a failed download or checksum check, a release whose signed checksum file does not verify |
| 2 | A check or an action failed | `render --check` drift; a `doctor` finding; `verify` lint, validator, build or lane failure; `verify --check` or `task complete` without a valid receipt; a failing post-edit hook; the adoption equivalence check; a failing action in `lifecycle run` or `fleet --deliver` |
| 3 | Blocked: a person must act | a confirmation without a terminal; a prerequisite a person must install, such as Node for the `skills` CLI, `bd` or `ralph-tui`; uncommitted changes where committed state is required; a resource owned by another session or run; a repository schema newer than the running release; an unfinished Ralph run; a lifecycle guard refusing `deliver`; an owned path edited by hand before `remove`; a `.harness/` directory another tool owns, at `scaffold` |

A command run without a terminal that exits 3 fires the [`blocked`](lifecycle.md#events) lifecycle event for the
scope of its work, with the refusal in `HARNESS_REASON`, so a declared notification reaches the person who must act.
Inside a Ralph worker nothing fires.

### Commands that fire lifecycle events

Some commands fire a [lifecycle event](lifecycle.md) when they succeed, which runs the actions the repository declares
with `auto: true`. The command's own work stands whatever the actions do: a written receipt stays written, a completed
task stays completed. A failing automatic event never changes the exit code of the command that fired it: it is
reported on stderr and recorded in `.work/lifecycle.jsonl` in the primary checkout; `harness lifecycle run <event>`
retries it. When the work is not concluded (uncommitted changes, the default branch, or no valid receipt when the
verify module is enabled), the automatic `deliver` does not fire, and the command says which condition is unmet.

| Command | Fires |
| --- | --- |
| `worktree create` | `start` (scope `change`), only for a worktree created without `--task` |
| `task claim` | `start` (scope `task`) |
| `verify` (never with `--check`) | `verified` |
| `task complete` | `completed` (scope `task`), then `deliver` (scope `task`) |
| `ralph run`, `ralph resume` | `completed` (scope `task`) when a verified task commit lands on the run branch, `completed` (scope `run`) when the run finishes, each followed by `deliver` of the same scope; `blocked` (scope `task`) when the launcher skips a task after its retries, `blocked` (scope `run`) when the run stops unfinished; actions run in the launcher process, never inside a worker |
| `worktree remove`, `ralph clean` | `landed` once per recorded branch whose patches are on the default branch, before its worktree is removed |
| `fleet --deliver` | `deliver` (scope `change`) in each repository with a committed change, after `harness verify`; every declared `deliver` action for scope `change` runs |
| any command run without a terminal that exits 3 | `blocked`, for the scope of the command's work, otherwise `change` |

`harness doctor` never fires events or runs actions: it lists landed branches and prints the command that fires
`landed` for each.

## Commands

| Command | Does | Exit codes | Page |
| --- | --- | --- | --- |
| `scaffold` | Set up a repository, `harnessw` and `wrapper.yaml` included, applying the presets of the detected stacks (`--preset` names one, or `none`), pinning the running release after verifying its signed checksum file, or, with `--offline`, writing a [pending pin](wrapper.md#a-pending-pin) and making no network request; `--adopt` imports an existing setup, `--import` chooses the scopes | 0 set up; 1 error, already scaffolded, the release's checksum file unreachable without `--offline`, or not verified; 2 the equivalence check lost a rule; 3 confirmation needed, or a `.harness/` directory another tool owns | [scaffold](scaffold.md) |
| `remove` | Undo the adoption, or one module, using the manifest; `--eject` stops managing and keeps the result | 0 removed or ejected; 1 error; 3 an owned path changed by hand, or confirmation needed | [remove](remove.md) |
| `render` | Render native files, `harnessw`, task shims, the skill, blocks; `--check` for drift | 0 rendered or no drift; 1 invalid file; 2 drift; 3 newer schema, or migration needs confirmation | [render](render.md) |
| `preset list` | List the running release's built-in presets, the stack each fits, and whether its marker files are present | 0 listed | [presets](presets.md#harness-preset-list) |
| `preset show <name>` | Print every entry a preset holds, as YAML grouped by file | 0 printed; 1 unknown preset | [presets](presets.md#harness-preset-show) |
| `preset apply <name>...` | Add the presets' entries the repository's `.harness/config.yaml` and `.harness/hooks.yaml` lack, after showing the diff and confirming; keep and report every item the repository already names; then render | 0 applied, or nothing to add; 1 unknown preset, not scaffolded, invalid repository file; 3 confirmation needed without a terminal, or newer schema | [presets](presets.md#harness-preset-apply) |
| `wrapper` | Pin a release in `.harness/wrapper.yaml` (`--version`, `--latest`): verify the release's signed checksum file with the running release's embedded keys, then download and verify this platform's binary, then have the new release render the repository: `harnessw`, `wrapper.yaml`, every file it renders differently, and a migration of older-schema files after confirmation. Without a flag, print the pin, whether it is cached and the newest release, and write nothing | 0 pinned, a pending pin completed, already pinned, or status printed; 1 unknown tag, no network, a signature that does not verify, missing checksums, checksum mismatch, invalid `wrapper.yaml`; 3 a migration needs confirmation without a terminal; nothing is written unless every step passes | [wrapper](wrapper.md#harness-wrapper) |
| `doctor` | Read-only health checks, the wrapper, the single pin, the release signature and the cached release, the adoption to-do, notes, landed branches with the command that fires `landed` | 0 no findings; 1 cannot run; 2 a finding or an open to-do item | [doctor](doctor.md) |
| `explain <topic>` | Built-in documentation. Topics: `layout`, `profile`, `policy`, `hooks`, `lifecycle`, `context`, `adoption`, `verify`, `ralph`, `invariants`, `blocks`, `presets`, `wrapper`, `safety`, `pitfalls` | 0 printed; 1 unknown topic | [explain](explain.md) |
| `context show` | Preview the standing context a session would receive for a role, paths and a prompt, and why | 0 printed; 1 invalid `context` declarations, a missing skill or file, an unknown role | [context](context.md#harness-context-show) |
| `hook <event>` | Handle an agent event read from stdin (Claude Code or Codex payload): run post-edit commands and validators, add session context, inject standing context, run `attention` and `turn_end` hooks. Called by rendered hook entries through `harnessw --no-download`, with the entry's event as argument | 0 nothing to do or passed; 2 a post-edit command or validator failed | [hooks](hooks.md) |
| `lifecycle list` | List events, their actions, `auto` flags and scopes | 0 listed; 1 invalid hooks file | [lifecycle](lifecycle.md) |
| `lifecycle run` | Run an event's actions by hand, or as a coordinating agent, `blocked` included; never asks for confirmation | 0 every action passed, or none declared; 1 error; 2 an action failed; 3 a guard refused (including any event inside a Ralph worker) | [lifecycle](lifecycle.md) |
| `worktree create` | Create a managed task worktree; fires `start` | 0 created; 1 error | [worktrees](worktrees.md) |
| `worktree remove` | Remove a clean managed worktree, keeping its branch; fires `landed` | 0 removed; 1 error; 3 uncommitted changes or a locked worktree | [worktrees](worktrees.md) |
| `verify` | Run checks and write the receipt; `--check` validates it; fires `verified` | 0 passed, or valid receipt; 1 lane rule not met, files changed during verification; 2 a check failed, or no valid receipt | [verify](verify.md) |
| `task` | Claim, note, complete or release a task; fires `start` and `completed` | 0 done; 1 error; 2 `complete` without a valid receipt; 3 owned by another actor, or a Ralph run owns the PRD | [tasks](tasks.md) |
| `ralph run` | Launch an unattended run | 0 finished; 1 error; 3 refused, or unfinished | [ralph](ralph.md) |
| `ralph resume` | Continue an unfinished run in its coordinator | as `ralph run` | [ralph](ralph.md) |
| `ralph clean` | Remove finished sessions' worktrees and landed branches; fires `landed` | 0 cleaned; 1 error; 3 a Ralph run holds the lock | [ralph](ralph.md#finishing-resuming-cleaning) |
| `ralph-worker` | Internal: the worker command ralph-tui calls | 0 task verified; 1 refused or failed attempt | [ralph](ralph.md#the-worker-contract) |
| `skills sync` | Install declared skill sources through the `skills` CLI | 0 installed; 1 the `skills` CLI failed; 3 no Node | [skills](skills.md) |
| `fleet` | Run a command across a list of repositories; `--deliver` commits and delivers. `fleet wrapper --version <tag> --deliver` moves every repository's pin | the highest per-repository code; a refused dirty repository counts as 3, a failing `deliver` action as 2, a `deliver` guard refusal as 3 | [fleet](fleet.md) |
| `version` | Print the version and the supported schema; `--check-update` checks for a newer release now | 0 printed; 1 error | [installation](installation.md#update-notice) |

## Files read and written

Paths under `.work/` are shown at their defaults; the profile moves them with `state.dir`, `worktrees.dir`,
`tasks.prd_dir` and `ralph.worktrees_dir` ([profile](profile.md#state-paths)).

| Command | Reads | Writes |
| --- | --- | --- |
| `harnessw` | `.harness/wrapper.yaml`, `$HARNESS_HOME/versions/<version>/harness` | `$HARNESS_HOME/versions/<version>/harness`, on a verified download only |
| `scaffold` | marker files, git metadata, mise configuration and tasks, native agent files, `CLAUDE.md`, `.gitignore`; with `--import`, the permission and hook files of each chosen scope; the running release's checksum file and its signature | the three repository files, `.harness/wrapper.yaml`, `.agents/memory/` skeleton, everything `render` writes (`harnessw` included) |
| `remove` | the manifest and the paths it lists; `.harness/policy.yaml` and `.harness/hooks.yaml` for the provenance of imported entries | deletes owned paths, `harnessw`, every file the manifest records in `.harness/`, and `.harness/` once nothing else is in it (an unrecorded file is listed and left); on undo, the memory skeleton files still matching their seeded hash; rewrites native files and block hosts (undo restores imported settings; `--eject` keeps rendered content and strips markers); `.harness/config.yaml` with `--module` |
| `render` | the three repository files, `.harness/wrapper.yaml`, the manifest, native files, block hosts, `.agents/memory/`, the files and skills of `static` context entries with `skills-lock.json`, the git index | native files (owned keys and entries), `harnessw`, `.codex/rules/harness.rules`, `.mise/tasks/` task shims, `.agents/skills/harness/`, skill links, blocks in `AGENTS.md`, `README.md`, `CLAUDE.md`, `.gitignore`, the manifest; the `.agents/memory/` skeleton, once, the first time it renders with the memory module on |
| `preset list` | the marker files of each stack | nothing |
| `preset show` | nothing | nothing |
| `preset apply` | `.harness/config.yaml`, `.harness/hooks.yaml`, everything `render` reads | `.harness/config.yaml` and `.harness/hooks.yaml` (additions only), then everything `render` writes |
| `wrapper` | `.harness/wrapper.yaml`, the manifest, the target release's checksum file and its signature, everything `render` reads | `$HARNESS_HOME/versions/<version>/harness`, then `.harness/wrapper.yaml`, the manifest and every file `render` writes that the new release renders differently (`harnessw` included), and migrated repository files, only after every verification passed |
| `doctor` | everything `render` reads, `harnessw`, `$HARNESS_HOME`, `mise tasks`, `mise.toml`, `.mise.toml`, `.tool-versions`, tool versions, `.work/` session records, `.work/lifecycle.jsonl`, online the pinned release's signed checksum file | `$HARNESS_HOME` (update and signature checks) only |
| `explain` | nothing | nothing |
| `context show` | the profile, the skills and files `context` entries name, `skills-lock.json`; with `--role worker`, those files at `HEAD` | nothing |
| `hook` | stdin, the profile, the hooks file, the skills and files `context` entries name, `skills-lock.json`, `.work/session.json`, `.work/hook-checks.json`, `.work/context/<session-id>.json` | `.work/hook-checks.json`, `.work/context/<session-id>.json`; whatever declared commands and `attention` or `turn_end` actions write |
| `lifecycle list` | the hooks file | nothing |
| `lifecycle run` | the hooks file, `.work/session.json`, `.work/verified.json`, git | `.work/lifecycle.jsonl`; whatever the actions write |
| `worktree create` | the profile, git | `.work/worktrees/<name>/`, branch `work/<name>`, its `.work/session.json`; `.beads/` when initialising Beads; `.work/lifecycle.jsonl` |
| `worktree remove` | git, the hooks file | removes `.work/worktrees/<name>/`; `.work/lifecycle.jsonl`; whatever `landed` actions write; for a `landed` `agent` action, a recorded worktree `.work/worktrees/landed-<slug>-<n>` on branch `work/landed-<slug>-<n>` |
| `verify` | the profile, git, changed files, `.work/session.json` | `.work/verified.json`; `.work/lifecycle.jsonl` |
| `task` | `.work/session.json`, `.work/verified.json` | the Beads database through `bd`, or the PRD file; `.work/lifecycle.jsonl` |
| `ralph run` / `resume` | the profile, policy, hooks file (from the base commit), the PRD or Beads | `.work/ralph.lock`, the coordinator worktree `.work/worktrees/ralph-<id>` and its `.ralph-tui/`, sequential task worktrees `.work/worktrees/ralph-<id>-<task>`, ralph-tui's parallel worker checkouts under `.work/ralph-worktrees/`, branches `work/ralph-*` and `ralph/session-*`, `.work/lifecycle.jsonl` |
| `ralph clean` | `.work/` session records, git | removes recorded worktrees and landed branches; `.work/lifecycle.jsonl`; for a `landed` `agent` action, a recorded worktree `.work/worktrees/landed-<slug>-<n>` |
| `skills sync` | the profile | `.agents/skills/`, `skills-lock.json` (through the `skills` CLI) |
| `fleet` | the fleet file | whatever the command writes; with `--deliver`, a local branch, a commit and a receipt per changed repository, `.work/lifecycle.jsonl`, and whatever its `deliver` actions write |
| `version` | `$HARNESS_HOME` | `$HARNESS_HOME` with `--check-update` |

## Environment variables

Variables that configure `harnessw` and `harness`:

| Variable | Read by | Effect |
| --- | --- | --- |
| `HARNESS_HOME` | `harnessw`, `harness` | The per-user cache directory, default `$XDG_CACHE_HOME/harness`, or `~/.cache/harness` when `XDG_CACHE_HOME` is unset: downloaded releases at `versions/<version>/harness`, the update-check result and the signature-check result of `doctor` |
| `HARNESS_BINARY` | `harnessw` | A path to a `harness` binary to exec instead of the pinned release; no download and no checksum check, so it bypasses the pin, and `harness doctor` notes it ([wrapper](wrapper.md#air-gapped-machines)) |
| `HARNESS_NO_UPDATE_CHECK=1` | `harness` | Disable the daily release check on explicit commands and in `doctor` |
| `HARNESS_NO_CONTEXT=1` | `harness hook` | Inject no [standing context](context.md) in this environment and every process started from it; `static` entries in `AGENTS.md` stay |
| `HARNESS_WORKER=1` | `harness` | Set by the Ralph worker launcher and for `agent` actions, never by hand: selects the `worker` role for standing context, makes the binary refuse `harness lifecycle run`, and keeps every command from firing events |

Set for lifecycle actions, on top of the environment of the process that fires the event. Values reach actions only
through these variables: `run` commands read them as shell variables, and `http` and `agent` actions expand `$NAME`
in their body or prompt ([action kinds](lifecycle.md#action-kinds)); `harness` never substitutes a value into a
command string. A variable that does not apply is unset; see [lifecycle](lifecycle.md#environment) for when each is
set.

| Variable | Value |
| --- | --- |
| `HARNESS_EVENT` | the event: `start`, `verified`, `completed`, `deliver`, `landed`, `blocked` |
| `HARNESS_SCOPE` | the scope: `task`, `run` or `change` |
| `HARNESS_REPO` | the primary checkout's root |
| `HARNESS_WORKTREE` | the path of the checkout the actions run in |
| `HARNESS_WORKTREE_NAME` | the managed worktree's name |
| `HARNESS_BRANCH` | the work's branch |
| `HARNESS_BASE` | the work's base commit |
| `HARNESS_DEFAULT_BRANCH` | the branch `origin/HEAD` points to |
| `HARNESS_TASK` | the task id |
| `HARNESS_SESSION` | the Ralph session id |
| `HARNESS_RECEIPT` | the path of the receipt JSON |
| `HARNESS_SUMMARY` | the one-line verification summary |
| `HARNESS_REASON` | `blocked` only: the one-line refusal or stop reason |

An `http` action reads its URL and header values from the variables its `url_env` and `headers_env` name, so secrets
stay out of committed files; `harness` never logs those values.

Set for [notification hooks](hooks.md), the `attention` and `turn_end` actions in `.harness/hooks.yaml`:

| Variable | Value |
| --- | --- |
| `HARNESS_EVENT` | `attention` (the agent waits for a person) or `turn_end` (the agent finished a turn) |
| `HARNESS_AGENT` | the agent: `claude` or `codex` |
| `HARNESS_MESSAGE` | the agent's own notification text; untrusted |
| `HARNESS_REPO` | the primary checkout's root |
| `HARNESS_WORKTREE` | the path of the checkout the agent works in |
| `HARNESS_BRANCH` | that checkout's branch |

## User-level paths

| Path | Holds |
| --- | --- |
| `$HARNESS_HOME` (default `$XDG_CACHE_HOME/harness`, or `~/.cache/harness`) | `versions/<version>/harness` for each downloaded release, the update-check and signature-check results |
| `~/.config/harness/fleet.yaml` | the default fleet file |
