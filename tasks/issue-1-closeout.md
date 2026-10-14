# Issue #1 closeout — draft handoff

## Objective

Assess the documentation work for [#1](https://github.com/lmarqs/the-cook/issues/1) separately from product
implementation. This is a local draft for maintainer review, not a posted comment or an instruction to close the
issue. No issues were edited or closed. The [PRD](prd-documentation-alignment.md) supplies the agreed target;
source inspection supports current-implementation claims only.

## Documentation delivered

- [Architecture](../docs/architecture.md) defines trusted Groovy evaluation, ingredients as program data models,
  separate cookbooks, ordered file operations, actual terminal diff review, whole-batch approval, recovery,
  apply/discard/revert, source origins, destination boundaries, local history and JVM/jpackage delivery.
- [Philosophy](../docs/philosophy.md), [feature references](../docs/features/README.md),
  [ADRs](../docs/adrs/README.md) and [UX standards](../docs/ux/README.md) follow that architecture. Retired
  lifecycle, worker/jail, ownership-reconciliation and adoption requirements were corrected case by case.
  ADRs 0005, 0015 and 0027 and the attempts feature page were removed; affected navigation and citations were repaired.
- The [root README](../README.md), [core guide](../core/README.md), [cookbook index](../cookbook/recipes/README.md)
  and all 22 recipe guides describe current execution and prerequisites without claiming target completion.
  Fresh-clone entry instructions now explain build/help, policy and skill-source requirements.

The acyclic-link requirement was withdrawn. Useful reciprocal references are allowed; no graph enforcement or
directory reordering is required. No mandatory build, lint, test, link-check or other quality gates or check commands
were requested for this run. None were run or added. Existing development-command descriptions do not reinstate
those requirements for this documentation task.

## Combined review and documentation blockers

Reviewed the architecture and philosophy together with all 31 feature pages, 25 retained ADRs and their index,
all 9 UX pages, root/core guides and cookbook index/recipe guides. Compared operation semantics, CLI grammar,
approval, trust, sources/history, distribution and deferred APIs; inspected affected relative file links and heading
references. No broken affected cross-reference or conflicting target product contract was found. Truthful current
ownership/adoption behavior is an implementation gap, not an alternative target design.

These documentation conflicts remain closeout blockers for #1, independent of product implementation:

1. **Current-implementation scope in the dogfood page.** The [feature index's authority statement](../docs/features/README.md#authority)
   covers feature references generally, but [agentic-dogfood.md](../docs/features/agentic-dogfood.md) gives unqualified
   ownership/adoption, drift and project-gate instructions without an architecture link or current-versus-target
   explanation. Label its scope explicitly as current repository setup, link to the target and required adoption
   removal, and distinguish its existing project gate from this run's explicit exemption. Retain truthful usage
   examples until code changes; resolving this ambiguity does not require implementing the target first.
2. **ADR writing rules versus the reconciled records.** The [ADR index](../docs/adrs/README.md#writing-rules)
   prohibits command/flag/file names, and its authority section prohibits restating other decisions. Retained ADRs
   repeat relevant operation guarantees and explicitly name `--adopt` and `.cook/`. Reconcile the editorial rules
   with the explanatory records without weakening the agreed contract or restoring obsolete obligations.
3. **Opening-structure rule versus reference pages.** The [documentation standard](../docs/ux/messages-and-docs.md#every-document-opens-with-its-objective)
   requires an Objective section immediately after every top-level/feature title, with Problem and Goal.
   Architecture and the CLI reference use other introductions. Align the rule's scope or the affected introductions;
   this is an editorial conflict, not a missing product feature.

The handoff story is complete when these findings are recorded; #1 should not be presented as unconditionally
ready to close until the documentation blockers are addressed. No implementation gap below is a condition of #1.

## Corrections to carry into #9

The PRD notes that [#9](https://github.com/lmarqs/the-cook/issues/9) cites an earlier revision. Current local evidence
at `021e2d6` supports these corrections; live issue reads failed because GitHub was unavailable in this environment,
so the latest issue wording and comments were not verified. These are proposed handoff corrections, not issue edits.

| Review claim to reassess | Current evidence and correction |
| --- | --- |
| Dynamic factories are unreachable through the CLI | [CookCommand](../cli/src/main/kotlin/thecook/cli/terminal/CookCommand.kt) accepts named recipes and local `.groovy` paths. [Main](../cli/src/main/kotlin/thecook/cli/run/Main.kt) wires CookService to GroovyBatchLoader; [RepositoryScript](../core/src/main/groovy/thecook/groovy/RepositoryScript.groovy) calls Cookbook.recipeFile or recipeValue, mounts the returned component and renders it. [Cookbook](../core/src/main/groovy/thecook/cookbook/Cookbook.groovy) invokes closure and declared factories. They are reachable today, although some inputs require typed composition values rather than CLI strings. |
| The BatchAdapter reference is still broken | It is no longer present in the current source path. RepositoryScript handles a typed Batch with `RenderTree.from((Batch) result)`; [GroovyBatchLoader](../core/src/main/groovy/thecook/groovy/GroovyBatchLoader.groovy) constructs a RenderedPlan from that tree. Do not carry the old missing-reference finding forward as a current defect. This source review makes no claim that builds were run. |
| Cleanup selects one composition API or deletes the other | The agreed design makes no such selection. Existing dynamic composition and compiled suppliers are implementation evidence, not approved final APIs or authorized deletion targets. |

## Required product work for #9 or successors

- **Remove `--adopt` wherever exposed**, including recipe-specific CLI inputs and other entry points, and remove
  underlying supplier/render-record/reconciler adoption gates. Remove ownership-state prerequisites and managed-edit
  vetoes for the target operations. [RegionReconciler](../core/src/main/groovy/thecook/reconciliation/RegionReconciler.groovy),
  [FileReconciler](../core/src/main/groovy/thecook/reconciliation/FileReconciler.groovy) and
  [SymbolicLinkReconciler](../core/src/main/groovy/thecook/reconciliation/SymbolicLinkReconciler.groovy) still enforce
  existing-content gates. The replacement is review and approval of the **actual batch diff** for existing or edited
  content, with no adoption alias, separate adoption step or ownership-state prerequisite. Merely hiding the flag
  does not implement this change; documentation completion alone does not complete it.
- Implement the target default/explicit apply, discard and recorded-run revert grammar, ordered recipe operands,
  exact paths/URIs, scoped arguments and no-argument help. Today apply consumes a saved plan and removal reads
  ownership; cooking reports paths and writes without content-diff approval. Add whole-batch review and
  `--batch-approve`, including refusal to silently authorize noninteractive writes.
- Replace ownership reconciliation, overlap rejection, implicit withdrawal and adopted-original restoration with
  ordered declared operations, unrelated-output preservation, repeated evaluation, declared discard and recorded
  reversal against current content. Preserve the destination boundary, including symlink resolution.
- Implement origin-preserving local/HTTPS source loading and capture all consumed local/downloaded recipe/template
  sources. Add one run record and one reversible patch per touched file, saved before target writes, with rollback
  retaining recovery patches and failure information. Current state, saved plans and journals do not supply that history.
- Deliver the pinned version/checksum wrapper, verified first-use download/cache and explicit upgrades. The current
  [cook](../cook) launcher changes into the checkout and runs a prebuilt JVM distribution; local jpackage bundles
  Java but does not establish published packages or a working pinned-download wrapper.

After each product change, update the current-implementation guides to match it. Track sequencing separately; this
note creates no issues, commits, pushes, pull requests or merges.

## Separate discussions

Ingredient/recipe APIs, nesting/composition, station roles, decorators, parameter injection, annotations and lifecycle
hooks require their own discussion. `Recipe(...)`, `Batch().withRecipe(...)`, `@pwd`, `@param` and `@before` remain
exploratory sketches, not approved APIs. Native program hooks described in usage guides do not settle Cook hooks.
Concrete patch representation, metadata schema, history management and reversal after overlapping edits also remain
open implementation design. Kit backup (#5) and session orchestration (#6) remain future work. None blocks #1's
documentation scope.
