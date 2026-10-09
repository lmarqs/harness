# 0013. Organization-specific content lives in presets, never in the binary

## Context

- The tool is open source and meant for users outside any one organization.
- Agent policies in practice mix generic rules with one organization's choices. One surveyed policy holds, beside its
  generic rules, a deny on synchronising the task database to remotes, allows for deployment tasks, read patterns for
  an observability service, and a commit trailer that triggers CI. Other teams need different choices; a team that
  synchronises its task database across machines needs that deny absent.
- An organization applies its choices across many repositories, including how its work is delivered
  ([0015](0015-development-lifecycle.md), [0019](0019-action-kinds.md)). Copying them into each repository is the
  divergence this tool removes ([0006](0006-repository-contract.md)).
- Drift checks run in CI and on developer machines ([0007](0007-ownership.md)). A check whose result changes when
  someone else publishes a change fails pipelines that changed nothing.
- Policy entries and actions run with the credentials of the person or pipeline that renders and triggers them.

## Decision

1. The binary contains no organization-specific content: no organization names, hosts, services, policy entries or
   conventions. Built-in content is limited to the invariants ([0011](0011-safety-invariants.md)) and to defaults keyed
   by technology stack.
2. A repository's data may extend presets: extensions that an organization or a person publishes. A preset is
   referenced by a local path, a git URL or a subpath of a hosted repository, optionally pinned to a git reference. It
   supplies policy entries, hook declarations, actions ([0019](0019-action-kinds.md)), standing-context entries
   ([0020](0020-standing-context.md)), managed-block text and defaults. Presets are fetched and cached per user.
3. The resolved commit of every preset is recorded in the manifest ([0007](0007-ownership.md)). Rendering and drift
   checks use the recorded commit. Moving to a newer upstream commit is an explicit operation whose result is reviewed
   as a diff; the health check reports a newer upstream commit as a note.
4. Repository entries apply after preset entries. Lists are unions; a named item the repository declares overrides the
   preset's item of the same name.
5. Nothing in a preset or in a repository removes or weakens a built-in invariant ([0011](0011-safety-invariants.md)).

## Reasons

- **1.** Organization content inside the binary couples releases to organizations, publishes their content in the
  tool, and makes every other user carry entries they never use. Stack defaults are different in kind: they follow
  the technology a repository uses, which is a fact about the repository, not a choice of an organization.
- **2.** An organization's choices must reach its repositories without being copied into each one. A preset is data
  the organization keeps in a repository it controls and reviews, so its delivery conventions, policy and guidance
  live in one place. Referencing presets by path or git needs no registry and no hosting-platform logic
  ([0012](0012-write-boundary.md)).
- **3.** Recording the resolved commit makes rendering reproducible: the same repository commit renders the same bytes
  on every machine and every day, so an upstream publish never fails a check by itself. Moving forward is explicit, so
  each repository reviews the organization's change as a diff, like any other change it accepts.
- **4.** A repository knows its own case best, so its entries apply last. Unions let it add to what the preset grants
  or denies; a named item lets it override one action, block or default without restating the rest. The trade is that
  a repository cannot drop a single list entry its preset declares; it extends another preset or none.
- **5.** The invariants are never a legitimate choice ([0011](0011-safety-invariants.md)), and a preset grants
  permissions and declares actions that run with the person's credentials. Letting a preset lower the floor would make
  choosing a preset a way to lose the floor.

## Rejected alternatives

### Organization presets shipped inside the binary

At its best, those organizations need no setup. It loses because releases become coupled to organizations, their
content becomes public in the tool, and every other user carries entries they do not use.

### One fork per organization

At its best, each organization has full control. It loses because forks are the divergence this tool exists to
remove.

### No extension mechanism

Each repository lists all of its entries. At its best, everything a repository does is in the repository. It loses
because an organization's entries are duplicated across its repositories, which is the copying problem again.

### Always render the latest upstream preset

At its best, an organization's change reaches every repository without any action in them. It loses because the same
commit renders differently on different days, and a drift check fails in repositories that changed nothing.

## Consequences

Costs:

- An organization's change reaches a repository only when that repository moves to the newer commit.
- The first use of a preset at a recorded commit needs network access.
- A preset can grant permissions and declare actions that run with the person's credentials, so choosing a preset
  source is a security decision for the repository.
- A repository cannot remove one list entry of a preset it extends.

Must be maintained:

- The preset format and the layering order, as part of the public contract ([0006](0006-repository-contract.md)).

## Confirmation

- CI scans the binary's embedded assets for organization identifiers and fails on a match.
- A fixture that extends a preset renders the preset's entries; a fixture whose preset contradicts an invariant still
  renders the invariant.
- A fixture whose preset gains a new upstream commit passes the drift check unchanged, and the health check reports the
  newer commit.
- A fixture whose repository declares a named item that its preset also declares renders the repository's item.
