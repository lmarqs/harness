# 0025. A recipe is one kind of change, run through the lifecycle

## Objective

**Problem.** A repository makes the same kinds of change again and again, such as adding a database migration or a
module. Each instance has steps that are always the same, steps that need judgement and checks it must pass, yet people
and coding agents make each instance their own way, nothing ensures that the checks the kind of change needs ran, and an
instance that stops partway while nobody watches stays where it stopped. When teams share how a kind of change is made,
each team adapts it to its repository, and a newer shared version then has to reach the repository without undoing the
team's changes.

**Goal.** Every instance of a kind of change goes through the same steps, checks and guards, whoever starts it and
whatever program supplies its judgement, and an instance that stops reaches a person. A team adapts a shared kind of
change to its repository and still takes a newer version of it in one reviewed change.

## Context

- A repository makes some kinds of change many times, each with a fixed part, a part that needs judgement, and checks of
  its own. Example: `acme/api` adds a database migration several times a month. Each time someone runs the stack's
  generator command with the migration's name, writes the up and down steps, adds a test of the down step, checks the
  generated SQL against production data volumes, and runs the unit and migration test lanes. Done by hand, or by a
  coding agent from a page of instructions, one migration ships without its down test and another without its migration
  lane run.
- Teams that share how a kind of change is made adapt it to their repository. Example: `acme/web` adds migrations the
  way `acme/api` does, but its generator writes them to another directory, its migration lane has another name, and its
  instructions for the up and down steps name its own database conventions.
- Nx generators, Angular schematics and Rails generators produce a change from declared inputs: a schema or an argument
  list declares the options, the generator's code writes and edits files from them, and a dry run shows the result
  without writing it. A generator is code in its framework's language, distributed with the framework, with a plugin or
  in the project. None of the three has a notion of checks the generated change must pass, or of the change being done:
  a run ends once its files are written and any follow-up task it schedules, such as installing packages, has run.
- A skill is instructions a coding agent reads and applies with judgement. Nothing fixes its output, so two runs on one
  repository can make two different changes, and nothing outside the agent records
  which of its steps ran or whether the checks it names passed.
- Every change goes through the stages of the lifecycle, with guards between them, a proof of verification bound to its
  base and content, and events that fire whoever starts the work ([0015](0015-development-lifecycle.md)).
- A loop runner runs one command for each task, the tool's attempt command when it starts workers through the tool
  ([0022](0022-tool-integration.md)). The attempt runs in whatever working copy the loop runner runs in, and fires
  work completed only when that copy is bound to a task ([0015](0015-development-lifecycle.md)).
- A worker in an attempt can edit every file in its working copy, the repository's data included, and a run nobody
  watches has nobody to answer a question ([0011](0011-safety-invariants.md), [0015](0015-development-lifecycle.md)).
- A worker in a person's session runs the tool's commands with no terminal attached, as a loop runner and a script do:
  Claude Code 2.1.x attaches no terminal to the standard input, output or error of the shell commands it runs. Whether a
  terminal is attached does not tell whether a person will answer.
- A verification a worker runs checks the lanes it selects for its change, and its proof records that selection
  ([0011](0011-safety-invariants.md)). A lane the change selects nothing for runs nothing.
- A value substituted into shell text can add a command ([0019](0019-action-kinds.md)). A value passed as one element of
  an argument list, with no shell in between, reaches the program as that one argument, which the program may still read
  as an option when it begins with a dash.
- ESLint loads a shareable configuration that a project names on every run, and the project's own settings override it
  ([0024](0024-ingredients-and-stations.md)).
- shadcn/ui distributes components as source: its command line copies a component's code into the project, which from
  then on owns it and edits it like code it wrote, instead of importing it from a package.
- Copier records in the project which template and which version generated it
  ([0024](0024-ingredients-and-stations.md)). Its update applies the template's changes between that version and a newer
  one while keeping the project's own changes, and marks for a person, inline or in reject files, the places where both
  changed the same lines.
