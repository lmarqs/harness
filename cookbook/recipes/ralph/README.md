# ralph

Sets up Ralph TUI as the loop runner, with the JSON or Beads tracker, as a standalone runner or as mise tasks.

```sh
./cook dev.thecook:ralph --tracker=beads --mode=mise --issue-prefix=cook
```

| Target | Part |
| --- | --- |
| `.ralph-tui/config.toml` | Each top-level key |
| `.ralph-tui/run` | The whole executable runner, in `standalone` mode |
| `mise.toml` | `tools.bun`, `tools."npm:ralph-tui"`, in `mise` mode |
| `.mise/tasks/ralph/run` | The runner, in `mise` mode |
| `.beads/config.yaml` | `json` and `issue-prefix`, for the `beads` tracker |
| `.mise/tasks/beads/ready` | The ready-task script, for the `beads` tracker |
| `.gitignore` | The section `ignore`: Ralph's and Beads' local files |
