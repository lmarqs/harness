# Installation

## Objective

**Problem.** A repository needs a repeatable engine version across developer machines and CI.

**Goal.** Every machine runs the engine version the repository pins, and a reader of this repository can build and
run a local package today.

## Overview

Cook uses `jpackage` to produce a package for the target operating system and architecture: an application image
directory containing the engine, its launcher and a bundled Java runtime. A user of the complete package does not need
a separate Java installation or Gradle. The launcher depends on the rest of the package: copying only its executable
is insufficient.

Engine release packaging and cookbook distribution have separate responsibilities. Cookbooks hold and ship
[ingredients](glossary.md) (program data models) and Groovy recipes through their repositories, and can build
independently of Cook. Users manage their recipe versions and supply the version they intend to apply.

The [architecture](../architecture.md#engine-distribution) defines the intended distribution through a committed
wrapper; [intended distribution](#intended-distribution) summarizes it. The sections before it describe what this
repository builds and runs today.

## Build a local package today

The current [`cook` script](../../cook) is a development launcher: it changes into this source repository and
launches the local JVM distribution that `cliBuild` produces. That path requires a Java 17 JDK on the machine. From
the checkout root:

```sh
./gradlew cliBuild
./cook --help
```

The help output begins with `Usage:`. The [root README](../../README.md#start-from-a-fresh-clone) covers configuring
another directory with this build.

The existing [jpackage task](../../cli/gradle/jpackage.gradle) builds the package with bundled Java, using a Java 17
JDK that includes `jpackage`. To build it and inspect its help:

```sh
./gradlew jpackage
cli/build/jpackage/the-cook/bin/the-cook --help
```

Its help output also begins with `Usage:`. Copy the complete package directory to a machine with the same OS and
architecture. The build defines these platform-specific launcher paths:

| Build platform | Package directory under `cli/build/jpackage/` | Launcher within the package |
| --- | --- | --- |
| Linux | `the-cook/` | `bin/the-cook` |
| macOS | `the-cook.app/` | `Contents/MacOS/the-cook` |
| Windows | `the-cook/` | `the-cook.exe` |

Run the packaged launcher from the directory you intend to configure. See the [CLI reference](cli.md) for the
intended command grammar.

## Cookbook repositories and builds

The source repository exposes a `cookbookBuild` task separately from CLI packaging; some cookbook runtime
dependencies are also included on the CLI's packaged classpath. Cookbook authors maintain and distribute their own
models and recipes.

## Intended distribution

This section describes the intended design. The [wrapper page](wrapper.md) owns its rules.

### The wrapper

The intended repository entry point is a committed executable `./cook` wrapper that pins an engine version and
matching package checksums, then downloads, verifies and caches the pinned package on first use. See
[how it works](wrapper.md#how-it-works) for the flow and its prerequisites.

### Upgrading

An upgrade is an explicit, reviewed change to the committed version and its checksums; see
[upgrading](wrapper.md#upgrading).

## Troubleshooting

For the current development launcher, the message `Build the CLI first: ./gradlew cliBuild` means the local CLI
distribution is absent; run `./gradlew cliBuild`. A copied jpackage launcher needs its complete package, including
the bundled runtime, on a matching platform. For the intended download and cache flow, see
[wrapper troubleshooting](wrapper.md#troubleshooting).
