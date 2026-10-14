# Composition boundaries and cookbooks

## Objective

**Problem.** Reusable setup choices and program knowledge need a clear home, but naming an extra composition unit can
prematurely settle an API.

**Goal.** Preserve the reasons for sharing setup and distributing program models while leaving station semantics for
the separate model discussion.

## Reusable setup choices

A team can reuse settings, instructions, skills and build or CI configuration across repositories. Useful shared
choices still need repository-specific review: a command name or permission setting suitable for one project may not
fit another. Native files make the resulting choices visible to people and directly consumable by programs.

[Cook architecture](../architecture.md) assigns program knowledge to [ingredients](ingredients.md) and requested file
changes to trusted Groovy [recipes](recipes.md). The engine composes declared operations left to right, presents their
combined diff, obtains approval, records recovery information and performs writes. When two recipes write the
same target, the last one wins and becomes its owner; applying a recipe does not delete output another recipe owns.

Review existing or edited content through that same batch flow. Deliberately choose declaration scope: whole-file
operations address entire files, key operations preserve unrelated keys, and marked sections preserve text outside
their boundaries. See [apply](render.md) and [revert](remove.md).

## Station role remains deferred

Whether a **station** becomes a separate composition unit, and how it relates to recipes, is an open question in the
ingredient/recipe model discussion.

Existing dynamic composition and compiled suppliers remain supported. For current implementation behavior, use the
[core guide](../../core/README.md) and recipe READMEs.

## Cookbooks

A cookbook repository holds and ships ingredients and recipes. Ingredients model programs' native files and
configuration; recipes describe reusable setup choices using those models. A cookbook can have its own build,
independent of Cook's build and release process. Authors maintain program-specific knowledge separately from the
engine.

Engine distribution uses a platform-specific jpackage package containing Java and a committed wrapper that pins an
engine version and checksum, downloads it on first use and caches it. See [installation](installation.md) and
[wrapper](wrapper.md). Changing that engine pin does not upgrade cookbook content or manage recipe versions.

Users supply the recipe version they intend to apply; `cook.lock` records which version produced each recipe's
ownership. Recipe operands are paths inside the destination repository or `https://` URLs.

## Review and execution boundaries

Review cookbook recipe code and sources before running them, then review their resulting native-file diff. Recipes
are trusted executable code. Approval, destination boundaries and rollback govern declared operations, not arbitrary
recipe side effects. Supervising a configured loop runner or agent, verifying its work and delivering its changes
belong to the people and programs that run it. See [safety guidelines](safety.md) and
[workflow boundaries](lifecycle.md).
