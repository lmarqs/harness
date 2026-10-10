# Tasks

## Objective

**Problem.** A developer who works on one task of a tracker in its own checkout, by hand or through an agent, needs
the tracker to show who holds the task and to mark it finished only once its checks pass. Updated by hand, the tracker
falls behind the work, and a task is closed before its checks ran. Teams use different trackers, or none, so a tool
built around one tracker serves only the teams that chose it.

**Goal.** Whatever tracker a team uses, a task in it shows who works on it and counts as finished only after its checks
pass.

## Overview

`the-cook` binds a [managed workspace](workspaces.md) to a task and fires [lifecycle events](lifecycle.md) around the
work on it. It contains no code for any task tracker: it never reads or writes a tracker's data and never checks that a
task id exists. A tracker is wired by actions the repository declares on the events `start`, `completed` and `blocked`,
which read the task id and the actor from the environment ([ADR 0022](../adrs/0022-tool-integration.md)).

Three pieces make this work:

- **Task binding.** `the-cook workspace create <name> --task <id>` records a free-form task id. Every event fired in
  that workspace carries it in `COOK_TASK`, as well as the workspace's actor in `COOK_ACTOR`, which every managed
  workspace has.
- **The `completed` guard.** `completed` with scope `task` is refused (exit 3) without a valid
  [verification receipt](verify.md) when the verify module is on, whoever fires it.
