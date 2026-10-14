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
| `@Ingredient` | class | A model or content. The engine adds `directory`, the instance's base inside the destination, and `resource(name)`, which reads a file packaged in the jar. |
| `@Section(path, id, comment)` | member returning `List<String>` | The marked section `id` of a shared text file. `comment` names the comment syntax when the extension does not. |
| `@Key(path, segments)` | member | One key path in a JSON, TOML or YAML document, owned whole. |
| `@Keys(path, under)` | member returning `Map` | One owned key per entry, under the key path `under`. A `List` map key is a deeper path. |
| `@File(path, mode)` | member returning `String` | The whole file. |
| `@Files(path, mode)` | member returning `Map<String, String>` | One whole file per entry, under `path`. |
| `@Links(path)` | member returning `Map<String, String>` | One symbolic link per entry, under `path`, pointing at the value. |
| `@Seed(path)` | member returning `String` | Written only when the file is absent, and never owned. |
| `@Delete(path)` | member returning `boolean` | Deletes a file the recipe does not own, when true. |

Rules:

- A member that returns `null` declares nothing. A pristine instance declares nothing: only what the recipe asked for
  is written.
- `path` joins to the instance's `directory`. `${property}` in a path reads the instance, for files the recipe names.
- The engine owns every format: markers in the right comment syntax, key merge that preserves unrelated keys, order
  and comments, link and mode handling, the destination boundary.
- An ingredient reads destination files as plain files. It never reads the lock or the engine's state.
- Two declarations of one target in one recipe are an error. Across recipes, the last one wins and owns the target.
- A model exposes facts for other models through plain methods, such as the files it leaves behind. The recipe wires
  them. Ingredients never call each other's outputs.

### In a recipe

| Annotation | On | Declares |
| --- | --- | --- |
| `@ingredient(Type, at)` | field | An injected instance, at the destination root or at the directory `at`. |
| `@param(names, doc, default, choices, many)` | field | An input bound from `--name=value`. Required without a default. `many` accepts repeats. `File` fields resolve against the destination. |

The body is plain Groovy: it calls ingredient methods and ends. `return` ends it with nothing declared. Everything
the injected instances declare is owned by the recipe.

## Layout

```text
ingredients/<name>/<Name>.groovy     the model or content, with README.md and resources beside it
recipes/<name>/recipe.groovy         the recipe, with README.md beside it
```

Each directory is one artifact. Skill content lives under `ingredients/skills/<name>/`.
