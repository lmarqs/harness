# 0007. A committed manifest records everything the tool owns

## Objective

**Problem.** Withdrawing a practice from a repository should cost as little as adding it, and a hand edit to generated
output must be neither lost nor accepted without anyone noticing. When nobody knows exactly what the tool wrote, removal
is a manual pass through every repository, and generated content cannot be told from content a person wrote.

**Goal.** Withdrawing a practice is one step that removes exactly what the tool wrote, and every hand edit to generated
output is detected.

## Context

- Withdrawing a practice from a repository must cost as little as adding it. Without a record, withdrawal is a manual
  pass through each repository.
- The tool's output lands in three kinds of places: whole files it creates; blocks of text inside files people write,
  such as `AGENTS.md`, `README.md` and `.gitignore`; and keys inside native configuration files it shares with people
  ([0008](0008-native-configuration.md)).
- Adoption also creates files that the repository then edits as its own: its data files
  ([0006](0006-repository-contract.md)) and skeletons for agent content. They mean nothing without the tool, but after
  creation they are not generated.
- A clone without the binary must work ([0010](0010-dependence-on-the-binary.md)): harnesses read their configuration
  from the clone when a session starts. Reviewers must see permission changes as diffs.
- A hand edit to generated output has to be detected. Otherwise the next render discards it silently, or a check passes
  over it.
- JSON allows no comments, so ownership of a key inside a JSON file cannot be marked in the file itself.
- A team that stops using the tool wants one of two outcomes: the repository as it was before adoption, or the setup the
  tool produced, kept and maintained by hand.
- Clone-local state can hold uncommitted work, and cleanup acts only on what the tool recorded creating
  ([0012](0012-write-boundary.md)).

## Decision

1. Ownership is recorded, not inferred. A manifest committed in the repository lists every owned path, managed block and
   owned key set, each with a content hash; every seeded file, created once by adoption and owned by the repository from
   then on, with the hash it was created with; and the schema version ([0005](0005-schema-compatibility.md)).
2. Generated artefacts are committed.
3. Inside files people write, owned text sits between begin and end markers that carry a block id, written in the file
   format's comment syntax. Text outside the markers is never written. Owned whole files that allow comments carry a
   header naming the source to edit, or the operation that changes them, at the first position the format allows.
4. A drift check, run by the pinned release ([0017](0017-engine-version.md)), compares the repository with what its
   sources render and with the manifest, and fails on any difference. No owned artefact is exempt.
5. Removal follows the manifest, in two modes. Both refuse owned content that was edited by hand unless the person
   forces it.
   - Undo returns the repository to its state without the tool. It removes owned content, managed blocks and owned
     keys, and restores imported settings as plain native settings ([0016](0016-permission-adoption.md)). It removes
     the seeded files in the tool's configuration directory, and seeded files elsewhere only while they still match
     their recorded hash; an edited seeded file outside that directory is knowledge and stays, listed. Clone-local
     state stays in place and is listed.
   - Eject ends management and keeps the result. Convention blocks ([0018](0018-conventions.md)) lose their markers and
     stay; tool blocks are removed; rendered native configuration stays as hand-owned content without its headers;
     hooks, entry points and permission entries that call or name the tool's commands are removed and listed; the
     tool's configuration directory ([0014](0014-directory-layout.md)), the wrapper ([0017](0017-engine-version.md))
     and the manifest are deleted.
   - In both modes, a file the manifest does not record is listed and left, even inside the tool's configuration
     directory, which is deleted once nothing else is in it ([0012](0012-write-boundary.md)).

## Reasons

- **1.** Removal and the drift check both need the exact set of things the tool wrote. Markers cannot mark keys inside
  JSON, and without a recorded hash a hand-edited output cannot be told from a generated one. Recording seeded files
  separately lets undo remove them while the drift check leaves them alone, since the repository edits them; their
  creation hash tells an untouched skeleton from content someone wrote.
