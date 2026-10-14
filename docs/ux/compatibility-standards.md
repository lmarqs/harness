# Compatibility standards

## Objective

**Problem.** Repositories upgrade at different times. Changes to command grammar or native configuration can break
scripts and programs, while broad replacements can erase settings maintained by teammates or other tools. Pinning an
engine release alone does not control cookbook sources or the effects of executable recipes.

**Goal.** Users can review compatibility changes deliberately, preserve unrelated content, and distinguish engine
versions, cookbook sources and native program behaviour.

## Overview

This standard follows the [architecture](../architecture.md). It covers the intended file-operation engine and its
coexistence with configured programs. Current execution instructions belong in the [root README](../../README.md)
and [core guide](../../core/README.md); design requirements do not establish that they are implemented.

## Versioning

Keep these version choices distinct, because changing one does not automatically update the others:

| Artifact | Version responsibility | What to review |
| --- | --- | --- |
| Cook engine and CLI | The repository chooses an engine release through the committed wrapper's version and matching platform-package checksums | Command grammar, operation behaviour and the explicit pin change |
| Ingredients and cookbooks | Cookbook authors maintain program data models and recipes through their repositories and builds | Source changes and the native program versions and formats they cover |
| Supplied recipes | Users manage versions and supply the version they intend to apply or discard | The exact source and its consumed recipe/template dependencies |
| Native files and marked sections | The consuming program defines its format and semantics | Actual file changes, key paths, section boundaries and program defaults |
| Local run history | Cook records recovery information under the destination's ignored `.cook/` | The recorded operation, captured sources and prior values needed for reversal |

The wrapper pin controls the engine package, not recipe identity or version management. Cook does not infer which
older recipe to discard, merge recipe upgrades or reapply setup when the engine pin changes.

