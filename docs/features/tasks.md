# Tasks

## Objective

**Problem.** A developer who splits a piece of work into small steps, for several agent sessions or an unattended run in
one clone, needs to know which session holds which step and which steps are really finished. Without that, two sessions
work on the same step or close each other's, a step is marked finished before its checks pass, and the bookkeeping is
either lost between sessions or spills into the issues and pull requests the team reads.

**Goal.** Each step is held by one session at a time and counts as finished only after its checks pass, and the team's
record of work stays as the team writes it.

## Overview

`harness task` claims, annotates, completes and releases tasks in a clone-local tracker: a Beads database (Beads is a
task tracker that keeps its database in `.beads/` and is used through its `bd` command) or a PRD JSON file (a list of
user stories in the format of [ralph-tui](https://github.com/subsy/ralph-tui), the runner behind [Ralph](ralph.md)).
Each managed worktree has its own actor, the identity under which its session claims tasks, so two sessions cannot
complete each other's tasks, and completion requires a valid [verification receipt](verify.md).

Commands are named `harness <command>` on this page. The examples run them through the repository's wrapper,
`./harnessw <command>`, from the root of a checkout; with a global install, `harness <command>` is equivalent
([wrapper](wrapper.md)).

## Issues and tasks

| | Issue | Task |
| --- | --- | --- |
| Lives in | GitHub (issues, pull requests, projects) | this clone: `.beads/` or a PRD JSON file |
| Seen by | the team | this clone only |
| Purpose | the record of the project's work | organising long sessions and local work, including [Ralph](ralph.md) runs |
| Written by `harness` | never | yes, through `harness task` and Ralph |

What the team needs to know goes to the issue or the pull request. Tasks are optional and disposable.

## When to use it

- You split a piece of work into stories and want agents to pick them up one at a time.
- You run [Ralph](ralph.md), which takes its tasks from Beads or a PRD file.
- You want a per-task worktree whose completion is tied to a receipt.

Tasks need `modules.tasks: true` in the [profile](profile.md).

## How it works

### Trackers

| Tracker | Backend | Initialisation |
| --- | --- | --- |
| `beads` | the `bd` CLI and the clone's `.beads/` database | If `.beads/` does not exist, `harness` creates a local database with prefix `tracker.beads_prefix`, without git hooks, without agent files and without remotes, and turns off automatic export. An existing `.beads/` is used exactly as found and never reconfigured |
| `prd` | a PRD JSON file you choose (`--prd`), in ralph-tui's `userStories` format | The file must exist. A file in the configured PRD directory (`tasks.prd_dir`, default `.work/prds`) stays out of git: the `tasks` block of `.gitignore` ignores that directory |
| `none` | nothing | Nothing is initialised; the session is handed off through the agent conversation |

`harness` never switches backends on its own: when `bd` is not installed, `--tracker beads` fails rather than fall
back.

The tracker is chosen when the worktree is created (`harness worktree create --tracker …`, default
`tracker.default`), and recorded in the worktree's session.

### Sessions and ownership

`harness task` runs inside a [managed worktree](worktrees.md). Its session supplies the tracker and an actor unique to
that worktree (`<user>:<worktree>:<random>`).

| Action | Beads | PRD file |
| --- | --- | --- |
| `claim <id>` | `bd update <id> --claim` as the actor | sets the story's `owner` to the actor; refuses a story that passes or that another actor owns |
| `note <id> --note <text>` | `bd comments add <id> <text>` | appends `{actor, text}` to the story's `progress` |
| `complete <id>` | `bd close <id>` | sets the story's `passes` to `true` |
| `release <id>` | `bd unclaim <id>` | removes the story's `owner` |

Rules:

- `note`, `complete` and `release` require the task to be claimed by this session's actor.
- `complete` requires a valid receipt in this checkout (`harness verify --check` passes).
- A worktree created with `--task <id>` refuses other ids. A `claim` in a worktree without a task binds it to that
  id.
- `--note` on any action also records the note.

### Lifecycle events

| Action | Fires, with scope `task` |
| --- | --- |
| `claim` | `start` |
| `complete` | `completed`, then, when its actions succeed and the work is concluded (committed, clean tree, not on the default branch, and a valid receipt when the verify module is enabled), `deliver` |
| any action, run without a terminal, that exits 3 | `blocked`, with `HARNESS_REASON` set to the one-line refusal, such as a task another actor owns |

Only actions declared with `auto: true` run at these points; `HARNESS_TASK` carries the task id. The task is claimed
or completed whatever the actions do: a failing automatic event is reported on stderr and recorded in the
[lifecycle log](lifecycle.md#the-lifecycle-log), and never changes the task action's exit code. See
[lifecycle](lifecycle.md).

PRD writes take a lock (`<prd>.lock` next to the file) and replace the file atomically. While a Ralph run owns a PRD
(`<prd>.owner.lock`), `harness task` refuses to change it.

### The convention in `AGENTS.md` and `.gitignore`

With the tasks module on, `harness render` writes the task convention into the `tasks` block of `AGENTS.md`: the
tracker in use (`tracker.default`), where PRD files are kept, and the rules every session follows. The PRD directory
goes into the `tasks` block of `.gitignore`. With `tracker.default: beads` and the default paths:

```markdown
<!-- harness:tasks -->
Local tasks are tracked in this clone, never in the team's record: the issue and the pull request are the record.
Tasks live in the Beads database in `.beads/` and are handled with `bd`. PRD files live in `.work/prds/`, in
ralph-tui's `userStories` format. A task is claimed before work on it starts, has one owner at a time, and is
completed only after its checks pass. A task that will not be finished is released, not completed.
<!-- /harness:tasks -->
```

With `tracker.default: prd` the second sentence names the PRD files as the tracker; with `none`, it says tasks are
handed off through the agent conversation.

```gitignore
# harness:tasks
/.work/prds/
# /harness:tasks
```

When `harness` created the Beads database and the repository does not track `.beads/`, it also writes:

```gitignore
# harness:beads
/.beads/
# /harness:beads
```

An existing `.beads/` keeps whatever ignore rules the repository already has.

These are [convention blocks](concepts.md#convention-blocks-and-tool-blocks): they state the convention as a fact and
name no `harness` command. After [`harness remove --eject`](remove.md#eject) the markers go and the text and ignore
entries stay. `bd` and ralph-tui keep working with the same database and PRD files, and agents keep claiming before they
work, but nothing checks for a receipt at completion any more. `harness remove --module tasks` removes the blocks and
leaves the database and the PRD files in place.

## Reference

```text
harness task <claim|note|complete|release> <id> [--note <text>]
```

| Argument or flag | Meaning |
| --- | --- |
| `claim` \| `note` \| `complete` \| `release` | The action |
| `<id>` | Beads issue id or PRD story id |
| `--note <text>` | Text to record; required for `note` |

| Exit code | Meaning |
| --- | --- |
| 0 | Done |
| 1 | Error: not in a managed worktree, the session has no tracker, `bd` missing, unknown id, task bound to another id |
| 2 | `complete` refused: no valid receipt. Run `harness verify` |
| 3 | Refused: the task belongs to another actor, or a Ralph run owns the PRD. A person decides whether to take it over |

Files read and written: `session.json` in the worktree's state directory; the Beads database through `bd`; the PRD
file and its lock files; the lifecycle log.

## Examples

### A Node API with Beads

```sh
./harnessw worktree create invoice-rounding --tracker beads --task acme-api-7f2
cd .work/worktrees/invoice-rounding
./harnessw task claim acme-api-7f2
./harnessw task note acme-api-7f2 --note "Rounding moved to the domain service; verifying"
./harnessw verify --lane unit=invoice --skip e2e="no route, controller or guard changed"
./harnessw task complete acme-api-7f2
```

`bd ready`, `bd show`, `bd search` and the rest of `bd` keep working as usual.

### A Go repository with a PRD file

```sh
mkdir -p .work/prds
$EDITOR .work/prds/export-csv.json        # or: ralph-tui convert --to json export.md -o .work/prds/export-csv.json
./harnessw worktree create export-csv-1 --tracker prd --prd .work/prds/export-csv.json --task US-001
cd .work/worktrees/export-csv-1
./harnessw task claim US-001
./harnessw verify --lane test=./internal/export/...
./harnessw task complete US-001
```

A minimal PRD file:

```json
{
  "name": "CSV export",
  "userStories": [
    {
      "id": "US-001",
      "title": "Export invoices as CSV",
      "description": "Describe the intended behaviour.",
      "acceptanceCriteria": ["Observable success", "harness verify passes with the relevant lanes"],
      "priority": 1,
      "passes": false
    }
  ]
}
```

### A Terraform repository without a tracker

Leave `modules.tasks: false` and `tracker.default: none`; worktrees and `harness verify` work without tasks.

## Guidelines

- Do link a Beads task to its GitHub issue in the task itself (for example with `bd`'s external reference); that link
  is the agent's job, never the tool's.
- Do release a task you will not finish, so another session can claim it.
- Don't complete a task to "save progress"; use `note`.
- Don't edit a PRD file by hand while Ralph runs on it.
- Don't share task ids across clones: they mean something only in the clone that holds the tracker.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| Exit 1: not in a managed worktree | `harness task` was run in the primary checkout | `harness worktree create … --tracker …` |
| `complete` exits 2 | No valid receipt, or a file changed after `verify` | `harness verify …`, then complete |
| `claim` exits 3 | Another session owns the task | Inspect the claim; release it from that session, or take over deliberately with `bd` |
| `--tracker beads` fails at once | `bd` is not installed | Install `bd`, or choose `prd` or `none` |
| `.beads` exists but `harness` refuses it | The directory has no Beads metadata (for example a partial checkout) | Restore its configuration before using it |
| `bd` suggests pushing to a remote | The database `harness` created is local by design | Nothing to do; add a deny for the push in the policy if agents should never try |

## Coexistence

- GitHub stays the record: the binary contains no logic for any hosting platform and never writes issues, projects,
  comments, reviews or labels. Through `harness`, a pull request is opened only by a `deliver` action the
  repository declares.
- Task state `harness` creates stays in the clone. An existing `.beads/`, including one synchronised across machines or
  tracked in git, is used as found. What `harness` writes into it (claims, notes, verification comments) therefore
  travels where that synchronisation sends it, and `harness` never configures the synchronisation
  ([ADR 0012](../adrs/0012-write-boundary.md)).
- `harness` does not change `core.hooksPath` and initialises Beads without git hooks.

See [compatibility](compatibility.md#beads).
