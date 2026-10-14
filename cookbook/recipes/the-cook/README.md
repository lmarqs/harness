# TheCook repository setup

This guide describes the current implementation. The [architecture](../../../docs/architecture.md) defines the
intended design; the [factory guide](../README.md) explains current CLI limits, source prerequisites and ownership
reconciliation. Existing composition APIs and adoption gates do not settle the deferred model or hooks.

Composes TheCook and Git to ignore local ownership state while preserving existing `.gitignore` entries.

```sh
./cook the-cook --dry-run
./cook the-cook
```

This factory ignores its option map; it declares no configurable recipe inputs or CLI removal scope.
The commands require a built local CLI and absent or already managed `git/ignore` content. Existing handwritten
entries outside that block are preserved; unmanaged matching content or edits inside a managed block can reject
reconciliation, including a dry run. The recipe declares an ignore entry for `.cook/`; it does not create the target
per-run history or pinned wrapper. Current cooking maintains local ownership records separately. It can also be included through `book.recipe('the-cook', kitchen)`.
Use [cookbook](../cookbook/README.md) for this project's agent policy and shared skills.
