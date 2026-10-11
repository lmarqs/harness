# 0011. Safety invariants are built into the binary, never configured

## Objective

**Problem.** Safety settings that live in configuration get lost when the configuration is copied or edited, and a copy
that has lost them runs unattended coding agents with permission checks turned off. A coding agent left alone that can
edit its own rules or its own verification can also give itself more permissions and claim work it did not do.

**Goal.** In every repository, every worker the tool starts unattended runs with permission checks on, cannot widen its
own permissions, and cannot be recorded as done without a verification that passed.

## Context

- Safety settings that live in configuration do not survive copying. Example: acme copies one agent workflow by hand
  from `acme/api` into `acme/web` and `acme/infra`. In `acme/web`, the configuration of the runner that drives
  unattended agents through a task list skips permission prompts. In `acme/infra`, the second agent the repository uses
  for unattended work runs in its full-auto mode with hook trust bypassed, and the copy's documentation calls an
  unrestricted sandbox intended. The `acme/api` copy refuses exactly these settings.
- An unattended coding agent can claim its task is complete, and one that can write both its work and the configuration
  of its verification can weaken the verification.
- A worker left unattended writes in a tree that also holds the rendered permission rules, the hook entries, the wrapper
  that starts the tool, the guidance files the worker reads and the recipes the repository runs
  ([0025](0025-recipes.md)). An edit to them changes what the next attempt may do or is told, without passing through
  the policy.
- A loop runner can run its tasks one after another in one working copy, each starting from what the previous one
  committed, and can start the same command again after a failed attempt. ralph-tui 0.12 does both in its default
  sequential mode. A task whose commit changed the repository's rules would then set the rules for every later task,
  and a failed attempt's edits would be the files the next attempt starts from.
- Lifecycle actions and notification actions ([0015](0015-development-lifecycle.md), [0019](0019-action-kinds.md)) run
  as child processes of the binary, outside the per-command permission checks of any worker, with the credentials in the
  environment.
- An action, or a step of a recipe, may start a worker ([0019](0019-action-kinds.md), [0025](0025-recipes.md)). Its
  prompt can contain values written by people and workers, such as task titles and a worker's messages, and nobody
  watches the session.
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
  that runs the wrapper script. A coding agent's permission rules match the literal leading words of a command
  (Claude Code 2.1.x pattern strings, Codex CLI 0.16x rule files).
- Organizations legitimately differ in policy ([0013](0013-organization-content.md)).
- A person can start a worker program, or a loop runner that starts workers, without the tool. A loop runner merges a
  person's own configuration into every run and passes what it configures to the command it starts as arguments:
  ralph-tui (0.12) places its configured default flags and its agent plugin's flags before the task's arguments
  ([0022](0022-tool-integration.md)).

## Decision

The following hold in every repository, for what the binary renders and for every worker the binary starts
([0022](0022-tool-integration.md)). They are built into the binary, are not expressible in repository data, and cannot
be removed or weakened by it. Repository policy adds to them. Facts about a program that they apply, such as which
arguments bypass a worker program's permission checks, come from that program's ingredient, marked as floor
([0022](0022-tool-integration.md)). An attempt is one unattended run of a worker on one task, and the invariants that
bound it are the attempt contract.

1. No attempt runs with permission bypass. Every argument the worker's ingredient marks as bypass is refused when an
   attempt receives it, with a message that names the argument.
2. The deny rules that an ingredient in use marks as floor are rendered into every worker's output on every render, and
   repository policy cannot remove or weaken them. Which rules an ingredient marks as floor is part of the ingredient
   the repository pinned and a reviewer read; examples are a force push to the default branch the version control
   ingredient reports, and chaining several tasks in one task-runner invocation.
3. Proof of verification, the record a verification run leaves of the checks it ran, is replayed by the binary with
   configuration read from the committed base, outside any tree the worker can write.
4. Completion is withheld until the replay passes. A malformed proof counts as failed.
5. Hooks never decide permissions; permissions come only from the rendered policy.
6. The binary never writes knowledge (prose, memory, domain skills) and never writes the team's record of work
   ([0012](0012-write-boundary.md)).
