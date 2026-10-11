# 0013. Organization-specific content lives in each repository's own files

## Objective

**Problem.** An organization wants its choices, such as which services coding agents may read and how work is
delivered, in many repositories, and repositories on one technology stack, or that use one third-party tool, need the
same starting content. Both have to reach each repository without making its behaviour depend on anything its own files
do not show: a person or a coding agent reading a repository must be able to tell from it what it does and why.

**Goal.** Anyone reading a repository can tell from its files alone what its workers do and why, and organization
choices, stack facts and the wiring of a tool still reach each repository through one reviewed change.

## Context

- The tool is open source and meant for users outside any one organization.
- The policies of coding agents mix generic rules with one organization's choices. For example, beside generic rules
  such as a deny on force pushes to the default branch, the policy of `acme/api` holds a deny on synchronising its
  clone-local task database to remotes, allows for acme's deployment tasks, read patterns for the observability service
  acme uses, and a commit trailer that triggers acme's CI. Other teams, at acme or elsewhere, need different choices; a
  team that synchronises its task database across machines needs that deny absent.
- Repositories on the same technology stack start from the same facts: how to lint an edited file, how to run the tests,
  how to build. For example, `acme/api` and `acme/web` are both Node repositories and lint, test and build the same way,
  while acme's Terraform repositories need other entries, and one repository can use several stacks.
- Repositories that use the same third-party tool wire it to the tool the same way. Example: `acme/api` and `acme/web`
  both run ralph-tui (0.12) with each agent entry naming the same attempt command, and both mark a Beads (1.3.1) task in
  progress when work on it starts and close it when it is verified ([0022](0022-tool-integration.md)).
- Some starting content is a whole file rather than entries, such as a loop runner's configuration file, and the team
  edits it to its needs from then on.
- Some programs a team uses are run by no other organization, such as an organization's internal command-line
  programs. Example: every acme service repository deploys with acme's own `acme-deploy` program, which no other
  organization runs.
- People reviewing a change, and coding agents working on one, read the files of the repository in front of them.
  Content resolved from another repository or from a per-user cache is not among those files.
- Content identified by a cryptographic hash, such as SHA-256, is the same bytes wherever and whenever it is fetched;
  any change to the content changes the hash.
- Drift checks run in CI and on developer machines ([0007](0007-ownership.md)). A check whose result changes when
  someone else publishes a change fails pipelines that changed nothing.
- Every change a repository accepts is reviewed in that repository. A shared source pinned per repository still needs
  a reviewed change in every repository to move to a newer version of it.
- Policy entries and actions run with the credentials of the person or pipeline that renders and triggers them.

## Decision

1. The binary contains no organization-specific content: no organization names, hosts, services, policy entries or
   conventions. The content the release carries is limited to the invariants ([0011](0011-safety-invariants.md)), the
   templates and starting entries of the tool's own files seeded into a newly adopted repository
   ([0007](0007-ownership.md), [0015](0015-development-lifecycle.md)), and the documentation and skill the tool ships
   ([0002](0002-division-of-work.md)). It carries no ingredient, no station and no recipe
   ([0022](0022-tool-integration.md), [0024](0024-ingredients-and-stations.md), [0025](0025-recipes.md)).
2. What the tool renders and runs in a repository is determined by that repository's own files, the release it pins
   ([0017](0017-engine-version.md)) and the ingredients it pins ([0022](0022-tool-integration.md)), and by nothing else.
   Repository data has no inheritance and no includes, and no key brings entries from outside the repository into what
   the tool renders or runs: a station's entries reach the repository only by being written into its files (point 3),
   and the repository's record of the stations it applied acts on nothing ([0024](0024-ingredients-and-stations.md)); a
   recipe runs as its copy in the repository's files is written, and the origin the copy records acts on nothing
   ([0025](0025-recipes.md)). Where the data names an outside location to fetch from, what it brings acts only when it
   matches the hash the repository committed.
