# Agentic compositions

- [codex](codex/README.md) composes native Codex configuration, hooks, rules and instructions.
- [claude](claude/README.md) composes native Claude settings and instructions.
- [ralph](ralph/README.md) composes the legacy RalphTUI supplier with Beads and mise when selected.
- [runtime](runtime/README.md) adapts the typed AgenticRuntime recipe to a kitchen.
- [permission](permission/README.md), [context](context/README.md), and [init](init/README.md) have independent builds and fixture checks. Their flat Groovy entrypoints are the packaged definitions.

These are recipe entry points invoked with `book.recipe('agentic/<name>', kitchen, options)`; see [factory usage](../README.md). Codex, Claude and Ralph generate configuration or runner files. Permission, context and init currently establish composition identities for later behavior. Starting Ralph is a separate action.
