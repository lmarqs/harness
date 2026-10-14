# grill

Declares a mise task named `grill` that runs the command given in `script`.

## Usage

```groovy
book.recipe('grill', kitchen, [script: 'git status']).mount()
```

## Arguments

| Argument | Required | Default | Meaning |
| --- | --- | --- | --- |
| `script` | yes | none | Command the `grill` task runs. An empty value is rejected. |
| `id` | no | `grill` | Component ID of the recipe. |
| `mise` | no | a new `mise` ingredient | Existing mise component that receives the task. |
| `adopt` | no | `false` | Passed to the mise component the recipe creates; boolean `true` or string `'true'` lets it adopt an existing unmanaged `grill` task. Ignored when `mise` is supplied. |

## Output

| Target | Format | Part the recipe owns |
| --- | --- | --- |
| `mise.toml`, or the file of the supplied `mise` component | TOML | The `tasks.grill` key: `{ run = "<script>" }`. |

## Inputs

Running the task needs mise and the tools that `script` calls.

## Behaviour

The recipe writes the task declaration only; it does not run the command.
