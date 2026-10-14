# Cookbook agent setup

This guide describes the current implementation. The [architecture](../../../docs/architecture.md) defines the
intended design; the [factory guide](../README.md) explains current CLI limits, source prerequisites and ownership
reconciliation. Existing composition APIs and adoption gates do not settle the deferred model or hooks.

`recipe.groovy` returns a factory composing Git, TheCook, shared skill links, shared instructions, and the Codex and Claude recipe renderers.

For independent harness setup, use [agentic init, permission, and context](../agentic/README.md). That flow selects applications per invocation and requires no `cook.groovy`. This existing `cookbook` recipe remains a supported combined entry point with its policy and shared-skills behavior; existing invocations and composed `book.recipe('cookbook', ...)` calls continue to work. Its outputs and ownership differ from the independent recipes, so follow the command set for the entry point you selected.

Before the commands below, build the local CLI as described in the [factory guide](../README.md#before-running-recipes).
Supply a policy path and a skill source containing `solid/SKILL.md`, `hexagonal-layout/SKILL.md` and
`effective-java-core/SKILL.md`. The default `~/.agents/skills` is personal input, not supplied by this clone; use
`--skills-home=/absolute/path/to/skills` to choose another source. `.agents/policy.yaml` is not a tracked starter:
an absent destination-local path can be seeded, while an existing source must be a valid regular policy file.
Symlink policy sources are rejected. A missing source outside the destination cannot be seeded there.

These commands assume absent or already managed selected output. Tracked agent files in a fresh clone are unmanaged,
so even `--dry-run` can fail. It reports changed paths rather than a content diff; the write command has no approval
prompt. Review sources and output prerequisites before invoking it.

```sh
./cook cookbook --policy=.agents/policy.yaml --dry-run
./cook cookbook --policy=.agents/policy.yaml
```

| Argument | Contract |
| --- | --- |
| `--policy` | Required policy YAML path. Relative paths resolve from the repository root. An absent path inside the repository receives a version-one starter policy. |
| `--skills-home` | Optional shared skill directory; defaults to `~/.agents/skills`. Relative paths resolve from the repository root. |
| `--adopt` | Optional `true` or `false`, default `false`. Allows adoption of selected unmanaged keys, Codex rule content and skill links; managed drift still rejects. |

`--adopt=true` does not adopt the `AGENTS.md` instruction block or Claude's `CLAUDE.md` block, and it cannot resolve
conflicting ownership from another application. Combined and independent permission renderers share the
`agentic.permissions/codex` and `agentic.permissions/claude` application IDs, so recooking either entry point can
withdraw omitted contributions within those applications. Inspect their output scopes before mixing them.

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

Outputs are `AGENTS.md`, `CLAUDE.md`, managed Codex TOML keys and rules, managed Claude JSON permission keys, and skill symlinks. `.agents/skills/<name>` points to the absolute shared skill path; `.claude/skills/<name>` points to `../../.agents/skills/<name>`. Git ignores local ownership state and those links. An absent policy receives an editable starter file after its effective content passes policy validation. Existing policy files are read without modification; the seeded policy is unowned and is preserved when its
contributions are withdrawn through composition. This closure factory has no removal declaration, so
`./cook cookbook --remove` is unsupported. Use the declared independent recipes for their documented CLI removal flow.

Codex rules govern requests outside its sandbox and do not inspect script contents. Claude permissions use native Bash prefix rules. Policy instructions are guidance for both agents.

The CLI and `book.recipe('cookbook', kitchen, options)` invoke the same factory. Relative policy and skill paths resolve from the destination repository. In a composed call, `policy` is a string,
`'skills-home'` is an optional string, and `adopt` must be the string `'true'` or `'false'`, matching this factory's
validation; it does not accept boolean `true` like the guarded migration factories.
