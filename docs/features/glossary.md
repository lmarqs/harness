# Glossary

## Objective

**Problem.** A reader may arrive through a search or cross-reference and mistake a recipe, ingredient or run for a
development lifecycle mechanism.

**Goal.** Readers can find the meaning of Cook's terms and the reference that explains each responsibility.

## Terms

These terms follow [Cook architecture](../architecture.md), which states the intended design. For current execution,
see the [root README](../../README.md) and [core implementation guide](../../core/README.md).

| Term | Meaning |
| --- | --- |
| Agent harness | A program that runs a coding agent and consumes its native configuration and guidance. Configuring it does not make Cook its session controller. See [agent and worker configuration](workers.md). |
| Agent-content directory | A repository convention such as `.agents/` for skills, memory and guidance. Program support determines what is consumed; it is not a required engine layout. |
| Apply | Evaluate supplied recipes against current content and propose their declared additions and updates. See [apply](render.md). |
| Batch | An ordered collection of declared file operations, reviewed as one final diff and approved as a whole. It can include directory and symlink operations, but external command execution is not a batch action. |
| Batch approval | Approval for all declared operations in the final terminal diff. `--batch-approve` displays that diff and proceeds without prompting; otherwise Cook asks before target writes. Noninteractive execution alone does not authorize writes. See [CLI](cli.md). |
| Block / managed block | A section in a shared text file addressed by begin/end markers. Markers identify boundaries rather than ownership; manual edits use the ordinary batch review. See [shared text sections](render.md#managed-blocks). |
| Cookbook | A repository that holds and ships ingredients and recipes. Its build and distribution can be separate from the engine's. See [cookbooks](stations.md#cookbooks). |
| Destination | The invocation's current working directory. Declared writes and deletions stay within it, including symlink resolution, regardless of recipe or executable location. |
| Diff | The actual change from current content to the combined final batch result, displayed in the terminal before target writes. |
| Discard | Evaluate the supplied recipe versions and propose deleting their declared contributions, even when current values differ. It preserves unrelated content and does not restore prior values. See [discard](remove.md#discard). |
| File operation | A declared addition, update or deletion addressed by a whole-file path, structured key path or bounded text section, including directory and symlink operations where applicable. |
| History | Local recovery information in the destination's ignored `.cook/` directory, with a separate record per batch operation. Apply/discard need no prior history; reverting a run requires that run's history. See [history](../architecture.md#paths-source-snapshots-and-local-history). |
| Ingredient | A program data model for the files and configuration a program natively consumes, such as Git's ignore file or Codex's configuration and skills. See [ingredients](ingredients.md). |
| Key path | The address of structured content within a file. An operation on a declared key preserves unrelated keys. |
| Knowledge | Durable, reviewed repository guidance, such as prose, memory and skills. Recipes can declare guidance files; usefulness and program compliance still require human judgment. |
| Memory | Durable repository knowledge for people and coding agents. `.agents/memory/` and references in `AGENTS.md` are example conventions, not required paths or an automatically maintained index. See [memory](memory.md). |
| Native file | A file in the format and location the configured program consumes directly. Reading it does not require invoking Cook. |
| Ordered composition | Recipes and their operations execute left to right. Later instructions may replace earlier values at overlapping targets; the preview shows their combined final result. |
| Origin | The original local location or source URL of a consumed recipe or template, captured with its source. Relative references resolve from the referencing recipe's original location, not its snapshot location. |
| Patch | Recovery information for the combined operation on one touched file. One reversible patch supports forward and reverse application; its concrete representation remains separate implementation design. |
| Recipe | Trusted executable Groovy code describing requested file operations using program models. See [recipes](recipes.md). |
| Recipe argument | A value following its recipe operand in `--name=value` form. Each next recipe starts its own argument scope. This CLI grammar does not choose a parameter-injection API. See [CLI](cli.md). |
| Recipe operand | An exact local file path or supported source URI, including `file://` and `https://`; there is no implicit cookbook-name lookup. |
| Release pin | The engine version and matching platform package checksum selected by the committed wrapper. Changing the pin is an explicit upgrade, separate from cookbook and recipe versions. See [wrapper](wrapper.md). |
| Revert | Use a recorded run's history to propose restoring that operation's prior values against current content, preserving unrelated content and showing the actual diff. It does not evaluate a recipe again or veto manual edits. See [revert](remove.md#revert). |
| Rollback | On a batch write failure, reverse changes already made by the batch and retain recovery patches and the failure record. Arbitrary recipe side effects are outside this guarantee. |
| Run | One recorded batch operation, with sources, origins, arguments, execution order, operation and result or failure log. It is not a pass through development lifecycle stages. |
| Snapshot | Captured source content actually consumed in a run, including local and downloaded recipes and templates. Original origins remain recorded so capture does not change source resolution. |
| Station | A term whose role remains deferred to the ingredient/recipe model discussion. This reference does not define a station schema, command, composition unit or lifecycle role. See [composition boundaries](stations.md). |
| Template | A source used by a recipe to describe native output. Consumed templates are captured with their origins; this definition selects no template language or loading API. |
| Trusted recipe | Executable code the caller trusts before evaluation. Batch review, output boundaries and recovery do not sandbox its arbitrary effects. See [safety](safety.md). |
| Workspace | A separate working copy managed by people and their version-control tools. Cook can describe repository conventions and configuration; it does not create a managed session contract. See [workspaces](workspaces.md). |
| Wrapper | The committed `./cook` entry point that pins an engine version and checksum, downloads and verifies its platform package on first use, then caches it. This intended delivery contract does not establish current implementation availability. See [wrapper](wrapper.md). |

Personal setup backup (kit) and agent-session orchestration are future work in
[issue #5](https://github.com/lmarqs/the-cook/issues/5) and
[issue #6](https://github.com/lmarqs/the-cook/issues/6). Their vocabulary does not define the current engine contract.
Ingredient/recipe APIs, station roles, nesting, hooks, decorators, annotations and parameter injection remain separate
discussion topics.
