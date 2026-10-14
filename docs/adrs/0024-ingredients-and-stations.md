# 0024. Program models remain distinct from reusable setup choices

## Objective

**Problem.** Teams reuse both knowledge of a program's configuration format and choices about how they work. When
those are treated as one responsibility, a shared program description can impose one team's choices on another, and
an update can obscure which choices the repository is being asked to change.

**Goal.** Teams share program models while choosing their own setup, and review the combined effect of requested
setup changes before they are written.

## Context

- Ingredients model programs' native files and configuration. Recipes are trusted executable Groovy code that describes
  requested file operations using those models. Cookbooks hold and ship both through their repositories and can build
  separately from Cook ([architecture](../architecture.md#ingredients-recipes-and-cookbooks), FR-1, FR-2).
- Worked example: `acme/api` and `acme/infra` use the same agent configuration model but have different project
  instructions. `acme/api` also configures a loop runner. The program model and the team's selection of settings are
  different responsibilities, even when distributed together.
- Recipes and operations compose left to right; later instructions may replace earlier values. Applying recipe B
  alone leaves unrelated output from recipe A intact (FR-7, FR-8).
- The [architecture requirements](../architecture.md#requirements) leave the ingredient/recipe model, station role,
  composition API and lifecycle hooks for a separate discussion (FR-30).
- Engine delivery is a platform-specific jpackage package containing Java. A committed wrapper pins the engine version
  and corresponding package checksums, verifies first-use downloads, caches packages and upgrades only through an
  explicit pin change ([0017](0017-engine-version.md), [0021](0021-release-trust.md), FR-26).

## Decision

1. Preserve ingredients as program data models, distinct in responsibility from recipes' setup choices. Cookbooks hold
   and ship ingredients and recipes; the engine evaluates recipes and reviews and writes the ordered batch.
2. Reusable setup follows the agreed operation contract. Every apply invocation evaluates the supplied recipes against
   current content; history does not mark setup as already applied. Later operations can replace earlier values in one
   combined diff. Existing or edited content uses the same whole-batch approval flow; recorded ownership never blocks a
   change. Omitting a recipe does not withdraw its earlier output.
3. Leave station semantics undecided.
4. Keep engine version/checksum pinning separate from cookbook builds and user-managed recipe versions. Batch records
   capture consumed sources and origins for inspection and recovery; `cook.lock` records which recipe version produced
   the current ownership without selecting the next one.

## Reasons

- **1.** The acme example uses the same program format with different choices. Separating responsibilities lets a
  model be reused without embedding one team's instructions as engine policy. The trade is that cookbook authors must
  make the distinction clear.
- **2.** FR-7 through FR-9 and FR-25 make requested changes and their order authoritative. A first-value-wins,
  add-only or already-applied rule would silently replace that contract. The trade is that applying shared setup can
  propose replacing a local value; the reviewer sees the final diff before approving the batch.
- **3.** FR-30 explicitly defers station roles and model APIs. A fixed station schema would decide that role
  indirectly. The trade is leaving an authoring question open while documenting the program-model concept and
  operation behavior that are already agreed.
- **4.** An engine pin chooses an executable package, while a supplied recipe chooses the requested setup. Treating
  history or the lock as an update baseline would add the recipe-version management the architecture excludes; the
  lock only records which version produced the current ownership (FR-14, FR-25). The trade is that users supply the
  versions they intend to apply, independently of engine upgrades.

## Rejected alternatives

### Keep the former ingredient/station field split

Separate renders and floor rules from copy-once entries and templates would make each old unit's effect predictable.
That benefit depends on lifecycle machinery and ownership gates outside the agreed scope and settles the deferred
station role. Retain the distinction of responsibilities without that schema.

### Treat all reusable setup as add-only starting content

Keeping every existing value can protect local customization from replacement. It also prevents an explicit later
recipe from changing an earlier value, contrary to ordered composition. Review the requested diff instead of making
first-value-wins an engine rule.

### Decide inheritance or composition from existing implementations

Choosing a current mechanism would give authors an immediate convention. It would finalize nesting and supplier
mechanics without the model discussion. Dynamic composition and compiled suppliers remain candidates for that
separate discussion rather than cleanup targets inferred from issue #9.

## Consequences

Costs:

- Updating reusable setup requires supplying the intended recipe version and reviewing changes to current content;
  engine pins and local batch records do not perform that selection.

Must be maintained:

- The ingredient concept and the distinction between program configuration knowledge and repository setup choices.
- Ordered overlap, declared-target scope and whole-batch review without automatic withdrawal or ownership gates.

## Confirmation

Review against FR-1, FR-2, FR-7 through FR-9, FR-14, FR-25, FR-26 and FR-30. In the acme example, two recipes can reuse
one program model while choosing different instructions. If recipe A sets a key and recipe B overrides it, the preview
shows B's value; applying B alone leaves A's unrelated file intact. Repeating apply evaluates the recipe again, although
unchanged content may yield an empty diff.
