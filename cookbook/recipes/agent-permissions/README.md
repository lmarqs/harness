# agent-permissions

Renders the shared policy at `.agents/policy.yaml` into each selected harness's native permissions. An absent policy
is seeded with the starter, which grants nothing.

```sh
./cook dev.thecook:agent-permissions --harness=claude --harness=codex
```

| Target | Part |
| --- | --- |
| `.agents/policy.yaml` | Seeded when absent; never owned |
| `.claude/settings.json` | `permissions`, for `claude` |
| `.codex/config.toml` | `sandbox_mode`, `approval_policy`, `sandbox_workspace_write`, for `codex` |
| `.codex/rules/cook.rules` | The section `commands`, for `codex` |

Applying it again with fewer harnesses removes the outputs of the ones left out.
