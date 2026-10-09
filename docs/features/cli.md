# CLI reference

Every `harness` command, flag, exit code and environment variable on one page. Each command links to its feature page
for behaviour and examples.

## Synopsis

```text
harness scaffold [--adopt] [--import <scope>[,<scope>]...] [--preset <go|node|terraform|python|generic>]
                 [--front-door mise|binary] [--yes] [--dry-run]
harness remove [--module <name>]... [--force] [--dry-run]
harness render [--check] [--refresh] [--dry-run]
harness doctor [--json]
harness explain <topic>
harness hook
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

## Global flags

| Flag | Applies to | Meaning |
| --- | --- | --- |
| `-C <dir>` | every command | Run as if started in `<dir>` |
| `--json` | commands whose output is data: `doctor`, `lifecycle list`, `fleet`, `scaffold` (the to-do), `version` | Print one JSON document instead of text |
| `--dry-run` | commands that change files or run actions: `scaffold`, `remove`, `render`, `worktree`, `lifecycle run`, `skills sync`, `fleet` (passed to each repository's command; no branch, commit or delivery) | Print what would change or run; change nothing |
| `--yes` | commands that ask for confirmation: `scaffold`, `remove`, `render` (schema migration), `fleet` (passed through) | Answer yes; required without a terminal |
| `--verbose` | every command | Print each command `harness` runs and why |

Commands never prompt without a terminal: where confirmation is needed and `--yes` is absent, they stop with exit 3.
`harness lifecycle run` never asks for confirmation; the gate for agents is the policy
([the seeded entry](policy.md#the-seeded-entry)).

## Exit codes

| Code | Meaning | Typical causes |
| --- | --- | --- |
| 0 | Success | |
| 1 | Error | invalid flags or files, a missing tool, a failed external command |
| 2 | A check or an action failed | `render --check` drift; a `doctor` finding; `verify` lint, validator, build or lane failure; `verify --check` or `task complete` without a valid receipt; a failing post-edit hook; the adoption equivalence check; a failing action in `lifecycle run` or `fleet --deliver` |
| 3 | Blocked: a person must act | a confirmation without a terminal; uncommitted changes where committed state is required; a resource owned by another session or run; a repository schema newer than the binary; an unfinished Ralph run; a lifecycle guard refusing `deliver` |

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
| `ralph run`, `ralph resume` | `completed` (scope `task`) when a verified task commit lands on the run branch, `completed` (scope `run`) when the run finishes, each followed by `deliver` of the same scope; actions run in the launcher process, never inside a worker |
| `worktree remove`, `ralph clean` | `landed` once per recorded branch whose patches are on the default branch, before its worktree is removed |
| `fleet --deliver` | `deliver` (scope `change`) in each repository with a committed change, after `harness verify`; every declared `deliver` action for scope `change` runs |

`harness doctor` never fires events or runs actions: it lists landed branches and prints the command that fires
`landed` for each.

## Commands

| Command | Does | Exit codes | Page |
| --- | --- | --- | --- |
| `scaffold` | Set up a repository; `--adopt` imports an existing setup, `--import` chooses the scopes | 0 set up; 1 error, already scaffolded; 2 the equivalence check lost a rule; 3 confirmation needed | [scaffold](scaffold.md) |
| `remove` | Remove the tool or modules, using the manifest | 0 removed; 1 error; 3 an owned path changed by hand, or confirmation needed | [remove](remove.md) |
| `render` | Render native files, wrappers, the skill, blocks; `--check` for drift; `--refresh` re-resolves presets | 0 rendered or no drift; 1 invalid file, unresolvable preset; 2 drift; 3 newer schema, or migration needs confirmation | [render](render.md) |
| `doctor` | Read-only health checks, the adoption to-do, notes, landed branches with the command that fires `landed` | 0 no findings; 1 cannot run; 2 a finding or an open to-do item | [doctor](doctor.md) |
| `explain <topic>` | Built-in documentation. Topics: `layout`, `profile`, `policy`, `hooks`, `lifecycle`, `adoption`, `verify`, `ralph`, `invariants`, `blocks`, `presets`, `safety`, `pitfalls` | 0 printed; 1 unknown topic | [explain](explain.md) |
| `hook` | Run hook declarations for an agent event read from stdin (Claude Code or Codex payload). Called by rendered hook entries | 0 nothing to do or passed; 2 a post-edit command or validator failed | [hooks](hooks.md) |
| `lifecycle list` | List events, their actions, `auto` flags, scopes and sources (repository or preset) | 0 listed; 1 invalid hooks file or preset | [lifecycle](lifecycle.md) |
| `lifecycle run` | Run an event's actions by hand, or as a coordinating agent; never asks for confirmation | 0 every action passed, or none declared; 1 error; 2 an action failed; 3 a guard refused (including any event inside a Ralph worker) | [lifecycle](lifecycle.md) |
| `worktree create` | Create a managed task worktree; fires `start` | 0 created; 1 error | [worktrees](worktrees.md) |
| `worktree remove` | Remove a clean managed worktree, keeping its branch; fires `landed` | 0 removed; 1 error; 3 uncommitted changes or a locked worktree | [worktrees](worktrees.md) |
| `verify` | Run checks and write the receipt; `--check` validates it; fires `verified` | 0 passed, or valid receipt; 1 lane rule not met, files changed during verification; 2 a check failed, or no valid receipt | [verify](verify.md) |
| `task` | Claim, note, complete or release a task; fires `start` and `completed` | 0 done; 1 error; 2 `complete` without a valid receipt; 3 owned by another actor, or a Ralph run owns the PRD | [tasks](tasks.md) |
| `ralph run` | Launch an unattended run | 0 finished; 1 error; 3 refused, or unfinished | [ralph](ralph.md) |
| `ralph resume` | Continue an unfinished run in its coordinator | as `ralph run` | [ralph](ralph.md) |
| `ralph clean` | Remove finished sessions' worktrees and landed branches; fires `landed` | 0 cleaned; 1 error; 3 a Ralph run holds the lock | [ralph](ralph.md#finishing-resuming-cleaning) |
| `ralph-worker` | Internal: the worker wrapper ralph-tui calls | 0 task verified; 1 refused or failed attempt | [ralph](ralph.md#the-worker-contract) |
| `skills sync` | Install declared skill sources through the `skills` CLI | 0 installed; 1 the `skills` CLI failed; 3 no Node | [skills](skills.md) |
| `fleet` | Run a command across a list of repositories; `--deliver` commits and delivers | the highest per-repository code; a refused dirty repository counts as 3, a failing `deliver` action as 2, a `deliver` guard refusal as 3 | [fleet](fleet.md) |
| `version` | Print the version and the supported schema; `--check-update` checks for a newer release now | 0 printed; 1 error | [installation](installation.md#update-notice) |

## Files read and written

| Command | Reads | Writes |
| --- | --- | --- |
| `scaffold` | marker files, git metadata, mise configuration and tasks, native agent files, `CLAUDE.md`, `.gitignore`; with `--import`, the permission and hook files of each chosen scope | the three repository files, `.agents/memory/` skeleton, everything `render` writes |
| `remove` | the manifest and the paths it lists | deletes owned paths; rewrites native files and block hosts; `.agents/harness.yaml` with `--module` |
| `render` | the three repository files, presets at their recorded commits, the manifest, native files, block hosts, `.agents/memory/`, the git index | native files (owned keys and entries), `.codex/rules/harness.rules`, `.mise/tasks/` wrappers, `.agents/skills/harness/`, skill links, blocks in `AGENTS.md`, `README.md`, `CLAUDE.md`, `.gitignore`, the manifest (with each preset's resolved commit) |
| `doctor` | everything `render` reads, `mise tasks`, tool versions, `.work/` session records, `.work/lifecycle.jsonl` | `~/.cache/harness/` (update check) only |
| `explain` | nothing | nothing |
| `hook` | stdin, the profile, the hooks file, `.work/session.json`, `.work/hook-checks.json` | `.work/hook-checks.json`; whatever declared commands write |
| `lifecycle list` | the hooks file, presets | nothing |
| `lifecycle run` | the hooks file, presets, `.work/session.json`, `.work/verified.json`, git | `.work/lifecycle.jsonl`; whatever the actions write |
| `worktree create` | the profile, git | `.work/worktrees/<name>/`, branch `work/<name>`, its `.work/session.json`; `.beads/` when initialising Beads; `.work/lifecycle.jsonl` |
| `worktree remove` | git, the hooks file | removes `.work/worktrees/<name>/`; `.work/lifecycle.jsonl`; whatever `landed` actions write |
| `verify` | the profile, git, changed files, `.work/session.json` | `.work/verified.json`; `.work/lifecycle.jsonl` |
| `task` | `.work/session.json`, `.work/verified.json` | the Beads database through `bd`, or the PRD file; `.work/lifecycle.jsonl` |
| `ralph run` / `resume` | the profile, policy, hooks file (from the base commit), the PRD or Beads | `.work/ralph.lock`, the coordinator worktree and its `.ralph-tui/`, task and worker checkouts, branches `work/ralph-*` and `ralph/session-*`, `.work/lifecycle.jsonl` |
| `ralph clean` | `.work/` session records, git | removes recorded worktrees and landed branches; `.work/lifecycle.jsonl` |
| `skills sync` | the profile | `.agents/skills/`, `skills-lock.json` (through the `skills` CLI) |
| `fleet` | the fleet file | whatever the command writes; with `--deliver`, a local branch, a commit and a receipt per changed repository, `.work/lifecycle.jsonl`, and whatever its `deliver` actions write |
| `version` | `~/.cache/harness/` | `~/.cache/harness/` with `--check-update` |

## Environment variables

Read by `harness`:

| Variable | Effect |
| --- | --- |
| `HARNESS_NO_UPDATE_CHECK=1` | Disable the daily release check in `doctor` and the `SessionStart` hook |

Set for lifecycle actions, on top of the environment of the process that fires the event. Values reach actions only
through these variables, never by substitution into the command string. A variable that does not apply is unset; see
[lifecycle](lifecycle.md#environment) for when each is set.

| Variable | Value |
| --- | --- |
| `HARNESS_EVENT` | the event: `start`, `verified`, `completed`, `deliver`, `landed` |
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

## User-level paths

| Path | Holds |
| --- | --- |
| `~/.cache/harness/` | update-check result, fetched presets |
| `~/.config/harness/fleet.yaml` | the default fleet file |
