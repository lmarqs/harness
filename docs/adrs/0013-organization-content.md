# 0013. Cookbooks carry organization choices separately from the engine

## Objective

**Problem.** Organizations reuse setup across repositories, but their services, permissions and conventions differ.
Putting those choices in a shared engine couples its releases to individual teams; copying setup by hand makes
improvements hard to share and review.

**Goal.** Teams share setup without forking the engine, and reviewers can see the configuration changes each
repository accepts while preserving choices outside those changes.

## Context

- In a worked example, `acme/api` and `acme/web` share build instructions, but only `acme/api` needs configuration
  for acme's deployment service. A different organization uses other services and permissions.
- [Ingredients](../features/glossary.md) model programs' native files and configuration, including an organization's
  own program. Cookbook repositories hold and ship ingredients and trusted Groovy recipes; cookbook builds can be
  separate from Cook's build ([architecture](../architecture.md#ingredients-recipes-and-cookbooks)).
- Recipes come from paths inside the destination repository, from `https://` URLs, or from coordinates resolved from
  the cookbook repositories the destination lists ([0029](0029-cookbook-distribution.md)); a local recipe or reference
  outside the repository is refused ([source origins](../architecture.md#destination-and-source-origins)). Relative
  recipe and template references resolve from the referencing recipe's original location, not from the copy saved in
  the batch record.
- Cook evaluates supplied recipes against current content in declared order and shows one final batch diff. Each batch
  record captures the sources actually evaluated and consumed, including local sources, with origins and arguments.
- The engine's jpackage distribution and committed version and checksum pin are separate from cookbook and recipe
  versions ([distribution](../architecture.md#engine-distribution)).

## Decision

1. Organization choices and program-specific models belong in cookbook content and supplied recipes, rather than
   becoming engine policy. Cook handles recipe evaluation and the ordered file-operation batch, review, recovery
   and writes; ingredients model native program configuration.
2. Cookbooks distribute their ingredients and recipes through their repositories and may build independently.
   Engine upgrades do not manage recipe versions.
3. Reused content can participate in recipe evaluation from paths inside the destination repository, from `https://`
   URLs, or by coordinates from a cookbook release ([0029](0029-cookbook-distribution.md)). Users manage recipe versions
   and supply the version to apply; `cook.lock` records which version produced the current ownership.
4. Ordered composition, ownership and review of the resulting changes follow [0007](0007-ownership.md).
5. Native output stays readable without Cook ([0010](0010-dependence-on-the-binary.md)), and the batch record keeps
   the evaluated sources ([0014](0014-directory-layout.md)).

## Reasons

- **1, 2.** Separate cookbook responsibility lets teams maintain native program knowledge and shared choices without
  making every change an engine release or organization-specific fork. Cookbook content is still executable or
  consumed input that users must trust.
- **3.** Shared sources address hand-copying without forcing all inputs into each destination repository. Limiting
  local sources to the repository keeps every local input visible in the same clone, while HTTPS carries content
  shared between repositories. Capturing consumed sources and preserving their origins makes a batch inspectable.
  Trusted code may use changing inputs, so engine pinning alone is not a promise of identical output across machines
  or days.
- **4.** Review exposes what the requested setup changes in this repository, and a shared recipe overridden by a
  later, repository-specific one shows the override in the same diff. [0007](0007-ownership.md) gives the reasons.
- **5.** Native output serves readers who do not run Cook; the batch record serves those inspecting a recorded
  operation. Losing local batch records loses the ability to revert those batches, not the ability to apply supplied
  recipes.

## Rejected alternatives

### Organization choices maintained in engine releases

At its best, every user gets shared setup from one release. It loses because engine releases become coupled to
organization-specific choices and teams must fork or wait for changes. Cookbook authors can maintain those choices
separately.

### Only copied repository content may affect evaluation

At its best, all source material is visible in one checkout. It loses because it prohibits the agreed HTTPS
source flow and turns copying and merge management into engine responsibilities. Native output, the reviewed diff
and captured consumed sources provide distinct views without forbidding composition.

### Add-only templates and three-way recipe merges

At its best, content written only where a target is absent never overwrites a local change, and a three-way merge
against a stored copy of a recipe's previous output reconciles upstream and local edits. It loses because add-only
writes prevent intentional updates, and three-way merges make the engine store and manage recipe versions. Users
supply recipe versions, and the diff against current content shows local edits before approval.

## Consequences

Costs:

- Reviewers may need the cookbook sources and local batch records to explain how output was produced. Native output
  alone does not describe every dependency or arbitrary side effect of executable recipes.
- Engine version and checksum pinning protects the selected engine download; it does not authenticate every recipe
  source or sandbox recipe execution. [0026](0026-cookbook-trust.md) explains cookbook trust.

Must be maintained:

- Cookbook authors maintain program models and shared recipes independently; teams review the resulting native-file
  changes and manage the recipe versions they supply.

## Confirmation

- Review the acme example with shared setup and a repository-specific choice: does the batch change only declared
  targets, show later overrides in its final diff, and preserve unrelated configuration?
- Review a batch with local and remote recipes: does `./cook batch inspect` trace each consumed source to its original
  location?
