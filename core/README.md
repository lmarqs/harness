# Core implementation

This guide describes existing code, including its ownership-based reconciliation. The
[architecture](../docs/architecture.md) states the intended design; current implementation details do not change
that contract or settle the deferred ingredient/recipe APIs, composition rules or lifecycle hooks.

The core declares typed Kotlin cookbook contracts and reconciles their outputs through Groovy filesystem and document adapters. Repository recipes may use dynamic Groovy; published definitions compile statically.

## Recipe execution

Recipes live in `cookbook/recipes/<name>/recipe.groovy` or `cookbook/recipes/<name>.groovy` and return `{ kitchen, book, options -> component }` or a `DeclaredRecipe`. A direct `.groovy` path can also return a factory outside the cookbook directory. The CLI and Gradle invoke the same factory used by `book.recipe(name, kitchen, options)`. Project inputs resolve against `kitchen.repository.directory`; supplier definitions resolve through `book`. Nested recipe names and packaged factories are supported, with local definitions taking precedence. Two local files for one name are rejected as ambiguous.

Declared recipes accept `--name=value`, `--name value`, and legacy `--set name=value`; repeatable choices retain their order. `<recipe> --help` shows required inputs, cardinality, choices, and execution options without running the factory.

Declaration help loads the trusted Groovy definition; it skips the returned factory, not arbitrary top-level script
code. The current CLI accepts one named recipe or local `.groovy` script per invocation. It does not implement the
target multiple-operand grammar, HTTPS source loading or the restriction of local sources to the destination
repository. Its `apply` command executes a saved plan; the target `revert <batchId>`, `batch list`, `batch inspect` and
`--batch-approve` are not implemented.

A `DeclaredRecipe` wraps a `RecipeDeclaration` and a factory. Required single paths, required repeatable choices and
optional single choices validate before the factory runs. Paths arrive as strings, repeatable choices as ordered lists
with identical duplicates removed, and optional choices as strings. Closure factories without a declaration receive
command-line arguments as strings; a composition can pass typed Groovy maps, lists and booleans. `repo`, `dry-run`,
`remove`, `script`, `plan`, `library`, `set` and `help` are reserved argument names. `book.declaration(name)` returns
a recipe's declaration without mounting or rendering it. `--remove` requires a declaration with a removal scope and
withdraws the selected applications using saved ownership; recipes without a declaration cannot be removed this way.

A standalone composition needs core on its classpath and a `Cookbook` pointing at available recipe sources. Mounting
declares output; `kitchen.plan()` reconciles it without writing, and `plan.apply()` writes it:

```groovy
import thecook.composition.Kitchen
import thecook.cookbook.Cookbook

def book = new Cookbook(new File('cookbook'))
def kitchen = new Kitchen(new File('/path/to/repository'))
book.recipe('grill', kitchen, [script: 'git status']).mount()
def plan = kitchen.plan()
println plan.changes*.path
```

Optional repository composition scripts such as `cook.groovy` receive `book`, `kitchen`, `repositoryDirectory`, `scriptDirectory`, and `preferences`. They can return a recipe component or a typed `Batch`; both pass through the same checked rendering pipeline before planning. `evaluateRecipe(relativePath, arguments)` delegates to another script while preserving the destination repository. `the-cook cook` still loads the repository's `cook.groovy` by default, while a direct `.groovy` path names any local script. A `cook.groovy` file is not required to run a named or direct recipe.

## Rendering

Compiled contributors and the dynamic composition DSL evaluate directly into the same Kotlin render records. Aggregation checks file sharing and key overlap on those records; reconciliation keeps identities and document locations typed. Ownership encoding and reporting are the places that convert records to maps. Document adapters retain their format-specific restoration metadata.

The current CLI reports changed paths, using `--dry-run` for a preview without target writes. Normal cooking applies the
plan without prompting. Saved plans and the transaction journal contain before/after content and state, but they are not
the intended consumed-source snapshots or per-batch records with reversible patches. The filesystem transaction checks
observed content and modes before writing, journals the proposal and restores applied changes on write failure.
Successful rollback removes that journal; the target requirement to retain the batch record and failure needs
implementation. Trusted recipe evaluation is not sandboxed by these checks.

## Contribution identity

A published ingredient's annotation matches its artifact name. Its explicit instance name identifies its use in a batch. The contribution address combines that instance with its region: `git/ignore`, for example. Class packages and batch names do not change persistent ownership addresses.

Packages under `thecook.cookbook.ingredients` identify supplier code; Codex and Claude code lives under `thecook.cookbook.recipes.agentic`. `AgentInstructions` is a renderer within the agent-policy module, with historical default instance name `agent-policy`.

## Agent recipes

Codex and Claude each generate their native file declarations in one compiled renderer inside its recipe module. Their policy translators produce native settings and rules separately. Factory recipes adapt their named contributions to these renderers; custom paths, hooks, instruction text, rule files, and deferred connections remain supported. Factory defaults retain `codex` / `claude` instance names; policy constructors retain `cookbook.codex` / `cookbook.claude`. Existing key granularity is preserved when recooking either form.

