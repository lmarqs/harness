# the-cook

<p align="center"><img src="docs/assets/the-cook.png" alt="the-cook: pixel-art portrait of a man in a black pork-pie hat lowering dark sunglasses" width="640"></p>

**The cook: cook the best product without any source of contamination.**

## Objective

**Problem.** Teams reuse setup across repositories: agent instructions and permissions, editor settings, ignore rules,
build and CI files. Copied by hand, the copies drift. Those files are shared: a setup that replaces one whole loses
what teammates and other tools wrote in it, and nobody can tell which part came from where or remove a part cleanly.

**Goal.** A setup learned once reaches every repository through one reviewed change, lives beside hand-written content
in the same files, and is withdrawn the same way. Contributors who do not run Cook need nothing to contribute: their
programs read the native files directly, and the lock and markers Cook leaves are reviewed like any other change.

## Current implementation

This section describes what runs on `main`. The [architecture](docs/architecture.md) defines the intended design, and
[issue #15](https://github.com/lmarqs/the-cook/issues/15) tracks implementing it.

### Quick start

With a Java 17 JDK and access to obtain Gradle and build dependencies, from the checkout root, build the CLI and
publish the cookbook to `cookbook/build/repository`:

```sh
./gradlew cliBuild :cookbook:publish
```

Read the `agents` artifact's front page and one of its recipes, without running anything:

```sh
./cook help dev.thecook:agents:0.1.0
./cook help dev.thecook:agents:0.1.0:instructions
```

Make a scratch kitchen whose `cook.toml` resolves from this checkout's repository, then cook two recipes in it:

```sh
kitchen=$(mktemp -d)
printf 'repositories = ["%s/cookbook/build/repository"]\n' "$PWD" > "$kitchen/cook.toml"
./cook -C "$kitchen" dev.thecook:agents:0.1.0:permissions --harness=claude dev.thecook:agents:0.1.0:instructions --harness=claude
```

Cook shows the diff of every file it would write, `cook.lock` included, and asks `Write these changes? [y/N]`. Answer
`y`: it prints `Created` for `.agents/policy.yaml`, `.claude/settings.json`, `AGENTS.md`, `CLAUDE.md` and `cook.lock`,
then what to do next. Run the same command again: nothing changed, so it prints `Already cooked.` and asks nothing.

The repository's [`cook` script](cook) launches the local distribution, and the directory it runs from is the kitchen
Cook cooks in. `-C <dir>` or `--kitchen=<dir>` before the first recipe cooks in `<dir>` as if Cook had started there.
`./cook --help` prints the usage.

### What runs

`./cook` cooks recipes left to right in one invocation, each named by coordinates,
`group:artifact:version[:recipe]`, or by a `.groovy` path inside the kitchen:

- One review before any write: a `Changed source` notice for a release whose sources differ from what `cook.lock`
  recorded under the same version, each recipe's `@confirm` text, a unified diff and one question; `--batch-approve` to
  write without asking, and nothing written without a terminal unless it is given
  ([batch options and approval](docs/features/cli.md#batch-options-and-approval)).
- One line per changed path after the writes, `Would` lines under `--dry-run`, then each recipe's `@yield` text
  ([recipe arguments and ordering](docs/features/cli.md#recipe-arguments-and-ordering)).
- `--force` and `--skip` for a file a recipe writes from a template the first time it is cooked
  ([existing files](docs/features/cli.md#existing-files)).
- `cook.lock`, which the kitchen keeps and commits: each recipe's short name, source, the source's sha256 and what it
  owns, so a later run withdraws what a recipe no longer declares
  ([the lock](docs/architecture.md#ownership-and-the-lock)).
- A failed write rolls back the writes already made, so the files are as they were, and prints the error.
- `./cook discard [--dry-run] [--batch-approve] <recipe>...`, which removes what `cook.lock` lists for each recipe,
  without running it, through the same review: each section with its markers, each key, each whole file and link,
  deleting a file left empty, and the recipe's lock entry ([discard](docs/features/discard.md)).
- `help` on an artifact, a recipe or a recipe path, read without running it.

Coordinates resolve from the first repository of the kitchen's `cook.toml` `repositories` list that holds them, and
from Maven Central when the kitchen has no `cook.toml`. An entry is a Maven-layout repository of jars, an `https://` URL
or a path, where `~/` starts from the user's home as in `~/.m2/repository`, or a git repository,
`{ git = "<url>", dir = "<dir>" }`, read at the release tag with the machine's `git` ([cookbook](cookbook/README.md)).
Arguments follow their recipe as `--name=value` or `--name value`, and a yes-or-no argument as a bare `--name` or
`--name=false`. A word that contains `/` or `:` or ends with `.groovy` starts the next recipe, so a value shaped like
one is written `--name=value`.

A URL is not a recipe operand; a recipe shared beyond one repository is published in a cookbook and cooked by its
coordinates ([a cookbook outside this repository](cookbook/README.md#a-cookbook-outside-this-repository)).

### Recipe paths

A `.groovy` path inside the kitchen is a recipe like any other. Its short name, in section markers and `cook.lock`, is
its path in the kitchen without `.groovy`, so moving the file renames the recipe; the lock records its path and the
file's sha256. It imports models from the cookbook releases it names with `@cookbook('group:version')` on one of its
imports, resolved through the same repositories as coordinates:

```groovy
@help('Shares the solid skill with Claude, through AGENTS.md and CLAUDE.md.')
@cookbook('dev.thecook:0.1.0')
import dev.thecook.agents.Agents
import dev.thecook.agents.Claude

@ingredient(Agents) def agents
@ingredient(Claude) def claude

agents.skills('solid')
agents.connect([claude])
```

A path outside the kitchen, through a symbolic link too, is refused, as are two releases of one cookbook.

Of the [cookbook](cookbook/README.md), the `agents` artifact runs; the other directories are a design sketch that has
not been compiled. `./gradlew :cookbook:publish` packages the cookbook with the `dev.thecook.cookbook` Gradle plugin,
built from [`plugin`](plugin) apart from the engine: a jar and a POM per directory and the `dev.thecook:cookbook` BOM.
Packaging compiles nothing; the engine compiles an artifact's sources when one of its recipes is cooked.

### Development tooling

`./gradlew check` includes `spotlessCheck`, which enforces the format of Groovy and Gradle (Groovy-Eclipse, [settings](gradle/spotless/greclipse.properties)) and plain-text files, with 2-space indentation per [`.editorconfig`](.editorconfig). `./gradlew spotlessApply` fixes violations. The Groovy formatter on Java 17 cannot parse the `!in` operator or a GString nested inside another; write `!(a in b)` and build such strings with `+`.

`./gradlew check` also lints Groovy and Gradle files with CodeNarc (`./gradlew codenarc`), using its `basic`, `braces`,
`imports` and `unused` rulesets ([rules](gradle/lint/codenarc.groovy)). Every `if`/`else`/`for`/`while` body has braces,
and Groovy-Eclipse moves braced bodies onto their own line. Spotless's Groovy step and CodeNarc skip `cookbook/`, most
of which is a design sketch that has not been compiled.

`docs/` is its own build. `./gradlew -p docs check`, which `./gradlew check` includes, fails on a relative link in
`docs/` that points to a missing file or heading anchor; [`docs/build.gradle`](docs/build.gradle) defines the check.

See [the core implementation](core/README.md) for how the engine cooks a recipe and how it differs from the
intended architecture.
[Infra](infra/README.md) reads the kitchen's `cook.toml` and fetches cookbook releases from Maven-layout and git
repositories; the CLI hands its repositories to the core.

## Read next

| To learn | Read |
| --- | --- |
| Why the project exists, what it is trying to achieve, and the tenets behind every decision | [Philosophy](docs/philosophy.md) |
| The intended design: responsibilities, operations, commands and distribution | [Architecture](docs/architecture.md) |

## Install

There is no published release. The [jpackage task](cli/gradle/jpackage.gradle) builds a self-contained distribution
with a Java 17 JDK (including `jpackage`):

```sh
./gradlew jpackage
cli/build/jpackage/the-cook/bin/the-cook --help
```

Its help output also begins with `Usage:`. The image includes the CLI and a Java runtime. Copy the **whole**
`cli/build/jpackage/the-cook` directory to another machine with the same OS and architecture. Run its `bin/the-cook`
from the repository you want to cook, or name it with `-C`:

```sh
/path/to/the-cook/bin/the-cook -C /path/to/repository dev.thecook:agents:0.1.0:instructions --harness=claude
```

The paths above are for Linux. The build defines `the-cook.app/Contents/MacOS/the-cook` on macOS and
`the-cook/the-cook.exe` on Windows. Build on the target OS and architecture. This task produces an application
directory. The Windows path in the build configuration does not establish verified Windows support.

`./gradlew jpackageTest` builds the image and tests the native launcher with Java and Gradle absent from `PATH`: help, cooking coordinates and a recipe path in one invocation, the refusal to write without a terminal, a repeated run, `-C` and a usage error. This distribution check runs separately from `./gradlew check`.

For development, `./gradlew cliBuild` builds the regular JVM distribution used by this repository's `./cook` entry point; that distribution requires Java 17 on the user's machine.
