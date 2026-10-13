# Implement a Beads issue

Compose Ralph with the Beads tracker and mise, then declare a `ralph:bead` task.

```groovy
book.recipe('implement-bead', kitchen, [
    issue: 'example-123', runCommand: './run-selected-issue example-123'
]).mount()
```

Required options are `issue` and `runCommand`; the command must contain the issue string explicitly. The generated task first runs `bd show <issue>`, then the supplied command. The example command must be supplied by your project.

Optional `id` defaults to `implement-bead`. Remaining options are forwarded to [agentic/ralph](../agentic/ralph/README.md), with `tracker: 'beads'` and `mode: 'mise'` selected by this recipe.

See [factory usage](../README.md).
