# Philosophy

## Objective

**Problem.** The ADRs, standards and feature pages each say what holds and why, but none of them shows the whole they
serve. A contributor or an agent reading one of them cannot tell what the project as a whole is trying to achieve, so
a proposal can solve a problem nobody has, or work against a goal the project holds, without anyone noticing.

**Goal.** In one read, anyone can learn which problems `harness` exists to solve, what it is trying to achieve and the
tenets every decision follows from, and can check a new proposal against them.

## The problems

Teams that work with coding agents keep each repository's agent setup by hand: permissions, hooks, instruction files,
skills, and the loops that run agents unattended. Across many repositories, that produces five problems.

1. **A practice spreads only by hand.** Something learned in one repository, such as a permission policy, a
   verification step or a way to run agents unattended, reaches another only when someone visits it and copies it.
   Withdrawing it is another visit to each.
2. **Copies drift.** Each hand-made copy becomes its own dialect: the same command does different things in different
   repositories, and a fix made in one never reaches the others. For example, suppose acme copies one agent workflow by
   hand into `acme/api`, `acme/web` and `acme/infra`. A year later the verification step also lints in `acme/api` and
   skips the slow tests in `acme/web`, and `acme/infra` still has a bug that `acme/api` fixed months ago.
3. **Safety is lost in copying.** In the same example, `acme/web` and `acme/infra` now start unattended agents with
   permission checks turned off, which the workflow in `acme/api` never allowed.
4. **Everyone pays for one person's choice.** Teammates and pipelines that never chose a practice inherit whatever its
   copy requires, or whatever it breaks. For example, a hook that one person adds, calling a program only their machine
   has, fails in every teammate's agent session and in every pipeline that runs it.
5. **Nobody can tell what a repository's agents may do, or why.** Rules spread across hand-edited files, personal
   settings and sources outside the repository cannot be read, reviewed or explained from the repository itself.

## The goals

- A practice learned once reaches every repository through one reviewed change in each, and is withdrawn the same way.
- A fix to a workflow the tool provides reaches every adopted repository through one release.
- People and agents work the same way in every adopted repository.
- Wherever agents run unattended, they cannot bypass permissions or be recorded as done without a verification that
  passed.
- Adoption is optional per repository and per person, and removal leaves a working repository.
- Anyone reading a repository can tell what its agents may do and why.
- The tool's own repository is developed with the tool.

## What harness is

