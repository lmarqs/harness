# User-facing principles

## Objective

**Problem.** Specific rules cannot foresee every case. Convenience, preserving someone's work and sharing setup can
pull in different directions. Without a common basis, contributors make inconsistent choices about what Cook changes
and what a user must approve.

**Goal.** Contributors can resolve an uncovered case from the same responsibilities and explain the result to the
person whose files are affected.

## Overview

These principles follow the [architecture](../architecture.md), which states the intended design. The
[glossary](../features/glossary.md) defines the engine terms used here, such as ingredient, cookbook, batch and
destination. Apply the principles when a specific interaction rule leaves a question open, and record the reason in
the relevant standard ([changing a standard](README.md#changing-a-standard)). Current execution instructions belong
in the [root README](../../README.md) and [core guide](../../core/README.md).

## 1. Optional and transparent

Programs consume their native configuration directly. Using Cook to prepare those files should not make teammates
invoke Cook to read them or perform their ordinary work. Why: teammates share configuration without necessarily
choosing the tool that produced it.

- [Ingredients](../features/glossary.md) model native program files; cookbooks hold and ship ingredients and recipes
  separately from the engine. Recipes can configure build, test or CI files when requested; those programs run their
  own workflows.
- Personal settings are not implicitly imported or changed. An explicitly declared target inside the destination
  uses the same review flow as any other target ([configuration](configuration-standards.md#where-files-live)).
- Keeping native configuration means leaving it in place; reversal is the explicit revert operation (principle 5).
- The committed wrapper pins the engine version; upgrades are explicit and separate from cookbook and recipe versions
  ([wrapper](../features/wrapper.md)).

## 2. People judge; Cook presents the requested changes

People and coding agents choose trusted sources and review the proposed result. Cook evaluates the supplied recipes,
composes their file operations and displays the actual diff. Why: showing what will change gives the reviewer concrete
information rather than asking them to infer a result from configuration.

- Evaluate each supplied recipe against current content on every invocation. A batch record never marks a recipe as
  already applied. An unchanged result has an empty diff.
- Do not promise identical output across machines or a network-free evaluation: trusted Groovy recipes can read their
  environment and execute arbitrary code. Review source trust separately from the resulting batch.
- Explain errors precisely enough for the caller to correct the request
  ([messages](messages-and-docs.md#error-messages)).

## 3. Configure programs through files

Cook is a recipe driven configuration engine. It evaluates trusted Groovy recipes, obtains approval for an ordered
batch, records the batch and ownership and performs declared writes. Why: program configuration can be reused without
placing the program's execution under Cook's control.

Ingredients are program data models, including native settings, ignore files and skills. Recipes may declare prose,
skill content or existing native configuration as targets. Program knowledge belongs with cookbook models and recipes;
the engine handles file operations. Executing external commands is not a batch action.

## 4. Least surprise

The same command line means the same thing wherever it runs. Why: a reader predicts the result from the invocation
alone only when grammar, order and paths follow fixed rules.

- Follow the agreed [CLI grammar](../features/cli.md): batch options precede operands, and recipe arguments follow
  their recipe.
- Compose recipes and operations left to right, last writer wins, and show the combined final result as one batch
  diff, because approval covers that combined result
  ([ordered composition](../architecture.md#ordered-composition-and-declared-targets)).
- Applying B never removes output A owns.
- The invocation directory is the destination, and relative source references resolve from the referencing recipe
  ([path resolution](../architecture.md#destination-and-source-origins)).

## 5. Show changes, obtain approval and prepare recovery

Proposed replacements and deletions need visible scope and a way to recover. Why: a repository contains people's work.

- Display the final diff in the terminal before target writes, and ask before writing unless `--batch-approve`
  preapproves the whole batch; noninteractive execution alone does not authorize writes
  ([batch approval](../architecture.md#batch-approval-and-failure-recovery)).
- Whole-file targets affect the declared file; structured targets preserve unrelated keys; sections preserve text
  outside their `cook:begin <recipe>#<region>` and `cook:end <recipe>#<region>` markers. Use format-appropriate
  comments, including HTML comments in Markdown.
- Existing and edited content uses the same approval flow. Ownership never blocks a change, including a change to
  content a developer edited.
- Save the batch record before the first target write. On write failure, roll back the changes already made by the
  batch and retain the record and failure. This covers declared operations, not arbitrary recipe effects.
- Revert reverses one recorded batch's changes against current content and shows the actual diff. Later
  non-overlapping edits survive; an overlapping edit makes revert write nothing and list the conflicts
  ([operation semantics](../architecture.md#apply-discard-and-revert)).

## 6. One clear interface and vocabulary

Use the CLI as the public cooking interface. Gradle builds and packages Cook and cookbooks. Why: users need one place to
learn operation and approval semantics.

Use `revert`, `batch` and `help` as commands and `--batch-approve` as the preapproval spelling. The wrapper and a global
installation expose the same grammar. Default apply and explicit apply have the same meaning
([entry points](cli-standards.md#entry-points)).

Native configuration lives where its program reads it. Source recipes and templates retain their origins; ownership
lives in the committed `cook.lock`, and batch records live under the destination's ignored `.cook/batches/`. Use the
[glossary](../features/glossary.md) consistently and link to command references instead of maintaining competing command
descriptions.

## 7. Approval has a defined boundary

Declared writes and deletions stay inside the invocation directory, including paths resolved through symlinks.
Review, approval and rollback govern those operations. Why: a user needs to know exactly what the batch review covers.

Recipes are trusted executable code. They can cause side effects, including network calls or external commands, outside
the batch; approval is not a sandbox or a guarantee that nothing leaves the destination. Review those sources before
evaluation. A diff of native hook settings reviews the configuration, not the later effects of the configured program.

## 8. Conventions outlive the tool

Write conventions where people and native programs can find them, with the values that apply. Keep instructions
concise and link to detail when useful. Native configuration without a Cook runtime dependency remains usable
without Cook ([generated-content-standards.md](generated-content-standards.md#instruction-files-and-conventions)).
Why: people and programs keep reading these files after Cook leaves the repository.

Markers bound a declared section and name its owning recipe and region. Keeping configuration means leaving it in
place; reversal follows principle 5
([generated-content-standards.md](generated-content-standards.md#revert)).

## 9. Sources and effects are reviewable

Cook accepts recipe paths inside the destination repository and `https://` URLs. Users supply the recipe versions
they intend to apply, and the committed `cook.lock` records which source and sha256 produced the current ownership
([the lock](../architecture.md#ownership-and-the-lock)). A repository commit alone does not determine the effects of
trusted recipe code. Why: a reviewer can trust or question only a source they can identify.

Each batch keeps one record of the sources it evaluated and consumed, with their origins, arguments and order, and one
reversible patch per touched file ([batch record](../architecture.md#one-record-for-the-batch)). Apply needs neither
batch records nor `cook.lock`; reverting a batch requires its record
([history availability](../architecture.md#batch-record-availability)).

## When principles conflict

Follow the architecture's operation and trust boundaries. Within them, protect review and recovery before convenience,
and make the affected scope explicit. Ownership never blocks a change.

For example, a batch changed a key from `old` to `new`, and a person then edited it to `custom`. Applying the recipe
again proposes `custom` to `new` in the diff. Reverting that batch writes nothing and lists the key as a conflict
(principle 5); once the person resolves it, revert proposes the actual diff for approval, preserving unrelated keys. A
shared remote recipe also requires source trust; batch approval cannot authorize or reverse every effect its code might
cause.
