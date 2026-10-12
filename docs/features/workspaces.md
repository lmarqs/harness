# Workspaces

## Objective

**Problem.** A developer who wants a coding agent to work on a task while they keep using their own checkout, or
several coding agents on several tasks at once, needs a separate working copy for each. Made by hand, each copy lands
somewhere different, starts from a commit nobody recorded, lacks the dependencies it needs to build, and can be deleted
along with work nobody committed.

**Goal.** Every task gets its own working copy, ready to build, starting from a known commit, in the place every
teammate and worker expects, and no copy is removed while it holds uncommitted work.

## Overview

Workspaces serve the Start stage of the [development lifecycle](lifecycle.md): a change starts in one, from a recorded
base, and removing it is where the Landed stage is detected.

`the-cook workspace` creates and removes managed task workspaces: separate checkouts of the clone, added and removed
through the [version-control contract](ingredients.md#the-version-control-contract), nested inside the clone in the
configured workspaces directory (`workspaces.dir`, default `.work/workspaces`), each on its own branch, with a recorded
starting commit, an actor identity and an optional task id. The task id binds the workspace to one task of the team's
tracker: every event fired there carries the id in `COOK_TASK` and the actor in `COOK_ACTOR` ([tasks](tasks.md)).
[`the-cook make`](recipes.md#the-cook-make) creates one the same way for each [recipe](recipes.md) run, the workspace
of one change ([created by a recipe run](#created-by-a-recipe-run)).

Commands are named `the-cook <command>` on this page. The examples run them through the repository's wrapper,
`./cook <command>`, from the root of a checkout; with a global install, `the-cook <command>` is equivalent
([wrapper](wrapper.md)).

## When to use them

- You want a worker to work on a task while you keep using the primary checkout.
- You work on tasks of a tracker, such as a local task database or the hosting platform's issues, and want each task in
  its own checkout, with the tracker updated by the repository's lifecycle actions ([tasks](tasks.md)).
- You want a [verification receipt](verify.md) whose base is fixed at the moment the work started.
- You run a recipe: `the-cook make` creates the workspace the recipe names, and keeps it while the run is paused or
  stopped.

You do not need them for ordinary work: `the-cook verify` also works in the primary checkout.

## How it works

### Create

`the-cook workspace create <name>`:

1. Validates the name: letters, digits, `.`, `-` and `_`, starting with a letter or digit.
2. Resolves `--base` (default: the checkout's head commit) to a commit through the version-control ingredient.
3. Adds the workspace through the version-control ingredient, at `<workspaces.dir>/<name>` on a new branch
   `work/<name>` from the base.
4. Writes the session to `session.json` in the new workspace's state directory (`<workspace>/<state.dir>/session.json`,
   by default `.work/workspaces/<name>/.work/session.json`): the base commit, the task id given with `--task`, and a
   unique actor (`<user>:<name>:<random>`).
5. Prints the workspace path.
6. Runs the profile's `bootstrap` commands, such as `npm ci` in a Node repository, in the new workspace, unless
   `--no-bootstrap`. [`the-cook attempt`](attempts.md) also runs them, once per checkout it runs in, so a checkout a
   loop runner prepares is ready to build too.
7. Fires the `start` [lifecycle event](lifecycle.md) in the new workspace: with scope `task` and `COOK_TASK` set to
   the id when `--task` is given, with scope `change` otherwise. Actions declared with `auto: true` run, such as
   [tracker actions from a station](tasks.md#tracker-actions-from-a-station).

The workspace starts from committed code only. `create` copies no uncommitted changes, credentials, dependency
directories or personal configuration.

### Created by a recipe run

#### Workspace creation

`the-cook make <recipe>` is the Start stage of one change ([the run](recipes.md#the-run)). After it validates the inputs
and the recipe's requirements, it creates the workspace the recipe's `workspace` key names, with the inputs substituted,
such as `migration-add-invoice-index` for `migration-{name}` with `--name add-invoice-index`.

It creates it the way `the-cook workspace create` does: at `<workspaces.dir>/<name>`, on a new branch `work/<name>`,
with a session, an actor and `bootstrap`, and it fires `start`.

Its base is the revision the shared default line of work is at, as the version-control ingredient's `default_revision`
reports it, not the checkout's head commit. The run reads the recipe from there too, so a recipe runs in a new workspace
only once it is on the shared default line of work (for git, pushed), not merely committed in this clone. The run learns
nothing new about what is shared: its base is the revision this checkout last learned
([the version-control contract](ingredients.md#the-version-control-contract)).

With `--task <id>` the workspace is bound to the task ([tasks](tasks.md#a-task-run-by-a-recipe)).

#### Drafting with `--here`

With `--here`, the run uses the managed workspace it is started in and creates none, and reads the recipe from that
workspace's working tree, for drafting one. That workspace's base and branch are the run's, and its task unless
`--task` gives one. No `bootstrap` runs and no `start` fires.

It refuses `--unattended`, a checkout that is not a managed workspace, and a workspace that holds a run that is not
completed.

#### The run record

The run records itself in `run.json` in the workspace's state directory (by default
`.work/workspaces/<name>/.work/run.json`): the recipe's name, its content and digest as the run read them when it
started, the inputs, whether the run is unattended, each step's result and the current stage.

`the-cook make --status [<workspace>]` prints it. A paused or stopped run keeps its workspace with whatever its steps
left in it, the changes of a failed `attempt` step included: nothing resets the workspace to its base.

`the-cook make --continue [<workspace>]` resumes it with the recipe content the record holds, and after a failed step
runs that step again on the tree as it is, so inspect or discard those changes first.

To abandon the run, discard the workspace's changes and remove it with `the-cook workspace remove`, which refuses
uncommitted changes as for any workspace. The branch `work/<name>` stays, as after every removal, so a later run that
would create the same workspace needs it deleted first.

### Nesting

Because the workspace lives inside the primary checkout, tools that look for files by walking up directories find the
primary checkout's files when the workspace has none: a task runner's configuration, a `node_modules`, a tracker's
database. That is how a nested workspace shares the clone's tracker database, and how it inherits the trust a worker
gives the primary checkout ([workers](workers.md#trust)). It is also why `bootstrap` matters: without the workspace's
own dependencies, builds can pick up the primary checkout's, or fail on relative tool paths.

Nesting holds only while `workspaces.dir` is a path inside the repository. A directory outside it gives up the shared
trust, configuration and tracker database.

### Remove

`the-cook workspace remove <name>`:

- refuses a name that is not a managed workspace in the configured workspaces directory;
- refuses a workspace with uncommitted changes;
- removes the workspace through the version-control ingredient and never forces it, so the version control system
  refuses a locked workspace;
- keeps the branch `work/<name>`;
- with `workspaces.update_before_landed: true` in the profile, runs the version-control contract's `update` operation
  before it detects landed work, so the checkout learns what is shared; when `update` fails, as it does offline, or
  the version-control ingredient has none, it prints one note and goes on with what the checkout knows
  ([profile](profile.md#workspaces));
- fires the `landed` [lifecycle event](lifecycle.md) when the version-control ingredient finds every patch of
  `work/<name>` in the revision its `default_revision` reports, the shared default line of work as the checkout knows
  it, after its own checks pass and before it removes the workspace; automatic `landed` actions run in the primary
  checkout, or an `attempt` action in a fresh workspace at that revision ([environment](lifecycle.md#environment)).
  Each recorded branch fires `landed` at most once successfully; a failed `landed` is re-fired manually with
  `the-cook lifecycle run landed`. A failing action is reported on stderr and recorded in the
  [lifecycle log](lifecycle.md#the-lifecycle-log). The exit code is the removal's.

`the-cook doctor` lists managed workspaces whose branch has landed, against what the checkout knows, and prints the
`the-cook workspace remove` command for each; it never runs `update` and never fires `landed` itself.

Before removing a workspace, put what matters where the team can see it: deliver the branch
(`the-cook lifecycle run deliver`), comment on the issue or the pull request. Logs, dependencies and other local state
in the workspace are disposable.

### The convention in `AGENTS.md` and `.gitignore`

`the-cook render` writes the workspace convention, with the configured directory, into a managed block of `AGENTS.md`.
The version control system's ignore file is the version-control ingredient's: a `git` ingredient, for example,
renders the directory into a managed block of `.gitignore`. With the defaults:

```markdown
<!-- cook:workspaces -->
Workspaces in this project are created in `.work/workspaces/<name>`, on branch `work/<name>`, from committed code.
<!-- /cook:workspaces -->
```

```gitignore
# cook:workspaces
/.work/workspaces/
# /cook:workspaces
```

`<name>` is the workspace's name. `workspaces` is a [convention block](concepts.md#convention-blocks-and-tool-blocks):
it states the convention as a fact and names no `the-cook` command, so it holds without `the-cook`. After
[`the-cook remove --eject`](remove.md#eject) the markers go and the text and the ignore entry stay. People and workers
creating workspaces by hand with the version control system keep following it. Workspaces are part of the core, so only
an undo removes both blocks.

The ignore-file block lists `/.work/workspaces/` even though the `state` block's `/.work/` already covers it: each
module writes its own entries, so removing one module never un-ignores another module's paths.

## Reference

```text
the-cook workspace create <name> [--base <ref>] [--task <id>] [--no-bootstrap]
the-cook workspace remove <name>
```

| Flag | Default | Meaning |
| --- | --- | --- |
| `--base <ref>` | the checkout's head commit | Commit the workspace starts from, resolved by the version-control ingredient; recorded as the session base |
| `--task <id>` | none | Bind the workspace to one task. The id is free-form and recorded in the session; events fired in the workspace default to scope `task` and carry it in `COOK_TASK` ([tasks](tasks.md#task-binding)) |
| `--no-bootstrap` | off | Skip the profile's `bootstrap` commands |

| Exit code | Meaning |
| --- | --- |
| 0 | Created or removed |
| 1 | Error: invalid name, unknown ref, existing branch, bootstrap failed |
| 3 | `remove` refused: uncommitted changes, or a locked workspace; or no ingredient fills the version-control contract, its archive is not cached, or its program is not installed |

When bootstrap fails, the workspace already exists: fix the cause and run the bootstrap commands in it, or remove it.

Paths below show the defaults, `state.dir: .work` and `workspaces.dir: .work/workspaces` ([profile](profile.md)).

| Path | Written by `create` |
| --- | --- |
| `.work/workspaces/<name>/` | the checkout |
| branch `work/<name>` | the branch |
| `.work/workspaces/<name>/.work/session.json` | the session |
| `.work/workspaces/<name>/.work/run.json` | the run record, written by `the-cook make`, not by `create` |
| `.work/workspaces.lock` | held while creating |
| `.work/lifecycle.jsonl` | `create` and `remove`, for the events they fire |

## Examples

### A Node API, with tracker actions

With [tracker actions from a station](tasks.md#tracker-actions-from-a-station) applied:

```sh
./cook workspace create fix-invoice-rounding --task acme-api-7f2   # start: the task is marked in progress
cd .work/workspaces/fix-invoice-rounding
# edit, test
./cook verify --lane unit=invoice --skip e2e="no route, controller or guard changed"
./cook lifecycle run completed    # refused without a valid receipt; closes the task
./cook lifecycle run deliver
```

### A Terraform repository, no tracker

```sh
./cook workspace create bump-provider --no-bootstrap
cd .work/workspaces/bump-provider
terraform -chdir=envs/staging init -backend=false
```

### A Go repository, starting from a release branch

```sh
./cook workspace create backport-1-4 --base origin/release-1.4
```

### Removing

```sh
./cook workspace remove fix-invoice-rounding   # fires landed when its patches are on the shared default line of work
git branch -d work/fix-invoice-rounding        # when the branch is merged
```

## Guidelines

- Do commit before creating a workspace for a worker: it only sees committed code.
- Do bind one task per workspace with `--task`, so every event fired there names it.
- Do remove workspaces you are done with; the workspaces directory grows with dependencies.
- Do keep `workspaces.dir` inside the repository, so workspaces stay nested.
- Don't remove someone else's workspace in a shared clone.
- Don't move workspaces by hand; the version control system records their paths.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| `branch work/<name> already exists` | A previous workspace with that name kept its branch | Delete the branch, or choose another name |
| Build in the workspace uses the primary checkout's dependencies | Bootstrap was skipped or failed | Run the profile's bootstrap commands in the workspace |
| `remove` refuses | Uncommitted changes | Commit, stash or discard them first |
| `remove` fires no `landed` for a branch that was merged | The checkout has not learned where the shared default line of work is, and the profile leaves `workspaces.update_before_landed` off | Fire it with `the-cook lifecycle run landed`. For the next removals, set `workspaces.update_before_landed: true` ([profile](profile.md#workspaces)), or have the version control program learn what is shared before removing |
| `remove` prints a note that what is shared could not be learned | `workspaces.update_before_landed` is on, and `update` failed, as it does offline, or the version-control ingredient has no `update` | Nothing: landed was detected against what the checkout knows; if that missed it, fire `landed` later with `the-cook lifecycle run landed` |
| `remove` refuses the workspace of a stopped recipe run | Its steps left uncommitted changes | `the-cook make --continue` to finish the run; to abandon it, discard the changes, then remove |
| `the-cook make --here` exits 1 | It was started with `--unattended`, outside a managed workspace, or in one whose run is not completed | Run it in a managed workspace, or without `--here` so the run creates its own; resume an unfinished run with `the-cook make --continue` |
| `the-cook make` exits 1: the branch `work/<name>` exists | An earlier run with the same inputs, or a workspace of that name, kept its branch | Delete the branch if its work is done, or run with other inputs |
| A worker in the workspace prompts for every command | The worker does not trust the folder | Trust the primary checkout; nested workspaces inherit it |

## Coexistence

`the-cook` manages only the workspaces it recorded in the configured workspaces directory. It never lists, cleans or
modifies a workspace that a worker program creates in its own directories, that another tool creates, or that someone
makes by hand. Branch names start with `work/`, so they stay apart from the branches a loop runner creates for its own
sessions; a loop runner can run in a managed workspace ([attempts](attempts.md)).
