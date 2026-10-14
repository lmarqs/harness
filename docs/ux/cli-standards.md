# CLI standards

## Objective

**Problem.** People and automation need to understand what a command will change, what approval covers, and how to
withdraw or reverse configuration. Inconsistent argument scope and hidden approval rules make those decisions hard.

**Goal.** Cook presents predictable commands and the actual combined diff before target writes. Interactive users
approve the whole batch; unattended callers explicitly preapprove it.

## Overview

These standards follow the intended [architecture](../architecture.md), with command details in the
[CLI reference](../features/cli.md) and vocabulary in the [glossary](../features/glossary.md). The architecture is the
authority when a supporting standard conflicts with it. These requirements do not establish that the target behaviour
is implemented; see the [root README](../../README.md) for current execution instructions.

Cook evaluates trusted Groovy recipes into ordered file operations. Approval, destination boundaries and rollback
cover declared batch operations, not arbitrary side effects of recipe code. Trusting a recipe is separate from
approving the changes it proposes. Cook does not run a development lifecycle or orchestrate agent sessions.

## Entry points

Document cooking invocations through the committed `./cook` wrapper. It pins an engine version and package checksums,
downloads and verifies the platform package on first use, and reuses the local cache. The JVM package includes Java;
upgrades are explicit pin and checksum changes. See [installation](../features/installation.md) and
[wrapper](../features/wrapper.md) for the distribution contract and current implementation limitations.

The CLI is the public cooking interface. Gradle builds and packages the engine and cookbooks; it is not a second
public cooking interface. Cookbook repositories and builds remain separate from engine releases.

