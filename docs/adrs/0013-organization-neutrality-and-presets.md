# 0013. The binary carries no organization-specific content; organizations layer presets through an extension mechanism

- Status: Accepted
- Date: 2026-10-09

## Context

- The tool is open source and meant for users outside any one organization.
- The incumbent policy mixed generic invariants with one organization's choices: a deny on synchronising the task
  database to remotes, allows for deployment tasks, read patterns for an observability service, and a commit
  trailer that triggers CI. Other teams need different choices; a team that synchronises its task database across
  machines needs that deny absent.
- An organization still needs to apply its choices across its repositories without copying them into each one,
  including how its work is delivered ([0015](0015-lifecycle-events-with-declared-actions.md)).
- Drift checks run in CI and on developer machines ([0007](0007-recorded-ownership-and-committed-artefacts.md)).
  A check whose result changes when someone else publishes a change fails pipelines that changed nothing.

## Decision

- The binary contains no organization-specific content: no organization names, hosts, services, policy entries or
  conventions. Built-in content is limited to the invariants ([0011](0011-built-in-safety-invariants.md)) and to
  defaults keyed by technology stack.
- A repository's data may extend a preset: the extension an organization or a person publishes. A preset is
  referenced by a local path, a git URL or a subpath of a hosted repository, optionally pinned to a git reference.
  It supplies policy entries, hook declarations, lifecycle actions, managed-block text and defaults. Lifecycle
  actions in a preset are where an organization's delivery conventions live. Presets are fetched and cached per
  user.
- The resolved commit of every preset is recorded in the ownership manifest. Rendering and drift checks reuse the
  recorded commit, so checks are reproducible and an upstream change never fails a check by itself. Moving to a
  newer upstream commit is an explicit operation whose result is reviewed as a diff; the health check reports a
  newer upstream commit as a note.
- Repository entries are applied after preset entries. Lists are unions; a named item the repository declares
  replaces the preset's item of the same name.
- Nothing in a preset or a repository removes or weakens a built-in invariant.

## Alternatives considered

### Organization presets shipped inside the binary

At its best, those organizations need no setup. Not chosen: releases become coupled to organizations, their content
becomes public in the tool, and every other user carries entries they do not use.

### One fork per organization

At its best, each organization has full control. Not chosen: forks are the divergence this project exists to remove.

### No extension mechanism

Each repository lists all of its entries. Not chosen: an organization's entries are duplicated across its
repositories, which is the copying problem again.

### Always render the latest upstream preset

At its best, an organization's change reaches every repository without any action in them. Not chosen: the same
commit renders differently on different days, and a drift check fails in repositories that changed nothing.

## Consequences

Positive:

- Anyone can use the tool; organization content lives in a repository the organization controls and reviews.
- Drift checks depend only on the repository's own commits.

Negative:

- An organization's change reaches a repository only when that repository moves to the newer commit.
- The first use of a preset at a recorded commit needs network access.
- A preset can grant permissions and declare actions that run with the person's credentials, so choosing a preset
  source is a security decision for the repository.

Must be maintained:

- The preset format and the layering order, as part of the public contract
  ([0006](0006-repository-state-is-declarative-data.md)).

## Confirmation

- CI scans the binary's embedded assets for organization identifiers and fails on a match.
- A fixture that extends a preset renders the preset's entries; a fixture whose preset contradicts an invariant
  still renders the invariant.
- A fixture whose preset gains a new upstream commit passes the drift check unchanged, and the health check
  reports the newer commit.
- A fixture whose repository declares a named item that its preset also declares renders the repository's item.
