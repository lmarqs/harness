# 0011. Safety invariants are built into the binary, never configured

## Objective

**Problem.** Safety settings that live in configuration get lost when the configuration is copied or edited, and a copy
that has lost them runs unattended agents with permission checks turned off. An unattended agent that can edit its own
rules or its own verification can also give itself more permissions and claim work it did not do.

**Goal.** In every repository, every unattended agent the tool starts runs with permission checks on, cannot widen its
own permissions, and cannot be recorded as done without a verification that passed.

## Context

- Safety settings that live in configuration do not survive copying. Example: acme copies one agent workflow by hand
  from `acme/api` into `acme/web` and `acme/infra`. In `acme/web`, the configuration of the runner that drives
  unattended agents through a task list skips permission prompts. In `acme/infra`, the second agent the repository uses
  for unattended work runs in its full-auto mode with hook trust bypassed, and the copy's documentation calls an
  unrestricted sandbox intended. The `acme/api` copy refuses exactly these settings.
- An unattended agent can claim its task is complete. An agent that can write both its work and the configuration of
  its verification can weaken the verification.
- An unattended agent writes in a tree that also holds the rendered permission rules, the hook entries, the wrapper
  that starts the tool and the guidance files its harness reads. An edit to them changes what the next attempt may do
  or is told, without passing through the policy.
- A loop runner can run its tasks one after another in one working copy, each starting from what the previous one
  committed, and can start the same command again after a failed attempt. ralph-tui 0.12 does both in its default
  sequential mode. A task whose commit changed the repository's rules would then set the rules for every later task,
  and a failed attempt's edits would be the files the next attempt starts from.
- Lifecycle actions and notification actions ([0015](0015-development-lifecycle.md),
  [0019](0019-action-kinds.md)) run as child processes of the binary, outside the per-command permission checks of any
  agent, with the credentials in the environment.
- An action may start an agent ([0019](0019-action-kinds.md)). Its prompt can contain values written by people and
  agents, such as task titles and agent messages, and nobody watches the session.
- Delivery publishes work outside the clone, for example by pushing a branch or opening a pull request. Publishing from
  the default branch, publishing uncommitted work, or publishing work whose verification did not pass is never a
  legitimate choice.
- A task runner that accepts several tasks in one invocation, as mise (2026.6) does, lets a single allowed command run
  tasks the policy never listed. For example, a rule that allows the runner's `test` task by its leading words also
  matches one invocation that runs `test` and then `deploy`.
- A repository's default branch is its own setting, and its name varies: `main`, `master` and `develop` all occur.
  Example: the default branch of `acme/infra` is `master`, so a rule that denies force pushes to `main` does not protect
  it.
- A tool command has several spellings: the committed wrapper, a global install, an absolute path to either, a shell
  that runs the wrapper script. Agent permission rules match the literal leading words of a command (Claude Code 2.1.x
  pattern strings, Codex CLI 0.16x rule files).
- Organizations legitimately differ in policy ([0013](0013-organization-content.md)).
- A person can start an agent, or a loop runner that starts agents, without the tool. A loop runner merges a person's
  own configuration into every run and passes what it configures to the command it starts as arguments: ralph-tui (0.12)
  places its configured default flags and its agent plugin's flags before the task's arguments
  ([0022](0022-tool-integration.md)).

## Decision

The following hold in every repository, for what the binary renders and for every agent the binary starts
([0022](0022-tool-integration.md)). They are built into the binary, are not expressible in repository data, and cannot
be removed or weakened by it. Repository policy adds to them.

1. Unattended agents never run with permission bypass. A bypass flag among the arguments the worker command receives is
   refused with a message that names the flag.
2. Force push to the repository's default branch, detected from the remote's `HEAD`, is denied.
3. Chaining several tasks in one task-runner invocation is denied.
4. Proof of verification, the record a verification run leaves of the checks it ran, is replayed by the binary with
   configuration read from the committed base, outside any tree the agent can write.
5. Completion is withheld until the replay passes. A malformed proof counts as failed.
6. Hooks never decide permissions; permissions come only from the rendered policy.
7. The binary never writes knowledge (prose, memory, domain skills) and never writes the team's record of work
   ([0012](0012-write-boundary.md)).
8. The binary never reconfigures state it did not create ([0012](0012-write-boundary.md)).
9. An unattended agent cannot change the rules it runs under. Before every launch of an unattended agent, the binary
   restores the tool-owned files (rendered rules, owned settings keys, hook entries, the wrapper and its pin) from the
   base commit of the attempt. After the agent exits, it compares them with the base, together with the repository data
   that drives the replay and the guidance injected into the agent ([0020](0020-standing-context.md)). Any difference
   fails the attempt, withholds completion and is reported. The repository data and the guidance are read from the
   base commit. A failed attempt of the worker command leaves the working copy at its base commit, with the attempt's
   changes kept aside for a person to read.
10. Lifecycle actions and notification actions never run inside an unattended agent. The binary fires lifecycle
    events in its own process after the agent exits, and unattended agents are denied triggering events.
11. Delivery is refused from the default branch and with uncommitted changes and, when verification is enabled,
    without a valid proof of verification for the work's base. A refused delivery runs none of its actions.
12. An action that starts an agent runs it under the same contract as an unattended agent: the binary pins its
    permission mode, applies the deny rules for unattended agents, refuses bypass and runs the tamper check of
    invariant 9. The agent cannot commit, push or trigger events.
