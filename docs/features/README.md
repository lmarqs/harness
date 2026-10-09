# harness

`harness` is a command-line tool that sets up, constrains and checks the environment coding agents work in. It
writes the permission rules, hooks and instructions that Claude Code and Codex read; it gives people and agents the
same commands for task worktrees, verification receipts and local task tracking; and it launches unattended
[Ralph](ralph.md) runs whose workers cannot bypass permissions or claim work they did not verify. It fires
[development lifecycle](lifecycle.md) events at fixed points, so what a repository declares (a push, a pull request,
a cleanup) happens at the same moment for people and agents. It never calls a model, never writes knowledge such as
prose, memory or domain skills, and contains no logic for any hosting platform.

## The game master

Think of a tabletop game. The game master rules the world but never plays. In `harness`:

| In the game | In harness |
| --- | --- |
| The game master | `harness`: it sets the rules and enforces them, and never takes a turn |
| The players | developers, who decide what gets built |
| The characters | agents (Claude Code, Codex), who act inside the rules |
| The world | repositories, with their files, history and conventions |
| The rulebook | the three repository files `.agents/harness.yaml`, `.agents/policy.yaml` and `.agents/hooks.yaml` |

The game master keeps the rules consistent across every table it runs: the same commands, the same guardrails and the
same documentation blocks in every repository that adopts it.

## What it does

| Module | What you get | Page |
| --- | --- | --- |
| Core (always on) | `.agents/` layout, profile, policy rendered for each agent, managed blocks in `AGENTS.md`/`README.md`, manifest, the harness skill, lifecycle events and actions | [concepts](concepts.md), [policy](policy.md), [render](render.md), [lifecycle](lifecycle.md) |
| `hooks` | Agent-neutral hooks: lint-on-edit, validators, session context | [hooks](hooks.md) |
| `verify` | Named verification lanes and a receipt bound to file contents | [verify](verify.md) |
| `tasks` | Clone-local task tracking with Beads or a PRD JSON file | [tasks](tasks.md) |
| `ralph` | Unattended multi-task runs through ralph-tui with a safe worker contract | [ralph](ralph.md) |
| `memory` | A memory skeleton under `.agents/memory/` and an index block in `AGENTS.md` | [memory](memory.md) |
| `skills` | Declared skill sources installed through the `skills` CLI | [skills](skills.md) |

Task worktrees ([worktrees](worktrees.md)) are available in every adopted repository. Every module is opt-in and
removable on its own ([remove](remove.md)).

## Five-minute quickstart

Install the binary once per machine ([installation](installation.md)):

```sh
mise use -g github:lmarqs/harness@<tag>
harness version
```

Scaffold a repository that has no agent setup yet:

```sh
cd ~/src/acme-api
harness scaffold --dry-run      # shows every file it would write
harness scaffold                # writes them after confirmation
git diff --stat
```

Or adopt one that already has hand-written `.claude/settings.json`, Codex rules, hooks or `CLAUDE.md`:

```sh
harness scaffold --adopt --dry-run   # the generated files and the equivalence report
harness scaffold --adopt
harness doctor                       # lists what an agent must finish
```

Review and edit the profile and the policy, then render and commit; a worktree starts from committed code:

```sh
$EDITOR .agents/harness.yaml .agents/policy.yaml
harness render
harness render --check          # exit 0: rendered files match their sources
git add -A && git commit -m "chore: adopt harness"
```

Work on a change with a task worktree and a verification receipt:

```sh
harness worktree create fix-login
cd .work/worktrees/fix-login
# ... edit ...
harness verify --lane unit=login --skip e2e="no route or controller changed"
harness verify --check
git commit -am "fix: login redirect"
harness lifecycle list               # what deliver does in this repository
harness lifecycle run deliver        # push and open a pull request, if the repository declares it
```

Teammates without `harness` installed get the same rendered permissions and instructions from the committed files;
hooks print one line and continue.

## Pages

Getting started

- [Installation](installation.md): global install, per-repository pin, update notice, uninstall
- [Concepts](concepts.md): layers, ownership, adoption classes, optional and transparent behaviour
- [Development lifecycle](lifecycle.md): from plan to landed, events, actions and guards
- [Safety guidelines](safety.md): the threat model, layers of defence, placing policy entries
- [Pitfalls, gaps and traps](pitfalls.md): what goes wrong and what to do about it
- [Glossary](glossary.md)
- [FAQ](faq.md)

Setting up a repository

- [scaffold](scaffold.md): greenfield scaffold, `--adopt` and `--import`
- [remove](remove.md): remove the tool or one module
- [render](render.md): rendered files, managed blocks, merge-rendering, drift checks, `--refresh`
- [doctor](doctor.md): health checks and the adoption to-do
- [explain](explain.md): built-in documentation for people and agents

Configuration

- [Profile](profile.md): `.agents/harness.yaml`
- [Policy](policy.md): `.agents/policy.yaml`, the built-in base, rendering per agent
- [Hooks](hooks.md): `.agents/hooks.yaml`, the hook runtime and lifecycle actions
- [Presets](presets.md): stack presets, `extends`, pinning and preset lifecycle actions

Daily work

- [Worktrees](worktrees.md): `harness worktree`
- [Verify](verify.md): lanes and the receipt
- [Tasks](tasks.md): Beads, PRD JSON or none
- [Ralph](ralph.md): unattended runs

Knowledge

- [Skills](skills.md): the harness skill and `skills sync`
- [Memory](memory.md): the memory skeleton and its index

Many repositories and many agents

- [Fleet](fleet.md): run a command across a list of repositories, and deliver the changes
- [Agents](agents.md): the Claude Code and Codex adapters, roles, and the adapter contract
- [Compatibility](compatibility.md): coexistence with GitHub, Beads, ralph-tui, mise, CI and existing files

Reference

- [CLI reference](cli.md): every command, flag, exit code and environment variable on one page
