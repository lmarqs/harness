# Configuration diagnostics

## Objective

**Problem.** When configuration does not work as expected, a contributor needs to distinguish a wrong file change from
a program that did not load it, an unavailable source, or a missing batch record.

**Goal.** Diagnostics identify the path, operation and cause.

## Reviewing an operation

The [architecture](../architecture.md) defines Cook's file-operation engine and its batch record; the
[glossary](glossary.md) defines the destination, batch and `cook.lock`.

Use the relevant evidence to investigate a problem:

| Question | Evidence and responsibility |
| --- | --- |
| Why is a different file changing? | Review declared paths and symlink resolution against the destination; see [destination and source paths](apply.md#destination-and-source-paths). |
| Why is a value replaced? | The last recipe to write a section, key or file wins; see [ordered operations](apply.md#ordered-operations). |
| Why does the program ignore a setting or instruction? | The program's native configuration rules and the ingredient's cookbook documentation decide what loads. |
| Why did a write fail? | `./cook batch inspect <batchId>` shows the batch's failure log, files and retained patches. |
| Why does a prior batch fail to revert? | Revert needs the batch record and writes nothing when a later edit overlaps; see [revert](revert.md#revert). |

Writing configuration does not enforce its loading; check the program's supported settings and trust prerequisites.
Cook rolls back changes a failed batch already made; arbitrary recipe effects are outside that guarantee, as
[safety](safety.md) explains. Losing the batch records under `.cook/batches/` prevents reverting those batches, not
new apply operations; ownership is recorded in the committed `cook.lock`. The following illustrates the evidence to
gather after a failed write:

```sh
./cook batch list
./cook batch inspect 2026-10-14T15-04-05Z
```

## Related pages

Repository checks run through the team's programs; see [check configuration](verify.md).

For commands available in the current implementation, use the [root README](../../README.md) and
[core implementation guide](../../core/README.md).
