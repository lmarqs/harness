# Claude setup

Compose native Claude settings and instructions:

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

`adopt` defaults to `false`. Set it to `true` to take responsibility for existing selected keys or whole rule files. Recooking preserves unrelated content; withdrawing the recipe restores adopted values when managed content has not drifted.

The recipe declares files; it does not launch the agent. Mount it alongside the other recipes required by the repository before planning. Each plan represents the complete desired composition.

`ClaudeFiles` is the compiled renderer used by this recipe. A typed repository recipe can include `new ClaudeFiles(policy, adopt)` in its batch. This constructor translates an agent policy into individual permission keys and a `CLAUDE.md` reference to `AGENTS.md`, retaining the historical `cookbook.claude` ownership ID. Its third argument overrides the ID.

The module builds independently with `./gradlew -p cookbook/recipes/agentic/claude build` and publishes as `dev.thecook.recipes:agentic-claude`, with its version in `gradle.properties`.
