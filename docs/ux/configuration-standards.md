# Configuration standards

## Objective

**Problem.** Shared configuration can hide conflicting values, mistyped inputs, and credentials. When the scope of a
change is unclear, reviewers cannot tell what it replaces or how to recover it.

**Goal.** Recipe inputs and native output are understandable, the proposed changes are visible before writes, and
recovery records explain the sources and operation that produced them.

## Overview

These standards follow the intended [architecture](../architecture.md). Cook evaluates trusted Groovy recipes into
ordered file operations; ingredients model a program's native files and configuration, and cookbooks hold and ship
ingredients and recipes. The [feature reference](../features/README.md) expands these responsibilities.

See the [root README](../../README.md) for current execution.

## Where files live

The current working directory is the destination, regardless of recipe and executable locations. Declared writes and
deletions stay within the destination, including paths resolved through symlinks. Why: a reviewer must know the boundary
of the batch they approve.

| Content | Location | Purpose |
| --- | --- | --- |
| Native configuration and guidance | Paths declared by recipes within the destination, where the program reads them | Settings, ignore entries, instruction files, skills, and other requested output |
| Recipe and template sources | Paths inside the destination repository or `https://` URLs | Trusted input; relative references resolve from the referencing recipe's location or URL |
| Ownership lock | Committed `cook.lock` at the repository root | Each applied recipe's short name, source, sha256, and owned sections, keys, and files |
| Batch records | The destination's ignored `.cook/batches/<batchId>/` directory | One record per batch: source snapshots and reversible patches for revert |
| Engine wrapper | Committed `./cook` | Pins the engine version and package checksum; first use fetches and caches the platform package |

`.cook/` holds local batch records; committed ownership lives in `cook.lock`. A recipe may use shared guidance under
`.agents/` where that suits the native program.

