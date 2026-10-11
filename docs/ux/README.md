# User-experience standards

## Objective

**Problem.** Developers, reviewers, coding agents and the other programs that change repositories for them, CI pipelines
and teammates who never chose the tool all meet it through what it shows them and what it leaves in their repositories.
When each feature decides on its own how it treats them, what someone learns from one feature does not hold for the
next, a new feature can impose on people who never adopted the tool, and nobody can tell whether a behaviour they meet
is intended or a defect.

**Goal.** Every feature, present and future, behaves the same predictable way toward each of these users, and anyone who
meets a behaviour can tell whether it is intended or a defect.

## Overview

`the-cook` is a declared software development lifecycle: the stages a change goes through, the events it fires between
them, the guards that hold between stages and the proof a change carries ([lifecycle](../features/lifecycle.md)). It
knows no program: a stage that needs one, such as a coding agent at Implement, reaches it through a contract an
ingredient fills or through an action the repository declares. Work enters the lifecycle through a person, a worker, a
loop runner or a recipe run: one pass of one change through the stages, as a recipe the repository holds describes it.
These pages hold the rules for everything `the-cook` presents to the people, workers and systems that meet it: commands,
flags, output, exit codes, configuration files, generated content, skills and documentation. They are normative, like
architecture decision records, but they govern how `the-cook` is experienced rather than how it is built.

- Architecture decisions: [../adrs/](../adrs/)
- Individual features and their reference: [../features/](../features/)
- Terms (profile, policy, managed block, manifest, receipt, worker, attempt, ingredient, station, recipe, recipe run,
  contract, renders, lane, …): [glossary](../features/glossary.md)
- Ingredients, the contracts they fill and their floor: [ingredients](../features/ingredients.md)
- Stations, what a way of working needs in place, which a repository applies once: [stations](../features/stations.md)
- Recipes, the kinds of change a repository makes many times and runs through the lifecycle:
  [recipes](../features/recipes.md)

A standard here applies to every feature, present and future. When the binary, a generated file, the shipped skill or
a features page disagrees with a standard, that artifact is wrong and is corrected to match the standard. When a
standard disagrees with an ADR, the ADR wins and the standard is corrected, because the ADRs decide the architecture
the standards present to users.

