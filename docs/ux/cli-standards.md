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
The [wrapper](../features/wrapper.md) page covers first-use download, checksum verification, caching and upgrades; the
root README's [install section](../../README.md#install) covers current local packaging and its limitations.

The CLI is the public cooking interface. Gradle builds and packages the engine and cookbooks. Cookbook repositories and
builds remain separate from engine releases.

The invocation's current working directory is the destination, regardless of the executable or recipe location; see
[destination and source paths](../features/apply.md#destination-and-source-paths) for the write boundary and how
recipe and template references resolve.

## Command grammar

The [CLI reference](../features/cli.md) defines the commands: apply, implicit or explicit, as the default; `discard`
with recipes; and `help` with recipes. It also defines recipe
operands, per-recipe argument scope and the placement of batch options before the first operand. Help, messages and
examples follow that grammar, because callers predict what a command does from its operands and argument scope.

Each argument has one spelling and one position, and an action other than applying is a command, not an option. A
second accepted form for the same meaning is refused with a message naming the one form, because every alternative is
another reading a caller, a document and a test must account for; `--help` therefore stands alone, and reading a
recipe is `help <recipe>`.

Recipe arguments take two forms, `--name value` and `--name=value`, with one meaning. Callers type the space form in
every other command-line tool, and a refusal of it is an error they meet on their first invocation; the `=` form
carries a value that looks like a recipe operand. A word is a recipe by its marks alone, `/`, `:` or a `.groovy`
ending, and a yes-or-no param never takes the next word, so no word's reading depends on what the shell expanded.

## Apply and discard

Apply evaluates the supplied recipes against current content and `cook.lock` and proposes their declared changes.
Discard removes what `cook.lock` lists for each named recipe without evaluating it. The architecture states the
semantics in [apply and discard](../architecture.md#apply-and-discard) and
[ownership and the lock](../architecture.md#ownership-and-the-lock); the [discard](../features/discard.md) page works
through an example.

### Repeated invocations

Every apply invocation evaluates the supplied recipes against current content, even after an identical invocation.
When the result is unchanged, report that there are no proposed changes rather than claiming evaluation was skipped,
because evaluation always runs.

Apply works without `cook.lock`; discard needs it, because the lock is its only record of what a recipe owns.

## Batch review and approval

Cook must display the final combined diff in the terminal before target writes, because the user approves the actual
change to current content. The CLI accepts the whole batch; choosing individual changes belongs to a separate interface
decision.

Without preapproval, ask before changing target files; declining approval leaves batch targets unchanged.
[`--batch-approve`](../features/cli.md#batch-options-and-approval) preapproves the entire operation, apply or discard:
display the diff and proceed without prompting. Existing and edited content follows this same review flow; ownership
never blocks a change.

### Failure recovery

If a write fails, roll back changes already made by the batch, so the files stay as they were. Report the failure
and the rollback clearly so the caller can tell what happened.

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
scripts must not treat their wording as a stable data format.

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

With no arguments, with `help` alone or with `--help` alone, show help. `help <recipe>...` shows each recipe's help in
order without running it, because a recipe inside an artifact cannot be read with a pager. Present the operation
summary, grammar, operand and argument scope, batch approval behavior, and examples in a consistent order, because
readers need to predict how a command works. Examples use `./cook` and exact recipe paths, `https://` URLs or
coordinates. Explain that apply evaluates supplied recipes while discard reads only `cook.lock`, and link to the
[CLI reference](../features/cli.md) for details.

Help advertises only the commands in the [command grammar](#command-grammar), because callers act on what help lists.

## Command set

The command set is apply (implicit or explicit), `discard` and `help`. Reserving `discard` and `help` means a recipe
file named `discard` or `help` is given as a path, such as `./help`.
[What counts as breaking](compatibility-standards.md#what-counts-as-breaking) covers removing or renaming a command.

## Prior art

Tools that write files a person may already have, and how each treats an existing file, as their documentation or
source states it:

| Tool | When the file exists | Override | Keep the existing file | Status per file |
| --- | --- | --- | --- | --- |
| Rails generators (Thor) | Asks `Overwrite <file>? [Ynaqdhm]`, with `d` for a diff and `a` for all | `-f`, `--force` | `-s`, `--skip` | `create`, `identical`, `force`, `skip`, `conflict`, also under `--pretend` |
| Angular `ng generate` | Not documented | `--force` | Not documented | Not documented |
| Yeoman | Asks before each write that overwrites content | Not documented | Not documented | Not documented |
| Copier | Asks | `--overwrite`, which overwrites without asking | `skip_if_exists`, declared per file by the template | Not documented |
| cookiecutter | Not documented | `-f`, `--overwrite-if-exists` | `-s`, `--skip-if-file-exists` | Not documented |
| chezmoi | Asks when the file changed since chezmoi last wrote it | `--force`, which applies without prompting | Not documented | Not documented |
| GNU Stow | Records a conflict and refuses to proceed | `--adopt`, which moves the existing file into the package | Not documented | Not documented |
| Kubernetes server-side apply | Rejects a field another field manager owns | `--force-conflicts` | Leave the field out of the applied configuration | Not documented |
| Ansible `copy` | Overwrites; `force` defaults to yes | Not applicable | `force: false`, which writes only when the destination is absent | Not documented |

Go's `flag` package takes a bare `-flag` for a boolean flag and turns it off with `-flag=false`. It refuses
`-flag value` for a boolean, because a shell wildcard after the flag would change its meaning.

What Cook takes from them:

- The default refuses: no tool keeps an existing file silently. Cook's refusal names every file and recipe, as Stow
  and server-side apply refuse, and the approval prompt shows the diff, as Thor does on request.
- The override is a tool option under the names people know, `--force` and `--skip`, covering the whole command, as
  in Rails and cookiecutter.
- The template declares how its file is treated, as Copier's `skip_if_exists` does; Cook's `@Seed` declares
  `existing = 'fail'`.
- One status line per file, under `--dry-run` too, as Thor reports under `--pretend`, so a replaced file is visible
  before it is lost.
- A yes-or-no recipe argument is a bare `--name`, turned off with `--name=false`, as in Go's `flag`.

Sources:

- [Go flag package](https://pkg.go.dev/flag)
- [Rails generators guide](https://guides.rubyonrails.org/generators.html)
- [Thor `create_file.rb`](https://github.com/rails/thor/blob/main/lib/thor/actions/create_file.rb)
- [Thor `shell/basic.rb`](https://github.com/rails/thor/blob/main/lib/thor/shell/basic.rb)
- [Angular `ng generate`](https://angular.dev/cli/generate)
- [Yeoman file system](https://yeoman.io/authoring/file-system)
- [Copier configuring](https://copier.readthedocs.io/en/stable/configuring/)
- [cookiecutter CLI options](https://cookiecutter.readthedocs.io/en/stable/cli_options.html)
- [chezmoi `apply`](https://www.chezmoi.io/reference/commands/apply/)
- [chezmoi global flags](https://www.chezmoi.io/reference/command-line-flags/global/)
- [GNU Stow man page](https://manpages.debian.org/bookworm/stow/stow.8.en.html)
- [Kubernetes server-side apply](https://kubernetes.io/docs/reference/using-api/server-side-apply/)
- [Ansible `copy` module source](https://github.com/ansible/ansible/blob/devel/lib/ansible/modules/copy.py)
