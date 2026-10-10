# 0022. Every program the binary uses is described by a plugin

## Objective

**Problem.** Teams surround their coding agents with other programs: a version control system, the agent harnesses
themselves, loop runners, task trackers, skills installers, task runners and hosting platforms. Each team picks
different ones, and each program changes its formats and commands on its own schedule. A tool that builds support for
any of them into its own code has to follow that program's releases, carries compatibility code between programs it
does not own, and makes every repository depend on choices its team did not make.

**Goal.** A team uses whichever programs it wants, switching one changes only the repository's own files and the
description of that program, and the tool's code names no program at all.

## Context

- The tool renders a repository's declared setup and constrains how an agent starts when the tool starts it
  ([0001](0001-role-of-the-tool.md)). To do that it needs two kinds of answer from outside: from a version control
  system, which revision is the base and what a file held there ([0011](0011-safety-invariants.md)); from a harness,
  what to write in its native files and which arguments start it unattended ([0009](0009-agent-support.md)).
- Every one of these programs is driven by a command line, and the answers the tool needs come back as a short
  string, a list of lines, a file's bytes or an exit status. git 2.x prints the current revision with `rev-parse`
  and a file at a revision with `show`. Claude Code 2.1.x and Codex CLI 0.16x take their permission mode and their
  prompt as arguments and send their hook payloads as JSON on standard input.
- The programs around an agent differ from team to team. Loop runners: ralph-tui (0.12). Task trackers: Beads (1.3.1),
  a JSON list of stories, an issue tracker. Skills installers: the `skills` CLI (1.7), which needs Node. Task runners:
  mise (2026.6), make, a package manager's scripts. Hosting platforms: GitHub and others. Version control: git in
  nearly every repository, and others exist.
- Each program has its own configuration format and release schedule. Example: a launcher that drives ralph-tui 0.12
  over a Beads 1.3.1 database has to present task dependencies in field names ralph-tui reads, which differ from
  those `bd` prints. That is compatibility code between two programs neither of which belongs to the project.
- ralph-tui merges a person's own configuration, `~/.config/ralph-tui/config.toml`, into every run it starts, and
  anyone can start it, or a script around it, without the tool. No tool can know every way a person runs programs on
  their machine.
- A description of a program is small. What the tool needs from git is a dozen commands. What it needs from a harness
  is the files and keys it owns, a template per file, the arguments that start it unattended, the arguments that would
  bypass its permission checks, and the paths in its hook payload that name the tool used and the files touched.
- A description read from the repository's own working tree can be edited by the agent working in that tree, and a
  description fetched during an unattended run can be swapped by whoever controls the source
  ([0011](0011-safety-invariants.md), [0021](0021-release-trust.md)).
- semantic-release, a release tool in wide use, keeps its own git commands in its core and reaches every other program
  through plugins. Chef, Ansible and Terraform describe the systems they manage as data and ship the descriptions for
  the common ones with the tool.

## Decision

1. The binary contains code for no external program. Every program it starts, calls or writes files for is described
   by a plugin: a named, versioned bundle of data the binary interprets. That includes the version control system and
   the agent harnesses.
2. A plugin holds up to five parts. Entries: additions to the repository's declared data, applied once and the
   repository's own afterwards ([0013](0013-organization-content.md)). Templates: whole files written once where absent,
   never recorded or removed ([0012](0012-write-boundary.md)). Renders: files, keys and blocks the binary renders on
   every render from the repository's declared data and the plugin's floor rules, owned and recorded under the plugin's
   name ([0007](0007-ownership.md), [0008](0008-native-configuration.md)). Operations: the commands and mappings that
   fill one of the binary's contracts ([0023](0023-core-and-contracts.md)). Checks: a version command with a supported
   range, and a detect rule that tells adoption the program is in use.
