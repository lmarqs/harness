# 0022. The binary integrates only git and the agent harnesses

## Objective

**Problem.** Teams surround their coding agents with other tools: loop runners that work through a task list, task
trackers, skills installers, task runners and hosting platforms. Each team picks different ones, and each tool changes
its formats and commands on its own schedule. A tool that builds in support for some of them has to follow every one of
their releases, ends up carrying compatibility code between tools it does not own, and makes every repository depend on
choices its team did not make.

**Goal.** A team uses whichever of these tools it wants, or none, and switching one changes only the repository's own
files.

## Context

- The tool configures agent harnesses ([0001](0001-role-of-the-tool.md)). Their files, events and flags are what it
  renders and constrains ([0009](0009-agent-support.md)). It also rests on git for worktrees, base commits and
  ownership ([0004](0004-runtime-dependencies.md)).
- The tools around an agent differ from team to team. Loop runners: ralph-tui (0.12). Task trackers: Beads (1.3.1), a
  JSON list of stories, an issue tracker. Skills installers: the `skills` CLI (1.7), which needs Node. Task runners:
  mise (2026.6), make, a package manager's scripts. Hosting platforms: GitHub and others.
- Each has its own configuration format and release schedule. Example: a launcher that drives ralph-tui 0.12 over a
  Beads 1.3.1 database has to present task dependencies in field names ralph-tui reads, which differ from those `bd`
  prints. That is compatibility code between two tools neither of which belongs to the project.
- ralph-tui merges a person's own configuration, `~/.config/ralph-tui/config.toml`, into every run it starts, and
  anyone can start it, or a script around it, without the tool. No tool can know every way a person runs the tools on
  their machine.
- Every one of these tools can be driven by a command line. ralph-tui 0.12 starts each agent as a configured command
  with configured default flags placed before its own arguments (`command`, `defaultFlags`). Beads 1.3.1 marks, closes
  and comments on a task with `bd update`, `bd close` and `bd comments add`. The skills installer is one
  `npx skills add` command.
- Delivery to a hosting platform is already a command the repository declares, not code in the binary
  ([0012](0012-write-boundary.md)): a pull request is opened by an action that runs the platform's own CLI.

## Decision

1. The binary integrates two kinds of external program: git, and the agent harnesses it has adapters for
   ([0009](0009-agent-support.md)). It contains no code for any other tool: no loop runner, task tracker, skills
   installer, task runner or hosting platform.
2. Every other tool reaches a repository as data in the repository's own files: a command the repository declares, an
   action a lifecycle event runs ([0015](0015-development-lifecycle.md), [0019](0019-action-kinds.md)), or entries and
   files a preset writes on request ([0013](0013-organization-content.md)).
3. The binary offers an interface other tools call: a worker command that runs one agent for one task under the
   unattended contract ([0011](0011-safety-invariants.md)) and reports the outcome through its exit status, the
   lifecycle events, and environment variables that name the task and the actor. A loop runner or a tracker connects
   through its own configuration, which names that interface. A completion signal a loop runner expects is text the
   caller hands to the worker command, which releases it only for a verified attempt.
4. The binary reads no other tool's configuration and does not try to detect how a person runs a tool outside it. The
   invariants ([0011](0011-safety-invariants.md)) hold for what the binary renders and for every agent the binary
   starts.
5. The binary may recognise a command shape that would weaken an invariant, such as a task runner's chaining syntax or
   an agent's bypass flag, without integrating the tool behind it.
6. The health check reports a program that the repository's declared commands or actions name and that is not
   installed. It checks versions only of git and of the agent harnesses.

## Reasons

- **1.** The harnesses are what the tool configures, so their formats are its job ([0001](0001-role-of-the-tool.md),
  [0009](0009-agent-support.md)), and git is what its ownership, worktrees and verification rest on. Every other tool
  is a choice a team makes, and Context shows how many there are. Code for each would grow with every team's choices
  and break with every one of their releases. The trade: what a built-in integration would give for free, such as
  claiming a task in a tracker when work starts, becomes entries a repository carries.
- **2.** Data in the repository's files is explicit and reviewable ([0013](0013-organization-content.md)), and it is
  replaceable: moving from one tracker to another changes actions, not the binary. A preset keeps the cost of writing
  that data low.
- **3.** Every tool in Context accepts a command line, so a stable command interface is enough for any of them to call.
  The worker contract is the part that must not depend on who calls it: it refuses bypass flags, reads the rules from
  the base commit and replays the proof whoever started it ([0011](0011-safety-invariants.md)). Passing the completion
  signal in as text keeps one loop runner's protocol out of the binary while still withholding it from an attempt that
  failed.
- **4.** A tool that reads other tools' configuration to catch overrides has to follow every format, and it still misses
  a script a person writes around the tool (Context). The tool's job is to set things up and to keep its own promises,
  not to police what people run outside it. The trade: a person whose loop-runner configuration turns permission checks
  off learns it when the worker command refuses the flag at the first task, not before the run starts.
- **5.** A deny rule for a dangerous command shape costs nothing where the tool behind it is absent, and keeps allow
  rules meaning what a reviewer reads where it is present ([0011](0011-safety-invariants.md)). Recognising the shape
  needs no knowledge of how the tool works.
- **6.** A declared command that names a missing program fails for everyone who runs it, and reporting that needs no
  knowledge of the program. A supported version range matters only where the binary writes a format the program reads,
  which is true of the harnesses alone.

## Rejected alternatives

### Built-in support for the common tools

The binary launches the loop runner, generates its configuration, exposes task commands for one tracker and one JSON
format, installs shared skills, and renders tasks for one task runner. At its best, the most common setup works with
one command, and the binary guards a run end to end, including reading the loop runner's configuration to name a bypass
setting before anything starts. It loses because a team on any other tool gets nothing from that code, compatibility
code between third-party tools lives in the binary (Context), a release of any of those tools can break every
repository at once, and a person can still run the tool outside the binary, so the end-to-end guard is partial anyway.

### A plugin interface for tool integrations

At its best, the core stays small and anyone can add support for a tool. It loses because plugins are engine code
beside the binary, which the repository contract excludes ([0006](0006-repository-contract.md)), and a command-line
interface already lets any tool call the binary without code on either side.

### Integrate only the loop runner, because the unattended contract depends on how agents start

At its best, the safety path stays under the binary's control from launch to completion. It loses because the contract
is enforced inside the worker command, whoever starts it (point 3). A launcher adds no guarantee the worker command
does not already give, and it ties the binary to one loop runner's configuration format.

## Consequences

Costs:

- A repository that wants a tracker updated, a loop runner configured or shared skills installed carries the entries
  and files for it, written by a preset, a person or an agent.
- Nothing fires the events of an unattended run as a whole; whoever drives the run fires them.
- A bypass setting in a loop runner's configuration surfaces only when the worker command refuses it, at the first
  task.
- Each tool preset is tested against the real tool, which makes CI heavier.

Must be maintained:

- The worker command's flags, exit statuses and environment, as a stable interface other tools' configuration names.
- The tool presets, checked against the tool versions the documentation names.

## Confirmation

- A CI check fails when the binary's source starts a program other than git and the adapted agents, except programs
  that repository data names.
- Fixtures run ralph-tui with the ralph-tui preset and Beads with the Beads preset. In the ralph-tui fixture, an agent
  entry configured with a bypass flag fails at the first task with the worker command's refusal, and a failed attempt
  never releases the completion signal.
- A fixture repository on a machine without any of the tools in Context passes the health check.
