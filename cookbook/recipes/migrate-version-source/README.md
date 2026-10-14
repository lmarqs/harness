# Migrate a version source

This guide describes the current implementation. The [architecture](../../../docs/architecture.md) defines the
intended design; the [factory guide](../README.md) explains current CLI limits, source prerequisites and ownership
reconciliation. Existing composition APIs and adoption gates do not settle the deferred model or hooks.

Apply guarded file replacements and declare a runtime version in mise.

```groovy
book.recipe('migrate-version-source', kitchen, [
    adopt: true, runtime: 'node', version: '22',
    files: [[path: '.nvmrc', expected: oldText, content: newText]]
]).mount()
```

Define `oldText` and `newText` as the exact source and desired UTF-8 file contents before the example; the selected
file must already exist. These are caller-supplied values. This map requires Groovy composition, since CLI strings
cannot supply a typed list or boolean adoption.

Required options are the boolean `adopt: true`, `runtime`, `version`, and a nonempty `files` list. Each file requires `path`, `expected`, and `content` strings. Every file must exist and contain either the expected source or the desired content; otherwise the recipe fails.

Optional `id` defaults to `migrate-version-source`. Optional `mise` supplies an existing component; otherwise mise is created with adoption enabled. Whole files are replaced, and the runtime version is added to mise tools. The recipe does not delete the selected files, install the runtime or run mise. Managed edits can still fail
reconciliation even when a file passes the explicit expected/content guard. Using the configured runtime later
requires mise and a separate tool installation.

See [factory usage](../README.md).
