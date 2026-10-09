# 0011. Safety invariants are built into the binary, never configured

## Context

- Safety settings that live in configuration do not survive copying. Of three hand-ported copies of one agent
  workflow, two start unattended agents with permission bypass: the loop runner's configuration skips permission
  prompts, the second agent runs in full-auto mode with hook trust bypassed, and one copy documents an unrestricted
  sandbox as intended. The third copy refuses exactly those flags.
- An unattended agent can claim its task is complete. An agent that can write both its work and the configuration of
  its verification can weaken the verification.
- An unattended agent writes in a tree that also holds the rendered permission rules, the hook entries and the
  guidance files its harness reads. An edit to them changes what the next attempt may do or is told, without passing
  through the policy.
- Lifecycle actions and notification actions ([0015](0015-development-lifecycle.md),
  [0019](0019-action-kinds.md)) run as child processes of the binary, outside the per-command permission checks of any
  agent, with the credentials in the environment.
- An action may start an agent ([0019](0019-action-kinds.md)). Its prompt can contain values written by people and
  agents, such as task titles and agent messages, and nobody watches the session.
- Delivery publishes work outside the clone. Publishing from the default branch, publishing uncommitted work, or
  publishing work whose verification did not pass is never a legitimate choice.
- A task runner that accepts several tasks in one invocation lets a single allowed command run tasks the policy never
  listed.
- 5 of 51 surveyed repositories have a default branch other than `main`; a rule that names `main` is wrong in them.
- A tool command has several spellings: the committed wrapper, a global install, an absolute path to either, a shell
  that runs the wrapper script. Agent permission rules match the literal leading words of a command (Claude Code 2.1.x
  pattern strings, Codex CLI 0.16x rule files).
- Organizations legitimately differ in policy ([0013](0013-organization-content.md)).

## Decision

The following hold in every repository. They are built into the binary, are not expressible in repository data or
presets, and cannot be removed or weakened by them. Repository policy and presets add to them.

1. Unattended agents never run with permission bypass. Bypass flags in arguments or in inherited configuration are
   refused with a message that names their source.
2. Force push to the repository's default branch, detected from the remote's `HEAD`, is denied.
3. Chaining several tasks in one task-runner invocation is denied.
4. Proof of verification is replayed by the binary with configuration read from the committed base, outside any tree
   the agent can write.
5. Completion is withheld until the replay passes. A malformed proof counts as failed.
6. Hooks never decide permissions; permissions come only from the rendered policy.
7. The binary never writes knowledge (prose, memory, domain skills) and never writes the team's record of work
   ([0012](0012-write-boundary.md)).
8. The binary never reconfigures state it did not create ([0012](0012-write-boundary.md)).
9. Tool-owned files cannot be tampered with by an unattended agent. Before every launch of an unattended agent, the
   binary restores the tool-owned files (rendered rules, owned settings keys, hook entries) from the session's base
   commit; after the agent exits, it compares them with the base. Any difference fails the attempt, withholds
   completion and is reported. The repository data that drives the replay, and the guidance injected into the agent
   ([0020](0020-standing-context.md)), are read from the base commit.
10. Lifecycle actions and notification actions never run inside an unattended agent. The binary fires lifecycle
    events in its own process after the agent exits, and unattended agents are denied triggering events.
11. Delivery is refused from the default branch and with uncommitted changes and, when verification is enabled,
    without a valid proof of verification for the work's base. A refused delivery runs none of its actions.
12. An action that starts an agent runs it under the same contract as an unattended agent: the binary pins its
    permission mode, applies the unattended denies, refuses bypass and runs the tamper check of invariant 9. The agent
    cannot commit, push or trigger events.
13. The binary enforces its guards itself. It recognises an unattended agent by a mark its launcher sets in that
    agent's environment, and it checks branch and tree state before acting. Permission entries that deny or ask for
    the tool's own commands are defence in depth, never the only guard.

## Reasons

- **All.** Every rule in this list is never a legitimate choice, so a repository loses nothing by being unable to
  configure it. Rules that are configuration get dropped: two of three copies of one workflow lost them by omission.
  Built into the binary, the floor travels with every release and no copy of a repository's files can lower it. The
  line between invariant and policy is exactly "never legitimate"; everything an organization may want differently
  stays policy ([0013](0013-organization-content.md)).
- **1.** Bypass removes every permission check from an agent that nobody is watching. Naming the source of the flag
  (an argument or an inherited configuration file) lets the person fix the file that carries it.
