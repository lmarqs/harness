# Ralph factory composition

This guide describes the current implementation. The [architecture](../../../../docs/architecture.md) defines the
intended design; the [agentic guide](../README.md) explains current CLI limits, launcher prerequisites and differences
from the target. Existing composition APIs and adoption behavior do not settle the deferred model or hooks.

In a Groovy composition with `book` and `kitchen` initialized and the cookbook sources/dependencies available, compose the legacy RalphTUI supplier, Beads when selected, and mise in mise mode.

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

Starting the runner separately requires Ralph, the selected agent and tracker tools, and a PRD file or Beads epic. Writing mise tool declarations does not install those tools. This factory has no removal declaration for CLI `--remove`; withdrawal through composition uses current ownership reconciliation and its drift gates. `adopt` permits takeover of selected existing outputs and is not part of the target stateless operation contract.
