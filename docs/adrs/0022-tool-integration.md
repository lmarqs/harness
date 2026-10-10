# 0022. Every program the binary uses is described by a recipe

## Objective

**Problem.** Teams surround their coding agents with other programs: a version control system, the agent harnesses
themselves, loop runners, task trackers, skills installers, task runners and hosting platforms. Each team picks
different ones, and each program changes its formats and commands on its own schedule. A tool that builds support for
any of them into its own code has to follow that program's releases, carries compatibility code between programs it
does not own, and makes every repository depend on choices its team did not make.

**Goal.** A team uses whichever programs it wants, switching one changes only the repository's own files and the
description of that program, and neither the tool's code nor its release names any program.

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
- The facts a description of a harness holds, such as its settings keys, the arguments that start it unattended and
  the arguments that bypass its permission checks, are defined by the harness's vendor and can change in any of its
  releases.
- ralph-tui merges a person's own configuration, `~/.config/ralph-tui/config.toml`, into every run it starts, and
  anyone can start it, or a script around it, without the tool. No tool can know every way a person runs programs on
  their machine.
- A description of a program is small. What the tool needs from git is a dozen commands. What it needs from a harness
  is the files and keys it owns, a template per file, the arguments that start it unattended, the arguments that would
  bypass its permission checks, and the paths in its hook payload that name the tool used and the files touched.
- A description of a program is mostly knowledge of that program: its file keys, arguments, supported versions and
  surprising behaviours. Documenting a description documents those facts.
- A test that runs a description against a program shows that the description works with the versions the test
  installs. It says nothing about whether a team should use that program.
- A description read from the repository's own working tree can be edited by the agent working in that tree, and a
  description fetched during an unattended run can be swapped by whoever controls the source
  ([0011](0011-safety-invariants.md), [0021](0021-release-trust.md)).
- semantic-release, a release tool in wide use, keeps its own git commands in its core and reaches every other program
  through plugins. Chef ships resources for the common systems inside its client. Terraform's core binary contains no
  provider: a configuration declares each provider it uses by source and version constraint, providers are downloaded
  on initialisation, and a committed dependency lock file records their hashes so that dependency changes go through
  code review (Terraform 0.14 and later).

## Decision

1. The binary contains code for no external program. Every program it starts, calls or writes files for is described
   by a recipe: a named, versioned bundle of data the binary interprets. That includes the version control system and
   the agent harnesses.
2. A recipe holds up to five parts. Entries: additions to the repository's declared data, applied once and the
   repository's own afterwards ([0013](0013-organization-content.md)). Templates: whole files written once where absent,
   never recorded or removed ([0012](0012-write-boundary.md)). Renders: files, keys and blocks the binary renders on
   every render from the repository's declared data and the recipe's floor rules, owned and recorded under the recipe's
   name ([0007](0007-ownership.md), [0008](0008-native-configuration.md)). Operations: the commands and mappings that
   fill one of the binary's contracts ([0023](0023-core-and-contracts.md)). Checks: a version command with a supported
   range, and a detect rule that tells adoption the program is in use.
3. The binary ships no recipe and names no source of recipes. Every recipe a repository uses, the version control
   recipe and the harness recipes included, comes from a cookbook: a source that publishes recipes by version, with a
   content hash for each. The repository's data declares every recipe it uses by name, cookbook, version and content
   hash, each name at most once, and a declaration enters the repository only through a reviewed change. The binary
   fetches a declared recipe only on an explicit command, caches it outside the repository and verifies it against its
   hash on every fetch and every load ([0021](0021-release-trust.md)). The project tests the binary with stand-in
   recipes that describe no real program; each cookbook tests its own recipes against the programs they describe.
4. Nothing fetches during a hook or an unattended attempt. A recipe the attempt needs and does not have is a missing
   prerequisite, reported before any agent starts. An unattended attempt resolves the recipes it needs before the
   agent starts and uses that resolution for every read, comparison, reset and launch of the attempt.
5. Facts about a program that the safety rules depend on are part of that program's recipe, marked as floor: the
   arguments that bypass a harness's permission checks, and deny rules such as a force push to the default branch or
   a task runner's chaining syntax. The binary renders floor rules into every agent's output on every render and
   refuses floor arguments at every launch, and repository data cannot remove or weaken either. The process around
   them is built in ([0011](0011-safety-invariants.md)).
6. The binary reads no program's own configuration and does not try to detect how a person runs a program outside
   it. The invariants hold for what the binary renders and for every agent the binary starts.
7. Other programs reach the binary only through its command line. The worker command's arguments, exit statuses and
   environment are a stable interface that a loop runner's configuration names; a completion signal a loop runner
   expects is text the caller hands to the worker command, which releases it only for a verified attempt.
