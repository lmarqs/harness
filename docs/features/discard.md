# Discard recipes

## Objective

**Problem.** A developer who stops using a recipe wants its output gone from the repository, without hunting through
shared files for what it wrote and without losing the content written by hand or by other recipes.

**Goal.** Discarding a recipe proposes the removal of exactly what the recipe owns, and everything else in the same
files stays.

This page follows the intended [architecture](../architecture.md#apply-and-discard), which owns the requirements. For
current execution, see the [root README](../../README.md).

## Overview

[Discard](glossary.md) removes what the committed `cook.lock` lists for each named [recipe](glossary.md). It does not
evaluate the recipe or fetch its cookbook, so it works after the recipe's source is gone.

### Choosing an operation

| Intent | Request | Basis |
| --- | --- | --- |
| Add or update declared contributions | `./cook apply ./recipe.groovy` | Supplied recipes against current content and `cook.lock` |
| Remove what a recipe's new version omits | `./cook apply ./recipe.groovy` with the new version | Ownership recorded for the recipe in `cook.lock` |
| Remove everything a recipe owns | `./cook discard ./recipe.groovy` | Ownership recorded for the recipe in `cook.lock` |
| Get back content from before an apply | The repository's version control, such as `git revert` | The commit history |
| Keep the configured native files and stop invoking Cook | Stop invoking Cook | Programs consume their native files directly |

Omitting a recipe from a later apply invocation leaves its output in place; see [apply](apply.md#ordered-operations).

## Reference

```text
./cook discard [batch-options] <recipe>...
```

Each recipe is written as for apply: coordinates `group:artifact:version[:recipe]` or a `.groovy` path in the kitchen.
Discard matches the recipe's short name in `cook.lock`, so the version in coordinates does not need to be the applied
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
recipes' sections, hand-written text in the same file, and files a seed wrote from a template on the recipe's first
apply.

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

## Keeping native configuration

Programs consume their own native files directly, so those files and their markers stay usable when you stop
invoking Cook. Keeping configured files and deciding whether to keep the executable or wrapper are separate from
discarding a recipe. Cook does not delete recipe sources.

## Approval, boundaries, and recovery

Discard uses the same terminal diff, whole-batch approval, `--dry-run` and write-failure rollback as apply; see
[review and recovery](apply.md#review-and-recovery). The destination boundary is described in
[destination and source paths](apply.md#destination-and-source-paths).

Recipes are trusted executable code. Discard runs none, and it does not undo what a recipe's evaluation did outside
its declared files.
