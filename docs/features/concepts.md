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

`harness` divides a repository into layers and directories and records which files it owns. It writes down the
conventions it sets up and adapts to repositories in different starting states, and an adopted repository keeps working
without it.

Examples on this page run commands as `./harnessw <command>`, through the repository's [wrapper](wrapper.md); with a
global install, `harness <command>` is equivalent.

## One split: deterministic work and variable work

`harness` does the work that has one right answer: scaffolding, importing, rendering, checking drift, verifying, running
one agent for one task under the worker contract ([`harness worker`](ralph.md)), firing lifecycle events, removing what
it wrote. Agents and people do the work that needs judgement: writing `AGENTS.md` prose, deciding which commands belong
in the [policy](policy.md)'s `allow` list, reviewing the [to-do](scaffold.md#the-to-do) an adoption leaves, choosing the
loop runner and tracker the team uses and wiring them into the repository's files (a [tool preset](presets.md) writes a
starting point), arranging an unattended run's commits for review, writing memory and skills. For example, rendering the
policy into `.claude/settings.json` has one right answer; deciding whether agents may run `terraform plan` without
asking does not. The embedded [harness skill](skills.md#the-harness-skill) and [`harness explain`](explain.md) teach
agents how to do that part.

## Two directories, two owners

| Directory | Belongs to | Holds | After `harness` is removed |
| --- | --- | --- | --- |
| `.harness/` | `harness`: its own versioned configuration | `config.yaml` (the profile), `policy.yaml`, `hooks.yaml`, `wrapper.yaml`, `manifest.json` | every file `harness` recorded there is deleted by both [removal modes](remove.md), and the directory once nothing else is in it; a file there that `harness` neither owns nor seeded, such as a `README.md` a person added, is listed and left for you to delete |
| `.agents/` | the cross-tool agent-content convention, shared with the `skills` CLI and the `AGENTS.md` ecosystem | `skills/` (including the tool-owned `skills/harness/`), `memory/`, `commands/`, `REVIEW.md` | kept, except the harness skill and, on undo, memory skeleton files nobody edited: other tools and agents read the rest |

Nothing in `.harness/` is state, and nothing in it is ignored: every file there is committed and reviewed. State lives
at [configurable paths](#state) outside it. The repository root holds the wrapper, `harnessw`, and the files that
carry [managed blocks](render.md#managed-blocks): `AGENTS.md`, `CLAUDE.md`, `README.md` and `.gitignore`.

## Layers

| Layer | Who writes it | Committed | In an adopted repository |
| --- | --- | --- | --- |
| Engine | nobody in the repository; the release the pin names, downloaded per machine by `harnessw` | no | the `harness` binary in `$HARNESS_HOME` (default `$XDG_CACHE_HOME/harness`, or `~/.cache/harness` when `XDG_CACHE_HOME` is unset) |
| Pin | `scaffold` and [`harness wrapper`](wrapper.md#harness-wrapper), after the running binary verifies the release's signed checksum file (`scaffold --offline` writes a [pending pin](wrapper.md#a-pending-pin)); owned, never edited by hand | yes | `.harness/wrapper.yaml` |
| Profile, policy, hooks | people and agents; seeded once by `scaffold`, which applies the [presets](presets.md#the-presets) of the detected stacks; `harness preset apply` adds a preset's entries on request | yes | `.harness/config.yaml`, `.harness/policy.yaml`, `.harness/hooks.yaml` |
| Templates | written once by `harness preset apply` or `scaffold` when the file is absent, then people and agents; never re-rendered or removed by `harness` | yes | a preset's whole files, such as `.ralph-tui/config.toml` from the `ralph-tui` [tool preset](presets.md) |
| Rendered | `harness render`; never edited by hand | yes | `harnessw`, owned keys of `.claude/settings.json` and `.codex/config.toml`, owned entries of `.codex/hooks.json`, `.codex/rules/harness.rules`, `.agents/skills/harness/SKILL.md`, skill links, `.harness/manifest.json` |
| Managed blocks | `harness render`, only between markers | yes | blocks inside `AGENTS.md`, `README.md`, `CLAUDE.md`, `.gitignore` |
| Knowledge | people and agents; never generated | yes | `AGENTS.md` prose, `.agents/memory/*`, domain skills, `.agents/commands/`, `.agents/REVIEW.md` |
| State | `harness` | never (gitignored) | the [state paths](#state) (default under `.work/`) |

The three repository files are the contract between a repository and the tool. Each has one audience:

| File | Answers | Reference |
| --- | --- | --- |
| `.harness/config.yaml` (the profile) | What is this repository: modules, commands, bootstrap, lanes, agents, state paths, standing context? | [profile](profile.md), [context](context.md) |
| `.harness/policy.yaml` (the policy) | What may agents run without asking, only after asking, or never? | [policy](policy.md) |
| `.harness/hooks.yaml` (the hooks file) | What runs when an agent starts a session, edits a file, waits for a person or ends a turn, and what runs at each lifecycle event? | [hooks](hooks.md), [lifecycle](lifecycle.md) |

What `harness` renders and runs in a repository comes from the repository's own files and the release
`.harness/wrapper.yaml` pins, and from nothing else: no inheritance, no includes, no sources outside the repository
([ADR 0013](../adrs/0013-organization-content.md)). Rendering reads nothing else and needs no network, so the same
commit renders the same bytes on every machine, and people and agents learn what a repository does by reading its files.
A [preset](presets.md) reaches a repository only as entries `harness preset apply` or `scaffold` writes into the profile
and the hooks file; once written, they are the repository's own. Other tools reach a repository the same way: a loop
runner or a tracker is wired by a command the profile declares, a lifecycle action, or a file a
[tool preset](presets.md) writes, because the binary integrates only git and the agent harnesses it has adapters for
([ADR 0022](../adrs/0022-tool-integration.md)). An organization's choices reach a repository the same way, as a reviewed
change to its files, usually written by an agent following a skill the organization publishes. For example, when acme
decides that no agent runs `terraform apply`, it publishes a skill saying so; in each of its Terraform repositories an
agent following that skill adds `[terraform, apply]` to `shared.deny` in `.harness/policy.yaml`, and the team reviews
that change like any other.

Rendered files are committed on purpose: a clone works before its pinned release is downloaded, and reviewers see
every permission change as a diff.

### Repository layout after adoption

```text
harnessw                    the wrapper: runs the pinned release (owned)
.harness/                   harness configuration: versioned, never state, never ignored
  config.yaml               profile
  policy.yaml               policy (repository entries only; the built-in base is not here)
  hooks.yaml                agent hooks, notification hooks and lifecycle actions
  wrapper.yaml              the pinned release, its source and checksums
  manifest.json             ownership record
.agents/                    agent content: meaningful without harness
  skills/harness/SKILL.md   the harness skill (owned)
  skills/<name>/            domain skills and shared skills the team installs (knowledge)
  memory/                   memory skeleton, then knowledge
  commands/                 agent commands (knowledge)
  REVIEW.md                 review guide (knowledge)
.claude/
  settings.json             owned keys: permissions, harness hook entries
  settings.local.json       never written; read only by scaffold --import local
  skills/harness -> ../../.agents/skills/harness
.codex/
  config.toml               owned keys only
  hooks.json                owned entries only
  rules/harness.rules       owned file
  skills/harness -> ../../.agents/skills/harness
AGENTS.md  README.md  CLAUDE.md  .gitignore   managed blocks, one or more per module
.work/                      state.dir: receipts, sessions, locks, lifecycle log, hook cache (gitignored)
  worktrees/                worktrees.dir: managed worktrees
```

A repository that applies a [tool preset](presets.md) also holds that preset's templates, such as
`.ralph-tui/config.toml` from the `ralph-tui` preset, as its own files.

## Ownership and the manifest

`harness` never guesses what it owns ([ADR 0007](../adrs/0007-ownership.md)). `.harness/manifest.json` records two
kinds of content:

| Kind | What it is | Entries |
| --- | --- | --- |
| Owned | content `harness` renders and rewrites; never edited by hand | every owned file with its content hash (`harnessw`, `.harness/wrapper.yaml`, `.codex/rules/harness.rules`, the harness skill and its links); every managed block, with the file it lives in and its content hash; every owned key or entry inside a native file that `harness` merge-renders, including rendered `raw` hooks |
| Seeded | files `harness` writes once; from then on the repository owns them and `render` never rewrites them | `.harness/config.yaml`, `.harness/policy.yaml` and `.harness/hooks.yaml`, written by `scaffold`, and the `.agents/memory/` skeleton, written when the memory module is enabled, by `scaffold` or by the first `render` after the module is turned on; each with the hash of the content it was created with |

It also records the `schema` the files were rendered with.

The manifest is committed and rewritten by `render`, `scaffold`, `wrapper` and `remove`. Do not edit it. The
difference between owned and seeded content decides what each [removal mode](remove.md#undo-and-eject-compared)
deletes.

How the commands use it:

| Command | Uses the manifest to |
| --- | --- |
| [`render`](render.md) | write exactly the owned set, and drop files and blocks that are no longer produced |
| [`render --check`](render.md#drift-check) | compare the owned set against what the sources produce now |
| [`remove`](remove.md) | undo: delete the owned content, the seeded files in `.harness/`, and seeded files elsewhere only while they still match the hash they were created with; `--eject`: keep convention blocks and rendered native content as hand-owned, delete the rest of what it lists; both: refuse an owned path whose hash changed unless `--force`, and list and leave any file the manifest does not record |
| [`doctor`](doctor.md) | report missing, modified or dangling owned paths |

### Marking

Ownership is also visible in the files themselves:

| Where | Marker |
| --- | --- |
| Owned whole files that allow comments | first line `# generated by harness (schema 1): edit .harness/<source> and run ./harnessw render`; scripts carry it right after their `#!` line, and `SKILL.md` as a YAML comment on the first line inside its front matter, right after the opening `---`. `.harness/wrapper.yaml`, which no source renders, carries `# generated by harness: change with ./harnessw wrapper --version <tag>` |
| Managed blocks in Markdown | `<!-- harness:<id> -->` … `<!-- /harness:<id> -->` |
| Managed blocks in hash-comment files: `.gitignore`, YAML, TOML, shell | `# harness:<id>` … `# /harness:<id>` |
| Owned keys in JSON files | none in the file; the manifest is the record |

Text outside markers, and keys `harness` does not own, are preserved byte for byte.

## Conventions outlive the tool

Every convention a module establishes is written down where people and other tools already look, in plain language
([ADR 0018](../adrs/0018-conventions.md)):

- in a managed block of `AGENTS.md`: where managed worktrees live and how their branches are named, the permissions
  agents work under, the memory index, and the [standing context](context.md) entries declared `static`;
- for every path it creates, in a managed block of `.gitignore`.

The worktrees module, for example, renders:

```markdown
<!-- harness:worktrees -->
Worktrees in this project are created in `.work/worktrees/<name>`, on branch `work/<name>`, from committed code.
<!-- /harness:worktrees -->
```

```gitignore
# harness:worktrees
/.work/worktrees/
# /harness:worktrees
```

Agents read these conventions from `AGENTS.md` like any other instruction, and git ignores what they say to ignore.

### Convention blocks and tool blocks

Managed blocks come in two classes:

| Class | States | Ids | After [`remove --eject`](remove.md#eject) |
| --- | --- | --- | --- |
| Convention block | a fact that stays true without `harness`; it never names a `harness` command | `AGENTS.md`: `worktrees`, `policy`, `memory`, `context`; `CLAUDE.md`: `import`; every `.gitignore` block | markers removed, text kept |
| Tool block | how to use `harness` in this repository; it may name `./harnessw` commands | `AGENTS.md`: `commands`, `layout`, `verify`, `lifecycle`; `README.md`: `commands` | removed |

So after an eject, what is left in `AGENTS.md` and `.gitignore` is true and usable without the tool, and nothing in
them points at a command that no longer exists.

Block ids are per module, so a module owns its blocks and removing the module removes them
([block ids](render.md#block-ids)). A block's text states its content as fact; nothing but its marker says `harness`
wrote it. Each module writes its own `.gitignore` entries even when another module's entry already covers the path:
the `state` block ignores `/.work/` and the `worktrees` block `/.work/worktrees/`. Removing one module therefore
never un-ignores a path another module still uses.

`AGENTS.md` is read at the start of every agent session, so the blocks `harness` writes there stay short: by default
at most 40 lines together and 8 lines each, the `context` block excepted ([profile](profile.md#blocks)). Detail lives
in [`./harnessw explain <topic>`](explain.md) and the harness skill.

## Modules

The profile's `modules:` map turns features on and off. Each module is opt-in and removable on its own.

| Module | Adds to the repository |
| --- | --- |
| `hooks` | harness hook entries in each agent's native files, from `.harness/hooks.yaml`: post-edit commands, validators, session context, notification hooks, `raw` hooks |
| `verify` | `harness verify`; allow entries for the command; the `verify` block in `AGENTS.md`; the receipt guard on `completed` with scope `task` |
| `memory` | the `.agents/memory/` skeleton (seeded when the module is enabled, never re-rendered) and the `memory` block in `AGENTS.md` |

Always present, whatever the modules: the `.harness/` files, `harnessw`, the rendered policy, the harness skill and its
links, the manifest, the [`harness worktree`](worktrees.md) commands with task binding, the [`harness worker`](ralph.md)
command, and these blocks: `commands`, `layout`, `worktrees`, `lifecycle` and `policy` in `AGENTS.md`; `commands` in
`README.md`; `import` in `CLAUDE.md`; `state` and `worktrees` in `.gitignore`.

[Standing context](context.md) is declared in the profile's `context` section. Entries delivered by hook need the
`hooks` module; `static` entries go into the `context` block of `AGENTS.md` whatever the modules.

## State

State is clone-local, gitignored and disposable. It never leaves the clone unless the repository chose otherwise. It
lives at paths the [profile](profile.md#state-paths) sets, outside `.harness/`; the defaults are shown here.

| Path (profile key) | Owner | Holds |
| --- | --- | --- |
| `.work/` (`state.dir`), in the primary checkout | `harness` | `lifecycle.jsonl` (every lifecycle run of the clone), locks shared by every worktree of the clone |
| `.work/` (`state.dir`), in each checkout | `harness` | `session.json` (managed worktree session: base, task, actor), `verified.json` (the [receipt](verify.md) of a passing `harness verify`), `hook-checks.json` (post-edit cache), `context/<session-id>.json` (which triggered context entries a session has received), `last-worker-output` and `last-attempt.patch` (the full output and the changes of the last failed `harness worker` attempt), the record that `bootstrap` ran for a worker |
| `.work/worktrees/` (`worktrees.dir`) | `harness` | each managed worktree, at `<dir>/<name>` |

Other tools keep their own state where they choose, such as Beads in `.beads/`; `harness` neither reads, writes nor
ignores it. The `layout` block of `AGENTS.md` names every state path, and the `.gitignore` blocks ignore each one the
repository does not track. No state path may point inside `.harness/` or `.agents/`: those directories hold committed
files only, so the profile refuses such a path.

## Adoption classes

Repositories start in one of three states. `harness` treats each one deterministically; only the leftovers go to an
agent.

| Class | Looks like | Command | What is left for an agent |
| --- | --- | --- | --- |
| Greenfield | no `.claude/settings.json` permissions or hooks, no workflow engine of its own | `harness scaffold` | review the seeded profile; write `AGENTS.md` prose |
| Brownfield, simple | permissions and hooks hand-written in `.claude/settings.json` or `.codex/` | `harness scaffold --adopt` | imported personal entries and allow/deny conflicts to review; `raw` hooks worth modelling |
| Brownfield, complex | its own workflow around agents: verification scripts, a loop runner's configuration such as a root `.ralph-tui/`, a tracker such as `.beads/` | `harness scaffold --adopt` | lane definitions from the verification scripts; pointing the loop runner's agent command at `./harnessw worker` and the tracker's updates at lifecycle actions, which a [tool preset](presets.md) can write |

`scaffold --adopt` imports every permission rule and hook losslessly, proves it with an equivalence check, and prints
a to-do of the leftovers; [`harness doctor`](doctor.md) keeps reporting open items until they are resolved. See
[scaffold](scaffold.md#adopt) for the import scopes and rules.

## Development lifecycle

Work moves through five [lifecycle events](lifecycle.md): `start`, `verified`, `completed`, `deliver` and `landed`. A
sixth event, `blocked`, fires out of band at any stage: `harness` fires it when a command run without a terminal stops
with exit 3 for a person to act, and whoever drives an unattended run fires it when the run stops unfinished. `harness`
fires each event at fixed trigger points in its own commands, and a person, a coordinating agent or a script around a
loop runner can fire one by hand with `harness lifecycle run <event>`; nothing in the binary knows when an unattended
run starts or ends. What happens at an event is the actions the repository declares in `.harness/hooks.yaml`; the binary
ships none. Everything that leaves the clone, such as a push, a pull request or a chat notification, is one of those
actions.

Every action has one kind ([action kinds](lifecycle.md#action-kinds)):

| Kind | What `harness` does |
| --- | --- |
| `run` | runs a shell command; values reach it only through `HARNESS_*` environment variables |
| `http` | makes an HTTP request itself, with the URL and header values read from environment variables, never from the file |
| `agent` | starts an agent headless, under the [worker contract](ralph.md#the-worker-contract); it cannot commit, push or fire events |

Two agent events run declared `run` and `http` actions too: `attention`, when an agent waits for a person, and
`turn_end`, when it finishes a turn ([hooks](hooks.md)). `harness` itself is never a client of an MCP server: work that
needs an MCP tool is an `agent` action, or the coordinating agent's job.

| Page | Covers |
| --- | --- |
| [Development lifecycle](lifecycle.md) | the stages from plan to landed, who acts at each, and the full event reference |
| [Safety guidelines](safety.md) | the threat model, the layers of defence, placing policy entries, unattended runs, secrets |
| [Pitfalls, gaps and traps](pitfalls.md) | what goes wrong, why, what `harness` does about it, and what you do |

## Optional and transparent

A repository adopts `harness` without forcing anyone else to.

| Who | What they experience |
| --- | --- |
| A teammate whose machine has not downloaded the pinned release | Committed rendered files give their agent the same permissions and instructions, convention blocks and `static` context included. The first `./harnessw` command downloads and verifies the release. Until then, every harness hook prints one line on stderr and exits 0, the session-start hook also adds one line to the agent's context, `harness <version> is not cached on this machine; run ./harnessw version once to fetch it.`, `raw` hooks keep running, and hook-delivered context, notification hooks and lifecycle events do nothing. |
| CI | Builds, tests, deploys and releases are unchanged; nothing requires the binary. `./harnessw render --check` and `./harnessw doctor` are optional steps; the wrapper downloads and verifies the pinned release ([wrapper](wrapper.md#ci)). |
| A person who stops using it | `harness remove` undoes the adoption from the manifest; `harness remove --eject` stops managing the repository and keeps the result: convention blocks, `.gitignore` entries, permissions and `raw` hooks ([remove](remove.md)). |
| A person who uses only part of it | Every module is opt-in; disabling one removes its files and blocks on the next render. |

## Built-in invariants

These hold in every repository, for what the binary renders and for every agent the binary starts, and cannot be turned
off by any repository file ([ADR 0011](../adrs/0011-safety-invariants.md)). An agent a person starts some other way,
such as from their own loop-runner configuration, is outside them: `harness` neither detects nor prevents it
([ADR 0022](../adrs/0022-tool-integration.md)).

1. Agents `harness worker` starts never run with permission bypass; bypass flags among its arguments, such as Claude
   Code's `--dangerously-skip-permissions` or Codex's `--yolo`, are refused with exit 1, naming the flag.
2. Force pushes to the detected default branch (`origin/HEAD`) are denied.
3. mise `:::` task chaining is denied, so an allowed `mise run lint` cannot carry a second task, as
   `mise run lint ::: deploy` would.
4. Verification proofs are replayed outside the tree the agent can write: `harness worker` reruns the lanes the agent's
   [receipt](verify.md) selected, with the profile read from the base commit.
5. Completion is withheld until the replayed proof passes: only then does `harness worker` exit 0, release a loop
   runner's completion signal and, in a worktree bound to a task, fire `completed`. Whoever fires `completed` with scope
   `task`, it is refused without a valid receipt when the verify module is enabled.
6. `harness worker`'s tamper check restores the tool-owned files from the base commit before it starts the agent and
   fails the attempt if the agent changed them, a file under `.harness/` or a file standing context names. The standing
   context a worker receives is read from the same base commit. A failed attempt returns the checkout to its base.
7. Hooks never decide permissions.
8. Lifecycle and notification actions never run inside an unattended agent: `harness worker` fires events in its own
   process after the agent exits, the binary refuses `harness lifecycle` under the unattended mark (`HARNESS_WORKER=1`),
   and the built-in worker deny covers it in every rendered spelling (`harness`, `./harnessw`, `harnessw`).
9. `deliver` always refuses on the default branch and with uncommitted changes and, when the verify module is enabled,
   without a valid receipt for the work's base.
10. The binary never writes knowledge (prose, memory, domain skills), contains no logic for the platform that hosts the
    team's work, such as GitHub, and never writes issues, projects, boards, comments, reviews or labels.
11. The binary never reconfigures state it did not create, such as `core.hooksPath`, `CLAUDE.md` content or a
    `.harness/` directory another tool owns, and reads no configuration of a tool it has no code for, such as `.beads/`
    or `.ralph-tui/`. A preset's template is written only when its file is absent.
12. An `agent` action runs its agent under the worker contract: auto permission mode, the worker denies, bypass flags
    refused, the tamper check. It cannot commit, push or fire lifecycle events.
13. Guards are enforced by the binary itself. Policy entries for `harness` commands are defence in depth: a command
    has more spellings (an absolute path, `sh harnessw`) than prefix rules can list.

`./harnessw explain invariants` prints this list with the reasoning for each item.
