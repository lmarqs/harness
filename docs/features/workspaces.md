# Workspaces

## Objective

**Problem.** A developer who wants an agent to work on a task while they keep using their own checkout, or several
agents on several tasks at once, needs a separate working copy for each. Made by hand, each copy lands somewhere
different, starts from a commit nobody recorded, lacks the dependencies it needs to build, and can be deleted along with
work nobody committed.

**Goal.** Every task gets its own working copy, ready to build, starting from a known commit, in the place every
teammate and agent expects, and no copy is removed while it holds uncommitted work.

## Overview

`the-cook workspace` creates and removes managed task workspaces: separate checkouts of the clone, added and removed
through the [version-control recipe](recipes.md#the-version-control-contract), nested inside the clone in the configured
workspaces directory (`workspaces.dir`, default `.work/workspaces`), each on its own branch, with a recorded starting
commit, an actor identity and an optional task id. The task id binds the workspace to one task of the team's tracker:
every event fired there carries the id in `COOK_TASK` and the actor in `COOK_ACTOR` ([tasks](tasks.md)).

Commands are named `the-cook <command>` on this page. The examples run them through the repository's wrapper,
`./cook <command>`, from the root of a checkout; with a global install, `the-cook <command>` is equivalent
([wrapper](wrapper.md)).

## When to use them

- You want an agent to work on a task while you keep using the primary checkout.
- You work on tasks of a tracker, such as a local task database or the hosting platform's issues, and want each task in
  its own checkout, with the tracker updated by the repository's lifecycle actions ([tasks](tasks.md)).
- You want a [verification receipt](verify.md) whose base is fixed at the moment the work started.

You do not need them for ordinary work: `the-cook verify` also works in the primary checkout.

## How it works

### Create

`the-cook workspace create <name>`:

1. Validates the name: letters, digits, `.`, `-` and `_`, starting with a letter or digit.
2. Resolves `--base` (default: the checkout's head commit) to a commit through the version-control recipe.
3. Adds the workspace through the version-control recipe, at `<workspaces.dir>/<name>` on a new branch `work/<name>`
   from the base.
4. Writes the session to `session.json` in the new workspace's state directory (`<workspace>/<state.dir>/session.json`,
   by default `.work/workspaces/<name>/.work/session.json`): the base commit, the task id given with `--task`, and a
   unique actor (`<user>:<name>:<random>`).
5. Prints the workspace path.
6. Runs the profile's `bootstrap` commands, such as `npm ci` in a Node repository, in the new workspace, unless
   `--no-bootstrap`. [`the-cook worker`](worker.md) also runs them, once per checkout it runs in, so a checkout a loop
   runner prepares is ready to build too.
7. Fires the `start` [lifecycle event](lifecycle.md) in the new workspace: with scope `task` and `COOK_TASK` set to
   the id when `--task` is given, with scope `change` otherwise. Actions declared with `auto: true` run, such as the
   actions of a [tracker recipe](tasks.md#a-tracker-recipe).

The workspace starts from committed code only. Uncommitted changes, credentials, dependency directories and personal
configuration are not copied.

### Nesting

Because the workspace lives inside the primary checkout, tools that look for files by walking up directories find the
primary checkout's files when the workspace has none: a task runner's configuration, a `node_modules`, a tracker's
database. That is how a nested workspace shares the clone's tracker database, and how it inherits the agent's trust in
the primary checkout ([agents](agents.md#trust)). It is also why `bootstrap` matters: without the workspace's
own dependencies, builds can pick up the primary checkout's, or fail on relative tool paths.

Nesting holds only while `workspaces.dir` is a path inside the repository. A directory outside it gives up the shared
trust, configuration and tracker database.

### Remove

`the-cook workspace remove <name>`:

- refuses a name that is not a managed workspace in the configured workspaces directory;
- refuses a workspace with uncommitted changes;
- removes the workspace through the version-control recipe and never forces it, so the version control system refuses a
  locked workspace;
- keeps the branch `work/<name>`;
- fires the `landed` [lifecycle event](lifecycle.md) when the version-control recipe finds every patch of `work/<name>`
  on the default branch, after its own checks pass and before it removes the workspace, so automatic `landed` actions
  run in it. Each recorded branch fires `landed` at most once successfully; a failed `landed` is re-fired manually with
  `the-cook lifecycle run landed`. A failing action is reported on stderr and recorded in the
  [lifecycle log](lifecycle.md#the-lifecycle-log). The exit code is the removal's.

`the-cook doctor` lists managed workspaces whose branch has landed and prints the `the-cook workspace remove` command
for each; it never fires `landed` itself.

Before removing a workspace, put what matters where the team can see it: deliver the branch
(`the-cook lifecycle run deliver`), comment on the issue or the pull request. Logs, dependencies and other local state
in the workspace are disposable.

### The convention in `AGENTS.md` and `.gitignore`

`the-cook render` writes the workspace convention, with the configured directory, into a managed block of `AGENTS.md`.
The version control system's ignore file is the version-control recipe's: a `git` recipe, for example, renders the
directory into a managed block of `.gitignore`. With the defaults:

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
[`the-cook remove --eject`](remove.md#eject) the markers go and the text and the ignore entry stay. People and agents
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
| `--base <ref>` | the checkout's head commit | Commit the workspace starts from, resolved by the version-control recipe; recorded as the session base |
| `--task <id>` | none | Bind the workspace to one task. The id is free-form and recorded in the session; events fired in the workspace default to scope `task` and carry it in `COOK_TASK` ([tasks](tasks.md#task-binding)) |
| `--no-bootstrap` | off | Skip the profile's `bootstrap` commands |

| Exit code | Meaning |
| --- | --- |
| 0 | Created or removed |
| 1 | Error: invalid name, unknown ref, existing branch, bootstrap failed |
| 3 | `remove` refused: uncommitted changes, or a locked workspace; or the version-control recipe's program is not installed |

When bootstrap fails, the workspace already exists: fix the cause and run the bootstrap commands in it, or remove it.

Paths below show the defaults, `state.dir: .work` and `workspaces.dir: .work/workspaces` ([profile](profile.md)).

| Path | Written by `create` |
| --- | --- |
| `.work/workspaces/<name>/` | the checkout |
| branch `work/<name>` | the branch |
| `.work/workspaces/<name>/.work/session.json` | the session |
| `.work/workspaces.lock` | held while creating |
| `.work/lifecycle.jsonl` | `create` and `remove`, for the events they fire |

## Examples

### A Node API, with a tracker recipe

With a [tracker recipe](tasks.md#a-tracker-recipe) pinned and applied:

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
./cook workspace remove fix-invoice-rounding   # fires landed when the branch's patches are on the default branch
git branch -d work/fix-invoice-rounding           # when the branch is merged
```

## Guidelines

- Do commit before creating a workspace for an agent: it only sees committed code.
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
| The agent in the workspace prompts for every command | The agent does not trust the folder | Trust the primary checkout; nested workspaces inherit it |

## Coexistence

`the-cook` manages only the workspaces it recorded in the configured workspaces directory. Workspaces an agent harness
creates in its own directories, by other tools or by hand are never listed, cleaned or modified. Branch names start
with `work/`, so they stay apart from the branches a loop runner creates for its own sessions; a loop runner can run in
a managed workspace ([worker](worker.md)).
