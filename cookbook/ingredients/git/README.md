# git

Models the files Git reads. Today that is the ignore file.

## Model

- `ignore(patterns)` adds patterns, in gitignore syntax, relative to this instance's directory.

## Writes

| Target | Part |
| --- | --- |
| `.gitignore` | The section `ignore` |

A second `.gitignore` is a second instance at another directory: `@ingredient(Git, at = 'packages/api')`.
