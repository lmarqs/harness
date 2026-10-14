# 0009. Worker ingredients render worker-neutral sources into each worker's native files

## Objective

**Problem.** Each coding agent, and each other program that changes a repository for someone, has its own permission
syntax, hook schema and events, or none of them, and new ones keep appearing. Configuration kept by hand once per
program drifts, so one repository grants different permissions to different coding agents, and supporting another
program means writing everything again.

**Goal.** One declaration grants the same permissions and runs the same hooks in every supported worker that has them,
and supporting another worker changes no repository's declarations.

## Context

- The supported coding agents differ in permission syntax (pattern strings in Claude Code's settings, rule files that
  match the leading words of a command in Codex CLI), in hook schema, in the payload they send to a hook, and in the
  events they emit. More coding agents exist and new ones appear.
- Programs that change a repository for someone differ in what they have at all. A coding agent reads permissions,
  hooks, instructions and skills from files in the repository. A codemod, or a bot that opens pull requests, is started
  with arguments and reads none of these.
- Configuration maintained by hand once per coding agent drifts. Example: `acme/api` writes its permissions by hand
  twice, in Claude Code's settings and in Codex CLI's rule files. A teammate allows a new test command in one and
  forgets the other, so the two coding agents disagree, and the repository grows a dedicated check whose only job is to
  compare the two files and keep them in step.
- Coding agents change their formats between releases. A central description fixes such a change once, and is broken
  by it in every repository until fixed.
- What a worker needs from the tool can be described as data (Context of [0022](0022-tool-integration.md)): the
  arguments that start it and, for a coding agent, the native files and keys to own, a template for each, and the paths
  in its hook payload.
- Some constructs exist in one coding agent only. A repository can hook events that only some coding agents emit, and
  coding agents differ in whether they report that they are waiting for a person or that a turn ended.
- Repositories assign programs to roles differently. Example: `acme/api` uses Claude Code interactively and Codex CLI
  only for unattended work, `acme/cli` uses both interactively, and `acme/web` uses Claude Code for both.
- Repositories also differ in whether unattended work may reach the network, and programs differ in how they are told.
  Example: `acme/web` lets its unattended worker install packages from a registry, and `acme/infra` keeps every
  attempt off the network. Codex CLI 0.160 reads the choice from a sandbox setting, `network_access`, which an argument
  at launch can set; another program may take a flag of its own, or offer no such control.

## Decision

1. The sources are worker-neutral: one permission policy and one set of hook declarations per repository.
2. Each worker is served by a worker ingredient ([0022](0022-tool-integration.md)) that fills the worker contract
   ([0023](0023-core-and-contracts.md)). The contract's one required part is how the worker is launched. Where the
   worker has them, the ingredient also gives the arguments that start it unattended, the arguments that bypass its
   permission checks, marked as floor, the renders that turn the neutral sources into its native files under the
   file-preservation rules of [0008](0008-native-configuration.md), the mapping of its hook payload, the location of its
   instructions file and of its skills, how its native permission rules are imported, and the arguments that let the
   worker's attempts use the network. Its checks declare the worker versions it supports. A worker runs attempts only
   when its ingredient delivers the repository's unattended deny rules, as launch arguments or through the permissions
   it renders; one that can do neither is used interactively only, and a deterministic program with neither runs as a
   declared command, not as a worker.
3. Each worker ingredient maps its worker's hook payloads onto one neutral set of worker events. The set includes
   session start, a file read or edited and a prompt submitted, the points where guidance enters the worker's context
   ([0020](0020-standing-context.md)), and the notification events (waiting for a person and turn ended)
   ([0019](0019-action-kinds.md)). Where a worker lacks an event, or a part of the contract the repository's data
   relies on, its ingredient renders nothing for it and the health check (the tool's read-only report on a repository's
   setup) reports the gap.
4. The worker contract is public and documented. Adding a worker means a repository pinning a worker ingredient from a
   cookbook and declaring the worker; the sources and the other ingredients do not change.
5. A construct the neutral model does not express may be written as a raw, worker-specific entry. An ingredient whose
   worker cannot express it skips it. Every native permission rule of every worker an ingredient describes is
   expressible this way ([0016](0016-permission-adoption.md)).
6. Which workers a repository declares, which role each fills (interactive or unattended) and whether each one's
   attempts may use the network (off unless the repository says otherwise) are repository data, not ingredient content.
   The ingredient maps the network choice to its worker's arguments, as it maps the launch; a repository that lets the
   attempts of a worker use the network, when the worker's ingredient gives no such arguments, fails validation. The
   declaration may also name a jail that confines the worker's attempts, with the jail's options the repository chooses;
   the jail's own arguments are facts in the jail's ingredient ([0027](0027-worker-jails.md)). A station may propose
   these as entries, which are the repository's own once applied ([0024](0024-ingredients-and-stations.md)).

## Reasons

- **1.** One source means one change reaches every worker with equivalent effect. The alternative, one hand-written
  file per coding agent, drifts. The dedicated check of the `acme/api` example in Context shows the cost.
