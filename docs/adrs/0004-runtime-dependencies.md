# 0004. Cook packages its Java runtime

## Objective

**Problem.** Repositories use different language stacks and runtime versions. Requiring each developer to prepare an
extra matching runtime makes adoption a setup project and introduces failures unrelated to the configuration they
want to change.

**Goal.** Users can run the complete release package on its matching platform without separately installing Java or
the tools used to build Cook.

## Context

- A repository's existing runtimes follow its own stack. Worked example: acme's Node service, Python service and
  Terraform repository do not share a Java installation requirement.
- Cook retains the [JVM stack](0003-implementation-language.md). The
  [architecture](../architecture.md#engine-distribution) specifies jpackage packages containing Java and a committed
  wrapper that downloads, verifies and caches a pinned platform package on first use.
- A launcher is one part of that complete package; distributing only the launcher omits the runtime and application
  files it needs. The [installation reference](../features/installation.md) distinguishes current local packaging
  from the intended wrapper delivery design.
- Cookbook distribution can be independent of engine distribution. Recipes are trusted executable code, so their
  arbitrary side effects cannot be bounded by an engine runtime-dependency claim.

## Decision

1. The complete platform-specific jpackage package contains Cook and its Java runtime. Package users do not install
   Java or Gradle separately. The runtime is a bundled engine dependency.
2. A committed wrapper pins the engine version and matching platform-package checksum. It downloads and verifies the
   complete package before first execution, caches it locally, and reuses that pin. Upgrades are explicit reviewed
   changes; ordinary invocations do not select newer releases.
3. Cookbook repositories ship ingredients and recipes separately from engine releases and can build independently.
   Engine pins do not manage cookbook or recipe versions. Users supply the recipe version they intend to use.
4. Batch operations do not execute external commands. This does not prohibit arbitrary trusted recipe code from
   accessing external resources or causing side effects; batch approval and rollback do not govern those effects.

## Reasons

- **1.** Packaging the runtime makes the acme repositories independent of their existing language stacks for running
  Cook. The trade: a release is a larger multi-file package with runtime maintenance and platform-specific builds,
  rather than a statically linked executable that needs only the operating system.
- **2.** A reviewed version and checksum make release selection explicit and allow repeat use without repeated
  downloads. Verifying before execution checks the package against the committed pin. The trade: first use needs
  download and verification facilities; their concrete tooling and cache layout are not decided here.
- **3.** Program models and recipes evolve separately from the engine. Separate builds preserve that responsibility.
  The trade: changing an engine pin does not update recipes; users and cookbook authors manage the supplied content.
- **4.** File-operation recovery has a defined scope. Treating recipe execution as an OS-only or closed dependency
  surface would overstate that scope. The trade: trusting a recipe includes assessing needs and effects beyond its
  declared batch, independently of reviewing the final file diff.

## Rejected alternatives

### Require a separately installed Java runtime

At its best, release packages are smaller and users manage one runtime for several applications. It loses because
machines may have no Java or an incompatible version, defeating the self-contained delivery objective.

### Forbid bundled runtimes and require an OS-only native binary

At its best, one native file is simple to copy and run. It loses because the chosen stack evaluates Groovy on the JVM;
removing separate runtime installation does not require removing the runtime from the package.

## Consequences

Engine releases maintain the bundled Java runtime and complete platform packages. Copying a launcher alone is
insufficient. Development builds have their own toolchain requirements.

## Confirmation

Review runtime and wrapper descriptions against the [architecture](../architecture.md#engine-distribution) and
[wrapper reference](../features/wrapper.md). Delivery instructions must distribute the complete package and keep
first-use requirements distinct from development tooling.
