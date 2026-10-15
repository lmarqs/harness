# ralph

Sets up Ralph TUI as the repository's loop runner: its configuration, a runner and the tracker it takes stories
from.

## Before applying

- For the `json` tracker, a PRD in Ralph's JSON format at `--prd`. The recipe does not create one.
- For `--mode=mise`, mise installed. Bun and Ralph TUI are pinned for it; `mise install` fetches them.
- For `--mode=standalone`, `ralph-tui` on the `PATH`.
- For the `beads` tracker, `bd` on the `PATH`.

## Apply

```sh
./cook dev.thecook:ralph
./cook dev.thecook:ralph --tracker=beads --mode=mise --issue-prefix=cook
```

## Writes

| Target | Part | When |
| --- | --- | --- |
| `.ralph-tui/config.toml` | Each top-level key | Always |
| `.ralph-tui/run` | The whole executable runner | `--mode=standalone` |
| `mise.toml` | `tools.bun`, `tools."npm:ralph-tui"` | `--mode=mise` |
| `.mise/tasks/ralph/run` | The whole executable runner | `--mode=mise` |
| `.beads/config.yaml` | `json`, `issue-prefix` | `--tracker=beads` |
| `.mise/tasks/beads/ready` | The whole executable script | `--tracker=beads` |
| `.gitignore` | The section `ignore`: Ralph's local and ephemeral files, and Beads' with `--tracker=beads` | Always |

## After applying

- `--mode=standalone`: run `.ralph-tui/run`, with a PRD path or a Beads epic id as its argument.
- `--mode=mise`: run `mise install`, then `mise run ralph:run` with the same argument. `mise run beads:ready` lists
  the ready Beads tasks.
- Ralph's worktrees, iterations, reports and session files stay out of version control.

## With other recipes

- `implement-prd-story` writes a PRD reduced to one story. Apply it before `ralph`, with `--prd` pointing at the
  same path.
- `implement-bead` adds the task `ralph:bead`, which runs Ralph on one Beads issue. It needs `--tracker=beads` and
  `--mode=mise`.
