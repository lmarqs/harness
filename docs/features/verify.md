# Repository checks and configuration

## Objective

**Problem.** Contributors need to know which checks exercise a change and how to run them. Instructions that only say
"tests passed" leave reviewers unable to assess what was checked.

**Goal.** Keep useful check instructions alongside the repository's native setup, with execution and results handled
by the team's tools.

## Native check instructions

Cook [recipes](glossary.md) can declare files that describe a repository's lint, build, and test commands: for
example, task-runner configuration, CI files, or a section of contributor instructions. Ingredients model the native
formats those programs consume. Cook proposes the file changes through the ordinary
[batch diff and approval flow](apply.md); the task runner, CI service, contributor, or agent runs the commands
independently.

Useful instructions name the commands and explain which changes they exercise. When reporting a result, say which
checks ran and which could not run, with reasons a reviewer can assess. A passing unrelated test does not establish
that the changed behavior works; a runner that succeeds with zero matching tests needs attention in that runner's
configuration.

Sections in shared Markdown instruction files use `cook:begin <recipe>#<region>` and `cook:end <recipe>#<region>`
markers written as HTML comments; see [sections and markers](apply.md#sections-and-markers) and
[native configuration](../adrs/0008-native-configuration.md). For illustration, with commands a repository might
choose:

```markdown
<!-- cook:begin agentic/context#commands -->
Before review, run `make lint`, `make build` and `make test`, and report which of them ran.
<!-- cook:end agentic/context#commands -->
```

## Engine boundary

The [engine](../architecture.md) evaluates recipes and reviews and writes declared file operations. Configured checks
remain usable without Cook.

A [batch record](apply.md#batch-record) holds Cook's file patches; its result or failure log is not proof that a
project's checks passed.
