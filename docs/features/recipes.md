# Recipes

## Objective

**Problem.** Copying program setup between repositories by hand makes improvements hard to reuse and changes hard to
review. A recipe also needs a clear boundary between describing file changes and running the configured workflow.

**Goal.** Authors can express reusable setup changes, and callers can understand their ordered result, review it and
choose whether to apply a recipe or revert a batch.

## Overview

A recipe is trusted executable Groovy code that describes requested file operations using
[ingredients](ingredients.md), the program data models. [Cook architecture](../architecture.md) states the intended
engine contract: evaluate recipes into an ordered batch, display the final diff, obtain whole-batch approval, save
the batch record and perform writes.

Each recipe has a short name, such as `agentic/context`, that section markers and `cook.lock` use to identify it. A
recipe declares its short name; without a declaration, the name derives from the recipe's path.

Cookbook repositories hold and ship ingredients and recipes, with builds that can be separate from Cook's build.
Recipes can express a team's setup choices using program-specific models, while configured programs consume their
native files directly. Cooking is exposed through the CLI; Gradle builds and packages Cook and cookbooks.

This page describes the intended design. For current commands and supplier behavior, use the
[root README](../../README.md), [core implementation guide](../../core/README.md) and recipe READMEs.

## Invoking recipes

Pass recipe paths inside the destination repository or `https://` URLs. Ordinary relative paths such as
`recipe.groovy` are accepted; Cook refuses operands outside the repository.

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

Recipes and their operations execute in declared order, left to right. When two recipes write the same section, key
or file, the last one wins and becomes its owner; the diff is the only notice. If a base recipe sets a key to `base`
and a project recipe sets it to `project`, the final combined preview shows `project`, and the project recipe owns
the key. Cook presents one diff for the whole batch.

Applying the project recipe alone later leaves the base recipe's output intact; applying one recipe never removes
another recipe's output, and absence from an invocation is not a deletion request. Applying a new version of a recipe
removes what its previous version owned and the new version no longer declares, and the diff shows that removal.
Every apply invocation evaluates the supplied versions against current content, even after an identical earlier
invocation. An unchanged result produces an empty diff; history does not mark recipes as already applied.

Declared targets can be whole files, structured key paths or bounded sections in shared text files. Whole-file
operations concern the whole file; key operations preserve unrelated keys, and section operations preserve text
outside their boundaries. Section markers, `cook:begin <recipe>/<region>` and `cook:end <recipe>/<region>` in the
file format's comment syntax, identify the owning recipe and region. See [apply](render.md).

## Review and trust

Read recipe code and its sources before evaluation. Recipes are trusted code, and can cause effects outside their
declared operations. Batch review, output boundaries and rollback are not a sandbox or a guarantee against those
effects. External command execution is not a batch action, even when a recipe writes configuration naming commands
that another program will execute later.

Cook proposes changes against current content, including existing files and manual edits. Ownership recorded in
`cook.lock` never blocks a change. Cook displays the actual final terminal diff and asks before target writes unless
`--batch-approve` precedes the recipes. With that flag, it still displays the diff and proceeds without prompting.
Approval covers the whole batch; noninteractive execution alone does not authorize writes. See [safety](safety.md) for
trust and configuration review guidance.

## Sources and destination

The invocation directory is the destination regardless of executable location. Declared writes and
deletions remain within that directory, including paths resolved through symlinks.

Recipes come from paths inside the destination repository or `https://` URLs. Relative source recipe and template
references resolve from the referencing recipe's location: inside the repository for a local recipe, or against its
source URL for HTTPS sources. A local reference that resolves outside the repository is refused. For example, when
invoked from `/work/project`, a recipe at `cookbook/recipes/project.groovy` referring to `../templates/settings.toml`
reads `cookbook/templates/settings.toml`. Its declared `settings.toml` output goes to `/work/project/settings.toml`.

The batch record captures the recipe sources actually evaluated and all consumed recipe/template sources, local and
downloaded, with origins, arguments and execution order. Capturing a source must not change relative source resolution.
See [paths and history](../architecture.md#paths-source-snapshots-and-local-history).

## Discard and recorded reversal

Users manage recipe versions and supply the version they intend to apply. The committed `cook.lock` records, for
each applied recipe, its short name, its source as a repository-relative path or `https://` URL, the source's sha256
and the sections, keys and files it owns; it records no recipe arguments.

```sh
./cook revert 2026-10-14T15-04-05Z
```

Revert applies the named batch's reverse patches against current content without evaluating recipes again, and
restores the `cook.lock` ownership the batch changed. Later edits that do not overlap the batch's changes survive; an
overlapping edit makes the revert write nothing and list each conflicting file and section. Discard, which removes
what `cook.lock` lists for a recipe without evaluating it, is deferred; revert the batches that applied the recipe
instead.

For a key changed by an apply batch from `old` to `new`, reverting that batch proposes `new` to `old`. If the key was
since edited by hand to `custom`, the revert refuses and names the file and key. Apply requires neither batch history
nor `cook.lock`; revert requires that batch's record. See [discard and revert](remove.md).

Each batch has one record in the destination's ignored `.cook/batches/<batchId>/` directory, with one reversible patch
per touched file, `cook.lock` included. The record is saved before the first target write. On a write failure, Cook
rolls back changes already made by the batch and retains the record and failure information. Patch representation
and metadata schema belong to the implementation.

## Workflow boundary

Recipes describe file changes. Programs and teams execute checks, track tasks, launch agents and arrange delivery
independently of Cook. Native workflow configuration can be useful recipe output. See
[workflow boundaries](lifecycle.md).
