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

`the-cook` is a single command-line binary that enforces the software development lifecycle a repository declares.

Every change goes through the same stages: plan, start, implement, verify, complete, deliver, review and merge, and
landed.

Between stages the binary fires events, at which the actions the repository declares run.

It holds the guards between stages, so delivery never runs for uncommitted work, work on the default branch or, with
verification on, unverified work.

It binds the proof a change carries, a verification receipt, to the change's base and exact content.

An unattended attempt is reported done only after that proof is replayed from the base
([development lifecycle](docs/features/lifecycle.md), [ADR 0015](docs/adrs/0015-development-lifecycle.md)).

Everything it does in a repository is written in that repository's own files and pinned to one verified release. It
does no stage's work itself and never calls a model: like a game master, it sets the rules of the world and leaves the
turns to the players, who are the developers, and their characters, the workers.

The name comes from the kitchen: a cook follows the recipe exactly and lets nothing leave the kitchen until it passes
the check. The workers do the cooking, and `the-cook` holds them to that standard.

The lifecycle knows no program. What a repository adds to it comes in three units, which cookbooks publish, and one rule
separates them: facts about programs are pinned and rendered; practices are copied and become the repository's own
([ADR 0022](docs/adrs/0022-tool-integration.md), [ADR 0024](docs/adrs/0024-ingredients-and-stations.md),
[ADR 0025](docs/adrs/0025-recipes.md)).

- An [ingredient](docs/features/ingredients.md) holds the facts about one program, such as the version control system or
  a coding agent: the contract it fills, the files rendered for it and its floor, the rules about that program that no
  repository policy can remove, such as the arguments that bypass a coding agent's permission checks
  ([ADR 0011](docs/adrs/0011-safety-invariants.md)). A repository pins it by cookbook, version and sha256. It acts on
  every render and every launch.
- A [station](docs/features/stations.md) holds what one way of working needs in place: the ingredients to pin, the
  workers and their roles, entries for the profile, the policy and the hooks file, templates, and the recipes to add. A
  repository applies it once, in one reviewed change. Everything it wrote is the repository's own from then on.
- A [recipe](docs/features/recipes.md) is one kind of change, made many times: its inputs, its requirements, its steps
  and the lanes it requires. A repository writes its own or copies one from a cookbook, with its origin recorded, and
  edits it like any other file. Each run is one pass of one change through the stages: a workspace at start, the steps
  at implement, the lanes the change selects and those the recipe requires at verify, then complete.

`the-cook` ships none of them and names no default cookbook: a repository takes what it uses from cookbooks, each in a
reviewed change, and writes recipes of its own.

Agentic coding is the first station and the main example. The `agentic-coding` station pins ingredients for git, two
coding agents, a loop runner and a tracker, declares an interactive worker and an unattended one, and adds the
`implement-task` recipe. `./cook station apply agentic-coding --cookbook github:acme/cookbook@v1.2.0` writes all of it
into the repository's files on request. `the-cook` is not a launcher for any loop runner: a loop runner names
`./cook make implement-task --task <id> --unattended` as the command it runs for each task, and that run starts the
unattended coding agent under rules it cannot bypass and reports the task done only after the proof is replayed from the
base. The same repository keeps recipes of its own beside it, such as one that adds a database migration.

For a cookbook to read or start from, see [lmarqs/the-cookbook](https://github.com/lmarqs/the-cookbook), the author's
personal cookbook, starting with the `agentic-coding` station, the ingredients it pins and the `implement-task` recipe
it adds.

## Read next

| To learn | Read |
| --- | --- |
| Why the project exists, what it is trying to achieve, and the tenets behind every decision | [Philosophy](docs/philosophy.md) |
| What each feature does and how to use it, starting with a five-minute quickstart | [Features](docs/features/README.md) |
| The stages every change goes through, the events and guards between them, and how each stage reaches its programs | [Development lifecycle](docs/features/lifecycle.md) |
| What an ingredient, a station and a recipe hold, how a repository takes each from a cookbook, and how to write them | [Ingredients](docs/features/ingredients.md), [stations](docs/features/stations.md), [recipes](docs/features/recipes.md) |
| The decisions that shape the tool, and their reasons; the authority over everything else | [Architecture decisions](docs/adrs/README.md) |
| The rules for everything a user sees: commands, files, messages, generated content | [User-experience standards](docs/ux/README.md) |

## Install

In a repository that already uses `the-cook`, there is nothing to install: run `./cook doctor`. To set up a new
repository, see [installation](docs/features/installation.md).
