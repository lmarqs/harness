# Development composition

Compose mise, Node, the selected package manager, and Git.

```groovy
book.recipe('development', kitchen, [
    manager: 'npm', nodeVersion: '22', adopt: true
]).mount()
```

All options are optional. `manager` is `npm` (default) or `yarn`; `nodeVersion` defaults to `22`. `npmVersion` or `yarnVersion` configures the selected manager. `adopt` defaults to false and is converted from its string representation to a boolean.

The recipe wires package fields, mise tools and tasks, and Git ignores for ephemeral supplier files. Its component ID is `development`.

See [factory usage](../README.md).