3. The release embeds and tests plugins for the common programs: the version control system, the two adapted
   harnesses, a loop runner, a task tracker, a task runner, a skills installer, and one per supported stack. A
   repository may also declare a plugin from an outside source, pinned by version and content hash, which the binary
   fetches only on an explicit command, caches outside the repository and verifies by hash on every load. A plugin
   from outside cannot take the name of an embedded one.
4. Nothing fetches during a hook or an unattended attempt. A plugin the attempt needs and does not have is a missing
   prerequisite, reported before any agent starts. An unattended attempt resolves the plugins it needs before the
   agent starts and uses that resolution for every read, comparison, reset and launch of the attempt.
5. Facts about a program that the safety rules depend on are part of that program's plugin, marked as floor: the
   arguments that bypass a harness's permission checks, and deny rules such as a force push to the default branch or
   a task runner's chaining syntax. The binary renders floor rules into every agent's output on every render and
   refuses floor arguments at every launch, and repository data cannot remove or weaken either. The process around
   them is built in ([0011](0011-safety-invariants.md)).
6. The binary reads no program's own configuration and does not try to detect how a person runs a program outside
   it. The invariants hold for what the binary renders and for every agent the binary starts.
7. Other programs reach the binary only through its command line. The worker command's arguments, exit statuses and
   environment are a stable interface that a loop runner's configuration names; a completion signal a loop runner
   expects is text the caller hands to the worker command, which releases it only for a verified attempt.
8. The health check reports a program that a plugin, a declared command or an action names and that is not installed,
   a program whose version falls outside its plugin's supported range, and a program whose plugin's detect rule
   matches the repository while the plugin is not in use. It reports a plugin that the release did not ship as not
   verified by the release.

## Reasons

- **1.** Context shows how many programs a team may choose and how often each changes. Code for any of them would grow
  with every team's choices and break with every one of their releases, and a program given code in the binary is a
  choice imposed on every repository. A description is small (Context) and changes with the program, not with the
  binary. The trade: the binary gains an interpreter for descriptions, one more format to version
  ([0005](0005-schema-compatibility.md)), and the pull to grow that format every time a program does something it
  cannot express.
- **2.** The five parts are the five ways a program touches a repository: once (entries, templates), on every render
  (renders), when the binary needs an answer or starts it (operations), and when a person checks the machine
  (checks). Naming them keeps each plugin reviewable as data ([0006](0006-repository-contract.md)) and keeps
  ownership exact: a render is recorded, a template is not.
- **3.** Shipping the common descriptions with the release makes adoption one command for the usual stack and puts
  the project's tests behind them, which is where a team expects the safety facts of point 5 to be maintained. An
  outside source lets a team describe a program the project does not know, which is what agnostic means. Pinning by
  hash and verifying on load gives an outside plugin the same trust as a release
  ([0021](0021-release-trust.md)). Refusing an embedded name keeps a shipped description from being replaced quietly
  under the name every page documents.
- **4.** An unattended attempt that fetched would hand the attempt to whoever controls the source, and a description
  resolved from the working tree during the attempt would hand it to the agent (Context). Resolving once, before the
  agent starts, from the release or the verified cache, keeps every operation of the attempt the one a person pinned.
- **5.** The bypass arguments of a harness and the chaining syntax of a task runner are facts about those programs, so
  they belong with the program's description, and they change when the program changes. Marking them as floor, and
  rendering them on every render instead of applying them once as entries, is what keeps them from being lost the way
  editable entries are lost ([0011](0011-safety-invariants.md)): a repository can edit its own data, not a plugin's
  floor. The process that applies them, refusing before launch, rendering a deny in every output, replaying proof
  from the base, is what must not depend on any program, and it stays in the binary. The trade: a team that writes
  its own harness plugin owns its floor, in the open, in a file a reviewer reads.
- **6.** A tool that reads other programs' configuration to catch overrides has to follow every format, and it still
  misses a script a person writes around the program (Context). The tool's job is to set things up and to keep its
  own promises, not to police what people run outside it. The trade: a person whose loop-runner configuration turns
  permission checks off learns it when the worker command refuses the argument at the first task.
