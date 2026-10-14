# CLI reference

This page describes the intended command line contract from the
[architecture](../architecture.md#command-line-and-build-boundary) and PRD
[FR-17 through FR-20](../../tasks/prd-documentation-alignment.md#cli-grammar). For current execution and build
instructions, see the [root README](../../README.md). The design examples here do not establish that the target CLI
is implemented.

## Synopsis

```text
./cook [batch-options] <recipe> [recipe-arguments] [<recipe> [recipe-arguments]]...
./cook apply [batch-options] <recipe> [recipe-arguments] [<recipe> [recipe-arguments]]...
./cook discard [batch-options] <recipe> [recipe-arguments] [<recipe> [recipe-arguments]]...
./cook revert [batch-options] <run-id>
```

`apply` is the default. Running `./cook` with no arguments shows help.

## Commands

| Operation | Operand | Proposed change |
| --- | --- | --- |
| Apply, implicit or explicit | One or more recipes, with arguments for each | Declared additions and updates against current content |
| Discard | One or more recipes, with arguments for each | Delete the supplied recipes' declared contributions, including values edited by hand; preserve unrelated content |
| Revert | One recorded run ID | Reverse the recorded operation to its prior values against current content, preserving unrelated content; do not evaluate recipes again |

Apply and discard evaluate the recipe versions supplied for that invocation, even after an identical earlier run.
They require neither prior history nor an ownership database. Revert requires the chosen run's history. See
[operation semantics](../architecture.md#apply-discard-and-revert) for the discard/revert distinction.

```sh
# Default and explicit apply
./cook ./recipe.groovy
./cook apply ./recipe.groovy

# Discard the contributions declared by this supplied recipe
./cook discard --batch-approve ./recipe.groovy

# Reverse a recorded operation; replace RUN_ID with its identifier
./cook revert --batch-approve RUN_ID
```

These are the target operations. The retired lifecycle and management commands are not compatibility aliases.
Discard is a command, not a flag. The sole preapproval spelling is `--batch-approve`; earlier tentative spellings
are not aliases.

## Recipe operands

A recipe operand is an exact file path or supported source URI. Local paths, `file://`, and `https://` are supported.
An ordinary relative path such as `recipe.groovy` is accepted. Cook does not look up implicit cookbook names.

```sh
./cook recipe.groovy
./cook ./recipes/project.groovy
./cook /path/to/recipe.groovy
./cook file:///path/to/recipe.groovy
./cook https://example.com/recipes/project.groovy
```

Initial bare `apply`, `discard`, and `revert` are reserved command names that select an operation. To apply a recipe
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
Recipes and their operations compose left to right. Later operations can replace earlier values at the same target;
Cook displays one combined final diff; approval covers the entire batch.

An argument value that looks like a recipe path remains an argument value:

```sh
./cook apply ./a.groovy --source=./b.groovy
./cook apply ./a.groovy ./b.groovy
```

The first invocation evaluates only `a.groovy` and gives it `source` with the value `./b.groovy`. The second evaluates
`a.groovy`, then `b.groovy`, as two recipes. These examples specify CLI scope; they do not decide the deferred
recipe parameter-injection API.

## Batch options and approval

Batch options precede the first recipe operand. For revert, they precede the run ID. `--batch-approve` applies to
the whole operation: display the actual diff and proceed without prompting. Without it, ask before changing target
files. Noninteractive execution without this flag does not silently authorize writes.

```sh
./cook --batch-approve ./recipe.groovy
./cook apply --batch-approve ./a.groovy ./b.groovy
./cook discard --batch-approve ./a.groovy ./b.groovy
./cook revert --batch-approve RUN_ID
```

A batch option after a recipe receives the normal unknown-option error:

```sh
# Misplaced batch option: unknown-option error
./cook apply ./a.groovy --batch-approve

# Correct scope: batch option before the first recipe
./cook apply --batch-approve ./a.groovy
```

Approval covers all files and hunks in the batch. Existing or edited content follows this same diff and
batch-approval flow, without an ownership-state prerequisite or a manual-edit veto. The target CLI has no `--adopt`
flag, adoption alias, or separate adoption step. See
[batch review and recovery](../architecture.md#batch-approval-and-failure-recovery) for the write and rollback contract.
