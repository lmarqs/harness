# Discard contributions and revert runs

## Objective

A developer may want to delete a recipe's declared contributions or restore the values from before a particular
operation. Those are different requests: discard uses the supplied recipe's targets; revert uses a run's history.
Both show the actual proposed change against current content and preserve unrelated content.

This page follows the intended [architecture](../architecture.md#apply-discard-and-revert). For current execution,
see the [root README](../../README.md). The examples specify the design, without claiming it is implemented.

## Choosing an operation

| Intent | Request | Basis |
| --- | --- | --- |
| Add or update declared contributions | `./cook apply ./recipe.groovy` | Supplied recipes against current content |
| Delete declared contributions | `./cook discard ./recipe.groovy` | Targets declared by the supplied recipe version |
| Restore the prior values of a recorded operation | `./cook revert RUN_ID` | That run's recorded history |
| Keep the configured native files and stop invoking Cook | No removal operation needed | Programs consume their native files directly |

`RUN_ID` stands for the recorded run's identifier. The target operations replace the earlier remove/undo/eject
contract. There are no compatibility aliases for those commands or their flags, no module-removal mode, and no
automatic withdrawal when an ingredient or recipe is omitted from a later apply invocation.

## Discard

Discard evaluates the supplied recipes and proposes deleting their declared contributions, even when their current
values differ from what the recipes would generate. A whole-file target addresses the file; a structured target
addresses its key path; a shared-text target addresses the section between its begin/end markers. Deleting a
`test` key preserves unrelated keys, and deleting a declared section preserves unrelated text.

```sh
./cook discard ./recipe.groovy
./cook discard --batch-approve ./base.groovy --agent=codex ./project.groovy --agent=claude
```

Recipes and their operations execute left to right. Overlap is intentional composition, and the terminal shows one
final combined diff. Recipe arguments follow their recipe as `--name=value`; batch options precede the first recipe.
See the [CLI reference](cli.md).

Discard uses the recipe version supplied for this invocation. Cook does not track a persistent recipe identity or
infer an earlier version. Users manage recipe versions and supply the version whose contributions they intend to
delete. Deleting a recipe source or omitting it from a later apply does not remove its output.

Apply and discard are stateless with respect to ownership: neither requires an ownership database or previous run
history. Every invocation evaluates the supplied recipes against current content, even after an identical earlier
invocation. History does not skip a recipe as already applied; an unchanged result may produce an empty diff.

Discard deletes declared contributions. It does not restore imported or adopted originals from an ownership
manifest. Use revert when the request is to restore a recorded operation's prior values.

## Revert

Revert reverses a particular run's recorded operation using that run's history. It restores the operation's prior
values, without evaluating the recipes again or requiring their original sources to remain available elsewhere.

```sh
./cook revert RUN_ID
./cook revert --batch-approve RUN_ID
```

Later edits do not create an ownership or drift veto. Cook proposes the reversal against current content, displays
the actual diff, and preserves unrelated content. The same whole-batch approval and recovery flow applies. Concrete
patch representations and the mechanics of reversing overlapping edits require later implementation design.

### Discard and revert compared

Suppose an apply run changes `test` in `settings.toml` from `old` to `new`, then a developer edits it to `custom`.
Starting from that current content, discard and revert are alternative requests:

| Request | Proposed change | Unrelated `other = "keep"` |
| --- | --- | --- |
| Discard the supplied recipe declaring `test` | Delete `test`, including the current `custom` value | Preserved |
| Revert the original apply run | Replace `custom` with the recorded prior value `old` | Preserved |

Discard proposes:

```diff
--- a/settings.toml
+++ b/settings.toml
@@ -1,2 +1 @@
-test = "custom"
 other = "keep"
```

Revert instead proposes:

```diff
--- a/settings.toml
+++ b/settings.toml
@@ -1,2 +1,2 @@
-test = "custom"
+test = "old"
 other = "keep"
```

Neither request needs an adoption step or a force flag merely because the target was edited. The actual change is
reviewed and approved as a batch.

## Keeping native configuration

Programs consume their own native files without invoking Cook. Stopping use of Cook does not require ejecting those
files, stripping markers, or transferring ownership. Markers identify text boundaries, not persistent ownership.
Keeping configured files and deciding whether to keep the executable or wrapper are separate from deleting recipe
contributions or reversing a recorded operation. Cook does not automatically delete recipe sources or `.cook/`
history as part of discard.

## Approval, boundaries, and recovery

Cook displays the final diff in the terminal and asks before changing target files. Approval covers the whole batch;
selecting individual files or hunks is outside this design. `--batch-approve` displays the diff and proceeds without
prompting. Noninteractive execution without that flag does not silently authorize writes. Existing and edited
content follows this same flow; there is no `--adopt` flag, adoption alias, or ownership-state prerequisite.

The invocation directory is the destination, regardless of the recipe or executable location. Declared writes and
deletions stay within it, including paths resolved through symlinks. Relative references to recipe/template sources
resolve from the referencing recipe's original location, or against its HTTPS source URL. Snapshotting must preserve
that resolution. See [destination and source examples](render.md#destination-and-source-paths).

Record one operation for the entire batch in the destination's ignored `.cook/` directory, with a separate run
record. Save one reversible patch per touched file for the combined change, before the first target write. Each
patch supports forward and reverse application; separate copies are unnecessary. Capture all consumed recipe/template
sources, including local sources and evaluated recipes, their origins, arguments, execution order, operation, and
result or failure log. If a write fails, roll back changes already made and retain the patches and failure record.
The metadata serialization format and complete storage schema are unspecified; JSON metadata is not prohibited by
removal of ownership state.

Losing `.cook/` loses local recovery information, not the ability to apply or discard supplied recipes. New apply
and discard operations still record recovery information. Reverting a particular run requires that run's history;
there is no recipe-based substitute for its recorded prior values.

Recipes are trusted executable code. Approval, output boundaries, and rollback cover declared batch operations,
not arbitrary side effects during recipe evaluation.

## Reference

```text
./cook discard [batch-options] <recipe> [recipe-arguments] [<recipe> [recipe-arguments]]...
./cook revert [batch-options] <run-id>
```

Use exact recipe paths or supported source URIs, including local paths, `file://`, and `https://`. There is no implicit
cookbook-name lookup. Batch options precede recipes or the revert run ID; `--batch-approve` is the sole preapproval
spelling. No additional exit codes, removal flags, hook phases, patch formats, or metadata schemas are specified by
this reference.
