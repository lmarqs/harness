# 0011. Safety invariants are built into the binary and cannot be expressed or removed as configuration

- Status: Accepted
- Date: 2026-10-09

## Context

- Safety settings expressed as configuration did not survive copying. Two of three hand-ported copies of one agent
  workflow start unattended agents with permission bypass: the loop runner's configuration skips permission prompts,
  the second agent runs in full-auto mode with hook trust bypassed, and one copy documents an unrestricted sandbox
  as intended. The original refuses exactly those flags.
- An unattended agent can claim its task is complete. An agent that can write both its work and the configuration
  of its verification can weaken the verification.
- An unattended agent writes in a tree that also holds the rendered permission rules and hook entries its harness
  reads. An edit to them widens what the next attempt may do, without passing through the policy.
- Lifecycle actions ([0015](0015-lifecycle-events-with-declared-actions.md)) run as child processes of the binary,
  outside the per-command permission checks of any agent. Run inside an unattended agent, they would let it reach
  anything they reach.
- Delivery publishes work outside the clone. Publishing from the default branch, publishing uncommitted work, or
  publishing work whose verification did not pass is never a legitimate choice.
- A task runner that accepts several tasks in one invocation lets a single allowed command run tasks the policy
  never listed.
- A default branch name hardcoded in the incumbent policy would be wrong in the 5 of 51 surveyed repositories whose
  default branch is not `main`.
- Organizations legitimately differ in policy ([0013](0013-organization-neutrality-and-presets.md)). The invariants
  must be the small set that is never a legitimate choice.

## Decision

The following hold in every repository. They are built into the binary, are not expressible in repository policy
or presets, and cannot be removed or weakened by them. Repository policy and presets add to them.

1. Unattended agents never run with permission bypass. Bypass flags in arguments or in inherited configuration are
   refused with a message that names their source.
2. Force push to the repository's default branch, detected from the remote's `HEAD`, is denied.
3. Chaining several tasks in one task-runner invocation is denied.
4. Proof of verification is replayed by the binary with configuration read from the committed base, outside any
   tree the agent can write.
5. Completion is withheld until the replay passes. A malformed proof counts as failed.
6. Hooks never decide permissions; permissions come only from the rendered policy.
7. The binary never writes knowledge (prose, memory, domain skills) and never writes the team's record of work
   ([0012](0012-coexistence-with-team-record-and-third-party-state.md)).
8. The binary never reconfigures state it did not create
   ([0012](0012-coexistence-with-team-record-and-third-party-state.md)).
9. Tool-owned files cannot be tampered with by an unattended agent. Before every launch of an unattended agent, the
   binary restores the tool-owned files (rendered rules, owned settings keys, hook entries) from the session's base
   commit; after the agent exits, it compares them with the base. Any difference fails the attempt, withholds
   completion and is reported. The repository data that drives the replay is read from the base commit.
10. Lifecycle actions never run inside an unattended agent. The binary fires lifecycle events in its own process
    after the agent exits, and unattended agents are denied triggering events.
11. Delivery is refused from the default branch and with uncommitted changes and, when verification is enabled,
    without a valid proof of verification for the work's base. A refused delivery runs none of its actions.

## Alternatives considered

### Default policy entries the repository can edit

At its best, the rules are visible and adjustable per repository. Not chosen: this is how they were lost.

### An opt-in safety preset

At its best, teams choose their own floor. Not chosen: it fails the same way, by omission.

### Invariants with an override switch

At its best, real exceptions have an escape hatch. Not chosen: an override in a committed file is a bypass that
copies along with the file. A person who needs different behaviour starts the harness directly, outside the tool,
and the tool does not interfere ([0012](0012-coexistence-with-team-record-and-third-party-state.md)).

## Consequences

Positive:

- Every adopting repository has the same safety floor, and copying a repository's files cannot lower it.

Negative:

- Teams that run unattended agents with permission bypass cannot do so through the tool.
- An unattended agent cannot change its own permissions or hooks during a session, even when the change is
  intended; the change goes through a person and a commit.
- Changing an invariant takes a release and applies everywhere at once.

Must be maintained:

- At least one contract test per invariant, and this list in the user documentation.

## Confirmation

- CI maps each numbered invariant to at least one named contract test and fails when an invariant has none.
- A fixture policy that omits or contradicts a built-in entry still renders that entry in every agent's output.
- A fixture whose inherited configuration carries a bypass flag is refused.
- A fixture in which the agent edits its verification configuration still has its proof replayed with the base
  configuration.
- A fixture in which the agent edits a rendered rule or a hook entry fails the attempt and withholds completion.
- A fixture in which an unattended agent tries to trigger a lifecycle event is denied, and the event's actions run
  only after the agent exits.
- Delivery fixtures on the default branch, with uncommitted changes and, with verification enabled, with an invalid
  proof are refused, and no action runs.
