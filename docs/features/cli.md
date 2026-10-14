# CLI reference

This page describes the intended command line contract from the
[architecture](../architecture.md#command-line-and-build-boundary) and its requirements
[FR-17 through FR-20](../architecture.md#requirements): command syntax, operands, arguments and options. The
[glossary](glossary.md) defines terms such as recipe, batch and `cook.lock`. For the options the current CLI accepts,
see the root README's [current implementation](../../README.md#current-implementation).

## Synopsis

```text
./cook [batch-options] <recipe> [recipe-arguments] [<recipe> [recipe-arguments]]...
./cook apply [batch-options] <recipe> [recipe-arguments] [<recipe> [recipe-arguments]]...
./cook revert [batch-options] <batchId>
./cook batch list
./cook batch inspect <batchId>
```

`apply` is the default. Running `./cook` with no arguments shows help.

`<batchId>` identifies a recorded batch: the UTC time the batch started, such as `2026-10-14T15-04-05Z`, with a
suffix when two batches start in the same second.

## Commands

| Operation | Operand | Proposed change |
| --- | --- | --- |
| Apply, implicit or explicit | One or more recipes, with arguments for each | Declared additions and updates against current content, and removal of what each recipe's previous version owned and no longer declares |
| Revert | One batch ID | Use the batch's reverse patches against current content; recipes are not evaluated again |
| Batch list | None | Show the recorded batches; read only |
| Batch inspect | One batch ID | Show one batch's recipes, arguments, files, patches and, for a failed batch, its failure log; read only |

The [architecture](../architecture.md#apply-discard-and-revert) defines these operations, including the deferred
discard; [apply](apply.md) and [revert](revert.md) explain them with examples.

```sh
# Default and explicit apply
./cook ./recipe.groovy
./cook apply ./recipe.groovy

# Reverse a recorded batch, then list and inspect recorded batches
./cook revert --batch-approve 2026-10-14T15-04-05Z
./cook batch list
./cook batch inspect 2026-10-14T15-04-05Z
```

## Recipe operands

A recipe operand is a path inside the destination repository or an `https://` URL. An ordinary relative path such as
`recipe.groovy` is accepted. Cook refuses an operand outside the repository.

```sh
./cook recipe.groovy
./cook ./recipes/project.groovy
./cook https://example.com/recipes/project.groovy
```

A first operand equal to `apply`, `revert` or `batch` selects that command. To apply a recipe file named `revert`,
make its path explicit:

```sh
./cook ./revert
```

## Recipe arguments and ordering

Recipe arguments follow their recipe and use `--name=value`. Each following recipe operand starts a new argument
scope. Shell-quote values containing spaces so the shell passes the value as part of one argument.

```sh
./cook apply --batch-approve ./base.groovy --agent=codex ./project.groovy --agent=claude
./cook ./recipe.groovy --instructions='Keep existing comments.'
```

In the first invocation, `--agent=codex` belongs to `base.groovy`, and `--agent=claude` belongs to `project.groovy`.
Recipes and their operations compose left to right into one batch; the
[architecture](../architecture.md#ordered-composition-and-declared-targets) defines how the last writer of a section,
key or file wins.

An argument value that looks like a recipe path remains an argument value:

```sh
./cook apply ./a.groovy --source=./b.groovy
./cook apply ./a.groovy ./b.groovy
```

The first invocation evaluates only `a.groovy` and gives it `source` with the value `./b.groovy`. The second evaluates
`a.groovy`, then `b.groovy`, as two recipes.

## Batch options and approval

| Option | Applies to | Position | Effect |
| --- | --- | --- | --- |
| `--batch-approve` | `apply`, implicit or explicit, and `revert` | Before the first recipe operand; for `revert`, before the batch ID | Displays the final diff and proceeds without prompting; approval covers the whole batch |

`--batch-approve` is the batch option and the only preapproval spelling. Without it, Cook asks before changing target
files. Noninteractive execution without this flag does not silently authorize writes.

```sh
./cook --batch-approve ./recipe.groovy
./cook apply --batch-approve ./a.groovy ./b.groovy
./cook revert --batch-approve 2026-10-14T15-04-05Z
```

A batch option after a recipe receives the normal unknown-option error. This invocation fails:

```sh
./cook apply ./a.groovy --batch-approve
```

Put the option before the first recipe instead:

```sh
./cook apply --batch-approve ./a.groovy
```

Approval covers all files and hunks in the batch. Existing or edited content follows this same diff and batch-approval
flow; ownership recorded in `cook.lock` never blocks a change. See
[batch review and recovery](../architecture.md#batch-approval-and-failure-recovery) for the write and rollback contract.
