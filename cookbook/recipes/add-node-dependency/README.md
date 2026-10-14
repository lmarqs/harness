# add-node-dependency

`add-node-dependency` declares one package in a JSON package manifest and a `dependency:node` mise task that installs
the manifest's dependencies with npm, pnpm or yarn.

## Usage

```groovy
book.recipe('add-node-dependency', kitchen, [
    package: 'example-library', version: '1.2.3',
    manager: 'npm', manifest: 'package.json'
]).mount()
```

## Arguments

| Argument | Required | Default | Meaning |
| --- | --- | --- | --- |
| `package` | yes | none | Package name: an optional `@scope/` prefix, then letters, digits, `_`, `.` or `-`. |
| `version` | yes | none | Version specification, written as given, for example `^1.2.0`. The recipe does not validate it. |
| `manager` | yes | none | Package manager the task runs: `npm`, `pnpm` or `yarn`. |
| `manifest` | yes | none | Path of the JSON manifest, for example `package.json`. |
| `kind` | no | `dependencies` | Manifest section: `dependencies`, `devDependencies`, `optionalDependencies` or `peerDependencies`. |
| `id` | no | `add-node-dependency` | Identity of the recipe component. |
| `mise` | no | a new `mise` ingredient | Existing mise ingredient component that receives the task. Pass one when the kitchen already holds a component named `mise`. |
| `adopt` | no | `false` | Boolean. `true` takes over an existing unmanaged `<kind>.<package>` key and, when the recipe creates the mise ingredient, an existing unmanaged task key. |

## Output

| Target | Format | Part the recipe owns |
| --- | --- | --- |
| `<manifest>` | JSON | The `<kind>.<package>` key, set to `<version>`. |
| `mise.toml`, or the file of the supplied `mise` | TOML | The `tasks."dependency:node"` key: a task that runs `cd '<directory>' && <manager> install`. |

`<directory>` is the manifest's parent directory, or `.` for a manifest at the repository root.

## Inputs

The `dependency:node` task needs mise, Node, the selected package manager and the manifest, plus registry access or
cached packages.

## Behaviour

- The recipe writes the manifest key and the task declaration. It does not run the task, install packages or install
  the package manager.
- Other keys of the manifest stay unchanged.
- A `manager`, `kind` or `package` outside the accepted values fails the recipe.
