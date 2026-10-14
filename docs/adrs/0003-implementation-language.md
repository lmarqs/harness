# 0003. Cook retains its JVM stack

## Objective

**Problem.** Changing the engine's language is a rewrite of its file handling and public entry point. Contributors need
one stack decision that supports executable recipes without making users install the project's development toolchain.

**Goal.** Contributors can maintain the engine and cookbook content separately, and users can run a complete package
on its matching platform without preparing a development environment.

## Context

- The [root README](../../README.md#current-implementation) describes Kotlin core and CLI modules, Groovy adapters,
  and Groovy cookbook suppliers and recipes.
- The [architecture](../architecture.md#engine-distribution) retains JVM distribution using jpackage with bundled Java.
  Recipes are trusted executable Groovy, and cookbook builds can be separate from the engine build.
- The CLI is the public cooking interface. Gradle builds and packages the engine and cookbooks.

## Decision

1. Cook retains its Kotlin/Groovy JVM stack. Recipe evaluation accepts trusted Groovy code.
2. Engine distribution uses complete platform-specific jpackage packages containing Java, as explained in
   [0004](0004-runtime-dependencies.md). The wrapper pins and verifies the package.
3. Engine and cookbook builds have distinct responsibilities. Cookbooks hold and ship ingredients and recipes;
   engine packaging does not manage their versions. The public cooking interface is the CLI, and Gradle handles
   building and packaging.

## Reasons

- **1.** The retained stack supports the chosen executable recipe language and existing modules without a language
  rewrite. The trade: contributors maintain JVM tooling and the Kotlin/Groovy boundary; this is not a native,
  single-file engine.
- **2.** Bundling Java meets the user-facing runtime objective without requiring each repository to install a matching
  runtime. The trade: packages include runtime files and must be built for their target platforms.
- **3.** Cookbook authors maintain program models on the programs' schedules, while engine contributors maintain
  evaluation and file operations. A common CLI gives users one cooking interface. The trade: separate builds require
  compatibility coordination.

## Rejected alternatives

### Rewrite as a standalone Go or Rust executable

At its best, native distribution can simplify downloading and runtime setup. It loses because the agreed design
retains executable Groovy recipes and the JVM engine. A native rewrite adds language migration and an evaluation
integration problem without being required to remove a separate Java installation for package users.

### Require users to run cooking through Gradle

At its best, one build tool supplies compilation and execution in a contributor checkout. It loses because users need
the public CLI and complete runtime package, rather than a build environment in every destination repository.

## Consequences

Contributors need the JVM development toolchain. Releases maintain complete platform packages and a bundled runtime.
Cookbook builds remain distinct from engine releases.

## Confirmation

Review stack and packaging descriptions against the [architecture](../architecture.md#engine-distribution) and
[installation reference](../features/installation.md). Current-execution claims must describe the actual local
packaging entry path.
