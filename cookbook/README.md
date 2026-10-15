# Cookbook

Design sketch. Every file under this directory is written in a Groovy-like pseudo language to show the shape of
recipes and ingredients. Nothing here runs.

## Units

A cookbook directory is one artifact holding its models, recipes, resources and one `README.md`. A model describes
how a program reads its files, or supplies content another model consumes; a recipe chooses a setup through models.
Models may depend on other models for their types and on ordinary libraries. Recipes depend on models.

A cookbook has one release version. It publishes one jar per directory to a Maven-layout repository, with a POM
for each jar and a bill of materials (BOM) listing the artifacts at that release. Each jar holds the directory's
sources, resources and README: everything a reader needs without the source checkout.

A recipe is addressed as `group:artifact:version[:recipe]`. The recipe named like its directory is the apply
default: `dev.thecook:ralph:0.1.0` applies `ralph`, while `dev.thecook:ralph:0.1.0:prd-story` applies `prd-story`.
Only exact release versions are accepted; snapshots and version ranges are refused. `cook.lock` records the
address and the jar's sha256.

A loose recipe declares `@cookbook('dev.thecook:0.1.0')` once per cookbook it uses. The declaration selects the
cookbook's BOM at that release so its model imports resolve to the listed jars. Two versions of one artifact
inside one recipe's dependency tree are an error.

A committed `cook.toml` at the destination lists the repositories to resolve from. Maven Central is the default.

Finding a recipe happens outside the engine: a cookbook's repository, an artifact host, a teammate's link. With the
coordinates in hand:

```sh
./cook dev.thecook:cook:0.1.0 --help         # the artifact's front page
./cook dev.thecook:cook:0.1.0:cook --help    # the recipe's doc comment, params and ingredients
./cook dev.thecook:cook:0.1.0
./cook dev.thecook:agents:0.1.0:permissions --harness=claude dev.thecook:agents:0.1.0:context --harness=claude
```

`--help` on `group:artifact:version` prints the artifact's front page; on `group:artifact:version:recipe` it prints
that recipe's help. Recipes named in one invocation compose left to right by file content. Each recipe is evaluated
in its own classloader. A recipe never composes another recipe.

## Names

A model's package is its cookbook's group plus its directory name: `cookbook/docker/Compose.groovy` declares
`dev.thecook.docker.Compose` in the artifact `dev.thecook:docker`. A recipe imports every type it injects, so its
imports are its visible dependency list and `@ingredient(Compose)` resolves through them.

Recipe names are noun phrases naming the state the destination gets, without repeating the directory's topic:
`node/project.groovy`, `docker/base-image.groovy` and `agents/permissions.groovy`.

The engine imports its own annotations, `groovy.json`, `groovy.yaml`, `groovy.toml` and `java.nio.file` into every unit,
as the Groovy shell imports `java.util`. A unit imports nothing from the engine and nothing for JSON, YAML, TOML or paths.

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
- A declaration member may throw to reject the request, after the guard that keeps a pristine instance declaring
  nothing.
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
| `@cookbook('group:version')` | loose recipe | The cookbook release used by its imports, once per cookbook it uses; for example, `@cookbook('dev.thecook:0.1.0')`. |
| `@ingredient(Type, at)` | field | An injected instance, at the destination root or at the directory `at`. |
| `@param(value = names, doc = '...', default = ..., choices = ..., many = ...)` | field | An input bound from `--name=value`. Required without a default. `many` accepts repeats. A `File` field is an input file in the destination, read as it was before the invocation. `doc` is required: it is the param's line in `--help`. |

A file that another recipe in the same invocation may write, such as a program's own configuration or Ralph's PRD,
is read by the ingredient that models it, through `current(path)`; the recipe passes its path as a `String`.

The body is plain Groovy: it calls ingredient methods and ends. `return` ends it with nothing declared. Everything
the injected instances declare is owned by the recipe. Call order in the body is meaningful. A method that must
follow others says so in its doc comment, as `Agents.connect` does: call it after adding skills.

## Documentation