- **2.** A force push to the default branch rewrites history others build on. Detecting the branch from the remote's
  `HEAD` makes the rule right in the 5 surveyed repositories whose default branch is not `main`.
- **3.** With chaining, an allow rule for one task becomes an allow rule for any task, and the policy stops meaning
  what a reviewer reads.
- **4, 5.** The agent's proof selects which checks to run; the binary runs them, from configuration the agent cannot
  edit. An agent that weakens its verification in its own tree therefore changes nothing. A malformed proof fails so
  that a broken proof leads to a retry, never to a pass.
- **6.** Permissions decided in hooks would live in code paths reviewers do not read, beside the policy they do. One
  reviewable source keeps every agent's permissions explainable ([0009](0009-agent-support.md)).
- **7, 8.** These hold whatever an agent asks the binary to do, because unattended agents run the binary
  ([0002](0002-division-of-work.md), [0012](0012-write-boundary.md)).
- **9.** Restoring before launch removes edits left by an earlier attempt; comparing after exit catches edits made
  during this one. Reading the replay's data and the injected guidance from the base commit means an agent cannot
  rewrite its checks or its own instructions between retries. The trade is that an agent cannot change its own
  permissions, hooks or guidance during a session, even on purpose.
- **10.** Actions reach whatever their credentials reach. Run inside an unattended agent, they would extend that reach
  to the agent. Firing events in the binary's own process after the agent exits keeps actions out of its hands.
- **11.** Delivery leaves the clone, so its mistakes are visible to others and hard to undo, and the three refused
  states are never wanted. Refusing before any action runs keeps a partial delivery from happening.
- **12.** An agent started by an action is unattended in every respect that matters: nobody watches it, it runs with
  the person's credentials, and its prompt can carry injected text. The unattended contract already bounds such an
  agent; reusing it gives one contract to test instead of a second, weaker one. Without the commit, push and event
  restrictions, an action could deliver past the guards of invariant 11 or chain events.
- **13.** Prefix rules match literal words, and the spellings of a tool command are open-ended; no finite rule list
  covers an absolute path or a shell running the wrapper script. A guard the binary checks holds whatever spelling
  started it. Permission rules still stop the common spellings before the binary starts and show intent in review.

## Rejected alternatives

### Default policy entries the repository can edit

At its best, the rules are visible and adjustable per repository. It loses because this is how they are lost: an entry
a repository can edit is an entry a copy can drop.

### An opt-in safety preset

At its best, teams choose their own floor and the binary stays smaller. It loses the same way, by omission: a
repository that never extends the preset has no floor.

### Invariants with an override switch

At its best, real exceptions have an escape hatch. It loses because an override in a committed file is a bypass that
copies along with the file. A person who needs different behaviour starts the harness directly, outside the tool, and
the tool does not interfere ([0012](0012-write-boundary.md)).

### Guards expressed only as permission rules

At its best, there is one enforcement mechanism, visible in the native files and applied by the harness before a
command starts. It loses because prefix rules cannot enumerate every spelling of a command, so an agent that calls the
tool by an absolute path would pass.

## Consequences

Costs:

- Teams that run unattended agents with permission bypass cannot do so through the tool.
- An unattended agent cannot change its own permissions, hooks or guidance during a session; the change goes through a
  person and a commit.
- An action that starts an agent gets the unattended permissions only; work that needs more is a command action or a
  person's job.
- Changing an invariant takes a release and applies everywhere at once.

Must be maintained:

- At least one contract test per invariant, this list in the user documentation, and the set of command spellings the
  rendered permission entries cover.

## Confirmation

- CI maps each numbered invariant to at least one named contract test and fails when an invariant has none.
- A fixture policy that omits or contradicts a built-in entry still renders that entry in every agent's output.
- A fixture whose inherited configuration carries a bypass flag is refused, and the message names the file.
- A fixture in which the agent edits its verification configuration still has its proof replayed with the base
  configuration.
- A fixture in which the agent edits a rendered rule or a hook entry fails the attempt and withholds completion.
- A fixture in which the agent edits a guidance file receives the base commit's guidance on its next attempt.
- A fixture in which an unattended agent tries to trigger a lifecycle event, by any spelling including an absolute
  path to the binary, is refused by the binary, and the event's actions run only after the agent exits.
- Delivery fixtures on the default branch, with uncommitted changes and, with verification enabled, with an invalid
  proof are refused, and no action runs.
- A fixture action that starts an agent runs it with the pinned permission mode, the unattended denies and the tamper
  check, and the agent's attempts to commit, push or trigger an event are refused.
