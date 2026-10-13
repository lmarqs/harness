# Add a Node dependency

Declare one JSON package manifest key and a `dependency:node` installation task.

```groovy
book.recipe('add-node-dependency', kitchen, [
    package: 'example-library', version: '1.2.3',
    manager: 'npm', manifest: 'package.json'
]).mount()
```

Required options are `package`, `version`, `manager`, and `manifest`. Manager is `npm`, `pnpm`, or `yarn`. Optional `kind` defaults to `dependencies` and also accepts `devDependencies`, `optionalDependencies`, and `peerDependencies`. Other optional options are `id` (default `add-node-dependency`), `mise`, and `adopt` for the manifest key and mise supplier.

The task changes to the manifest directory and runs the selected manager's `install` command.

See [factory usage](../README.md).
