# Grill task

This guide describes the current implementation. The [architecture](../../../docs/architecture.md) defines the
intended design; the [factory guide](../README.md) explains current CLI limits, source prerequisites and ownership
reconciliation. Existing composition APIs and adoption gates do not settle the deferred model or hooks.

Declare a mise task named `grill`.

```groovy
book.recipe('grill', kitchen, [script: 'git status']).mount()
```

`script` is required and becomes the task command. Optional options are `id` (default `grill`), `mise` (an existing mise component), and `adopt` (passed to mise). Applying the recipe does not run the command. Invoke the generated task separately with mise
and the tools used by `script` installed. Existing unmanaged task content requires the mise adoption option;
managed edits still fail reconciliation.

See [factory usage](../README.md).
