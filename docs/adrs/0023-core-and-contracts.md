# 0023. The core separates operation rules from external mechanisms

## Objective

**Problem.** When rules for reviewing and applying configuration are mixed with filesystem access, source loading and
terminal interaction, a mechanism change can alter the operation contract. Maintainers also struggle to reason about
failure recovery without exercising every external dependency.

**Goal.** Maintainers can reason about file-operation rules independently of the mechanisms that read, present and
write their inputs and results.

## Context

- The [architecture](../architecture.md#evaluation-review-and-writes) defines the engine's work: evaluate trusted
  Groovy recipes, combine ordered file operations, show the final diff, obtain approval, save the
  [batch record](../features/glossary.md) and perform writes (FR-1, FR-7 through FR-13).
- The invocation directory is the destination. Source references resolve from the referencing recipe's original
  location, including its URL for HTTPS sources; the batch record preserves that origin. A local recipe or reference
  outside the repository is refused (FR-21, FR-22).
- Apply evaluates supplied recipes against current content and needs neither earlier batch records nor `cook.lock`
  (FR-25). The committed `cook.lock` records each recipe's ownership (FR-14). Reverting a batch applies its reverse
  patches rather than reevaluating a recipe (FR-16).
- The core proposes the batch and writes and records only the changes it receives as accepted; choosing individual
  changes is a separate interface decision (FR-11).
- Ingredients model native program files and configuration in cookbooks ([0022](0022-tool-integration.md)).
- In the `core` and `cli` builds of this repository, `cli/build.gradle` depends on the core artifact
  `dev.thecook:the-cook-core`, and `core/build.gradle` declares no dependency on the CLI.

## Decision

1. Operation rules and the models they require live in the core. Connections (the adapters for the core's ports)
   implement the external mechanisms those rules need; wiring (the entry point) supplies those connections.
   Dependencies point from connections and wiring toward the core.
2. Those boundaries follow the agreed file-operation responsibilities: current content, ordered changes, actual diff
   review, whole-batch approval, destination containment, recovery and recorded reversal. The core defines no
   contracts for version control or for running agents, and adds no external command execution to a batch. This
   decision does not enumerate or finalize method signatures, model APIs or a complete port inventory.
3. The core is independent of any interface and writes and records only the changes it receives as accepted
   ([0007](0007-ownership.md)). The destination is the invocation directory ([0012](0012-write-boundary.md)), not a
   root discovered by walking upward to a configuration directory or by consulting version control.
4. The batch record follows [0014](0014-directory-layout.md).
5. Distribution follows [0017](0017-engine-version.md) and [0021](0021-release-trust.md).

## Reasons

- **1.** Keeping mechanisms behind core-defined boundaries protects the operation rules when an external mechanism
  changes and supports reasoning with substitutes. The trade is indirection and the need to keep substitutes faithful
  to the relevant behavior. A separate interface is justified by an actual boundary, not merely by a future
  possibility.
- **2.** The architecture defines file changes rather than program execution. Contracts for running programs would
  encode responsibilities outside Cook's scope. Ingredient knowledge does not require banning all file-format concepts
  from the core.
- **3.** FR-11 keeps acceptance outside the core, so another interface can choose individual changes without altering
  operation rules, and `cook.lock` and the batch record describe only what was written. Upward root discovery could
  redirect writes away from the invocation directory. The trade is that callers choose the destination by their
  working directory, even when the executable or recipe lives elsewhere.
- **4, 5.** The batch record and distribution are fixed independently of the internal boundary shape; the linked ADRs
  give their reasons.

## Rejected alternatives

### Leave operation rules mixed with external mechanisms

At its best, direct calls are shorter and easy to follow in a small operation. It loses because, as review, source
loading and rollback grow, mechanism changes can reach the rules themselves. Separation applies at those boundaries
without wrapping every value or helper in an interface.

### Fixed version control and agent contracts

At its best, version control and agent interfaces give a uniform surface for integrating with the programs a team
runs. It loses because those contracts miss this engine's scope and would make configuration changes depend on
programs they do not need. Apply requires neither a version control program nor `cook.lock`.

## Consequences

Costs:

- Connections and their wiring add indirection; maintainers must keep their behavior consistent with the operation
  contract, especially destination resolution and write-failure recovery.
- Trusted recipe side effects remain outside the declared-operation boundary; dependency separation is no sandbox.

Must be maintained:

- Dependency direction and the distinction between recipe choices, file-operation rules and external mechanisms.
- Independent destination and source resolution and the batch record.

## Confirmation

- Review `core/build.gradle` and `cli/build.gradle`: does the core build still declare no dependency on the CLI, and
  does a change to a core class import nothing from the CLI or from a connection?
- Review against FR-21 and FR-22: does an HTTPS recipe still target the invocation directory, keep the origin of its
  relative sources in the batch record, and is a local recipe outside the repository refused?
- Review against FR-13, FR-25 and FR-16: does apply work without earlier batch records or `cook.lock`, does reversal
  require the batch record, and does a failed batch keep its batch record after rollback?
