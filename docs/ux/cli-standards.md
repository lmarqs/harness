# CLI standards

## Objective

**Problem.** People and automation need to understand what a command will change, what approval covers, and how to
withdraw or reverse configuration. Inconsistent argument scope and hidden approval rules make those decisions hard.

**Goal.** Cook presents predictable commands and the actual combined diff before target writes. Interactive users
approve the whole batch; unattended callers explicitly preapprove it.

## Overview

These standards follow the intended [architecture](../architecture.md), with command details in the
[CLI reference](../features/cli.md) and vocabulary in the [glossary](../features/glossary.md). The architecture is the
authority when a supporting standard conflicts with it. See the [root README](../../README.md) for current execution
instructions.

Cook evaluates trusted Groovy recipes into ordered file operations. Approval, destination boundaries and rollback cover
declared batch operations, not arbitrary side effects of recipe code. Trusting a recipe is separate from approving the
changes it proposes.

## Entry points

Document cooking invocations through the committed `./cook` wrapper. It pins an engine version and package checksums,
downloads and verifies the platform package on first use, and reuses the local cache. The JVM package includes Java;
upgrades are explicit pin and checksum changes. See [installation](../features/installation.md) and
[wrapper](../features/wrapper.md) for the distribution contract and current implementation limitations.

The CLI is the public cooking interface. Gradle builds and packages the engine and cookbooks. Cookbook repositories and
builds remain separate from engine releases.