7. The binary never reconfigures state it did not create ([0012](0012-write-boundary.md)).
8. An attempt cannot change the rules it runs under. Its baseline is the checkout as it was when the attempt began: the
   base commit for an attempt run on its own, which refuses uncommitted changes, and the working copy as the earlier
   steps left it for a recipe's attempt step, which runs on their edits. Before every attempt starts its worker, the
   binary resolves the ingredients the attempt uses from the verified cache, and restores the tool-owned files (rendered
   rules, owned settings keys, hook entries, the wrapper and its pin) to that baseline. After the worker exits, it
   compares them with the baseline, together with the repository data that drives the replay and the guidance injected
   into the worker ([0020](0020-standing-context.md)). Any difference fails the attempt, withholds completion and is
   reported. The repository data and the guidance are read from the base commit. A base that cannot be read fails the
   attempt. A failed attempt that the attempt command runs on its own, where a loop runner retries, leaves the working
   copy at its base commit, with the attempt's changes kept aside for a person to read; a recipe's attempt step that
   fails follows invariant 13 instead. For a worker whose declaration names a jail, the version control metadata of the
   working copy is shared read-only inside the jail, so the worker cannot change the history, the configuration or the
   hooks that the binary's own operations, outside the jail, read and run ([0027](0027-worker-jails.md)).
9. Lifecycle actions and notification actions never run inside a worker the binary starts unattended. The binary fires
   lifecycle events in its own process after the worker exits, and a worker it starts unattended is denied triggering
   events.
10. Delivery is refused from the default branch and with uncommitted changes and, when verification is enabled,
    without a valid proof of verification for the work's base. A refused delivery runs none of its actions.
11. An action or a recipe's step that starts a worker runs it under the same contract as an attempt: the binary pins its
    permission mode, applies the deny rules for unattended work, refuses bypass and runs invariant 8's tamper check. The
    worker cannot commit, push or trigger events.
12. The binary enforces its guards itself. It recognises a worker it started unattended by a mark that every attempt,
    and every action or recipe step that starts a worker, sets in that worker's environment, and that the binary passes
    through the jail of a worker that runs in one, as it passes every mark it sets ([0027](0027-worker-jails.md)); and
    it checks branch and tree state before acting. Permission entries that deny or ask for the tool's own commands are
    defence in depth, never the only guard.
13. A recipe run holds every guard of the lifecycle ([0015](0015-development-lifecycle.md), [0025](0025-recipes.md)). A
    recipe can only add to what a change must pass: steps, required lanes and requirements. It cannot skip a lane,
    weaken a guard, declare an action or fire an event; the binary fires the run's events, and the run itself verifies
    every lane the change selects and every lane the recipe requires. A run reads its recipe once, from the base commit,
    when it starts, with the rest of the repository data, as an attempt does (invariant 8), keeps that content in its
    record and never reads the recipe again, so no step can change the recipe of the run it is in. Only a run a person
    drives, started to draft a recipe, reads them from the working copy instead. The caller declares whether a person or
    a worker drives the run, and a handoff in a run a worker drives stops it and fires blocked instead of waiting. An
    attempt step that fails, by its worker's exit or by the tamper check, leaves its changes in place and stops the run,
    which fires blocked naming the step; continued, after a person has inspected or discarded those changes, the run
    starts that step again under the same contract on the tree as it is. The stopped run is the retry boundary, and the
    run's verification and the replay from the base still stand between those changes and completion. A worker the
    binary started unattended cannot start a recipe run.

## Reasons

- **All.** Every rule in this list is never a legitimate choice, so a repository loses nothing by being unable to
  configure it. Rules that are configuration get dropped by omission, as in two of the three acme copies in Context.
  Built into the binary, the process travels with every release, the program facts travel with the ingredients a
  repository pinned, and no copy of a repository's files can lower either. The line between invariant and policy is
  exactly "never legitimate". Everything an organization may want differently stays policy
  ([0013](0013-organization-content.md)). The floor covers what the binary renders and the workers it starts, because
  those are what it controls: a worker program or a loop runner a person starts outside the tool is beyond its reach,
  and reading other programs' configuration to chase it is rejected in [0022](0022-tool-integration.md). The process is
  in the binary and the program facts are in ingredients, because the binary names no program
  ([0022](0022-tool-integration.md), [0023](0023-core-and-contracts.md)); a fact an ingredient marks as floor is out of
  the repository's reach in the same way as the process, because the repository data never contains it.
- **1.** Bypass removes every permission check from a worker that nobody is watching. A loop runner passes what its
  merged configuration sets to the attempt as arguments (Context), so checking the arguments it receives catches a
  bypass from any of those files without reading one ([0022](0022-tool-integration.md)). Naming the argument lets the
  person find the configuration that passes it. The bypass arguments differ per worker program and change between its
  releases, so the worker's ingredient carries them.
- **2.** A force push to the default branch rewrites history others build on, and asking the version control ingredient
  for the default branch makes the rule right in every repository, including those whose default branch is not `main`,
  such as `acme/infra` in Context. With chaining, an allow rule for one task becomes an allow rule for any task, and the
  policy stops meaning what a reviewer reads. Both are rules a repository would lose by omission if they were entries it
  could edit, so they are rendered from the ingredient on every render. The trade: a repository whose task runner has no
  ingredient in use has no chaining deny, which the health check reports when it detects the program.
