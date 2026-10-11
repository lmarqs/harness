# Tasks

## Objective

**Problem.** A developer who works on one task of a tracker in its own checkout, by hand or through a coding agent,
needs the tracker to show who holds the task and to mark it finished only once its checks pass. Updated by hand, the
tracker falls behind the work, and a task is closed before its checks ran. Teams use different trackers, or none, so a
tool built around one tracker serves only the teams that chose it.

**Goal.** Whatever tracker a team uses, a task in it shows who works on it and counts as finished only after its checks
pass.

## Overview

Tasks serve two stages of the [development lifecycle](lifecycle.md): Plan, where a task id is all `the-cook` takes from
a tracker, and Complete, where the `completed` guard decides when a task counts as finished.

`the-cook` binds a [managed workspace](workspaces.md) to a task and fires [lifecycle events](lifecycle.md) around the
work on it. It contains no code for any task tracker: it never reads or writes a tracker's data and never checks that a
task id exists. A tracker is wired by actions the repository declares on the events `start`, `completed` and `blocked`,
which read the task id and the actor from the environment ([ADR 0022](../adrs/0022-tool-integration.md)).

Three pieces make this work:

- **Task binding.** `the-cook workspace create <name> --task <id>` records a free-form task id, and so does
  `the-cook make <recipe> --task <id>` for the workspace it creates. Every event fired in that workspace carries it in
  `COOK_TASK`, as well as the workspace's actor in `COOK_ACTOR`, which every managed workspace has.
- **The `completed` guard.** `completed` with scope `task` is refused (exit 3) without a valid
  [verification receipt](verify.md) when the verify module is on, whoever fires it.
