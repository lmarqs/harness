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
| The rulebook | the three repository files `.harness/config.yaml`, `.harness/policy.yaml` and `.harness/hooks.yaml` |

The game master keeps the rules consistent across every table it runs: the same commands, the same guardrails and the
same documentation blocks in every repository that adopts it.

## What it does

| Module | What you get | Page |
| --- | --- | --- |
| Core (always on) | the `harnessw` wrapper pinned to one verified release, the `.harness/` configuration, profile, policy rendered for each agent, conventions written as managed blocks in `AGENTS.md`, `README.md` and `.gitignore`, manifest, the harness skill, lifecycle events and actions | [wrapper](wrapper.md), [concepts](concepts.md), [policy](policy.md), [render](render.md), [lifecycle](lifecycle.md) |
| `hooks` | Agent-neutral hooks: lint-on-edit, validators, session context, notifications when an agent waits for a person or ends a turn, and [standing context](context.md): directives and skills injected when a declared trigger fires | [hooks](hooks.md), [context](context.md) |
| `verify` | Named verification lanes and a receipt bound to file contents | [verify](verify.md) |
| `tasks` | Clone-local task tracking with Beads or a PRD JSON file | [tasks](tasks.md) |
| `ralph` | Unattended multi-task runs through ralph-tui with a safe worker contract | [ralph](ralph.md) |
| `memory` | A memory skeleton under `.agents/memory/` and its index in the `memory` block of `AGENTS.md` | [memory](memory.md) |
| `skills` | Declared skill sources installed through the `skills` CLI | [skills](skills.md) |

Task worktrees ([worktrees](worktrees.md)) are available in every adopted repository. Every module is opt-in and
removable on its own, and the whole tool can be undone or ejected, keeping its conventions ([remove](remove.md)).

## Five-minute quickstart

In a repository that already uses `harness` there is nothing to install. Its committed wrapper, `harnessw`, downloads
the release the repository pins, verifies its checksum and runs it ([wrapper](wrapper.md)):

```sh
cd ~/src/acme-api
./harnessw doctor
```

Every command below runs as `./harnessw <command>`; with a global install, `harness <command>` is equivalent.

A repository that has no `harnessw` yet needs a `harness` binary once, to scaffold it
([installation](installation.md#scaffolding-a-new-repository)). The scaffold pins the release that runs it:

```sh
mise use -g github:lmarqs/harness@<tag>     # or one-off: mise exec github:lmarqs/harness@<tag> -- harness scaffold
cd ~/src/acme-api
harness scaffold --dry-run      # shows every file it would write, harnessw and .harness/ included
harness scaffold                # writes them after confirmation
git diff --stat
```

Or adopt one that already has hand-written `.claude/settings.json`, Codex rules, hooks or `CLAUDE.md`:

```sh
harness scaffold --adopt --dry-run   # the generated files and the equivalence report
harness scaffold --adopt
./harnessw doctor                    # lists what an agent must finish
```

Review and edit the profile and the policy, then render and commit; a worktree starts from committed code:

```sh
$EDITOR .harness/config.yaml .harness/policy.yaml
./harnessw render
./harnessw render --check       # exit 0: rendered files match their sources
git add -A && git commit -m "chore: adopt harness"
```

Work on a change with a task worktree and a verification receipt:

```sh
./harnessw worktree create fix-login
cd .work/worktrees/fix-login
# ... edit ...
./harnessw verify --lane unit=login --skip e2e="no route or controller changed"
./harnessw verify --check
git commit -am "fix: login redirect"
./harnessw lifecycle list            # what deliver does in this repository
./harnessw lifecycle run deliver     # push and open a pull request, if the repository declares it
```

Move the repository to a newer release in a reviewable commit:

```sh
./harnessw wrapper --latest
./harnessw render
git add -A && git commit -m "chore: upgrade harness"
```

Teammates install nothing: the committed rendered files give their agents the same permissions and instructions, and
their first `./harnessw` command downloads the pinned release. Until then, agent hooks print one line and continue.

## Pages

Getting started

- [Installation](installation.md): the wrapper, scaffolding a new repository, global install, upgrades, uninstall
- [Wrapper](wrapper.md): `harnessw`, the pinned release, its checksums, upgrades, CI and air-gapped machines
- [Concepts](concepts.md): layers, the `.harness/` and `.agents/` directories, ownership, conventions, adoption
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

- [Profile](profile.md): `.harness/config.yaml`, state paths
- [Policy](policy.md): `.harness/policy.yaml`, the built-in base, rendering per agent
- [Hooks](hooks.md): `.harness/hooks.yaml`, the hook runtime, notification hooks and lifecycle actions
- [Context](context.md): standing context, directives and skills injected by declared triggers
- [Presets](presets.md): the built-in stack presets, `harness preset list`, `show` and `apply`, and where an
  organization's choices live

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
- [Compatibility](compatibility.md): coexistence with GitHub, Beads, ralph-tui, mise, CI and existing files; what
  removal keeps

Reference

- [CLI reference](cli.md): every command, flag, exit code and environment variable on one page
