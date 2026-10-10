# 0015. The development lifecycle is a fixed set of events the binary fires

## Objective

**Problem.** The same steps recur at the same points of every change: when work starts, when it is verified, when it is
complete, when it is delivered and when it lands. Each repository and each actor runs them differently, by hand or
through prose an agent follows. Delivery mistakes leave the clone and are hard to undo, and when unattended work stops,
nobody is told.

**Goal.** Each step runs the same way whoever starts it, delivery never publishes work that is not ready, and a stopped
run reaches a person.

## Context

- The same steps recur at the same points of every change: something to do once work starts, once it is verified,
  once it is complete, when it is ready to leave the clone, and once it has landed on the default branch.
- What happens at those points differs between repositories and organizations. Delivery follows each team's hosting
  platform and conventions: draft or ready pull requests, a commit that triggers CI, a notification. The binary carries
  no hosting-platform logic and no organization content ([0012](0012-write-boundary.md),
  [0013](0013-organization-content.md)).
- Delivery leaves the clone, so its mistakes are visible to others and hard to undo. Publishing from the default
  branch, publishing uncommitted work and publishing unverified work are never wanted
  ([0011](0011-safety-invariants.md)).
- Steps are started by different actors: a person at the terminal, a coordinating agent following shipped
  documentation, or the tool itself at the end of an unattended run. Three hand-ported copies of one agent workflow
  exist, and each runs the same steps differently; a step defined once per actor drifts in the same way
  ([0006](0006-repository-contract.md)).
- Unattended agents are the least trusted actor ([0011](0011-safety-invariants.md)).
- Values such as task titles and branch names are written by people and agents. Substituted into command text, they
  are an injection path.
- A step an agent carries out by following prose gives a different result on each run
  ([0002](0002-division-of-work.md)).
- Work also stops before it concludes: an unattended run ends with tasks unfinished, a task is skipped after its
  retries, a command run without a terminal refuses because a person must act. Nobody watches an unattended run; the
  person learns of the stop only by looking.
- The clone's state is readable by every process in it, agents included.

## Decision

1. The development lifecycle is a fixed, ordered set of events: work started, work verified, work completed, work
   ready for delivery, and work landed on the default branch.
2. One out-of-band event, blocked, fires at any stage when work stops unfinished or needs a person: an unattended run
   stops, a task is skipped after its retries, or a command run without a terminal refuses because a person must act.
   It carries the stop reason. Its actions never run inside an unattended agent, and it has no other guard.
3. Each event applies to one or more scopes: a single task, an unattended run, or a change. The events, their order
   and their scopes are part of the binary; repositories cannot add events.
4. The binary decides when an event fires and enforces the guards that apply to it. The ready-for-delivery event fires
   only for concluded work: committed, with a clean tree, on a branch other than the default branch and, when
   verification is enabled, with a valid proof of verification for the work's base ([0011](0011-safety-invariants.md)).
5. The repository declares the actions for each event ([0019](0019-action-kinds.md)): run in order, optionally limited
   to some of the event's scopes, each with a rule for whether its failure stops the remaining actions. The binary ships
   no actions.
6. Every event can be triggered by a person, by a coordinating agent, or automatically at its trigger point, and every
   trigger runs the same declared actions under the same guards. Automatic triggering applies only to actions that opt
   in to it.
7. Event data reaches actions only through environment variables the binary sets for each run
   ([0019](0019-action-kinds.md)).
8. Actions never run inside an unattended agent. The binary fires events in its own process after the agent exits, and
   unattended agents are denied triggering events ([0011](0011-safety-invariants.md)).
9. Every run of an event is recorded in the clone's own state: the event, the scope, and for each action its name,
   kind, exit status and duration. The record never holds secrets, request bodies or prompts. A preview shows the
   actions and the environment they would receive without running them.
10. Delivery that needs judgement, such as choosing which commits of an unattended run go together or rewording their
    messages, stays with an agent guided by shipped documentation ([0002](0002-division-of-work.md)); the agent then
    triggers the ready-for-delivery event.
11. The policy a newly adopted repository starts with asks a person before an agent triggers the ready-for-delivery
    event. It is a seeded entry: the repository owns it and may change it ([0007](0007-ownership.md)).

## Reasons

- **1, 3.** The recurring points are common to every change, so one fixed vocabulary serves every repository: actions
  written for one repository carry over to another unchanged, and the shipped documentation describes one lifecycle
  instead of one per repository. A set that repositories could extend would give each repository its own event names,
  which is the dialect problem the hand-ported copies show. The trade is that a step that fits no event stays outside
  the tool.
- **2.** A stop is the moment a person is needed, and in an unattended run it is also the moment nobody is watching.
  Modelling every stop as one event lets a repository attach one notification to all of them instead of configuring
  each cause. It is out of band because it is not a step in the order of a change. It needs no guard beyond the
  unattended-agent rule because it publishes no work; the guards of point 4 exist because delivery does.