A unit is read by someone who has its artifact and not its source. Everything they need ships in the artifact: its
README, the doc comments on its code and its annotations. `./cook <coordinates> --help` prints help and stops. An
artifact address prints its front page; an explicit recipe address prints that recipe's help. In a composed
invocation, `--help` after a recipe's arguments selects help for that address.

| Unit | `--help` prints, in order | The author writes |
| --- | --- | --- |
| Recipe | The opening doc comment; the params from their annotations, each with its name, type, default or `required`, choices and `doc`; the ingredients it injects | The opening doc comment, and a `doc` on every param |
| Model | The class doc comment; the API from the doc comments of its public members; the writes from its annotations and the doc comments of the annotated members | The class doc comment, and a doc comment on every public member and every annotated member |

A fact that lives in code is rendered from the code and written nowhere else. State a model keeps for itself is
`private`, so the rendered API is the one the author means. Every public method or property and every annotated
member has a doc comment. The engine refuses to load a unit with an undocumented param, public member or annotated
member, naming it.

A directory README is the artifact's front page: one paragraph on the topic, holding nothing that code holds.

### Recipe doc comment

A recipe file opens with a `/** */` doc comment holding these sections in order; empty sections are omitted.

| Section | Content |
| --- | --- |
| Lead | One or two sentences: what the destination gets |
| Before applying | What must exist or be installed, and what the recipe reads |
| Apply | One to three invocations showing the choices that matter; the params are not repeated |
| Writes | `Target`, `Part` and `When`: the argument value that triggers the row, or `Always` |
| After applying | What the setup changes day to day, and what to run |
| With other recipes | What it composes with, in which order, and which arguments must agree |

### Model class doc comment

A model's class doc comment holds these sections in order.

| Section | Content |
| --- | --- |
| Lead | One or two sentences: the program or content it models |
| Writes for | The programs, versions and formats the model writes for |
| Instances | What one instance covers, and when a recipe places one `at` a directory |
| Use | A recipe excerpt: the import, the injection and the typical calls |
| With other models | The facts it exposes and which models consume them |

### Doc comments

| On | Says |
| --- | --- |
| The class | The lead and model sections above |
| A public method or property | What it means for the destination, what its arguments accept and what is rejected |
| An annotated member | The part written and when, as one row of the rendered writes: `` `engines.node`, a caret range on the major, when a version is set. `` |

Rendered help for an artifact and a recipe, as the design intends it:

```text
$ ./cook dev.thecook:ralph:0.1.0 --help
Ralph TUI configuration and runners for recipe-driven development loops.
```

The artifact's front page introduces the topic. The explicit recipe address renders the recipe:

```text
$ ./cook dev.thecook:ralph:0.1.0:ralph --help
ralph

Sets up Ralph TUI as the repository's loop runner: its configuration, a runner and the tracker it takes stories
from.
...

Parameters
  --agent=<String>          The agent Ralph drives, by its Ralph plugin name          default: codex
  --tracker=<String>        Where Ralph takes its stories from                       default: json
                            choices: json, beads
  --mode=<String>           Run Ralph through its own runner at .ralph-tui/run, or    default: standalone
                            through the mise task ralph:run; choices: standalone, mise
  ...
  --issue-prefix=<String>   The Beads issue prefix, for the beads tracker             default: none

Ingredients
  dev.thecook.beads.Beads
  dev.thecook.git.Git
  dev.thecook.mise.Mise
  dev.thecook.ralph.Ralph
```

## Layout

```text
<dir>/<Model>.groovy     the model or content, in the package <group>.<dir>
<dir>/<recipe>.groovy    the recipe, opening with its doc comment
<dir>/README.md          the artifact's front page
<dir>/...               resources beside the models and recipes
cookbook.toml           the cookbook manifest at the cookbook root
```

Each topic directory is one artifact. Skill content lives under `skills/<name>/`, with `SKILL.md` and its supporting
files together.

`cookbook.toml` declares the cookbook's `group`, its release `version`, the engine API version as `api`, and any
other cookbooks it uses at exact releases. This cookbook's manifest is:

```toml
group = "dev.thecook"
version = "0.1.0"
api = 1
```