- **7.** Every program in Context accepts a command line, so a stable command interface is enough for any of them to
  call. Passing the completion signal in as text keeps one loop runner's protocol out of the binary while still
  withholding it from an attempt that failed.
- **8.** A declared command that names a missing program fails for everyone who runs it, and reporting that needs no
  knowledge of the program. A version range matters wherever the binary writes a format a program reads or relies on
  what it prints, and the plugin that describes the program is where that range is known. A program in use without
  its plugin also runs without the plugin's floor, so the detect rule turns that gap into a finding. Naming an
  unverified plugin tells a reader whose tests stand behind it.

## Rejected alternatives

### Built-in code for the version control system and the harnesses, plugins for the rest

The binary keeps git, Claude Code and Codex CLI in its own code, because it relies on git's answers and must know a
harness's bypass arguments to refuse them, and reaches every other program through declared commands. At its best,
the safety facts live in compiled, signed code and the binary parses no description. It loses because the line it
draws is about trust, not about kind: an embedded plugin is part of the same signed release as compiled code, so the
trust is equal, while the mechanism stops being uniform, a third harness or another version control system needs a
release of the binary, and the binary's own code names programs its users may not run.

### Built-in support for every common program

The binary launches the loop runner, generates its configuration, exposes task commands for one tracker, installs
shared skills and renders tasks for one task runner. At its best, the most common setup works with one command and the
binary guards a run end to end. It loses because a team on any other program gets nothing from that code,
compatibility code between third-party programs lives in the binary (Context), a release of any of those programs can
break every repository at once, and a person can still run the program outside the binary, so the end-to-end guard is
partial anyway.

### Plugins as executable code

Plugins are programs or libraries the binary loads, as a release tool's plugins are packages in its language. At its
best, a plugin can do anything the program needs, and the description format never limits it. It loses because
executable plugins are engine code beside the binary, which the repository contract excludes
([0006](0006-repository-contract.md)), they bring a runtime the binary does not have
([0004](0004-runtime-dependencies.md)), and the parts the binary needs from a program (Context) are small enough to
describe.

### Descriptions read from the repository's working tree

The version control and harness descriptions live in the repository like any other declared data. At its best, there
is one place for everything and no cache. It loses because the description of how to read the repository cannot be
read from the repository, and an agent working in the tree could rewrite the description the next attempt uses
(Context).

## Consequences

Costs:

- The plugin format is one more schema to version, and every program feature it cannot express is either added to the
  format or left out.
- A repository that wants a tracker updated, a loop runner configured or shared skills installed carries the entries
  and files for it, written by a plugin, a person or an agent.
- Nothing fires the events of an unattended run as a whole; whoever drives the run fires them.
- A bypass setting in a loop runner's configuration surfaces only when the worker command refuses it, at the first task.
- A team that writes its own harness plugin, or its own version control plugin, is responsible for the safety facts
  in it; the health check says so.
- Each shipped plugin is tested against the real program, which makes CI heavier.

Must be maintained:

- The plugin schema and the two contracts, as a public format other people write to.
- The shipped plugins, checked against the program versions the documentation names.
- The worker command's arguments, exit statuses and environment, as a stable interface other programs name.

## Confirmation

- A CI check fails when the binary's source contains the name of any external program, or starts a program that no
  plugin, declared command or action names.
- Fixtures run every shipped plugin against the version of its program the documentation names: the git plugin's
  operations, the harness plugins' renders, launches and payload mappings, the ralph-tui and Beads plugins end to
  end. In the ralph-tui fixture, an agent entry configured with a bypass argument fails at the first task with the
  worker command's refusal, and a failed attempt never releases the completion signal.
- A fixture plugin from an outside source with a wrong hash is refused on load; a fixture attempt that needs an
  uncached plugin fails before any agent starts and no fetch happens.
- A fixture plugin that reuses an embedded name is refused.
- A fixture repository on a machine without any of the programs in Context passes the health check with every missing
  program named.
