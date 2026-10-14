# 0010. Native configuration remains usable without Cook

## Objective

**Problem.** Not everyone who clones a repository chooses the same setup tool. If generated configuration requires
that tool merely to be read, one person's choice becomes a dependency for every teammate and pipeline.

**Goal.** People and pipelines use the repository's native configuration without installing its setup tool.

## Context

- Cook evaluates trusted Groovy [recipes](../features/glossary.md) into file operations. Configured programs consume
  their native files directly ([architecture](../architecture.md#scope-and-responsibilities)).
- Ingredients model native program files and configuration. Cookbooks supply those models and recipes separately
  from the engine's evaluation, review, recovery and writing responsibilities.
- The engine is a complete platform package containing Java ([0004](0004-runtime-dependencies.md)), which a committed
  wrapper pins, downloads on first use and caches ([0017](0017-engine-version.md)).
- In a worked example, a recipe configures an agent's native settings and adds a build instruction to a Markdown
  file. A teammate reads those files and runs the agent or build directly, without running Cook.

## Decision

1. Native configuration written by Cook remains meaningful to the configured programs without Cook installed.
   Configuring a program does not make Cook responsible for starting it, supervising it or handling its events.
2. Running Cook itself requires its engine package, which the committed wrapper obtains and verifies
   ([0017](0017-engine-version.md)).
3. Cook introduces no required CI step merely because it wrote configuration. A team may explicitly invoke Cook in
   CI through the same wrapper and pin flow as a developer; a CI job that invokes Cook and cannot obtain the engine
   fails instead of reporting success.
4. Ownership lives in the committed `cook.lock` and in section markers ([0007](0007-ownership.md)), both plain text in
   the repository. A team that stops using Cook keeps its native configuration as written.
5. Configuration Cook writes gives configured programs no entry point that calls Cook; native settings can describe
   hooks that the program itself runs.

## Reasons

- **1.** Native files let teammates use their own programs and workflows without making one person's setup choice a
  mandate. This independence concerns consuming configuration; it is not a promise that arbitrary recipe content
  can never introduce a dependency. The batch diff exposes the requested file changes for review.
- **2.** Version and checksum pinning make the engine package a reviewed choice rather than a changing download. The
  trade is a platform-specific complete package and a first-use download when the pin is not cached.
- **3.** CI belongs to the team. Optional explicit cooking still needs an engine and approval; absence of the engine
  cannot be reported as success.
- **4.** A program or teammate reads committed files and comments without Cook, so leaving Cook needs no extra step
  to keep native files usable.
- **5.** Native program settings can describe hooks without making Cook a hook runner.

## Rejected alternatives

### Require Cook whenever native configuration is consumed

At its best, a single runtime mediates every program's setup. It loses because configuration becomes unusable for
teammates who did not choose Cook, and Cook becomes responsible for running programs outside its file-operation role.

### Run Cook from agent session hooks

At its best, agent hooks call Cook when a session starts or acts, and skip when the engine is absent so the session
continues on machines without it. It loses because it makes Cook respond to session events and checks that are
outside the engine's role ([0001](0001-role-of-the-tool.md)).

## Consequences

Costs:

- Teams review native output for dependencies their recipes introduce; batch approval does not sandbox trusted
  recipe code.
- Explicit cooking needs the pinned engine package and access to obtain it when uncached. Consuming existing native
  configuration does not.

Must be maintained:

- Engine packaging and wrapper maintenance separate from cookbook builds and recipe version management.
- Native output that the configured programs read without Cook.

## Confirmation

- In the worked example, can a teammate read the settings and follow the build instruction with the configured
  program alone, without a Cook invocation?
- Does every CI example that invokes Cook use the wrapper and pin flow, and fail when the engine is unavailable?
- Does any configuration a recipe writes call Cook from a program's hook?
