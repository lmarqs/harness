# CLI reference

This page describes the intended command line contract from the
[architecture](../architecture.md#command-line-and-build-boundary) and its requirements
[FR-17 through FR-20](../architecture.md#requirements). For current execution and build instructions, see the
[root README](../../README.md).

## Synopsis

```text
./cook [batch-options] <recipe> [recipe-arguments] [<recipe> [recipe-arguments]]...
./cook apply [batch-options] <recipe> [recipe-arguments] [<recipe> [recipe-arguments]]...
./cook revert [batch-options] <batchId>
./cook batch list
./cook batch inspect <batchId>
```

`apply` is the default. Running `./cook` with no arguments shows help.

## Commands

| Operation | Operand | Proposed change |
| --- | --- | --- |
| Apply, implicit or explicit | One or more recipes, with arguments for each | Declared additions and updates against current content, plus removal of what each recipe's previous version owned and the supplied version no longer declares |
| Revert | One batch ID | Apply the batch's reverse patches against current content; do not evaluate recipes again |
| Batch list | None | Show the recorded batches; read only |
| Batch inspect | One batch ID | Show one batch's recipes, arguments, files, and patches; read only |

Apply evaluates the recipe versions supplied for that invocation, even after an identical earlier invocation. It
requires neither batch history nor `cook.lock`; without a lock entry for a recipe, it removes nothing on that recipe's
behalf. Revert requires the chosen batch's record. Later edits that do not overlap the batch's changes survive; an
overlapping edit makes the revert write nothing and list each conflicting file and section. Reverting a batch also
restores the `cook.lock` ownership it changed. Discard is deferred; see
[operation semantics](../architecture.md#apply-discard-and-revert).

```sh
# Default and explicit apply
./cook ./recipe.groovy
./cook apply ./recipe.groovy

# Reverse a recorded batch, then list and inspect recorded batches
./cook revert --batch-approve 2026-10-14T15-04-05Z
./cook batch list
./cook batch inspect 2026-10-14T15-04-05Z
```

`--batch-approve` is the only preapproval spelling.

## Recipe operands

A recipe operand is a path inside the destination repository or an `https://` URL. An ordinary relative path such as
`recipe.groovy` is accepted. Cook refuses an operand outside the repository.

```sh
./cook recipe.groovy
./cook ./recipes/project.groovy
./cook https://example.com/recipes/project.groovy
```

Initial bare `apply`, `revert`, and `batch` are reserved command names that select a command. To apply a recipe
file named `revert`, make its path explicit:

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
Recipes and their operations compose left to right. When two recipes write the same section, key, or file, the last
one wins and becomes its owner; the combined final diff is the only notice, and approval covers the entire batch.

An argument value that looks like a recipe path remains an argument value:

```sh
./cook apply ./a.groovy --source=./b.groovy
./cook apply ./a.groovy ./b.groovy
```

The first invocation evaluates only `a.groovy` and gives it `source` with the value `./b.groovy`. The second evaluates
`a.groovy`, then `b.groovy`, as two recipes. These examples specify CLI scope.

## Batch options and approval

Batch options precede the first recipe operand. For revert, they precede the batch ID. `--batch-approve` applies to
the whole operation: display the actual diff and proceed without prompting. Without it, ask before changing target
files. Noninteractive execution without this flag does not silently authorize writes.

```sh
./cook --batch-approve ./recipe.groovy
./cook apply --batch-approve ./a.groovy ./b.groovy
./cook revert --batch-approve 2026-10-14T15-04-05Z
```

A batch option after a recipe receives the normal unknown-option error:

```sh
# Misplaced batch option: unknown-option error
./cook apply ./a.groovy --batch-approve

# Correct scope: batch option before the first recipe
./cook apply --batch-approve ./a.groovy
```

Approval covers all files and hunks in the batch. Existing or edited content follows this same diff and batch-approval
flow; ownership recorded in `cook.lock` never blocks a change. See
[batch review and recovery](../architecture.md#batch-approval-and-failure-recovery) for the write and rollback contract.
