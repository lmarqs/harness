# 0013. Organization-specific content lives in each repository's own files

## Objective

**Problem.** An organization wants its choices, such as which services agents may read and how work is delivered, in
many repositories, and repositories on one technology stack need the same starting entries. Both have to reach each
repository without making its behaviour depend on anything its own files do not show: a person or an agent reading a
repository must be able to tell from it what it does and why.

**Goal.** Anyone reading a repository can tell from its files alone what its agents do and why, and organization choices
and stack facts still reach each repository through one reviewed change.

## Context

- The tool is open source and meant for users outside any one organization.
- Agent policies in practice mix generic rules with one organization's choices. One surveyed policy holds, beside its
  generic rules, a deny on synchronising the task database to remotes, allows for deployment tasks, read patterns for
  an observability service, and a commit trailer that triggers CI. Other teams need different choices; a team that
  synchronises its task database across machines needs that deny absent.
- Repositories on the same technology stack start from the same facts: how to lint an edited file, how to run the
  tests, how to build. Of 32 surveyed repositories of one organization, 17 use Node, 7 Python, 3 Terraform and 1 Go,
  some of them several.
- People reviewing a change, and agents working on one, read the files of the repository in front of them. Content
  resolved from another repository or from a per-user cache is not among those files.
- Drift checks run in CI and on developer machines ([0007](0007-ownership.md)). A check whose result changes when
  someone else publishes a change fails pipelines that changed nothing.
- Every change a repository accepts is reviewed in that repository. A shared source pinned per repository still needs
  a reviewed change in every repository to move to a newer version of it.
- Policy entries and actions run with the credentials of the person or pipeline that renders and triggers them.

## Decision

1. The binary contains no organization-specific content: no organization names, hosts, services, policy entries or
   conventions. Built-in content is limited to the invariants ([0011](0011-safety-invariants.md)), to presets keyed by
   technology stack (point 3), and to the starting entries seeded into a newly adopted repository
   ([0015](0015-development-lifecycle.md)).
2. What the tool renders and runs in a repository is determined by that repository's own files and the release it
   pins ([0017](0017-engine-version.md)), and by nothing else. Repository data has no inheritance and no includes, and
   no key brings entries from outside the repository into what the tool renders or runs. Where the data names an
   outside location to fetch from, what it brings acts only as the pinned release or as files committed to the
   repository. Configuration outside the repository is read only to refuse a setting that would weaken an invariant
   ([0012](0012-write-boundary.md)).
3. A preset is a set of starting entries for one technology stack, embedded in the binary. Applying a preset happens
   only on request, at adoption or at any later time. It writes the preset's entries into the repository's own files and
   adds only what they lack: an entry already present stays as it is, an item or value the repository already has is
   never replaced, every difference from the preset is reported, and nothing is removed. Once written, the entries are
   the repository's like any it wrote itself, and nothing records where they came from.
4. An organization's choices reach its repositories as changes to each repository's files, written by a person or an
   agent, for example one following a skill the organization publishes, and reviewed in each repository as a diff.

## Reasons

- **1.** Organization content inside the binary couples releases to organizations, publishes their content in the
  tool, and makes every other user carry entries they never use. Stack presets are different in kind: they follow the
  technology a repository uses, which is a fact about the repository, not a choice of an organization. Seeded starting
  entries are generic and belong to the repository from the moment they are written, so any organization changes them
  like its own.
- **2.** People and agents learn what a repository does from its files. A rule resolved from elsewhere acts in the
  repository while its source sits outside it: an agent working there cannot read it, a reviewer sees its effect in
  the rendered output but not why it is there, and the repository's behaviour cannot be explained from the repository.
  With the repository's files and the pinned release as the only inputs, the same commit renders the same bytes on
  every machine and every day, with no fetch, no cache and no check that fails because someone else published a
  change. A shared source would save only the writing, because each repository still reviews every move to a newer
  version of it. The data does name where releases and installed skills come from, but a release acts only once it
  matches the committed pin and a skill only once it is committed, so both are in the repository's files when they
  act. Reading outside configuration to refuse a bypass adds no behaviour; it removes one that a third-party tool
  would otherwise inject. The trade is that a choice shared by many repositories is written in each of them.
- **3.** Stack facts are the same in every repository on that stack, so the binary can supply them deterministically
  ([0002](0002-division-of-work.md)). Applying only on request keeps every write to a repository's files something a
  person asked for. Adding only what is missing keeps the repository's own entries authoritative and makes a second
  application harmless, so a repository can take a later release's improvements to a preset by applying it again and
  reading the diff. Leaving no record of origin keeps one source of truth for each entry, the file it is in, and gives
  the tool nothing it could later use to change those entries by itself.
- **4.** An organization's choices differ between its repositories (Context), so applying them takes judgement, which
  belongs to people and agents guided by documentation and skills ([0002](0002-division-of-work.md)). Each repository
  reviews the change as it reviews any other, and afterwards the change is in its files.

## Rejected alternatives

### Organization presets shipped inside the binary

At its best, those organizations need no setup. It loses because releases become coupled to organizations, their
content becomes public in the tool, and every other user carries entries they do not use.

### One fork per organization

At its best, each organization has full control. It loses because forks are the divergence this tool exists to
remove.

### Shared sources resolved at every render

A repository names shared sources, pinned to a commit, and the tool merges their entries into every render. At its best,
an organization changes a rule in one place, and each repository takes it with a one-line change of the pinned commit.
It loses because the rule never appears in the repository it governs (point 2). Each repository still reviews every
move, so only the writing is saved. Merging needs fetching, a per-user cache, recorded commits and override rules that a
person must learn before they can predict what a repository does. Without the pin, the same commit renders differently
on different days, and the drift check fails in repositories that changed nothing.

### Applied presets recorded in the manifest

The manifest records which presets a repository applied, so the tool can report a preset's new entries or apply them
again. At its best, a repository learns when a preset it used gains an entry. It loses because the record ties the
repository's own entries to a definition outside its files: applying from it would be the hidden coupling point 2
excludes, and reporting from it nags a repository that removed an entry on purpose. A repository that wants a newer
preset's entries applies it again and reads the diff.

## Consequences

Costs:

- A choice shared by many repositories is written into each of them, and changing it is a change in each.
- A later release's improvements to a preset reach a repository only when someone applies the preset again.
- Two repositories of one organization can hold different entries for the same choice, because each was applied with
  judgement.

Must be maintained:

- The built-in presets for each supported stack, and the rule that applying a preset only adds.

## Confirmation

- CI scans the binary's embedded assets for organization identifiers and fails on a match.
- Validation of every repository file rejects a key that brings entries from outside the repository into what the
  tool renders or runs.
- A fixture renders the same bytes with no network access and an empty per-user cache as on any other machine.
- Applying a preset to a fixture that already holds some of its entries adds only the missing ones and changes no
  existing line; applying it a second time changes nothing.
- A fixture that names an item, or sets a value, that the preset also has keeps its own after the preset is applied,
  and the difference is reported.
