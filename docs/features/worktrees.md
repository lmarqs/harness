# Worktrees

## Objective

**Problem.** A developer who wants an agent to work on a task while they keep using their own checkout, or several
agents on several tasks at once, needs a separate working copy for each. Made by hand, each copy lands somewhere
different, starts from a commit nobody recorded, lacks the dependencies it needs to build, and can be deleted along with
work nobody committed.

**Goal.** Every task gets its own working copy, ready to build, starting from a known commit, in the place every
teammate and agent expects, and no copy is removed while it holds uncommitted work.

## Overview

`the-cook worktree` creates and removes managed task worktrees: separate checkouts of the clone, added and removed
through the [version-control recipe](recipes.md#the-version-control-contract) (with the git recipe, git worktrees),
nested inside the clone in the configured worktrees directory (`worktrees.dir`, default `.work/worktrees`), each on its
own branch, with a recorded starting commit, an actor identity and an optional task id. The task id binds the worktree
to one task of the team's tracker: every event fired there carries the id in `COOK_TASK` and the actor in
`COOK_ACTOR` ([tasks](tasks.md)).

Commands are named `the-cook <command>` on this page. The examples run them through the repository's wrapper,
`./cook <command>`, from the root of a checkout; with a global install, `the-cook <command>` is equivalent
([wrapper](wrapper.md)).

## When to use them

- You want an agent to work on a task while you keep using the primary checkout.
- You work on tasks of a tracker, such as Beads or GitHub issues, and want each task in its own checkout, with the
  tracker updated by the repository's lifecycle actions ([tasks](tasks.md)).
- You want a [verification receipt](verify.md) whose base is fixed at the moment the work started.

You do not need them for ordinary work: `the-cook verify` also works in the primary checkout.

## How it works

### Create

`the-cook worktree create <name>`:

1. Validates the name: letters, digits, `.`, `-` and `_`, starting with a letter or digit.
2. Resolves `--base` (default: the checkout's head commit) to a commit through the version-control recipe.
3. Adds the worktree through the version-control recipe, at `<worktrees.dir>/<name>` on a new branch `work/<name>`
   from the base. The git recipe runs `git worktree add -b work/<name> <worktrees.dir>/<name> <base>`, with
   `core.hooksPath=/dev/null` for that command only, so no git hook from the primary checkout runs during creation.
4. Writes the session to `session.json` in the new worktree's state directory (`<worktree>/<state.dir>/session.json`,
   by default `.work/worktrees/<name>/.work/session.json`): the base commit, the task id given with `--task`, and a
   unique actor (`<user>:<name>:<random>`).
5. Prints the worktree path.
6. Runs the profile's `bootstrap` commands, such as `npm ci` in a Node repository, in the new worktree, unless
   `--no-bootstrap`. [`the-cook worker`](ralph.md) also runs them, once per checkout it runs in, so a checkout a loop
   runner prepares is ready to build too.
7. Fires the `start` [lifecycle event](lifecycle.md) in the new worktree: with scope `task` and `COOK_TASK` set to
   the id when `--task` is given, with scope `change` otherwise. Actions declared with `auto: true` run, such as the
   tracker actions of the [Beads recipe](tasks.md#the-beads-recipe).

The worktree starts from committed code only. Uncommitted changes, credentials, dependency directories and personal
configuration are not copied.

### Nesting

Because the worktree lives inside the primary checkout, tools that look for files by walking up directories find the
primary checkout's files when the worktree has none: [mise](https://mise.jdx.dev) configuration, a `node_modules`, a
`.beads/` database. That is how a nested worktree shares the clone's Beads database, and how it inherits the agent's
trust in the primary checkout ([agents](agents.md#trust)). It is also why `bootstrap` matters: without the worktree's
own dependencies, builds can pick up the primary checkout's, or fail on relative tool paths.

Nesting holds only while `worktrees.dir` is a path inside the repository. A directory outside it gives up the shared
trust, configuration and Beads database.

### Remove

`the-cook worktree remove <name>`:

- refuses a name that is not a managed worktree in the configured worktrees directory;
- refuses a worktree with uncommitted changes;
- removes the worktree through the version-control recipe and never forces it, so a locked worktree is refused (with
  the git recipe, by git);
- keeps the branch `work/<name>`;
- fires the `landed` [lifecycle event](lifecycle.md) when the version-control recipe finds every patch of `work/<name>`
  on the default branch (the git recipe compares with `git cherry`), after its own checks pass and before it removes the
  worktree, so automatic `landed` actions run in it. Each recorded branch fires `landed` at most once successfully; a
  failed `landed` is re-fired manually with `the-cook lifecycle run landed`. A failing action is reported on stderr and
  recorded in the [lifecycle log](lifecycle.md#the-lifecycle-log). The exit code is the removal's.

`the-cook doctor` lists managed worktrees whose branch has landed and prints the `the-cook worktree remove` command for
each; it never fires `landed` itself.

Before removing a worktree, put what matters where the team can see it: deliver the branch
(`the-cook lifecycle run deliver`), comment on the issue or the pull request. Logs, dependencies and other local state
in the worktree are disposable.

### The convention in `AGENTS.md` and `.gitignore`

`the-cook render` writes the worktree convention, with the configured directory, into a managed block of `AGENTS.md`.
The version control system's ignore file is the version-control recipe's: the git recipe renders the directory into a
managed block of `.gitignore`. With the defaults:

```markdown
<!-- cook:worktrees -->
Worktrees in this project are created in `.work/worktrees/<name>`, on branch `work/<name>`, from committed code.
<!-- /cook:worktrees -->
```

```gitignore
# cook:worktrees
/.work/worktrees/
# /cook:worktrees
```

`<name>` is the worktree's name. `worktrees` is a [convention block](concepts.md#convention-blocks-and-tool-blocks): it
states the convention as a fact and names no `the-cook` command, so it holds without `the-cook`. After
[`the-cook remove --eject`](remove.md#eject) the markers go and the text and the ignore entry stay. People and agents
creating worktrees by hand with `git worktree add` keep following it. Worktrees are part of the core, so only an undo
removes both blocks.

The `.gitignore` block lists `/.work/worktrees/` even though the `state` block's `/.work/` already covers it: each
module writes its own entries, so removing one module never un-ignores another module's paths.

## Reference

```text
the-cook worktree create <name> [--base <ref>] [--task <id>] [--no-bootstrap]
the-cook worktree remove <name>
```

| Flag | Default | Meaning |
| --- | --- | --- |
| `--base <ref>` | the checkout's head commit | Commit the worktree starts from, resolved by the version-control recipe; recorded as the session base |
| `--task <id>` | none | Bind the worktree to one task. The id is free-form and recorded in the session; events fired in the worktree default to scope `task` and carry it in `COOK_TASK` ([tasks](tasks.md#task-binding)) |
| `--no-bootstrap` | off | Skip the profile's `bootstrap` commands |

| Exit code | Meaning |
| --- | --- |
| 0 | Created or removed |
| 1 | Error: invalid name, unknown ref, existing branch, bootstrap failed |
| 3 | `remove` refused: uncommitted changes, or a locked worktree; or the version-control recipe's program is not installed |

When bootstrap fails, the worktree already exists: fix the cause and run the bootstrap commands in it, or remove it.

Paths below show the defaults, `state.dir: .work` and `worktrees.dir: .work/worktrees` ([profile](profile.md)).

| Path | Written by `create` |
| --- | --- |
| `.work/worktrees/<name>/` | the checkout |
| branch `work/<name>` | the branch |
| `.work/worktrees/<name>/.work/session.json` | the session |
| `.work/worktrees.lock` | held while creating |
| `.work/lifecycle.jsonl` | `create` and `remove`, for the events they fire |

## Examples

### A Node API, with Beads

With the [Beads recipe](tasks.md#the-beads-recipe) applied:

```sh
./cook worktree create fix-invoice-rounding --task acme-api-7f2   # start: the bead is marked in progress
cd .work/worktrees/fix-invoice-rounding
# edit, test
./cook verify --lane unit=invoice --skip e2e="no route, controller or guard changed"
./cook lifecycle run completed    # refused without a valid receipt; closes the bead
./cook lifecycle run deliver
```

### A Terraform repository, no tracker

```sh
./cook worktree create bump-provider --no-bootstrap
cd .work/worktrees/bump-provider
terraform -chdir=envs/staging init -backend=false
```

### A Go repository, starting from a release branch

```sh
./cook worktree create backport-1-4 --base origin/release-1.4
```

### Removing

```sh
./cook worktree remove fix-invoice-rounding   # fires landed when the branch's patches are on the default branch
git branch -d work/fix-invoice-rounding           # when the branch is merged
```

## Guidelines

- Do commit before creating a worktree for an agent: it only sees committed code.
- Do bind one task per worktree with `--task`, so every event fired there names it.
- Do remove worktrees you are done with; the worktrees directory grows with dependencies.
- Do keep `worktrees.dir` inside the repository, so worktrees stay nested.
- Don't remove someone else's worktree in a shared clone.
- Don't move worktrees by hand; the version control system records their paths, as git does.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| `branch work/<name> already exists` | A previous worktree with that name kept its branch | Delete the branch, or choose another name |
| Build in the worktree uses the primary checkout's dependencies | Bootstrap was skipped or failed | Run the profile's bootstrap commands in the worktree |
| `remove` refuses | Uncommitted changes | Commit, stash or discard them first |
| The agent in the worktree prompts for every command | The agent does not trust the folder | Trust the primary checkout; nested worktrees inherit it |

## Coexistence

`the-cook` manages only the worktrees it recorded in the configured worktrees directory. Claude Code's own
`.claude/worktrees/` and worktrees created by other tools or by hand are never listed, cleaned or modified. Branch names
start with `work/`, so they do not collide with the `ralph/session-*` branches of
[ralph-tui](https://github.com/subsy/ralph-tui), a loop runner that can run in a managed worktree ([ralph](ralph.md)).
