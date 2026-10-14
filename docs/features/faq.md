# FAQ

## Objective

**Problem.** Someone evaluating the tool, adopting it or meeting it in a teammate's repository usually has a few short,
specific questions: whether teammates must install anything, whether it pushes code or writes to the hosting platform,
whether an unattended worker, such as a coding agent, can skip its permission checks, how to stop using it. The answers
exist, but spread across pages organised by feature rather than by question, so the reader has to read most of the
documentation to answer one, or guesses.

**Goal.** A reader with a common question finds a short, direct answer and the page that covers it in full, without
reading the feature pages first.

## Overview

Short answers to the questions people ask most often, grouped by subject. Each answer links the page that
covers it in full.

Commands on this page run as `./cook <command>`, through the repository's [wrapper](wrapper.md); with a global
install, `the-cook <command>` is equivalent.

## General

### Does `the-cook` call a model or host coding agents?

No. It runs the lifecycle the workers work in, coding agents among them, and does no stage's work itself: it never
calls a model. When a loop runner needs a worker for a task, it calls `./cook make <recipe> --task <id> --unattended` or
`./cook attempt <worker>`, and either one starts the worker's own program under the
[attempt contract](attempts.md#the-attempt-contract); an [`attempt` action](lifecycle.md#attempt) and a recipe's
[`attempt` step](recipes.md#step-kinds) do the same.

### Is `the-cook` a coding-agent tool?

It is a software development lifecycle a repository declares, and the binary that runs it: the stages a change goes
through, the events between them, the guards that hold and the proof a change carries ([lifecycle](lifecycle.md)).
Coding agents are the workers it is used with most, and agentic coding is the first station, but the lifecycle knows no
program: a bot, a codemod or a person moves a change through the same stages, each program is described by an
[ingredient](ingredients.md) the repository pins, and a [recipe](recipes.md) whose steps run no worker at all, only
commands and templates, goes through the same verification as one that does.

### Does it use MCP servers?

The binary is never an MCP client. Work that needs an MCP tool is done by a worker that owns that connection and its
credentials: the coordinating worker, meaning the worker session that runs `the-cook` commands for a person, or an
[`attempt` action](lifecycle.md#attempt). Deterministic integrations use a service's API through an `http` action, or
its CLI through a `run` action.

### Do my teammates need to install it?

No. The repository's committed wrapper, `./cook`, downloads the pinned release the first time they run a command. It
needs a POSIX `sh`, `curl` or `wget`, and `sha256sum` or `shasum`. Rendered files are committed, so their workers get
the same permissions and instructions before that. Worker hooks never download: until the release is cached they print
one line naming `./cook` and continue.

### What is `cook`?

A POSIX `sh` script committed at the repository root, in the pattern of Gradle's `gradlew`. It reads the release the
repository pins in `.cook/wrapper.yaml`, downloads it over HTTPS when it is not in the per-user cache, checks its
sha256 against the committed checksum, and runs it. It is the default entry point for people, workers, hooks and loop
runners ([wrapper](wrapper.md)).

### Do I have to invoke guideline skills by hand in every session?

No. Declare them as standing context in the profile: always on, when the worker touches matching files, or when a
message contains certain words. `the-cook` injects them through the workers' hooks, and in an attempt whose prompt
arrives on stdin it puts the `always` entries in the prompt, so they arrive even where the worker's program runs no hook
unattended. `./cook context show` shows what a session would get and why ([context](context.md)).

### Do I need a global install?

No. A global install is a convenience for scaffolding new repositories and for `the-cook fleet`. Inside a pinned
repository it defers to the pin, so it never changes which release runs there.

### Does CI need it?

No. Nothing in a repository's build, test, deploy or release depends on it. `./cook render --check` and
`./cook doctor` are optional CI steps; the wrapper downloads and verifies the pinned release
([wrapper](wrapper.md#ci)).

### Does it work offline?

Yes, once the pinned release and the pinned ingredients are cached: the wrapper makes no network request after the first
download, and only the commands that pin, apply, add, update, fetch or read a cookbook tag download from a cookbook
([fetching](ingredients.md#fetching)). Running a recipe downloads nothing from a cookbook: it is a file in the
repository. A machine that never has network access uses `COOK_BINARY` or a pre-filled cache
([wrapper](wrapper.md#air-gapped-machines)). Adoption works offline too: `the-cook scaffold --offline` writes everything
and leaves the pin pending until a later `the-cook wrapper --version <tag>` with network
access verifies the release and records its checksums. Complete it before you push. Only `the-cook fleet --deliver`,
and `the-cook workspace remove` when the profile sets `workspaces.update_before_landed`, have the version control system
learn what is shared; offline, `workspace remove` prints one note and detects landed work against what the checkout
knows ([profile](profile.md#workspaces)).

### Which coding agents and other workers are supported?

Any program an ingredient the repository pins describes. The binary has no code or data for any worker: a worker
ingredient fills the [worker contract](workers.md#the-worker-contract), and a worker declaration names it, for example
a worker `reviewer` declared with `ingredient: claude`, pinned from `github:acme/cookbook`. Which programs and versions
a cookbook covers, and how it tests them, is that cookbook's to say.

### Where do ingredients, stations and recipes come from?

From cookbooks, and recipes also from the repository itself. Each tag of a cookbook publishes one archive per unit,
named by kind, `ingredient-<name>.tar.gz`, `station-<name>.tar.gz` and `recipe-<name>.tar.gz`, and a `checksums.txt`
listing their sha256. A repository pins every ingredient it uses in `ingredients:` with its `name`, `source` (the
cookbook), `version` (the tag) and `sha256`, and records every station it applied in `stations:` the same way.
`./cook station apply <name>... --cookbook <source>@<version>` verifies the stations against that tag's checksums and
shows their entries, templates, recipes and ingredient pins as one diff to review; `./cook ingredient pin` adds or moves
one ingredient pin; `./cook recipe add <name>... --cookbook <source>@<version>` copies recipes into `.cook/recipes/`,
each with its `origin`. `the-cook` ships no ingredient, station or recipe and names no default cookbook, so a repository
may take them from any number of cookbooks, its own included ([cookbooks](stations.md#cookbooks),
[ingredients](ingredients.md), [recipes](recipes.md)).

### Can a repository have several recipes?

Yes, one for each kind of change it makes again and again, each in its own directory under `.cook/recipes/`. Some are
the repository's own, written with `./cook recipe create <name>`, such as a `migration` recipe that creates a database
migration, writes its down test, has a worker write the up and down steps and requires the `migrations` lane. Some are
added from a cookbook with `./cook recipe add`, such as a `nestjs-module` recipe from `github:acme/cookbook`, and some
come with a station, such as `implement-task`, which the `agentic-coding` station adds for a loop runner to run for each
task. Once in the repository they are all alike: its own files, edited and reviewed like any other. `./cook recipe list`
lists them. A recipe's name is unique in the repository, and a station and a recipe may share a name. A recipe runs
only as `./cook make <name>`, so its name never meets a command's ([recipes](recipes.md)).

### Is monitoring a recipe?

No. A recipe is one kind of change to the repository, taken through the stages: a workspace, steps, a verification and
the repository's delivery. Watching a running system changes nothing in the repository, so there is no workspace to
start, no receipt to write and nothing to deliver; it is the work of the monitoring system, and of a person or a worker
following a monitoring skill. What monitoring finds can start a recipe: a recipe whose input is the alert, with an
`attempt` step whose worker follows the monitoring skill to find the cause and fix it, and the lanes the fix must pass.
Give such a recipe the alert's id as a `slug` input rather than the alert's text, since an input in a prompt is text the
worker reads as instructions ([pitfalls](pitfalls.md#an-input-in-a-prompt-addresses-the-worker)).

### Why are recipes copied but ingredients pinned?

Because they hold different things. An ingredient holds facts about a program, such as the files it reads and the
arguments that bypass its checks: the same in every repository that uses the program, and wrong to change in one of
them. Pinned and rendered on every render, those facts cannot drift between repositories, and their floor cannot be
edited away. A recipe holds a practice: how this repository makes one kind of change, its commands, paths, prompts and
lanes, which differ from one repository to the next and which the team keeps improving. A recipe inherited live would
change what a repository's changes go through without any change to the repository, and nobody could fit a pinned one to
the repository. A copied recipe is the repository's own: its `origin` records where it came from, and
`./cook recipe update <name> --cookbook <source>@<version>` merges a newer version into the copy as a diff to review.
Copying loses no safety: a recipe can only add steps, lanes and requirements, and the guards and the floor stay in the
binary and the ingredients ([ADR 0024](../adrs/0024-ingredients-and-stations.md), [ADR 0025](../adrs/0025-recipes.md)).

### Can I use my own version control system or worker?

Yes. Nothing in the binary sets any program apart: it contains code and data for no external program
([ADR 0022](../adrs/0022-tool-integration.md)). A version control system needs an ingredient that fills the
[version-control contract](ingredients.md#the-version-control-contract), named with `vcs:` in the profile; a worker
needs an ingredient that fills the [worker contract](ingredients.md#the-worker-contract), and a jail around a worker's
attempts one that fills the [jail contract](ingredients.md#the-jail-contract). The version-control contract asks little
of a system: what history a checkout has and where the shared default line of work is, and an ingredient for a system
with no shared copies can leave out the optional `default_revision` and `update`, because there the branch itself is the
shared line and there is nothing to learn. A team writes the ingredient as data, publishes it in a cookbook, and each
repository pins it with `./cook ingredient pin`. Reviewing the pin is the trust decision:
`./cook ingredient show <name>` prints everything the ingredient runs and renders, its floor rules included.

### Does `the-cook` work with my loop runner or tracker?

Yes, with any that runs a command. The binary contains code for no external program; every program reaches a repository
as data: an ingredient's renders, a station's entries and templates, a recipe's steps, a declared command or a lifecycle
action ([ADR 0022](../adrs/0022-tool-integration.md)). A loop runner names
`./cook make <recipe> --task <id> --unattended`, or `./cook attempt <worker>`, as the command it runs for each task. A
tracker is updated by lifecycle actions on `start`, `completed` and `blocked` that read `COOK_TASK` and `COOK_ACTOR` in
a workspace created with `--task`. A station can write those entries for you, and add the recipe the loop runner runs,
as `agentic-coding` does with `implement-task`:
`./cook station apply agentic-coding --cookbook github:acme/cookbook@v1.2.0` ([stations](stations.md)). Otherwise
declare the action yourself, for example a `completed` action that runs `./scripts/close-task "$COOK_TASK"`, or write a
station that holds it.

### Does it depend on a task runner or a tool-version manager?

No. The binary has code for neither; a repository that uses one may pin an ingredient that describes it, whose floor
rules then apply to workers there. Commands run as `./cook <command>`, and the wrapper needs only the shell, download
and checksum tools named above ([compatibility](compatibility.md)).

### Does it support monorepos?

It operates at the repository root, the nearest directory upward that holds `.cook/config.yaml`: one profile, policy
and hooks file per repository ([compatibility](compatibility.md#monorepos)).

### Does it run on Windows?

No. It runs on linux and macOS, on amd64 and arm64, and the wrapper is a POSIX `sh` script.

## Files

### Why three files instead of one?

Each of `.cook/config.yaml` (the profile), `.cook/policy.yaml` (the policy) and `.cook/hooks.yaml` (the hooks
file) has one audience and one renderer: the profile describes the repository, the policy is reviewed as security
policy, and the hooks file describes editor feedback. Reviewers of a permission change read only `policy.yaml` and its
rendered diff.

### Why are there both `.cook/` and `.agents/`?

They have different owners. `.cook/` is `the-cook`'s own versioned configuration: the three repository files, the
pin, the manifest. `.agents/` is the cross-tool home of agent content (skills, memory, agent commands, the review
guide), a convention of the coding-agent ecosystem that other tools read too and that stays meaningful without
`the-cook`. State lives in neither
([concepts](concepts.md#two-directories-two-owners)).

### Can I edit a worker's native settings by hand?

Yes, outside the keys and entries `the-cook` owns there, which the worker's ingredient renders and the manifest
records. Everything else is preserved byte for byte. Personal settings belong in the file the worker keeps for local
settings, which `the-cook` never writes.

### I edited inside a managed block and my text disappeared.

`render` owns the text between markers. Write outside the markers; you may move the whole block anywhere in the file.

### Why are rendered files committed instead of generated on the fly?

So a clone works before its pinned release is downloaded, and so every permission change is visible as a diff in
review.

### How do I stop using it?

Two ways ([remove](remove.md)). `./cook remove` undoes the adoption: it deletes what `the-cook` added and writes back
the permission rules and hooks adoption imported. `./cook remove --eject` stops `the-cook` managing the repository and
keeps the result: the conventions in `AGENTS.md` and the ignore file, the permissions and the `raw` hooks stay as
hand-owned files. Both delete `cook`, the files in `.cook/` that `the-cook` owns or seeded and every recipe in
`.cook/recipes/`, which the plan lists first so you can keep a copy, then `.cook/` once it is empty. A file the
manifest does not record, such as a `README.md` a person added to `.cook/`, is listed and left.

### Does anything keep working after an eject?

Yes. Workers keep their permissions and `raw` hooks from the native files, and the convention blocks stay in `AGENTS.md`
and the ignore file as plain text, so people and workers still find workspaces and state where they were. The tool
blocks, which describe how to use `the-cook`, are removed, so nothing left names a command that is gone. What stops is
everything `the-cook` runs: the wrapper, verification receipts, the lifecycle's events and guards, recipe runs,
`the-cook attempt` and its contract, injected standing context and the hooks that called `the-cook`. Templates a station
wrote stay as they are, so a loop runner's configuration whose command for each task is `./cook make` or
`./cook attempt` needs a new one.

## Policy

### Can a repository remove the force-push deny?

No. It is a [floor rule](ingredients.md#the-floor) of the version-control ingredient that marks it: rendered into every
worker's output on every render, never written into `.cook/policy.yaml`, and out of reach of every repository file.
Floor rules are few by design: they cover only what is unsafe in every repository that uses the program.

### Why is my organization's deny list not built in?

The binary contains no organization-specific entry. Organization content in the binary would tie its releases to one
organization, publish that organization's choices in an open-source tool, and make every other user carry entries
they never use ([ADR 0013](../adrs/0013-organization-content.md)). Write the deny list into each repository's
`.cook/policy.yaml`, as the next answer describes.

### How do we share our organization's choices across repositories?

Write them into each repository. What `the-cook` renders and runs in a repository comes only from that repository's
files, the release it pins and the ingredients it pins by hash, so a reviewer and a worker there can read every rule
that applies, and the same commit renders the same bytes on every machine
([ADR 0013](../adrs/0013-organization-content.md)).

- **Choices that take judgement**, such as which MCP tools coding agents may read without asking, a deny on each
  repository's deploy command, a CI trigger in delivery or the task tracker its repositories use: publish a skill that
  describes them, install it with the team's skills installer, and have a coding agent following it change each
  repository's `.cook/policy.yaml`, `.cook/hooks.yaml` or profile. Each repository reviews the change as a diff, so two
  repositories can hold different entries for the same choice when they need to.
- **A way of working**, such as the organization's lanes, unattended denies and delivery actions: publish it as a
  station in the organization's cookbook, and apply it in each repository; it becomes that repository's own.
- **`the-cook` commands**, such as applying a stack station or moving to a new release: run them across repositories
  with [`the-cook fleet`](fleet.md), for example
  `the-cook fleet station apply node --cookbook github:acme/cookbook@v1.2.0 --yes --deliver` with a fleet file of Node
  repositories.
- **Stack facts**, such as how to lint, test and build a Node project: these come from a stack station in a cookbook,
  which `scaffold --cookbook` applies when its detect rule matches and `the-cook station apply` applies on request
  ([stations](stations.md)).

### An allowed command still prompts. Why?

An `ask` or `deny` entry also matches (deny > ask > allow), or the worker does not trust the folder. The
`policy` block in `AGENTS.md` shows the effective policy and where each entry comes from.

### Why does one worker not enforce one of my denies?

A bare string or a native rule string renders only for the workers whose ingredient can express it; a worker that
matches only command prefixes cannot enforce it. The `policy` block in `AGENTS.md` lists, for each such entry, the
workers it does not reach. Express the rule as a prefix list, which every worker ingredient renders
([policy](policy.md#entry-forms)).

### Does adoption lose any of our permission rules?

No. `scaffold --adopt` imports every project rule, keeps its decision, and runs an equivalence check per worker before
writing anything; a rule that would stop applying makes adoption exit 2 and name it. Only a rule that would weaken a
floor rule of an ingredient in use, such as allowing a force push to the default branch, is dropped, and it is reported
([scaffold](scaffold.md#the-equivalence-check)).

### How do I let coding agents use a read-only MCP tool without a prompt?

Add a native rule string under `shared.allow`, in the syntax of the worker's own rules. It renders verbatim for the
workers whose ingredient accepts it, and the `policy` block lists the workers it does not reach.

## Verification and tasks

### Why does `verify` refuse to skip every lane?

For an application change, one that touches a file matching `verify.app_paths`, each lane (a named family of test
commands in the profile) is either run or skipped with a reason, such as `--skip e2e="no route changed"`. A skip reason
is free text that no check can judge. It may skip a lane, but an application change needs at least one lane of real
evidence ([verify](verify.md#lane-rules)).

### My receipt became invalid after I committed.

Committing does not invalidate it. Editing, adding, deleting or `chmod`-ing a changed file does, and so does rebasing
onto new commits ([verify](verify.md#freshness)).

### Are tasks the same as issues on the hosting platform?

A task is whatever the team's tracker holds, named by the free-form id given to `the-cook workspace create --task`: an
entry in a tracker's database, a story in a JSON list, or an issue. The binary never writes issues or projects on any
hosting platform. A repository that wants an issue closed when its task completes declares an action that does it
([tasks](tasks.md)).

### I already sync my tracker across machines. Will `the-cook` break that?

No. `the-cook` has no code for any tracker: it never initialises, configures or reads a tracker's data. A station that
wires a tracker, such as `agentic-coding`, adds only lifecycle actions that run the tracker's own commands on the task a
workspace is bound to ([tasks](tasks.md)).

## Lifecycle and delivery

### Does `the-cook` push or open pull requests?

Not by itself. Everything that leaves the clone is a lifecycle action the repository declares in its own
`.cook/hooks.yaml`, such as a push and a pull request under `deliver`. No action runs that the repository does
not declare, and the binary contains no hosting-platform logic ([lifecycle](lifecycle.md)).

### Can it notify a chat channel when work finishes or a worker needs me?

Yes, through declared actions. An `http` action on `completed` or `blocked` posts to a webhook whose URL comes from an
environment variable, never from a committed file. The `attention` and `turn_end` hooks fire when a worker waits for a
person or ends a turn ([hooks](hooks.md)). Like every hook, they stay silent on a machine where the pinned release is
not cached yet.

### Is it safe to allow a worker to run `the-cook lifecycle run deliver`?

Allowing it allows everything the `deliver` actions do: they run as child processes of `the-cook`, outside the worker's
per-command permission checks. That is why scaffolded policies start with the command under `ask`. Review the actions
like code before moving it to `allow` ([safety](safety.md)).

### Can an attempt deliver its own work?

No. The binary refuses `the-cook lifecycle run` while `COOK_ATTEMPT=1` is set, whatever spelling the worker uses, and
the binary also renders `[the-cook, lifecycle]` into every worker's unattended denies, for each spelling `the-cook`
renders. When the checkout is bound to a task, `the-cook attempt` fires `completed` and then `deliver`, with scope
`task`, in its own process, after the worker has exited and the replayed checks have passed. In a workspace not bound to
a task, such as a loop runner's, it fires no event.

## Unattended runs

### Can a worker run in a container or on another machine?

Not as a worker's jail. A [jail](workers.md#jails) runs the host's own programs at the host's own paths, confined to
the directories, variables and network it is given, so the worker program, the version-control program and `the-cook`
inside it are the same files as outside. A container image brings its own copies of the programs, so the version
checks, the floor and the renders would describe a program other than the one that runs, and the binary would need a
build for the image's platform; another machine has its own files, so the tamper check, the replay and the commit could
not see what the worker changed. Run the whole recipe run or loop run there instead, with `the-cook` installed there:
a loop runner's command for each task can start `./cook make <recipe> --task {id} --unattended` inside a container or
on another machine. The core needs nothing for that ([ADR 0027](../adrs/0027-worker-jails.md)).

### Can an attempt run with permission checks bypassed?

Not under `the-cook attempt`. It refuses every argument the worker's ingredient marks as a floor bypass argument with
exit 1 naming the argument, so a loop runner configured to pass one fails at its first task. A worker a person starts
some other way, for example from their own loop-runner configuration, is outside `the-cook`, which neither detects nor
prevents it ([ADR 0022](../adrs/0022-tool-integration.md)).

### Why does an attempt replay verification instead of trusting the receipt?

The receipt, the record `the-cook verify` writes when the checks pass, is in the tree the worker can write, so it only
selects what to check. `the-cook attempt` reruns those checks against the final content, with rules read from the base
commit, before it exits 0 and releases a loop runner's completion signal.

### Does `the-cook` open the pull request after a run?

The binary has no logic for pull requests or any hosting platform. In a loop runner's workspace, which is not bound to a
task, `the-cook attempt` fires no event, and the run ends with its verified commits on the workspace's branch. Whoever
drives the run fires its events. Most runs need judgement first (which task commits go together, how to word them), so a
person or a coordinating worker arranges the commits with a skill, then runs
`./cook lifecycle run completed --scope run` and `./cook lifecycle run deliver --scope run`; the `deliver` actions do
the rest, for example a push and a pull request declared in `.cook/hooks.yaml` ([attempts](attempts.md#delivery)). In a
workspace created with `--task`, `the-cook attempt` fires `completed` and, when the work is concluded, `deliver` itself,
both with scope `task`. When the loop runner's command for each task is `./cook make <recipe> --task <id> --unattended`,
each task runs in a workspace of its own bound to the task, and the run fires those events for it
([attempts](attempts.md#through-a-recipe)).

### I run my loop runner directly with my own configuration. Does `the-cook` interfere?

No. `the-cook` has no code for any loop runner and reads no loop runner's configuration. A station that holds a loop
runner's configuration template writes it only when you apply the station and the file is absent; an existing file is
reported as `kept <path>: exists` and left as it is. Workers your configuration starts without `the-cook attempt` get
no attempt contract.

## Versions and updates

### Should we pin the version per repository?

Every repository set up with `the-cook scaffold` is pinned: `.cook/wrapper.yaml` names one release and the checksums of
its binaries, and both `./cook` and a global install run that release. Within one repository, every machine and CI job
runs the same version ([wrapper](wrapper.md)). `wrapper.yaml` is the only pin of the release: a global install, however
it was installed, defers to it. Each ingredient has its own pin in `ingredients:`, each applied station its record
in `stations:`, and each recipe added from a cookbook its `origin` in its own file.

### How do I upgrade?

`./cook wrapper --version <tag>`, or `--latest`, moves the pin and renders the repository with the new release in
the same change, so the pin and everything the new release renders differently arrive as one diff. Review it and commit
it. `the-cook fleet wrapper --version <tag> --deliver` does the same across many repositories
([wrapper](wrapper.md#upgrading)).

### A newer version of a station has entries our repository lacks.

A station is applied once: its entries reached the repository when someone applied it, and they are the repository's
own. Run `./cook station apply node --cookbook github:acme/cookbook@v1.3.0 --dry-run` to see what the newer `node`
station would add. Without `--dry-run` it shows the same diff, asks, adds only what the repository lacks, moves the
station's record in `stations:` and the ingredient pins it lists, and renders ([stations](stations.md)). An ingredient's
renders and floor rules follow its pin on every render, so `./cook ingredient pin <name> --cookbook <source>@<version>`
alone updates them. Upgrading the release moves no pin.

### A cookbook has a newer version of a recipe we added.

Run `./cook recipe update <name> --cookbook <source>@<version>`. It merges three versions: the copy the repository has,
the version the copy came from, which its `origin` names, and the newer one. Clean hunks apply, conflicts are left
marked in the recipe's files for a person or a worker to resolve, and `origin` moves to the newer version either way; it
shows the diff and asks before writing. Until every conflict is resolved, `./cook make` refuses the recipe, and
resolving them is all that is left to do ([pitfalls](pitfalls.md#a-recipe-update-leaves-conflicts-in-the-recipe)). A
recipe of the repository's own has no origin and nothing to update ([recipes](recipes.md)).

### A teammate's `the-cook` refuses our repository.

With the pin in place this happens only when the pin is bypassed: `COOK_BINARY` names a release older than the
repository's `schema`, or their global binary is too old to defer to the pin. Unset `COOK_BINARY`, or run
`./cook <command>`.

### Is it safe to run a binary that a committed script downloads?

The script downloads over HTTPS and runs a binary only after its sha256 matches the checksum committed in
`.cook/wrapper.yaml`. A `the-cook` binary wrote those checksums only after it verified the release's signed checksum
file with the release keys it embeds, so a pin can name only a release the project signed. Trust starts with the first
binary you installed, and every later pin change is verified by a binary already trusted and reviewed as a diff of
`wrapper.yaml`. `doctor`, when online, re-verifies the pin against the signed file. Worker hooks never download.

### How do I turn off the update notice?

`export COOK_NO_UPDATE_CHECK=1`. The notice is one stderr line on explicit commands, at most once a day, plus a
`doctor` note. It never appears in worker sessions, because moving the pin is a reviewed decision, not one a worker
takes because it read a notice.
