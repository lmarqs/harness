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
noninteractive execution without that flag does not authorize writes. The CLI's approval covers a whole batch in one
destination; choosing individual files or hunks belongs to a separate interface decision. Existing and edited files use
the same review flow without `--adopt`, an adoption alias, or an ownership-state prerequisite.

## History and reversal

Each destination records its own batches in its ignored `.cook/batches/<batchId>/` directory, with reverse patches
saved before the first target write, and commits its own `cook.lock` with each recipe's source, sha256 and owned
targets. Apply needs neither prior history nor the lock and always evaluates the supplied recipe versions against
current content. Users supply the versions they intend to use; Cook does not manage recipe upgrades.

[Reverting](remove.md#revert) a batch applies its reverse patches against current content in that destination, including
`cook.lock`. Later non-overlapping edits survive; an overlapping edit makes the revert write nothing and list the
conflicts. Applying one recipe does not remove output another recipe owns. Cook's rollback on write failure covers the
batch in that destination; it provides no transaction across repositories.

## Execution and delivery boundary

The engine's public cooking interface is apply, revert and read-only batch listing and inspection. There is no fleet
command, fleet-file schema, automatic branch or commit creation, verification receipt, or delivery event in that
contract. Checking repositories, tracking rollout results, running agents, committing, pushing and opening pull requests
belong to the maintainer's workflow and external tools. Executing external commands is not a batch action. Engine
version/checksum pinning and explicit upgrades follow the [wrapper design](wrapper.md), separately from cookbook and
recipe versions.

Personal setup backup and agent-session orchestration remain future capabilities in [issue
#5](https://github.com/lmarqs/the-cook/issues/5) and [issue #6](https://github.com/lmarqs/the-cook/issues/6). This page
defines no fleet service, session protocol or hook API.
