# 0004. Cook packages its Java runtime

## Objective

**Problem.** Repositories use different language stacks and runtime versions. Requiring each developer to prepare an
extra matching runtime makes adoption a setup project and introduces failures unrelated to the configuration they
want to change.

**Goal.** A user runs Cook on a machine that has no Java installation and none of the tools used to build Cook.

## Context

- A repository's existing runtimes follow its own stack. Worked example: acme's Node service, Python service and
  Terraform repository do not share a Java installation requirement.
- Cook retains the [JVM stack](0003-implementation-language.md). The
  [architecture](../architecture.md#engine-distribution) specifies jpackage packages containing Java and a committed
  wrapper that downloads, verifies and caches a pinned platform package on first use.
- A launcher is one part of that complete package; distributing only the launcher omits the runtime and application
  files it needs. The [installation reference](../features/installation.md) distinguishes current local packaging
  from the intended wrapper delivery design.
- [Recipes](../features/glossary.md) are trusted executable code and can call network services or local tools of
  their own.

## Decision

1. The complete platform-specific jpackage package contains Cook and its Java runtime. Package users do not install
   Java or Gradle separately. The runtime is a bundled engine dependency.
2. A committed wrapper pins, downloads, verifies and caches the complete platform package
   ([0017](0017-engine-version.md)).
3. Cookbook repositories ship ingredients and recipes separately from engine releases, and engine pins do not select
   recipe versions ([0013](0013-organization-content.md)).
4. Batch operations execute no external commands ([0019](0019-action-kinds.md)). Trusted recipe code can still reach
   external resources; batch approval and rollback do not govern those effects ([0026](0026-cookbook-trust.md)).

## Reasons

- **1.** Packaging the runtime makes the acme repositories independent of their existing language stacks for running
  Cook. The trade: a release is a larger multi-file package with runtime maintenance and platform-specific builds,
  rather than a statically linked executable that needs only the operating system.
- **2.** A reviewed version and checksum make release selection explicit and allow repeat use without repeated
  downloads. Verifying before execution checks the package against the committed pin. The trade: first use needs
  download and verification facilities.
- **3.** Program models and recipes evolve separately from the engine. Separate builds preserve that responsibility.
  The trade: changing an engine pin does not update recipes; users and cookbook authors manage the supplied content.
- **4.** The package bundles the runtime Cook needs, not everything a recipe may call: a recipe may reach network
  services or local tools that the package does not include. The trade: trusting a recipe includes assessing what it
  needs and does beyond its declared batch, independently of reviewing the final file diff.

## Rejected alternatives

### Require a separately installed Java runtime

At its best, release packages are smaller and users manage one runtime for several applications. It loses because
machines may have no Java or an incompatible version, defeating the self-contained delivery objective.

### Forbid bundled runtimes and require an OS-only native binary

At its best, one native file is simple to copy and run. It loses because the chosen stack evaluates Groovy on the JVM;
removing separate runtime installation does not require removing the runtime from the package.

## Consequences

Costs:

- A release is a larger multi-file package built for each platform; copying a launcher alone does not run Cook.
- Development builds have their own toolchain requirements.

Must be maintained:

- The bundled Java runtime and complete platform packages in each engine release.
- Delivery instructions that distribute the complete package and keep first-use requirements distinct from
  development tooling.

## Confirmation

- `./gradlew jpackageTest` builds and smoke-tests the packaged CLI with its bundled runtime.
- Do the [installation](../features/installation.md) and [wrapper](../features/wrapper.md) references distribute the
  complete package and keep first-use requirements apart from development tooling?
