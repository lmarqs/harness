# Context recipe

`agentic/context` currently declares its identity and composes no outputs. Later stories add context behavior. The Codex and Claude native renderers remain recipe dependencies of this module.

Run its supplier compilation, fixture test, formatting, and static checks independently:

```sh
./gradlew -p cookbook/recipes/agentic/context check
```

This command builds this recipe and its declared dependencies. Full distribution verification uses the repository root `./gradlew check cookbookBuild`.
