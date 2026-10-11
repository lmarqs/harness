# User-facing principles

## Objective

**Problem.** No set of specific rules foresees every case, and the concerns behind them pull against each other, such as
convenience against keeping someone's work intact. When contributors and reviewers settle such a case by their own sense
of what matters, a teammate who never chose the tool, a developer whose repository it writes to or a CI pipeline is
protected by one feature and let down by the next.

**Goal.** Two people deciding the same uncovered case, or the same conflict between two concerns, reach the same
decision and can give the same reason for it.

## Overview

Every other page in these standards applies one or more of the principles below. When a case is not covered by a
specific rule, decide it from these principles and write the decision, with its reason, into the standard it belongs
to ([README.md](README.md#changing-a-standard)).

## 1. Optional and transparent

Nobody suffers for not using `the-cook`. Why: teammates, pipelines and repositories that never chose `the-cook` share
the committed files of those that did, so anything `the-cook` requires of them is imposed, not adopted
([ADR 0010](../adrs/0010-dependence-on-the-binary.md)).

- A clone of an adopted repository MUST build, test, deploy and start its workers without the binary, whether
  downloaded by the committed wrapper script `cook` or installed globally, because a clone is used by people who
  never ran `the-cook`.
- Implicit entry points degrade quietly and never download; explicit ones fetch the pinned version or fail loudly
  ([cli-standards.md](cli-standards.md#entry-points)).
  - An implicit entry point is one a worker fires on its own: a hook rendered by `the-cook`. It runs
    `./cook --no-download`, MUST NOT download, and when no binary is cached MUST exit 0 after one stderr line
    naming `./cook`. Why: a download inside a hook stalls every action of the worker on a slow or offline machine,
    and a failing hook interrupts sessions of people who never installed `the-cook`.
  - An explicit entry point is one a person or a worker runs on purpose: `./cook <command>` or a global
    `the-cook <command>`. It downloads and verifies the pinned version on first use, and
    MUST exit non-zero naming the cause when it cannot obtain it: 1 for a failed download, 3 when a person must act
    first. Why: a skipped verification that exits 0 reads as a pass.
- CI MUST NOT need the binary or the wrapper, because an adopting repository's pipelines keep their own steps.
  `./cook render --check` and `./cook doctor` are optional CI steps a repository MAY add; the wrapper downloads
  and verifies the pinned version for them.
- Files `the-cook` writes MUST NOT add a runtime dependency, a required CI step, or a change to build, test, deploy or
  release outputs, because those outputs belong to the repository's own toolchain. The wrapper's first-run
  requirements (a POSIX `sh`, `curl` or `wget`, `sha256sum` or `shasum`) apply only to running `the-cook`.
- Adoption and removal are per repository and per module (for example, `modules.verify` on and `modules.memory` off),
  because a repository may want one practice without the others. Removal has two modes, undo (`the-cook remove`) and
  eject (`the-cook remove --eject`), and either leaves a repository that works with what remains (see
  [generated-content-standards.md](generated-content-standards.md#removal)).

## 2. The binary is deterministic; judgement belongs to people and workers

From the user's side, everything `the-cook` does is predictable. A person, or a worker such as a coding agent, does
whatever needs judgement, and `the-cook` checks the result. Why: deterministic output can be reviewed once and trusted
in every repository, while a guess made by code is wrong in the first repository nobody anticipated
([ADR 0002](../adrs/0002-division-of-work.md)).

- Every binary command produces the same result for the same inputs on every supported machine, so a user can read
  the repository files in `.cook/` and know what `the-cook render` will write.
- When the binary meets something it cannot decide without judgement (an unrecognised hook, a worker's instructions
  file with content), it MUST NOT guess, because a wrong guess silently rewrites a team's configuration. It leaves the
  item untouched and lists it as a to-do (`the-cook doctor --json`) that a person or a coding agent resolves.
- Every judgement step a person or a worker performs ends in a deterministic check the binary owns
  (`the-cook render --check`, `the-cook doctor`, `the-cook verify --check`), so the outcome of judgement is still
  verified.
- A recipe keeps the same split inside one kind of change a repository makes many times: the binary runs its `run` and
  `template` steps, a worker in an `attempt` step or a person at a handoff does the judgement, and the recipe run is
  verified with the lanes the recipe requires before it completes
  ([when-cli-when-skill.md](when-cli-when-skill.md#recipes)).

[when-cli-when-skill.md](when-cli-when-skill.md) decides where a given behaviour belongs.

## 3. Sets the rules, never plays

`the-cook` is a declared software development lifecycle: the stages a change goes through, the events it fires between
them, the guards that hold between stages and the proof a change carries. It sets those rules for whoever does the work
of a change, people and the workers they choose, such as coding agents, and it never takes a worker's place. Why: the
coding agents people already use are developed by their vendors and follow every model change. A tool that competed with
them would carry credentials and cost, and a loop of its own to maintain ([ADR 0001](../adrs/0001-role-of-the-tool.md),
[ADR 0015](../adrs/0015-development-lifecycle.md)).

- The binary never calls a model and is never a client of a tool server (MCP). Work that needs a model or an MCP tool
  is done by a worker: the [coordinating worker](../features/lifecycle.md#the-lifecycle-stage-by-stage), or an
  `attempt` action that `the-cook` starts as a child process under the
  [attempt contract](../features/attempts.md#the-attempt-contract). Why: the worker owns those connections, their
  credentials and their permissions.
- The binary never writes knowledge: no free prose, no memory entries, no domain skills, no policy entries invented on
  the user's behalf, because knowledge needs judgement. It writes rendered files, managed blocks whose text is derived
  from the repository files (fixed sentences stating a convention with its configured values, tables, lists), the
  repository files (`config.yaml`, `policy.yaml`, `hooks.yaml`) only when seeding them, migrating them, pinning an
  ingredient or applying a station a person asked for, a recipe in `.cook/recipes/` only when a person adds, updates or
  creates it or applies a station that adds it, the owned pin `wrapper.yaml` only when scaffolding or re-pinning, and,
  in a recipe run's workspace, the files the recipe's `template` steps render.
- The binary contains no logic for any hosting platform and never writes the team's record of work: no issues,
  projects, boards, comments, reviews or labels. Why: the team's record already has writers under the team's own
  conventions, and platform logic in the binary binds every release to one platform. Everything that leaves the
  clone, a pull request included, is a declared action ([principle 7](#7-nothing-leaves-the-clone-undeclared);
  [ADR 0012](../adrs/0012-write-boundary.md)).
- The binary contains code for no external program, the version control system and the workers included, and reads no
  program's own configuration. A stage that needs a program reaches it through a contract that an
  [ingredient](../features/ingredients.md) fills, or through the commands the profile declares and the actions lifecycle
  events fire. Why: each team picks different programs, and code for one would follow every release of a program the
  project does not own while serving no team that chose another ([ADR 0022](../adrs/0022-tool-integration.md),
  [ADR 0023](../adrs/0023-core-and-contracts.md)).
- The binary never reconfigures state it did not create (a task tracker's existing database; the version control
  system's hook settings; a task runner's tasks and tool list; the content of a worker's instructions file; a `.cook/`
  directory that another product created), because its owners depend on it as it is.

## 4. Least surprise

- A command does what its name says and nothing else. Read commands (`doctor`, `explain`, `version`, `context show`,
  `lifecycle list`, `ingredient list`, `ingredient show`, `station list`, `station show`, `recipe list`, `recipe show`,
  `make --status`, `render --check`, `verify --check`) MUST NOT modify the repository, fire lifecycle events or run
  actions. Why: workers and CI call read commands freely, so a side effect there happens without anyone deciding it.
- The same invocation in the same state gives the same result. Running a mutation twice changes nothing the second
  time and says so, because workers and scripts retry and a retry must be safe.
- Environment and machine affect presentation (color, notices, verbosity), never the bytes written into a repository,
  because two teammates rendering the same commit must produce the same diff. A binary upgrade changes those bytes only
  under the rules in [compatibility-standards.md](compatibility-standards.md#rendered-output-across-releases), and an
  ingredient's renders change only when its pin moves
  ([ingredients and stations across versions](compatibility-standards.md#ingredients-and-stations-across-versions)).
- No hidden network access, because a user must be able to say what a command contacted. The network calls `the-cook`
  makes on its own are: the wrapper's download of the version pinned in `.cook/wrapper.yaml` (on an explicit invocation
  only, over HTTPS, verified against the pinned checksum); the release's signed checksum file, fetched by
  `the-cook scaffold` (not with `--offline`) and `the-cook wrapper` before they write a pin and re-verified by
  `the-cook doctor` at most daily; the download of a cookbook tag's `checksums.txt` and of ingredient, station and
  recipe archives by `the-cook ingredient pin`, `the-cook station apply` (with `--cookbook`, or for a pin that is not
  cached), `the-cook recipe add`, `the-cook recipe update`, `the-cook fetch`, `scaffold --cookbook` and the
  `--cookbook` reads of `ingredient list`, `ingredient show`, `station list`, `station show`, `recipe list` and
  `recipe show` (over HTTPS, each archive verified against its `sha256`); and the daily release check
  ([update notices](compatibility-standards.md#update-notices)). Each is documented and the release check can be
  disabled. Rendering needs none of them once the pinned ingredients are cached, and neither does applying a cached
  station again or running a recipe, because their only inputs are the repository's files, the pinned release and what
  the repository pins by hash ([principle 9](#9-everything-a-repository-does-is-in-its-own-files)). Any other network
  access is declared: an `http` action, a `run` action or a recipe's `run` step whose program reaches the network, or
  an `attempt` action or `attempt` step whose worker does, visible in `the-cook lifecycle list`, in `.cook/hooks.yaml`
  and in `.cook/recipes/`.
- Names, flags and output look the same across commands ([cli-standards.md](cli-standards.md)), so what a user learns
  on one command holds on the next.

## 5. Never destroy; always show what will change

Why: a repository is the team's work, and a tool that loses part of it is removed and not trusted again
([ADR 0007](../adrs/0007-ownership.md)).

- Every mutating command accepts `--dry-run` and prints exactly the change set it would apply, so the change can be
  judged before it happens.
- `the-cook` writes only inside content it owns: files and blocks recorded in the manifest, and the native keys it owns.
  The other writes are a station's template, only where its file is absent; a recipe copied into
  `.cook/recipes/<name>/`, only where that directory is absent, or merged into the repository's copy by
  `the-cook recipe update`, after a diff a person confirmed; and, inside a recipe run's own workspace, the files its
  steps write, a `template` step only where its file is absent. Everything else is preserved byte for byte, because
  content outside the manifest is somebody else's.
- When owned content has been edited by hand, `the-cook remove` refuses to delete it unless `--force` is given, and
  names each path it refused, because the hand edit may be the only copy of someone's work.
- Every change `the-cook` makes outside its state directories (the gitignored paths the profile sets, under `.work/` by
  default) lands in versioned files, so it reaches review as a diff.
- Removal is deterministic in both modes: undo and eject act only on what the manifest lists and on the recipes in
  `.cook/recipes/`, which they list before deleting, so what removal deletes can be known before it runs.

## 6. One way to do each thing

Why: two ways to do one thing drift apart, and a reader cannot tell which one is in force.

- Each operation has one command and one spelling. There are no command aliases. The wrapper and a global install are
  entry points to the same command, not spellings of different ones.
- Each fact lives in one file: permissions in `policy.yaml`; hooks on worker events, notification actions and lifecycle
  actions in `hooks.yaml`; the pinned version and its checksums in `wrapper.yaml`; each kind of change in its own recipe
  under `.cook/recipes/`; everything else `the-cook` needs, standing context included, in `config.yaml`. Rendered files
  are outputs, never a second place to edit.
- The version of `the-cook` is pinned in exactly one place, `.cook/wrapper.yaml`, because two pins disagree as soon as
  one of them moves. `the-cook` reads no other file for it.
- Each kind of content has one home: the configuration of `the-cook` in `.cook/`, the skills, memory, commands and
  review guide that workers read in `.agents/`, state at the paths the profile sets
  ([configuration-standards.md](configuration-standards.md#where-files-live)).
- A repository has one documented entry point, the wrapper script `./cook`. The `AGENTS.md` `commands` block shows it,
  so workers and people type the same thing. A task the repository writes in its own task runner to call `the-cook` is
  the repository's file, not a second entry point `the-cook` renders.
- A skill never re-describes what a command does deterministically; it calls the command
  ([skills-standards.md](skills-standards.md)), because a description drifts from the command it describes.

## 7. Nothing leaves the clone undeclared

The binary contains no logic for any hosting platform. Everything that leaves the clone (a push, a pull request, a CI
trigger, a notification, a call to a service) happens only through a **declared action**: an action that the repository
declares on a lifecycle event (`start`, `verified`, `completed`, `deliver`, `landed`, or the out-of-band `blocked`) or
on a worker's notification event (`attention`, `turn_end`). Why: a side effect outside the clone is visible to others
and hard to undo, so it must be written down, reviewed and guarded before it can happen
([ADR 0015](../adrs/0015-development-lifecycle.md), [ADR 0019](../adrs/0019-action-kinds.md)). The
[lifecycle guide](../features/lifecycle.md) has the stages, trigger points and full reference.

- **Declared.** Actions live in `.cook/hooks.yaml`, and no action runs that the repository does not declare there. A
  station's actions, such as the tracker actions of an `agentic-coding` station, reach the file only when a person
  applies the station and reads its diff; stack stations hold none. A recipe declares none: the work of a recipe run
  is delivered by the repository's own actions. So what "deliver" means is decided in the repository, where an
  organization's delivery convention is written like any other entry.
- **Three kinds.** An action is a shell command (`run`), an HTTP request `the-cook` performs itself (`http`), or a
  worker started unattended on a prompt (`attempt`). Notification events accept `run` and `http` only, so a worker's
  hook never starts another worker.
- **Visible.** `the-cook lifecycle list` shows every event, action, kind, scope and `auto` flag.
- **Dry-runnable.** `the-cook lifecycle run <event> --dry-run` prints each action and the `COOK_*` environment it
  would receive, and runs nothing.
- **Recorded.** Every run appends its event, scope, and each action's name, kind, exit status and duration to the
  lifecycle log, `lifecycle.jsonl` in the state directory of the primary checkout (`.work/lifecycle.jsonl` by
  default). The log never records URLs, headers, bodies or prompts, because they can carry secrets and untrusted
  text. Dry runs are not recorded. A failing automatic event never changes the exit code of the command that fired it.
  `the-cook` reports it on stderr and records it.
- **One action, three triggers.** A person runs `the-cook lifecycle run <event>`; a coordinating worker runs the same
  command; or `the-cook` fires the action at the event's trigger point when the action sets `auto: true`. `auto`
  defaults to false, so nothing leaves the clone automatically unless a committed file says so.
- **Guarded.** Actions never run inside an attempt (the unattended run of a worker that
  [`the-cook attempt`](cli-standards.md#the-attempt-command) starts for one task) or an `attempt` action: the binary
  refuses `lifecycle run` there itself, whatever spelling invoked it. `deliver` always refuses on the default branch and
  with uncommitted changes and, when the verify module is enabled, without a valid receipt (the record a passing
  `the-cook verify` writes). These guards are built-in invariants ([ADR 0011](../adrs/0011-safety-invariants.md)).
- **Code, not settings.** Actions are committed and reviewed like code. They receive values only as environment
  variables, never substituted into a command string, so a task title or a branch name cannot inject shell syntax.
  Secrets reach an action only from the environment (`url_env`, `headers_env`, or the environment a `run` command
  inherits), never from a committed file.
- **Workers for judgement.** Work that needs judgement or an MCP tool after an event is an `attempt` action or the
  coordinating worker's job; `the-cook` never connects to an MCP server itself.
- **Transparent.** Without the binary no action fires, and a person pushes and opens pull requests by hand, exactly as
  before adoption.

Permission to run `the-cook lifecycle run <event>` is permission for everything that event's actions do: the actions run
as child processes of `the-cook`, outside the worker's per-command permission checks. The scaffolded repository policy
therefore seeds `[the-cook, lifecycle, run, deliver]` under `ask`, rendered for every rendered spelling (`the-cook`,
`./cook` and `cook`). It is a seeded entry: the repository owns it and MAY change it
([ADR 0015](../adrs/0015-development-lifecycle.md)). The rendered entries are defence in depth. Other spellings, such as
an absolute path to the binary, match no prefix rule, and the binary enforces the guards itself. Permission to start
`the-cook make` is, for the same reason, permission for every step of the recipe and for the automatic actions its
events fire.

## 8. Conventions outlive the tool

Every convention `the-cook` establishes is written down as plain-language text where people and other tools already
look, and stays valid after `the-cook` is removed. Why: people, workers and the other tools in a repository rely on
where workspaces and state live. If those conventions existed only in the configuration of `the-cook`, removing
`the-cook` would leave state that follows rules nobody wrote down ([ADR 0018](../adrs/0018-conventions.md)).

- Two classes of managed block carry the text `the-cook` writes into `AGENTS.md`
  ([generated-content-standards.md](generated-content-standards.md#block-classes)):
  - **Convention blocks** state facts that stay true without `the-cook`: where workspaces are created and on which
    branch, the effective permissions, the memory index, standing directives declared `static`, and the block a worker
    ingredient renders to make its worker's instructions file import `AGENTS.md`. They never name a `the-cook` command.
  - **Tool blocks** describe how to use `the-cook`: its commands, what `.cook/` holds, how to verify through it, how
    work is delivered through its lifecycle. They MAY name `./cook` commands.
- Each path a module establishes is also listed in a managed block of the version control system's ignore file, which
  the version-control ingredient renders and which is a convention block.
- State lives at paths the profile sets (`state.dir`, `workspaces.dir`), never inside `.cook/` or `.agents/`. The
  block text names the configured path, so people, workers and other tools find it by reading the repository, not by
  asking `the-cook`.
- A block states its convention as fact. It never says it is managed by `the-cook` beyond its marker, so it reads as the
  repository's own text.
- `the-cook remove --eject` keeps convention blocks and ignore-file entries as the repository's own content and removes
  tool blocks, so the conventions remain in force after `the-cook` leaves and no instruction names a command that no
  longer exists ([generated-content-standards.md](generated-content-standards.md#removal)).

## 9. Everything a repository does is in its own files

What `the-cook` renders and runs in a repository comes from that repository's files, the release it pins and the
ingredients it pins by hash, and from nothing else: no inheritance, no includes, no source the repository does not pin.
Why: people and workers learn what a repository does by reading it. A rule that acts in a repository from somewhere else
cannot be read by anyone working there, and a reviewer sees its effect without its reason
([ADR 0013](../adrs/0013-organization-content.md)).

- The same commit renders the same bytes on every machine and every day, with no network, because no input can change
  without a commit in the repository: the release and every ingredient are pinned by hash in its files, and the binary
  ships no ingredient, no station and no recipe of its own.
- No repository file has a key that names a source outside the repository for what `the-cook` renders or runs, except a
  release, an ingredient or a station pinned by hash ([configuration-standards.md](configuration-standards.md#inputs)),
  so reading the repository is enough to predict what it does. A recipe's `origin` records where its copy came from and
  is never read to run it.
- Every write to the repository files is one a person or a worker asked for: seeding them, migrating them, pinning an
  ingredient with `the-cook ingredient pin`, applying a station with `the-cook station apply`, adding, updating or
  creating a recipe with `the-cook recipe add`, `recipe update` or `recipe create`, or an edit. Why: a file that changes
  by itself holds decisions nobody made.
- A program reaches a repository only through an ingredient the repository pins in `ingredients:`, as renders the
  manifest records under the ingredient's name. A way of working reaches it only when a person applies a station: as
  entries written into its files, files its templates write, the ingredient pins it lists and the recipes it adds, all
  in one reviewed diff. A kind of change reaches it as a recipe in `.cook/recipes/`, written there or copied from a
  cookbook, which runs as the repository's own file. `stations:` records the pin a station was applied at, and a
  recipe's `origin` the version its copy came from; nothing records where an entry or a template file came from, so each
  has one source of truth: the file it is in
  ([configuration-standards.md](configuration-standards.md#ingredients-and-stations)).
- An organization's choices reach its repositories as changes to each repository's files, written by a person or by a
  coding agent following the organization's skill, and reviewed there as a diff. The cost is that a choice shared by
  many repositories is written, and changed, in each of them. For example, when acme lets its coding agents use the
  read-only tools of its observability MCP server, `acme/api` and `acme/web` each get those entries in their own
  `policy.yaml`, in a change reviewed in that repository.

## When principles conflict

Apply them in this order, because each earlier principle protects something harder to recover than the next:

1. The built-in invariants and the floor of the ingredients in use ([ADR 0011](../adrs/0011-safety-invariants.md),
   [ADR 0022](../adrs/0022-tool-integration.md)). No principle, setting or flag weakens them.
2. Never destroy.
3. Nothing leaves the clone undeclared.
4. Optional and transparent.
5. Conventions outlive the tool.
6. Everything a repository does is in its own files.
7. Deterministic in the binary, judgement to people and workers.
8. Least surprise.
9. One way to do each thing.

Example: a convenient default that would overwrite a hand-edited file loses to "never destroy", so the command refuses
and names `--force` instead. A shared source that would let an organization change a rule once for all its
repositories loses to "everything a repository does is in its own files", even though one place for the rule would
serve "one way to do each thing". The rule is written into each repository instead.
