# Safety guidelines

## Objective

A contributor needs to know what Cook's approval and recovery cover before running a recipe or accepting its file
changes. Executable recipe trust, review of declared operations and the behaviour of configured programs are separate
responsibilities.

## Trust boundary

Cook evaluates trusted Groovy recipes into ordered file operations, as defined by the
[architecture](../architecture.md). Recipes are executable code, including those supplied by cookbooks. Read their
sources and dependencies before evaluating them; a source URL or a reviewed output diff alone does not establish that
code is safe to execute.

Approval, destination boundaries and rollback govern declared batch operations. They do not sandbox recipe
evaluation or guarantee protection against arbitrary side effects. A recipe can run code outside the batch before
the diff appears; rejecting the batch or reverting a run does not undo those effects. Executing external commands is
not a batch action.

## Declared operations and review

| Protection | Scope and limit |
| --- | --- |
| Destination boundary | Declared writes and deletions stay within the invocation directory, including paths resolved through symlinks. This does not confine arbitrary recipe code. |
| Combined terminal diff | Shows the final result of left-to-right composition against current content. Later declarations can replace earlier values; intermediate results are not separate approvals. |
| Whole-batch approval | Without preapproval, ask before target writes. With `--batch-approve`, show the diff and proceed without prompting. Noninteractive execution without that flag does not authorize writes. Partial file or hunk selection is outside the design. |
| Recovery before writes | Save one reversible patch per touched file before the first target write. On a write failure, roll back changes already made by the batch and retain patches and the failure record. Recovery covers declared file operations. |
| Declared scope | Whole-file paths, structured key paths and begin/end text markers determine what changes. Unrelated keys and text outside declared sections are preserved. Markers identify boundaries, not ownership. |

Existing and edited targets use the same actual diff and approval flow. There is no `--adopt`, adoption alias,
separate adoption step, ownership-state prerequisite or refusal merely because a developer edited a target. Applying
another recipe does not implicitly withdraw unrelated output from an earlier one. See [apply](render.md).

Review deletions as carefully as additions. [Discard](remove.md) evaluates the supplied recipe version and proposes
deleting its declared contributions even when their current values differ from the recipe's generated values.
Revert uses a recorded run's prior values, proposes the actual reversal against current content and preserves
unrelated content; later edits do not impose a drift veto. Recovery mechanics for overlapping edits and non-text
operations need later implementation design; this page does not select a patch representation.

## Reviewing native configuration and guidance

Configuration files can change what another program runs or what a coding agent is instructed to do. Review those
consequences as well as the file diff:

- Permission and settings changes: what the program can express, what becomes broader, and which global or managed
  settings affect it. Writing a rule does not guarantee its enforcement.
- Hook, task-runner and CI configuration: the code those programs will execute and the credentials they will use.
  Cook writes the configuration; those programs execute it independently.
- Instruction and skill files: who supplied the guidance and which programs read it. Keep untrusted issue text and
  generated reports identified as data rather than directives. See [repository guidance](context.md).
- Shared knowledge: whether changes remain accurate and reviewed, and whether references still lead to the right
  files. See [memory](memory.md).

Keep guidance focused on the repository. Personal settings may contain one-time approvals, private paths, host names
or tokens; review anything copied into shared configuration before distributing it. There is no separate Cook import
or adoption gate that makes that material safe.

## Secrets

Do not put secrets, credentials or personal data in shared recipes, templates, instruction files or configuration.
The terminal diff, captured sources and reversible patches can retain sensitive content, including prior values from
existing files. Keep that in mind when choosing declared targets and handling local history.

Trusted recipe code runs with the access of the process evaluating it. Choose its environment and credentials
accordingly; batch approval does not restrict their use. A native task can also reach a remote system through an
environment file or inherited credentials. Its execution safeguards belong to that program and its environment.

## The pinned binary

The intended [wrapper](wrapper.md) pins an engine version and checksum, fetches the platform package on first use,
verifies the download and caches it locally. The jpackage distribution contains Java; upgrades are explicit. Review
changes to the source, version and checksum together. A checksum verifies the fetched artifact against the chosen pin;
it does not prove that the selected release or recipe is trustworthy.

Engine pinning is separate from cookbook distribution and the recipe versions users supply. No recipe updater,
registry, signature-key protocol or worker tamper check is defined here. The [installation reference](installation.md)
distinguishes the delivery design from the current local build and launcher.

## History and engine boundaries

Local `.cook/` history records the operation, evaluated and consumed sources (including local ones), origins,
arguments, order, reversible patches and result or failure log. Apply and discard require no previous history or
ownership database. Losing `.cook/` loses local recovery information; reverting a particular run requires its record.
History is not a verification receipt or a worker supervision log.

Permission enforcement, branch protection, checks, publication and runtime confinement belong to people, configured
programs and their execution environments. Cook does not supervise unattended sessions, enforce a safety floor,
launch jailed workers or guard lifecycle delivery. Personal setup backup and agent-session orchestration remain
future work in [issue #5](https://github.com/lmarqs/the-cook/issues/5) and
[issue #6](https://github.com/lmarqs/the-cook/issues/6). Ingredient/recipe APIs and hooks remain outside this discussion.