- A station sets up a way of working once: it pins the ingredients that fill the version control and worker contracts,
  and declares the workers ([0024](0024-ingredients-and-stations.md)). Starting a working copy needs the version control
  contract, and an attempt needs a declared worker ([0023](0023-core-and-contracts.md)).
- Delivery follows each team's conventions and hosting platform, declared once in the repository as actions on lifecycle
  events ([0015](0015-development-lifecycle.md)).
- Some recurring work changes nothing in the repository, such as watching a running system for errors. Acting on what it
  finds, such as fixing the error an alert names, does.

## Decision

1. A recipe describes one kind of change that a repository makes many times: the inputs that vary between instances,
   what the change requires of the repository, the working copy it starts in, the steps that implement it, and the lanes
   its verification must include. A cookbook may publish recipes beside its ingredients and stations
   ([0024](0024-ingredients-and-stations.md)), and a repository may write its own.
2. A run of a recipe is one pass of one change through the stages of the lifecycle
   ([0015](0015-development-lifecycle.md)). It creates the change's working copy, on a branch of its own at the revision
   of the shared default line of work, as the clone last learned it, or runs in the managed working copy it is started
   in. Its steps, in order, are the implement stage. The run verifies every lane the change selects and every lane the
   recipe requires, none of them skipped, and a required lane the change selects nothing for runs in full; the proof
   records, for each lane, what it ran. A verification that the worker of an attempt step runs informs that worker and
   is not the run's proof. The run then completes as any work does: the proof is replayed from the base, and the events
   fire at the binary's trigger points. Delivery follows the repository's declared actions; a recipe declares no
   lifecycle action.
3. Each step has one of four kinds, a closed set the binary defines:
   - a command: an argument list the binary runs in the working copy, never through a shell; a command that fails stops
     the run;
   - a template: a file the recipe carries, rendered with the inputs and written into the working copy; a path that
     exists stops the run and stays as it is;
   - an attempt: a worker the repository declares in the unattended role, started on a prompt in the working copy under
     the attempt contract ([0011](0011-safety-invariants.md)); its edits stay for the next steps, the run's verification
     and completion cover them, and an attempt that fails stops the run and leaves its changes in place;
   - a handoff: a text shown to whoever drives the run, after which a run a person drives pauses until it is
     continued, and fires nothing.

   The caller declares whether a person or a worker drives the run. A person drives it at a terminal or through the
   worker in their session; a worker drives it when nobody watches, as when a loop runner starts a run for a task. A
   run whose caller declares nothing is one a person drives.

   How these kinds relate to the kinds of declared actions is decided in [0019](0019-action-kinds.md).
4. Each input has a type from a closed set, and every input is validated against its type before any step runs. No input
   of any type may begin with a hyphen, and validation refuses one before anything runs. A command receives the inputs
   substituted into whole elements of its argument list, and as environment values; a prompt and a handoff's text
   receive them as plain text.
5. A recipe declares the contracts, worker roles and lanes it requires. The binary checks them when the recipe is added
   and before each run, and a missing one is a missing prerequisite, named before any step runs
   ([0023](0023-core-and-contracts.md)).
6. What a recipe can and cannot change about the lifecycle's guards, how a run reads its recipe, how a run a worker
   drives treats its handoffs, and what a failed attempt step leaves, are invariants
   ([0011](0011-safety-invariants.md)).
7. A run that stops fires the blocked event with the step and the reason ([0015](0015-development-lifecycle.md)) and
   keeps its working copy. It stops at a step that fails, at a verification that lacks a lane the repository's lane
   rules demand, or at a handoff in a run a worker drives. Continued, after a person has inspected or discarded what the
   step left, it runs the step that stopped again on the tree as it is. Abandoning it is removing its working copy.
   Every run keeps a record in the clone's state: the recipe's content as the run read it and a hash of that content,
   the inputs, each step's result and the current stage. Continuing a run follows the recipe content in its record. The
   proof of verification a run writes records the recipe, its hash and its inputs, and the declared actions of its
   events receive them.
