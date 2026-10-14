# 0015. The binary's core is a fixed software development lifecycle

## Objective

**Problem.** The same steps recur at the same points of every change: when work starts, when it is verified, when it is
complete, when it is delivered and when it lands. Each repository and each actor runs them differently, by hand or
through prose a coding agent follows, and rules written around one kind of program that does the work leave every
other kind outside them. Delivery mistakes leave the clone and are hard to undo, and when unattended work stops, nobody
is told.

**Goal.** Each step runs the same way whoever starts it and whatever program does the work, delivery never publishes
work that is not ready, and a stopped run can reach a person through an event.

## Context

- The same steps recur at the same points of every change: something to do once work starts, once it is verified,
  once it is complete, when it is ready to leave the clone, and once it has landed on the default branch.
- The work between those points is done by people and by workers, the programs that change a repository for someone: a
  coding agent used interactively or in an attempt, a bot, a codemod. Each point
  that needs a program needs something different from it: a working copy, a base and the history of a branch from
  version control; a launch and configuration from a worker; a command from the repository
  ([0022](0022-tool-integration.md), [0023](0023-core-and-contracts.md)).
- Some of a change happens outside the clone and the binary: a team plans in its tracker, and reviews and merges on its
  hosting platform, in CI and among reviewers.
- A change lands on the shared default line of work, which a clone knows only as it last learned it. Learning what is
  shared asks the version control system to reach beyond the clone, often over the network, so a merge made on a
  hosting platform reaches the clone only when something asks ([0023](0023-core-and-contracts.md)).
- What happens at those points differs between repositories and organizations. Delivery follows each team's hosting
  platform and conventions: draft or ready pull requests, a commit that triggers CI, a notification. The binary carries
  no hosting-platform logic and no organization content ([0012](0012-write-boundary.md),
  [0013](0013-organization-content.md)).
- Delivery leaves the clone, so its mistakes are visible to others and hard to undo. Publishing from the default
  branch, publishing uncommitted work and publishing unverified work are never wanted
  ([0011](0011-safety-invariants.md)).
- Steps are started by different actors: a person at the terminal, a coordinating worker following shipped
  documentation, a script a team writes around its loop runner, or the tool itself at a point of its own work, such as
  the end of a verified attempt. A workflow copied by hand drifts into dialects: suppose acme copies one coding-agent
  workflow into `acme/api` and `acme/web`, and a year later delivery in `acme/api` runs the tests and opens a draft pull
  request, while delivery in `acme/web` pushes without running them. A step defined once per actor drifts in the same
  way: the person, the coordinating worker and the end of the attempt each do it their own way
  ([0006](0006-repository-contract.md)).
- A worker in an attempt is the least trusted actor ([0011](0011-safety-invariants.md)).
- Values such as task titles and branch names are written by people and workers. Substituted into command text, they
  are an injection path.
- A step a coding agent carries out by following prose gives a different result on each run.
- Work also stops before it concludes: an unattended run ends with tasks unfinished, a task is skipped after its
  retries, a command run without a terminal refuses because a person must act. Nobody watches an unattended run; the
  person learns of the stop only by looking.
- An unattended run as a whole, with its start, its end and its retries, belongs to the loop runner that drives it, not
  to the binary ([0022](0022-tool-integration.md)). The tool sees one task at a time, through the command a loop runner
  calls for each: the attempt command, or a run of a recipe ([0025](0025-recipes.md)).
- A task tracker marks a task done when it is told the task is complete, whoever tells it.
- The clone's state is readable by every process in it, workers included.

## Decision

1. The binary's core is the development lifecycle: a fixed, ordered sequence of stages that every change goes through,
   plan, start, implement, verify, complete, deliver, review and merge, and landed, with events fired between them. The
   core holds the guards between stages, the proof of verification bound to a change's base and content, its replay
   from the base, the floor process and the attempt contract ([0011](0011-safety-invariants.md)). Nothing in the core is
   about one kind of worker: a program's own safety facts, such as the arguments that bypass a worker's permission
   checks, are data in its ingredient ([0022](0022-tool-integration.md)).
