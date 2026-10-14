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
the diff appears; rejecting the batch or reverting a batch does not undo those effects. Executing external commands is
not a batch action.

## Declared operations and review

| Protection | Scope and limit |
| --- | --- |
| Destination boundary | Declared writes and deletions stay within the invocation directory, including paths resolved through symlinks. Recipes come from paths inside the repository or `https://` URLs; a local recipe or template outside the repository is refused. This does not confine arbitrary recipe code. |
| Combined terminal diff | Shows the final result of left-to-right composition against current content. When two recipes write the same target, the last one wins and becomes its owner. |
| Whole-batch approval | Without preapproval, ask before target writes. With `--batch-approve`, show the diff and proceed without prompting. Noninteractive execution without that flag does not authorize writes. Choosing individual files or hunks belongs to a separate interface decision. |
| Recovery before writes | Save one reversible patch per touched file before the first target write. On a write failure, roll back changes already made by the batch and retain patches and the failure record. Recovery covers declared file operations. |
| Declared scope | Whole-file paths, structured key paths and `cook:begin <recipe>/<region>` / `cook:end <recipe>/<region>` text markers determine what changes. Unrelated keys and text outside declared sections are preserved. Markers name the owning recipe; ownership never blocks a change. |

Existing and edited targets use the same actual diff and approval flow as any other target. Applying another recipe
does not remove output an earlier recipe owns. See [apply](render.md).

Review deletions as carefully as additions. Applying a new version of a recipe removes what its previous version
owned and the new version no longer declares, and the diff shows that removal. [Revert](remove.md) applies a recorded
batch's reverse patches against current content, `cook.lock` included; later edits that do not overlap the batch's
changes survive, and an overlapping edit makes the revert write nothing and list each conflicting file and section.

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
or tokens; review anything copied into shared configuration before distributing it.

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

Engine pinning is separate from cookbook distribution and the recipe versions users supply. The
[installation reference](installation.md) distinguishes the delivery design from the current local build and launcher.

## History and engine boundaries

Each batch record in the ignored `.cook/batches/<batchId>/` directory holds evaluated and consumed sources (including
local ones), origins, arguments, order, reversible patches and result or failure log. Apply requires neither previous
history nor `cook.lock`. Losing `.cook/` loses local recovery information; ownership survives in the committed
`cook.lock`, and reverting a particular batch requires its record.

Permission enforcement, branch protection, checks, publication and runtime confinement belong to people, configured
programs and their execution environments.
