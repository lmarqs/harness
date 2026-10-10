# 0012. The binary writes only content it owns

## Objective

**Problem.** Repositories already hold state and records that people and other tools depend on: synchronised task
databases, the configuration of a loop runner (a program that runs agents unattended, one task after another), an issue
tracker, a code-review platform. A tool that writes or normalises what it did not create breaks their work, often in
ways that are hard to undo.

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
  root; a task runner's files, with their tool-version lists; instruction files with hand-written content; and an
  agent's own worktree directory. For example, `acme/api` commits its task database to git and points git's hooks path
  at the database's own hooks, and keeps a loop-runner configuration at the root for people who start runs by hand.
- Normalising state a person created breaks them: disconnecting the remotes of a task database ends a synchronisation
  they rely on.
- People run the loop runner, ralph-tui (0.12), directly, and it names branches by a fixed pattern. A tool that cleans
  branches by pattern can delete a branch a person created.
- A directory name at the repository root is not reserved: any program or person may create one, including one with
  the name of the tool's configuration directory ([0014](0014-directory-layout.md)).
- The loop runner merges its per-user configuration into every run it starts, and anyone can start it without the tool
  ([0022](0022-tool-integration.md)).
- Some starting files are another tool's configuration, such as a loop runner's agent entries that name the tool's
  worker command; a recipe can supply one as a template ([0013](0013-organization-content.md)).
- The tool's own releases must be fetched from somewhere; some networks reach only an internal mirror.

## Decision

1. The binary contains no logic for the platform that hosts a team's work: no client for its API, no credentials for
   it, no conventions of its review or delivery. The tool's own releases come from the release source the pin names,
   over plain HTTPS with no platform API ([0017](0017-engine-version.md)).
2. The binary never writes the team's record: issues, projects, boards, comments, reviews and labels. Task state the
   binary records, such as the task a working copy is bound to, stays in the clone.
3. Delivery is everything that leaves the clone: a push, a pull request, a CI trigger, a notification. It happens only
   through actions that the repository declares on lifecycle events ([0015](0015-development-lifecycle.md),
   [0019](0019-action-kinds.md)). No action runs that the repository's files do not declare: a recipe that holds actions
   writes them into those files only when a person applies it.
4. The binary never reconfigures state it did not create:
   - an existing third-party database is left as found;
   - another program's configuration and files are written only as a template a person applies, where the file is
     absent, or as content a recipe renders and the manifest records ([0007](0007-ownership.md),
     [0013](0013-organization-content.md), [0022](0022-tool-integration.md)), and are never read beyond the files a
     recipe renders into and the rules adoption imports ([0008](0008-native-configuration.md),
     [0016](0016-permission-adoption.md));
   - the version control system's configuration, such as its hooks path, is not changed; a setting an operation needs is
     passed on that operation's own command, never written into the configuration ([0022](0022-tool-integration.md));
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
  work. A tracker is updated by actions the repository declares, under the declarer's choice (point 3,
  [0022](0022-tool-integration.md)).
- **4.** Each item protects something a person relies on. A database left as found keeps its synchronisation; an
  unchanged hooks path keeps the task database's own hooks (Context), and a setting passed on one command changes
  nothing any other program sees; content outside blocks is the repository's prose; agent directories hold the agent's
  working state. Another tool's files belong to that tool and to the people who run it, and the binary has no code that
  reads them ([0022](0022-tool-integration.md)): a root loop-runner configuration left alone keeps direct use working,
  and a task runner's file keeps its pins and comments. A template is written only where its file is absent, so it never
  replaces a file someone already keeps, and from then on the file is the repository's own
  ([0013](0013-organization-content.md)). The trade: a setting in another tool's configuration that works against the
  tool, such as a bypass, surfaces only when the binary meets it in its own input ([0011](0011-safety-invariants.md)).
- **5.** A directory of the same name that the binary did not create holds files the binary does not own, and another
  program may read them. Writing into it would mix the binary's files with someone else's, and removal, which deletes
  what the manifest lists ([0007](0007-ownership.md)), would then operate inside a directory others depend on. Refusing
  is the only outcome that cannot damage it. The trade: a repository whose root already holds such a directory cannot
  adopt the tool until it is moved.
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
- A repository whose root already holds a directory of the configuration directory's name, created by something
  else, cannot adopt the tool until it is moved.

Must be maintained:

- A record of everything the tool creates, the list of third-party locations treated as foreign, and the check that
  tells the tool's own configuration directory from a foreign one.

## Confirmation

- A fixture with pre-existing third-party state (a synchronised database, a root loop-runner configuration, a task
  runner's file that pins the tool, a branch matching a session-name pattern, a configured version-control hooks path)
  is unchanged after every write operation of the tool.
- A fixture with a foreign tool configuration directory refuses adoption, names the conflict and writes nothing.
- A fixture whose root and per-user loop-runner configurations set a bypass runs every command of the tool without
  opening either file, and both are byte-identical afterwards.
- Applying a recipe whose template names a loop-runner configuration that already exists leaves that file byte-identical
  and reports it as kept.
- Review rejects any hosting-platform client library, platform API endpoint or platform credential in the binary.
- A fixture with no declared lifecycle actions, taken through every lifecycle event, leaves its remote unchanged.
