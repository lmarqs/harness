# Skills standards

## Objective

**Problem.** Coding agents do the judgement work around the tool by following written guidance they load for a task:
guidance the tool ships to everyone, guidance an organization shares across its repositories, and guidance one
repository keeps. When that guidance has no shared rules, it fails to load when the task needs it or loads where it does
not belong, it restates what a command does and drifts from it, and it gives orders without reasons that a coding agent
drops when they seem inconvenient. A developer then gets a coding agent that edits the wrong files, skips the checks a
command would have run, or follows rules the release in use no longer has, and nobody finds out until it acts.

**Goal.** Whichever guidance a coding agent follows loads when the task needs it, agrees with the release the repository
runs, and ends in a result a check confirms rather than one taken on trust.

## Overview

A skill is a directory with a `SKILL.md` that a worker loads when its description matches the task, or that `the-cook`
injects through standing context. Skills carry the judgement half of the work; the binary carries the deterministic half
([when-cli-when-skill.md](when-cli-when-skill.md)). This page covers the skill `the-cook` ships and the rules `the-cook`
expects of the skills around it. Terms such as standing context, pin and profile are defined in the
[glossary](../features/glossary.md).

## Three kinds of skill

| Kind | Lives in | Written by | Reaches a repository by | Holds |
| --- | --- | --- | --- | --- |
| **shipped skill** | the binary; installed at `.agents/skills/the-cook/` | `the-cook` maintainers | `the-cook render` (tool-owned, listed in the manifest) | how to run `the-cook` (`./cook <command>`), edit the repository files in `.cook/`, finish an adoption, read `doctor` findings, when to run `the-cook lifecycle run <event>`, how to drive a recipe run through its handoffs, and why the invariants exist |
| **Organization skill** | an organization's skills repository | the organization | installed by the team with the skills installer of its choice and committed under `.agents/skills/`; the binary has no code for the installer, which an ingredient the repository pins can describe | conventions shared by one organization's repositories, and how to write them into one repository's files: delivery actions, policy entries (observability read patterns, deploy denies), review, domain vocabulary |
| **Repository skill** | `.agents/skills/<name>/` in the repository | people and workers in that repository | committed directly | knowledge of one repository: its modules, risks, procedures |

