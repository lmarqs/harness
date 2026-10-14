# implement-bead

Declares a mise task that shows one Beads issue and then runs a command that works on it. Apply it after
[ralph](../ralph/README.md) with the Beads tracker in mise mode:

```sh
./cook dev.thecook:ralph --tracker=beads --mode=mise \
       dev.thecook:implement-bead --issue=cook-42 --run='mise run ralph:run cook-42'
```

| Target | Part |
| --- | --- |
| `mise.toml` | `tasks."ralph:bead"` |
