# Repository recipes

These entry points return a typed `Batch` for the CLI.

- [cookbook](cookbook/README.md): shared instructions, skills, and Codex/Claude policy.
- [agent-setup](agent-setup/README.md): compatibility aliases for cookbook.
- [the-cook](the-cook/README.md): ignores local ownership state.

Run `./cook cookbook --policy=.agents/policy.yaml --dry-run` from the repository root to preview agent setup. The root `cook.groovy` delegates to cookbook with `.agents/policy.yaml` as its default policy.
