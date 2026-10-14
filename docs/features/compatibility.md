# Compatibility and coexistence

## Objective

**Problem.** A repository already holds settings, instructions, task-runner files, tracker data and CI pipelines that
people depend on. Contributors need to understand what a recipe changes before approving it, and what remains usable
when they stop invoking Cook.

**Goal.** Review each declared change in its native context, preserve unrelated content, and keep the configured
programs independent of Cook's execution.

## Overview

This page follows the [intended architecture](../architecture.md); the [glossary](glossary.md) defines its terms.
Cook evaluates trusted Groovy recipes into ordered file operations, shows the combined terminal diff, obtains
whole-batch approval, saves the batch record and writes the declared targets. Ingredients model programs' native files
and configuration; cookbooks hold and ship those models and recipes through their repositories, with builds that can
be separate from Cook's build.

For current execution, see the [root README](../../README.md) and [core implementation guide](../../core/README.md).

Coexistence depends on the targets a recipe declares; see
[ordered composition and declared targets](../architecture.md#ordered-composition-and-declared-targets),
[apply](apply.md) and [repository setup](scaffold.md).

Executing external commands is not a batch action. Trusted recipe code can still cause arbitrary side effects during
evaluation: diff review, destination boundaries and rollback cover declared file operations, not those effects. Read
and trust recipe sources before evaluation; do not infer a sandbox from the boundaries described below.

## At a glance

| Already in the destination | How it coexists with declared operations |
| --- | --- |
| Hosting-platform issues, pull requests and boards | Batches cover local file operations; people, external tools and CI own these records and delivery |
| Task-tracker data | Unrelated data stays in place; review any recipe that explicitly targets it |
| Loop-runner configuration | Recipes can declare native configuration; the runner owns its execution and task selection |
| Task-runner and CI files | Recipes can declare file changes; the task runner or CI service executes them independently |
| Shared skills and installer lock files | A recipe and an installer that both update the same skill can overwrite each other's changes |
| Agent settings, hooks and instructions | Review the declared file, key or section and its meaning to the consuming program |
| Version-control hooks | Only explicit file declarations change them; the version-control program runs them |
| `.gitignore` and shared instruction files | Marked sections can coexist with unrelated lines and sections |
| Local or personal settings | Does this choice belong in shared files, or does it stay personal? |
| `.cook/` | Ignored destination-local batch records; ownership is recorded in the committed `cook.lock` |

## Hosting platforms

Cook's batch contract covers local file operations. People, external tools and CI push branches, open pull requests,
update issues and report task completion. Recipes can declare CI or integration configuration files, but writing those
files does not run the configured service. Arbitrary recipe side effects remain outside this contract.

## Task trackers

Trackers own their databases, assignment rules, sync settings, remotes and commands. Cook can configure their native
files when a recipe declares them. Unrelated tracker data is preserved. A recipe can target tracker data; review it as
a change to that data. See [task-tracker configuration](tasks.md).

Synchronized tracker data can expose assignments and comments to other machines. Choose local or shared data and
remote-sync permissions using the tracker's own controls; writing configuration does not isolate its later activity.

## Loop runners

A loop runner is a program that runs a coding agent over a series of tasks, such as Ralph TUI. A recipe can declare its
native configuration. Starting it, selecting tasks, choosing models and parallelism, creating working copies and
signaling completion belong to the runner and whoever drives it. Declared operations can change existing runner
configuration through the ordinary diff and approval flow; existing configuration has no special protection. See
[workflow configuration and engine boundaries](lifecycle.md).

## Task runners

Reuse a repository's task runner to describe its preparation, build and check commands in native configuration or
contributor instructions. Recipes can target task-runner files, but executing a task is not a batch action. See
[repository configuration choices](profile.md).

Avoid competing engine pins in a task runner and wrapper that cause different entry points to run different versions.
See [the wrapper's one pin](wrapper.md#one-pin).

## Skills installers

A skills installer is a program that copies shared skills into a repository. Skills can be maintained through a recipe,
an installer or ordinary repository edits. Choose a maintenance process so one source does not unexpectedly overwrite
another's changes. Where an installer records a lock file, review that file alongside the content it describes.

Discovery depends on the consuming program's locations, metadata, links and trust settings. A real skills directory
and a whole-directory symlink are different targets; review the existing shape before declaring changes. See
[skills and program configuration](skills.md).

## Native program files

Ingredients describe the native settings, rules, hooks, instructions and skills a program consumes. A recipe may target
specific keys or marked sections to preserve unrelated configuration instead of declaring an entire shared file.
Review the actual diff and permission meaning, including other settings scopes and program defaults. See
[program permissions](policy.md).

Local, user and managed settings may change how project settings behave. Decide which settings belong in shared files
and which remain personal. Native hooks run under the program that consumes them, with that program's working directory,
credentials and failure behavior. See [native program hooks](hooks.md).

## Version control

Recipes may declare native version-control configuration, ignore rules or hook files; the version-control program
interprets them. Creating branches, managing worktrees, creating or removing working copies, detecting landed work,
enforcing delivery guards and preserving uncommitted work belong to people and external tools. See
[workspace conventions and configuration](workspaces.md).

## `.gitignore`

Keep the destination-local `.cook/` batch records ignored. Other ignore entries are repository choices that recipes can
declare. A marked section bounds a contribution within the ignore file:

```gitignore
# cook:begin git#ignore
/.cook/
# cook:end git#ignore
```

Review overlapping ignore rules and tracked files using the version-control program's own semantics. Removing one
section does not establish that a path becomes tracked or that no other rule ignores it. Markers name the
owning recipe and region; ownership never protects a section from a later explicit change.

## Other tools in `AGENTS.md`

Separate marked sections can coexist with other tools' instructions and hand-written text. Boundaries use markers, and
Markdown uses HTML comments:

```markdown
<!-- cook:begin agentic/context#project-guidance -->
Use the repository's documented build and check commands.
<!-- cook:end agentic/context#project-guidance -->
```

Text outside that section remains unrelated to its declaration. Review duplicated or contradictory instructions even
when the sections do not overlap. Programs decide which instructions they load and follow; see
[repository guidance and context](context.md).

## Monorepos

The invocation's current working directory is the destination. Run from the root when root files are intended, or from a
subdirectory when that directory is the destination. See
[destination and source origins](../architecture.md#destination-and-source-origins).

Whether an agent started in a subdirectory reads root settings is that program's behavior. Check its configuration
lookup and trust rules. See [pitfalls](pitfalls.md#program-configuration).

## CI/CD

Programs consume native files directly. Recipes can change build or CI behavior when they explicitly declare those
files, so inspect their effects as part of review.

A job that invokes Cook uses the same batch diff and approval contract; noninteractive execution without
`--batch-approve` does not authorize writes. Sources and their dependencies must be available to the job. For example,
a job step might run:

```sh
./cook apply --batch-approve ./recipe.groovy
```

See [CLI approval](cli.md#batch-options-and-approval) and [wrapper use in CI](wrapper.md#ci).

## Versions

The [wrapper](wrapper.md) pins the engine version; upgrades are explicit reviewed pin changes. Users supply the
recipe version they intend to apply; `cook.lock` records which version produced the current ownership.

### Supported program versions

Consult the cookbook's guidance for the programs, native formats and versions its ingredients cover. Review
configuration when upgrading a consuming program as well as when changing cookbook sources.

## After removal

Stopping Cook invocations leaves written native files available to their programs. Revert a recorded batch with
`./cook revert <batchId>`; it writes nothing when a later edit overlaps the batch's changes. Discarding a recipe's
sections and keys is deferred; remove them by hand or revert the batches that applied the recipe. See
[revert](revert.md).

Losing `.cook/` loses the local batch records, not the ability to apply recipes; ownership survives in the committed
`cook.lock`. Reverting a particular batch requires its record. File reversal does not undo external activity caused by
configured programs or arbitrary recipe code.
