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
it out: it writes, checks and removes the configuration that agent harnesses read.
Everything it does in a repository is written in that repository's own files and pinned to one verified release. It
never calls a model, and it never plays: like a game master, it sets the rules of the world and leaves the turns to the
players, who are the developers, and their characters, the agents.

The name comes from the kitchen: a cook follows the recipe exactly and lets nothing leave the kitchen until it passes
the check. The agents do the cooking, and `the-cook` holds them to that standard.

The binary contains no code and no data for any program. Each program it starts, calls or writes files for, version
control and the agent harnesses included, is described by a [recipe](docs/features/recipes.md): a versioned bundle of
data with entries and templates applied once, renders written on every render, operations that answer the binary's
questions, and checks. Recipes come from cookbooks, published collections of recipes, and a repository pins each one it
uses by version and sha256 in a reviewed change. `the-cook` ships no recipe and names no default cookbook
([ADR 0022](docs/adrs/0022-tool-integration.md)). Safety facts about a program, such as the arguments that bypass a
harness's permission checks, are floor rules of its recipe that no repository policy can remove
([ADR 0011](docs/adrs/0011-safety-invariants.md)).

It is not a launcher for any loop runner. A loop runner names `./cook worker <agent>` as its agent command, which runs
the agent for each task under rules the agent cannot bypass, and a task tracker is updated by lifecycle actions the
repository declares. `./cook recipe apply <name>` writes that wiring into the repository's files on request.

For a cookbook to read or start from, see [lmarqs/the-cookbook](https://github.com/lmarqs/the-cookbook), the author's
personal recipes for git, Claude Code, Codex CLI, ralph-tui, Beads, mise, the skills CLI and a few stacks.

## Read next

| To learn | Read |
| --- | --- |
| Why the project exists, what it is trying to achieve, and the tenets behind every decision | [Philosophy](docs/philosophy.md) |
| What each feature does and how to use it, starting with a five-minute quickstart | [Features](docs/features/README.md) |
| What a recipe holds, how a repository pins one from a cookbook, and how to write one | [Recipes](docs/features/recipes.md) |
| The decisions that shape the tool, and their reasons; the authority over everything else | [Architecture decisions](docs/adrs/README.md) |
| The rules for everything a user sees: commands, files, messages, generated content | [User-experience standards](docs/ux/README.md) |

## Install

In a repository that already uses `the-cook`, there is nothing to install: run `./cook doctor`. To set up a new
repository, see [installation](docs/features/installation.md).
