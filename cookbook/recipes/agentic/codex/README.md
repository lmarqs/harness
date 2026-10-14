# Codex setup

This guide describes the current implementation. The [architecture](../../../../docs/architecture.md) defines the
intended design; the [agentic guide](../README.md) explains current CLI limits, launcher prerequisites and differences
from the target. Existing composition APIs and adoption behavior do not settle the deferred model or hooks.

In a Groovy composition with `book` and `kitchen` initialized and the required cookbook artifacts or sources available (see [factory usage](../../README.md)), compose native Codex configuration, hooks, rules and instructions:

```groovy
def codex = book.recipe('agentic/codex', kitchen, [
    config: [model: 'selected-model'],
    instructions: 'Use project tasks.',
    rules: ['project.rules': 'prefix_rule(pattern = ["mise", "run", "check"], decision = "allow")\n']
])
codex.mount()
```

| Option | Default | Purpose |
| --- | --- | --- |
| `id` | `codex` | Stable contribution identity |
| `config` | empty | Top-level TOML keys; map or collection of maps |
| `hooks` | empty | Top-level JSON keys; map or collection of maps |
| `instructions` | absent | Managed instruction block |
| `rules` | empty | Map of simple filenames to whole rule file content |
| `configPath` | `.codex/config.toml` | Configuration file |
| `hooksPath` | `.codex/hooks.json` | Hooks file |
| `instructionsPath` | `AGENTS.md` | Instructions file |
| `adopt` | `false` | Adopt existing selected content |

The returned recipe exposes `config` and `hooks` inputs for deferred connections, and a `nativePaths` export.

`adopt` defaults to `false` and applies to selected configuration/hooks keys and whole rule files, not the instruction block. Recooking preserves unrelated content; withdrawal restores saved adopted values when managed content has not drifted. The example adds a managed block to `AGENTS.md`; use [context](../context/README.md) instead when you want to seed an editable file.

The recipe declares files; it does not launch the agent. Mount it alongside the other recipes required by the repository before planning. The factory belongs to the `agentic.permissions/codex` application, shared with [permission](../permission/README.md). Updating that application replaces its declared contribution set, so do not treat these two entry points as independent owners. Omitted contributions in that application can be withdrawn; this is current reconciliation behavior, not the target ordered-operation contract. This factory has no removal declaration for CLI `--remove`; withdrawal here means changing the composition before planning.

`CodexFiles` is the compiled renderer used by this recipe. A typed repository recipe can include `new CodexFiles(policy, adopt)` in its batch. This constructor translates an agent policy into sandbox settings and `.codex/rules/cookbook.rules`, retaining the historical `cookbook.codex` ownership ID. Its third argument overrides the ID.

The module builds independently with `./gradlew -p cookbook/recipes/agentic/codex build` and defines publication coordinates `dev.thecook.recipes:agentic-codex`, with its version in `gradle.properties`.
