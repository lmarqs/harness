# Cookbook

Design sketch. Every file under this directory is written in a Groovy-like pseudo language to show the shape of
recipes and ingredients. Nothing here runs.

## Units

A cookbook publishes two kinds of artifact to a Maven-layout repository. Each is one jar plus a POM and carries
everything it needs: code, `README.md`, templates, scripts, skill files.

| Unit | What it is | May depend on |
| --- | --- | --- |
| Ingredient | A model of how a program reads its files, or content a model consumes | Other ingredients, for their types; ordinary libraries |
| Recipe | Logic that chooses a setup through ingredients, plus its README | Ingredients only |

A recipe is addressed as `group:artifact`, or `group:artifact:name` when a jar holds several recipes. Markers and
`cook.lock` use that address as the recipe's short name. Exact release versions only.

Finding a recipe happens outside the engine: a cookbook's repository, an artifact host, a teammate's link. With the
coordinates in hand:

```sh
./cook dev.thecook:the-cook --help         # the recipe's README, then its params
./cook dev.thecook:the-cook
./cook dev.thecook:agent-permissions --harness=claude dev.thecook:agent-context --harness=claude
```

Recipes named in one invocation compose left to right. A recipe never composes another recipe.

## The author's API

Annotations are the only door between an author's code and the engine. Nothing from the engine is imported.

### In an ingredient

| Annotation | On | Declares |
| --- | --- | --- |
| `@Ingredient` | class | A model or content. The engine adds `directory`, the instance's base inside the destination with `.` at its root; `resource(name)` and `resources(dir)`, which read one file or every file packaged in the jar; and `current(path)`, which reads a destination file. |
| `@Section(path, id, comment)` | member returning `List<String>` | The marked section `id` of a shared text file. `comment` names the comment syntax when the extension does not. |
| `@Key(path, segments)` | member | One key path in a JSON, TOML or YAML document, owned whole. A segment can select a list entry. |
| `@Keys(path, under)` | member returning `Map` | One owned key per entry, under the key path `under`. A `List` map key is a deeper path. |
| `@Lines(path)` | member returning `Map<String, String>` | One line per entry, owned on its own. The key is a regular expression: the value replaces the one line it matches, or is appended when none does. More than one match is an error. |
| `@File(path, mode)` | member returning `String` | The whole file. |
| `@Files(path, mode)` | member returning `Map<String, String>` | One whole file per entry, under `path`. |
| `@Links(path)` | member returning `Map<String, String>` | One symbolic link per entry, under `path`, pointing at the value. |
| `@Seed(path)` | member returning `String` | Written only when the file is absent, and never owned. |
| `@Delete(path)` | member returning `boolean` | Deletes a file the recipe does not own, when true. |

Rules:

- A member that returns `null` declares nothing. A pristine instance declares nothing: only what the recipe asked for
  is written. The engine evaluates a pristine instance of every ingredient it loads and rejects the ingredient, naming
  the member, when anything is declared.
- `path` joins to the instance's `directory`. `${property}` in a path reads the instance, for files the recipe names.
- Declare the entry the recipe means, never the list or file around it: a step, a requirement, a line. Ownership then
  covers only that entry, and applying again finds it in place.
- A `Map` segment in a key path selects one entry of a list: `[id: 'cache']` the map entry whose `id` is `cache`,
  `[matches: regex]` the string entry the expression matches. An absent entry is inserted at `place`: `start`, `end`
  by default, `before:<value>` or `after:<value>`, compared the same way. An absent list is created. A path that
  continues past a selector requires the entry to exist. Lock and docs write the entry as `steps[id=cache]`.
- The engine owns every format: markers in the right comment syntax, key merge that preserves unrelated keys, order
  and comments, link and mode handling, the destination boundary.
- An ingredient reads destination content only through `current(path)`, which returns the file as the recipes before
  this one in the invocation left it, or `null` when absent. It never reads the disk, the lock or the engine's state.
- Two declarations of one target in one recipe are an error. Across recipes, the last one wins and owns the target.
- A model exposes facts for other models through plain methods, such as the files it leaves behind. The recipe wires
  them. Ingredients never call each other's outputs.
- A model that knows how to run something returns a command, `[run: [step...], dir: String, env: Map]`, where a step
  is an argument list or shell text and `dir` is relative to the destination. The consumer, such as `mise`, renders
  and quotes it; models never quote.
- A model that several programs read through their own files, such as `agents`, owns that shared layout and drives
  each program through one interface, such as `Harness`. Each program's model only translates.

### In a recipe

| Annotation | On | Declares |
| --- | --- | --- |
| `@ingredient(Type, at)` | field | An injected instance, at the destination root or at the directory `at`. |
| `@param(names, doc, default, choices, many)` | field | An input bound from `--name=value`. Required without a default. `many` accepts repeats. A `File` field is an input file in the destination, read as it was before the invocation. |

A file that another recipe in the same invocation may write, such as a program's own configuration or Ralph's PRD,
is read by the ingredient that models it, through `current(path)`; the recipe passes its path as a `String`.

The body is plain Groovy: it calls ingredient methods and ends. `return` ends it with nothing declared. Everything
the injected instances declare is owned by the recipe.

## Layout

```text
ingredients/<name>/<Name>.groovy     the model or content, with README.md and resources beside it
recipes/<name>/recipe.groovy         the recipe, with README.md beside it
```

Each directory is one artifact. Skill content lives under `ingredients/skills/<name>/`, with the skill's own files under
`skill/`.
