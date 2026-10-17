# Where new behavior goes

## Objective

**Problem.** Contributors need to decide whether a capability belongs in the engine, a program model, a reusable
setup recipe or guidance. A broad deterministic-versus-judgment rule can put external workflows into Cook merely
because they are repeatable, or turn program-specific configuration into engine policy.

**Goal.** Capabilities follow Cook's agreed responsibilities, with reusable program knowledge and setup in cookbooks,
judgment in guidance, and external work performed by the people and programs responsible for it.

## Overview

This page places a new capability in one of the destinations below: the engine and CLI, an ingredient, a recipe, a
cookbook, native repository files, a skill or prose, or an external tool. It follows the
[architecture](../architecture.md); the [glossary](../features/glossary.md) defines its engine terms. Cook evaluates
trusted Groovy recipes into ordered file operations, presents the combined terminal diff, obtains whole-batch approval,
records the batch and ownership and writes the declared targets. The CLI is the public cooking interface; Gradle
builds and packages Cook and cookbooks.

A capability may have several parts. Place each by responsibility and effect, rather than assuming every deterministic
part belongs in the binary.

## Destinations

| Destination | Responsibility | Maintainer |
| --- | --- | --- |
| Cook engine and CLI | Evaluation, ordered composition, diff presentation, approval, destination boundaries, recovery, writes, ownership in `cook.lock` and recorded-batch reversal | Cook contributors |
| Ingredient | Model a program's native files and configuration, including settings, ignore files or skills | Authors of the cookbook that holds it |
| Recipe | Describe reusable requested file operations using program models and trusted Groovy code | Cookbook or repository authors |
| Cookbook repository | Hold and ship ingredients and recipes; build them separately where appropriate | Cookbook authors |
| Native repository files | Hold settings, instructions and conventions consumed by the relevant programs | Repository contributors, with their chosen maintenance process |
| Skill or focused prose | Guide source selection, configuration choices, review and other work requiring judgment | Shared, organization, personal or repository authors |
| External tool or person | Execute tasks, run agents, manage working copies, publish work, contact services or update trackers | The team and those tools |

