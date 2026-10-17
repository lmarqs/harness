# Wrapper: `cook`

## Objective

**Problem.** Developers and CI need to run the same engine version, and a download must match the version the
repository reviewed before it runs.

**Goal.** The engine version is a repository decision: every machine runs the pinned platform package, verifies its
checksum before execution, and upgrades only through an explicit, reviewed change.

This page owns the `./cook` wrapper of the [intended architecture](../architecture.md#engine-distribution): its pin,
checksum verification, first-use download, cache and upgrades. The current source-repository
[`cook` script](../../cook) is a development launcher for a local build; see
[current repository launcher](#current-repository-launcher).

## Overview

A repository commits an executable `./cook` [wrapper](glossary.md) with an engine version and matching platform-package
checksums. It selects the pinned package for the machine's OS and architecture. Cook is JVM-based: the package is
produced with `jpackage` and contains Java. The wrapper caches and launches the complete package.

The engine pin belongs in committed repository content, separate from [`cook.lock`](glossary.md), which records
recipe ownership.

## How it works

1. Read the committed engine version and checksum for the target platform package.
2. If that pinned package is already cached locally, launch it.
3. Otherwise download that version's package and compare it with the committed checksum before execution.
4. Refuse to run a download whose checksum does not match; do not accept it as the cached package.
5. Cache the verified complete package, including Java, and launch it with the supplied CLI arguments.

First use of an uncached version needs access to its download source and tools for downloading, checksum
verification, and unpacking. Later use of the cached package requires no download. The cache must distinguish
platform packages as well as versions; a package built for one OS or architecture is not a substitute for another.
The specific tools, exact asset names, download-source configuration, pin file format, cache layout, shell
prerequisites, and failure exit codes belong to the implementation.

The wrapper passes its CLI arguments to the engine. The [CLI reference](cli.md) defines cooking, discard and help, recipe
operands, and batch options. The invocation directory is the destination for declared file
operations, regardless of the wrapper's location.

## One pin

The committed version and platform checksums identify the engine packages the repository intends to run. A
cached package is reused for that pin. Review changes to the wrapper and pin as executable code and dependency
changes.

A checksum detects whether a download matches the reviewed pin. It does not by itself authenticate a publisher.
[FR-26](../architecture.md#requirements) requires checksum pinning.

## Upgrading

Upgrade by explicitly changing the committed version and its corresponding package checksums together. Review
the pin change; on the next use, obtain the new package through the same verified download/cache flow. An ordinary
invocation does not move the pin or select a newer engine release.

Changing the engine pin does not manage recipe versions, fetch cookbook updates, or automatically cook recipes into
project files. Cookbooks ship ingredients and recipes through their repositories and can have separate builds. Users
supply the recipe version to cook.

## CI

A CI job that invokes Cook uses the repository's committed wrapper and pin through the same verification flow as a
developer. A fresh machine needs the pinned package downloaded; a machine with that package cached can reuse it.
Configured programs consume their native files directly.

## Air-gapped machines

A locally cached pinned package can run without downloading again. A machine without the complete package needs
it transferred from elsewhere, with its checksum verified against the committed pin. Transfer the package including
Java and use one built for that machine's OS and architecture. Without the package or access to obtain it, the
wrapper cannot run the engine.

## Security

Review the wrapper, engine version, and checksums together. A checksum mismatch prevents the downloaded package
from running. Protect cached executable packages from unintended changes. Package verification is separate from
recipe trust: Groovy recipes are executable code, and batch approval is not a sandbox for recipe side effects.

## Current repository launcher

The existing [`cook` script](../../cook) invokes `cli/build/install/the-cook/bin/the-cook` after changing into the
source repository. If that local distribution is absent, it prints `Build the CLI first: ./gradlew cliBuild`. The
root README's [install section](../../README.md#install) describes the existing self-contained jpackage build.

## Troubleshooting

| Situation | Required response |
| --- | --- |
| An uncached package cannot be downloaded | Obtain the pinned platform package; a cached complete package runs offline |
| A download does not match its checksum | Refuse execution; resolve the download or pin discrepancy |
| A copied launcher cannot find its runtime | Transfer the complete jpackage image, including Java |
| The current repository script prints `Build the CLI first: ./gradlew cliBuild` | The local CLI distribution is absent; run `./gradlew cliBuild`; see the [quick start](../../README.md#quick-start) |
