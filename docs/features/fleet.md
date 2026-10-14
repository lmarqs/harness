# Configuration across repositories

## Objective

**Problem.** A maintainer sharing a practice across repositories needs each repository's actual changes reviewed and
a clear account of where the configuration was applied. Repeating commands alone does not account for different
repository content or local conventions.

**Goal.** Reuse cookbook recipes while keeping each destination's diff, approval and recovery information explicit.

## Per-repository file operations

Cookbooks distribute ingredients and recipes that can be reused in multiple repositories. Each invocation evaluates
the supplied recipes against the destination's current content and shows one combined terminal diff. Applying a recipe
in one repository does not authorize writes in another. See [Cook's architecture](../architecture.md) and
[the CLI grammar](cli.md).

The invocation directory is the destination. People or external automation choose the repositories and invoke Cook
from each intended directory. Keep that selection explicit: an unrelated clone on a machine is not an instruction to
configure it. Use the appropriate recipes and arguments for each repository, since native program settings and local
conventions can differ. Review a representative change before repeating it elsewhere, and review each destination's
actual diff.

Without preapproval Cook asks before target writes. `--batch-approve` displays the diff and proceeds without prompting;
noninteractive execution without that flag does not authorize writes. Approval covers a whole batch in one destination,
not selected files or hunks. Existing and edited files use the same review flow without `--adopt`, an adoption alias,
or an ownership-state prerequisite.

## History and reversal

Each destination records its own operation and recovery information in its ignored `.cook/` directory, with patches
saved before the first target write. Apply and discard need no prior history and always evaluate the supplied recipe
versions against current content. Users supply the versions they intend to use; Cook does not infer a repository's
earlier recipe version or manage recipe upgrades.

[Discard](remove.md) proposes deleting the supplied recipe's declared contributions in that destination. Reverting a
run restores its recorded prior values using that destination's history. Discard does not infer earlier recipe
declarations, and neither operation removes unrelated content. Cook's rollback on write failure covers the batch in
that destination; it provides no transaction across repositories.

## Execution and delivery boundary

The engine's public cooking interface is apply, discard and revert. There is no fleet command, fleet-file schema,
automatic branch or commit creation, verification receipt, or delivery event in that contract. Checking repositories,
tracking rollout results, running agents, committing, pushing and opening pull requests belong to the maintainer's
workflow and external tools. Executing external commands is not a batch action. Engine version/checksum pinning and
explicit upgrades follow the [wrapper design](wrapper.md), separately from cookbook and recipe versions.

Personal setup backup and agent-session orchestration remain future capabilities in
[issue #5](https://github.com/lmarqs/the-cook/issues/5) and
[issue #6](https://github.com/lmarqs/the-cook/issues/6). This page defines no fleet service, session protocol or hook API.
