# Agentic recipes

Recipes that configure coding-agent harnesses in a repository. Each one is explained in its own README.

- [init](init/README.md) bootstraps the selected harnesses with permissions and shared context.
- [permission](permission/README.md) renders one agent policy into each selected harness's native permissions.
- [context](context/README.md) seeds `AGENTS.md` and connects Claude to it.
- [codex](codex/README.md) composes native Codex configuration, hooks, rules and instructions.
- [claude](claude/README.md) composes native Claude settings and instructions.
- [ralph](ralph/README.md) composes the RalphTUI runner, with Beads and mise when selected.
- [runtime](runtime/README.md) adapts the typed agentic runtime recipe to a kitchen.

`init`, `permission` and `context` have flat entry points (`init.groovy`, `permission.groovy`, `context.groovy`) beside
their directories. `testing/` holds the packaging test that the agentic module checks share.