- **2.** Harnesses read configuration from the clone when a session starts, so it has to exist in every clone, including
  clones without the binary ([0010](0010-dependence-on-the-binary.md)). Committed artefacts also show every permission
  change to reviewers as a diff.
- **3.** Markers let the tool share a file with people without owning the file; block ids let each capability own its
  own block ([0018](0018-conventions.md)). Comment syntax keeps markers invisible in rendered Markdown and inert in
  ignore files. A header tells a person who opens an owned file where to make the change instead; it goes at the first
  position the format allows, after a script's interpreter line or inside a document's front matter, so the file keeps
  working for the program that reads it.
- **4.** Without a check, a hand edit to owned content is lost at the next render. Comparing with both the render and
  the manifest separates a source changed but not rendered from an output edited by hand, so the report names the right
  fix. The pinned release runs the check because it rendered the committed artefacts: upgrades re-render inside the
  same change ([0017](0017-engine-version.md)), so the pinned release's output and the repository agree, and an
  exemption for any artefact would hide exactly the edits the check exists to catch.
- **5.** Undo makes withdrawal as cheap as adoption by deleting exactly what the manifest lists, and restoring imported
  settings returns the permissions the repository had. Seeded files in the configuration directory mean nothing
  without the tool, so they go; a seeded file elsewhere that someone edited, such as a memory file, is knowledge the
  binary never removes ([0011](0011-safety-invariants.md)). Clone-local state can hold uncommitted work, so undo lists
  it instead of deleting it. Eject serves the other outcome: conventions, permissions and hooks keep working without the
  tool ([0018](0018-conventions.md)), while tool blocks, hooks, entry points and permission entries that would call or
  name the absent tool are removed. Refusing hand-edited owned content in both modes protects a change a person made,
  even against the rules. A file nobody recorded is someone's, wherever it sits, so removal leaves it and says so
  ([0012](0012-write-boundary.md)).

## Rejected alternatives

### Infer ownership from headers and markers alone

At its best, there is no extra file. It loses because owned keys inside JSON cannot be marked, generated and hand-edited
content cannot be told apart, and removal has to guess.

### Generate on demand without committing

Render at session start or in CI. At its best, generated files never appear in diffs and cannot drift. It loses because
a clone without the binary has no agent configuration, reviewers lose permission diffs, and harnesses read their
configuration when a session starts.

### Own whole directories

At its best, the ownership rule is one line. It loses because the directories agents read are shared with people and
other tools ([0014](0014-directory-layout.md)).

### One removal mode

Undo only: at its best, one behaviour to document and test. It loses because a team that wants to keep the setup has to
rebuild by hand what undo deleted. Eject only: it loses the way back to the repository as it was before adoption.

## Consequences

Costs:

- Generated files appear in diffs and can conflict in merges; the resolution is to render again, not to merge by hand.
- The manifest is one more committed file, and a hand edit to it is itself drift.
- After an undo, clone-local state remains without its conventions or ignore entries until someone deletes it.
- After an eject, blocks and native configuration have no source to render from; later changes are hand edits.

Must be maintained:

- The marker syntax for each file format and the manifest format, both part of the public contract
  ([0006](0006-repository-contract.md)).
- The classification of every artefact as owned or seeded.

## Confirmation

- Fixture tests: an edit inside a managed block fails the drift check and names the file; an edit outside passes.
- For a fixture adopted and then undone, the working tree differs from its pre-adoption state only in clone-local state
  paths and in seeded files edited after adoption, which the output lists; an unedited seeded file is gone.
- Both removal modes refuse a path whose hash changed unless forced.
- A fixture with an unrecorded file in the configuration directory keeps that file, and the directory, after either
  removal, and lists it.
- After an eject, no marker, wrapper or manifest remains, nor a configuration directory without unrecorded files;
  every convention block's text remains and no tool block does; the fixture's own workflow commands and its native
  hooks that do not call the binary run with the binary absent.
