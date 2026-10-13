# Agentic compositions

- [codex](codex/README.md) composes native Codex configuration, hooks, rules and instructions.
- [claude](claude/README.md) composes native Claude settings and instructions.
- [ralph](ralph/README.md) composes the legacy RalphTUI supplier with Beads and mise when selected.
- [runtime](runtime/README.md) adapts the typed AgenticRuntime recipe to a kitchen.
- [permission](permission/README.md), [context](context/README.md), and [init](init/README.md) have independent builds and fixture checks. Their flat Groovy entrypoints are the packaged definitions.

Run the [permission](permission/README.md), [context](context/README.md), and [init](init/README.md) recipes independently by name or by their flat `.groovy` paths. Each README gives exact help, apply, update, and removal commands. No `cook.groovy` or replay script is required for this flow. `.cook/state.json` tracks output ownership for reconciliation; it is not a user-authored inventory of everything the repository should contain.

These are also recipe entry points invoked with `book.recipe('agentic/<name>', kitchen, options)`; see [factory usage](../README.md). Codex, Claude and Ralph generate configuration or runner files. Permission configures harness permissions, context seeds `AGENTS.md` and connects Claude to it, and init bootstraps selected harnesses with both. Starting Ralph is a separate action.

## Build and verification

Each recipe has a separate module check from the repository root:

```sh
./gradlew -p cookbook/recipes/agentic/permission check
./gradlew -p cookbook/recipes/agentic/context check
./gradlew -p cookbook/recipes/agentic/init check
```

Permission and context declare the Codex and Claude renderer modules as dependencies. Init declares permission and context. Their composite builds include core and the agent-policy ingredient. Core and CLI changes have their own `./gradlew -p core check` and `./gradlew -p cli check` gates. Final integration runs `./gradlew check cliBuild cookbookBuild jpackageTest`; the package test exercises the native launcher separately from recipe module checks.
