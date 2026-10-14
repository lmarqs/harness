# Recipes

## Objective

**Problem.** Copying program setup between repositories by hand makes improvements hard to reuse and changes hard to
review. A recipe also needs a clear boundary between describing file changes and running the configured workflow.

**Goal.** Authors can express reusable setup changes, and callers can understand their ordered result, review it and
choose whether to apply, discard or reverse an operation.

## Overview

A recipe is trusted executable Groovy code that describes requested file operations using
[ingredients](ingredients.md), the program data models. [Cook architecture](../architecture.md) states the intended
engine contract: evaluate recipes into an ordered batch, display the final diff, obtain whole-batch approval, save
recovery information and perform writes.

Cookbook repositories hold and ship ingredients and recipes, with builds that can be separate from Cook's build.
Recipes can express a team's setup choices using program-specific models, while configured programs consume their
native files directly. Cooking is exposed through the CLI; Gradle builds and packages Cook and cookbooks.

This page describes the intended design. For current commands and supplier behavior, use the
[root README](../../README.md), [core implementation guide](../../core/README.md) and cookbook usage guides.
Ingredient/recipe APIs, nesting, annotations, injection and hooks remain a separate model discussion. The CLI examples
below do not specify how a recipe receives arguments or composes models internally.

## Invoking recipes

Pass exact recipe file paths or supported source URIs, including local paths, `file://` and `https://`. Ordinary
relative paths such as `recipe.groovy` are accepted; Cook does not perform implicit cookbook-name lookup.

```sh
./cook ./recipe.groovy
./cook apply ./recipe.groovy
./cook apply ./base.groovy --agent=codex ./project.groovy --agent=claude
./cook apply https://example.com/cookbook/recipes/project.groovy
```

Apply is the default. Each recipe's arguments follow its operand as `--name=value`; the next recipe begins its own
argument scope. Shell quoting keeps a value containing spaces together. Batch options precede the first recipe.
See the [CLI reference](cli.md) for the full grammar and reserved initial command names.

## Ordered composition

Recipes and their operations execute in declared order, left to right. Overlap is intentional: later instructions
can replace earlier values. If a base recipe sets a key to `base` and a project recipe sets it to `project`, the final
combined preview shows `project`. Cook presents one diff for the whole batch rather than asking for approval of each
intermediate result.

Applying the project recipe alone later leaves unrelated output from the base recipe intact. The supplied recipes
request changes at their declared targets; absence from an invocation is not a deletion request. Every apply/discard
invocation evaluates the supplied versions against current content, even after an identical earlier invocation.
An unchanged result may produce an empty diff; history does not mark recipes as already applied.

Declared targets can be whole files, structured key paths or bounded sections in shared text files. Whole-file
operations concern the whole file; key operations preserve unrelated keys, and section operations preserve text
outside their boundaries. Markers use the file format's comment syntax and identify boundaries rather than ownership.
See [apply](render.md).

## Review and trust

Read recipe code and its sources before evaluation. Recipes are trusted code, and can cause effects outside their
declared operations. Batch review, output boundaries and rollback are not a sandbox or a guarantee against those
effects. External command execution is not a batch action, even when a recipe writes configuration naming commands
that another program will execute later.

Cook proposes changes against current content, including existing files and manual edits. There is no `--adopt`,
adoption alias, separate adoption step or ownership reconciliation. Cook displays the actual final terminal diff and
asks before target writes unless `--batch-approve` precedes the recipes. With that flag, it still displays the diff
and proceeds without prompting. Approval covers the whole batch; noninteractive execution alone does not authorize
writes. See [safety](safety.md) for trust and configuration review guidance.

## Sources and destination

The invocation directory is the destination regardless of recipe or executable location. Declared writes and
deletions remain within that directory, including paths resolved through symlinks.

Relative source recipe and template references resolve from the referencing recipe's original location, or against
its source URL for HTTPS sources. For example, a recipe at `/home/dev/book/recipes/project.groovy` referring to
`../templates/settings.toml` reads `/home/dev/book/templates/settings.toml`, even when invoked from `/work/project`.
Its declared `settings.toml` output goes to `/work/project/settings.toml`. This resolution rule selects no nesting or
template-loading API.

History captures the recipe sources actually evaluated and all consumed recipe/template sources, local and downloaded,
with origins, arguments and execution order. Capturing a source must not change relative source resolution. See
[paths and history](../architecture.md#paths-source-snapshots-and-local-history).

## Discard and recorded reversal

Users manage recipe versions and supply the version they intend to apply or discard. Cook does not infer a persistent
recipe identity or retrieve a previously applied version automatically.

```sh
./cook discard ./recipe.groovy
./cook revert RUN_ID
```

Discard evaluates the supplied recipe version and proposes deleting its declared contributions, including values
that have since been edited. It preserves unrelated keys or sections. Revert instead uses the named run's history to
restore that operation's prior values against current content without evaluating recipes again. Neither operation
vetoes changes merely because of manual edits; each presents its actual diff for whole-batch approval.

For a key changed by an apply run from `old` to `new`, then manually to `custom`, discard proposes deleting the key.
Reverting the original run instead proposes `custom` to `old`. Apply/discard require no prior history; revert requires
that run's record. See [discard and revert](remove.md).

One batch operation has a separate run record in the destination's ignored `.cook/` directory and one reversible patch
per touched file. Patches are saved before the first target write. On a write failure, Cook rolls back changes already
made by the batch and retains patches and failure information. This is file-operation recovery, not a workflow
verification receipt. Patch representation, metadata schema and overlapping reversal mechanics remain separate design.

## Workflow boundary

Recipes describe file changes, not passes through a fixed development lifecycle. Programs and teams execute checks,
track tasks, launch agents and arrange delivery independently of Cook. Native workflow configuration can be useful
recipe output without making those activities engine responsibilities. See [workflow boundaries](lifecycle.md).

Personal setup backup (kit) and agent-session orchestration remain future work in
[issue #5](https://github.com/lmarqs/the-cook/issues/5) and
[issue #6](https://github.com/lmarqs/the-cook/issues/6). This reference does not define their protocols or recipe hooks.
