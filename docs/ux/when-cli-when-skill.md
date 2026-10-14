# Where new behavior goes

## Objective

**Problem.** Contributors need to decide whether a capability belongs in the engine, a program model, a reusable
setup recipe or guidance. A broad deterministic-versus-judgement rule can put external workflows into Cook merely
because they are repeatable, or turn program-specific configuration into engine policy.

**Goal.** Capabilities follow Cook's agreed responsibilities, with reusable program knowledge and setup in cookbooks,
judgement in guidance, and external work performed by the people and programs responsible for it.

## Overview

This page follows the [architecture](../architecture.md). Cook evaluates trusted Groovy recipes into ordered file
operations, presents the combined terminal diff, obtains whole-batch approval, records the batch and ownership and
writes the declared targets. The CLI is the public cooking interface; Gradle builds and packages Cook and cookbooks.

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
| Skill or focused prose | Guide source selection, configuration choices, review and other work requiring judgement | Shared, organization, personal or repository authors |
| External tool or person | Execute tasks, run agents, manage working copies, publish work, contact services or update trackers | The team and those tools |

Ingredients are program data models. A recipe can configure native enforcement features, and the consuming program
enforces them. See [program permissions](../features/policy.md).

## Criteria

Use these questions together, because repetition alone does not establish an engine responsibility:

| Question | Placement consequence |
| --- | --- |
| Does it change Cook's evaluation, review or execution of declared file operations? | Consider an engine capability within the architecture |
| Does it describe a program's native files or configuration? | Keep that knowledge in an ingredient and cookbook |
| Does it select reusable file additions, updates or deletions for a setup? | Express the request through a recipe |
| Does it require fitting conventions to one repository, weighing options or writing guidance? | Use a person or agent guided by a skill or prose |
| Does it execute a command, launch a session, publish work or contact a service? | Keep the activity with external tools and people |
| Is it a native setting whose later effects include execution or network access? | It can be a declared file change; review the program's interpretation and consequences |
| Is the choice shared or personal, and which existing content should remain? | Choose declared targets and maintenance sources deliberately |
| Does the proposal depend on a deferred model or hook API? | Leave that API for the separate discussion rather than defining it here |

Writing configuration and executing its workflow are separate steps. A recipe may write a CI file, a
task-runner command or an agent hook; Cook reviews that file operation. The consuming program later runs it under its
own working directory, credentials, permissions and failure rules. See [native program hooks](../features/hooks.md).

## Engine commands and recipe arguments

The intended command set is implicit or explicit apply, revert, `batch list` and `batch inspect`. Apply proposes
additions and updates, together with removal of what each recipe's previous version owned and no longer declares;
revert applies a recorded batch's reverse patches without evaluating recipes again; `batch list` and `batch inspect`
read batch records. Discard is deferred.

A new engine command or flag requires its own decision when it changes that scope. Do not add a command for each program
or organization, or invent a setting that bypasses the agreed review and recovery guarantees. Per-recipe choices use
arguments following that recipe as `--name=value`.

```sh
./cook ./recipes/project.groovy
./cook apply ./recipes/base.groovy --agent=codex ./recipes/project.groovy --agent=claude
./cook batch list
./cook revert 2026-10-14T15-04-05Z
```

Recipe operands are paths inside the destination repository, including ordinary relative paths, or `https://` URLs.
Initial bare command names select commands; use `./revert` for a file named `revert`. An argument value such as
`--source=./b.groovy` remains an argument value.

Batch options precede operands, including the batch ID for revert. Without preapproval, Cook asks before target writes.
`--batch-approve` displays the final diff and proceeds without prompting. Noninteractive execution alone does not
authorize writes; the CLI accepts the whole batch. A misplaced batch option after a recipe receives
the normal unknown-option error. See [CLI grammar](../features/cli.md).

These examples describe the intended design. Consult the [root README](../../README.md) and recipe READMEs for current
commands and prerequisites.

## Recipes

Use a recipe to reuse requested native setup, whether the invocation is a first application or an update. Recipes and
their operations compose left to right; when two recipes write the same target, the last one wins and becomes its owner,
and the diff is the only notice. Cook shows one final combined diff.

