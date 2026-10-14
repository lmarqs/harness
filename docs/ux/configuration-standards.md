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

This page covers where files live, recipe inputs and their defaults, validation messages, native values and secrets.
For composition, approval and revert it links to the pages that define them; generated file content follows the
[generated-content standard](generated-content-standards.md). See the [root README](../../README.md) for current
execution.

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
[permissions](../features/policy.md), [native hooks](../features/hooks.md), and [guidance](../features/context.md).

Document what a native hook command needs, such as executables, credentials, a working directory, and safely quoted
paths, in the recipe's guidance. Why: the program runs that hook later, outside Cook's review, and undoing the file
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

Keep credentials out of shared recipe sources, configuration, comments, URLs, and arguments that may be displayed or
recorded. Use the native program's supported credential mechanism, such as an environment reference or its own
credential store, rather than embedding a secret in generated output. Why: committed content reaches every reader
of the repository, and diffs, arguments, and batch records can retain sensitive values locally too.

Batch records are not a secret store. Review source contents and target values with that retention in mind. Trusted
Groovy code can read inherited credentials and perform arbitrary effects; batch boundaries and rollback do not
confine it or promise automatic redaction.

Keep personal and shared choices deliberate. Local overrides belong to the native program's configuration scopes;
copying a value into shared output changes who can read it and whose behavior it affects. A declared batch target that
holds existing personal settings uses the normal approval flow.
