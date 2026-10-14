# Concepts

## Objective

**Problem.** Reusing program setup involves recipe code, program models, native files and recovery history. Confusing
these responsibilities can make a contributor expect Cook to run a development workflow or treat generated content as
permanently owned.

**Goal.** Readers can distinguish what cookbooks describe, what Cook reviews and writes, what programs consume, and
what information a recorded operation needs for reversal.

## Overview

[Cook architecture](../architecture.md) states the intended design. Cook evaluates trusted Groovy recipes into an
ordered batch of file operations, shows the final diff, obtains approval, saves recovery information and performs the
writes. These definitions describe that design; the [root README](../../README.md) and
[core implementation guide](../../core/README.md) describe current execution.

## Ingredients, recipes and cookbooks

| Concept | Responsibility | Reference |
| --- | --- | --- |
| Ingredient | Model a program's native files and configuration, such as Git's ignore file or Codex's configuration and skills | [Ingredients](ingredients.md) |
| Recipe | Describe requested file operations in trusted executable Groovy code using program models | [Recipes](recipes.md) |
| Cookbook | Hold and ship ingredients and recipes through a repository, with a build that can be separate from Cook's build | [Cookbooks](stations.md#cookbooks) |
| Batch | Combine declared file operations in order for one final diff and whole-batch approval | [Apply](render.md) |
| Engine | Evaluate recipes, review their resulting batch, record recovery information and perform writes | [Architecture](../architecture.md) |

Program knowledge belongs in ingredients; setup choices belong in recipes. The engine handles the resulting file
operations. Ingredient/recipe APIs, nesting, station roles, decorators, annotations, parameter injection and hooks
remain a separate model discussion; this division does not select their interfaces.

## Ordered changes and review

Recipes and their operations compose left to right against current content. Later instructions can replace earlier
values at the same target. If recipe A sets a key to `A` and recipe B sets it to `B`, the combined preview shows `B`.
Applying B alone later leaves unrelated output from A intact: omission is not a request to delete earlier output.

Targets are whole-file paths, structured key paths, or sections bounded by begin/end markers. Markers identify text
boundaries, not ownership. Markdown uses HTML comments; ignore files and TOML use their appropriate hash comments.
Unrelated keys and text outside a declared section remain in place. Whole-file declarations address the entire file.

Existing content and manual edits use the same actual diff and approval flow. There is no ownership reconciliation,
`--adopt`, adoption alias or separate adoption gate. Every apply/discard invocation evaluates the supplied recipes,
even after an identical invocation; unchanged results may have an empty diff.

Cook displays one final terminal diff. Approval covers the whole batch. Without preapproval, Cook asks before changing
target files; `--batch-approve` displays the diff and proceeds without prompting. Noninteractive execution without
that flag does not authorize writes. See the [CLI reference](cli.md) for exact paths, URIs and argument scope.

## Apply, discard and revert

| Operation | Basis | Effect |
| --- | --- | --- |
| Apply | Supplied recipes evaluated against current content | Propose declared additions and updates |
| Discard | Supplied recipe versions evaluated against current content | Propose deleting declared contributions, including manually edited values |
| Revert | A recorded run's history | Propose restoring that operation's prior values against current content |

If a run changes a key from `old` to `new`, then a person changes it to `custom`, discarding a recipe that declares
that key proposes deletion. Reverting the original run instead proposes `custom` to `old`. Both preserve unrelated
content and show the actual diff; neither refuses merely because the key was edited. See
[discard and revert](remove.md) for the full distinction.

Cook does not infer a persistent recipe identity or an earlier version to discard. Users supply the version they
intend to apply or discard. Neither operation needs previous history; reverting a particular run needs its record.

## Files, sources and local history

Native files are consumed directly by the configured programs. A program does not need to invoke Cook to read its
settings, instructions, skills or ignore rules. Teams choose which resulting files to commit and review; Cook does
not establish a mandatory repository layout for their guidance or program configuration.

| Material | Location and purpose |
| --- | --- |
| Recipe and template sources | Local paths or supported source URIs; relative source references resolve from the referencing recipe's original location, including its URL for HTTPS sources |
| Declared output | The invocation's current working directory, regardless of the recipe or executable location; declared writes and deletions stay within it, including symlink resolution |
| Operation history | The destination's ignored `.cook/` directory, with a separate record for each batch operation |
| Repository guidance | Team-chosen native instruction and skill files; `.agents/memory/`, `.agents/skills/` and `AGENTS.md` are useful conventions where the program supports them |
| Engine package | A platform-specific jpackage distribution containing Java, selected by the committed wrapper's version and checksum |

History captures one reversible patch per touched file, the recipe sources actually evaluated, all consumed recipe
and template sources (local and downloaded), origins, arguments, execution order, operation and result or failure log.
Source snapshots preserve original resolution; storing a source in `.cook/` does not rebase its references there.

Save recovery patches before the first target write. On a write failure, roll back changes already made by the batch
and retain the patches and failure record. Losing `.cook/` loses local recovery information, not the ability to apply
or discard supplied recipes. See [history](../architecture.md#paths-source-snapshots-and-local-history).

## Conventions outlive the tool

Keep useful instructions where people and programs already look. For example, a team can document its working-copy
location in `AGENTS.md` and declare matching ignore rules in `.gitignore`. These are repository choices, not managed
workspace requirements. Native permission settings, skills and reviewed knowledge remain useful independently of
Cook; writing guidance does not guarantee that a program loads or obeys it.

See [workspace conventions](workspaces.md), [repository memory](memory.md) and
[repository guidance](context.md) for configuration concerns that support this boundary.

## Trust and execution boundaries

Recipes are trusted executable code. Batch approval, destination boundaries and rollback cover declared file
operations; they do not sandbox arbitrary effects during recipe evaluation. Review recipe code and its sources before
running it. A diff of resulting native configuration also deserves review: other programs may execute commands named
in those files later. Executing external commands is not a batch action.

The CLI is the public cooking interface. Gradle builds and packages Cook and cookbooks. Cook does not own a fixed
software development lifecycle, worker launches, verification receipts or jails. Programs and teams run their own
checks, task tracking and delivery. See [workflow boundaries](lifecycle.md) and [safety guidelines](safety.md).

Personal setup backup (kit) and agent-session orchestration are future capabilities tracked in
[issue #5](https://github.com/lmarqs/the-cook/issues/5) and
[issue #6](https://github.com/lmarqs/the-cook/issues/6). Their protocols are outside this reference.
