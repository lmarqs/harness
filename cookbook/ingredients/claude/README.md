# claude

Models the files Claude Code reads in a repository: `.claude/settings.json`, `CLAUDE.md` and `.claude/skills/`. It is
a harness: [agents](../agents/README.md) drives it through `permit`, `readInstructions` and `readSkills`.

## Model

- `permit(policy)` writes the shared policy as `permissions`: `acceptEdits`, and one `Bash(<prefix> *)` entry per
  command in the list named after its decision.
- `settings(map)` adds top-level settings keys, hooks included.
- `instruct(lines)` adds instruction lines.
- `readInstructions(path)` adds the import line `@<path>` to `CLAUDE.md`.
- `readSkills(path, names)` links each named skill into `.claude/skills/`.
- `localFiles` names the files Claude writes locally, for an ignore consumer.

## Writes

| Target | Part |
| --- | --- |
| `.claude/settings.json` | Each top-level key given, owned whole |
| `CLAUDE.md` | The section `instructions` |
| `.claude/skills/<name>` | One relative link per shared skill |

The same prefixes do not mean identical enforcement across harnesses; review the generated permissions against
Claude's own permission model.
