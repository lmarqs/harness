# Configuration diagnostics

## Objective

When configuration does not work as expected, a contributor needs to distinguish a wrong file change from a program
that did not load it, an unavailable source, or missing recovery information. Useful diagnostics identify the path,
operation and cause.

## Reviewing an operation

The [architecture](../architecture.md) defines Cook's file-operation engine and its result or failure record.

Use the relevant evidence to investigate a problem:

| Question | Evidence and responsibility |
| --- | --- |
| Why is a different file changing? | The invocation directory is the destination; source recipe/template references resolve from the original recipe location. Review declared paths and symlink resolution against the destination boundary. |
| Why is a value replaced? | Recipes and operations compose left to right. The last recipe to write a section, key or file wins and becomes its owner in `cook.lock`; review the final combined diff against current content. |
| Why does the program ignore a setting or instruction? | Consult the program's native configuration rules and the ingredient's cookbook documentation, including supported settings and trust prerequisites. Writing configuration does not enforce its loading. |
| Why did a write fail? | Inspect the batch's failure record and retained recovery patches with `./cook batch inspect <batchId>`. Cook rolls back changes already made by the batch; arbitrary recipe effects are outside that guarantee. |
| Why does a prior batch fail to revert? | Revert requires that batch's record under the destination's `.cook/batches/`. Losing history does not prevent new apply operations. If a later edit overlaps the batch's changes, revert writes nothing and lists each conflicting file and section. |

See [apply](apply.md) for review and source resolution, [revert](revert.md) for reversal semantics, and
[safety](safety.md) for the trusted-code boundary. The batch record captures evaluated and consumed sources, their
origins, arguments, execution order, reverse patches and result or failure log. It is recovery information; ownership
is recorded in the committed `cook.lock`.

## Scope

Repository checks run through the team's programs; see [check configuration](verify.md).

For commands available in the current implementation, use the [root README](../../README.md) and
[core implementation guide](../../core/README.md).
