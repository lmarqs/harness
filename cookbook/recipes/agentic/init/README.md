# Init recipe

`agentic/init` composes the permission identity and selects both harnesses for context bootstrapping. Context seeds `AGENTS.md` when absent and connects Claude through a managed import block. Permission configuration is added by a later story.

Run its supplier compilation, fixture test, formatting, and static checks independently:

```sh
./gradlew -p cookbook/recipes/agentic/init check
```

This command builds init and its declared recipe dependencies. Full distribution verification uses the repository root `./gradlew check cookbookBuild`.