3. A station ([0024](0024-ingredients-and-stations.md)) carries the starting content for one way of running the
   lifecycle, such as one technology stack or one way of working with workers: entries for the repository's data files,
   and templates, which are whole files. Applying a station happens only on request, at adoption or at any later time.
   It writes the station's entries into the repository's own files and adds only what they lack: an entry already
   present stays as it is, an item or value the repository already has is never replaced, every difference from the
   station is reported, and nothing is removed. A template is rendered once from the repository's data, written only
   when its file is absent, and never rendered again; an existing file is reported and left as it is. Once written,
   entries and files are the repository's like any it wrote itself, and nothing records which station wrote which of
   them. An ingredient's renders and floor rules are not entries: they are rendered output
   ([0022](0022-tool-integration.md)).
4. An organization's choices reach its repositories as changes to each repository's files, written by a person or a
   coding agent, for example one following a skill the organization publishes, or by applying a station the
   organization publishes, and reviewed in each repository as a diff.
5. An organization's stations and ingredients reach its repositories the way every station and ingredient does: the
   organization publishes them in a cookbook of its own, never inside the binary, and a repository that uses one pins it
   in its data by version and content hash ([0022](0022-tool-integration.md), [0024](0024-ingredients-and-stations.md)).
   Each acts only as the bytes its pin names ([0026](0026-cookbook-trust.md)). A station's entries and templates reach
   the repository as point 3 describes. An ingredient for one of the organization's own programs renders on every render
   while the repository pins it, and its floor can make the organization's own deny rules about that program a floor. An
   organization's recipes reach its repositories from its cookbook as point 6 describes.
6. A recipe ([0025](0025-recipes.md)) reaches a repository as a whole copy, written into the repository's own files on a
   person's request and shown as a diff first, and the copy is the repository's from then on. Adding a recipe never
   replaces one the repository already holds. The copy records its origin: the cookbook, the version and the content
   hash it was copied from. Updating it, also on request, merges three versions: the repository's copy, the version its
   origin names and the newer version. A change only the newer version makes is applied, a change only the repository
   made stays, and where both changed the same lines both are left marked in the copy for a person or a worker to
   resolve. The whole result is shown as a diff before anything is written, and once it is written the origin names the
   newer version, whether conflicts remain or not. A recipe that holds conflict markers is invalid and cannot run until
   they are resolved; resolving them is all that remains of the update, and the tool keeps no record of a merge in
   progress.

## Reasons

- **1.** Organization content inside the binary couples releases to organizations, publishes their content in the tool,
  and makes every other user carry entries they never use. Knowledge of programs, stacks, ways of working and kinds of
  change inside the binary would couple releases to those programs in the same way, which is why the release carries no
  ingredient, no station and no recipe either ([0022](0022-tool-integration.md)). What the release keeps is about the
  tool itself and the same in every repository: its invariants, the skeletons of its own files and its own
  documentation. Seeded templates and starting entries are generic and belong to the repository from the moment they are
  written, so any organization changes them like its own.
- **2.** People and coding agents learn what a repository does from its files. A rule resolved from elsewhere acts in
  the repository while its source sits outside it: a coding agent working there cannot read it, a reviewer sees its
  effect in the rendered output but not why it is there, and the repository's behaviour cannot be explained from the
  repository. With the repository's files and what they pin by hash as the only inputs, the same commit renders the
  same bytes on every machine and every day, and no check fails because someone else published a change (Context). A
  shared source would save only the writing, because each repository still reviews every move to a newer version of
  it. The data does name where the release, the ingredients and the stations come from, but each acts only once it
  matches the hash the repository committed, so the repository's files fix exactly what acts; a station acts only when
  a person applies it, and the record that it was applied is no input at all. The binary reads no configuration from
  outside the repository; the programs it starts read their own ([0022](0022-tool-integration.md)). The trade is that
  a choice shared by many repositories is written in each of them.
- **3.** Stack facts are the same in every repository on that stack, and a program's wiring is the same in every
  repository that uses it (Context), so a station can supply them deterministically ([0002](0002-division-of-work.md)).
  Applying only on request keeps every write to a repository's files something a person asked for. Adding only what is
  missing keeps the repository's own entries authoritative and makes a second application harmless, so a repository can
  take a later version's improvements to a station by applying it again and reading the diff. A template is a whole file
  that the team then edits, so it cannot be merged entry by entry: writing it only where the file is absent never
  replaces the repository's version, and never rendering it again keeps the team's edits. Leaving no record of which
  station wrote which entry keeps one source of truth for each entry and file, the file itself, and gives the tool
  nothing it could later use to change them by itself. An ingredient's renders differ in kind from entries: they are the
  program's side of the repository's data, which nobody writes by hand, so they follow the ingredient on every render,
  and a repository that stops pinning an ingredient loses exactly its renders while the entries a station applied stay
  ([0007](0007-ownership.md), [0024](0024-ingredients-and-stations.md)).
