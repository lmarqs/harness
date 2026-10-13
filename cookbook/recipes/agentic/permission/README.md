# Permission recipe

`agentic/permission` applies a version 1 policy file to the selected harnesses. The policy contains literal command prefixes; see [PolicyFile.groovy](../../../ingredients/agent-policy/PolicyFile.groovy) for the accepted YAML shape. Supply `--policy` and at least one `--harness=claude` or `--harness=codex`. Repeat `--harness` to apply one policy to both in a single transaction.

```sh
./cook agentic/permission --policy=.agents/policy.interactive.yaml --harness=claude
./cook agentic/permission --policy=.agents/policy.unattended.yaml --harness=codex
```

The filename only selects policy content. It does not select a session role. These files configure each harness repository-wide: one Claude configuration and one Codex configuration can coexist, but a single harness does not have separate interactive and unattended sessions from this recipe. Applying a policy to one harness leaves the other harness's files and ownership unchanged. Changing the policy filename for a harness updates that same application.

Claude writes permission keys in `.claude/settings.json`. Codex writes execution settings in `.codex/config.toml` and command rules in `.codex/rules/cookbook.rules`. The recipe preserves unrelated settings. It creates no skills, hooks, `AGENTS.md`, or `CLAUDE.md`. Existing unmanaged conflicting keys require `--adopt=true`; edited managed content is rejected. A missing, empty, malformed, or unsupported policy fails before any output or state change.

To remove only one harness's managed permissions, run `./cook agentic/permission --harness=claude --remove` or select Codex. Removal does not require the policy file. Run `./gradlew -p cookbook/recipes/agentic/permission check` to verify the recipe and its fixtures.
