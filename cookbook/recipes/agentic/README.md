# Agentic compositions

These guides describe the current cookbook implementation. The [architecture](../../../docs/architecture.md)
defines the intended design; existing factories, typed APIs and ownership behavior do not decide the deferred
ingredient/recipe model or hooks.

The current CLI cooks one named recipe or local Groovy source per invocation. `--dry-run` reports changed paths,
not a terminal content diff; ordinary cooking writes without an approval prompt. `--remove` uses saved ownership.
The current `apply` command applies a saved plan, not recipe operands. The target instead requires ordered
operands, stateless apply/discard, run-ID reversal and whole-batch diff approval. Consumed-source snapshots,
per-run reversible patches and pinned wrapper downloads are also requirements, not current capabilities.
The target eliminates `--adopt`, adoption aliases and underlying adoption/drift gates; existing or edited content
must use the actual batch diff and whole-batch approval. The adoption options below describe code still awaiting
that change.

## Before running recipes

From the checkout root, use a Java 17 JDK and build the local CLI before invoking the development launcher:

```sh
./gradlew cliBuild
./cook --help
./cook agentic/permission --help
```

These help commands need no policy or personal skill source. The checkout's `./cook` script changes into this
checkout and runs the prebuilt JVM distribution; it does not pin, download, verify or cache a release.
Use `--repo PATH` for another destination, or run the built distribution's launcher by absolute path from that
directory. See the [root entry guide](../../../README.md#start-from-a-fresh-clone) and
[local packaging instructions](../../../README.md#install).

Permission needs an existing valid policy source; context needs none, and init can seed an absent starter policy.
These three recipes do not require personal skill files. The write examples assume absent or already managed
selected output. This checkout includes tracked agent output without ownership in a fresh clone, so ordinary
write examples can fail on unmanaged content. For this checkout's current adoption prerequisites, see
[the repository setup guide](../../../docs/features/agentic-dogfood.md); init itself has no adoption input.
A dry run performs reconciliation and can report the same conflicts as a write invocation.

## Recipes

- [codex](codex/README.md) composes native Codex configuration, hooks, rules and instructions.
- [claude](claude/README.md) composes native Claude settings and instructions.
- [ralph](ralph/README.md) composes the legacy RalphTUI supplier with Beads and mise when selected.
- [runtime](runtime/README.md) adapts the typed AgenticRuntime recipe to a kitchen.
- [permission](permission/README.md), [context](context/README.md), and [init](init/README.md) have independent builds and fixture checks. Their flat Groovy entrypoints are the packaged definitions.

Run the [permission](permission/README.md), [context](context/README.md), and [init](init/README.md) recipes independently by name or by their flat `.groovy` paths. Each README gives current help, cooking, update, and removal commands. No `cook.groovy` or replay script is required for this flow. `.cook/state.json` tracks output ownership for reconciliation; it is not a user-authored inventory of everything the repository should contain.

Removal requires at least one `--harness` for these recipes. Permission and context remove only their declared applications for the selected harnesses; init selects only its declared permission and context children. Removal reads the recipe declaration and stored ownership without reading the policy source. An older factory with no removal declaration cannot be removed through `--remove`; declare its exact application scope before using removal.

These are also recipe entry points invoked with `book.recipe('agentic/<name>', kitchen, options)`; see [factory usage](../README.md). Codex, Claude and Ralph generate configuration or runner files. Permission configures harness permissions, context seeds `AGENTS.md` and connects Claude to it, and init bootstraps selected harnesses with both. Starting Ralph is a separate action.

## Build and verification

For recipe development, these existing module checks can be run from the repository root:

```sh
./gradlew -p cookbook/recipes/agentic/permission check
./gradlew -p cookbook/recipes/agentic/context check
./gradlew -p cookbook/recipes/agentic/init check
```

Permission and context declare the Codex and Claude renderer modules as dependencies. Init declares permission and context. Their composite builds include core and the agent-policy ingredient. The native launcher has separate packaging tests. These build and test entry points describe development tooling, separate from CLI cooking.
