# 0003. Cook retains its JVM stack

## Objective

**Problem.** Contributors maintain the engine, its executable recipe support and cookbook content. Changing the
engine's language means rewriting its file handling and public entry point, and a stack that requires the project's
development toolchain on every user's machine turns running Cook into a setup project.

**Goal.** Contributors can maintain the engine and cookbook content separately, and users can run Cook without
preparing a development environment.

## Context

- The core, infra and CLI modules and the cookbook ingredients and [recipes](../features/glossary.md) are Groovy
  ([root README](../../README.md#current-implementation)).
- The [architecture](../architecture.md#engine-distribution) retains JVM distribution using jpackage with bundled Java.
  Recipes are trusted executable Groovy, and cookbook builds can be separate from the engine build.
- The [architecture's requirements](../architecture.md#responsibilities-and-scope) make the CLI the public cooking
  interface and Gradle the tool that builds and packages the engine and cookbooks (FR-5).

## Decision

1. Cook is Groovy on the JVM: its core, its connections and its CLI. Recipe evaluation accepts trusted Groovy code.
2. Engine distribution uses complete platform-specific jpackage packages containing Java
   ([0004](0004-runtime-dependencies.md)), which the wrapper pins and verifies ([0017](0017-engine-version.md)).
3. Engine and cookbook builds have distinct responsibilities; cookbooks hold and ship ingredients and recipes
   ([0013](0013-organization-content.md)). The public cooking interface is the CLI, and Gradle handles building and
   packaging.

## Reasons

- **1.** Recipes are Groovy, and the core compiles them, so writing the engine in Groovy too leaves one language, one
  compiler, one formatter and one lint setup, and the author's annotations are written in the language recipes use.
  The trade: Groovy's dynamic dispatch reports some type errors only when tests run; the engine is a JVM application,
  not a native single-file executable.
- **2.** Bundling Java meets the user-facing runtime objective without requiring each repository to install a matching
  runtime. The trade: packages include runtime files and must be built for their target platforms.
- **3.** Cookbook authors maintain program models on the programs' schedules, while engine contributors maintain
  evaluation and file operations. A common CLI gives users one cooking interface. The trade: separate builds require
  compatibility coordination.

## Rejected alternatives

### Rewrite as a standalone Go or Rust executable

At its best, native distribution can simplify downloading and runtime setup. It loses because the agreed design
retains executable Groovy recipes and the JVM engine. A native rewrite adds language migration and an evaluation
integration problem, while bundling Java already removes the separate Java installation for package users.

### Require users to run cooking through Gradle

At its best, one build tool supplies compilation and execution in a contributor checkout. It loses because users need
the public CLI and complete runtime package, rather than a build environment in every destination repository.

## Consequences

Costs:

- Contributors need the JVM development toolchain.
- Releases build a complete package with a bundled runtime for each target platform.
- Separate engine and cookbook builds need compatibility coordination.

Must be maintained:

- Cookbook builds distinct from engine releases.
- The CLI as the public cooking interface.

## Confirmation

- `./gradlew jpackageTest` builds and smoke-tests the packaged CLI with its bundled runtime.
- `./gradlew cookbookBuild` builds the cookbooks as a task separate from the engine build.
- Does the root README's [install section](../../README.md#install) describe the actual local packaging entry path,
  `./gradlew jpackage`?
