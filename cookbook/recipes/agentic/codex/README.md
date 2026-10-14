# agentic/codex

Writes Codex configuration keys into `.codex/config.toml`, hook keys into `.codex/hooks.json`, whole rule files under
`.codex/rules/` and a managed instruction block into `AGENTS.md`. A composing recipe uses it to declare native Codex
configuration.

## Usage

```groovy
book.recipe('agentic/codex', kitchen, [
    config: [model: 'selected-model'],
    instructions: 'Use project tasks.',
    rules: ['project.rules': 'prefix_rule(pattern = ["mise", "run", "check"], decision = "allow")\n']
]).mount()
```

## Arguments

| Argument | Required | Default | Meaning |
| --- | --- | --- | --- |
| `id` | no | `codex` | Component identity. The instruction block is marked `<id>/instructions`. |
| `config` | no | none | A map, or a collection of maps, of top-level TOML keys and their values. |
| `hooks` | no | none | A map, or a collection of maps, of top-level hook keys and their values. |
| `instructions` | no | none | Text of the instruction block. The block is written only when this is set. |
| `rules` | no | none | A map from rule file name to the whole content of that file. |
| `configPath` | no | `.codex/config.toml` | File that receives the `config` keys. |
| `hooksPath` | no | `.codex/hooks.json` | File that receives the `hooks` keys. |
| `instructionsPath` | no | `AGENTS.md` | File that receives the instruction block. |
| `adopt` | no | `false` | Boolean. `true` takes over existing unmanaged values at the selected `config` and `hooks` keys and existing rule files. It never adopts the instruction block. |

## Output

| Target | Format | Part the recipe owns |
| --- | --- | --- |
| `.codex/config.toml` | TOML | Each top-level key supplied through `config`, as a whole value. |
| `.codex/hooks.json` | JSON | Each top-level key supplied through `hooks`, as a whole value. |
| `.codex/rules/<name>` | Codex rules | The whole file, one per `rules` entry. |
| `AGENTS.md` | Markdown | The block `<id>/instructions`, `codex/instructions` by default. |

## Behaviour

Each top-level key is owned whole. Contributions are merged, and two contributions with different values for the same
key are rejected with `Conflicting Codex config: <key>` or `Conflicting Codex hooks: <key>`. A rule file name must
consist of letters, digits, `_`, `.` and `-` and must not be `.` or `..`; other names are rejected with
`Codex rule file requires a simple file name`. Rule files always live under `.codex/rules/`. Each output is written
only when its argument is given. Other keys and text outside the block are left untouched.

The returned recipe exposes:

- the inputs `config` and `hooks`, which accept further maps or deferred exports from a composing recipe;
- the export `nativePaths`: `AGENTS.md`, `.codex/config.toml`, `.codex/rules/` and `.codex/hooks.json`.

The recipe records its output under the application `agentic.permissions/codex`. The Codex output of
[agentic/permission](../permission/README.md) and of [cookbook](../../cookbook/README.md) uses the same application,
so cooking one of them withdraws content another recorded there that the run does not declare. The recipe
declares no removal scope.

The module compiles the renderer `thecook.cookbook.recipes.agentic.codex.CodexFiles`:

- `new CodexFiles(String name = 'codex', boolean adopt = false)` exposes `config` and `hooks` (lists of key
  contributions), `configPath`, `hooksPath`, `instructionsPath` and `instructions`, plus `rule(name, content)` for a
  whole rule file and `ruleBlock(name, region, content)` for a managed block in a rule file. This recipe uses it.
- `new CodexFiles(Policy policy, boolean adopt = false, String name = 'cookbook.codex')` translates an agent policy
  into `sandbox_mode = "workspace-write"`, `approval_policy = "on-request"` and
  `sandbox_workspace_write.network_access = false`, plus the block `commands` in `.codex/rules/cookbook.rules` with
  one `prefix_rule` per policy command: `allow` stays `allow`, `ask` becomes `prompt` and `deny` becomes `forbidden`.
  `adopt` applies to those keys and that block.

agentic/permission and cookbook use the policy constructor.

## Development

```sh
./gradlew -p cookbook/recipes/agentic/codex check
./gradlew -p cookbook/recipes/agentic/codex build
```

The module publishes `dev.thecook.recipes:agentic-codex`, with its version in `gradle.properties`, and depends on
`dev.thecook.ingredients:agent-policy`.
