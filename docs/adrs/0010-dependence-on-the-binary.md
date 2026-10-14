# 0010. Native configuration remains usable without Cook

## Objective

**Problem.** Not everyone who clones a repository chooses the same setup tool. If generated configuration requires
that tool merely to be read, one person's choice becomes a dependency for every teammate and pipeline.

**Goal.** People and pipelines use the repository's native configuration without installing its setup tool, and
teams can change one part of their setup without withdrawing unrelated parts.

## Context

- Cook evaluates trusted Groovy recipes into file operations. Configured programs consume their native files
  directly; Cook is not their runner or lifecycle controller ([architecture](../architecture.md#scope-and-responsibilities)).
- Ingredients model native program files and configuration. Cookbooks supply those models and recipes separately
  from the engine's evaluation, review, recovery and writing responsibilities.
- The engine distribution uses platform-specific jpackage packages containing Java. The intended committed wrapper
  pins an engine version and package checksums, downloads an uncached package on first use, and caches it locally
  ([distribution](../architecture.md#engine-distribution)).
- In a worked example, a recipe configures an agent's native settings and adds a build instruction to a Markdown
  file. A teammate reads those files and runs the agent or build directly, without running Cook.

## Decision

1. Native configuration written by Cook remains meaningful to the configured programs without Cook installed.
   Configuring a program does not make Cook responsible for starting it, supervising it or handling its events.
2. Running Cook itself requires its engine package. The committed wrapper obtains the pinned platform package,
   verifies its checksum before execution, and caches the complete package, including Java. Upgrades explicitly
   change the committed version and matching checksums; an ordinary invocation does not select a newer release.
3. Cook introduces no required CI step merely because it wrote configuration. A team may explicitly invoke Cook in
   CI through the same wrapper and pin flow as a developer. A missing engine cannot stand in for a completed operation.
4. Recipe requests affect their declared targets and the targets their recipes owned before
   ([0007](0007-ownership.md)). Applying one recipe does not remove output another recipe owns. A recipe's new version
   removes what its previous version owned and no longer declares, through the normal batch diff and approval flow;
   stopping use of Cook does not itself delete native configuration.
5. This decision defines no implicit hook entry points, cache-only hook mode, session hints or lifecycle contracts.
   Recipe/ingredient APIs, station semantics and recipe hooks remain for the separate model discussion. Personal
   setup backup and agent-session orchestration remain future capabilities.

## Reasons

- **1.** Native files let teammates use their own programs and workflows without making one person's setup choice a
  mandate. This independence concerns consuming configuration; it is not a promise that arbitrary recipe content
  can never introduce a dependency. The batch diff exposes the requested file changes for review.
- **2.** Bundled Java avoids a separate runtime installation for package users. Version and checksum pinning make
  the engine package a reviewed choice rather than a changing download. The trade is a platform-specific complete
  package and a first-use download when the pin is not cached.
- **3.** CI belongs to the team. Optional explicit cooking still needs an engine and approval; absence cannot be
  reported as success. There is no engine-owned drift check or mandatory pipeline integration to guard.
- **4.** Declared scope and per-recipe ownership support changing one part of setup without withdrawing other
  recipes' output. Ownership lives in a committed file and in comment markers, neither of which a program or teammate
  needs Cook to read, so no eject step is required to keep using native files.
- **5.** Native program settings can describe hooks without making Cook a hook runner. Deferring model mechanics avoids
  retaining the former session engine through distribution requirements.

## Rejected alternatives

### Require Cook whenever native configuration is consumed

At its best, a single runtime mediates every program's setup. It loses because configuration becomes unusable for
teammates who did not choose Cook, and Cook becomes responsible for running programs outside its file-operation role.

### Keep the guarded session-hook architecture

At its best, cache-only hooks let sessions continue on machines without the engine. It loses because it assumes
Cook-owned session events and checks that are outside the agreed engine responsibilities. There is no hook latency
budget or skip protocol to preserve here.

### Automatically withdraw omitted recipes

At its best, one invocation describes all desired setup. It loses because applying a recipe would implicitly delete
unrelated output. Removal instead follows a recipe's own later versions and is visible against current content.

## Consequences

- Teams review native output for dependencies their recipes introduce; batch approval does not sandbox trusted recipe code.
- Explicit cooking needs the pinned engine package and access to obtain it when uncached. Consuming existing native
  configuration does not.
- Engine packaging and wrapper maintenance remain separate from cookbook builds and recipe version management.

## Confirmation

Review a native-configuration example from a teammate's perspective: reading its settings and following its instructions
requires the configured program, not a Cook invocation. Review the wrapper design for bundled Java, version/checksum
pinning, first-use verification/cache and explicit upgrades. Review a two-recipe example to ensure applying one preserves
unrelated output and a recipe's new version removes only what that recipe owned. These are design review criteria,
not new CI gates.
