# 0012. The binary writes only content it owns

## Objective

**Problem.** Repositories already hold state and records that people and other tools depend on: synchronised task
databases, the configuration of a loop runner (a program that runs agents unattended, one task after another), task
names, an issue tracker, a code-review platform. A tool that writes or normalises what it did not create breaks their
work, often in ways that are hard to undo.

**Goal.** Everything people and other tools kept before adoption works the same after it, and the team's record of work
stays the team's.

## Context

- Teams record their work in an issue tracker and a code-review platform. People and agents already write there, under
  the team's permissions and conventions. Clone-local task tracking is a working aid, not a record.
- Delivery conventions differ between organizations and hosting platforms: draft or ready pull requests, a commit that
  triggers CI, a notification, or a platform other than the common ones. Logic for one platform serves that
  platform's users and depends on its API and credentials.
- Repositories already contain third-party state that its owners depend on: clone-local task databases synchronised to
  remotes, sometimes committed to git with their own git hooks path; a loop runner's configuration at the repository
  root; task-runner tasks whose names collide with the tool's; instruction files with hand-written content; and an
  agent's own worktree directory. For example, `acme/api` commits its task database to git and points git's hooks path
  at the database's own hooks, keeps a loop-runner configuration at the root for people who start runs by hand, and has
  a task-runner task with the same name as one the tool would add.
- Normalising state a person created breaks them: disconnecting the remotes of a task database ends a synchronisation
  they rely on.
- People run the loop runner, ralph-tui (0.12), directly, and it names branches by a fixed pattern. A tool that cleans
  branches by pattern can delete a branch a person created.
- Some task runners, such as mise (2026.6), also install tools, at the versions their own file lists. A version pin for
  this tool can appear in that file, beside other tools' pins and comments ([0017](0017-engine-version.md)).
- Another product, the CI/CD platform Harness.io, keeps its pipeline definitions in a directory at the repository root
  whose name is the name of the tool's configuration directory ([0014](0014-directory-layout.md)).
- The loop runner merges its per-user configuration into every run it starts, including runs the binary launches
  ([0014](0014-directory-layout.md)).
- The tool's own releases must be fetched from somewhere; some networks reach only an internal mirror.

## Decision

1. The binary contains no logic for the platform that hosts a team's work: no client for its API, no credentials for
   it, no conventions of its review or delivery. The tool's own releases come from the release source the pin names,
   over plain HTTPS with no platform API ([0017](0017-engine-version.md)).
2. The binary never writes the team's record: issues, projects, boards, comments, reviews and labels. Task state the
   tool creates stays in the clone; a task database the repository already synchronises is used as found (point 4),
   so what the tool writes into it travels where that synchronisation sends it.
3. Delivery is everything that leaves the clone: a push, a pull request, a CI trigger, a notification. It happens only
   through actions that the repository declares on lifecycle events ([0015](0015-development-lifecycle.md),
   [0019](0019-action-kinds.md)). The binary ships no actions.
4. The binary never reconfigures state it did not create:
   - an existing third-party database is used as found;
   - third-party configuration at its default location is never written, and is read only when the repository opts in
     to inheriting it, or to find and name a setting that would weaken an invariant in a run the binary launches
     ([0011](0011-safety-invariants.md));
   - git configuration such as the hooks path is not changed; the binary sets overrides only on its own git
     invocations;
   - an existing task-runner task is never overwritten; a collision gets a namespaced name and an unresolved item
     ([0002](0002-division-of-work.md));
   - a task runner's own files, including its tool-version list, are never edited; a pin of this tool found there is
     reported, and its removal is listed as unresolved ([0017](0017-engine-version.md));
   - content outside managed blocks is never written ([0007](0007-ownership.md));
   - directories that belong to an agent are never scanned or cleaned.
5. A tool configuration directory the binary did not create is foreign. Adoption refuses to write into it and names
   the conflict.
6. Cleanup acts only on what the binary recorded creating, never on name patterns.

## Reasons

- **1, 3.** Delivery conventions are each organization's own. Platform logic in the binary would serve one platform's
  users, bind every release to that platform's API and credentials, and still leave everyone else to build their own
  path. Declared actions put the convention in committed configuration that the organization reviews and controls
  ([0013](0013-organization-content.md)), and the binary works the same with every platform and with none. The trade
  is that a repository with no declared actions gets no delivery from the binary. Fetching the tool's own releases is
  a different concern: it is distribution of the tool, not the team's work, and a source fetched as static files over
  HTTPS needs no platform API, so the pin can name a mirror as easily as the project's own release host.
