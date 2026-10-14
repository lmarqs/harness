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
files and configuration; cookbooks supply ingredients and recipes separately from the engine. The CLI accepts exact
recipe paths or supported source URIs, with no implicit cookbook-name lookup.

```sh
./cook ./recipe.groovy
./cook apply ./recipe.groovy
./cook apply --batch-approve ./base.groovy --agent=codex ./project.groovy --agent=claude
```

Apply is the default. Batch options precede recipe operands; recipe arguments follow their recipe as `--name=value`.
See the [CLI reference](cli.md) for the full grammar. Apply replaces the earlier render command contract; retired
render flags and commands are not compatibility aliases.

## Ordered operations

Recipes and their operations compose left to right against current content. Later instructions can replace earlier
values at the same target. Cook displays one final combined diff for the whole batch, without requiring separate
approval for intermediate values.

Suppose recipe A sets `test = "A"` in `settings.toml` and creates `notes.txt`, and recipe B sets `test = "B"`.
Applying A followed by B previews B's final value and A's new file. Applying B alone after an earlier apply of A
updates `test` and leaves `notes.txt` intact. Omitting a recipe does not withdraw its unrelated outputs.

Every apply invocation evaluates the supplied recipe versions, including an invocation identical to an earlier
one. History does not mark recipes as already applied or skip them. An unchanged result may have an empty diff.

## Declared outputs

A batch affects the targets its recipes declare. There is no engine-mandated catalog of generated configuration,
wrapper files, agent instructions, skills, hooks, or ignore entries. A recipe may declare those native files;
configuring a program does not make Cook its runner.

| Target | Address | Effect of apply |
| --- | --- | --- |
| Whole file | File path | Propose the declared whole-file addition or update |
| Structured content | File path and key path | Propose the declared key change, preserving unrelated keys |
| Shared text | File path and begin/end markers | Propose the section change, preserving text outside its boundaries |
| Directory or symlink | Declared path, where applicable | Include the declared filesystem operation in the batch |

### Merge-rendering native files

Structured operations target keys rather than claiming ownership of the whole native file. A declared `test` key
can change while an unrelated `other` key remains. Whole-file targets have whole-file scope, so review their full
diff. The recipe's declarations determine that scope, rather than a persistent ownership manifest.

Existing or edited content follows the same diff and approval flow. A developer's manual edit is not an ownership
or drift veto. Cook requires no ownership-state reconciliation, `--adopt` flag, compatibility alias, or separate
adoption step.

### Native program files

Recipes may configure a program's own permissions, skills, or hook settings in the files that program consumes.
Executing external commands is not a batch action. Cook does not prescribe a universal hook command, event mapping,
safety-floor policy, or lifecycle engine. Recipe/model APIs and lifecycle hooks require a separate discussion.

## Managed blocks

Markers identify text boundaries, not ownership. A recipe can address a section of a shared file while preserving
prose outside it. Use the file format's appropriate comment syntax:

| File kind | Opening marker | Closing marker |
| --- | --- | --- |
| Markdown | `<!-- cook:<id> -->` | `<!-- /cook:<id> -->` |
| Hash-comment formats, such as TOML and ignore files | `# cook:<id>` | `# /cook:<id>` |

```markdown
Text people wrote outside the section.

<!-- cook:commands -->
Run the project's build command.
<!-- /cook:commands -->

More unrelated text.
```

### Block ids

A block ID identifies a section's begin/end boundaries. It does not establish a persistent recipe identity, owner,
or an engine-wide module catalog. The recipe declares which section it addresses. Manual edits within the section
are included in the next actual diff; they do not trigger an adoption gate. Removing a recipe from a later apply
invocation does not delete its section. Use [discard](remove.md#discard) to delete declared contributions.

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
4. Save one reversible patch per touched file before the first target write, and capture the sources and run
   information for the whole batch under the destination's ignored `.cook/` directory.
5. Perform the approved writes. On a write failure, roll back changes already made and retain the patches and failure
   record.

Approval covers all files and hunks; partial selection is outside the design. Terminal review replaces the old
render drift-check and dry-run contracts, without adding those flags to the target CLI.

Recipes are trusted executable code. Diff, approval, output boundaries, and rollback govern declared batch
operations; they are not a sandbox or protection against arbitrary side effects during recipe evaluation.

## Destination and source paths

The invocation directory is the destination, regardless of the recipe or executable location. Declared writes and
deletions stay within it, including paths resolved through symlinks. A symlink cannot route a declared target outside
that boundary.

Relative recipe/template source references resolve from the referencing recipe's original location. HTTPS
references resolve against its source URL. For example, when invoked from `/work/project`:

| Recipe operand | Template reference in that recipe | Resolved source | Output declared as `settings.toml` |
| --- | --- | --- | --- |
| `/home/dev/cookbook/recipes/project.groovy` | `../templates/settings.toml` | `/home/dev/cookbook/templates/settings.toml` | `/work/project/settings.toml` |
| `https://example.com/cookbook/recipes/project.groovy` | `../templates/settings.toml` | `https://example.com/cookbook/templates/settings.toml` | `/work/project/settings.toml` |

All consumed recipe/template sources are captured, including local sources and the recipes actually evaluated.
Recording their origins preserves relative resolution when the sources are snapshotted. Multiple recipes touching
one file produce one patch for the combined change and one recorded operation for the whole batch. A single patch
supports forward and reverse application. Arguments, execution order, operation, and result or failure log are
recorded too; no patch representation or metadata serialization format is specified here.

Apply requires no previous history or ownership database. Losing `.cook/` loses local recovery information, not the
ability to apply or discard. New operations still save recovery patches before writes. Reverting a run requires that
run's history. See [architecture: paths and history](../architecture.md#paths-source-snapshots-and-local-history).

## Reference

```text
./cook [batch-options] <recipe> [recipe-arguments] [<recipe> [recipe-arguments]]...
./cook apply [batch-options] <recipe> [recipe-arguments] [<recipe> [recipe-arguments]]...
```

`--batch-approve` is the sole preapproval spelling. To review interactively, omit it and inspect the diff before
answering the whole-batch prompt. Recipe arguments use `--name=value`, with shell quoting for values containing
spaces. The [CLI reference](cli.md) defines operand and option scope.

To delete a supplied recipe's declared contributions or reverse a recorded run, see [discard and revert](remove.md).
