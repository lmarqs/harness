# Apply recipe contributions

## Objective

Programs read native configuration that people and other tools also edit. Cook lets a developer propose a recipe's
file changes against that current content, review the combined diff, and approve the batch while preserving content
outside the declared targets.

This page follows the intended [architecture](../architecture.md#evaluation-review-and-writes). For commands
supported by the current implementation, see the [root README](../../README.md). The design examples here do not
establish that the target behavior is implemented.

## Overview

Apply evaluates trusted Groovy recipes into an ordered batch of file operations. Ingredients model programs' native
files and configuration; cookbooks supply ingredients and recipes separately from the engine. The CLI accepts recipe
paths inside the destination repository or `https://` URLs, with no implicit cookbook-name lookup.

```sh
./cook ./recipe.groovy
./cook apply ./recipe.groovy
./cook apply --batch-approve ./base.groovy --agent=codex ./project.groovy --agent=claude
```

Apply is the default. Batch options precede recipe operands; recipe arguments follow their recipe as `--name=value`.
See the [CLI reference](cli.md) for the full grammar. Render flags and commands are not compatibility aliases.

## Ordered operations

Recipes and their operations compose left to right against current content. When two recipes write the same
section, key, or file, the last one wins and becomes its owner; the diff is the only notice. Cook displays one final
combined diff for the whole batch, without requiring separate approval for intermediate values.

Suppose recipe A sets `test = "A"` in `settings.toml` and creates `notes.txt`, and recipe B sets `test = "B"`.
Applying A followed by B previews B's final value and A's new file, and B owns `test`. Applying B alone after an
earlier apply of A updates `test`, moves its ownership to B, and leaves `notes.txt` intact. Applying B never removes
A's output, and omitting a recipe does not withdraw its outputs. Applying a new version of A removes what A's
previous version owned and the new version no longer declares; the diff shows that removal.

Every apply invocation evaluates the supplied recipe versions, including an invocation identical to an earlier
one. History does not mark recipes as already applied or skip them. An unchanged result has an empty diff.

## Declared outputs

A batch affects the targets its recipes declare and the targets they owned before. There is no engine-mandated
catalog of generated configuration, wrapper files, agent instructions, skills, hooks, or ignore entries. A recipe may
declare those native files; configuring a program does not make Cook its runner.

| Target | Address | Effect of apply |
| --- | --- | --- |
| Whole file | File path | Propose the declared whole-file addition or update |
| Structured content | File path and key path | Propose the declared key change, preserving unrelated keys |
| Shared text | File path and begin/end markers | Propose the section change, preserving text outside its boundaries |
| Directory or symlink | Declared path, where applicable | Include the declared filesystem operation in the batch |

### Merge-rendering native files

Structured operations target keys rather than claiming ownership of the whole native file. A declared `test` key
can change while an unrelated `other` key remains. Whole-file targets have whole-file scope, so review their full
diff. The recipe's declarations determine that scope; the committed `cook.lock` records the sections, keys, and
files each recipe owns.

Existing or edited content follows the same diff and approval flow. Ownership never blocks a change: a developer's
manual edit appears in the next diff like any other difference. Cook requires no `--adopt` flag, compatibility alias,
or separate adoption step.

### Native program files

Recipes may configure a program's own permissions, skills, or hook settings in the files that program consumes.
Executing external commands is not a batch action. Cook does not prescribe a universal hook command, event mapping,
safety-floor policy, or lifecycle engine. Recipe/model APIs and lifecycle hooks require a separate discussion.

## Managed blocks

Markers bound a section and name the recipe that owns it and the region within that recipe. A recipe can address a
section of a shared file while preserving prose outside it, and several recipes can keep separate sections in one
file. Use the file format's comment syntax:

| File kind | Opening marker | Closing marker |
| --- | --- | --- |
| Markdown | `<!-- cook:begin <recipe>/<region> -->` | `<!-- cook:end <recipe>/<region> -->` |
| Hash-comment formats: ignore files, YAML, TOML, and shell scripts | `# cook:begin <recipe>/<region>` | `# cook:end <recipe>/<region>` |

```markdown
Text people wrote outside the section.

<!-- cook:begin agentic/context/commands -->
Run the project's build command.
<!-- cook:end agentic/context/commands -->

More unrelated text.
```

### Block ids

A block ID has the form `<recipe>/<region>`: the owning recipe's short name, such as `agentic/context`, followed by
the region within that recipe. A recipe declares its short name; without a declaration, the name derives from the
recipe's path. The block ID is not an engine-wide module catalog. Manual edits within the section are included in the
next actual diff; ownership never blocks them. Removing a recipe from a later apply invocation does not delete its
section; a new version of the recipe that no longer declares the section removes it. [Discard](remove.md#discard) is
deferred; revert the batches that applied a recipe to undo its output.

### Instruction files

Recipes can write sections in `AGENTS.md`, `README.md`, or another program's instruction file. Keep instructions
concise and put supporting detail in linked documentation when useful. The engine does not mandate the former fixed
line budgets, block classes, or `explain`/`doctor` commands. Instruction content and its size belong to the recipe's
configuration choices.

## Review and recovery

1. Evaluate supplied recipes against current content and compose their declared operations in order.
2. Display the final combined diff in the terminal.
3. Ask for approval of the entire batch before changing target files. With `--batch-approve`, display the diff and
   proceed without prompting. Noninteractive execution without that flag does not authorize writes.
4. Save the batch record before the first target write: one reversible patch per touched file, `cook.lock`
   included, plus the sources and batch information, under the destination's ignored `.cook/batches/<batchId>/`
   directory.
5. Perform the approved writes and update `cook.lock`. On a write failure, roll back changes already made and retain
   the batch record and failure.

The core writes and records only accepted changes. The CLI accepts the whole batch, covering all files and hunks;
choosing individual changes belongs to a separate interface decision. Terminal review takes the place of render
drift-check and dry-run flags, which the target CLI does not have.

Recipes are trusted executable code. Diff, approval, output boundaries, and rollback govern declared batch
operations; they are not a sandbox or protection against arbitrary side effects during recipe evaluation.

## Destination and source paths

The invocation directory is the destination, regardless of the executable location. Declared writes and
deletions stay within it, including paths resolved through symlinks. A symlink cannot route a declared target outside
that boundary.

Recipes come from paths inside the destination repository or `https://` URLs. Relative recipe/template source
references resolve from the referencing recipe's location: inside the repository for a local recipe, and against
the source URL for a remote one. A local operand or reference that resolves outside the repository is refused. For
example, when invoked from `/work/project`:

| Recipe operand | Template reference in that recipe | Resolved source | Output declared as `settings.toml` |
| --- | --- | --- | --- |
| `cookbook/recipes/project.groovy` | `../templates/settings.toml` | `cookbook/templates/settings.toml` | `/work/project/settings.toml` |
| `https://example.com/cookbook/recipes/project.groovy` | `../templates/settings.toml` | `https://example.com/cookbook/templates/settings.toml` | `/work/project/settings.toml` |

All consumed recipe/template sources are captured, including local sources and the recipes actually evaluated.
Recording their origins preserves relative resolution when the sources are snapshotted. Multiple recipes touching
one file produce one patch for the combined change and one batch record for the whole batch. A single patch
supports forward and reverse application. Arguments, execution order, and result or failure log are recorded too;
no patch representation or metadata serialization format is specified here.

Apply requires neither batch history nor `cook.lock`; without a lock entry for a recipe, it removes nothing on that
recipe's behalf. Losing `.cook/` loses the ability to revert earlier batches, not the ability to apply recipes;
ownership survives in the committed `cook.lock`. Reverting a batch requires its record. See
[architecture: paths and history](../architecture.md#paths-source-snapshots-and-local-history).

## Reference

```text
./cook [batch-options] <recipe> [recipe-arguments] [<recipe> [recipe-arguments]]...
./cook apply [batch-options] <recipe> [recipe-arguments] [<recipe> [recipe-arguments]]...
```

`--batch-approve` is the sole preapproval spelling. To review interactively, omit it and inspect the diff before
answering the whole-batch prompt. Recipe arguments use `--name=value`, with shell quoting for values containing
spaces. The [CLI reference](cli.md) defines operand and option scope.

To reverse a recorded batch, or for the deferred discard, see [discard and revert](remove.md).
