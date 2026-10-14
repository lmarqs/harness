# Concepts

## Objective

**Problem.** Reusing program setup involves recipe code, program models, native files and recovery history. Confusing
these responsibilities can make a contributor expect Cook to run a development workflow or treat generated content as
permanently owned.

**Goal.** Readers can distinguish what cookbooks describe, what Cook reviews and writes, what programs consume, and
what information a recorded batch needs for reversal.

## Overview

[Cook architecture](../architecture.md) states the intended design. Cook evaluates trusted Groovy recipes into an
ordered batch of file operations, shows the final diff, obtains approval, records the batch and performs the
writes. These definitions describe that design; the [root README](../../README.md) and
[core implementation guide](../../core/README.md) describe current execution.

## Ingredients, recipes and cookbooks

| Concept | Responsibility | Reference |
| --- | --- | --- |
| Ingredient | Model a program's native files and configuration, such as Git's ignore file or Codex's configuration and skills | [Ingredients](ingredients.md) |
| Recipe | Describe requested file operations in trusted executable Groovy code using program models | [Recipes](recipes.md) |
| Cookbook | Hold and ship ingredients and recipes through a repository, with a build that can be separate from Cook's build | [Cookbooks](stations.md#cookbooks) |
| Batch | Combine declared file operations in order for one final diff and whole-batch approval | [Apply](render.md) |
| Engine | Evaluate recipes, review their resulting batch, record the batch and perform writes | [Architecture](../architecture.md) |

Program knowledge belongs in ingredients; setup choices belong in recipes. The engine handles the resulting file
operations. Ingredient/recipe APIs, nesting, station roles, decorators, annotations, parameter injection and hooks
remain a separate model discussion.

## Ordered changes and review

Recipes and their operations compose left to right against current content. When two recipes write the same
section, key or file, the last one wins and becomes its owner; the diff is the only notice. If recipe A sets a key to
`A` and recipe B sets it to `B`, the combined preview shows `B`, and B owns the key. Applying B alone later leaves A's
output intact: applying one recipe never removes another's output. Applying a new version of A removes what A's
previous version owned and the new version no longer declares, and the diff shows that removal.

Targets are whole-file paths, structured key paths, or sections bounded by `cook:begin <recipe>/<region>` and
`cook:end <recipe>/<region>` markers. Markers identify the owning recipe and region. Markdown uses HTML comments;
ignore files, YAML, TOML and shell scripts use hash comments. Unrelated keys and text outside a declared section
remain in place. Whole-file declarations address the entire file.

The committed `cook.lock` at the repository root records, for each applied recipe, its short name, source, sha256 and
owned sections, keys and files. Ownership decides what a recipe's later versions update and remove; it never blocks a
change. Existing content and manual edits use the same actual diff and approval flow. Every apply invocation evaluates
the supplied recipes, even after an identical invocation; unchanged results have an empty diff.

Cook displays one final terminal diff. The CLI accepts the whole batch. Without preapproval, Cook asks before changing
target files; `--batch-approve` displays the diff and proceeds without prompting. Noninteractive execution without
that flag does not authorize writes. See the [CLI reference](cli.md) for recipe operands and argument scope.

## Apply, discard and revert

| Operation | Basis | Effect |
| --- | --- | --- |
| Apply | Supplied recipes evaluated against current content and `cook.lock` | Propose declared additions and updates, and remove what each recipe's previous version owned and the new version no longer declares |
| Discard (deferred) | What `cook.lock` lists for a recipe, without evaluating it | Remove the recipe's owned sections, keys and files |
| Revert | A recorded batch's reverse patches | Propose reversing only that batch's changes against current content, `cook.lock` included |

If a batch changes a key from `old` to `new`, reverting that batch proposes `new` to `old` and preserves later edits
elsewhere in the file. If a person has since changed that key to `custom`, the edit overlaps the batch's change, so
the revert writes nothing and lists the conflicting file and key. Discard is deferred; revert the batches that applied
a recipe instead. See [discard and revert](remove.md) for the full distinction.

Users supply the recipe version they intend to apply; `cook.lock` records which version produced the current
ownership. Apply needs neither batch history nor `cook.lock`; reverting a particular batch needs its record.

## Files, sources and local history

Native files are consumed directly by the configured programs. Teams choose which resulting files to commit and
review.

| Material | Location and purpose |
| --- | --- |
| Recipe and template sources | Paths inside the destination repository or `https://` URLs; relative source references resolve from the referencing recipe's location, including its URL for HTTPS sources, and local references outside the repository are refused |
| Declared output | The invocation's current working directory, regardless of the executable location; declared writes and deletions stay within it, including symlink resolution |
| Ownership | The committed `cook.lock` at the repository root, recording each applied recipe's short name, source, sha256 and owned sections, keys and files |
| Batch history | The destination's ignored `.cook/batches/<batchId>/` directories, one record per batch |
| Repository guidance | Team-chosen native instruction and skill files; `.agents/memory/`, `.agents/skills/` and `AGENTS.md` are useful conventions where the program supports them |
| Engine package | A platform-specific jpackage distribution containing Java, selected by the committed wrapper's version and checksum |

A batch record captures one reversible patch per touched file (`cook.lock` included), the recipe sources actually
evaluated, all consumed recipe and template sources (local and downloaded), origins, arguments, execution order and
result or failure log. Source snapshots preserve original resolution.

Save the batch record before the first target write. On a write failure, roll back changes already made by the batch and
retain the record and failure. Losing `.cook/` loses the ability to revert earlier batches, not the ability to apply
recipes; ownership survives in the committed `cook.lock`. See
[history](../architecture.md#paths-source-snapshots-and-local-history).

## Conventions outlive the tool

Keep useful instructions where people and programs already look. For example, a team can document its working-copy
location in `AGENTS.md` and declare matching ignore rules in `.gitignore`. These are repository choices. Native
permission settings, skills and reviewed knowledge remain useful independently of Cook; writing guidance does not
guarantee that a program loads or obeys it.

See [workspace conventions](workspaces.md), [repository memory](memory.md) and
[repository guidance](context.md) for configuration concerns that support this boundary.

## Trust and execution boundaries

Recipes are trusted executable code. Batch approval, destination boundaries and rollback cover declared file
operations; they do not sandbox arbitrary effects during recipe evaluation. Review recipe code and its sources before
running it. A diff of resulting native configuration also deserves review: other programs may execute commands named
in those files later. Executing external commands is not a batch action.

The CLI is the public cooking interface. Gradle builds and packages Cook and cookbooks. Programs and teams run their
own checks, task tracking and delivery. See [workflow boundaries](lifecycle.md) and [safety guidelines](safety.md).
Their protocols are outside this reference.
