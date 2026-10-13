# Codex setup

Compose native Codex configuration, hooks, rules and instructions:

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

`adopt` defaults to `false`. Set it to `true` to take responsibility for existing selected keys or whole rule files. Recooking preserves unrelated content; withdrawing the recipe restores adopted values when managed content has not drifted.

The recipe declares files; it does not launch the agent. Mount it alongside the other recipes required by the repository before planning. Each plan represents the complete desired composition.

`CodexFiles` is the compiled renderer used by this recipe. A typed repository recipe can include `new CodexFiles(policy, adopt)` in its batch. This constructor translates an agent policy into sandbox settings and `.codex/rules/cookbook.rules`, retaining the historical `cookbook.codex` ownership ID. Its third argument overrides the ID.

The module builds independently with `./gradlew -p cookbook/recipes/agentic/codex build` and publishes as `dev.thecook.recipes:agentic-codex`, with its version in `gradle.properties`.
