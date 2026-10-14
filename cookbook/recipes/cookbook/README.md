# Cookbook agent setup

`recipe.groovy` returns a factory composing Git, TheCook, shared skill links, shared instructions, and the Codex and Claude recipe renderers.

For independent harness setup, use [agentic init, permission, and context](../agentic/README.md). That flow selects applications per invocation and requires no `cook.groovy`. This existing `cookbook` recipe remains a supported combined entry point with its policy and shared-skills behavior; existing invocations and composed `book.recipe('cookbook', ...)` calls continue to work. Its outputs and ownership differ from the independent recipes, so follow the command set for the entry point you selected.

```sh
./cook cookbook --policy=.agents/policy.yaml --dry-run
./cook cookbook --policy=.agents/policy.yaml
```

| Argument | Contract |
| --- | --- |
| `--policy` | Required policy YAML path. Relative paths resolve from the repository root. An absent path inside the repository receives a version-one starter policy. |
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

Outputs are `AGENTS.md`, `CLAUDE.md`, managed Codex TOML keys and rules, managed Claude JSON permission keys, and skill symlinks. `.agents/skills/<name>` points to the absolute shared skill path; `.claude/skills/<name>` points to `../../.agents/skills/<name>`. Git ignores local ownership state and those links. An absent policy receives an editable starter file after its effective content passes policy validation. Existing policy files are read without modification, and removing the application preserves the policy source.

Codex rules govern requests outside its sandbox and do not inspect script contents. Claude permissions use native Bash prefix rules. Policy instructions are guidance for both agents.

The CLI and `book.recipe('cookbook', kitchen, options)` invoke the same factory. Policy and skill paths resolve from the destination repository.
