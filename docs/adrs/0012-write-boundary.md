# 0012. Cook confines declared batch operations to reviewed destination targets

## Objective

**Problem.** Repositories contain configuration, prose, and state that people and other tools depend on. Unrequested
normalisation or cleanup can break their work, while refusing every change to existing content prevents useful setup
updates. People need to see the actual requested changes and keep unrelated content intact.

**Goal.** Requested changes are reviewable and recoverable, and content outside their scope remains intact.

## Context

- Worked example: `acme/api` keeps a task database with remote synchronisation, a loop-runner configuration, and
  hand-written agent instructions. Changing one program setting need not rewrite the database or the other settings.
- A file or directory name does not prove who created it. People may edit recipe-produced content after a batch.
- The agreed [architecture](../architecture.md#evaluation-review-and-writes) makes declared targets, current content,
  whole-batch approval, and recovery the operation contract. Ownership recorded in the committed `cook.lock` decides
  what a recipe's later versions remove and never blocks a change.
- Trusted Groovy recipes execute before batch writes. Code evaluated with the user's privileges can have effects
  that are not represented in the batch.
- Issue trackers, code-review platforms, loop runners, and worker sessions have their own execution and delivery
  conventions. Configuring their native files does not make Cook their controller.

## Decision

1. Declared writes and deletions stay within the invocation's current working directory, regardless of the recipe
   or executable location. Targets resolved through symlinks remain within that boundary. Recipes come from paths
   inside the destination repository or from `https://` URLs. Relative references to recipes and templates resolve
   from the referencing recipe's location, or against its source URL for HTTPS sources; a local operand or reference
   that resolves outside the repository is refused.
2. A batch affects its declared file paths, structured key paths, or begin/end text sections, including directory
   and symlink operations where applicable, and the targets its recipes owned before. Content outside those targets is
   preserved. Markers name the owning recipe and region ([0007](0007-ownership.md)); whole-file targets have
   whole-file scope.
3. Cook proposes changes against current content, including existing files and manual edits, and presents one final
   combined terminal diff. Approval covers the whole batch. Explicit preapproval still displays the diff;
   noninteractive execution without it does not authorize writes. Recorded ownership never blocks a change; there is
   no adoption flag, compatibility alias, separate adoption step, or drift veto.
4. Cook saves recovery patches before the first target write. A write failure rolls back changes already made by
   the batch and retains the patches and failure record. The batch record in the ignored `.cook/batches/<batchId>/`
   directory holds the patches and the consumed sources with their origins. Ownership lives in the committed
   `cook.lock`; neither is a prerequisite for apply.
5. Deletion follows the requested operation. Applying a recipe's new version proposes removing what its previous
   version owned and no longer declares, even when current values differ. Revert applies a batch's reverse patches
   against current content and writes nothing when a later edit overlaps them. Neither infers ownership from names,
   and applying one recipe does not remove output another recipe owns. Discarding a recipe is deferred.
6. The engine's batch contains file operations, not external commands, hosting-platform API calls, pushes, or
   lifecycle delivery actions. Approval, output boundaries, and recovery cover declared batch operations only;
   trusted recipe code is not sandboxed and can have arbitrary side effects outside these guarantees.

## Reasons

- **1.** The invocation directory makes the destination explicit even for remote recipes. Resolving symlink targets
  within it prevents a declared path from redirecting a batch write elsewhere. Source origins have a different role:
  they locate inputs without moving outputs or changing relative resolution when inputs are snapshotted. Keeping local
  sources inside the repository means every local input is one a reviewer can see in the same repository; the trade
  is that a shared recipe elsewhere on disk must be moved in or served over HTTPS.
- **2.** Target scope protects the unrelated database and settings in Context while allowing the setting the user
  requested to change. The trade is that declaring a whole file permits a broader change than declaring one key;
  users must review that scope in the diff.
- **3.** Current content is the relevant basis for approval. Creation records and directory names cannot establish
  whether today's change is wanted. Removing their veto allows useful edits, but places the decision on actual
  batch review, including deletion or replacement of content someone has edited.
- **4.** Saved recovery information makes completed batch writes reversible if a later write fails. The trade is
  local history storage; losing it loses recovery for recorded batches, not the ability to evaluate new recipes.
- **5.** Recorded ownership and a batch's reverse patches answer different questions: what a recipe's new version no
  longer declares, and what a particular batch changed. Names cannot answer either, as Context notes. The trade is
  that users retain the record of a batch they intend to revert.
- **6.** File review and recovery cannot govern remote delivery or arbitrary executable code. Keeping those effects
  outside the batch avoids a second writer to the team's record and a misleading sandbox promise. The trade is
  that users must trust recipes even when their declared output is confined to the destination.

## Rejected alternatives

### Write only previously owned content

At its best, creation records limit accidental changes to existing files. It loses because it prevents requested
updates to current configuration and adds ownership/adoption gates that the agreed review flow excludes.

### Normalise existing state or clean up by naming convention

At its best, every repository ends in a uniform state without explicit target declarations. It loses because names
collide and unrelated databases, settings, and hand-written content can be changed without being requested.

### Include delivery commands in the batch

At its best, setup and publication are one invocation. It loses because commands and remote writes have effects
that the declared file diff and local recovery patches cannot describe or reverse.

## Consequences

Costs:

- Approval can authorize changes to existing or edited content; it is not an ownership-based protection against a
  user accepting an unwanted diff.
- Recovery needs local history, and arbitrary recipe side effects are outside batch rollback.

Must be maintained:

- Declared target scope, destination containment including symlinks, and preservation of unrelated content.
- Whole-batch diff and approval, recovery before target writes, and the distinction between removal by a recipe's
  new version and revert of a batch.

## Confirmation

- Review a key update alongside unrelated keys and text, a whole-file replacement, and a target resolving outside
  the destination through a symlink. The declared scope determines the diff; an escaping target is not permitted.
- Review an existing manually edited target through the ordinary batch diff and approval flow, without adoption,
  whether or not `cook.lock` records its owner. Applying another recipe does not delete unrelated prior output.
- Review a recipe operand and a template reference that resolve outside the repository: both are refused.
- Review recovery ordering and write-failure behavior against the architecture, without treating recipe evaluation
  side effects as reversible batch writes. Concrete patch formats remain an implementation choice.
