# 0026. Cookbook execution requires trust beyond batch approval

## Objective

**Problem.** Shared setup can come from sources outside the destination repository. Replaced source content or
unreviewed code can act with the caller's credentials, while a preview of file changes shows only part of what
executable code can do. Confusing package integrity, source trust and output approval gives users assurances those
mechanisms do not provide.

**Goal.** Users understand which content they trust and what approving a configuration change covers, while retaining
an inspectable record of the sources actually used.

## Context

- Cook evaluates trusted executable Groovy recipes into ordered file operations. Approval, output boundaries and
  rollback govern declared operations; they do not sandbox recipe code or cover arbitrary side effects
  ([architecture](../architecture.md#evaluation-review-and-writes), FR-1, FR-3, FR-4).
- [Ingredients](../features/glossary.md) model native program files and configuration; cookbook repositories hold and
  ship ingredients and recipes, with builds that can be separate from Cook ([0022](0022-tool-integration.md), FR-2).
- Recipes are supplied as paths inside the destination repository or as coordinates of a cookbook
  release ([0029](0029-cookbook-distribution.md)); a local operand or reference
  outside the repository is refused. Relative references resolve from their original source locations (FR-18, FR-22).
- Engine package verification checks bytes against a committed checksum; it does not alone authenticate the publisher
  or verify cookbook content ([0021](0021-release-trust.md)). A checksum obtained from the same replaceable source as
  an asset cannot independently establish that asset's author or trustworthiness.
- `cook.lock` records each cooked recipe's source and sha256 (FR-14); users manage recipe versions.

## Decision

1. Recipes are trusted executable code, together with any code a recipe loads from other sources, whatever form a
   recipe or ingredient takes. Trust in the source precedes evaluation; approving the resulting batch approves
   declared file changes, not the admission of arbitrary code into a sandbox. Being data-only is not a safety
   guarantee.
2. `cook.lock` records the source and sha256 of each cooked recipe; for coordinates the sha256 covers the
   artifact's sources. It identifies which version produced the current output; it does not authenticate an author
   or undo arbitrary code side effects. Cook keeps no copy of the sources ([0014](0014-directory-layout.md)).
3. Approval of the combined diff before target writes follows [0007](0007-ownership.md).
4. Engine delivery is verified independently ([0017](0017-engine-version.md), [0021](0021-release-trust.md)). Engine
   checksums do not vouch for cookbook authors or move recipe versions.

## Reasons

- **1.** FR-4 explicitly puts recipe code outside batch guarantees. Code can act during evaluation, before a diff
  exists, so output approval cannot establish trust in that code. The trade is reliance on users' source selection and
  code review; the engine promises no protection from a malicious trusted recipe.
- **2.** A source can change after a batch. The sha256 lets a reviewer tell whether the source they read is the one
  that produced the output, and a changed release shows before the diff; the source itself stays with its repository
  or cookbook, which keeps its history. The trade is that a source that disappears cannot be read again from Cook. A
  hash alone is neither authentication nor a sandbox.
- **3.** FR-9 and FR-11 through FR-13 make visible requested changes and explicit approval the write boundary. An
  ownership veto or an import step would add a reconciliation step that the diff makes unnecessary. The trade is that
  edited values can be replaced after review; recovery protects declared writes, not every effect of the supplied code.
- **4.** FR-26 pins the engine package, while FR-2 leaves cookbook distribution separate and users supply recipe
  versions. The sha256 in `cook.lock` identifies which recipe version produced the current ownership (FR-14); it does
  not vouch for that source. Keeping the verification claims separate prevents a trusted engine from implying trusted
  inputs. The trade is that no engine check establishes cookbook authenticity.

## Rejected alternatives

### Commit a reviewed hash for every cookbook source

At its best, committed hashes verified on each load detect changed bytes after selection. It loses because they do
not prove the code is safe or its author authentic, and the architecture does not select them; users manage the
versions they supply.

### Treat the engine checksum or batch approval as cookbook authentication

At its best, one check is convenient to explain and operate. It loses because an engine checksum concerns another
artifact, and a batch diff omits arbitrary recipe side effects. Neither answers the source-trust problem.

### Reject remote or executable sources

At its best, a local, data-only format reduces some exposure. It loses because local code can still be harmful, and
the agreed design permits trusted Groovy recipes and cookbook releases resolved from listed repositories. Introducing a data-only format or sandbox would
change that design.

### Require signatures from every cookbook author

At its best, signatures help authenticate content under a separately designed key-trust policy. It loses because
signatures add key distribution decisions and decisions about whose keys to accept that the architecture does not
make, and authenticated code would still require trust.

## Consequences

Costs:

- Users trust sources before execution; declared-operation review and rollback cannot recover arbitrary code effects.
- Users manage recipe versions independently of engine package pins; an engine upgrade cannot establish input trust.

Must be maintained:

- Accurate guarantees for package integrity, source trust, source identification and declared-operation approval.

## Confirmation

- Review against FR-18 and FR-22: do a local recipe and a recipe cooked by coordinates both use the invocation
  directory as destination, is a local recipe outside the repository refused, and is a URL operand refused?
- Review against FR-14: does `cook.lock` record each cooked recipe's source and sha256?
- Review against FR-4 and FR-26: does any document promise that an engine checksum or approval of the diff stops code
  side effects? None may.
- Review against FR-25: does cooking stay available without `cook.lock`?