- **4.** An organization's choices differ between its repositories (Context), so applying them takes judgement, which
  belongs to people and coding agents guided by documentation and skills ([0002](0002-division-of-work.md)). A station
  the organization publishes writes the same kind of change mechanically, and the person who applies it still reviews
  the diff, together with every difference point 3 reports. Each repository reviews the change as it reviews any other,
  and afterwards the change is in its files.
- **5.** An organization's own program (Context) needs an ingredient like any other program, and an organization's way
  of working a station like any other. Inside the binary, either loses for the reasons of point 1.
  - Publishing them in the organization's own cookbook gives them the path every ingredient and station takes
    ([0022](0022-tool-integration.md), [0024](0024-ingredients-and-stations.md)), so no organization's content is
    special. Pinning by content hash makes each an input like the release: the repository's files fix exactly which
    bytes act, a newer version reaches a repository only through a reviewed change of the pin, and publishing a version
    breaks no check anywhere (Context).
  - A station's entries and templates follow point 3, and a recipe point 6, so the organization's choices of what
    workers may do still land in each repository's own files. What an ingredient adds on every render is what any
    ingredient renders: the repository's own data in the program's format, and floor rules, which only deny or refuse
    ([0022](0022-tool-integration.md)).
  - The trade, shared by every ingredient: the description sits in a per-user cache, not among the repository's files
    (Context), so a reader sees its renders in the committed output under its name but reads the description itself at
    its source. An organization's floor rules bind every repository that pins its ingredient, which lets the
    organization make its own deny rules a floor. Every machine that renders such a repository, CI included, fetches the
    ingredient before it can render.
- **6.** A recipe is a practice a team adapts to its repository ([0025](0025-recipes.md)), so, like a station's content,
  it is written once into the repository's files and is the repository's own afterwards, read where every reader of the
  repository reads it (point 2).
  - Unlike a station's entries, it stays one whole unit in files of its own, so it can carry the record of where it came
    from in its own file, in view of every reader. That record names one version of the whole recipe, not which entries
    of shared files came from where, the per-entry record rejected below. With that version as the common ancestor, a
    three-way merge tells the team's changes from the cookbook's, as a project template tool's update does
    ([0025](0025-recipes.md)). The update brings only the cookbook's changes, a deliberate edit by the team, such as a
    step it removed, survives, and only lines both changed need a person.
  - A station's entries are spread through files that also hold the repository's own entries and other stations', and
    nothing records which station or version wrote a template (point 3), so neither has a copy whose ancestor is known,
    and both keep the rule that applying only adds. Adding never replaces a recipe the repository holds, for the reason
    an entry already present stays: the repository's version is the one its team chose.
  - A recipe with conflicts left in it would run steps nobody chose, half the repository's and half the cookbook's, so
    it is invalid until a person or a worker resolves them. Moving the origin with the merge, conflicts or not, keeps
    the whole state of the update in the copy's own file: the markers say what is left to decide and the origin which
    version the copy follows, so resolving the markers is an ordinary edit and nothing else has to happen afterwards. A
    record of a merge in progress would be state outside the repository's files that the next update would have to
    honour (point 2).
  - The trade: an update can leave conflicts that a person or a worker resolves before the recipe runs again, and a
    cookbook's fix reaches a copy only when someone updates it.

## Rejected alternatives

### Organization ingredients and stations shipped inside the binary

At its best, the organizations whose content ships in the binary need no source of their own, no pin and no fetch. It
loses because releases become coupled to organizations, their content becomes public in the tool, and every other user
carries descriptions they do not use. An ingredient or a station in the organization's own cookbook gives it the same
content without any of the three (point 5).

### One fork per organization

