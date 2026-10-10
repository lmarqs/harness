# the-cook

<p align="center"><img src="docs/assets/the-cook.png" alt="the-cook: pixel-art portrait of a man in a black pork-pie hat lowering dark sunglasses" width="640"></p>

## Objective

**Problem.** Teams that work with coding agents keep each repository's agent setup by hand: permissions, hooks,
instructions, skills and the loops that run agents unattended. A practice learned in one repository reaches the others
only by copying, the copies drift into dialects that lose their safety settings, and nobody can tell from a repository
what its agents may do or why.

**Goal.** A practice learned once reaches every repository through one reviewed change in each and is withdrawn the
same way; agents everywhere run inside the same explicit, verified rules; and people who do not use the tool never
notice it.

## What it is

`the-cook` is a single command-line binary that bridges a repository's declared agent setup to the programs that carry
it out: it writes, checks and removes the configuration agent harnesses such as Claude Code and Codex CLI read.
Everything it does in a repository is written in that repository's own files and pinned to one verified release. It
never calls a model, and it never plays: like a game master, it sets the rules of the world and leaves the turns to the
players, who are the developers, and their characters, the agents.

The name comes from the kitchen: a cook follows the recipe exactly and lets nothing leave the kitchen until it passes
the check. The agents do the cooking, and `the-cook` holds them to that standard.

The binary contains code for no program. Each program it starts, calls or writes files for, git and the agent harnesses
included, is described by a [recipe](docs/features/recipes.md): a versioned bundle of data with entries and templates
applied once, renders written on every render, operations that answer the binary's questions, and checks. The release
ships recipes for git, Claude Code, Codex CLI, ralph-tui, Beads, mise, the skills CLI and the Go, Node, Terraform and
Python stacks, and a repository may declare others pinned by hash ([ADR 0022](docs/adrs/0022-tool-integration.md)).
Safety facts about a program, such as the arguments that bypass a harness's permission checks, are floor rules of its
recipe that no repository policy can remove ([ADR 0011](docs/adrs/0011-safety-invariants.md)).

It is not a launcher for any loop runner. A loop runner such as ralph-tui names `./cook worker <agent>` as its
agent command, which runs the agent for each task under rules the agent cannot bypass, and a tracker such as Beads is
updated by lifecycle actions the repository declares. `./cook recipe apply <name>` writes that wiring into the
repository's files on request.

## Read next

| To learn | Read |
| --- | --- |
| Why the project exists, what it is trying to achieve, and the tenets behind every decision | [Philosophy](docs/philosophy.md) |
| What each feature does and how to use it, starting with a five-minute quickstart | [Features](docs/features/README.md) |
| Which programs the release ships recipes for, and how a recipe is written | [Recipes](docs/features/recipes.md) |
| The decisions that shape the tool, and their reasons; the authority over everything else | [Architecture decisions](docs/adrs/README.md) |
| The rules for everything a user sees: commands, files, messages, generated content | [User-experience standards](docs/ux/README.md) |

## Install

In a repository that already uses `the-cook`, there is nothing to install: run `./cook doctor`. To set up a new
repository, see [installation](docs/features/installation.md).
