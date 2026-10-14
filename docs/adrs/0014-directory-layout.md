# 0014. Native output and local batch records have separate homes

## Objective

**Problem.** Setup changes touch repositories that already contain program configuration, shared agent content and
local state. Misplacing those files prevents programs from finding their settings or causes local batch records to be
shared accidentally.

**Goal.** Programs find the configuration intended for them, reviewers can inspect shared changes, and each
repository keeps its batch records local without relocating unrelated files.

## Context

- [Ingredients](../features/glossary.md) model native program files and configuration. Recipes declare destination
  targets; Cook reviews and performs their file operations
  ([architecture](../architecture.md#ingredients-recipes-and-cookbooks)).
- In a worked example, `acme/api` keeps shared skills under `.agents/skills/` with a directory symlink from a native
  skills location. `acme/web` instead keeps a real skills directory containing hand-written files. Both layouts
  predate a recipe invocation.
- A repository may commit configuration while keeping its batch records local. Its current working directory is
  the destination, regardless of the recipe or executable location.
- Sources have origins distinct from destination targets. A recipe served over HTTPS can refer to a template beside
  itself at its URL while proposing output inside the destination.
- Cook's wrapper obtains and locally caches a complete pinned jpackage package containing Java. That executable
  cache and a destination's batch records serve different purposes.

## Decision

1. Native output lives at the destination paths declared by the recipes for the configured programs to consume.
   Shared content can use locations such as `.agents/`. Unrelated real directories, files and existing links remain in
   place.
2. Declared writes and deletions stay inside the destination, as [0012](0012-write-boundary.md) decides. An existing
   directory inside it is accepted as a target even when Cook did not create it.
3. Batch records live under the destination's ignored `.cook/` directory, with one record per batch in
   `.cook/batches/<batchId>/`, where the batch ID is the UTC start time. The record holds one reversible patch per
   touched file, `cook.lock` included, saved before the first target write. It also holds the evaluated and consumed
   recipe and template sources, including local sources, their origins, arguments, execution order and result or
   failure log. On a write failure, Cook rolls back prior batch changes and retains the patches and the failure record.
4. The batch record serves recovery. Recipe ownership lives in the committed `cook.lock` at the repository
   root ([0007](0007-ownership.md)). Apply evaluates supplied recipes against current content without earlier batch
   records; losing `.cook/` does not prevent it or lose ownership. Reverting a particular batch requires its record.
5. The engine pin and its cache follow [0017](0017-engine-version.md).
6. Cookbook repositories and source origins follow [0013](0013-organization-content.md).
7. Configuring native program files does not make Cook the owner of those programs' state or workspaces. Recipes do
   not implicitly relocate that state.

## Reasons

- **1.** Native paths let programs consume their configuration directly. Declared scope preserves unrelated content
  without forcing every repository into one skills layout.
- **2.** A symlinked skills directory, as in the `acme/api` example, makes an apparently local path able to reach
  outside the destination; [0012](0012-write-boundary.md) gives the reasons for the boundary. Accepting existing
  directories lets both example layouts receive declared changes as they are.
- **3, 4.** Separate local records keep recovery data out of shared configuration. One combined patch per file
  supports forward and reverse application without duplicate patch copies. Sources and origins make the evaluated
  operation inspectable; a batch record never marks a recipe as already applied. Ownership is shared review material
  rather than recovery data, so it lives in one committed file that every clone reads and pull requests show.
- **5, 6.** A committed engine pin, cookbook sources and batch records answer different questions with different
  lifetimes; the linked ADRs give their reasons. Capturing consumed sources in the batch record accepts its storage
  cost.
- **7.** Existing state and workspace conventions belong to their programs and users, so the layout leaves their files
  where they are.

## Rejected alternatives

### A committed ownership/configuration directory with no ignored paths

At its best, one directory contains everything reviewers need to describe managed output. It loses because recovery
patches describe one clone's operations and would add every batch record to commits; shared ownership needs only
the single committed `cook.lock`.

### Ingredients and recipes only in a per-user content-hash cache

At its best, many repositories share one verified copy of each ingredient or recipe source. It loses as a universal
layout requirement because recipes can use local cookbook sources inside the repository, and a source cache would be
confused with the batch record's captured sources. An engine package cache serves the pinned wrapper without imposing
that cache model on cookbook content.

### Replace native skills directories with whole-directory links

At its best, a single link exposes all shared skills. It loses as a default because an existing real directory may
hold unrelated files. An explicitly declared symlink operation still uses the ordinary batch review and destination
boundary.

## Consequences

Costs:

- Shared native configuration, a committed engine pin, local engine packages and ignored `.cook/` batch records have
  different lifetimes. Losing batch records prevents reverting those batches, while a missing package requires
  obtaining the pinned package before applying recipes.
- Batch records consume local space.

Must be maintained:

- Recipes and program models account for the native paths they configure and the extent of declared replacements.
- `.cook/` stays ignored, and `cook.lock` stays committed at the repository root.

## Confirmation

- Review the two skills-layout examples: do declared changes preserve unrelated content in both?
- Review an HTTPS recipe's batch: does its batch record keep the source URL as the base for relative references, and is
  a local recipe outside the repository refused?
- Review a new path Cook writes: is it either native output at a declared target, a batch record under the ignored
  `.cook/`, or `cook.lock`?
