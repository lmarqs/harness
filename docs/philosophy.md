# Philosophy

## Objective

**Problem.** The ADRs, standards and feature pages each say what holds and why, but none of them shows the whole they
serve. A contributor reading one of them, or a coding agent working for one, cannot tell what the project as a whole is
trying to achieve, so a proposal can solve a problem nobody has, or work against a goal the project holds, without
anyone noticing.

**Goal.** In one read, anyone can learn which problems `the-cook` exists to solve, what it is trying to achieve and the
tenets every decision follows from, and can check a new proposal against them.

## The problems

Teams work with programs that change their repositories for them: coding agents above all today, and bots, codemods,
trackers and task runners beside them. Each repository's way of working with those programs is kept by hand: what each
program may do, the checks a change must pass, when work counts as done, how it leaves the clone, and the loops that run
workers unattended. Across many repositories, that produces five problems.

1. **A practice spreads only by hand.** Something learned in one repository, such as a permission policy, a
   verification step, the way one kind of change is made or a way to run workers unattended, reaches another only when
   someone visits it and copies it. Withdrawing it is another visit to each.
2. **Copies drift.** Each hand-made copy becomes its own dialect: the same command does different things in different
   repositories, and a fix made in one never reaches the others. For example, suppose acme copies its coding-agent
   workflow by hand into `acme/api`, `acme/web` and `acme/infra`. A year later the verification step also lints in
   `acme/api` and skips the slow tests in `acme/web`, and `acme/infra` still has a bug that `acme/api` fixed months ago.
3. **Safety is lost in copying.** In the same example, `acme/web` and `acme/infra` now start unattended coding agents
   with permission checks turned off, which the workflow in `acme/api` never allowed.
4. **Everyone pays for one person's choice.** Teammates and pipelines that never chose a practice inherit whatever its
   copy requires, or whatever it breaks. For example, a hook that one person adds, calling a program only their machine
   has, fails in every teammate's coding-agent session and in every pipeline that runs it.
5. **Nobody can tell what a repository's workers may do, what a change must pass, or why.** Rules spread across
   hand-edited files, personal settings and sources outside the repository cannot be read, reviewed or explained from
   the repository itself.

## The goals

- A practice learned once reaches every repository through one reviewed change in each, and is withdrawn the same way.
- A fix to the lifecycle the tool enforces reaches every adopted repository through one release, a fix to how a program
  is described through one reviewed pin move in each, and a better way to make one kind of change through one reviewed
  merge in each.
- Every change passes the same stages and guards in every adopted repository, whether a person or a worker makes it.
- Wherever workers run unattended, they cannot bypass permissions or be recorded as done without a verification that
  passed.
- Adoption is optional per repository and per person, and removal leaves a working repository.
- Anyone reading a repository can tell what its workers may do, what a change must pass, and why.
- The tool's own repository is developed with the tool.

## What `the-cook` is

