# Generated-content standards

## Objective

**Problem.** Generated configuration shares files with developers and other tools. Broad replacements, noisy output,
and unclear deletion rules make it hard to judge a change or recover earlier values.

**Goal.** A developer can see the actual proposed change, approve its full scope, and reverse the changes of a
recorded batch. Content outside the declared targets stays intact.

## Overview

These standards follow the intended [architecture](../architecture.md). They cover Cook's declared batch operations. See
the [root README](../../README.md) for current execution. Cook evaluates trusted Groovy recipes; approval, boundaries,
and recovery do not sandbox arbitrary effects of recipe code.

## Kinds of generated content

A recipe declares its targets. Ingredients model programs' native files and configuration; cookbooks supply ingredients
and recipes independently of the engine.

| Target | Address | Review scope |
| --- | --- | --- |
| Whole file | File path | The entire declared file, including existing content it replaces |
| Structured content | File path and key path | The declared keys, with unrelated keys preserved |
| Shared text | File path and begin/end markers | The declared section, with text outside its boundaries preserved |
| Directory or symlink | Declared path, where applicable | The declared filesystem operation |

Native settings, instruction files, skills, and ignore entries may be recipe targets. A template supplies content.
Executing external commands is not a batch action; the program runs its configured native hooks.

## Header

When a recipe includes a generated-content notice, use the file's comment syntax and place it where the native
program permits it: after a script's shebang, for example. Do not add comments to JSON. A useful notice identifies
the source so a reader can find the recipe or template behind the output.

A notice is explanatory text; ownership is recorded in the committed `cook.lock`. Existing content and manual edits
follow the same actual diff and approval flow.

## Managed blocks

Markers bound a section in shared text and name the recipe that owns it and the region within that recipe:
`cook:begin <recipe>/<region>` and `cook:end <recipe>/<region>`. Use the file format's appropriate comment syntax:

```markdown
Text outside the section remains intact.

<!-- cook:begin agentic/context/commands -->
Run the project's build command.
<!-- cook:end agentic/context/commands -->
```

```gitignore
# cook:begin git/ignore
/.cook/
# cook:end git/ignore
```

Markdown uses HTML comments; ignore files, TOML, YAML, and shell files use hash comments where their formats permit
comments. A declared section update preserves unrelated text, comments, and layout outside its boundaries. Manual edits
inside the section appear in the next proposed diff; ownership never blocks a change.

### Block ids

A section ID is `<recipe>/<region>`: the owning recipe's short name, such as `agentic/context`, followed by a region
name chosen by that recipe. Several recipes can therefore keep separate sections in one file. `cook.lock` lists each
recipe's sections by file and ID. Recipes declare the sections they address.

### Instruction files and conventions

Keep shared instructions concise, avoid duplicating existing guidance, and link to detail when useful. Why: programs
that load instructions repeatedly pay for every line. Content and size are recipe configuration choices.

State a convention where people and the native program can find it, using the values that actually apply. Native
configuration remains usable without Cook when it has no runtime dependency on Cook. See
[discard and revert](#discard-and-revert) for batch reversal.

## Keys in native files

A structured operation changes its declared key paths. Preserve unrelated keys and avoid unnecessary changes to their
order, comments, or formatting. Whole-file declarations have broader scope; review the entire replacement rather than
assuming key-level preservation.

For example, a recipe that sets `test` in `settings.toml` preserves an unrelated `other` key. A permission list or hook
setting has the scope the recipe declares. A program enforces its own native settings.

## Ordered operations and repeat application

Recipes and their operations compose left to right against current content. When two recipes write the same
section, key, or file, the last one wins and becomes its owner; the diff is the only notice. If A sets `test = "A"`
and creates `notes.txt`, and B sets `test = "B"`, applying A then B previews B's final value and A's new file in one
batch diff, and B owns `test`. Applying B alone later leaves `notes.txt` intact: applying B never removes A's output.
Applying a new version of A removes what A's previous version owned and the new version no longer declares; the diff
shows that removal.

Every apply evaluates the supplied recipe versions against current content, even after an identical invocation. History
does not skip recipes as already applied. An unchanged result may produce an empty diff. A manual edit appears in the
next diff like any other difference.

## Stable output

Keep generated changes easy to review. Avoid incidental timestamps, usernames, hostnames, random values, or absolute
machine paths unless the requested configuration needs them. Preserve useful native formatting and avoid moving
unrelated lines. Text examples use UTF-8, LF line endings, and a final newline.

Follow declared operation order rather than sorting operations to eliminate overlap. Output depends on current
content, supplied sources, and arguments; trusted executable recipes do not imply identical bytes on every machine
or a network-free evaluation. Engine version pinning does not pin or manage recipe versions.

## Review

Cook displays the final combined diff in the terminal, including changes to existing or manually edited targets.
The CLI accepts the whole batch; choosing individual changes belongs to a separate interface decision. The core
writes and records only accepted changes.

```sh
./cook apply ./base.groovy ./project.groovy
./cook apply --batch-approve ./base.groovy ./project.groovy
```

Without preapproval, ask before changing target files. With `--batch-approve`, display the diff and proceed without
prompting. Noninteractive execution without that flag does not authorize writes. Batch options precede recipe
operands; recipe arguments follow their recipe as `--name=value`. See the [CLI standard](cli-standards.md).

Review source trust as well as the resulting native settings. Arbitrary effects of recipe evaluation are outside the
batch diff and recovery guarantee. Commit and delivery decisions belong to the repository's own workflow.

## Destination and local history

The current working directory is the destination, regardless of the recipe or executable location. Declared writes
and deletions stay within it, including paths resolved through symlinks. Recipes come from paths inside the
destination repository or `https://` URLs. Source references resolve from the recipe's location or URL, not from the
destination or a saved snapshot; a local reference outside the repository is refused.

Before the first target write, save the batch record in the destination's ignored `.cook/batches/<batchId>/` directory,
where the batch ID is the UTC start time. It holds one reversible patch per touched file, `cook.lock` included; multiple
recipes touching one file produce one patch for their combined change. The record also holds the evaluated recipes, all
consumed recipe/template sources (local and downloaded), origins, arguments, execution order, and result or failure log.
One patch supports forward and reverse application; the concrete patch format and metadata schema belong to the
implementation.

On a write failure, roll back changes already made by the batch and retain its record and failure. Apply needs
neither batch history nor `cook.lock`. Losing `.cook/` loses the ability to revert earlier batches; new apply
operations remain possible and record their own batches, and ownership survives in the committed `cook.lock`. Revert
requires the batch's record. See [configuration locations](configuration-standards.md#where-files-live).

## Discard and revert

Revert reverses one recorded batch:

```sh
./cook batch list
./cook revert 2026-10-14T15-04-05Z
```

Revert applies the batch's reverse patches against current content without evaluating recipes again. It reverses
only that batch's changes; later edits that do not overlap them survive. If a later edit overlaps a change the batch
made, revert writes nothing and lists each conflicting file and section. Revert displays the actual diff and uses
whole-batch approval; `--batch-approve` goes before the batch ID when preapproval is intended. Reverting a batch also
restores the `cook.lock` ownership it changed.

If an apply changes `test` from `old` to `new`, and a person later changes an unrelated `other` key, reverting that
batch proposes `new` to `old` and keeps the edit to `other`. If the person had changed `test` instead, the revert
refuses and names the file and `test`. Discarding a recipe's whole contribution is deferred; revert the batches that
applied it instead. See the [operation reference](../features/remove.md).

To keep native configuration, leave it in place.
