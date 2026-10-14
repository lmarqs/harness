# Compatibility standards

## Objective

**Problem.** Repositories upgrade at different times. Changes to command grammar or native configuration can break
scripts and programs, while broad replacements can erase settings maintained by teammates or other tools. Pinning an
engine release alone does not control cookbook sources or the effects of executable recipes.

**Goal.** Users can review compatibility changes deliberately, preserve unrelated content, and distinguish engine
versions, cookbook sources and native program behaviour.

## Overview

This standard follows the [architecture](../architecture.md). It covers the intended file-operation engine and its
coexistence with configured programs. Current execution instructions belong in the [root README](../../README.md) and
[core guide](../../core/README.md).

## Versioning

Keep these version choices distinct, because changing one does not automatically update the others:

| Artifact | Version responsibility | What to review |
| --- | --- | --- |
| Cook engine and CLI | The repository chooses an engine release through the committed wrapper's version and matching platform-package checksums | Command grammar, operation behaviour and the explicit pin change |
| Ingredients and cookbooks | Cookbook authors maintain program data models and recipes through their repositories and builds | Source changes and the native program versions and formats they cover |
| Supplied recipes | Users manage versions and supply the version they intend to apply; the committed `cook.lock` records the source and sha256 that produced each recipe's current ownership | The exact source, its consumed recipe/template dependencies and the `cook.lock` change |
| Native files and marked sections | The consuming program defines its format and semantics | Actual file changes, key paths, section boundaries and program defaults |
| Local batch records | Cook records each batch under the destination's ignored `.cook/batches/<batchId>/` | The recorded batch, captured sources and reversible patches |

The wrapper pin controls the engine package, not recipe versions. Cook does not merge recipe upgrades or reapply setup
when the engine pin changes.

