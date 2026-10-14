# Composition boundaries and cookbooks

## Objective

**Problem.** Reusable setup choices and program knowledge need a clear home, but naming an extra composition unit can
prematurely settle an API or bring back lifecycle responsibilities that do not belong to Cook.

**Goal.** Preserve the reasons for sharing setup and distributing program models while leaving station semantics for
the separate model discussion.

## Reusable setup choices

A team can reuse settings, instructions, skills and build or CI configuration across repositories. Useful shared
choices still need repository-specific review: a command name or permission setting suitable for one project may not
fit another. Native files make the resulting choices visible to people and directly consumable by programs.

[Cook architecture](../architecture.md) assigns program knowledge to [ingredients](ingredients.md) and requested file
changes to trusted Groovy [recipes](recipes.md). The engine composes declared operations left to right, presents their
combined diff, obtains approval, records recovery information and performs writes. Later instructions can replace
earlier values at overlapping targets; applying a recipe does not implicitly delete unrelated earlier output.

Review existing or edited content through that same batch flow. There is no adoption step or rule to keep every
existing value merely because it is already present. Deliberately choose declaration scope: whole-file operations
address entire files, key operations preserve unrelated keys, and marked sections preserve text outside their
boundaries. See [apply](render.md) and [discard and revert](remove.md).

## Station role remains deferred

The term **station** has appeared in composition discussions, but its role is an open question in the ingredient/recipe
model discussion. This page defines no station schema, command, stages, detection rules, pinning or apply-once merge
behavior. It does not decide whether a station becomes a separate composition unit or how it relates to recipes.

Ingredient/recipe APIs, composition and nesting rules, decorators, annotations, parameter injection and lifecycle hooks
also require that discussion. Existing dynamic composition and compiled suppliers are not selected for deletion by
this documentation change. For current implementation behavior, use the [core guide](../../core/README.md) and
cookbook usage guides rather than treating an exploratory sketch as the target API.

## Cookbooks

A cookbook repository holds and ships ingredients and recipes. Ingredients model programs' native files and
configuration; recipes describe reusable setup choices using those models. A cookbook can have its own build,
independent of Cook's build and release process. Authors maintain program-specific knowledge without turning each
program's workflow into engine behavior.

Engine distribution uses a platform-specific jpackage package containing Java and a committed wrapper that pins an
engine version and checksum, downloads it on first use and caches it. See [installation](installation.md) and
[wrapper](wrapper.md). Changing that engine pin does not upgrade cookbook content or manage recipe versions.

Users supply the recipe version they intend to apply or discard. Recipe operands are exact paths or supported source
URIs; there is no implicit cookbook-name lookup. This reference prescribes no registry, archive naming scheme,
cookbook manifest, automatic recipe updater or complete publication protocol.

## Review and execution boundaries

Review cookbook recipe code and sources before running them, then review their resulting native-file diff. Recipes
are trusted executable code. Approval, destination boundaries and rollback govern declared operations, not arbitrary
recipe side effects. Writing configuration for a loop runner or agent does not make Cook supervise it, verify its
work or deliver its changes. See [safety guidelines](safety.md) and [workflow boundaries](lifecycle.md).

Personal setup backup (kit) and agent-session orchestration remain future work tracked in
[issue #5](https://github.com/lmarqs/the-cook/issues/5) and
[issue #6](https://github.com/lmarqs/the-cook/issues/6). Their design is outside these composition boundaries.
