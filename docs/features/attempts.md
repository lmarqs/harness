# Attempts: `the-cook attempt`

## Objective

**Problem.** A developer with a list of small, well-specified tasks wants them implemented while nobody watches, by a
coding agent or another program, started by whatever program the team uses to work through a task list. A coding agent
left alone can turn off its permission checks, change the rules or tests its work is judged by, and report a task done
that is not. Without protection against that, the developer either watches every session or cannot trust what was
done.

**Goal.** The developer comes back to a local branch holding only tasks whose checks passed under the rules the
repository committed, whichever program started them. `the-cook` started no program with more permissions than the
repository grants it.

## Overview

An **attempt** is one unattended run of a [worker](workers.md) on one task, under the guards of the
[attempt contract](#the-attempt-contract). It is how the [Implement](lifecycle.md#implement) stage of the lifecycle
runs when nobody watches, and its replayed receipt is the proof the [Complete](lifecycle.md#complete) stage needs.

`the-cook attempt <worker>` runs one attempt of a declared worker, in the checkout it is started in. It resolves the
ingredients the attempt uses, pins the worker's launch, refuses permission bypass, restores and checks the tool-owned
files around the worker, replays the worker's verification with rules read from the base commit, and reports the
outcome through its exit status and, in a workspace bound to a task, [lifecycle events](lifecycle.md). A completion
signal the caller expects is text the caller hands to `the-cook attempt`, which prints it only for a verified attempt.

`the-cook attempt` is the command a **loop runner** runs for each task. A loop runner is a third-party program that
works through a list of tasks and starts a worker for each. `the-cook` has no code for any loop runner: it does not
start one, read its configuration or know when a run starts or ends. An
unattended run of a loop runner whose command for each task is `the-cook attempt <worker>` or
`the-cook make <recipe> --unattended` is a **loop run**. A station that uses a loop runner, applied from a cookbook,
seeds its configuration with `./cook attempt`, or with `./cook make <recipe> --task <id> --unattended`, as that command
([how a loop runner calls it](#how-a-loop-runner-calls-it)). A team's own script calls `the-cook attempt` the same way
([example](#a-teams-own-loop-script)).

A loop runner may instead name `the-cook make <recipe> --task <id> --unattended` as its command for each task, with the
same caller-protocol arguments, so each task becomes one [recipe run](recipes.md#the-run): one pass of one change
through the stages ([through a recipe](#through-a-recipe)). `the-cook attempt` stays the primitive for scripts, and it
is what a recipe's `attempt` step runs, under the same contract ([in a recipe step](#in-a-recipe-step)).

Commands are named `the-cook <command>` on this page. The examples run them through the repository's wrapper,
`./cook <command>`, from the root of a checkout; with a global install, `the-cook <command>` is equivalent
([wrapper](wrapper.md)).

## When to use it

- A set of tasks is broken down so a worker can implement them one at a time.
- You want the work done while you are away, on a local branch you review and deliver afterwards.
- You want the same safety rules for every worker the repository declares, whichever loop runner starts them.

Use an interactive session instead when the work needs decisions only a person can make as it goes. When every task
should go through the same steps and lanes, each in its own workspace and on its own branch, have the loop runner run a
[recipe](recipes.md) for each task ([through a recipe](#through-a-recipe)).

Prerequisites:

| Need | Why |
| --- | --- |
| the worker's program installed and declared in [`workers`](profile.md#workers), its ingredient pinned in the profile's `ingredients:` | the program the attempt starts, and the [worker ingredient](workers.md#the-worker-contract) that says how to start it unattended |
| a worker ingredient that delivers `unattended.deny`, as launch arguments or through rendered permissions | the binary's own deny on lifecycle actions and the repository's unattended denies hold in every attempt ([the worker contract](workers.md#the-worker-contract)) |
| the version-control ingredient the profile names in `vcs:`, pinned in `ingredients:`, and its program | the attempt reads the base, compares and resets through it ([the version-control contract](ingredients.md#the-version-control-contract)) |
| every ingredient the attempt uses in the cache | an attempt never fetches; run `./cook fetch` first |
| for a worker declared with a [jail](workers.md#jails), the jail's program, its ingredient pinned, and a version-control ingredient that fills `metadata` | the worker and the replay run inside the jail, with the version-control metadata shared read-only ([the jail contract](ingredients.md#the-jail-contract)) |
| `role: unattended`, for a worker whose ingredient passes `unattended.deny` only through rendered rules | its unattended denies come only from rendered rules ([roles](workers.md#roles)) |
| a clean, committed checkout | `the-cook attempt` refuses uncommitted changes |
| `the-cook verify` working in this repository | every attempt must leave a [receipt](verify.md#the-receipt), the record of a passing `the-cook verify`, which `the-cook attempt` replays |
| a loop runner, or a script of your own | it picks the tasks and starts an attempt for each |

`the-cook doctor` checks the version of each program an ingredient in use describes, the version control program and
the workers included, against that ingredient's supported range. A loop runner is the team's to install and update.

**Quick reference:** [Signature and exit codes](#the-cook-attempt) · [Argument passing](#argument-passing) ·
[Environment variables](#environment) · [Files](#files)

## How it works

### The attempt contract

For each attempt, `the-cook attempt <worker>`:

1. Resolves, before anything else runs, the version-control ingredient the profile names in `vcs:`, the worker's
   ingredient (`workers.<name>.ingredient`, by default the worker's name) and, for a worker with a jail, the jail's
   ingredient (`workers.<name>.jail.ingredient`), each from the verified cache in the user's cache directory,
   `$COOK_HOME/cookbook/<sha256>.tar.gz`. It never fetches: a pinned ingredient that is not in the
   cache exits 3 and names `./cook fetch`; a profile where no pinned ingredient fills the version-control contract exits
   3 and names `./cook ingredient pin`; a worker whose ingredient can deliver `unattended.deny` in neither way, no deny
   arguments at launch and no rendered permissions, exits 3 and names that gap. Every read, comparison, reset and launch
   of the attempt uses this resolution, so nothing in the checkout changes how the base is read or how the worker
   starts.
2. Records the base: the head commit of the checkout when it starts, as the version-control ingredient reports it. A
   base that cannot be read fails the attempt, exit 1, before the worker starts. It refuses to run, exit 1, when the
   version-control ingredient reports uncommitted changes.
3. Refuses the bypass arguments the worker's ingredient marks as [floor](ingredients.md#the-floor), and for a worker
   with a jail those the jail's ingredient marks, wherever they appear among the arguments it receives and, for the
   jail's, in `jail.args`. It exits 1 and names the argument. It reads no loop runner's configuration: a bypass a loop
   runner adds reaches `the-cook attempt` as an argument and is refused there, at the first task.
4. Reads the profile, the policy, the hooks file and the [standing context](context.md) from the base commit through
   the version-control ingredient, not from the files in the checkout, so a worker cannot weaken the rules its own
   result is checked against or rewrite its own instructions between attempts.
5. Runs the **tamper check**, first half: restores every tool-owned file in the checkout (the renders of every
   ingredient in use, such as rendered rule files, owned keys of native settings files and `the-cook hook` entries,
   and the wrapper `cook` and its pin `.cook/wrapper.yaml`) from the base commit, which is their state when the attempt
   began, because the checkout is clean.
6. Pins the launch with the worker's ingredient: its unattended arguments, its sandbox-root arguments, its network
   arguments for a worker declared with `network: true`, the way it passes `unattended.deny`, as launch arguments or
   through the rendered rules the tamper check keeps intact, and the declared model and effort. For a worker with a
   sandbox, the writable roots are the checkout and the directories listed in `workers.<name>.writable_roots`, such as a
   task tracker's database directory ([profile](profile.md#workers)). The clone's state directory is not among them:
   each checkout keeps its own state directory inside itself (receipt, session, hook cache, run record, bootstrap record
   and attempt output); the primary checkout's holds only `lifecycle.jsonl` and the locks every workspace shares, which
   the binary writes outside the worker; and it also holds every managed workspace, so opening it would let a worker in
   one workspace write every other. A repository that needs more names it in `writable_roots`. A worker declared with
   `network: false`, the default, gets no argument about the network, and its program's default holds. For a worker with
   a jail, it composes the jailed launch around that pinned launch: the jail's command, its working-directory share with
   the checkout, one write share per `writable_roots` entry, the read shares (the paths the version-control ingredient's
   `metadata` reports, then `$COOK_HOME`), one `env.pass` per variable it passes, the jail's `network` arguments with
   `network: true`, `jail.args` and the jail's separator ([the jailed launch](ingredients.md#the-jailed-launch)).
7. Runs the profile's `bootstrap` once per checkout, and records that in the checkout's state directory, so later
   attempts in the same checkout skip it.
8. Runs the worker with its prompt, read from stdin or from the worker arguments, whichever the caller uses, and
   `COOK_ATTEMPT=1` in its environment. A prompt on stdin carries the `always` entries of the `unattended` role, read
   from the base commit, before the task text, and the binary also sets `COOK_CONTEXT_IN_PROMPT=1`, under which the
   session-start hook injects no `always` entry; a prompt in the worker arguments is passed as it is, and the
   session-start hook delivers those entries ([context](context.md#delivery-in-the-prompt)). It buffers the worker's
   output instead of streaming it. Every `the-cook` process the worker starts sees `COOK_ATTEMPT=1` and refuses
   `the-cook lifecycle run`, and the worker's `attention` and `turn_end` [hooks](hooks.md) do nothing. A worker with a
   jail runs inside it, started from the checkout; the jail receives every `COOK_*` variable set for the worker, and
   `COOK_HOME` with the cache's absolute path, so these marks hold inside the jail whatever environment it gives.
9. Runs the tamper check, second half: compares with the base commit the tool-owned files, the files under `.cook/`
   and every file and skill a `context` entry names. Any difference fails the attempt and is reported by path: a worker
   that rewrote its own rules cannot complete, and a change to the rules reaches a run only through a commit a person
   made.
10. Reads the receipt the worker wrote with `the-cook verify`. A missing or malformed receipt is a failed attempt.
    Otherwise `the-cook attempt` replays the lanes and skip reasons the receipt selected against the final content,
    with the profile from the base commit. The receipt only selects; the replay is the proof. For a worker with a jail,
    every lane command and validator of the replay runs in the same jail, with the same shares, variables, network and
    `jail.args`, because it runs code the worker wrote.
11. If the replay passes: with `--commit`, commits the work in the checkout through the version-control ingredient,
    with the verification summary in the commit body (the [lanes](verify.md) and test patterns run, such as
    `unit=invoice`, and the reason for each skipped lane); prints the buffered output; when the checkout is bound to a
    task, fires `completed` with scope `task`, then `deliver` with scope `task` when the work is concluded
    ([lifecycle events](#lifecycle-events)); exits 0.
12. If the attempt fails after the worker ran: saves the attempt's changes as `last-attempt.patch` and the full output
    as `last-attempt-output` in the checkout's state directory, returns the checkout to the base commit through the
    version-control ingredient (it was clean when the attempt started, so every change is the attempt's), removes
    every occurrence of the `--withhold` text from the output, prints the rest, and exits 1. The next attempt
    therefore starts from files `the-cook attempt` checked, including the `./cook` it is started through. Only
    `the-cook attempt` run on its own resets the checkout this way; a recipe's `attempt` step leaves its changes in
    place ([in a recipe step](#in-a-recipe-step)).

Stopping `the-cook attempt` stops the worker and any replayed checks, with every process they started.

The worker never commits, pushes or fires lifecycle events. The binary refuses `the-cook lifecycle run` under
`COOK_ATTEMPT=1`, whatever spelling started it. As defence in depth, the binary renders `[the-cook, lifecycle]` into
`unattended.deny` in each spelling `the-cook` renders ([policy](policy.md#the-floor)). The repository's
`unattended.deny` denies the other commands a worker must not run, such as commits, pushes and the hosting platform's
CLI, which a station may add as [entries](stations.md#entries) when it is applied. A worker that lacks a permission
reports the task as blocked in its output.

An [`attempt` lifecycle action](lifecycle.md#attempt) runs under the same contract and sets the same `COOK_ATTEMPT=1`
mark, and so does a recipe's `attempt` step ([in a recipe step](#in-a-recipe-step)).

### In a recipe step

A recipe's `attempt` step ([step kinds](recipes.md#step-kinds)) starts a worker the profile declares with role
`unattended` on the step's prompt, in the run's workspace. It keeps every part of the attempt contract that bounds the
worker: the launch pinned by the worker's ingredient, the unattended denies, the floor bypass arguments refused, the
tamper check around the worker, its rules read from the workspace's head commit, and `COOK_ATTEMPT=1` in the worker's
environment. The tamper check compares `.cook/` and the tool-owned files with their state when the step began, the
uncommitted edits of the steps before it included, and restores that state, so a `--here` run of an uncommitted draft
recipe keeps its draft. Its prompt is the step's own, or the one the caller of `the-cook make` passes on stdin; the
arguments given to `the-cook make` after `--` reach its worker after those the ingredient pins, and a floor bypass
argument among them is refused. The recipe's inputs reach the prompt as plain text, a prompt-injection surface as in
`attempt` actions ([safety](safety.md#recipe-runs)). A worker with a [jail](workers.md#jails) runs inside it, and from
then on the run's later `run` steps, its Verify stage and the replay of its Complete stage run in the same jail, because
they run code the worker wrote ([what runs in the jail](ingredients.md#what-runs-in-the-jail)).

The run concludes the work. The step's edits stay in the workspace for the next steps, and the step neither replays a
receipt, commits nor fires events. A worker that runs `./cook verify` gets feedback on its change
and leaves the selection the run's Verify stage starts from; that stage runs the lanes itself, with every lane the
recipe requires added, and the run's Complete stage replays its receipt from the base.

A failed attempt step, where the worker exits non-zero or the tamper check finds a difference, stops the run as
blocked, naming the step. It does not reset the workspace: the worker's changes stay in it, with those of the steps
before, and nothing is set aside in `last-attempt.patch`. A person inspects them, keeps or discards them, and
`the-cook make --continue` runs the step again on the tree as it is ([the run](recipes.md#the-run)). The reset to the
base and the saved patch belong only to `the-cook attempt` run on its own.

### The completion signal

A loop runner decides that a task is done by reading something from the worker's output, such as a fixed tag a coding
agent prints. `the-cook` knows no loop runner's signal: the caller passes it as text with `--withhold <text>`. On a
verified attempt `the-cook attempt` prints the worker's output as it is, signal included. On a failed attempt it
removes every occurrence of that text before printing, so the loop runner sees a failure even when the worker claimed
success.

For example, with `--withhold '<task-done/>'`, a worker that prints `<task-done/>` but leaves no receipt fails at step
10. `the-cook attempt` prints its output without the signal, exits 1, and the loop runner counts the attempt as
failed.

### Lifecycle events

`the-cook attempt` fires events only for a task bound to its checkout: a managed workspace created with
`the-cook workspace create <name> --task <id>`, where `COOK_TASK` is set ([tasks](tasks.md)). It fires them in its
own process, after the worker has exited. A loop runner's workspace holds a whole run and is bound to no task, so
there `the-cook attempt` fires no event, and the loop runner's own task source records each task's status. Every event
of a run is fired by whoever drives it, with [`the-cook lifecycle run`](lifecycle.md#the-cook-lifecycle-run): a
person, a [coordinating worker](#running-it-from-a-coordinating-worker) or a script around the loop runner.

| When | Event | Scope | Fired by |
| --- | --- | --- | --- |
| an attempt in a workspace bound to a task passes its replay | `completed` | `task` | `the-cook attempt` |
| right after, when the work is [concluded](lifecycle.md#scopes) | `deliver` | `task` | `the-cook attempt` |
| the run finished every task | `completed` | `run` | the caller: `./cook lifecycle run completed --scope run` |
| the run stopped with tasks left open | `blocked` | `run` | the caller: `./cook lifecycle run blocked --scope run` |
| `the-cook attempt` exits 3 without a terminal | `blocked` | as for [any command that exits 3](lifecycle.md#scopes) | the binary |

When the loop runner's command for each task is `the-cook make <recipe> --task <id> --unattended` instead, each task has
its own workspace bound to it, and its `start`, `completed`, `deliver` and `blocked` come from the run
([through a recipe](#through-a-recipe)); the events of scope `run` stay the caller's.

At the trigger points of `the-cook attempt` only actions declared with `auto: true` run, read from the base commit, so
a worker cannot add or change an action its own task triggers. A failing action is reported on stderr and recorded in
the [lifecycle log](lifecycle.md#the-lifecycle-log); it never changes the exit code of `the-cook attempt`. The
[`completed` guard](lifecycle.md#built-in-guards) applies to the attempt's `completed` as to any other: without a
valid receipt, when the verify module is on, it is refused and no action runs.

### How tasks run

The loop runner decides whether tasks run one after another or in parallel, and where each runs:

| Mode | Where each task runs | How the result lands |
| --- | --- | --- |
| Sequential | the checkout the loop runner runs in, such as a managed workspace | with `--commit`, `the-cook attempt` commits each verified task there, so the next task starts from it |
| Parallel | the loop runner's own checkouts, one per task | `the-cook attempt` commits each verified task in its checkout; the loop runner merges the commits onto its own branch |

In both modes `the-cook attempt` runs `bootstrap` once in each checkout it starts in. Parallel scheduling, merging and
conflict handling are the loop runner's.

In a sequential run each attempt's base is the previous task's commit. The tamper check fails any attempt that changes
`.cook/` or a file standing context names, so no task's commit changes the rules for the tasks after it. A failed
attempt leaves the checkout at its base, so the loop runner's retry starts the task over.

### Delivery

The result of a run is a local branch with one commit per task: the workspace's branch, such as `work/run-invoice`, in
sequential mode, or the loop runner's own branch in parallel mode. The binary contains no delivery logic of its own;
the repository's `deliver` actions define what delivery means ([lifecycle](lifecycle.md)).

| Delivery | How |
| --- | --- |
| Per task | in a workspace bound to a task, `deliver` actions with `auto: true` and scope `task` run from `the-cook attempt` after its verified attempt |
| Per run | the caller runs `./cook lifecycle run deliver --scope run` once the run is done |
| After judgement | choosing which task commits go together, splitting them across branches, rewording messages: a coordinating worker does this with the [shipped skill](skills.md#the-shipped-skill), or a person by hand, then runs `./cook lifecycle run deliver` on each resulting branch |

Most runs need judgement: the task commits are small and numerous, and a reviewer reads a pull request, not a run.
Once the delivered work is merged, `./cook workspace remove <name>` fires `landed` and removes the run's workspace
([workspaces](workspaces.md#remove)).

### How a loop runner calls it

A loop runner works with `the-cook attempt` when its configuration holds these properties. `the-cook` reads none of
that configuration, neither the project's nor a per-user one the loop runner merges in, so they are the repository's
to keep:

| Property | Why |
| --- | --- |
| the command for every task is `./cook attempt <worker>`, with `--commit` and `--withhold <signal>`, followed by the loop runner's own arguments for the worker; or `./cook make <recipe> --task <id> --unattended` with the same caller-protocol arguments, the worker's after `--` ([through a recipe](#through-a-recipe)) | `the-cook attempt` reads its own flags and passes the rest to the worker ([argument passing](#argument-passing)) |
| no option adds a permission-bypass argument | `the-cook attempt` refuses it at the first task |
| the task's prompt goes on stdin | only a prompt on stdin carries the `always` entries of the `unattended` role, which then reach the worker whether or not its program runs hooks unattended ([context](context.md#delivery-in-the-prompt)) |
| an exit status other than 0 counts as a failed attempt | `the-cook attempt` exits 1 on a failed attempt |
| the loop runner does not commit in sequential mode | `the-cook attempt` commits a verified task |
| the loop runner's state files inside the checkout are ignored by the version control system | otherwise `the-cook attempt` refuses the checkout as having uncommitted changes, and `--commit` would commit them |
| the command that marks a task done in the task source is in `unattended.deny`, and the loop runner marks the task itself once it receives the completion signal | the task is marked done only after a verified attempt |

A station that uses a loop runner holds these properties for it: one of its [templates](stations.md#templates) seeds the
loop runner's configuration once, from then on the repository's own file, which `the-cook` never re-renders, reads or
removes; its [entries](stations.md#entries) add the `unattended.deny` rules; and the loop runner's ingredient renders
the ignore block for its state files ([renders](ingredients.md#renders)). Their pages in their cookbook explain each
setting.

The task text each worker receives is the loop runner's. A worker that reads `AGENTS.md` learns how to verify from its
`verify` block ([verify](verify.md#the-verify-block-in-agentsmd)). Standing context of the `unattended` role reaches
it as [context](context.md#delivery-in-the-prompt) describes: when the loop runner passes the prompt on stdin, the
`always` entries go before the task text, so they reach the worker even when its program runs no hook unattended.

### Through a recipe

A loop runner may name `./cook make <recipe> --task <id> --unattended` as its command for each task, in place of
`./cook attempt <worker>`, with the same caller-protocol arguments as `the-cook attempt`: `--commit`;
`--withhold <signal>`, whose text is printed only for completed work; and the loop runner's own arguments for the
worker, after `--`, because `the-cook make` reads the flags before it as the recipe's inputs. `--unattended` declares
that nobody drives the run, so a handoff stops it as blocked. Without it the run is attended: a handoff pauses it with
exit 3 and no completion text, and the loop runner never counts the task done. Any exit status other than 0 is a task
that is not done ([`the-cook make`](recipes.md#the-cook-make)). The loop runner fills in the task id, and the
task's prompt it passes on stdin reaches every `attempt` step that has no prompt of its own. Each task is then one
recipe run, one pass of one change through the stages:

| Stage | For each task |
| --- | --- |
| Start | its own managed workspace, at the revision of the shared default line of work, bound to the task; `start` (scope `task`) runs the tracker's automatic actions |
| Implement | the recipe's steps, in order; an `attempt` step runs the worker it names under the attempt contract ([in a recipe step](#in-a-recipe-step)) |
| Verify | the run runs every lane the change selects and every lane the recipe requires itself, none of the required ones skipped, and writes the receipt |
| Complete | the replay from the base; with `--commit`, the commit; then `completed` (scope `task`), and `deliver` (scope `task`) when the work is concluded |
| A stop | `blocked` (scope `task`), fired by the run itself, with the stage, the step and the reason; the workspace stays for a person |

Each run reads its recipe once, from its new workspace's base commit, the revision of the shared default line of work
this checkout last learned, and follows that content to the end, so no worker changes the recipe of the run it is in or
of the next task's ([guards](recipes.md#guards)). A station that uses a loop runner can add a recipe for this, such as
the `implement-task` recipe of `agentic-coding` ([stations](stations.md)).

Each task is delivered on its own, by the repository's `deliver` actions with scope `task` and `auto: true`, or by a
person afterwards. `the-cook attempt` stays the command for a loop runner whose tasks share one workspace and one
branch, and the primitive for scripts.

## Reference

### `the-cook attempt`

```text
the-cook attempt <worker> [--commit] [--withhold <text>] [-- <worker args>...]
```

| Argument or flag | Default | Meaning |
| --- | --- | --- |
| `<worker>` | required | a worker declared in the profile's `workers` |
| `--commit` | off | After a passing replay, commit the work in the checkout, with the verification summary in the body |
| `--withhold <text>` | none | On a failed attempt, remove every occurrence of this text from the printed output |
| `-- <worker args>...` | none | Everything after `--` goes to the worker unchanged |

`the-cook explain attempt` prints the attempt contract.

#### Argument passing

`the-cook attempt` reads its own flags first. The first argument it does not recognise, and everything after it, go to
the worker unchanged, after the arguments the launch of the worker's ingredient pins. A loop runner that places its own
arguments after the configured ones therefore needs no `--`; `--` makes the split explicit in a script. The prompt
comes from stdin or from the worker arguments, as the caller passes it; only a prompt on stdin carries the `always`
entries of standing context ([context](context.md#delivery-in-the-prompt)).

For example, a loop runner configured to run `./cook` with `attempt claude --commit --withhold '<task-done/>'` that
appends its own `--model <id>` starts, with the prompt on stdin:

```text
./cook attempt claude --commit --withhold '<task-done/>' --model <id>
```

`the-cook attempt` takes `claude`, `--commit` and `--withhold <task-done/>`, and passes `--model <id>` to the worker.
A bypass argument the worker's ingredient marks as floor is refused anywhere in the worker arguments (step 3 of the
[attempt contract](#the-attempt-contract)).

#### Exit codes

| Exit code | Meaning |
| --- | --- |
| 0 | The attempt was verified: the replay passed, the work was committed with `--commit`, and the output was printed |
| 1 | Refused: uncommitted changes; a floor bypass argument of the worker's ingredient or its jail's; a worker with a jail that fails validation ([jails](workers.md#jails)); a worker declared with `role: interactive` whose ingredient passes `unattended.deny` only through rendered rules; or a cached ingredient whose hash does not match its pin. Or a failed attempt: a base that cannot be read; a tamper-check difference; a missing or malformed receipt; or a failing replay. The output was printed without the `--withhold` text |
| 3 | A prerequisite is missing. Without a terminal this fires `blocked`. The worker's program; the version-control ingredient's program; the program of the worker's jail; a pinned ingredient, the jail's included, that is not in the cache (the message names `./cook fetch`); a version-control contract no pinned ingredient fills (the message names `./cook ingredient pin`); or a worker whose ingredient can deliver `unattended.deny` in neither way (the message names that gap) |

#### Environment

| Variable | Set for | Value |
| --- | --- | --- |
| `COOK_ATTEMPT` | the worker and every process it starts | `1`: `the-cook lifecycle run` refuses, and `attention` and `turn_end` hooks do nothing |
| `COOK_CONTEXT_IN_PROMPT` | the worker and every process it starts, when the prompt arrived on stdin | `1`: the prompt carried the `always` entries, and the session-start hook injects none ([context](context.md#delivery-in-the-prompt)) |
| `COOK_HOME` | the jail of a worker with one, among the variables it passes | the per-user cache's absolute path, which the jail shares read-only, so the wrapper and `the-cook` inside load the pinned release and ingredients from it and write nothing there |
| `COOK_TASK` | the actions of the events the attempt fires | the task id the workspace was created with (`--task`) |
| `COOK_ACTOR` | the actions of the events the attempt fires | the managed workspace's actor, `<user>:<workspace>:<random>` |

The other `COOK_*` variables those actions receive are listed under
[lifecycle environment](lifecycle.md#environment).

#### Files

Paths below show the default `state.dir: .work` ([profile](profile.md#state-paths)).

| Path | Read or written |
| --- | --- |
| the version-control ingredient, the worker's ingredient and its jail's | resolved from `$COOK_HOME/cookbook/<sha256>.tar.gz` before anything else runs |
| `.cook/config.yaml`, `.cook/policy.yaml`, `.cook/hooks.yaml` | read from the base commit |
| the tool-owned files of the checkout | restored from the base commit before the worker starts, compared after it exits |
| `<checkout>/.work/verified.json` | the receipt the worker wrote, read |
| `<checkout>/.work/` | the record that `bootstrap` ran in this checkout |
| `<checkout>/.work/last-attempt-output` | the full output of a failed attempt |
| `<checkout>/.work/last-attempt.patch` | the changes of a failed attempt, removed from the checkout |
| a commit on the checkout's branch | with `--commit`, after a passing replay |
| `.work/lifecycle.jsonl` in the primary checkout | one line per event the attempt fires |

## Examples

### A loop runner run in its own workspace

`acme/api` applied a station from `github:acme/cookbook` that uses a loop runner, and committed the configuration it
seeded. The run goes in its own workspace, and whoever started it fires the run's event:

```sh
./cook workspace create run-invoice
cd .work/workspaces/run-invoice
# start the loop runner here, headless, on one set of tasks
./cook lifecycle run completed --scope run    # every task finished
./cook lifecycle run blocked --scope run      # instead, when tasks were left open
```

The branch `work/run-invoice` then holds one commit per verified task.

### A team's own loop script

`acme/api` also runs one-off batches without a loop runner: a directory of prompt files, one task each, worked through
in a managed workspace. The script reads the exit code of `the-cook attempt` and fires the run's event itself:

```sh
#!/bin/sh
# scripts/run-batch.sh <prompt-dir>: one verified commit per prompt file, stopping at the first failure
for prompt in "$1"/*.md; do
  ./cook attempt claude --commit < "$prompt"
  case $? in
    0) ;;
    3) exit 3 ;;
    *) ./cook lifecycle run blocked --scope run; exit 1 ;;
  esac
done
./cook lifecycle run completed --scope run
```

```sh
./cook workspace create batch-docs
cd .work/workspaces/batch-docs
../../../scripts/run-batch.sh ../../../batches/docs
```

`the-cook attempt` gives the script the same guarantees it gives a loop runner: exit 0 means a replayed receipt. An
attempt that exits 3 without a terminal has already fired `blocked` itself.

### Running it from a coordinating worker

A coordinating worker is a session of a worker, typically a coding agent, that drives `the-cook` commands for a
person. Left alone in its unattended mode, it can run the steps of the
[loop runner example](#a-loop-runner-run-in-its-own-workspace): create the workspace, start the loop runner headless,
watch it, and fire `completed` or `blocked` with scope `run` when it ends. Its own policy must permit starting the loop
runner. For delivery it arranges the task commits on branches, guided by the
[shipped skill](skills.md#the-shipped-skill), then runs `./cook lifecycle run deliver` on each; the seeded `ask` entry
for that command asks a person first ([policy](policy.md#the-seeded-entry)).

## Guidelines

- Do make each task's description self-contained: the story, the rules the worker needs, and its acceptance criteria
  and quality gates, in the form the loop runner reads them.
- Do scope the run to one set of tasks, so it takes only those.
- Do commit before creating the run's workspace, including any change to `.cook/`, `.agents/` and the loop runner's
  configuration: the workspace starts from committed code, and `the-cook attempt` reads the rules from the base commit.
- Do keep `--commit` in a sequential run: without it, the second task's attempt refuses the first task's uncommitted
  work.
- Do keep tasks that change `.cook/` or standing context out of a run: their attempts fail. Make those changes
  interactively and commit them before the run.
- Do read `last-attempt.patch` in the checkout's state directory after a failed attempt: the checkout itself is back
  at its base.
- Do keep every option that adds a permission bypass off in every configuration of the loop runner that names
  `the-cook attempt`, a per-user one included.
- Don't give a recipe a loop runner runs for each task a `handoff` step: in an unattended run a handoff stops the task
  as blocked, because it needs a person.
- Do get a recipe onto the shared default line of work (for git, pushed and merged) before a loop runner runs it: an
  unattended run reads it from its workspace's base commit, the revision of that line this checkout last learned.
- Don't edit the run's task list by hand during a run.
- Don't fire lifecycle events from inside the worker's task: fire run-scope events from the session that started the
  loop runner.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| Refused: uncommitted changes | The checkout has changes: the loop runner's state is not ignored, or the previous task's work was not committed | Ignore the loop runner's state files (its ingredient renders the block; run `./cook render`); keep `--commit` in its command for each task |
| Refused: permission bypass, at the first task | The loop runner adds a bypass argument from its project or per-user configuration | Turn that option off where it is set |
| Every task fails with completion withheld | The worker does not run `the-cook verify`, or the replay fails | Read `last-attempt-output` in the checkout's state directory; run the lanes yourself there |
| An attempt fails naming a tool-owned file, a file under `.cook/` or a context file | The tamper check found the worker changed a rendered rule, an owned settings key, a hook entry, `cook`, a repository file under `.cook/` or a file standing context names | Make that change interactively and commit it before the run; `the-cook attempt` reads the rules from the base commit |
| The checkout lost a failed attempt's edits | A failed attempt returns the checkout to its base | The edits are in `last-attempt.patch` in the checkout's state directory; apply it with the version control program to continue by hand |
| Attempt refused for a worker with `role: interactive` | Its ingredient passes `unattended.deny` only through rendered rules | Set `workers.<name>.role: unattended` and render, or use a worker whose ingredient passes `unattended.deny` at launch |
| Exit 3 | The worker's program or the version-control program is not installed, a pinned ingredient is not in the cache, no pinned ingredient fills the version-control contract, or the worker's ingredient can deliver `unattended.deny` in neither way | Install the program, run `./cook fetch`, pin a version-control ingredient with `./cook ingredient pin`, or use a worker whose ingredient passes the denies; `./cook doctor` names what is missing |
| An attempt fails before the worker starts: the base cannot be read | The version-control ingredient could not read the checkout's head commit | Check the checkout with its version control program, then start the attempt again |
| No `completed` or `deliver` action runs after a verified task in a loop runner's run | The run's workspace is bound to no task, so `the-cook attempt` fires no event | Expected: the loop runner's task source records the task's status; fire run-scope events after the run ([lifecycle events](#lifecycle-events)) |
| `the-cook lifecycle run` refused during a run | It ran under `COOK_ATTEMPT=1`, inside the worker | Fire it from the session that started the loop runner |
| Every task a loop runner starts through `the-cook make` fails with exit 3 | The recipe requires a contract, role or lane the repository lacks, which the message names, or a step stopped the run, such as a handoff, which an unattended run cannot pass | Pin or declare what the message names; `./cook make --status <workspace>` shows the step that stopped; keep handoffs out of a recipe a loop runner runs |
| A loop runner counts no task done, and each run ends paused at a handoff | Its command lacks `--unattended`, so each run is attended: it pauses at the handoff, exits 3 and prints no completion text | Add `--unattended` to the command for each task; keep handoffs out of a recipe a loop runner runs |
| An `attempt` step stopped a recipe run | Its worker exited non-zero, or the tamper check found a changed tool-owned file, file under `.cook/` or context file | `./cook make --status <workspace>` names the step; its changes stay in the workspace, unlike after `the-cook attempt` on its own: inspect them, keep or discard them, then `./cook make --continue <workspace>` runs the step again |
| One pull request per task, too many to review | A task-scope `deliver` action with `auto: true`, fired by attempts in workspaces bound to a task | Remove `auto` from that action and deliver after judgement |

## Coexistence with direct loop runner use

- A loop runner started with a configuration whose command for each task is not `the-cook attempt`, or a script that
  starts a worker directly, runs outside `the-cook`: no bypass refusal, tamper check or replay applies to it.
  `the-cook` neither detects nor prevents such a run.
- `the-cook` reads no loop runner configuration, the project's or a per-user one, and never writes it after a
  station's template seeded the project's file.
- A loop runner's checkouts, branches and state files are its own; `the-cook` never cleans them up.

See [compatibility](compatibility.md).
