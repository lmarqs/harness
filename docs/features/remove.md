# Discard contributions and revert batches

## Objective

A developer may want to delete a recipe's contributions or restore the content from before a particular batch. Those
are different requests: discard uses what `cook.lock` lists for a recipe; revert uses a batch's record. Revert shows
the actual proposed change against current content and preserves unrelated content. Discard is deferred; revert is
the current way to undo a recipe's output.

This page follows the intended [architecture](../architecture.md#apply-discard-and-revert). For current execution,
see the [root README](../../README.md).

## Choosing an operation

| Intent | Request | Basis |
| --- | --- | --- |
| Add or update declared contributions | `./cook apply ./recipe.groovy` | Supplied recipes against current content and `cook.lock` |
| Remove what a recipe's new version no longer declares | `./cook apply ./recipe.groovy` with the new version | Ownership recorded for the recipe in `cook.lock` |
| Restore the content from before a recorded batch | `./cook revert <batchId>` | That batch's reverse patches |
| Keep the configured native files and stop invoking Cook | Stop invoking Cook | Programs consume their native files directly |

`<batchId>` stands for the recorded batch's identifier, the UTC time the batch started, such as
`2026-10-14T15-04-05Z`. `./cook batch list` shows the recorded batches. Omitting an ingredient or recipe from a later
apply invocation does not withdraw its output.

## Discard

Discard is deferred until a concrete need. When it is available, discarding a recipe removes the sections, keys, and
files that `cook.lock` lists for it, across all the batches that wrote them, without evaluating the recipe. A
whole-file entry addresses the file; a key entry addresses its key path; a section entry addresses the text between
its `cook:begin <recipe>/<region>` and `cook:end <recipe>/<region>` markers. Removing a `test` key preserves
unrelated keys, and removing a section preserves unrelated text.

Until then, [revert](#revert) the batches that applied the recipe, or remove its sections and keys by hand. Applying a
new version of a recipe removes what its previous version owned and the new version no longer declares; applying
another recipe B never removes recipe A's output. Deleting a recipe source or omitting it from a later apply does not
remove its output.

Apply requires neither batch history nor `cook.lock`. Every invocation evaluates the supplied recipes against current
content, even after an identical earlier invocation. History does not skip a recipe as already applied; an unchanged
result produces an empty diff. Without a lock entry for a recipe, apply removes nothing on its behalf.

## Revert

Revert reverses one recorded batch by applying its reverse patches against current content. It does not evaluate the
recipes again or require their original sources to remain available elsewhere.

```sh
./cook revert 2026-10-14T15-04-05Z
./cook revert --batch-approve 2026-10-14T15-04-05Z
```

Revert reverses only the batch's own changes. Later edits to other parts of the same files survive. If a later edit
overlaps a change the batch made, the revert writes nothing and lists each conflicting file and section; resolve
those and run it again. `cook.lock` is one of the files a batch touches, so reverting a batch also restores the
ownership it changed. The same whole-batch approval and recovery flow applies.

### Discard and revert compared

Suppose an apply batch changes `test` in `settings.toml` from `old` to `new`, and a developer then edits `other`.
Starting from that current content:

| Request | Proposed change | Edited `other = "edited"` |
| --- | --- | --- |
| Discard the recipe that owns `test` (deferred) | Delete `test` as listed in `cook.lock`, without evaluating the recipe | Preserved |
| Revert the original apply batch | Replace `new` with the recorded prior value `old` | Preserved |

Revert proposes:

```diff
--- a/settings.toml
+++ b/settings.toml
@@ -1,2 +1,2 @@
-test = "new"
+test = "old"
 other = "edited"
```

If the developer had edited `test` instead, the revert would write nothing and name `settings.toml` and `test` as
conflicting. In both requests, the actual change is reviewed and approved as a batch.

## Keeping native configuration

Programs consume their own native files directly, so those files and their markers stay usable when you stop
invoking Cook. Markers identify the owning recipe and region; ownership never blocks a change. Keeping configured
files and deciding whether to keep the executable or wrapper are separate from removing recipe contributions or
reversing a recorded batch. Cook does not automatically delete recipe sources, `cook.lock`, or `.cook/` history.

## Approval, boundaries, and recovery

Cook displays the final diff in the terminal and asks before changing target files. The CLI accepts the whole batch;
choosing individual files or hunks belongs to a separate interface decision. `--batch-approve` displays the diff and
proceeds without prompting. Noninteractive execution without that flag does not silently authorize writes. Existing
and edited content follows this same flow.

The invocation directory is the destination, regardless of the executable location. Declared writes and deletions
stay within it, including paths resolved through symlinks. Recipes come from paths inside the destination repository
or `https://` URLs. Relative references to recipe/template sources resolve from the referencing recipe's location,
inside the repository or against its HTTPS source URL; a local reference outside the repository is refused. See
[destination and source examples](render.md#destination-and-source-paths).

Each batch has one record in the destination's ignored `.cook/batches/<batchId>/` directory. Save one reversible patch
per touched file for the combined change, `cook.lock` included, before the first target write. Each patch supports
forward and reverse application. The record also holds the evaluated and consumed recipe/template sources with their
origins, the recipe arguments, the execution order, and the result or failure log. If a write fails, roll back changes
already made and retain the record and failure. The metadata serialization format and complete storage schema belong
to the implementation.

Without `.cook/`, earlier batches cannot be reverted, but recipes still apply; ownership survives in the
committed `cook.lock`. Reverting a particular batch requires its record.

Recipes are trusted executable code. Approval, output boundaries, and rollback cover declared batch operations,
not arbitrary side effects during recipe evaluation.

## Reference

```text
./cook revert [batch-options] <batchId>
./cook batch list
./cook batch inspect <batchId>
```

Batch options precede the batch ID; `--batch-approve` is the sole preapproval spelling. Discard is deferred.
