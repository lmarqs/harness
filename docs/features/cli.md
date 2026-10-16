# CLI reference

This page describes the intended command line contract from the
[architecture](../architecture.md#command-line-and-build-boundary) and its requirements
[FR-17 through FR-20](../architecture.md#requirements): command syntax, operands, arguments and options. The
[glossary](glossary.md) defines terms such as recipe, batch and `cook.lock`. For the options the current CLI accepts,
see the root README's [current implementation](../../README.md#current-implementation).

## Synopsis

```text
./cook [-C <dir>] [batch-options] <recipe> [recipe-arguments] [<recipe> [recipe-arguments]]...
./cook apply [-C <dir>] [batch-options] <recipe> [recipe-arguments] [<recipe> [recipe-arguments]]...
./cook revert [-C <dir>] [batch-options] <batchId>
./cook batch list [-C <dir>]
./cook batch inspect [-C <dir>] <batchId>
./cook help [-C <dir>] [<recipe>]...
./cook --help
```

`apply` is the default. Running `./cook` with no arguments, `./cook help` or `./cook --help` shows the usage.
`--help` takes no other arguments.

`<batchId>` identifies a recorded batch: the UTC time the batch started, such as `2026-10-14T15-04-05Z`, with a
suffix when two batches start in the same second.

## Commands

| Operation | Operand | Proposed change |
| --- | --- | --- |
| Apply, implicit or explicit | One or more recipes, with arguments for each | Declared additions and updates against current content, and removal of what each recipe's previous version owned and no longer declares |
| Revert | One batch ID | Use the batch's reverse patches against current content; recipes are not evaluated again |
| Batch list | None | Show the recorded batches; read only |
| Help | One or more recipes, without arguments | Show each recipe's help in order; read only, and no recipe runs |
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

A recipe operand is a path inside the destination repository, an `https://` URL, or a recipe's coordinates,
`group:artifact:version[:recipe]`. An ordinary relative path such as `recipe.groovy` is accepted. Cook refuses an
operand outside the repository. Coordinates resolve from the repositories that the destination's `cook.toml` lists;
without a recipe, they apply the recipe named like the artifact.

```sh
./cook recipe.groovy
./cook ./recipes/project.groovy
./cook https://example.com/recipes/project.groovy
./cook dev.thecook:agents:0.1.0:instructions --harness=claude
```

`help` prints a recipe's `@help`, `@confirm` and `@yield` texts with its `@param` and `@ingredient` annotations, read
from its source without running it or resolving its imports. It is the one way to read a recipe through Cook:

```sh
./cook help ./recipes/project.groovy
./cook help ./recipes/project.groovy ./recipes/dependency.groovy
```

A first operand equal to `apply`, `revert`, `batch` or `help` selects that command. To apply a recipe file named `revert`,
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

Because every word without `--` starts a recipe, an argument always carries its value after `=`:

| Written | Result |
| --- | --- |
| `--agent=codex` | The value `codex` for `agent` |
| `--agent codex` | Refused: `--agent needs a value: write --agent=<value>` |
| `agent=codex` | Refused: `Not a recipe: agent=codex. Recipe arguments start with --` |
| `--force`, for a yes-or-no param | Refused; write `--force=true` |
| The same recipe twice, with any arguments or versions | Refused: `cook.lock` keeps one entry per recipe |

When a recipe fails to evaluate, nothing is written, and the error names the recipe by its position and operand, such
as `recipe 2 (dev.thecook:agents:0.1.0:instructions)`.

Cook prints one line per changed path, in path order, or `Already cooked.` when nothing changes:

| Line | Path |
| --- | --- |
| `Created <path>` | Absent before the apply |
| `Updated <path>` | Present before and after the apply |
| `Deleted <path>` | Removed by the apply |

With `--dry-run`, the lines read `Would create`, `Would update` and `Would delete`, and nothing is written. After the
writes, Cook prints each recipe's `@yield` text in order, headed by its address.

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

## Kitchen

The kitchen is the directory Cook cooks in, and it is the destination. Without an option it is the invocation's
current working directory.

| Option | Applies to | Position | Effect |
| --- | --- | --- | --- |
| `-C <dir>`, `--kitchen=<dir>` | Every command | Before the first recipe operand, or before the batch ID; once | Cooks in `<dir>` as if Cook had started there: recipe operands and other relative paths resolve against it |

```sh
./cook -C services/api ./recipe.groovy
./cook apply --kitchen=services/api --batch-approve ./recipe.groovy
```

Like a batch option, the kitchen option after a recipe receives an error, because arguments after a recipe belong to
that recipe.

Approval covers all files and hunks in the batch. Existing or edited content follows this same diff and batch-approval
flow; ownership recorded in `cook.lock` never blocks a change. See
[batch review and recovery](../architecture.md#batch-approval-and-failure-recovery) for the write and rollback contract.
