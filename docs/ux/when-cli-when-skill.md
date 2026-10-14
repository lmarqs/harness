# Where new behaviour goes

## Objective

**Problem.** Contributors need to decide whether a capability belongs in the engine, a program model, a reusable
setup recipe or guidance. A broad deterministic-versus-judgement rule can put external workflows into Cook merely
because they are repeatable, or turn program-specific configuration into engine policy.

**Goal.** Capabilities follow Cook's agreed responsibilities, with reusable program knowledge and setup in cookbooks,
judgement in guidance, and external work performed by the people and programs responsible for it.

## Overview

This page follows the [architecture](../architecture.md). Cook evaluates trusted Groovy recipes into ordered file
operations, presents the combined terminal diff, obtains whole-batch approval, records recovery information and writes
the declared targets. The CLI is the public cooking interface; Gradle builds and packages Cook and cookbooks.

A capability may have several parts. Place each by responsibility and effect, rather than assuming every deterministic
part belongs in the binary. These rules do not select ingredient/recipe APIs, station roles, nesting, injection,
annotations or hooks.

## Destinations

| Destination | Responsibility | Maintainer |
| --- | --- | --- |
| Cook engine and CLI | Evaluation, ordered composition, diff presentation, approval, destination boundaries, recovery, writes and recorded-run reversal | Cook contributors |
| Ingredient | Model a program's native files and configuration, including settings, ignore files or skills | Authors of the cookbook that holds it |
| Recipe | Describe reusable requested file operations using program models and trusted Groovy code | Cookbook or repository authors |
| Cookbook repository | Hold and ship ingredients and recipes; build them separately where appropriate | Cookbook authors |
| Native repository files | Hold settings, instructions and conventions consumed by the relevant programs | Repository contributors, with their chosen maintenance process |
| Skill or focused prose | Guide source selection, configuration choices, review and other work requiring judgement | Shared, organization, personal or repository authors |
| External tool or person | Execute tasks, run agents, manage working copies, publish work, contact services or update trackers | The team and those tools |

There is no floor-rule category, mandatory neutral profile or lifecycle-action contract. Ingredients remain program
data models. A recipe can configure native enforcement features, but the consuming program enforces them; Cook does
not supervise later program activity. See [program permissions](../features/policy.md).

## Criteria

Use these questions together, because repetition alone does not establish an engine responsibility:

| Question | Placement consequence |
| --- | --- |
| Does it change Cook's evaluation, review or execution of declared file operations? | Consider an engine capability within the architecture |
| Does it describe a program's native files or configuration? | Keep that knowledge in an ingredient and cookbook |
| Does it select reusable file additions, updates or deletions for a setup? | Express the request through a recipe |
| Does it require fitting conventions to one repository, weighing options or writing guidance? | Use a person or agent guided by a skill or prose |
| Does it execute a command, launch a session, publish work or contact a service? | Keep the activity with external tools and people; it is not a batch action |
| Is it a native setting whose later effects include execution or network access? | It can be a declared file change; review the program's interpretation and consequences |
| Is the choice shared or personal, and which existing content should remain? | Choose declared targets and maintenance sources deliberately |
| Does the proposal depend on a deferred model or hook API? | Leave that API for the separate discussion rather than defining it here |

The distinction between writing configuration and executing its workflow matters. A recipe may write a CI file, a
task-runner command or an agent hook; Cook reviews that file operation. The consuming program later runs it under its
own working directory, credentials, permissions and failure rules. See [native program hooks](../features/hooks.md).

## Engine commands and recipe arguments

The intended command set is implicit or explicit apply, discard and revert. Apply proposes additions and updates;
discard deletes the supplied recipes' declared contributions; revert uses a recorded run's history to restore prior
values without evaluating recipes again.

A new engine command or flag requires its own decision when it changes that scope. Do not add a command for each
program or organization, or invent a setting that bypasses the agreed review and recovery guarantees. Per-recipe
choices use arguments following that recipe as `--name=value`; this grammar does not settle parameter injection.

```sh
./cook ./recipes/project.groovy
./cook apply ./recipes/base.groovy --agent=codex ./recipes/project.groovy --agent=claude
./cook discard ./recipes/project.groovy
./cook revert RUN_ID
```