8. A recipe reaches a repository as a copy, made on a person's request, and the copy is the repository's own from then
   on: its team edits it like any of its files, and a run follows the copy as the repository holds it. The copy records
   its origin, the cookbook, version and content hash it came from, and a newer version reaches it through a merge that
   keeps the team's changes ([0013](0013-organization-content.md)). Adding and updating a recipe verify it as
   [0026](0026-cookbook-trust.md) decides. A station may pin recipes it adds, each by its content hash, as it pins its
   ingredients, and applying it copies them in the same change ([0024](0024-ingredients-and-stations.md)).
9. A loop runner can run a recipe for each task, through the same caller interface as the attempt command
   ([0022](0022-tool-integration.md)). Each task is then one run, in a working copy bound to that task. The attempt
   command stays the primitive that scripts call and that attempt steps use.
10. Work that changes nothing in the repository, such as watching a running system, is not a recipe: it has no working
    copy, no proof and nothing to deliver. Acting on what such work finds is one: a recipe whose inputs carry the
    finding, such as an alert, and whose attempt step has a worker follow the guidance for handling it.

## Reasons

- **1.** Kinds of change recur with the same shape, a fixed part, a part that needs judgement and checks of their own
  (Context). Describing that shape once, as data the repository holds, lets every instance follow it. The inputs are
  what varies between instances; the requirements, the working copy, the steps and the lanes are what does not. A recipe
  a cookbook publishes and one a repository writes are the same unit, because both are the repository's own once it
  holds them (point 8). The trade: one more format to version, and a kind of
  change made too rarely to describe gains nothing from one.
- **2.** A generator stops once its files are written, and a skill is applied as the agent judges (Context), so in both
  the checks the change needs are left to whoever ran it. Running a recipe as one pass through the stages puts every
  instance under the guards and the proof that every change has ([0015](0015-development-lifecycle.md)): it cannot
  complete without a verification that includes its lanes, whoever started it. The run verifies itself because a
  worker's verification checks only the lanes the worker selected (Context), which can leave out the lane the kind of
  change needs; the worker's own verification still tells it whether its edits pass, but the proof is the run's.
  Required lanes come on top of those the change selects, so the checks of the kind of change, such as the migration
  lane in Context, run in every instance, whatever its files select, and a required lane the change selects nothing for
  runs in full, because run with nothing selected it would check nothing (Context). Recording what each lane ran lets
  the replay from the base run exactly that again. A working copy and a branch of its own make each instance a change of
  its own, like any work started in the lifecycle. Starting it at the revision of the shared default line of work,
  rather than at one clone's own commits, means a run follows the recipe and the repository data that were shared, and
  so reviewed, and that every clone runs alike. Delivery stays with the repository's declared actions because it is the
  repository's convention, the same for every change (Context): if each recipe declared its own, how a change leaves the
  clone would depend on which recipe made it. The trade: a required lane the change selects nothing for costs its full
  run in every such instance, and a kind of change cannot bring its own delivery; the repository's delivery actions read
  the recipe and its inputs (point 7) and decide in the repository's own declarations.
- **3.** A command does deterministic local
  work, such as running the stack's generator (Context). A template writes a file that every instance adds,
  deterministically from the inputs. An attempt gives judgement to a worker, under the contract that already bounds
  every worker started unattended ([0011](0011-safety-invariants.md)). A handoff is for what needs a person, such as
  checking SQL against production data volumes (Context); the person, or the worker in their session, continues the run.
  A closed set means each guard is proven once per kind and no engine code enters the repository; anything else a step
  must do is a command. A template never writes over a path
  that exists, so a run never replaces a file someone keeps. An attempt's edits stay
  for the next steps because a change is made in steps, and verification and completion belong to the whole change, not
  to one step of it. A failed command or attempt stops the run because the next step would build on a broken result. A
  handoff pauses instead of failing because a person is expected there. The caller declares who drives the run because
  a worker in a person's session runs commands with no terminal, as a loop runner does (Context), so only the caller
  knows whether anyone will continue after a handoff. A run whose caller declares nothing pauses there, so a loop runner
  that omits the declaration gets a task that never completes rather than one completed without the person the handoff
  asks for. The trade: work that fits none of the four kinds is a command the recipe's author writes, and every caller
  that drives runs with nobody watching has to declare it.
