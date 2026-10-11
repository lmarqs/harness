# Generated-content standards

## Objective

**Problem.** A tool that writes into a repository shares its files with developers, reviewers, other tools and teammates
who never use it. When nobody can tell the tool's content from a person's, a hand edit is silently overwritten, or
someone's work is deleted with the tool's. A reviewer cannot judge a change hidden among lines that move on every run, a
convention only the tool knows disappears when it leaves, and a team that stops using it cannot get a working repository
back.

**Goal.** Anyone reading an adopted repository, with or without the tool, can tell which content the tool wrote, and
every change to that content, by the tool or by hand, is visible in review. When the tool leaves, the repository works,
has lost nothing a person wrote, and keeps the conventions the team chose to keep.

## Overview

These rules apply to anything `the-cook` writes into a repository.

## Kinds of generated content

| Kind | Example | How ownership is marked |
| --- | --- | --- |
| Owned whole file that allows comments | the wrapper `cook` (after its `#!` line), `.cook/wrapper.yaml`, a whole rules file a worker ingredient renders, the shipped skill `.agents/skills/the-cook/SKILL.md` (as a YAML comment right after the opening `---` of its front matter) | header line, plus the manifest |
| Owned whole file that allows no comments | `.cook/manifest.json` and other JSON files | the manifest |
| Seeded file | `.cook/config.yaml`, `.cook/policy.yaml`, `.cook/hooks.yaml`, the `.agents/memory/` skeleton | the manifest records it as seeded, with the hash it was created with: written once, then the repository's |
| Template file | a file a station's template wrote, such as a loop runner's configuration file from a station that uses the loop runner | nothing: seeded content the manifest does not record, written once and only where the file was absent, then the repository's |
| Recipe | `.cook/recipes/<name>/`, written by `the-cook recipe add`, `recipe update` or `recipe create`, or by applying a station that adds the recipe | nothing but the recipe's own `origin`, when it was copied from a cookbook: the repository's from the moment it is written; removal deletes `.cook/recipes/`, listing each recipe first |
| Files a recipe run writes | the files a recipe's `run` and `template` steps write in the run's workspace, such as a migration and its test | nothing: ordinary changes on the run's branch, verified and delivered as the change they belong to |
| Managed block inside a human file | the [block ids](#block-ids) in `AGENTS.md`, `README.md`, and the files ingredients render blocks into, such as a worker's instructions file and the version control system's ignore file | opening and closing markers, plus the manifest |
| Owned keys inside a native file | the permission keys, `the-cook hook` entries (hooks on worker events, notification and context hooks) and rendered raw hook entries in a worker's settings files, as its ingredient names them | the manifest |
| Link | a link from a worker's native skill directory to `.agents/skills/the-cook`, as its ingredient names it | the manifest |
| State | the paths the profile sets (`state.dir`, `workspaces.dir`; under `.work/` by default), including the lifecycle log `lifecycle.jsonl` in `state.dir` | gitignored by managed blocks; never committed, never listed as content, never inside `.cook/` or `.agents/` |

The manifest (`.cook/manifest.json`) is the record of ownership. Content not listed in it does not belong to `the-cook`,
whatever it looks like. Why: a header or a marker can be copied into a file `the-cook` never wrote, and only the record
says what `the-cook` created.

The manifest also records the owner of each piece of owned content: the binary, for what it renders from the neutral
model alone (`cook`, `wrapper.yaml`, the shipped skill, the manifest, the `AGENTS.md` and `README.md` blocks), or the
[ingredient](../features/ingredients.md#renders) whose renders produce it (a worker ingredient's keys in its worker's
settings file and its instructions-file import, a whole rules file, the version-control ingredient's ignore-file
blocks). Renders come only from ingredients: a station has none, so no owned content belongs to a station. Deleting an
ingredient from `ingredients:` and rendering removes exactly the content the manifest records under that ingredient.
Why: the binary contains no program's format, so what a program's files hold is the ingredient's, and only an owner in
the record tells a render which content leaves with the ingredient ([ADR 0007](../adrs/0007-ownership.md),
[ADR 0022](../adrs/0022-tool-integration.md)).

Owned content is rendered and replaced on every render, and a hand edit to it is drift. Seeded content is written once
and then edited by people and workers. `the-cook` never re-renders it. It writes to the repository files again only when
a person pins an ingredient (`the-cook ingredient pin`), which adds or moves that pin, or applies a station
(`the-cook station apply`), which adds only what they lack, and it writes a recipe only when a person adds, updates or
creates it (`the-cook recipe add`, `recipe update`, `recipe create`) or applies a station that adds it. Why: the seeded
files hold the repository's choices and knowledge, which `the-cook` cannot recompute.

A template is seeded content of its own kind: a whole file in a station or in `scaffold`, rendered once with values from
the profile, written only when the file is absent, and from then on the repository's own file. `the-cook` never renders
it again and never removes it, and the manifest does not record it. Why: a template is usually a file another program
reads, such as a loop runner's configuration, which the team then edits for that program. A record would make it look
owned, and removing it with the station's record would then delete a file the team edited
([configuration-standards.md](configuration-standards.md#what-each-part-writes)).

A recipe is the repository's own from the moment it is written, like a seeded file, but the manifest does not record
it: the recipe's own `origin` says where a copy came from, and a recipe the repository wrote has none. A recipe's
`template` step is not a template in the sense above: it writes a file into a recipe run's workspace, refusing a path
that exists, as part of the change that run makes, which reaches review with the rest of that change. Why: a recipe is
the repository's description of its own work, which it edits, updates and deletes like any file it owns, and what a
recipe run writes is the change itself ([configuration-standards.md](configuration-standards.md#recipes)).

`the-cook scaffold` seeds the repository files and, with `--cookbook`, applies the stations whose detect rule matches
the repository as it seeds them, the recipes they add included
([configuration-standards.md](configuration-standards.md#applying-a-station)). The `.agents/memory/` skeleton is seeded
when the memory module is enabled, by `the-cook scaffold` or by the first `the-cook render` after the module is enabled,
and is never re-rendered. The recorded creation hash is what lets undo (`the-cook remove`) tell an untouched skeleton
from content someone wrote ([ADR 0007](../adrs/0007-ownership.md)).

## Header

Owned files that allow comments carry exactly one header line, at the first position the format allows: the first
line; after the `#!` line in a script; right after the opening `---` of YAML front matter in a Markdown file such as
the shipped skill. Why: the file must keep working for the program that reads it, so the header goes where that program
ignores it ([ADR 0007](../adrs/0007-ownership.md)).

Rendered files carry:

```text
# generated by the-cook (schema 1): edit .cook/<source> and run ./cook render
```

The pin `.cook/wrapper.yaml`, which no source renders, carries the operation that changes it:

```text
# generated by the-cook: change with ./cook wrapper --version <tag>
```

- The comment syntax adapts to the file type; the words do not, so a search for `generated by the-cook` finds every
  generated file.
- `<source>` names the repository file that produces this output, so a reader knows where to make the change.
- `manifest.json` is JSON and carries no header; the manifest itself is its record.
- The header carries the schema, never the binary version, date or user, so an upgrade or another author produces no
  diff.

## Managed blocks

A block opens with `cook:<id>` and closes with `/cook:<id>`, in the comment syntax of the file:

```markdown
<!-- cook:workspaces -->
Workspaces in this project are created in `.work/workspaces/<name>`, on branch `work/<name>`, from committed code.
<!-- /cook:workspaces -->
```

```gitignore
# cook:workspaces
/.work/workspaces/
# /cook:workspaces
```

- Markdown uses HTML comments (`<!-- cook:<id> -->` … `<!-- /cook:<id> -->`); `.gitignore`, YAML, TOML and
  shell files use `#` comments (`# cook:<id>` … `# /cook:<id>`). Why: markers in the file's own comment syntax
  never change how the file reads or parses.
- Block ids come from a documented, closed set. An id is never renamed except through a schema migration, because
  the manifest and the markers already committed in repositories refer to it.
- A file holds at most one block per id, so an id names exactly one place.
- `the-cook` owns everything between the markers and replaces it on render. Everything outside them is never read
  for writing, reordered or reformatted, because it belongs to whoever wrote it.
- A new block is appended at the end of the file. Once present, a block may be moved anywhere in the file by a person,
  and `the-cook` updates it where it is, so a team can arrange its own file.
- Block text comes only from the binary's templates and the repository's values: fixed sentences that state a
  convention with its configured values, tables, lists, one-line imports, and, in the `context` block, the text of the
  repository files and installed skills that `static` entries name. It is never free prose, opinion or knowledge,
  because the binary does only deterministic work ([ADR 0002](../adrs/0002-division-of-work.md)). No source
  outside the repository adds text to a block, because text there acts in every session while its source would sit
  where nobody working in the repository reads it ([ADR 0013](../adrs/0013-organization-content.md)).
- A `.gitignore` block omits any path the repository tracks, so ignoring never hides a committed file.
- Each module writes its own `.gitignore` entries, even when another module's entry already covers them (for example
  `/.work/` and `/.work/workspaces/`). Why: removing one module must never un-ignore another module's paths.

### Block classes

Every block belongs to one of two classes. Why: some of the text `the-cook` writes states facts that stay true without
it, and some only explains how to use it. Eject (`the-cook remove --eject`) must keep the first and must not leave the
second behind ([ADR 0018](../adrs/0018-conventions.md)).

| Class | States | Mentions `the-cook` commands | On `the-cook remove --eject` |
| --- | --- | --- | --- |
| Convention block | facts that hold without `the-cook`: where things are, how work is tracked, what is permitted | never | markers removed, text kept |
| Tool block | how to use `the-cook`: its commands, its configuration, how it verifies and delivers | MAY, as `./cook <command>` | removed |

- A convention block MUST read correctly after eject, so it never names `cook`, `the-cook` or `.cook/`.
- The `policy` convention block describes the effective permissions in the native files, never `policy.yaml`, so it
  stays true after eject.

### The `AGENTS.md` budget

All the blocks `the-cook` writes in `AGENTS.md` together stay within 40 lines by default (`blocks.agents_md_budget` in
the profile), and each block within 8 lines. Detail lives in `the-cook explain <topic>` and the shipped skill.
`the-cook doctor` reports a note when the budget is exceeded, for example by a `lifecycle` block listing many actions.
Why: workers read `AGENTS.md` at the start of every session, so every line `the-cook` adds is paid in every session and
dilutes the repository's own instructions.

Static standing-context entries in the `context` block count against `context.budget`
([configuration-standards.md](configuration-standards.md#standing-context)), not against this budget, because their
length is the repository's own choice of directives.

### Block ids

Block ids are per module, per ingredient or per convention. A module or an ingredient owns its blocks: turning the
module off, deleting the ingredient from `ingredients:` and rendering, or removing the module with
`the-cook remove --module <name>`, removes them, because a block that states a convention nobody follows any more is
wrong. These blocks belong to every adopted repository, whatever the modules: `commands`, `layout`, `workspaces`,
`lifecycle` and `policy` in `AGENTS.md`; `commands` in `README.md` when it exists; `state` and `workspaces` in the
ignore file the version-control ingredient renders. Why: workspaces and lifecycle events exist in every adopted
repository, so their conventions and conditions are stated in every one.

| File | Block id | Class | Owner | States |
| --- | --- | --- | --- | --- |
| `AGENTS.md` | `commands` | tool | the binary | how to run things: `./cook <command>` |
| `AGENTS.md` | `layout` | tool | the binary | what `.cook/`, `.agents/` and the state directories hold |
| `AGENTS.md` | `verify` | tool | the binary | the repository's checks: lint, build, the test lanes and when each is required |
| `AGENTS.md` | `lifecycle` | tool | the binary | the conditions work meets before it leaves the clone, and each event's actions in order |
| `AGENTS.md` | `workspaces` | convention | the binary | where managed workspaces are created, on which branch, from which base |
| `AGENTS.md` | `policy` | convention | the binary | the effective permissions by decision, with each floor rule marked with the ingredient it comes from (`(*floor rule of the git ingredient*)` for an ingredient named `git`) and each entry some worker does not enforce, with those workers |
| `AGENTS.md` | `memory` | convention | the binary | the index of `.agents/memory/` |
| `AGENTS.md` | `context` | convention | the binary | the standing directives declared `static`, inline or as pointer lines |
| `README.md` | `commands` | tool | the binary | how to run things, as in `AGENTS.md` |
| the version control system's ignore file | `state` | convention | the version-control ingredient | `/<state.dir>/` |
| the version control system's ignore file | `workspaces` | convention | the version-control ingredient | `/<workspaces.dir>/` |
| a worker's instructions file | an id its ingredient declares | convention | the worker ingredient, unless the file already imports `AGENTS.md` | one line importing `AGENTS.md`, in the syntax the worker reads |
| any file an ingredient names | an id the ingredient declares | as the ingredient declares | that ingredient | what the ingredient states, such as the ignore entries for a program's session files |

### Conventions in blocks

Every convention a module establishes is written where people and other tools read it, so it remains true without
`the-cook` ([principles.md](principles.md#8-conventions-outlive-the-tool)):

- In `AGENTS.md`, as a convention block of plain-language statements: where workspaces are created, what is permitted,
  which directives always apply.
- For each path, in a managed block of the version control system's ignore file, which the version-control
  ingredient renders.
- The text names the configured value of each path, never a default the profile overrode, because a reader acts on
  what the text says.
- A block's text describes the convention as fact. It never says it is managed by `the-cook` beyond the marker, and it
  never depends on the binary to be true, so people, workers and the other tools in the repository can rely on it after
  `the-cook` is ejected.

## Owned keys in native files

Why: native files are shared with people and other tools, so `the-cook` owns only the keys and entries the renders of
the worker's ingredient name, and nothing else ([ADR 0008](../adrs/0008-native-configuration.md),
[ADR 0022](../adrs/0022-tool-integration.md)).

- `the-cook` changes only the keys it owns. Every other key, its value, its order and its formatting are preserved byte
  for byte.
- Owned permission lists belong to `the-cook` entirely: an entry added there by hand is drift. To keep it, add it to
  `policy.yaml`. Adoption does this for entries that existed before `the-cook`. Why: two writers of one list cannot both
  be right after the next render. For example, a rule typed by hand into a worker's owned permission list is drift,
  while `[make, lint]` under `shared.allow` in `policy.yaml` renders as that same rule for every worker, in the form
  each worker ingredient writes.
- Hook entries are owned one by one. `the-cook` owns the `the-cook hook` entries (hooks on worker events, notification
  hooks and the hooks that inject standing context) and the native entries it renders from `raw` declarations in
  `hooks.yaml`. Hooks added by other tools or by hand are preserved where they are. A native setting that holds a single
  program, such as a notification command, is owned only when it is absent or already runs `the-cook hook`; when a
  repository has its own program there, `the-cook` leaves it in place and does not render that event for the worker, and
  `the-cook doctor` notes it. The worker ingredient names such settings.
- A raw hook renders as a native entry that runs its own command, not `the-cook hook`, so it keeps working
  without the binary. It is recorded in the manifest as owned: a hand edit to it is drift, `the-cook remove` removes
  it, and `the-cook remove --eject` keeps it as hand-owned.
- A `the-cook hook` entry calls the wrapper at the repository root with `--no-download`
  (`cook --no-download hook <event>`), so it never downloads.
- Local override files a worker reads are never written or listed. They are read only by
  `the-cook scaffold --import local`, because they are personal and usually untracked.

## Determinism

Why: two teammates rendering the same commit must produce the same diff, or every render is noise in review.

- The same repository files, pinned release and ingredients pinned by hash produce the same bytes on every supported
  operating system, with no network access, because nothing else is an input
  ([ADR 0013](../adrs/0013-organization-content.md), [ADR 0022](../adrs/0022-tool-integration.md)). The per-user cache
  supplies only ingredients whose archive matches their `sha256`, and neither a station nor a recipe is an input to
  rendering. A new release may render differently, the shipped skill and `cook` included; a new version of an ingredient
  may render differently, its floor included. Moving either pin (`the-cook wrapper --version <tag>`,
  `the-cook ingredient pin`) renders the repository in the same change, so every such difference reaches review in that
  diff ([compatibility-standards.md](compatibility-standards.md#rendered-output-across-releases),
  [ADR 0017](../adrs/0017-engine-version.md)). Rendered content never records the release that rendered it
  ([ADR 0005](../adrs/0005-schema-compatibility.md)).
- Generated content contains no timestamps, binary versions, usernames, hostnames, absolute paths or random values.
- Order follows the source file's declaration order where one exists, and a documented sort otherwise.
- Text files are UTF-8, use LF line endings and end with one newline.

## Idempotency and drift

- Running `the-cook render` twice in a row changes nothing the second time, so render is safe to run whenever in doubt.
- A hand edit to owned content is drift. `the-cook render --check` exits 2 and names each file, block and key that
  drifted. `the-cook render` replaces the drifted content and lists what it replaced; `--dry-run` shows it first. Why:
  the hand edit belongs in the source, where the next render keeps it.
- Drift is detected by comparing against the manifest's hashes, so it is reported even when the repository files
  have not changed.
- The pinned release runs the drift check, and no owned file is exempt, the shipped skill included. The pinned
  release renders the skill the repository has, because upgrades render in their own change. A binary other than the
  pinned release (`COOK_BINARY`) gets a `the-cook doctor` note naming both versions, because its drift results may
  differ. Why: an exemption would hide exactly the hand edits the check exists to catch
  ([ADR 0007](../adrs/0007-ownership.md)).

## Review

- Generated content is committed so that every change reaches review as a diff and a clone works without the binary.
- A change to one repository file produces the smallest rendered diff that expresses it; unrelated lines do not move,
  so a reviewer reads only the effect of the change.
- The binary commits in a repository in three cases only: `the-cook attempt --commit` commits a task's verified work in
  the checkout it ran in; `the-cook make --commit` commits a recipe run's work, after its replay passes, on the branch
  of the run's workspace, with the recipe and its inputs in the commit body; and `the-cook fleet <command> --deliver`,
  which runs a command across a listed set of repositories, commits the tool-owned changes on a new local branch
  `<branch_prefix><command>`, cut from the fetched default branch, then fires that repository's `deliver` actions. Any
  other commit, push or pull request is a declared action. Why: a commit is the start of delivery, and delivery is the
  repository's decision. The three exceptions commit only work the binary itself verified or rendered, in a checkout
  its caller chose for that work or on a branch it created.
- Reviewers judge the source change (`policy.yaml`, `hooks.yaml`, `config.yaml`); the rendered diff shows its effect
  on each worker.
- `the-cook station apply` writes its additions and renders them in the same change, so the station and ingredient pins,
  the entries, the files its templates wrote, the recipes it added and their rendered effect reach review as one diff,
  and the drift check passes on it. `the-cook ingredient pin` does the same for the pins it writes.
- `the-cook recipe add` and `the-cook recipe update` show the recipe they copy or merge as a diff before writing it, and
  `recipe update` writes each conflict into the copy with conflict markers and moves `origin` at once, so the person or
  worker who resolves the markers does so in the same change and nothing else is left to do. What a recipe run writes
  reaches review as the change it makes, delivered like any other work.
- A version upgrade reaches review as one diff: `wrapper.yaml` (the version and its checksums) and every artefact the
  new release renders differently, `cook` and the shipped skill included. The checksums were written only after
  the running binary verified the release's signed checksum file, so reviewers confirm that the version is the one
  they expect ([compatibility-standards.md](compatibility-standards.md#release-trust)).

## Removal

Removal has two modes. Undo takes `the-cook` and what it wrote out of the repository. Eject stops `the-cook` managing
the repository and keeps what it wrote. Reference: [`the-cook remove`](../features/remove.md). Why: a trial adoption
must be fully reversible, and a team that leaves `the-cook` must keep the conventions, permissions and hooks it came to
rely on.

Both modes:

- act only on what the manifest lists and on `.cook/recipes/`, and never delete any other file: a file the manifest
  does not record, elsewhere in `.cook/`, is listed and left, and `.cook/` is deleted once nothing else is in it, so
  what removal touches is known before it runs ([ADR 0007](../adrs/0007-ownership.md));
- list each recipe in `.cook/recipes/` in their plan before deleting it, so a person can keep a copy first. Why: a
  recipe is the repository's own description of its work and means nothing without `the-cook`, so it goes with the
  other repository files in `.cook/`, and the plan is the last moment to keep it;
- refuse a listed owned file whose content no longer matches its recorded hash (it was edited by hand), name it, and
  exit 3 unless `--force` is given, because the edit may be someone's only copy of their work;
- accept `--dry-run`, which lists every path, block, entry and key they would change;
- leave text and keys outside owned content byte for byte, so `git status` afterwards shows only changes to content
  `the-cook` owned or seeded and the deleted recipes.

### Undo: `the-cook remove`

`the-cook remove` (or `the-cook remove --module <name>` for one module) returns the repository to a working state
without `the-cook`:

- It deletes the owned files and links, removes every managed block including its markers and text, and removes owned
  keys and entries from native files. Without `--module`, it also deletes `cook`, `wrapper.yaml`, the manifest, the
  seeded repository files in `.cook/` and the recipes in `.cook/recipes/`, listing them first, and then `.cook/` once
  nothing else is in it. Their history stays in version control. Entries a station added are part of those files and go
  with them, and so do the recipes it added. Why: the seeded files and the recipes have no meaning without `the-cook`.
- A seeded file outside `.cook/` (the `.agents/memory/` skeleton) is deleted only while it still matches the hash
  the manifest recorded when it was seeded; an edited one is kept and listed. Why: an untouched skeleton is generated
  output, while a file someone wrote into is the repository's knowledge, which `the-cook` never removes
  ([ADR 0007](../adrs/0007-ownership.md)).
- Permission entries and hooks that adoption imported from project-scope native files are written back as plain
  native settings, so the workers keep behaving as before. Entries imported from the `local` or `user` scope are not
  written back, because their source files were never changed and still hold them.
- State directories are left in place and listed in the output, for a person to delete, because workspaces can hold
  uncommitted work. Their `.gitignore` blocks are gone, so they show as untracked until deleted.

### Eject: `the-cook remove --eject`

`the-cook remove --eject` stops managing the repository and keeps the result as the repository's own content:

- It strips the markers of every convention block and keeps its text, and keeps the `.gitignore` entries, so the
  conventions stay written ([principles.md](principles.md#8-conventions-outlive-the-tool)).
- It removes every tool block, so no instruction names a command that no longer exists.
- It keeps rendered native files and owned keys, floor rules included, as hand-owned content, removing their
  generated-file headers.
- It removes hook entries that call `the-cook`, and permission entries that name a `the-cook` command in any rendered
  spelling, and lists them; raw hooks keep working. Why: they name a program that no longer exists in the repository.
- It removes the shipped skill and its links, because the skill teaches commands that no longer exist.
- It deletes `cook`, the owned and seeded files in `.cook/`, the manifest included, and the recipes in
  `.cook/recipes/`, listed first, then `.cook/` once nothing else is in it. Why: a recipe runs only through `the-cook`,
  so after eject it would describe a command that no longer exists.
- It keeps the `.agents/memory/` skeleton and whatever people wrote into it, because it is the repository's knowledge.

After eject, the repository keeps its conventions, permissions, hooks and static standing directives, and every file a
template wrote. A program whose configuration a station's template wrote keeps that configuration; an entry in it that
runs `./cook` is the repository's to change.
