# the-cook

## Objective

**Problem.** A developer meeting the tool for the first time, in a repository they cloned or as a maintainer weighing it
for their own repositories, needs to know what it does, what it leaves alone and what it asks of them and their
teammates. Without one starting point that answers this and points onward, they piece it together from pages written for
readers who already know the tool, or judge it by its most visible use, coding agents, instead of by the lifecycle every
change goes through.

**Goal.** After one read, a newcomer can say what the tool is for and what it never does, can try it on a repository,
and can find the page for any part of it.

## Overview

`the-cook` is a command-line tool that enforces the software development lifecycle a repository declares.

Every change goes through the same stages: plan, start, implement, verify, complete, deliver, review and merge, and
landed, with `blocked` out of band for work that stops.

At fixed points between them the binary fires [lifecycle events](lifecycle.md), at which the actions the repository
declares run (a push, a pull request, a tracker update, a cleanup).

It holds the guards between stages: delivery runs only for committed work off the default branch and, with verification
on, verified work.

The proof a change carries is a receipt bound to its base and its exact content ([verify](verify.md)). An unattended
attempt is reported done only after that proof is replayed from the base
([ADR 0015](../adrs/0015-development-lifecycle.md)).

People and workers move work through the stages with the same commands: [`the-cook make`](recipes.md#the-cook-make),
which takes one change through the stages as a recipe says, task [workspaces](workspaces.md), [task binding](tasks.md),
which ties a workspace to a task id from any tracker, and [`the-cook attempt`](attempts.md), which runs one worker for
one task under rules it cannot bypass.

The lifecycle knows no program. Each stage that needs one reaches it through a contract or a declared action: the
version-control contract for workspaces, bases, history and landed work, the worker contract for the programs that
change the repository for someone, such as coding agents, bots and codemods, and lifecycle actions for everything else.
The binary contains code and data for no external program. What a repository adds to the lifecycle comes in three units,
and one rule separates them: facts about programs are pinned and rendered; practices are copied and become the
repository's own ([ADR 0022](../adrs/0022-tool-integration.md), [ADR 0024](../adrs/0024-ingredients-and-stations.md),
[ADR 0025](../adrs/0025-recipes.md)).

| Unit | What it is | How a repository holds it | When it acts |
| --- | --- | --- | --- |
| [Ingredient](ingredients.md) | the facts about one program: the contract it fills, the configuration rendered for it, such as a coding agent's permission rules, hooks and instructions, its floor, the rules about it no repository can remove, and its checks | pinned by cookbook, version and sha256 in `ingredients:` | on every render and every launch |
| [Station](stations.md) | what one way of working needs in place: the ingredients to pin, the workers and their roles, entries for the profile, the policy and the hooks file, templates, and the recipes to add | applied once: its entries, templates and recipes become the repository's own; recorded in `stations:` | once, and again to take a newer version |
| [Recipe](recipes.md) | one kind of change, made many times: its inputs, its requirements, its steps mapped onto the stages, the lanes it requires | copied into `.cook/recipes/<name>/` with its origin recorded, or written there by the repository; the repository's own to edit | every time someone runs it |

Ingredients and stations come from cookbooks, and so may recipes; the binary ships none and names no default cookbook.
A recipe run is one pass of one change through the stages: it creates the change's workspace (start), runs the recipe's
steps (implement), runs every lane the recipe requires, never skipped, on top of the lanes the change selects (verify),
and completes the change once that proof is replayed from the base (complete). Delivery follows the repository's own
lifecycle actions, as for any work.

Agentic coding is the first station and the main example on these pages: an interactive coding agent, an unattended one,
a tracker updated by lifecycle actions, and the `implement-task` recipe, which a loop runner runs for each task with
`the-cook make implement-task --task <id> --unattended`. Whatever the station, `the-cook` never calls a model, never
writes knowledge such as prose, memory or domain skills, and never writes the team's record of work.

The name comes from the kitchen: a cook follows the recipe exactly and lets nothing leave the kitchen until it passes
the check. The workers do the cooking, and `the-cook` holds them to that standard.

## The game master

Think of a tabletop game. The game master rules the world but never plays. In `the-cook`:

| In the game | In `the-cook` |
| --- | --- |
| The game master | `the-cook`: it sets the rules and enforces them, and never takes a turn |
| The players | developers, who decide what gets built |
| The characters | workers, such as coding agents, each reached through its ingredient, who act inside the rules |
| The order of play | the lifecycle: the stages every change goes through and the guards between them |
| The world | repositories, with their files, history and conventions |
| The rulebook | the three repository files `.cook/config.yaml`, `.cook/policy.yaml` and `.cook/hooks.yaml` |

The game master keeps the rules consistent across every table it runs: the same stages, the same guards, the same
commands and the same documentation blocks in every repository that adopts it.

## What it does

