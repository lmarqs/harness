# agentic/claude

Writes Claude Code settings keys into `.claude/settings.json` and a managed instruction block into `CLAUDE.md`. A
composing recipe uses it to declare native Claude configuration.

## Usage

```groovy
book.recipe('agentic/claude', kitchen, [
    settings: [permissions: [allow: ['Bash(mise run check *)']]],
    instructions: '@AGENTS.md'
]).mount()
```

## Arguments

| Argument | Required | Default | Meaning |
| --- | --- | --- | --- |
| `id` | no | `claude` | Component identity. The instruction block is marked `<id>/instructions`. |
| `settings` | no | none | A map, or a collection of maps, of top-level settings keys and their values. |
| `instructions` | no | none | Text of the instruction block. The block is written only when this is set. |
| `settingsPath` | no | `.claude/settings.json` | File that receives the settings keys. |
| `instructionsPath` | no | `CLAUDE.md` | File that receives the instruction block. |
| `adopt` | no | `false` | Boolean. `true` takes over existing unmanaged values at the selected settings keys. It never adopts the instruction block. |

## Output

| Target | Format | Part the recipe owns |
| --- | --- | --- |
| `.claude/settings.json` | JSON | Each top-level key supplied through `settings`, as a whole value. |
| `CLAUDE.md` | Markdown | The block `<id>/instructions`, `claude/instructions` by default. |

## Behaviour

Each top-level settings key is owned whole: `settings: [permissions: [allow: [...]]]` owns the entire `permissions`
object. Contributions are merged, and two contributions with different values for the same key are rejected with
`Conflicting Claude settings: <key>`. The recipe writes nothing when neither `settings` nor `instructions` is given.
Other keys in the settings file and text outside the block are left untouched. The recipe does not create files that
the instructions refer to, such as `AGENTS.md`.

The returned recipe exposes:

- the input `settings`, which accepts further maps or deferred exports from a composing recipe;
- the export `nativePaths`: `CLAUDE.md`, `.claude/settings.json` and `.claude/settings.local.json`;
- the export `files.local`: the root file `.claude/settings.local.json`, for an ignore consumer. The recipe never
  writes that file.

The recipe records its output under the application `agentic.permissions/claude`. The Claude output of
[agentic/permission](../permission/README.md) and of [cookbook](../../cookbook/README.md) uses the same application,
so cooking one of them withdraws content another recorded there that the run does not declare. The recipe
declares no removal scope.

The module compiles the renderer `thecook.cookbook.recipes.agentic.claude.ClaudeFiles`:

- `new ClaudeFiles(String name = 'claude')` exposes `settings` (a list of key contributions), `settingsPath`,
  `instructionsPath`, `instructions` and `adoptInstructions`. This recipe uses it.
- `new ClaudeFiles(Policy policy, boolean adopt = false, String name = 'cookbook.claude')` translates an agent policy
  into `permissions.defaultMode` (`acceptEdits`) and `permissions.allow`, `permissions.ask` and `permissions.deny`
  lists of `Bash(<prefix> *)` entries, plus an instruction block containing `@AGENTS.md`. `adopt` applies to those
  keys.

agentic/permission and cookbook use the policy constructor; [agentic/context](../context/README.md) uses the first
constructor for its `CLAUDE.md` import block.

## Development

```sh
./gradlew -p cookbook/recipes/agentic/claude check
./gradlew -p cookbook/recipes/agentic/claude build
```

The module publishes `dev.thecook.recipes:agentic-claude`, with its version in `gradle.properties`, and depends on
`dev.thecook.ingredients:agent-policy`.
