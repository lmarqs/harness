# the-cook

## Objective

**Problem.** A developer meeting the tool for the first time, in a repository they cloned or as a maintainer weighing it
for their own repositories, needs to know what it does, what it leaves alone and what it asks of them and their
teammates. Without one starting point that answers this and points onward, they piece it together from pages written for
readers who already know the tool, or judge it by what its name suggests.

**Goal.** After one read, a newcomer can say what the tool is for and what it never does, can try it on a repository,
and can find the page for any part of it.

## Overview

`the-cook` is a command-line tool that sets up, constrains and checks the environment coding agents work in. It writes
the permission rules, hooks and instructions that agents read. It gives people and agents the same commands for
task [workspaces](workspaces.md), verification receipts (records that a change's checks passed on its exact content,
[verify](verify.md)) and [task binding](tasks.md), which ties a workspace to a task id from any tracker. Its
[`the-cook worker`](worker.md) command runs one agent for one task, for any loop runner that names it as its agent
command, and that agent cannot bypass permissions or be reported done without a verification that passed. It fires
[development lifecycle](lifecycle.md) events at fixed points, so what a repository declares (a push, a pull request, a
tracker update, a cleanup) happens at the same moment for people and agents. It never calls a model, never writes
knowledge such as prose, memory or domain skills, and contains code and data for no external program. Every program it
works with, the version control system and the agent harnesses included, is described by a [recipe](recipes.md): a
versioned bundle of data the binary interprets. Recipes come from cookbooks, published collections of recipes, and a
repository declares each one it uses, pinned by cookbook, version and sha256; the binary ships none and names no default
cookbook ([ADR 0022](../adrs/0022-tool-integration.md)).

The name comes from the kitchen: a cook follows the recipe exactly and lets nothing leave the kitchen until it passes
the check. The agents do the cooking, and `the-cook` holds them to that standard.

## The game master

Think of a tabletop game. The game master rules the world but never plays. In `the-cook`:

| In the game | In `the-cook` |
| --- | --- |
| The game master | `the-cook`: it sets the rules and enforces them, and never takes a turn |
| The players | developers, who decide what gets built |
| The characters | agents, each run through its harness, who act inside the rules |
| The world | repositories, with their files, history and conventions |
| The rulebook | the three repository files `.cook/config.yaml`, `.cook/policy.yaml` and `.cook/hooks.yaml` |

The game master keeps the rules consistent across every table it runs: the same commands, the same guardrails and the
same documentation blocks in every repository that adopts it.

## What it does

| Module | What you get | Page |
| --- | --- | --- |
| Core (always on) | the `cook` wrapper pinned to one verified release, the `.cook/` configuration, profile, the pinned recipes, policy rendered for each agent, conventions written as managed blocks in `AGENTS.md`, `README.md` and the files the recipes in use name, manifest, the shipped skill, lifecycle events and actions, task workspaces with task binding, unattended runs through `the-cook worker` | [wrapper](wrapper.md), [concepts](concepts.md), [policy](policy.md), [render](render.md), [lifecycle](lifecycle.md), [workspaces](workspaces.md), [tasks](tasks.md), [worker](worker.md), [skills](skills.md) |
| `hooks` | Agent-neutral hooks: lint-on-edit, validators, session context, notifications when an agent waits for a person or ends a turn, and [standing context](context.md): directives and skills injected when a declared trigger fires | [hooks](hooks.md), [context](context.md) |
| `verify` | Named verification lanes (families of test commands, such as `unit` and `e2e`) and a receipt bound to file contents | [verify](verify.md) |
| `memory` | A memory skeleton under `.agents/memory/` and its index in the `memory` block of `AGENTS.md` | [memory](memory.md) |

A loop runner connects by naming `./cook worker <agent>` as its agent command, and a task tracker by lifecycle actions
on `start`, `completed` and `blocked`; a [recipe](recipes.md) for each program can write both. Every module is opt-in
and removable on its own, and the whole tool can be undone or ejected, keeping its conventions ([remove](remove.md)).

## Five-minute quickstart

In a repository that already uses `the-cook` there is nothing to install. Its committed wrapper, `cook`, downloads
the release the repository pins, verifies its checksum and runs it ([wrapper](wrapper.md)):

```sh
cd ~/src/acme-api
./cook doctor
```

Every command below runs as `./cook <command>`; with a global install, `the-cook <command>` is equivalent.

