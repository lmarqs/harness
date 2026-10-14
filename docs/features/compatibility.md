# Compatibility and coexistence

## Objective

**Problem.** A repository already holds settings, instructions, task-runner files, tracker data and CI pipelines that
people depend on. Contributors need to understand what a recipe changes before approving it, and what remains usable
when they stop invoking Cook.

**Goal.** Review each declared change in its native context, preserve unrelated content, and keep the configured
programs independent of Cook's execution.

## Overview

This page follows the [intended architecture](../architecture.md). Cook evaluates trusted Groovy recipes into ordered
file operations, shows the combined terminal diff, obtains whole-batch approval, saves recovery information and writes
the declared targets. Ingredients model programs' native files and configuration; cookbooks hold and ship those models
and recipes through their repositories, with builds that can be separate from Cook's build.

These requirements do not establish implementation completion. For current execution, see the
[root README](../../README.md) and cookbook usage guides.

Coexistence depends on the targets a recipe declares. A whole-file declaration affects the whole file; a key or marked
section limits the contribution within a shared file. Existing and manually edited content uses the same diff and
approval flow, without `--adopt`, an adoption alias or an ownership-state prerequisite. Applying one recipe does not
implicitly remove unrelated output from another. See [apply](render.md) and [repository setup](scaffold.md).

Executing external commands is not a batch action. Trusted recipe code can still cause arbitrary side effects during
evaluation: diff review, destination boundaries and rollback cover declared file operations, not those effects. Read
and trust recipe sources before evaluation; do not infer a sandbox from the boundaries described below.

## At a glance

| Already in the destination | How it coexists with declared operations |
| --- | --- |
| Hosting-platform issues, pull requests and boards | File operations do not update remote records or deliver work |
| Task-tracker data | Unrelated data stays in place; review any recipe that explicitly targets it |
| Loop-runner configuration | Recipes can declare native configuration; the runner owns its execution and task selection |
| Task-runner and CI files | Recipes can declare file changes; the task runner or CI service executes them independently |
| Shared skills and installer lock files | Choose how to maintain them and avoid conflicting updates from recipes and installers |
| Agent settings, hooks and instructions | Review the declared file, key or section and its meaning to the consuming program |
| Version-control hooks | Only explicit file declarations change them; the version-control program runs them |
| `.gitignore` and shared instruction files | Marked sections can coexist with unrelated lines and sections |
| Local or personal settings | Make shared-versus-personal choices deliberately; Cook does not define an import protocol |
| `.cook/` | Ignored destination-local operation history, not a committed ownership database or repository profile |

## Hosting platforms

Cook's batch contract covers local file operations. It does not push branches, open pull requests, update issues or
report task completion. People, external tools and CI own those activities. Recipes can declare CI or integration
configuration files, but writing those files does not run the configured service. Arbitrary recipe side effects remain
outside this contract.

## Task trackers

Trackers own their databases, assignment rules, sync settings, remotes and commands. Cook can configure their native
files when a recipe declares them, without becoming the tracker or binding tasks to sessions. Unrelated tracker data is
preserved. A recipe that targets tracker data must be reviewed as a change to that data; there is no blanket guarantee
that every tracker file is excluded from recipes. See [task-tracker configuration](tasks.md).

Synchronised tracker data can expose assignments and comments to other machines. Choose local or shared data and
remote-sync permissions using the tracker's own controls; writing configuration does not isolate its later activity.

## Loop runners

A recipe can declare a loop runner's native configuration. Starting it, selecting tasks, choosing models and parallelism,
creating working copies and signalling completion belong to the runner and whoever drives it. Cook supplies no attempt
contract or lifecycle command for that runner to call. Existing runner configuration can be changed through declared
operations and the ordinary diff/approval flow; it is not automatically kept merely because it exists. See
[workflow configuration and engine boundaries](lifecycle.md).

## Task runners

Reuse a repository's task runner to describe its preparation, build and check commands in native configuration or
contributor instructions. Recipes can target task-runner files, but executing a task is not a batch action. Gradle
builds and packages Cook and cookbooks; the CLI is the public cooking interface. See
[repository configuration choices](profile.md).

