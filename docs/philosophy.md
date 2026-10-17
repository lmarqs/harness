# Philosophy

## Objective

**Problem.** Teams and developers reuse configuration across repositories, but copying setup by hand makes it hard to
share improvements, review changes, and preserve unrelated configuration. A contributor needs to understand which
part of that problem Cook solves before proposing another responsibility for it.

**Goal.** Readers can judge proposals against Cook's purpose: turn reusable [recipes](features/glossary.md) into
visible, approved changes to native program files, while keeping program-specific setup separate from the engine.

[Cook architecture](architecture.md) states the intended design. This philosophy explains its goals and tenets;
[ADRs](adrs/README.md) record the supporting reasoning. Both follow the architecture. Documentation of the current
implementation belongs in the [root README](../README.md) and [core guide](../core/README.md).

## The problems

Teams share repositories, while each person uses their own editors, agents, loop runners and task trackers. These
programs consume native files, often inside the repository. Reusing their setup raises several practical problems:

- A useful configuration change reaches another repository only when someone copies it, and the copies drift.
- Replacing a shared file can erase unrelated settings or changes someone made by hand.
- Hidden changes make it hard to judge what cooking or discarding setup will do.
- Coupling program-specific configuration to the engine makes each program's evolution an engine concern.

## The goals

- Reuse configuration through recipes and ingredients maintained in cookbook repositories.
- Review the combined effect of the requested changes against current content before approving writes.
- Preserve unrelated content when cooking or discarding recipes.
- Let configured programs consume their native files directly.
- Keep the engine's responsibilities bounded so cookbooks can evolve separately.

## What `the-cook` is

Cook is a recipe driven configuration engine: its CLI evaluates trusted Groovy recipes into an ordered batch of file
operations, shows the resulting diff, obtains approval, records the ownership in `cook.lock`, and
performs the writes, while cookbook repositories carry the ingredients and recipes for program-specific setup. The
[architecture](architecture.md) states the full design, and the
[current implementation](../README.md#current-implementation) describes what runs today.

## Tenets

### Start from the objective

**Problem.** A proposal without a stated purpose is judged against whatever purpose its reader assumes, and can
outlive the reason it was made.

**Tenet.** State the problem and desired outcome before the mechanism. Keep the reasons visible so a contributor can
judge whether a decision still serves its goal. See the [ADR structure](adrs/README.md#the-objective-comes-first).

### Keep the engine focused on file operations

**Problem.** A core that owns a team's development lifecycle must change whenever that way of working changes.

**Tenet.** Cook evaluates recipes and reviews and performs their declared file operations. The agents, loop
runners and CI systems it configures run and control their own workflows. See the
[role of the tool](adrs/0001-role-of-the-tool.md) ADR.

### Trust recipes explicitly

**Problem.** Approval of a diff can be mistaken for a guarantee about everything executable recipe code might do.

**Tenet.** Recipes are trusted Groovy code. Approval, destination boundaries and rollback govern declared batch
operations; they do not sandbox recipe evaluation or guarantee that arbitrary recipe code has no side effects. Choose
recipe sources accordingly. See the [cookbook trust](adrs/0026-cookbook-trust.md) ADR.

### Keep program models in cookbooks

**Problem.** Program-specific setup tied to the engine makes the engine follow each program's configuration changes.

**Tenet.** Ingredients model native program files and configuration, and cookbooks distribute ingredients and recipes
separately from the engine. See the [organization content](adrs/0013-organization-content.md) ADR.

### Review the actual change

**Problem.** Hidden reconciliation or refusals based on manual edits prevent readers from judging the change they
requested against the files they have now.

**Tenet.** Evaluate supplied recipes against current content on every invocation that cooks them and show their combined
effect as one diff, approved as a whole. Ownership, recorded in the committed `cook.lock`, never blocks a change, and
cooking one recipe never removes another recipe's output. The cost is that when a later recipe takes over a target,
the diff is the only notice. See [ordered composition](architecture.md#ordered-composition-and-declared-targets)
(FR-7 to FR-9) and the [ownership](adrs/0007-ownership.md) ADR.

### Make recovery part of writing

**Problem.** A failed write can leave only part of the approved change, and removing a recipe's output can be
confused with restoring the past.

**Tenet.** Roll back a failed batch so the files stay as they were. Discard removes exactly what a recipe owns and
restores nothing; earlier content belongs to the repository's history. See
[batch approval and failure recovery](architecture.md#batch-approval-and-failure-recovery) (FR-13, FR-15, FR-25),
[cooking](features/cook.md) and [discard](features/discard.md).

### Let native configuration stand on its own

**Problem.** A setup tool can become an unnecessary dependency of every program it configures.

**Tenet.** Programs read the native files Cook writes directly, and teammates keep their own editors, agents and ways
of working. See the [dependence on the binary](adrs/0010-dependence-on-the-binary.md) and
[place of the tool](adrs/0028-place-of-the-tool.md) ADRs.
