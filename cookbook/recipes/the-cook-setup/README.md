# the-cook-setup

This repository's own agent setup: the shared policy rendered for Claude and Codex, three skills, the working
instructions and the ignore entries.

```sh
./cook dev.thecook:the-cook-setup
```

| Target | Part |
| --- | --- |
| `.agents/policy.yaml` | Seeded when absent; never owned |
| `AGENTS.md` | Seeded when absent; the section `instructions` |
| `.agents/skills/<name>/**` | `solid`, `hexagonal-layout`, `effective-java-core` |
| `.claude/settings.json` | `permissions` |
| `CLAUDE.md` | The section `instructions`: `@AGENTS.md` |
| `.claude/skills/<name>` | One relative link per skill |
| `.codex/config.toml` | `sandbox_mode`, `approval_policy`, `sandbox_workspace_write` |
| `.codex/rules/cook.rules` | The section `commands` |
| `.gitignore` | The section `ignore`: `/.cook/` and `/.claude/settings.local.json` |