- **3, 4.** The worker's proof selects which checks to run. The binary runs them from configuration the worker cannot
  edit. A worker that weakens its verification in its own tree therefore changes nothing. A malformed proof fails so
  that a broken proof leads to a retry, never to a pass.
- **5.** Permissions decided in hooks would live in code paths reviewers do not read, beside the policy they do. One
  reviewable source keeps every worker's permissions explainable ([0009](0009-agent-support.md)).
- **6, 7.** These hold whatever a worker asks the binary to do, because workers nobody watches run the binary too
  ([0002](0002-division-of-work.md), [0012](0012-write-boundary.md)).
- **8.** Restoring before launch removes edits left by an earlier attempt, and comparing after exit catches edits made
  during this one. Reading the replay's data and the injected guidance from the base commit means a worker cannot
  rewrite its checks or its own instructions between retries. Resolving the ingredients before launch, from outside the
  working tree, means the worker cannot change how the base is read or how it is started
  ([0022](0022-tool-integration.md)); a base that cannot be read fails closed, because the base is what every other read
  trusts. Failing an attempt that changed the rules means a change to the rules reaches unattended work only through a
  commit a person made, even in a run where each task starts from the previous task's commit (Context). Returning a
  failed attempt to its base means the next attempt starts from files the binary checked, including the wrapper a loop
  runner starts it through. Inside a jail, the worker could otherwise write the metadata the binary's commit and
  comparisons rely on, and the version control program would run what it wrote outside the jail, so the metadata is
  shared read-only there. The trade is that a worker cannot change its own permissions, hooks or guidance, even on
  purpose, and a retry starts over instead of continuing the failed attempt's edits.
- **9.** Actions reach whatever their credentials reach. Run inside a worker nobody watches, they would extend that
  reach to the worker. Firing events in the binary's own process after the worker exits keeps actions out of its hands.
- **10.** Delivery leaves the clone, so its mistakes are visible to others and hard to undo, and the three refused
  states are never wanted. Refusing before any action runs keeps a partial delivery from happening.
- **11.** A worker started by an action or by a recipe's step is unattended in every respect that matters: nobody
  watches it, it runs with the person's credentials, and its prompt can carry injected text. The attempt contract
  already bounds such a worker. Reusing it gives one contract to test instead of a second, weaker one. Without the
  commit, push and event restrictions, an action could deliver past the guards of invariant 10 or chain events.
- **12.** Prefix rules match literal words, and the spellings of a tool command are open-ended. No finite rule list
  covers an absolute path or a shell running the wrapper script. A guard the binary checks holds whatever spelling
  started it. Permission rules still stop the common spellings before the binary starts and show intent in review. A
  jail may pass only the variables it is given ([0027](0027-worker-jails.md)), so the binary passes the mark through it
  explicitly; otherwise every guard that rests on the mark would fall away for the very workers a repository chose to
  confine.
- **13.** A recipe is a file in the tree a worker writes in (Context). If a recipe could skip a lane or fire an event,
  the guards of every change it makes would be only as strong as the last edit to it; because it can only add, the most
  a recipe does is ask for more checks. The run verifies the lanes itself because a worker's verification covers only
  what that worker selected ([0025](0025-recipes.md)), so the proof of a recipe run does not depend on its worker.
  Reading the recipe once, from the base commit, and running the content the record keeps, together with the tamper
  check over the repository data (invariant 8), means a worker cannot change the recipe that it, or a later task, runs:
  an edit to the file reaches no step of the run it is in. A person drafting a recipe runs it before it is committed and
  is there to watch it, while a run a worker drives never reads the working copy, so no draft nobody reviewed runs with
  nobody watching. A handoff in a run a worker drives would wait for a person who is not there; stopping it fires the
  event that reaches one ([0015](0015-development-lifecycle.md)). The caller declares who drives the run because a
  worker in a person's session runs commands with no terminal ([0025](0025-recipes.md)), so a terminal cannot tell, and
  a run whose caller declares nothing pauses at a handoff, which completes nothing. A recipe run's commands run as child
  processes of the binary, like actions, so started from inside a worker the binary started unattended they would reach
  what invariant 9 keeps out of that worker's hands. An attempt step works in a tree that holds the uncommitted output
  of the steps before it, so returning it to the base, as invariant 8 does for an attempt a loop runner retries, would
  discard that output with the attempt's edits; nothing retries a step by itself, and a person decides what to keep
  before it runs again. What the step left can still reach completion only through the run's verification and the replay
  from the base, the proof of invariants 3 and 4, and invariant 8's restore of the tool-owned files to the attempt's
  baseline before every attempt starts its worker. The trade: a recipe meant for a run a worker drives cannot hand off
  to a person, a change to a recipe reaches such a run only once it is on the shared default line of work, and a failed
  attempt step's edits stay in the working copy until a person deals with them.

