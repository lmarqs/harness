# Cookbook agent setup

`recipe.groovy` returns a `Batch` composing Git, TheCook, shared skill links, shared instructions, and the Codex and Claude targets.

```sh
./cook cookbook --policy=.agents/policy.yaml --dry-run
./cook cookbook --policy=.agents/policy.yaml
```

| Argument | Contract |
| --- | --- |
| `--policy` | Required policy YAML. Relative paths resolve from the repository root. |
| `--skills-home` | Optional shared skill directory; defaults to `~/.agents/skills`. Relative paths resolve from the repository root. |
| `--adopt` | Optional `true` or `false`, default `false`. Allows adoption of existing unmanaged keys and links; managed drift still rejects. |

Unknown arguments, invalid policies, and missing `SKILL.md` files fail before outputs are applied. The skills directory must contain `solid`, `hexagonal-layout`, and `effective-java-core`.

The policy schema is version 1:

```yaml
version: 1
instructions:
  - Preserve unrelated work.
commands:
  allow:
    - [git, status]
  ask:
    - [terraform, apply]
  deny:
    - [git, reset, --hard]
```

Command entries are nonempty lists of literal tokens. Shell syntax, wildcards, unknown fields, duplicate keys, and executable YAML tags are rejected.

Outputs are `AGENTS.md`, `CLAUDE.md`, managed Codex TOML keys and rules, managed Claude JSON permission keys, and skill symlinks. `.agents/skills/<name>` points to the absolute shared skill path; `.claude/skills/<name>` points to `../../.agents/skills/<name>`. Git ignores local ownership state and those links. The selected policy is read without modification; policy and shared settings remain available to version control.

Codex rules govern requests outside its sandbox and do not inspect script contents. Claude permissions use native Bash prefix rules. Policy instructions are guidance for both agents.

The root `cook.groovy` and [agent setup aliases](../agent-setup/README.md) use this recipe with a default policy of `.agents/policy.yaml`.
