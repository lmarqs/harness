# Core implementation

The core declares typed Kotlin cookbook contracts and reconciles their outputs through Groovy filesystem and document adapters. Repository recipes may use dynamic Groovy; published definitions compile statically.

## Recipe execution

CLI and Gradle recipes receive `repositoryDirectory` for project inputs, `scriptDirectory` for supplier assets, and `preferences` for arguments. `evaluateRecipe(relativePath, arguments)` delegates to another supplier script while preserving the destination repository. Moving a recipe does not change which project's policy it reads.

## Contribution identity

A published ingredient's annotation matches its artifact name. Its explicit instance name identifies its use in a batch. The contribution address combines that instance with its region: `git/ignore`, for example. Class packages and batch names do not change persistent ownership addresses.

Packages under `thecook.cookbook.ingredients` identify supplier code; Codex and Claude code lives under `thecook.cookbook.targets`. `AgentInstructions` is a renderer within the agent-policy module, with historical default instance name `agent-policy`.

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
