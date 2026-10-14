# Cookbook recipe factories

These guides describe current cookbook execution. The [architecture](../../docs/architecture.md) defines the intended
product; these factories and ownership records do not decide the deferred ingredient/recipe model, composition or hooks.
Ingredients model native program files; cookbook sources and artifacts supply those models and recipes separately
from the engine's release responsibilities.

## Current execution and target requirements

The current CLI runs one named recipe or local Groovy source per invocation. `--dry-run` reports changed paths and
performs ownership reconciliation; it can fail on unmanaged output or managed edits. Ordinary cooking writes without
a terminal content diff or approval prompt. The current `apply` command consumes a saved plan, not recipe operands.

The target [CLI](../../docs/features/cli.md) requires ordered operands, stateless apply/discard, recorded-run revert
and whole-batch diff approval. Consumed-source snapshots, per-run reversible patches and pinned wrapper downloads
also require implementation. The target eliminates `--adopt`, adoption aliases and underlying adoption/drift gates:
existing or edited content must follow the actual batch diff and whole-batch approval flow. Adoption options in these
guides describe existing code awaiting that change, rather than target prerequisites.

## Before running recipes

From the checkout root, with a Java 17 JDK and access to build dependencies:

```sh
./gradlew cliBuild
./cook --help
./cook agentic/permission --help
```

These help commands need no policy or personal skills. Recipe-specific help requires a declaration; most factories
listed below have none. General `--help` still works. Declaration help loads trusted Groovy definitions but skips
their returned factories; arbitrary top-level script code is not sandboxed.

