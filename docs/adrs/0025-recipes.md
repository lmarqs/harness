# 0025. Trusted Groovy recipes describe reusable file changes

## Objective

**Problem.** Teams repeat setup choices across repositories and adapt shared configuration to each project. Hand
copies drift, and a reviewer needs to see what a requested update changes in the project's existing files.

**Goal.** Teams reuse setup choices while reviewing their combined effect on current content before approving writes.

## Context

For example, acme's API and web projects can reuse one program model while supplying different instruction text.

The [architecture](../architecture.md#ingredients-recipes-and-cookbooks) and its
[requirements](../architecture.md#requirements) FR-1 and FR-2 define recipes as trusted executable Groovy code and
[ingredients](../features/glossary.md) as program data models. Cookbooks hold and ship both. FR-7 through FR-16
define ordered file changes, review, recorded ownership, the deferred discard and batch revert.

## Decision

1. A recipe is trusted Groovy code that describes requested file operations using program models. Cookbook
   repositories can publish recipes beside ingredients, and projects can supply their own recipe sources.
2. Each apply evaluates the supplied recipe versions against current content, including repeated
   invocations. Recipes and their operations compose left to right into one final batch diff; later declarations
   can replace earlier values. Applying a recipe does not remove outputs another recipe owns. Existing or edited
   targets use the same review and approval flow; recorded ownership never blocks a change.
3. Users manage recipe versions and supply the version they intend to apply. Each recipe has a short name that
   markers and `cook.lock` use, and `cook.lock` records its source, sha256 and owned targets
   ([0007](0007-ownership.md)). Applying a new version removes what the previous version owned and no longer declares,
   including edited values. Revert applies a batch's reverse patches without evaluating recipes again.
4. The batch record of the sources each apply evaluated follows [0014](0014-directory-layout.md).
5. A recipe describes file operations; it does not launch agents or verify their results. Batch approval and rollback
   cover declared file operations, not arbitrary effects of trusted code.

## Reasons

- **1.** FR-1 and FR-2 preserve reuse and adaptation, sharing a change while allowing project-specific choices,
  without requiring a closed schema. Models hold program knowledge and recipes choose requested setup. The trade is
  that executable sources require trust before evaluation.
- **2.** FR-7, FR-8 and FR-25 make supplied declarations and current content authoritative. Review shows the final
  effect instead of silently skipping an already-applied recipe or removing omitted output. The trade is that a
  later recipe can replace a local value; whole-batch review exposes that change.
- **3.** FR-14 records what each recipe owns, so a new version's removals follow from the lock rather than from
  reevaluating an earlier version, and FR-16 restores a batch's prior values from its patches. The short name ties
  versions of one recipe together without a stored copy of the previous version for merging, which would add version
  management outside the agreed design. Users must supply the recipe version they mean, rather than relying on Cook to
  reconstruct it.
- **4.** Revert needs the evaluated content and origins, not just a recipe name; [0014](0014-directory-layout.md)
  gives the reasons for the record's home and contents.
- **5.** FR-3 and FR-4 distinguish configuration writes from running programs; a contract to verify results and resume
  interrupted runs would make Cook run programs, outside its scope. The trade is that checks, delivery and session
  control belong to the programs or callers responsible for them.

## Rejected alternatives

### A recipe also launches agents and verifies their results

At its best, one workflow combines generation, an agent's judgment, verification and delivery. It loses because its
guarantees require launching agents, recording their results and resuming interrupted sessions, outside Cook's
file-operation responsibility.

### Copy every recipe into the destination and merge later versions

At its best, source copies can be adapted locally, and a merge can help incorporate upstream changes. It loses
because making this the engine's contract would add recipe identity tracking, stored copies of previous versions for
merging, and version management. Users can manage their sources without Cook mandating a copy or merge protocol.

## Consequences

Costs:

- Users choose and maintain the recipe versions they supply.
- A reviewed batch is not proof that tests ran or that arbitrary recipe effects are recoverable.

Must be maintained:

- Reusable program models, ordered current-content proposals and whole-batch review.
- The distinction between supplied recipe sources and local batch records.

## Confirmation

- Review against FR-7 and FR-8: if recipe A sets a key and B replaces it, does the final diff show B's result, and does
  applying B alone leave A's unrelated output intact?
- Review against FR-16: if a batch changed `old` to `new` and a developer then edited another key, does reverting that
  batch propose `old` and keep the edit, and if the developer instead wrote `custom` to the same key, does the revert
  write nothing and name the key?
- Review a proposed recipe feature against FR-3 and FR-4: does it describe file operations only?