A repository that has no `cook` yet needs a `the-cook` binary once, to scaffold it
([installation](installation.md#scaffolding-a-new-repository)). The scaffold pins the release that runs it, and
`--cookbook` names the cookbook tag whose matching recipes it pins and applies:

```sh
cd ~/src/acme-api
the-cook scaffold --cookbook github:acme/cookbook@v1.2.0 --dry-run   # every file it would write, cook and .cook/ included
the-cook scaffold --cookbook github:acme/cookbook@v1.2.0             # writes them after confirmation
git diff --stat
```

Or adopt one that already has hand-written agent permissions, hooks or instruction files:

```sh
the-cook scaffold --adopt --cookbook github:acme/cookbook@v1.2.0 --dry-run   # files and the equivalence report
the-cook scaffold --adopt --cookbook github:acme/cookbook@v1.2.0
./cook doctor                    # lists what an agent must finish
```

Without `--cookbook`, the scaffold writes an empty `recipes:` and its to-do lists each contract no recipe fills; pin
recipes later with `./cook recipe pin <name>... --cookbook <source>@<version>` ([pinning](recipes.md#pinning)).

Review and edit the profile and the policy, then render and commit; a workspace starts from committed code:

```sh
$EDITOR .cook/config.yaml .cook/policy.yaml
./cook render
./cook render --check       # exit 0: rendered files match their sources
git add -A && git commit -m "chore: adopt the-cook"
```

Work on a change with a task workspace and a verification receipt:

```sh
./cook workspace create fix-login
cd .work/workspaces/fix-login
# ... edit ...
./cook verify --lane unit=login --skip e2e="no route or controller changed"
./cook verify --check
git commit -am "fix: login redirect"
./cook lifecycle list            # what deliver does in this repository
./cook lifecycle run deliver     # push and open a pull request, if the repository declares it
```

Move the repository to a newer release in a reviewable commit:

```sh
./cook wrapper --latest
./cook render
git add -A && git commit -m "chore: upgrade the-cook"
```

Teammates install nothing: the committed rendered files give their agents the same permissions and instructions, and
their first `./cook` command downloads the pinned release. Until then, agent hooks print one line and continue.

## Pages

Getting started

- [Installation](installation.md): the wrapper, scaffolding a new repository, global install, upgrades, uninstall
- [Wrapper](wrapper.md): `cook`, the pinned release, its checksums, upgrades, CI and air-gapped machines
- [Concepts](concepts.md): layers, the `.cook/` and `.agents/` directories, ownership, conventions, adoption
  classes, optional and transparent behaviour
- [Development lifecycle](lifecycle.md): from plan to landed, events, actions and guards
- [Safety guidelines](safety.md): the threat model, layers of defence, placing policy entries
- [Pitfalls, gaps and traps](pitfalls.md): what goes wrong and what to do about it
- [Glossary](glossary.md)
- [FAQ](faq.md)

Setting up a repository

- [scaffold](scaffold.md): greenfield scaffold, `--adopt` and `--import`
- [remove](remove.md): undo the adoption, eject and keep the conventions, or remove one module
- [render](render.md): rendered files, managed blocks and their ids, merge-rendering, drift checks
- [doctor](doctor.md): health checks and the adoption to-do
- [explain](explain.md): built-in documentation for people and agents

Configuration

- [Profile](profile.md): `.cook/config.yaml`, state paths
- [Policy](policy.md): `.cook/policy.yaml`, the floor rules of recipes, rendering per agent
- [Hooks](hooks.md): `.cook/hooks.yaml`, the hook runtime, notification hooks and lifecycle actions
- [Context](context.md): standing context, directives and skills injected by declared triggers
- [Recipes](recipes.md): how every program is described, the version-control and harness contracts, floor rules,
  cookbooks and pins, `the-cook recipe pin`, `list`, `show`, `apply` and `fetch`, and where an organization's choices
  live

Daily work

- [Workspaces](workspaces.md): `the-cook workspace`
- [Verify](verify.md): lanes and the receipt
- [Tasks](tasks.md): task binding with `workspace create --task`, the `completed` guard, trackers wired by actions
- [worker](worker.md): unattended runs with any loop runner through `the-cook worker`

Knowledge

- [Skills](skills.md): the shipped skill, `.agents/skills/` and skills referenced from standing context
- [Memory](memory.md): the memory skeleton and its index

Many repositories and many agents

- [Fleet](fleet.md): run a command across a list of repositories, and deliver the changes
- [Agents](agents.md): agent declarations, their harness recipes, roles, and the harness contract
- [Compatibility](compatibility.md): how hosting platforms, loop runners, task trackers, task runners, CI and existing
  files fit with `the-cook`; what removal keeps

Reference

- [CLI reference](cli.md): every command, flag, exit code and environment variable on one page
