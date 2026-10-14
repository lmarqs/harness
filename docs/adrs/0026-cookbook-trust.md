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
- Ingredients model native program files and configuration; cookbook repositories hold and ship ingredients and recipes,
  with builds that can be separate from Cook ([0022](0022-tool-integration.md), FR-2).
- Recipes are supplied as paths inside the destination repository or as `https://` URLs; a local operand or reference
  outside the repository is refused. Relative references resolve from their original source locations, not their
  snapshots (FR-18, FR-22).
- Engine package verification checks bytes against a committed checksum; it does not alone authenticate the publisher
  or verify cookbook content ([0021](0021-release-trust.md)). A checksum obtained from the same replaceable source as
  an asset cannot independently establish that asset's author or trustworthiness.
- FR-23 and FR-24 record evaluated recipes and all consumed recipe/template sources, local as well as downloaded,
  together with origins, arguments, execution order and outcome, in each batch record. `cook.lock` records each
  applied recipe's source and sha256 (FR-14); users manage recipe versions.

## Decision

1. Treat recipes as trusted executable code, including supplied code they execute. Trust in the source must precede
   evaluation; approving the resulting batch is approval of declared file changes, not admission of arbitrary code
   into a sandbox. No assumption that an ingredient or recipe is data-only provides an execution safety guarantee.
2. Preserve the sources actually evaluated and all consumed recipe/template sources in the destination-local ignored
   batch record, with their origins, arguments, order and outcome. Preserve original reference resolution when
   capturing them. Keep one record per batch and one reversible patch per touched file, saved before the first
   target write; on write failure, roll back batch changes already made and retain patches and the failure record.
   Snapshots support inspection and recovery; they do not authenticate an author or undo arbitrary code side effects.
3. Review the actual combined diff against current content and obtain whole-batch approval before target writes.
   Preapproval displays the diff and proceeds without prompting; noninteractive invocation alone does not authorize
   writes. Apply requires neither prior history nor `cook.lock`, and existing content follows the same flow.
   Reverting a batch requires its record.
4. Verify engine delivery independently: the committed wrapper pins an explicit engine version and matching checksums
   for platform-specific jpackage packages containing Java. It downloads and verifies an uncached package on first use,
   caches it locally and upgrades only through explicit pin changes ([0017](0017-engine-version.md)). Those checksums
   do not vouch for cookbook authors or move recipe versions.
5. Preserve ingredients as program data models. Trust applies to executable content regardless of the mechanism the
   separate model discussion eventually selects.

## Reasons

- **1.** FR-4 explicitly puts recipe code outside batch guarantees. Code can act during evaluation, before a diff
  exists, so output approval cannot establish trust in that code. The trade is reliance on users' source selection and
  code review; the engine promises no protection from a malicious trusted recipe.
- **2.** A source can change after a batch, and local content matters as much as downloaded content. Capturing the bytes
  used and their origins makes the declared operation inspectable later and preserves reference meaning. The trade is
  local storage, which can contain sensitive source material; losing it loses local recovery information. Snapshotting
  alone is neither authentication nor a sandbox.
- **3.** FR-9 and FR-11 through FR-13 make visible requested changes and explicit approval the write boundary. An
  ownership veto or adoption gate would add a reconciliation step that the diff makes unnecessary. The trade is that
  edited values can be replaced after review; recovery protects declared writes, not every effect of the supplied code.
- **4.** FR-26 pins the engine package, while FR-2 leaves cookbook distribution separate and users supply recipe
  versions. The sha256 in `cook.lock` identifies which recipe version produced the current ownership (FR-14); it does
  not vouch for that source. Keeping the verification claims separate prevents a trusted engine from implying trusted
  inputs. The trade is that no engine check establishes cookbook authenticity.
- **5.** FR-30 reserves the model decisions. A data-only trust argument would silently rule out executable suppliers
  before that discussion. The trade is an unresolved authoring contract; the established trust boundary still applies.

## Rejected alternatives

### Retain mandatory reviewed hashes for every cookbook unit

Committed hashes and verification on each load can detect changed bytes after selection. They do not prove the code
is safe or its author authentic, and the former protocol assumes finalized ingredient, station and recipe admission
models. It is not selected by the architecture; users still manage the versions they supply.

### Treat the engine checksum or batch approval as cookbook authentication

One check would be convenient to explain and operate. An engine checksum concerns another artifact, and a batch diff
omits arbitrary recipe side effects. Neither answers the source-trust problem.

### Reject remote or executable sources

A local, data-only format could reduce some exposure. Local code can still be harmful, and the agreed design permits
trusted Groovy recipes and HTTPS operands. Introducing a data-only format or sandbox would change that design.

### Require signatures from every cookbook author

Signatures could help authenticate content under a separately designed key-trust policy. They would add key distribution
and admission decisions that the architecture does not make, and authenticated code would still require trust. This
record neither selects nor rules out signing for later work.

## Consequences

Costs:

- Users trust sources before execution; declared-operation review and rollback cannot recover arbitrary code effects.
- Captured sources and recovery patches require local storage and may contain sensitive material.
- Users manage recipe versions independently of engine package pins; an engine upgrade cannot establish input trust.

Must be maintained:

- Accurate guarantees for package integrity, source trust, source capture and declared-operation approval.
- Complete consumed recipe/template capture with original origins, separate from `cook.lock` and public model APIs.

## Confirmation

Review against FR-4, FR-9, FR-11 through FR-14, FR-18 and FR-22 through FR-26. A local recipe and an HTTPS recipe both
use the invocation directory as destination and retain their original source bases in history; a local recipe outside
the repository is refused. The record contains what was consumed, not merely a URL to mutable content. Neither an engine
checksum nor approval of the diff promises to stop code side effects. Losing history leaves apply available but prevents
reverting a batch whose record is gone.