8. The health check reports a program that a recipe, a declared command or an action names and that is not installed,
   a program whose version falls outside its recipe's supported range, and a program whose recipe's detect rule
   matches the repository while the recipe is not in use.

## Reasons

- **1.** Context shows how many programs a team may choose and how often each changes. Code for any of them would grow
  with every team's choices and break with every one of their releases, and a program given code in the binary is a
  choice imposed on every repository. A description is small (Context) and changes with the program, not with the
  binary. The trade: the binary gains an interpreter for descriptions, one more format to version
  ([0005](0005-schema-compatibility.md)), and the pull to grow that format every time a program does something it
  cannot express.
- **2.** The five parts are the five ways a program touches a repository: once (entries, templates), on every render
  (renders), when the binary needs an answer or starts it (operations), and when a person checks the machine
  (checks). Naming them keeps each recipe reviewable as data ([0006](0006-repository-contract.md)) and keeps
  ownership exact: a render is recorded, a template is not.
- **3.** A recipe is knowledge of one program, and that knowledge changes with the program's releases (Context). In the
  binary, as data or as code, it would put the binary's release on every program's schedule: a harness that changes a
  bypass argument would need a release of the binary, and every repository would take that release, and everything else
  in it, to get the fix. From a cookbook, a recipe's new version moves alone, through one reviewed change of its pin. A
  recipe the release shipped would also be the choice every page documents and every test of the project vouches for,
  which is the imposed choice of point 1 in data form: a test of a recipe shows only that it works with its program
  (Context), and the choice of program stays the team's. Keeping every recipe in cookbooks keeps the project's own
  documentation and decisions about contracts, which change with the binary, and leaves each program's knowledge with
  that program's description. Naming no source keeps the binary from choosing a cookbook for anyone: whatever a
  repository uses, it named. Pinning by hash in a reviewed change makes every recipe an input the repository fixes
  exactly, as a committed dependency lock file does for an infrastructure tool's providers (Context), and verifying on
  every load catches a cached copy that changed ([0021](0021-release-trust.md)). A name declared twice would make the
  description that fills a contract depend on order, so each name appears once. Testing the binary with stand-ins tests
  the rules and the contracts, which are what the project owns ([0023](0023-core-and-contracts.md)). The trade: adoption
  takes a step that names a cookbook, a repository that has pinned no recipe can run nothing that needs a contract
  ([0023](0023-core-and-contracts.md)), every machine fetches its recipes before first use, and the floor of every
  program is only as good as the review of its pin ([0021](0021-release-trust.md)).
- **4.** An unattended attempt that fetched would hand the attempt to whoever controls the source, and a description
  resolved from the working tree during the attempt would hand it to the agent (Context). Resolving once, before the
  agent starts, from the verified cache, keeps every operation of the attempt the one a person pinned.
- **5.** The bypass arguments of a harness and the chaining syntax of a task runner are facts about those programs, so
  they belong with the program's description, and they change when the program changes. Marking them as floor, and
  rendering them on every render instead of applying them once as entries, is what keeps them from being lost the way
  editable entries are lost ([0011](0011-safety-invariants.md)): a repository can edit its own data, not a recipe's
  floor. The process that applies them, refusing before launch, rendering a deny in every output, replaying proof
  from the base, is what must not depend on any program, and it stays in the binary. The trade: a program's floor is
  what its recipe marks, so it is as strong as the cookbook that wrote it and the review that pinned it, in the open,
  in data a reviewer reads ([0021](0021-release-trust.md)).
- **6.** A tool that reads other programs' configuration to catch overrides has to follow every format, and it still
  misses a script a person writes around the program (Context). The tool's job is to set things up and to keep its
  own promises, not to police what people run outside it. The trade: a person whose loop-runner configuration turns
  permission checks off learns it when the worker command refuses the argument at the first task.
- **7.** Every program in Context accepts a command line, so a stable command interface is enough for any of them to
  call. Passing the completion signal in as text keeps one loop runner's protocol out of the binary while still
  withholding it from an attempt that failed.
- **8.** A declared command that names a missing program fails for everyone who runs it, and reporting that needs no
  knowledge of the program. A version range matters wherever the binary writes a format a program reads or relies on
  what it prints, and the recipe that describes the program is where that range is known. A program in use without
  its recipe also runs without the recipe's floor, so the detect rule turns that gap into a finding.

## Rejected alternatives

### The release embeds and tests recipes for the common programs

