# agentic/context

Seeds a minimal `AGENTS.md` and, when Claude is selected, adds a managed `@AGENTS.md` import to `CLAUDE.md`, so the
selected agent harnesses read the same repository instructions.

## Usage

```sh
./cook agentic/context --harness=codex --harness=claude
```

## Arguments

| Argument | Required | Default | Meaning |
| --- | --- | --- | --- |
| `harness` | yes | none | `codex` or `claude`. Repeat it to select both. |
| `adopt` | no | `false` | `true` or `false`. `true` takes over an existing unmanaged `claude-context/instructions` block in `CLAUDE.md`. It has no effect for `codex`. |

## Output

| Target | Format | Part the recipe owns |
| --- | --- | --- |
| `AGENTS.md` | Markdown | None. The file is seeded with `# Agent instructions` when it does not exist. |
| `CLAUDE.md` | Markdown | The block `claude-context/instructions`, written when `claude` is selected. |

## Behaviour

An existing `AGENTS.md`, including an empty one, is never changed, and the seeded file belongs to the repository from
then on. Seeding is rejected with `Seed conflicts with managed output` when Cook already manages content in
`AGENTS.md`. Text in `CLAUDE.md` outside the block is left untouched.

Codex reads `AGENTS.md` directly, so selecting `codex` writes nothing beyond the seed. Each harness has its own
application, `agentic.context/claude` or `agentic.context/codex`, recorded even when it owns no content.

`--remove --harness=claude` withdraws `agentic.context/claude`, which removes the `CLAUDE.md` block;
`--remove --harness=codex` withdraws `agentic.context/codex`, which owns no content. `AGENTS.md` stays in both cases.

The Claude block is rendered by `ClaudeFiles` from [agentic/claude](../claude/README.md). The recipe is composed by
[agentic/init](../init/README.md).

## Example

With `--harness=claude` in a repository without these files, `AGENTS.md` contains `# Agent instructions` and
`CLAUDE.md` contains:

```markdown
# cook:begin claude-context/instructions
@AGENTS.md
# cook:end claude-context/instructions
```

## Development

```sh
./gradlew -p cookbook/recipes/agentic/context check
```

`check` runs the module tests, CodeNarc and Spotless. The recipe source is `cookbook/recipes/agentic/context.groovy`,
packaged as `recipe.groovy`. The module publishes `dev.thecook.recipes:agentic-context`, with its version in
`gradle.properties`, and depends on the `agentic-codex` and `agentic-claude` modules.
