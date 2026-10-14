# Glossary

## Objective

**Problem.** A reader may arrive through a search or cross-reference and mistake a recipe, ingredient or batch for a
development lifecycle mechanism.

**Goal.** Readers can find the meaning of Cook's terms and the reference that explains each responsibility.

## Terms

These terms follow [Cook architecture](../architecture.md), which states the intended design. For current execution,
see the [root README](../../README.md) and [core implementation guide](../../core/README.md).

| Term | Meaning |
| --- | --- |
| Agent harness | A program that runs a coding agent and consumes its native configuration and guidance. See [agent and worker configuration](workers.md). |
| Agent-content directory | A repository convention such as `.agents/` for skills, memory and guidance. Program support determines what is consumed. |
| Apply | Evaluate supplied recipes against current content and propose their declared additions and updates, together with the removal of what each recipe's previous version owned and the supplied version no longer declares. See [apply](apply.md). |
| Batch | An ordered collection of declared file operations, reviewed as one final diff and approved as a whole. It can include directory and symlink operations, but external command execution is not a batch action. |
| Batch approval | Approval for all declared operations in the final terminal diff. `--batch-approve` displays that diff and proceeds without prompting; otherwise Cook asks before target writes. Noninteractive execution alone does not authorize writes. See [CLI](cli.md). |
| Batch ID | The identifier of a recorded batch: the UTC time the batch started, such as `2026-10-14T15-04-05Z`, with a suffix when two batches start in the same second. |
| Batch record | The local record of one batch in the ignored `.cook/batches/<batchId>/` directory: one reversible patch per touched file (`cook.lock` included), the evaluated and consumed sources with their origins, arguments, execution order, and result or failure log. |
| Block / managed block | A section in a shared text file bounded by `cook:begin <recipe>/<region>` and `cook:end <recipe>/<region>` markers in the file format's comment syntax. Markers identify the owning recipe and region; ownership never blocks a change, and manual edits use the ordinary batch review. See [shared text sections](apply.md#managed-blocks). |
| `cook.lock` | The committed YAML file at the repository root that records, for each applied recipe, its short name, source (repository-relative path or `https://` URL), the source's sha256, and the sections, keys and files it owns. It records no recipe arguments. Ownership never blocks a change. See [the lock](../architecture.md#ownership-and-the-lock). |
| Cookbook | A repository that holds and ships ingredients and recipes. Its build and distribution can be separate from the engine's. See [cookbooks](cookbooks.md#cookbooks). |
| Destination | The invocation's current working directory. Declared writes and deletions stay within it, including symlink resolution, regardless of executable location. |
| Diff | The actual change from current content to the combined final batch result, displayed in the terminal before target writes. |
| Discard | Remove what `cook.lock` lists for a recipe, without evaluating the recipe. Discard is deferred; revert the batches that applied a recipe instead. See [discard](revert.md#discard). |
| File operation | A declared addition, update or deletion addressed by a whole-file path, structured key path or bounded text section, including directory and symlink operations where applicable. |
| History | The batch records in the destination's ignored `.cook/batches/` directory, one per batch. Apply needs neither history nor `cook.lock`; reverting a batch requires its record. See [history](../architecture.md#paths-source-snapshots-and-local-history). |
| Ingredient | A program data model for the files and configuration a program natively consumes, such as Git's ignore file or Codex's configuration and skills. See [ingredients](ingredients.md). |
| Key path | The address of structured content within a file. An operation on a declared key preserves unrelated keys. |
| Knowledge | Durable, reviewed repository guidance, such as prose, memory and skills. Recipes can declare guidance files; usefulness and program compliance still require human judgment. |
| Memory | Durable repository knowledge for people and coding agents. `.agents/memory/` and references in `AGENTS.md` are example conventions. See [memory](memory.md). |
| Native file | A file in the format and location the configured program consumes directly. |
| Ordered composition | Recipes and their operations execute left to right. When two recipes write the same section, key or file, the last one wins and becomes its owner; the preview shows their combined final result and is the only notice. |
| Origin | The original local location or source URL of a consumed recipe or template, captured with its source. Relative references resolve from the referencing recipe's location. |
| Patch | Recovery information for the combined operation on one touched file. One reversible patch supports forward and reverse application. |
| Recipe | Trusted executable Groovy code describing requested file operations using program models. Each recipe has a short name, declared or derived from its path, that markers and `cook.lock` use. See [recipes](recipes.md). |
| Recipe argument | A value following its recipe operand in `--name=value` form. Each next recipe starts its own argument scope. See [CLI](cli.md). |
| Recipe operand | A path inside the destination repository or an `https://` URL; operands outside the repository are refused. |
| Release pin | The engine version and matching platform package checksum selected by the committed wrapper. Changing the pin is an explicit upgrade, separate from cookbook and recipe versions. See [wrapper](wrapper.md). |
| Revert | Apply a recorded batch's reverse patches against current content, restoring `cook.lock` too, without evaluating recipes again. Later non-overlapping edits survive; an overlapping edit makes the revert write nothing and list the conflicting files and sections. See [revert](revert.md#revert). |
| Rollback | On a batch write failure, reverse changes already made by the batch and retain the batch record and failure. Arbitrary recipe side effects are outside this guarantee. |
| Snapshot | Captured source content actually consumed in a batch, including local and downloaded recipes and templates. Original origins remain recorded, and snapshots preserve original resolution. |
| Station | A term whose role remains deferred to the ingredient/recipe model discussion. See [composition boundaries](cookbooks.md). |
| Template | A source used by a recipe to describe native output. Consumed templates are captured with their origins. |
| Trusted recipe | Executable code the caller trusts before evaluation. Batch review, output boundaries and recovery do not sandbox its arbitrary effects. See [safety](safety.md). |
| Workspace | A separate working copy managed by people and their version-control tools. Cook can describe repository conventions and configuration. See [workspaces](workspaces.md). |
| Wrapper | The committed `./cook` entry point that pins an engine version and checksum, downloads and verifies its platform package on first use, then caches it. See [wrapper](wrapper.md). |

Ingredient/recipe APIs, station roles, nesting, hooks, decorators, annotations and parameter injection remain separate
discussion topics.
