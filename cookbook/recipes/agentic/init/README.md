# Init recipe

`agentic/init` composes permissions and shared context for selected harnesses. It seeds `AGENTS.md` when absent and connects Claude with a managed `@AGENTS.md` block. The commands below use this checkout's `./cook` wrapper and target this checkout. Use `--repo PATH` for another destination or run a packaged launcher from that repository:

```sh
./cook agentic/init --help
./cook cookbook/recipes/agentic/init.groovy --help
./cook agentic/init --harness=claude --harness=codex --dry-run
./cook agentic/init --harness=claude --harness=codex
./cook cookbook/recipes/agentic/init.groovy --harness=codex
```

`--harness CHOICE` is required and repeatable (`codex` or `claude`). `--repo PATH` chooses another destination, and `--dry-run` previews writes. Init has no `--policy` or `--adopt` input. For each selected harness without an existing permission application, it reads `.agents/policy.yaml`. If absent, it seeds a version 1 policy with no instructions or command grants. An existing starter policy must be valid. An existing selected permission application keeps its chosen policy and native output; init does not replace it.

After init, apply different policies with two independent commands, as shown in the [permission recipe](../permission/README.md). Repeating init or a permission command reconciles only its selected applications. `.cook/state.json` records ownership and supports reconciliation; users do not write it as a complete desired setup. This flow requires neither `cook.groovy` nor a replay script.

Remove one selected harness's init, permission, and context applications explicitly:

```sh
./cook agentic/init --harness=claude --remove --dry-run
./cook agentic/init --harness=claude --remove
./cook cookbook/recipes/agentic/init.groovy --harness=codex --remove
```

The starter policy and `AGENTS.md` remain repository owned. Other harness applications remain installed; unrelated native settings are preserved. Managed drift rejects removal before writes.

Check init independently:

```sh
./gradlew -p cookbook/recipes/agentic/init check
```

Init declares the permission and context recipe modules as dependencies. Its build also includes their renderer dependencies, core, and the agent-policy ingredient. See [independent module checks](../README.md#build-and-verification).