2. At each stage the core does only the following, and reaches every program the stage needs through a contract or a
   declared action ([0023](0023-core-and-contracts.md), [0019](0019-action-kinds.md)):
   - Plan: nothing beyond the task's identifier; people and their tracker plan, and declared actions reach the tracker.
   - Start: creates the working copy and records its base, its actor and its task, through the version control
     contract.
   - Implement: renders the workers' configuration and floor, runs the declared hooks and runs attempts, through the
     worker contract.
   - Verify: runs the lanes the repository declares and writes the proof, bound to the base and the content.
   - Complete: replays the proof from the base, through the version control contract, and fires work completed.
   - Deliver: enforces the delivery guards and fires work ready for delivery, whose declared actions deliver.
   - Review and merge: nothing; the hosting platform, CI and reviewers act.
   - Landed: detects, when a working copy is removed, that its branch's changes are on the shared default line of
     work, as the clone last learned it, through the version control contract, and fires work landed. Removal first
     learns what is shared only when the repository's declared data asks for it, and a failure to learn leaves one
     note and the detection against what the clone knows; no other stage learns what is shared on its own.

   A run of a recipe the repository holds is one pass of one change through these stages, from start to complete
   ([0025](0025-recipes.md)); it adds no stage and changes none. Every capability of the binary serves one of these
   stages or keeps the setup they read consistent, reachable and removable. A capability that does neither does not
   belong in the binary.
3. The events between the stages are a fixed, ordered set: work started, work verified, work completed, work ready for
   delivery, and work landed on the default branch.
4. One out-of-band event, blocked, fires at any stage when work stops unfinished or needs a person: an unattended run
   stops, a task is skipped after its retries, a recipe run stops at a step, or a command run without a terminal refuses
   because a person must act. It carries the stop reason. Its actions never run inside an attempt, and it has no other
   guard.
5. Each event applies to one or more scopes: a single task, an unattended run, or a change. The stages, the events,
   their order and their scopes are part of the binary; repositories cannot add stages or events.
6. The binary decides when an event fires and enforces the guards that apply to it. The ready-for-delivery event fires
   only for concluded work: committed, with a clean tree, on a branch other than the default branch and, when
   verification is enabled, with a valid proof of verification for the work's base ([0011](0011-safety-invariants.md)).
   When verification is enabled, the work-completed event at the scope of a task fires only with a valid proof of
   verification, whoever triggers it.
7. The repository declares the actions for each event ([0019](0019-action-kinds.md)): run in order, optionally limited
   to some of the event's scopes, each with a rule for whether its failure stops the remaining actions. No action runs
   that the repository's files do not declare; a station's actions reach them only as entries a person applies
   ([0013](0013-organization-content.md), [0024](0024-ingredients-and-stations.md)).
8. Every event can be triggered by a person, by a coordinating worker or by a script, or automatically at a trigger
   point of the binary's own work, and every trigger runs the same declared actions under the same guards. Automatic
   triggering applies only to actions that opt in to it. The automatic trigger points are:
   - Creating a working copy fires work started, at the scope of a task when the copy is bound to one and of a change
     otherwise.
   - A verification run fires work verified.
   - A verified attempt in a working copy bound to a task fires work completed and then, when the work is concluded,
     ready for delivery, both at the scope of that task, and fires nothing in a working copy bound to no task.
   - Removing a working copy fires work landed once for each recorded branch whose changes are on the shared default
     line of work.
   - A recipe run fires work started when it creates its working copy and work verified when it verifies, as those
     steps do, and, once its proof is replayed, what a verified attempt fires, at the same scopes, and it fires blocked
     when a step stops it.
   - Delivery across several repositories fires ready for delivery at the scope of a change in each.
   - A command run without a terminal that stops for a person fires blocked, except a recipe run, for which the
     caller's declaration of who drives it takes the place of the terminal ([0025](0025-recipes.md)).

   Nothing in the binary fires an event at the scope of an unattended run; its caller does.
9. Event data reaches actions only through environment variables the binary sets for each run
   ([0019](0019-action-kinds.md)).
10. Actions never run inside an attempt. The binary fires events in its own process after the worker exits, and a
    worker in an attempt is denied triggering events ([0011](0011-safety-invariants.md)).
11. Every run of an event is recorded in the clone's own state: the event, the scope, and for each action its name,
    kind, exit status and duration. The record never holds secrets, request bodies or prompts. A preview shows the
    actions and the environment they would receive without running them.
12. Delivery that needs judgement, such as choosing which commits of an unattended run go together or rewording their
    messages, stays with a person or a coordinating worker guided by shipped documentation, who then triggers the
    ready-for-delivery event.
13. The policy a newly adopted repository starts with asks a person before a worker triggers the ready-for-delivery
    event. It is a seeded entry: the repository owns it and may change it ([0007](0007-ownership.md)).

## Reasons

