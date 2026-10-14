# Installation

## Objective

A repository needs a repeatable engine version across developer machines and CI. The chosen delivery mechanism
is a platform-specific JVM package with Java included, reached through a committed wrapper that pins the version
and verifies its download. This page describes the [intended architecture](../architecture.md#engine-distribution),
then gives the local packaging path supported by the current repository.

## Overview

Cook uses `jpackage` to produce a package for the target operating system and architecture. The package contains
the engine and a bundled Java runtime. A user of the complete package does not need a separate Java installation
or Gradle. The launcher depends on the rest of the package: copying only its executable is insufficient.

Engine release packaging and cookbook distribution have separate responsibilities. Cookbooks hold and ship
program data models (ingredients) and Groovy recipes through their repositories, and can build independently of
Cook. The engine's version pin does not select or upgrade cookbook content. Users manage their recipe versions
and supply the version they intend to apply; Cook does not introduce a registry or recipe-version manager.

## The wrapper (default)

The intended repository entry point is a committed executable `./cook` wrapper. It pins an engine version and
matching checksums for the platform packages the repository uses. On first use on a machine, it downloads the
pinned package, verifies the download against its committed checksum before execution, and caches the complete
package locally. Later invocations use the cached package without downloading it again.

The machine needs the wrapper's execution environment and, for an uncached package, network access, a downloader,
a checksum verifier, and tools to unpack the package. Java comes from the package. The exact wrapper tooling,
pin representation, cache paths, release asset layout, and supported release platform matrix are not specified here.
See [wrapper](wrapper.md#how-it-works) for the required flow.

## Upgrading

An upgrade explicitly changes the committed engine version and its matching platform checksums. Review those
changes together before using the new pin. Its first invocation downloads and caches the new package through the
same verification flow. Ordinary invocations continue to use the pinned version; they do not discover or adopt a
newer release automatically.

This design does not add an updater command, release-notice service, schema migration workflow, or automatic
re-rendering on a pin change. Cookbook changes remain separate from engine upgrades.

## Build a local package today

The repository does not provide a published-release installation path or a working pinned-download wrapper.
The current [`cook` script](../../cook) launches the local JVM distribution built by `cliBuild`; it neither pins
nor downloads a release and changes into this source repository before launching. That development path requires
Java 17 on the machine. It is not the target wrapper described above.

The existing [jpackage task](../../cli/gradle/jpackage.gradle) builds an application image with bundled Java,
using a Java 17 JDK that includes `jpackage`. To build and inspect its help:

```sh
./gradlew jpackage
cli/build/jpackage/the-cook/bin/the-cook --help
```

The current task produces an application directory, rather than an OS installer. Copy the complete image to a machine with the same
OS and architecture. The build defines these platform-specific launcher paths:

| Build platform | Image under `cli/build/jpackage/` | Launcher within the image |
| --- | --- | --- |
| Linux | `the-cook/` | `bin/the-cook` |
| macOS | `the-cook.app/` | `Contents/MacOS/the-cook` |
| Windows | `the-cook/` | `the-cook.exe` |

Run the packaged launcher from the directory you intend to configure. The build definitions establish packaging
paths, not availability of published packages for those platforms. See the [CLI design](cli.md) for the intended
cooking grammar; building the current image does not establish that grammar or the target operation behavior is
implemented.

## Cookbook repositories and builds

The source repository currently exposes a `cookbookBuild` task separately from CLI packaging; some cookbook
runtime dependencies are also included on the CLI's packaged classpath. That build arrangement does not make the
engine release responsible for versioning every cookbook. Cookbook authors maintain and distribute their own
models and recipes. Neither downloading an engine package nor changing its pin supplies or selects a recipe
version.

## Troubleshooting

For the intended download/cache flow, see [wrapper troubleshooting](wrapper.md#troubleshooting). For the current
development launcher, a message asking for `./gradlew cliBuild` means the local CLI distribution is absent. A copied
jpackage launcher needs its complete image, including the bundled runtime, on a matching platform.
