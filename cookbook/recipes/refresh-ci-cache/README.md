# refresh-ci-cache

`refresh-ci-cache` applies caller-supplied CI cache changes as whole-file replacements, after checking each file's
current content.

## Usage

`currentWorkflow` and `updatedWorkflow` hold the file's exact current and desired content:

```groovy
book.recipe('refresh-ci-cache', kitchen, [
    adopt: true,
    files: [[path: '.github/workflows/build.yaml',
        expected: currentWorkflow, content: updatedWorkflow]]
]).mount()
```

## Arguments

| Argument | Required | Default | Meaning |
| --- | --- | --- | --- |
| `adopt` | yes | none | Boolean; must be `true`. |
| `files` | yes | none | Nonempty list of maps with `path`, `expected` and `content`, where `expected` and `content` are strings. |
| `id` | no | `refresh-ci-cache` | Identity of the recipe component. |

## Output

| Target | Format | Part the recipe owns |
| --- | --- | --- |
| each `files` path | Text | The whole file, set to `content`. |

## Inputs

The recipe reads each `files` path, which must exist.

## Behaviour

- A `files` entry whose current content equals neither `expected` nor `content` fails the recipe, as do an `adopt`
  other than `true`, an empty `files` list, and an entry without `path` or with a non-string `expected` or `content`.
- The recipe takes over the selected files.
- The recipe writes the supplied content only. It does not run CI or touch a remote cache.