- **1, 2.** Context shows the same points in every change, reached by different actors and done by different programs.
  With the lifecycle as the core, the stages, guards and proof are the same whoever does the work, and a worker of
  another kind needs an ingredient, not a change to the core. The core is about no kind of worker because a worker's own
  facts are data in its ingredient ([0022](0022-tool-integration.md)), so a bot or a codemod is guarded the way a coding
  agent is. Each stage needs something different from programs (Context); reaching each through a contract or a declared
  action keeps program code out of the core ([0023](0023-core-and-contracts.md)). Doing nothing at plan and at review
  and merge keeps the binary out of judgement and off the hosting platform (Context,
  [0012](0012-write-boundary.md)). Requiring every capability to serve a stage, or the setup the stages read, gives each
  one a reason a reviewer can check and keeps the binary from growing into a worker.
  A recipe run goes through the same stages instead of adding its own, so a kind of change a repository repeats has the
  guards every change has ([0025](0025-recipes.md)). Detecting landed work against what the clone last learned keeps
  removal from reaching beyond the clone unasked (Context); a repository whose work lands elsewhere chooses to learn
  first, and since a failure to learn only delays the event, it costs a note, not the removal. The trade: a step that
  fits no stage stays outside the tool, and landed work merged elsewhere goes unseen until the clone learns it, unless
  the repository asks removal to learn first.
- **3, 5.** The recurring points are common to every change, so one fixed vocabulary serves every repository: actions
  written for one repository carry over to another unchanged, and the shipped documentation describes one lifecycle
  instead of one per repository. A set that repositories could extend would give each repository its own stage and
  event names, which is the dialect problem of workflows copied by hand (Context).
- **4.** A stop is the moment a person is needed, and in an unattended run it is also the moment nobody is watching.
  Modelling every stop as one event lets a repository attach one notification to all of them instead of configuring
  each cause. It is out of band because it is not a step in the order of a change. It needs no guard beyond the
  attempt rule because it publishes no work; the guards of point 6 exist because delivery does.
- **6.** A guard is sound only when the component that checks it is the one that fires the event; if the trigger were
  trusted to check, a person, a worker and the automatic trigger could each skip it differently. The delivery guards
  apply invariant 10 of [0011](0011-safety-invariants.md): the three conditions are never a legitimate choice, so no
  trigger can bypass them. The completion guard applies invariant 4 of [0011](0011-safety-invariants.md) to every
  trigger: a tracker's action on work completed marks the task done (Context), so a person or a script that fires the
  event by hand would otherwise record as done work that no verification passed.
- **7.** What happens at each event differs per repository and organization, and the binary carries neither platform
  logic nor organization content ([0012](0012-write-boundary.md), [0013](0013-organization-content.md)). Declared
  actions put those conventions in committed files that are reviewed like code. Running only declared actions means the
  binary never sends anything out of a clone that nobody declared. The trade is that a repository that declares nothing
  gets no delivery from the binary.
- **8.** One definition run by every trigger removes the per-actor drift described in Context: a person, a coordinating
  worker, a script around a loop runner and the end of a verified attempt all produce the same result. Automatic
  triggering is opt-in per action because an action that runs without anyone choosing it, such as one that publishes
  work, is a decision the repository must make explicitly. Each trigger point is a step the binary performs itself, so
  it fires there with the facts of that step at hand. A recipe run creates its working copy, verifies and replays its
  proof itself, so it fires the same events at the same points, and a step that stops it is one of the stops of point 4.
  A handoff in a run a person drives pauses it for that person, who continues it, so it is no stop and fires nothing;
  the terminal cannot tell such a run apart, because the worker in a person's session runs commands without one
  ([0025](0025-recipes.md)). The run scope has no such point: the run belongs to the loop runner (Context), so its
  caller fires the run's events. An attempt in a working copy bound to no task, such as the one a loop runner works in,
  has no task for the binary to name, so it fires nothing and leaves the events to the caller as well. The trade: a run
  whose caller fires nothing reports no stop of the run as a whole.
- **9.** Task titles and branch names are written by people and workers; passing them as environment values keeps them
  out of the text the shell parses, so a title cannot add a command. How each action kind consumes them is decided in
  [0019](0019-action-kinds.md).
- **10.** Actions run as child processes of the binary, outside a worker's per-command permission checks. Inside an
  attempt, they would let the least trusted actor reach everything the actions reach.
- **11.** The record answers what left the clone, when and with what result, for every trigger. It omits secrets,
  bodies and prompts because the clone's state is readable by every process in it, workers included. The preview lets
  a person or a worker see what an event would do before permitting or triggering it.
- **12.** Grouping and rewording commits is judgement ([0002](0002-division-of-work.md)); the event that follows still
  applies the guards to the result, so judgement never bypasses them.
- **13.** Permitting a worker to trigger delivery permits every action declared for it, and those actions run outside
  the worker's per-command checks. Until a team has seen what its delivery actions do, a person confirms each one. The
  entry is seeded rather than made an invariant ([0011](0011-safety-invariants.md)) because the choice is the
  repository's: a team that trusts its actions may let its coordinating worker deliver unattended.

## Rejected alternatives

### Stages a repository declares

