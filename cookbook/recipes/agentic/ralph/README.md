# Ralph factory composition

Compose the legacy RalphTUI supplier, Beads when selected, and mise in mise mode.

```groovy
book.recipe('agentic/ralph', kitchen, [
    agent: 'codex', tracker: 'json', mode: 'mise', prdPath: 'prd.json'
]).mount()
```

| Option | Default and accepted values |
| --- | --- |
| `id` | `agentic-ralph`, the recipe component ID |
| `agent` | `codex`; also `claude` |
| `tracker` | `json`; also `beads`, `beads-bv` |
| `mode` | `standalone`; also `mise` |
| `workers` | `1`; integer from 1 through 32 |
| `maxIterations` | `10`; integer from 0 through 1000 |
| `prdPath` | `prd.json`; repository path without parent traversal |
| `headless`, `autoCommit`, `adopt` | `false`; boolean values or strings `true`/`false` |
| `model` | Unset; passed through to agent options |
| `agentOptions` | Empty map |
| `issuePrefix` | Passed to Beads when the tracker uses Beads |
| `mise` | Existing mise component; otherwise created in mise mode |

The resulting recipe exposes `ralph`, and exposes `beads` and `mise` when composed. It declares Ralph config and a runner at `.ralph-tui/run` in standalone mode or `.mise/tasks/ralph/run` in mise mode. Running the generated script starts Ralph; applying a kitchen plan only writes files.

See [factory usage](../../README.md).
