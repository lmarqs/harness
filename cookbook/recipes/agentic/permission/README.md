# Permission recipe

`agentic/permission` currently declares its identity and composes no outputs. Later stories add permission behavior. The Codex and Claude native renderers remain recipe dependencies of this module.

Run its supplier compilation, fixture test, formatting, and static checks independently:

```sh
./gradlew -p cookbook/recipes/agentic/permission check
```

This command builds this recipe and its declared dependencies. Full distribution verification uses the repository root `./gradlew check cookbookBuild`.