The binary carries recipes for the version control system, the common harnesses, a loop runner, a task tracker, a task
runner, a skills installer and each common stack, and the project's CI tests each against its program. At its best,
adoption is one command for the usual setup, with no cookbook to name and nothing to fetch, and the safety facts of
point 5 sit behind the project's tests and the release's signature, which is where a team expects them to be
maintained. It loses because the binary's release then follows every program's releases: a changed bypass argument or
settings key needs a release, and every repository takes the whole release to get it (Context). The project's tests
then vouch for choices teams did not make, and the recipes shipped become the setup every page assumes, which is the
imposed choice of point 1 in data form. The documentation and the decision records fill with product knowledge, the
file keys, versions and pitfalls of programs the project does not own, which go stale on those programs' schedules. A
release's signature adds nothing a reviewed pin lacks for data a reviewer can read in full
([0021](0021-release-trust.md)).

### A default cookbook the binary names

The binary ships no recipe but knows one cookbook it uses when a person names none. At its best, adoption needs no
source argument, everyone starts from the same well-known descriptions, and the release stays free of program
knowledge. It loses because the binary then chooses a cookbook for every repository: its maintainers inherit the
project's role of following every program, the tool's documentation describes its contents, and its users expect the
project to vouch for it. Changing the default needs a release of the binary, and one source becomes the place whose
compromise reaches every repository at its next pin. It saves little, since every recipe is pinned by hash in a
reviewed change anyway, and naming the cookbook in that step costs one argument.

### Built-in code for the version control system and the harnesses, recipes for the rest

The binary keeps the version control system and the common harnesses in its own code, because it relies on the
version control's answers and must know a harness's bypass arguments to refuse them, and reaches every other program
through recipes. At its best, the safety facts live in compiled, signed code and the binary parses no description for
the programs it cannot work without. It loses because those safety facts are still facts of programs the project does
not own and change with their releases (Context), so compiled code puts them on the binary's release schedule, the
mechanism stops being uniform, a third harness or another version control system needs a release of the binary, and
the binary's own code names programs its users may not run.

### Built-in support for every common program

The binary launches the loop runner, generates its configuration, exposes task commands for one tracker, installs
shared skills and renders tasks for one task runner. At its best, the most common setup works with one command and the
binary guards a run end to end. It loses because a team on any other program gets nothing from that code,
compatibility code between third-party programs lives in the binary (Context), a release of any of those programs can
break every repository at once, and a person can still run the program outside the binary, so the end-to-end guard is
partial anyway.

### Recipes as executable code

Recipes are programs or libraries the binary loads, as a release tool's plugins are packages in its language. At its
best, a recipe can do anything the program needs, and the description format never limits it. It loses because
executable recipes are engine code beside the binary, which the repository contract excludes
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

- The recipe format is one more schema to version, and every program feature it cannot express is either added to the
  format or left out.
- Adoption takes a step that names a cookbook and pins its recipes, and until a repository pins a recipe for a
  contract, nothing that needs that contract runs in it ([0023](0023-core-and-contracts.md)).
- Every machine, CI included, fetches a repository's recipes in an explicit step before a hook or an attempt can use
  them ([0010](0010-dependence-on-the-binary.md)).
- The floor of every program is as strong as the cookbook that describes it and the review of its pin
  ([0021](0021-release-trust.md)).
- The project's tests show that the core interprets recipes correctly, not that any recipe works with its program;
  showing that is each cookbook's work.
- A repository that wants a tracker updated, a loop runner configured or shared skills installed carries the entries
  and files for it, written by a recipe, a person or an agent.
- Nothing fires the events of an unattended run as a whole; whoever drives the run fires them.
- A bypass setting in a loop runner's configuration surfaces only when the worker command refuses it, at the first task.

Must be maintained:

- The recipe schema and the two contracts, as a public format other people write to.
- Stand-in recipes that exercise every part of the format and every operation of both contracts.
- The check that the binary's source and embedded assets name no external program and hold no recipe.
- The worker command's arguments, exit statuses and environment, as a stable interface other programs name.

## Confirmation

- A CI check fails when the binary's source or embedded assets contain the name of any external program or any
  recipe, or when the binary starts a program that no recipe, declared command or action names.
- Fixtures run stand-in recipes, named after no real program, through every part of the format: the version control
  operations, the harness renders, launches and payload mappings, and a loop runner and a task tracker end to end. In
  the loop runner fixture, an agent entry configured with a bypass argument fails at the first task with the worker
  command's refusal, and a failed attempt never releases the completion signal.
- A fixture recipe with a wrong hash is refused on load; a fixture attempt that needs an uncached recipe fails before
  any agent starts and no fetch happens.
- A fixture whose data declares two recipes with one name fails validation.
- A fixture repository that declares no recipe gets none from the binary, and every use case that needs a contract
  reports it unfilled ([0023](0023-core-and-contracts.md)).
- A fixture repository on a machine without any of the programs its recipes name passes the health check with every
  missing program named.
