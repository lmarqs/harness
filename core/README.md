# Core implementation

The core declares typed Kotlin cookbook contracts and reconciles their outputs through Groovy filesystem and document adapters. Repository recipes may use dynamic Groovy; published definitions compile statically.

## Recipe execution

Recipes live in `cookbook/recipes/<name>/recipe.groovy` or `cookbook/recipes/<name>.groovy` and return `{ kitchen, book, options -> component }` or a `DeclaredRecipe`. A direct `.groovy` path can also return a factory outside the cookbook directory. The CLI and Gradle invoke the same factory used by `book.recipe(name, kitchen, options)`. Project inputs resolve against `kitchen.repository.directory`; supplier definitions resolve through `book`. Nested recipe names and packaged factories are supported, with local definitions taking precedence. Two local files for one name are rejected as ambiguous.

Declared recipes accept `--name=value`, `--name value`, and legacy `--set name=value`; repeatable choices retain their order. `<recipe> --help` shows required inputs, cardinality, choices, and execution options without running the factory.

Optional repository composition scripts such as `cook.groovy` receive `book`, `kitchen`, `repositoryDirectory`, `scriptDirectory`, and `preferences`. They can return a recipe component or a typed `Batch`; both pass through the same checked rendering pipeline before planning. `evaluateRecipe(relativePath, arguments)` delegates to another script while preserving the destination repository.

## Rendering

Compiled contributors and the dynamic composition DSL evaluate directly into the same Kotlin render records. Aggregation checks file sharing and key overlap on those records; reconciliation keeps identities and document locations typed. Ownership encoding and reporting are the places that convert records to maps. Document adapters retain their format-specific restoration metadata.

## Contribution identity

A published ingredient's annotation matches its artifact name. Its explicit instance name identifies its use in a batch. The contribution address combines that instance with its region: `git/ignore`, for example. Class packages and batch names do not change persistent ownership addresses.

Packages under `thecook.cookbook.ingredients` identify supplier code; Codex and Claude code lives under `thecook.cookbook.recipes.agentic`. `AgentInstructions` is a renderer within the agent-policy module, with historical default instance name `agent-policy`.

## Agent recipes

Codex and Claude each generate their native file declarations in one compiled renderer inside its recipe module. Their policy translators produce native settings and rules separately. Factory recipes adapt their named contributions to these renderers; custom paths, hooks, instruction text, rule files, and deferred connections remain supported. Factory defaults retain `codex` / `claude` instance names; policy constructors retain `cookbook.codex` / `cookbook.claude`. Existing key granularity is preserved when recooking either form.

The factories are loaded with `book.recipe('agentic/codex', kitchen, options)` or `book.recipe('agentic/claude', kitchen, options)`. Their renderer classes are `CodexFiles` and `ClaudeFiles`.

The dynamic DSL can include a compiled contributor with `contribute { contributor }`. Its instance must match the mounted declaration; its outputs use the shared typed render model.

## Shared files

Several ingredients can contribute separate text blocks or structured keys to one file. Whole files and symlinks require exclusive ownership. Equal or overlapping structured key paths conflict even when their values match; format differences and case collisions also reject before writes.

Reconciliation preserves handwritten content and checks each managed contribution for drift. Withdrawing one contribution restores its adopted original or removes its generated content. Transferring a structured key to a new contribution requires an applied unmount first. Ejection retains native content and releases ownership.

Each cooked batch declares the complete desired set. Running another recipe is not an additive installation operation.

## Ownership records

Version-two `.cook/state.json` records each partial region by contribution address, with its instance owner, optional published definition name, rendered content and adopted original. Document metadata stays at the file level. Whole files and symlinks retain their exclusive owner.

Version-one records are converted in memory during planning. Existing markers and adopted originals are preserved; the transaction writes the converted record only on successful application. Existing saved plans remain readable, and rollback restores their exact previous state.

This implementation keeps ownership state in the ignored `.cook` directory. That differs from [ADR 0007](../docs/adrs/0007-ownership.md), which calls for a committed manifest. A fresh clone without the state cannot infer ownership or adopted originals from generated files alone. Markers cannot represent JSON ownership.

The current executable Groovy suppliers also differ from the data-only model in [ADR 0023](../docs/adrs/0023-core-and-contracts.md) and [ADR 0024](../docs/adrs/0024-ingredients-and-stations.md). This describes the implemented model; those older decisions need reconciliation before release.

## Verification

Run `./gradlew check cliBuild cookbookBuild`. Tests exercise shared files, adoption, individual removal, collisions, drift, ejection, state migration, transaction recovery, and both dynamic and compiled declarations.
