# 0018. Native instruction text states conventions independently of Cook

## Objective

**Problem.** Teammates and coding agents need to understand repository conventions even when they do not use the tool
that configured them. Instructions can become misleading when they assume that tool is always present, and long
instruction files add reading cost to every session.

**Goal.** Repository conventions remain readable without Cook, with concise instructions that explain the actual
setup readers encounter.

## Context

- The [architecture](../architecture.md#ingredients-recipes-and-cookbooks) uses native files that configured programs
  consume directly, without invoking Cook to read them.
- Worked example: `acme/api` uses a recipe to write a marked section in an agent instruction file describing its test
  command, and a section in its ignore file. A teammate without Cook can read the instructions and use the command.
- A path remains ignored while an ignore entry covers it. Deleting the last covering entry can expose existing files
  as untracked; deleting an ignore entry does not delete those files.
- Instructions about repository practice and instructions for invoking Cook have different readers and dependencies.

## Decision

1. Recipe-declared instruction text states repository conventions in plain language. Describe the configured program
   or repository practice directly; references to Cook belong where readers need to invoke Cook. Keep frequently read
   instructions concise and put supporting detail in documentation, without an engine-enforced length bound.
2. Sections in shared text files use `cook:begin <recipe>/<region>` and `cook:end <recipe>/<region>` markers with
   the format's appropriate comment syntax, including HTML comments in Markdown and hash comments in ignore files.
   Markers name the owning recipe and region ([0007](0007-ownership.md)). Text outside the declared section is
   preserved ([0008](0008-native-configuration.md)).
3. Applying recipes composes declared instruction and ignore-file operations in order. Applying another recipe does
   not remove text or ignore entries another recipe owns. Applying a recipe's new version removes the sections and
   entries its previous version owned and no longer declares, after whole-batch review, even when their current
   content was edited. It does not automatically delete the paths those entries describe.
4. Stopping use of Cook requires no eject operation: native files, their markers and `cook.lock` can stay as they
   are. Reverting a batch restores its prior values from its reverse patches, subject to actual batch diff review; a
   later edit that overlaps the batch's changes makes the revert write nothing and list the conflict. The
   [operation reference](../features/remove.md) explains these distinct requests.

## Reasons

- **1.** The example's teammate needs the test command, not an account of how it was generated. Direct, concise wording
  serves readers without requiring Cook. Recipe authors remain responsible for the relevance and length of their text.
- **2.** Boundaries let a recipe address one section without rewriting the rest of a shared file. Native comment syntax
  keeps those boundaries compatible with the consuming format, and manual edits remain part of current-content review.
  Naming the recipe and region lets several recipes keep sections in one file and tells a reader which recipe to
  change; the trade is a recipe name visible in native files.
- **3.** Explicit operations keep omission from becoming implicit deletion. Ignore entries affect visibility of paths,
  so their deletion must be reviewed even though it leaves those paths in place. Overlapping declared targets still
  follow order: the last recipe to write a target wins and becomes its owner.
- **4.** Native files already outlive the configuring invocation. An eject classifier would add cleanup semantics that
  the agreed apply and revert contract does not need. A reviewer decides whether a particular deletion or reversal
  leaves instructions that accurately describe the remaining setup.

## Rejected alternatives

### State conventions only in recipe sources

At its best, each choice is described once. It leaves people and agents reading native instructions without the
information they need; recipe sources are not the consuming program's instruction file.

### Classify blocks for automatic retention or withdrawal

At its best, cleanup can keep conventions while removing instructions tied to the configuring tool. It requires
persistent block classes and lifecycle reconciliation. Removal by a recipe's new version and reversal of a batch
instead expose exactly what will change for approval.

### Enforce a fixed instruction-size budget in the engine

At its best, every session gets a bounded amount of generated text. It adds an unrequested enforcement system and
cannot decide which repository instructions are useful. Concision remains authoring guidance.

## Consequences

Costs:

- Recipe authors must keep instruction text concise and accurate for its readers.
- Deleting instructions or ignore entries can leave retained files unexplained or visible to version control; review
  must consider the remaining setup.
- Keeping native files after stopping Cook leaves future edits to people or another configuring invocation.

Must be maintained:

- Clear native instruction text and format-compatible section boundaries.
- Reviewable instruction and ignore-file changes that preserve unrelated content.

## Confirmation

- Read the example's resulting native files without Cook: the repository convention remains understandable.
- Review a Markdown section update: HTML comment markers bound the change and unrelated text remains intact.
- Review a later unrelated apply: the earlier instruction and ignore sections remain in place.
- Review a recipe's new version that stops declaring an edited section and an ignore entry: the actual deletions
  appear for approval, with no automatic deletion of the described path or restoration of earlier text.
