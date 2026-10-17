# Cook recipe contributions

## Objective

**Problem.** Programs read native configuration that people and other tools also edit. Copying a setup change into
those files by hand overwrites edits and leaves no reviewable record.

**Goal.** A developer proposes a [recipe](glossary.md)'s file changes against current content, reviews the combined
diff, and approves the batch while content outside the declared targets stays intact.

This page follows the intended [architecture](../architecture.md#evaluation-review-and-writes), which owns the
requirements. For commands supported by the current implementation, see the [root README](../../README.md).

## Overview

Cooking evaluates trusted Groovy recipes into an ordered batch of file operations. Ingredients model programs' native
files and configuration; cookbooks supply ingredients and recipes separately from the engine. The CLI accepts recipe
paths inside the destination repository, `https://` URLs or coordinates.

```sh
./cook ./recipe.groovy
./cook --batch-approve ./base.groovy --agent=codex ./project.groovy --agent=claude
```

## Reference

```text
./cook [batch-options] <recipe> [recipe-arguments] [<recipe> [recipe-arguments]]...
```

Cooking takes no command word. Batch options precede recipe operands; recipe arguments follow their recipe as
`--name=value` or `--name value`. To review interactively, omit `--batch-approve` and inspect the diff before
answering the whole-batch prompt. The [CLI reference](cli.md) defines the grammar, operand and option scope.

## Ordered operations

Recipes and their operations compose left to right against current content. When two recipes write the same
section, key, or file, the last one wins and becomes its owner; the diff is the only notice. Cook displays one final
combined diff for the whole batch.

Suppose recipe A sets `test = "A"` in `settings.toml` and creates `notes.txt`, and recipe B sets `test = "B"`.
Cooking A followed by B previews B's final value and A's new file, and B owns `test`. Cooking B alone after an
earlier run that cooked A updates `test`, moves its ownership to B, and leaves `notes.txt` intact. Cooking B never
removes A's output, and omitting a recipe from an invocation leaves its outputs in place. Cooking a new version of A
removes what A's previous version owned and the new version omits, even content someone edited; the diff shows that
removal, so review deletions as carefully as additions.

Every invocation evaluates the supplied recipe versions, including an invocation identical to an earlier one.
Nothing marks a recipe as already cooked. An unchanged result has an empty diff.

## Declared outputs

A batch affects the targets its recipes declare and the targets they owned before. A recipe may declare native files
such as generated configuration, wrapper files, agent instructions, skills, hooks, or ignore entries; the configured
programs consume those files directly. Recipes may configure a program's own permissions, skills, or hook settings in
the files that program consumes.

| Target | Address | Effect of cooking |
| --- | --- | --- |
| Whole file | File path | Propose the declared whole-file addition or update |
| Structured content | File path and key path | Propose the declared key change, preserving unrelated keys |
| Shared text | File path and begin/end markers | Propose the section change, preserving text outside its boundaries |
| Directory or symlink | Declared path, where applicable | Include the declared filesystem operation in the batch |

### Structured and whole-file targets

Structured operations target keys. A declared `test` key can change while an unrelated `other` key remains.
Whole-file targets have whole-file scope, so review their full diff. The recipe's declarations determine that scope;
the committed `cook.lock` records the sections, keys, and files each recipe owns.

Existing or edited content follows the same diff and approval flow. Ownership never blocks a change: a developer's
manual edit appears in the next diff like any other difference.

## Sections and markers

A section is a marker-delimited part of a shared text file. Its markers name the recipe that owns it and the region
within that recipe. A recipe can address a section of a shared file while preserving prose outside it, and several
recipes can keep separate sections in one file. Markers use the file format's comment syntax:

| File kind | Opening marker | Closing marker |
| --- | --- | --- |
| Markdown | `<!-- cook:begin <recipe>#<region> -->` | `<!-- cook:end <recipe>#<region> -->` |
| Hash-comment formats: ignore files, YAML, TOML, and shell scripts | `# cook:begin <recipe>#<region>` | `# cook:end <recipe>#<region>` |

```markdown
Text people wrote outside the section.

<!-- cook:begin agentic/context#commands -->
Run the project's build command.
<!-- cook:end agentic/context#commands -->

More unrelated text.
```

### Short names and regions

A marker names the owning recipe's short name, such as `agentic/context`, followed by `#` and the region within that
recipe, such as `commands`. Each recipe has a short name that markers and `cook.lock` use. Manual edits within the
section are included in the next actual diff; ownership never blocks them.

Omitting a recipe from a later invocation leaves its section in place; a new version of the recipe that omits
the section removes it. To remove all of a recipe's output, [discard](discard.md) the recipe.

### Instruction files

Recipes can write sections in `AGENTS.md`, `README.md`, or another program's instruction file. Keep instructions
concise and put supporting detail in linked documentation when useful. Instruction content and its size belong to the
recipe's configuration choices.

## Review and recovery

1. Evaluate supplied recipes against current content and compose their declared operations in order.
2. Display the final combined diff in the terminal.
3. Ask for approval of the entire batch before changing target files. With `--batch-approve`, display the diff and
   proceed without prompting. Noninteractive execution without that flag does not authorize writes.
4. Perform the approved writes and update `cook.lock`. On a write failure, roll back changes already made and report
   the failure; the files stay as they were.

The core writes and records only accepted changes. The CLI accepts the whole batch, covering all files and hunks;
choosing individual changes is a separate interface decision.

Recipes are trusted executable code. Diff, approval, output boundaries, and rollback govern declared batch
operations; they are not a sandbox or protection against arbitrary side effects during recipe evaluation.

## Destination and source paths

The invocation directory is the destination, regardless of the executable location. Declared writes and
deletions stay within it, including paths resolved through symlinks. A symlink cannot route a declared target outside
that boundary.

Recipes come from paths inside the destination repository, `https://` URLs or coordinates. Relative recipe/template
source references resolve from the referencing recipe's location: inside the repository for a local recipe, and against
the source URL for a remote one. A local operand or reference that resolves outside the repository is refused. For
example, when invoked from `/work/project`:

| Recipe operand | Template reference in that recipe | Resolved source | Output declared as `settings.toml` |
| --- | --- | --- | --- |
| `recipes/project.groovy` | `../templates/settings.toml` | `templates/settings.toml` | `/work/project/settings.toml` |
| `https://example.com/recipes/project.groovy` | `../templates/settings.toml` | `https://example.com/templates/settings.toml` | `/work/project/settings.toml` |

## Without the lock

Cooking does not require `cook.lock`; without a lock entry for a recipe, it removes nothing on that recipe's behalf.
Discard needs the lock, because the lock is its only record of what a recipe owns.
