# Context recipe

`agentic/context` seeds a minimal `AGENTS.md` when the file is absent. Existing files, including empty files, remain untouched. Codex reads `AGENTS.md` directly. Selecting Claude adds one managed `@AGENTS.md` block to `CLAUDE.md`, preserving text outside the block.

Select one or both harnesses with repeatable `--harness=codex` and `--harness=claude` options. An existing unmanaged block with the same identity requires `--adopt=true`. A managed block edited by hand causes a drift error. Remove a selected harness with `./cook agentic/context --harness=claude --remove` or the corresponding Codex choice. Removal retains `AGENTS.md` and unrelated `CLAUDE.md` text.

Run its supplier compilation, fixture test, formatting, and static checks independently:

```sh
./gradlew -p cookbook/recipes/agentic/context check
```

This command builds this recipe and its declared dependencies. Full distribution verification uses the repository root `./gradlew check cookbookBuild`.
