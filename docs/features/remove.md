# remove

`harness remove` takes `harness`, or one of its modules, out of a repository. It deletes exactly what the
[manifest](concepts.md#ownership-and-the-manifest) lists as tool-owned, removes managed blocks and owned keys, and
leaves everything else as it is. The repository keeps working with what remains.

## When to use it

| Situation | Command |
| --- | --- |
| The repository should stop using `harness` | `harness remove` |
| The repository no longer wants one feature, for example Ralph | `harness remove --module ralph` |
| You want to see what would be deleted | `harness remove --dry-run` |

Turning a module off in the profile and running `harness render` has the same effect as `remove --module`, except
that `remove --module` also sets the module to `false` in the profile for you.

## How it works

### Removing everything

`remove` shows what it will delete and rewrite, and asks before doing it; `--yes` skips the question, and without a
terminal and without `--yes` it stops with exit 3.

1. Read the manifest.
2. Check each owned path's hash. A path whose content changed since the last render is refused, and the command
   stops before deleting anything; `--force` deletes it anyway.
3. Delete owned files and links: the harness skill, its links, `.codex/rules/harness.rules`, mise wrappers, the
   manifest.
4. Remove managed blocks from `AGENTS.md`, `README.md`, `CLAUDE.md` and `.gitignore`, markers included. A file left
   empty once its blocks are gone is deleted.
5. Unrender owned keys and entries from native files:
   - `.claude/settings.json` keeps every key `harness` does not own. Its `permissions` lists are rewritten as plain
     rules rendered from the policy one last time, so imported and repository entries keep working as a hand-owned
     file. The harness hook entries are removed; `raw` hook entries stay as plain hooks.
   - `.codex/hooks.json` and `.codex/config.toml` lose the harness entries and keys; `raw` entries stay.
6. Leave the repository files in place: `.agents/harness.yaml`, `.agents/policy.yaml` and `.agents/hooks.yaml` are
   repository-owned. Delete them yourself if you no longer want them.

Never touched: knowledge (`AGENTS.md` prose, `.agents/memory/` content, domain skills), state (`.work/`, `.beads/`),
`settings.local.json`, and anything `harness` did not create.

### Removing one module

`--module <name>` removes only what that module produces and sets `modules.<name>: false` in the profile:

| Module | Removed |
| --- | --- |
| `hooks` | harness hook entries in native files (`raw` hooks stay as plain hooks) |
| `verify` | the `verify` wrapper and the module's allow entries |
| `tasks` | the `task` wrapper; `/.beads/` from the `ignore` block |
| `ralph` | the `ralph` and `ralph:clean` wrappers and the module's allow entries |
| `memory` | the `memory-index` block; the `.agents/memory/` content stays, it is knowledge |
| `skills` | nothing on disk; `skills sync` and the pending check stop. Installed skills stay |

Repeat `--module` to remove several.

## Reference

```text
harness remove [--module <name>]... [--force] [--dry-run]
```

| Flag | Default | Meaning |
| --- | --- | --- |
| `--module <name>` | all | Remove one module: `hooks`, `verify`, `tasks`, `ralph`, `memory`, `skills`. Repeatable |
| `--force` | off | Delete owned paths even when their content changed since the last render |
| `--dry-run` | off | Print what would be deleted or rewritten |

Global flags `-C <dir>`, `--yes` and `--verbose` also apply; see [CLI reference](cli.md).

| Exit code | Meaning |
| --- | --- |
| 0 | Removed |
| 1 | Error, for example no manifest |
| 3 | An owned path was modified by hand, so nothing was deleted: inspect it, then rerun with `--force` or restore it. Or confirmation was needed without a terminal: rerun with `--yes` |

Files read: the manifest and every path it lists. Files written: those paths, `.agents/harness.yaml` (for
`--module`).

## Examples

```sh
$ harness remove --dry-run
would delete: .agents/skills/harness/SKILL.md .claude/skills/harness .codex/skills/harness
              .codex/rules/harness.rules .mise/tasks/verify .mise/tasks/task
              .mise/tasks/worktree/create .mise/tasks/worktree/remove .agents/.harness/manifest.json
would remove blocks: AGENTS.md (commands, files, memory-index, policy-summary), README.md (commands, files),
                     CLAUDE.md (import), .gitignore (ignore)
would unrender: .claude/settings.json (hook entries; permissions kept as plain rules), .codex/hooks.json
$ harness remove --yes
$ git status --short
```

Drop Ralph from an Acme Go repository that never runs unattended:

```sh
harness remove --module ralph
git commit -am "chore: drop harness ralph module"
```

## Guidelines

- Do commit or stash your work first, so `git diff` shows only what `remove` did.
- Do read the `--dry-run` output for a repository you did not set up.
- Don't use `--force` before looking at why a path changed: a hand edit there may be the only copy of a decision that
  belongs in a repository file.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| Exit 3 naming a file | The file changed since the last render | Move the meaningful change into a repository file, or rerun with `--force` |
| `CLAUDE.md` was deleted | It held only the `import` block, so it was empty once the block was removed | Expected; recreate it if you want one |
| The agent lost its lint-on-edit hook | The `hooks` module was removed | Re-enable `modules.hooks` and render, or keep a `raw` hook |