- **Trackers wired by actions.** The repository declares the actions with the tracker's own command line, or applies
  a [station](stations.md#entries) that holds them as entries. Every tracker is wired the same way.

Commands are named `the-cook <command>` on this page. The examples run them through the repository's wrapper,
`./cook <command>`, from the root of a checkout; with a global install, `the-cook <command>` is equivalent
([wrapper](wrapper.md)).

## When to use it

- You work on tasks of a tracker in managed workspaces and want the tracker updated when work starts, finishes or stops.
- You want a task to count as finished only after `the-cook verify` passed on its final content.
- You run an unattended loop through [`the-cook attempt`](attempts.md), which fires `completed` for each verified
  attempt.
- A task is one instance of a kind of change the repository has a [recipe](recipes.md) for:
  `the-cook make <recipe> --task <id>` takes it through the stages and fires the task's events
  ([a task run by a recipe](#a-task-run-by-a-recipe)).

Without a tracker, leave out `--task`: workspaces and `the-cook verify` work without tasks.

## How it works

### Task binding

`the-cook workspace create <name> --task <id>`, or `the-cook make <recipe> --task <id>`, records the id in the
workspace's session (`session.json` in the workspace's state directory) next to its base commit and its actor,
`<user>:<name>:<random>`. The id is free-form: a tracker's task id such as `acme-api-7f2`, an issue number such as
`412`, or any string the team's tracker understands. `the-cook` does not interpret it.

| In a workspace bound to a task | Value |
| --- | --- |
| Scope of the events fired there | `task`, unless `--scope` says otherwise ([scopes](lifecycle.md#scopes)) |
| `COOK_TASK` | the id given with `--task` |
| `COOK_ACTOR` | the workspace's actor, `<user>:<name>:<random>` |

| Event | Fired, with scope `task` |
| --- | --- |
| `start` | by `the-cook workspace create --task` or `the-cook make --task`, in the new workspace |
| `verified` | by every `the-cook verify` that passes there |
| `completed` | by [`the-cook attempt`](attempts.md) after a verified attempt in a workspace bound to a task, by `the-cook make` after its replay passes, or by a person or a coordinating worker with `the-cook lifecycle run completed` |
| `deliver` | by `the-cook attempt` or `the-cook make` after `completed` when the work is concluded, or by `the-cook lifecycle run deliver` |
| `blocked` | by a `the-cook` command run without a terminal that exits 3, or by `the-cook make --unattended` when it exits 3 before the run starts, with `COOK_REASON` set to its one-line refusal; by a recipe run that stops, with the stage, the step and the reason; never by a handoff that pauses an attended run |

At an automatic trigger point only actions declared with `auto: true` run. A failing automatic event is reported on
stderr and recorded in the [lifecycle log](lifecycle.md#the-lifecycle-log), and never changes the exit code of the
command that fired it. `the-cook lifecycle run` runs exactly one event, every action of it for the scope, so after a
manual `completed`, delivery is its own `the-cook lifecycle run deliver`.

### The `completed` guard

`completed` with scope `task` is refused with exit 3 when the verify module is on (`modules.verify: true`) and the
checkout has no valid receipt (`the-cook verify --check` fails). No action runs, and the refusal is recorded in the
lifecycle log. The guard is a [built-in guard](lifecycle.md#built-in-guards): it applies whoever fires the event, a
person, a worker or `the-cook attempt`, and no repository file removes it. A tracker action on `completed` therefore
closes a task only after its checks passed on its final content.

`the-cook attempt` fires `completed` only after it replayed the worker's verification itself, so in an attempt the
guard and the replay agree. It fires `completed` and `deliver` only in a workspace bound to a task; in a workspace
without one, such as a loop runner's, it fires no event, and whoever drives the run fires the run-scope events.

### A task run by a recipe

`the-cook make <recipe> --task <id>` takes one task through the stages in one pass of a [recipe](recipes.md)
([the run](recipes.md#the-run)). It creates the recipe's workspace from the default branch, bound to the task, and the
task's events come from the run:

| Stage of the run | Event, scope `task` | What a tracker action sees |
| --- | --- | --- |
| Start: the workspace is created | `start` | `COOK_TASK` and `COOK_ACTOR`, to mark the task in progress |
| Verify: the run runs the lanes the change selects and every lane the recipe requires, and writes the receipt | `verified` | `COOK_SUMMARY`, `COOK_RECIPE` and the inputs as `COOK_INPUT_<NAME>` |
| Complete: the replay from the base passes, and with `--commit` the work is committed | `completed`, then `deliver` when the work is concluded | the same, to close the task |
| The run stops: a step fails, Verify cannot select a lane the lane rules demand, a lane or the replay fails, or a handoff is reached in a run started with `--unattended` | `blocked` | `COOK_REASON`, with the stage, the step and the reason |

The `completed` guard holds as for any task. A stopped run keeps its workspace and the task's binding:
`the-cook make --continue` resumes it, and the run's later events still carry the task id. A loop runner can name this
command, with `--unattended`, for each task it takes ([attempts](attempts.md#through-a-recipe)).

### Trackers

A tracker is wired by actions on `start`, `completed` and `blocked`, scoped to `task`, that run the tracker's own
command with the task id and the actor:

| Event | What the action does with the tracker |
| --- | --- |
| `start` | marks the task in progress and assigned to `$COOK_ACTOR` |
| `completed` | closes the task `$COOK_TASK`, or records that it passed its checks |
| `blocked` | records `$COOK_REASON` on the task |

The actions are the repository's own entries in `.cook/hooks.yaml`, reviewed like any other change. Switching
trackers changes those entries and nothing else. A program an action names that is not on `PATH` is a
[`the-cook doctor`](doctor.md) finding.

#### Tracker actions from a station

A [station](stations.md), one way of running the lifecycle such as `agentic-coding`, can hold these actions as
[entries](stations.md#entries). `the-cook station apply <name> --cookbook <source>@<version>` shows them in one diff
with the rest of the station and writes them into `.cook/hooks.yaml` ([stations](stations.md#how-apply-works)); from
then on they are the repository's own. Such a station usually also holds:

- an `unattended.deny` entry for the tracker's close command, so a task closes only through the guarded `completed`
  event, never from inside an attempt;
- a `writable_roots` value on each worker its [stages](stations.md#stages) declare whose ingredient opens sandbox
  roots, naming the tracker's data directory, so the tracker commands a sandboxed worker runs can update it
  ([profile](profile.md#workers)).

It may also list the tracker's [ingredient](ingredients.md), which applying the station pins, so `the-cook doctor`
checks the tracker's version against the range that ingredient supports. Nothing in the binary initialises, configures
or reads a tracker's data. Creating it is the tracker's own job.

#### GitHub issues with `gh`

A repository on GitHub declares these actions itself, with GitHub's `gh` command. The task id is the issue number:

```yaml
# .cook/hooks.yaml
lifecycle:
  start:
    - name: issue-assign
      run: gh issue edit "$COOK_TASK" --add-assignee "@me"
      auto: true
      scope: [task]
  completed:
    - name: issue-verified
      run: 'gh issue comment "$COOK_TASK" --body "Verified on $COOK_BRANCH: $COOK_SUMMARY"'
      auto: true
      scope: [task]
  blocked:
    - name: issue-blocked
      run: 'gh issue comment "$COOK_TASK" --body "Blocked: $COOK_REASON"'
      auto: true
      scope: [task]
```

The issue stays open until the team closes it. These actions write to the team's record, so declare them only where the
team wants that record written from the work.

## Reference

```text
the-cook workspace create <name> --task <id>
the-cook make <recipe> [--<input> <value>]... --task <id> [--unattended]
the-cook lifecycle run completed [--scope task]
```

| Variable | Value | Set |
| --- | --- | --- |
| `COOK_TASK` | the id given with `--task` | every event fired in a workspace bound to a task |
| `COOK_ACTOR` | the workspace's actor, `<user>:<name>:<random>` | every event fired in a managed workspace |
| `COOK_REASON` | the one-line refusal of the command that exited 3, or the step that stopped a recipe run and why | `blocked`, when it fires at a trigger point |

| Exit code of `completed`, scope `task` | Meaning |
| --- | --- |
| 0 | Every action passed, or none is declared for the scope |
| 2 | An action failed |
| 3 | Refused: no valid receipt while the verify module is on; no action ran |

The full flags and exit codes are in [workspaces](workspaces.md#reference), [lifecycle](lifecycle.md#reference) and
[`the-cook make`](recipes.md#the-cook-make).
Files read: `session.json` in the workspace's state directory, `verified.json` for the guard, `.cook/hooks.yaml`.
Files written: the lifecycle log; whatever the actions write.

## Examples

### A Node API with tracker actions from a station

The repository applies the station `agentic-coding` from the cookbook `github:acme/cookbook`, whose entries include the
actions for its tracker:

```sh
./cook station apply agentic-coding --cookbook github:acme/cookbook@v1.2.0
./cook workspace create invoice-rounding --task acme-api-7f2   # the start action marks the task in progress
cd .work/workspaces/invoice-rounding
# edit, test
./cook verify --lane unit=invoice --skip e2e="no route, controller or guard changed"
./cook lifecycle run completed                                # the completed action closes acme-api-7f2
./cook lifecycle run deliver
```

The station also adds the `implement-task` recipe to `.cook/recipes/`, so the same task can be made in one command:

```sh
./cook make implement-task --task acme-api-7f2 --commit
# start: claim-task; the recipe's steps in their own workspace; its lanes; the commit; completed: close-task
```

When the run stops, `release-task` records the stage, the step and the reason on the task, and the workspace stays for a
person to fix and `./cook make --continue`.

### A Go repository with GitHub issues

With the actions of [GitHub issues with `gh`](#github-issues-with-gh) declared:

```sh
./cook workspace create export-csv --task 412                  # issue-assign assigns issue 412
cd .work/workspaces/export-csv
./cook verify --lane test=./internal/export/...
./cook lifecycle run completed                                # issue-verified comments with the summary
```

### A Terraform repository without a tracker

Create workspaces without `--task` and declare no tracker actions; workspaces and `the-cook verify` work without tasks.

## Guidelines

- Do bind a workspace to one task with `--task`, so every event fired there names it.
- Do run `the-cook verify` before firing `completed`; the guard refuses it otherwise.
- Do keep tracker actions scoped to `task`, so a workspace without a task never runs them.
- Don't let a worker close tasks from inside an attempt; deny the tracker's close command in `unattended.deny`.
- Don't share task ids across clones of a clone-local tracker: they mean something only where the tracker's data is.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| `lifecycle run completed` exits 3 and runs no action | No valid receipt, or a file changed after `verify` | `the-cook verify …`, then fire `completed` again |
| The tracker did not change when the workspace was created | The `start` action is not `auto: true`, is not scoped to `task`, or failed | `the-cook lifecycle list`; the lifecycle log shows the failure; `the-cook lifecycle run start` retries it |
| A tracker action fails with a missing id | `completed` or `blocked` was fired by hand, with scope `task`, in a workspace created without `--task` | Bind the workspace with `--task` |
| `doctor` reports a program not on `PATH` | A declared action names a program this machine lacks | Install it, or remove the action |
| A task stays in progress after `the-cook make --task` stopped | The run stopped, fired `blocked` and kept the workspace | `the-cook make --status <workspace>` names the step; fix it and `the-cook make --continue <workspace>`, or discard its changes and `the-cook workspace remove` it to abandon the run |
| A task is assigned to `<user>:<workspace>:<random>` | That is the workspace's actor, from `COOK_ACTOR` | Nothing to do; the actor names the workspace that holds the task |

## Coexistence

- The binary contains no code for any task tracker or hosting platform. What reaches a tracker or a hosting platform is
  what the repository's own actions write, and a pull request is opened only by a `deliver` action the repository
  declares.
- A tracker's data in the clone, including data synchronised across machines or tracked in version control, is the
  tracker's own: `the-cook` neither reads nor configures it, and what the actions write into it travels where its
  synchronisation sends it ([ADR 0012](../adrs/0012-write-boundary.md)).
- `the-cook` installs and changes no version-control hooks.

See [compatibility](compatibility.md#task-trackers).
