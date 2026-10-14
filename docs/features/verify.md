# Repository checks and configuration

## Objective

**Problem.** Contributors need to know which checks exercise a change and how to run them. Instructions that only say
"tests passed" leave reviewers unable to assess what was checked.

**Goal.** Keep useful check instructions alongside the repository's native setup, with execution and results handled
by the team's tools.

## Native check instructions

Cook recipes can declare files that describe a repository's lint, build, and test commands: for example, task-runner
configuration, CI files, or a section of contributor instructions. Ingredients model the native formats those programs
consume. Cook proposes the file changes through the ordinary [batch diff and approval flow](render.md); the task
runner, CI service, contributor, or agent runs the commands independently.

Useful instructions name the commands and explain which changes they exercise. When reporting a result, say which
checks ran and which could not run, with reasons a reviewer can assess. A passing unrelated test does not establish
that the changed behavior works; a runner that succeeds with zero matching tests needs attention in that runner's
configuration. These are workflow choices.

Sections in shared Markdown instruction files use `cook:begin <recipe>/<region>` and `cook:end <recipe>/<region>`
markers written as HTML comments. The markers bound the declared text and name its owning recipe; ownership never
blocks a change. Targeted edits preserve unrelated content; broad replacements show their
full scope in the final diff. See [native configuration](../adrs/0008-native-configuration.md).

## Engine boundary

The [engine](../architecture.md) evaluates recipes and reviews and writes declared file operations. Configured checks
remain usable without Cook.

Batch records in the ignored `.cook/batches/<batchId>/` directory hold Cook's file patches and their recovery
information. Their result or failure log is not proof that a project's checks passed. Reverting a batch applies its
reverse patches. See [revert](remove.md).
