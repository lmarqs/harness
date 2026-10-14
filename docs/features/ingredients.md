# Ingredients

## Objective

**Problem.** Programs use different native files, formats and configuration concepts. Putting this knowledge into
Cook's engine would couple its file-operation responsibilities to every program's settings and releases.

**Goal.** Cookbook authors can maintain program knowledge as data models, while Cook evaluates recipes and handles
the resulting file operations.

## Overview

An ingredient is a **program data model**. It models the files and configuration a program natively consumes, such as
Git's ignore file or Codex's configuration and skills. [Cook architecture](../architecture.md) establishes this
responsibility, and the [glossary](glossary.md) defines the related terms.

[Recipes](recipes.md) use program models to describe requested file changes in trusted executable Groovy code.
[Cookbook repositories](cookbooks.md#cookbooks) hold and ship ingredients and recipes; their builds can be separate
from Cook's build. The engine evaluates recipes into an ordered batch, shows its diff, obtains approval, saves
the batch record and performs writes.

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

Recipes produce file operations using these models: whole files, structured keys, or sections bounded by
`cook:begin <recipe>#<region>` and `cook:end <recipe>#<region>` markers. Choose declarations with the scope of the
requested change in mind. Composition, ownership and target rules are defined in
[ordered composition and declared targets](../architecture.md#ordered-composition-and-declared-targets); see
[apply](apply.md) and [revert](revert.md) for worked examples.

## Trust and review

Review recipe code and the sources it consumes before evaluation; recipes are trusted executable code and are not
sandboxed. Review native configuration in the final diff too: permission changes or commands named in a program's
configuration can affect what that program does later. See [safety guidelines](safety.md).
