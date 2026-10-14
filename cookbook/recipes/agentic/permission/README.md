# Permission recipe

This guide describes the current implementation. The [architecture](../../../../docs/architecture.md) defines the
intended design; the [agentic guide](../README.md) explains current CLI limits, launcher prerequisites and differences
from the target. Existing composition APIs and adoption behavior do not settle the deferred model or hooks.

`agentic/permission` applies one version 1 policy to the selected harnesses. After the [CLI build](../README.md#before-running-recipes), the commands below use this checkout's `./cook` development launcher, which chooses this checkout as the destination. Use `--repo PATH` for another destination or run a packaged launcher from that repository. A relative `--policy` path resolves from the destination, including when the recipe is invoked by direct path.

```sh
./cook agentic/permission --help
./cook cookbook/recipes/agentic/permission.groovy --help
./cook agentic/permission --policy=.agents/policy.interactive.yaml --harness=claude --dry-run
./cook agentic/permission --policy=.agents/policy.interactive.yaml --harness=claude
./cook agentic/permission --policy=.agents/policy.unattended.yaml --harness=codex
./cook cookbook/recipes/agentic/permission.groovy --policy=.agents/policy.interactive.yaml --harness=claude
```

`--policy PATH` is required for application, and `--harness CHOICE` is required and repeatable (`claude` or `codex`). Repeating it applies the same policy to both harnesses in one transaction. `--adopt=true` permits takeover of conflicting unmanaged permission keys; the default is `false`. `--repo PATH` selects another destination, and `--dry-run` reports proposed changed paths without writing target files. The CLI's `--help` lists these options without reading the policy file.

The cooking examples assume the selected native content is absent or already owned by this application; unmanaged conflicts need the current `--adopt=true` option, including Codex's rules block. This option does not bypass managed edits. Create the two regular policy source files in the destination before cooking; symbolic-link policy sources are rejected. The init starter `.agents/policy.yaml` does not supply these two filenames. Existing tracked policies in this checkout need not be replaced.

For instance, `.agents/policy.interactive.yaml` can contain:

```yaml
version: 1
commands:
  allow:
    - [mise, run, check]
```

And `.agents/policy.unattended.yaml` can contain:

```yaml
version: 1
commands:
  allow:
    - [git, status]
```

The two policy commands above deliberately use different sources. The filename selects policy content, not a session role. Each harness has one repository-wide permission application. Applying Claude leaves Codex's native files and ownership intact, and vice versa. Edit a policy and run its command again to update that harness. A renamed policy file can be passed to the same harness application; the path is not its ownership identity.

The policy format accepts `version: 1`, optional `instructions`, and `commands.allow`, `commands.ask`, and `commands.deny` lists of literal, nonempty command-token prefixes. See [PolicyFile.groovy](../../../ingredients/agent-policy/PolicyFile.groovy). Shell expressions and wildcards are outside this scope. Native rendering uses each harness's own permission model: Claude receives Bash permission entries in `.claude/settings.json`; Codex receives execution settings in `.codex/config.toml` and prefix rules in `.codex/rules/cookbook.rules`. Equivalent source prefixes do not guarantee identical enforcement by the two harnesses. Review the generated native configuration and the harness's behavior before relying on a rule.

The recipe preserves unrelated native settings and creates no skills, hooks, `AGENTS.md`, or `CLAUDE.md`. An existing unmanaged conflicting key requires `--adopt=true`; an edited managed region stops reconciliation. A missing, empty, malformed, or unsupported policy fails before writes. `.cook/state.json` records owned output for later reconciliation; it is not a user-authored complete desired setup.

Remove only the selected harness's managed permissions explicitly; the source policy remains. Current removal restores saved adopted originals when applicable and rejects managed drift; it is not target discard or run-ID revert:

```sh
./cook agentic/permission --harness=claude --remove --dry-run
./cook agentic/permission --harness=claude --remove
./cook cookbook/recipes/agentic/permission.groovy --harness=codex --remove
```

Removal reads saved ownership, so it needs no policy source. For permission development, the existing independent module check is:

```sh
./gradlew -p cookbook/recipes/agentic/permission check
```

The permission module declares the Codex and Claude renderer modules as dependencies; its build also includes core and the agent-policy ingredient. See [independent module checks](../README.md#build-and-verification) for related development commands.
