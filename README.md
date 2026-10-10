# the-cook

<p align="center"><img src="docs/assets/the-cook.png" alt="the-cook: pixel-art portrait of a man in a black pork-pie hat lowering dark sunglasses" width="640"></p>

## Objective

**Problem.** Teams work with programs that change their repositories for them: coding agents above all today, and bots,
codemods, trackers and task runners beside them. How a team works with those programs is kept by hand in each
repository: what each program may do, which checks a change must pass, when work counts as done and how it leaves the
clone. A practice learned in one repository reaches the others only by copying, the copies drift into dialects that lose
their safety settings, and nobody can tell from a repository what its programs may do or why.

**Goal.** A way of working learned once reaches every repository through one reviewed change in each and is withdrawn
the same way; every change, whoever or whatever makes it, passes the same declared stages and guards; and people who do
not use the tool never notice it.

## What it is

`the-cook` is a single command-line binary that enforces the software development lifecycle a repository declares. Every
change goes through the same stages: plan, start, implement, verify, complete, deliver, review and merge, and landed.
Between them the binary fires events, at which the actions the repository declares run; it holds the guards between
stages, so delivery never runs for uncommitted work, work on the default branch or, with verification on, unverified
work; and it binds the proof a change carries, a verification receipt, to the change's base and exact content. An
unattended attempt is reported done only after that proof is replayed from the base
([development lifecycle](docs/features/lifecycle.md), [ADR 0015](docs/adrs/0015-development-lifecycle.md)).

Everything it does in a repository is written in that repository's own files and pinned to one verified release. It
does no stage's work itself and never calls a model: like a game master, it sets the rules of the world and leaves the
turns to the players, who are the developers, and their characters, the workers.

The name comes from the kitchen: a cook follows the recipe exactly and lets nothing leave the kitchen until it passes
the check. The workers do the cooking, and `the-cook` holds them to that standard.

The lifecycle knows no program. The programs that do the work at its stages, version control, workers such as coding
agents or bots, trackers and task runners, are described by ingredients and combined by stations, both published in
cookbooks ([ADR 0022](docs/adrs/0022-tool-integration.md)). An [ingredient](docs/features/ingredients.md) describes one
program: the files rendered for it on every render, the operations that fill one of the lifecycle's contracts, and its
floor, the rules about that program that no repository policy can remove, such as the arguments that bypass a coding
agent's permission checks ([ADR 0011](docs/adrs/0011-safety-invariants.md)). A [station](docs/features/stations.md) is
one way of running the lifecycle: the ingredients it uses, which of them fills version control and which are its
workers, and the entries and templates it writes into the repository once, which are the repository's own from then on.
A repository pins every ingredient and station it uses by version and sha256 in a reviewed change; `the-cook` ships none
and names no default cookbook ([ADR 0024](docs/adrs/0024-ingredients-and-stations.md)).

Agentic coding is the first station and the main example. It declares an interactive coding agent and an unattended one,
the lanes they verify with, what unattended runs must never do, and lifecycle actions that keep a tracker up to date.
`the-cook` is not a launcher for any loop runner: a loop runner names `./cook attempt <worker>` as the command it runs
for each task, and that command runs the unattended coding agent under rules it cannot bypass and reports the task done
only after the replay passes. `./cook station apply agentic-coding --cookbook github:acme/cookbook@v1.2.0` writes that
wiring into the repository's files on request.

For a cookbook to read or start from, see [lmarqs/the-cookbook](https://github.com/lmarqs/the-cookbook), the author's
personal cookbook, starting with an `agentic-coding` station and the ingredients it uses.

## Read next

| To learn | Read |
| --- | --- |
| Why the project exists, what it is trying to achieve, and the tenets behind every decision | [Philosophy](docs/philosophy.md) |
| What each feature does and how to use it, starting with a five-minute quickstart | [Features](docs/features/README.md) |
| The stages every change goes through, the events and guards between them, and how each stage reaches its programs | [Development lifecycle](docs/features/lifecycle.md) |
| What an ingredient and a station hold, how a repository pins them from a cookbook, and how to write them | [Ingredients](docs/features/ingredients.md), [stations](docs/features/stations.md) |
| The decisions that shape the tool, and their reasons; the authority over everything else | [Architecture decisions](docs/adrs/README.md) |
| The rules for everything a user sees: commands, files, messages, generated content | [User-experience standards](docs/ux/README.md) |

## Install

In a repository that already uses `the-cook`, there is nothing to install: run `./cook doctor`. To set up a new
repository, see [installation](docs/features/installation.md).