The binary writes only the shipped skill. Organization and repository skills are knowledge; the binary never creates,
edits or deletes them ([principles.md](principles.md#3-runs-the-game-never-plays)), because knowledge needs judgement
the binary does not have.

In a repository, every skill lives under `.agents/skills/`, the directory of content workers read that other tools
share, so a skills installer, the workers and people find them in one place. The configuration of `the-cook` lives in
`.cook/` and never among skills ([configuration-standards.md](configuration-standards.md#where-files-live)).

## When to create each

| Situation | Kind | Why |
| --- | --- | --- |
| Workers misuse a `the-cook` file or command in many unrelated repositories | shipped skill (change ships in a release) | the fix reaches each repository in the diff that moves its pin to that release |
| A new `the-cook explain` topic is needed | shipped skill reference with the same text | both come from one embedded source |
| A convention holds across one organization's repositories, as judgement or as entries `the-cook` enforces (a deny, a delivery action) | organization skill that guides a coding agent to write it into each repository's files ([below](#organization-skills-that-write-repository-entries)) | the organization owns and reviews the skill once; applying it to a repository takes judgement, and each repository reviews the result |
| A procedure or body of knowledge is specific to one repository | repository skill | it is reviewed where it applies |
| Known guidance must apply every session, or whenever certain files or words appear | an existing skill plus a standing-context entry ([configuration-standards.md](configuration-standards.md#standing-context)) | the trigger is deterministic; the skill stays the single text |
| The behaviour is deterministic and organization neutral | not a skill: a command, flag, profile field, [ingredient](../features/ingredients.md) or [station](../features/stations.md) | prose would drift from the behaviour |
| A kind of change a repository makes many times, part deterministic and part judgement (a migration, a new module) | a [recipe](../features/recipes.md), whose `attempt` steps name the skill that guides the judgement ([below](#skills-a-recipe-step-names)) | the recipe makes the change the same way and verified every time, and the skill keeps the guidance in one text |
| A one-off instruction for a single session | not a skill: the prompt | a skill outlives the session it was written for |

The shipped skill names no organization, vendor or company convention, because it ships to every user of an open-source
tool. Anything that would need one belongs in an organization skill, and from there in each repository's files.

## Structure

```text
<name>/
  SKILL.md          # frontmatter + the core procedure
  references/       # detail loaded on demand
  scripts/          # optional helpers the procedure runs
```

- `name` in the frontmatter matches the directory name: lower case, hyphen-separated, so the name a person types and
  the directory a worker reads agree.
- `description` is the trigger. It states what the skill does and when to use it, in the words users and workers
  actually use for the task, and names the closest situations where it does not apply. Why: the worker matches the
  description against the task, so the wrong words mean the skill never loads or loads where it does not belong.
- The body of `SKILL.md` SHOULD stay under 500 lines. It holds the procedure a worker follows every time; anything
  needed only in some cases moves to `references/` with a one-line pointer saying when to read it. Why: the body is
  loaded in full whenever the skill is used.
- Procedures are numbered steps. Each step that changes state names the command that performs it. The last step is the
  check that confirms the result (`the-cook render --check`, `the-cook doctor`, `the-cook verify --check`), so the
  outcome of the coding agent's judgement is checked by the binary.

## Skills used as standing context

A skill named in a standing-context entry is injected without the worker choosing it: as content (the `SKILL.md` body,
front matter stripped) or as a one-line pointer to its path. It may also still load by its description. A skill meant
for standing context:

- keeps its body short and self-contained, because an always-on entry is paid in every session and counts against
  `context.budget`;
- reads correctly without the description that normally introduces it, because injected content arrives without its
  front matter;
- states in its first lines what it applies to, so a pointer line ("Apply the <name> skill now: read <path>") and the
  first screen of the body are enough to act on;
- reads correctly when loaded twice, once injected and once by description, because the worker's own matching is left
  as it is ([compatibility-standards.md](compatibility-standards.md#coexistence));
- is committed under `.agents/skills/`, whichever tool installed it, because `the-cook` injects the committed file (for
  an attempt, as it is at the base commit) and reports a `context` entry that names a skill not found there.

A directive that must apply even without the binary is declared `static`, so it is written into `AGENTS.md` and does
not depend on a hook.

## Skills a recipe step names

A recipe's `attempt` step names, in its prompt, the skill that guides the judgement it asks for, such as a migration
skill for the step that writes a migration's up and down steps, or a monitoring skill for a recipe that acts on an
alert. The recipe holds the steps and the skill holds the guidance, so neither repeats the other
([when-cli-when-skill.md](when-cli-when-skill.md#recipes)). Such a skill:

- is written for an unattended worker: it asks no question and waits for no answer, because nobody replies inside an
  attempt; a check a person must make is a handoff in the recipe, not a question in the skill;
- ends with the check: `./cook verify` with the lanes and patterns the change needs.
  - The check tells the worker whether its edits pass.
  - That receipt is feedback for the worker. The proof comes from the recipe run's Verify stage, which itself runs
    every lane the change selects and every lane the recipe requires.
  - Completion and delivery belong to the run, so the skill never tells the worker to commit, deliver or fire an event,
    which the attempt contract refuses anyway.
- reads correctly in an interactive session too, because the same skill guides a person's coding agent making the same
  kind of change by hand.

## Organization skills that write repository entries

An organization's choices that `the-cook` enforces (which observability tools coding agents may read without asking, a
deny on each repository's deploy command, a CI-trigger commit in delivery, the actions that update the task tracker its
repositories use) reach a repository only as entries in its own files, written by a person or by a coding agent
following the organization's skill. Why: no repository file can name a source outside the repository, and the
organization's choices differ between its repositories, so applying them takes judgement. Such a skill:

- states each entry with the reason the organization holds it, and how to fit it to the repository in front of the
  coding agent: which entries apply, and what to do with an entry the repository already has, because the coding agent
  decides per repository;
- has the coding agent edit `.cook/policy.yaml`, `.cook/hooks.yaml` or `.cook/config.yaml`, never a rendered file or
  a managed block, because those are replaced by the next render;
- ends by running `the-cook render` and then `the-cook doctor`, so the entries and their rendered effect reach review as
  one diff and the binary checks the result.

For example, acme's CI runs a pull request only when its tip commit carries `#ci`, and acme's coding agents may search
its observability service without asking. acme's skill has the coding agent add `"mcp__acme_observability__search_*"`
under `shared.allow` in `.cook/policy.yaml`, read the repository's CI configuration, and, only where that CI waits for
`#ci`, declare a `ci-trigger` action before the pull-request action under `lifecycle.deliver` in `.cook/hooks.yaml`.

## Explain the reasoning

Each rule in a skill carries its reason in one sentence. A coding agent that knows why a rule exists applies it to
cases the text did not foresee and does not drop it when it seems inconvenient.

- Good: "Edit `.cook/policy.yaml`, never a worker's rendered settings: they are rendered from the policy and
  `render --check` fails on hand edits."
- Not enough: "Never edit the workers' settings files."

Rules phrased only as capitalised commands (ALWAYS, NEVER) without a reason SHOULD be rewritten, because emphasis
without a reason gives the coding agent nothing to apply to a new case.

## Never duplicate a CLI behaviour in prose

Why: a description of a command drifts from the command, and a coding agent that follows the description instead of
running the command skips the command's checks.

- A skill calls a command; it never lists the steps the command performs so that a coding agent could perform them by
  hand.
- A skill whose task is a kind of change a recipe in the repository makes runs that recipe
  (`./cook make <recipe> --<input> <value>`) instead of listing its steps, so the change takes the recipe's steps and
  required lanes, and the skill does not drift from the recipe.
- A skill never tells a coding agent to edit a rendered file, edit inside a managed block, or bypass permissions,
  because each of those is undone by the next render or refused by the invariants.
- A skill that ends in delivery does its judgement steps (grouping commits, wording messages), then runs
  `the-cook lifecycle run deliver`. It never repeats the declared actions (a push, a pull-request command, a webhook)
  by hand, so every side effect outside the clone stays declared, guarded and recorded.
- Flag tables, schemas and exit codes are not copied into skills. A skill points to `the-cook <command> --help` or
  `the-cook explain <topic>` for them.

## Keeping the shipped skill and the CLI in sync

Why: a coding agent acts on the skill; when the skill and the binary disagree, it acts on wrong rules.

- `the-cook explain <topic>` prints the same text as the shipped skill's reference for that topic. Both come from one
  embedded source, so they cannot disagree.
- Every topic listed by `the-cook explain` has a reference in the skill, and every reference is reachable from
  `SKILL.md`.
- Every command line quoted in the shipped skill MUST exist in the CLI surface of the same release. The release
  process checks this.
- The shipped skill writes commands as `./cook <command>` and shows `the-cook <command>` as the equivalent when
  `the-cook` is installed globally ([cli-standards.md](cli-standards.md#entry-points)).
- A change to a command's behaviour, a file schema or an invariant updates the matching skill text in the same pull
  request.

## Evaluation before release

A skill is released only after it is evaluated. For the shipped skill this is required; organization and repository
skills SHOULD follow the same practice. Why: a skill's effect is seen only in what coding agents do with it, and reading
the text does not show that.

| Evaluation | What it checks | Pass condition |
| --- | --- | --- |
| Trigger | prompts that should load the skill, and near-miss prompts that should not | the skill loads for the first set and not for the second |
| Task | realistic requests run by a coding agent in fixture repositories of each supported stack ("allow the deploy tasks", "finish the adoption", "add a lane for integration tests") | the binary's own checks pass afterwards (`render --check`, `doctor`, `verify --check`) and no file outside the expected ones changed |
| Regression | the task set from the previous release | no task that passed before fails |

Evaluations judge outcomes by deterministic checks wherever possible, not by reading the coding agent's prose, because
the same prose can come with a wrong result.
