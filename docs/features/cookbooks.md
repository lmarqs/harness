# Cookbooks and reusable setup

## Objective

**Problem.** A team that reuses setup across repositories needs to know where the reusable choices and the program
knowledge behind them live, and how they reach a repository.

**Goal.** Readers can tell what a cookbook holds, how its recipes reach a repository, and which review each step
needs.

## Reusable setup choices

A team can reuse settings, instructions, skills and build or CI configuration across repositories. Useful shared
choices still need repository-specific review: a command name or permission setting suitable for one project may not
fit another. Native files make the resulting choices visible to people and directly consumable by programs.

[Cook architecture](../architecture.md) assigns program knowledge to [ingredients](ingredients.md) and requested file
changes to trusted Groovy [recipes](recipes.md); the [glossary](glossary.md) defines these terms. The engine composes
declared operations left to right into one reviewed batch; see [ordered operations](apply.md#ordered-operations) for how
they combine and [revert](revert.md) for undoing a batch.

## Cookbooks

A cookbook repository holds and ships ingredients and recipes. Ingredients model programs' native files and
configuration; recipes describe reusable setup choices using those models. A cookbook can have its own build,
independent of Cook's build and release process. Authors maintain program-specific knowledge separately from the
engine. For current implementation behavior, use the [core guide](../../core/README.md) and recipe READMEs.

The engine reaches a repository through the [wrapper](wrapper.md) and its pin; see also
[installation](installation.md). Changing that engine pin does not upgrade cookbook content or manage recipe versions.

Users supply the recipe version they intend to apply; `cook.lock` records which version produced each recipe's
ownership. Recipe operands are paths inside the destination repository or `https://` URLs.

## Review and execution boundaries

Review cookbook recipe code and sources before running them, then review their resulting native-file diff. Recipes
are trusted executable code. Approval, destination boundaries and rollback govern declared operations, not arbitrary
recipe side effects. Supervising a configured loop runner or agent, verifying its work and delivering its changes
belong to the people and programs that run it. See [safety guidelines](safety.md) and
[workflow boundaries](lifecycle.md).
