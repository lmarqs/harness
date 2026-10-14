# agentic/init

Sets up agent permissions and shared instructions for the selected harnesses in one run. It applies
`.agents/policy.yaml`, seeded with a starter policy when missing, through [agentic/permission](../permission/README.md)
to each harness without a permission setup, and composes [agentic/context](../context/README.md) for every selected
harness.

## Usage

```sh
./cook agentic/init --harness=claude --harness=codex
```

## Arguments

| Argument | Required | Default | Meaning |
| --- | --- | --- | --- |
| `harness` | yes | none | `codex` or `claude`. Repeat it to select both. |

## Output

| Target | Format | Part the recipe owns |
| --- | --- | --- |
| `.agents/policy.yaml` | YAML | None. The file is seeded with the starter policy when it does not exist and a selected harness has no permission setup. |
| `.claude/settings.json` | JSON | Through agentic/permission: keys `permissions.defaultMode`, `permissions.allow`, `permissions.ask` and `permissions.deny`. |
| `.codex/config.toml` | TOML | Through agentic/permission: keys `sandbox_mode`, `approval_policy` and `sandbox_workspace_write.network_access`. |
| `.codex/rules/cookbook.rules` | Codex rules | Through agentic/permission: the block `cookbook.codex/commands`. |
| `AGENTS.md` | Markdown | None. agentic/context seeds it when it does not exist. |
| `CLAUDE.md` | Markdown | Through agentic/context: the block `claude-context/instructions`. |

The permission targets are written only for harnesses without a permission setup, and the Claude targets only when
`claude` is selected.

## Inputs

`.agents/policy.yaml` is read when a selected harness has no permission setup. When the file does not exist, the recipe
uses the starter policy, which grants no commands:

```yaml
version: 1
instructions: []
commands: {}
```

## Behaviour

A harness has a permission setup when the repository already records the application `agentic.permissions/<harness>`.
Such a harness keeps its existing policy and permission output; init does not compose agentic/permission for it. When
every selected harness has a permission setup, init does not read `.agents/policy.yaml`.

An existing `.agents/policy.yaml` must be a valid version 1 policy. Otherwise the recipe is rejected with
`Invalid starter policy .agents/policy.yaml` and the starter content as an example, before any file is written.

Init passes no `adopt` value to the recipes it composes, so it never takes over existing unmanaged content. Run
agentic/permission or agentic/context with `--adopt=true` for that.

`--remove --harness=<harness>` withdraws `agentic.permissions/<harness>` and `agentic.context/<harness>`. The other
harness, `.agents/policy.yaml` and `AGENTS.md` stay.

## Development

```sh
./gradlew -p cookbook/recipes/agentic/init check
```

`check` runs the module tests, CodeNarc and Spotless. The recipe source is `cookbook/recipes/agentic/init.groovy`,
packaged as `recipe.groovy`. The module publishes `dev.thecook.recipes:agentic-init`, with its version in
`gradle.properties`, and depends on the `agentic-permission` and `agentic-context` modules.
