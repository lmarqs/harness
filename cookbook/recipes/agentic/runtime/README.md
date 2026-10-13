# Typed AgenticRuntime composition

`AgenticRuntime.groovy` implements `RecipeDefinition`. Its `compose()` returns a `Batch` containing Git and, when enabled, the typed Ralph ingredient. Git ignores Ralph's local and ephemeral files even while Ralph is disabled.

The factory adapts the batch into the supplied kitchen and mounts it:

```groovy
book.recipe('agentic/runtime', kitchen, [
    agent: 'codex', tracker: 'json', mode: 'standalone', enabled: true
])
```

Optional options are `agent` (`codex` or `claude`), `tracker` (`json`, `beads`, or `beads-bv`), `mode` (`standalone` or `mise`), `prdPath` (default `prd.json`), `workers` (1–32, default 1), `maxIterations` (0–1000, default 10), `adopt` and `autoCommit` (default false). The factory converts the last two values from strings to booleans. `enabled` disables Ralph only when its string representation is `false`.

The typed API is also available directly:

```groovy
import thecook.cookbook.ingredients.ralphtui.RalphConfig
import thecook.cookbook.recipes.AgenticRuntime

def batch = new AgenticRuntime(new RalphConfig(), true).compose()
```

This recipe does not compose separate Beads or mise suppliers. Use [agentic/ralph](../ralph/README.md) for those legacy compositions. See [factory usage](../../README.md).
