# 0012. Cook confines declared batch operations to reviewed destination targets

## Objective

**Problem.** Repositories contain configuration, prose, and state that people and other tools depend on. Unrequested
normalization or cleanup can break their work, while refusing every change to existing content prevents useful setup
updates. People need to see the actual requested changes and keep unrelated content intact.

**Goal.** Requested changes are reviewable and recoverable, and content outside their scope remains intact.

## Context

- Worked example: `acme/api` keeps a task database with remote synchronization, agent settings, and hand-written
  agent instructions. Changing one program setting need not rewrite the database or the other settings.
- A file or directory name does not prove who created it. People may edit recipe-produced content after a batch.
- The agreed [architecture](../architecture.md#evaluation-review-and-writes) makes declared targets, current content,
  whole-batch approval, and recovery the operation contract.
- Trusted Groovy [recipes](../features/glossary.md) execute before batch writes. Code evaluated with the user's
  privileges can have effects that are not represented in the batch.
- Issue trackers, code-review platforms and coding agents have their own execution and delivery conventions.

## Decision

1. Declared writes and deletions stay within the invocation's current working directory, regardless of the recipe
   or executable location. Targets resolved through symlinks remain within that boundary.
2. Recipes come from paths inside the destination repository or from `https://` URLs
   ([0013](0013-organization-content.md)). Relative references to recipes and templates resolve from the referencing
   recipe's location; for an HTTPS source, they resolve against its source URL. A local operand or reference that
   resolves outside the repository is refused.
3. A batch affects its declared file paths, structured key paths, or marked text sections, including directory and
   symlink operations where applicable, and the targets its recipes owned before. Content outside those targets is
   preserved. Markers name the owning recipe and region ([0007](0007-ownership.md)); whole-file targets have
   whole-file scope.
4. Cook proposes changes against current content, including existing files and manual edits; review, whole-batch
   approval and ownership that never blocks a change follow [0007](0007-ownership.md).
5. The batch record is saved before the first target write and supports rollback on a write failure
   ([0007](0007-ownership.md)); neither it nor `cook.lock` is a prerequisite for apply.
6. Deletion follows the requested operation: removal by a recipe's new version, even when current values differ, or
   revert of a batch ([0007](0007-ownership.md)). Neither infers ownership from names, and applying one recipe does not
   remove output another recipe owns.
7. The batch contains file operations, not external commands, hosting-platform API calls, pushes, or delivery
   actions ([0019](0019-action-kinds.md)). Trusted recipe code can have side effects outside these guarantees
   ([0011](0011-safety-invariants.md)).

## Reasons

- **1.** The invocation directory makes the destination explicit even for remote recipes. Resolving symlink targets
  within it prevents a declared path from redirecting a batch write elsewhere.
- **2.** Source origins have a different role from the destination: they locate inputs without moving outputs or
  changing relative resolution when inputs are captured in the batch record. Keeping local sources inside the
  repository means every local input is one a reviewer can see in the same repository; the trade is that a shared
  recipe elsewhere on disk must be moved in or served over HTTPS.
- **3.** Target scope protects the unrelated database and settings in Context while allowing the setting the user
  requested to change. The trade is that declaring a whole file permits a broader change than declaring one key;
  users must review that scope in the diff.
- **4.** Current content is the relevant basis for approval. Creation records and directory names cannot establish
  whether today's change is wanted, so they hold no veto: useful edits proceed, and the decision rests on actual batch
  review, including deletion or replacement of content someone has edited.
- **5.** A saved batch record makes completed batch writes reversible if a later write fails. The trade is local
  storage for batch records; losing them loses recovery for recorded batches, not the ability to evaluate new
  recipes.
- **6.** Recorded ownership and a batch's reverse patches answer different questions: what a recipe's new version no
  longer declares, and what a particular batch changed. Names cannot answer either, as Context notes. The trade is
  that users retain the record of a batch they intend to revert.
- **7.** File review and recovery cannot govern remote delivery or arbitrary executable code. Keeping those effects
  outside the batch avoids a second writer to the team's issue tracker or code-review platform and a misleading
  sandbox promise. The trade is that users must trust recipes even when their declared output is confined to the
  destination.

## Rejected alternatives

### Write only previously owned content

At its best, creation records limit accidental changes to existing files. It loses because it prevents requested
updates to current configuration and requires a separate step to bring existing content under Cook before any change,
which review of the actual diff makes unnecessary.

### Normalize existing state or clean up by naming convention

At its best, every repository ends in a uniform state without explicit target declarations. It loses because names
collide and unrelated databases, settings, and hand-written content can be changed without being requested.

### Include delivery commands in the batch

At its best, setup and publication are one invocation. It loses because commands and remote writes have effects
that the declared file diff and local recovery patches cannot describe or reverse.

## Consequences

Costs:

- Approval can authorize changes to existing or edited content; it is not an ownership-based protection against a
  user accepting an unwanted diff.
- Recovery needs the local batch record, and arbitrary recipe side effects are outside batch rollback.

Must be maintained:

- Declared target scope, destination containment including symlinks, and preservation of unrelated content.
- Whole-batch diff and approval, a batch record before target writes, and the distinction between removal by a
  recipe's new version and revert of a batch.

## Confirmation

- For a key update alongside unrelated keys and text, a whole-file replacement, and a target resolving outside the
  destination through a symlink, does the declared scope determine the diff and is the escaping target refused?
- Does an existing manually edited target go through the ordinary batch diff and approval flow, whether or not
  `cook.lock` records its owner, while applying another recipe leaves unrelated prior output in place?
- Are a recipe operand and a template reference that resolve outside the repository both refused?
- Do descriptions of recovery ordering and write failures match the architecture, without treating recipe evaluation
  side effects as reversible batch writes?