The checkout's `./cook` script changes into this checkout and launches the prebuilt JVM distribution. It does not
pin, download, verify or cache a release. For another destination, use `--repo PATH` or the built distribution's
launcher by absolute path from that directory. See the [root entry guide](../../README.md#start-from-a-fresh-clone)
and [packaging instructions](../../README.md#install).

Cooking examples require the selected sources and dependencies, plus absent or already managed output unless the
factory supports the required adoption. This checkout's tracked agent files have no ownership records in a fresh
clone, so even a dry run can reject them. The combined [cookbook setup](cookbook/README.md) additionally needs a
`--policy` path and three existing personal skill sources. It is not a prerequisite-free quickstart.

```sh
# Requires the policy/skill and output prerequisites in the combined setup guide.
./cook cookbook --policy=.agents/policy.yaml --dry-run
# Requires absent or already managed selected output.
./cook the-cook --dry-run
./cook agentic/codex --instructions='Run tests before committing.' --dry-run
```

## Factory loading and arguments

Each `recipe.groovy` returns a closure factory `{ kitchen, book, options -> component }` or a `DeclaredRecipe`.
The CLI reaches these factories, and a Groovy composition calls them through `book.recipe(name, kitchen, options)`.
A successful direct CLI invocation still depends on whether its string arguments satisfy the factory's input contract.

Names resolve to `cookbook/recipes/<name>/recipe.groovy` or `cookbook/recipes/<name>.groovy`, then packaged resources
available on the classpath. Two local forms for one name are ambiguous and rejected. Local definitions take precedence;
explicit local `.groovy` paths work too. This is current name lookup, not the target exact-path/URI contract.
The current loader does not implement `file://` or HTTPS recipe operands.

A `DeclaredRecipe` wraps a `RecipeDeclaration` and a factory. Required single paths, required repeatable choices and
optional single choices validate before the factory. Paths arrive as strings, repeatable choices as ordered lists
with identical duplicates removed, and optional choices as strings. Legacy closure factories receive scalar CLI
strings; composition code can pass typed Groovy maps, lists and booleans. The CLI does not decode JSON strings into
maps or convert `--adopt=true` into boolean `true` for factories that explicitly require that type. Use a Groovy
composition for those examples rather than translating their maps directly into CLI flags.

Running a factory mounts its returned component and reconciles its rendered children against saved ownership.
Applications update their own contributions, including withdrawing omitted contributions within the selected
application. Other application ownership is retained, while unassigned legacy contributions are reconciled as a group.
This differs from the target rule that applying a recipe never implicitly removes unrelated earlier output.
See the [core implementation guide](../../core/README.md#shared-files).

The `agentic/permission`, `agentic/context`, and `agentic/init` definitions use flat `cookbook/recipes/agentic/<name>.groovy` source files. Their independent builds package those files at the standard recipe resource paths, and the local loader resolves the flat files directly. See the [independent agentic flow](agentic/README.md) for exact commands, ownership and build entry points. It needs no `cook.groovy`; that optional file and legacy Batch scripts remain supported entry points.

A supplier can expose metadata through `book.declaration(name)` without mounting or rendering the recipe. Declarations use the typed `thecook.model.RecipeDeclaration` and `RecipeInput` contracts. A path such as `--policy=.agents/policy.yaml` is interpreted by the factory relative to `kitchen.repository.directory`, including when the recipe file comes from another directory. `repo`, `dry-run`, `remove`, `script`, `plan`, `library`, `set`, and `help` are reserved declaration names. Existing version 1 policy syntax remains literal prefix based.

A repository's optional `cook.groovy` can return `book.recipe('cookbook', kitchen, [policy: '.agents/policy.yaml'] + preferences)`. Typed scripts returning `Batch` remain supported. There is no separate root `recipes/` convention.

`--remove` requires a recipe declaration with a removal scope and withdraws the selected applications using saved
ownership. The declared `agentic/permission`, `agentic/context` and `agentic/init` recipes accept `--harness=claude`
or `--harness=codex`, including repeated selections, and `--dry-run`. `agentic/init` selects its declared permission
and context children. Removal does not read the policy source; managed edits still reject the operation. The
undeclared factories below, including `cookbook` and `the-cook`, do not support CLI `--remove` or recipe-specific help.
Omitting contributions through a composition uses current reconciliation, not target `discard` or recorded-run reversal.

## Groovy composition examples

The recipe guides' Groovy snippets assume `book` and `kitchen` are initialized and the required cookbook sources or
artifacts and dependencies are available. A standalone program needs core on its classpath and a `Cookbook` pointing
to available sources; the example below assumes this checkout's `cookbook` directory and a real destination path.
A repository script can instead use the existing bound `book` and `kitchen`, compose recipes and return its root
component for `the-cook cook --script PATH`. See [script execution](../../core/README.md#recipe-execution).

Mounting declares output; `kitchen.plan()` reconciles it without applying writes. `plan.changes` contains change records with before/after content; printing them does not provide
the target terminal diff or an approval flow. Inspect the proposal before explicitly applying it. For example:

```groovy
import thecook.composition.Kitchen
import thecook.cookbook.Cookbook

def book = new Cookbook(new File('cookbook'))
def kitchen = new Kitchen(new File('/path/to/repository'))
book.recipe('grill', kitchen, [script: 'git status']).mount()
def plan = kitchen.plan()
println plan.changes*.path
// Review the plan before calling plan.apply().
```

Each directory documents its option map and behavior:

- This repository: [agent policy and skills](cookbook/README.md), [ignore local Cook state](the-cook/README.md).

- Dependencies: [Node](add-node-dependency/README.md), [Python](add-python-dependency/README.md), [Go](add-go-dependency/README.md).
- Environment: [development](development/README.md), [Compose service](add-compose-service/README.md), [local AWS resource](add-local-aws-resource/README.md).
- Migrations: [AWS credentials](migrate-aws-credentials/README.md), [version source](migrate-version-source/README.md), [CI cache](refresh-ci-cache/README.md), [Docker base](update-docker-base/README.md).
- Agent work: [agentic](agentic/README.md), [Beads issue](implement-bead/README.md), [PRD story](implement-prd-story/README.md), [grill task](grill/README.md).

`operations.groovy` is a shared helper map for required values, shell quoting, task composition, whole-file outputs, and guarded replacements. It is not a recipe factory.

Task recipes declare commands in generated mise tasks. Applying a kitchen plan writes those declarations; it does
not execute the commands, install tools or orchestrate agent sessions. Invoking a task separately requires mise,
the named external tools and any source files, environment or services the task consumes. Native program hooks are
configuration for their consuming programs, not a decision about Cook's deferred recipe hooks.