## Rejected alternatives

### Default policy entries the repository can edit

At its best, the rules are visible and adjustable per repository. It loses because editable entries are how the rules
get lost: a copy can drop any entry a repository can edit.

### Floor rules as station entries

The floor's program facts come as entries a station adds to the repository's policy when it is applied
([0024](0024-ingredients-and-stations.md)). At its best, the rules are visible in the repository's own files and the
binary renders nothing the policy does not hold. It loses the same way as default entries, by omission: a repository
that never applies the station, or edits its entries away, has no floor. Rendered from the ingredient on every render,
the same rules stay visible in every worker's output and out of the repository's reach.

### Invariants with an override switch

At its best, real exceptions have an escape hatch. It loses because an override in a committed file is a bypass that
copies along with the file. A person who needs different behaviour starts the worker program directly, outside the tool,
and the tool does not interfere ([0012](0012-write-boundary.md)).

### Guards expressed only as permission rules

At its best, there is one enforcement mechanism, visible in the native files and applied by the worker program before a
command starts. It loses because prefix rules cannot enumerate every spelling of a command, so a worker that calls the
tool by an absolute path would pass.

## Consequences

Costs:

- Teams that run unattended workers with permission bypass cannot do so through the tool.
- A worker in an attempt cannot change its own permissions, hooks or guidance during a session; the change goes through
  a person and a commit.
- An action that starts a worker gets the unattended permissions only; work that needs more is a command action or a
  person's job.
- Changing an invariant takes a release and applies everywhere at once.
- A program's floor rules are only as complete as the ingredient the repository pinned; an ingredient that marks no
  floor leaves its program without one.
- Inside a jail, the mark and the read-only metadata hold only as far as the jail program honours the variables and
  shares its ingredient declares, which the cookbook that publishes the ingredient tests
  ([0027](0027-worker-jails.md)).

Must be maintained:

- At least one contract test per invariant, this list in the user documentation, and the set of command spellings the
  rendered permission entries cover.

## Confirmation

- CI maps each numbered invariant to at least one named contract test and fails when an invariant has none.
- A fixture in which an attempt receives a bypass argument that the worker's ingredient marks, passed directly or by a
  loop runner's configuration, is refused, the message names the argument, and no worker starts.
- A fixture policy that tries to allow a floor rule of an ingredient in use still renders the deny in every worker's
  output.
- A fixture attempt whose base cannot be read fails before the worker starts.
- A fixture in which the worker edits a rendered rule or a hook entry fails the attempt and withholds completion.
- A fixture in which the worker edits a guidance file or the verification configuration fails the attempt, and its next
  attempt starts at the base commit with the base commit's guidance.
- A fixture in which the worker rewrites the wrapper and then fails leaves the base commit's wrapper in the working
  copy, and the next attempt runs the pinned release.
- A fixture worker in a jail cannot write the version control metadata of its working copy.
- A fixture in which a worker in an attempt tries to trigger a lifecycle event, by any spelling including an absolute
  path to the binary, is refused by the binary, and the event's actions run only after the worker exits; the same holds
  for a fixture worker in a jail that drops every variable it is not passed.
- Delivery fixtures on the default branch, with uncommitted changes and, with verification enabled, with an invalid
  proof are refused, and no action runs.
- A fixture action that starts a worker, and a fixture recipe's attempt step, each run the worker with the pinned
  permission mode, the unattended denies and the tamper check, and the worker's tries to commit, push or trigger an
  event are refused.
- Validation refuses a fixture recipe that skips a lane, declares an action or fires an event. A fixture run follows the
  recipe of its base commit as it read it when it started, after a worker edits the recipe in the working copy and after
  it is continued. A fixture run declared as driven by a worker and asked to read its recipe from the working copy is
  refused, and one declared so that reaches a handoff stops and fires blocked; the same run with no declaration pauses
  there and fires nothing. A fixture run whose worker leaves a proof without a required lane still runs that lane in its
  own verification. A fixture worker in an attempt that starts a recipe run is refused. A fixture attempt step whose
  worker fails leaves its changes in the working copy, stops the run with the blocked event naming the step, and runs
  again on the tree as it is when the run is continued.