13. The binary enforces its guards itself. It recognises an unattended agent by a mark that the worker command and an
    action that starts an agent set in that agent's environment, and it checks branch and tree state before acting.
    Permission entries that deny or ask for the tool's own commands are defence in depth, never the only guard.

## Reasons

- **All.** Every rule in this list is never a legitimate choice, so a repository loses nothing by being unable to
  configure it. Rules that are configuration get dropped by omission, as in two of the three acme copies in Context.
  Built into the binary, the floor travels with every release and no copy of a repository's files can lower it. The line
  between invariant and policy is exactly "never legitimate". Everything an organization may want differently stays
  policy ([0013](0013-organization-content.md)). The floor covers what the binary renders and the agents it starts,
  because those are what it controls: an agent or a loop runner a person starts outside the tool is beyond its reach,
  and reading other tools' configuration to chase it is rejected in [0022](0022-tool-integration.md).
- **1.** Bypass removes every permission check from an agent that nobody is watching. A loop runner hands the worker
  command what its merged configuration sets as arguments (Context), so checking the arguments it receives catches a
  bypass from any of those files without reading one ([0022](0022-tool-integration.md)). Naming the flag lets the person
  find the configuration that passes it.
- **2.** A force push to the default branch rewrites history others build on. Detecting the branch from the remote's
  `HEAD` makes the rule right in every repository, including those whose default branch is not `main`, such as
  `acme/infra` in Context.
- **3.** With chaining, an allow rule for one task becomes an allow rule for any task, and the policy stops meaning what
  a reviewer reads. Denying the shape needs no integration of the task runner behind it
  ([0022](0022-tool-integration.md)).
- **4, 5.** The agent's proof selects which checks to run. The binary runs them from configuration the agent cannot
  edit. An agent that weakens its verification in its own tree therefore changes nothing. A malformed proof fails so
  that a broken proof leads to a retry, never to a pass.
- **6.** Permissions decided in hooks would live in code paths reviewers do not read, beside the policy they do. One
  reviewable source keeps every agent's permissions explainable ([0009](0009-agent-support.md)).
- **7, 8.** These hold whatever an agent asks the binary to do, because unattended agents run the binary
  ([0002](0002-division-of-work.md), [0012](0012-write-boundary.md)).
- **9.** Restoring before launch removes edits left by an earlier attempt, and comparing after exit catches edits made
  during this one. Reading the replay's data and the injected guidance from the base commit means an agent cannot
  rewrite its checks or its own instructions between retries. Failing an attempt that changed them means a change to
  the rules reaches unattended work only through a commit a person made, even in a run where each task starts from
  the previous task's commit (Context). Returning a failed attempt to its base means the next attempt starts from files
  the binary checked, including the wrapper a loop runner starts it through. The trade is that an agent cannot change
  its own permissions, hooks or guidance, even on purpose, and a retry starts over instead of continuing the failed
  attempt's edits.
- **10.** Actions reach whatever their credentials reach. Run inside an unattended agent, they would extend that reach
  to the agent. Firing events in the binary's own process after the agent exits keeps actions out of its hands.
- **11.** Delivery leaves the clone, so its mistakes are visible to others and hard to undo, and the three refused
  states are never wanted. Refusing before any action runs keeps a partial delivery from happening.
- **12.** An agent started by an action is unattended in every respect that matters: nobody watches it, it runs with the
  person's credentials, and its prompt can carry injected text. The unattended contract already bounds such an agent.
  Reusing it gives one contract to test instead of a second, weaker one. Without the commit, push and event
  restrictions, an action could deliver past the guards of invariant 11 or chain events.
- **13.** Prefix rules match literal words, and the spellings of a tool command are open-ended. No finite rule list
  covers an absolute path or a shell running the wrapper script. A guard the binary checks holds whatever spelling
  started it. Permission rules still stop the common spellings before the binary starts and show intent in review.

## Rejected alternatives

### Default policy entries the repository can edit

At its best, the rules are visible and adjustable per repository. It loses because editable entries are how the rules
get lost: a copy can drop any entry a repository can edit.

### An opt-in safety preset

The invariants ship as a preset a repository applies. At its best, teams choose their own floor and the binary stays
smaller. It loses the same way, by omission: a repository that never applies the preset, or edits its entries away,
has no floor.

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
- A fixture in which the worker command receives a bypass flag, passed directly or by a loop runner's agent entry, is
  refused, the message names the flag, and no agent starts.
- A fixture in which the agent edits a rendered rule or a hook entry fails the attempt and withholds completion.
- A fixture in which the agent edits a guidance file or the verification configuration fails the attempt, and its
  next attempt starts at the base commit with the base commit's guidance.
- A fixture in which the agent rewrites the wrapper and then fails leaves the base commit's wrapper in the working
  copy, and the next attempt runs the pinned release.
- A fixture in which an unattended agent tries to trigger a lifecycle event, by any spelling including an absolute
  path to the binary, is refused by the binary, and the event's actions run only after the agent exits.
- Delivery fixtures on the default branch, with uncommitted changes and, with verification enabled, with an invalid
  proof are refused, and no action runs.
- A fixture action that starts an agent runs it with the pinned permission mode, the unattended denies and the tamper
  check, and the agent's attempts to commit, push or trigger an event are refused.
