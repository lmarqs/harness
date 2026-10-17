# 0007. Declared targets define changes reviewed against current content

## Objective

**Problem.** Developers share configuration with other people and tools. An update can erase a manual change, output
that a recipe stops declaring can linger after the recipe changes, and removing everything a recipe produced is a
different request from changing what it declares.

**Goal.** Developers see the actual additions, updates and deletions before approving them, while unrelated content
stays intact, every clone agrees on which recipe produced which output, and a recipe's output can be removed without
disturbing anything else.

## Context

- Cook evaluates trusted Groovy [recipes](../features/glossary.md) into ordered file operations, as defined by the
  [architecture](../architecture.md#evaluation-review-and-writes).
- Configuration can occupy whole files, keys in structured native files, or sections in shared text. JSON cannot
  carry comment markers; a structured key path can address content without adding metadata to the native file.
- Git distributes committed files to every clone and shows their changes in pull requests; files matched by an ignore
  rule stay local to one clone.
- Worked example: in `acme/api`, version 1 of a recipe changes a configuration key from `old` to `new` and adds a
  marked section of build commands. Version 2 declares the key but not the section. A developer then changes the key to
  `custom`, and later stops using the recipe.

## Decision

1. Recipe declarations address whole files by path, structured content by key path, and sections of shared text by
   markers, `cook:begin <recipe>#<region>` and `cook:end <recipe>#<region>`, which name the owning recipe's short name
   and the region within it. Markers use the format's comment syntax, including HTML comments for Markdown. Content
   outside the declared targets is preserved ([0008](0008-native-configuration.md)).
2. Recipes and their operations compose left to right against current content. When two recipes write the same
   section, key or file, the last one wins and becomes its owner; the diff is the only notice. Cooking recipe B does
   not remove output that recipe A owns, even when A is omitted from the invocation.
3. The repository commits `cook.lock`, a YAML file at its root ([example](../architecture.md#ownership-and-the-lock)).
   - For each cooked recipe it records the short name, the source as a repository-relative path, `https://` URL or
     coordinates, the source's sha256, and the owned sections, keys, files and links. It records no recipe arguments.
   - Cooking a new version of a recipe also removes what its previous version owned and the new version no longer
     declares; the diff shows that removal.
   - Without a lock entry for a recipe, cooking removes nothing on its behalf.
4. Ownership decides what a recipe's later versions update and remove; it never blocks a change. Existing or manually
   edited targets follow the same batch diff and approval flow as any other change.
5. The core proposes the final combined diff, writes only the changes it receives as accepted, and records only those
   in `cook.lock`. The CLI displays that diff in the terminal and accepts the whole batch.
   Preapproval still displays the diff; noninteractive execution alone does not authorize writes. Choosing individual
   changes is a separate interface decision.
6. Discarding recipes removes what `cook.lock` lists for each, without evaluating them: a section with its markers, a
   key, a whole file or a link. A file left empty is deleted, and each recipe's entry leaves the lock. Discard restores
   no value the recipe replaced. It follows the same diff and whole-batch approval as cooking.
7. Cooking does not require `cook.lock`. Each invocation evaluates supplied recipes again; nothing skips a recipe as
   already cooked. An unchanged result may have an empty diff.
8. Cook keeps no history of its own. `cook.lock` is its only record; on a write failure, Cook rolls back the batch's
   completed writes in place and reports the failure. Earlier content lives in the repository's version control.

The trust limits of these guarantees follow [0011](0011-safety-invariants.md), and the destination boundary follows
[0012](0012-write-boundary.md).

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
  the targets each recipe owns, and reading that ownership needs no arguments. The trade: a generated file to commit
  and review.
- **4.** Reviewing the actual diff exposes a manual edit that a requested update would replace. Approval makes that
  choice explicit; a veto would block the developer's intended change, and a separate step to bring existing content
  under Cook before changing it would add a second contract for content the diff already shows.
- **5.** Recording only accepted changes keeps `cook.lock` describing what was written, whatever
  interface collected the acceptance. Whole-batch acceptance keeps the CLI's contract simple. The trade: a CLI user
  cannot accept only selected files or hunks.
- **6.** In the worked example, the developer who stops using the recipe wants its key and section gone, and nothing
  else. The lock already names exactly those targets, so discard needs no recipe code, which may have changed or
  disappeared, and no record beyond the lock. Cook owns content, not files: a file it created only to hold that
  content has no reason to stay once the content goes. The trade: the developer's `custom` value goes with the key,
  and the diff is the only notice.
- **7.** Current declarations and content suffice for a new proposal; a missing lock only means no previous ownership
  to compare. A record of earlier evaluation cannot determine what a recipe requests now.
- **8.** The repository's version control already holds every earlier version of the files Cook writes, with tools
  to restore, compare and merge them. A second history inside Cook would duplicate that, with weaker tools and one
  clone's view. Rollback after a partial failure needs only the prior content of the batch's own files, which Cook
  holds in memory while it writes. The trade: content that was never committed can be restored only by hand.

## Rejected alternatives

### Keep ownership in ignored local state

At its best, local state avoids a generated file in commits. It loses because each clone then knows a different
ownership: a teammate cooking a recipe's new version would leave behind what another clone recorded, and reviewers
never see ownership change.

### Refuse manually edited targets

At its best, refusing a target whose content hash changed since Cook wrote it prevents a generated update from
replacing an edit unnoticed. It loses because an actual diff and approval already expose that replacement, while
refusal blocks a developer's intended change.

### Withdraw output of recipes omitted from an invocation

At its best, the repository then matches exactly the recipes in the latest invocation. It loses because a request to
cook one recipe would delete every other recipe's output, turning omission into an unreviewed deletion request.

### Record each batch and reverse it later

At its best, a stored reverse patch per batch restores the exact values a batch replaced, keeping later edits beside
them. It loses because it rebuilds version control inside Cook: patches, conflict detection and history, kept in one
clone and lost with it, while the repository already restores earlier content with better tools.

### Restore replaced values on discard

At its best, discarding a recipe puts back what was there before it. It loses because that needs a copy of every value
the recipe ever replaced, which is the batch history rejected above; and once other edits followed, the old value may
no longer be what anyone wants.

## Consequences

Costs:

- Approval can authorize replacing or deleting manually edited target content; review must show that actual change.
- `cook.lock` changes with every batch that changes ownership, and reviewers read those changes with the native files.
- Discard removes values a recipe replaced instead of restoring them; restoring them is the repository history's job.

Must be maintained:

- Target addressing, owner-named marker boundaries and combined diffs that preserve unrelated content.
- `cook.lock` entries that match what was written, and that list everything discard must remove.

## Confirmation

- Two recipes set the same key in order; the combined diff shows the later value, `cook.lock` names the later recipe as
  owner, and unrelated earlier output stays.
- A manual edit inside a declared section appears in the proposed change; text outside the markers stays intact.
- In the worked example, cooking version 2 proposes removing the section; discarding the recipe after the `custom`
  edit proposes removing the key with its `custom` value, and the recipe's lock entry.
- Discarding a recipe whose section shares a file with hand-written text removes the section and its markers and
  keeps the text; a file holding only the recipe's content is deleted.
- With no `cook.lock`, cooking still evaluates supplied recipes and removes nothing on their behalf. An identical
  invocation evaluates again.
- A write failure rolls back completed batch changes and leaves no file of Cook's behind.
