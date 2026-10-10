# harness

## Objective

**Problem.** Teams that work with coding agents keep each repository's agent setup by hand: permissions, hooks,
instructions, skills and the loops that run agents unattended. A practice learned in one repository reaches the others
only by copying, the copies drift into dialects that lose their safety settings, and nobody can tell from a repository
what its agents may do or why.

**Goal.** A practice learned once reaches every repository through one reviewed change in each and is withdrawn the
same way; agents everywhere run inside the same explicit, verified rules; and people who do not use the tool never
notice it.

## What it is

`harness` is a single command-line binary that writes, checks and removes the configuration agent harnesses read
(Claude Code, Codex CLI, and ralph-tui, which runs agents unattended through a list of tasks). Everything it does in a
repository is written in that repository's own files and pinned to one verified release. It never calls a model, and it
never plays: like a game master, it sets the rules of the world and leaves the turns to the players, who are the
developers, and their characters, the agents.

## Read next

| To learn | Read |
| --- | --- |
| Why the project exists, what it is trying to achieve, and the tenets behind every decision | [Philosophy](docs/philosophy.md) |
| What each feature does and how to use it, starting with a five-minute quickstart | [Features](docs/features/README.md) |
| The decisions that shape the tool, and their reasons; the authority over everything else | [Architecture decisions](docs/adrs/README.md) |
| The rules for everything a user sees: commands, files, messages, generated content | [User-experience standards](docs/ux/README.md) |

## Install

In a repository that already uses `harness`, there is nothing to install: run `./harnessw doctor`. To set up a new
repository, see [installation](docs/features/installation.md).
