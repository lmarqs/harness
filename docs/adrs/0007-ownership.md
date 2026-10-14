# 0007. Declared targets define changes reviewed against current content

## Objective

**Problem.** Developers share configuration with other people and tools. An update or deletion can erase a manual
change, and a request to delete configuration is different from a request to restore an earlier value.

**Goal.** Developers see the actual additions, updates and deletions before approving them, while unrelated content
stays intact and a recorded operation can be reversed.

## Context

- Cook evaluates trusted Groovy recipes into ordered file operations, as defined by the
  [architecture](../architecture.md#evaluation-review-and-writes).
- Configuration can occupy whole files, keys in structured native files, or sections in shared text. JSON cannot
  carry comment markers; a structured key path can address content without adding metadata to the native file.
- Worked example: in `acme/api`, a recipe changes a configuration key from `old` to `new`. A developer then changes
  it to `custom`. Deleting that key and restoring its value before the original run produce different changes.
- Programs consume native configuration directly. Teams can commit shared configuration for teammates and reviewers;
  local operation history serves recovery rather than distribution of that configuration.

## Decision

1. Recipe declarations address whole files by path, structured content by key path, and shared text by begin/end
   markers. Markers identify boundaries, not owners. Use the format's comment syntax, including HTML comments for
   Markdown. Preserve content outside the declared targets ([0008](0008-native-configuration.md)).
2. Recipes and their operations compose left to right against current content. Later instructions can replace earlier
   values at the same target. Applying a batch does not withdraw unrelated output from an earlier recipe.
3. Show the final combined diff in the terminal and obtain approval for the whole batch before target writes.
   Preapproval still displays the diff; noninteractive execution alone does not authorize writes. Existing or manually
   edited targets follow this same flow, without an adoption flag, alias, separate adoption step or drift veto.
4. Deleting contributions evaluates the supplied recipe version and deletes its declared targets, even when their
   current values differ. It does not restore adopted originals. Reversing a recorded run instead uses its history to
   restore that operation's prior values, proposing the reversal against current content and preserving unrelated
   content. Users supply recipe versions; Cook does not infer a persistent recipe identity.
5. Apply and discard require no committed or local ownership manifest and no previous history. Each invocation evaluates
   supplied recipes again; history does not skip them as already applied. An unchanged result may have an empty diff.
6. Record one operation for the batch in ignored local history, including evaluated and consumed recipe/template
   sources, origins, arguments, order, operation and result or failure log. Save one reversible patch per touched file
   before the first target write. On write failure, roll back prior batch changes and retain patches and the failure
   record. Reverting a run requires its history; losing history does not prevent apply or discard.

Approval, destination boundaries and recovery govern declared batch operations
([0012](0012-write-boundary.md)). They do not sandbox trusted recipe code or cover arbitrary side effects during
its evaluation. Concrete patch representations and history schemas remain outside this decision.

## Reasons

- **1.** Paths, keys and marked sections identify the scope a developer reviews without classifying content by creator.
  Format-appropriate markers let people and programs continue reading a shared file. Key paths avoid requiring
  comments in formats that do not allow them.
- **2.** Ordered composition lets recipes intentionally overlap. Limiting an invocation to declared targets keeps a
  request for one update from implicitly deleting another recipe's unrelated output.
- **3.** Reviewing the actual diff exposes a manual edit that a requested update would replace. Approval makes that
  choice explicit without requiring a separate ownership reconciliation process. Whole-batch approval means the
  developer cannot accept only selected files or hunks.
- **4.** In the worked example, deletion proposes removing `custom`; reversal of the original run proposes replacing
  `custom` with `old`. Declarations answer the first request and recorded prior values answer the second. The cost is
  that users must retain the recipe version they intend to discard and the history of a run they intend to reverse.
- **5.** Current declarations and content suffice for a new proposal. An ownership database would make ordinary editing
  depend on clone-local records, while a record of earlier evaluation cannot determine what a recipe requests now.
- **6.** Recovery needs the operation's actual prior values, not an ownership catalog. A combined patch per file captures
  overlapping instructions as one result. Saving patches before writes supports rollback after partial failure,
  at the cost of local storage and a requirement to retain history for later reversal.

## Rejected alternatives

### Reconcile a persistent ownership manifest

At its best, a manifest identifies earlier generated contributions and their originals without reevaluating recipes.
It loses because it makes editing depend on persistent ownership, treats intentional overlap as a transfer, and turns
omission into withdrawal rather than an explicit deletion request.

### Refuse manually edited targets

At its best, refusing a changed hash prevents a generated update from replacing an edit unnoticed. It loses because
an actual diff and approval already expose that replacement, while refusal blocks a developer's intended change.

### Use one deletion operation to restore originals

At its best, one operation provides a way back without explaining two intents. It loses because deleting declared
contributions and restoring a particular operation's prior values differ, as the worked example shows.

## Consequences

Costs:

- Approval can authorize replacing or deleting manually edited target content; review must show that actual change.
- A supplied recipe version may declare different targets from an earlier version. Cook does not reconstruct that
  earlier version for deletion.
- Local recovery information is lost with its history. Native configuration can still be used and changed without it.

Must be maintained:

- Target addressing, format-appropriate marker boundaries and combined diffs that preserve unrelated content.
- Recovery records that distinguish reversing an operation from deleting recipe contributions.

## Confirmation

Review the intended behavior using these scenarios:

- Two recipes set the same key in order; the combined diff shows the later value and keeps unrelated earlier output.
- A manual edit inside a declared section appears in the proposed change; text outside the markers stays intact.
- In the `old` → `new` → `custom` example, discard deletes the key and revert proposes `old`, with no drift veto.
- With no previous history, apply and discard still evaluate supplied recipes. An identical invocation evaluates again.
- Recovery patches precede target writes; a write failure rolls back completed batch changes and retains its record.
