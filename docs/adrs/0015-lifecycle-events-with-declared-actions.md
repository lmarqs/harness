# 0015. The development lifecycle is a fixed set of events; the binary fires them and enforces guards, and repositories and presets declare the actions

- Status: Accepted
- Date: 2026-10-09

## Context

- The same steps recur at the same points of every change: something to do once work starts, once it is verified,
  once it is complete, when it is ready to leave the clone, and once it has landed on the default branch.
- What happens at those points differs between repositories and organizations. Delivery in particular follows each
  team's hosting platform and conventions: draft or ready pull requests, a commit that triggers CI, a notification.
  The binary carries none of this ([0012](0012-coexistence-with-team-record-and-third-party-state.md),
  [0013](0013-organization-neutrality-and-presets.md)).
- Delivery leaves the clone, so its mistakes are visible to others and hard to undo. Publishing from the default
  branch, publishing uncommitted work and publishing unverified work are never wanted
  ([0011](0011-built-in-safety-invariants.md)).
- Steps are started by different actors: a person at the terminal, a coordinating agent following shipped
  documentation, or the tool itself at the end of an unattended run. A step defined once per actor drifts, which is
  the copying problem again ([0006](0006-repository-state-is-declarative-data.md)).
- Unattended agents are the least trusted actor ([0011](0011-built-in-safety-invariants.md)).
- Values such as task titles and branch names are written by people and agents. Substituting them into command
  text is an injection path.
- A step an agent carries out by following prose gives a different result on each run
  ([0002](0002-deterministic-work-in-the-binary.md)).

## Decision

- The development lifecycle is modelled as a fixed, ordered set of events: work started, work verified, work
  completed, work ready for delivery, and work landed on the default branch. Each event applies to one or more
  scopes: a single task, an unattended run, or a change. The set and its order are part of the binary; repositories
  and presets cannot add events.
- The binary decides when an event fires and enforces the guards that apply to it. The ready-for-delivery event
  fires only for concluded work: committed, with a clean tree, on a branch other than the default branch and, when
  verification is enabled, with a valid proof of verification for the work's base
  ([0011](0011-built-in-safety-invariants.md)).
- The repository, and the presets it extends, declare the actions: shell commands run in order for an event,
  optionally limited to some of its scopes, each with a rule for whether a failure stops the remaining actions. The
  binary ships no actions.
- Each action can be triggered in three ways, and every way runs the same declared actions: by a person, by a
  coordinating agent, or automatically at the event's trigger point. Automatic triggering applies only to actions
  that opt in to it.
- Values reach actions only through environment variables the binary sets. The binary never substitutes a value
  into an action's command text.
- Actions never run inside an unattended worker. The binary fires events in its own process after the worker exits,
  and unattended workers are denied triggering events ([0011](0011-built-in-safety-invariants.md)).
- Every run of an event is recorded in the clone's own state: the event, the scope, the actions, their exit status
  and duration. A preview shows the actions and their environment without running them.
- Delivery that needs judgement, such as choosing which commits of an unattended run go together or rewording
  their messages, stays with an agent guided by shipped documentation; the agent then triggers the delivery event.

## Alternatives considered

### Built-in delivery for a hosting platform

The binary pushes and opens pull requests through the most common platform's API. At its best, delivery works with
no configuration for most users. Not chosen: it binds every release to one platform's API, credentials and
conventions; organizations with other conventions or another platform still need their own path; and it
contradicts [0012](0012-coexistence-with-team-record-and-third-party-state.md) and
[0013](0013-organization-neutrality-and-presets.md).

### Leave delivery entirely to agents and skills

Shipped documentation describes how to deliver and an agent does it. At its best, there is no new contract and
every case gets judgement. Not chosen: the result differs per run, nothing enforces the guards, a person without an
agent has no repeatable path, and nothing can run at a trigger point without an agent present.

### A general plugin system

Plugins hook any point of the binary's operation with their own code. At its best, every extension need is met. Not
chosen: plugin code is engine code in repositories ([0006](0006-repository-state-is-declarative-data.md)) and may
need a runtime ([0004](0004-zero-runtime-dependencies.md)); an open set of hook points has no guard that covers all
of them; and the tool's surface grows into a second harness ([0001](0001-meta-harness-never-calls-a-model.md)).

## Consequences

Positive:

- Delivery conventions live in committed configuration that is reviewed like code, in the repository or in the
  organization's preset.
- A person, a coordinating agent and the automatic trigger run the same actions, under the same guards.
- The guards hold whoever triggers the event, and every run leaves a record.

Negative:

- Permitting an agent to trigger an event permits every action declared for that event. Actions run as child
  processes of the binary, outside the agent's per-command permission checks, so a command the policy denies to
  the agent still runs when an action contains it. A newly adopted repository's policy asks a person before an agent
  triggers delivery; the repository may move that entry.
- Actions inherit the environment, including credentials. An action from a preset runs with the person's
  credentials.
- A step that fits no event stays outside the tool.
- Automatic delivery at the scope of an unattended run delivers the whole run as one unit.
- A repository that declares no actions gets no delivery from the binary.

Must be maintained:

- The event set, its order, each event's trigger points, scopes and guards, and the environment variables passed to
  actions, as part of the public contract ([0006](0006-repository-state-is-declarative-data.md)).

## Confirmation

- For each event, a fixture asserts that the trigger point runs only the actions that opt in to automatic
  triggering, and that a person's or an agent's trigger runs all of them.
- Fixtures on the default branch, with uncommitted changes and, with verification enabled, with an invalid proof
  refuse the ready-for-delivery event and run no action.
- A fixture whose task title and branch name contain shell metacharacters passes them to actions unchanged as
  environment values, and nothing in them executes.
- A fixture in which an unattended worker tries to trigger an event is denied, and the event fires only after the
  worker exits.
- Every event run in the fixture suite appears in the record with its exit status.
- With no actions declared, every event runs nothing and succeeds.
