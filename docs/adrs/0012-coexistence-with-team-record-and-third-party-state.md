# 0012. The binary contains no hosting-platform logic, never writes the team's record, and never reconfigures third-party state or files it did not create

- Status: Accepted
- Date: 2026-10-09

## Context

- Teams record their work in an issue tracker and a code-review platform. A second writer there competes with the
  team's conventions and adds noise. Clone-local task tracking must not become a second record.
- Delivery conventions differ between organizations and hosting platforms: draft or ready pull requests, a commit
  that triggers CI, a notification, or a platform other than the common ones. Logic for one platform in the binary
  serves that platform's users and binds every release to its API and credentials.
- Repositories already contain third-party state, and its owners depend on it. Across 51 surveyed repositories:
  local task databases synchronised to remotes, one of them committed to git with its own git hooks path; loop-runner
  configurations at the repository root in two repositories; task-runner tasks whose names collide with the tool's
  (`verify` in 4 repositories, 5 to 7 collisions in each of the three copies of the existing workflow); instruction
  files with 2 to 24 KB of content in 17 repositories; and an agent's own worktree directory in 28.
- The incumbent implementation disconnected the remotes of an existing task database, and cleaned branches by name
  pattern, which could delete a branch a person created by running the loop runner directly.

## Decision

- The binary contains no logic for any hosting platform: no platform API client, no platform credentials, no
  platform-specific conventions.
- The binary never writes the team's record: issues, projects, boards, comments, reviews and labels. Task state the
  tool keeps stays in the clone.
- Delivery is everything that leaves the clone: a push, a pull request, a CI trigger, a notification. Delivery is
  not writing the team's record. It happens only through actions that the repository or a preset it extends
  ([0013](0013-organization-neutrality-and-presets.md)) declares on lifecycle events
  ([0015](0015-lifecycle-events-with-declared-actions.md)). The binary ships no actions. What a declared action does
  is the repository's or the organization's decision, committed and reviewed like code.
- The binary never reconfigures state it did not create:
  - an existing third-party database is used as found;
  - third-party configuration at its default location is neither read nor written unless the repository opts in to
    inheriting it;
  - git configuration such as the hooks path is not changed; the binary sets overrides only on its own git
    invocations;
  - an existing task-runner task is never overwritten; a collision gets a namespaced name and an unresolved item
    ([0002](0002-deterministic-work-in-the-binary.md));
  - content outside managed blocks is never written ([0007](0007-recorded-ownership-and-committed-artefacts.md));
  - directories that belong to an agent are never scanned or cleaned.
- Cleanup acts only on what the binary recorded creating, never on name patterns.

## Alternatives considered

### Integrate with the issue tracker

Synchronise tasks and post progress. At its best, the team gets one view of all work. Not chosen: it makes the tool
a second writer to the team's record, which people and agents already write under the team's own permissions and
conventions.

### Built-in pull-request creation on the person's request

The binary opens a pull request for tool-owned changes when a person asks. At its best, the common case works with
no configuration. Not chosen: it puts one hosting platform's API, credentials and conventions in the binary;
organizations that open pull requests differently, or use another platform, still need their own path; and two
delivery paths exist where one suffices ([0015](0015-lifecycle-events-with-declared-actions.md)).

### Normalise third-party state on adoption

At its best, every repository ends in the same state. Not chosen: it breaks people who rely on the current
configuration, such as remote synchronisation or direct use of a tool; the incumbent did this.

### Clean up by naming convention

At its best, no records are needed. Not chosen: names collide with those other tools and people use.

## Consequences

Positive:

- Adoption does not change the behaviour of other tools or of people's direct use of them.
- The team's record stays the single place where work is tracked.
- The binary works the same with every hosting platform, and with none.

Negative:

- A repository that declares no delivery actions delivers by hand or through an agent; the binary leaves its
  committed work in the clone.
- The guarantee covers the binary's own behaviour. A declared action can write anywhere its credentials reach,
  including the team's record; that choice belongs to whoever declares and reviews it.
- Two names for one task can coexist until someone reconciles them.
- Inherited third-party configuration may use features the tool does not support.

Must be maintained:

- A record of everything the tool creates, and the list of third-party locations treated as foreign.

## Confirmation

- A fixture with pre-existing third-party state (a synchronised database, a root loop-runner configuration, a
  colliding task, a branch matching a session-name pattern, a configured git hooks path) is unchanged after every
  write operation of the tool.
- Review rejects any hosting-platform client library, platform API endpoint or platform credential in the binary.
- A fixture with no declared lifecycle actions, taken through every lifecycle event, leaves its remote unchanged.
