# claude-settings

Writes chosen Claude settings and instructions from files and arguments you supply.

```sh
./cook dev.thecook:claude-settings --settings=setup/claude.json --instructions='Run tests before committing.'
```

| Target | Part |
| --- | --- |
| `.claude/settings.json` | Each top-level key of the settings file, owned whole |
| `CLAUDE.md` | The section `instructions` |