| Module | What you get | Page |
| --- | --- | --- |
| Core (always on) | the lifecycle: its events, guards and declared actions; recipe runs through `the-cook make`; task workspaces with task binding; unattended attempts through `the-cook attempt`; the `cook` wrapper pinned to one verified release; the `.cook/` configuration with the profile, its pinned ingredients and applied stations, and the repository's recipes; policy rendered for each worker; conventions written as managed blocks in `AGENTS.md`, `README.md` and the files the ingredients in use name; the manifest; the shipped skill | [lifecycle](lifecycle.md), [recipes](recipes.md), [workspaces](workspaces.md), [tasks](tasks.md), [attempts](attempts.md), [wrapper](wrapper.md), [concepts](concepts.md), [ingredients](ingredients.md), [stations](stations.md), [policy](policy.md), [render](render.md), [skills](skills.md) |
| `hooks` | Worker-neutral hooks: lint-on-edit, validators, session context, notifications when a worker waits for a person or ends a turn, and [standing context](context.md): directives and skills injected when a declared trigger fires | [hooks](hooks.md), [context](context.md) |
| `verify` | Named verification lanes (families of test commands, such as `unit` and `e2e`) and a receipt bound to file contents: the proof the verify stage writes and the complete and deliver guards check | [verify](verify.md) |
| `memory` | A memory skeleton under `.agents/memory/` and its index in the `memory` block of `AGENTS.md` | [memory](memory.md) |

A loop runner connects by naming `./cook make <recipe> --task <id> --unattended` as the command it runs for each task,
or `./cook attempt <worker>` when a script needs only the attempt, and a task tracker by lifecycle actions on `start`,
`completed` and `blocked`; a [station](stations.md) such as `agentic-coding` writes both and adds the recipe the loop
runner runs. Every module is opt-in and removable on its own, and the whole tool can be undone or ejected, keeping its
conventions ([remove](remove.md)).

## Five-minute quickstart

In a repository that already uses `the-cook` there is nothing to install. Its committed wrapper, `cook`, downloads
the release the repository pins, verifies its checksum and runs it ([wrapper](wrapper.md)):

```sh
cd ~/src/acme-api
./cook doctor
./cook recipe list          # the kinds of change this repository runs through the lifecycle
```

Every command below runs as `./cook <command>`; with a global install, `the-cook <command>` is equivalent.