Do not require a universal schema key in native files, an ingredient/station contract version, or a committed Cook
configuration tree. Ingredient/recipe APIs, station roles and hooks remain separate discussion work. Concrete patch
representations, metadata serialization, storage schemas and overlapping-reversal mechanics are also outside this
standard. See [configuration formats](configuration-standards.md#defaults-and-format-changes).

## What counts as breaking

Review compatibility by effect rather than by whether an edit merely adds a field or flag. An added reserved command
name can change how an existing bare operand is parsed; a new default can change configuration without changing its
shape. Release notes SHOULD explain affected use and the migration a user must make, because users upgrade at their
own pace.

| Surface | Compatibility concern |
| --- | --- |
| Commands and arguments | Removing or renaming an operation, changing operand meaning or argument scope, or changing accepted paths and URIs |
| Approval | Suppressing the actual diff, accepting noninteractive execution as authorization, or changing whole-batch approval into partial selection |
| Ordered operations | Reordering recipes, rejecting intentional overlap, skipping repeated evaluation, or withdrawing unrelated earlier output |
| Native output | Changing a program's effective settings, replacing a broader target, losing unrelated keys or comments, or changing marked boundaries |
| Paths | Changing the invocation-directory destination or rebasing relative source references onto captured snapshots |
| Recovery | Changing recorded information in a way that prevents reversal of a retained run or misstates its prior values |
| Distribution | Changing package/runtime requirements, checksum verification or explicit pinning behaviour |
| Skill guidance | Teaching commands or prerequisites unavailable in the version the guidance describes |

Engine releases use semantic versioning (`MAJOR.MINOR.PATCH`) to signal interface compatibility. Breaking changes
belong in a major release; before 1.0, they may appear in a minor release and should be listed first in its notes,
because users need to assess the change before moving their pin. Release notes should distinguish incompatible
behaviour, additions and corrections. A fix can change rendered bytes or native behaviour, so a patch label is not
proof that applying recipes produces no diff. This page does not assign new exit codes, machine-output formats,
schema support windows or deprecation aliases.

The target command set is implicit or explicit apply, discard and revert. Retired lifecycle and management commands
are not aliases. There is no `--adopt`, adoption alias or separate adoption step; compatibility guidance must not
preserve those gates. Existing or edited content uses the same actual diff and whole-batch approval flow.

### Output across releases and sources

Changing an engine version, cookbook source, recipe argument or consuming program can affect the requested setup.
Review the actual batch diff against current content. Trusted executable recipes do not carry a universal guarantee
of identical output across machines or releases, or of network-free evaluation.

Every apply/discard invocation evaluates the supplied recipes, even after an identical earlier invocation. An
unchanged result may produce an empty diff. An engine upgrade does not automatically refresh native files or skill
content. Users decide when to invoke the supplied sources, and later recipes can intentionally replace earlier values.

For example, recipe A creates `notes.txt` and sets a key; recipe B sets the same key. Applying A then B previews B's
final value in one combined diff. Applying B alone later leaves A's unrelated `notes.txt` intact. This is ordered
composition, not an ownership conflict or automatic withdrawal.

## Supported programs and tools

Ingredients model programs' native files and configuration. Cookbook authors SHOULD document the program versions,
formats and limitations their models cover, because successful file writing does not prove that a program reads the
result as intended. Engine builds and cookbook builds can be separate; neither implies a universal program support
matrix or a runtime version-check command.

Review native permissions, hook semantics, discovery paths, links and trust settings using the consuming program's
rules. Local, user and managed settings can affect project settings. Cook does not translate every rule universally,
enforce permissions during later program use or supply a worker/jail launch contract. See
[program compatibility](../features/compatibility.md) and [program permissions](../features/policy.md).

## Transparency for people and CI that do not use Cook

Programs consume their native files without invoking Cook merely to read them. Shared instruction sections remain
readable without Cook, and skill discovery follows the consuming program. Configuring a program does not make Cook
its runner or lifecycle controller.

A recipe may explicitly change build, test, deploy or CI files, so review those consequences; do not promise that all
setup leaves behaviour unchanged. Cook adds no mandatory CI step. Teams choose whether CI invokes Cook and which
sources and dependencies must be available to that job.

CI uses the ordinary approval contract. `--batch-approve` precedes operands, displays the final terminal diff and
proceeds without prompting. Noninteractive execution without it does not authorize target writes. No separate
render/check/doctor workflow is required. See [CLI approval](../features/cli.md#batch-options-and-approval).

Stopping Cook invocations leaves native content in place. There is no eject protocol or automatic repository-wide
restoration. Discard deletes supplied declarations; revert restores recorded prior values. Both show the actual diff
for whole-batch approval, including changes to manually edited targets, while preserving unrelated content.

If a run changed `test` from `old` to `new` and a person then changed it to `custom`, discarding the supplied recipe
that declares `test` proposes deleting the key. Reverting that original run instead proposes `custom` to `old`.
Neither affects unrelated keys or reverses external activity by configured programs. See [discard and revert](../features/remove.md).

## Coexistence

Choose declared targets deliberately, because native files can contain settings maintained by several sources.
Whole-file operations affect the whole file; key and section operations preserve unrelated content. Markers identify
boundaries rather than permanent ownership. Use native comment syntax, including HTML comments in Markdown.

| Already present | Intended interaction |
| --- | --- |
| Hand-written settings or instruction text | Propose declared changes against current content; preserve unrelated keys or text outside declared sections |
| Other tools' ignore entries and marked sections | Preserve entries outside the declared targets; review overlapping native semantics and contradictory instructions |
| Skills, memory, command folders and installer lock files | Change only supplied declarations; choose a maintenance process that avoids competing updates |
| Real directories, per-entry links or whole-directory symlinks | Review the existing shape and declared scope; resolved batch targets must remain within the destination |
| Native agent skill discovery | The agent decides what it loads and follows; Cook provides no standing-context injection protocol ([skills guidance](skills-standards.md#discovery-and-session-guidance)) |
| Local, user or managed program settings | Do not implicitly import them; choose shared versus personal content explicitly, with batch writes confined to the destination |
| Task-runner, build and CI files | Recipes may target native configuration; those programs execute their own tasks independently |
| Trackers, loop runners and working-copy tools | Recipes may configure them; Cook does not select tasks, update issue state, launch sessions or clean working copies |
| Pushes, pull requests, notifications and MCP services | External tools and people perform these activities; command execution is not a batch action |
| Another program's data in `.cook/` | Account for it before using the destination-local history path; do not infer a conflict exit code or automatic takeover protocol |
| Executables on `PATH` or a global Cook install | The wrapper design runs its pinned cached package; use explicit paths when selecting another executable, without assuming global delegation |

Approval, output boundaries and rollback cover declared batch operations, not arbitrary side effects of trusted Groovy
recipe code. Trust sources before evaluating them; the resulting diff is not a sandbox.

## Paths and recovery

The invocation's current working directory is the destination, regardless of executable or recipe location. Declared
writes and deletions stay inside it, including resolution through symlinks. Relative source references resolve from
the referencing recipe's original location or HTTPS URL; capturing a snapshot must not change that base.

Save recovery patches before the first target write. Keep one reversible patch per touched file for the combined
change and a separate run record under ignored destination-local `.cook/`. Capture evaluated recipes, all consumed
local and downloaded recipe/template sources, their origins, arguments, order, operation and result or failure log.
On write failure, roll back changes already made by the batch and retain patches and failure records.

Apply/discard need no prior history or ownership database. Losing `.cook/` loses local recovery information, not the
ability to cook. Reverting a particular run requires that run's history. These guarantees do not choose a patch
format or metadata schema. See [paths and history](../architecture.md#paths-source-snapshots-and-local-history).

## Installation and version pinning

Cook uses platform-specific JVM packages built with `jpackage`, including Java. Distribute the complete package,
not a standalone launcher; package users do not install Java separately.

The committed `./cook` wrapper design pins an engine version and matching package checksums. First use downloads the
pinned platform package, verifies its checksum before execution and caches the complete package locally. Later
invocations reuse that version. Upgrades are explicit reviewed changes to the version and matching checksums;
ordinary invocation does not choose a newer release or automatically reapply recipes.

A matching checksum establishes correspondence with the committed package choice. It does not by itself prove
publisher identity or source safety. Review the source of the pin and checksums; this standard does not select a
signature, key-rotation or release-notice protocol. See [release trust reasoning](../adrs/0021-release-trust.md).

These requirements do not establish that published packages or a working pinned-download wrapper exist. See
[installation](../features/installation.md) and [wrapper](../features/wrapper.md) for the design and current local entry
path. No cache layout, pin serialization, release asset layout or updater command is mandated here.
