# Core implementation

This guide describes the engine on `main`. The [architecture](../docs/architecture.md) states the intended design,
and [issue #15](https://github.com/lmarqs/the-cook/issues/15) tracks the parts that do not run yet.

The core is one engine, `thecook.cookbook`, that applies recipes named by coordinates or by a `.groovy` path in the
kitchen. The author's annotations are Kotlin, in `thecook.cookbook.api`; the engine is Groovy.

## One invocation

`CookbookApply.propose` takes the recipes in the order given and returns a `Proposal`; nothing reaches the disk until
`Proposal.commit`.

1. `RecipeLoader` resolves each operand. Coordinates read the artifact's jar from the kitchen's repositories
   (`CookbookRepositories`). A path must resolve inside the kitchen, symbolic links included; its model imports
   resolve from the releases its `@cookbook('group:version')` annotations name. Each artifact is read, and its models
   compiled, once per invocation.
2. `CookbookCompiler` compiles an artifact's models in a class loader of their own, then the recipe in its own class
   loader over them. Every unit imports the author's annotations, `groovy.json`, `groovy.yaml`, `groovy.toml` and
   `java.nio.file` by default. A recipe's `@param` and `@ingredient` lines become fields.
3. `RecipeRun` binds each `@param` from the recipe's arguments, injects each `@ingredient` as a new model instance at
   its directory, and runs the recipe's body. A model reads its own artifact's packaged files and the draft's current
   content.
4. `Declarations` reads what the models declare through their annotated members: the sections, keys, whole files and
   links they own, the files they seed and the files they delete.
5. The recipe applies to one in-memory `Draft` of the kitchen: it withdraws what `cook.lock` says it owned and no
   longer declares, writes what it declares, and takes ownership from the recipes that held it. Each later recipe
   sees what the earlier ones wrote.
6. `CookLock` records each recipe's short name, source, the source's sha256 and what it owns, and the draft writes
   `cook.lock` with the rest.

## Recipe names and sources

| Operand | Short name | Source in `cook.lock` | sha256 of |
| --- | --- | --- | --- |
| `group:artifact:version[:recipe]` | `group:artifact:recipe` | the coordinates with the recipe | the jar |
| A `.groovy` path in the kitchen | the path in the kitchen without `.groovy` | the path in the kitchen | the file |

Section markers name the short name and the region: `cook:begin <short name>#<region>`.

## Targets

Each owned part is a `Target` that writes itself, withdraws itself and gives its `cook.lock` entry:

| Target | Document |
| --- | --- |
| `SectionTarget` | `BlockDocument`, with markers in the file's comment syntax: `<!-- -->` in Markdown, `#` elsewhere |
| `KeyTarget` | `JsonDocument`, `TomlDocument` or `YamlDocument`, which edit one key path and keep the rest of the text |
| `FileTarget` | The whole file, with the permissions its declaration names |
| `LinkTarget` | A symbolic link |

A section or key is never written through a symbolic link, and `TargetPath` refuses paths that leave the kitchen or
enter `.git` or `.cook`.

## Writing

`Draft.commit` checks that no changed path differs from what the review showed, then writes each path atomically.
When a write fails, it restores the paths it already wrote and fails.

## Differences from the intended architecture

- No batch records and no `revert`: `Draft.commit` keeps no journal, so a process killed mid-commit can leave files
  and `cook.lock` half-written.
- No `https://` recipe operands, and no `apply` written as a command.
- Models use only their own artifact's types.
- A document forgets, between invocations, which empty parents it created, so withdrawing a key leaves those parents.

## Verification

`./gradlew check` runs the core tests: `CookbookApplyTest` applies the `agents` artifact and small artifacts built
in the test, by coordinates and by path; `DocumentsTest` edits JSON and TOML through the documents' own API;
`CookbookRepositoriesTest` and `RecipeSourceTest` cover resolution and recipe texts.
