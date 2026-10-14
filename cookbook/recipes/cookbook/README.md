# cookbook

Configures Codex and Claude from one version 1 policy file. The repository gets shared agent instructions in
`AGENTS.md`, native permission settings for both agents, and links to three shared skills.

## Usage

```sh
./cook cookbook --policy=.agents/policy.yaml
```

```groovy
book.recipe('cookbook', kitchen, [policy: '.agents/policy.yaml']).mount()
```

## Arguments

| Argument | Required | Default | Meaning |
| --- | --- | --- | --- |
| `policy` | yes | none | String path to the policy YAML. A relative path resolves from the repository root. The path must resolve inside the repository. |
| `skills-home` | no | `~/.agents/skills` | String path to the directory that holds the shared skills. A relative path resolves from the repository root. |
| `adopt` | no | `'false'` | String `'true'` or `'false'`; any other value, including boolean `true`, is rejected. `'true'` lets the recipe adopt existing unmanaged Codex and Claude permission keys, the Codex rules block and the skill links. |

## Output

| Target | Format | Part the recipe owns |
| --- | --- | --- |
| `AGENTS.md` | Markdown | The `agent-policy/instructions` block: a fixed `# Working on the-cook` section that names the three skills and the build commands, followed by the policy's `instructions`. |
| `CLAUDE.md` | Markdown | The `cookbook.claude/instructions` block, containing `@AGENTS.md`. |
| `.codex/config.toml` | TOML | `sandbox_mode` (`workspace-write`), `approval_policy` (`on-request`) and `sandbox_workspace_write.network_access` (`false`). |
| `.codex/rules/cookbook.rules` | Codex rules | The `cookbook.codex/commands` block: one `prefix_rule` per policy command, with `allow`, `ask` and `deny` rendered as `allow`, `prompt` and `forbidden`. |
| `.claude/settings.json` | JSON | `permissions.defaultMode` (`acceptEdits`) and the `permissions.allow`, `permissions.ask` and `permissions.deny` lists, one `Bash(<tokens> *)` entry per policy command. |
| `.agents/skills/<name>` | Symbolic link | The whole link, pointing to the absolute path of `<skills-home>/<name>`. |
| `.claude/skills/<name>` | Symbolic link | The whole link, pointing to `../../.agents/skills/<name>`. |
| `.gitignore` | Ignore file | The `git/ignore` block: `/.cook/` and the six skill links. |
| The `policy` path | YAML | Nothing. When the file is absent, the recipe writes the starter `version: 1`, `instructions: []`, `commands: {}` and does not own it. |

`<name>` is each of `solid`, `hexagonal-layout` and `effective-java-core`.

## Inputs

- The policy file. It contains `version: 1`, an optional `instructions` list of strings, and optional `commands.allow`,
  `commands.ask` and `commands.deny` lists. Each command is a nonempty list of literal tokens made of ASCII letters,
  digits and `_ . / : = @ + -`. [PolicyFile.groovy](../../ingredients/agent-policy/PolicyFile.groovy) and
  [Policy.groovy](../../ingredients/agent-policy/Policy.groovy) define the format.
- The skill source at `skills-home`. It contains `solid/SKILL.md`, `hexagonal-layout/SKILL.md` and
  `effective-java-core/SKILL.md`.

## Behaviour

- Before it declares any output, the recipe rejects unknown arguments, a missing `policy`, an `adopt` value other than
  `'true'` or `'false'`, a policy path that is a symbolic link, an invalid policy and a missing `SKILL.md`.
- A policy is invalid when its version is not 1, when it has unknown fields or duplicate keys, when it uses non-standard
  YAML tags, or when a command token contains any character outside the allowed set.
- An absent policy file is read as the starter, so the outputs carry no policy instructions or commands. An existing
  policy file is read and never modified.
- `adopt` does not apply to the `AGENTS.md`, `CLAUDE.md` and `.gitignore` blocks.
- The recipe declares these applications: `agentic.init/cookbook` for itself; `agentic.init/the-cook`,
  `agentic.init/git`, `agentic.init/skills`, `agentic.init/claude-skills` and `agentic.init/agent-policy` for its
  ingredients; `agentic.permissions/codex` and `agentic.permissions/claude` for the two permission renderers.

## Example

`.agents/policy.yaml`:

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

`.codex/rules/cookbook.rules`:

```text
# cook:begin cookbook.codex/commands
# Generated from the selected policy by ./cook cookbook.
# These literal prefix rules govern execution outside the Codex sandbox.
# They do not inspect the contents of scripts or enforce instructions as OS controls.
prefix_rule(pattern = ["git","status"], decision = "allow")
prefix_rule(pattern = ["terraform","apply"], decision = "prompt")
prefix_rule(pattern = ["git","reset","--hard"], decision = "forbidden")
# cook:end cookbook.codex/commands
```

`.claude/settings.json`:

```json
{"permissions":{"allow":["Bash(git status *)"], "ask":["Bash(terraform apply *)"], "defaultMode":"acceptEdits", "deny":["Bash(git reset --hard *)"]}}
```