These pages name commands `the-cook <command>`, the program's name. In a repository they run through the committed
wrapper as `./cook <command>` ([cli-standards.md](cli-standards.md#entry-points)).

## Who these standards protect

| User | Meets `the-cook` through | What the standards guarantee |
| --- | --- | --- |
| Developer in an adopted repository | `./cook`, the repository's recipes, generated files in pull requests | one pinned version on every machine, predictable commands, readable diffs, nothing written without being shown first |
| Worker in an interactive session (a coding agent a person works with, as a pinned worker ingredient describes it) | rendered settings, hooks, `AGENTS.md` blocks, injected standing context, the shipped skill, `the-cook explain` | parseable output, exact next commands, text that matches the binary |
| Worker in an attempt (one [`the-cook attempt`](../features/attempts.md) runs for a loop runner, a script or a coordinating worker, one a recipe run's `attempt` step starts, or one an [`attempt` action](../features/lifecycle.md#action-kinds) starts) | the CLI without a terminal, exit codes, JSON | no prompts, a distinct "blocked" outcome, no hidden state |
| Another tool that calls `the-cook` (a loop runner, a tracker updated by actions) | `the-cook attempt`, `the-cook make`, lifecycle events and their `COOK_*` variables, files a station's templates wrote | a stable attempt and make interface; no code for the tool in the binary and no reading of its configuration; every file it reads is the repository's own |
| CI | the optional `./cook render --check` and `./cook doctor` steps | deterministic checks with the pinned, checksum-verified version; nothing required |
| Repository owner or teammate who does not use `the-cook` | committed files only | a clone that builds, tests and runs its workers without the binary; conventions written in plain language in `AGENTS.md` and the ignore file |
| Repository that stops using `the-cook` | `the-cook remove`, `the-cook remove --eject` | undo returns the repository to a working state without `the-cook`; eject keeps its conventions, permissions and hooks as the repository's own |
| Pull-request reviewer | diffs of rendered files, managed blocks and the pin | every generated change visible and traceable to a source file; every version change traceable to a signed release |
| Workflow maintainer | `fleet`, cookbooks, `the-cook station apply`, `the-cook ingredient pin` and `the-cook recipe add`, declared actions, organization skills, schemas | one way to do each thing, safe across many repositories; nothing leaves a clone undeclared; nothing reaches a repository except as a reviewed change to its own files |
| Open-source user | any of the above | no organization-specific content in the binary, its skill or its docs |

## Normative language

The key words MUST, MUST NOT, REQUIRED, SHALL, SHALL NOT, SHOULD, SHOULD NOT, RECOMMENDED, MAY and OPTIONAL are
interpreted as described in BCP 14 (RFC 2119, RFC 8174) when, and only when, they appear in capitals. Lower-case uses
carry their ordinary English meaning.

- MUST / MUST NOT: a release that violates the rule is defective.
- SHOULD / SHOULD NOT: deviation needs a reason stated in the pull request that introduces it.
- MAY: an allowed option; no justification needed.

## Every rule carries its reason

Each normative rule in these pages states why it holds, in a "because" clause or a "Why:" sentence that follows it. A
rule whose reason is known can be applied to a case the text did not foresee, and can be judged when it seems to get
in the way; a rule without one can only be obeyed or ignored.

## Changing a standard

A standard is edited in place, in the same pull request that changes the behaviour it governs. Why: the standards
describe what `the-cook` is, so a standard and the behaviour it describes change together or not at all.

- The edited rule keeps its reason, updated when the reason changed.
- A change that would contradict an ADR changes the ADR first, in the same pull request, because the ADR is the
  authority the standard derives from.
- Typo fixes and clarifications that do not change a rule need no stated reason.
- The pages hold no status, date or record of earlier rules. Version control keeps the past; the page states what is
  true.

## Index

| Page | Governs |
| --- | --- |
| [principles.md](principles.md) | The user-facing principles every other standard derives from, and how they rank when they conflict |
| [cli-standards.md](cli-standards.md) | Entry points (the wrapper, a global install, implicit hooks), the attempt and run commands other tools call, command grammar, naming, flags, output, exit codes, errors, confirmations, dry-run, non-interactive use, environment variables, help, version, deprecation |
| [when-cli-when-skill.md](when-cli-when-skill.md) | Where new behaviour goes: command, flag, profile field, declared action, standing-context entry, skill, ingredient, station, recipe or the repository itself; support for a third-party program as an ingredient or a declared action; a repeated kind of change as a recipe |
| [skills-standards.md](skills-standards.md) | The shipped skill, organization skills and repository skills: when to create each, structure, use as standing context, evaluation |
| [configuration-standards.md](configuration-standards.md) | Where files live (`.cook/`, `.agents/`, state paths) and the user-editable files: the wrapper pin, inputs (only the repository's own files, its pinned release and its pinned ingredients), schemas, defaults, validation, policy entry forms, declared actions and their kinds, standing context, the ingredients it pins and the stations it applied (`ingredients:`, `stations:`, `vcs:`, `workers:`), the recipe file (inputs, steps, substitution, `origin`), secrets, what never becomes a setting |
| [generated-content-standards.md](generated-content-standards.md) | Everything `the-cook` writes into a repository: headers, owned and seeded files, template files, recipes, managed-block markers, ids and classes, the `AGENTS.md` budget, determinism, review, removal (undo and eject) |
| [messages-and-docs.md](messages-and-docs.md) | Voice and tone for CLI messages, text workers read, block text and documentation; the names of the lifecycle's parts (worker, attempt, ingredient, station, recipe, recipe run, step, handoff, cookbook); the features page template |
| [compatibility-standards.md](compatibility-standards.md) | Versioning, breaking changes, ingredients, stations and recipes across versions and the version ranges of ingredients, adoption equivalence, deprecation windows, supported workers, coexistence, transparency, version pinning and release trust, update notices |
