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
- Ingredients model programs' native files and configuration, including an organization's own program. Cookbook
  repositories hold and ship ingredients and trusted Groovy recipes; cookbook builds can be separate from Cook's
  build ([architecture](../architecture.md#ingredients-recipes-and-cookbooks)).
- Recipes come from paths inside the destination repository or from `https://` URLs; a local recipe or reference
  outside the repository is refused ([source origins](../architecture.md#destination-and-source-origins)). Relative
  recipe/template references resolve from the referencing recipe's original location, not from its saved snapshot.
- Cook evaluates supplied recipes against current content in declared order and shows one final batch diff. Each batch
  record captures the sources actually evaluated and consumed, including local sources, with origins and arguments.
- The engine's jpackage distribution and committed version/checksum pin are separate from cookbook and recipe versions
  ([distribution](../architecture.md#engine-distribution)).

## Decision

1. Organization choices and program-specific models belong in cookbook content and supplied recipes, rather than
   becoming engine policy. Cook handles recipe evaluation and the ordered file-operation batch, review, recovery
   and writes; ingredients model native program configuration.
2. Cookbooks distribute their ingredients and recipes through their repositories and may build independently.
   This responsibility split does not impose a blanket prohibition on bundling cookbook content in a distribution,
   require a registry, or mandate a content-addressed supplier cache. Engine upgrades do not manage recipe versions.
3. Reused content can participate in recipe evaluation from paths inside the destination repository or from
   `https://` URLs. There is no rule requiring shared content to be copied into each repository, ban on includes or
   dynamic composition, mandatory copy-once workflow, or automatic recipe merge/update protocol. Users manage recipe
   versions and supply the version to apply; `cook.lock` records which version produced the current ownership.
4. Cook proposes declared changes against current content. Later recipes can replace earlier values at overlapping
   targets; the terminal shows the final combined diff for whole-batch approval. Applying a recipe leaves unrelated
   outputs intact. Existing content and manual edits follow the same flow without adoption gates; ownership recorded
   in `cook.lock` never blocks a change ([0007](0007-ownership.md)).
5. Native output is readable by its configured programs without Cook ([0010](0010-dependence-on-the-binary.md)).
   Each local batch record holds evaluated and consumed sources, origins, arguments, order and result or failure,
   with recovery patches saved before target writes. This record supports inspection and reversal, not automatic
   reapplication.
6. Ingredient/recipe APIs, nesting, station semantics, annotations, injection, decorators and lifecycle hooks remain
   for the separate model discussion. Issue #9 is review input, not a decision to delete dynamic composition or
   compiled suppliers. This record selects neither implementation as the final model API.

## Reasons

- **1, 2.** Separate cookbook responsibility lets teams maintain native program knowledge and shared choices without
  making every change an engine release or organization-specific fork. Cookbook content is still executable or
  consumed input that users must trust; separation alone does not establish a supplier admission protocol.
- **3.** Shared sources address hand-copying without forcing all inputs into each destination repository. Limiting
  local sources to the repository keeps every local input visible in the same clone, while HTTPS carries content
  shared between repositories. Capturing consumed sources and preserving their origins makes a batch inspectable.
  Trusted code may use changing inputs, so engine pinning alone is not a promise of identical output across machines
  or days.
- **4.** Review exposes what the requested setup changes in this repository. Add-only station application would prevent
  intentional updates; automatic withdrawal would delete output outside the request. Ordered overlap and declared
  scope support both replacement and preservation; recorded ownership limits removal to what a recipe's own previous
  version wrote.
- **5.** Native output serves readers who do not run Cook; source history serves those inspecting a recorded operation.
  Losing local history loses that recovery information, not the ability to apply supplied recipes.
- **6.** Program models remain useful without settling how they compose or receive parameters. Removing stale
  lifecycle requirements is not evidence that either current composition implementation should be removed.

## Rejected alternatives

### Organization choices maintained in engine releases

At its best, every user gets shared setup from one release. It loses because engine releases become coupled to
organization-specific choices and teams must fork or wait for changes. Cookbook authors can maintain those choices separately.

### Only copied repository content may affect evaluation

At its best, all source material is visible in one checkout. It loses because it prohibits the agreed HTTPS
source flow and turns copying and merge management into engine responsibilities. Native output, the reviewed diff
and captured consumed sources provide distinct views without forbidding composition.

### Preserve the station and recipe update protocols unchanged

At its best, add-only station templates and three-way recipe merges offer established update rules. They lose here
because they decide station roles and recipe version management outside the agreed scope. Users supply recipe
versions; the separate model discussion decides APIs and lifecycle mechanics.

## Consequences

- Cookbook authors maintain program models and shared recipes independently; teams review the resulting native-file
  changes and manage the recipe versions they supply.
- Reviewers may need the cookbook sources and local batch records to explain how output was produced. Native output
  alone does not describe every dependency or arbitrary side effect of executable recipes.
- Engine version/checksum pinning protects the selected engine download; it does not authenticate every recipe source
  or sandbox recipe execution. Cookbook trust and the model APIs need their own discussion.

## Confirmation

Review the acme example with shared setup and a repository-specific choice: the batch changes only declared targets,
shows later overrides in its final diff, and preserves unrelated configuration. Trace local and remote consumed sources
to their original locations and captured history. Confirm that neither the content split nor references to issue #9
select a station API, recipe updater, or deletion of a composition implementation. No new check command is required.