Each repository declares its own stages and the events between them, and the binary runs whatever sequence it
declares. At its best, a team whose process has a step the fixed list lacks, such as a security review before
delivery, models it directly, and the binary imposes no shape on anyone's process. It loses because a guard is
attached to a stage: the delivery guards hold only if delivery is a stage the binary knows, and a declared sequence
could reach publication by a stage that carries none of them. Actions written for one repository's stages would not
carry over to another's, which is the dialect problem of Context, and the shipped documentation could describe no
lifecycle at all.

### Built-in delivery for a hosting platform

The binary pushes and opens pull requests through the most common platform's API. At its best, delivery works with no
configuration for most users. It loses because it binds every release to one platform's API, credentials and
conventions. Organizations with other conventions or another platform still need their own path, and it contradicts
[0012](0012-write-boundary.md) and [0013](0013-organization-content.md).

### Delivery left entirely to workers and skills

Shipped documentation describes how to deliver and a coordinating worker does it. At its best, there is no new
contract and every case gets judgement. It loses because the result differs per run, nothing enforces the guards, a
person without a coding agent has no repeatable path, and nothing runs at a trigger point when no worker is present.

### Extension code at any point of the binary's operation

Extensions hook any point of the binary's operation with their own code. At its best, every extension need is met. It
loses because extension code is engine code in repositories ([0006](0006-repository-contract.md)) that may need a
runtime. An open set of hook points has no guard that covers all of them, and the
tool grows into a worker. An ingredient is data that fills fixed contracts
([0022](0022-tool-integration.md), [0023](0023-core-and-contracts.md)), a station is starting content applied once
([0024](0024-ingredients-and-stations.md)), and a recipe's steps are a closed set of kinds that run inside the stages
([0025](0025-recipes.md)), so none adds a hook point.

### Stops reported only in the command's output

At its best, there is no extra event: the command that stops says why. It loses because an unattended run has no one
reading its output, which is exactly when a stop needs to reach a person.

## Consequences

Costs:

- Permitting a worker to trigger an event permits every action declared for that event. Actions run outside the
  worker's per-command permission checks, so a command the policy denies to the worker still runs when an action
  contains it. The seeded ask entry of point 13 covers delivery; other events need their own entries.
- Actions inherit the environment, including credentials, so an action runs with the credentials of whoever triggers
  it.
- A step that fits no stage stays outside the tool, and a team's own step, such as a security review, runs as a
  declared action on the nearest event or outside the binary.
- Delivery at the scope of an unattended run, triggered by the run's caller, delivers the whole run as one unit.
- A stop of an unattended run as a whole reaches a person only when the run's caller fires the blocked event.
- A repository that declares no actions gets no delivery from the binary, and its stops reach nobody beyond the
  command's output.
- Work merged on a hosting platform is detected as landed only once the clone has learned it, unless the repository
  asks removal to learn what is shared first.

Must be maintained:

- The stages, the event set, its order, each event's trigger points, scopes and guards, the environment passed to
  actions, and the record format, as part of the public contract ([0006](0006-repository-contract.md)).
- For each capability of the binary, the stage it serves or the setup it keeps.

## Confirmation

- Review rejects a capability of the binary that names neither a stage it serves nor the setup it keeps.
- For each event, a fixture asserts that the trigger point runs only the actions that opt in to automatic triggering,
  and that a person's or a worker's trigger runs all of them.
- Fixtures on the default branch, with uncommitted changes and, with verification enabled, with an invalid proof refuse
  the ready-for-delivery event and run no action.
- A fixture whose task title and branch name contain shell metacharacters passes them to actions unchanged as
  environment values, and nothing in them executes.
- A fixture in which a worker in an attempt tries to trigger an event is denied, and the event fires only after the
  worker exits.
- Fixtures fire the blocked event with its reason when a command without a terminal refuses for a person to act, and
  when a caller triggers it at the scope of a task or of a run; none of its actions runs while an attempt runs.
- With verification enabled, a fixture that triggers the work-completed event at the scope of a task without a valid
  proof is refused and runs no action, whether a person, a worker or a script triggers it.
- No command of the binary, run through a whole fixture lifecycle, fires an event at the scope of an unattended run.
- Every event run in the fixture suite appears in the record with its exit status; a fixture whose environment holds a
  secret asserts that the record does not contain it.
- A fixture recipe run fires work started, work verified and, in a working copy bound to a task, work completed at the
  same points and scopes as the steps it performs do elsewhere, and fires the blocked event naming the step that stops
  it.
- With no actions declared, every event runs nothing and succeeds.
- A fixture whose shared default line of work holds a branch's changes that the clone has not learned fires no work
  landed when its working copy is removed; with the repository asking removal to learn first, it fires work landed;
  and when that learning fails, removal reports one note, detects against what the clone knows and still removes the
  working copy.
