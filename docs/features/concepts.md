# Concepts

## Objective

**Problem.** A developer or an agent working in an adopted repository meets files written by different hands: some by
people, some generated from other files, some local state that must never be committed. Without knowing which is which,
they edit a generated file and lose the edit or fail a check, commit state that belongs to one machine, or cannot tell
what would remain if the repository stopped using the tool. A newcomer also cannot tell which work follows fixed rules
and which is left to their judgement.

**Goal.** For any file in an adopted repository, a reader can tell who writes it, whether it is committed, where a
change to it belongs and what happens to it if the tool is removed. For any piece of setup work, they can tell whether
it follows a fixed rule or needs judgement.

## Overview

`the-cook` divides a repository into layers and directories and records which files it owns. The binary keeps the
process, and [recipes](recipes.md) describe the programs it works with, git and the agent harnesses included. It writes
down the conventions it sets up and adapts to repositories in different starting states, and an adopted repository
keeps working without it.

Examples on this page run commands as `./cook <command>`, through the repository's [wrapper](wrapper.md); with a
global install, `the-cook <command>` is equivalent.

## One split: deterministic work and variable work

`the-cook` does the work that has one right answer: scaffolding, importing, rendering, checking drift, verifying,
running one agent for one task under the worker contract ([`the-cook worker`](ralph.md)), firing lifecycle events,
removing what it wrote. Agents and people do the work that needs judgement: writing `AGENTS.md` prose, deciding which
commands belong in the [policy](policy.md)'s `allow` list, reviewing the [to-do](scaffold.md#the-to-do) an adoption
leaves, choosing the loop runner and tracker the team uses and wiring them into the repository's files (a
[recipe](recipes.md) writes a starting point), arranging an unattended run's commits for review, writing memory and
skills. For example, rendering the policy into `.claude/settings.json` has one right answer; deciding whether agents may
run `terraform plan` without asking does not. The embedded [shipped skill](skills.md#the-shipped-skill) and
[`the-cook explain`](explain.md) teach agents how to do that part.

## Two directories, two owners

| Directory | Belongs to | Holds | After `the-cook` is removed |
| --- | --- | --- | --- |
| `.cook/` | `the-cook`: its own versioned configuration | `config.yaml` (the profile), `policy.yaml`, `hooks.yaml`, `wrapper.yaml`, `manifest.json` | every file `the-cook` recorded there is deleted by both [removal modes](remove.md), and the directory once nothing else is in it; a file there that `the-cook` neither owns nor seeded, such as a `README.md` a person added, is listed and left for you to delete |
| `.agents/` | the cross-tool agent-content convention, shared with the `skills` CLI and the `AGENTS.md` ecosystem | `skills/` (including the tool-owned `skills/the-cook/`), `memory/`, `commands/`, `REVIEW.md` | kept, except the shipped skill and, on undo, memory skeleton files nobody edited: other tools and agents read the rest |

Nothing in `.cook/` is state, and nothing in it is ignored: every file there is committed and reviewed. State lives
at [configurable paths](#state) outside it. The repository root holds the wrapper, `cook`, and the files that
carry [managed blocks](render.md#managed-blocks): `AGENTS.md`, `CLAUDE.md`, `README.md` and `.gitignore`.

## Layers

| Layer | Who writes it | Committed | In an adopted repository |
| --- | --- | --- | --- |
| Engine | nobody in the repository; the release the pin names, downloaded per machine by `cook` | no | the `the-cook` binary in `$COOK_HOME` (default `$XDG_CACHE_HOME/the-cook`, or `~/.cache/the-cook` when `XDG_CACHE_HOME` is unset), with its [builtin recipes](recipes.md#builtin-recipes) embedded |
| Fetched recipes | nobody in the repository; each recipe's author. `the-cook recipe fetch` downloads, per machine, the version and sha256 the profile pins | no | `$COOK_HOME/recipes/<sha256>.tar.gz` ([fetched recipes](recipes.md#fetched-recipes)) |
| Pin | `scaffold` and [`the-cook wrapper`](wrapper.md#the-cook-wrapper), after the running binary verifies the release's signed checksum file (`scaffold --offline` writes a [pending pin](wrapper.md#a-pending-pin)); owned, never edited by hand | yes | `.cook/wrapper.yaml` |
| Profile, policy, hooks | people and agents; seeded once by `scaffold`, which lists the detected [recipes](recipes.md#builtin-recipes) under `recipes:` and applies their [entries](recipes.md#entries); `the-cook recipe apply` adds a recipe and its entries on request | yes | `.cook/config.yaml`, `.cook/policy.yaml`, `.cook/hooks.yaml` |
| Templates | written once by `the-cook recipe apply` or `scaffold` when the file is absent, then people and agents; never re-rendered or removed by `the-cook` | yes | a recipe's whole [templates](recipes.md#templates), such as `.ralph-tui/config.toml` from the `ralph-tui` recipe |
| Rendered | `the-cook render`, from the repository files, the binary's own templates and the [renders](recipes.md#renders) of the recipes in use; never edited by hand | yes | `cook`, `.agents/skills/the-cook/SKILL.md` and `.cook/manifest.json`, which the binary renders itself; owned keys of `.claude/settings.json` and `.codex/config.toml`, owned entries of `.codex/hooks.json`, `.codex/rules/the-cook.rules` and skill links, which the claude and codex recipes render |
| Managed blocks | `the-cook render`, only between markers | yes | blocks inside `AGENTS.md` and `README.md`, which the binary renders; inside `CLAUDE.md` and `.gitignore`, which the claude, git and ralph-tui recipes render |
| Knowledge | people and agents; never generated | yes | `AGENTS.md` prose, `.agents/memory/*`, domain skills, `.agents/commands/`, `.agents/REVIEW.md` |
| State | `the-cook` | never (gitignored) | the [state paths](#state) (default under `.work/`) |

The three repository files are the contract between a repository and the tool. Each has one audience:

| File | Answers | Reference |
| --- | --- | --- |
| `.cook/config.yaml` (the profile) | What is this repository: modules, commands, bootstrap, lanes, agents, state paths, standing context? | [profile](profile.md), [context](context.md) |
| `.cook/policy.yaml` (the policy) | What may agents run without asking, only after asking, or never? | [policy](policy.md) |
| `.cook/hooks.yaml` (the hooks file) | What runs when an agent starts a session, edits a file, waits for a person or ends a turn, and what runs at each lifecycle event? | [hooks](hooks.md), [lifecycle](lifecycle.md) |

What `the-cook` renders and runs in a repository comes from the repository's own files, the release
`.cook/wrapper.yaml` pins and the recipes `recipes:` names, a fetched one pinned by its sha256, and from nothing
else: no inheritance, no includes, no sources outside the repository ([ADR 0013](../adrs/0013-organization-content.md)).
Rendering reads nothing else and needs no network, so the same commit renders the same bytes on every machine, and
people and agents learn what a repository does by reading its files. The binary contains code for no program: every
program it starts, calls or writes files for, git and the agent harnesses included, is described by a
[recipe](recipes.md) ([ADR 0022](../adrs/0022-tool-integration.md), [ADR 0023](../adrs/0023-core-and-contracts.md)). A
recipe's entries and templates reach a repository once, written by `the-cook recipe apply` or `scaffold`, and are the
repository's own from then on; its renders and [floor](recipes.md#the-floor) come from the recipe on every render, as
long as `recipes:` names it. A loop runner or a tracker is wired the same way, by a command the profile declares, a
lifecycle action, or the entries and templates of its recipe. An organization's choices reach a repository the same way,
as a reviewed change to its files, usually written by an agent following a skill the organization publishes. For
example, when acme decides that no agent runs `terraform apply`, it publishes a skill saying so; in each of its
Terraform repositories an agent following that skill adds `[terraform, apply]` to `shared.deny` in
`.cook/policy.yaml`, and the team reviews that change like any other.

Rendered files are committed on purpose: a clone works before its pinned release is downloaded, and reviewers see
every permission change as a diff.

### Repository layout after adoption

```text
cook                    the wrapper: runs the pinned release (owned)
.cook/                   the-cook configuration: versioned, never state, never ignored
  config.yaml               profile
  policy.yaml               policy (repository entries only; floor rules are not here)
  hooks.yaml                agent hooks, notification hooks and lifecycle actions
  wrapper.yaml              the pinned release, its source and checksums
  manifest.json             ownership record
.agents/                    agent content: meaningful without the-cook
  skills/the-cook/SKILL.md   the shipped skill (owned)
  skills/<name>/            domain skills and shared skills the team installs (knowledge)
  memory/                   memory skeleton, then knowledge
  commands/                 agent commands (knowledge)
  REVIEW.md                 review guide (knowledge)
.claude/
  settings.json             owned keys: permissions, the-cook hook entries
  settings.local.json       never written; read only by scaffold --import local
  skills/the-cook -> ../../.agents/skills/the-cook
.codex/
  config.toml               owned keys only
  hooks.json                owned entries only
  rules/the-cook.rules       owned file
  skills/the-cook -> ../../.agents/skills/the-cook
AGENTS.md  README.md  CLAUDE.md  .gitignore   managed blocks, one or more per module
.work/                      state.dir: receipts, sessions, locks, lifecycle log, hook cache (gitignored)
  workspaces/                workspaces.dir: managed workspaces
```

A repository that applies a [recipe](recipes.md) with templates also holds them as its own files, such as
`.ralph-tui/config.toml` from the `ralph-tui` recipe.

## Ownership and the manifest

`the-cook` never guesses what it owns ([ADR 0007](../adrs/0007-ownership.md)). `.cook/manifest.json` records two
kinds of content:

| Kind | What it is | Entries |
| --- | --- | --- |
| Owned | content `the-cook` renders and rewrites; never edited by hand | every owned file with its content hash (`cook`, `.cook/wrapper.yaml`, `.codex/rules/the-cook.rules`, the shipped skill and its links); every managed block, with the file it lives in and its content hash; every owned key or entry inside a native file that `the-cook` merge-renders, including rendered `raw` hooks. Each entry names its owner: the binary, or the recipe whose render produced it |
| Seeded | files `the-cook` writes once; from then on the repository owns them and `render` never rewrites them | `.cook/config.yaml`, `.cook/policy.yaml` and `.cook/hooks.yaml`, written by `scaffold`, and the `.agents/memory/` skeleton, written when the memory module is enabled, by `scaffold` or by the first `render` after the module is turned on; each with the hash of the content it was created with |

It also records the `schema` the files were rendered with.

The manifest is committed and rewritten by `render`, `scaffold`, `wrapper` and `remove`. Do not edit it. The
difference between owned and seeded content decides what each [removal mode](remove.md#undo-and-eject-compared)
deletes.

How the commands use it:

| Command | Uses the manifest to |
| --- | --- |
| [`render`](render.md) | write exactly the owned set, and drop files and blocks that are no longer produced, such as the renders of a recipe deleted from `recipes:` |
| [`render --check`](render.md#drift-check) | compare the owned set against what the sources produce now |
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
  agents work under, the memory index, and the [standing context](context.md) entries declared `static`;
- for every path it creates, in a managed block of `.gitignore`, rendered by the version-control recipe, `git`.

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

Agents read these conventions from `AGENTS.md` like any other instruction, and git ignores what they say to ignore.

### Convention blocks and tool blocks

Managed blocks come in two classes:

| Class | States | Ids | After [`remove --eject`](remove.md#eject) |
| --- | --- | --- | --- |
| Convention block | a fact that stays true without `the-cook`; it never names a `the-cook` command | `AGENTS.md`: `workspaces`, `policy`, `memory`, `context`; `CLAUDE.md`: `import`; every `.gitignore` block | markers removed, text kept |
| Tool block | how to use `the-cook` in this repository; it may name `./cook` commands | `AGENTS.md`: `commands`, `layout`, `verify`, `lifecycle`; `README.md`: `commands` | removed |

So after an eject, what is left in `AGENTS.md` and `.gitignore` is true and usable without the tool, and nothing in
them points at a command that no longer exists.

Block ids are per module and per recipe, so a module or a recipe owns its blocks and removing it removes them
([block ids](render.md#block-ids)). A block's text states its content as fact; nothing but its marker says `the-cook`
wrote it. Each block lists its own `.gitignore` entries even when another block's entry already covers the path: the git
recipe's `state` block ignores `/.work/` and its `workspaces` block `/.work/workspaces/`. Removing one block therefore
never un-ignores a path another still uses.

`AGENTS.md` is read at the start of every agent session, so the blocks `the-cook` writes there stay short: by default
at most 40 lines together and 8 lines each, the `context` block excepted ([profile](profile.md#blocks)). Detail lives
in [`./cook explain <topic>`](explain.md) and the shipped skill.

## Modules

The profile's `modules:` map turns features on and off. Each module is opt-in and removable on its own.

| Module | Adds to the repository |
| --- | --- |
| `hooks` | `the-cook hook` entries in each agent's native files, from `.cook/hooks.yaml`: post-edit commands, validators, session context, notification hooks, `raw` hooks |
| `verify` | `the-cook verify`; allow entries for the command; the `verify` block in `AGENTS.md`; the receipt guard on `completed` with scope `task` |
| `memory` | the `.agents/memory/` skeleton (seeded when the module is enabled, never re-rendered) and the `memory` block in `AGENTS.md` |

Always present, whatever the modules: the `.cook/` files, `cook`, the rendered policy, the shipped skill and its links,
the manifest, the [`the-cook workspace`](workspaces.md) commands with task binding, the [`the-cook worker`](ralph.md)
command, and these blocks: `commands`, `layout`, `workspaces`, `lifecycle` and `policy` in `AGENTS.md`; `commands` in
`README.md`; `import` in `CLAUDE.md` with the claude recipe; `state` and `workspaces` in `.gitignore` with the git
recipe.

[Standing context](context.md) is declared in the profile's `context` section. Entries delivered by hook need the
`hooks` module; `static` entries go into the `context` block of `AGENTS.md` whatever the modules.

## State

State is clone-local, gitignored and disposable. It never leaves the clone unless the repository chose otherwise. It
lives at paths the [profile](profile.md#state-paths) sets, outside `.cook/`; the defaults are shown here.

| Path (profile key) | Owner | Holds |
| --- | --- | --- |
| `.work/` (`state.dir`), in the primary checkout | `the-cook` | `lifecycle.jsonl` (every lifecycle run of the clone), locks shared by every workspace of the clone |
| `.work/` (`state.dir`), in each checkout | `the-cook` | `session.json` (managed workspace session: base, task, actor), `verified.json` (the [receipt](verify.md) of a passing `the-cook verify`), `hook-checks.json` (post-edit cache), `context/<session-id>.json` (which triggered context entries a session has received), `last-worker-output` and `last-attempt.patch` (the full output and the changes of the last failed `the-cook worker` attempt), the record that `bootstrap` ran for a worker |
| `.work/workspaces/` (`workspaces.dir`) | `the-cook` | each managed workspace, at `<dir>/<name>` |

Other tools keep their own state where they choose, such as Beads in `.beads/`; `the-cook` neither reads nor writes it,
and ignores it only through a recipe's `.gitignore` block, as the `ralph-tui` recipe does for `.ralph-tui/`. The
`layout` block of `AGENTS.md` names every state path, and the `.gitignore` blocks ignore each one the repository does
not track. No state path may point inside `.cook/` or `.agents/`: those directories hold committed files only, so the
profile refuses such a path.

## Adoption classes

Repositories start in one of three states. `the-cook` treats each one deterministically; only the leftovers go to an
agent.

| Class | Looks like | Command | What is left for an agent |
| --- | --- | --- | --- |
| Greenfield | no `.claude/settings.json` permissions or hooks, no workflow engine of its own | `the-cook scaffold` | review the seeded profile; write `AGENTS.md` prose |
| Brownfield, simple | permissions and hooks hand-written in `.claude/settings.json` or `.codex/` | `the-cook scaffold --adopt` | imported personal entries and allow/deny conflicts to review; `raw` hooks worth modelling |
| Brownfield, complex | its own workflow around agents: verification scripts, a loop runner's configuration such as a root `.ralph-tui/`, a tracker such as `.beads/` | `the-cook scaffold --adopt` | lane definitions from the verification scripts; pointing the loop runner's agent command at `./cook worker` and the tracker's updates at lifecycle actions, which a [recipe](recipes.md#builtin-recipes) can write |

`scaffold --adopt` imports every permission rule and hook losslessly, proves it with an equivalence check, and prints
a to-do of the leftovers; [`the-cook doctor`](doctor.md) keeps reporting open items until they are resolved. See
[scaffold](scaffold.md#adopt) for the import scopes and rules.

## Development lifecycle

Work moves through five [lifecycle events](lifecycle.md): `start`, `verified`, `completed`, `deliver` and `landed`. A
sixth event, `blocked`, fires out of band at any stage: `the-cook` fires it when a command run without a terminal stops
with exit 3 for a person to act, and whoever drives an unattended run fires it when the run stops unfinished. `the-cook`
fires each event at fixed trigger points in its own commands, and a person, a coordinating agent or a script around a
loop runner can fire one by hand with `the-cook lifecycle run <event>`; nothing in the binary knows when an unattended
run starts or ends. What happens at an event is the actions the repository declares in `.cook/hooks.yaml`; the binary
ships none. Everything that leaves the clone, such as a push, a pull request or a chat notification, is one of those
actions.

Every action has one kind ([action kinds](lifecycle.md#action-kinds)):

| Kind | What `the-cook` does |
| --- | --- |
| `run` | runs a shell command; values reach it only through `COOK_*` environment variables |
| `http` | makes an HTTP request itself, with the URL and header values read from environment variables, never from the file |
| `agent` | starts an agent headless, under the [worker contract](ralph.md#the-worker-contract); it cannot commit, push or fire events |

Two agent events run declared `run` and `http` actions too: `attention`, when an agent waits for a person, and
`turn_end`, when it finishes a turn ([hooks](hooks.md)). `the-cook` itself is never a client of an MCP server: work that
needs an MCP tool is an `agent` action, or the coordinating agent's job.

| Page | Covers |
| --- | --- |
| [Development lifecycle](lifecycle.md) | the stages from plan to landed, who acts at each, and the full event reference |
| [Safety guidelines](safety.md) | the threat model, the layers of defence, placing policy entries, unattended runs, secrets |
| [Pitfalls, gaps and traps](pitfalls.md) | what goes wrong, why, what `the-cook` does about it, and what you do |

## Optional and transparent

A repository adopts `the-cook` without forcing anyone else to.

| Who | What they experience |
| --- | --- |
| A teammate whose machine has not downloaded the pinned release | Committed rendered files give their agent the same permissions and instructions, convention blocks and `static` context included. The first `./cook` command downloads and verifies the release. Until then, every `the-cook hook` entry prints one line on stderr and exits 0, the session-start hook also adds one line to the agent's context, `the-cook <version> is not cached on this machine; run ./cook version once to fetch it.`, `raw` hooks keep running, and hook-delivered context, notification hooks and lifecycle events do nothing. |
| CI | Builds, tests, deploys and releases are unchanged; nothing requires the binary. `./cook render --check` and `./cook doctor` are optional steps; the wrapper downloads and verifies the pinned release ([wrapper](wrapper.md#ci)). |
| A person who stops using it | `the-cook remove` undoes the adoption from the manifest; `the-cook remove --eject` stops managing the repository and keeps the result: convention blocks, `.gitignore` entries, permissions and `raw` hooks ([remove](remove.md)). |
| A person who uses only part of it | Every module is opt-in; disabling one removes its files and blocks on the next render. |

## Built-in invariants

These hold in every repository, for what the binary renders and for every agent the binary starts, and cannot be turned
off by any repository file ([ADR 0011](../adrs/0011-safety-invariants.md)). They are the process, built into the
binary. The facts about a program they apply, such as which arguments bypass an agent's permission checks or which
command rewrites the default branch, are [floor](recipes.md#the-floor) rules of that program's recipe: rendered on every
render, never written into the policy and out of its reach. An agent a person starts some other way, such as from their
own loop-runner configuration, is outside them: `the-cook` neither detects nor prevents it
([ADR 0022](../adrs/0022-tool-integration.md)). The numbers match ADR 0011.

1. Agents `the-cook worker` starts never run with permission bypass: every argument the agent's harness recipe marks as
   a floor bypass argument, such as Claude Code's `--dangerously-skip-permissions` or Codex's `--yolo`, is refused with
   exit 1, naming the argument.
2. The deny rules every recipe in use marks as floor are rendered into every agent's output on every render, and the
   policy cannot remove or weaken them. The builtin recipes mark a force push to the default branch the
   version-control recipe reports (the git recipe) and mise `:::` task chaining (the mise recipe), so an allowed
   `mise run lint` cannot carry a second task, as `mise run lint ::: deploy` would.
3. Verification proofs are replayed outside the tree the agent can write: `the-cook worker` reruns the lanes the agent's
   [receipt](verify.md) selected, with the profile read from the base commit.
4. Completion is withheld until the replayed proof passes: only then does `the-cook worker` exit 0, release a loop
   runner's completion signal and, in a workspace bound to a task, fire `completed`. Whoever fires `completed` with
   scope `task`, it is refused without a valid receipt when the verify module is enabled.
5. Hooks never decide permissions.
6. The binary never writes knowledge (prose, memory, domain skills), contains no logic for the platform that hosts the
   team's work, such as GitHub, and never writes issues, projects, boards, comments, reviews or labels.
7. The binary never reconfigures state it did not create, such as `core.hooksPath`, `CLAUDE.md` content or a
   `.cook/` directory another tool owns, and reads no program's own configuration, such as `.beads/` or
   `.ralph-tui/config.toml`. A recipe's template is written only when its file is absent.
8. An unattended agent cannot change the rules it runs under. Before it starts the agent, `the-cook worker` resolves the
   version-control recipe and the agent's harness recipe from the release or the verified cache, and restores the
   tool-owned files, the renders of every recipe in use, `cook` and its pin, from the base commit. After the agent
   exits it fails the attempt if the agent changed them, a file under `.cook/` or a file standing context names. The
   standing context a worker receives is read from the same base commit; a base that cannot be read fails the attempt.
   A failed attempt returns the checkout to its base.
9. Lifecycle and notification actions never run inside an unattended agent: `the-cook worker` fires events in its own
   process after the agent exits, the binary refuses `the-cook lifecycle` under the unattended mark (`COOK_WORKER=1`),
   and the binary renders its own `worker.deny` entry for it in every spelling (`the-cook`, `./cook`, `cook`).
10. `deliver` always refuses on the default branch and with uncommitted changes and, when the verify module is enabled,
    without a valid receipt for the work's base.
11. An `agent` action runs its agent under the worker contract: auto permission mode, the worker denies, the floor
    bypass arguments refused, the tamper check of invariant 8. It cannot commit, push or fire lifecycle events.
12. Guards are enforced by the binary itself. Policy entries for `the-cook` commands are defence in depth: a command
    has more spellings (an absolute path, `sh cook`) than prefix rules can list.

A floor rule holds while its recipe is in use; [`the-cook doctor`](doctor.md) reports a program whose recipe's detect
rule matches while the recipe is not in `recipes:`, such as a `mise.toml` without the mise recipe.

`./cook explain invariants` prints this list with the reasoning for each item.