- **Trackers wired by actions.** The `beads` [recipe](recipes.md#beads) writes the actions for Beads, a task tracker
  that keeps its database in `.beads/` and is used through its `bd` command. Any other tracker is wired the same way
  with its own command line, such as GitHub issues through `gh`.

Commands are named `the-cook <command>` on this page. The examples run them through the repository's wrapper,
`./cook <command>`, from the root of a checkout; with a global install, `the-cook <command>` is equivalent
([wrapper](wrapper.md)).

## When to use it

- You work on tasks of a tracker in managed workspaces and want the tracker updated when work starts, finishes or stops.
- You want a task to count as finished only after `the-cook verify` passed on its final content.
- You run an unattended loop through [`the-cook worker`](ralph.md), which fires `completed` for each verified attempt.

Without a tracker, leave out `--task`: workspaces and `the-cook verify` work without tasks.

## How it works

### Task binding

`the-cook workspace create <name> --task <id>` records the id in the workspace's session (`session.json` in the
workspace's state directory) next to its base commit and its actor, `<user>:<name>:<random>`. The id is free-form: a
bead id such as `acme-api-7f2`, an issue number such as `412`, or any string the team's tracker understands. `the-cook`
does not interpret it.

| In a workspace bound to a task | Value |
| --- | --- |
| Scope of the events fired there | `task`, unless `--scope` says otherwise ([scopes](lifecycle.md#scopes)) |
| `COOK_TASK` | the id given with `--task` |
| `COOK_ACTOR` | the workspace's actor, `<user>:<name>:<random>` |

| Event | Fired, with scope `task` |
| --- | --- |
| `start` | by `the-cook workspace create --task`, in the new workspace |
| `verified` | by every `the-cook verify` that passes there |
| `completed` | by [`the-cook worker`](ralph.md) after a verified attempt in a workspace bound to a task, or by a person or agent with `the-cook lifecycle run completed` |
| `deliver` | by `the-cook worker` after `completed` when the work is concluded, or by `the-cook lifecycle run deliver` |
| `blocked` | by a `the-cook` command run without a terminal that exits 3, with `COOK_REASON` set to its one-line refusal |

At an automatic trigger point only actions declared with `auto: true` run. A failing automatic event is reported on
stderr and recorded in the [lifecycle log](lifecycle.md#the-lifecycle-log), and never changes the exit code of the
command that fired it. `the-cook lifecycle run` runs exactly one event, every action of it for the scope, so after a
manual `completed`, delivery is its own `the-cook lifecycle run deliver`.

### The `completed` guard

`completed` with scope `task` is refused with exit 3 when the verify module is on (`modules.verify: true`) and the
checkout has no valid receipt (`the-cook verify --check` fails). No action runs, and the refusal is recorded in the
lifecycle log. The guard is a [built-in guard](lifecycle.md#built-in-guards): it applies whoever fires the event, a
person, an agent or `the-cook worker`, and no repository file removes it. A tracker action on `completed` therefore
closes a task only after its checks passed on its final content.

`the-cook worker` fires `completed` only after it replayed the agent's verification itself, so in a worker the guard and
the replay agree. It fires `completed` and `deliver` only in a workspace bound to a task; in a workspace without one,
such as a loop runner's, it fires no event, and whoever drives the run fires the run-scope events.

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

#### The Beads recipe

`the-cook recipe apply beads` writes these entries for Beads 1.3.1, after showing them as a diff
([recipes](recipes.md#how-apply-works)):

```yaml
# .cook/hooks.yaml
lifecycle:
  start:
    - name: bead-start
      run: bd update "$COOK_TASK" --status in_progress --assignee "$COOK_ACTOR"
      auto: true
      scope: [task]
  completed:
    - name: bead-close
      run: bd close "$COOK_TASK" --reason "verified"
      auto: true
      scope: [task]
  blocked:
    - name: bead-comment
      run: bd comments add "$COOK_TASK" "$COOK_REASON"
      auto: true
      scope: [task]
```

```yaml
# .cook/config.yaml
agents:
  codex:
    writable_roots: [.beads]
```

```yaml
# .cook/policy.yaml
worker:
  deny:
    - [bd, close]
```

- The `completed` action closes the bead from the process that fired the event, and a worker's agent is denied
  `bd close`, so a bead closes only through the guarded event.
- Codex workers get `.beads/` as a writable root, so `bd` commands the agent runs can update the database.
- `bd` refuses a missing id, so an action that runs with `COOK_TASK` unset fails instead of touching another bead.
- Nothing initialises, configures or localises a Beads database. Creating one is `bd`'s own job (`bd init`).
- When ralph-tui runs with its Beads tracker, ralph-tui updates bead status itself ([ralph](ralph.md)). The recipe's
  actions matter for interactive work in managed workspaces.

#### GitHub issues with `gh`

No builtin recipe holds these; a repository declares them itself. The task id is the issue number:

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
      run: gh issue comment "$COOK_TASK" --body "Verified on $COOK_BRANCH: $COOK_SUMMARY"
      auto: true
      scope: [task]
  blocked:
    - name: issue-blocked
      run: gh issue comment "$COOK_TASK" --body "Blocked: $COOK_REASON"
      auto: true
      scope: [task]
```

The issue stays open until the team closes it, for example through a pull request that says `Closes #412`. These
actions write to the team's record, so declare them only where the team wants that record written from the work.

## Reference

```text
the-cook workspace create <name> --task <id>
the-cook lifecycle run completed [--scope task]
```

| Variable | Value | Set |
| --- | --- | --- |
| `COOK_TASK` | the id given with `--task` | every event fired in a workspace bound to a task |
| `COOK_ACTOR` | the workspace's actor, `<user>:<name>:<random>` | every event fired in a managed workspace |
| `COOK_REASON` | the one-line refusal of the command that exited 3 | `blocked`, when it fires at a trigger point |

| Exit code of `completed`, scope `task` | Meaning |
| --- | --- |
| 0 | Every action passed, or none is declared for the scope |
| 2 | An action failed |
| 3 | Refused: no valid receipt while the verify module is on; no action ran |

The full flags and exit codes are in [workspaces](workspaces.md#reference) and [lifecycle](lifecycle.md#reference).
Files read: `session.json` in the workspace's state directory, `verified.json` for the guard, `.cook/hooks.yaml`.
Files written: the lifecycle log; whatever the actions write.

## Examples

### A Node API with Beads

```sh
./cook recipe apply beads
./cook workspace create invoice-rounding --task acme-api-7f2   # bead-start marks acme-api-7f2 in progress
cd .work/workspaces/invoice-rounding
# edit, test
./cook verify --lane unit=invoice --skip e2e="no route, controller or guard changed"
./cook lifecycle run completed                                # bead-close closes acme-api-7f2
./cook lifecycle run deliver
```

`bd ready`, `bd show`, `bd search` and the rest of `bd` keep working as usual.

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
- Don't let a worker's agent close tasks itself; deny the tracker's close command in `worker.deny`, as the Beads recipe
  does.
- Don't share task ids across clones of a clone-local tracker such as Beads: they mean something only where the
  tracker's data is.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| `lifecycle run completed` exits 3 and runs no action | No valid receipt, or a file changed after `verify` | `the-cook verify …`, then fire `completed` again |
| The tracker did not change when the workspace was created | The `start` action is not `auto: true`, is not scoped to `task`, or failed | `the-cook lifecycle list`; the lifecycle log shows the failure; `the-cook lifecycle run start` retries it |
| A Beads action fails with a missing id | `completed` or `blocked` was fired by hand, with scope `task`, in a workspace created without `--task` | Bind the workspace with `--task`; under ralph-tui's Beads tracker, ralph-tui updates the bead itself |
| `doctor` reports `bd` or `gh` not on `PATH` | A declared action names a program this machine lacks | Install it, or remove the action |
| A bead is assigned to `<user>:<workspace>:<random>` | That is the workspace's actor, from `COOK_ACTOR` | Nothing to do; the actor names the workspace that holds the task |

## Coexistence

- The binary contains no code for any task tracker or hosting platform. What reaches a tracker or GitHub is what the
  repository's own actions write, and a pull request is opened only by a `deliver` action the repository declares.
- An existing `.beads/`, including one synchronised across machines or tracked in git, is the tracker's own data:
  `the-cook` neither reads nor configures it, and what the actions write into it travels where its synchronisation
  sends it ([ADR 0012](../adrs/0012-write-boundary.md)).
- `the-cook` does not change `core.hooksPath`.

See [compatibility](compatibility.md#beads).