Recipe operands are exact paths or supported URIs, including ordinary relative paths, `file://` and `https://`.
There is no implicit cookbook-name lookup. Initial bare operation names select commands; use `./revert` for a file
named `revert`. An argument value such as `--source=./b.groovy` is not a second recipe operand.

Batch options precede operands, including the run ID for revert. Without preapproval, Cook asks before target writes.
`--batch-approve` displays the final diff and proceeds without prompting. Noninteractive execution alone does not
authorize writes; approval covers all files and hunks in the batch. A misplaced batch option after a recipe receives
the normal unknown-option error. See [CLI grammar](../features/cli.md).

These examples describe the intended design. Consult the [root README](../../README.md) and cookbook usage guides for
current commands and prerequisites; do not infer implementation from a design example.

## Recipes

Use a recipe to reuse requested native setup, whether the invocation is a first application or an update. Recipes and
their operations compose left to right, and later instructions may replace earlier values. Cook shows one final
combined diff rather than approving intermediate results.

For example, A sets `test` to `A` and creates `notes.txt`; B sets `test` to `B`. Applying A then B previews `B` and the
new file. Applying B alone after A was applied earlier changes the key and leaves `notes.txt` intact. Omission of A
does not withdraw its unrelated output.

Every apply/discard invocation evaluates the supplied recipe versions against current content, even after an identical
earlier invocation. An unchanged result may have an empty diff. There is no already-applied recipe identity, ownership
database, adoption step or manual-edit veto. The target eliminates `--adopt` and adoption aliases.

Discard uses the supplied recipe version, which users manage. If an apply changed `test` from `old` to `new`, then a
person changed it to `custom`, discarding a recipe that declares that key proposes deleting `test`. Reverting the
original run instead proposes `custom` to `old` using its history. Both preserve unrelated keys and present the actual
diff for whole-batch approval. See [discard and revert](../features/remove.md).

A recipe is trusted executable code, not a fixed development lifecycle with run/template/attempt step kinds, handoffs
or verification lanes. External command execution is not a batch action. Arbitrary recipe side effects remain outside
diff approval, output boundaries and rollback; this standard does not specify a sandbox or recipe hook protocol.

## Guidance and native configuration

Use a skill to explain choices and review decisions, and a recipe when reusable file operations express the selected
setup. For instance, an organization skill can explain why certain native permissions apply, while ingredients model
the agent's format and a recipe supplies the requested settings. The actual settings and diff remain reviewable.

For repository-authored guidance or settings, direct edits are also legitimate. For recipe-maintained content,
updating the source first avoids a later apply replacing a local correction. Neither process establishes permanent
ownership or prohibits deliberate later changes. See [skills standards](skills-standards.md).

Skill discovery and session loading belong to the native program. Cook can supply instructions, links and settings as
file operations, but it defines no standing-context triggers, role budgets, static injection or worker-hook delivery.
Keep frequently read guidance short and link relevant detail; cross-references may run in either direction.

Build, test and publication procedures belong in existing task runners, CI and guidance. Cook can configure them but
does not execute their lifecycle or prove that their checks passed. A shared skill should call supported commands
rather than imitate their implementation; its evidence should fit the task rather than require a Cook receipt.

## Worked examples

