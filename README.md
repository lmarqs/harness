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

The core and CLI use Kotlin, with Groovy document and filesystem adapters. Cookbook suppliers and repository recipes use Groovy. Ingredients publish their own builds and versions; recipes compose their contributions into a batch.

`./cook <recipe>` renders the desired batch into native project files. Ingredients can share separate blocks or keys in a file, while whole files and symlinks have exclusive owners. Cooking checks for managed edits and uses a transaction journal with rollback on write failure. Declared applications update their own contributions; a legacy direct Batch updates its unassigned contributions while retaining independently owned applications.

The current CLI resolves cookbook names or local `.groovy` paths. `--dry-run` reports proposed changed paths; a normal
cooking invocation writes without an approval prompt or a terminal content diff. `--remove` withdraws selected
applications using ownership records. The existing `apply` command applies a saved plan. No-argument invocation
reports a missing command; use `--help` explicitly. Some recipes expose `--adopt`, and the core enforces adoption and
managed-edit gates.

### Start from a fresh clone

With a Java 17 JDK and access to obtain Gradle and build dependencies, build the local CLI and inspect its help from
the checkout root:

```sh
./gradlew cliBuild
./cook --help
./cook agentic/permission --help
```

The help output begins with `Usage:`. The repository's [`cook` script](cook) launches the local distribution after
changing into this checkout. To configure another directory, invoke `cli/build/install/the-cook/bin/the-cook` by its
absolute path from that directory, or use its `--repo` option.

### Development tooling

`./gradlew check` includes `spotlessCheck`, which enforces the format of Kotlin (ktlint), Groovy and Gradle (Groovy-Eclipse, [settings](gradle/spotless/greclipse.properties)) and plain-text files, with 2-space indentation per [`.editorconfig`](.editorconfig). `./gradlew spotlessApply` fixes violations. The Groovy formatter on Java 17 cannot parse the `!in` operator; write `!(a in b)` instead.

`./gradlew check` also requires braces around every `if`/`else`/`for`/`while` body, with the body on its own line. In Kotlin this is a custom ktlint rule ([`gradle/lint/ktlint-rules`](gradle/lint/ktlint-rules)) that Spotless runs; an `if` used as a value (`val x = if (a) b else c`, an argument, a return value, an expression body, the final `if`/`else` of a lambda) is exempt. In Groovy and Gradle, CodeNarc (`./gradlew codenarc`, [rules](gradle/lint/codenarc.groovy)) requires the braces, and Groovy-Eclipse moves braced bodies onto their own line. Violations that predate the rule are baselined: Groovy per violation in [`codenarc-baseline.xml`](gradle/lint/codenarc-baseline.xml), which `./gradlew codenarcBaseline` regenerates, and Kotlin per file in the `suppressLintsFor` list in [`build.gradle`](build.gradle). Remove a file's entry once its violations are fixed.

`docs/` is its own build. `./gradlew -p docs check`, which `./gradlew check` includes, fails on a relative link in
`docs/` that points to a missing file or heading anchor; [`docs/build.gradle`](docs/build.gradle) defines the check.

### Recipe prerequisites

The combined cookbook agent setup requires a `--policy` path and shared skills; see its [README](cookbook/recipes/cookbook/README.md).
It can seed an absent policy inside the destination, but its skill source must already contain `solid/SKILL.md`,
`hexagonal-layout/SKILL.md` and `effective-java-core/SKILL.md`. That source defaults to `~/.agents/skills` and can be
selected with `--skills-home`. Those personal files are not supplied by a fresh clone, and `.agents/policy.yaml` is
not a tracked starter policy. Inspect the prerequisites before invoking the combined setup.

The [independent agentic flow](cookbook/recipes/agentic/README.md) provides separate init, permission, and context
commands; [this repository's setup](docs/features/agentic-dogfood.md) uses that flow. Repository recipe sources live
in `cookbook/recipes/`, with packaged definitions available on the CLI classpath: `./cook <name>` runs the same
factory that another recipe can compose through `book.recipe(name, kitchen, options)`. Nested names work, for example
`./cook agentic/codex --instructions='Run tests before committing.'`. Each invocation updates its selected
applications. Use `./cook agentic/permission --harness=claude --remove --dry-run` to preview removal and omit
`--dry-run` to apply it. Removal reads stored ownership.

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

Its help output also begins with `Usage:`. The image includes the CLI, the packaged agentic permission, context, and
init definitions with their declared dependencies, and a Java runtime. Copy the **whole** `cli/build/jpackage/the-cook`
directory to another machine with the same OS and architecture. Run its `bin/the-cook` from the repository you want to
cook:

```sh
/path/to/the-cook/bin/the-cook init
/path/to/the-cook/bin/the-cook agentic/init --help
/path/to/the-cook/bin/the-cook agentic/init --harness=codex --harness=claude
```

The builtin `init` creates `cook.groovy` and preserves an existing regular file. Inspect the packaged recipe's help and
[init guide](cookbook/recipes/agentic/init/README.md) before writing agent files. `the-cook cook` evaluates the
destination's `cook.groovy`; the script and any sources it requires must be available.

The paths above are for Linux. The build defines `the-cook.app/Contents/MacOS/the-cook` on macOS and
`the-cook/the-cook.exe` on Windows. Build on the target OS and architecture. This task produces an application
directory. The Windows path in the build configuration does not establish verified Windows support.

`./gradlew jpackageTest` builds the image and tests the native launcher with Java and Gradle absent from `PATH`, including `init`, packaged and local recipe discovery, repeated options, help, child updates, removal, legacy scripts, repeat cooking, and error exit codes. This distribution check runs separately from `./gradlew check`.

For development, `./gradlew cliBuild` builds the regular JVM distribution used by this repository's `./cook` entry point; that distribution requires Java 17 on the user's machine.
