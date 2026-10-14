# Philosophy

## Objective

**Problem.** Teams and developers reuse configuration across repositories, but copying setup by hand makes it hard to
share improvements, review changes, and preserve unrelated configuration. A contributor needs to understand which
part of that problem Cook solves before proposing another responsibility for it.

**Goal.** Readers can judge proposals against Cook's purpose: turn reusable recipes into visible, approved changes to
native program files, while keeping program-specific setup separate from the engine.

[Cook architecture](architecture.md) states the intended design. This philosophy explains its goals and tenets;
[ADRs](adrs/) record the supporting reasoning. Both follow the architecture. Documentation of the current
implementation belongs in the [root README](../README.md) and [core guide](../core/README.md).

## The problems

Teams share repositories, while each person uses their own editors, coding agents, loop runners and trackers. These
programs consume native files, often inside the repository. Reusing their setup raises several practical problems:

- A useful configuration change reaches another repository only when someone copies it, and the copies drift.
- Replacing a shared file can erase unrelated settings or changes someone made by hand.
- Hidden changes make it hard to judge what applying or withdrawing setup will actually do.
- Coupling program-specific configuration to the engine makes each program's evolution an engine concern.

Personal setup portability and traces left by agent sessions are related problems. They motivate future kit and
session work.

## The goals

- Reuse configuration through recipes and ingredients maintained in cookbook repositories.
- Review the combined effect of the requested changes against current content before approving writes.
- Preserve unrelated content when applying recipes or reverting batches.
- Let configured programs consume their native files directly.
- Keep the engine's responsibilities bounded so cookbooks can evolve separately.

## What `the-cook` is

This section describes the intended design in the [architecture](architecture.md); the
[current implementation](../README.md#current-implementation) describes what runs today.

Cook is a recipe driven configuration engine. Its CLI evaluates trusted executable Groovy recipes into an ordered
batch of file operations, shows the resulting terminal diff, obtains approval, records the batch and the ownership in
`cook.lock`, and performs the writes. Batches can include directory and symlink operations where applicable;
executing external commands is not a batch action.

Ingredients are program data models: they describe native files and configuration, such as Git's ignore file or
Codex's configuration and skills. Cookbook repositories hold and ship ingredients and recipes, with builds that can
be separate from Cook's build. The engine handles evaluation, review and file operations; cookbooks carry the
program-specific setup.

Cook uses JVM-based distribution with `jpackage` and bundled Java. The delivery design includes a committed `./cook`
wrapper that pins a version and checksum, downloads and caches that package on first use, and upgrades through explicit
changes. See [installation](features/installation.md) and [the wrapper](features/wrapper.md) for this design and the
current local build entry path. Gradle builds and packages Cook and cookbooks; the CLI is the public cooking interface.

Personal setup backup (**kit**) remains future work in [issue #5](https://github.com/lmarqs/the-cook/issues/5).
Agent-session orchestration (**cooking**) remains future work in
[issue #6](https://github.com/lmarqs/the-cook/issues/6).

## Tenets

### Start from the objective

**Problem.** A proposal without a stated purpose is judged against whatever purpose its reader assumes, and can
outlive the reason it was made.

**Tenet.** State the problem and desired outcome before the mechanism. Keep the reasons visible so a contributor can
judge whether a decision still serves its goal. See the [ADR structure](adrs/README.md#the-objective-comes-first).

### Keep the engine focused on file operations

**Problem.** A core that owns a team's development lifecycle must change whenever that way of working changes.

**Tenet.** Cook evaluates recipes and reviews and performs their declared file operations. The coding agents, loop
runners and CI systems it configures run and control their own workflows.

### Trust recipes explicitly

**Problem.** Approval of a diff can be mistaken for a guarantee about everything executable recipe code might do.

**Tenet.** Recipes are trusted Groovy code. Approval, destination boundaries and rollback govern declared batch
operations; they do not sandbox recipe evaluation or guarantee that arbitrary recipe code has no side effects. Choose
recipe sources accordingly.

### Keep program models in cookbooks

**Problem.** Program-specific setup tied to the engine makes the engine follow each program's configuration changes.

**Tenet.** Ingredients model native program files and configuration, and cookbooks distribute ingredients and recipes
separately from the engine.

### Review the actual change

**Problem.** Hidden reconciliation or refusals based on manual edits prevent readers from judging the change they
requested against the files they have now.

**Tenet.** Evaluate supplied recipes against current content on every apply invocation. Compose operations in
declared order; when two recipes write the same target, the last one wins and becomes its owner, and one combined diff
shows the result. The CLI accepts the whole batch. Ownership, recorded in the committed `cook.lock`, never blocks a
change: existing or edited content uses the same flow as any other change. Applying one recipe never removes another
recipe's output.

### Make recovery part of writing

**Problem.** A failed write can leave only part of the approved change, and later withdrawal can be confused with
restoring the past.

**Tenet.** Save the batch record before the first target write and roll back completed writes if the batch fails,
retaining the record and failure. Revert reverses only one recorded batch's changes against current content: later
non-overlapping edits survive, and an overlapping edit makes revert write nothing and list the conflicts. Apply needs
neither batch history nor `cook.lock`; reverting a batch needs its record. Discarding a recipe's whole contribution is
deferred. See [apply](features/render.md) and [revert](features/remove.md).

### Let native configuration stand on its own

**Problem.** A setup tool can become an unnecessary dependency of every program it configures.

**Tenet.** Programs read the native files Cook writes directly, and teammates keep their own editors, agents and ways
of working.
