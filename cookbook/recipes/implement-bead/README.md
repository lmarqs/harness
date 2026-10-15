# implement-bead

Declares a mise task that shows one Beads issue and then runs Ralph on it. Apply it with
[ralph](../ralph/README.md) using the Beads tracker in mise mode, which declares the `ralph:run` task:

```sh
./cook dev.thecook:ralph --tracker=beads --mode=mise \
       dev.thecook:implement-bead --issue=cook-42
```

| Target | Part |
| --- | --- |
| `mise.toml` | `tasks."ralph:bead"` |
