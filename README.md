# the-cook

<p align="center"><img src="docs/assets/the-cook.png" alt="the-cook: pixel-art portrait of a man in a black pork-pie hat lowering dark sunglasses" width="640"></p>

**The cook: cook the best product without any source of contamination.**

## Objective

**Problem.** Teams share repositories, and each person works with their own programs: coding agents, loop runners,
trackers and editors. Those programs leave their files in the repository. One person's setup gets committed, and every
teammate inherits it. A way of working learned in one repository reaches the others only by copying it by hand, and the
copies drift.

**Goal.** A way of working learned once reaches every repository through one reviewed change, and is withdrawn the same
way. Everyone keeps their own way of working, and when they are done only the work remains. People who do not use
the tool never notice it.

## Current implementation

This section describes the repository's executable behavior. The [architecture](docs/architecture.md) defines the
intended design, and [issue #15](https://github.com/lmarqs/the-cook/issues/15) tracks implementing it.

The core and CLI use Kotlin, with Groovy document and filesystem adapters. Local recipes for the current core are Groovy scripts. The [cookbook](cookbook/README.md) shows how recipes and ingredients are written and distributed. Its `agents` artifact runs: `./cook` applies its recipes by coordinates. The other directories are a design sketch that has not been compiled, and the CLI ships without packaged recipes. `./gradlew :cookbook:publish` packages the cookbook with the `dev.thecook.cookbook` Gradle plugin, built from [`plugin`](plugin) apart from the engine, into the Maven-layout repository at `cookbook/build/repository`: a jar and a POM per directory and the `dev.thecook:cookbook` BOM. Packaging compiles nothing; the engine compiles an artifact's sources when one of its recipes is applied.

`./cook <recipe>` renders the desired batch into native project files. Ingredients can share separate blocks or keys in a file, while whole files and symlinks have exclusive owners. Cooking checks for managed edits and uses a transaction journal with rollback on write failure. Declared applications update their own contributions; a legacy direct Batch updates its unassigned contributions while retaining independently owned applications.

The current CLI resolves local `.groovy` recipe paths. Cook's options go before the recipe and the recipe's own
arguments follow it, as in `./cook --dry-run recipe.groovy --harness=claude`; [picocli](https://picocli.info) parses
Cook's grammar. `--dry-run` reports proposed changed paths; a normal cooking invocation writes without an approval
prompt or a terminal content diff. `--remove` withdraws selected applications using ownership records. The existing `apply` command applies a saved plan. No-argument invocation reports a missing
command; use `--help` or `help` explicitly. `help <recipe>...` prints each recipe's help; for a recipe whose first
import carries `@help`, such as `./cook help cookbook/agents/instructions.groovy`, the help is read from its source
without running it. `help` also takes coordinates, `group:artifact:version[:recipe]`, and reads the recipe, or
on `group:artifact:version` the artifact's README with its recipes and models, from the artifact's jar. The jar comes
from the first repository holding it in the `repositories` list of the kitchen's `cook.toml`, each an `https://` URL
or a path relative to the kitchen, and from Maven Central when the kitchen has no `cook.toml`. This repository's
`cook.toml` lists `cookbook/build/repository`, so `./gradlew :cookbook:publish` followed by
`./cook help dev.thecook:agents:0.1.0:instructions` reads the packaged recipe.

`./cook` applies coordinates through its own path in the engine, with `cook.lock` as its record. Each recipe after
the first starts at a word without `--`, and its arguments are written `--name=value`, as in
`./cook dev.thecook:agents:0.1.0:permissions --harness=claude dev.thecook:agents:0.1.0:instructions --harness=claude`.
Recipes run left to right against one in-memory draft, so each sees what the ones before it wrote. A recipe removes
what it owned and no longer declares, and the last recipe to write a section, key, file or link owns it. `cook.lock`
records each recipe's short name, coordinates, the jar's sha256 and what it owns. Cook prints the changed paths, or
`Already cooked.`, then each recipe's `@yield` text after the writes; `--dry-run` reports the paths and writes
nothing. A `.groovy` path and coordinates are applied in separate invocations, and `--remove` and `--library` take a
`.groovy` path. The work that remains on this path is tracked in
[issue #15](https://github.com/lmarqs/the-cook/issues/15).

The core enforces adoption and managed-edit gates for `.groovy` recipe paths.

### Start from a fresh clone

With a Java 17 JDK and access to obtain Gradle and build dependencies, build the local CLI and inspect its help from
the checkout root:

```sh
./gradlew cliBuild
./cook --help
```

The help output begins with `Usage:`. The repository's [`cook` script](cook) launches the local distribution, and the
directory it runs from is the kitchen Cook cooks in. `-C <dir>` or `--kitchen=<dir>` before the first recipe cooks in
`<dir>` as if Cook had started there, and relative paths resolve against it.

### Development tooling

`./gradlew check` includes `spotlessCheck`, which enforces the format of Kotlin (ktlint), Groovy and Gradle (Groovy-Eclipse, [settings](gradle/spotless/greclipse.properties)) and plain-text files, with 2-space indentation per [`.editorconfig`](.editorconfig). `./gradlew spotlessApply` fixes violations. The Groovy formatter on Java 17 cannot parse the `!in` operator; write `!(a in b)` instead.

`./gradlew check` also requires braces around every `if`/`else`/`for`/`while` body, with the body on its own line. In Kotlin this is a custom ktlint rule ([`gradle/lint/ktlint-rules`](gradle/lint/ktlint-rules)) that Spotless runs; an `if` used as a value (`val x = if (a) b else c`, an argument, a return value, an expression body, the final `if`/`else` of a lambda) is exempt. In Groovy and Gradle, CodeNarc (`./gradlew codenarc`, [rules](gradle/lint/codenarc.groovy)) requires the braces, and Groovy-Eclipse moves braced bodies onto their own line. Violations that predate the rule are baselined: Groovy per violation in [`codenarc-baseline.xml`](gradle/lint/codenarc-baseline.xml), which `./gradlew codenarcBaseline` regenerates, and Kotlin per file in the `suppressLintsFor` list in [`build.gradle`](build.gradle). Remove a file's entry once its violations are fixed. Spotless's Groovy step and CodeNarc skip `cookbook/`, most of which is a design sketch that has not been compiled.

`docs/` is its own build. `./gradlew -p docs check`, which `./gradlew check` includes, fails on a relative link in
`docs/` that points to a missing file or heading anchor; [`docs/build.gradle`](docs/build.gradle) defines the check.

See [the core implementation](core/README.md) for ownership, contribution identities, migration and differences from
the intended architecture.

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
from the repository you want to cook:

```sh
/path/to/the-cook/bin/the-cook init
```

The builtin `init` creates `cook.groovy` and preserves an existing regular file. `the-cook cook` evaluates the
destination's `cook.groovy`; the script and any sources it requires must be available.

The paths above are for Linux. The build defines `the-cook.app/Contents/MacOS/the-cook` on macOS and
`the-cook/the-cook.exe` on Windows. Build on the target OS and architecture. This task produces an application
directory. The Windows path in the build configuration does not establish verified Windows support.

`./gradlew jpackageTest` builds the image and tests the native launcher with Java and Gradle absent from `PATH`, including `init`, local recipe discovery, repeated options, help, child updates, removal, legacy scripts, repeat cooking, and error exit codes. This distribution check runs separately from `./gradlew check`.

For development, `./gradlew cliBuild` builds the regular JVM distribution used by this repository's `./cook` entry point; that distribution requires Java 17 on the user's machine.
