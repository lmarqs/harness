# Init recipe

`agentic/init` composes the permission and context recipe identities. Those recipes currently produce no outputs; later stories add their behavior.

Run its supplier compilation, fixture test, formatting, and static checks independently:

```sh
./gradlew -p cookbook/recipes/agentic/init check
```

This command builds init and its declared recipe dependencies. Full distribution verification uses the repository root `./gradlew check cookbookBuild`.
