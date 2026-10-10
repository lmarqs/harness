# Unattended runs: `the-cook worker`

## Objective

**Problem.** A developer with a list of small, well-specified tasks wants them implemented while nobody watches, by
whatever program the team uses to work through a task list. An agent left alone can turn off its permission checks,
change the rules or tests its work is judged by, and report a task done that is not. Without protection against that,
the developer either watches every session or cannot trust what was done.

**Goal.** The developer comes back to a local branch holding only tasks whose checks passed under rules the agent that
did each task could not change, whichever program started the agents. No agent had more permissions than the
repository grants it.

## Overview

`the-cook worker <agent>` starts one declared agent, such as Claude Code or Codex CLI, headless, for one task, in the
checkout it is started in, under the [worker contract](#the-worker-contract). It resolves the recipes the attempt uses,
pins the agent's permission mode, refuses permission bypass, restores and checks the tool-owned files around the agent,
replays the agent's verification with rules read from the base commit, and reports the outcome through its exit status
and, in a worktree bound to a task, [lifecycle events](lifecycle.md). A completion signal the caller expects is text the
caller hands to `the-cook worker`, which prints it only for a verified attempt.

`the-cook worker` is the command a **loop runner** names as its agent command. A loop runner is a third-party program
that works through a list of tasks and starts an agent for each. `the-cook` has no code for any loop runner: it does
not start one, read its configuration or know when a run starts or ends
([ADR 0022](../adrs/0022-tool-integration.md)). An unattended run of a loop runner whose agent command is
`the-cook worker` is called a Ralph run.

The worked example on this page is [ralph-tui](https://github.com/subsy/ralph-tui), a loop runner that takes tasks from
a Beads database or a JSON list of user stories. The `ralph-tui` [recipe](recipes.md#ralph-tui) seeds a
`.ralph-tui/config.toml` whose agent command is `./cook worker`. A team's own script calls `the-cook worker` the
same way ([example](#a-teams-own-loop-script)).

Commands are named `the-cook <command>` on this page. The examples run them through the repository's wrapper,
`./cook <command>`, from the root of a checkout; with a global install, `the-cook <command>` is equivalent
([wrapper](wrapper.md)).

## When to use it

- A set of tasks, such as a Beads epic or a list of user stories, is broken down so an agent can implement them one at
  a time.
- You want the work done while you are away, on a local branch you review and deliver afterwards.
- You want the same safety rules for Claude Code and Codex, whichever loop runner starts them.

Use an interactive session instead when the work needs decisions only a person can make as it goes.

Prerequisites:

| Need | Why |
| --- | --- |
| the agent CLI (`claude` or `codex`) installed and declared in [`agents`](profile.md#agents), its harness recipe in the profile's `recipes:` | the agent the worker starts, and the [harness recipe](recipes.md#the-harness-contract) that says how to start it |
| the version-control recipe the profile names in `vcs:` (`scaffold` writes `vcs: git`) and its program | the worker reads the base, compares and resets through it ([the version-control contract](recipes.md#the-version-control-contract)) |
| every [fetched recipe](recipes.md#fetched-recipes) the attempt uses in the cache | an attempt never fetches; run `./cook recipe fetch` first |
| for Codex, `role: worker` | its worker denies come only from rendered rules ([agents](agents.md#roles)) |
| a clean, committed checkout | the worker refuses uncommitted changes |
| `the-cook verify` working in this repository | every attempt must leave a [receipt](verify.md#the-receipt), the record of a passing `the-cook verify`, which the worker replays |
| a loop runner, such as `ralph-tui` 0.12, or a script of your own | it picks the tasks and starts the worker for each |

`the-cook doctor` checks the version of each program a recipe in use describes, git and the agents included, against
that recipe's supported range. A loop runner is the team's to install and update.

## How it works

### The worker contract

For each attempt, `the-cook worker <agent>`:

1. Resolves, before anything else runs, the version-control recipe the profile names in `vcs:` and the agent's
   harness recipe (`agents.<agent>.recipe`, by default the agent's name), each from the release or from the verified
   cache in the user's cache directory. It never fetches: a fetched recipe that is not in the cache exits 3 and names
   `./cook recipe fetch`. Every read, comparison, reset and launch of the attempt uses this resolution, so nothing
   in the checkout changes how the base is read or how the agent starts.
2. Records the base: the head commit of the checkout when it starts, as the version-control recipe reports it (the git
   recipe runs `git rev-parse HEAD`). A base that cannot be read fails the attempt, exit 1, before the agent starts.
   It refuses to run, exit 1, when the version-control recipe reports uncommitted changes.
3. Refuses the bypass arguments the agent's harness recipe marks as floor, wherever they appear among the arguments it
   receives: the claude recipe marks `--dangerously-skip-permissions`, `--allow-dangerously-skip-permissions` and the
   `bypassPermissions` permission mode; the codex recipe marks `--dangerously-bypass-approvals-and-sandbox`, `--yolo`
   and the `danger-full-access` sandbox. It exits 1 and names the argument. It reads no loop runner's configuration: a
   bypass a loop runner adds reaches the worker as an argument and is refused there, at the first task.
4. Reads the profile, the policy, the hooks file and the [standing context](context.md) from the base commit through the
   version-control recipe (the git recipe runs `git show <base>:<path>`), not from the files in the checkout, so an
   agent cannot weaken the rules its own result is checked against or rewrite its own instructions between attempts.
5. Runs the **tamper check**, first half: restores every tool-owned file in the checkout (the rendered rules, the owned
   keys of the native settings files, the `the-cook hook` entries, the wrapper `cook` and its pin
   `.cook/wrapper.yaml`) from the base commit.
6. Pins the permission mode with the launch operation of the agent's harness recipe: its unattended arguments, its
   sandbox-root arguments and the way it passes `worker.deny`:
   - the claude recipe (Claude Code): `--permission-mode auto`, plus `worker.deny` as disallowed tools.
   - the codex recipe (Codex): the `workspace-write` sandbox with network access. The writable roots are the checkout,
     the clone's state directory (`state.dir`, default `.work`) and the directories listed in
     `agents.codex.writable_roots`, such as `.beads` for a Beads database ([profile](profile.md#agents)).
     `worker.deny` applies through the rendered rules, which the tamper check keeps intact.
7. Runs the profile's `bootstrap` once per checkout, and records that in the checkout's state directory, so later
   attempts in the same checkout skip it.
8. Runs the agent with its prompt, read from stdin or from the agent arguments, whichever the caller uses, and
   `COOK_WORKER=1` in its environment. It buffers the agent's output instead of streaming it. Every `the-cook`
   process the agent starts sees that variable and refuses `the-cook lifecycle run`, and the agent's `attention` and
   `turn_end` [hooks](hooks.md) do nothing.
9. Runs the tamper check, second half: compares with the base commit the tool-owned files, the files under
   `.cook/` and every file and skill a `context` entry names. Any difference fails the attempt and is reported by
   path: an agent that rewrote its own rules cannot complete, and a change to the rules reaches a run only through a
   commit a person made.
10. Reads the receipt the agent wrote with `the-cook verify`. A missing or malformed receipt is a failed attempt.
    Otherwise the worker replays the lanes and skip reasons the receipt selected against the final content, with the
    profile from the base commit. The receipt only selects; the replay is the proof.
11. If the replay passes: with `--commit`, commits the work in the checkout through the version-control recipe, with the
    verification summary in the commit body (the [lanes](verify.md) and test patterns run, such as `unit=invoice`, and
    the reason for each skipped lane); prints the buffered output; when the checkout is bound to a task, fires
    `completed` with scope `task`, then `deliver` with scope `task` when the work is concluded
    ([lifecycle events](#lifecycle-events)); exits 0.
12. If the attempt fails after the agent ran: saves the attempt's changes as `last-attempt.patch` and the full output as
    `last-worker-output` in the checkout's state directory, returns the checkout to the base commit through the
    version-control recipe (it was clean when the worker started, so every change is the attempt's), removes every
    occurrence of the `--withhold` text from the output, prints the rest, and exits 1. The next attempt therefore starts
    from files the worker checked, including the `./cook` it is started through.

Stopping the worker stops the agent and any replayed checks, with every process they started.

The agent never commits, pushes or fires lifecycle events. The binary refuses `the-cook lifecycle run` under
`COOK_WORKER=1`, whatever spelling started it. As defence in depth, the binary renders `[the-cook, lifecycle]` into
`worker.deny` in each spelling `the-cook` renders ([policy](policy.md#the-floor)). The repository's `worker.deny`
denies the other commands an agent must not run, such as `git commit`, `git push` and `gh`, which the `ralph-tui`
recipe adds as entries. An agent that lacks a permission reports the task as blocked in its output.

An [`agent` lifecycle action](lifecycle.md#agent) runs under the same contract and sets the same `COOK_WORKER=1`
mark.

### The completion signal

A loop runner decides that a task is done by reading something from the agent's output. ralph-tui reads
`<promise>COMPLETE</promise>`. `the-cook` knows no loop runner's signal: the caller passes it as text with
`--withhold <text>`. On a verified attempt the worker prints the agent's output as it is, signal included. On a failed
attempt it removes every occurrence of that text before printing, so the loop runner sees a failure even when the
agent claimed success.

For example, an agent that prints `<promise>COMPLETE</promise>` but leaves no receipt fails at step 10. The worker
prints its output without the signal, exits 1, and ralph-tui counts the attempt as failed.

### Lifecycle events

`the-cook worker` fires events only for a task bound to its checkout: a managed worktree created with
`the-cook worktree create <name> --task <id>`, where `COOK_TASK` is set ([tasks](tasks.md)). It fires them in its
own process, after the agent has exited. A loop runner's worktree holds a whole run and is bound to no task, so there
the worker fires no event, and ralph-tui's own tracker records each task's status. Every event of a run is fired by
whoever drives it, with [`the-cook lifecycle run`](lifecycle.md#the-cook-lifecycle-run): a person, a
[coordinating agent](#running-it-from-an-agent-session) or a script around the loop runner.

| When | Event | Scope | Fired by |
| --- | --- | --- | --- |
| an attempt in a worktree bound to a task passes its replay | `completed` | `task` | `the-cook worker` |
| right after, when the work is [concluded](lifecycle.md#scopes) | `deliver` | `task` | `the-cook worker` |
| the run finished every task | `completed` | `run` | the caller: `./cook lifecycle run completed --scope run` |
| the run stopped with tasks left open | `blocked` | `run` | the caller: `./cook lifecycle run blocked --scope run` |
| `the-cook worker` exits 3 without a terminal | `blocked` | as for [any command that exits 3](lifecycle.md#scopes) | the binary |

At the worker's trigger points only actions declared with `auto: true` run, read from the base commit, so an agent
cannot add or change an action its own task triggers. A failing action is reported on stderr and recorded in the
[lifecycle log](lifecycle.md#the-lifecycle-log); it never changes the worker's exit code. The
[`completed` guard](lifecycle.md#built-in-guards) applies to the worker's `completed` as to any other: without a valid
receipt, when the verify module is on, it is refused and no action runs.

### How tasks run

The loop runner decides whether tasks run one after another or in parallel, and where each runs. With ralph-tui:

| Mode | Where each task runs | How the result lands |
| --- | --- | --- |
| Sequential (the default) | the checkout ralph-tui runs in, such as a managed worktree | with `--commit`, the worker commits each verified task there, so the next task starts from it |
| Parallel (`ralph-tui run --parallel <n>`) | ralph-tui's own worker checkouts, by default under `.ralph-tui/worktrees` | the worker commits each verified task in its checkout; ralph-tui merges the commits onto its session branch |

In both modes the worker runs `bootstrap` once in each checkout it starts in. Parallel scheduling, merging and conflict
handling are ralph-tui's.

In a sequential run each attempt's base is the previous task's commit. The tamper check fails any attempt that changes
`.cook/` or a file standing context names, so no task's commit changes the rules for the tasks after it.
A failed attempt leaves the checkout at its base, so ralph-tui's retry starts the task over.

### Delivery

The result of a ralph-tui run is a local branch with one commit per task: the worktree's branch, such as
`work/run-invoice`, in sequential mode, or ralph-tui's session branch in parallel mode. The binary contains no delivery
logic of its own; the repository's `deliver` actions define what delivery means ([lifecycle](lifecycle.md)).

| Delivery | How |
| --- | --- |
| Per task | in a worktree bound to a task, `deliver` actions with `auto: true` and scope `task` run from the worker after its verified attempt |
| Per run | the caller runs `./cook lifecycle run deliver --scope run` once the run is done |
| After judgement | choosing which task commits go together, splitting them across branches, rewording messages: a coordinating agent does this with the [shipped skill](skills.md#the-shipped-skill), or a person by hand, then runs `./cook lifecycle run deliver` on each resulting branch |

Most runs need judgement: the task commits are small and numerous, and a reviewer reads a pull request, not a run.
Once the delivered work is merged, `./cook worktree remove <name>` fires `landed` and removes the run's worktree
([worktrees](worktrees.md#remove)).

### The ralph-tui recipe

The [`ralph-tui` recipe](recipes.md#ralph-tui) describes ralph-tui. `./cook recipe apply ralph-tui` adds it to the
profile's `recipes:`, shows what it would write and, once confirmed, writes:

| Item | Kind | Content |
| --- | --- | --- |
| `.ralph-tui/config.toml` | [template](recipes.md#templates) | one `[[agents]]` entry per agent declared in the profile, whose command is `./cook worker`, plus the ralph-tui options a run under the worker needs |
| `worker.deny` in `.cook/policy.yaml` | [entries](recipes.md#entries) | `[git, commit]`, `[git, push]`, `[gh]` |
| the recipe's block in `.gitignore` | [render](recipes.md#renders) | `/.ralph-tui/*` and `!/.ralph-tui/config.toml` |

The template is rendered once and written only when `.ralph-tui/config.toml` is absent. From then on it is the
repository's own file: `the-cook` never re-renders, reads or removes it. When the file exists, `recipe apply` reports
`kept .ralph-tui/config.toml: exists` and writes nothing to it. The recipe adds no lifecycle actions, because nothing
fires a run's events automatically.

For a profile that declares `claude` and `codex`, the template is:

```toml
autoCommit = false

[errorHandling]
continueOnNonZeroExit = false

[[agents]]
name = "claude"
plugin = "claude"
command = "./cook"
defaultFlags = ["worker", "claude", "--commit", "--withhold", "<promise>COMPLETE</promise>"]

[agents.options]
skipPermissions = false

[[agents]]
name = "codex"
plugin = "codex"
command = "./cook"
defaultFlags = ["worker", "codex", "--commit", "--withhold", "<promise>COMPLETE</promise>"]

[agents.options]
fullAuto = false
```

| Key | Why |
| --- | --- |
| `command`, `defaultFlags` | ralph-tui 0.12 starts an agent as `command` with `defaultFlags` first, then the flags of its own agent plugin, so the worker reads its own flags and passes the rest to the agent ([argument passing](#argument-passing)) |
| `--commit` | the worker commits each verified task, so the next task starts from a clean checkout |
| `--withhold`, `<promise>COMPLETE</promise>` | ralph-tui's completion signal reaches it only from a verified attempt |
| `options.skipPermissions = false` | ralph-tui's own Claude agent plugin otherwise adds `--dangerously-skip-permissions`, which the worker refuses |
| `options.fullAuto = false` | ralph-tui's own Codex agent plugin otherwise adds `--full-auto` |
| `autoCommit = false` | the worker commits a verified task; ralph-tui does not commit in sequential mode |
| `errorHandling.continueOnNonZeroExit = false` | a worker that exits 1 failed its attempt, and ralph-tui handles it as a failure |

ralph-tui merges `~/.config/ralph-tui/config.toml` into the project's configuration. `the-cook` reads neither; a bypass
either of them adds is refused by the worker at the first task. `ralph-tui config show --sources` prints the merged
configuration and the files it came from.

ralph-tui keeps its session, progress and iteration logs under `.ralph-tui/` in the checkout it runs in. The recipe's
block in `.gitignore` ignores them and keeps only the configuration tracked; otherwise the worker refuses the checkout
as having uncommitted changes, and `--commit` would commit them. Every `the-cook render` writes the block, and
`the-cook render --check` reports it when it is edited:

```gitignore
/.ralph-tui/*
!/.ralph-tui/config.toml
```

The prompt each agent receives is ralph-tui's: its built-in prompt for the tracker, or a template the repository
configures in ralph-tui. The agent learns how to verify from the `verify` block of `AGENTS.md`
([verify](verify.md#the-verify-block-in-agentsmd)) and from the worker's standing context. ralph-tui 0.12's built-in
Beads prompt also tells the agent to run `bd close`; with the `beads` recipe, `worker.deny` refuses that command, and
ralph-tui's Beads tracker closes the bead itself (`bd close --force`) once it receives the completion signal, so the
bead closes only after a verified attempt.

## Reference

### `the-cook worker`

```text
the-cook worker <agent> [--commit] [--withhold <text>] [-- <agent args>...]
```

| Argument or flag | Default | Meaning |
| --- | --- | --- |
| `<agent>` | required | an agent declared in the profile's `agents`, such as `claude` or `codex` |
| `--commit` | off | After a passing replay, commit the work in the checkout, with the verification summary in the body |
| `--withhold <text>` | none | On a failed attempt, remove every occurrence of this text from the printed output |
| `-- <agent args>...` | none | Everything after `--` goes to the agent unchanged |

`the-cook explain worker` prints the worker contract.

#### Argument passing

The worker reads its own flags first. The first argument it does not recognise, and everything after it, go to the agent
unchanged, after the arguments the launch operation of the agent's harness recipe pins. A loop runner that places its
own arguments after the configured ones therefore needs no `--`; `--` makes the split explicit in a script. The prompt
comes from stdin or from the agent arguments, as the caller passes it.

For example, `ralph-tui run --agent claude --model opus` with the recipe's template starts, with the prompt on stdin:

```text
./cook worker claude --commit --withhold '<promise>COMPLETE</promise>' --print --model opus
```

The worker takes `claude`, `--commit` and `--withhold <promise>COMPLETE</promise>`, and passes `--print --model opus`
to Claude Code. A bypass argument the claude recipe marks as floor is refused anywhere in the agent arguments (step 3
of the [worker contract](#the-worker-contract)).

#### Exit codes

| Exit code | Meaning |
| --- | --- |
| 0 | The attempt was verified: the replay passed, the work was committed with `--commit`, and the output was printed |
| 1 | Refused (uncommitted changes, a floor bypass argument, Codex declared with `role: interactive`, a cached recipe whose hash does not match the profile), or a failed attempt (a base that cannot be read, a tamper-check difference, a missing or malformed receipt, a failing replay). The output was printed without the `--withhold` text |
| 3 | A prerequisite is missing: the agent binary, the version-control recipe's program, or a fetched recipe that is not in the cache (the message names `./cook recipe fetch`). Without a terminal this fires `blocked` |

#### Environment

| Variable | Set for | Value |
| --- | --- | --- |
| `COOK_WORKER` | the agent and every process it starts | `1`: `the-cook lifecycle run` refuses, and `attention` and `turn_end` hooks do nothing |
| `COOK_TASK` | the actions of the events the worker fires | the task id the worktree was created with (`--task`) |
| `COOK_ACTOR` | the actions of the events the worker fires | the managed worktree's actor, `<user>:<worktree>:<random>` |

The other `COOK_*` variables those actions receive are listed under
[lifecycle environment](lifecycle.md#environment).

#### Files

Paths below show the default `state.dir: .work` ([profile](profile.md#state-paths)).

| Path | Read or written |
| --- | --- |
| the version-control recipe and the agent's harness recipe | resolved from the release or the user's recipe cache before anything else runs |
| `.cook/config.yaml`, `.cook/policy.yaml`, `.cook/hooks.yaml` | read from the base commit |
| the tool-owned files of the checkout | restored from the base commit before the agent starts, compared after it exits |
| `<checkout>/.work/verified.json` | the receipt the agent wrote, read |
| `<checkout>/.work/` | the record that `bootstrap` ran in this checkout |
| `<checkout>/.work/last-worker-output` | the full output of a failed attempt |
| `<checkout>/.work/last-attempt.patch` | the changes of a failed attempt, removed from the checkout |
| a commit on the checkout's branch | with `--commit`, after a passing replay |
| `.work/lifecycle.jsonl` in the primary checkout | one line per event the worker fires |

## Examples

### A Node API: a Beads epic with ralph-tui

`acme/api` declares Claude Code and Codex. Apply the recipe and commit what it wrote:

```sh
./cook recipe apply ralph-tui
git add -A
git commit -m "chore: run ralph-tui through the-cook worker"
```

Create the epic. The first `bd create` prints only the new epic's id (`--silent`), here `acme-api-a10`. Each story
names it with `--parent` and gets a child id, `acme-api-a10.1` and `acme-api-a10.2`, and `bd dep add` makes US-002
wait for US-001.

```sh
bd create --type epic --title "Invoice rounding" --no-inherit-labels --body-file epic.md --silent
bd create --parent acme-api-a10 --title "US-001: Round at the domain boundary" --no-inherit-labels --body-file us-001.md --silent
bd create --parent acme-api-a10 --title "US-002: Show rounded totals in the API" --no-inherit-labels --body-file us-002.md --silent
bd dep add acme-api-a10.2 acme-api-a10.1
```

Run it in its own worktree, then fire the run's event:

```sh
./cook worktree create run-invoice
cd .work/worktrees/run-invoice
ralph-tui run --agent claude --tracker beads --epic acme-api-a10 --headless
./cook lifecycle run completed --scope run    # every task finished
./cook lifecycle run blocked --scope run      # instead, when tasks were left open
```

The branch `work/run-invoice` then holds one commit per verified task.

### A Go repository: a PRD with parallel workers on Codex

The stories are a JSON file in the primary checkout's ignored state directory; ralph-tui runs three Codex workers in
parallel:

```sh
./cook worktree create run-export
prd="$PWD/.work/prds/export-csv.json"
cd .work/worktrees/run-export
ralph-tui run --agent codex --prd "$prd" --parallel 3 --headless
./cook lifecycle run completed --scope run
```

### A team's own loop script

`acme/api` also runs one-off batches without ralph-tui: a directory of prompt files, one task each, worked through in
a managed worktree. The script reads the worker's exit code and fires the run's event itself:

```sh
#!/bin/sh
# scripts/run-batch.sh <prompt-dir>: one verified commit per prompt file, stopping at the first failure
for prompt in "$1"/*.md; do
  ./cook worker claude --commit < "$prompt"
  case $? in
    0) ;;
    3) exit 3 ;;
    *) ./cook lifecycle run blocked --scope run; exit 1 ;;
  esac
done
./cook lifecycle run completed --scope run
```

```sh
./cook worktree create batch-docs
cd .work/worktrees/batch-docs
../../../scripts/run-batch.sh ../../../batches/docs
```

The worker gives the script the same guarantees it gives ralph-tui: exit 0 means a replayed receipt. A worker that
exits 3 without a terminal has already fired `blocked` itself.

### A Terraform repository

`acme/infra` does not apply the `ralph-tui` recipe: `terraform plan` needs credentials and judgement an unattended agent
must not hold.

### Running it from an agent session

A coordinating Claude Code session started with `claude --permission-mode auto` can run the steps of the
[Node API example](#a-node-api-a-beads-epic-with-ralph-tui): create the worktree, start ralph-tui headless, watch it,
and fire `completed` or `blocked` with scope `run` when it ends. Its own policy must permit `ralph-tui run`. For
delivery it arranges the task commits on branches, guided by the [shipped skill](skills.md#the-shipped-skill), then
runs `./cook lifecycle run deliver` on each; the seeded `ask` entry for that command asks a person first
([policy](policy.md#the-seeded-entry)).

## Guidelines

Preparing a Beads epic for ralph-tui:

- Make each task's description self-contained: the story, the rules the agent needs, and, last, a
  `## Acceptance Criteria` section with the criteria and quality gates as a `- [ ]` checklist. ralph-tui reads the
  criteria from that section, not from `bd`'s acceptance field.
- Put dependencies on the story that needs them, not on the epic: `bd ready` treats every descendant of a blocked
  bead as blocked.
- Use `bd create --parent … --no-inherit-labels` unless the children should carry the epic's labels.
- Always pass `--epic`, so the run takes only that epic's children.

Running:

- Do commit before creating the run's worktree, including any change to `.cook/`, `.agents/` and
  `.ralph-tui/config.toml`: the worktree starts from committed code, and the worker reads the rules from the base
  commit.
- Do keep `--commit` in a sequential run: without it, the second task's worker refuses the first task's uncommitted
  work.
- Do keep tasks that change `.cook/` or standing context out of a run: the worker fails them. Make those changes
  interactively and commit them before the run.
- Do read `last-attempt.patch` in the checkout's state directory after a failed attempt: the checkout itself is back
  at its base.
- Do keep `skipPermissions` and `fullAuto` at `false` in every ralph-tui configuration that names the worker,
  including `~/.config/ralph-tui/config.toml`.
- Don't edit a PRD file by hand during a run.
- Don't fire lifecycle events from inside the agent's task: fire run-scope events from the session that started the
  loop runner.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| Refused: uncommitted changes | The checkout has changes: ralph-tui's state is not ignored, or the previous task's work was not committed | Check that `ralph-tui` is in the profile's `recipes:` and run `./cook render`, which writes its `.gitignore` block; keep `--commit` in `defaultFlags` |
| Refused: permission bypass, at the first task | ralph-tui's agent plugin adds a bypass flag: `skipPermissions` or `fullAuto` is not `false` in the project or global configuration | Set it to `false` in the agent's `[agents.options]`; `ralph-tui config show --sources` shows where it is set |
| ralph-tui reports a configuration error in `.ralph-tui/config.toml` | A key is at the wrong level, such as `continueOnNonZeroExit` outside `[errorHandling]` | Compare with the [recipe's template](#the-ralph-tui-recipe) |
| Every task fails with completion withheld | The agent does not run `the-cook verify`, or the replay fails | Read `last-worker-output` in the checkout's state directory; run the lanes yourself there |
| An attempt fails naming a tool-owned file, a file under `.cook/` or a context file | The tamper check found the agent changed a rendered rule, an owned settings key, a hook entry, `cook`, a repository file under `.cook/` or a file standing context names | Make that change interactively and commit it before the run; the worker reads the rules from the base commit |
| The checkout lost a failed attempt's edits | A failed attempt returns the checkout to its base | The edits are in `last-attempt.patch` in the checkout's state directory; `git apply` it to continue by hand |
| Codex worker refused | Codex is declared with `role: interactive` | Set `agents.codex.role: worker` and render, or use Claude workers |
| Exit 3 | The agent binary or the version-control program is not installed, or a fetched recipe is not in the cache | Install the program, or run `./cook recipe fetch`; `./cook doctor` names what is missing |
| An attempt fails before the agent starts: the base cannot be read | The version-control recipe could not read the checkout's head commit | Check the checkout with its version-control program, such as `git status` with the git recipe, then start the worker again |
| No `completed` or `deliver` action runs after a verified task in a ralph-tui run | The run's worktree is bound to no task, so the worker fires no event | Expected: ralph-tui's tracker records the task's status; fire run-scope events after the run ([lifecycle events](#lifecycle-events)) |
| `the-cook lifecycle run` refused during a run | It ran under `COOK_WORKER=1`, inside the agent | Fire it from the session that started the loop runner |
| One pull request per task, too many to review | A task-scope `deliver` action with `auto: true`, fired by workers in worktrees bound to a task | Remove `auto` from that action and deliver after judgement |

## Coexistence with direct ralph-tui use

- ralph-tui started with a configuration whose agent command is not `the-cook worker`, or a script that starts an agent
  directly, runs outside `the-cook`: no bypass refusal, tamper check or replay applies to it. `the-cook` neither detects
  nor prevents such a run.
- `the-cook` reads no ralph-tui configuration, neither the project's `.ralph-tui/config.toml` nor
  `~/.config/ralph-tui/config.toml`, and never writes them after the recipe seeded the project's file.
- ralph-tui's worker checkouts, session branches and state under `.ralph-tui/` are ralph-tui's; `the-cook` never cleans
  them up.

See [compatibility](compatibility.md#ralph-tui).
