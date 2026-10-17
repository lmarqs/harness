# 0001. Cook reviews and performs recipe-declared file operations

## Objective

**Problem.** Teams copy program setup between repositories by hand, and those copies diverge. A tool that helps share
that setup needs a clear responsibility: otherwise it grows into a second coding agent or workflow controller,
duplicating products that already manage models, credentials and sessions.

**Goal.** Developers can carry a way of working between repositories through reviewed configuration changes while
keeping their preferred programs and control over how they work.

## Context

- The [architecture](../architecture.md#scope-and-responsibilities) assigns Cook the evaluation of
  [recipes](../features/glossary.md), batch review and file writes. Ingredients model native program
  files; cookbooks hold and ship those models and recipes.
- Programs consume their own native configuration. Configuring a coding agent or editor does not require Cook to run
  it or to control its workflow.
- Worked example: acme shares agent settings between `acme/api` and `acme/web`. Each team wants to review the settings
  against its repository's current files, but neither team needs to change the agent it uses or the stages of its work.
- Recipe authors supply trusted executable Groovy code. Evaluation can have effects beyond the file operations it
  declares.

## Decision

1. Cook evaluates trusted Groovy recipes into an ordered batch of file operations, presents the combined diff,
   obtains whole-batch approval and performs the declared writes.
2. Ingredients model a program's native files and configuration. Cookbook repositories hold and ship ingredients and
   recipes, with builds that can be separate from the engine build. Cook's role remains file operations across
   programs.
3. Cook is neither a coding agent nor a fixed software development lifecycle. Model reasoning and provider
   credentials stay with the agents users choose. External command execution is not a batch action
   ([0019](0019-action-kinds.md)).
4. Approval, destination boundaries and rollback govern declared batch operations, not arbitrary recipe code or its
   side effects. The destination boundary is decided in [0012](0012-write-boundary.md) and recovery in
   [0007](0007-ownership.md).

## Reasons

- **1.** The acme example needs a shared way to propose configuration changes, not a shared runner. A combined diff
  makes the requested result reviewable against current content.
  The trade: Cook does not determine whether the resulting setup is the best workflow for the team.
- **2.** Program configuration changes on each program's schedule. Cookbook-maintained models let authors update that
  knowledge separately from the engine's review and writing logic. The trade: cookbook authors maintain compatibility
  with the programs they describe.
- **3.** Reimplementing a model loop duplicates a coding agent's work and takes on credential, provider and cost choices
  that belong to its users. Making configuration the engine's responsibility keeps the configured programs replaceable.
  The trade: Cook does not enforce a development process or judge the work those programs perform.
- **4.** Executable recipes cannot be treated as data-only declarations. Batch review exposes declared file changes,
  but does not make arbitrary recipe execution safe. The trade: users must trust recipe code before evaluating it,
  independently of approving the resulting batch.

## Rejected alternatives

### A fixed lifecycle controller

At its best, one controller gives teams common stages and guards. It loses because configuring native files does not
require imposing those stages, supervising agents or recording their verification.

### A coding agent with its own model loop

At its best, one program chooses the setup and implements it end to end. It loses because it duplicates the agents
users already choose and brings their model credentials, cost and changing APIs into the configuration engine.

## Consequences

Costs:

- Cookbook authors maintain program-specific configuration knowledge.
- Users trust the recipes they evaluate in addition to reviewing the declared file changes.

Must be maintained:

- Ordered operations, approval and recovery in the engine.
- Configuration that stays native to the program that consumes it.

## Confirmation

- Does the [architecture](../architecture.md#scope-and-responsibilities) assign the engine only recipe evaluation,
  review and writes (FR-1), with batches of file operations (FR-3)?
- Do the approval and recovery claims in the documentation apply only to declared operations (FR-4)?
- Does a proposed engine change run a model, hold provider credentials or impose stages on a team's work? Such a
  change falls outside the role this decision gives Cook.