An engine pin and cookbook or recipe versions are separate choices. Avoid competing engine pins in a task runner and
wrapper that cause different entry points to run different versions. See [the wrapper's one pin](wrapper.md#one-pin).

## Skills installers

Skills can be maintained through a recipe, an installer or ordinary repository edits. Choose a maintenance process so
one source does not unexpectedly overwrite another's changes. Where an installer records a lock file, review that file
alongside the content it describes. Cook does not require an installer or automatically run one as a batch action.

Discovery depends on the consuming program's locations, metadata, links and trust settings. A real skills directory
and a whole-directory symlink are different targets; review the existing shape before declaring changes. There is no
mandatory shipped Cook skill or session-injection contract. See [skills and program configuration](skills.md).

## Native program files

Ingredients describe the native settings, rules, hooks, instructions and skills a program consumes. A recipe may target
specific keys or marked sections to preserve unrelated configuration instead of declaring an entire shared file.
Review the actual diff and permission meaning, including other settings scopes and program defaults; Cook does not
promise universal rule translation or enforcement. See [program permissions](policy.md).

Local, user and managed settings may change how project settings behave. Decide which settings belong in shared files
and which remain personal; no mandatory import or ownership gate governs existing content. Native hooks run under the
program that consumes them, with that program's working directory, credentials and failure behaviour. See
[native program hooks](hooks.md).

## Version control

Cook does not create branches, manage worktrees, detect landed work or enforce delivery guards. Recipes may declare
native version-control configuration, ignore rules or hook files; the version-control program interprets them. Creating
or removing working copies and preserving uncommitted work belong to people and external tools. See
[workspace conventions and configuration](workspaces.md).

## `.gitignore`

Keep destination-local `.cook/` history ignored. Other ignore entries are repository choices that recipes can declare.
A marked section bounds a contribution without taking ownership of the whole ignore file:

```gitignore
# cook:local-history
/.cook/
# /cook:local-history
```

Review overlapping ignore rules and tracked files using the version-control program's own semantics. Removing one
section does not establish that a path becomes tracked or that another rule no longer ignores it. Markers identify
boundaries; they do not create an ownership database or protect a section from a later explicit change.

## Other tools in `AGENTS.md`

Separate marked sections can coexist with other tools' instructions and hand-written text. Boundaries use markers,
rather than headings, and Markdown uses HTML comments:

```markdown
<!-- cook:project-guidance -->
Use the repository's documented build and check commands.
<!-- /cook:project-guidance -->
```

Text outside that section remains unrelated to its declaration. Review duplicated or contradictory instructions even
when the sections do not overlap. Programs decide which instructions they load and follow; see
[repository guidance and context](context.md).

## Monorepos

The invocation's current working directory is the destination, not a repository root discovered by walking upward.
Run from the root when root files are intended, or from a subdirectory when that directory is the destination. Declared
writes and deletions must stay within it, including resolution through symlinks. Recipe and template source references
resolve from the referencing recipe's original location. See [destination and source origins](../architecture.md#destination-and-source-origins).

Whether an agent started in a subdirectory reads root settings is that program's behaviour. Check its configuration
lookup and trust rules; Cook does not provide session inheritance. See [pitfalls](pitfalls.md#program-configuration).

## CI/CD

Programs consume native files without invoking Cook merely to read them. Recipes can change build or CI behaviour when
they explicitly declare those files, so inspect their effects as part of review. Cook adds no mandatory CI step.

A job that invokes Cook uses the same batch diff and approval contract: `--batch-approve` displays the diff and proceeds
without prompting; noninteractive execution without it does not authorize writes. Sources and their dependencies must
be available to the job. See [CLI approval](cli.md#batch-options-and-approval) and [wrapper use in CI](wrapper.md#ci).

## Versions

The wrapper design pins an engine version and matching platform-package checksums, obtains the complete jpackage
package with Java on first use, and caches it locally. Upgrades are explicit reviewed pin changes. This does not manage
cookbook or recipe versions, automatically reapply recipes, or guarantee identical output from arbitrary executable
sources. See [wrapper](wrapper.md) for the target and the current development launcher.

### Supported program versions

Consult the cookbook's guidance for the programs, native formats and versions its ingredients cover. Do not infer a
universal support matrix or a runtime version-check command from the ingredient concept. Review configuration when
upgrading a consuming program as well as when changing cookbook sources.

## After removal

Stopping Cook invocations leaves written native files available to their programs. There is no eject protocol or
automatic repository-wide restoration. To delete declared contributions, discard the supplied recipe version; to
restore a recorded operation's prior values, revert that run using its history. Both present the actual diff for
whole-batch approval, preserve unrelated content, and can change manually edited targets. See
[discard and revert](remove.md).

Losing `.cook/` loses local recovery information, not the ability to apply or discard recipes. Reverting a particular
run requires its history. File reversal does not undo external activity caused by configured programs or arbitrary
recipe code.

Personal setup backup (kit) and agent-session orchestration remain future work in
[issue #5](https://github.com/lmarqs/the-cook/issues/5) and
[issue #6](https://github.com/lmarqs/the-cook/issues/6). Ingredient/recipe APIs, station roles and hooks require their
separate model discussion.
