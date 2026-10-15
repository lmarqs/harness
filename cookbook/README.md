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

## Names

An ingredient is declared in a package that starts with its cookbook's group: `dev.thecook.Compose` is the Compose of
the `dev.thecook` cookbook. A recipe imports each type it injects, so its imports are its visible dependency list and
`@ingredient(Compose)` resolves through them. Two ingredients of one cookbook cannot share a simple name, which is what
turns `Compose` into `DockerCompose` the day a second one appears.

The engine imports its own annotations, `groovy.json`, `groovy.yaml` and `java.nio.file` into every unit, as the
Groovy shell imports `java.util`. A unit imports nothing from the engine and nothing for JSON, YAML or paths.

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
| `@param(names, doc, default, choices, many)` | field | An input bound from `--name=value`. Required without a default. `many` accepts repeats. A `File` field is an input file in the destination, read as it was before the invocation. `doc` is required: it is the param's line in `--help`. |

A file that another recipe in the same invocation may write, such as a program's own configuration or Ralph's PRD,
is read by the ingredient that models it, through `current(path)`; the recipe passes its path as a `String`.

The body is plain Groovy: it calls ingredient methods and ends. `return` ends it with nothing declared. Everything
the injected instances declare is owned by the recipe.

## Documentation

A unit is read by someone who has its artifact and not its source. Everything they need ships in the artifact: its
README, the doc comments on its code and its annotations. `./cook <coordinates> --help` prints them and stops. In a
composed invocation, `--help` after a recipe's arguments prints that recipe's.

| Unit | `--help` prints, in order | The author writes |
| --- | --- | --- |
| Recipe | The README; the params from `@param`, each with its type, default or `required`, choices and `doc`; the ingredients it injects | The README, and a `doc` on every param |
| Ingredient | The README; the API from the doc comments of its public members; the writes from its annotations and the doc comments of the annotated members | The README, and a doc comment on every public member and every annotated member |

A fact that lives in code is rendered from the code and written nowhere else. State a model keeps for itself is
`private`, so the rendered API is the one the author means.

### Recipe README

Sections in this order; an empty one is omitted.

| Section | Content |
| --- | --- |
| Title and lead | One or two sentences: what the destination gets |
| Before applying | What must exist or be installed, and what the recipe reads |
| Apply | One to three invocations showing the choices that matter; the params are not repeated |
| Writes | `Target`, `Part` and `When`: the argument value that triggers the row, or `Always` |
| After applying | What the setup changes day to day, and what to run |
| With other recipes | What it composes with, in which order, and which arguments must agree |

### Ingredient README

| Section | Content |
| --- | --- |
| Title and lead | One or two sentences: the program or content it models |
| Writes for | The programs, versions and formats the model writes for |
| Instances | What one instance covers, and when a recipe places one `at` a directory |
| Use | A recipe excerpt: the import, the injection and the typical calls |
| With other models | The facts it exposes and which models consume them |

### Doc comments

| On | Says |
| --- | --- |
| The class | What it models, in one sentence; the heading of the rendered API |
| A public method or property | What it means for the destination, what its arguments accept and what is rejected |
| An annotated member | The part written and when, as one row of the rendered writes: `` `engines.node`, a caret range on the major, once a version was set. `` |

Rendered help for a recipe, as the design intends it:

```text
$ ./cook dev.thecook:ralph --help
ralph

Sets up Ralph TUI as the repository's loop runner: its configuration, a runner and the tracker it takes stories
from.
...

Parameters
  --agent=<value>           The agent Ralph drives, by its Ralph plugin name          default: codex
  --tracker=json|beads      Where Ralph takes its stories from                       default: json
  --mode=standalone|mise    Run Ralph through its own runner at .ralph-tui/run, or    default: standalone
                            through the mise task ralph:run
  --issue-prefix=<value>    The Beads issue prefix, for the beads tracker            default: none

Ingredients
  dev.thecook: Beads, Git, Mise, Ralph
```

## Layout

```text
ingredients/<name>/<Name>.groovy     the model or content in the cookbook's package, with README.md and resources beside it
recipes/<name>/recipe.groovy         the recipe, with README.md beside it
```

Each directory is one artifact. Skill content lives under `ingredients/skills/<name>/`, with the skill's own files under
`skill/`.