- **4.** A guard is sound only when the component that checks it is the one that fires the event; if the trigger were
  trusted to check, a person, an agent and the automatic trigger could each skip it differently. The delivery guards
  apply invariant 11 of [0011](0011-safety-invariants.md): the three conditions are never a legitimate choice, so no
  trigger can bypass them.
- **5.** What happens at each event differs per repository and organization, and the binary carries neither
  platform logic nor organization content ([0012](0012-write-boundary.md), [0013](0013-organization-content.md)).
  Declared actions put those conventions in committed files that are reviewed like code. Shipping no actions means the
  binary never sends anything out of a clone that nobody declared. The trade is that a repository that declares
  nothing gets no delivery from the binary.
- **6.** One definition run by every trigger removes the per-actor drift seen in the hand-ported copies: a person, a
  coordinating agent and the end of an unattended run all produce the same result. Automatic triggering is opt-in per
  action because an action that runs without anyone choosing it, such as one that publishes work, is a decision the
  repository must make explicitly.
- **7.** Task titles and branch names are written by people and agents; passing them as environment values keeps them
  out of the text the shell parses, so a title cannot add a command. How each action kind consumes them is decided in
  [0019](0019-action-kinds.md).
- **8.** Actions run as child processes of the binary, outside an agent's per-command permission checks. Inside an
  unattended agent, they would let the least trusted actor reach everything the actions reach.
- **9.** The record answers what left the clone, when and with what result, for every trigger. It omits secrets,
  bodies and prompts because the clone's state is readable by every process in it, agents included. The preview lets
  a person or an agent see what an event would do before permitting or triggering it.
- **10.** Grouping and rewording commits is judgement ([0002](0002-division-of-work.md)); the event that follows still
  applies the guards to the agent's result, so judgement never bypasses them.
- **11.** Permitting an agent to trigger delivery permits every action declared for it, and those actions run outside
  the agent's per-command checks. Until a team has seen what its delivery actions do, a person confirms each one. The
  choice is the repository's and not an invariant, because a team that trusts its actions may let its coordinating agent
  deliver unattended. The entry is therefore seeded instead of built in ([0011](0011-safety-invariants.md)).

## Rejected alternatives

### Built-in delivery for a hosting platform

The binary pushes and opens pull requests through the most common platform's API. At its best, delivery works with no
configuration for most users. It loses because it binds every release to one platform's API, credentials and
conventions. Organizations with other conventions or another platform still need their own path, and it contradicts
[0012](0012-write-boundary.md) and [0013](0013-organization-content.md).

### Delivery left entirely to agents and skills

Shipped documentation describes how to deliver and an agent does it. At its best, there is no new contract and every
case gets judgement. It loses because the result differs per run, nothing enforces the guards, a person without an
agent has no repeatable path, and nothing runs at a trigger point when no agent is present.

### A general plugin system

Plugins hook any point of the binary's operation with their own code. At its best, every extension need is met. It loses
because plugin code is engine code in repositories ([0006](0006-repository-contract.md)) that may need a runtime
([0004](0004-runtime-dependencies.md)). An open set of hook points has no guard that covers all of them, and the tool
grows into a harness ([0001](0001-role-of-the-tool.md)).

### Stops reported only in the command's output

At its best, there is no extra event: the command that stops says why. It loses because an unattended run has no one
reading its output, which is exactly when a stop needs to reach a person.

## Consequences

Costs:

- Permitting an agent to trigger an event permits every action declared for that event. Actions run outside the
  agent's per-command permission checks, so a command the policy denies to the agent still runs when an action
  contains it. The seeded ask entry of point 11 covers delivery; other events need their own entries.
- Actions inherit the environment, including credentials, so an action runs with the credentials of whoever triggers
  it.
- A step that fits no event stays outside the tool.
- Automatic delivery at the scope of an unattended run delivers the whole run as one unit.
- A repository that declares no actions gets no delivery from the binary, and its stops reach nobody beyond the
  command's output.

Must be maintained:

- The event set, its order, each event's trigger points, scopes and guards, the environment passed to actions, and the
  record format, as part of the public contract ([0006](0006-repository-contract.md)).

## Confirmation

- For each event, a fixture asserts that the trigger point runs only the actions that opt in to automatic triggering,
  and that a person's or an agent's trigger runs all of them.
- Fixtures on the default branch, with uncommitted changes and, with verification enabled, with an invalid proof refuse
  the ready-for-delivery event and run no action.
- A fixture whose task title and branch name contain shell metacharacters passes them to actions unchanged as
  environment values, and nothing in them executes.
- A fixture in which an unattended agent tries to trigger an event is denied, and the event fires only after the agent
  exits.
- Fixtures fire the blocked event with its reason when an unattended run stops unfinished (run scope), when a task is
  skipped after its retries (task scope) and when a command without a terminal refuses for a person to act; none of
  its actions runs while an unattended agent runs.
- Every event run in the fixture suite appears in the record with its exit status; a fixture whose environment holds a
  secret asserts that the record does not contain it.
- With no actions declared, every event runs nothing and succeeds.
