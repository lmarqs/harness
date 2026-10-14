# CLI standards

## Objective

**Problem.** People and automation need to understand what a command will change, what approval covers, and how to
withdraw or reverse configuration. Inconsistent argument scope and hidden approval rules make those decisions hard.

**Goal.** Cook presents predictable commands and the actual combined diff before target writes. Interactive users
approve the whole [batch](../features/glossary.md); unattended callers explicitly preapprove it.

## Overview

These standards follow the intended [architecture](../architecture.md), with command details in the
[CLI reference](../features/cli.md) and vocabulary in the [glossary](../features/glossary.md). The architecture is the
authority when a supporting standard conflicts with it. See the [root README](../../README.md) for current execution
instructions.

Cook evaluates trusted Groovy recipes into ordered file operations. Approval, destination boundaries and rollback cover
declared batch operations, not arbitrary side effects of recipe code. Trusting a recipe is separate from approving the
changes it proposes.

## Entry points

Document Cook invocations through the committed `./cook` wrapper, which pins an engine version and package checksums.
The [wrapper](../features/wrapper.md) page covers first-use download, checksum verification, caching and upgrades;
[installation](../features/installation.md) covers the distribution contract and current implementation limitations.

The CLI is the public cooking interface. Gradle builds and packages the engine and cookbooks. Cookbook repositories and
builds remain separate from engine releases.

The invocation's current working directory is the destination, regardless of the executable or recipe location; see
[destination and source paths](../features/apply.md#destination-and-source-paths) for the write boundary and how
recipe and template references resolve.

## Command grammar

The [CLI reference](../features/cli.md) defines the commands: apply, implicit or explicit, as the default; `revert`
with a batch ID; `batch list`; and `batch inspect` with a batch ID. It also defines recipe operands, per-recipe
argument scope and the placement of batch options before the first operand. Help, messages and examples follow that
grammar, because callers predict what a command does from its operands and argument scope.

## Apply and revert

Apply evaluates the supplied recipes against current content and `cook.lock` and proposes their declared changes.
Revert reverses one recorded batch's changes without evaluating recipes again. The architecture states the semantics
in [apply, discard, and revert](../architecture.md#apply-discard-and-revert) and
[ownership and the lock](../architecture.md#ownership-and-the-lock); the [revert](../features/revert.md) page works
through conflicts and examples.

### Repeated invocations and batch records

Every apply invocation evaluates the supplied recipes against current content, even after an identical invocation.
When the result is unchanged, report that there are no proposed changes rather than claiming evaluation was skipped,
because evaluation always runs.

Apply works without batch records or `cook.lock`; reverting a batch requires its record
([history availability](../architecture.md#batch-record-availability)).

## Batch review and approval

Cook must display the final combined diff in the terminal before target writes, because the user approves the actual
change to current content. The CLI accepts the whole batch; choosing individual changes belongs to a separate interface
decision.

Without preapproval, ask before changing target files; declining approval leaves batch targets unchanged.
[`--batch-approve`](../features/cli.md#batch-options-and-approval) preapproves the entire operation, apply or revert:
display the diff and proceed without prompting. Existing and edited content follows this same review flow; ownership
never blocks a change.

### Failure recovery

Save the [batch record](../architecture.md#one-record-for-the-batch) before the first target write, so a failed write
can be rolled back and the batch reverted later. If a write fails, roll back changes already made by the batch and
retain the record and failure. Report the failure and recovery outcome clearly so the caller can inspect what
happened.

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

The prefix of a human message depends on its stream:

| Stream | Terminal conditions met | Otherwise |
| --- | --- | --- |
| stderr | `the-cook: `, or one status icon and one space in its place | `the-cook: ` |
| stdout | No prefix, or one status icon and one space | No prefix |

Icons never appear in generated file content or replace the diff itself. See
[message wording](messages-and-docs.md#cli-messages).

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

Use one icon per message, from this set, with the status word still present; the prefix icon is that one icon.
Progress describes the activity; the final message describes its outcome, so an activity icon does not suggest success
after a failure.

### Machine-readable output

CLI results and diagnostics are the human output described under [streams and human output](#streams-and-human-output);
scripts must not treat their wording as a stable data format. The metadata serialization format of batch records belongs
to the implementation.

### Progress

Progress names the activity, such as source loading, batch evaluation, writing or rollback. Off a TTY, use plain
milestone lines on stderr so logs remain readable. Progress must not obscure the final diff or imply that target
writes happened before approval.

## Errors

Every error states what failed, why, and an actionable next step when known. Name the relevant path and, when known,
line and column so the reader can locate the problem. Invalid usage explains the correct argument scope; for example,
a misplaced `--batch-approve` message directs the caller to put it before the first recipe.

Keep routine diagnostics focused on information the caller can act on, rather than internal identifiers or stack traces.
Exit-code meanings are not assigned in this design.

## Non-interactive operation

Noninteractive execution alone does not authorize target writes. Without `--batch-approve`, do not change batch
targets or wait indefinitely for an unavailable answer; explain that approval is required. With that flag, display
the actual diff and proceed without prompting, using the same operations and destination boundaries as interactive
execution. Do not open a pager or editor that an unattended caller would have to close.

## Help text

With no arguments, show help. Present the operation summary, grammar, operand and argument scope, batch approval
behavior, and examples in a consistent order, because readers need to predict how a command works. Examples use
`./cook` and exact recipe paths or `https://` URLs. Explain that apply evaluates supplied recipes while revert uses a
recorded batch's patches, and link to the [CLI reference](../features/cli.md) for details.

Help advertises only the commands in the [command grammar](#command-grammar), because callers act on what help lists.

## Command set

The command set is apply (implicit or explicit), revert, `batch list` and `batch inspect`; discard is deferred.
[What counts as breaking](compatibility-standards.md#what-counts-as-breaking) covers removing or renaming a command.
