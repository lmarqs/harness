# Glossary

## Objective

**Problem.** A reader may arrive through a search or cross-reference and need the meaning of a term without reading
the pages that use it.

**Goal.** Readers can find the meaning of Cook's terms and the reference that explains each responsibility.

## Terms

These terms follow [Cook architecture](../architecture.md), which states the intended design. For current execution,
see the [root README](../../README.md) and [core implementation guide](../../core/README.md).

| Term | Meaning |
| --- | --- |
| Agent harness | A program that runs an agent and consumes its native configuration and guidance. See [ingredients](../architecture.md#ingredients-recipes-and-cookbooks). |
| Agent-content directory | A repository convention such as `.agents/` for skills, memory and guidance. Program support determines what is consumed. |
| Apply | The operation that evaluates supplied recipes against current content and proposes their declared additions and updates, with removal of what each recipe's previous version owned and the supplied version no longer declares. See [apply](apply.md). |
| Batch | An ordered collection of declared file operations, reviewed as one final diff and approved as a whole. It can include directory and symlink operations, but external command execution is not a batch action. |
| Batch approval | Approval for all declared operations in the final terminal diff. `--batch-approve` displays that diff and proceeds without prompting; otherwise Cook asks before target writes. Noninteractive execution alone does not authorize writes. See [CLI](cli.md). |
| Batch ID | The identifier of a recorded batch: the UTC time the batch started, such as `2026-10-14T15-04-05Z`, with a suffix when two batches start in the same second. |
| Batch record | The local record of one batch in the ignored `.cook/batches/<batchId>/` directory, which revert uses to reverse that batch. See [one record for the batch](../architecture.md#one-record-for-the-batch). |
| `cook.lock` | The committed YAML file at the repository root that records each applied recipe's short name, source, source sha256 and owned sections, keys and files. See [the lock](../architecture.md#ownership-and-the-lock). |
| Cookbook | A repository that holds and ships ingredients and recipes. Its build and distribution can be separate from the engine's. See [cookbooks](../architecture.md#ingredients-recipes-and-cookbooks). |
| Cooking | Running recipes through the CLI, which is the public cooking interface. See [CLI](cli.md). |
| Destination | The invocation's current working directory. Declared writes and deletions stay within it, including symlink resolution, regardless of executable location. See [destination and source origins](../architecture.md#destination-and-source-origins). |
| Diff | The change from current content to the combined final batch result, displayed in the terminal before target writes. See [apply](apply.md#review-and-recovery). |
| Discard | Remove what `cook.lock` lists for a recipe, without evaluating the recipe. Discard is deferred; revert the batches that applied a recipe instead. See [discard](revert.md#discard). |
| Engine | Cook, the recipe driven configuration engine: it evaluates recipes into a batch, presents the diff, obtains approval, records the batch and performs the writes. See [architecture](../architecture.md#scope-and-responsibilities). |
| File operation | A declared addition, update or deletion addressed by a whole-file path, structured key path or bounded text section, including directory and symlink operations where applicable. |
| Ingredient | A program data model for the files and configuration a program natively consumes, such as Git's ignore file or Codex's configuration and skills. See [ingredients](../architecture.md#ingredients-recipes-and-cookbooks). |
| Key path | The address of structured content within a file. An operation on a declared key preserves unrelated keys. |
| Kitchen | The directory Cook cooks in, which is the destination: the invocation's current working directory, or the directory that `-C <dir>` or `--kitchen=<dir>` names, as if Cook had started there. See the [CLI reference](cli.md#kitchen). |
| Knowledge | Durable, reviewed repository guidance, such as prose, memory and skills. Recipes can declare guidance files; usefulness and program compliance still require human judgment. |
| Loop runner | A program that runs a coding agent over a series of tasks, such as Ralph TUI. Recipes can declare its native configuration; the runner and whoever drives it start it, select tasks, choose models and parallelism, create working copies and signal completion. |
| Marker | One of the `cook:begin <recipe>#<region>` and `cook:end <recipe>#<region>` lines, in the file format's comment syntax, that bound a section and name its owning recipe's short name and its region. See [declared targets](../architecture.md#ordered-composition-and-declared-targets). |
| Memory | Durable repository knowledge for people and agents, written and reviewed by contributors as repository documentation. `.agents/memory/` and references in `AGENTS.md` are example conventions. See [instruction files and conventions](../ux/generated-content-standards.md#instruction-files-and-conventions). |
| Native file | A file in the format and location the configured program consumes directly. |
| Ordered composition | Recipes and their operations execute left to right. When two recipes write the same section, key or file, the last one wins and becomes its owner; the preview shows their combined final result and is the only notice. |
| Owner | The recipe that `cook.lock` records for a section, key or file: the last recipe to write it. Ownership decides what that recipe's later versions update and remove, and never blocks a change. See [the lock](../architecture.md#ownership-and-the-lock). |
| Patch | The reversible change to one touched file in a batch, combining every recipe's change to that file. One patch supports forward and reverse application. See [one record for the batch](../architecture.md#one-record-for-the-batch). |
| Recipe | Trusted executable Groovy code describing requested file operations using program models. See [recipes](../architecture.md#ingredients-recipes-and-cookbooks). |
| Recipe argument | A value following its recipe operand in `--name=value` form. Each next recipe starts its own argument scope. See [CLI](cli.md). |
| Recipe operand | A path inside the destination repository or an `https://` URL; operands outside the repository are refused. |
| Region | The name after `#` in a marker, identifying one of the owning recipe's sections. |
| Release pin | The engine version and matching platform package checksum selected by the committed wrapper. Changing the pin is an explicit upgrade, separate from cookbook and recipe versions. See [wrapper](wrapper.md). |
| Revert | The operation that applies a recorded batch's reverse patches against current content, without evaluating recipes, and writes nothing when a later edit overlaps the batch's changes. See [revert](revert.md#revert). |
| Rollback | On a batch write failure, reverse changes already made by the batch and retain the batch record and failure. Arbitrary recipe side effects are outside this guarantee. |
| Section | The part of a shared text file between a recipe's markers. Text outside it stays in place; manual edits inside it appear in the next batch diff. See [shared text sections](apply.md#sections-and-markers). |
| Short name | The name, such as `agentic/context`, that identifies a recipe in markers and `cook.lock`. |
| Skills installer | A program that copies shared skills into a repository. Skills can be maintained through a recipe, an installer or ordinary repository edits. See [skills sources and maintenance](../ux/skills-standards.md#sources-and-maintenance). |
| Snapshot | Captured content of a source consumed in a batch, including local and downloaded recipes and templates, recorded with its source origin. See [one record for the batch](../architecture.md#one-record-for-the-batch). |
| Source origin | The original local location or source URL of a consumed recipe or template, captured with its snapshot. Relative references resolve from the referencing recipe's location. See [destination and source origins](../architecture.md#destination-and-source-origins). |
| Template | A source used by a recipe to describe native output. Consumed templates are captured with their origins. See [apply](apply.md#destination-and-source-paths). |
| Trusted recipe | Executable code the caller trusts before evaluation. Batch review, output boundaries and recovery do not sandbox its arbitrary effects. See [trust recipes explicitly](../philosophy.md#trust-recipes-explicitly). |
| Workspace | A separate working copy, used by contributors and agents for concurrent tasks and managed by people and their version-control tools. Cook can describe repository conventions and configuration. See [working copies](pitfalls.md#working-copies-inherit-parent-configuration-and-share-services). |
| Wrapper | The committed `./cook` entry point that pins an engine version and checksum, downloads and verifies its platform package on first use, then caches it. See [wrapper](wrapper.md). |
