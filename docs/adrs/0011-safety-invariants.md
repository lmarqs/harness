# 0011. Cook's guarantees cover declared batch operations

## Objective

**Problem.** A developer needs to understand both the changes being approved and the limits of that approval.
Calling configuration editing safe without stating those limits can suggest protection from executable code or from
programs the configuration controls.

**Goal.** Developers can review requested file changes and recover from a failed write without mistaking those
mechanisms for protection from arbitrary recipe execution or agent sessions.

## Context

- The [architecture](../architecture.md#evaluation-review-and-writes) defines trusted Groovy
  [recipe](../features/glossary.md) evaluation, ordered file operations, terminal review, approval, recovery and
  writes.
- Recipes are executable code. Their evaluation can have side effects outside their declared file operations.
- Worked example: a recipe declares a settings-key update but also executes code that contacts a service during
  evaluation. A diff of the settings file describes only the declared update; rejecting it cannot undo the contact.
- A partially written batch leaves a destination in a different state from either the starting content or the
  approved result. The batch record is needed before any target write.

## Decision

1. Cook displays the actual final diff against current content and obtains approval for the entire batch before
   changing target files, as [0007](0007-ownership.md) decides.
2. Declared writes and deletions stay within the destination, symlinks included ([0012](0012-write-boundary.md)), and
   content outside the declared targets is preserved ([0008](0008-native-configuration.md)).
3. Cook saves the batch record before the first target write and rolls back the batch on a write failure; a revert
   writes nothing when a later edit overlaps the batch's changes ([0007](0007-ownership.md)).
4. These guarantees cover declared batch operations. They do not sandbox trusted recipe execution, roll back arbitrary
   recipe side effects, or enforce the behavior of configured programs. Executing external commands is not a batch
   action ([0019](0019-action-kinds.md)).
5. Recorded ownership never blocks a change, and apply needs neither `cook.lock` nor earlier batch records
   ([0007](0007-ownership.md)).

## Reasons

- **1.** Review must show what will actually change, including deliberate replacement of existing values. Approval
  authorizes that combined result. Preapproval is an explicit choice; the absence of a terminal is not one.
- **2.** A destination boundary limits declared output, and precise targets reduce incidental edits. Whole-file
  replacement carries a broader review responsibility. None of these limits constrains arbitrary recipe code.
- **3.** A batch record saved after a write would be too late for that write's failure. The same patches later restore
  the batch's own prior values without reevaluating recipes, and refusing on overlap keeps later edits. Recovery
  depends on keeping the relevant local batch record.
- **4.** The example separates output approval from code trust. Claiming protection beyond the declared batch would
  promise control the engine does not provide. Developers must choose which recipes to trust before evaluating them.
- **5.** Current content is the basis for review. Recorded ownership decides what a recipe's later versions remove,
  not whether a change may proceed. Not gating on ownership keeps operations usable without prior state but makes the
  actual diff, including deletion of edited values, essential.

## Rejected alternatives

### Enforce minimum agent permissions in the engine

At its best, the engine gives every agent session it configures a shared minimum of permission and verification
guards. It loses because enforcing them requires the engine to start and supervise agent sessions, outside its role
([0001](0001-role-of-the-tool.md)), and it still does not protect against arbitrary trusted recipe execution.

### Refuse targets edited since Cook wrote them

At its best, Cook records a hash of each owned target and refuses to change one whose current content no longer
matches, so an edit to generated content is never replaced unnoticed. It loses because it substitutes recorded
ownership for review of the requested operation: the diff already shows the replacement for approval, and refusal
blocks the change the developer requested.

### Treat approval as a recipe sandbox

At its best, one approval covers everything a recipe does, so a developer makes a single trust decision per
invocation. It loses because the batch diff cannot describe or reverse arbitrary code effects, as the worked example
shows; presenting approval that way would misstate the trust boundary.

## Consequences

Costs:

- Recipe evaluation must be trusted separately from approval of its output.
- Preapproval places responsibility for the entire displayed change on the caller.
- Losing `.cook/` loses the batch records of past batches. Apply remains available and ownership survives in
  `cook.lock`; reverting a particular batch requires its record.

Must be maintained:

- Accurate combined diffs, explicit batch approval, destination containment and recovery for declared operations.
- An honest distinction between these guarantees and recipe or configured-program execution.

## Confirmation

- For an edited target, is the actual proposed replacement or deletion displayed for approval?
- For a batch with a write failure, does the batch record precede target writes, are completed changes rolled back,
  and is the failure retained?
- For paths that resolve through symlinks, does declared output remain within the destination?
- For the worked example, are approval and recovery described only for the declared file operations?
