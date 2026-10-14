# 0023. The core separates operation rules from external mechanisms

## Objective

**Problem.** When rules for reviewing and applying configuration are mixed with filesystem access, source loading and
terminal interaction, a mechanism change can alter the operation contract. Maintainers also struggle to reason about
failure recovery without exercising every external dependency.

**Goal.** Maintainers can reason about file-operation rules independently of the mechanisms that read, present and
write their inputs and results.

## Context

- The [architecture](../architecture.md#evaluation-review-and-writes) defines the engine's work: evaluate trusted
  Groovy recipes, combine ordered file operations, show the final diff, obtain approval, save recovery information and
  perform writes (FR-1, FR-7 through FR-13).
- The invocation directory is the destination. Source references resolve from the referencing recipe's original
  location, including its URL for HTTPS sources; snapshots preserve that origin. A local recipe or reference outside
  the repository is refused (FR-21, FR-22).
- Apply evaluates supplied recipes against current content and needs neither prior batch history nor `cook.lock`
  (FR-25). The committed `cook.lock` records each recipe's ownership (FR-14). Reverting a batch applies its reverse
  patches rather than reevaluating a recipe (FR-16).
- The core proposes the batch and writes and records only the changes it receives as accepted; choosing individual
  changes is a separate interface decision (FR-11).
- Ingredients model native program files and configuration in cookbooks ([0022](0022-tool-integration.md)). Fixed
  lifecycle, worker and jail contracts are outside the engine's responsibilities (FR-2, FR-3, FR-6).
- Ports and adapters separates rules, connections to external mechanisms and entry-point wiring. That dependency
  direction is useful without selecting a complete public interface or a connection for every conceivable dependency.

## Decision

1. Keep operation rules and the models they require in the core. Connections implement the external mechanisms those
   rules need; wiring supplies those connections. Dependencies point from connections and wiring toward the core,
   rather than requiring operation rules to import a terminal, HTTP client or filesystem implementation.
2. Shape those boundaries around the agreed file-operation responsibilities: current content, ordered changes, actual
   diff review, whole-batch approval, destination containment, recovery and recorded reversal. Do not retain the former
   three ingredient-filled contracts for version control, workers and jails, or add external command execution to a
   batch. This decision does not enumerate or finalize method signatures, model APIs or a complete port inventory.
3. Keep the core independent of any interface: it receives accepted changes and writes and records only those. Use the
   CLI as the public cooking interface; it accepts the whole batch. Gradle builds and packages Cook and cookbooks; it is
   not a second public cooking interface. The destination is the invocation directory, not a root discovered by walking
   upward to a configuration directory or by consulting version control.
4. Local history under the destination's ignored `.cook/batches/<batchId>/` directory keeps one record per batch: one
   reversible patch per touched file, `cook.lock` included, consumed local and remote recipe/template sources, origins,
   arguments, order and outcome. Save recovery patches before the first target write. On write failure, roll back
   changes already made and retain patches and the failure record. History is not a prerequisite for apply, and
   ownership lives in the committed `cook.lock`; reverting a batch requires its record. Metadata formats and full
   storage schemas remain undecided.
5. Distribution retains platform-specific jpackage packages containing Java and a committed wrapper with an explicit
   engine version and matching checksums. First use downloads and verifies an uncached package, then caches it;
   upgrades explicitly change the pin ([0017](0017-engine-version.md), [0021](0021-release-trust.md)). Cookbook builds
   and recipe-version selection remain separate. Ingredient/recipe APIs, nesting, station semantics and lifecycle
   mechanics remain deferred; issue #9 decides no deletion of dynamic composition or compiled suppliers.

## Reasons

- **1.** Keeping mechanisms behind core-defined boundaries protects the operation rules when an external mechanism
  changes and supports reasoning with substitutes. The trade is indirection and the need to keep substitutes faithful
  to the relevant behavior. A separate interface is justified by an actual boundary, not merely by a future possibility.
- **2.** The architecture defines file changes rather than program execution. Retaining lifecycle contracts would
  encode responsibilities Cook no longer owns. The trade is that future orchestration needs its own design instead of
  inheriting these old contracts. Ingredient knowledge does not require banning all file-format concepts from the core.
- **3.** FR-5 gives users one public cooking interface; FR-21 makes the destination explicit. FR-11 keeps acceptance
  outside the core, so another interface can choose individual changes without altering operation rules, and `cook.lock`
  and the batch record describe only what was written. Upward root discovery could redirect writes away from the
  invocation directory. The trade is that callers choose the destination by their working directory, even when the
  executable or recipe lives elsewhere.
- **4.** FR-13 and FR-23 through FR-25 separate recovery from ownership. Recording consumed sources and origins makes
  a batch inspectable without using history to skip recipe evaluation. The trade is local recovery storage, and losing
  it loses reversal of those batches while apply remains available.
- **5.** FR-26 fixes delivery independently of the internal boundary shape; FR-30 leaves model APIs open. The trade
  is maintaining platform packages and separate cookbook compatibility without treating current composition code or
  an older issue as the final public contract.

## Rejected alternatives

### Leave operation rules mixed with external mechanisms

Direct calls can be shorter and easy to follow in a small operation. As review, source loading and rollback grow,
mechanism changes can reach the rules themselves. Keep separation at those boundaries without wrapping every value
or helper in an interface.

### Retain the lifecycle's fixed contracts

Version control, worker and jail interfaces would give an orchestration engine a uniform integration surface. They
miss this engine's scope and would make configuration changes depend on programs or pinned descriptions they do not
need. Apply requires neither a version control program nor `cook.lock`.

### Choose a supplier or composition API from the existing code

Selecting one implementation would remove ambiguity for authors now. It would also settle the deferred model
questions from implementation evidence alone. Neither compiled suppliers nor dynamic composition is rejected here.

## Consequences

Costs:

- Connections and their wiring add indirection; maintainers must keep their behavior consistent with the operation
  contract, especially destination resolution and write-failure recovery.
- Trusted recipe side effects remain outside the declared-operation boundary; dependency separation is no sandbox.

Must be maintained:

- Dependency direction and the distinction between recipe choices, file-operation rules and external mechanisms.
- Independent destination/source resolution and recovery information without ownership gates.

## Confirmation

Review dependency direction and scenarios against FR-1, FR-5, FR-11, FR-13, FR-14, FR-16 and FR-21 through FR-26. An
HTTPS recipe still targets the invocation directory; its relative sources keep their origin after capture, and a local
recipe outside the repository is refused. Apply works without earlier history or `cook.lock`, while reversal requires
the batch record. A failed batch retains recovery information after rollback. The package pin does not choose recipe
versions or public model APIs. These criteria guide design review and later implementation validation; they introduce no
mandatory commands or implementation claims.
