# Context recipe

`agentic/context` seeds a minimal `AGENTS.md` only when absent. An existing file, including an empty one, stays untouched. Codex reads it directly. Selecting Claude adds a managed `@AGENTS.md` import block to `CLAUDE.md` while retaining other text.

The commands below use this checkout's `./cook` wrapper and target this checkout. Use `--repo PATH` for another destination or run a packaged launcher from that repository. The direct file path works alongside the packaged name:

```sh
./cook agentic/context --help
./cook cookbook/recipes/agentic/context.groovy --help
./cook agentic/context --harness=codex --harness=claude --dry-run
./cook agentic/context --harness=codex --harness=claude
./cook cookbook/recipes/agentic/context.groovy --harness=claude
```

`--harness CHOICE` is required and repeatable (`codex` or `claude`). `--adopt=true` takes over a conflicting unmanaged Claude import block; its default is `false`. `--repo PATH` selects another repository, and `--dry-run` previews the change. An edited managed block causes a drift error. Rerunning the same selection updates its applications without replacing unrelated instructions. `.cook/state.json` records managed ownership for reconciliation, not a complete setup for users to edit.

Remove a selected harness's context application explicitly:

```sh
./cook agentic/context --harness=claude --remove --dry-run
./cook agentic/context --harness=claude --remove
./cook cookbook/recipes/agentic/context.groovy --harness=codex --remove
```

Removal retains the seeded `AGENTS.md` and unrelated `CLAUDE.md` text. Codex selection has no separate native context output, but its application identity can still be withdrawn.

Check this module independently:

```sh
./gradlew -p cookbook/recipes/agentic/context check
```

Context declares the Codex and Claude renderer modules as dependencies; its build also includes core and the agent-policy ingredient. See [independent module checks](../README.md#build-and-verification).
