# Migrate a version source

Apply guarded file replacements and declare a runtime version in mise.

```groovy
book.recipe('migrate-version-source', kitchen, [
    adopt: true, runtime: 'node', version: '22',
    files: [[path: '.nvmrc', expected: oldText, content: newText]]
]).mount()
```

Required options are the boolean `adopt: true`, `runtime`, `version`, and a nonempty `files` list. Each file requires `path`, `expected`, and `content` strings. Every file must exist and contain either the expected source or the desired content; otherwise the recipe fails.

Optional `id` defaults to `migrate-version-source`. Optional `mise` supplies an existing component; otherwise mise is created with adoption enabled. Whole files are replaced, and the runtime version is added to mise tools. The recipe does not delete the selected files.

See [factory usage](../README.md).
