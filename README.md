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

The core and CLI use Kotlin, with Groovy document and filesystem adapters. Cookbook suppliers and repository recipes use Groovy. Ingredients publish their own builds and versions; recipes compose their contributions into a batch.

`./cook <recipe>` renders the desired batch into native project files. Ingredients can share separate blocks or keys in a file, while whole files and symlinks have exclusive owners. Cooking checks for managed edits and applies changes atomically. Declared applications update their own contributions; a legacy direct Batch updates its unassigned contributions while retaining independently owned applications.

```sh
./gradlew check cliBuild cookbookBuild
./cook init
./cook cookbook --policy=.agents/policy.yaml --dry-run
./cook cookbook --policy=.agents/policy.yaml
```

`./gradlew check` includes `spotlessCheck`, which enforces the format of Kotlin (ktlint), Groovy and Gradle (Groovy-Eclipse, [settings](gradle/spotless/greclipse.properties)) and plain-text files, with 2-space indentation per [`.editorconfig`](.editorconfig). `./gradlew spotlessApply` fixes violations. The Groovy formatter on Java 17 cannot parse the `!in` operator; write `!(a in b)` instead.

`./gradlew check` also requires braces around every `if`/`else`/`for`/`while` body, with the body on its own line. In Kotlin this is a custom ktlint rule ([`gradle/lint/ktlint-rules`](gradle/lint/ktlint-rules)) that Spotless runs; an `if` used as a value (`val x = if (a) b else c`, an argument, a return value, an expression body, the final `if`/`else` of a lambda) is exempt. In Groovy and Gradle, CodeNarc (`./gradlew codenarc`, [rules](gradle/lint/codenarc.groovy)) requires the braces, and Groovy-Eclipse moves braced bodies onto their own line. Violations that predate the rule are baselined: Groovy per violation in [`codenarc-baseline.xml`](gradle/lint/codenarc-baseline.xml), which `./gradlew codenarcBaseline` regenerates, and Kotlin per file in the `suppressLintsFor` list in [`build.gradle`](build.gradle). Remove a file's entry once its violations are fixed.

The combined cookbook agent setup requires a policy file and shared skills; see its [README](cookbook/recipes/cookbook/README.md). The [independent agentic flow](cookbook/recipes/agentic/README.md) provides separate init, permission, and context commands without a mandatory `cook.groovy`; [this repository's setup](docs/features/agentic-dogfood.md) uses that flow. All recipes live in `cookbook/recipes/`: `./cook <name>` runs the same factory that another recipe can compose through `book.recipe(name, kitchen, options)`. Nested names work, for example `./cook agentic/codex --instructions='Run tests before committing.'`. Each invocation updates its selected applications. Use `./cook agentic/claude --remove --dry-run` to preview removal and omit `--dry-run` to apply it. Removal reads stored ownership and does not require the policy source.

See [the core implementation](core/README.md) for ownership, contribution identities, migration and differences from the earlier design documents.

## Read next

| To learn | Read |
| --- | --- |
| Why the project exists, what it is trying to achieve, and the tenets behind every decision | [Philosophy](docs/philosophy.md) |
| The design: the parts, the commands, the requirements and the decisions behind them | [Architecture](docs/architecture.md) |

## Install

There is no published release. Build a self-contained distribution with a Java 17 JDK (including `jpackage`):

```sh
./gradlew jpackage
cli/build/jpackage/the-cook/bin/the-cook --help
```

The image includes the CLI, the packaged agentic permission, context, and init definitions with their declared dependencies, and a Java runtime. It does not fetch recipes from a remote registry. Copy the **whole** `cli/build/jpackage/the-cook` directory to another machine with the same OS and architecture; users do not need Java or Gradle installed. Run its `bin/the-cook` from the repository you want to cook:

```sh
/path/to/the-cook/bin/the-cook init
/path/to/the-cook/bin/the-cook cook
/path/to/the-cook/bin/the-cook agentic/init --harness=codex --harness=claude
```

On macOS the image is `the-cook.app` and the launcher is `Contents/MacOS/the-cook`; on Windows the launcher is `the-cook/the-cook.exe`. Build each image on its target platform, as required by [jpackage](https://docs.oracle.com/en/java/javase/17/docs/specs/man/jpackage.html). Linux is the currently verified platform. This task produces an application directory, not an OS installer.

`./gradlew jpackageTest` builds the image and tests the native launcher with Java and Gradle absent from `PATH`, including `init`, packaged and local recipe discovery, repeated options, help, child updates, removal, legacy scripts, repeat cooking, and error exit codes. This distribution check runs separately from `./gradlew check`.

For development, `./gradlew cliBuild` builds the regular JVM distribution used by this repository's `./cook` entry point; that distribution requires Java 17 on the user's machine.
