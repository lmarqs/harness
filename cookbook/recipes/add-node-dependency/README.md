# Add a Node dependency

This guide describes the current implementation. The [architecture](../../../docs/architecture.md) defines the
intended design; the [factory guide](../README.md) explains current CLI limits, source prerequisites and ownership
reconciliation. Existing composition APIs and adoption gates do not settle the deferred model or hooks.

Declare one JSON package manifest key and a `dependency:node` installation task.

```groovy
book.recipe('add-node-dependency', kitchen, [
    package: 'example-library', version: '1.2.3',
    manager: 'npm', manifest: 'package.json'
]).mount()
```

Required options are `package`, `version`, `manager`, and `manifest`. Manager is `npm`, `pnpm`, or `yarn`. Optional `kind` defaults to `dependencies` and also accepts `devDependencies`, `optionalDependencies`, and `peerDependencies`. Other optional options are `id` (default `add-node-dependency`), `mise`, and `adopt` for the manifest key and mise supplier.

The task changes to the manifest directory and runs the selected manager's `install` command when invoked
separately. Cooking only writes the selected key and task; it does not install dependencies or the manager.
Running the task requires mise, Node, the selected package manager and a usable manifest, with registry access
or cached packages as needed. Existing unmanaged selected keys need the supplier's adoption option; managed
edits still fail reconciliation.

See [factory usage](../README.md).
