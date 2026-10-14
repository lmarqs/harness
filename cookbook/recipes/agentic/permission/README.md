# agentic/permission

Renders one agent policy file into the native permission settings of the selected harnesses: Bash permission rules for
Claude, and sandbox settings plus command prefix rules for Codex.

## Usage

```sh
./cook agentic/permission --policy=.agents/policy.yaml --harness=claude --harness=codex
```

## Arguments

| Argument | Required | Default | Meaning |
| --- | --- | --- | --- |
| `policy` | yes | none | Path to a version 1 policy file. A relative path resolves against the destination repository. The file must be a regular file, not a symbolic link. |
| `harness` | yes | none | `claude` or `codex`. Repeat it to apply the same policy to both. |
| `adopt` | no | `false` | `true` or `false`. `true` takes over existing unmanaged values at the keys the recipe writes and an existing unmanaged `cookbook.codex/commands` block. |

## Output

| Target | Format | Part the recipe owns |
| --- | --- | --- |
| `.claude/settings.json` | JSON | Keys `permissions.defaultMode`, `permissions.allow`, `permissions.ask` and `permissions.deny`. |
| `.codex/config.toml` | TOML | Keys `sandbox_mode`, `approval_policy` and `sandbox_workspace_write.network_access`. |
| `.codex/rules/cookbook.rules` | Codex rules | The block `cookbook.codex/commands`. |

## Inputs

The policy file is YAML with these fields, read by
[PolicyFile.groovy](../../../ingredients/agent-policy/PolicyFile.groovy):

- `version`: required, must be `1`.
- `instructions`: optional list of strings. This recipe accepts it and writes nothing from it.
- `commands`: optional object with optional `allow`, `ask` and `deny` lists. Each entry is a list of tokens that form a
  literal command prefix, such as `[mise, run, check]`.

Each token is nonempty and consists of letters, digits and `_ . / : = @ + -`, as defined in
[Policy.groovy](../../../ingredients/agent-policy/Policy.groovy). Shell syntax and wildcards are not supported.

## Behaviour

For Claude, `permissions.defaultMode` is `acceptEdits`, and each command becomes a `Bash(<tokens> *)` entry in the
list named after its decision. All three lists are written, empty when the policy has no command for that decision.

For Codex, the keys are `sandbox_mode = "workspace-write"`, `approval_policy = "on-request"` and
`sandbox_workspace_write.network_access = false`. The rules block starts with three comment lines, followed by one
`prefix_rule` per command: `allow` stays `allow`, `ask` becomes `prompt` and `deny` becomes `forbidden`.

The same prefixes do not guarantee identical enforcement by the two harnesses; review the generated configuration
against each harness's permission model.

A missing, empty or malformed policy, an unknown field, a version other than `1`, a duplicate key, an unsupported
token or a symbolic link is rejected before any file is written. The recipe leaves other keys in these files untouched
and writes no `AGENTS.md`, `CLAUDE.md`, skills or hooks.

Each harness has its own application, `agentic.permissions/claude` or `agentic.permissions/codex`. Applying one
harness leaves the other harness's files intact. The policy path is not part of the identity, so cooking a harness
with another policy file replaces that harness's output.

`--remove --harness=<harness>` withdraws `agentic.permissions/<harness>`. `--policy` is accepted during removal but not
read, and the policy file is not touched.

The recipe renders Claude output with `ClaudeFiles` from [agentic/claude](../claude/README.md), without its instruction
block, and Codex output with `CodexFiles` from [agentic/codex](../codex/README.md). It is composed by
[agentic/init](../init/README.md).

## Example

The policy

```yaml
version: 1
commands:
  allow:
    - [mise, run, check]
  deny:
    - [git, push]
```

sets `permissions.allow` to `["Bash(mise run check *)"]`, `permissions.ask` to `[]` and `permissions.deny` to
`["Bash(git push *)"]` in `.claude/settings.json`, and produces this block in `.codex/rules/cookbook.rules`:

```text
# cook:begin cookbook.codex/commands
# Generated from the selected policy by ./cook cookbook.
# These literal prefix rules govern execution outside the Codex sandbox.
# They do not inspect the contents of scripts or enforce instructions as OS controls.
prefix_rule(pattern = ["mise","run","check"], decision = "allow")
prefix_rule(pattern = ["git","push"], decision = "forbidden")
# cook:end cookbook.codex/commands
```

## Development

```sh
./gradlew -p cookbook/recipes/agentic/permission check
```

`check` runs the module tests, CodeNarc and Spotless. The recipe source is
`cookbook/recipes/agentic/permission.groovy`, packaged as `recipe.groovy`. The module publishes
`dev.thecook.recipes:agentic-permission`, with its version in `gradle.properties`, and depends on the `agentic-codex`
and `agentic-claude` modules.
