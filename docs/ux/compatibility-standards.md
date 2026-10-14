# Compatibility standards

## Objective

**Problem.** Repositories upgrade at different times. Changes to command grammar or native configuration can break
scripts and programs, while broad replacements can erase settings maintained by teammates or other tools. Pinning an
engine release alone does not control [cookbook](../features/glossary.md) sources or the effects of executable
recipes.

**Goal.** Users can review compatibility changes deliberately, preserve unrelated content, and distinguish engine
versions, cookbook sources and native program behavior.

## Overview

This standard follows the [architecture](../architecture.md). It covers the intended file-operation engine and its
coexistence with configured programs. Current execution instructions belong in the [root README](../../README.md) and
[core guide](../../core/README.md).

## Versioning

Keep these version choices distinct, because changing one does not automatically update the others:

| Artifact | Version responsibility | What to review |
| --- | --- | --- |
| Cook engine and CLI | The committed wrapper pins an engine version and matching platform-package checksums | Command grammar, operation behavior and the pin change |
| Ingredients and cookbooks | Cookbook authors maintain program models and recipes in their repositories and builds | Source changes and the native program versions and formats they cover |
| Supplied recipes | Users supply the recipe version they intend to apply | The exact source and its consumed recipe/template dependencies |
| `cook.lock` | Cook records the source and sha256 that produced each recipe's current ownership | The `cook.lock` change |
| Native files and sections | The consuming program defines its format and semantics | Actual file changes, key paths, section boundaries and program defaults |
| Batch records | Cook records each batch under the destination's ignored `.cook/batches/<batchId>/` | The recorded batch, captured sources and reversible patches |

