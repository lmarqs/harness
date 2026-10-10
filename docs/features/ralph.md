# Ralph

## Objective

**Problem.** A developer with a list of small, well-specified tasks wants them implemented while nobody watches. An
agent left alone can turn off its permission checks, change the rules or tests its work is judged by, and report a task
done that is not; when it gets stuck, nobody learns of it until someone comes back to look. Without protection against
that, the developer either watches every session or cannot trust what was done.

**Goal.** The developer comes back to a local branch holding only tasks whose checks passed under rules no agent in the
run could change, and is told which tasks were left unfinished and why. No agent in the run had more permissions than
the repository grants it.

## Overview

`harness ralph` launches [ralph-tui](https://github.com/subsy/ralph-tui) to work through a set of tasks unattended, one
agent process per task, from Beads or a PRD file. `harness` supplies what makes the run safe to leave alone: an isolated
coordinator checkout, a generated ralph-tui configuration, and a worker wrapper. The worker wrapper pins the agent's
permission mode, refuses permission bypass, restores and checks the tool-owned files around every agent launch, replays
the agent's verification from a tree the agent cannot write, and withholds completion until that replay passes.
Lifecycle events fire from the launcher, and their actions run in the launcher process, never inside a worker.

Commands are named `harness <command>` on this page. The examples run them through the repository's wrapper,
`./harnessw <command>`, from the root of a checkout; with a global install, `harness <command>` is equivalent
([wrapper](wrapper.md)).

## When to use it

- An epic or a PRD is broken into stories that an agent can implement one at a time.
- You want the work done while you are away, on a local branch you review and deliver afterwards.
- You want the same safety rules for Claude Code and Codex workers.

Use an interactive session instead when the work needs decisions only a person can make as it goes.

Prerequisites:

| Need | Why |
| --- | --- |
| `modules.ralph: true` and a `ralph` section in the [profile](profile.md#ralph) | defaults and the task shims |
| `ralph-tui` installed | it schedules and runs the tasks |
| `bd` installed, for `--tracker beads` | the task source |
| the agent CLI (`claude` or `codex`) installed and declared in `agents` | the worker |
| a clean working tree | runs start from committed code |
| `harness verify` working in this repository | workers must produce a receipt |

`harness doctor` checks the tool versions against the supported range.

## How it works

### Launch

`harness ralph run`:

1. Refuses when the working tree has uncommitted changes, when another Ralph run holds the clone's lock
   (`ralph.lock` in the configured state directory: `state.dir`, default `.work`), or, with a PRD, when stories are
   claimed by a standalone session.
2. Takes the PRD's owner lock for the whole run, so standalone `harness task` commands cannot change it meanwhile.
3. Creates the **coordinator**: a managed worktree `ralph-<session-id>` in the configured worktrees directory
   (`worktrees.dir`, default `.work/worktrees`), on branch `work/ralph-<session-id>`, from `HEAD`. The session id is
   12 hexadecimal characters.
4. Writes `<coordinator>/.ralph-tui/config.toml` ([configuration](#managed-and-inherited-configuration)).
5. With Beads, puts a `bd` shim first on ralph-tui's PATH. The shim presents task dependencies in the field names
   ralph-tui reads and lists only the tasks of this run that can run: ready, in progress, or waiting only on other
   tasks of the run.
6. Runs ralph-tui in the coordinator with the agent, tracker, epics, iteration budget and `--headless` you chose.
7. Records the launch settings and whether the run finished in `session.json` in the coordinator's state directory.

The primary checkout is never switched, reset or merged into.

### How tasks run

ralph-tui decides whether to run tasks one after another or in parallel workers.

| Mode | Where each task runs | How the result lands |
| --- | --- | --- |
| Sequential | the worker wrapper creates a task worktree `ralph-<session-id>-<task>` in the worktrees directory | after a passing replay, the wrapper commits the task there and fast-forwards the coordinator branch to it |
| Parallel (`--workers` > 1) | ralph-tui's worker checkouts under `<session-id>/` in the configured Ralph worktrees directory (`ralph.worktrees_dir`, default `.work/ralph-worktrees`) | after a passing replay, ralph-tui commits each task onto `ralph/session-<session-id>` |

Each task commit carries a verification summary in its body: the lanes and patterns run, and the reason for each
skipped lane. With Beads, the same summary is added as a comment on the task after integration; a comment that fails
costs only the comment, never the verified task. In a database the repository synchronises, the comment travels where
that synchronisation sends it ([tasks](tasks.md#coexistence)).

### The worker contract

ralph-tui starts every agent through the worker wrapper, `ralph-worker <agent>`, which the launcher writes into the
coordinator's configuration as a command on its own binary, by absolute path. A worker therefore never resolves
`harness` from `PATH` and never starts through a `harnessw` the agent could have edited. For each task, the worker
wrapper:

1. Refuses to run unless started by `harness ralph`.
2. Refuses permission-bypass flags: `--dangerously-skip-permissions`, `--dangerously-bypass-approvals-and-sandbox`,
   `--yolo` and the `danger-full-access` sandbox. When such a flag comes from an inherited or global ralph-tui
   configuration, the message names the key that injected it.
3. Reads the profile, policy and hooks file from the session's base commit, not from the worker's checkout, so an agent
   cannot weaken the rules its own result is checked against. The [standing context](context.md) injected into the
   worker's session is read from the base commit too, so an agent cannot rewrite its own instructions between attempts.
4. Runs the **tamper check**, first half: restores every tool-owned file in the task checkout (the rendered rules,
   the owned keys of the native settings files, the harness hook entries, the wrapper `harnessw` and its pin
   `.harness/wrapper.yaml`) from the session's base commit.
5. Pins the permission mode:
   - Claude Code: `--permission-mode auto`, plus `worker.deny` as disallowed tools.
   - Codex: the `workspace-write` sandbox with network access. Besides the task checkout, the only writable roots are
     the clone's shared state directory, mise's cache directory and, with Beads, the clone's `.beads/`. `worker.deny`
     applies through the rendered rules, which the tamper check keeps intact; that is why Codex must have `role: worker`
     to run as a worker ([agents](agents.md#roles)).
6. Claims the task as the run's actor (`ralph:<session-id>`), when using Beads.
7. Runs the profile's `bootstrap` once per task checkout.
8. Runs the agent with the task prompt and `HARNESS_WORKER=1` in its environment, buffering its output instead of
   streaming it. Every `harness` process the agent starts sees that variable and refuses to fire lifecycle events,
   and the agent's `attention` and `turn_end` hooks do nothing: an unattended run reports through `blocked`
   instead.
9. Runs the tamper check, second half: compares the tool-owned files with the base commit. Any difference fails the
   attempt and is reported by path: an agent that rewrote its own rules cannot complete.
10. When the agent exits successfully, reads the receipt the agent wrote with `harness verify`. A missing or malformed
    receipt is a failed attempt. Otherwise the wrapper replays the lanes and skip reasons it selected against the final
    content, with the profile from the base commit. The receipt only selects; the replay is the proof.
11. If the replay passes: commits and integrates (sequential mode), then prints the buffered output, including the
    agent's `<promise>COMPLETE</promise>`.
12. If anything fails: withholds the output, keeps it in `last-worker-output` in the task checkout's state directory,
    and exits non-zero. ralph-tui retries the task twice, then skips it and moves on.

Stopping Ralph stops the agent and any replayed checks, with every process they started.

Workers never commit, push, merge, close tasks, edit PRD status or fire lifecycle events themselves. The embedded prompt
says so. The binary refuses `harness lifecycle run` under `HARNESS_WORKER=1`, whatever spelling started it. As defence
in depth, the built-in base also denies `harness lifecycle` to every worker, rendered for each spelling `harness`
renders. The repository's `worker.deny` denies the rest ([policy](policy.md#a-node-api)). A worker that lacks a
permission reports the task as blocked.

### Lifecycle events

The launcher, the `harness ralph run` or `harness ralph resume` process, stays alive while ralph-tui runs. It fires
[lifecycle events](lifecycle.md) in its own process, after the worker that produced the result has exited:

| When | Event | Scope |
| --- | --- | --- |
| a task's verified commit lands on the run branch | `completed` | `task` |
| right after those `completed` actions succeed, when the work is concluded | `deliver` | `task` |
| the run finishes | `completed` | `run` |
| right after those `completed` actions succeed, when the work is concluded | `deliver` | `run` |
| ralph-tui skips a task after its retries | `blocked` | `task` |
| the run stops unfinished | `blocked` | `run` |

`blocked` carries the reason in `HARNESS_REASON`, such as the task that was skipped or the tasks left waiting, so a
declared action can tell a person, for example with an `http` notification ([lifecycle](lifecycle.md)).

Only actions declared with `auto: true` run at these points, read from the run's base commit, so a worker cannot add
or change an action its own task triggers. No lifecycle action ever runs inside a worker: `harness lifecycle run`
refuses there, and trigger points inside a worker fire nothing. A failing action is reported on stderr and recorded in
the [lifecycle log](lifecycle.md#the-lifecycle-log); it never changes the run's exit code.

### Managed and inherited configuration

| `ralph.config` | Base for `<coordinator>/.ralph-tui/config.toml` | Use when |
| --- | --- | --- |
| `managed` (default) | generated from the profile: one agent entry per declared agent, with `ralph.agents.<name>.model` and `effort`, and `max_iterations` | the repository has no ralph-tui setup of its own, or wants `harness` to own it |
| `inherit` | the repository's root `.ralph-tui/config.toml` (agents, templates, options) | the repository already runs ralph-tui directly and wants to reuse that setup |

In both modes these keys are forced, whatever the base says:

| Forced | Value |
| --- | --- |
| each agent's command | `<binary> ralph-worker <agent>`, where `<binary>` is the absolute path of the launcher's own `harness` binary |
| Claude agent `skipPermissions` | `false` |
| Codex agent `fullAuto` | `false` |
| error handling | retry, 2 retries, `continueOnNonZeroExit = false` |
| parallel worker directory | `<ralph.worktrees_dir>/<session-id>`, by default `.work/ralph-worktrees/<session-id>` |
| parallel target branch | `ralph/session-<session-id>`, never a direct merge |
| Beads working directory | the primary checkout, so every worktree shares the clone's database |

The root `.ralph-tui/config.toml` is never written, and is read only with `inherit`. ralph-tui's global configuration,
`~/.config/ralph-tui/config.toml`, which ralph-tui merges into every run it starts, is never written either. The
launcher reads it for one purpose: to find and name a bypass setting it would inject into a harness-launched run
([ADR 0012](../adrs/0012-write-boundary.md)). The launcher does not support trackers other than `beads` and `json` (PRD)
in an inherited configuration.

### The prompt

Workers receive the embedded prompt unless the repository provides `.harness/ralph/prompt.hbs`, a ralph-tui Handlebars
template (`{{taskId}}`, `{{taskTitle}}`, `{{taskDescription}}`, `{{acceptanceCriteria}}`, `{{prdContent}}`,
`{{recentProgress}}`). The embedded prompt tells the agent to read `AGENTS.md`, implement only its task in the worktree
it is in, run `./harnessw verify` with explicit lanes or skip reasons, leave completion, commits and integration to
Ralph, report missing permissions as blocked, and signal completion only after verifying. `harness explain ralph` prints
it. Start an override from that text and keep its first line: it carries the task identity the worker reads.

### Model, effort and iterations

| Setting | Default from the profile | Per run |
| --- | --- | --- |
| Model | `ralph.agents.<agent>.model` | `--model <id>` (a full model id) |
| Effort | `ralph.agents.<agent>.effort` (Claude `--effort`, Codex `model_reasoning_effort`) | `--effort <level>` |
| Iterations | `ralph.max_iterations` | `--iterations <n>`; `0` is unlimited |
| Agent | `ralph.default_agent` | `--agent <name>` |

The worker substitutes the model and effort into the agent's invocation. In sequential mode the iteration budget
covers the whole session; in parallel mode it applies per task.

### Finishing, resuming, cleaning

A run is **finished** when ralph-tui completes and, with Beads, no task of the run is left open. A run is
**unfinished** when ralph-tui stops early, or tasks remain open: tasks waiting on a blocker outside the run, and tasks
skipped after failing. `harness ralph run` then lists those tasks, keeps the coordinator, and prints the command to
resume.

`harness ralph resume <session-id>` starts ralph-tui again in the same coordinator, with the same tracker, PRD, epics
and Beads actor; each task returns to its own task worktree. `--agent`, `--model`, `--effort`, `--iterations` and
`--headless` given on resume replace the earlier values and persist for later resumes. Parallel workers of a resumed
run get a new directory, so the previous run's worker checkouts stay intact for repair. Resume refuses while ralph-tui
still runs in that coordinator.

`harness ralph clean` acts only on sessions `harness` recorded in the state directory:

| Removes | Keeps, and says why |
| --- | --- |
| clean worktrees of finished sessions: coordinator, task worktrees, parallel worker checkouts | worktrees with uncommitted changes |
| branches of recorded sessions that have landed: every patch is on the default branch, merged, cherry-picked or rebased (matched with `git cherry`) | worktrees and branches of unfinished sessions |
| empty session directories | branches whose commits are not on the default branch, and branches checked out in a worktree |

For each recorded branch that has landed, `clean` fires the `landed` event once, before it removes the session's
worktrees. Its `run` and `http` actions run in the primary checkout, and an `agent` action runs in a fresh worktree of
the default branch ([lifecycle](lifecycle.md#agent)). Each recorded branch fires `landed` at most once successfully. To
re-fire a failed `landed`, run `harness lifecycle run landed --session <session-id>` manually. A failing action never
changes `clean`'s exit code.

It refuses to run while a Ralph run holds the clone's lock. Branches and worktrees `harness` did not record, such as
`ralph/session-*` branches from direct ralph-tui runs, are never touched.

### Delivery

The result of a run is a local branch with one commit per task, starting from the commit the run was launched from:
`work/ralph-<session-id>` in sequential mode, `ralph/session-<session-id>` in parallel mode. The binary contains no
delivery logic of its own; the repository's `deliver` actions define what delivery means.

| Delivery | How |
| --- | --- |
| Automatic | `deliver` actions with `auto: true` run from the launcher at the `task` or `run` scope. A run-scope action that opens a pull request opens one per run |
| After judgement | Choosing which task commits go together, splitting them across branches, rewording messages: a coordinating agent does this with a skill, or a person by hand, then runs `harness lifecycle run deliver` on each resulting branch |

Most runs need judgement: the task commits are small and numerous, and a reviewer reads a pull request, not a run.
Once the delivered work is merged, `harness ralph clean` fires `landed` and removes what the run left behind.

### The convention in `AGENTS.md` and `.gitignore`

With the Ralph module on, `harness render` writes the run convention, with the configured directories, into the
`ralph` block of `AGENTS.md`, and the directories into the `ralph` block of `.gitignore`. With the defaults:

```markdown
<!-- harness:ralph -->
Ralph runs start from committed code. Each run has a coordinator worktree, `.work/worktrees/ralph-<session-id>`, on
branch `work/ralph-<session-id>`; ralph-tui keeps the run's state in the coordinator's `.ralph-tui/`. Tasks run one at
a time in `.work/worktrees/ralph-<session-id>-<task>`, or in parallel in `.work/ralph-worktrees/<session-id>/` with
their commits landing on `ralph/session-<session-id>`. A Ralph worker implements only its own task, in the worktree it
is in, and runs the repository's checks; it never commits, pushes, merges, closes tasks or edits PRD status, and it
reports a missing permission as blocked work. The result of a run is a local branch with one commit per task.
<!-- /harness:ralph -->
```

```gitignore
# harness:ralph
/.ralph-tui/
/.work/ralph-worktrees/
# /harness:ralph
```

`/.ralph-tui/` is left out when the repository tracks files under it, such as a root configuration used with
`ralph.config: inherit`.

These are convention blocks: they state the convention as a fact and name no `harness` command. After
[`harness remove --eject`](remove.md#eject) the markers go and the text and ignore entries stay: ralph-tui keeps running
from its own configuration, and people and agents know where runs live and what a worker may do. Nothing then enforces
the worker contract: there is no bypass refusal, tamper check or replay. `harness remove --module ralph` removes the
blocks and leaves coordinators and run branches in place.

## Reference

```text
harness ralph run [--agent <name>] [--tracker beads|prd] [--prd <path>] [--epic <id>]... [--workers <n>]
                  [--model <id>] [--effort <level>] [--iterations <n>] [--headless]
harness ralph resume <session-id> [--agent <name>] [--model <id>] [--effort <level>] [--iterations <n>] [--headless]
harness ralph clean
harness ralph-worker <agent> [agent args...]
```

`harness ralph run`:

| Flag | Default | Meaning |
| --- | --- | --- |
| `--agent <name>` | `ralph.default_agent` | Worker agent; must be declared in `agents` |
| `--tracker beads\|prd` | `tracker.default`, when it is `beads` or `prd` | Task source. Required when `tracker.default` is `none` |
| `--prd <path>` | none | PRD JSON file; required with `--tracker prd` |
| `--epic <id>` | all ready, unowned tasks | Limit a Beads run to the children of this epic. Repeatable |
| `--workers <n>` | `1` | Maximum parallel workers, 1 to 32 |
| `--model <id>` | profile | Model for this run |
| `--effort <level>` | profile | Effort for this run |
| `--iterations <n>` | `ralph.max_iterations` | Iteration budget; `0` is unlimited |
| `--headless` | off | Run ralph-tui without its terminal interface |

`harness ralph resume <session-id>` takes the flags listed in its synopsis; tracker, PRD, epics and workers are those
of the original run.

`harness ralph-worker` is internal: it is the agent command the launcher writes into the coordinator's ralph-tui
configuration, on the launcher's own binary. Do not call it yourself; it refuses to run outside a `harness ralph` run.

| Exit code | `run` / `resume` | `clean` |
| --- | --- | --- |
| 0 | the run finished | cleaned (items kept are listed with reasons) |
| 1 | error: invalid flags, ralph-tui or the agent missing, unknown session | error |
| 3 | refused (uncommitted changes, another run holds the lock, PRD claimed by a standalone session, ralph-tui still running in the coordinator), or the run is unfinished, which fires `blocked`: fix what blocks it, then resume | a Ralph run holds the lock |

Paths below show the defaults, `state.dir: .work`, `worktrees.dir: .work/worktrees` and
`ralph.worktrees_dir: .work/ralph-worktrees` ([profile](profile.md)).

| Path | Written by |
| --- | --- |
| `.work/ralph.lock` | `run`, `resume`, `clean` |
| `.work/worktrees/ralph-<session-id>/` and branch `work/ralph-<session-id>` | `run`: the coordinator |
| `<coordinator>/.ralph-tui/` | `run` (config), ralph-tui (its session, logs, progress) |
| `<coordinator>/.work/session.json` | `run`, `resume`: launch settings and the finished flag |
| `.work/worktrees/ralph-<session-id>-<task>/` | the worker, in sequential mode |
| `.work/ralph-worktrees/<session-id>[-<n>]/` | ralph-tui, in parallel mode |
| `<task checkout>/.work/last-worker-output` | the worker, when a task fails |
| `<prd>.owner.lock` | `run`, `resume`, for the run's duration |
| `.work/lifecycle.jsonl` | `run`, `resume`, `clean`, for every lifecycle event they fire |

## Examples

### A Node API: a Beads epic, sequential

```sh
bd create --type epic --title "Invoice rounding" --no-inherit-labels --body-file epic.md --silent
bd create --parent acme-api-a10 --title "US-001: Round at the domain boundary" --no-inherit-labels --body-file us-001.md --silent
bd create --parent acme-api-a10 --title "US-002: Show rounded totals in the API" --no-inherit-labels --body-file us-002.md --silent
bd dep add acme-api-a12 acme-api-a11
git status --short        # must be empty: the run starts from committed code
./harnessw ralph run --agent claude --tracker beads --epic acme-api-a10 --headless
```

If the run stops unfinished:

```sh
./harnessw ralph resume 3f9c1a7b2e44 --iterations 10
```

### A Go repository: a PRD with parallel workers on Codex

```sh
./harnessw ralph run --agent codex --tracker prd --prd .work/prds/export-csv.json --workers 3 --effort high
```

### A Terraform repository

The `terraform` [preset](presets.md#the-presets) writes `modules.ralph: false` into a profile that does not set
`modules.ralph` yet: `plan` needs credentials and judgement a worker must not hold. A profile that already sets it
keeps its own value.

### Running it from an agent session

A coordinating Claude Code session started with `claude --permission-mode auto` can launch, monitor and deliver a run:
the module's allow entries cover each rendered spelling of `harness ralph`. For delivery it arranges the task commits
on branches, guided by the harness skill, then runs `harness lifecycle run deliver` on each; the seeded `ask` entry
for that command asks a person first ([policy](policy.md#the-seeded-entry)).

## Guidelines

Preparing a Beads epic:

- Make each task's description self-contained: the story, the rules the worker needs, and, last, a
  `## Acceptance Criteria` section with the criteria and quality gates as a `- [ ]` checklist. ralph-tui reads the
  criteria from that section, not from `bd`'s acceptance field.
- Put dependencies on the story that needs them, not on the epic: `bd ready` treats every descendant of a blocked
  bead as blocked.
- Use `bd create --parent … --no-inherit-labels` unless the children should carry the epic's labels.
- Always pass `--epic`. Without it, the run takes every ready, unowned bead in the clone.

Running:

- Do commit before launching, including any change to `.harness/` or `.agents/`: the coordinator sees only committed
  code, and the worker reads the rules from the base commit.
- Do set a model and effort per agent in the profile, so per-run overrides have something to replace.
- Don't edit a PRD file by hand during a run.
- Don't run ralph-tui yourself inside a coordinator; use `resume`.
- Don't delete the clone's Ralph lock (`.work/ralph.lock` by default) by hand unless no Ralph process is alive.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| Refused: uncommitted changes | Runs start from committed code | Commit or stash, then run |
| Every task fails with completion withheld | Workers do not run `harness verify`, or the replay fails | Read `last-worker-output` in a task checkout's state directory; run the lanes yourself there |
| An attempt fails naming a tool-owned file | The tamper check found the agent changed a rendered rule, an owned settings key, a hook entry, `harnessw` or `.harness/wrapper.yaml` | Change rules in `.harness/` and commit before the run; the worker reads them from the base commit |
| Refused: permission bypass | An inherited or global ralph-tui config sets `skipPermissions`, `fullAuto` or a bypass flag | Remove the key from that config; harness-launched runs never bypass |
| Codex worker refused | Codex is declared with `role: interactive` | Set `agents.codex.role: worker` and render, or use Claude workers |
| Run unfinished with tasks "waiting" | They depend on beads outside the run | Finish or unblock those, then `harness ralph resume <id>` |
| `--model` has no effect with `inherit` | The inherited agent entry has no model to replace | Add a model to it, or switch to `managed` |
| Resume refused: ralph-tui still running | A ralph-tui process outlived its launcher | Stop it; if no Ralph process is alive, delete `<coordinator>/.ralph-tui/ralph.lock` |
| `clean` keeps a branch | Its commits are not on the default branch | Deliver it, or delete it with `git branch -D` once you are sure |
| One pull request per run, too large to review | A run-scope `deliver` action with `auto: true` | Remove `auto` from that action and deliver after judgement |

## Coexistence with direct ralph-tui use

- A root `.ralph-tui/` belongs to whoever runs ralph-tui directly. Harness-launched runs never write it, and read it
  only with `ralph.config: inherit`.
- The global `~/.config/ralph-tui/config.toml` is never written; it is read only to refuse and name a bypass setting
  before it reaches a harness-launched worker.
- Direct runs keep their own configuration, including bypass settings; the worker contract applies only to runs
  launched by `harness ralph`.
- `harness ralph clean` never touches sessions it did not record: direct runs' branches, ralph-tui's sibling
  `../.ralph-worktrees/` directories and Claude Code's `.claude/worktrees/` are left alone.
- One `harness ralph` run per clone at a time; direct runs are not counted.

See [compatibility](compatibility.md#ralph-tui).
