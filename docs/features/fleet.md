# Configuration across repositories

## Objective

**Problem.** A maintainer sharing a practice across repositories needs each repository's actual changes reviewed and
a clear account of where the configuration was applied. Repeating commands alone does not account for different
repository content or local conventions.

**Goal.** Reuse cookbook recipes while keeping each destination's diff, approval and batch record explicit.

## Per-repository file operations

Cookbooks distribute ingredients and recipes that can be reused in multiple repositories; the
[glossary](glossary.md) defines these terms. Each invocation evaluates the supplied recipes against the destination's
current content and shows one combined terminal diff. Applying a recipe in one repository does not authorize writes in
another. See [Cook's architecture](../architecture.md) and [the CLI grammar](cli.md).

The invocation directory is the destination. People or external automation choose the repositories and invoke Cook
from each intended directory. Keep that selection explicit: an unrelated clone on a machine is not an instruction to
configure it. Use the appropriate recipes and arguments for each repository, since native program settings and local
conventions can differ. Review a representative change before repeating it elsewhere, and review each destination's
actual diff. The following illustrates applying one recipe in two repositories, each reviewed and approved on its own:

```sh
cd /work/repo-a && ./cook apply ./recipe.groovy
cd /work/repo-b && ./cook apply ./recipe.groovy
```

Approval covers a whole batch in one destination. See
[batch options and approval](cli.md#batch-options-and-approval).

## Batch records and reversal

Each destination keeps its own batch records in its ignored `.cook/batches/` directory and commits its own
`cook.lock`. Users supply the recipe versions they intend to use in each repository; see [apply](apply.md).

[Reverting](revert.md#revert) a batch acts on that destination only. Cook's rollback on write failure covers the batch
in that destination; it provides no transaction across repositories.

## Execution and delivery boundary

The engine's public cooking interface is apply, revert and read-only batch listing and inspection. Checking
repositories, tracking rollout results, running agents, committing, pushing and opening pull requests belong to the
maintainer's workflow and external tools. Executing external commands is not a batch action. Engine version/checksum
pinning and explicit upgrades follow the [wrapper design](wrapper.md), separately from cookbook and recipe versions.
