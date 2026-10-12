# Task sources, runner and workers

These are **concept examples**, not working Cook configuration. The [sample `prd.json`](prd.json) is illustrative data; it is not promised to match any installed ralph-tui version's schema.

| Piece | Role in this example | Shared/native paths to decide |
| --- | --- | --- |
| Beads (`bd`) | Task source with its own database and issue operations | `.beads/` can contain committed config and hooks as well as local data. |
| `prd.json` | Task source file supplied to one run | Its storage and commit policy are a repository choice. It is not an installed program. |
| ralph-tui | Runner that asks a source for work and starts a worker | `.ralph-tui/config.toml` names tracker and agents; logs and progress are local state. |
| Codex | Worker option | Shared `AGENTS.md` and chosen `.codex/` files differ from personal sessions and approvals. |
| Claude | Worker option | Shared `CLAUDE.md` and chosen `.claude/settings.json` differ from `.claude/settings.local.json` and sessions. |

The [Beads station](../stations/beads-tracker.yaml) can stand alone. The [ralph-beads station](../stations/ralph-beads.yaml) combines it with the runner and offers both workers; a run chooses Codex or Claude. The [ralph-prd station](../stations/ralph-prd.yaml) changes the task source. Applying both runner stations as written would collide on `.ralph-tui/config.toml`; today's station model has no safe merge rule for that. A composable design would select one default source and keep both worker options, or allow two explicitly named runner profiles.

The [implement-bead recipe](../recipes/implement-bead.yaml) and [implement-prd-story recipe](../recipes/implement-prd-story.yaml) duplicate much of the run. That duplication is evidence for a possible task-source interface, not proof that the interface is already defined. If the source interface cannot represent Beads ownership/dependencies and the JSON file's story state without losing meaning, two recipes are more honest.

The sibling repos demonstrate the variation: `../medprev-management/.ralph-tui/config.toml` uses `beads-bv` and offers both agents; `../medprev-rest-api/.agents/ralph/config.toml` declares both Beads and JSON trackers and uses different worker flags. A cookbook update cannot assume the same sandbox, permission, worktree, or completion policy across them.
