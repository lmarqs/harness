# Ingredients

## Objective

**Problem.** Programs use different native files, formats and configuration concepts. Putting this knowledge into
Cook's engine would couple its file-operation responsibilities to every program's settings and releases.

**Goal.** Cookbook authors can maintain program knowledge as data models, while Cook evaluates recipes and handles
the resulting file operations.

## Overview

An ingredient is a **program data model**. It models the files and configuration a program natively consumes, such as
Git's ignore file or Codex's configuration and skills. [Cook architecture](../architecture.md) establishes this
responsibility.

[Recipes](recipes.md) use program models to describe requested file changes in trusted executable Groovy code.
[Cookbook repositories](cookbooks.md#cookbooks) hold and ship ingredients and recipes; their builds can be separate
from Cook's build. The engine evaluates recipes into an ordered batch, shows its diff, obtains approval, records
recovery information and performs writes.

For current implementation behavior, see the [core implementation guide](../../core/README.md) and cookbook usage
guides.

## Program knowledge and setup choices

The useful distinction is between a program's configuration concepts and the choices a team makes using them.

| Program knowledge an ingredient models | Setup choice a recipe can describe |
| --- | --- |
| Git ignore-file content | Ignore this repository's local build output or working-copy directory |
| An agent's native configuration | Write the settings selected for this repository |
| A program's native instructions and skill locations | Supply reviewed guidance or skills where that program consumes them |

Use the program's own formats and supported configuration. Writing a file does not prove that a program loads it,
supports its settings or follows its instructions. Keep such support limits clear in cookbook guidance.

## Native files and declared targets

Recipes produce file operations using these models. The operation contract supports whole files addressed by path,
structured content addressed by key path, and shared text sections addressed by `cook:begin <recipe>/<region>` and
`cook:end <recipe>/<region>` markers. Directory and symlink operations are included where applicable; external
command execution is not a batch action.

Choose declarations with the scope of the requested change in mind. A whole-file declaration concerns that entire
file; a declared structured key preserves unrelated keys, and a marked section preserves text outside its boundaries.
Use comment syntax appropriate to the format, such as HTML comments in Markdown and hash comments in ignore files.
Markers name the owning recipe and region; ownership never bans manual edits.

Recipes and their operations compose left to right. When two recipes write the same target, the last one wins and
becomes its owner in `cook.lock`; the final combined diff shows the result. Applying another recipe does not withdraw
output an earlier recipe owns. See [apply](apply.md) for these rules and [revert](revert.md) for reversing a recorded
batch.

## Trust and review

Review recipe code and the sources it consumes before evaluation. Recipes are trusted executable code; batch
approval, destination boundaries and rollback govern declared operations rather than sandboxing arbitrary effects.
Review native configuration in the final diff too: permission changes or commands named in a program's configuration
can affect what that program does later.

Existing or manually edited files use the normal batch diff and whole-batch approval. Apply evaluates supplied recipes
against current content on every invocation and needs neither batch history nor `cook.lock`. See
[safety guidelines](safety.md).

## Model discussion boundary

Cook models native setup and handles declared file operations.

Ingredient/recipe APIs, composition and nesting rules, station roles, lifecycle hooks, decorators, parameter injection
and annotations need their own discussion.
