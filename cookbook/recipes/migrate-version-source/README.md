# migrate-version-source

`migrate-version-source` replaces selected version files with caller-supplied content after checking their current
content, and declares the runtime version in the mise `tools` table.

## Usage

```groovy
book.recipe('migrate-version-source', kitchen, [
    adopt: true, runtime: 'node', version: '22',
    files: [[path: '.nvmrc', expected: '20\n', content: '22\n']]
]).mount()
```

## Arguments

| Argument | Required | Default | Meaning |
| --- | --- | --- | --- |
| `adopt` | yes | none | Boolean; must be `true`. |
| `files` | yes | none | Nonempty list of maps with `path`, `expected` and `content`, where `expected` and `content` are strings. |
| `runtime` | yes | none | Tool name in the mise `tools` table, for example `node`. |
| `version` | yes | none | Version declared for `runtime`. |
| `id` | no | `migrate-version-source` | Identity of the recipe component. |
| `mise` | no | a new `mise` ingredient with adoption enabled | Existing mise ingredient component that receives the tool version. Pass one when the kitchen already holds a component named `mise`. |

## Output

| Target | Format | Part the recipe owns |
| --- | --- | --- |
| each `files` path | Text | The whole file, set to `content`. |
| `mise.toml`, or the file of the supplied `mise` | TOML | The `tools.<runtime>` key, set to `<version>`. |

## Inputs

The recipe reads each `files` path, which must exist.

## Behaviour

- A `files` entry whose current content equals neither `expected` nor `content` fails the recipe, as do an `adopt`
  other than `true`, an empty `files` list, and an entry without `path` or with a non-string `expected` or `content`.
- The recipe takes over the selected files and, when it creates the mise ingredient, an existing unmanaged
  `tools.<runtime>` value.
- The recipe does not delete the selected files, install the runtime or run mise.