At its best, each organization has full control. It loses because forks are the divergence this tool exists to
remove.

### Shared sources resolved at every render

A repository names shared sources, pinned to a commit, and the tool merges their entries into every render. At its best,
an organization changes a rule in one place, and each repository takes it with a one-line change of the pinned commit.
It loses because the rule never appears in the repository it governs (point 2): an allow, an action or a convention
merged at render time acts from a source no reader of the repository sees. Each repository still reviews every move, so
only the writing is saved, and merging needs override rules that a person must learn before they can predict what a
repository does. Without the pin, the same commit renders differently on different days, and the drift check fails in
repositories that changed nothing. An ingredient is pinned the same way but merges no entry into a render: on every
render it adds only the program's format and floor rules, which can only narrow what the repository allows, and a
station's entries are written into the repository's files once (point 3).

### Applied entries recorded with their station

The manifest records which station wrote each entry, so the tool can report a station's new entries or apply them again.
At its best, a repository learns when a station it used gains an entry. It loses because the record ties the
repository's own entries to a definition outside its files: applying from it would be the hidden coupling point 2
excludes, and reporting from it nags a repository that removed an entry on purpose. A repository that wants a newer
station's entries applies it again and reads the diff. The repository's data records which stations it applied, and at
which version, but not what each wrote ([0024](0024-ingredients-and-stations.md)). A recipe's origin is no such record:
it names where one whole recipe came from, in that recipe's own file (point 6).

### Only content applied once comes from outside

Cookbooks carry only entries and templates, applied once; the binary renders every program's format and fills the
contracts itself. At its best, nothing from outside the repository acts on a render, and point 2 needs no input beyond
the repository's files and the release. It loses because the binary would then carry knowledge of every program it
renders for, which [0022](0022-tool-integration.md) rejects, an organization's own program or a worker the binary does
not know could not be described at all, and a program used without a floor of its own runs without the deny rules
that protect it ([0011](0011-safety-invariants.md)).

## Consequences

Costs:

- A choice shared by many repositories is written into each of them, and changing it is a change in each.
- A later version's improvements to a station's entries reach a repository only when someone applies the station again,
  and improvements to a template never reach a repository that already has the file.
- Two repositories of one organization can hold different entries for the same choice, because each was applied with
  judgement.
- An ingredient is read at its source, not in the repository, and a repository renders only on machines that have
  fetched the ingredients it pins.
- An organization's ingredient can make the organization's own deny rules a floor in every repository that pins it, out
  of the reach of that repository's policy.
- A recipe update can leave conflicts marked in the copy for a person or a worker to resolve, and a recipe's later
  improvements reach a repository only when someone updates it there.

Must be maintained:

- The rule that applying a station only adds, the three-way merge of a recipe update, and the scan that keeps
  organization identifiers out of the release.

## Confirmation

- CI scans the binary's embedded assets for organization identifiers and fails on a match.
- Validation of every repository file rejects a key that brings entries from outside the repository into what the
  tool renders or runs.
- A fixture rendered with no network access and a per-user cache pre-filled with its pinned ingredients renders the
  same bytes as on any other machine; with an empty cache it renders nothing and fetches nothing.
- Applying a station to a fixture that already holds some of its entries adds only the missing ones and changes no
  existing line; applying it a second time changes nothing.
- A fixture that names an item, or sets a value, that the station also has keeps its own after the station is applied,
  and the difference is reported.
- Applying a station to a fixture that already has a file the station's template would write leaves that file
  byte-identical and reports it.
- A fixture that stops pinning an ingredient loses that ingredient's renders, and the entries and templates stations
  applied stay byte-identical.
- Updating a fixture recipe whose copy and newer version changed different lines applies the newer version's changes and
  keeps the fixture's; where both changed the same lines, both are marked and nothing else changes; in both cases the
  origin then names the newer version. Resolving the marked fixture by hand leaves a valid recipe, and a second update
  to the same version finds nothing to merge.
- Adding a recipe that a fixture already holds leaves the fixture's copy byte-identical and reports it.
- Validation refuses a fixture recipe that holds conflict markers, and no step of it runs.
- A fixture recipe whose origin names a version its source does not publish runs as its copy is written.
