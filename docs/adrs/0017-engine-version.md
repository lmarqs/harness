# 0017. Each repository pins its engine release through a committed wrapper

## Objective

**Problem.** Developers and CI can run different engine releases against the same repository. Changes to recipe
evaluation or file operations then depend on which release a machine happens to have, and a newcomer must find and
install the repository's chosen version before running recipes.

**Goal.** The repository chooses the engine version its developers and CI invoke, and a newcomer can obtain that
version through the repository's entry point without installing Java or Gradle separately.

## Context

- The agreed [architecture](../architecture.md#engine-distribution) retains JVM distribution using `jpackage`, with
  a platform-specific package containing Java. Its requirement FR-26 ([requirements](../architecture.md#requirements))
  selects a committed wrapper, version and checksum pinning, first-use download, local caching, and explicit upgrades.
- Developers may work in several repositories that choose different engine versions. CI machines may start without
  any cached package. A machine-wide installation alone does not express each repository's choice.
- A version and its corresponding package checksum identify the release the repository intends to run. Download
  integrity and publisher authentication are distinct concerns ([0021](0021-release-trust.md)).
- Cookbook repositories hold and ship ingredients and trusted Groovy recipes and can build separately from Cook
  ([0013](0013-organization-content.md)). Ingredients model programs' native files and configuration; the engine
  evaluates recipes and reviews and writes their declared file operations.
- Recipe ownership lives in the committed `cook.lock`, the only file of Cook's own in a destination
  ([0014](0014-directory-layout.md)); apply does not need it.
- The current [`cook` script](../../cook) launches a locally built JVM distribution, and the existing
  [jpackage build](../../cli/gradle/jpackage.gradle) produces an application image with bundled Java
  ([install](../../README.md#install)).

## Decision

1. The repository commits an executable `./cook` wrapper with an exact engine version and matching checksums for the
   platform packages the repository uses. The committed pin is separate from the recipe ownership in
   `cook.lock`.
2. The engine ships as a complete platform-specific `jpackage` package containing Java. The wrapper caches and
   launches that complete package; copying only its launcher does not supply the runtime or other package contents.
3. On first use of an uncached pin, the wrapper downloads the selected platform package and verifies it against the
   committed checksum before execution. A mismatch prevents execution and acceptance into the cache. Later invocations
   reuse the cached pinned package without downloading again. The cache distinguishes versions and platforms; its
   location and the pin's representation belong to the implementation.
4. An upgrade explicitly changes the committed version and corresponding package checksums together for review.
   Ordinary invocations do not move the pin. A pin change does not automatically rewrite project files, migrate
   configuration, update cookbooks, or choose a recipe version.
5. The wrapper passes CLI arguments to the engine. The invocation's current working directory remains the destination
   for declared writes regardless of the wrapper or package location. CI that chooses to invoke Cook uses the same
   pin and verification flow.
6. Engine pinning is separate from cookbook distribution and recipe trust. Users manage recipe versions and supply
   the version to apply.

## Reasons

- **1, 3.** A committed entry point and pin travel with a clone. Developers and CI obtain the chosen package rather
  than whichever version happens to be installed globally. Version and checksum changes are visible together in review;
  a checksum comparison prevents a different download from silently running under that pin. Local caching pays the
  download cost on first use and permits later use offline. A machine without the pinned package must obtain it
  before running recipes; the wrapper cannot make an uncached offline first invocation work. Leaving the cache location
  to the implementation avoids a storage decision the architecture does not require.
- **2.** Bundling Java retains the JVM implementation without requiring each user to maintain a separate runtime.
  The launcher depends on its package contents, so downloading and caching only an executable would break that promise.
- **4.** Explicit upgrades make a dependency change a repository decision. Separating that change from applying
  recipes avoids turning an ordinary invocation into an unrequested upgrade or file rewrite.
- **5.** Committed pins, local executable caches and recipe ownership serve different purposes. Losing the cache must
  not erase the engine choice, and locating an executable must not change output paths.
- **6.** Program models and recipes have their own authors and distribution. An engine version cannot establish the
  trustworthiness or intended version of those sources.

## Rejected alternatives

### Unpinned global installation

At its best, one installation per machine avoids a wrapper and repository pin. It loses because it leaves developers and
CI free to run different releases for the same repository, and each newcomer must obtain the right release
independently.

### Pin only through a task runner

At its best, a repository reuses a task runner's tool list. It loses because that makes the engine choice depend on
having that task runner installed and active. The committed wrapper reaches repositories and machines that use another
runner or none.

### Commit every platform package

At its best, vendoring the complete packages permits first use offline. It loses because it adds packages, including
Java, to the repository's Git history on every upgrade. The chosen download/cache flow keeps the dependency choice
reviewable without that storage cost, accepting that an uncached machine needs a way to obtain the package.

### Container as the required entry point

At its best, an image carries the engine and its runtime together and suits an existing container-based CI workflow. It
loses because requiring it for every invocation adds a container runtime and working-directory integration to every
user's setup. The chosen jpackage distribution supplies Java directly without that requirement.

## Consequences

Costs:

- First use needs access to the pinned package and tools to download, verify, and unpack it.
- Maintainers distribute complete packages and corresponding checksums for the platforms they support.

Must be maintained:

- Platform packages and cached executables are protected from unintended changes.
- Repositories review explicit pin changes; cookbook changes remain separate.

## Confirmation

- Review an uncached invocation: does it verify and cache the complete pinned platform package, including Java, before
  launching it with the supplied arguments, and does a checksum mismatch prevent execution and cache acceptance?
- Review a later invocation: does it reuse that pin offline, and does an explicit version and checksum change select
  the new package through the same verification flow without changing cookbook content or project configuration?
- Review a clone with the engine cache removed: is the committed pin still available, and does a wrapper or package
  invoked elsewhere still target the caller's working directory?