Record source origins when saving snapshots. A relative template reference in a local recipe resolves from that recipe's
location inside the repository; an HTTPS recipe resolves it against its source URL. Neither resolves from the snapshot
location, and a local reference outside the repository is refused. For example, invoking
`cookbook/recipes/project.groovy` from `/work/project` writes a declared `settings.toml` into `/work/project`, while its
`../templates/settings.toml` source resolves to `/work/project/cookbook/templates/settings.toml`. See [source
paths](../features/render.md#destination-and-source-paths).

## Inputs

Recipe operands are paths inside the destination repository or `https://` URLs. Ordinary relative paths such as
`recipe.groovy` are accepted. Users manage recipe versions and supply the version they intend to apply; `cook.lock`
records which version produced the current ownership. Remote and local sources are trusted executable code, not data
confined by batch approval.

```sh
./cook ./recipe.groovy
./cook apply ./base.groovy --agent=codex ./project.groovy --agent=claude
./cook apply ./a.groovy --source=./b.groovy
./cook apply ./a.groovy ./b.groovy
```

Recipe arguments follow their recipe as `--name=value`; use shell quoting for values containing spaces. Each recipe
starts its own argument scope. In the last two examples, `b.groovy` is respectively an argument value and a second
recipe. Batch options precede operands; placing `--batch-approve` after a recipe receives the normal unknown-option
error. The [CLI reference](../features/cli.md) defines the grammar, including reserved initial command names.

Document cookbook inputs close to the recipes that use them, with their meaning, supported values, defaults, and native
effects. An input format belongs to its cookbook guidance. Engine wrapper pinning is distinct from cookbook builds and
user-supplied recipe versions.

## Defaults and format changes

Make defaults visible in the recipe's guidance so an omitted input has an understandable effect. Keep examples
focused on the user's choices rather than copying every default. When changing a cookbook input or native format,
explain changed meanings and how users can supply the intended version. Why: a familiar key with a new meaning can
produce an unexpected diff.

Respect the native program's format and compatibility rules. Any declared transformation of existing configuration
follows the same diff and whole-batch approval flow as other changes.

## Validation messages

When an input or native document cannot be interpreted, name the source or target, the offending argument or key
path, and what was expected. Include line and column when available, and offer a concrete correction when known.
Why: a person should be able to find the mistake without reconstructing how a recipe reached it.

Do not silently omit a requested setting and present the batch as if it included that change. Explain native support
gaps where relevant. See the [CLI error guidance](cli-standards.md#errors-and-exit-codes).

## Values and native settings

Quote values according to the native format. For example, YAML strings containing `: ` or ` #` need quoting so their
content is not interpreted as a mapping or comment. Document how a recipe interprets paths or globs rather than
assuming every program shares one convention. Distinguish destination paths from source paths as described above.

Permission settings, hook entries, and skill discovery belong to the program that reads them. Preserve unrelated keys
and avoid unnecessary changes to comments, key order, or blank lines. Review the exact native rules and their scope:
programs can differ in matching, precedence, project trust, and local overrides. See
[permissions](../features/policy.md), [native hooks](../features/hooks.md), and [guidance](../features/context.md).

A native hook command may need executables, credentials, a working directory, and safely quoted paths. The program runs
that hook, and undoing the file configuration does not reverse its eventual effects.

## Ordered changes and approval

Apply proposes declared additions and updates against current content. Recipes and their operations compose left to
right; when two recipes write the same section, key, or file, the last one wins and becomes its owner, and the diff
is the only notice. If A sets `test = "A"` and creates `notes.txt`, and B sets `test = "B"`, applying both in that
order shows B's final value and A's new file in one diff. Applying only B later leaves `notes.txt` intact: applying B
never removes A's output. Applying a new version of A removes what A's previous version owned and the new version no
longer declares, and the diff shows that removal.

Whole-file operations address file paths, structured operations address key paths, and shared-text sections use
`cook:begin <recipe>/<region>` and `cook:end <recipe>/<region>` markers in the file format's comment syntax (HTML
comments for Markdown). Markers name the owning recipe and region; ownership never blocks a change. See the
[generated-content standard](generated-content-standards.md#kinds-of-generated-content).

Every apply invocation evaluates the supplied recipe versions against current content. Previous history does not skip a
recipe as already applied; unchanged output may produce an empty diff. Existing and manually edited content uses the
same review flow.

Display the final combined diff in the terminal and ask for approval of the whole batch before changing target files.
With `--batch-approve`, display the diff and proceed without prompting. Noninteractive execution without that flag
does not authorize writes. The CLI accepts the whole batch; choosing individual changes belongs to a separate
interface decision, and the core writes and records only accepted changes. Why: approval must cover the actual
combined effect of ordered operations, including changes to existing configuration.

## Local history and explicit reversal

Before the first target write, save the batch record in the destination's ignored `.cook/batches/<batchId>/`, where the
batch ID is the UTC start time. It holds one reversible patch per touched file, `cook.lock` included; one patch
represents the combined file change and supports forward and reverse application. The record also holds evaluated
recipes, all consumed recipe/template sources (local and downloaded), origins, arguments, execution order, and result or
failure log. On a write failure, roll back changes already made and retain the record and failure. The patch
representation, complete storage layout, and metadata schema belong to the implementation.

Apply requires neither batch history nor `cook.lock`. Losing `.cook/` loses the ability to revert earlier batches,
not the ability to cook; ownership survives in the committed `cook.lock`, and new batches still save their records
before writes. Reverting a batch requires its record.

```sh
./cook batch list
./cook revert 2026-10-14T15-04-05Z
```

Revert applies a recorded batch's reverse patches against current content without evaluating recipes again. It
reverses only that batch's changes. If a batch changed `test` from `old` to `new`, then a person changed an unrelated
`other` key, reverting that batch proposes `new` to `old` and keeps `other`. Had the person changed `test`, the revert
would write nothing and list the conflicting file and key. Revert shows the actual diff for whole-batch approval.

Discarding a recipe's whole contribution is deferred; revert the batches that applied it instead. Leave native
configuration in place when it should remain. See [revert](../features/remove.md).

## No secrets

Keep credentials out of shared recipe sources, configuration, comments, URLs, and arguments that may be displayed or
recorded. Use the native program's supported credential mechanism, such as an environment reference or its own
credential store, rather than embedding a secret in generated output. Why: committed content reaches every reader
of the repository, and diffs, arguments, snapshots, and recovery patches can retain sensitive values locally too.

Ignored history is not a secret store. Review source contents and target values with that retention in mind. Trusted
Groovy code can read inherited credentials and perform arbitrary effects; batch boundaries and rollback do not
confine it or promise automatic redaction.

Keep personal and shared choices deliberate. Local overrides belong to the native program's configuration scopes;
copying a value into shared output changes who can read it and whose behavior it affects. A declared batch target that
holds existing personal settings uses the normal approval flow.
