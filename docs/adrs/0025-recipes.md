# 0025. Trusted Groovy recipes describe reusable file changes

## Objective

**Problem.** Teams repeat setup choices across repositories and adapt shared configuration to each project. Hand
copies drift, and a reviewer needs to see what a requested update changes in the project's existing files.

**Goal.** Teams reuse setup choices while reviewing their combined effect on current content before approving writes.

## Context

The useful concern in reusable recipes is sharing a change while allowing project-specific choices. For example,
acme's API and web projects can reuse one program model while supplying different instruction text.

The [architecture](../architecture.md#ingredients-recipes-and-cookbooks) and PRD FR-1 and FR-2 define recipes as
trusted executable Groovy code and ingredients as program data models. Cookbooks hold and ship both. FR-7 through
FR-16 define ordered file changes, review, discard and recorded-run reversal; FR-30 leaves the authoring APIs open.

## Decision

1. A recipe is trusted Groovy code that describes requested file operations using program models. Cookbook
   repositories can publish recipes beside ingredients, and projects can supply their own recipe sources.
2. Each apply or discard evaluates the supplied recipe versions against current content, including repeated
   invocations. Recipes and their operations compose left to right into one final batch diff; later declarations
   can replace earlier values. Applying a recipe does not implicitly withdraw unrelated outputs of another recipe.
   Existing or edited targets use the same review and approval flow, without ownership reconciliation, an adoption
   flag or alias, a separate adoption step, or a manual-edit veto.
3. Users manage recipe versions and supply the version they intend to apply or discard. Discard proposes deleting
   that version's declared contributions, including edited values. Revert uses a recorded run's prior values without
   evaluating recipes again. Cook does not infer a persistent recipe identity or supply a recipe-copy updater.
4. Local history captures evaluated recipes and consumed recipe/template sources, their origins, arguments, order,
   operation and outcome, with one reversible patch per touched file and a separate run record for the batch.
   Patches are saved before target writes. Relative source references retain their original recipe origin when
   snapshots are saved. Apply/discard need no earlier history; reverting a run requires its record.
5. Recipes are not passes through a fixed development lifecycle. Cook defines no recipe command/attempt/handoff
   step catalog, verification proof, managed working copy, resume protocol or delivery event. Batch approval and
   rollback cover declared file operations, not arbitrary effects of trusted code. Session orchestration remains
   future work in [issue #6](https://github.com/lmarqs/the-cook/issues/6).
6. Ingredient/recipe APIs, nesting, station roles, decorators, parameter injection, annotations and lifecycle hooks
   remain for the separate model discussion. This record chooses neither dynamic composition nor compiled suppliers
   as the final authoring API and does not infer their deletion from issue #9. Patch representation and run metadata
   schemas remain for later implementation design.

## Reasons

- **1.** FR-1 and FR-2 preserve reuse and adaptation without requiring a closed lifecycle schema. Models hold program
  knowledge and recipes choose requested setup. The trade is that executable sources require trust before evaluation.
- **2.** FR-7, FR-8 and FR-25 make supplied declarations and current content authoritative. Review shows the final
  effect instead of silently skipping an already-applied recipe or withdrawing omitted output. The trade is that a
  later recipe can replace a local value; whole-batch review exposes that change.
- **3.** FR-14 through FR-16 separate declared deletion from restoration of recorded prior values. An update ancestor
  or inferred recipe identity would add version management outside the agreed design. Users must supply the recipe
  version they mean, rather than relying on Cook to reconstruct it.
- **4.** FR-22 through FR-25 retain the original concern that a record identify the sources actually used. Recovery
  needs the evaluated content and origins, not just a recipe name. The trade is local history storage; losing it
  loses reversal information but does not prevent another apply or discard.
- **5.** FR-3, FR-4 and FR-6 distinguish configuration writes from worker execution. The former verification and resume
  contract depended on a lifecycle engine that Cook no longer owns. The trade is that checks, delivery and session
  control belong to the programs or callers responsible for them.
- **6.** FR-30 defers the model design. Preserving the recipe concept and operation contract does not require choosing
  authoring syntax, composition mechanics or hooks from an earlier proposal or current implementation.

## Rejected alternatives

### Retain recipes as guarded lifecycle workflows

A workflow could combine generation, worker judgement, verification and delivery. Its guarantees require worker
launches, receipts, lifecycle guards and resumable sessions outside Cook's agreed file-operation responsibility.

### Copy every recipe into the destination and merge later versions

Source copies can be adapted locally, and a merge can help incorporate upstream changes. Making this the engine's
contract would add recipe identity, update ancestors and version management. Users can manage their sources without
Cook mandating a copy or merge protocol.

### Select the model API from existing composition implementations

Choosing one implementation would give authors an immediate convention. It would settle the deferred model discussion
without its design work. Neither implementation is a deletion target merely because the lifecycle contract is removed.

## Consequences

Costs:

- Users choose and maintain the recipe versions they supply.
- A reviewed batch is not proof that tests ran or that arbitrary recipe effects are recoverable.
- Authoring APIs and concrete recovery mechanisms still need separate design.

Must be maintained:

- Reusable program models, ordered current-content proposals and whole-batch review.
- The distinction between supplied recipe sources and local operation history.

## Confirmation

Review against FR-1 through FR-4, FR-7 through FR-16, FR-22 through FR-25 and FR-30. If recipe A sets a key and B
replaces it, the final diff shows B's result; applying B alone leaves A's unrelated output intact. If an apply changed
`old` to `new` and a developer then wrote `custom`, discard proposes deletion and revert of that run proposes `old`,
while preserving unrelated content. No lifecycle stage, recipe updater or finalized model API is required to state
these guarantees. These are design review criteria, not claims that the target behavior is implemented.
