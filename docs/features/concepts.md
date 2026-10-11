# Concepts

## Objective

**Problem.** A developer or a worker in an adopted repository meets one tool that runs a lifecycle, programs that
do the work at its stages, and files written by different hands: some by people, some generated from other files, some
local state that must never be committed. Without knowing which is which, they expect the tool to do a program's work or
a program to hold a guard, edit a generated file and lose the edit or fail a check, commit state that belongs to one
machine, or cannot tell what would remain if the repository stopped using the tool. A newcomer also cannot tell which
work follows fixed rules and which is left to their judgement.

**Goal.** A reader can tell what the lifecycle guarantees at each stage and which program does the work there. For any
file in an adopted repository, they can tell who writes it, whether it is committed, where a change to it belongs and
what happens to it if the tool is removed. For any piece of setup work, they can tell whether it follows a fixed rule or
needs judgement.

## Overview

`the-cook` runs the software development lifecycle a repository declares: the stages a change goes through, the
events fired between them, the guards that hold between them and the proof a change carries. The binary keeps that
lifecycle and the process around it. What a repository adds to it comes in three units, and one rule separates them:
facts about programs are pinned and rendered; practices are copied and become the repository's own.
[Ingredients](ingredients.md) hold the facts about the programs the stages reach, the version control system and the
workers included; [stations](stations.md) hold what one way of working needs in place; [recipes](recipes.md) hold the
kinds of change a repository makes again and again, and each run of one takes one change through the stages. The binary
ships none of them. Around the lifecycle, `the-cook` divides a repository into layers and directories, records which
files it owns, writes down the conventions it sets up and adapts to repositories in different starting states, and an
adopted repository keeps working without it.

Examples on this page run commands as `./cook <command>`, through the repository's [wrapper](wrapper.md); with a
global install, `the-cook <command>` is equivalent.

## The lifecycle at the centre

The one abstraction `the-cook` implements is the [development lifecycle](lifecycle.md). Every other part of this page
serves it: the repository files declare it, rendering and ownership deliver what each stage needs, ingredients reach the
programs that do the work, and recipes take one kind of change through it.

| Stage | Who acts | What the core does | Reached through |
| --- | --- | --- | --- |
| Plan | people, a tracker | nothing but the task id | lifecycle actions the repository declares |
| Start | a person, a coordinating worker or a script | creates the workspace, records its base, actor and task; fires `start` | the version-control contract |
| Implement | workers, interactive or unattended | renders the workers' configuration and floor, runs hooks, runs attempts, inside a worker's jail when it declares one | the worker contract, and the jail contract for a jailed worker |
| Verify | anyone | runs the declared lanes, writes the receipt bound to base and content; fires `verified` | the repository's lane commands |
| Complete | the binary after a replay, or a person | replays the proof from the base, fires `completed` | the version-control contract, actions |
| Deliver | declared actions | fires `deliver` only for committed work off the default branch and, with verification on, verified work | actions |
| Review and merge | the hosting platform, CI and reviewers | nothing | nothing |
| Landed | the binary on workspace removal, or a person | detects that the work's patches are on the shared default line of work, as the checkout knows it; fires `landed` | the version-control contract, actions |

`blocked` fires out of band, at any stage where work stops unfinished or needs a person. The guards between stages, the
receipt, the replay, the floor and the attempt contract are the core, and none of them is about one kind of program.
The facts that make a particular worker safe, such as the arguments that start a coding agent unattended or bypass its
permission checks, are data in that worker's ingredient ([ADR 0015](../adrs/0015-development-lifecycle.md),
[ADR 0023](../adrs/0023-core-and-contracts.md)).

### A recipe run: one pass of one change

