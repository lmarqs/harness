# Recipes

## Objective

**Problem.** Copying program setup between repositories by hand makes improvements hard to reuse and changes hard to
review. A recipe also needs a clear boundary between describing file changes and running the configured workflow.

**Goal.** Authors can express reusable setup changes, and callers can understand their ordered result, review it and
choose whether to apply a recipe or revert a batch.

## Overview

A [recipe](glossary.md) is trusted executable Groovy code that describes requested file operations using
[ingredients](ingredients.md), the program data models. [Cook architecture](../architecture.md) states the intended
engine contract: evaluate recipes into an ordered batch, display the final diff, obtain whole-batch approval, save
the batch record and perform writes.

Each recipe has a short name, such as `agentic/context`, that section markers and `cook.lock` use to identify it.

Cookbook repositories hold and ship ingredients and recipes, with builds that can be separate from Cook's build.
Recipes can express a team's setup choices using program-specific models, while configured programs consume their
native files directly. Recipes are applied through the CLI; Gradle builds and packages Cook and cookbooks.

This page describes the intended design. For current commands and behavior, use the [root README](../../README.md),
[core implementation guide](../../core/README.md) and recipe READMEs.

## Invoking recipes

Pass recipe paths inside the destination repository or `https://` URLs. Ordinary relative paths such as
`recipe.groovy` are accepted; Cook refuses operands outside the repository.

```sh
./cook ./recipe.groovy
./cook apply ./recipe.groovy
./cook apply ./base.groovy --agent=codex ./project.groovy --agent=claude
./cook apply https://example.com/cookbook/recipes/project.groovy
```

Apply is the default. Each recipe's arguments follow its operand as `--name=value`, and batch options precede the
first recipe. See the [CLI reference](cli.md) for the full grammar and reserved initial command names.

## Ordered composition

If a base recipe sets a key to `base` and a later project recipe sets it to `project`, the combined diff shows
`project` and the project recipe owns the key, because the last recipe to write a target wins. Applying one recipe
never removes another recipe's output, and a new version of a recipe removes what its previous version owned and the
new version omits. Every apply invocation evaluates the supplied versions against current content. See
[ordered operations](apply.md#ordered-operations).

Declared targets can be whole files, structured key paths or sections in shared text files, bounded by
`cook:begin <recipe>#<region>` and `cook:end <recipe>#<region>` markers. See
[sections and markers](apply.md#sections-and-markers).

## Review and trust

Read recipe code and its sources before evaluation. Recipes are trusted code, and can cause effects outside their
declared operations. Batch review, output boundaries and rollback are not a sandbox or a guarantee against those
effects. External command execution is not a batch action, even when a recipe writes configuration naming commands
that another program will execute later.

Cook proposes changes against current content, including existing files and manual edits; ownership recorded in
`cook.lock` never blocks a change. Approval covers the whole batch; noninteractive execution alone does not authorize
writes. See [review and recovery](apply.md#review-and-recovery) and [safety](safety.md) for trust and configuration
review guidance.

## Sources and destination

Writes stay within the invocation directory, and relative recipe and template references resolve from the
referencing recipe's location; see [destination and source paths](apply.md#destination-and-source-paths) for the
rules and a worked example. The [batch record](apply.md#batch-record) captures the sources each batch consumed.

## Discard and recorded reversal

Users manage recipe versions and supply the version they intend to apply. The committed `cook.lock` records each
applied recipe's short name, source, sha256 and owned targets; see
[ownership and the lock](../architecture.md#ownership-and-the-lock).

To undo a recipe's output, [revert](revert.md#revert) the batches that applied it: revert applies a batch's reverse
patches without evaluating recipes again and writes nothing when a later edit overlaps. Discard is deferred.

## Workflow boundary

Recipes describe file changes. Programs and teams execute checks, track tasks, launch agents and arrange delivery
independently of Cook. Native workflow configuration can be useful recipe output. See
[workflow boundaries](lifecycle.md).
