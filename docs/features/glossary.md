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
| Batch | An ordered collection of declared file operations, reviewed as one final diff and approved as a whole. It can include directory and symlink operations, but external command execution is not a batch action. |
| Batch approval | Approval for all declared operations in the final terminal diff. `--batch-approve` displays that diff and proceeds without prompting; otherwise Cook asks before target writes. Noninteractive execution alone does not authorize writes. See [CLI](cli.md). |
| `cook.lock` | The committed YAML file at the repository root that records each cooked recipe's short name, source, source sha256 (for coordinates, of the artifact's sources) and owned sections, keys, files and links. See [the lock](../architecture.md#ownership-and-the-lock). |
| Cookbook | A repository, or a directory in one, that holds and ships ingredients and recipes. It releases by git tag, and may also publish jars of the same sources. See [cookbook distribution](../architecture.md#cookbook-distribution). |
| Coordinates | A recipe's address in a cookbook, `group:artifact:version[:recipe]`, such as `dev.thecook:agents:0.1.0:instructions`. Without `:recipe` they name the recipe called like the artifact. See [CLI](cli.md#recipe-operands). |
| Cooking | The operation that evaluates supplied recipes against current content and proposes their declared additions and updates, with removal of what each recipe's previous version owned and the supplied version no longer declares. `./cook` cooks the recipes it is given, without a command word; the CLI is the public cooking interface. See [cooking](cook.md) and [CLI](cli.md). |
| Destination | The invocation's current working directory. Declared writes and deletions stay within it, including symlink resolution, regardless of executable location. See [destination and source origins](../architecture.md#destination-and-source-origins). |
| Diff | The change from current content to the combined final batch result, displayed in the terminal before target writes. See [cooking](cook.md#review-and-recovery). |
| Discard | The operation that removes what `cook.lock` lists for each named recipe, without evaluating it: its sections with their markers, its keys, its whole files and links. A file left empty is deleted; earlier values are not restored. See [discard](discard.md). |
| Engine | Cook, the recipe driven configuration engine: it evaluates recipes into a batch, presents the diff, obtains approval and performs the writes. See [architecture](../architecture.md#scope-and-responsibilities). |
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
| Recipe | Trusted executable Groovy code describing requested file operations using program models. See [recipes](../architecture.md#ingredients-recipes-and-cookbooks). |
| Recipe argument | A value following its recipe operand, written `--name=value` or `--name value`, or a bare `--name` for a yes-or-no param. Each next recipe starts its own argument scope. See [CLI](cli.md#recipe-arguments-and-ordering). |
| Recipe operand | A word that contains `/` or `:` or ends with `.groovy`: a path inside the destination repository, an `https://` URL or coordinates. Operands outside the repository are refused. See [CLI](cli.md#recipe-operands). |
| Region | The name after `#` in a marker, identifying one of the owning recipe's sections. |
| Release pin | The engine version and matching platform package checksum selected by the committed wrapper. Changing the pin is an explicit upgrade, separate from cookbook and recipe versions. See [wrapper](wrapper.md). |
| Rollback | On a batch write failure, reverse changes already made by the batch, so the files stay as they were, and report the failure. Arbitrary recipe side effects are outside this guarantee. |
| Section | The part of a shared text file between a recipe's markers. Text outside it stays in place; manual edits inside it appear in the next batch diff. See [shared text sections](cook.md#sections-and-markers). |
| Short name | The name, such as `agentic/context`, that identifies a recipe in markers and `cook.lock`. |
| Skills installer | A program that copies shared skills into a repository. Skills can be maintained through a recipe, an installer or ordinary repository edits. See [skills sources and maintenance](../ux/skills-standards.md#sources-and-maintenance). |
| Source origin | The original local location or source URL of a consumed recipe or template. Relative references resolve from the referencing recipe's location. See [destination and source origins](../architecture.md#destination-and-source-origins). |
| Template | A source used by a recipe to describe native output. See [cooking](cook.md#destination-and-source-paths). |
| Trusted recipe | Executable code the caller trusts before evaluation. Batch review, output boundaries and recovery do not sandbox its arbitrary effects. See [trust recipes explicitly](../philosophy.md#trust-recipes-explicitly). |
| Workspace | A separate working copy, used by contributors and agents for concurrent tasks and managed by people and their version-control tools. Cook can describe repository conventions and configuration. See [working copies](pitfalls.md#working-copies-inherit-parent-configuration-and-share-services). |
| Wrapper | The committed `./cook` entry point that pins an engine version and checksum, downloads and verifies its platform package on first use, then caches it. See [wrapper](wrapper.md). |