- **4.** Validating every input before the first step means a run never stops halfway on an input it could have refused
  at the start. Substituting an input into whole elements of an argument list run with no shell passes it to a generator
  as the argument the generator expects, and it cannot add a command (Context); the environment values serve commands
  that read them instead. In a prompt or a handoff, an input is text a worker or a person reads, the injection surface
  of an attempt action's prompt ([0019](0019-action-kinds.md)), and the attempt contract bounds what a misled worker can
  do ([0011](0011-safety-invariants.md)). Refusing any input that begins with a hyphen means no input can be read as an
  option by the program it reaches (Context). The trade: a free-text value cannot start with a hyphen.
- **5.** A recipe written for one repository reaches others through a cookbook or a station (point 8), and their
  contracts, workers and lanes differ. Checking its requirements when it is added shows the gap in the review of the
  change that adds it; checking again before each run catches a requirement the repository has lost since, before any
  step changes the working copy. Naming the missing piece is what [0023](0023-core-and-contracts.md) does for every use
  case that needs something the repository lacks.
- **6.** A recipe is a file of the repository, which a worker in an attempt can edit (Context). Rules about what such a
  file may change in the guards, and about where a run reads it from, are never a legitimate choice, so they are
  invariants, out of the reach of the repository's data ([0011](0011-safety-invariants.md)).
- **7.** A stop in a run nobody watches has to reach a person, and the blocked event is how every stop does
  ([0015](0015-development-lifecycle.md)). Keeping the working copy keeps the steps already done, and running again only
  the step that stopped avoids repeating them, which matters when an earlier step was an attempt whose result differs on
  each run ([0019](0019-action-kinds.md)). For the same reason a failed attempt step keeps its changes instead of
  returning to the base: the stopped run is the retry boundary, a person decides what to keep, and the run's
  verification and the replay from the base still stand between those changes and completion
  ([0011](0011-safety-invariants.md)). The record is what resuming reads, and what a person reads to see where a run
  stands. It holds the recipe's content, not only its name, so a continued run follows the recipe it started with even
  when the file changed while the run was stopped ([0011](0011-safety-invariants.md)). The proof carries the recipe and
  its inputs because delivery decides from the proof (point 2), and the hash ties the delivered change to the exact
  recipe that made it. The trade: a stopped run holds its working copy until a person continues it or removes it.
- **8.** A recipe is a practice a team adapts to its repository: its paths, its lanes, its prompts (Context). As a copy,
  it acts as the bytes every reader of the repository sees, like the repository's other files
  ([0013](0013-organization-content.md)), and the team adapts it without publishing a cookbook of its own, the model
  shadcn/ui follows for components (Context). Recording the origin in the copy, as Copier records its template
  (Context), gives an update the version it needs to tell the team's changes from the cookbook's. A station that relies
  on a recipe, such as one whose loop runner runs a recipe for each task, adds it in the same change, so its entries
  never name a recipe the repository lacks. It pins each recipe by content hash, as it pins its ingredients, so the
  recipe it copies is the one its own version was published with, verified as [0026](0026-cookbook-trust.md) decides.
  The trade: a cookbook's fix to a recipe reaches a repository only when someone there updates the copy.
- **9.** A loop runner runs one command for each task, and through the attempt command an attempt fires nothing in a
  working copy bound to no task (Context). Through the same caller interface, a recipe run gives each task its own
  working copy bound to it, its lanes, its events and its stop, so the work around the attempt is the lifecycle's
  instead of the caller's scripting. The attempt command stays the primitive, for scripts that need only an attempt and
  for the attempt steps themselves.
