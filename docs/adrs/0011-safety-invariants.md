# 0011. Cook's guarantees cover declared batch operations

## Objective

**Problem.** A developer needs to understand both the changes being approved and the limits of that approval.
Calling configuration editing safe without stating those limits can suggest protection from executable code or from
programs the configuration controls.

**Goal.** Developers can review requested file changes and recover from a failed write without mistaking those
mechanisms for protection from arbitrary recipe execution or agent sessions.

## Context

- The [architecture](../architecture.md#evaluation-review-and-writes) defines trusted Groovy recipe evaluation,
  ordered file operations, terminal review, approval, recovery and writes.
- Recipes are executable code. Their evaluation can have side effects outside their declared file operations.
- Worked example: a recipe declares a settings-key update but also executes code that contacts a service during
  evaluation. A diff of the settings file describes only the declared update; rejecting it cannot undo the contact.
- A partially written batch leaves a destination in a different state from either the starting content or the
  approved result. Recovery information is needed before any target write.

## Decision

1. Cook displays the actual final diff against current content and obtains approval for the entire batch before
   changing target files. Preapproval still displays the diff; noninteractive execution alone does not authorize
   writes. The core writes and records only accepted changes; the CLI accepts the whole batch, and choosing
   individual changes is a separate interface decision.
2. Declared writes and deletions stay within the invocation directory, including paths resolved through symlinks
   ([0012](0012-write-boundary.md)). Structured keys and marked sections preserve unrelated content; whole-file
   declarations expose their full scope. Recipes and operations compose in declared order.
3. Cook saves one reversible patch per touched file, `cook.lock` included, before the first target write and records
   the batch locally. A write failure rolls back changes already made by the batch and retains the patches and failure
   record. Reverting a batch applies its reverse patches against current content; a later edit that overlaps the
   batch's changes makes the revert write nothing and list the conflict.
4. These guarantees cover declared batch operations. They do not sandbox trusted recipe execution, roll back arbitrary
   recipe side effects, or enforce the behaviour of configured programs. Executing external commands is not a batch
   action.
5. Existing or manually edited content uses the same review and approval flow. Apply needs neither `cook.lock` nor
   previous batch history, and every invocation evaluates the supplied recipes again. Ownership recorded in `cook.lock`
   never blocks a change ([0007](0007-ownership.md)). Applying a recipe does not remove output another recipe owns.

## Reasons

- **1.** Review must show what will actually change, including deliberate replacement of existing values. Approval
  authorizes that combined result. Preapproval is an explicit choice; the absence of a terminal is not one.
- **2.** A destination boundary limits declared output, and precise targets reduce incidental edits. Whole-file
  replacement carries a broader review responsibility. None of these limits constrains arbitrary recipe code.
- **3.** Recovery saved after a write would be too late for that write's failure. The same patches later restore the
  batch's own prior values without reevaluating recipes, and refusing on overlap keeps later edits. Recovery depends
  on keeping the relevant local batch record.
- **4.** The example separates output approval from code trust. Claiming protection beyond the declared batch would
  promise control the engine does not provide. Developers must choose which recipes to trust before evaluating them.
- **5.** Current content is the basis for review. Recorded ownership decides what a recipe's later versions remove,
  not whether a change may proceed. Not gating on ownership keeps operations usable without prior state but makes the
  actual diff, including deletion of edited values, essential.

## Rejected alternatives

### Enforce worker safety floors in the engine

At its best, this gives supervised sessions shared permission and verification guards. It requires an agent-session
controller outside the agreed scope and still does not protect against arbitrary trusted recipe execution.

### Require ownership hashes and refuse drift

At its best, this detects edits to previously generated content automatically. It substitutes recorded ownership for
review of the requested operation and prevents the agreed changes to current content without extra gates.

### Treat approval as a recipe sandbox

At its best, a single approval would seem to authorize everything a recipe does. The batch diff cannot describe or
reverse arbitrary code effects; presenting it that way would misstate the trust boundary.

## Consequences

Costs:

- Recipe evaluation must be trusted separately from approval of its output.
- Preapproval places responsibility for the entire displayed change on the caller.
- Losing `.cook/` loses recovery for past batches. Apply remains available and ownership survives in `cook.lock`;
  reverting a particular batch requires its record.

Must be maintained:

- Accurate combined diffs, explicit batch approval, destination containment and recovery for declared operations.
- An honest distinction between these guarantees and recipe or configured-program execution.

## Confirmation

- Review an edited-target example: the actual proposed replacement or deletion is displayed for approval.
- Review a batch with a write failure: recovery precedes target writes, completed changes are rolled back and failure
  information is retained.
- Review path examples including symlinks: declared output remains within the destination.
- Review the recipe-side-effect example: approval and recovery are described only for the declared file operations.
