# Configuration standards

## Objective

**Problem.** Shared configuration can hide conflicting values, mistyped inputs, and credentials. When the scope of a
change is unclear, reviewers cannot tell what it replaces or how to recover it.

**Goal.** [Recipe](../features/glossary.md) inputs and native output are understandable, the proposed changes are
visible before writes, and batch records explain the sources and operation that produced them.

## Overview

These standards follow the intended [architecture](../architecture.md). Cook evaluates trusted Groovy recipes into
ordered file operations; ingredients model a program's native files and configuration, and cookbooks hold and ship
ingredients and recipes. The [feature reference](../features/README.md) expands these responsibilities.

This page covers where files live, recipe inputs and their defaults, validation messages, native values, permission
rules, hooks and secrets. For composition, approval and revert it links to the pages that define them; generated file
content follows the [generated-content standard](generated-content-standards.md). See the [root README](../../README.md)
for current execution.

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

Record source origins when saving snapshots. Relative template references resolve from the referencing recipe's
location or source URL, never from the snapshot location; a local reference outside the repository is refused.
[Destination and source paths](../features/apply.md#destination-and-source-paths) works through an example.

## Inputs

Recipe operands, `--name=value` recipe arguments and their per-recipe scope follow the
[CLI reference](../features/cli.md). Users manage recipe versions and supply the version they intend to apply;
`cook.lock` records which version produced the current ownership. Remote and local sources are trusted executable code,
not data confined by batch approval.

Document cookbook inputs and their formats close to the recipes that use them, with their meaning, supported values,
defaults, and native effects. Why: a person choosing a value reads the recipe that consumes it.

## Defaults and format changes

Make defaults visible in the recipe's guidance so an omitted input has an understandable effect. Keep examples
focused on the user's choices rather than copying every default. When changing a cookbook input or native format,
explain changed meanings and how users can supply the intended version. Why: a familiar key with a new meaning can
produce an unexpected diff.

Respect the native program's format and compatibility rules.

## Validation messages

When an input or native document cannot be interpreted, name the source or target, the offending argument or key
path, and what was expected. Include line and column when available, and offer a concrete correction when known.
Why: a person should be able to find the mistake without reconstructing how a recipe reached it.

Do not silently omit a requested setting and present the batch as if it included that change. Explain native support
gaps where relevant. See the [CLI error guidance](cli-standards.md#errors).

## Values and native settings

Quote values according to the native format. For example, YAML strings containing `: ` or ` #` need quoting so their
content is not interpreted as a mapping or comment. Document how a recipe interprets paths or globs rather than
assuming every program shares one convention. Distinguish destination paths from source paths as described above.

Permission settings, hook entries, and skill discovery belong to the program that reads them. Preserve unrelated keys
and avoid unnecessary changes to comments, key order, or blank lines. Review the exact native rules and their scope:
programs can differ in matching, precedence, project trust, and local overrides. See
[permission pitfalls](../features/pitfalls.md#permissions) and
[instruction files](generated-content-standards.md#instruction-files-and-conventions).

### Permission rules

Where a program supports allow, ask and deny decisions, use them to express what the team intends to permit, confirm or
prohibit. Record the reason for each group of rules, and review the effects of commands, including what a change makes
broader, rather than assuming a whole executable is harmless. Why: a task runner can invoke other programs, and a broad
prefix rule can permit more than its author intended. Applying or reverting permission configuration does not prove
that it suits every program or user. For each configured program, establish:

- What a rule matches: command prefixes, complete commands, paths, tools or another native form.
- How overlapping rules and unlisted actions are handled.
- Whether confirmation remains available in the mode in which the program runs.
- Which project, local, user or organization settings also affect the decision.
- Whether project trust or another setting is needed before the configuration is loaded.

A cookbook can provide policy inputs for its recipes; their format and prerequisites belong in that cookbook's usage
guide.

### Native hooks

Hook formats, event payloads and failure behavior differ between programs, and a copied hook can run with more authority
than its reviewer expected. Keep program-specific hook guidance with its cookbook's model and recipes, and document the
native program's behavior and any missing support, because not every program supports every purpose or treats failures
identically. Useful hook purposes and what to review:

| Hook purpose | What to review |
| --- | --- |
| Format or lint an edited file | Supported event, file-path handling, command dependencies and cost per edit |
| Check related repository files | Which changes trigger the check and what a failed check does to the session |
| Guard a path before a tool runs | Whether the native event can actually block the operation and what paths it sees |
| Notify a person | Supported event, destination, credentials, message content and failure behavior |

Document what a native hook command needs, such as executables, credentials, runtime permissions, a working directory,
and safely quoted paths, and how it behaves when a dependency is missing, in the recipe's guidance. Review scripts
alongside the settings that call them, and review the combined diff for any replacement of an existing hook command or
event entry from a person or plugin. Why: the program runs that hook later, outside Cook's review, and undoing the file
configuration does not reverse its eventual effects.

## Ordered changes and approval

Recipes compose left to right, and the last write to a section, key, or file wins;
[ordered composition](../architecture.md#ordered-composition-and-declared-targets) defines targets, markers, and
ownership. A declared transformation of existing or manually edited configuration follows the same diff and
[whole-batch approval](../features/cli.md#batch-options-and-approval) as any other change. Why: approval must cover the
actual combined effect of ordered operations, including changes to existing configuration.

## Batch records and explicit reversal

Cook saves a [batch record](../architecture.md#one-record-for-the-batch) in `.cook/batches/<batchId>/` before the first
target write. [Revert](../features/revert.md) reverses one recorded batch's changes. Leave native configuration in
place when it should remain. Why: reverting a batch requires its record.

## No secrets

Keep secrets, credentials, and personal data out of shared recipe sources, templates, instruction files, knowledge,
configuration, comments, URLs, and arguments that may be displayed or recorded. Use the native program's supported
credential mechanism, such as an environment reference or its own credential store, rather than embedding a secret in
generated output. Why: committed content reaches every reader of the repository, and diffs, arguments, and batch records
can retain sensitive values locally too.

Batch records are not a secret store: the terminal diff, captured sources, and reversible patches can retain sensitive
content, including prior values from existing files. Avoid declaring targets that hold secrets, treat the ignored
`.cook/` directory as sensitive local data, and review source contents and target values with that retention in mind.
Trusted Groovy code can read inherited credentials and perform arbitrary effects; batch boundaries and rollback do not
confine it or promise automatic redaction.

Keep personal and shared choices deliberate. Local overrides belong to the native program's configuration scopes;
copying a value into shared output changes who can read it and whose behavior it affects. Personal settings can hold
one-time approvals, private paths, host names, or tokens, so review anything copied from them before distributing it,
and copy personal permission settings into shared configuration only when the team wants those choices. A program can
continue to read personal settings independently of the files a recipe declares. A declared batch target that holds
existing personal settings uses the normal approval flow.
