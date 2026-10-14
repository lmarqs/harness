# agent-context

Seeds `AGENTS.md` as the shared instruction file and connects each selected harness to it.

```sh
./cook dev.thecook:agent-context --harness=claude
```

| Target | Part |
| --- | --- |
| `AGENTS.md` | Seeded with a title when absent; never owned |
| `CLAUDE.md` | The section `instructions`, containing `@AGENTS.md`, for `claude` |