- **2.** Each worker's syntax, schema and release cadence is confined to one ingredient. A format change in one worker
  is fixed in one ingredient, and the other workers' output is untouched. As data, the ingredient can be fixed without
  a change to the binary's code, and a team can describe any worker ([0022](0022-tool-integration.md)). Only the launch
  is required because some programs that change a repository have no permissions, hooks or instructions (Context).
  An attempt is different: the binary's own deny on lifecycle actions and the repository's unattended denies hold in
  every attempt ([0011](0011-safety-invariants.md)), and a worker that cannot receive them would run unattended with
  nothing to stop it, so it is refused for attempts. A deterministic program needs no attempt: run as a declared
  command, it still passes through the lifecycle's verification and guards. The supported-version declaration lets
  the health check stop an unsupported worker version before the tool renders configuration that worker misreads. The
  trade: everything a worker needs must fit the contract's fixed parts.
- **3.** Hook declarations, notification actions and context triggers are written once, against neutral events.
  Without normalisation, each would be declared once per worker and drift like the rest. Rendering nothing for a
  missing event or a missing part, and saying so, tells a person which worker lacks a behaviour instead of letting them
  assume it works.
- **4.** New workers keep appearing. A public contract lets one be supported without touching the data every adopting
  repository has committed or the schema it follows.
- **5.** The neutral model always lags the workers. Without a passthrough, a repository whose rules or hooks fall
  outside the model cannot adopt without losing them, which [0016](0016-permission-adoption.md) forbids. Skipping a
  raw entry for workers that cannot express it keeps every other ingredient's output valid.
- **6.** Role assignments differ between repositories (Context). An ingredient that fixed one assignment would impose it
  on every repository that uses the program. With the assignment as data, each repository states its own, and a station
  that proposes one hands it to the repository like any other entry. Network access is the same kind of choice
  (Context): whether attempts may reach the network is the repository's practice, while the arguments that carry the
  choice are a fact about the program, so the value is data the repository holds and its mapping is data the ingredient
  holds. Off by default keeps an attempt from reaching the network unless someone chose it. Failing validation when the
  ingredient cannot carry the choice keeps a repository from believing its attempts may reach the network when nothing
  tells the program; with the choice off, the binary says nothing to the program, whose own default then holds. Whether
  a worker's attempts run confined, and in which jail, is the same kind of choice again: it differs between repositories
  and between workers of one repository, so it belongs on the declaration, and the jail program's arguments belong to
  its ingredient ([0027](0027-worker-jails.md)).

## Rejected alternatives

### One source per worker

At its best, each worker's full feature set is available with no translation. It loses because every rule exists once
per worker, and those copies drift, as the `acme/api` example in Context shows.

### A neutral model with no passthrough

At its best, the contract is the smallest possible and every entry means the same in every worker. It loses because
repositories with hooks or rules outside the model cannot adopt without losing them.

### One coding agent's format as the source, translated for the others

At its best, no new format is introduced and one coding agent's users already know it. It loses because it ties the
repository-facing contract to one vendor's syntax and release schedule, and every other worker's output depends on
translating a format designed for someone else.

### A contract that requires every part

Every worker ingredient fills permissions, hooks, instructions and skills as well as the launch. At its best, every
declared worker honours the whole policy, and no gap needs reporting. It loses because a codemod or a bot that has no
such configuration (Context) could not be described at all, and would change repositories outside the lifecycle and
its guards.

## Consequences

Costs:

- The neutral model must follow the workers as they evolve, and translating worker-specific features can lose meaning.
- A raw entry takes effect only for the workers that can express it.
- A breaking release of a worker affects every adopting repository until its cookbook publishes an ingredient update
  and the repository moves its pin; the supported-version declaration detects it but does not prevent it.
- A behaviour declared once is absent for workers that lack its event.
- A worker whose ingredient neither passes the unattended deny rules at launch nor renders permissions cannot run
  attempts; it is used interactively, or its program runs as a declared command ([0011](0011-safety-invariants.md)).
- Network access left off passes nothing to the program, so what an attempt can reach is then the program's own
  default, which its cookbook documents; a program whose default reaches the network still reaches it.

Must be maintained:

- The worker contract, its shared contract suite and the neutral set of worker events.
- For each worker ingredient, in the cookbook that publishes it: its supported-version range and its run of the
  contract suite against the worker versions in that range.

## Confirmation

- The shared contract suite asserts that the same policy yields the same allow, ask or deny outcome for the same fixture
  commands in each worker's rendered output. The tool's own CI runs it against stand-in worker ingredients; a cookbook
  runs it against each worker ingredient it publishes.
- Recorded hook payloads from each worker an ingredient describes, including notification and context-injection
  events, normalise to the same neutral events.
- A fixture whose worker lacks a declared event renders nothing for it, and the health check reports the gap.
- A fixture worker whose ingredient fills only the launch is refused for an attempt with the missing part named, is
  accepted for interactive use, and the health check reports each part of the repository's data that cannot reach it.
- The health check reports worker versions outside an ingredient's supported range.
- A fixture worker whose attempts may use the network starts with its ingredient's network arguments at every
  unattended launch; one declared without network access starts without them; and a fixture that allows network access
  to a worker whose ingredient gives no network arguments fails validation.
- A fixture worker whose declaration names a jail starts every attempt through that jail, and its rendered files are
  the same as without one.
