# Generated-content standards

## Objective

**Problem.** Generated configuration shares files with developers and other tools. Broad replacements, noisy output,
and unclear deletion rules make it hard to judge a change or recover earlier values.

**Goal.** A developer can see the actual proposed change, approve its full scope, and recover the prior values of a
recorded operation. Content outside the declared targets stays intact.

## Overview

These standards follow the intended [architecture](../architecture.md). They cover Cook's declared batch operations;
they do not establish that the target behavior is implemented. See the [root README](../../README.md) for current
execution. Cook evaluates trusted Groovy recipes; approval, boundaries, and recovery do not sandbox arbitrary effects
of recipe code.

## Kinds of generated content

A recipe declares its targets. There is no required catalog of engine-owned files, seeded files, or modules.
Ingredients model programs' native files and configuration; cookbooks supply ingredients and recipes independently
of the engine.

| Target | Address | Review scope |
| --- | --- | --- |
| Whole file | File path | The entire declared file, including existing content it replaces |
| Structured content | File path and key path | The declared keys, with unrelated keys preserved |
| Shared text | File path and begin/end markers | The declared section, with text outside its boundaries preserved |
| Directory or symlink | Declared path, where applicable | The declared filesystem operation |

Native settings, instruction files, skills, and ignore entries may be recipe targets. A template supplies content;
using a template does not establish a write-once rule or a separate removal contract. Executing external commands is
not a batch action. Configuring a program's native hooks does not make Cook the hook runner.

## Header

When a recipe includes a generated-content notice, use the file's comment syntax and place it where the native
program permits it: after a script's shebang, for example. Do not add comments to JSON. A useful notice identifies
the source so a reader can find the recipe or template behind the output.

A notice is explanatory text, not an ownership record. It does not require a manifest, a schema number, or a special
command for editing the file. Existing content and manual edits follow the same actual diff and approval flow.

## Managed blocks

Markers identify boundaries in shared text, not ownership. Use the file format's appropriate comment syntax:

```markdown
Text outside the section remains intact.

<!-- cook:commands -->
Run the project's build command.
<!-- /cook:commands -->
```

```gitignore
# cook:history
/.cook/
# /cook:history
```

Markdown uses HTML comments; ignore files, TOML, YAML, and shell files use hash comments where their formats permit
comments. A declared section update preserves unrelated text, comments, and layout outside its boundaries. Manual
edits inside the section appear in the next proposed diff and do not trigger an adoption gate.

### Block ids

An ID identifies a section's begin/end boundaries in its file. It is not a persistent recipe identity or an
engine-wide module catalog. Recipes declare the sections they address; there is no required closed list of IDs,
block-class system, or migration protocol for them.

### Instruction files and conventions

Keep shared instructions concise, avoid duplicating existing guidance, and link to detail when useful. Why: programs
that load instructions repeatedly pay for every line. Content and size are recipe configuration choices, without a
fixed engine line budget or a context-injection API.

State a convention where people and the native program can find it, using the values that actually apply. Native
configuration remains usable without Cook when it has no runtime dependency on Cook. Keeping that configuration is a
choice to leave it in place; no eject operation or block class is needed. See [discard and revert](#discard-and-revert)
for deliberate deletion and history-based reversal.

## Keys in native files

A structured operation changes its declared key paths rather than claiming the whole file. Preserve unrelated keys
and avoid unnecessary changes to their order, comments, or formatting. Whole-file declarations have broader scope;
review the entire replacement rather than assuming key-level preservation.

For example, a recipe that sets `test` in `settings.toml` preserves an unrelated `other` key. A permission list or
hook setting has the scope the recipe declares. Cook does not require imported permission provenance, safety-floor
rules, or a universal hook entry. A program enforces its own native settings.

## Ordered operations and repeat application

Recipes and their operations compose left to right against current content. Overlap is intentional: later
instructions may replace earlier values. If A sets `test = "A"` and creates `notes.txt`, and B sets `test = "B"`,
applying A then B previews B's final value and A's new file in one batch diff. Applying B alone later leaves
`notes.txt` intact. Omitting A does not withdraw its outputs.

Every apply or discard evaluates the supplied recipe versions against current content, even after an identical
invocation. History does not skip recipes as already applied. An unchanged result may produce an empty diff. A
manual edit is not a drift or ownership veto; there is no `--adopt`, adoption alias, or separate adoption step.

## Stable output

Keep generated changes easy to review. Avoid incidental timestamps, usernames, hostnames, random values, or absolute
machine paths unless the requested configuration needs them. Preserve useful native formatting and avoid moving
unrelated lines. Text examples use UTF-8, LF line endings, and a final newline.

Follow declared operation order rather than sorting operations to eliminate overlap. Output depends on current
content, supplied sources, and arguments; trusted executable recipes do not imply identical bytes on every machine
or a network-free evaluation. Engine version pinning does not pin or manage recipe versions.

## Review

Cook displays the final combined diff in the terminal, including changes to existing or manually edited targets.
Approval covers the whole batch; partial selection of files or hunks is outside this design.

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
and deletions stay within it, including paths resolved through symlinks. Source references resolve from the recipe's
original location or URL, not from the destination or a saved snapshot.

Before the first target write, save one reversible patch per touched file under the destination's ignored `.cook/`
directory. Multiple recipes touching one file produce one patch for their combined change. Keep a separate run
record for the whole batch, with the evaluated recipes, all consumed recipe/template sources (local and downloaded),
origins, arguments, execution order, operation, and result or failure log. One patch supports forward and reverse
application; no concrete patch format or metadata schema is selected here.

On a write failure, roll back changes already made by the batch and retain its patches and failure record. Apply and
discard need no previous history or ownership database. Losing `.cook/` loses local recovery information; new apply
and discard operations remain possible and record their own recovery information. Revert requires the recorded run's
history. See [configuration locations](configuration-standards.md#where-files-live).

## Discard and revert

Use explicit operations rather than ownership reconciliation or automatic removal:

```sh
./cook discard ./recipe.groovy
./cook revert RUN_ID
```

Discard evaluates the supplied recipe version and proposes deleting its declared contributions, even when their
current values differ. Revert uses the recorded operation's history to restore its prior values against current
content, preserving unrelated content. Both display the actual diff and use whole-batch approval;
`--batch-approve` goes before the recipe operand or run ID when preapproval is intended.

If an apply changes `test` from `old` to `new`, and a person later changes it to `custom`, discard proposes deleting
`test`; reverting the original run proposes `custom` to `old`. Neither refuses solely because of the manual edit.
Deleting `test` preserves unrelated keys. Users supply the recipe version they intend to discard; Cook does not infer
an earlier version from a persistent recipe identity. See the [operation reference](../features/remove.md).

To keep native configuration, leave it in place. Discard does not restore adopted originals, and neither operation
requires removal of the wrapper, recipe sources, or unrelated setup as a group.
