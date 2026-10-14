# Refresh CI cache configuration

This guide describes the current implementation. The [architecture](../../../docs/architecture.md) defines the
intended design; the [factory guide](../README.md) explains current CLI limits, source prerequisites and ownership
reconciliation. Existing composition APIs and adoption gates do not settle the deferred model or hooks.

Apply explicit, guarded whole-file replacements.

```groovy
book.recipe('refresh-ci-cache', kitchen, [
    adopt: true,
    files: [[path: '.github/workflows/build.yaml',
        expected: oldWorkflow, content: newWorkflow]]
]).mount()
```

Define `oldWorkflow` and `newWorkflow` as exact UTF-8 workflow contents before the example; the workflow must
already exist. These are caller-supplied values. Use Groovy composition: CLI strings do not supply a typed `files`
list or boolean adoption.

Required options are the boolean `adopt: true` and a nonempty `files` list. Each entry needs `path`, `expected`, and `content` strings. The source file must exist and equal either the expected source or the desired content. Optional `id` defaults to `refresh-ci-cache`.

The caller supplies the exact cache changes; the recipe does not infer cache keys or actions, run CI or refresh a
remote cache. Managed edits can still fail reconciliation even when a file passes the explicit source/content guard.

See [factory usage](../README.md).
