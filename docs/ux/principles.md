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
- Keeping native configuration means leaving it in place. Revert is an explicit operation, not a required uninstall
  or eject procedure ([revert](../features/remove.md)).
- The committed wrapper pins an engine version and matching platform-package checksums, downloads and verifies the
  complete package on first use, and caches it locally. Upgrades are explicit. The package includes Java; cookbook
  builds and recipe versions remain separate choices ([wrapper](../features/wrapper.md)).

## 2. People judge; Cook presents the requested changes

People and coding agents choose trusted sources and review the proposed result. Cook evaluates the supplied recipes,
composes their file operations and displays the actual diff. Why: showing what will change gives the reviewer concrete
information rather than asking them to infer a result from configuration.

- Evaluate apply recipes against current content on every invocation. History must not skip a recipe as
  already applied. An unchanged result may have an empty diff.
- Do not promise identical output across machines or a network-free evaluation: trusted Groovy recipes can read their
  environment and execute arbitrary code. Review source trust separately from the resulting batch.
- Explain errors precisely enough for the caller to correct the request
  ([messages](messages-and-docs.md#error-messages)); do not invent a lifecycle verification or to-do system to resolve
  configuration choices.

## 3. Configure programs through files

Cook is a recipe driven file-operation engine. It evaluates trusted Groovy recipes, obtains approval for an ordered
batch, records the batch and ownership and performs declared writes. Why: program configuration can be reused without
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

- Use the agreed [CLI grammar](cli-standards.md#command-grammar): apply is implicit or explicit; revert takes a recorded
  batch ID; `batch list` and `batch inspect` read batch records. With no arguments, show help.
- Recipe operands are paths inside the destination repository or `https://` URLs, not implicit cookbook names. Batch
  options precede operands; recipe arguments follow their recipe as `--name=value`.
- Compose recipes and operations left to right. When two recipes write the same target, the last one wins and becomes
  its owner; the diff is the only notice. Show the combined final result as one batch diff, because intermediate results
  are not separate approval decisions.
- Applying B never removes output A owns. Applying a new version of A removes what A's previous version owned and the
  new version no longer declares, and the diff shows that removal.
- The invocation directory is the destination, including when the executable lives elsewhere. Relative recipe/template
  source references resolve from the referencing recipe's location or URL, including after snapshotting; a local
  reference outside the repository is refused ([path resolution](../architecture.md#destination-and-source-origins)).

## 5. Show changes, obtain approval and prepare recovery

A repository contains people's work. Proposed replacements and deletions need visible scope and a way to recover.

- Display the final diff in the terminal before target writes. The CLI accepts the whole batch; choosing individual
  changes belongs to a separate interface decision, and the core writes and records only accepted changes. Without
  preapproval, ask before writing. `--batch-approve` displays the diff and proceeds
  without prompting; noninteractive execution alone does not authorize writes.
- Whole-file targets affect the declared file; structured targets preserve unrelated keys; marked sections preserve
  text outside their `cook:begin <recipe>/<region>` and `cook:end <recipe>/<region>` markers. Use format-appropriate
  comments, including HTML comments in Markdown.
- Existing and edited content uses the same approval flow. Ownership never blocks a change: there is no `--adopt`,
  adoption alias or refusal merely because a developer edited the target.
- Save the batch record before the first target write. On write failure, roll back the changes already made by the
  batch and retain the record and failure. This covers declared operations, not arbitrary recipe effects.
- Revert reverses only one recorded batch's changes against current content. Later non-overlapping edits survive; an
  overlapping edit makes revert write nothing and list the conflicts. Discard is deferred. Revert shows the actual
  diff ([operation semantics](../architecture.md#apply-discard-and-revert)).

## 6. One clear interface and vocabulary

Use the CLI as the public cooking interface. Gradle builds and packages Cook and cookbooks; it is not a second public
cooking interface. Why: users need one place to learn operation and approval semantics.

Use `revert` and `batch` as commands and `--batch-approve` as the preapproval spelling. The wrapper and a global
installation expose the same grammar. Default apply and explicit apply have the same meaning ([entry
points](cli-standards.md#entry-points)).

Native configuration lives where its program reads it. Source recipes and templates retain their origins; ownership
lives in the committed `cook.lock`, and batch records live under the destination's ignored `.cook/batches/`. Do not
impose a neutral configuration tree, pin-file schema or model API to explain these responsibilities. Use the
[glossary](../features/glossary.md) consistently and link to command references instead of maintaining competing command
descriptions.

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

Markers bound a declared section and name its owning recipe and region; they do not define a convention/tool block
class. Keeping configuration means leaving it in place. Revert reverses one recorded batch's changes and shows the
actual diff for whole-batch approval
([generated-content-standards.md](generated-content-standards.md#discard-and-revert)).

## 9. Sources and effects are reviewable

Cook accepts recipe paths inside the destination repository and `https://` URLs. Users supply the recipe versions
they intend to apply; the committed `cook.lock` records each recipe's short name, source and sha256 with the sections,
keys and files it owns. A repository commit alone does not determine the effects of trusted recipe code.

Record each batch in the destination's ignored `.cook/batches/<batchId>/`, where the batch ID is the UTC start time.
Capture the recipe sources actually evaluated, all consumed local and downloaded recipe/template sources, origins,
arguments, execution order and result or failure. Save one reversible patch per touched file for its combined change,
not separate forward/reverse copies ([local history](configuration-standards.md#local-history-and-explicit-reversal)).

Apply needs neither batch history nor `cook.lock`. Losing `.cook/` loses the ability to revert earlier batches, not
the ability to cook; ownership survives in `cook.lock`. Reverting a batch requires its record. Patch representations,
metadata schemas and history management belong to the implementation.

## When principles conflict

Follow the architecture's operation and trust boundaries. Within them, protect review and recovery before convenience,
and make the affected scope explicit. Ownership never blocks a change; no principle creates an ownership veto, an
ingredient safety floor or a sandbox for recipe evaluation.

For example, a batch changed a key from `old` to `new`, and a person then edited it to `custom`. Applying the recipe
again proposes `custom` to `new` in the diff rather than refusing. Reverting that batch writes nothing and lists the key
as a conflict, because the edit overlaps the batch's change; once the person resolves it, revert proposes the actual
diff for approval, preserving unrelated keys. A shared remote recipe also requires source trust; batch approval cannot
authorize or reverse every effect its code might cause.
