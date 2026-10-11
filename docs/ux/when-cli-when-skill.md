# Where new behaviour goes

## Objective

**Problem.** Contributors proposing a new capability, reviewers judging the proposal, and coding agents advising a
repository all have to decide where the capability belongs: in the tool's code, in a repository's own files, or in
guidance a coding agent follows. Decided case by case, the same capability is built in two places that drift apart,
judgement ends up in code that guesses wrong in the first repository nobody foresaw, and a rule that must hold is left
to prose a coding agent can skip. A reviewer then cannot tell that a proposal is in the wrong place until it fails.

**Goal.** Two people placing the same capability independently choose the same home for it. Work with one right answer
comes out the same, and checkable, in every repository, and work that needs judgement stays with the people and coding
agents who can exercise it.

## Overview

These rules choose where each capability belongs ([ADR 0002](../adrs/0002-division-of-work.md)). They apply to proposals
for `the-cook` itself and to the advice `the-cook` gives repositories through its skill. A capability is placed whole,
or split when it has both a deterministic half and a judgement half ([below](#when-behaviour-splits)).

## Destinations

| Destination | What it is | Who maintains it |
| --- | --- | --- |
| **Built-in invariant** | a rule about the process, compiled into the binary and about no particular program, that no file or flag can remove | `the-cook` |
| **Floor rule of an ingredient** | a deny rule or bypass argument an ingredient marks as floor: rendered on every render, never in the policy and out of its reach ([ingredients](../features/ingredients.md#the-floor)) | the maintainers of the ingredient's cookbook; the repository chooses it by pinning the ingredient |
| **New command** | a new verb or noun-verb in the binary | `the-cook` |
| **Flag on an existing command** | a variation of an operation the binary already performs | `the-cook` |
| **Profile field** | a per-repository value that parametrises a deterministic binary behaviour (`config.yaml`; permissions in `policy.yaml`, hooks in `hooks.yaml`, the pinned version in `wrapper.yaml`) | the repository |
| **Declared action** | an action on a lifecycle event (`lifecycle:`) or a worker's notification event (`attention:`, `turn_end:`) in `.cook/hooks.yaml`, of kind `run` (a shell command), `http` (a request `the-cook` performs) or `attempt` (a worker started unattended on a prompt) | the repository |
| **Standing-context entry** | a declared trigger (`always`, `when.paths`, `when.prompt`) that puts an existing skill or repository file in front of a worker, under `context:` in `config.yaml` | the repository |
| **shipped skill or explain topic** | guidance a coding agent follows to do judgement work around `the-cook` | `the-cook` (embedded, shipped with the binary) |
| **Ingredient** | a named, versioned description of one program ([ingredients](../features/ingredients.md)): renders and floor written on every render, operations that fill the version-control, worker or jail contract, and checks. Ingredients describe the version control system, a worker such as a coding agent, a jail that confines a worker's attempts, a loop runner, a task tracker, a task runner or a skills installer | the maintainers of the cookbook that publishes it; the repository pins it |
| **Station** | what one way of working needs in place ([stations](../features/stations.md)): the ingredients it pins, the stages they fill, entries for the repository's files, templates and the recipes it adds, which `the-cook scaffold` and `the-cook station apply` write once, on request. `agentic-coding` sets up the lifecycle with a coding agent in sessions and in attempts, a loop runner and a tracker, and adds the `implement-task` recipe; a stack station (`go`, `node`, `terraform`, `python`) holds the starting commands, test lanes, validators, module switches and post-edit hooks of one stack | the maintainers of the cookbook that publishes it; once applied, its pins, entries, files and recipes are the repository's |
| **Recipe** | one kind of change the repository makes many times, in `.cook/recipes/<name>/` ([recipes](../features/recipes.md)): inputs, requirements, steps mapped onto the lifecycle's stages and required lanes, run by `the-cook make`. Its `run` and `template` steps do the part with one right answer, its `attempt` steps and handoffs carry the judgement, and the guidance for that judgement stays in a skill a step names | the repository; a recipe added from a cookbook, or by a station, is the repository's own copy, with its `origin` recorded |
| **Organization or personal skill** | guidance a coding agent follows for judgement work specific to an organization's conventions, including writing the organization's choices (policy entries, actions, standing-context entries) into a repository's files | the organization; the entries it leads to are the repository's |
| **Left to the repository** | a task in the repository's own task runner (a task-runner task, a `Makefile` target), a repository skill, prose in `AGENTS.md`, memory | the repository |

## Criteria

| Criterion | Question |
| --- | --- |
| Determinism | Given the same inputs, is there exactly one correct output? |
| Repetition | Is it needed, in the same form, in many unrelated repositories? |
| Safety relevance | Would getting it wrong, or switching it off, let a worker escape its permissions or claim unverified work? |
| Organization specificity | Is it true only for one organization's conventions, tools or vendors? |
| Judgement | Does it require reading prose, weighing options, or writing text? |
| Reach | Does it change something outside the clone (a push, a pull request, a CI trigger, a notification, a call to a service), or react to something that happened there (a merge)? |
| Lifecycle point | Does it belong at a point of the development lifecycle (`start`, `verified`, `completed`, `deliver`, `landed`), at the moment work stops for a person (`blocked`), or at a worker event (`attention`, `turn_end`)? |
| Guidance timing | Is it known guidance that applies at a predictable moment: every session, when certain files are touched, or when certain words appear? |
| Model or tool server | Does it need a model, or a tool that only an MCP server provides? |
| Program | Does it exist to describe or connect one program: a version control system, a worker, a loop runner, a task tracker, a skills installer, a task runner or a hosting platform? |
| Kind of change | Is it one kind of change a repository makes many times, with the same shape each time, that should arrive verified like any other work? |

## Decision table

| Determinism | Repetition | Safety relevant | Org specific | Judgement | Destination | Because |
| --- | --- | --- | --- | --- | --- | --- |
| yes | yes | yes | no | no | built-in invariant | a rule every repository needs and none may drop cannot be a setting, because a setting gets lost when a repository's configuration is copied or edited |
| yes | wherever one program is used | yes | no | no | floor rule of that program's ingredient | a fact about one program, such as its bypass arguments, changes with the program, so it lives in the program's ingredient; marking it floor keeps any repository from dropping it |
| yes | yes | no | no | no | command, flag, or profile field (see below) | one implementation serves every repository and is fixed in one release |
| yes | in every repository of one stack | no | no | no | stack station, applied with `the-cook station apply` | stack facts are the same in every repository on that stack, so a cookbook supplies them once for all of them; applying on request keeps every write something a person asked for |
| yes | any | no | yes | in applying it | entries in each repository's files, written by a coding agent following an organization skill | organization content stays out of an open-source binary; it differs between an organization's repositories, so applying it takes judgement, and each repository reviews the result as a diff |
| yes | any | no | yes, or one repository | no | declared action, when it reaches outside the clone at a lifecycle or worker event ([below](#declared-actions)) | side effects outside the clone must be declared, reviewed and guarded |
| yes | no | no | no | no | left to the repository (a task in its own task runner, or a profile value) | one repository's need does not justify a contract every user carries |
| no | yes | any | no | yes | shipped skill, with a binary check that proves the outcome | judgement belongs to people and coding agents, and the check keeps it honest |
| no | any | any | yes | yes | organization skill | the organization owns its own judgement calls |
| no | no | any | no | yes | repository skill or prose | knowledge is reviewed where it applies |

When guidance already exists as a skill or a file and the only question is when a worker should read it, the
destination is a standing-context entry, whatever the other columns say ([below](#standing-context)). Why: the timing
is deterministic even when the guidance is not.

When the capability is one kind of change a repository makes many times, with the same shape each time, the
destination is a recipe, whatever the other columns say ([below](#recipes)). Why: the columns sort the parts of a
change, and a recipe is where the parts of one repeated change meet: what has one right answer as steps the binary
runs, the judgement as steps a worker or a person takes, and the proof as lanes Verify requires.

Support for any program, the version control system and the workers included, is an ingredient or a declared action,
whatever the other columns say, and never a command, a flag, a profile field or code in the binary; a way of running
the lifecycle with several programs is a station built from their ingredients. The program calls `the-cook` through its
commands, such as [`the-cook attempt`](cli-standards.md#the-attempt-command) as the command a loop runner runs for each
task, and `the-cook` reaches the program through the operations of its ingredient and the commands the repository
declares. Why: each team picks different programs and each program follows its own release schedule, so code in the
binary for one of them breaks with its releases and serves no team that chose another, while an ingredient or an action
is data a repository can replace ([ADR 0022](../adrs/0022-tool-integration.md),
[ADR 0023](../adrs/0023-core-and-contracts.md)).

A safety-relevant rule that is organization specific (a deploy deny, a protected branch other than the default) is an
entry in each repository's `policy.yaml`, written there by a person or by a coding agent following the organization's
skill. The rendered policy enforces it like any other entry. It is neither a built-in invariant nor a floor rule of an
ingredient: the binary holds no organization-specific content, and an ingredient's floor holds only what is unsafe in
every repository that uses its program.

## Declared actions

A behaviour is a declared action when all of these hold:

1. It runs without judgement at the moment it fires, or its judgement is delegated to an `attempt` action whose result
   a person reviews.
2. It reaches outside the clone, or reacts to something that happened outside it, or tells a person something.
3. It is organization or repository specific: it names a hosting platform, a CI convention, a notification channel
   or a cleanup habit that not every repository shares.
4. It is triggered at a lifecycle point or a worker event, by a person, a
   [coordinating worker](../features/lifecycle.md#the-lifecycle-stage-by-stage) or automatically (`auto: true`).

Why: these are the side effects whose form differs per team, so the team declares them and `the-cook` only fires them
under its guards ([ADR 0015](../adrs/0015-development-lifecycle.md), [ADR 0019](../adrs/0019-action-kinds.md)).

A declared action is always declared in the repository's `hooks.yaml`. An action that holds for an organization's
repositories (open a pull request on a given hosting platform, add the organization's CI-trigger commit, post to the
organization's chat) is written into each repository's `hooks.yaml` by a person or by a coding agent following the
organization's skill, and reviewed there as a diff. Why: an action runs in its repository with the credentials of
whoever fires it, so it must be readable and reviewed where it runs ([ADR 0013](../adrs/0013-organization-content.md)).

A declared action never lives in the binary. The binary contains no logic for the platform that hosts the team's work,
and no action runs that the repository's files do not declare ([ADR 0012](../adrs/0012-write-boundary.md)). A command or
flag that pushes, opens a pull request or calls a hosting platform is a design error, whatever the convenience
([principles.md](principles.md#7-nothing-leaves-the-clone-undeclared)).

### Choosing the kind

| Need | Kind | Because |
| --- | --- | --- |
| Run a CLI the repository already uses (`git`, the hosting platform's CLI) | `run` | the CLI carries its own authentication and conventions |
| Notify a person or a service: a chat message, an incident, a status page | `http` | `the-cook` performs the request itself, reads the URL and headers from environment variables and JSON-encodes values, so no `curl`, no shell quoting and no secret on a command line |
| Tell a person the worker is waiting for them, or has finished a turn | `http` (or `run` for a local notifier) on `attention` or `turn_end` | notification events accept `run` and `http` only, so a worker's hook never starts another worker |
| Tell a person an unattended run stopped or a task was skipped | `http` on `blocked` | `blocked` fires when a command without a terminal exits 3 for a person, when a recipe run stops, and when whoever drives a loop runner's run fires it as that run stops, with the reason in `COOK_REASON` |
| Judgement after an event: summarise a merged change, write lessons into memory, triage a failure | `attempt` | the worker does the judgement under the [attempt contract](../features/attempts.md#the-attempt-contract); its edits stay uncommitted for review |
| Use a tool only an MCP server provides | `attempt`, or leave it to the coordinating worker | `the-cook` is never an MCP client; the worker owns the connection, its credentials and its permissions |

When the behaviour needs judgement before the side effect (which commits belong together, how to word them), split it: a
coding agent guided by a skill does the judgement, then runs `the-cook lifecycle run <event>`, so the side effect stays
declared, visible and recorded.

## Standing context

Use a standing-context entry when guidance that already exists must reach a worker at a predictable moment, instead
of relying on someone to invoke it ([ADR 0020](../adrs/0020-standing-context.md)).

| The guidance | Destination | Because |
| --- | --- | --- |
| applies to every session (house rules, coding guidelines) | `context.always`, injected as content | the worker sees it before its first action, and again after compaction |
| must hold even on a machine without `the-cook` | `context.always` with `static: true` | it is written into `AGENTS.md`, which coding agents read without the binary |
| applies only to attempts (or only to interactive sessions) | the entry with `for: [unattended]` (or `[interactive]`) | each role pays only for what applies to it |
| applies when certain files are touched (SQL, migrations, a payment module) | `context.when.paths` | the trigger is the file, not the person's memory |
| applies when certain words appear in the person's message (time zones, a domain term) | `context.when.prompt`, usually `inject: pointer` | a one-line pointer costs one line; the worker reads the skill only when it applies |
| applies only when the coding agent judges it relevant | a plain skill, loaded by its description | no deterministic trigger exists, so the worker's own matching is the right mechanism |

Always-on content has a budget per role (`context.budget`), because every always-on line is paid in every session and
dilutes attention. A long directive that applies to few tasks is a `when` entry or a plain skill, not an `always`
entry.

## Recipes

Use a recipe when a repository makes the same kind of change many times and each one should arrive verified, like any
other work ([recipes](../features/recipes.md), [ADR 0025](../adrs/0025-recipes.md)). A recipe run is one pass of one
change through the lifecycle's stages: Start creates its workspace, its steps run at Implement, Verify runs every lane
the change selects and every lane the recipe requires, and the repository's own actions deliver it. Within the recipe,
each part of the change has one place:

| Part of the change | Goes in | Because |
| --- | --- | --- |
| what has one right answer: running a generator, writing a file from a template | a `run` or `template` step | the binary does it the same way on every run, before any judgement |
| what needs judgement: writing the logic, naming, fitting the change to the code around it | an `attempt` step | a worker does it under the [attempt contract](../features/attempts.md#the-attempt-contract), and the lanes check it |
| what a person must check or decide before the run goes on | a handoff | it pauses an attended run for whoever drives it, and stops a run its caller marks unattended as blocked, because it needs a person |
| how to do the judgement well: the team's conventions for that kind of change | a skill the `attempt` step's prompt names, or a standing-context entry that matches the files the step touches | the guidance keeps one text, which reaches a worker in any recipe or session |
| what proves the change | `verify.require` | Verify runs those lanes on every run, in full when the change selects nothing for one, and never skips them |
| what happens to the result: a push, a pull request, a notification | the repository's own lifecycle actions | a recipe declares none, so delivery means the same for a recipe run as for any other work |

These are not recipes:

- Work that changes nothing in the repository, such as watching a running system: it has no workspace, receipt or
  delivery. Watching is a coding agent's job, following a monitoring skill. Acting on what it found is a recipe: the
  alert is an input, and the worker of an `attempt` step follows the monitoring skill.
- A generator whose output needs no verification or delivery: a task in the repository's own task runner. When its
  output should arrive verified, the generator becomes the `run` step of a recipe.
- A change made once: a person or a coordinating worker in a managed workspace, because a recipe pays for itself only
  over many runs.
- Setting up a way of working: a station, applied once, because its result is the repository's files, not a change
  made again and again.

## Choosing among command, flag and profile field

For deterministic, repeated, organization-neutral behaviour:

1. **Profile field** when repositories need different values for the same behaviour (the command of a test lane, the
   lint command, the bootstrap commands). The default MUST preserve existing behaviour, so adding the field changes
   nothing for repositories that do not set it.
2. **Flag** when the same repository needs the behaviour sometimes, per invocation (`verify --full`,
   `verify --base <ref>`, `workspace create --no-bootstrap`). A flag that every invocation in a repository would pass is
   a profile field instead, because a flag repeated on every call is a setting written in the wrong place.
3. **New command** when the operation has a different object or a different effect from every existing command. A
   behaviour that changes what an existing command writes is a flag or a field, not a new command, because two
   commands writing the same files compete.

None of the three serves a single program: that is an ingredient or a declared action ([above](#decision-table)). None
holds one kind of change a repository makes many times either: that is a recipe ([above](#recipes)).

## When behaviour splits

Most capabilities have a deterministic half and a judgement half. Split them, because each half needs a different kind
of owner:

- The binary does the deterministic half and exposes a check.
- The skill tells the coding agent how to do the judgement half and ends with the command that checks it.
- The binary never performs the judgement half by heuristics; the skill never re-implements the deterministic half
  in prose.

Adoption, taking over a repository's existing hand-written worker setup, is the pattern to follow.
`the-cook scaffold --adopt` imports what it can map and lists the rest as a to-do, the shipped skill guides a coding
agent through the to-do, and `the-cook doctor` confirms the result.

Repository content follows the same split ([ADR 0013](../adrs/0013-organization-content.md)):

- Stack facts need no judgement, so they are stack stations whose entries the binary writes on request
  (`the-cook scaffold --cookbook`, `the-cook station apply <name>`), adding only what the repository lacks.
- An organization's conventions differ between its repositories, so a coding agent following the organization's skill
  decides which entries a repository needs and writes them into its files. `the-cook render` renders them and
  `the-cook doctor` checks the result.
- Either way the entries end in the repository's own files and reach review as a diff. The binary never reads an
  organization's conventions from anywhere else.

A recipe makes the same split inside one repeated change: the binary runs its `run` and `template` steps, a worker in
an `attempt` step or a person at a handoff does the judgement, guided by a skill the step names, and Verify checks the
result with the lanes the recipe requires.

## Worked examples

| Request | Analysis | Destination |
| --- | --- | --- |
| Render permissions for every worker from one policy | deterministic, every repository, safety relevant | binary: `the-cook render`, through the renders of each worker's ingredient |
| Deny force pushes to the default branch | deterministic, every repository that uses the version control system, safety relevant, a fact about that program | floor rule of the version-control ingredient |
| Deny chaining a second task-runner task into one invocation | deterministic, every repository that uses the task runner, safety relevant, a fact about that program | floor rule of the task runner's ingredient |
| Allow the task runner's `deploy:status` task in one repository | deterministic, one repository | `policy.yaml` in that repository |
| Read-only patterns for an organization's observability MCP server | deterministic entries, organization specific, applied with judgement per repository | `shared.allow` entries in each repository's `policy.yaml`, written by a coding agent following the organization's skill |
| Open a pull request when work is concluded | deterministic command, leaves the clone, hosting-platform specific, belongs at `deliver` | a `deliver` action of kind `run` in `hooks.yaml` (push, then the hosting platform's pull-request command) |
| Add a CI trigger commit before the pull request | deterministic, leaves the clone with the push, organization convention, belongs at `deliver` | a `deliver` action in each repository's `hooks.yaml`, declared before the pull-request action and written there by a coding agent following the organization's skill |
| Set up agentic coding: a coding agent in sessions and in attempts, a loop runner and a tracker | what a way of working with several programs needs in place, deterministic wiring | station `agentic-coding`: `the-cook station apply agentic-coding --cookbook github:acme/cookbook@v1.2.0` pins its ingredients, writes its workers, lanes, unattended denies, lifecycle actions and the loop runner's configuration, and adds the `implement-task` recipe |
| Add a database migration the same way every time | one kind of change, made many times: a generator (one right answer), the up and down steps (judgement), a check against production data volumes (a person's), lanes `unit` and `migrations` | the repository's own recipe `migration`, started with `the-cook recipe create migration`: a `run` step for the generator, a `template` step for the down test, an `attempt` step for the up and down steps, a handoff for the check, and `verify.require: [unit, migrations]`; each migration is `./cook make migration --name add-invoice-index` |
| Add a NestJS module the way the organization's cookbook describes | one kind of change, the same in each of the organization's NestJS repositories | recipe `nestjs-module`, added with `the-cook recipe add nestjs-module --cookbook github:acme/cookbook@v1.2.0`, which copies it into `.cook/recipes/nestjs-module/` with its `origin`; the repository edits its copy, and `the-cook recipe update nestjs-module --cookbook github:acme/cookbook@v1.3.0` merges a newer version into it |
| Implement tracker tasks with a loop runner, each verified and completed | one kind of change per task, driven by a third-party loop runner | the `implement-task` recipe the `agentic-coding` station adds, which the loop runner's configuration names as `./cook make implement-task --task <id> --unattended --commit`: each task gets a workspace of its own, its required lanes and one verified commit |
| Watch production for errors | changes nothing in the repository: no workspace, receipt or delivery | not a recipe: a coding agent following a monitoring skill |
| Fix what a production alert points at | one kind of change, with the alert as its input | a recipe with the alert as an input and an `attempt` step whose worker follows the monitoring skill |
| Give a Node repository its lint, build and test commands and a lint-on-edit hook | deterministic, the same in every Node repository | stack station `node`: applied by `the-cook scaffold --cookbook <source>@<version>` when its detect rule finds `package.json`, or by `the-cook station apply node --cookbook <source>@<version>` |
| Add a stack's entries to a repository that took it up after adoption | deterministic, stack facts | `the-cook station apply <name> --cookbook <source>@<version>`; the additions are shown as a diff and confirmed |
| Take a newer version's additions to a station | deterministic, on request | `./cook station apply node --cookbook github:acme/cookbook@v1.3.0 --dry-run` shows them, and the same command without `--dry-run` writes them; across repositories, `the-cook fleet station apply node --cookbook github:acme/cookbook@v1.3.0 --yes --deliver` |
| Move to a newer description of a program, such as a version-control ingredient for a new release of the program | deterministic, on request, changes rendered output | `the-cook ingredient pin git --cookbook github:acme/cookbook@v1.3.0`: the pin and its rendered effect in one diff |
| Apply an organization's conventions to many repositories | judgement per repository, organization specific | a coding agent following the organization's skill in each repository, reviewed as a diff in each; `the-cook fleet` runs `the-cook` commands, never a worker |
| Post to the team chat when a task completes | deterministic, leaves the clone, a notification channel | a `completed` action of kind `http` with `url_env`, `auto: true` |
| Ping me when the coding agent is waiting for my answer | deterministic, a notification, per person or repository | an `attention` action of kind `http` (or `run` for a desktop notifier) in `hooks.yaml` |
| Tell me when an unattended [run of a loop runner](../features/attempts.md) stops unfinished | deterministic, a notification at the moment work stops; nothing in the binary knows when a run ends | a `blocked` action of kind `http` with scope `run`, fired by whoever drives the run: `./cook lifecycle run blocked --scope run` |
| Write lessons into memory after a change lands | judgement, after an event | a `landed` action of kind `attempt`; it runs in a fresh workspace at the revision of the shared default line of work, and its edits stay uncommitted there for review |
| Look something up through an MCP-only service before delivery | needs an MCP tool | the coordinating worker before it runs `the-cook lifecycle run deliver`, or an `attempt` action; never the binary |
| Choose which commits of an unattended run go into which pull request and reword them | judgement (grouping, wording), conventions vary by organization | a coding agent guided by an organization or personal skill, which then runs `the-cook lifecycle run deliver` |
| Remove the workspace after its branch is merged | deterministic, reacts to a merge, every repository | binary: `the-cook doctor` lists the landed branch and prints the command; `the-cook workspace remove` fires `landed`, then removes the workspace |
| Detect a merge made on the hosting platform when the workspace is removed | deterministic, reaches beyond the clone, the repository's practice | profile field `workspaces.update_before_landed: true`, which a station may seed: `the-cook workspace remove` then runs the version-control contract's `update` before it detects landed work; nothing else in the binary learns what is shared on its own |
| Let a worker's attempts reach the network | the repository's practice; the arguments that carry it are a fact about the program | profile field `workers.<name>.network: true`, which the worker ingredient's `launch.network` maps to the program's arguments |
| Delete the remote branch once the work has landed | deterministic, leaves the clone, repository habit, belongs at `landed` | a `landed` action in `hooks.yaml` (a push that deletes `$COOK_BRANCH` on the remote), `auto: true` if it should run whenever `workspace remove` handles a landed branch |
| Apply the team's coding guidelines in every session without invoking them | known guidance, every session | `context.always` entry naming the skill |
| Apply the SQL review skill whenever SQL or migrations are touched | known guidance, triggered by files | `context.when.paths` entry |
| Point the coding agent at the time-zone skill when the request mentions time zones | known guidance, triggered by words | `context.when.prompt` entry with `inject: pointer` |
| Keep a must-follow directive in force even where `the-cook` is not installed | known guidance, must hold without the binary | `context.always` entry with `static: true` |
| Pick which tests to run for a change | judgement (which code is affected) | a coding agent; the repository declares lanes and `required_when` globs in the profile; `the-cook verify` records the choice and replays it |
| Lint a file after every edit | deterministic, per-repository command | `hooks.yaml` `post_edit` entry |
| Support a new worker: a coding agent, a bot or a codemod | deterministic translation of the same policy and hooks | a worker ingredient that fills the [worker contract](../features/ingredients.md#the-worker-contract), published in a cookbook and pinned by the repository; never code in the binary |
| Use a different version control system | the answers every attempt relies on: base revision, files at a revision, reset, workspaces | an ingredient that fills the [version-control contract](../features/ingredients.md#the-version-control-contract), named by `vcs:` in the profile |
| Finish an adoption: move a hook the importer could not model, review the rules imported from the `local` scope | judgement | shipped skill, checked by `the-cook doctor` |
| Summarise the effective permissions in `AGENTS.md` | deterministic, derived from the policy | binary: the `policy` [convention block](generated-content-standards.md#block-classes) |
| Tell people and workers where workspaces are created and on which branch | deterministic, derived from the profile, must hold without `the-cook` | binary: the `workspaces` convention block in `AGENTS.md`, and the version-control ingredient's `workspaces` block in its ignore file ([principles.md](principles.md#8-conventions-outlive-the-tool)) |
| Keep workspaces and state somewhere other than `.work/` | deterministic, different values per repository | profile fields `state.dir`, `workspaces.dir`; `the-cook render` rewrites the blocks that state them |
| Keep `the-cook` configuration beside the skills in `.agents/` | `.agents/` is the cross-tool directory of the content workers read | not allowed: `the-cook` configuration lives in `.cook/`, state at the profile's paths |
| Explain in `AGENTS.md` why the team keeps e2e tests mandatory for routes | judgement, one repository | repository prose, outside [managed blocks](generated-content-standards.md#managed-blocks) |
| Record a lesson learned during a session | judgement | a coding agent, in the repository's memory; never the binary |
| Open an issue for each to-do item | judgement, writes to the record of work | a coding agent with its own tools; the binary never writes issues |
| Report workspaces whose branch was merged | deterministic, every repository | a `doctor` check |
| Install an organization's shared skills in a repository | a third-party installer, organization data | the team's own step with the skills installer of its choice, then a commit of `.agents/skills/` and the installer's lock file; an ingredient for the installer describes it, and standing context names the skills it injects |
| Run a loop runner over a task list with every result verified | a third-party loop runner, deterministic wiring | the loop runner's ingredient and a station that uses it, such as `agentic-coding`, whose template names the command the loop runner runs for each task: `./cook make <recipe> --task <id> --unattended --commit` for a recipe run, or `./cook attempt <worker> --commit`, the primitive, for one attempt; the person runs the loop runner in a managed workspace, never through a `the-cook` command |
| Update a task tracker when work starts, completes or stops | a third-party tracker, deterministic, at lifecycle points | the tracker's ingredient, and the lifecycle actions a station such as `agentic-coding` applies: actions on `start`, `completed` and `blocked`, scope `task`, `auto: true`, which run the tracker's commands with `$COOK_TASK` |
| Support another loop runner or task tracker | a third-party program | an ingredient pinned from a cookbook, or declared actions and a configuration that names `the-cook attempt`; never a command, flag or profile field |
| Pin the version of `the-cook` a repository runs | deterministic, every repository, affects what every machine runs | binary: `.cook/wrapper.yaml`, the only pin, and the committed wrapper `cook`; `the-cook wrapper --version <tag>` changes the pin after verifying the release's signed checksum file |
| Deliver an upgrade of `the-cook` to every repository | deterministic tool-owned changes, many repositories | `the-cook fleet wrapper --version <tag> --deliver`, then, once it lands, `the-cook fleet render --deliver`: fleet commits on a local branch and fires each repository's own `deliver` actions |
| Stop using `the-cook` but keep its conventions, permissions and hooks | deterministic, per repository | binary: `the-cook remove --eject` |
| Run `the-cook` from a worker's hook on a machine that has not downloaded it | implicit entry point | `./cook --no-download`: the hook skips with one line; it never downloads |

## Anti-patterns

Each of these breaks a principle, for the reason given after it.

- A command that writes free prose, memory or skill content: the binary does only deterministic work. Managed blocks
  hold only fixed convention statements derived from the repository files.
- A setting that turns off an invariant or a floor rule: once either is optional, it gets lost when a repository's
  configuration is copied or edited. For example, if `acme/web` copies its configuration from `acme/api`, where someone
  switched off the version-control ingredient's deny on force pushes to the default branch, `acme/web` allows force
  pushes without anyone there deciding to.
- A key that names a source outside the repository for entries `the-cook` renders or runs (an inherited file, an
  include, a remote set of entries), other than a release, an ingredient or a station pinned by hash: a rule whose
  source sits outside the repository cannot be read or reviewed where it acts
  ([ADR 0013](../adrs/0013-organization-content.md)).
- A default cookbook, or any ingredient or station, in the binary: the binary would choose for every repository which
  programs it describes and whose review it trusts, and every user would carry descriptions of programs they never use.
  Each repository pins its own.
- A station that renders or carries floor, or an ingredient with entries or templates: what acts on every render and
  what is applied once would share one pin, which would then mean two things at once, and a reader could not tell from
  the repository what still acts on it ([ADR 0024](../adrs/0024-ingredients-and-stations.md)).
- A station's entries applied by a command that was not asked to apply them (a render, an upgrade), or a record of which
  entries a station added: every write to a repository's files is one someone asked for, and a record would tie the
  repository's own entries to a definition outside its files.
- A recipe step that runs a shell string with an input substituted into it, such as
  `run: [sh, -c, "npm run migration:create {name}"]`: the input reaches a shell parser, so a `string` input such as
  `x; rm -rf src` adds a command, and what the step does depends on quoting nobody reviews. A step runs one program by
  its argument list. A step that needs a shell passes each input as a positional parameter, outside the string the
  shell parses (`run: [sh, -c, 'npm run migration:create -- "$1"', sh, "{name}"]`), and a longer pipeline is a script
  the repository commits, run as `run: [./scripts/new-migration.sh, "{name}"]`.
- A recipe that declares lifecycle actions, skips a lane or fires an event: a recipe only adds steps, lanes and
  requirements, so the guards and the meaning of delivery stay the same for every change, whoever wrote the recipe.
- A recipe read from its cookbook when it runs, instead of from the repository's copy: the steps that run would sit
  outside the repository, where nobody working there reads them, and a cookbook's new tag would change what runs
  without a diff.
- A recipe read again from the working tree while its run goes on: the worker of an `attempt` step could rewrite the
  steps after its own and the lanes that judge them, so a run reads its recipe once, when it starts.
- The guidance an `attempt` step needs written out in its prompt instead of in a skill the prompt names: the guidance
  then lives in every recipe that needs it and drifts, while a skill keeps one text for recipes and sessions alike.
- A step kind for one program or one organization, or a step kind a recipe defines: each kind is a process the binary
  runs under its guards, and what a program does is a `run` step that calls it.
- A recipe for work that changes nothing in the repository, such as watching a running system, or for setting up a way
  of working: the first has no workspace, receipt or delivery for a recipe run to carry, and the second is applied once,
  which is a station.
- A program's safety fact kept as a policy entry instead of a floor rule of its ingredient: an entry gets lost when the
  configuration is copied or edited.
- An ingredient fetched during a hook or an attempt: the attempt would run whatever the source serves at that moment,
  instead of the content the repository pinned.
- A heuristic in the binary that guesses where a person or a coding agent should decide: a wrong guess silently rewrites
  a team's configuration.
- A skill that lists the steps a command performs instead of calling the command: the description drifts from the
  command.
- An organization name, vendor or convention as a binary default: the binary is open source and serves every
  organization.
- A new command whose only difference from an existing one is a default value: one operation, one command.
- A command or flag that pushes, opens a pull request, or calls a hosting platform's API instead of a declared action:
  side effects outside the clone are declared, reviewed and guarded.
- A command, flag or profile field for one program, binary code that names a program, or binary code that reads a
  program's configuration: the binary would follow that program's releases and formats, and serve no team that chose
  another.
- `the-cook` acting as an MCP client, or calling a model: the worker owns those connections; `the-cook` sets the
  lifecycle's rules for workers and never acts as one.
- A webhook URL, token or header value written literally in `hooks.yaml`: committed files are readable by everyone
  with access, so a secret there is published.
- An `attempt` action on a notification event, or an `attempt` action that commits or pushes: workers do not start
  workers from their own hooks, and an attempt action's edits are reviewed before they leave the clone.
- A declared action that needs judgement to run correctly, as a `run` or `http` action: the judgement belongs to a
  coding agent before the action fires, or to an `attempt` action.
- Copying a skill's text into `AGENTS.md` to make sure the coding agent reads it: a `context.always` entry (with
  `static: true` where it must hold without the binary) keeps one source.
- A standing-context entry for guidance that applies to few tasks as `always`: every always-on line is paid in every
  session.
- An implicit entry point (a rendered hook) that downloads the binary: a hook must never stall or fail a session.
- `the-cook` configuration or state in `.agents/`, or state in `.cook/`: each directory has one owner.
- A convention that exists only in the configuration of `the-cook`, without a plain-language statement in `AGENTS.md`:
  it does not survive the tool.
- A convention block that names a `the-cook` command: eject keeps convention blocks, so the command does not exist after
  eject.
