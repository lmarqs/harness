# Refresh CI cache configuration

Apply explicit, guarded whole-file replacements.

```groovy
book.recipe('refresh-ci-cache', kitchen, [
    adopt: true,
    files: [[path: '.github/workflows/build.yaml',
        expected: oldWorkflow, content: newWorkflow]]
]).mount()
```

Required options are the boolean `adopt: true` and a nonempty `files` list. Each entry needs `path`, `expected`, and `content` strings. The source file must exist and equal either the expected source or the desired content. Optional `id` defaults to `refresh-ci-cache`.

The caller supplies the exact cache changes; the recipe does not infer cache keys or actions.

See [factory usage](../README.md).
