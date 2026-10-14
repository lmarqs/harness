# Development composition

This guide describes the current implementation. The [architecture](../../../docs/architecture.md) defines the
intended design; the [factory guide](../README.md) explains current CLI limits, source prerequisites and ownership
reconciliation. Existing composition APIs and adoption gates do not settle the deferred model or hooks.

Compose mise, Node, the selected package manager, and Git.

```groovy
book.recipe('development', kitchen, [
    manager: 'npm', nodeVersion: '22', adopt: true
]).mount()
```

All options are optional. `manager` is `npm` (default) or `yarn`; `nodeVersion` defaults to `22`. `npmVersion` or `yarnVersion` configures the selected manager. `adopt` defaults to false and is converted from its string representation to a boolean.

The recipe wires package fields, mise tools and tasks, and Git ignores for ephemeral supplier files. Its component ID is `development`. Cooking does not install Node, the manager or dependencies. Running generated
tasks separately requires mise and the tools/sources their scripts use. Existing selected keys and task files must
be managed or adopted; managed edits still fail. Optional manager versions must be exact numeric three-part versions.

See [factory usage](../README.md).