The invocation's current working directory is the destination, regardless of the executable or recipe location.
Declared writes and deletions stay inside that directory, including through symlinks. Relative recipe/template
source references resolve from the referencing recipe's original location, or its URL for HTTPS sources; saving a
snapshot does not rebase those references. See [paths and history](../architecture.md#paths-source-snapshots-and-local-history).

## Command grammar

```text
./cook [batch-options] <recipe> [recipe-arguments] [<recipe> [recipe-arguments]]...
./cook apply [batch-options] <recipe> [recipe-arguments] [<recipe> [recipe-arguments]]...
./cook discard [batch-options] <recipe> [recipe-arguments] [<recipe> [recipe-arguments]]...
./cook revert [batch-options] <run-id>
```

`apply` is the default. With no arguments, `./cook` shows help. Initial bare `apply`, `discard` and `revert` select
operations. A file named `revert` can be supplied as `./revert`, so its path is unambiguous.

Recipe operands are exact file paths or supported source URIs: local paths, `file://` and `https://`. Ordinary
relative paths such as `recipe.groovy` are accepted. There is no implicit cookbook-name lookup.

```sh
./cook recipe.groovy
./cook apply ./recipe.groovy
./cook ./revert
./cook file:///path/to/recipe.groovy
./cook https://example.com/recipes/project.groovy
./cook discard --batch-approve ./recipe.groovy
# Replace RUN_ID with the recorded run's identifier.
./cook revert --batch-approve RUN_ID
```

### Argument scope and ordering

Batch options precede the first recipe operand, or the run ID for revert. Recipe arguments follow their recipe and
use `--name=value`. Each following recipe starts its own argument scope. Shell-quote values containing spaces so
they reach Cook as one argument.

```sh
./cook apply --batch-approve ./base.groovy --agent=codex ./project.groovy --agent=claude
./cook ./recipe.groovy --instructions='Keep existing comments.'
```

Here `--agent=codex` belongs to `base.groovy`; `--agent=claude` belongs to `project.groovy`. Recipes and their
operations compose left to right. Later operations can replace earlier values at the same target. The terminal
shows the combined final result, rather than approving intermediate results separately.

An argument value that looks like a path remains an argument value:

```sh
./cook apply ./a.groovy --source=./b.groovy
./cook apply ./a.groovy ./b.groovy
```

The first invocation evaluates only `a.groovy`, with `./b.groovy` as its `source` argument value. The second evaluates
both recipes, in order. This grammar does not select a recipe parameter-injection API.

A misplaced batch option after a recipe receives the normal unknown-option error:

```sh
# Incorrect: batch option after the recipe
./cook apply ./a.groovy --batch-approve
# Correct: batch option before the first recipe
./cook apply --batch-approve ./a.groovy
```

## Apply, discard, and revert

| Operation | Basis | Proposed change |
| --- | --- | --- |
| Apply, implicit or explicit | Supplied recipe versions evaluated against current content | Declared additions and updates |
| Discard | Supplied recipe versions evaluated against current content | Delete their declared contributions, including edited values, while preserving unrelated content |
| Revert | A recorded run's history, without evaluating recipes again | Reverse that operation to its prior values against current content, preserving unrelated content |

Applying recipe B does not implicitly remove unrelated outputs from recipe A. Discard uses the recipe version the
user supplies; Cook does not infer an earlier version or maintain persistent recipe identity. For file, key and
marked-section targets, see [declared targets](../architecture.md#ordered-composition-and-declared-targets). Markers
identify text boundaries, not ownership, and use the file format's comment syntax, including HTML comments in Markdown.

For example, a run changes a `test` key from `old` to `new`, then a person changes it to `custom`. Discarding the
recipe that declares `test` proposes deleting that key. Reverting the original run instead proposes changing
`custom` to `old`. Both preserve unrelated keys and show the actual diff; manual edits do not create a drift veto.
See [discard and revert](../features/remove.md) for the full distinction.

### Repeated invocations and history

Every apply/discard invocation evaluates the supplied recipes against current content, even after an identical
invocation. History must not mark recipes as already applied or skip evaluation. An unchanged result may produce
an empty diff; report that there are no proposed changes rather than claiming evaluation was skipped.

Apply and discard require neither prior run history nor an ownership database. Losing the destination's ignored
`.cook/` directory loses local recovery information, not the ability to cook. Reverting a particular run requires
that run's history. See [local history](../architecture.md#one-record-for-the-batch) for the recorded information.

## Batch review and approval

Cook MUST display the final combined diff in the terminal before target writes, because the user approves the
actual change to current content. Approval covers the whole batch; selecting individual files or hunks is outside
this design. Diff visibility does not depend on a verbose flag or a separate preview command.

Without preapproval, ask before changing target files. Declining approval leaves batch targets unchanged. With
`--batch-approve`, display the diff and proceed without prompting. This flag preapproves the entire operation,
including discard or revert, so its meaning is the same across all three operations.

Existing and edited content follows this same review flow, without ownership reconciliation or a refusal merely
because a developer edited a target. The target CLI has no `--adopt`, adoption alias or separate adoption step.
`discard` is a command, not `--discard`. The only preapproval spelling is `--batch-approve`; `--batch-approved`,
`--approve` and `--yes` are not aliases. There is no separate `--force` gate for hand-edited targets.

### Failure recovery

Save recovery patches before the first target write. Record one operation for the whole batch under the destination's
ignored `.cook/`, with a separate run record and one reversible patch per touched file. Capture evaluated recipes,
all consumed recipe/template sources (local and downloaded), origins, arguments, execution order, operation and result
or failure log. One patch supports forward and reverse application; separate copies are unnecessary.

If a write fails, roll back changes already made by the batch and retain the patches and failure record. Report the
failure and recovery outcome clearly so the caller can inspect what happened. These guarantees cover declared batch
operations; arbitrary recipe-code side effects remain outside them. Patch representation, metadata serialization,
storage layout and mechanics for reversing overlapping edits remain separate implementation design work.

## Output

### Streams and human output

Keep results, including the actual batch diff and outcome summary, on stdout. Put errors, warnings and progress on
stderr so diagnostics remain distinguishable from the proposed changes. Summaries name the requested operation and
its outcome; a proposal must not be presented as a completed write.

Use short messages and explicit status words. Color or icons may reinforce a word, never replace it, because logs
and some readers cannot rely on decoration. Do not decorate diff content: it must remain readable as the actual
proposed change.

### Color, emoji and terminal control

Use color and cursor control only when the stream is a TTY, `NO_COLOR` is unset or empty, and `TERM` is not `dumb`.
Test each stream separately because stdout and stderr can be redirected independently. Output to a pipe or file
contains no ANSI escapes, spinners or carriage-return progress animations.

An icon may prefix a human message under those terminal conditions, followed by one space. Otherwise stderr
messages use `the-cook: ` and stdout results have no decorative prefix. Icons never appear in generated file content
or replace the diff itself. See [message wording](messages-and-docs.md#cli-messages).

### Icons

Retain a small set of kitchen/lab icons for terminal messages. Their meanings describe activities and outcomes,
without assigning exit codes or implying lifecycle stages.

| Icon | Kind |
| --- | --- |
| 😎 | Result or information |
| 🍳 | Declared file operations being written |
| 📖 | Recipe or documentation content being shown |
| 🧂 | Warning |
| 💥 | Error |
| ⛔ | Refused operation or missing approval |
| 🧯 | Reversal or rollback |
| ⏳ | Waiting, such as a package download |

Use one icon per message, from this set, with the status word still present. Progress describes the activity;
the final message describes its outcome, so an activity icon does not suggest success after a failure.

### JSON

The agreed CLI does not specify a `--json` option or machine-output schema. This standard does not introduce one.
Run history also has no mandated metadata serialization format; removing ownership state does not prohibit JSON.

### Progress

Progress names the activity, such as source loading, batch evaluation, writing or rollback. Off a TTY, use plain
milestone lines on stderr so logs remain readable. Progress must not obscure the final diff or imply that target
writes happened before approval.

## Errors and exit codes

Every error states what failed, why, and an actionable next step when known. Name the relevant path and, when known,
line and column so the reader can locate the problem. Invalid usage explains the correct argument scope; for example,
a misplaced `--batch-approve` message directs the caller to put it before the first recipe.

Keep routine diagnostics focused on information the caller can act on, rather than internal identifiers or stack
traces. This design does not assign new exit-code meanings, retry codes or hook-triggered failure events. The former
lifecycle engine's code table does not define the file-operation CLI contract.

## Non-interactive operation

Noninteractive execution alone does not authorize target writes. Without `--batch-approve`, do not change batch
targets or wait indefinitely for an unavailable answer; explain that approval is required. With that flag, display
the actual diff and proceed without prompting, using the same operations and destination boundaries as interactive
execution. Do not open a pager or editor that an unattended caller would have to close.

## Help text

With no arguments, show help. Present the operation summary, grammar, operand and argument scope, batch approval
behaviour, and examples in a consistent order, because readers need to predict how a command works. Examples use
`./cook` and exact recipe paths or supported URIs. Explain that discard evaluates supplied recipes while revert uses
a recorded run's history, and link to the [CLI reference](../features/cli.md) for details.

Help must not advertise retired lifecycle/management commands as part of this target contract. Ingredient/recipe
APIs, nesting, station roles, annotations, injection and lifecycle hooks require their own discussion; help and
examples must not turn exploratory model syntax into a normative API.

## Deprecation

The target command set is apply (implicit or explicit), discard and revert. Retired lifecycle/management commands,
adoption options and tentative approval spellings are not compatibility aliases. This documentation alignment does
not introduce a deprecation window or an updater; implementation changes remain separate work.
