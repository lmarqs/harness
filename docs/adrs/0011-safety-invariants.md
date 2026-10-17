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
  approved result. Undoing it needs the prior content of every file the batch has written so far.

## Decision

1. Cook displays the actual final diff against current content and obtains approval for the entire batch before
   changing target files, as [0007](0007-ownership.md) decides.
2. Declared writes and deletions stay within the destination, symlinks included ([0012](0012-write-boundary.md)), and
   content outside the declared targets is preserved ([0008](0008-native-configuration.md)).
3. Cook holds the prior content of each file it writes until the batch completes, and rolls back the batch's completed
   writes on a write failure ([0007](0007-ownership.md)).
4. These guarantees cover declared batch operations. They do not sandbox trusted recipe execution, roll back arbitrary
   recipe side effects, or enforce the behavior of configured programs. Executing external commands is not a batch
   action ([0019](0019-action-kinds.md)).
5. Recorded ownership never blocks a change, and cooking does not need `cook.lock` ([0007](0007-ownership.md)).

## Reasons

- **1.** Review must show what will actually change, including deliberate replacement of existing values. Approval
  authorizes that combined result. Preapproval is an explicit choice; the absence of a terminal is not one.
- **2.** A destination boundary limits declared output, and precise targets reduce incidental edits. Whole-file
  replacement carries a broader review responsibility. None of these limits constrains arbitrary recipe code.
- **3.** Rollback must restore every file the batch already wrote, so their prior content has to be in hand before
  the first write. Keeping it only for the batch's duration covers that failure without a second history beside the
  repository's version control.
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
- A process killed mid-batch cannot roll back; earlier content then comes from the repository's version control.

Must be maintained:

- Accurate combined diffs, explicit batch approval, destination containment and recovery for declared operations.
- An honest distinction between these guarantees and recipe or configured-program execution.

## Confirmation

- For an edited target, is the actual proposed replacement or deletion displayed for approval?
- For a batch with a write failure, are completed changes rolled back and the failure reported?
- For paths that resolve through symlinks, does declared output remain within the destination?
- For the worked example, are approval and recovery described only for the declared file operations?
