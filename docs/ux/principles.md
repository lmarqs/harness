# User-facing principles

## Objective

**Problem.** Specific rules cannot foresee every case. Convenience, preserving someone's work and sharing setup can
pull in different directions. Without a common basis, contributors make inconsistent choices about what Cook changes
and what a user must approve.

**Goal.** Contributors can resolve an uncovered case from the same responsibilities and explain the result to the
person whose files are affected.

## Overview

These principles follow the [architecture](../architecture.md), which states the intended design. Apply them when a
specific interaction rule leaves a question open, and record the reason in the relevant standard
([changing a standard](README.md#changing-a-standard)). They do not establish implementation completion; current
execution instructions belong in the [root README](../../README.md) and [core guide](../../core/README.md).

## 1. Optional and transparent

Programs consume their native configuration directly. Using Cook to prepare those files should not make teammates
invoke Cook to read them or perform their ordinary work. Why: teammates share configuration without necessarily
choosing the tool that produced it.

- Ingredients model native program files; cookbooks hold and ship ingredients and recipes separately from the engine.
  Recipes can configure build, test or CI files when requested. That does not make Cook the runner of those workflows.
- Personal settings are not implicitly imported or changed. An explicitly declared target inside the destination
  uses the same review flow as any other target ([configuration](configuration-standards.md#where-files-live)).
- Keeping native configuration means leaving it in place. Discard and revert are explicit operations, not a required
  uninstall or eject procedure ([discard and revert](../features/remove.md)).
- The committed wrapper pins an engine version and matching platform-package checksums, downloads and verifies the
  complete package on first use, and caches it locally. Upgrades are explicit. The package includes Java; cookbook
  builds and recipe versions remain separate choices ([wrapper](../features/wrapper.md)).

## 2. People judge; Cook presents the requested changes

People and coding agents choose trusted sources and review the proposed result. Cook evaluates the supplied recipes,
composes their file operations and displays the actual diff. Why: showing what will change gives the reviewer concrete
information rather than asking them to infer a result from configuration.

- Evaluate apply and discard recipes against current content on every invocation. History must not skip a recipe as
  already applied. An unchanged result may have an empty diff.
- Do not promise identical output across machines or a network-free evaluation: trusted Groovy recipes can read their
  environment and execute arbitrary code. Review source trust separately from the resulting batch.
- Explain errors precisely enough for the caller to correct the request
  ([messages](messages-and-docs.md#error-messages)); do not invent a lifecycle verification or to-do system to resolve
  configuration choices.

## 3. Configure programs through files

Cook is a recipe driven file-operation engine. It evaluates trusted Groovy recipes, obtains approval for an ordered
batch, records recovery information and performs declared writes. Why: program configuration can be reused without
placing the program's execution under Cook's control.

Ingredients are program data models, including native settings, ignore files and skills. Recipes may declare prose,
skill content or existing native configuration as targets. Program knowledge belongs with cookbook models and recipes;
the engine handles file operations. Executing external commands is not a batch action.

Cook does not own workers, jails, verification receipts or a fixed software development lifecycle. Personal setup
backup and agent-session orchestration remain future work in
[issue #5](https://github.com/lmarqs/the-cook/issues/5) and
[issue #6](https://github.com/lmarqs/the-cook/issues/6). Ingredient/recipe APIs, station roles, nesting, injection,
annotations and lifecycle hooks need a separate model discussion.

## 4. Least surprise

- Use the agreed [CLI grammar](cli-standards.md#command-grammar): apply is implicit or explicit; discard takes supplied
  recipes; revert takes a recorded run ID. With no arguments, show help.
- Recipe operands are exact paths or supported source URIs, not implicit cookbook names. Batch options precede
  operands; recipe arguments follow their recipe as `--name=value`.
- Compose recipes and operations left to right. Later instructions may replace earlier values. Show the combined
  final result as one batch diff, because intermediate results are not separate approval decisions.
- Applying B does not withdraw unrelated output from A. Every request affects its declared targets rather than
  reconciling a remembered desired state.
- The invocation directory is the destination, including when the recipe or executable lives elsewhere. Relative
  recipe/template source references resolve from the referencing recipe's original location or URL, including after
  snapshotting ([path resolution](../architecture.md#destination-and-source-origins)).

## 5. Show changes, obtain approval and prepare recovery

A repository contains people's work. Proposed replacements and deletions need visible scope and a way to recover.

- Display the final diff in the terminal before target writes. Approval covers the whole batch, without selecting
  individual files or hunks. Without preapproval, ask before writing. `--batch-approve` displays the diff and proceeds
  without prompting; noninteractive execution alone does not authorize writes.
- Whole-file targets affect the declared file; structured targets preserve unrelated keys; marked sections preserve
  text outside their boundaries. Use format-appropriate comments, including HTML comments in Markdown.
- Existing and edited content uses the same approval flow. There is no `--adopt`, adoption alias, ownership-state
  prerequisite or refusal merely because a developer edited the target.
- Save recovery patches before the first target write. On write failure, roll back the changes already made by the
  batch and retain the patches and failure record. This covers declared operations, not arbitrary recipe effects.
- Discard deletes the supplied recipe version's declared contributions, even when their current values differ.
  Revert restores a recorded operation's prior values against current content. Both preserve unrelated content and
  show the actual diff ([operation semantics](../architecture.md#apply-discard-and-revert)).

## 6. One clear interface and vocabulary

Use the CLI as the public cooking interface. Gradle builds and packages Cook and cookbooks; it is not a second public
cooking interface. Why: users need one place to learn operation and approval semantics.

Use `discard` as a command and `--batch-approve` as the preapproval spelling. The wrapper and a global installation
expose the same grammar. Default apply and explicit apply have the same meaning
([entry points](cli-standards.md#entry-points)).

Native configuration lives where its program reads it. Source recipes and templates retain their origins; recovery
history lives under the destination's ignored `.cook/`. Do not impose a neutral configuration tree, pin-file schema or
model API to explain these responsibilities. Use the [glossary](../features/glossary.md) consistently and link to
command references instead of maintaining competing command descriptions.

## 7. Approval has a defined boundary

Declared writes and deletions stay inside the invocation directory, including paths resolved through symlinks.
Review, approval and rollback govern those operations. Why: a user needs to know exactly what the batch review covers.

Recipes are trusted executable code. They can cause side effects, including network calls or external commands,
outside the batch; approval is not a sandbox or a guarantee that nothing leaves the destination. Review those sources
before evaluation. A diff of native hook settings reviews the configuration, not the later effects of the configured
program. Cook does not define a delivery-action or session-hook protocol here.

## 8. Conventions outlive the tool

Write conventions where people and native programs can find them, with the values that apply. Keep instructions
concise and link to detail when useful. Native configuration without a Cook runtime dependency remains usable
without Cook ([generated-content-standards.md](generated-content-standards.md#instruction-files-and-conventions)).

Markers define a declared section's boundaries rather than ownership or a convention/tool block class. Keeping
configuration means leaving it in place. Discard deletes supplied recipes' declared contributions, while revert
restores a recorded operation's prior values; both show the actual diff for whole-batch approval
([generated-content-standards.md](generated-content-standards.md#discard-and-revert)).

## 9. Sources and effects are reviewable

Cook accepts supplied recipe paths and supported source URIs, including local and HTTPS sources. Users supply the
recipe versions they intend to apply or discard; Cook does not infer a persistent recipe identity. A repository
commit alone does not determine the effects of trusted recipe code.

Record one operation for the batch under the destination's ignored `.cook/`, with a separate run record. Capture the
recipe sources actually evaluated, all consumed local and downloaded recipe/template sources, origins, arguments,
execution order, operation and result or failure. Save one reversible patch per touched file for its combined change,
not separate forward/reverse copies ([local history](configuration-standards.md#local-history-and-explicit-reversal)).

Apply and discard need no prior history or ownership database. Losing `.cook/` loses local recovery information,
not the ability to cook. Reverting a particular run requires its history. Patch representations, metadata schemas,
history management and overlapping-reversal mechanics require separate implementation design.

## When principles conflict

Follow the architecture's operation and trust boundaries. Within them, protect review and recovery before convenience,
and make the affected scope explicit. No principle creates an ownership veto, an ingredient safety floor or a sandbox
for recipe evaluation.

For example, a recipe declares a key whose value changed from `old` to `new` and was then edited to `custom`.
Discard proposes deleting `custom`; reverting the original run proposes restoring `old`. Explain that difference
and show the actual diff for approval, preserving unrelated keys. Do not refuse the request merely because the value
was edited. A shared remote recipe also requires source trust; batch approval cannot authorize or reverse every effect
its code might cause.