Concrete patch representations, metadata serialization and storage schemas belong to the implementation. See
[configuration formats](configuration-standards.md#defaults-and-format-changes).

## What counts as breaking

Review compatibility by effect rather than by whether an edit merely adds a field or flag. An added reserved command
name can change how an existing bare operand is parsed; a new default can change configuration without changing its
shape. Release notes SHOULD explain affected use and the migration a user must make, because users upgrade at their
own pace.

| Surface | Compatibility concern |
| --- | --- |
| Commands and arguments | Removing or renaming a command, changing operand meaning or argument scope, or changing accepted paths and URLs |
| Approval | Suppressing the actual diff, accepting noninteractive execution as authorization, or changing whole-batch approval into partial selection |
| Ordered operations | Reordering recipes, rejecting intentional overlap, skipping repeated evaluation, or removing output another recipe owns |
| Native output | Changing a program's effective settings, replacing a broader target, losing unrelated keys or comments, or changing marker syntax |
| Ownership | Changing a recipe's short name, which changes its marker IDs and `cook.lock` entry |
| Paths | Changing the invocation-directory destination or rebasing relative source references onto captured snapshots |
| Recovery | Changing recorded information in a way that prevents reversal of a retained batch or misstates its changes |
| Distribution | Changing package/runtime requirements, checksum verification or explicit pinning behaviour |
| Skill guidance | Teaching commands or prerequisites unavailable in the version the guidance describes |

Engine releases use semantic versioning (`MAJOR.MINOR.PATCH`) to signal interface compatibility. Breaking changes belong
in a major release; before 1.0, they may appear in a minor release and should be listed first in its notes, because
users need to assess the change before moving their pin. Release notes should distinguish incompatible behaviour,
additions and corrections. A fix can change rendered bytes or native behaviour, so a patch label is not proof that
applying recipes produces no diff.

The target command set is implicit or explicit apply, revert, `batch list` and `batch inspect`; discard is deferred.
Existing or edited content uses the same actual diff and whole-batch approval flow.

### Output across releases and sources

Changing an engine version, cookbook source, recipe argument or consuming program can affect the requested setup.
Review the actual batch diff against current content. Trusted executable recipes do not carry a universal guarantee
of identical output across machines or releases, or of network-free evaluation.

Every apply invocation evaluates the supplied recipes, even after an identical earlier invocation. An
unchanged result may produce an empty diff. An engine upgrade does not automatically refresh native files or skill
content. Users decide when to invoke the supplied sources, and later recipes can intentionally replace earlier values.

For example, recipe A creates `notes.txt` and sets a key; recipe B sets the same key. Applying A then B previews B's
final value in one combined diff, and B becomes the key's owner; the diff is the only notice. Applying B alone later
leaves A's `notes.txt` intact. Applying a new version of A removes what A's previous version owned and the new version
no longer declares, and the diff shows that removal.

## Supported programs and tools

Ingredients model programs' native files and configuration. Cookbook authors SHOULD document the program versions,
formats and limitations their models cover, because successful file writing does not prove that a program reads the
result as intended. Engine builds and cookbook builds can be separate.

Review native permissions, hook semantics, discovery paths, links and trust settings using the consuming program's
rules. Local, user and managed settings can affect project settings. The consuming program enforces its permissions
during later use. See [program compatibility](../features/compatibility.md) and
[program permissions](../features/policy.md).

## Transparency for people and CI that do not use Cook

Programs consume their native files without invoking Cook merely to read them. Shared instruction sections remain
readable without Cook, and skill discovery follows the consuming program.

A recipe may explicitly change build, test, deploy or CI files, so review those consequences; do not promise that all
setup leaves behaviour unchanged. Teams choose whether CI invokes Cook and which sources and dependencies must be
available to that job.

CI uses the ordinary approval contract. `--batch-approve` precedes operands, displays the final terminal diff and
proceeds without prompting. Noninteractive execution without it does not authorize target writes. See
[CLI approval](../features/cli.md#batch-options-and-approval).

Stopping Cook invocations leaves native content in place. Revert reverses only one recorded batch's changes and shows
the actual diff for whole-batch approval, preserving later edits that do not overlap them. Discarding a recipe's whole
contribution is deferred.

If a batch changed `test` from `old` to `new` and a person then changed an unrelated `other` key, reverting that batch
proposes `new` to `old` and keeps `other`. Had the person changed `test`, the revert would write nothing and list the
conflict. Revert does not reverse external activity by configured programs. See [revert](../features/remove.md).

## Coexistence

Choose declared targets deliberately, because native files can contain settings maintained by several sources.
Whole-file operations affect the whole file; key and section operations preserve unrelated content. Markers bound a
section and name its owner, `cook:begin <recipe>/<region>` and `cook:end <recipe>/<region>`; ownership never blocks a
change. Use native comment syntax, including HTML comments in Markdown.

| Already present | Intended interaction |
| --- | --- |
| Hand-written settings or instruction text | Propose declared changes against current content; preserve unrelated keys or text outside declared sections |
| Other tools' ignore entries and marked sections | Preserve entries outside the declared targets; review overlapping native semantics and contradictory instructions |
| Skills, memory, command folders and installer lock files | Change only supplied declarations; choose a maintenance process that avoids competing updates |
| Real directories, per-entry links or whole-directory symlinks | Review the existing shape and declared scope; resolved batch targets must remain within the destination |
| Native agent skill discovery | The agent decides what it loads and follows ([skills guidance](skills-standards.md#discovery-and-session-guidance)) |
| Local, user or managed program settings | Do not implicitly import them; choose shared versus personal content explicitly, with batch writes confined to the destination |
| Task-runner, build and CI files | Recipes may target native configuration; those programs execute their own tasks independently |
| Trackers, loop runners and working-copy tools | Recipes may configure them; those tools select tasks, update issue state, launch sessions and clean working copies |
| Pushes, pull requests, notifications and MCP services | External tools and people perform these activities |
| Another program's data in `.cook/` or `cook.lock` | Account for it before using these paths |
| Executables on `PATH` or a global Cook install | The wrapper design runs its pinned cached package; use explicit paths when selecting another executable |

Approval, output boundaries and rollback cover declared batch operations, not arbitrary side effects of trusted Groovy
recipe code. Trust sources before evaluating them; the resulting diff is not a sandbox.

## Paths and recovery

The invocation's current working directory is the destination, regardless of executable or recipe location. Declared
writes and deletions stay inside it, including resolution through symlinks. Recipes come from paths inside the
destination repository or `https://` URLs. Relative source references resolve from the referencing recipe's location
or HTTPS URL; capturing a snapshot must not change that base, and a local reference outside the repository is refused.

Save the batch record before the first target write. It lives in the ignored `.cook/batches/<batchId>/`, where the
batch ID is the UTC start time, and keeps one reversible patch per touched file for the combined change, `cook.lock`
included. Capture evaluated recipes, all consumed local and downloaded recipe/template sources, their origins,
arguments, order and result or failure log. On write failure, roll back changes already made by the batch and retain
the record and failure.

Apply needs neither batch history nor `cook.lock`. Losing `.cook/` loses the ability to revert earlier batches, not the
ability to cook; ownership survives in the committed `cook.lock`. Reverting a batch requires its record. The patch
format and metadata schema belong to the implementation. See
[paths and history](../architecture.md#paths-source-snapshots-and-local-history).

## Installation and version pinning

Cook uses platform-specific JVM packages built with `jpackage`, including Java. Distribute the complete package,
not a standalone launcher; package users do not install Java separately.

The committed `./cook` wrapper design pins an engine version and matching package checksums. First use downloads the
pinned platform package, verifies its checksum before execution and caches the complete package locally. Later
invocations reuse that version. Upgrades are explicit reviewed changes to the version and matching checksums;
ordinary invocation does not choose a newer release or automatically reapply recipes.

A matching checksum establishes correspondence with the committed package choice. It does not by itself prove publisher
identity or source safety. Review the source of the pin and checksums. See
[release trust reasoning](../adrs/0021-release-trust.md).

See [installation](../features/installation.md) and [wrapper](../features/wrapper.md) for the design and current local
entry path.