- **10.** Every capability of the binary serves a stage of the lifecycle ([0015](0015-development-lifecycle.md)).
  Watching a running system has no change to start, verify, complete or deliver, so as a recipe it would be a run whose
  stages are empty and whose proof proves nothing. Acting on a finding is a change like any other, and as a recipe it
  gets the guards every change has. The trade: monitoring itself stays with workers and their skills, outside the tool.

## Rejected alternatives

### Recipes pinned and read at every run

The repository pins each recipe by cookbook, version and content hash, as it pins an ingredient, and runs it from the
verified cache. At its best, a fix to a recipe reaches a repository with one move of its pin, and no merge is ever
needed. It loses because a recipe is a practice a team adapts (Context): bytes fixed by a hash cannot be edited, so
adapting one means publishing a cookbook of one's own, or override rules a person must learn: the inheritance that
[0013](0013-organization-content.md) and [0024](0024-ingredients-and-stations.md) reject for shared entries, and that a
shareable configuration loaded on every run brings (Context). The steps a run follows would also sit in a per-user
cache, not among the files a reviewer and a worker read ([0013](0013-organization-content.md)). Each repository still
reviews every move of the pin, so only the merge is saved.

### Recipes as task-runner scripts

Each kind of change is a script in the repository's task runner that calls the generator, starts a worker and runs the
checks. At its best, there is no new format, teams already know their task runner, and the script runs without the tool.
It loses because a script is engine code in the repository: it can skip a lane,
commit unverified work or fire events, and the binary cannot tell which line serves which stage, so it can require no
lane, keep no record to resume from and fire no blocked event at the step that stopped. Copied between repositories,
each script drifts into its own dialect, as copied workflows do ([0015](0015-development-lifecycle.md)).

### Recipes as skills only

A skill describes how to make each kind of change, and a coding agent follows it. At its best, every step gets
judgement, nothing new is needed, and the guidance works in any session. It loses because nothing fixes what the agent
does (Context): two runs make two different changes, nothing records which steps ran or makes the kind of change's lanes
run, a person without a coding agent has no path, and a stop in an unattended run reaches nobody. Skills keep their
place inside a recipe: an attempt step's worker can follow one, while the recipe holds the steps, lanes and guards
around it.

### An open set of step kinds, or plugin steps

Recipes or cookbooks define new step kinds, as code the binary loads or calls. At its best, any step a team needs, such
as taking a database snapshot or calling a service, is a first-class step with its own options. It loses because a step
kind is code, so it is engine code in the repository or a cookbook that may need a
runtime, and an open set has no rule set that covers every kind, which
[0015](0015-development-lifecycle.md) and [0019](0019-action-kinds.md) reject for extensions. A generator is such code
(Context); inside a recipe it runs as a command, with the guards around it.

### Setup as a recipe instead of a station

A way of working is a recipe whose steps write the entries, the templates and the pins, so a cookbook publishes one unit
fewer and setup goes through the lifecycle like any change. At its best, one format covers everything a cookbook
publishes, and setup gets a working copy, lanes and delivery. It loses because a run needs what setup provides: its
working copy needs the version control contract and its attempts need a declared worker (Context), so a recipe that pins
and declares them cannot run until it has run. Setup also merges entries into files the repository already has, under
rules that only add and report differences ([0013](0013-organization-content.md)), which no step kind does, and it is
applied once, while a recipe is made many times. A station stays the unit that sets up, and point 8 lets it add recipes.

### Who drives a run told by the terminal

A run is taken to be driven by a person when a terminal is attached, and by nobody otherwise. At its best, no caller
has to declare anything, and a run started from a terminal pauses at a handoff while one started by a loop runner stops.
It loses because a worker in a person's session runs commands with no terminal (Context): every run a coordinating
worker starts for a person would stop at its first handoff, as if nobody were there, and report a stop to the person
who is.

### The worker's verification as the run's proof

