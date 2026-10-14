# Wrapper: `cook`

## Objective

A committed wrapper makes the engine version a repository decision. Developers and CI use the same pinned
platform package, verify its checksum before execution, and upgrade explicitly. This page specifies the wrapper
required by the [intended architecture](../architecture.md#engine-distribution); the current source-repository
[`cook` script](../../cook) is a development launcher and does not implement that download flow.

## Overview

Commit an executable `./cook` wrapper with an engine version and matching platform-package checksums. It selects
the pinned package for the machine's OS and architecture. Cook remains JVM-based: the package is produced with
`jpackage` and contains Java. The wrapper caches and launches the complete package, rather than a standalone
binary that assumes a system Java installation.

The engine pin belongs in committed repository content. Destination-local `.cook/` operation history is ignored
and can be lost without preventing apply or discard; it must not be the only copy of the engine pin. This design
does not select a pin file format or cache directory and does not require an ownership manifest for the wrapper.

## How it works

1. Read the committed engine version and checksum for the target platform package.
2. If that pinned package is already cached locally, launch it.
3. Otherwise download that version's package and compare it with the committed checksum before execution.
4. Refuse to run a download whose checksum does not match; do not accept it as the cached package.
5. Cache the verified complete package, including Java, and launch it with the supplied CLI arguments.

First use of an uncached version needs access to its download source and tools for downloading, checksum
verification, and unpacking. Later use of the cached package requires no download. The cache must distinguish
platform packages as well as versions; a package built for one OS or architecture is not a substitute for another.
Exact asset names, download-source configuration, cache layout, shell prerequisites, and failure exit codes need
implementation design and are not prescribed by FR-26.

The wrapper passes cooking arguments to the engine. The [CLI reference](cli.md) defines apply, discard, revert,
recipe operands, and batch options. This wrapper design adds no management command or hook API. Its executable
location does not change the invocation directory's role as the destination for declared file operations.

## One pin

The committed version and platform checksums identify the engine packages the repository intends to run. A
cached package is reused for that pin, rather than replaced with whichever release is newest. Review changes to
the wrapper and pin as executable code and dependency changes.

A checksum detects whether a download matches the reviewed pin. It does not by itself authenticate a publisher.
FR-26 requires checksum pinning; it does not specify signing keys, signed checksum files, key rotation, or a
signature-verification command. This page does not promise those mechanisms.

## Upgrading

Upgrade by explicitly changing the committed version and its corresponding package checksums together. Review
the pin change; on the next use, obtain the new package through the same verified download/cache flow. An ordinary
invocation does not move the pin or select a newer engine release.

Changing the engine pin does not manage recipe versions, fetch cookbook updates, or automatically re-render project
files. Cookbooks ship ingredients and recipes through their repositories and can have separate builds. Users supply
the recipe version to apply or discard. The wrapper introduces no registry, recipe-version manager, or updater.

## CI

A CI job that invokes Cook uses the repository's committed wrapper and pin through the same verification flow as a
developer. A fresh machine needs the pinned package downloaded; a machine with that package cached can reuse it.
This design adds no mandatory CI job or check command. Configured programs consume their native files directly
without invoking Cook merely to read those files.

## Air-gapped machines

A locally cached pinned package can run without downloading again. A machine without the complete package needs
it transferred from elsewhere, with its checksum verified against the committed pin. Transfer the package including
Java and use one built for that machine's OS and architecture. No cache-seeding command or pin-bypass environment
variable is specified here. Without the package or access to obtain it, the wrapper cannot run the engine.

## Security

Review the wrapper, engine version, and checksums together. A checksum mismatch prevents the downloaded package
from running. Protect cached executable packages from unintended changes. Package verification is separate from
recipe trust: Groovy recipes are executable code, and batch approval is not a sandbox for recipe side effects.

## Current repository launcher

The existing [`cook` script](../../cook) invokes `cli/build/install/the-cook/bin/the-cook` after changing into the
source repository. If that local distribution is absent, it asks the developer to build it with `cliBuild`. It has
no committed release pin, checksum verifier, downloader, or release cache. The [installation page](installation.md#build-a-local-package-today)
describes the existing self-contained jpackage build. Neither the script nor that build provides a published-release
installation path or demonstrates a working pinned-download wrapper.

## Troubleshooting

| Situation | Required response |
| --- | --- |
| An uncached package cannot be downloaded | Obtain the pinned platform package before running the engine; a cached complete package can be used offline |
| A download does not match its checksum | Refuse execution and resolve the download or pin discrepancy; do not silently accept a different package |
| A copied launcher cannot find its runtime | Transfer the complete jpackage image, including Java |
| The current repository script requests a CLI build | Follow the local development build path; it does not fetch a release |