`the-cook` is a declared software development lifecycle, and the binary that enforces it. A change goes through fixed
stages: plan, start, implement, verify, complete, deliver, review and merge, and landed, with an out-of-band stop,
blocked, at any of them. The binary fires an event between stages, at which the repository's declared actions run; it
holds the guards between stages, so delivery never runs for work that is uncommitted, on the default branch or, with
verification on, unverified; and it binds the proof a change carries to the change's base and content, and replays that
proof from the base before an unattended attempt counts as done ([development lifecycle](features/lifecycle.md),
[ADR 0015](adrs/0015-development-lifecycle.md)). It does no stage's work itself: it never calls a model and never edits
the code. It sets the rules and never plays ([the game master](features/README.md#the-game-master),
[ADR 0001](adrs/0001-role-of-the-tool.md)). The name comes from the kitchen: a cook follows the recipe exactly and lets
nothing leave the kitchen until it passes the check. The workers do the cooking, and `the-cook` holds them to that
standard.

The lifecycle knows no program. Each stage that needs one reaches it through a contract or a declared action: the
version-control contract for workspaces, bases, history and landed work, the worker contract for the programs that
change the repository for someone, such as coding agents, bots and codemods, and lifecycle actions for everything else.
The binary contains code and data for no program. What a repository adds to the lifecycle comes in three units, and one
rule separates them: facts about programs are pinned and rendered; practices are copied and become the repository's own.
An [ingredient](features/ingredients.md) holds the facts about one program: the files rendered for it, the operations
that fill a contract, and the arguments and commands that are never safe; a repository pins it, and it acts on every
render and every launch. A [station](features/stations.md) holds what one way of working needs in place: the ingredients
to pin, the workers and their roles, the entries and templates a repository starts from, and the recipes to add; a
repository applies it once. A [recipe](features/recipes.md) is one kind of change, made many times, such as adding a
database migration or implementing a task: its inputs, its steps mapped onto the stages, and the lanes it requires; a
repository copies it from a cookbook or writes its own, and each run takes one change through the stages
([ADR 0022](adrs/0022-tool-integration.md), [ADR 0023](adrs/0023-core-and-contracts.md),
[ADR 0024](adrs/0024-ingredients-and-stations.md), [ADR 0025](adrs/0025-recipes.md)). semantic-release reaches its
release steps through plugins in the same way, and it and ESLint keep plugins, which add what a tool can do, apart from
shareable configurations, which set how a project uses it; Chef, Ansible and Terraform describe what they manage as
data. A repository takes each ingredient and station it uses from a cookbook in a reviewed change. Agentic coding, with
an interactive coding agent, an unattended one, the loop runner that hands it tasks and the recipe each task runs, is
the first station and the main example; it is one way of working, not the lifecycle's definition.

It is not a worker, not a client of hosting platforms or tool servers, not a source of configuration that lives outside
your repository, not a launcher for any loop runner, and not a replacement for your task runner, skills installer or
task tracker. It works with whichever programs a team uses, through the ingredients, commands and actions named in the
repository's own files.

## Tenets

Each tenet answers one or more of the problems above. A tenet decides nothing by itself; it names the ADRs and
standards that make it binding.

### Start from the objective

**Problem.** A feature, decision or rule whose purpose is not stated is judged against whatever purpose its reader
assumes. It cannot be changed safely, and it outlives the reason it was made.

**Tenet.** Everything starts from its objective: the problem it solves or the need it meets, and the goal that holds
once it is solved. Every document states its objective first, in a few plain sentences that a reader understands
without any data. The data and examples come after it, to make it concrete and checkable. When the objective cannot be
named, the thing is not built. When the problem goes away, the thing goes with it. The goal is what its confirmation
measures.

**Bound by.** [ADR structure](adrs/README.md#the-objective-comes-first),
[documentation structure](ux/messages-and-docs.md#documentation-structure).

### Everything is explicit

**Problem.** Problem 5: behaviour whose source is outside the repository cannot be read, reviewed or explained from it.

**Tenet.** Like Terraform, where infrastructure is what its configuration declares and every change is shown as a plan
before it is applied, a repository's lifecycle is what its files declare. What workers may do there, which lanes and
guards a change passes, which hooks run and which actions fire come from the repository's own files, the release it
pins and the ingredients it pins by hash, and from nothing else: nothing is inherited, included, fetched or inferred
while it runs. Generated files are committed, so what workers read is what a reviewer reads. Every write is shown
before it happens.

**Bound by.** [ADR 0013](adrs/0013-organization-content.md), [ADR 0007](adrs/0007-ownership.md),
[ADR 0017](adrs/0017-engine-version.md),
[principle 9](ux/principles.md#9-everything-a-repository-does-is-in-its-own-files),
[principle 5](ux/principles.md#5-never-destroy-always-show-what-will-change).

### Learn once, apply everywhere, withdraw as easily

**Problem.** Problems 1 and 2.

**Tenet.** What is learned once is built once, as a guard of the lifecycle in the tool, as an ingredient that holds a
fact about a program, or as a station or a recipe that holds a practice, and reaches each repository as data in that
repository's files. Adopting it, upgrading it and removing it take one command per repository, or one operation across
many, and removal leaves nothing the tool owns.

**Bound by.** [ADR 0006](adrs/0006-repository-contract.md), [ADR 0007](adrs/0007-ownership.md),
[ADR 0017](adrs/0017-engine-version.md).

### Optional, and invisible to those who do not use it

**Problem.** Problem 4.

**Tenet.** Nothing in an adopted repository needs the binary. A teammate without it, and every CI pipeline, works
exactly as before adoption, and a repository adopts or withdraws one practice at a time.

**Bound by.** [ADR 0010](adrs/0010-dependence-on-the-binary.md), [ADR 0004](adrs/0004-runtime-dependencies.md),
[principle 1](ux/principles.md#1-optional-and-transparent).

### Determinism in the binary, judgement in people and workers

**Problem.** Problem 2: work that nothing fixes comes out different every time.

**Tenet.** Work with one right answer is done by the binary, the same way every time, and can be checked: moving a
change between stages, rendering, verifying, replaying. Work that needs judgement is done by people, and by workers
such as coding agents guided by documentation and skills; the binary never guesses. A recipe draws the same line inside
one change: its commands and templates run the same way every time, and its attempts and handoffs give the judgement to
a worker or a person.

**Bound by.** [ADR 0002](adrs/0002-division-of-work.md),
[principle 2](ux/principles.md#2-the-binary-is-deterministic-judgement-belongs-to-people-and-workers),
[when to use the CLI and when a skill](ux/when-cli-when-skill.md).

### Sets the rules, never plays

**Problem.** A tool that does a stage's work itself has to follow every change of the programs that do that work, such
as every model and API change of a coding agent, and to hold credentials and costs that belong to each organization.

**Tenet.** `the-cook` enforces the lifecycle: the stages, the guards between them and the proof a change carries. It
configures and constrains the workers that do the work and never does it itself. It never calls a model, holds no model
credentials and makes no decision that needs one.

**Bound by.** [ADR 0001](adrs/0001-role-of-the-tool.md), [ADR 0015](adrs/0015-development-lifecycle.md),
[principle 3](ux/principles.md#3-sets-the-rules-never-plays).

### Knows no program

**Problem.** Problem 4: support in the binary for one loop runner, task tracker or task runner makes every repository
depend on a choice its team did not make. A tool with code for a program also has to follow every one of that
program's releases.

**Tenet.** Every program the binary starts, calls or writes files for, the version control system and the workers
included, is described by an ingredient. The binary keeps the lifecycle and the process around it: stages, events,
guards, proof, attempts, ownership, rendering and removal. Ingredients keep the program facts: the files a program
reads, the commands that answer the binary's questions, the arguments that start it unattended and the ones that are
never safe. Stations and recipes keep practices, which the next tenet sets apart from facts. The binary ships no
ingredient, station or recipe and names no default source of one: a repository takes every ingredient and station it
uses from a cookbook, and takes each recipe from one or writes its own, each in a reviewed change, and that review is
the trust decision for everything it runs. Any number of cookbooks may exist, and none is official. A team switches a
program by switching its ingredient, which changes only the repository's files, and other programs call `the-cook`
through a stable command interface.

**Bound by.** [ADR 0022](adrs/0022-tool-integration.md), [ADR 0023](adrs/0023-core-and-contracts.md),
[ADR 0024](adrs/0024-ingredients-and-stations.md).

### Facts are pinned, practices are copied

**Problem.** Problems 2 and 3, and the opposite trap: a setup inherited live from a shared source changes under a
repository that changed nothing, and a repository cannot shape a practice it does not own.

**Tenet.** Facts about programs are pinned and rendered; practices are copied and become the repository's own. A fact
about a program is the same in every repository that uses it, such as the files it reads or the arguments that bypass
its checks, so an ingredient holds it, a repository pins the ingredient by version and hash, and every render writes the
fact again: no copy can drift from it or lose it, and a fix reaches each repository as one reviewed pin move. A practice
differs from one repository to the next, such as the lanes a change must pass, what unattended runs must never do or the
steps that add a migration, so a station or a recipe holds it and a repository copies it in: a station's entries and
templates once, a recipe whole. The copy records where it came from, is the repository's to edit, and takes a newer
version only through a reviewed change: applying the newer station again, or merging the newer recipe into the copy. A
practice only adds: a station adds only what the repository lacks, a recipe only steps, lanes and requirements, and
neither reaches the floor or the guards.

**Bound by.** [ADR 0013](adrs/0013-organization-content.md), [ADR 0024](adrs/0024-ingredients-and-stations.md),
[ADR 0025](adrs/0025-recipes.md).

### A safety floor nobody can lower

**Problem.** Problem 3.

**Tenet.** The rules that are never a legitimate choice, such as running unattended workers without permission checks,
hold in every repository, and no repository, copy or edit can remove them. The process invariants, such as refusing
bypass arguments before a launch, replaying proof from the base and withholding completion, are built into the binary.
The program facts they apply, such as which arguments bypass a coding agent's checks or that a force push to the
default branch is denied, are floor rules of the ingredients the repository pins: rendered on every render and out of
reach of the repository's policy.

**Bound by.** [ADR 0011](adrs/0011-safety-invariants.md), [ADR 0022](adrs/0022-tool-integration.md).

### Never touch what it did not create

**Problem.** Problem 4: repositories already hold state, records and settings that people and other tools depend on.

**Tenet.** `the-cook` writes only what it owns, uses what others created as it finds it, and leaves the team's record
of work, such as issues and pull requests, to the team.

**Bound by.** [ADR 0012](adrs/0012-write-boundary.md), [ADR 0008](adrs/0008-native-configuration.md),
[ADR 0016](adrs/0016-permission-adoption.md).

### Conventions outlive the tool

**Problem.** Problem 4: teams stop using tools, and many teammates never start.

**Tenet.** The working agreements `the-cook` sets up, such as where working copies live and how tasks are tracked, are
written so that they read correctly and still hold without it.

**Bound by.** [ADR 0018](adrs/0018-conventions.md),
[principle 8](ux/principles.md#8-conventions-outlive-the-tool).

### Built with itself

**Problem.** A tool whose authors do not live with it ships its rough edges to every adopter.

**Tenet.** The `the-cook` repository is adopted with `the-cook`, and its CI runs the
[drift check](features/render.md#drift-check) and the [health check](features/doctor.md) on itself.

**Bound by.** The project's own CI.

## How the documentation fits together

| Part | Holds | Authority |
| --- | --- | --- |
| Philosophy (this page) | the problems, the goals and the tenets | none: it explains, and every tenet names what binds it |
| [ADRs](adrs/README.md) | the decisions and their reasons | the source of truth; every other part follows them |
| [User-experience standards](ux/README.md) | the rules for everything a user sees | follow the ADRs; govern the feature pages and the tool's behaviour toward users |
| [Features](features/README.md) | what each feature does and how to use it | describe; follow the ADRs and the standards |

Every document in each part opens with its objective. A tenet that nothing binds is a missing decision, not a rule:
the decision is written in an ADR or a standard, and the tenet links to it. When the problems or goals change, the
tenets change in the same pull request, and so does every ADR and standard they bind.