| Request | Destination | Reason and review boundary |
| --- | --- | --- |
| Combine two recipes that set the same key | Engine ordered composition | Later values replace earlier ones; preview the final combined result |
| Model Git ignore entries or Codex settings and skills | Ingredient in a cookbook | Knowledge follows the native program's files and formats |
| Apply shared agent settings | Recipe using program models | Review native permission meaning and preserve unrelated keys |
| Restrict force pushes or task chaining | Native program controls and team guidance | Cook may configure supported controls; it does not enforce later command activity or introduce safety floors |
| Allow one repository's observability search tools | Native settings chosen with organization guidance | Fit the scope to the repository; file approval is not permission to contact the service |
| Give a Node project lint/build/test commands and an edit hook | Recipe-declared task-runner and native hook files | Programs execute the resulting configuration independently |
| Add a migration or framework module | Existing generator and contributor skill | Generator execution and implementation judgement are outside the file-operation engine |
| Write reusable initial template files | Recipe | Explicit file operations can apply to existing content; there is no mandatory write-once station rule |
| Configure a loop runner or task tracker | Ingredients and recipes for native configuration | Running tasks, updating tracker state and signalling completion belong to external tools |
| Launch an agent with network restrictions | Agent/runner controls and guidance | Cook can configure native settings; no worker/jail contract is defined here |
| Choose tests for a code change | Person or agent, guided by repository instructions | Relevant checks run through the repository's tools, without engine lanes or receipts |
| Watch production or investigate an alert | Agent or person using service tools and a skill | Cook does not contact services as a batch action |
| Push, open a pull request, add a CI trigger commit or notify chat | Team publication process and external tools | Approval of file operations does not cover these external effects |
| Manage worktrees, detect merged branches or remove working copies | Version-control and working-copy tools | These activities are not engine lifecycle responsibilities |
| Explain permissions, branches or working-copy locations in `AGENTS.md` | Native instruction prose or recipe-declared marked section | Readable conventions survive stopping Cook; use HTML comments for Markdown boundaries |
| Share organization skills | Team-selected installer, recipe or reviewed copies | Choose one maintenance process; native discovery determines loading |
| Record a lesson or open an issue | Agent or person with appropriate tools | Knowledge and tracker activity need judgement and their own review |
| Pin or upgrade the Cook engine | Explicit committed wrapper version/checksum change | The jpackage package includes Java; first-use verified download/cache is separate from cookbook versions |
| Apply setup across repositories | Separate CLI invocations from each destination | Each has its own actual diff, approval and local history; no fleet transaction is implied |
| Delete declared setup or reverse a recorded change | Discard or revert respectively | Supplied declarations and recorded prior values answer different requests |

## Paths, history and distribution

The current working directory is the destination, regardless of recipe or executable location. Declared writes and
deletions stay within it, including paths resolved through symlinks. Source recipes and templates resolve relative to
the referencing recipe's original location or HTTPS URL; saving snapshots must not change source resolution.

Before the first target write, save recovery patches. Destination-local ignored `.cook/` history captures one
reversible patch per touched file and a separate run record for the batch, evaluated and consumed local/downloaded
sources, origins, arguments, order, operation and result or failure log. Write failure rolls back batch changes already
made and retains patches and failure records. Apply/discard need no prior history; revert requires the selected run's
history. No patch format, metadata schema or overlapping-reversal mechanism is selected here.

Engine delivery uses complete platform-specific jpackage packages containing Java. The committed wrapper design pins
an engine version and package checksums, verifies the first-use download and caches it locally. Upgrades are explicit;
ordinary invocation does not update recipes or choose a newer release. Cookbook repositories and builds remain
separate. See [wrapper](../features/wrapper.md) for the target and current entry path; these requirements do not prove
that published packages or pinned downloads are available.

## Anti-patterns

- Putting a repeated external workflow in the engine solely because it is deterministic: repetition does not change
  the engine's file-operation responsibilities.
- Treating an ingredient as a launch contract, lifecycle controller or nonoptional safety floor: it models native
  configuration, while the consuming program controls later activity.
- Treating a recipe as a workspace, attempt, verification or delivery pipeline: those activities belong to external
  tools and people, with future session orchestration left separate.
- Requiring ownership manifests, adoption gates or drift vetoes before changing existing content: the agreed contract
  is current-content evaluation and actual batch-diff approval.
- Declaring a whole shared file when only one key or section is intended: the broader scope can remove unrelated
  content and must be visible in review.
- Assuming markers or a successful skill-file write enforce instructions: boundaries and discoverability do not prove
  that an agent loaded or followed guidance.
- Treating batch approval as authorization for arbitrary recipe effects or publication: trusted evaluation and external
  actions have distinct consequences outside the declared batch.
- Using history to skip a recipe as already applied, or omitting a recipe to delete its previous output: each supplied
  apply/discard invocation evaluates current content, and deletion is explicit.
- Refreshing recipes or skills automatically when the engine pin moves: engine and cookbook sources are separate
  version choices.
- Turning exploratory model or hook sketches into an API here: those choices require the separate model discussion.
- Committing credentials in native configuration, skill examples or helper scripts: shared files expose them to
  everyone with repository access; use the program's credential mechanism and review captured sources and history
  for sensitive content ([credential guidance](configuration-standards.md#no-secrets)).

Personal setup backup (kit) and agent-session orchestration remain future capabilities in
[issue #5](https://github.com/lmarqs/the-cook/issues/5) and
[issue #6](https://github.com/lmarqs/the-cook/issues/6). This standard does not design their commands or protocols.
