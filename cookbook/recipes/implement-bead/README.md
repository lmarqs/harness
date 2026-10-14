# Implement a Beads issue

This guide describes the current implementation. The [architecture](../../../docs/architecture.md) defines the
intended design; the [factory guide](../README.md) explains current CLI limits, source prerequisites and ownership
reconciliation. Existing composition APIs and adoption gates do not settle the deferred model or hooks.

Compose Ralph with the Beads tracker and mise, then declare a `ralph:bead` task.

```groovy
book.recipe('implement-bead', kitchen, [
    issue: 'example-123', runCommand: './run-selected-issue example-123'
]).mount()
```

Required options are `issue` and `runCommand`; the command must contain the issue string explicitly. The generated task first runs `bd show <issue>`, then the supplied command. The example command must be supplied by your project; `issue` must name an existing Beads issue in that destination.
Cooking writes configuration and tasks without implementing the issue or starting Ralph. Running the task separately
requires mise, `bd`, the supplied command and any tools that command uses. Starting the generated Ralph runner has
the additional agent/tracker prerequisites in the Ralph guide. Mise tool declarations do not install those tools.

Optional `id` defaults to `implement-bead`. Remaining options are forwarded to [agentic/ralph](../agentic/ralph/README.md), with `tracker: 'beads'` and `mode: 'mise'` selected by this recipe.

See [factory usage](../README.md).