The invocation's current working directory is the destination, regardless of the executable or recipe location. Declared
writes and deletions stay inside that directory, including through symlinks. Recipes come from paths inside the
destination repository or `https://` URLs. Relative recipe/template references resolve from the referencing recipe's
location, or against its URL for HTTPS sources; a local reference outside the repository is refused. See [paths and
history](../architecture.md#paths-source-snapshots-and-local-history).

## Command grammar

```text
./cook [batch-options] <recipe> [recipe-arguments] [<recipe> [recipe-arguments]]...
./cook apply [batch-options] <recipe> [recipe-arguments] [<recipe> [recipe-arguments]]...
./cook revert [batch-options] <batchId>
./cook batch list
./cook batch inspect <batchId>
```

`apply` is the default. With no arguments, `./cook` shows help. Initial bare `apply`, `revert` and `batch` select
commands. A file named `revert` can be supplied as `./revert`, so its path is unambiguous. `batch list` and
`batch inspect` only read recorded batches.

Recipe operands are paths inside the destination repository or `https://` URLs. Ordinary relative paths such as
`recipe.groovy` are accepted.

```sh
./cook recipe.groovy
./cook apply ./recipe.groovy
./cook ./revert
./cook https://example.com/recipes/project.groovy
./cook revert --batch-approve 2026-10-14T15-04-05Z
./cook batch list
./cook batch inspect 2026-10-14T15-04-05Z
```

### Argument scope and ordering

Batch options precede the first recipe operand, or the batch ID for revert. Recipe arguments follow their recipe and
use `--name=value`. Each following recipe starts its own argument scope. Shell-quote values containing spaces so
they reach Cook as one argument.

```sh
./cook apply --batch-approve ./base.groovy --agent=codex ./project.groovy --agent=claude
./cook ./recipe.groovy --instructions='Keep existing comments.'
```

Here `--agent=codex` belongs to `base.groovy`; `--agent=claude` belongs to `project.groovy`. Recipes and their
operations compose left to right. When two recipes write the same section, key or file, the last one wins and becomes
its owner; the diff is the only notice. The terminal shows the combined final result.

An argument value that looks like a path remains an argument value:

```sh
./cook apply ./a.groovy --source=./b.groovy
./cook apply ./a.groovy ./b.groovy
```

The first invocation evaluates only `a.groovy`, with `./b.groovy` as its `source` argument value. The second evaluates
both recipes, in order.

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
| Apply, implicit or explicit | Supplied recipe versions evaluated against current content and `cook.lock` | Declared additions and updates, plus removal of what each recipe owned before and no longer declares |
| Revert | A recorded batch's reverse patches, without evaluating recipes again | Reverse only that batch's changes against current content, preserving later non-overlapping edits |

Applying recipe B does not remove output that recipe A owns. Applying a new version of recipe A removes what A's
previous version owned and the new version no longer declares, and the diff shows that removal. The committed
`cook.lock` records each recipe's short name, source, sha256 and owned sections, keys and files. For file, key and
marked-section targets, see [declared targets](../architecture.md#ordered-composition-and-declared-targets). Section
markers name the owning recipe and region, `cook:begin <recipe>/<region>` and `cook:end <recipe>/<region>`, in the
file format's comment syntax, including HTML comments in Markdown. Ownership never blocks a change.

For example, a batch changes a `test` key from `old` to `new`, then a person changes an unrelated `other` key.
Reverting that batch proposes changing `test` back to `old` and keeps the edit to `other`. If the later edit had
changed `test` instead, the revert writes nothing and lists the conflicting file and key. Discarding a recipe's whole
contribution is deferred; revert the batches that applied it instead. See [revert](../features/remove.md) for details.

### Repeated invocations and history

Every apply invocation evaluates the supplied recipes against current content, even after an identical
invocation. History must not mark recipes as already applied or skip evaluation. An unchanged result may produce
an empty diff; report that there are no proposed changes rather than claiming evaluation was skipped.

Apply requires neither batch history nor `cook.lock`; without a lock entry, it removes nothing on a recipe's behalf.
Losing the destination's ignored `.cook/` directory loses the ability to revert earlier batches, not the ability to
cook; ownership survives in the committed `cook.lock`. Reverting a batch requires its record. See [local
history](../architecture.md#one-record-for-the-batch) for the recorded information.

## Batch review and approval

Cook MUST display the final combined diff in the terminal before target writes, because the user approves the actual
change to current content. The CLI accepts the whole batch; choosing individual changes belongs to a separate interface
decision, and the core writes and records only the changes it receives as accepted.

Without preapproval, ask before changing target files. Declining approval leaves batch targets unchanged. With
`--batch-approve`, display the diff and proceed without prompting. This flag preapproves the entire operation,
including revert, so its meaning is the same for apply and revert.

Existing and edited content follows this same review flow; ownership never blocks a change. The only preapproval
spelling is `--batch-approve`.

### Failure recovery

Save the batch record before the first target write. Record the whole batch in the destination's ignored
`.cook/batches/<batchId>/`, where the batch ID is the UTC start time, with one reversible patch per touched file,
`cook.lock` included. Capture evaluated recipes, all consumed recipe/template sources (local and downloaded), origins,
arguments, execution order and result or failure log. One patch supports forward and reverse application.

If a write fails, roll back changes already made by the batch and retain the patches and failure record. Report the
failure and recovery outcome clearly so the caller can inspect what happened. These guarantees cover declared batch
operations; arbitrary recipe-code side effects remain outside them. Patch representation, metadata serialization
and storage layout belong to the implementation.

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

Terminal messages use a small set of kitchen/lab icons. Each icon marks an activity or an outcome.

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

CLI results and diagnostics are the human output described under [streams and human output](#streams-and-human-output);
scripts must not treat their wording as a stable data format. The metadata serialization format of batch records belongs
to the implementation.

### Progress

Progress names the activity, such as source loading, batch evaluation, writing or rollback. Off a TTY, use plain
milestone lines on stderr so logs remain readable. Progress must not obscure the final diff or imply that target
writes happened before approval.

## Errors and exit codes

Every error states what failed, why, and an actionable next step when known. Name the relevant path and, when known,
line and column so the reader can locate the problem. Invalid usage explains the correct argument scope; for example,
a misplaced `--batch-approve` message directs the caller to put it before the first recipe.

Keep routine diagnostics focused on information the caller can act on, rather than internal identifiers or stack traces.
This design does not assign exit-code meanings.

## Non-interactive operation

Noninteractive execution alone does not authorize target writes. Without `--batch-approve`, do not change batch
targets or wait indefinitely for an unavailable answer; explain that approval is required. With that flag, display
the actual diff and proceed without prompting, using the same operations and destination boundaries as interactive
execution. Do not open a pager or editor that an unattended caller would have to close.

## Help text

With no arguments, show help. Present the operation summary, grammar, operand and argument scope, batch approval
behaviour, and examples in a consistent order, because readers need to predict how a command works. Examples use
`./cook` and exact recipe paths or `https://` URLs. Explain that apply evaluates supplied recipes while revert uses a
recorded batch's patches, and link to the [CLI reference](../features/cli.md) for details.

Help advertises only the commands in the [command grammar](#command-grammar). Help and examples must not turn
exploratory model syntax into a normative API.

## Deprecation

The target command set is apply (implicit or explicit), revert, `batch list` and `batch inspect`; discard is deferred.
[What counts as breaking](compatibility-standards.md#what-counts-as-breaking) covers removing or renaming a command.