For example, A sets `test` to `A` and creates `notes.txt`; B sets `test` to `B`. Applying A then B previews `B` and the
new file. Applying B alone after A was applied earlier changes the key, moves its ownership to B and leaves `notes.txt`
intact: applying B never removes A's output.

Every apply invocation evaluates the supplied recipe versions against current content, even after an identical earlier
invocation. An unchanged result may have an empty diff. The committed `cook.lock` records ownership, but ownership never
blocks a change, including a change to content a developer edited.

Revert reverses only one recorded batch's changes. If a batch changed `test` from `old` to `new`, then a person changed
an unrelated `other` key, reverting that batch proposes `new` to `old` and keeps `other`; had the person changed
`test`, the revert would write nothing and list the conflict. Discarding a recipe's whole contribution is deferred.
See [revert](../features/revert.md).

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
| Add a migration or framework module | Existing generator and contributor skill | Generator execution and implementation judgement belong to those tools and people |
| Write reusable initial template files | Recipe | Explicit file operations can apply to existing content |
| Configure a loop runner or task tracker | Ingredients and recipes for native configuration | Running tasks, updating tracker state and signaling completion belong to external tools |
| Launch an agent with network restrictions | Agent/runner controls and guidance | Cook can configure native settings |
| Choose tests for a code change | Person or agent, guided by repository instructions | Relevant checks run through the repository's tools |
| Watch production or investigate an alert | Agent or person using service tools and a skill | Service tools contact the service; Cook's batch holds file operations |
| Push, open a pull request, add a CI trigger commit or notify chat | Team publication process and external tools | Approval of file operations does not cover these external effects |
| Manage worktrees, detect merged branches or remove working copies | Version-control and working-copy tools | Those tools manage working copies |
| Explain permissions, branches or working-copy locations in `AGENTS.md` | Native instruction prose or recipe-declared marked section | Readable conventions survive stopping Cook; use HTML comments for Markdown boundaries |
| Share organization skills | Team-selected installer, recipe or reviewed copies | Choose one maintenance process; native discovery determines loading |
| Record a lesson or open an issue | Agent or person with appropriate tools | Knowledge and tracker activity need judgement and their own review |
| Pin or upgrade the Cook engine | Explicit committed wrapper version/checksum change | The jpackage package includes Java; first-use verified download/cache is separate from cookbook versions |
| Apply setup across repositories | Separate CLI invocations from each destination | Each has its own actual diff, approval, `cook.lock` and batch records |
| Reverse a recorded change | Revert | Reverses one batch's changes; removing a recipe's whole contribution (discard) is deferred |

## Paths, history and distribution

The current working directory is the destination, regardless of recipe or executable location. Declared writes and
deletions stay within it, including paths resolved through symlinks. Recipes come from paths inside the destination
repository or `https://` URLs. Source recipes and templates resolve relative to the referencing recipe's location or
HTTPS URL; saving snapshots must not change source resolution, and a local reference outside the repository is refused.

Before the first target write, save the batch record. The ignored `.cook/batches/<batchId>/` directory, named by the
batch's UTC start time, captures one reversible patch per touched file (`cook.lock` included), evaluated and consumed
local/downloaded sources, origins, arguments, order and result or failure log. Write failure rolls back batch changes
already made and retains the record and failure. Apply needs neither batch history nor `cook.lock`; revert requires the
selected batch's record. The patch format and metadata schema belong to the implementation.

Engine delivery uses complete platform-specific jpackage packages containing Java. The committed wrapper design pins an
engine version and package checksums, verifies the first-use download and caches it locally. Upgrades are explicit;
ordinary invocation does not update recipes or choose a newer release. Cookbook repositories and builds remain separate.
See [wrapper](../features/wrapper.md) for the target and current entry path.

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
- Using history to skip a recipe as already applied, or omitting a recipe to delete its previous output: each apply
  invocation evaluates current content, and a recipe's output is removed only by applying a new version of that recipe
  that no longer declares it, or by reverting a batch.
- Refreshing recipes or skills automatically when the engine pin moves: engine and cookbook sources are separate
  version choices.
- Turning exploratory model or hook sketches into an API here: those choices require the separate model discussion.
- Committing credentials in native configuration, skill examples or helper scripts: shared files expose them to
  everyone with repository access; use the program's credential mechanism and review captured sources and history
  for sensitive content ([credential guidance](configuration-standards.md#no-secrets)).
