# 0014. Native output and local recovery history have separate homes

## Objective

**Problem.** Setup changes touch repositories that already contain program configuration, shared agent content and
local state. Misplacing those files prevents programs from finding their settings or causes local recovery data to
be shared accidentally.

**Goal.** Programs find the configuration intended for them, reviewers can inspect shared changes, and each
repository keeps its recovery information local without relocating unrelated files.

## Context

- Ingredients model native program files and configuration. Recipes declare destination targets; Cook reviews and
  performs their file operations ([architecture](../architecture.md#ingredients-recipes-and-cookbooks)).
- In a worked example, `acme/api` keeps shared skills under `.agents/skills/` with a directory symlink from a native
  skills location. `acme/web` instead keeps a real skills directory containing hand-written files. Both layouts
  predate a recipe invocation.
- A repository may commit configuration while keeping operation history local. Its current working directory is
  the destination, regardless of the recipe or executable location.
- Sources have origins distinct from destination targets. A recipe served over HTTPS can refer to a template beside
  itself at its URL while proposing output inside the destination.
- Cook's wrapper obtains and locally caches a complete pinned jpackage package containing Java. That executable
  cache and a destination's recovery history serve different purposes.

## Decision

1. Native output lives at the destination paths declared by the recipes for the configured programs to consume.
   Shared content can use locations such as `.agents/`. Unrelated real directories, files and existing links remain in
   place.
2. Declared writes and deletions stay inside the invocation directory, including paths resolved through symlinks.
   An existing directory is not refused merely because Cook did not create it. Existing content follows the same
   terminal diff and whole-batch approval flow; recorded ownership never blocks a change.
3. Local batch history lives under the destination's ignored `.cook/` directory, with one record per batch in
   `.cook/batches/<batchId>/`, where the batch ID is the UTC start time. Save one reversible patch per touched file,
   `cook.lock` included, before the first target write. Record evaluated and consumed recipe/template sources,
   including local sources, their origins, arguments, execution order and result or failure log. On a write failure,
   roll back prior batch changes and retain patches and the failure record.
4. History is recovery information. Recipe ownership lives in the committed `cook.lock` at the repository root
   ([0007](0007-ownership.md)). Apply evaluates supplied recipes against current content without previous history;
   losing `.cook/` does not prevent it or lose ownership. Reverting a particular batch requires its record.
5. The committed `./cook` wrapper pins an engine version and matching platform-package checksums. First use downloads
   and verifies the uncached package, then caches the complete package including Java. Upgrades explicitly change
   the version and checksums. The engine pin remains committed; the engine cache path and pin representation remain
   unspecified.
6. Cookbook repositories hold and ship ingredients and recipes, with builds that can be separate from the engine.
   Sources are paths inside the destination repository or `https://` URLs. Relative source references retain their
   original recipe base when captured in history ([source origins](../architecture.md#destination-and-source-origins)).
7. Configuring native program files does not make Cook the owner of those programs' state or workspaces. Recipes do
   not implicitly relocate that state.

## Reasons

- **1, 2.** Native paths let programs consume their configuration directly. Declared scope preserves unrelated content
  without forcing every repository into one skills layout. Symlink resolution matters because an apparently local
  path can reach outside the destination. These boundaries govern batch operations, not arbitrary trusted recipe code.
- **3, 4.** Separate local history keeps recovery information out of shared configuration. One combined patch per file
  supports forward and reverse application without duplicate patch copies. Sources and origins make the evaluated
  operation inspectable; history does not establish an already-applied gate. Ownership is shared review material
  rather than recovery data, so it lives in one committed file that every clone reads and pull requests show.
- **5.** A committed pin survives loss of local history and identifies the reviewed engine package across machines.
  Bundled Java removes a separate runtime prerequisite for package users; complete packages need platform-specific
  caching. Selecting a cache hierarchy now would add a storage decision the architecture does not require.
- **6.** Source location and destination location answer different questions. Requiring supplier-only caches would
  exclude local cookbook inputs and confuse recovery snapshots with source resolution. Capturing sources accepts
  storage costs while leaving cookbook distribution and model APIs separate.
- **7.** Existing state and workspace conventions belong to their programs and users, so the layout leaves their files
  where they are.

## Rejected alternatives

### A committed ownership/configuration directory with no ignored paths

At its best, one directory contains everything reviewers need to describe managed output. It loses because recovery
patches describe one clone's operations and would add every batch's history to commits; shared ownership needs only
the single committed `cook.lock`.

### Configurable session-state roots in every repository

At its best, repositories choose where Cook's worker workspaces live. It loses because session orchestration is a
future capability, and the agreed engine operation history has its local home under `.cook/`.

### Ingredients and stations only in a per-user content-hash cache

At its best, many repositories share one verified supplier copy. It loses as a universal layout requirement because
recipes can use local cookbook sources, and station and supplier mechanics are deferred. An engine package cache
serves the pinned wrapper without imposing that cache model on cookbook content.

### Replace native skills directories with whole-directory links

At its best, a single link exposes all shared skills. It loses as a default because an existing real directory may
hold unrelated files. An explicitly declared symlink operation still uses the ordinary batch review and destination boundary.

## Consequences

- Recipes and program models must account for the native paths they configure and the extent of declared replacements.
- Shared native configuration, a committed engine pin, local engine packages and ignored `.cook/` recovery have
  different lifetimes. Losing recovery history prevents reverting those batches, while a missing package requires
  obtaining the pinned package before cooking.
- Recovery snapshots consume local space.

## Confirmation

Review the two skills-layout examples to ensure declared changes preserve unrelated content. Trace an HTTPS recipe to
its native output and source snapshot, retaining its source URL as base; a local recipe outside the repository is
refused. Review containment through symlinks, ignored destination-local history, committed version/checksum pinning,
and complete jpackage caching as separate responsibilities.
