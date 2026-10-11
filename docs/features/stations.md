# Stations

## Objective

**Problem.** A team decides how each stage of its development lifecycle runs: which version control system holds the
history, which workers change the code and whether a person drives them or they run unattended, which lanes prove a
change, what an unattended run must never do, and which actions deliver work and keep a tracker current. Written by
hand, those choices come out different in every repository, and a setup copied from another repository carries that
repository's choices without saying which ones it made. A setup inherited live from a shared source changes under a
repository that changed nothing, and nobody reading the repository can tell why it does what it does.

**Goal.** A repository takes up a whole way of running its lifecycle, or the starting commands of a stack, in one
reviewed change. From then on every entry is its own to edit, and it takes a newer version's improvements only through
another reviewed change.

## Overview

`the-cook` declares a software development lifecycle: the stages a change goes through, the events fired between them,
the guards that hold between stages and the proof a change carries ([lifecycle](lifecycle.md)). It knows no program;
[ingredients](ingredients.md) describe the programs the stages reach. A station is a named, versioned description of
one way of running that lifecycle, built from ingredients: which ingredient serves version control, which workers
implement a change and in which role, and the lanes, denies, actions and files that go with them
([ADR 0024](../adrs/0024-ingredients-and-stations.md)). A station holds up to six parts:

| Part | What it is | What applying does |
| --- | --- | --- |
| [Ingredients](#ingredients) | the ingredients the station uses, each pinned by cookbook, version and sha256 | pins each into `ingredients:`; a pin the repository already holds stays |
| [Stages](#stages) | the ingredient that fills version control, and the workers, each with its ingredient and role | writes `vcs:` and `workers.<name>` into the profile |
| [Entries](#entries) | additions to the profile, the policy and the hooks file, standing context included | adds what the files lack |
| [Templates](#templates) | whole files | writes each one whose file is absent |
| [Recipes](#recipes) | the recipes of its cookbook tag it adds, each by name and the sha256 of its archive | verifies each against that sha256 and copies it into `.cook/recipes/<name>/`, with its `origin`, when that directory is absent |
| [Detect rule](#detect-rule) | file globs | nothing: `scaffold` applies the station when they match |

A station has no renders and no floor. It is applied once: [`the-cook station apply`](#how-apply-works) pins the
ingredients it lists, writes its stages, entries and templates into the repository's own files and copies its recipes
into `.cook/recipes/`, adding only what they lack, shows it all as one diff to review, then renders. From then on those
entries, files and recipes are the repository's, like anything written by hand, while everything rendered comes from
the pinned ingredients ([ADR 0013](../adrs/0013-organization-content.md)). A station runs nothing: a
[recipe](recipes.md) it adds, one kind of change made many times, acts only when someone runs it.

The profile records each applied station under `stations:` ([profile](profile.md#stations)), with its cookbook, version
and sha256, so a person can tell where the repository started from and apply a newer version later. A station in
`stations:` has no effect on what renders, and deleting the entry changes nothing rendered. A repository may apply
several stations: one way of working, such as `agentic-coding`, and one station for each stack it uses, such as `node`.

Commands are named `the-cook <command>` on this page. The examples run them through the repository's wrapper,
`./cook <command>`, from the root of a checkout; with a global install, `the-cook <command>` is equivalent
([wrapper](wrapper.md)). The examples use two stations of a cookbook published at `github:acme/cookbook`:
[`agentic-coding`](#the-agentic-coding-station), which runs the lifecycle on `git` with an interactive coding agent,
`claude`, an unattended one, `acme-agent`, a loop runner, `acme-loop`, and a tracker, `acme-tasks`, and adds the
`implement-task` recipe; and [`node`](#the-node-station), the commands, lanes and post-edit hook of a Node project.

## When to use it

| Situation | Command |
| --- | --- |
| Set up a new repository with the stations its files call for | `the-cook scaffold --cookbook github:acme/cookbook@v1.2.0`: it applies every station of the tag whose detect rule matches ([scaffold](scaffold.md#station-detection)) |
| Set up a new repository with chosen stations only | `the-cook scaffold --cookbook github:acme/cookbook@v1.2.0 --no-detect --station agentic-coding --station node` |
| See which stations a cookbook tag holds and which fit this repository | `./cook station list --cookbook github:acme/cookbook@v1.2.0` |
| Read everything a station holds before applying it | `./cook station show <name> --cookbook github:acme/cookbook@v1.2.0` |
| The repository takes up a way of working, or gains a stack | `./cook station apply <name> --cookbook github:acme/cookbook@v1.2.0` |
| See which stations the repository applied, and at which version | `./cook station list` |
| Take a newer version's entries and templates | `./cook station apply <name> --cookbook github:acme/cookbook@v1.3.0 --dry-run`, then without `--dry-run` ([Applying again](#applying-again)) |
| See what the files lack from the version a station was applied at | `./cook station apply <name> --dry-run` |
| Move the programs a station uses to newer versions | `./cook ingredient pin <name>... --cookbook github:acme/cookbook@v1.3.0` ([ingredients](ingredients.md#pinning)) |
| Take a newer version of a recipe a station added, keeping the repository's edits | `./cook recipe update <name> --cookbook github:acme/cookbook@v1.3.0` ([recipes](recipes.md#updating-an-added-recipe)) |
| Describe a way of working no cookbook has | write a station and publish it in a cookbook ([Cookbooks](#cookbooks)) |
| Apply a station in many repositories, each delivered for review | `the-cook fleet station apply <name> --cookbook <source>@<version> --yes --deliver` ([fleet](fleet.md)) |
| Bring an organization's conventions into a repository | a station the organization publishes, or a reviewed change written by a person or a worker: see [Organization choices](#organization-choices) |

### Organization choices

An organization's way of working, such as its delivery actions, the denies its unattended runs need or a commit
trailer its CI expects, is a station in a cookbook of its own, beside the ingredients for its own programs
([ADR 0013](../adrs/0013-organization-content.md)). Applying it writes those choices into each repository's own files
in one reviewed diff, and from then on each repository edits them like its own.

Choices that differ between the organization's repositories take judgement. A person, or a worker following a skill
the organization publishes, writes them into each repository's own files, and each repository reviews the change as a
diff like any other:

```sh
# install the organization's skill with the team's skills installer, then ask a coding agent:
#   "apply the acme delivery conventions to this repository"
./cook render
git diff .cook/ AGENTS.md
```

Either way the entries end up in the repository's files, where its people and workers read them, and the repository
renders the same bytes on every machine without fetching anything beyond its pins.

## Parts of a station

### Ingredients

A station lists the ingredients it uses, each pinned the way `ingredients:` pins one: name, cookbook, version and sha256
([ingredients](ingredients.md#pinning)), usually from its own cookbook tag. Applying the station pins each one into
`ingredients:` at the sha256 the station lists, in the same diff as everything else it writes. An ingredient the
repository already pins keeps the repository's pin, and a different sha256 is reported, so applying never moves a pin;
[`the-cook ingredient pin`](ingredients.md#the-cook-ingredient-pin) does that.

A stack station often lists no ingredient, or only a task runner's. The [stages](#stages) of a station may name only
ingredients it lists.

### Stages

The stages say which ingredient serves each stage of the lifecycle that needs a program:

- `vcs`: the ingredient that fills the [version-control contract](ingredients.md#the-version-control-contract), which
  Start, Verify, Complete and Landed reach;
- `workers`: the workers that act at Implement, each with its ingredient, which fills the
  [worker contract](ingredients.md#the-worker-contract), its role, `interactive` or `unattended`, and optionally its
  `writable_roots`, `model` and `effort` ([profile](profile.md#workers)).

Applying writes them into the profile as `vcs:` and one `workers.<name>` item each, under the
[merge rules](#merge-rules). Stages are what make a station a way of running the lifecycle. A stack station declares
none: it adds commands and lanes to a lifecycle that another station, or the repository itself, sets up.

### Entries

Entries are additions to the repository's three YAML files: commands, `bootstrap`, lanes, validators, module switches
and [standing context](context.md) in `.cook/config.yaml`; `unattended.deny` and other policy entries in
`.cook/policy.yaml`; post-edit hooks and lifecycle actions in `.cook/hooks.yaml`. `apply` adds only what the files
lack, following the [merge rules](#merge-rules). From then on each entry is the repository's own: people and workers
change or delete it by editing the file, and nothing records which station wrote it
([ADR 0013](../adrs/0013-organization-content.md)). A newer version's entries reach a repository only when someone
applies the station again ([Applying again](#applying-again)).

Entries fit what a repository may reasonably change, such as a stack's lint command or a tracker's lifecycle actions.
A rule that must hold whatever the repository edits is a [floor](ingredients.md#the-floor) rule of an ingredient
instead; a station holds none.

### Templates

A template is a whole file a station holds, rendered once with values from the profile in the
[template language](ingredients.md#the-template-language), such as a loop runner's configuration file that names
`./cook attempt <worker>`, or a directive file a standing-context entry names. `apply` writes it only when the file
is absent. From then on it is the repository's own file: `the-cook` never renders it again, never overwrites it and
never removes it, and [`the-cook remove`](remove.md) leaves it. When the file exists, `apply` writes nothing to it and
reports `kept <path>: exists`.

`the-cook scaffold` seeds the repository files, such as `.cook/config.yaml`, from templates of its own
([scaffold](scaffold.md#greenfield)). The manifest records those as seeded files, which `the-cook remove` deletes
([remove](remove.md#undo-and-eject-compared)); nothing records a template a station wrote.

### Recipes

A station lists the [recipes](recipes.md) it adds, the kinds of change its way of working makes many times, such as one
task of a tracker. Each is published under the station's own cookbook tag, and the station pins it, as it pins its
ingredients, by the sha256 of its archive. Applying the station verifies each recipe against that sha256 and copies it
into `.cook/recipes/<name>/` with its `origin`, as [`the-cook recipe add`](recipes.md#the-cook-recipe-add) does, in the
same diff as everything else the station writes. Because the hash comes from the station, applying it again never trusts
a fresh download of the tag's `checksums.txt` for its recipes. Each recipe's `requires` is checked against the profile
as the whole diff leaves it, so the workers and lanes the station adds count. A recipe directory that already exists is
the repository's: `apply` keeps it, reports `kept .cook/recipes/<name>/: exists` and never overwrites it. From then on
the copy is the repository's own, and [`the-cook recipe update`](recipes.md#the-cook-recipe-update) merges a newer
version into it. A station runs no recipe: a person, a worker or a loop runner runs one with
[`the-cook make`](recipes.md#the-cook-make).

### Detect rule

A detect rule is a list of file globs whose match shows that the station fits the repository, such as `package.json`
for `node`. `the-cook scaffold --cookbook` applies every station of the tag whose rule matches, and
`station list --cookbook` shows which match. A station without a detect rule is applied only when someone names it.
The rules are the cookbook's data; the binary holds none of its own.

## How apply works

`the-cook station apply <name>... [--cookbook <source>@<version>]` applies one or more stations, in the order given, to
a repository already set up with `the-cook scaffold`:

1. **Check.** The repository needs `.cook/config.yaml`; without it, `apply` exits 1 and names `the-cook scaffold`.
   With `--cookbook`, `apply` downloads the tag's `checksums.txt` and the named stations over HTTPS, verifies each
   archive against it and checks each `station.yaml`. Without it, every name must be in `stations:`, and a name that is
   not exits 1 and names `--cookbook`; a recorded station that is not cached is downloaded and verified against its
   pin. Every ingredient the stations list that is not cached is then downloaded and verified against the sha256 its
   station lists, and the stages are checked against the contracts those ingredients fill. Every recipe the stations
   list that `.cook/recipes/` lacks is downloaded from the station's tag and verified against the sha256 its station
   lists. On any failure nothing is written.
2. **Plan** the changes: each station's pin in `stations:`, added or moved to the version applied; the ingredient pins;
   the stages; the entries for `.cook/config.yaml`, `.cook/policy.yaml` and `.cook/hooks.yaml`; the templates whose
   file is absent, rendered with the profile; and a copy, with its `origin`, of each recipe whose directory is absent.
   Each station is planned against the files as the stations before it leave them, following the
   [merge rules](#merge-rules). Each recipe's `requires` is checked against the profile as the whole plan leaves it,
   so the workers and lanes the stations add count: a contract, role or lane still missing exits 3, names it, and
   nothing is written.
3. **Confirm.** Show every pin, addition, template and recipe to write as one diff, and every item kept with its
   difference or `exists`, then ask. `--yes` skips the question. Without a terminal and without `--yes`, nothing is
   written and the command exits 3. `--dry-run` prints the diff and writes nothing.
4. **Write** the pins and additions into the files, the templates, and the recipes into `.cook/recipes/`.
5. **Render** the repository, as [`the-cook render`](render.md) does: the renders and floor of every pinned
   ingredient, those just pinned included, so the additions and their rendered effect are one diff and the drift check
   passes on it.

When there is nothing to add and `stations:` already records each station at the version applied, `apply` says so,
writes nothing and exits 0.

### Merge rules

Applying only adds. The repository's own entries, files and pins stay authoritative.

| Element | Rule |
| --- | --- |
| An ingredient pin | added when `ingredients:` has none by that name; when it has one with another sha256, the repository's stays and the difference is reported: `kept ingredients.git: pinned at v1.1.0, station agentic-coding lists v1.2.0` |
| A station pin | added to `stations:`, or moved to the version applied |
| A single value: `vcs`, a module switch | written when the profile has none; when it has one that differs, the repository's stays and the difference is reported: `kept vcs: differs from station agentic-coding` |
| A named item: a worker, a command, a lane, a lifecycle action | added when the repository has none by that name; when it has one that differs, the repository's stays and the difference is reported: `kept lanes.unit: differs from station node` |
| An element of a list: a `bootstrap` command, a glob of a path list, a validator, a post-edit hook declaration, a standing-context entry, a policy entry such as one of `unattended.deny` | added when the list holds no equal element, one with the same fields and values |
| A template | written when the file is absent; an existing file is the repository's and stays untouched, and is reported: `kept <path>: exists` |
| A recipe | copied into `.cook/recipes/<name>/`, with its `origin`, when that directory is absent; an existing one is the repository's and stays untouched, whatever version it came from, and is reported: `kept .cook/recipes/<name>/: exists` |
| Everything already in the files | nothing is removed, replaced or reordered; comments, key order and blank lines are preserved |

Additions go at the end of the list or map they belong to; a section the file lacks is added at its end. Two stations
applied together follow the same rules: when both name the same item, such as `commands.lint`, the first one's entry
is added and the second one's is reported as kept.

### Applying again

Applying is idempotent: a second `apply` of the same version adds nothing, because every pin, entry and template is
already there. Applying again is therefore the way to take a newer version's improvements to a station's stages,
entries and templates. `./cook station apply node --cookbook github:acme/cookbook@v1.3.0 --dry-run` shows what the new
version would add and the `stations:` pin it would move, and the same command without `--dry-run` writes it as a diff
to review. The ingredient pins a newer version lists do not move the repository's: the diff reports each difference,
and `./cook ingredient pin <name>... --cookbook github:acme/cookbook@v1.3.0` moves them, with their renders and floor,
in the same change or another. A recipe the newer version adds is copied; one the repository already has is kept, and
`./cook recipe update <name> --cookbook github:acme/cookbook@v1.3.0` merges the newer version into it, keeping the
repository's edits ([recipes](recipes.md#updating-an-added-recipe)).

Applying again also adds back an entry, a template or a recipe the repository deleted, because nothing records that it
was removed on purpose. Read the diff before confirming. A template the repository kept is never updated by applying
again: to take a newer version's, delete the file and apply, then carry over your own changes.

### After applying

`stations:` records which stations the repository applied and at which pin. Nothing rendered depends on it: deleting an
entry from `stations:` changes nothing rendered and leaves the profile valid. Nothing records which entries or
templates a station wrote either: each entry's one source of truth is the file it is in, and `the-cook` holds no record
it could later use to change those entries by itself ([ADR 0013](../adrs/0013-organization-content.md)). Change or
delete an applied entry or template by editing the file, then run `./cook render`.

A recipe a station added records the cookbook it came from in its own `origin`, and nothing records which station
added it.

To stop using what a station set up, delete the entries that no longer apply from the files, the recipes it added that
the repository no longer runs, and the ingredient pins nothing else uses, with the `vcs:` or workers that name them,
then run `./cook render`. The render removes those
ingredients' renders and floor, which the manifest records under their names. An ingredient that `vcs:` or a worker
names cannot leave `ingredients:` while it is named: the profile fails validation with exit 1. Both removal modes of
[`the-cook remove`](remove.md) delete the seeded `.cook/` files whatever they hold, so applied entries go with them, and
`.cook/recipes/` with the rest of `.cook/`; a template a station wrote stays.

## Cookbooks

A cookbook is a source that publishes ingredients, stations and [recipes](recipes.md) by tag: an HTTPS base URL laid out
like the [release source](wrapper.md#release-source), or `github:<owner>/<repo>`, shorthand for
`https://github.com/<owner>/<repo>/releases`. `the-cook` holds no default cookbook and knows none by name; any number of
cookbooks exist, and a repository may pin from several. An organization keeps its own programs and its own way of
working in a cookbook of its own ([Organization choices](#organization-choices)), and its kinds of change as recipes
there.

Each tag of a cookbook publishes, under `<source>/download/<version>/`:

| File | Holds |
| --- | --- |
| `ingredient-<name>.tar.gz`, `station-<name>.tar.gz`, `recipe-<name>.tar.gz`: one archive per ingredient, station and recipe, named by its kind | a gzip-compressed tar of the unit's directory: `ingredient.yaml`, `station.yaml` or `recipe.yaml` at its root and `files/` beside it |
| `checksums.txt` | the sha256 of every archive of the tag, one `<sha256>  <archive>` line each, such as `<sha256>  station-node.tar.gz` |

Every ingredient and station of a tag carries the tag as its `version`; a recipe carries none, and a copy of it records
the tag in its `origin`. Names are unique within each kind, and an ingredient, a station and a recipe may share a name,
because each archive's name carries its kind. A repository pins from a tag with `the-cook station apply --cookbook` or
[`the-cook ingredient pin`](ingredients.md#the-cook-ingredient-pin), and copies recipes from one with
[`the-cook recipe add`](recipes.md#the-cook-recipe-add); each takes the hashes from that tag's `checksums.txt`, and
`scaffold --cookbook` does the same for every station it applies.

### Layout of a cookbook repository

```text
ingredients/<name>/ingredient.yaml   one directory per program the cookbook describes
ingredients/<name>/files/            its render templates
stations/<name>/station.yaml         one directory per way of running the lifecycle, or per stack
stations/<name>/files/               its templates
recipes/<name>/recipe.yaml           one directory per kind of change, without origin
recipes/<name>/files/                the sources of its template steps
docs/                                a page for each ingredient, station and recipe
```

`the-cook`'s documentation describes the core and the two contracts; what a particular program does, which files and
keys it reads and which of its arguments are unsafe is the cookbook's to describe, on the page beside its ingredient.

### Writing and publishing

To write an ingredient, a station or a recipe, create its directory following
[the ingredient file](ingredients.md#the-ingredient-file), [the station file](#the-station-file) or
[the recipe file](recipes.md#the-recipe-file). All three are data, and the cookbook that publishes them owns their
tests: an ingredient against the programs and versions its checks name, a station by applying it to sample repositories
and running the lifecycle there, a recipe by running it in sample repositories. `the-cook`'s own tests cover the core
with stand-in ingredients that describe no real program.

To publish a tag, build the ingredient and recipe archives first, because each station lists the sha256 of the
ingredients it uses, usually from the same tag, and of the recipes it adds, always from the same tag; then the station
archives; then `checksums.txt`, with every archive of the tag. Upload them all under the tag. A new version is a new
tag; an archive already published under a tag is never replaced, because every repository that pinned it would then
refuse it ([fetching](ingredients.md#fetching)).

## Reference

```text
the-cook station list [--cookbook <source>@<version>]
the-cook station show <name> [--cookbook <source>@<version>]
the-cook station apply <name>... [--cookbook <source>@<version>] [--yes] [--dry-run]
```

`station` follows the `the-cook <noun> <verb>` shape. `apply` has no inverse, because the entries and templates it
writes become the repository's own ([After applying](#after-applying)).
[`the-cook fetch`](ingredients.md#the-cook-fetch) caches the stations `stations:` records along with the ingredients
and the version every added recipe came from.
Global flags `-C <dir>`, `--json` and `--verbose` apply to every subcommand; see [CLI reference](cli.md).

### `the-cook station list`

Without `--cookbook`, prints the stations `stations:` records, each with its version and source and whether it is cached
on this machine. It reads only the profile and the cache.

With `--cookbook <source>@<version>`, prints the stations that tag publishes, each with whether its detect rule matches
in this repository, the ingredients it lists, the recipes it adds, and the version `stations:` records it at, if any. It
works in a repository that is not scaffolded yet, so it also helps choose `scaffold --station`.

| Flag | Default | Meaning |
| --- | --- | --- |
| `--cookbook <source>@<version>` | none | List that cookbook tag's stations instead of the applied ones |

| Exit code | Meaning |
| --- | --- |
| 0 | Listed |
| 1 | With `--cookbook`: the source cannot be reached, the tag has no `checksums.txt`, or an archive does not match it |

Files read: `.cook/config.yaml` when present, `$COOK_HOME/cookbook/`; with `--cookbook`, the tag's `checksums.txt` and
archives and the files each detect rule names. Files written: with `--cookbook`, each verified archive into
`$COOK_HOME/cookbook/`.

### `the-cook station show`

Prints the station's `station.yaml`, the ingredients it lists with their pins, its stages, its entries as the YAML they
add, grouped by the file they go into, every template with its path and content, and the recipes it adds with their
sha256. Templates are rendered with the repository's profile when there is one. Without `--cookbook` the station is one
`stations:` records; with it, the station of that name in that tag. It does not compare anything with the repository;
`apply --dry-run` shows what applying would write here. Read the ingredients a station lists with
[`the-cook ingredient show`](ingredients.md#the-cook-ingredient-show), and the recipes it adds with
[`the-cook recipe show`](recipes.md#the-cook-recipe-show).

| Flag | Default | Meaning |
| --- | --- | --- |
| `<name>` | required | The station |
| `--cookbook <source>@<version>` | none | Read the station from that cookbook tag instead of the recorded one |

| Exit code | Meaning |
| --- | --- |
| 0 | Printed |
| 1 | A name `stations:` does not record, which names `--cookbook`; a source that cannot be reached; an archive that does not match its pin or, with `--cookbook`, the tag's `checksums.txt`; with `--cookbook`, a name the tag does not publish |

Files read: `.cook/config.yaml`, when present; `$COOK_HOME/cookbook/<sha256>.tar.gz`; the station's source when it is
not cached; with `--cookbook`, the tag's `checksums.txt` and the archive. Files written: each verified archive it
downloads, into `$COOK_HOME/cookbook/`.

### `the-cook station apply`

Applies one or more stations, in the order given ([How apply works](#how-apply-works)).

| Flag | Default | Meaning |
| --- | --- | --- |
| `<name>...` | required | One or more stations: published by the `--cookbook` tag, or without it recorded in `stations:` |
| `--cookbook <source>@<version>` | none | The cookbook and the tag to apply from. Without it, each station is applied again at the version `stations:` records |
| `--yes` | off | Apply without asking; required without a terminal |
| `--dry-run` | off | Print the pins, the additions and the kept items; write nothing |

With `--json`, the output lists the pins added and moved, the entries added, the templates written, the recipes added
and the items kept, each with its file and, for an entry, its key.

| Exit code | Meaning |
| --- | --- |
| 0 | Applied, or nothing to add; `--dry-run` printed |
| 1 | Error: a repository that is not scaffolded, an invalid repository file; without `--cookbook`, a name `stations:` does not record (the message names `--cookbook`); with it, a name the tag does not publish; no network or a source that cannot be reached; an archive that does not match the tag's `checksums.txt`, its pin, or the sha256 its station lists; an invalid `station.yaml`, `ingredient.yaml` or `recipe.yaml`, one whose name or version differs from its pin, or one whose schema or contract version is newer than the running release supports (the message names the release needed); stages that name an ingredient the station does not list, or one that does not fill the contract the stage needs; a recipe the station lists that its tag does not publish. Nothing is written |
| 3 | Confirmation needed and there is no terminal: rerun with `--yes`; the repository files use a newer schema than the running release supports; or a contract, role or lane a recipe the station adds requires is still missing once the plan is applied, named. Nothing is written |

Files read: `.cook/config.yaml`, `.cook/policy.yaml`, `.cook/hooks.yaml`, whether each template's file exists,
`.cook/recipes/`, `$COOK_HOME/cookbook/`, with `--cookbook` the tag's `checksums.txt` and the named archives, the source
of each ingredient or recorded station that is not cached, the archive of each recipe to copy, and everything
[`render`](render.md#reference) reads. Files written: `$COOK_HOME/cookbook/<sha256>.tar.gz` for each verified archive it
downloads; `.cook/config.yaml`, `.cook/policy.yaml` and `.cook/hooks.yaml`, additions and pins only; each template whose
file was absent; `.cook/recipes/<name>/` for each recipe whose directory was absent; then every file `render` writes
([what render writes](render.md#what-render-writes)).

### The station file

A station is a directory:

```text
<name>/
  station.yaml      the station's description
  files/           template sources, when the station has any
```

| Key of `station.yaml` | Type | Required | Meaning |
| --- | --- | --- | --- |
| `schema` | integer | yes | Format version of the station file: `1` |
| `name` | string | yes | Lowercase letters, digits and `-`: the name `stations:` records |
| `version` | string | yes | The cookbook tag the station is published under, which `stations:` records |
| `description` | string | no | One line, printed by `station list` |
| `ingredients` | list of pins | no | [Ingredients](#ingredients): each with `name`, `source`, `version` and `sha256`, as an entry of `ingredients:` has them ([pinning](ingredients.md#pinning)); a name appears once |
| `stages.vcs` | ingredient name | no | [Stages](#stages): an ingredient of `ingredients` that fills the version-control contract; written as `vcs:` |
| `stages.workers` | map of worker name to declaration | no | Each declaration has the fields of a profile [worker](profile.md#workers): `ingredient`, an ingredient of `ingredients` that fills the worker contract, by default the one named like the worker; `role`; and optionally `writable_roots`, `model` and `effort` |
| `entries` | map of file to YAML | no | [Entries](#entries), grouped by the file they go into: `.cook/config.yaml`, `.cook/policy.yaml`, `.cook/hooks.yaml`. Each value has the shape of that file; entries for `.cook/config.yaml` hold no `schema`, `name`, `ingredients`, `stations`, `vcs` or `workers`, which pins and stages write |
| `templates` | list | no | [Templates](#templates): `path`, where the file is written, relative to the repository root, and `source`, a file under `files/` |
| `recipes` | list of `{ name, sha256 }` | no | [Recipes](#recipes): the recipes the station adds, each with `name`, a recipe published under the same cookbook tag as `recipe-<name>.tar.gz`, and `sha256`, the sha256 of that archive; a name appears once |
| `detect` | list of globs | no | The [detect rule](#detect-rule): paths relative to the repository root whose match shows the station fits; a trailing `/` matches a directory |

The binary refuses a station whose `schema` is newer than it supports, with exit 1 naming the release needed, and reads
older ones inside its support window ([ADR 0005](../adrs/0005-schema-compatibility.md)). `the-cook version` prints the
station and ingredient schemas and the contract versions the running release supports. Unknown keys are an error, and
so are `renders`, `floor`, `operations` and `checks`, which only an ingredient holds. Templates read the
[neutral model](ingredients.md#the-neutral-model).

#### The `agentic-coding` station

`agentic-coding` runs the lifecycle with an interactive coding agent and an unattended one that a loop runner starts
for each task of a tracker. Applied, it pins five ingredients, declares two workers and writes the module switches, a
validator, a standing-context entry, the unattended denies and the tracker's lifecycle actions, and two templates, and
it adds the `implement-task` recipe, which the loop runner runs for each task.

```text
agentic-coding/
  station.yaml
  files/
    acme-loop.toml
    unattended.md
```

```yaml
schema: 1
name: agentic-coding
version: v1.2.0
description: an interactive and an unattended coding agent on git, a loop runner and a tracker
ingredients:
  - name: git
    source: github:acme/cookbook
    version: v1.2.0
    sha256: d2f0d562418c7d176d0393d8694cc6f6f569f2dff3f7ede3788a2075d3ff5c3f
  - name: claude
    source: github:acme/cookbook
    version: v1.2.0
    sha256: b5fd834d175eb4b74cda3763a33fc04c87b00e8b85e18f8c1c43bf912eca8816
  - name: acme-agent
    source: github:acme/cookbook
    version: v1.2.0
    sha256: 6d2f26296cf399e1f470582106e0def36fcb3979b54422ecde46767a4b996ea7
  - name: acme-loop
    source: github:acme/cookbook
    version: v1.2.0
    sha256: 07d81036435275ca783721d8c19ed99760aed77d5f46e68443ae81864140d247
  - name: acme-tasks
    source: github:acme/cookbook
    version: v1.2.0
    sha256: 76dc8bdac719fd94d08f2ea705b6dcaefb28045f1f0880c4fd45eb839ddcd408
stages:
  vcs: git
  workers:
    claude: { ingredient: claude, role: interactive }
    acme-agent: { ingredient: acme-agent, role: unattended, writable_roots: [.tasks] }
detect: [AGENTS.md, .agents/]
entries:
  .cook/config.yaml:
    modules: { hooks: true, verify: true }
    verify:
      validators:
        - { when: [".cook/**", AGENTS.md], run: ./cook render --check }
    context:
      always:
        - { file: .agents/directives/unattended.md, for: [unattended] }
  .cook/policy.yaml:
    unattended:
      deny:
        - [git, push]
        - [acme-tasks, sync]
  .cook/hooks.yaml:
    lifecycle:
      start:
        - { name: claim-task, scope: [task], auto: true, run: 'acme-tasks claim "$COOK_TASK" --actor "$COOK_ACTOR"' }
      completed:
        - { name: close-task, scope: [task], auto: true, run: 'acme-tasks close "$COOK_TASK"' }
      blocked:
        - { name: release-task, scope: [task], auto: true, run: 'acme-tasks release "$COOK_TASK" -m "$COOK_REASON"' }
templates:
  - { path: .acme-loop.toml, source: files/acme-loop.toml }
  - { path: .agents/directives/unattended.md, source: files/unattended.md }
recipes:
  - name: implement-task
    sha256: f584da225933b3a72ab0fc922ae90eae3872e629529fb7c356ebefcfedc5a88a
```

`files/acme-loop.toml`, written once when `.acme-loop.toml` is absent, names the recipe the station adds as the loop
runner's command for each task, with `--unattended`, because nobody drives those runs:

```toml
# acme-loop settings for this repository, written once by the agentic-coding station; edit freely
tasks = ["acme-tasks", "ready", "--json"]
# acme-loop replaces {id} with each task's id and passes the task's text on stdin
worker = ["./cook", "make", "implement-task", "--task", "{id}", "--unattended", "--commit", "--"]
logs = "{{state_dir}}/acme-loop"
```

Each task then runs as one unattended [recipe run](recipes.md#the-run), in a workspace of its own bound to the task,
where the `start`, `completed` and `blocked` actions above keep the tracker current; its `attempt` step starts
`acme-agent` on the task ([recipes](recipes.md#a-loop-runner-runs-implement-task-for-each-task)).

`files/unattended.md`, the directive the standing-context entry gives every unattended session:

```markdown
# Rules for unattended work

- Work only on the task you were given, in this checkout.
- Run `./cook verify` with the lanes your change needs before you finish: the run runs the lanes of that selection
  itself and replays them from the base.
- Never push and never close a task yourself: the lifecycle closes the task once the replay passes, and delivery is
  the repository's.
```

The workers' native files, the floor of `git` and `acme-agent`, and the ignore rules of the state paths are not in
the station: they are the renders of the ingredients it pins, and they follow those pins on every render.
`implement-task` is not in the station's directory either: it is a recipe of the same tag, published as
`recipe-implement-task.tar.gz`, which applying copies.

#### The `node` station

`node` is a stack station: no ingredients, no stages, entries only.

```yaml
schema: 1
name: node
version: v1.2.0
description: the commands, lanes and post-edit hook of a Node project
detect: [package.json]
entries:
  .cook/config.yaml:
    bootstrap: [npm ci]
    commands:
      lint: npm run lint -- {file}
      build: npm run build
    verify:
      app_paths: [src/**, test/**, package.json, package-lock.json]
      lint_paths: ["src/**/*.{ts,js}", "test/**/*.{ts,js}"]
      lanes:
        unit: { select: "npm test -- {pattern}", full: npm test }
  .cook/hooks.yaml:
    post_edit:
      - { paths: ["src/**/*.{ts,js}"], run: "npm run lint -- {file}" }
```

The commands run the project's own scripts, so the station guesses only their names; the repository edits them to its
real commands.

## Examples

### Choose stations before scaffolding

```text
$ cd ~/src/acme-web
$ the-cook station list --cookbook github:acme/cookbook@v1.2.0
station         detected  ingredients                                     recipes         applied
agentic-coding  no        git, claude, acme-agent, acme-loop, acme-tasks  implement-task  -
node            yes       -                                               -               -
python          yes       -                                               -               -
terraform       no        -                                               -               -
$ the-cook scaffold --cookbook github:acme/cookbook@v1.2.0 --no-detect \
    --station agentic-coding --station node --dry-run
```

`requirements.txt` here only pins a documentation tool, so the scaffold takes `node` and leaves `python` out. The
repository has no `AGENTS.md` and no `.agents/` yet, so `agentic-coding` does not detect it and is named instead.

### Apply a way of working and a stack

The repository was scaffolded without a cookbook, and its people already wrote their own `build` command:

```text
$ ./cook station show agentic-coding --cookbook github:acme/cookbook@v1.2.0
$ ./cook ingredient show acme-agent --cookbook github:acme/cookbook@v1.2.0
$ ./cook station apply agentic-coding node --cookbook github:acme/cookbook@v1.2.0
verified against github:acme/cookbook@v1.2.0: agentic-coding, node
verified against agentic-coding: 5 ingredients, recipe implement-task
.cook/config.yaml
+  - name: git
+    source: github:acme/cookbook
+    version: v1.2.0
+    sha256: d2f0d562418c7d176d0393d8694cc6f6f569f2dff3f7ede3788a2075d3ff5c3f
   ... four more ingredient pins, then the pins of agentic-coding and node in stations:
+vcs: git
+workers:
+  claude: { ingredient: claude, role: interactive }
+  acme-agent: { ingredient: acme-agent, role: unattended, writable_roots: [.tasks] }
+  lint: npm run lint -- {file}
+    unit: { select: "npm test -- {pattern}", full: npm test }
   ... the module switches, the validator, the context entry, bootstrap and the path globs
.cook/policy.yaml
+    - [git, push]
+    - [acme-tasks, sync]
.cook/hooks.yaml
   ... the three lifecycle actions and the post-edit hook
new .acme-loop.toml
new .agents/directives/unattended.md
new .cook/recipes/implement-task/recipe.yaml
kept commands.build: differs from station node
apply these changes? [y/N] y
applied agentic-coding, node: 5 ingredients pinned, 2 templates, 1 recipe, 1 item kept; rendered the repository
$ git add -A && git commit -m "chore: apply the agentic-coding and node stations"
```

The diff is shortened here; `apply` prints all of it. Each teammate runs `./cook fetch` once on their machine.

### Apply a newer version

```sh
./cook ingredient pin git claude acme-agent acme-loop acme-tasks --cookbook github:acme/cookbook@v1.3.0 --yes
./cook station apply agentic-coding node --cookbook github:acme/cookbook@v1.3.0 --dry-run
./cook station apply agentic-coding node --cookbook github:acme/cookbook@v1.3.0 --yes
./cook recipe update implement-task --cookbook github:acme/cookbook@v1.3.0 --yes
git diff
```

The first command moves the programs to v1.3.0, with their renders and floor. The second shows what v1.3.0 adds that
the files lack and the `stations:` pins it moves; the third writes it. The fourth merges v1.3.0 of `implement-task`
into the repository's copy, which applying again keeps as it is. Without the first, the apply would report each
ingredient pin it leaves as it is.

### Many repositories

```sh
the-cook fleet station apply node --cookbook github:acme/cookbook@v1.2.0 --yes --deliver
```

Each repository that lacks some of the station's entries, or its record in `stations:`, gets a branch with the change
and its rendered effect, delivered through its own `deliver` actions. A repository that already holds them is
reported as unchanged ([fleet](fleet.md)). The command runs without a terminal in each repository, so it needs
`--yes`.

## Guidelines

- Do read a station with `the-cook station show <name> --cookbook <source>@<version>`, and the ingredients it lists
  with `the-cook ingredient show`, before applying it: the review of the diff `apply` shows is the trust decision for
  every pin it adds.
- Do read the diff `apply` shows before confirming: once written, the entries and templates are the repository's own
  and nothing marks where they came from.
- Do edit applied entries to fit the repository: a station guesses from the stack or the way of working, the repository
  knows its real commands, and applying again never overwrites an item you changed.
- Do run `./cook station apply <name> --cookbook <source>@<newer> --dry-run` when a cookbook publishes a newer tag: new
  entries reach the repository only when someone applies the station again.
- Do commit the pins and additions together with the files `apply` rendered, so the diff explains itself.
- Do write an organization's shared way of working as a station in its own cookbook, and choices that differ between
  its repositories through a reviewed change in each ([Organization choices](#organization-choices)).
- Do take a newer version of a recipe a station added with `recipe update`: applying again keeps the copy the
  repository has, edits included.
- Don't expect deleting an entry from `stations:` to undo a station: it changes nothing rendered. Delete the entries and
  the ingredient pins themselves.
- Don't apply a station again to restore an item you changed: `apply` keeps every item the repository names. Delete
  yours first if you want the station's.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| `kept lanes.unit: differs from station node` | The repository already has a `unit` lane, and `apply` never replaces an item the repository names | Nothing, if yours is right. To take the station's, delete yours and apply again |
| `kept <path>: exists` | The file is there, and `apply` writes a template only when its file is absent | Nothing, if yours is right. To take the station's, move yours aside, apply again, and carry over what you need |
| `kept .cook/recipes/<name>/: exists` | The repository already has a recipe of that name, and `apply` never overwrites one | Nothing, if yours is right. To take the station's version, `./cook recipe update <name> --cookbook <source>@<version>` merges it into yours |
| `station apply` exits 3 naming a contract, a role or a lane | A recipe the station adds requires it, and neither the profile nor the station provides it | Declare it in the profile, or apply a station that provides it, then apply again |
| `kept vcs: differs from station agentic-coding` | The profile already names a version-control ingredient | Nothing, if yours is right; `vcs:` is one value, and `apply` never replaces it |
| `kept ingredients.<name>: pinned at <version>, station <station> lists <version>` | The repository already pins the ingredient, and applying never moves a pin | Nothing, if the pinned version is right. To take the station's, `./cook ingredient pin <name> --cookbook <source>@<version>` |
| `station apply` exits 1 naming `the-cook scaffold` | The repository has no `.cook/config.yaml` | Run `the-cook scaffold` |
| `station apply` or `station show` exits 1 naming `--cookbook` | The name is not in `stations:`, so there is no recorded version to use | Pass `--cookbook <source>@<version>` |
| `station apply --cookbook` exits 1: the tag does not publish the name | The name is wrong, the name is an ingredient's or a recipe's, or the cookbook added the station in a later tag | `./cook station list --cookbook <source>@<version>` prints the tag's stations |
| `station apply` exits 3 | No terminal to confirm, and no `--yes` | Review with `--dry-run`, then rerun with `--yes`; under `fleet`, always pass `--yes` |
| Exit 1: an ingredient's or a recipe's archive does not match the sha256 its station lists | The archive at its source is not the one the station was built with: a new upload under the same version, or tampering | Do not work around it. Ask the cookbook's publisher; apply a tag whose archives match |
| Exit 1: a station's stages name an ingredient it does not list, or one that fills another contract | The station is invalid | Report it to the cookbook's publisher; apply another tag |
| Exit 1: a station's or an ingredient's schema or contract version is newer than the running release supports | The cookbook tag was written for a newer `the-cook` | Move the release pin with `./cook wrapper --version <tag>` to the release the message names, or apply an older tag |
| Deleting a station from `stations:` changed nothing | `stations:` is a record; nothing rendered depends on it | Delete the entries that no longer apply, and the ingredient pins nothing else uses, then `./cook render` ([After applying](#after-applying)) |
| An entry, a template or a recipe you deleted is back after `station apply` | `apply` adds every entry the files lack, writes every absent template and copies every absent recipe, and nothing records that you removed them | Delete it again, and read the diff before confirming the next apply |
| `station apply` with a newer tag adds nothing but the pin | The files already hold every entry of the station, or equal ones | Expected |
| Only one stack's lint command is in the profile | `commands.lint` is one named item: the first station applied wrote it, and the next one's was reported as kept | Write a lint command that covers both stacks, such as a script or a task-runner task that picks the linter by the file's extension |
| Entries of the wrong stack after `scaffold` | Marker files of another stack are present, such as a `package.json` for tooling in another stack's repository | Delete the entries that do not apply from `.cook/config.yaml` and `.cook/hooks.yaml`, and the station's record from `stations:`; on the next repository, `the-cook scaffold --cookbook <source>@<version> --no-detect --station <name>` |
