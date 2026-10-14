# Typed AgenticRuntime composition

This guide describes the current implementation. The [architecture](../../../../docs/architecture.md) defines the
intended design; the [agentic guide](../README.md) explains current CLI limits, launcher prerequisites and differences
from the target. Existing composition APIs and adoption behavior do not settle the deferred model or hooks.

`AgenticRuntime.groovy` implements `RecipeDefinition`. Its `compose()` returns a `Batch` containing Git and, when enabled, the typed Ralph ingredient. Git ignores Ralph's local and ephemeral files even while Ralph is disabled.

In a Groovy composition with `book` and `kitchen` initialized and required cookbook artifacts or sources available (see [factory usage](../../README.md)), the factory adapts the batch into an unmounted component. Mount it before planning:

```groovy
book.recipe('agentic/runtime', kitchen, [
    agent: 'codex', tracker: 'json', mode: 'standalone', enabled: true
]).mount()
```

Optional options are `agent` (`codex` or `claude`), `tracker` (`json`, `beads`, or `beads-bv`), `mode` (`standalone` or `mise`), `prdPath` (default `prd.json`), `workers` (1–32, default 1), `maxIterations` (0–1000, default 10), `adopt` and `autoCommit` (default false). The factory converts the last two values from strings to booleans. `enabled` disables Ralph only when its string representation is `false`.

With the typed classes on the Groovy classpath, the current typed API is also available directly. Composing a batch alone does not write files or start Ralph:

```groovy
import thecook.cookbook.ingredients.ralphtui.RalphConfig
import thecook.cookbook.recipes.AgenticRuntime

def batch = new AgenticRuntime(new RalphConfig(), true).compose()
```

This recipe does not compose separate Beads or mise suppliers. Use [agentic/ralph](../ralph/README.md) for those legacy compositions. See [factory usage](../../README.md).

The recipe writes Ralph configuration and a runner; Cook does not run agent sessions. Starting the generated runner separately requires Ralph and the chosen agent/tracker tools, plus the selected PRD or epic. Mise mode here does not install tools or compose a mise supplier. This factory has no removal declaration for CLI `--remove`. Existing `adopt` handling and ownership reconciliation are implementation details, not the target stateless operation contract.