The run completes on the proof a worker's verification inside an attempt step left, checked for the required lanes. At
its best, the lanes run once, and the worker chooses the patterns that fit its change. It loses because that proof
covers only what the worker selected (Context), so a run whose worker selected nothing for a required lane would stop
for a person instead of checking it, and the proof of a recipe run would depend on what its worker chose to run. The
worker's verification keeps its use as feedback, and its selection is where the run's own verification starts.

### Recipes that declare their own lifecycle actions

A recipe carries actions for its events, such as a delivery that opens a pull request labelled for a database
administrator's review. At its best, a kind of change brings its whole path out of the clone with it. It loses because
what an event does would then depend on which recipe made the change, so a reader of the repository's declared actions
could not tell what delivery does, and a recipe from a cookbook would bring another organization's delivery conventions
into the repository ([0013](0013-organization-content.md)). Delivery actions receive the recipe and its inputs from the
proof (point 7), so a repository can still deliver a migration differently, in its own declarations.

## Consequences

Costs:

- A recipe is one more format with a schema to version, and a step that fits none
  of the four kinds becomes a command.
- A cookbook's improvement to a recipe reaches a repository only when someone updates the copy, and an update can leave
  conflicts for a person or a worker to resolve ([0013](0013-organization-content.md)).
- No input, free text included, can start with a hyphen, and an input in a prompt can mislead the worker, which the
  attempt contract bounds.
- A failed attempt step's edits stay in the working copy until a person inspects or discards them.
- A run a worker drives cannot pass a handoff, so a recipe meant for a loop runner holds none, and a caller that drives
  runs with nobody watching must declare it, or its runs pause at their first handoff.
- A change to a recipe reaches a run a worker drives only once it is on the shared default line of work, not merely
  committed in one clone, and only once the clone has learned it; a stopped run continues with the recipe it started
  with ([0011](0011-safety-invariants.md)).
- A required lane the change selects nothing for runs in full in every instance.
- A stopped run keeps its working copy until a person continues it or removes it.
- A kind of change cannot declare its own delivery; a repository that delivers some kinds of change differently says so
  in its own actions.
- Monitoring, and any other work that changes nothing in the repository, stays outside the tool.

Must be maintained:

- The recipe schema, the closed set of step kinds with their rules, and the run record with the recipe content it
  holds, as part of the public contract.
- The check of a recipe's requirements when it is added and before each run.
- The caller interface of a recipe run, beside the attempt command's ([0022](0022-tool-integration.md)).

## Confirmation

- A fixture run creates its working copy at the revision of the shared default line of work, runs its steps in order,
  and completes only after its own verification, whose proof records every lane the change selects and every lane the
  recipe requires; a required lane the fixture change selects nothing for runs in full, and the proof records it.
- A proof that a fixture worker's verification leaves inside an attempt step, without a required lane, does not stop
  the fixture run from running that lane itself.
- A fixture run whose recipe file changes after the run starts, between a stop and its continuation, follows the recipe
  content its record holds.
- A fixture run whose caller declares nothing pauses at a handoff, fires nothing and prints no completion signal; the
  same run declared as driven by a worker stops there and fires the blocked event.
- A fixture command step whose input holds shell metacharacters receives the input as one argument, and nothing in it
  executes.
- A fixture template step whose path exists stops the run and leaves the file byte-identical.
- A fixture attempt step starts its stand-in worker under the attempt contract, leaves its edits for the next step, and
  fires no event.
- A fixture run whose step fails fires the blocked event naming the step, keeps its working copy, and runs that step
  again when continued; a failed attempt step's changes are still in the working copy when it does.
- A fixture recipe that requires a contract, a role or a lane the fixture lacks is refused when it is added and before a
  run, naming what is missing, and no step runs.
- Validation refuses a fixture step of a kind outside the four, a fixture input that does not match its type, and a
  fixture input of any type that begins with a hyphen, before any step runs.
- The proof of a fixture run records the recipe, its hash and its inputs, and a fixture delivery action receives the
  recipe and the inputs.
- A fixture loop runner that runs a recipe for each task gets one working copy bound to each task, and its completion
  signal only for a run that completed.
