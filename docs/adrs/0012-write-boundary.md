# 0012. Cook confines declared batch operations to reviewed destination targets

## Objective

**Problem.** Repositories contain configuration, prose, and state that people and other tools depend on. Unrequested
normalisation or cleanup can break their work, while refusing every change to existing content prevents useful setup
updates. People need to see the actual requested changes and keep unrelated content intact.

**Goal.** Requested changes are reviewable and recoverable, and content outside their scope remains intact.

## Context

- Worked example: `acme/api` keeps a task database with remote synchronisation, a loop-runner configuration, and
  hand-written agent instructions. Changing one program setting need not rewrite the database or the other settings.
- A file or directory name does not prove who created it. People may edit recipe-produced content after a run.
- The agreed [architecture](../architecture.md#evaluation-review-and-writes) makes declared targets, current content,
  whole-batch approval, and recovery the operation contract; it does not require persistent ownership state.
- Trusted Groovy recipes execute before batch writes. Code evaluated with the user's privileges can have effects
  that are not represented in the batch.
- Issue trackers, code-review platforms, loop runners, and worker sessions have their own execution and delivery
  conventions. Configuring their native files does not make Cook their controller.

## Decision

1. Declared writes and deletions stay within the invocation's current working directory, regardless of the recipe
   or executable location. Targets resolved through symlinks remain within that boundary. Relative source references
   resolve from the referencing recipe's original location, or its source URL for HTTPS sources.
2. A batch affects its declared file paths, structured key paths, or begin/end text sections, including directory
   and symlink operations where applicable. Content outside those targets is preserved. Markers identify text
   boundaries, not ownership; whole-file targets have whole-file scope.
3. Cook proposes changes against current content, including existing files and manual edits, and presents one final
   combined terminal diff. Approval covers the whole batch. Explicit preapproval still displays the diff;
   noninteractive execution without it does not authorize writes. There is no adoption flag, compatibility alias,
   separate adoption step, or ownership/drift veto.
4. Cook saves recovery patches before the first target write. A write failure rolls back changes already made by
   the batch and retains the patches and failure record. Local history records the operation and consumed sources;
   it is not an ownership database or a prerequisite for apply or discard.
5. Deletion follows the requested operation. Discard evaluates the supplied recipe version and proposes deletion
   of its declared contributions, even when their current values differ. Revert uses a run's recorded history to
   propose restoration of prior values against current content. Neither operation infers ownership from names or
   automatically withdraws unrelated outputs omitted from a later apply.
6. The engine's batch contains file operations, not external commands, hosting-platform API calls, pushes, or
   lifecycle delivery actions. Approval, output boundaries, and recovery cover declared batch operations only;
   trusted recipe code is not sandboxed and can have arbitrary side effects outside these guarantees.

## Reasons

- **1.** The invocation directory makes the destination explicit even for remote recipes. Resolving symlink targets
  within it prevents a declared path from redirecting a batch write elsewhere. Source origins have a different role:
  they locate inputs without moving outputs or changing relative resolution when inputs are snapshotted.
- **2.** Target scope protects the unrelated database and settings in Context while allowing the setting the user
  requested to change. The trade is that declaring a whole file permits a broader change than declaring one key;
  users must review that scope in the diff.
- **3.** Current content is the relevant basis for approval. Creation records and directory names cannot establish
  whether today's change is wanted. Removing their veto allows useful edits, but places the decision on actual
  batch review, including deletion or replacement of content someone has edited.
- **4.** Saved recovery information makes completed batch writes reversible if a later write fails. The trade is
  local history storage; losing it loses recovery for recorded runs, not the ability to evaluate new recipes.
- **5.** Recipe declarations and run history answer different questions: what to delete now and what a particular
  operation changed. The trade is that users supply the recipe version they intend to discard and retain the history
  of a run they intend to revert; Cook does not infer either from persistent recipe identity.
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
- Whole-batch diff and approval, recovery before target writes, and the distinction between discard and revert.

## Confirmation

- Review a key update alongside unrelated keys and text, a whole-file replacement, and a target resolving outside
  the destination through a symlink. The declared scope determines the diff; an escaping target is not permitted.
- Review an existing manually edited target through the ordinary batch diff and approval flow, without adoption or
  previous ownership records. Applying another recipe does not delete unrelated prior output.
- Review recovery ordering and write-failure behavior against the architecture, without treating recipe evaluation
  side effects as reversible batch writes. Concrete patch formats and overlapping-reversal mechanics remain deferred.
