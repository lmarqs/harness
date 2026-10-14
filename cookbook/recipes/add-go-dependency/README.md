# add-go-dependency

`add-go-dependency` adds an explicit `require` line for one module to an existing `go.mod` and declares a
`dependency:go` mise task that tidies the module and runs its tests.

## Usage

```groovy
book.recipe('add-go-dependency', kitchen, [
    module: 'example.org/library', version: 'v1.2.3',
    manifest: 'go.mod', adopt: true
]).mount()
```

## Arguments

| Argument | Required | Default | Meaning |
| --- | --- | --- | --- |
| `module` | yes | none | Module path of letters, digits, `_`, `.`, `/` or `-`. |
| `version` | yes | none | Module version: `v`, a digit, then letters, digits, `_`, `.`, `+` or `-`, for example `v1.2.3`. |
| `manifest` | yes | none | Path of the existing `go.mod`. |
| `id` | no | `add-go-dependency` | Identity of the recipe component. |
| `mise` | no | a new `mise` ingredient | Existing mise ingredient component that receives the task. Pass one when the kitchen already holds a component named `mise`. |
| `adopt` | no | `false` | Boolean. Only `true` lets the recipe take over the existing unmanaged `go.mod`, so the first application needs it. When the recipe creates the mise ingredient, it also takes over an existing unmanaged task key. |

## Output

| Target | Format | Part the recipe owns |
| --- | --- | --- |
| `<manifest>` | Text | The whole file: its current content, plus `require <module> <version>` when the module has no declaration. |
| `mise.toml`, or the file of the supplied `mise` | TOML | The `tasks."dependency:go"` key: a task that runs `cd '<directory>' && go mod tidy && go test ./...`. |

`<directory>` is the manifest's parent directory, or `.` for a manifest at the repository root.

## Inputs

The `dependency:go` task needs mise, Go and the module that `manifest` describes, plus network access or cached
modules.

## Behaviour

- A missing `manifest` file, or a `module` or `version` that does not match its pattern, fails the recipe.
- A line that, trimmed, starts with `<module> ` or `require <module> ` is the module's declaration. A declaration that
  reads exactly `<module> <version>` or `require <module> <version>` leaves the content unchanged. Any other
  declaration, including one with a trailing comment such as `// indirect`, fails the recipe.
- Without a declaration, the recipe appends `require <module> <version>` as the last line, after adding a final newline
  when the file lacks one.
- The recipe writes the task declaration. It does not run the task, fetch modules or run tests.

## Example

With the usage above and this `go.mod`:

```text
module example.org/service

go 1.24
```

the recipe writes:

```text
module example.org/service

go 1.24
require example.org/library v1.2.3
```