A [recipe](recipes.md) maps one kind of change onto the stages, and [`the-cook make <recipe>`](recipes.md#the-cook-make)
takes one change through them, the same way every time it runs:

| Stage | What the recipe declares | What the run does |
| --- | --- | --- |
| Plan | its `inputs` and `requires` | takes the task id (`--task <id>`) and the inputs from whoever runs it; before anything else, validates each input by type and checks the contracts, roles and lanes the recipe requires |
| Start | `workspace`, a name built from the inputs | creates a managed workspace and its branch `work/<name>` at the revision of the shared default line of work, as `the-cook workspace create` does, or uses the current managed workspace with `--here` |
| Implement | `steps`, in order | runs each [step](recipes.md#step-kinds): a command, a template written from the inputs, an attempt by an unattended worker, or a handoff to whoever drives the run |
| Verify | `verify.require`, the lanes it requires | runs the lanes itself: every lane the change selects and every required lane, never skipped and run in full when the change selects nothing for it; then writes the receipt |
| Complete | nothing | replays the proof from the base and, in a run with a task, fires `completed`, as for any work |
| Deliver, Review and merge, Landed | nothing: a recipe declares no lifecycle actions | as for any work: the repository's own actions, under the same guards |

The caller declares whether someone drives the run: a run is attended unless it is started with `--unattended`, as a
loop runner starts one. A handoff pauses an attended run until `the-cook make --continue`; in an unattended run it
stops the run as blocked instead, because it needs a person. A failed step, lane or replay stops the run too: it fires
`blocked` with the step and the reason and keeps the workspace, and `the-cook make --continue` resumes where it stopped
once a person has fixed what stopped it. A recipe can only add steps, required lanes and requirements: it cannot skip a
lane, weaken a guard or fire an event itself. A run reads its recipe once, from the base commit of the workspace it
creates, and follows that content to the end, so no step can change the recipe of the run it is in
([guards](recipes.md#guards), [ADR 0025](../adrs/0025-recipes.md)).

### Events and actions

`the-cook` fires each [lifecycle event](lifecycle.md#events) at fixed trigger points in its own commands, and a person,
a coordinating worker or a script around a loop runner can fire one by hand with `the-cook lifecycle run <event>`;
nothing in the binary knows when a loop runner's run starts or ends. `the-cook` fires `blocked` itself when a command
run without a terminal stops with exit 3 for a person to act, and when a recipe run stops; whoever drives a loop
runner's run fires it when the run stops unfinished. What happens at an event is the actions the repository declares in
`.cook/hooks.yaml`; the binary ships none. Everything that leaves the clone, such as a push, a pull request or a chat
notification, is one of those actions.

Every action has one kind ([action kinds](lifecycle.md#action-kinds)):

| Kind | What `the-cook` does |
| --- | --- |
| `run` | runs a shell command; values reach it only through `COOK_*` environment variables |
| `http` | makes an HTTP request itself, with the URL and header values read from environment variables, never from the file |
| `attempt` | starts a worker unattended on a prompt, under the [attempt contract](attempts.md#the-attempt-contract); it cannot commit, push or fire events |

Two worker events run declared `run` and `http` actions too: `attention`, when a worker waits for a person, and
`turn_end`, when it finishes a turn ([hooks](hooks.md)). `the-cook` itself is never a client of an MCP server: work that
needs an MCP tool is an `attempt` action, or the coordinating worker's job.

| Page | Covers |
| --- | --- |
| [Development lifecycle](lifecycle.md) | each stage, who acts at it, and the full event reference |
| [Safety guidelines](safety.md) | the threat model, the layers of defence, placing policy entries, unattended runs, secrets |
| [Pitfalls, gaps and traps](pitfalls.md) | what goes wrong, why, what `the-cook` does about it, and what you do |

## One split: deterministic work and variable work

`the-cook` does the work that has one right answer: moving a change between stages and holding the guards between them,
scaffolding, importing, rendering, checking drift, verifying, running a recipe's commands and templates, running one
worker for one task under the attempt contract ([`the-cook attempt`](attempts.md)), firing lifecycle events, removing
what it wrote. People and workers do the work that needs judgement: writing `AGENTS.md` prose, deciding which commands
belong in the [policy](policy.md)'s `allow` list, reviewing the [to-do](scaffold.md#the-to-do) an adoption leaves,
choosing the loop runner and tracker the team uses and wiring them into the repository's files (a [station](stations.md)
writes a starting point), arranging an unattended run's commits for review, writing memory, skills and recipes, and the
work a recipe hands to a worker in an attempt step or to a person in a handoff. For example, rendering the policy into a
coding agent's settings file has one right answer; deciding whether workers may run `terraform plan` without asking does
not. The embedded [shipped skill](skills.md#the-shipped-skill) and [`the-cook explain`](explain.md) teach coding agents
how to do that part.

## Two directories, two owners

| Directory | Belongs to | Holds | After `the-cook` is removed |
| --- | --- | --- | --- |
| `.cook/` | `the-cook`: its own versioned configuration | `config.yaml` (the profile), `policy.yaml`, `hooks.yaml`, `wrapper.yaml`, `manifest.json`, and `recipes/<name>/`, one directory per recipe | every file `the-cook` recorded there is deleted by both [removal modes](remove.md), and so is `recipes/`, each recipe listed in the plan first so a person can keep a copy; the directory goes once nothing else is in it; a file there that `the-cook` neither owns nor seeded, such as a `README.md` a person added, is listed and left for you to delete |
| `.agents/` | the cross-tool agent-content convention, shared with skills installers and the `AGENTS.md` ecosystem | `skills/` (including the tool-owned `skills/the-cook/`), `memory/`, `commands/`, `REVIEW.md` | kept, except the shipped skill and, on undo, memory skeleton files nobody edited: other tools and workers read the rest |

Nothing in `.cook/` is state, and nothing in it is ignored: every file there is committed and reviewed. State lives at
[configurable paths](#state) outside it. The repository root holds the wrapper, `cook`, and the files that carry
[managed blocks](render.md#managed-blocks): `AGENTS.md`, `README.md` and the files the ingredients in use render blocks
into, such as a coding agent's instruction file or the version control system's ignore file.

## Layers

| Layer | Who writes it | Committed | In an adopted repository |
| --- | --- | --- | --- |
| Engine | nobody in the repository; the release the pin names, downloaded per machine by `cook` | no | the `the-cook` binary in `$COOK_HOME` (default `$XDG_CACHE_HOME/the-cook`, or `~/.cache/the-cook` when `XDG_CACHE_HOME` is unset); it holds no ingredient, station or recipe |
| Cookbook archives | nobody in the repository; each cookbook's authors. The commands that pin, apply, add, update or fetch download, per machine, each ingredient and station the profile pins and the version each added recipe came from | no | `$COOK_HOME/cookbook/<sha256>.tar.gz` ([fetching](ingredients.md#fetching)) |
| Pin | `scaffold` and [`the-cook wrapper`](wrapper.md#the-cook-wrapper), after the running binary verifies the release's signed checksum file (`scaffold --offline` writes a [pending pin](wrapper.md#a-pending-pin)); owned, never edited by hand | yes | `.cook/wrapper.yaml` |
| Profile, policy, hooks | people and workers; seeded once by `scaffold`, which applies the [stations](stations.md) it is given: their entries become the repository's own and the ingredients they list are pinned under `ingredients:`. `the-cook station apply` does the same on request, and `the-cook ingredient pin` adds or moves one ingredient pin | yes | `.cook/config.yaml`, `.cook/policy.yaml`, `.cook/hooks.yaml` |
| Templates | written once by `the-cook station apply` or `scaffold` when the file is absent, then people and workers; never re-rendered or removed by `the-cook` | yes | a station's whole templates, such as a loop runner's configuration file from the `agentic-coding` station |
| Recipes | copied in by `the-cook recipe add`, `station apply` or `scaffold`, with their `origin`, or written by `the-cook recipe create`; then people and workers. `the-cook recipe update` merges a newer version into the copy; `render` never rewrites one | yes | `.cook/recipes/<name>/recipe.yaml` and the templates in its `files/` |
| Rendered | `the-cook render`, from the repository files, the binary's own templates and the renders of the [ingredients](ingredients.md) in use; never edited by hand | yes | `cook`, `.agents/skills/the-cook/SKILL.md` and `.cook/manifest.json`, which the binary renders itself; the owned keys, entries, files and skill links in each worker's native files, which its ingredient renders |
| Managed blocks | `the-cook render`, only between markers | yes | blocks inside `AGENTS.md` and `README.md`, which the binary renders; inside a coding agent's instruction file or the version control system's ignore file, which the ingredients in use render |
| Knowledge | people and workers; never generated | yes | `AGENTS.md` prose, `.agents/memory/*`, domain skills, `.agents/commands/`, `.agents/REVIEW.md` |
| State | `the-cook` | never (gitignored) | the [state paths](#state) (default under `.work/`) |

The three repository files are the contract between a repository and the tool. Each has one audience:

| File | Answers | Reference |
| --- | --- | --- |
| `.cook/config.yaml` (the profile) | What is this repository: modules, commands, bootstrap, lanes, workers, the ingredients and stations it pins, state paths, standing context? | [profile](profile.md), [context](context.md) |
| `.cook/policy.yaml` (the policy) | What may workers run without asking, only after asking, or never? | [policy](policy.md) |
| `.cook/hooks.yaml` (the hooks file) | What runs when a worker starts a session, edits a file, waits for a person or ends a turn, and what runs at each lifecycle event? | [hooks](hooks.md), [lifecycle](lifecycle.md) |

Beside them, each directory under `.cook/recipes/` answers one more question: what does one kind of change go through
here ([recipes](recipes.md))?

What `the-cook` renders and runs in a repository comes from the repository's own files, the release `.cook/wrapper.yaml`
pins and the ingredients `ingredients:` pins, and from nothing else: no inheritance, no includes, no sources outside the
repository ([ADR 0013](../adrs/0013-organization-content.md)). Rendering reads nothing else and needs no network once
the pinned ingredients are cached, so the same commit renders the same bytes on every machine, and people and workers
learn what a repository does by reading its files. The binary contains code for no program and data for none either:
every program it starts, calls or writes files for, the version control system and the workers included, is described by
an [ingredient](ingredients.md) ([ADR 0022](../adrs/0022-tool-integration.md),
[ADR 0023](../adrs/0023-core-and-contracts.md)).

### Ingredients, stations, recipes and cookbooks

A cookbook publishes three kinds of unit, each named and versioned, and one rule separates them: facts about programs
are pinned and rendered; practices are copied and become the repository's own
([ADR 0024](../adrs/0024-ingredients-and-stations.md), [ADR 0025](../adrs/0025-recipes.md)).

| Unit | What it is | How a repository holds it | When it acts |
| --- | --- | --- | --- |
| [Ingredient](ingredients.md) | facts about one program: the contract it fills, its renders, its floor, its checks | pinned by cookbook, version and sha256 in `ingredients:` | on every render and every launch |
| [Station](stations.md) | what one way of working needs in place: the ingredients to pin, workers and their roles, entries for the profile, the policy and the hooks file, standing context and lifecycle actions included, templates, and the recipes to add | applied once: its entries, templates and recipes become the repository's own; recorded in `stations:` for provenance and for applying a newer version | once, and again to take a newer version |
| [Recipe](recipes.md) | one kind of change, made many times: inputs, requirements, steps mapped onto the stages, required lanes | copied into `.cook/recipes/<name>/`, its origin recorded in the file, or written there by the repository; the repository's own to edit | every time someone runs it |

For example, the `agentic-coding` station pins ingredients for git, two coding agents, a loop runner and a tracker,
declares an interactive and an unattended worker, and adds the `implement-task` recipe. Beside it a repository keeps a
`migration` recipe of its own, and a `nestjs-module` recipe added from a cookbook. A station and a recipe may share a
name.

A [cookbook](stations.md#cookbooks) is a source such as `github:acme/cookbook`. Each of its tags publishes one archive
per unit, named by kind, `ingredient-<name>.tar.gz`, `station-<name>.tar.gz` and `recipe-<name>.tar.gz`, and one
`checksums.txt` with the sha256 of every archive. A repository may take units from any number of cookbooks; `the-cook`
ships none and names no default cookbook.

```yaml
# .cook/config.yaml
ingredients:
  - { name: git,    source: github:acme/cookbook, version: v1.2.0, sha256: <64 hex> }
  - { name: claude, source: github:acme/cookbook, version: v1.2.0, sha256: <64 hex> }
stations:
  - { name: agentic-coding, source: github:acme/cookbook, version: v1.2.0, sha256: <64 hex> }
vcs: git
workers:
  reviewer: { ingredient: claude, role: interactive }
```

`ingredients:` is what renders and runs depend on. `stations:` records which stations were applied and at which pin, for
provenance and for applying a newer version later; deleting an entry from it changes nothing rendered. A recipe records
its own origin in its file, `.cook/recipes/<name>/recipe.yaml`, and the repository's own recipes have none.

`./cook station apply agentic-coding --cookbook github:acme/cookbook@v1.2.0` downloads the tag's `checksums.txt` and the
station and verifies them, then plans the station's pin, the pins of the ingredients it lists, its entries, its
templates and the recipes it adds, and shows them as one diff. It asks, writes, fetches the ingredients and renders, so
the declaration and its rendered effect are one change to review. That review is the trust decision for everything the
ingredients run. `./cook ingredient pin <name> --cookbook <source>@<version>` adds or moves one ingredient pin the same
way, such as a newer version of the git ingredient, and `./cook recipe add <name> --cookbook <source>@<version>` copies
one recipe in, after checking that the repository has what it requires. From then on the binary checks every archive
against the sha256 the repository recorded for it, on every download and every load. Only those commands,
`recipe update`, `scaffold --cookbook` and `the-cook fetch` download ([fetching](ingredients.md#fetching)); everything
else reads the per-user cache, so a clone works offline once its ingredients are cached. A command that needs a contract
no pinned ingredient fills, or an ingredient that is not cached, exits 3 and names the command that supplies it.

A station reaches a repository once: its entries and templates are the repository's own from then on, and applying a
newer version adds only what the repository lacks. A recipe is a copy the repository edits like any other file:
`./cook recipe update <name> --cookbook <source>@<version>` merges a newer version into it, three ways from the version
the copy came from: it applies the clean hunks, leaves conflicts marked for a person or a worker, moves `origin` to the
newer version, and shows the diff before it writes. An ingredient's renders and [floor](ingredients.md#the-floor) come
from the ingredient on every render, as long as `ingredients:` pins it. A loop runner or a tracker is wired the same
way, by a command the profile declares, a lifecycle action, or the entries, templates and recipes of a station. An
organization's choices reach a repository the same way, as a reviewed change to its files, usually written by a coding
agent following a skill the organization publishes. For example, when acme decides that no worker runs
`terraform apply`, it publishes a skill saying so; in each of its Terraform repositories a coding agent following that
skill adds `[terraform, apply]` to `shared.deny` in `.cook/policy.yaml`, and the team reviews that change like any
other.

Rendered files are committed on purpose: a clone works before its pinned release is downloaded, and reviewers see
every permission change as a diff.

### Repository layout after adoption

```text
cook                    the wrapper: runs the pinned release (owned)
.cook/                   the-cook configuration: versioned, never state, never ignored
  config.yaml               profile, with the pinned ingredients and the applied stations
  policy.yaml               policy (repository entries only; floor rules are not here)
  hooks.yaml                worker hooks, notification hooks and lifecycle actions
  wrapper.yaml              the pinned release, its source and checksums
  manifest.json             ownership record
  recipes/<name>/           one recipe: recipe.yaml and the templates in files/ (the repository's own)
.agents/                    agent content: meaningful without the-cook
  skills/the-cook/SKILL.md   the shipped skill (owned)
  skills/<name>/            domain skills and shared skills the team installs (knowledge)
  memory/                   memory skeleton, then knowledge
  commands/                 agent commands (knowledge)
  REVIEW.md                 review guide (knowledge)
<worker files>              each worker's native files, as its ingredient names them: owned keys, entries,
                            files and a link to the shipped skill
AGENTS.md  README.md        managed blocks, one or more per module
<ingredient files>          managed blocks an ingredient renders, such as in an instruction file or an ignore file
.work/                      state.dir: receipts, sessions, locks, lifecycle log, hook cache (gitignored)
  workspaces/                workspaces.dir: managed workspaces
```

A repository that applies a [station](stations.md) with templates also holds them as its own files, such as a loop
runner's configuration file from the `agentic-coding` station, and the recipes the station adds sit in `.cook/recipes/`
beside the repository's own.

## Ownership and the manifest

`the-cook` never guesses what it owns ([ADR 0007](../adrs/0007-ownership.md)). `.cook/manifest.json` records two
kinds of content:

| Kind | What it is | Entries |
| --- | --- | --- |
| Owned | content `the-cook` renders and rewrites; never edited by hand | every owned file with its content hash (`cook`, `.cook/wrapper.yaml`, the whole files an ingredient renders, the shipped skill and its links); every managed block, with the file it lives in and its content hash; every owned key or entry inside a native file that `the-cook` merge-renders, including rendered `raw` hooks. Each entry names its owner: the binary, or the ingredient whose render produced it |
| Seeded | files `the-cook` writes once; from then on the repository owns them and `render` never rewrites them | `.cook/config.yaml`, `.cook/policy.yaml` and `.cook/hooks.yaml`, written by `scaffold`, and the `.agents/memory/` skeleton, written when the memory module is enabled, by `scaffold` or by the first `render` after the module is turned on; each with the hash of the content it was created with |

It also records the `schema` the files were rendered with.

The manifest is committed and rewritten by `render`, `scaffold`, `wrapper` and `remove`. Do not edit it. The
difference between owned and seeded content decides what each [removal mode](remove.md#undo-and-eject-compared)
deletes.

How the commands use it:

| Command | Uses the manifest to |
| --- | --- |
| [`render`](render.md) | write exactly the owned set, and drop files and blocks not produced, such as the renders of an ingredient deleted from `ingredients:` |
| [`render --check`](render.md#drift-check) | compare the owned set against what the sources produce |
| [`remove`](remove.md) | undo: delete the owned content, the seeded files in `.cook/`, and seeded files elsewhere only while they still match the hash they were created with; `--eject`: keep convention blocks and rendered native content as hand-owned, delete the rest of what it lists; both: refuse an owned path whose hash changed unless `--force`, and list and leave any file the manifest does not record |
| [`doctor`](doctor.md) | report missing, modified or dangling owned paths |

### Marking

Ownership is also visible in the files themselves:

| Where | Marker |
| --- | --- |
| Owned whole files that allow comments | first line `# generated by the-cook (schema 1): edit .cook/<source> and run ./cook render`; scripts carry it right after their `#!` line, and `SKILL.md` as a YAML comment on the first line inside its front matter, right after the opening `---`. `.cook/wrapper.yaml`, which no source renders, carries `# generated by the-cook: change with ./cook wrapper --version <tag>` |
| Managed blocks in Markdown | `<!-- cook:<id> -->` … `<!-- /cook:<id> -->` |
| Managed blocks in hash-comment files: `.gitignore`, YAML, TOML, shell | `# cook:<id>` … `# /cook:<id>` |
| Owned keys in JSON files | none in the file; the manifest is the record |

Text outside markers, and keys `the-cook` does not own, are preserved byte for byte.

## Conventions outlive the tool

Every convention a module establishes is written down where people and other tools already look, in plain language
([ADR 0018](../adrs/0018-conventions.md)):

- in a managed block of `AGENTS.md`: where managed workspaces live and how their branches are named, the permissions
  workers work under, the memory index, and the [standing context](context.md) entries declared `static`;
- for every path it creates, in a managed block of `.gitignore`, rendered by the version-control ingredient, such as
  `git`.

The workspaces module, for example, renders:

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

Workers that read `AGENTS.md`, such as coding agents, follow these conventions like any other instruction, and git
ignores what they say to ignore.

### Convention blocks and tool blocks

Managed blocks come in two classes:

| Class | States | Ids | After [`remove --eject`](remove.md#eject) |
| --- | --- | --- | --- |
| Convention block | a fact that stays true without `the-cook`; it never names a `the-cook` command | `AGENTS.md`: `workspaces`, `policy`, `memory`, `context`; every block an ingredient renders, such as the block that makes a coding agent's instruction file import `AGENTS.md` and every ignore-file block | markers removed, text kept |
| Tool block | how to use `the-cook` in this repository; it may name `./cook` commands | `AGENTS.md`: `commands`, `layout`, `verify`, `lifecycle`; `README.md`: `commands` | removed |

So after an eject, what is left in `AGENTS.md` and `.gitignore` is true and usable without the tool, and nothing in
them points at a command that does not exist.

Block ids are per module and per ingredient, so a module or an ingredient owns its blocks and removing it removes them
([block ids](render.md#block-ids)). A block's text states its content as fact; nothing but its marker says `the-cook`
wrote it. Each block lists its own `.gitignore` entries even when another block's entry already covers the path: the git
ingredient's `state` block ignores `/.work/` and its `workspaces` block `/.work/workspaces/`. Removing one block
therefore never un-ignores a path another still uses.

`AGENTS.md` is read at the start of every session of a worker that reads it, such as a coding agent, so the blocks
`the-cook` writes there stay short: by default at most 40 lines together and 8 lines each, the `context` block excepted
([profile](profile.md#blocks)). Detail lives in [`./cook explain <topic>`](explain.md) and the shipped skill.

## Modules

The profile's `modules:` map turns features on and off. Each module is opt-in and removable on its own.

| Module | Adds to the repository |
| --- | --- |
| `hooks` | `the-cook hook` entries in each worker's native files, from `.cook/hooks.yaml`: post-edit commands, validators, session context, notification hooks, `raw` hooks |
| `verify` | `the-cook verify`; allow entries for the command; the `verify` block in `AGENTS.md`; the receipt guard on `completed` with scope `task` |
| `memory` | the `.agents/memory/` skeleton (seeded when the module is enabled, never re-rendered) and the `memory` block in `AGENTS.md` |

Always present, whatever the modules: the lifecycle with its events, guards and declared actions, the `.cook/` files,
`cook`, the rendered policy, the shipped skill and its links, the manifest, the [`the-cook workspace`](workspaces.md)
commands with task binding, the [`the-cook make`](recipes.md#the-cook-make) and `the-cook recipe` commands, the
[`the-cook attempt`](attempts.md) command, and these blocks: `commands`, `layout`, `workspaces`, `lifecycle` and
`policy` in `AGENTS.md`; `commands` in `README.md`; and the blocks the ingredients in use render, such as the
version-control ingredient's `state` and `workspaces` blocks in its ignore file.

[Standing context](context.md) is declared in the profile's `context` section. Entries delivered by hook need the
`hooks` module; `static` entries go into the `context` block of `AGENTS.md` whatever the modules; and in an attempt
whose prompt arrives on stdin, the `always` entries go in the prompt
([delivery in the prompt](context.md#delivery-in-the-prompt)).

## State

State is clone-local, gitignored and disposable. It never leaves the clone unless the repository chose otherwise. It
lives at paths the [profile](profile.md#state-paths) sets, outside `.cook/`; the defaults are shown here.

| Path (profile key) | Owner | Holds |
| --- | --- | --- |
| `.work/` (`state.dir`), in the primary checkout | `the-cook` | `lifecycle.jsonl` (every lifecycle run of the clone), locks shared by every workspace of the clone |
| `.work/` (`state.dir`), in each checkout | `the-cook` | `session.json` (managed workspace session: base, task, actor), `verified.json` (the [receipt](verify.md) of a passing `the-cook verify`), `hook-checks.json` (post-edit cache), `context/<session-id>.json` (which triggered context entries a session has received), `last-attempt-output` and `last-attempt.patch` (the full output and the changes of the last failed attempt), the record that `bootstrap` ran for an attempt |
| `.work/workspaces/` (`workspaces.dir`) | `the-cook` | each managed workspace, at `<dir>/<name>` |

Other tools keep their own state where they choose, such as a task tracker's database directory; `the-cook` neither
reads nor writes it, and ignores it only through an ignore-file block of that program's ingredient. The `layout` block
of `AGENTS.md` names every state path, and the version-control ingredient's ignore-file blocks ignore each one the
repository does not track. No state path may point inside `.cook/` or `.agents/`: those directories hold committed files
only, so the profile refuses such a path.

## Adoption classes

Repositories start in one of three states. `the-cook` treats each one deterministically; only the leftovers need
judgement, from a person or a coding agent.

| Class | Looks like | Command | What is left for judgement |
| --- | --- | --- | --- |
| Greenfield | no permissions or hooks in any worker's native files, no workflow engine of its own | `the-cook scaffold` | review the seeded profile; write `AGENTS.md` prose |
| Brownfield, simple | permissions and hooks hand-written in a worker's native files | `the-cook scaffold --adopt` | imported personal entries and allow/deny conflicts to review; `raw` hooks worth modelling |
| Brownfield, complex | its own workflow around its workers: verification scripts, a loop runner's configuration, a task tracker's data | `the-cook scaffold --adopt` | lane definitions from the verification scripts; pointing the loop runner's command for each task at `./cook make <recipe> --task <id> --unattended` or `./cook attempt`, and the tracker's updates at lifecycle actions, which a [station](stations.md) such as `agentic-coding` can write, with the `implement-task` recipe the runner runs |

`scaffold --adopt` imports every permission rule and hook losslessly, proves it with an equivalence check, and prints
a to-do of the leftovers; [`the-cook doctor`](doctor.md) keeps reporting open items until they are resolved. See
[scaffold](scaffold.md#adopt) for the import scopes and rules.

## Optional and transparent

A repository adopts `the-cook` without forcing anyone else to.

| Who | What they experience |
| --- | --- |
| A teammate whose machine has not downloaded the pinned release | Committed rendered files give their workers the same permissions and instructions, convention blocks and `static` context included. The first `./cook` command downloads and verifies the release. Until then, every `the-cook hook` entry prints one line on stderr and exits 0, the session-start hook also adds one line to the worker's context, `the-cook <version> is not cached on this machine; run ./cook version once to fetch it.`, `raw` hooks keep running, and hook-delivered context, notification hooks and lifecycle events do nothing. |
| CI | Builds, tests, deploys and releases are unchanged; nothing requires the binary. `./cook render --check` and `./cook doctor` are optional steps; the wrapper downloads and verifies the pinned release ([wrapper](wrapper.md#ci)). |
| A person who stops using it | `the-cook remove` undoes the adoption from the manifest; `the-cook remove --eject` stops managing the repository and keeps the result: convention blocks, `.gitignore` entries, permissions and `raw` hooks ([remove](remove.md)). |
| A person who uses only part of it | Every module is opt-in; disabling one removes its files and blocks on the next render. |

## Built-in invariants

These hold in every repository, for what the binary renders and for every worker the binary starts, and cannot be
turned off by any repository file ([ADR 0011](../adrs/0011-safety-invariants.md)). They are the process, built into the
binary. The facts about a program they apply, such as which arguments bypass a worker's permission checks or which
command rewrites the default branch, are [floor](ingredients.md#the-floor) rules of that program's ingredient: rendered
on every render, never written into the policy and out of its reach. A worker a person starts some other way, such as
from their own loop-runner configuration, is outside them: `the-cook` neither detects nor prevents it
([ADR 0022](../adrs/0022-tool-integration.md)). The numbers match ADR 0011.

1. Workers `the-cook attempt` starts never run with permission bypass: every argument the worker's ingredient marks as a
   floor bypass argument is refused with exit 1, naming the argument.
2. The deny rules every ingredient in use marks as floor are rendered into every worker's output on every render, and
   the policy cannot remove or weaken them. A version-control ingredient, for example, marks a force push to the
   default branch it reports, and a task runner's ingredient marks the syntax that chains a second task onto an allowed
   one.
3. Verification proofs are replayed outside the tree the worker can write: `the-cook attempt` reruns the lanes the
   worker's [receipt](verify.md) selected, with the profile read from the base commit.
4. Completion is withheld until the replayed proof passes: only then does `the-cook attempt` exit 0, release a loop
   runner's completion signal and, in a workspace bound to a task, fire `completed`. `completed` with scope `task` is
   refused without a valid receipt when the verify module is enabled, whoever fires it.
5. Hooks never decide permissions.
6. The binary never writes knowledge (prose, memory, domain skills), contains no logic for the platform that hosts the
   team's work, such as GitHub, and never writes issues, projects, boards, comments, reviews or labels.
7. The binary never reconfigures state it did not create, such as the version control system's hook settings, a
   worker's instruction file content outside its managed blocks or a `.cook/` directory another tool owns, and reads no
   program's own configuration, such as a task tracker's database or a loop runner's configuration file. A station's
   template is written only when its file is absent.
8. An unattended worker cannot change the rules it runs under. Before it starts the worker, `the-cook attempt` resolves
   the version-control ingredient, the worker's ingredient and its jail's from the verified cache, and restores the
   tool-owned files, the renders of every ingredient in use, `cook` and its pin, from the base commit. After the worker
   exits it fails the attempt if the worker changed them, a file under `.cook/` or a file standing context names. The
   standing context an attempt receives is read from the same base commit; a base that cannot be read fails the attempt.
   A failed attempt returns the checkout to its base. A worker with a [jail](workers.md#jails) runs inside it with the
   version-control metadata and the per-user cache read-only, and the replay of its work runs in the same jail.
9. Lifecycle and notification actions never run inside an attempt: `the-cook attempt` fires events in its own process
   after the worker exits, the binary refuses `the-cook lifecycle` under the attempt mark (`COOK_ATTEMPT=1`), which it
   passes through a worker's jail too, and the binary renders its own `unattended.deny` entry for it in every spelling
   (`the-cook`, `./cook`, `cook`).
10. `deliver` always refuses on the default branch and with uncommitted changes and, when the verify module is enabled,
    without a valid receipt for the work's base.
11. An `attempt` action runs its worker under the attempt contract: the unattended launch arguments of its ingredient,
    the floor and the repository's `unattended.deny`, the floor bypass arguments refused, and the tamper check of
    invariant 8. It cannot commit, push or fire lifecycle events.
12. Guards are enforced by the binary itself. Policy entries for `the-cook` commands are defence in depth: a command
    has more spellings (an absolute path, `sh cook`) than prefix rules can list.

A recipe's `attempt` step runs its worker under the same attempt contract as an `attempt` action, and nothing in a
recipe turns an invariant off: a recipe can only add steps, required lanes and requirements
([guards](recipes.md#guards)).

A floor rule holds while its ingredient is in use. `the-cook ingredient list --cookbook <source>@<version>` shows which
ingredients of a cookbook tag detect their program in the repository, such as a task runner whose configuration file is
present while its ingredient is not in `ingredients:` ([ingredients](ingredients.md)).

`./cook explain invariants` prints this list with the reasoning for each item.