A repository that has no `cook` yet needs a `the-cook` binary once, to scaffold it
([installation](installation.md#scaffolding-a-new-repository)). The scaffold pins the release that runs it, and
`--cookbook` names the cookbook tag whose stations it applies: each one named with `--station`, and every one whose
detect rule matches the repository, such as a stack station for its language. Applying the `agentic-coding` station
pins its ingredients, writes its entries and templates, and copies its `implement-task` recipe into
`.cook/recipes/implement-task/`, all in one diff:

```sh
cd ~/src/acme-api
the-cook scaffold --cookbook github:acme/cookbook@v1.2.0 --station agentic-coding --dry-run   # what it would write
the-cook scaffold --cookbook github:acme/cookbook@v1.2.0 --station agentic-coding             # writes once confirmed
git diff --stat
```

Or adopt one that already has hand-written coding-agent permissions, hooks or instruction files:

```sh
# --dry-run also prints the equivalence report
the-cook scaffold --adopt --cookbook github:acme/cookbook@v1.2.0 --station agentic-coding --dry-run
the-cook scaffold --adopt --cookbook github:acme/cookbook@v1.2.0 --station agentic-coding
./cook doctor                    # lists what is left for a person or a coding agent to finish
```

Without `--cookbook`, the scaffold writes the skeleton, and its to-do lists each contract no pinned ingredient fills.
Apply stations later with `./cook station apply <name>... --cookbook <source>@<version>` ([stations](stations.md)),
add one recipe with `./cook recipe add <name>... --cookbook <source>@<version>` ([recipes](recipes.md)), or pin one
ingredient with `./cook ingredient pin <name>... --cookbook <source>@<version>` ([ingredients](ingredients.md)).

Review and edit the profile and the policy, then render and commit. A recipe run starts from the shared default line
of work, so deliver the commit before the first run:

```sh
$EDITOR .cook/config.yaml .cook/policy.yaml
./cook render
./cook render --check       # exit 0: rendered files match their sources
git add -A && git commit -m "chore: adopt the-cook"
# deliver it like any change; once it is merged, have this clone learn it (for git: git pull)
```

Take a change through the lifecycle with a recipe. `implement-task` creates a workspace for the task, starts the
unattended coding agent on it, verifies the result and completes the task once the proof is replayed from the base,
printing each step as it runs. A loop runner runs the same command for each task it takes, with `--unattended`, because
nobody drives its runs:

```sh
# the task's text on stdin is the worker's prompt, as a loop runner passes it
echo "Redirect to the page the user asked for after login." | ./cook make implement-task --task acme-142
```

A kind of change the repository makes again and again becomes a recipe of its own, such as adding a database migration.
Write it, get it onto the shared default line of work, which a run's workspace starts from, and run it with its
inputs:

```sh
./cook recipe create migration                 # a skeleton at .cook/recipes/migration/recipe.yaml
$EDITOR .cook/recipes/migration/recipe.yaml
git add .cook/recipes && git commit -m "chore: add the migration recipe"
# deliver it like any change; once it is merged, have this clone learn it (for git: git pull)
./cook make migration --name add-invoice-due-date
./cook make --continue migration-add-invoice-due-date   # after doing what its handoff step asked
```

At a handoff step an attended run pauses for whoever drives it, and a run started with `--unattended` stops as blocked;
on a step that fails a run stops, fires `blocked` and keeps the workspace for a person to fix and continue. Delivery
then follows the repository's own lifecycle actions, as for any work ([the run](recipes.md#the-run)).

The same stages can be walked by hand: start a change in a task workspace, implement it, verify it and deliver it:

```sh
./cook workspace create fix-login        # start: a workspace from committed code; fires start
cd .work/workspaces/fix-login
# ... implement ...
./cook verify --lane unit=login --skip e2e="no route or controller changed"   # verify: writes the receipt
./cook verify --check
git commit -am "fix: login redirect"
./cook lifecycle list                    # what deliver does in this repository
./cook lifecycle run deliver             # deliver: push and open a pull request, if the repository declares it
```

Move the repository to a newer release in a reviewable commit:

```sh
./cook wrapper --latest
./cook render
git add -A && git commit -m "chore: upgrade the-cook"
```

Teammates install nothing: the committed rendered files give their workers the same permissions and instructions, and
their first `./cook` command downloads the pinned release. Until then, worker hooks print one line and continue.

## Pages

Start here

- [Development lifecycle](lifecycle.md): the abstraction every other page serves: the stages from plan to landed, who
  acts at each, the events, guards and proof between them, and how each stage reaches its programs

Getting started

- [Installation](installation.md): the wrapper, scaffolding a new repository, global install, upgrades, uninstall
- [Wrapper](wrapper.md): `cook`, the pinned release, its checksums, upgrades, CI and air-gapped machines
- [Concepts](concepts.md): the lifecycle and the programs around it, ingredients, stations and recipes, layers, the
  `.cook/` and `.agents/` directories, ownership, conventions, adoption classes, optional and transparent behaviour
- [Safety guidelines](safety.md): the threat model, layers of defence, placing policy entries
- [Pitfalls, gaps and traps](pitfalls.md): what goes wrong and what to do about it
- [Glossary](glossary.md)
- [FAQ](faq.md)

Setting up a repository

- [scaffold](scaffold.md): greenfield scaffold, `--adopt` and `--import`
- [remove](remove.md): undo the adoption, eject and keep the conventions, or remove one module
- [render](render.md): rendered files, managed blocks and their ids, merge-rendering, drift checks
- [doctor](doctor.md): health checks and the adoption to-do
- [explain](explain.md): built-in documentation for people and workers

Configuration

- [Profile](profile.md): `.cook/config.yaml`, state paths
- [Policy](policy.md): `.cook/policy.yaml`, the floor rules of ingredients, rendering per worker
- [Hooks](hooks.md): `.cook/hooks.yaml`, the hook runtime, notification hooks and lifecycle actions
- [Context](context.md): standing context, directives and skills injected by declared triggers
- [Ingredients](ingredients.md): how each program is described, its renders, floor and checks, the version-control
  and worker contracts, pins, trust, fetching and the cache, `the-cook ingredient pin`, `list` and `show`, and
  `the-cook fetch`
- [Stations](stations.md): what one way of working needs in place, built from ingredients: their stages, entries,
  templates and the recipes they add, applying once and again, the merge rules, `the-cook station apply`, `list` and
  `show`, cookbooks and the archives they publish by kind, and where an organization's choices live

Daily work

- [Recipes](recipes.md): the kinds of change a repository makes many times, each run as one pass through the stages
  with `the-cook make`: the recipe file, inputs and step kinds, the run, its record, stopping and continuing, the
  guards, and `the-cook recipe add`, `update`, `create`, `list` and `show`
- [Workspaces](workspaces.md): `the-cook workspace`
- [Verify](verify.md): lanes and the receipt
- [Tasks](tasks.md): task binding with `workspace create --task`, the `completed` guard, trackers wired by actions
- [Attempts](attempts.md): unattended runs through `the-cook attempt`, the primitive behind a recipe's `attempt` steps,
  the attempt contract, and loop runs

Knowledge

- [Skills](skills.md): the shipped skill, `.agents/skills/` and skills referenced from standing context
- [Memory](memory.md): the memory skeleton and its index

Many repositories and many workers

- [Fleet](fleet.md): run a command across a list of repositories, and deliver the changes
- [Workers](workers.md): worker declarations, their ingredients, roles, and the worker contract
- [Compatibility](compatibility.md): how hosting platforms, loop runners, task trackers, task runners, CI and existing
  files fit with `the-cook`; what removal keeps

Reference

- [CLI reference](cli.md): every command, flag, exit code and environment variable on one page
