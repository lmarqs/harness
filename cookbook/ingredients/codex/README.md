# codex

Models the files Codex reads in a repository: `.codex/config.toml`, `.codex/hooks.json`, `.codex/rules/` and its
share of `AGENTS.md`. It is a harness: [agents](../agents/README.md) drives it through `permit`, `readInstructions`
and `readSkills`.

## Model

- `permit(policy)` writes `sandbox_mode = "workspace-write"`, `approval_policy = "on-request"`,
  `sandbox_workspace_write.network_access = false`, and one `prefix_rule` per command. `allow` stays `allow`, `ask`
  becomes `prompt`, `deny` becomes `forbidden`.
- `config(map)` and `hooks(map)` add top-level keys.
- `rule(name, body)` adds a whole rule file.
- `instruct(lines)` adds Codex-specific instruction lines.
- `readInstructions(path)` and `readSkills(path, names)` write nothing: Codex reads `AGENTS.md` and `.agents/skills`
  in its own directory. Any other path is rejected.

## Writes

| Target | Part |
| --- | --- |
| `.codex/config.toml` | Each top-level key given, owned whole |
| `.codex/hooks.json` | Each top-level key given, owned whole |
| `.codex/rules/<name>` | The whole file, one per rule |
| `.codex/rules/cook.rules` | The section `commands`, in `#` comment syntax |
| `AGENTS.md` | The section `codex` |