The factories are loaded with `book.recipe('agentic/codex', kitchen, options)` or `book.recipe('agentic/claude', kitchen, options)`. Their renderer classes are `CodexFiles` and `ClaudeFiles`.

The dynamic DSL can include a compiled contributor with `contribute { contributor }`. Its instance must match the mounted declaration; its outputs use the shared typed render model.

## Shared files

Several ingredients can contribute separate text blocks or structured keys to one file. Whole files and symlinks require exclusive ownership. Equal or overlapping structured key paths conflict even when their values match; format differences and case collisions also reject before writes.

Use `output('path', [seed: true]) { text { 'starter content' } }` for an editable source file, or `SeedOutput` from a typed contributor. A seed creates an absent regular file in the checked transaction and then leaves it unowned. Recooking and application removal preserve it, including user edits and empty content. Seeds conflict with other declarations for the same path and with existing managed ownership. Directories and symlinks at the destination are rejected.

Reconciliation preserves handwritten content and checks each managed contribution for drift. Withdrawing one contribution restores its adopted original or removes its generated content. Transferring a structured key to a new contribution requires an applied unmount first. Ejection retains native content and releases ownership.

An application recipe replaces only its selected application contributions. A direct legacy `Batch` has no application identity: each run replaces its legacy contributions and preserves unrelated application ownership. Removing a legacy contribution by omitting it from the next Batch can withdraw that contribution, but cannot implicitly delete an independently owned application. Overlap with an application is rejected rather than transferred. Use `--remove` to explicitly withdraw selected applications.

## Ownership records

Version-three `.cook/state.json` records each partial region by contribution address, with its instance owner, optional published definition name, rendered content and adopted original. Document metadata stays at the file level. Whole files and symlinks retain their exclusive owner. Application IDs map to their owned addresses; `legacy` explicitly identifies addresses without a known application. Recipe path and input provenance is diagnostic metadata, not an instruction to load or run a repository file.

Version-one and version-two records are converted in memory during planning. Unclaimed older contributions become legacy ownership. An application cannot claim an overlapping legacy contribution, even with equal content or a matching recipe name; withdraw that contribution in an applied transaction first. Existing markers and adopted originals are preserved; the transaction writes the converted record only on successful application. A preview leaves state bytes unchanged. Existing saved plans remain readable, and rollback restores their exact previous state.

This implementation keeps ownership state in the ignored `.cook` directory. A fresh clone without the state cannot
infer ownership or adopted originals from generated files alone. Markers cannot represent JSON ownership. The intended
architecture records ownership in a committed `cook.lock`, and its apply does not require that lock.

## Differences from the intended architecture

The [operation contract](../docs/architecture.md#evaluation-review-and-writes) and
[ADR 0007](../docs/adrs/0007-ownership.md) require changes from supplied declarations and current content, with
ownership recorded in a committed `cook.lock` that never blocks a change. When two recipes write the same target, the
last one wins and becomes its owner; applying one recipe never removes another's output; and existing or edited content
uses the actual terminal diff with whole-batch approval. Current `.cook/state.json` ownership identities, overlap
conflicts, adopted-original restoration and managed-edit vetoes describe the reconciler, not that target contract.
Current `--remove` withdraws applications using stored ownership; the target defers discard and reverses recorded
batches with `revert`.

Some current recipe factories expose `--adopt`; render records and reconcilers also retain adoption gates. The target
has no adoption flag, alias or separate adoption step. Removing those entry points and underlying gates in favor of
actual batch diff review and approval remains product implementation work.

The [history design](../docs/architecture.md#paths-source-snapshots-and-local-history) requires consumed local and
downloaded sources with their origins and one reversible patch per touched file, saved before target writes.
Ownership state, diagnostic recipe/input provenance and saved plans do not supply that history. Records under
`.cook/batches/<batchId>/`, the committed `cook.lock` and reversal of a recorded batch still need implementation.

[ADR 0023](../docs/adrs/0023-core-and-contracts.md) describes operation boundaries, and
[ADR 0024](../docs/adrs/0024-ingredients-and-stations.md) preserves program models while deferring their APIs.
Neither requires data-only recipes or decides to delete dynamic composition or compiled suppliers. The Kotlin
contracts and Groovy APIs above are evidence of the current implementation, not a finalized model for the target.

The [repository launcher](../cook) requires the local `cliBuild` distribution and changes into the source checkout.
The current jpackage build bundles Java, but the launcher has no version/checksum pin, downloader or release cache.
See the [root build instructions](../README.md#start-from-a-fresh-clone) and
[wrapper design](../docs/features/wrapper.md) for that distinction.

## Verification

The existing development tasks include `./gradlew check cliBuild cookbookBuild`. Tests exercise shared files,
adoption, individual removal, collisions, drift, ejection, state migration, transaction recovery, and both dynamic
and compiled declarations. Those tests cover the current implementation; they do not establish that the target
operations or deferred model decisions are implemented.
