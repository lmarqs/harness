@help('''
Renders the shared agent policy into each selected harness's native permissions. An absent policy is seeded
with the starter, which grants nothing.

## Before applying

Reads `.agents/policy.yaml` when present. The policy uses version 1 and literal command prefixes;
unsupported versions, unknown top-level fields and invalid command prefixes are rejected.

## Apply

```sh
./cook dev.thecook:agents:0.1.0:permissions --harness=claude --harness=codex
```

## Writes

| Target | Part | When |
| --- | --- | --- |
| `.agents/policy.yaml` | Starter policy, seeded when absent; never owned | Always |
| `.claude/settings.json` | `permissions` | `--harness=claude` |
| `.codex/config.toml` | `sandbox_mode`, `approval_policy`, `sandbox_workspace_write` | `--harness=codex` |
| `.codex/rules/cook.rules` | The section `commands` | `--harness=codex` and the policy has command rules |

Applying with fewer harnesses removes the outputs of the ones left out.

## With other recipes

Apply `dev.thecook:agents:0.1.0:instructions` with the same harness selection to connect shared instructions.
The recipes write separate targets and work in either order.
''')
@yield('''
Edit the shared policy and apply again to render the permissions.
''')
import dev.thecook.agents.Agents
import dev.thecook.agents.Claude
import dev.thecook.agents.Codex

@param(value = ['harness'], choices = ['claude', 'codex'], many = true, doc = 'Harnesses that receive the shared policy')
String[] harness

@ingredient(Agents) def agents
@ingredient(Claude) def claude
@ingredient(Codex) def codex

agents.permit([claude, codex].findAll { it.name in harness })
