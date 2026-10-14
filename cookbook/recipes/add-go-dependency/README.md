# Add a Go dependency

This guide describes the current implementation. The [architecture](../../../docs/architecture.md) defines the
intended design; the [factory guide](../README.md) explains current CLI limits, source prerequisites and ownership
reconciliation. Existing composition APIs and adoption gates do not settle the deferred model or hooks.

Declare an explicit module requirement in an existing `go.mod` and a `dependency:go` task.

```groovy
book.recipe('add-go-dependency', kitchen, [
    module: 'example.org/library', version: 'v1.2.3',
    manifest: 'go.mod', adopt: true
]).mount()
```

Required options are `module`, `version`, and `manifest`. The module and version must be valid literal tokens; the version begins with `v` and a digit. An existing declaration with a different version is rejected. The whole manifest is managed, so an existing unmanaged file requires boolean `adopt: true` through Groovy
composition; CLI `--adopt=true` is a string and cannot satisfy this whole-file adoption check. Existing managed
edits still fail reconciliation.

Optional options are `id` (default `add-go-dependency`), `mise`, and `adopt`. The task changes to the manifest directory, runs `go mod tidy`, then `go test ./...` when invoked separately. Cooking does not fetch modules or run tests. The generated task
requires mise, Go and a valid Go project, with network access or cached modules as needed.

See [factory usage](../README.md).
