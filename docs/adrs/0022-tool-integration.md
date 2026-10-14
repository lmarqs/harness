# 0022. Ingredients model programs' native configuration

## Objective

**Problem.** Teams configure different agents, editors, version control tools and loop runners. Each program has its
own files and configuration conventions, which change independently. Mixing that knowledge into the configuration
engine makes program changes harder to maintain and confuses configuring a program with controlling its execution.

**Goal.** Teams reuse program-specific setup without making their chosen programs part of the engine's workflow.
Program knowledge can evolve separately from the rules for reviewing and writing file changes.

## Context

- The [architecture](../architecture.md#scope-and-responsibilities) assigns recipe evaluation, ordered file operations,
  diff presentation, approval, recovery and writes to Cook (FR-1 through FR-5 in its
  [requirements](../architecture.md#requirements)).
- [Ingredients](../features/glossary.md) are program data models for native files and configuration. Git's ignore
  file and Codex's configuration and skills illustrate that responsibility.
- Cookbooks hold and ship ingredients and trusted Groovy recipes through their repositories, with builds that can be
  separate from the engine build (FR-2). Native output is consumed by the configured programs directly
  ([0010](0010-dependence-on-the-binary.md)).
- Worked example: `acme/api` and `acme/infra` use the same agent but choose different project instructions. They can
  reuse its configuration model while supplying different choices through recipes.

## Decision

1. Ingredients carry program-specific knowledge as models of native files and configuration. Cookbooks hold and ship
   those models and recipes; Cook handles the resulting ordered file operations.
2. Configuring a program does not make Cook its runner. External commands are not batch actions. Recipes remain trusted
   executable code, whose arbitrary side effects are outside batch guarantees.
3. Native targets, ordered composition and ownership follow [0007](0007-ownership.md); omitting a model from an
   invocation removes nothing.
4. Engine package pinning follows [0017](0017-engine-version.md) and is separate from cookbook content: an engine
   upgrade does not select new cookbook content.

## Reasons

- **1.** Program formats evolve independently (Objective). Keeping their models in cookbooks lets cookbook authors
  maintain that knowledge separately, while engine maintainers focus on file-operation behavior. The trade is that
  cookbook authors maintain compatibility with the programs they model; separating responsibilities alone does not
  guarantee compatible versions.
- **2.** FR-3 limits batches to file operations. Contracts for launching and supervising programs would make a
  configuration engine run them. Native files let teams use their own runners without making Cook part of every run.
  The trade is that Cook does not police how those programs run, and trusted recipe code can act beyond the declared
  batch.
- **3.** FR-7 through FR-10 make requested targets and order the basis of composition, so in the acme example one model
  serves both repositories while each recipe's targets decide the result. Recorded ownership decides only what a
  recipe's later versions remove (FR-8). Reconciling earlier output would block requested changes instead (FR-9). The
  trade is that a recipe can propose replacing an edited target; the combined diff and whole-batch approval make that
  change visible.
- **4.** The wrapper selects an engine package, not a team's program setup. Keeping these responsibilities distinct
  avoids coupling cookbook maintenance to engine upgrades. The trade is that users select recipe versions themselves;
  engine checksum verification does not establish cookbook trust ([0026](0026-cookbook-trust.md)).

## Rejected alternatives

### Keep all program knowledge in the engine

At its best, a single engine release provides and maintains every supported setup, giving users one maintenance
source. It loses because it couples program-specific changes to the engine's release schedule and gives cookbook
authors less independence. Cookbooks own those models; this does not require excluding all cookbook assets from an
engine package.

### Ingredients describe how to launch and check programs

At its best, ingredient descriptions of agent launches, launch arguments and version checks make running programs
extensible. It loses because running programs is outside the engine responsibilities of FR-1 through FR-5, and it
would turn native configuration models into program runners.

### Require data-only ingredients and recipes

At its best, a fixed declarative schema makes descriptions uniform and constrains their behavior. It loses because
the agreed design makes recipes trusted executable Groovy code ([0025](0025-recipes.md)); a data-only schema would
replace that design rather than constrain it.

## Consequences

Costs:

- Cookbook authors maintain program-specific models and compatibility; the engine cannot promise that all supplied
  configuration works with all program versions.
- Trust in recipe code remains necessary even when declared changes are reviewed.

Must be maintained:

- The distinction between native configuration models, cookbook choices and engine file-operation responsibilities.
- Ordered composition and review against current content, without a `cook.lock` prerequisite.

## Confirmation

- Review the acme example against FR-1 through FR-10: does a shared model accept different recipe choices, can later
  instructions replace earlier values, and does applying one recipe leave unrelated earlier output intact?
- Review a proposed ingredient: does it describe native files and configuration only, with nothing that launches or
  supervises the program?
- Review an engine upgrade against FR-26: does it change the pin without selecting cookbook content?
