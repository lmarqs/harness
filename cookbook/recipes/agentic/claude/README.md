# Claude setup

This guide describes the current implementation. The [architecture](../../../../docs/architecture.md) defines the
intended design; the [agentic guide](../README.md) explains current CLI limits, launcher prerequisites and differences
from the target. Existing composition APIs and adoption behavior do not settle the deferred model or hooks.

In a Groovy composition with `book` and `kitchen` initialized and the required cookbook artifacts or sources available (see [factory usage](../../README.md)), compose native Claude settings and instructions:

```groovy
def claude = book.recipe('agentic/claude', kitchen, [
    settings: [permissions: [allow: ['Bash(mise run check *)']]],
    instructions: '@AGENTS.md'
])
claude.mount()
```

| Option | Default | Purpose |
| --- | --- | --- |
| `id` | `claude` | Stable contribution identity |
| `settings` | empty | Top-level JSON keys; map or collection of maps |
| `instructions` | absent | Managed instruction block |
| `settingsPath` | `.claude/settings.json` | Settings file |
| `instructionsPath` | `CLAUDE.md` | Instructions file |
| `adopt` | `false` | Adopt existing selected keys |

The returned recipe exposes a `settings` input for deferred connections, plus `nativePaths` and `files.local` exports. The latter describes `.claude/settings.local.json`; the recipe does not create that personal file. A containing recipe can connect these local paths to Git's ignore contributions.

`adopt` defaults to `false` and applies to selected settings keys. This factory has no rule-file output, and the option does not adopt its instruction block. Recooking preserves unrelated content; withdrawal restores saved adopted settings values when managed content has not drifted. The example references `AGENTS.md` but does not create it; supply that file or compose [context](../context/README.md) instead for seeded shared guidance.

The recipe declares files; it does not launch the agent. Mount it alongside the other recipes required by the repository before planning. The factory belongs to the `agentic.permissions/claude` application, shared with [permission](../permission/README.md). Updating that application replaces its declared contribution set, so do not treat these two entry points as independent owners. Omitted contributions in that application can be withdrawn; this is current reconciliation behavior, not the target ordered-operation contract. This factory has no removal declaration for CLI `--remove`; withdrawal here means changing the composition before planning.

`ClaudeFiles` is the compiled renderer used by this recipe. A typed repository recipe can include `new ClaudeFiles(policy, adopt)` in its batch. This constructor translates an agent policy into individual permission keys and a `CLAUDE.md` reference to `AGENTS.md`, retaining the historical `cookbook.claude` ownership ID. Its third argument overrides the ID.

The module builds independently with `./gradlew -p cookbook/recipes/agentic/claude build` and defines publication coordinates `dev.thecook.recipes:agentic-claude`, with its version in `gradle.properties`.
