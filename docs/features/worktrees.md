# Worktrees

`harness worktree` creates and removes managed task worktrees: separate git checkouts, nested inside the clone under
`.work/worktrees/<name>`, each on its own branch, with a recorded starting commit, an actor identity and an optional
task tracker. People and agents use them to work on several tasks at once without touching the primary checkout.

## When to use them

- You want an agent to work on a task while you keep using the primary checkout.
- You track tasks with Beads or a PRD file and want each task in its own checkout ([tasks](tasks.md)).
- You want a [verification receipt](verify.md) whose base is fixed at the moment the work started.

You do not need them for ordinary work: `harness verify` also works in the primary checkout.

## How it works

### Create

`harness worktree create <name>`:

1. Validates the name: letters, digits, `.`, `-` and `_`, starting with a letter or digit.
2. Initialises the tracker when one is selected (see [tasks](tasks.md#trackers)).
3. Resolves `--base` (default `HEAD`) to a commit.
4. Runs `git worktree add -b work/<name> .work/worktrees/<name> <base>`, with `core.hooksPath=/dev/null` for that
   command only, so no git hook from the primary checkout runs during creation.
5. Writes the session to `.work/worktrees/<name>/.work/session.json`: the base commit, the tracker, the task, the
   PRD path, and a unique actor (`<user>:<name>:<random>`).
6. Prints the worktree path.
7. Runs the profile's `bootstrap` commands in the new worktree, unless `--no-bootstrap`.
8. Without `--task`, fires the `start` [lifecycle event](lifecycle.md) in the new worktree, with scope `change`;
   actions declared with `auto: true` run. A worktree bound to a task starts when the task is claimed, so `start`
   fires once, with scope `task`, from `harness task claim`.

The worktree starts from committed code only. Uncommitted changes, credentials, dependency directories and personal
configuration are not copied.

### Nesting

Because the worktree lives inside the primary checkout, tools that look for files by walking up directories find the
primary checkout's files when the worktree has none: mise configuration, a `node_modules`, a `.beads/` database. That
is how a nested worktree shares the clone's Beads database. It is also why `bootstrap` matters: without the
worktree's own dependencies, builds can pick up the primary checkout's, or fail on relative tool paths.

### Remove

`harness worktree remove <name>`:

- refuses a name that is not a managed worktree under `.work/worktrees/`;
- refuses a worktree with uncommitted changes;
- never forces removal, so git also refuses a locked worktree;
- keeps the branch `work/<name>`;
- fires the `landed` [lifecycle event](lifecycle.md) when every patch of `work/<name>` is on the default branch
  (compared with `git cherry`), after its own checks pass and before it removes the worktree, so automatic `landed`
  actions run in it. Each recorded branch fires `landed` at most once successfully; a failed `landed` is re-fired
  manually with `harness lifecycle run landed`. A failing action is reported on stderr and recorded in
  `.work/lifecycle.jsonl`; the exit code is the removal's.

`harness doctor` lists managed worktrees whose branch has landed and prints the `harness worktree remove` command for
each; it never fires `landed` itself.

Before removing a worktree, put what matters where the team can see it: deliver the branch
(`harness lifecycle run deliver`), comment on the issue or the pull request. Logs, dependencies and local tracker state
in the worktree are disposable.

## Reference

```text
harness worktree create <name> [--base <ref>] [--tracker beads|prd|none] [--task <id>] [--prd <path>] [--no-bootstrap]
harness worktree remove <name>
```

| Flag | Default | Meaning |
| --- | --- | --- |
| `--base <ref>` | `HEAD` | Commit the worktree starts from; recorded as the session base |
| `--tracker beads\|prd\|none` | profile `tracker.default` | Task tracker for this worktree |
| `--task <id>` | none | Bind the worktree to one task; `harness task` refuses other ids here |
| `--prd <path>` | none | PRD JSON file; required with `--tracker prd` |
| `--no-bootstrap` | off | Skip the profile's `bootstrap` commands |

| Exit code | Meaning |
| --- | --- |
| 0 | Created or removed |
| 1 | Error: invalid name, unknown ref, existing branch, tracker unavailable, bootstrap failed |
| 3 | `remove` refused: uncommitted changes, or a locked worktree |

When bootstrap fails, the worktree already exists: fix the cause and run the bootstrap commands in it, or remove it.

| Path | Written by `create` |
| --- | --- |
| `.work/worktrees/<name>/` | the checkout |
| branch `work/<name>` | the branch |
| `.work/worktrees/<name>/.work/session.json` | the session |
| `.work/worktrees.lock` | held while creating |
| `.beads/` | only when `--tracker beads` and no database exists yet |
| `.work/lifecycle.jsonl` | `create` and `remove`, for the events they fire |

## Examples

### A Node API, with Beads

```sh
harness worktree create fix-invoice-rounding --tracker beads --task acme-api-7f2
cd .work/worktrees/fix-invoice-rounding
harness task claim acme-api-7f2
# edit, test
harness verify --lane unit=invoice --skip e2e="no route, controller or guard changed"
harness task complete acme-api-7f2        # fires completed, then deliver when concluded
harness lifecycle run deliver             # or deliver by hand, when no deliver action is automatic
```

With `front_door: mise`, the same steps read `mise run worktree:create …`, `mise run task claim …`,
`mise run verify …`.

### A Terraform repository, no tracker

```sh
harness worktree create bump-provider --no-bootstrap
cd .work/worktrees/bump-provider
terraform -chdir=envs/staging init -backend=false
```

### A Go repository, starting from a release branch

```sh
harness worktree create backport-1-4 --base origin/release-1.4
```

### Removing

```sh
harness worktree remove fix-invoice-rounding   # fires landed when the branch's patches are on the default branch
git branch -d work/fix-invoice-rounding        # when the branch is merged
```

## Guidelines

- Do commit before creating a worktree for an agent: it only sees committed code.
- Do keep one task per worktree when you use a tracker; `--task` enforces it.
- Do remove worktrees you are done with; `.work/` grows with dependencies.
- Don't remove someone else's worktree in a shared clone.
- Don't move worktrees by hand; git records their paths.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| `branch work/<name> already exists` | A previous worktree with that name kept its branch | Delete the branch, or choose another name |
| Build in the worktree uses the primary checkout's dependencies | Bootstrap was skipped or failed | Run the profile's bootstrap commands in the worktree |
| `remove` refuses | Uncommitted changes | Commit, stash or discard them first |
| The agent in the worktree prompts for every command | The agent does not trust the folder | Trust the primary checkout; nested worktrees inherit it |

## Coexistence

`harness` manages only `.work/worktrees/`. Claude Code's own `.claude/worktrees/` and worktrees created by other tools
or by hand are never listed, cleaned or modified. Branch names start with `work/`, so they do not collide with
ralph-tui's `ralph/session-*` branches.
