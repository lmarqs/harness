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

Nobody suffers for not using harness. Why: teammates, pipelines and repositories that never chose harness share the
committed files of those that did, so anything harness requires of them is imposed, not adopted
([ADR 0010](../adrs/0010-dependence-on-the-binary.md)).

- A clone of an adopted repository MUST build, test, deploy and start its agents without the binary, whether
  downloaded by the committed wrapper script `harnessw` or installed globally, because a clone is used by people who
  never ran harness.
- Implicit entry points degrade quietly and never download; explicit ones fetch the pinned version or fail loudly
  ([cli-standards.md](cli-standards.md#entry-points)).
  - An implicit entry point is one the agent fires on its own: a hook rendered by harness. It runs
    `./harnessw --no-download`, MUST NOT download, and when no binary is cached MUST exit 0 after one stderr line
    naming `./harnessw`. Why: a download inside a hook stalls every agent action on a slow or offline machine, and a
    failing hook interrupts sessions of people who never installed harness.
  - An explicit entry point is one a person or agent runs on purpose: `./harnessw <command>` or a global
    `harness <command>`. It downloads and verifies the pinned version on first use, and
    MUST exit non-zero naming the cause when it cannot obtain it: 1 for a failed download, 3 when a person must act
    first. Why: a skipped verification that exits 0 reads as a pass.
- CI MUST NOT need the binary or the wrapper, because an adopting repository's pipelines keep their own steps.
  `./harnessw render --check` and `./harnessw doctor` are optional CI steps a repository MAY add; the wrapper downloads
  and verifies the pinned version for them.
- Files harness writes MUST NOT add a runtime dependency, a required CI step, or a change to build, test, deploy or
  release outputs, because those outputs belong to the repository's own toolchain. The wrapper's first-run
  requirements (a POSIX `sh`, `curl` or `wget`, `sha256sum` or `shasum`) apply only to running harness.
- Adoption and removal are per repository and per module (for example, `modules.verify` on and `modules.memory` off),
  because a repository may want one practice without the others. Removal has two modes, undo (`harness remove`) and
  eject (`harness remove --eject`), and either leaves a repository that works with what remains (see
  [generated-content-standards.md](generated-content-standards.md#removal)).

## 2. The binary is deterministic; judgement belongs to agents

From the user's side, everything harness does is predictable. An agent does whatever needs judgement, and harness
checks the result. Why: deterministic output can be reviewed once and trusted in every repository, while a
guess made by code is wrong in the first repository nobody anticipated
([ADR 0002](../adrs/0002-division-of-work.md)).

- Every binary command produces the same result for the same inputs on every supported machine, so a user can read
  the repository files in `.harness/` and know what `harness render` will write.
- When the binary meets something it cannot decide without judgement (an unrecognised hook, a `CLAUDE.md` with
  content), it MUST NOT guess, because a wrong guess silently rewrites a team's configuration. It
  leaves the item untouched and lists it as a to-do (`harness doctor --json`) that an agent or a person resolves.
- Every judgement step an agent performs ends in a deterministic check the binary owns (`harness render --check`,
  `harness doctor`, `harness verify --check`), so the outcome of judgement is still verified.

[when-cli-when-skill.md](when-cli-when-skill.md) decides where a given behaviour belongs.

## 3. Sets the rules, never plays

harness is a meta-harness (see the [glossary](../features/glossary.md)): it configures and constrains the tools that run
models, and it never takes their place. Why: the agents people already use are developed by their vendors and follow
every model change. A tool that competed with them would carry credentials, cost and a second agent loop to maintain
([ADR 0001](../adrs/0001-role-of-the-tool.md)).

- The binary never calls a model and is never a client of an agent tool server (MCP). Work that needs a model or an MCP
  tool is done by an agent: the [coordinating agent](../features/lifecycle.md#the-lifecycle-stage-by-stage), or an
  `agent` action that harness starts as a child process under the
  [worker contract](../features/ralph.md#the-worker-contract). Why: the agent owns those connections, their credentials
  and their permissions.
- The binary never writes knowledge: no free prose, no memory entries, no domain skills, no policy entries invented on
  the user's behalf, because knowledge needs judgement. It writes rendered files, managed blocks whose text is derived
  from the repository files (fixed sentences stating a convention with its configured values, tables, lists), the
  repository files (`config.yaml`, `policy.yaml`, `hooks.yaml`) only when seeding them, migrating them or applying a
  preset a person asked for, and the owned pin `wrapper.yaml` only when scaffolding or re-pinning.
- The binary contains no logic for any hosting platform and never writes the team's record of work: no issues,
  projects, boards, comments, reviews or labels. Why: the team's record already has writers under the team's own
  conventions, and platform logic in the binary binds every release to one platform. Everything that leaves the
  clone, a pull request included, is a declared action ([principle 7](#7-nothing-leaves-the-clone-undeclared);
  [ADR 0012](../adrs/0012-write-boundary.md)).
- The binary integrates two kinds of external program: git and the agent harnesses it has adapters for. It contains no
  code for any other tool, such as a loop runner, a task tracker, a skills installer or a task runner, and reads none
  of their configuration. Such a tool reaches a repository as data: a command the profile declares, an action a
  lifecycle event fires, or an entry or file a preset writes on request. Why: each team picks different tools, and
  code for one would follow every release of a tool the project does not own while serving no team that chose
  another ([ADR 0022](../adrs/0022-tool-integration.md)).
- The binary never reconfigures state it did not create (an existing `.beads/`, the database of the Beads task tracker;
  `core.hooksPath`; a task runner's tasks and tool list; `CLAUDE.md` content; a `.harness/` directory that another
  product created), because its owners depend on it as it is.

## 4. Least surprise

- A command does what its name says and nothing else. Read commands (`doctor`, `explain`, `version`, `context show`,
  `lifecycle list`, `preset list`, `preset show`, `render --check`, `verify --check`) MUST NOT modify the repository,
  fire lifecycle events or run actions. Why: agents and CI call read commands freely, so a side effect there happens
  without anyone deciding it.
- The same invocation in the same state gives the same result. Running a mutation twice changes nothing the second
  time and says so, because agents retry and a retry must be safe.
- Environment and machine affect presentation (color, notices, verbosity), never the bytes written into a
  repository, because two teammates rendering the same commit must produce the same diff. A binary upgrade changes
  those bytes only under the rules in
  [compatibility-standards.md](compatibility-standards.md#rendered-output-across-releases).
- No hidden network access, because a user must be able to say what a command contacted. The network calls harness makes
  on its own are: the wrapper's download of the version pinned in `.harness/wrapper.yaml` (on an explicit invocation
  only, over HTTPS, verified against the pinned checksum); the release's signed checksum file, fetched by
  `harness scaffold` (not with `--offline`) and `harness wrapper` before they write a pin and re-verified by
  `harness doctor` at most daily; and the daily release check
  ([update notices](compatibility-standards.md#update-notices)). Each is documented and the release check can be
  disabled. Rendering and applying a preset need none of them, because their only inputs are the repository's files and
  the pinned release ([principle 9](#9-everything-a-repository-does-is-in-its-own-files)). Any other network access is a
  declared action (an `http` action, or a `run` or `agent` action whose command reaches the network), visible in
  `harness lifecycle list` and in `.harness/hooks.yaml`.
- Names, flags and output look the same across commands ([cli-standards.md](cli-standards.md)), so what a user learns
  on one command holds on the next.

## 5. Never destroy; always show what will change

Why: a repository is the team's work, and a tool that loses part of it is removed and not trusted again
([ADR 0007](../adrs/0007-ownership.md)).

- Every mutating command accepts `--dry-run` and prints exactly the change set it would apply, so the change can be
  judged before it happens.
- harness writes only inside content it owns: files and blocks recorded in the manifest, and the native keys it owns.
  The one other write is a preset's template, and only where its file is absent. Everything else is preserved byte for
  byte, because content outside the manifest is somebody else's.
- When owned content has been edited by hand, `harness remove` refuses to delete it unless `--force` is given, and
  names each path it refused, because the hand edit may be the only copy of someone's work.
- Every change harness makes outside its state directories (the gitignored paths the profile sets, under `.work/` by
  default) lands in versioned files, so it reaches review as a diff.
- Removal is deterministic in both modes: undo and eject act only on what the manifest lists, so what removal deletes
  can be known before it runs.

## 6. One way to do each thing

Why: two ways to do one thing drift apart, and a reader cannot tell which one is in force.

- Each operation has one command and one spelling. There are no command aliases. The wrapper and a global install are
  entry points to the same command, not spellings of different ones.
- Each fact lives in one file: permissions in `policy.yaml`; agent hooks, notification actions and lifecycle actions
  in `hooks.yaml`; the pinned version and its checksums in `wrapper.yaml`; everything else harness needs, standing
  context included, in `config.yaml`. Rendered files are outputs, never a second place to edit.
- The harness version is pinned in exactly one place, `.harness/wrapper.yaml`, because two pins disagree as soon as one
  of them moves. harness reads no other file for it.
- Each kind of content has one home: harness's own configuration in `.harness/`, agent content (skills, memory,
  commands, review guide) in `.agents/`, state at the paths the profile sets
  ([configuration-standards.md](configuration-standards.md#where-files-live)).
- A repository has one documented entry point, the wrapper script `./harnessw`. The `AGENTS.md` `commands` block shows
  it, so agents and people type the same thing. A task the repository writes in its own task runner to call harness is
  the repository's file, not a second entry point harness renders.
- A skill never re-describes what a command does deterministically; it calls the command
  ([skills-standards.md](skills-standards.md)), because a description drifts from the command it describes.

## 7. Nothing leaves the clone undeclared

The binary contains no logic for any hosting platform. Everything that leaves the clone (a push, a pull request, a CI
trigger, a notification, a call to a service) happens only through a **declared action**: an action that the repository
declares on a lifecycle event (`start`, `verified`, `completed`, `deliver`, `landed`, or the out-of-band `blocked`) or
on an agent notification event (`attention`, `turn_end`). Why: a side effect outside the clone is visible to others and
hard to undo, so it must be written down, reviewed and guarded before it can happen
([ADR 0015](../adrs/0015-development-lifecycle.md), [ADR 0019](../adrs/0019-action-kinds.md)). The
[lifecycle guide](../features/lifecycle.md) has the stages, trigger points and full reference.

- **Declared.** Actions live in `.harness/hooks.yaml`, and no action runs that the repository does not declare there.
  Stack presets hold none; a tool preset's actions (the `beads` preset's, for example) reach the file only when a
  person applies the preset and reads its diff. So what "deliver" means is decided in the repository, where an
  organization's delivery convention is written like any other entry.
- **Three kinds.** An action is a shell command (`run`), an HTTP request harness performs itself (`http`), or a
  headless agent run (`agent`). Notification events accept `run` and `http` only, so an agent hook never starts
  another agent.
- **Visible.** `harness lifecycle list` shows every event, action, kind, scope and `auto` flag.
- **Dry-runnable.** `harness lifecycle run <event> --dry-run` prints each action and the `HARNESS_*` environment it
  would receive, and runs nothing.
- **Recorded.** Every run appends its event, scope, and each action's name, kind, exit status and duration to the
  lifecycle log, `lifecycle.jsonl` in the state directory of the primary checkout (`.work/lifecycle.jsonl` by
  default). The log never records URLs, headers, bodies or prompts, because they can carry secrets and untrusted
  text. Dry runs are not recorded. A failing automatic event never changes the exit code of the command that fired it.
  harness reports it on stderr and records it.
- **One action, three triggers.** A person runs `harness lifecycle run <event>`; a coordinating agent runs the same
  command; or harness fires the action at the event's trigger point when the action sets `auto: true`. `auto`
  defaults to false, so nothing leaves the clone automatically unless a committed file says so.
- **Guarded.** Actions never run inside a worker (the agent [`harness worker`](cli-standards.md#the-worker-command)
  starts for one task) or an `agent` action: the binary refuses `lifecycle run` there itself, whatever spelling invoked
  it. `deliver` always refuses on the default branch and with uncommitted changes and, when the verify module is
  enabled, without a valid receipt (the record a passing `harness verify` writes). These guards are built-in invariants
  ([ADR 0011](../adrs/0011-safety-invariants.md)).
- **Code, not settings.** Actions are committed and reviewed like code. They receive values only as environment
  variables, never substituted into a command string, so a task title or a branch name cannot inject shell syntax.
  Secrets reach an action only from the environment (`url_env`, `headers_env`, or the environment a `run` command
  inherits), never from a committed file.
- **Agents for agent work.** Work that needs judgement or an MCP tool after an event is an `agent` action or the
  coordinating agent's job; harness never connects to an MCP server itself.
- **Transparent.** Without the binary no action fires, and a person pushes and opens pull requests by hand, exactly as
  before adoption.

Permission to run `harness lifecycle run <event>` is permission for everything that event's actions do: the actions
run as child processes of harness, outside the agent's per-command permission checks. The scaffolded repository policy
therefore seeds `[harness, lifecycle, run, deliver]` under `ask`, rendered for every rendered spelling (`harness`,
`./harnessw` and `harnessw`). It is a seeded entry: the repository owns it and MAY change it
([ADR 0015](../adrs/0015-development-lifecycle.md)). The rendered entries are defence in depth. Other spellings, such
as an absolute path to the binary, match no prefix rule, and the binary enforces the guards itself.

## 8. Conventions outlive the tool

Every convention harness establishes is written down as plain-language text where people and other tools already look,
and stays valid after harness is removed. Why: people, agents and the other tools in a repository rely on where
worktrees and state live. If those conventions existed only in harness's configuration, removing harness
would leave state that follows rules nobody wrote down ([ADR 0018](../adrs/0018-conventions.md)).

- Two classes of managed block carry harness's text in `AGENTS.md`
  ([generated-content-standards.md](generated-content-standards.md#block-classes)):
  - **Convention blocks** state facts that stay true without harness: where worktrees are created and on which
    branch, the effective permissions, the memory index, standing
    directives declared `static`, and the `CLAUDE.md` import. They never name a harness command.
  - **Tool blocks** describe how to use harness: its commands, what `.harness/` holds, how to verify through it, how
    work is delivered through its lifecycle. They MAY name `./harnessw` commands.
- Each path a module establishes is also listed in a managed block of `.gitignore`, which is a convention block.
- State lives at paths the profile sets (`state.dir`, `worktrees.dir`), never inside `.harness/` or `.agents/`. The
  block text names the configured path, so people, agents and other tools find it by reading the repository, not by
  asking harness.
- A block states its convention as fact. It never says it is managed by harness beyond its marker, so it reads as the
  repository's own text.
- `harness remove --eject` keeps convention blocks and `.gitignore` entries as the repository's own content and
  removes tool blocks, so the conventions remain in force after harness leaves and no instruction names a command that
  no longer exists ([generated-content-standards.md](generated-content-standards.md#removal)).

## 9. Everything a repository does is in its own files

What harness renders and runs in a repository comes from that repository's files and the release it pins, and from
nothing else: no inheritance, no includes, no source outside the repository. Why: people and agents learn what a
repository does by reading it. A rule that acts in a repository from somewhere else cannot be read by an agent working
there, and a reviewer sees its effect without its reason ([ADR 0013](../adrs/0013-organization-content.md)).

- The same commit renders the same bytes on every machine and every day, with no network and an empty per-user cache,
  because no input can change without a commit in the repository.
- No repository file has a key that names a source outside the repository for what harness renders or runs
  ([configuration-standards.md](configuration-standards.md#inputs)), so reading the repository is enough to predict
  what it does.
- Every write to the repository files is one a person or agent asked for: seeding them, migrating them, applying a
  preset with `harness preset apply`, or an edit. Why: a file that changes by itself holds decisions nobody made.
- A built-in preset reaches a repository only as entries written into its files and files its templates write, and
  nothing records where an entry or a file came from, so each entry has one source of truth: the file it is in
  ([configuration-standards.md](configuration-standards.md#presets)).
- An organization's choices reach its repositories as changes to each repository's files, written by a person or by an
  agent following the organization's skill, and reviewed there as a diff. The cost is that a choice shared by many
  repositories is written, and changed, in each of them. For example, when acme lets its agents use the read-only tools
  of its observability MCP server, `acme/api` and `acme/web` each get those entries in their own `policy.yaml`, in a
  change reviewed in that repository.

## When principles conflict

Apply them in this order, because each earlier principle protects something harder to recover than the next:

1. The built-in invariants ([ADR 0011](../adrs/0011-safety-invariants.md)). No principle, setting or flag weakens
   them.
2. Never destroy.
3. Nothing leaves the clone undeclared.
4. Optional and transparent.
5. Conventions outlive the tool.
6. Everything a repository does is in its own files.
7. Deterministic in the binary, judgement to agents.
8. Least surprise.
9. One way to do each thing.

Example: a convenient default that would overwrite a hand-edited file loses to "never destroy", so the command refuses
and names `--force` instead. A shared source that would let an organization change a rule once for all its
repositories loses to "everything a repository does is in its own files", even though one place for the rule would
serve "one way to do each thing". The rule is written into each repository instead.
