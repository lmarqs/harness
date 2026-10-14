# Revert batches

## Objective

**Problem.** After approving a batch, a developer may want the content from before it back, while keeping edits made
since then to other parts of the same files.

**Goal.** Reverting a recorded batch proposes the actual change against current content, preserves unrelated content,
and writes nothing when a later edit overlaps the batch's changes.

This page follows the intended [architecture](../architecture.md#apply-discard-and-revert), which owns the
requirements. For current execution, see the [root README](../../README.md).

## Overview

[Revert](glossary.md) reverses one recorded [batch](glossary.md) using its batch record. It is the way to undo a
recipe's output: revert the batches that applied the recipe.

### Choosing an operation

| Intent | Request | Basis |
| --- | --- | --- |
| Add or update declared contributions | `./cook apply ./recipe.groovy` | Supplied recipes against current content and `cook.lock` |
| Remove what a recipe's new version omits | `./cook apply ./recipe.groovy` with the new version | Ownership recorded for the recipe in `cook.lock` |
| Restore the content from before a recorded batch | `./cook revert <batchId>` | That batch's reverse patches |
| Keep the configured native files and stop invoking Cook | Stop invoking Cook | Programs consume their native files directly |

`<batchId>` stands for the recorded batch's identifier, the UTC time the batch started, such as
`2026-10-14T15-04-05Z`. Omitting an ingredient or recipe from a later apply invocation leaves its output in place; see
[apply](apply.md#ordered-operations).

## Reference

```text
./cook revert [batch-options] <batchId>
./cook batch list
./cook batch inspect <batchId>
```

Batch options precede the batch ID. `batch list` shows the recorded batches, and `batch inspect` shows one batch's
recipes, arguments, files, patches and, for a failed batch, its failure log; both only read. The [CLI reference](cli.md) defines the grammar and
`--batch-approve`.

## Revert

Revert reverses one recorded batch by applying its reverse patches against current content. It does not evaluate
the recipes again or require their original sources to remain available elsewhere.

```sh
./cook batch list
./cook revert 2026-10-14T15-04-05Z
./cook revert --batch-approve 2026-10-14T15-04-05Z
```

Revert reverses only the batch's own changes. Later edits to other parts of the same files survive. If a later edit
overlaps a change the batch made, the revert writes nothing and lists each conflicting file and section; resolve
those and run it again. `cook.lock` is one of the files a batch touches, so reverting a batch also restores the
ownership it changed.

### Revert example

Suppose an apply batch changes `test` in `settings.toml` from `old` to `new`, and a developer then edits `other` to
`other = "edited"`. Reverting the original apply batch replaces `new` with the recorded prior value `old` and keeps
the edited `other` key:

```diff
--- a/settings.toml
+++ b/settings.toml
@@ -1,2 +1,2 @@
-test = "new"
+test = "old"
 other = "edited"
```

If the developer had edited `test` instead, the revert would write nothing and name `settings.toml` and `test` as
conflicting. The actual change is reviewed and approved as a batch.

## Discard

Discard, which removes what `cook.lock` lists for a recipe without evaluating it or restoring earlier values, is
deferred; until then, [revert](#revert) the batches that applied the recipe or remove its sections and keys by hand.

## Keeping native configuration

Programs consume their own native files directly, so those files and their markers stay usable when you stop
invoking Cook. Keeping configured files and deciding whether to keep the executable or wrapper are separate from
reverting a recorded batch. Cook does not automatically delete recipe sources, `cook.lock`, or `.cook/` batch records.

## Approval, boundaries, and recovery

Revert uses the same terminal diff, whole-batch approval, and write-failure rollback as apply; see
[review and recovery](apply.md#review-and-recovery). The destination boundary is described in
[destination and source paths](apply.md#destination-and-source-paths).

Reverting a batch requires its [batch record](apply.md#batch-record). Without `.cook/`, earlier batches cannot be
reverted, but recipes still apply; ownership survives in the committed `cook.lock`.

Recipes are trusted executable code. Approval, output boundaries, and rollback cover declared batch operations,
not arbitrary side effects during recipe evaluation.
