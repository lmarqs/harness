# 0006. Repositories hold trusted recipes separately from the shared engine

## Objective

**Problem.** When a practice spreads by copying its engine code into each repository, every copy becomes a fork: a fix
made in one never reaches the others. Each repository also has facts and configuration choices only it knows, which
must live with it. Confusing those choices with engine implementation makes both harder to maintain.

**Goal.** A fix to the shared engine reaches repositories through one release, while each repository controls its
own configuration choices and can reuse practices maintained elsewhere.

## Context

- Worked example: acme copies its configuration renderer from `acme/api` into `acme/web` and `acme/infra`. A local
  renderer change in `acme/web` makes a later bug fix in `acme/api` difficult to transfer to either copy.
- Repository-specific choices include program settings, instruction text, and build or check commands to put in
  native configuration. Sharing the renderer does not make those choices the engine's responsibility.
- The agreed [architecture](../architecture.md#ingredients-recipes-and-cookbooks) distinguishes trusted executable
  Groovy recipes, ingredients as program data models, cookbook distribution, and the shared file-operation engine.
- Cook retains its JVM stack ([0003](0003-implementation-language.md)) and packages Java with the engine
  ([0004](0004-runtime-dependencies.md)). Recipe execution therefore does not require a separate user-installed
  scripting runtime.
- The repository-facing contract is costly to change once recipes depend on it. The ingredient/recipe API,
  composition rules, station role, and hooks remain a separate model discussion.

## Decision

1. The engine's evaluation, batch review, recovery, and writing responsibilities stay in the shared engine release.
   A repository need not vendor or maintain a copy of that engine to describe its own configuration.
2. Repositories may hold trusted executable Groovy recipes and their source/template files. Recipes describe
   requested file operations; they are not restricted to declarative YAML or a closed set of lifecycle steps.
3. Cookbooks hold and ship ingredients and recipes through their own repositories, with builds that can be separate
   from Cook's build. Ingredients model programs' native files and configuration. This decision does not select
   model APIs, nesting, lifecycle hooks, or a mandatory cookbook storage or version-management scheme.
4. A committed wrapper pins the engine version and package checksum, downloads and verifies the platform package
   on first use, caches it locally, and changes version only through an explicit upgrade. The platform-specific
   JVM package uses jpackage and contains Java. Engine pinning is separate from selecting the recipe version to use.
5. Recipes are trusted code. Approval, destination boundaries, and rollback govern their declared batch operations;
   they do not constrain arbitrary side effects during recipe evaluation. External commands are not batch actions.

## Reasons

- **1.** Sharing the engine avoids the renderer forks in Context. A reviewed engine-pin change can bring the same
  engine fix to each repository. The trade is that engine source is not necessarily present in the destination.
- **2.** Executable recipes express repository choices and reusable configuration practices without copying Cook's
  review and writing machinery. The trade is that source review alone does not establish the resulting diff or rule
  out side effects; authors and users must trust the code they evaluate.
- **3.** Program knowledge changes independently of engine mechanics. Separate cookbook maintenance keeps those
  release responsibilities apart. The trade is that engine and cookbook compatibility still needs attention;
  this ADR does not settle its API or serialization contract.
- **4.** A version and checksum make the selected engine package reviewable, while bundled Java avoids a separate
  runtime installation. The trade is complete platform-specific packages and separate cookbook/recipe updates.
- **5.** A declared file operation can be previewed and recovered by Cook. Arbitrary Groovy code can act outside
  those operations, so treating review of the batch as a sandbox would promise protection the engine does not give.

## Rejected alternatives

### Vendored or hand-copied engine per repository

At its best, the engine is available offline and can be tailored locally. It loses because locally edited copies
fork the shared mechanics and each engine fix becomes a separate integration in every repository.

### Data-only repository configuration

At its best, a small declarative format is easy to inspect without executing it. It loses as a universal repository
contract because the agreed design uses trusted executable recipes. Native configuration may still be data; that
property does not apply to every source that produces it.

### One release for the engine and all program knowledge

At its best, one package supplies everything together. It loses because program-model and recipe changes then
require engine releases even when the review and writing mechanics are unchanged.

## Consequences

Costs:

- Users trust recipe code as well as the engine package; the batch diff covers only declared file changes.
- Cookbooks and recipe sources have maintenance and compatibility concerns separate from the engine pin.

Must be maintained:

- The separation between shared engine mechanics, recipe choices, and cookbook program models.
- The distinction between the bundled runtime and independently selected recipe content.

## Confirmation

- Review accepts a repository-local Groovy recipe without requiring it to be converted to YAML or forbidding its
  source files in the repository.
- Review traces evaluation, combined diff, approval, recovery, and declared writes to engine responsibilities,
  while leaving program-specific configuration choices to cookbook content and recipes.
- Distribution descriptions use complete JVM packages with bundled Java and explicit engine pins, without
  claiming that recipe code is sandboxed or that a deferred model API has been chosen.
