# Add a Go dependency

Declare an explicit module requirement in an existing `go.mod` and a `dependency:go` task.

```groovy
book.recipe('add-go-dependency', kitchen, [
    module: 'example.org/library', version: 'v1.2.3',
    manifest: 'go.mod', adopt: true
]).mount()
```

Required options are `module`, `version`, and `manifest`. The module and version must be valid literal tokens; the version begins with `v` and a digit. An existing declaration with a different version is rejected. The whole manifest is managed, so an existing unmanaged file requires `adopt: true`.

Optional options are `id` (default `add-go-dependency`), `mise`, and `adopt`. The task changes to the manifest directory, runs `go mod tidy`, then `go test ./...`.

See [factory usage](../README.md).