Changing the engine pin leaves recipe versions and project files as they are; see
[upgrading](../features/wrapper.md#upgrading).

## What counts as breaking

Review compatibility by effect rather than by whether an edit merely adds a field or flag. An added reserved command
name can change how an existing bare operand is parsed; a new default can change configuration without changing its
shape. Release notes should explain affected use and the migration a user must make, because users upgrade at their
own pace.

| Surface | Compatibility concern |
| --- | --- |
| Commands and arguments | Removing or renaming a command, changing operand meaning or argument scope, or changing accepted paths and URLs |
| Approval | Suppressing the actual diff, accepting noninteractive execution as authorization, or changing whole-batch approval into partial selection |
| Ordered operations | Reordering recipes, rejecting intentional overlap, skipping repeated evaluation, or removing output another recipe owns |
| Native output | Changing a program's effective settings, replacing a broader target, losing unrelated keys or comments, or changing marker syntax |
| Ownership | Changing a recipe's short name, which changes its markers and `cook.lock` entry |
| Paths | Changing the invocation-directory destination or rebasing relative source references onto captured snapshots |
| Recovery | Changing recorded information in a way that prevents reversal of a retained batch or misstates its changes |
| Distribution | Changing package/runtime requirements, checksum verification or explicit pinning behavior |
| Skill guidance | Teaching commands or prerequisites unavailable in the version the guidance describes |

The [command set](cli-standards.md#command-set) lists the commands this table protects.

## Output across releases and sources

Changing an engine version, cookbook source, recipe argument or consuming program can affect the requested setup.
Review the actual batch diff against current content. Trusted executable recipes do not carry a universal guarantee
of identical output across machines or releases, or of network-free evaluation.

An engine upgrade leaves native files and skill content as they are until users invoke the supplied sources. Later
recipes can intentionally replace earlier values;
[ordered composition](../architecture.md#ordered-composition-and-declared-targets) shows how, and what applying a new
recipe version removes.

## Supported programs and tools

Ingredients model programs' native files and configuration. Cookbook authors should document the program versions,
formats and limitations their models cover, because successful file writing does not prove that a program reads the
result as intended. Engine builds and cookbook builds can be separate.

Review native permissions, hook semantics, discovery paths, links and trust settings using the consuming program's
rules. Local, user and managed settings can affect project settings. The consuming program enforces its permissions
during later use. See [permission rules](configuration-standards.md#permission-rules). Review configuration when
upgrading a consuming program as well as when changing cookbook sources.

## Transparency for people and CI that do not use Cook

Programs read their native files directly, without invoking Cook. Shared instruction sections remain readable
without Cook, and skill discovery follows the consuming program.

A recipe may explicitly change build, test, deploy or CI files, so review those consequences; do not promise that all
setup leaves behavior unchanged. Teams choose whether CI invokes Cook and which sources and dependencies must be
available to that job.

CI uses the ordinary [approval contract](../features/cli.md#batch-options-and-approval): noninteractive execution
without `--batch-approve` does not authorize target writes.

Stopping Cook invocations leaves native content in place. [Revert](../features/revert.md) reverses one recorded batch's
changes and keeps later non-overlapping edits; it does not reverse external activity by configured programs.

## Coexistence

Choose declared targets deliberately, because native files can contain settings maintained by several sources.
Whole-file operations affect the whole file; key and section operations preserve unrelated content.
[Markers](generated-content-standards.md#sections-and-markers) bound each section and name its owning recipe and
region; ownership never blocks a change.

| Already present | Intended interaction |
| --- | --- |
| Hand-written settings or instruction text | Propose declared changes against current content; preserve unrelated keys or text outside declared sections |
| Other tools' ignore entries and marked sections | Preserve entries outside the declared targets; review overlapping native semantics and contradictory instructions |
| Skills, memory, command folders and installer lock files | Change only supplied declarations; maintain each through one process, because competing updates can overwrite each other |
| Real directories, per-entry links or whole-directory symlinks | Review the existing shape and declared scope; resolved batch targets must remain within the destination |
| Native agent skill discovery | The agent decides what it loads and follows ([skills guidance](skills-standards.md#discovery-and-session-guidance)) |
| Local, user or managed program settings | Do not implicitly import them; choose shared versus personal content explicitly, with batch writes confined to the destination |
| Task-runner, build and CI files | Recipes may target native configuration; the repository's task runner or contributor instructions describe its preparation, build and check commands, and those programs execute their own tasks independently |
| Trackers, loop runners and working-copy tools | Recipes may configure their native files; those tools select and assign tasks, track dependencies, update and synchronize issue state, launch sessions, signal completion and clean working copies |
| Tracker databases and state files | They remain the tracker's data; applying configuration does not implicitly migrate, clear or synchronize them, and a recipe that targets them declares that change in the reviewed diff, within the destination |
| Version-control configuration, ignore rules and hook files | Recipes may declare them and the version-control program interprets and runs them; people and external tools create branches, manage worktrees, create or remove working copies, detect landed work, enforce delivery guards and preserve uncommitted work |
| Pushes, pull requests, notifications and MCP services | External tools and people perform these activities |
| Another program's data in `.cook/` or `cook.lock` | Move it elsewhere first, because Cook writes batch records to `.cook/` and ownership to `cook.lock` |
| Executables on `PATH` or a global Cook install | The wrapper design runs its pinned cached package; use explicit paths when selecting another executable |

Trackers own their assignment rules, sync settings, remotes and commands. Synchronized tracker data can expose
assignments and comments to other machines, so choose local or shared data and remote-sync permissions with the
tracker's own controls; writing configuration does not isolate its later activity, and reverting a configuration batch
does not reverse task activity the tracker performed later.

Approval, output boundaries and rollback cover declared batch operations, not arbitrary side effects of trusted Groovy
recipe code. Trust sources before evaluating them; the resulting diff is not a sandbox.

## Paths and recovery

Destination and source resolution follow
[destination and source origins](../architecture.md#destination-and-source-origins). Capturing a source snapshot must
not change the base for relative references, because a rebased reference would resolve a different source.

Each batch keeps [one record](../architecture.md#one-record-for-the-batch), saved before the first target write.
Reverting a batch requires its record, so losing `.cook/` makes earlier batches irreversible while apply keeps working
and ownership survives in `cook.lock` ([history availability](../architecture.md#batch-record-availability)).

## Installation and version pinning

Cook ships as platform-specific `jpackage` packages that include Java. Distribute the complete package, because a copied
launcher cannot find its runtime ([wrapper troubleshooting](../features/wrapper.md#troubleshooting)).

The committed `./cook` [wrapper](../features/wrapper.md) pins an engine version and matching package checksums. Upgrades
are explicit reviewed changes to both; ordinary invocation does not choose a newer release or reapply recipes. A
matching checksum establishes correspondence with the committed package choice, not publisher identity, so review the
source of the pin and checksums ([release trust reasoning](../adrs/0021-release-trust.md)). See the root README's
[install section](../../README.md#install) for the current local entry path.
