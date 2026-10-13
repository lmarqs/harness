# Agentic compositions

- [ralph](ralph/README.md) composes the legacy RalphTUI supplier with Beads and mise when selected.
- [runtime](runtime/README.md) adapts the typed AgenticRuntime recipe to a kitchen.

Both are factory entry points invoked with `book.recipe('agentic/<name>', kitchen, options)`; see [factory usage](../README.md). They generate configuration and runner files. Starting Ralph is a separate action.
