# 0025. Trusted Groovy recipes describe reusable file changes

## Objective

**Problem.** Teams repeat setup choices across repositories and adapt shared configuration to each project. Hand
copies drift, and a reviewer needs to see what a requested update changes in the project's existing files.

**Goal.** Teams reuse setup choices while reviewing their combined effect on current content before approving writes.

## Context

The useful concern in reusable recipes is sharing a change while allowing project-specific choices. For example,
acme's API and web projects can reuse one program model while supplying different instruction text.

The [architecture](../architecture.md#ingredients-recipes-and-cookbooks) and its
[requirements](../architecture.md#requirements) FR-1 and FR-2 define recipes as trusted executable Groovy code and
ingredients as program data models. Cookbooks hold and ship both. FR-7 through FR-16 define ordered file changes,
review, recorded ownership, the deferred discard and batch revert; FR-30 leaves the authoring APIs open.

## Decision

1. A recipe is trusted Groovy code that describes requested file operations using program models. Cookbook
   repositories can publish recipes beside ingredients, and projects can supply their own recipe sources.
2. Each apply evaluates the supplied recipe versions against current content, including repeated
   invocations. Recipes and their operations compose left to right into one final batch diff; later declarations
   can replace earlier values. Applying a recipe does not remove outputs another recipe owns. Existing or edited
   targets use the same review and approval flow; recorded ownership never blocks a change.
3. Users manage recipe versions and supply the version they intend to apply. Each recipe has a short name, declared or
   derived from its path, and `cook.lock` records its source, sha256 and owned targets ([0007](0007-ownership.md)).
   Applying a new version removes what the previous version owned and no longer declares, including edited values.
   Revert applies a batch's reverse patches without evaluating recipes again.
4. Each batch record, in the ignored `.cook/batches/<batchId>/` directory, captures evaluated recipes and consumed
   recipe/template sources, their origins, arguments, order and outcome, with one reversible patch per touched file.
   Patches are saved before target writes. Relative source references retain their original recipe origin when
   snapshots are saved. Apply needs no earlier history; reverting a batch requires its record.
5. Recipes are not passes through a fixed development lifecycle. Batch approval and rollback cover declared file
   operations, not arbitrary effects of trusted code.
6. Ingredient/recipe APIs, nesting, station roles, decorators, parameter injection, annotations and lifecycle hooks
   remain for the separate model discussion.

## Reasons

- **1.** FR-1 and FR-2 preserve reuse and adaptation without requiring a closed lifecycle schema. Models hold program
  knowledge and recipes choose requested setup. The trade is that executable sources require trust before evaluation.
- **2.** FR-7, FR-8 and FR-25 make supplied declarations and current content authoritative. Review shows the final
  effect instead of silently skipping an already-applied recipe or withdrawing omitted output. The trade is that a
  later recipe can replace a local value; whole-batch review exposes that change.
- **3.** FR-14 records what each recipe owns, so a new version's removals follow from the lock rather than from
  reevaluating an earlier version, and FR-16 restores a batch's prior values from its patches. The short name ties
  versions of one recipe together without an update ancestor, which would add version management outside the agreed
  design. Users must supply the recipe version they mean, rather than relying on Cook to reconstruct it.
- **4.** FR-22 through FR-25 make a record identify the sources actually used. Recovery needs the evaluated content and
  origins, not just a recipe name. The trade is local history storage; losing it loses reversal information but does
  not prevent another apply.
- **5.** FR-3, FR-4 and FR-6 distinguish configuration writes from worker execution; a verification and resume contract
  would depend on a lifecycle engine outside Cook's scope. The trade is that checks, delivery and session control belong
  to the programs or callers responsible for them.
- **6.** FR-30 defers the model design. Preserving the recipe concept and operation contract does not require choosing
  authoring syntax, composition mechanics or hooks.

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

Must be maintained:

- Reusable program models, ordered current-content proposals and whole-batch review.
- The distinction between supplied recipe sources and local batch records.

## Confirmation

Review against FR-1 through FR-4, FR-7 through FR-16, FR-22 through FR-25 and FR-30. If recipe A sets a key and B
replaces it, the final diff shows B's result; applying B alone leaves A's unrelated output intact. If a batch changed
`old` to `new` and a developer then edited another key, reverting that batch proposes `old` and keeps the edit; if the
developer instead wrote `custom` to the same key, the revert writes nothing and names the key.
