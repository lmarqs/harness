# 0022. Ingredients model programs' native configuration

## Objective

**Problem.** Teams configure different agents, editors, version control tools and loop runners. Each program has its
own files and configuration conventions, which change independently. Mixing that knowledge into the configuration
engine makes program changes harder to maintain and confuses configuring a program with controlling its execution.

**Goal.** Teams reuse program-specific setup without making their chosen programs part of the engine's workflow.
Program knowledge can evolve separately from the rules for reviewing and writing file changes.

## Context

- The [architecture](../architecture.md#scope-and-responsibilities) assigns recipe evaluation, ordered file operations,
  diff presentation, approval, recovery and writes to Cook (PRD FR-1 through FR-6).
- Ingredients are program data models for native files and configuration. Git's ignore file and Codex's configuration
  and skills illustrate that responsibility; these examples do not prescribe a supplier API.
- Cookbooks hold and ship ingredients and trusted Groovy recipes through their repositories, with builds that can be
  separate from the engine build (FR-2). Native output is consumed by the configured programs directly
  ([0010](0010-dependence-on-the-binary.md)).
- Worked example: `acme/api` and `acme/infra` use the same agent but choose different project instructions. They can
  reuse its configuration model while supplying different choices through recipes.
- Distribution uses platform-specific jpackage packages containing Java. The committed wrapper pins an engine version
  and matching package checksums, downloads and verifies an uncached package on first use, and caches it locally.
  Upgrades are explicit ([0017](0017-engine-version.md), [0021](0021-release-trust.md), FR-26).

## Decision

1. Ingredients carry program-specific knowledge as models of native files and configuration. Cookbooks hold and ship
   those models and recipes; Cook handles the resulting ordered file operations. This responsibility split imposes no
   blanket prohibition on program names in engine code or on packaging cookbook content with a distribution.
2. Configuring a program does not make Cook its runner. Version control commands, worker launches, tracker updates,
   jails, fixed lifecycle stages and completion signals are not engine responsibilities. External commands are not
   batch actions. Recipes remain trusted executable code, whose arbitrary side effects are outside batch guarantees.
3. Native targets are file paths, structured key paths or marked text sections. Cook proposes changes against current
   content, combines overlapping operations in declared order and presents one batch diff. Ingredients require no
   persistent ownership database, adoption step or automatic withdrawal when a model is omitted.
4. Engine package pinning is separate from cookbook distribution and user-managed recipe versions. An engine upgrade
   does not select new cookbook content. This record does not introduce cookbook hash admission, a registry, a
   recipe updater, program-detection rules or a safety-floor enforcement protocol.
5. Ingredient/recipe APIs, composition and nesting, station semantics, decorators, annotations, parameter injection
   and lifecycle hooks remain for the separate model discussion. Issue #9 does not decide deletion of dynamic
   composition or compiled suppliers; neither implementation becomes the final API through this record.

## Reasons

- **1.** Program formats evolve independently (Objective). Keeping their models in cookbooks lets cookbook authors
  maintain that knowledge separately, while engine maintainers focus on file-operation behavior. The trade is that
  cookbook authors maintain compatibility with the programs they model; separating responsibilities alone does not
  guarantee compatible versions or require a particular packaging arrangement.
- **2.** FR-3 and FR-6 bound Cook's role. Launch and lifecycle contracts would add orchestration to a configuration
  engine. Native files let teams use their own runners without making Cook part of every run. The trade is that Cook
  does not police how those programs run, and trusted recipe code can act beyond the declared batch.
- **3.** FR-7 through FR-10 make requested targets and order the basis of composition. An ownership manifest would
  replace that contract with reconciliation of earlier output. The trade is that a recipe can propose replacing an
  edited target; the actual combined diff and whole-batch approval make that change visible.
- **4.** The wrapper selects an engine package, not a team's program setup. Keeping these responsibilities distinct
  avoids coupling cookbook maintenance to engine upgrades. The trade is that users select recipe versions themselves;
  engine checksum verification does not establish cookbook trust ([0026](0026-cookbook-trust.md)).
- **5.** FR-30 preserves the model questions for their own discussion. Resolving them from an older cleanup proposal
  would make an API decision without that discussion. The trade is that the concept is established before its public
  authoring contract is finalized.

## Rejected alternatives

### Keep all program knowledge in the engine

A single engine release could provide and maintain every supported setup. That gives users one maintenance source,
but couples program-specific changes to the engine's release schedule and gives cookbook authors less independence.
Cookbooks own those models; this does not require excluding all cookbook assets from an engine package.

### Retain ingredient-based launch, floor and health contracts

Descriptions of worker launches, bypass arguments and version checks could make orchestration extensible. They solve
an orchestration problem outside FR-1 through FR-6 and would turn native configuration models into lifecycle providers.
They are not requirements of this engine design.

### Require data-only ingredients and forbid executable suppliers

A fixed declarative schema could make descriptions uniform and constrain their behavior. It would also settle the
supplier model before the separate discussion. The agreed JVM/Groovy design does not support rejecting executable
suppliers merely on an assumption that their runtime is unavailable.

## Consequences

Costs:

- Cookbook authors maintain program-specific models and compatibility; the engine cannot promise that all supplied
  configuration works with all program versions.
- Trust in supplied code remains necessary even when declared changes are reviewed.

Must be maintained:

- The distinction between native configuration models, cookbook choices and engine file-operation responsibilities.
- Ordered composition and review against current content, without ownership or adoption prerequisites.

## Confirmation

Review the design against FR-1 through FR-10, FR-26 and FR-30: the acme example permits a shared model with different
recipe choices, later instructions can replace earlier values, and applying one recipe leaves unrelated earlier
output intact. The engine promises concern declared operations, not worker execution or recipe sandboxing. Package
verification follows the explicit engine pin independently of cookbook selection. These are design review criteria,
not claims that the target behavior is implemented or mandatory commands for this documentation run.
