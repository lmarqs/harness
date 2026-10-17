# Discard recipes

## Objective

**Problem.** A developer who stops using a recipe wants its output gone from the repository, without hunting through
shared files for what it wrote and without losing the content written by hand or by other recipes.

**Goal.** Discarding a recipe proposes the removal of exactly what the recipe owns, and everything else in the same
files stays.

This page follows the intended [architecture](../architecture.md#cooking-and-discard), which owns the requirements. For
current execution, see the [root README](../../README.md).

## Overview

[Discard](glossary.md) removes what the committed `cook.lock` lists for each named [recipe](glossary.md). It does not
evaluate the recipe or fetch its cookbook, so it works after the recipe's source is gone.

### Choosing an operation

| Intent | Request | Basis |
| --- | --- | --- |
| Add or update declared contributions | `./cook ./recipe.groovy` | Supplied recipes against current content and `cook.lock` |
| Remove what a recipe's new version omits | `./cook ./recipe.groovy` with the new version | Ownership recorded for the recipe in `cook.lock` |
| Remove everything a recipe owns | `./cook discard ./recipe.groovy` | Ownership recorded for the recipe in `cook.lock` |
| Get back content from before a run | The repository's version control, such as `git revert` | The commit history |
| Keep the configured native files and stop invoking Cook | Stop invoking Cook | Programs consume their native files directly |

Omitting a recipe from a later invocation leaves its output in place; see [cooking](cook.md#ordered-operations).

## Reference

```text
./cook discard [batch-options] <recipe>...
```

Each recipe is written as for cooking: coordinates `group:artifact:version[:recipe]` or a `.groovy` path in the kitchen.
Discard matches the recipe's short name in `cook.lock`, so the version in coordinates does not need to be the cooked
one, and a path's file does not need to exist any more. Discard takes no recipe arguments. Batch options precede the
first recipe; the [CLI reference](cli.md) defines them.

## What discard removes

| The recipe owns | Discard removes |
| --- | --- |
| A marked section | The section, from its `cook:begin` marker to its `cook:end` marker |
| A key in a JSON, TOML or YAML file | The key |
| A whole file | The file |
| A symbolic link | The link |

A file left empty is deleted, because it existed only to hold the recipe's content. Each discarded recipe's entry
leaves `cook.lock`, and a lock that lists no recipe is deleted. Everything the recipe does not own stays: other
recipes' sections, hand-written text in the same file, and files a seed wrote from a template the first time the
recipe was cooked.

Discard does not restore values the recipe replaced. If a recipe changed `test = "old"` to `test = "new"`, discarding
it removes the `test` key. To get `old` back, use the repository's history.

Ownership never blocks a change, so content a developer edited inside an owned section or key is removed with it; the
diff shows that removal. A recipe that `cook.lock` does not list is refused, and so is a recipe named twice.

### Discard example

`CLAUDE.md` holds a hand-written line and a section that `dev.thecook:agents:instructions` owns:

```markdown
Hand-written notes stay.
<!-- cook:begin dev.thecook:agents:instructions#instructions -->
@AGENTS.md
<!-- cook:end dev.thecook:agents:instructions#instructions -->
```

`./cook discard dev.thecook:agents:0.1.0:instructions` proposes:

```diff
--- a/CLAUDE.md
+++ b/CLAUDE.md
@@ -1,4 +1,1 @@
 Hand-written notes stay.
-<!-- cook:begin dev.thecook:agents:instructions#instructions -->
-@AGENTS.md
-<!-- cook:end dev.thecook:agents:instructions#instructions -->
```

together with the removal of the recipe's entry from `cook.lock`.

### Renaming a recipe

A path recipe's short name is its place in the repository, so moving `recipes/a.groovy` to `recipes/b.groovy` renames
it. Cooking `recipes/b.groovy` writes its own sections, such as `recipes/b#notes`, and takes over the keys and files
both declare; `recipes/a`'s sections stay, because cooking one recipe never removes another's output. Discarding the
old name removes what the lock still lists for it, although its file is gone:

```sh
git mv recipes/a.groovy recipes/b.groovy
./cook recipes/b.groovy
./cook discard recipes/a.groovy
```

A recipe cooked by coordinates keeps its short name across versions, so only a changed group, artifact or recipe
name renames it, with the same two steps.

## Keeping native configuration

Programs consume their own native files directly, so those files and their markers stay usable when you stop
invoking Cook. Keeping configured files and deciding whether to keep the executable or wrapper are separate from
discarding a recipe. Cook does not delete recipe sources.

## Approval, boundaries, and recovery

Discard uses the same terminal diff, whole-batch approval, `--dry-run` and write-failure rollback as cooking; see
[review and recovery](cook.md#review-and-recovery). The destination boundary is described in
[destination and source paths](cook.md#destination-and-source-paths).

Recipes are trusted executable code. Discard runs none, and it does not undo what a recipe's evaluation did outside
its declared files.