- **2.** The team's record has one set of writers, under the team's permissions. A second writer competes with its
  conventions and adds noise, and clone-local task state that leaked there would become a second record of the same
  work. A task database that a person already synchronises is theirs: breaking the synchronisation would violate point
  4, so whatever the tool writes into it follows the person's own choice of where it goes.
- **4.** Each item protects something a person relies on. A database used as found keeps its synchronisation; root
  loop-runner configuration left alone keeps direct use working; an unchanged hooks path keeps the task database's own
  git hooks; an untouched task keeps the commands people type; content outside blocks is the repository's prose; agent
  directories hold the agent's working state. The task runner's file belongs to the repository and may hold other
  pins and comments; whether the task runner keeps installing this tool is the team's call, so the binary reports the
  duplicate and hands the choice to a person or agent ([0002](0002-division-of-work.md)). The loop runner applies its
  per-user configuration to every run it starts, so the binary reads that configuration, and writes nothing to it, for
  one purpose: to refuse and name a bypass setting before it reaches an unattended agent
  ([0011](0011-safety-invariants.md)).
- **5.** A directory of the same name that another product reads holds files the binary does not own. Writing into it
  would mix two products' files, and removal, which deletes what the manifest lists ([0007](0007-ownership.md)), would
  then operate inside a directory another product depends on. Refusing is the only outcome that cannot damage it. The
  trade: a repository that uses that product cannot adopt the tool.
- **6.** Branch and directory names collide with those people and other tools create. A record of what the binary
  created is the only proof that something is its to remove.

## Rejected alternatives

### Integrate with the issue tracker

The tool synchronises tasks and posts progress. At its best, the team gets one view of all work. It loses because it
makes the tool a second writer to the team's record, which people and agents already write under the team's permissions.

### Built-in pull-request creation on a person's request

At its best, the common case works with no configuration. It loses because it puts one hosting platform's API,
credentials and conventions in the binary. Organizations that deliver differently still need their own path, and two
delivery paths exist where one suffices.

### Normalise third-party state on adoption

At its best, every repository ends in the same state. It loses because it breaks people who rely on the current
configuration, such as remote synchronisation or direct use of a tool.

### Remove a duplicate pin from the task runner's file

At its best, the repository ends with one pin and nobody has a follow-up. It loses because it edits a file the tool
does not own, next to other tools' pins and comments, and decides for the team whether the task runner keeps
installing the tool.

### Adopt into a foreign directory by namespacing inside it

At its best, adoption never refuses. It loses because two products then share one directory, and each one's cleanup
or removal can touch the other's files.

### Clean up by naming convention

At its best, no records are needed. It loses because names collide with those other tools and people use.

## Consequences

Costs:

- A repository that declares no delivery actions delivers by hand or through an agent; the binary leaves committed
  work in the clone.
- The guarantee covers the binary's own behaviour. A declared action can write anywhere its credentials reach,
  including the team's record; that choice belongs to whoever declares and reviews it.
- Two names for one task can coexist, and a duplicate pin can stay in the task runner's file, until someone
  reconciles them.
- Inherited third-party configuration may use features the tool does not support.
- A repository that already uses the other product's directory of the same name cannot adopt the tool.

Must be maintained:

- A record of everything the tool creates, the list of third-party locations treated as foreign, and the check that
  tells the tool's own configuration directory from a foreign one.

## Confirmation

- A fixture with pre-existing third-party state (a synchronised database, a root loop-runner configuration, a colliding
  task, a branch matching a session-name pattern, a configured git hooks path) is unchanged after every write
  operation of the tool.
- A fixture whose task runner's file pins the tool is byte-identical after adoption, and the pin is reported and listed
  as unresolved.
- A fixture with a foreign tool configuration directory refuses adoption, names the conflict and writes nothing.
- A fixture whose per-user loop-runner configuration sets a bypass is refused with a message naming that file and key,
  and the file is byte-identical afterwards.
- Review rejects any hosting-platform client library, platform API endpoint or platform credential in the binary.
- A fixture with no declared lifecycle actions, taken through every lifecycle event, leaves its remote unchanged.