Ingredients are program data models. A recipe can configure native enforcement features, and the consuming program
enforces them. See [permission rules](configuration-standards.md#permission-rules).

The distinction is between a program's configuration concepts and the choices a team makes using them:

| Program knowledge an ingredient models | Setup choice a recipe can describe |
| --- | --- |
| Git ignore-file content | Ignore this repository's local build output or working-copy directory |
| An agent's native configuration | Write the settings selected for this repository |
| A program's native instructions and skill locations | Supply reviewed guidance or skills where that program consumes them |

## Criteria

Ask these questions in order for each part of a capability and stop at the first yes, because repetition alone does
not establish an engine responsibility:

| Question | Placement |
| --- | --- |
| 1. Does it change Cook's evaluation, review or execution of declared file operations? | Engine capability within the architecture |
| 2. Must the capability itself execute a command, launch a session, publish work or contact a service? | External tools and people |
| 3. Does it model the format of a program's native files or configuration? | Ingredient in a cookbook |
| 4. Does it select reusable file additions, updates or deletions for a setup, including a native setting, CI file, task-runner command or hook that a program later executes? | Recipe; review the program's interpretation and consequences of the written setting |
| 5. Does it require fitting conventions to one repository, weighing options or writing guidance? | Person or agent guided by a skill or prose |

Writing configuration and executing its workflow are separate steps, so a hook or CI file answers question 4, not
question 2: Cook reviews the file operation, and the consuming program later runs it under its own working directory,
credentials, permissions and failure rules. See [native hooks](configuration-standards.md#native-hooks).

Whatever the placement, decide whether the choice is shared or personal and which existing content should remain, and
choose declared targets and maintenance sources accordingly.

## Engine commands and recipe arguments

The engine's commands are apply, revert and `batch` ([CLI reference](../features/cli.md)). A new engine command or flag
requires its own decision when it changes that scope. Do not add a command for each program or organization, or invent
a setting that bypasses the agreed review and recovery guarantees, because program and organization choices belong in
cookbooks. Per-recipe choices use recipe arguments following that recipe
([CLI reference](../features/cli.md#recipe-arguments-and-ordering)).

## Recipes

Use a recipe to reuse requested native setup, whether the invocation is a first application or an update. Recipes
compose left to right into one combined diff, the last writer of a target becomes its owner, and applying B never
removes A's output ([ordered composition](../architecture.md#ordered-composition-and-declared-targets)). Ownership
never blocks a change.

Revert writes a recorded batch's reverse patches without evaluating recipes again, so reversing a recorded change
needs no recipe ([revert](../features/revert.md)).

A recipe is trusted executable code. External command execution is not a batch action. Arbitrary recipe side effects
remain outside diff approval, output boundaries and rollback.

## Guidance and native configuration

Use a skill to explain choices and review decisions, and a recipe when reusable file operations express the selected
setup. For instance, an organization skill can explain why certain native permissions apply, while ingredients model
the agent's format and a recipe supplies the requested settings. The actual settings and diff remain reviewable.

For repository-authored guidance or settings, direct edits are also legitimate. For recipe-maintained content,
updating the source first avoids a later apply replacing a local correction. Neither process establishes permanent
ownership or prohibits deliberate later changes. See [skills standards](skills-standards.md).

Skill discovery and session loading belong to the native program. Cook can supply instructions, links and settings as
file operations. Keep frequently read guidance short and link relevant detail; cross-references may run in either
direction.

Build, test and publication procedures belong in existing task runners, CI and guidance. Cook can configure them, and
those programs run them. A shared skill should call supported commands rather than imitate their implementation; its
evidence should fit the task.

## Worked examples

| Request | Destination | Reason and review boundary |
| --- | --- | --- |
| Combine two recipes that set the same key | Engine ordered composition | Later values replace earlier ones; preview the final combined result |
| Model Git ignore entries or Codex settings and skills | Ingredient in a cookbook | Knowledge follows the native program's files and formats |
| Apply shared agent settings | Recipe using program models | Review native permission meaning and preserve unrelated keys |
| Restrict force pushes or task chaining | Native program controls and team guidance | Cook may configure supported controls; the consuming program enforces them |
| Allow one repository's observability search tools | Native settings chosen with organization guidance | Fit the scope to the repository; file approval is not permission to contact the service |
| Give a Node project lint/build/test commands and an edit hook | Recipe-declared task-runner and native hook files | Programs execute the resulting configuration independently |
| Add a migration or framework module | Existing generator and contributor skill | Generator execution and implementation judgment belong to those tools and people |
| Write reusable initial template files | Recipe | Explicit file operations can apply to existing content |
| Configure a loop runner or task tracker | Ingredients and recipes for native configuration | Running tasks, updating tracker state and signaling completion belong to external tools |
| Launch an agent with network restrictions | Agent/runner controls and guidance | Cook can configure native settings |
| Choose tests for a code change | Person or agent, guided by repository instructions | Relevant checks run through the repository's tools |
| Watch production or investigate an alert | Agent or person using service tools and a skill | A recipe can configure monitoring through declared files; service tools contact the service, and Cook's batch holds file operations |
| Push, open a pull request, add a CI trigger commit or notify chat | Team publication process and external tools | Approval of file operations does not cover these external effects |
| Manage worktrees, detect merged branches or remove working copies | Version-control and working-copy tools | Those tools manage working copies |
| Explain permissions, branches or working-copy locations in `AGENTS.md`, with matching ignore rules in `.gitignore` | Native instruction prose or recipe-declared marked section | Readable conventions survive stopping Cook; describe the native settings and their limitations; use HTML comments for Markdown boundaries |
| Share organization skills | Team-selected installer, recipe or reviewed copies | Choose one maintenance process; native discovery determines loading |
| Record a lesson or open an issue | Agent or person with appropriate tools | Knowledge and tracker activity need judgment and their own review |
| Pin or upgrade the Cook engine | Explicit committed wrapper version/checksum change | Engine and cookbook versions are separate choices; see [wrapper](../features/wrapper.md) |
| Apply setup across repositories | Separate CLI invocations from each destination | Each has its own actual diff, approval, `cook.lock` and batch records; external tools select repositories and deliver changes |
| Reverse a recorded change | Revert | Reverses one batch's changes; removing a recipe's whole contribution (discard) is deferred |

## Paths, batch records and distribution

Each invocation works on one destination, its current working directory, so setup across repositories means one
invocation per repository ([destination and source origins](../architecture.md#destination-and-source-origins)). Batch
records stay local to that destination ([batch record](../architecture.md#one-record-for-the-batch)). The engine
version is pinned by the committed wrapper and moves only by an explicit upgrade, separately from cookbook and recipe
versions ([wrapper](../features/wrapper.md)).

People or external automation choose the repositories and invoke Cook from each intended directory; keep that selection
explicit, because an unrelated clone on a machine is not an instruction to configure it. Use the recipes and arguments
that fit each repository, review a representative change before repeating it elsewhere, and review each destination's
actual diff, for example:

```sh
cd /work/repo-a && ./cook apply ./recipe.groovy
cd /work/repo-b && ./cook apply ./recipe.groovy
```

Approval covers a whole batch in one destination, and revert acts on that destination only. Rollback on write failure
covers the batch in that destination and provides no transaction across repositories. Checking repositories, tracking
rollout results, running agents, committing, pushing and opening pull requests belong to the maintainer's workflow and
external tools.

## Anti-patterns

- Putting a repeated external workflow in the engine solely because it is deterministic: repetition does not change
  the engine's file-operation responsibilities.
- Treating an ingredient as a controller of program activity: it models native configuration, while the consuming
  program controls later activity.
- Treating a recipe as a workspace, verification or delivery pipeline: those activities belong to external tools and
  people.
- Letting ownership block changes to existing content: `cook.lock` records ownership, and the agreed contract is
  current-content evaluation and actual batch-diff approval.
- Declaring a whole shared file when only one key or section is intended: the broader scope can remove unrelated
  content and must be visible in review.
- Assuming markers or a successful skill-file write enforce instructions: boundaries and discoverability do not prove
  that an agent loaded or followed guidance.
- Treating batch approval as authorization for arbitrary recipe effects or publication: trusted evaluation and external
  actions have distinct consequences outside the declared batch.
- Omitting a recipe to delete its previous output: its output is removed only by applying a new version of that
  recipe that no longer declares it, or by reverting a batch ([apply](../features/apply.md)).
- Refreshing recipes or skills automatically when the engine pin moves: engine and cookbook sources are separate
  version choices.
- Committing credentials in native configuration, skill examples or helper scripts: shared files expose them to
  everyone with repository access; use the program's credential mechanism and review captured sources and batch
  records for sensitive content ([credential guidance](configuration-standards.md#no-secrets)).