`harness` is a meta-harness. It writes, checks and removes the configuration that the agent harnesses Claude Code and
Codex CLI read, and it constrains how an agent runs when `harness` starts it, as `harness worker` does for each task a
loop runner hands it. It never calls a model and never runs an agent loop: it sets the rules and never plays
([the game master](features/README.md#the-game-master), [ADR 0001](adrs/0001-role-of-the-tool.md)).

It is not an agent, not a client of hosting platforms or agent tool servers, not a source of configuration that lives
outside your repository, not a launcher for any loop runner, and not a replacement for your task runner, skills
installer or task tracker. It contains no code for those tools and works with whichever a team uses, through commands,
actions and presets in the repository's own files ([ADR 0022](adrs/0022-tool-integration.md)).

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
before it is applied, a repository's agent setup is what its files declare. What agents may do there, which hooks run
and which actions fire come from the repository's own files and the release it pins, and from nothing else: nothing
is inherited, included, fetched or inferred while it runs. Generated files are committed, so what agents read is what a
reviewer reads. Every write is shown before it happens.

**Bound by.** [ADR 0013](adrs/0013-organization-content.md), [ADR 0007](adrs/0007-ownership.md),
[ADR 0017](adrs/0017-engine-version.md),
[principle 9](ux/principles.md#9-everything-a-repository-does-is-in-its-own-files),
[principle 5](ux/principles.md#5-never-destroy-always-show-what-will-change).

### Learn once, apply everywhere, withdraw as easily

**Problem.** Problems 1 and 2.

**Tenet.** A practice is built once, in the tool, and reaches each repository as data in that repository's files.
Adopting it, upgrading it and removing it take one command per repository, or one operation across many, and removal
leaves nothing the tool wrote.

**Bound by.** [ADR 0006](adrs/0006-repository-contract.md), [ADR 0007](adrs/0007-ownership.md),
[ADR 0017](adrs/0017-engine-version.md).

### Optional, and invisible to those who do not use it

**Problem.** Problem 4.

**Tenet.** Nothing in an adopted repository needs the binary. A teammate without it, and every CI pipeline, works
exactly as before adoption, and a repository adopts or withdraws one practice at a time.

**Bound by.** [ADR 0010](adrs/0010-dependence-on-the-binary.md), [ADR 0004](adrs/0004-runtime-dependencies.md),
[principle 1](ux/principles.md#1-optional-and-transparent).

### Determinism in the binary, judgement in agents

**Problem.** Problem 2: work that nothing fixes comes out different every time.

**Tenet.** Work with one right answer is done by the binary, the same way every time, and can be checked. Work that
needs judgement is done by people and by agents guided by documentation and skills; the binary never guesses.

**Bound by.** [ADR 0002](adrs/0002-division-of-work.md),
[principle 2](ux/principles.md#2-the-binary-is-deterministic-judgement-belongs-to-agents),
[when to use the CLI and when a skill](ux/when-cli-when-skill.md).

### Sets the rules, never plays

**Problem.** A tool that runs agents itself has to follow every model and API change, and to hold credentials and
costs that belong to each organization.

**Tenet.** `harness` configures and constrains the harnesses that run agents. It never calls a model, holds no model
credentials and makes no decision that needs one.

**Bound by.** [ADR 0001](adrs/0001-role-of-the-tool.md),
[principle 3](ux/principles.md#3-sets-the-rules-never-plays).

### Integrates only what it configures

**Problem.** Problem 4: built-in support for one loop runner, task tracker or task runner makes every repository depend
on a choice its team did not make, and breaks with each of that tool's releases.

**Tenet.** The binary integrates git and the agent harnesses it configures, and nothing else. Every other tool reaches
a repository as data in that repository's own files: a command it declares, an action a lifecycle event runs, or the
entries and templates of a preset. Other tools call `harness` through a stable command interface, so switching one
changes only the repository's files.

**Bound by.** [ADR 0022](adrs/0022-tool-integration.md), [ADR 0019](adrs/0019-action-kinds.md),
[ADR 0013](adrs/0013-organization-content.md).

### A safety floor nobody can lower

**Problem.** Problem 3.

**Tenet.** The rules that are never a legitimate choice, such as running unattended agents without permission checks,
are built into the binary. No repository, copy or edit can remove them.

**Bound by.** [ADR 0011](adrs/0011-safety-invariants.md).

### Never touch what it did not create

**Problem.** Problem 4: repositories already hold state, records and settings that people and other tools depend on.

**Tenet.** `harness` writes only what it owns, uses what others created as it finds it, and leaves the team's record
of work, such as issues and pull requests, to the team.

**Bound by.** [ADR 0012](adrs/0012-write-boundary.md), [ADR 0008](adrs/0008-native-configuration.md),
[ADR 0016](adrs/0016-permission-adoption.md).

### Conventions outlive the tool

**Problem.** Problem 4: teams stop using tools, and many teammates never start.

**Tenet.** The working agreements `harness` sets up, such as where working copies live and how tasks are tracked, are
written so that they read correctly and still hold without it.

**Bound by.** [ADR 0018](adrs/0018-conventions.md),
[principle 8](ux/principles.md#8-conventions-outlive-the-tool).

### Built with itself

**Problem.** A tool whose authors do not live with it ships its rough edges to every adopter.

**Tenet.** The `harness` repository is adopted with `harness`, and its CI runs the
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
