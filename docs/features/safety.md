# Safety guidelines

## Objective

**Problem.** A contributor who runs a recipe or accepts its file changes can mistake batch approval for a guarantee
about the recipe code or the programs that read the resulting configuration.

**Goal.** The contributor knows what approval and recovery cover, and reviews recipe trust, declared operations and
the behavior of configured programs as separate responsibilities.

## Trust boundary

Cook evaluates trusted Groovy [recipes](glossary.md) into ordered file operations, as defined by the
[architecture](../architecture.md). Recipes are executable code, including those supplied by cookbooks. Read their
sources and dependencies before evaluating them; a source URL or a reviewed output diff alone does not establish that
code is safe to execute.

Approval, destination boundaries and rollback govern declared batch operations. They do not sandbox recipe
evaluation or guarantee protection against arbitrary side effects. A recipe can run code outside the batch before
the diff appears; rejecting the batch or reverting a batch does not undo those effects. Executing external commands is
not a batch action.

## Declared operations and review

| Protection | Scope | Limit |
| --- | --- | --- |
| Destination boundary | Declared writes and deletions stay within the invocation directory, including paths resolved through symlinks. | Does not confine arbitrary recipe code. |
| Recipe sources | Recipes come from paths inside the repository or `https://` URLs; a local recipe or template outside the repository is refused. | Says nothing about whether the code is safe. |
| Combined terminal diff | Shows the final result of left-to-right composition against current content; the last recipe to write a target wins and becomes its owner. | The diff is the only notice of an overwrite. |
| Whole-batch approval | Without preapproval, Cook asks before target writes; `--batch-approve` shows the diff and proceeds without prompting. | Noninteractive execution without that flag does not authorize writes. |
| Recovery before writes | The batch record is saved before the first target write; a write failure rolls back changes already made and retains the record and failure. | Covers declared file operations only. |
| Declared scope | Whole-file paths, key paths and `cook:begin <recipe>#<region>` / `cook:end <recipe>#<region>` markers determine what changes; unrelated keys and text outside sections are preserved. | Ownership never blocks a change. |

Review deletions as carefully as additions: applying a new version of a recipe removes what its previous version
owned, and [revert](revert.md) writes nothing when a later edit overlaps the batch. See [apply](apply.md) for the
operation semantics.

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
existing files. Avoid declaring targets that hold secrets, and treat the ignored `.cook/` directory as sensitive
local data.

Trusted recipe code runs with the access of the process evaluating it. Choose its environment and credentials
accordingly; batch approval does not restrict their use. A native task can also reach a remote system through an
environment file or inherited credentials. Its execution safeguards belong to that program and its environment.

## The pinned binary

The [wrapper](wrapper.md) pins an engine version and checksum and verifies the download before running it. Review
changes to the source, version and checksum together; a checksum verifies the fetched artifact against the chosen pin
and does not prove that the selected release or recipe is trustworthy.

## Batch records and engine boundaries

Each [batch record](apply.md#batch-record) holds captured sources and reversible patches, which is why it can retain
sensitive content. Apply requires neither earlier batch records nor `cook.lock`; ownership survives in the committed
`cook.lock`, and reverting a batch requires its record.

Permission enforcement, branch protection, checks, publication and runtime confinement belong to people, configured
programs and their execution environments.
