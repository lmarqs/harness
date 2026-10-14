# 0007. Declared targets define changes reviewed against current content

## Objective

**Problem.** Developers share configuration with other people and tools. An update can erase a manual change, output
that a recipe stops declaring can linger after the recipe changes, and undoing one operation is a different request from
changing what a recipe declares.

**Goal.** Developers see the actual additions, updates and deletions before approving them, while unrelated content
stays intact, every clone agrees on which recipe produced which output, and a recorded operation can be reversed.

## Context

- Cook evaluates trusted Groovy recipes into ordered file operations, as defined by the
  [architecture](../architecture.md#evaluation-review-and-writes).
- Configuration can occupy whole files, keys in structured native files, or sections in shared text. JSON cannot
  carry comment markers; a structured key path can address content without adding metadata to the native file.
- Git distributes committed files to every clone and shows their changes in pull requests; files matched by an ignore
  rule stay local to one clone.
- Worked example: in `acme/api`, version 1 of a recipe changes a configuration key from `old` to `new` and adds a
  marked section of build commands. Version 2 declares the key but not the section. A developer then changes the key to
  `custom`.

## Decision

1. Recipe declarations address whole files by path, structured content by key path, and shared text by begin/end
   markers, `cook:begin <recipe>/<region>` and `cook:end <recipe>/<region>`, which name the owning recipe and the region
   within it. Markers use the format's comment syntax, including HTML comments for Markdown. Preserve content outside
   the declared targets ([0008](0008-native-configuration.md)).
2. Recipes and their operations compose left to right against current content. When two recipes write the same
   section, key or file, the last one wins and becomes its owner; the diff is the only notice. Applying recipe B does
   not remove output that recipe A owns, even when A is omitted from the invocation.
3. The repository commits `cook.lock`, a YAML file at its root. For each applied recipe it records the short name, the
   source as a repository-relative path or `https://` URL, the source's sha256, and the owned sections, keys and files.
   It records no recipe arguments. Applying a new version of a recipe also removes what its previous version owned and
   the new version no longer declares; the diff shows that removal. Without a lock entry for a recipe, apply removes
   nothing on its behalf.
4. Ownership decides what a recipe's later versions update and remove; it never blocks a change. Existing or manually
   edited targets follow the same batch diff and approval flow as any other change.
5. The core proposes the final combined diff, writes only the changes it receives as accepted, and records only those
   in `cook.lock` and the batch record. The CLI displays that diff in the terminal and accepts the whole batch.
   Preapproval still displays the diff; noninteractive execution alone does not authorize writes. Choosing individual
   changes is a separate interface decision.
6. Reverting a batch applies its reverse patches against current content without evaluating recipes. Later edits that
   do not overlap the batch's changes survive; an overlapping edit makes the revert write nothing and list each
   conflicting file and section. `cook.lock` is one of the files a batch touches, so revert also restores the
   ownership the batch changed. Discarding a recipe, which removes what `cook.lock` lists for it without evaluating it,
   is deferred until a concrete need.
7. Apply requires neither batch history nor `cook.lock`. Each invocation evaluates supplied recipes again; history does
   not skip them as already applied. An unchanged result may have an empty diff.
8. Each batch has one record in the ignored `.cook/batches/<batchId>/` directory, where the batch ID is the UTC start
   time. It holds one reversible patch per touched file, `cook.lock` included, the evaluated and consumed recipe and
   template sources with their origins, arguments, order, and result or failure log. Save the record before the first
   target write. On write failure, roll back prior batch changes and retain the record and failure. Reverting a batch
   requires its record; losing `.cook/` does not prevent apply, and ownership survives in `cook.lock`.

Approval, destination boundaries and recovery govern declared batch operations
([0012](0012-write-boundary.md)). They do not sandbox trusted recipe code or cover arbitrary side effects during
its evaluation.

## Reasons

- **1.** Paths, keys and marked sections identify the scope a developer reviews. A marker that names its recipe and
  region lets several recipes keep separate sections in one file and lets a reader see which recipe produced a section.
  Format-appropriate markers let people and programs continue reading a shared file. Key paths avoid requiring
  comments in formats that do not allow them.
- **2.** Ordered composition lets recipes intentionally overlap, and the last writer is the one whose later versions
  should maintain the target. Limiting an invocation to declared and previously owned targets keeps a request for one
  recipe from deleting another recipe's output. The trade: an overlap transfers ownership with no notice beyond the
  diff, so reviewers must read it.
- **3.** In the worked example, the section version 1 added would otherwise linger after version 2 stops declaring it.
  Comparing the new declarations with the recorded ownership answers that, and only a record of the previous version's
  targets can supply that comparison. Committing the record gives every clone the same answer and puts ownership changes
  in pull-request review; key paths and whole files carry no markers, so markers alone cannot hold it. The lock lists
  the targets each recipe owns, and reading that ownership needs no arguments; the batch record keeps the arguments
  (point 8). The trade: a generated file to commit and review.
- **4.** Reviewing the actual diff exposes a manual edit that a requested update would replace. Approval makes that
  choice explicit; a veto would block the developer's intended change, and an adoption step would add a second
  contract for content the diff already shows.
- **5.** Recording only accepted changes keeps `cook.lock` and the batch record describing what was written, whatever
  interface collected the acceptance. Whole-batch acceptance keeps the CLI's contract simple. The trade: a CLI user
  cannot accept only selected files or hunks.
- **6.** In the worked example, reverting the version 1 batch would restore `old`, but the developer's `custom` overlaps
  that change, so the revert lists the key and writes nothing instead of erasing the edit. Reverse patches restore
  the batch's own changes without depending on recipe code that may have changed. Discard needs no record beyond
  `cook.lock`, so deferring it costs no information. The trade: until discard exists, stopping a recipe means
  reverting its batches or editing its output by hand.
- **7.** Current declarations and content suffice for a new proposal; a missing lock only means no previous ownership
  to compare. A record of earlier evaluation cannot determine what a recipe requests now.
- **8.** Recovery needs the batch's actual prior values. A combined patch per file captures overlapping instructions as
  one result. Saving the record before writes supports rollback after partial failure, at the cost of local storage and
  a requirement to retain the record for later reversal. The record is local recovery data rather than shared review
  material, so it stays ignored.

## Rejected alternatives

### Keep ownership in ignored local state

At its best, local state avoids a generated file in commits. It loses because each clone then knows a different
ownership: a teammate applying a recipe's new version would leave behind what another clone recorded, and reviewers
never see ownership change.

### Refuse manually edited targets

At its best, refusing a changed hash prevents a generated update from replacing an edit unnoticed. It loses because
an actual diff and approval already expose that replacement, while refusal blocks a developer's intended change.

### Withdraw output of recipes omitted from an invocation

At its best, the repository then matches exactly the recipes in the latest invocation. It loses because a request to
apply one recipe would delete every other recipe's output, turning omission into an unreviewed deletion request.

### Revert by evaluating an earlier recipe version

At its best, a revert then needs no stored patches. It loses because recipe code and its inputs change, the earlier
version may be unavailable, and evaluating it would overwrite later edits rather than detect the overlap.

## Consequences

Costs:

- Approval can authorize replacing or deleting manually edited target content; review must show that actual change.
- `cook.lock` changes with every batch that changes ownership, and reviewers read those changes with the native files.
- Local recovery information is lost with `.cook/`. Native configuration and ownership remain usable without it.

Must be maintained:

- Target addressing, owner-named marker boundaries and combined diffs that preserve unrelated content.
- `cook.lock` entries that match what was written, and batch records whose reverse patches include `cook.lock`.

## Confirmation

Review the intended behavior using these scenarios:

- Two recipes set the same key in order; the combined diff shows the later value, `cook.lock` names the later recipe as
  owner, and unrelated earlier output stays.
- A manual edit inside a declared section appears in the proposed change; text outside the markers stays intact.
- In the worked example, applying version 2 proposes removing the section; reverting the version 1 batch after the
  `custom` edit writes nothing and names the key.
- With no batch history and no `cook.lock`, apply still evaluates supplied recipes and removes nothing on their behalf.
  An identical invocation evaluates again.
- The batch record precedes target writes; a write failure rolls back completed batch changes and retains its record.
