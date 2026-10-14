# codex-settings

Writes chosen Codex configuration, hooks, rule files and instructions from files and arguments you supply.

```sh
./cook dev.thecook:codex-settings --config=setup/codex.toml --rules=setup/codex-rules
```

| Target | Part |
| --- | --- |
| `.codex/config.toml` | Each top-level key of the config file, owned whole |
| `.codex/hooks.json` | Each top-level key of the hooks file, owned whole |
| `.codex/rules/<name>` | One whole file per file in the rules directory |
| `AGENTS.md` | The section `codex` |
