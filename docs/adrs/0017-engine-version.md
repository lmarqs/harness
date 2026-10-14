# 0017. Each repository pins its engine release through a committed wrapper

## Objective

**Problem.** Developers and CI can run different engine releases against the same repository. Changes to recipe
evaluation or file operations then depend on which release a machine happens to have, and a newcomer must find and
install the repository's chosen version before cooking.

**Goal.** The repository chooses the engine version its developers and CI invoke, and a newcomer can obtain that
version through the repository's entry point without installing Java or Gradle separately.

## Context

- The agreed [architecture](../architecture.md#engine-distribution) retains JVM distribution using `jpackage`, with
  a platform-specific package containing Java. FR-26 of the
  [documentation alignment PRD](../../tasks/prd-documentation-alignment.md#distribution-and-documentation-authority)
  selects a committed wrapper, version and checksum pinning, first-use download, local caching, and explicit upgrades.
- Developers may work in several repositories that choose different engine versions. CI machines may start without
  any cached package. A machine-wide installation alone does not express each repository's choice.
- A version and its corresponding package checksum identify the release the repository intends to run. Download
  integrity and publisher authentication are distinct concerns ([0021](0021-release-trust.md)).
- Cookbook repositories hold and ship ingredients and trusted Groovy recipes and can build separately from Cook
  ([0013](0013-organization-content.md)). Ingredients model programs' native files and configuration; the engine
  evaluates recipes and reviews, records, and writes their declared file operations.
- Destination-local `.cook/` is ignored recovery history, not a committed ownership database or the sole home of the
  engine pin ([0014](0014-directory-layout.md)). Apply and discard need no prior history or ownership records.
- These are target decisions. The current [`cook` script](../../cook) launches a locally built JVM distribution; it
  does not pin, download, verify, or cache a release. The existing
  [jpackage build](../../cli/gradle/jpackage.gradle) produces an application image with bundled Java. Neither establishes
  a published-release installation path. See [installation](../features/installation.md#build-a-local-package-today).

## Decision

1. Commit an executable `./cook` wrapper with an exact engine version and matching checksums for the platform packages
   the repository uses. The committed pin remains available independently of ignored `.cook/` history. This decision
   does not select a pin serialization format or require an ownership manifest.
2. Distribute the engine as a complete platform-specific `jpackage` package containing Java. Cache and launch that
   complete package; copying only its launcher does not supply the runtime or other package contents.
3. On first use of an uncached pin, the wrapper downloads the selected platform package and verifies it against the
   committed checksum before execution. A mismatch prevents execution and acceptance into the cache. Later invocations
   reuse the cached pinned package without downloading again. The cache distinguishes versions and platforms.
4. Upgrade by explicitly changing the committed version and corresponding package checksums together for review.
   Ordinary invocations do not move the pin. A pin change does not automatically re-render project files, migrate
   configuration, update cookbooks, or choose a recipe version. No updater or release-notice command is selected here.
5. The wrapper passes CLI arguments to the engine. The invocation's current working directory remains the destination
   for declared writes regardless of the wrapper or package location. CI that chooses to invoke Cook uses the same
   pin and verification flow; no CI job or check command is required by this decision.
6. Engine pinning is separate from cookbook distribution and recipe trust. Users manage recipe versions and supply
   the version to apply or discard. Ingredient/recipe APIs, nesting, station semantics, decorators, injection,
   annotations, and lifecycle hooks remain for the separate model discussion. Issue #9 does not decide deletion of
   dynamic composition or compiled suppliers, and this wrapper decision selects neither as the final model API.

## Reasons

- **1, 3.** A committed entry point and pin travel with a clone. Developers and CI obtain the chosen package rather
  than whichever version happens to be installed globally. Version and checksum changes are visible together in review;
  a checksum comparison prevents a different download from silently running under that pin.
- **2.** Bundling Java retains the JVM implementation without requiring each user to maintain a separate runtime.
  The launcher depends on its package contents, so downloading and caching only an executable would break that promise.
- **3.** Local caching pays the download cost on first use and permits later use offline. A machine without the pinned
  package must obtain it before cooking; the wrapper cannot make an uncached offline first invocation work.
- **4.** Explicit upgrades make a dependency change a repository decision. Separating that change from cooking avoids
  turning an ordinary invocation into an unrequested upgrade or file rewrite.
- **1, 5.** Committed pins, local executable caches, and recovery history serve different purposes. Losing history
  must not erase the engine choice or prevent apply/discard, and locating an executable must not change output paths.
- **6.** Program models and recipes have their own authors and distribution. An engine version cannot establish the
  trustworthiness or intended version of those sources, and packaging does not decide how their APIs compose.

## Rejected alternatives

### Unpinned global installation

One installation per machine avoids a wrapper and repository pin. It leaves developers and CI free to run different
releases for the same repository, and each newcomer must obtain the right release independently.

### Pin only through a task runner

A repository can reuse a task runner's tool list. That makes the engine choice depend on having that task runner
installed and active. The committed wrapper reaches repositories and machines that use another runner or none;
this decision does not prohibit a team's task runner or prescribe how Cook reads program configuration.

### Commit every platform package

Vendoring the complete packages permits first use offline. It adds packages, including Java, to repository history
on every upgrade. The chosen download/cache flow keeps the dependency choice reviewable without that storage cost,
accepting that an uncached machine needs a way to obtain the package.

### Container as the required entry point

An image can carry the engine and its runtime together and suit an existing container-based CI workflow. Requiring it
for every invocation adds a container runtime and working-directory integration to every user's setup. The chosen
jpackage distribution supplies Java directly without that requirement.

## Consequences

- First use needs access to the pinned package and tools to download, verify, and unpack it. Users of the complete
  package need no separate Java or Gradle installation. Wrapper tooling and the release platform matrix still need
  implementation design; this record does not prescribe shells, asset paths, cache paths, or failure exit codes.
- Maintainers distribute complete packages and corresponding checksums for the platforms they support. Platform
  packages and cached executables need protection from unintended changes.
- Repositories review explicit pin changes. Cookbook changes remain separate; no registry, recipe-version manager,
  automatic updater, global-install delegation, hook cache mode, or checksum-bypass mechanism is introduced.
- Existing target content uses the actual batch diff and approval flow, without `--adopt`, an adoption alias, a separate
  adoption step, or ownership/drift gates. Engine pinning does not create those prerequisites.

## Confirmation

Review the distribution design for these scenarios; they are acceptance observations, not required check commands:

- An uncached invocation verifies and caches the complete pinned platform package, including Java, before launching
  it with the supplied arguments. A checksum mismatch prevents execution and cache acceptance.
- A later invocation reuses that pin offline. An explicit version/checksum change selects the new package through the
  same verification flow without automatically changing cookbook content or project configuration.
- Removing local recovery history leaves the committed pin available. Invoking a wrapper or package elsewhere still
  targets the caller's working directory. Recipe/model APIs and lifecycle mechanics remain undecided.
