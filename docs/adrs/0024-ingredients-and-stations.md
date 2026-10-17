# 0024. Program models remain distinct from reusable setup choices

## Objective

**Problem.** Teams reuse both knowledge of a program's configuration format and choices about how they work. When
those are treated as one responsibility, a shared program description can impose one team's choices on another, and
an update can obscure which choices the repository is being asked to change.

**Goal.** Teams share program models while choosing their own setup, and review the combined effect of requested
setup changes before they are written.

## Context

- [Ingredients](../features/glossary.md) model programs' native files and configuration. Recipes are trusted
  executable Groovy code that describes requested file operations using those models. Cookbooks hold and ship both
  through their repositories and can build separately from Cook
  ([architecture](../architecture.md#ingredients-recipes-and-cookbooks), FR-1, FR-2).
- Worked example: `acme/api` and `acme/infra` use the same agent configuration model but have different project
  instructions. The program model and the team's selection of settings are different responsibilities, even when
  distributed together.
- Recipes and operations compose left to right; later instructions may replace earlier values. Applying recipe B
  alone leaves unrelated output from recipe A intact (FR-7, FR-8).

## Decision

1. Ingredients are program data models, distinct in responsibility from recipes' setup choices. Cookbooks hold and
   ship ingredients and recipes; the engine evaluates recipes and reviews and writes the ordered batch.
2. Reusable setup follows the agreed operation contract. Every apply invocation evaluates the supplied recipes against
   current content; nothing marks setup as already applied. Later operations can replace earlier values in
   one combined diff. Existing or edited content uses the same whole-batch approval flow; recorded ownership never
   blocks a change. Omitting a recipe leaves its earlier output in place.
3. Engine version and checksum pinning is separate from cookbook builds and user-managed recipe versions. `cook.lock`
   records which recipe version produced the current ownership without selecting the next one.

## Reasons

- **1.** The acme example uses the same program format with different choices. Separating responsibilities lets a
  model be reused without embedding one team's instructions as engine policy. The trade is that cookbook authors must
  make the distinction clear.
- **2.** FR-7 through FR-9 and FR-25 make requested changes and their order authoritative. A first-value-wins,
  add-only or already-applied rule would silently replace that contract. The trade is that applying shared setup can
  propose replacing a local value; the reviewer sees the final diff before approving the batch.
- **3.** An engine pin chooses an executable package, while a supplied recipe chooses the requested setup. Treating
  the lock as an update baseline would add the recipe-version management the architecture excludes;
  the lock only records which version produced the current ownership (FR-14, FR-25). The trade is that users supply
  the versions they intend to apply, independently of engine upgrades.

## Rejected alternatives

### Fixed write behavior for each ingredient field

At its best, an ingredient schema gives every field one fixed write behavior, such as regenerated on each apply,
written once and then left alone, or enforcing a minimum rule set, which makes each field's effect predictable. It
loses because it needs ownership checks that block changes, and it fixes per-field behavior that ordered composition
and review of the final diff already decide. The distinction of responsibilities holds without that schema.

### Treat all reusable setup as add-only starting content

At its best, keeping every existing value protects local customization from replacement. It loses because it also
prevents an explicit later recipe from changing an earlier value, contrary to ordered composition. Review of the
requested diff replaces a first-value-wins engine rule.

## Consequences

Costs:

- Updating reusable setup requires supplying the intended recipe version and reviewing changes to current content;
  engine pins and the lock do not perform that selection.

Must be maintained:

- The ingredient concept and the distinction between program configuration knowledge and repository setup choices.
- Ordered overlap, declared-target scope and whole-batch review, with ownership that never blocks a change and no
  automatic removal of omitted recipes' output.

## Confirmation

- Review the acme example against FR-1 and FR-2: can two recipes reuse one program model while choosing different
  instructions?
- Review against FR-7 through FR-9: if recipe A sets a key and recipe B overrides it, does the preview show B's value,
  and does applying B alone leave A's unrelated file intact?
- Review against FR-14 and FR-25: does repeating apply evaluate the recipe again, with unchanged content yielding an
  empty diff?
