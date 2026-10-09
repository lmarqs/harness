# Concepts

This page explains how `harness` divides a repository into layers and directories, how it knows which files it owns,
how the conventions it sets up are written down, how it adapts to repositories in different starting states, and why
a repository keeps working without it.

Examples on this page run commands as `./harnessw <command>`, through the repository's [wrapper](wrapper.md); with a
global install, `harness <command>` is equivalent.

## One split: deterministic work and variable work

`harness` does the work that has one right answer: scaffolding, importing, rendering, checking drift, verifying,
launching runs, firing lifecycle events, removing what it wrote. Agents and people do the work that needs judgement:
writing `AGENTS.md` prose, deciding which commands belong in `allow`, reviewing an adoption's to-do, arranging a Ralph
run's commits for review, writing memory and skills. The embedded
[harness skill](skills.md#the-harness-skill) and [`harness explain`](explain.md) teach agents how to do that part.

## Two directories, two owners

| Directory | Belongs to | Holds | After `harness` is removed |
| --- | --- | --- | --- |
| `.harness/` | `harness`: its own versioned configuration | `config.yaml` (the profile), `policy.yaml`, `hooks.yaml`, `wrapper.yaml`, `manifest.json`, an optional `ralph/prompt.hbs` | every file `harness` recorded there is deleted by both [removal modes](remove.md), and the directory once nothing else is in it; a file there that `harness` neither owns nor seeded, such as `ralph/prompt.hbs`, is listed and left for you to delete |
| `.agents/` | the cross-tool agent-content convention, shared with the `skills` CLI and the `AGENTS.md` ecosystem | `skills/` (including the tool-owned `skills/harness/`), `memory/`, `commands/`, `REVIEW.md` | kept, except the harness skill and, on undo, memory skeleton files nobody edited: other tools and agents read the rest |

Nothing in `.harness/` is state, and nothing in it is ignored: every file there is committed and reviewed. State lives
at [configurable paths](#state) outside it. The repository root holds the wrapper, `harnessw`, and the files that
carry [managed blocks](render.md#managed-blocks): `AGENTS.md`, `CLAUDE.md`, `README.md` and `.gitignore`.

A [preset](presets.md) directory mirrors `.harness/`: it holds `config.yaml`, `policy.yaml`, `hooks.yaml`, and
`blocks/<id>.md` for block text.

## Layers

| Layer | Who writes it | Committed | In an adopted repository |
| --- | --- | --- | --- |
| Engine | nobody in the repository; the release the pin names, downloaded per machine by `harnessw` | no | the `harness` binary in `$HARNESS_HOME` (default `$XDG_CACHE_HOME/harness`, or `~/.cache/harness` when `XDG_CACHE_HOME` is unset) |
| Pin | `scaffold` and [`harness wrapper`](wrapper.md#harness-wrapper), after the running binary verifies the release's signed checksum file (`scaffold --offline` writes a [pending pin](wrapper.md#a-pending-pin)); owned, never edited by hand | yes | `.harness/wrapper.yaml` |
| Profile, policy, hooks | people and agents; seeded once by `scaffold` | yes | `.harness/config.yaml`, `.harness/policy.yaml`, `.harness/hooks.yaml` |
| Rendered | `harness render`; never edited by hand | yes | `harnessw`, owned keys of `.claude/settings.json` and `.codex/config.toml`, owned entries of `.codex/hooks.json`, `.codex/rules/harness.rules`, task shims under `.mise/tasks/`, `.agents/skills/harness/SKILL.md`, skill links, `.harness/manifest.json` |
| Managed blocks | `harness render`, only between markers | yes | blocks inside `AGENTS.md`, `README.md`, `CLAUDE.md`, `.gitignore` |
| Knowledge | people and agents; never generated | yes | `AGENTS.md` prose, `.agents/memory/*`, domain skills, `.agents/commands/`, `.agents/REVIEW.md` |
| Presets | an organization or a person, in their own repository | in their own repository | sources named by `extends` |
| State | `harness`, `bd`, `ralph-tui` | never (gitignored) | the [state paths](#state) (default under `.work/`), `.beads/`, `<coordinator>/.ralph-tui/` |

The three repository files are the contract between a repository and the tool. Each has one audience:

| File | Answers | Reference |
| --- | --- | --- |
| `.harness/config.yaml` (the profile) | What is this repository: modules, commands, lanes, tracker, agents, state paths, Ralph defaults, skill sources, standing context? | [profile](profile.md), [context](context.md) |
| `.harness/policy.yaml` (the policy) | What may agents run without asking, only after asking, or never? | [policy](policy.md) |
| `.harness/hooks.yaml` (the hooks file) | What runs when an agent starts a session, edits a file, waits for a person or ends a turn, and what runs at each lifecycle event? | [hooks](hooks.md), [lifecycle](lifecycle.md) |

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
  ralph/prompt.hbs          optional override of the embedded Ralph prompt
.agents/                    agent content: meaningful without harness
  skills/harness/SKILL.md   the harness skill (owned)
  skills/<name>/            domain skills and skills installed by the skills CLI (knowledge)
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
.mise/tasks/                task shims: one-line tasks that exec ./harnessw (front_door: mise)
AGENTS.md  README.md  CLAUDE.md  .gitignore   managed blocks, one or more per module
.work/                      state.dir: receipts, sessions, locks, lifecycle log, hook cache (gitignored)
  worktrees/                worktrees.dir: managed worktrees
  prds/                     tasks.prd_dir: PRD files
  ralph-worktrees/          ralph.worktrees_dir: ralph-tui's parallel worker checkouts
.beads/                     the Beads database (bd's own location)
```

## Ownership and the manifest

`harness` never guesses what it owns ([ADR 0007](../adrs/0007-ownership.md)). `.harness/manifest.json` records two
kinds of content:

| Kind | What it is | Entries |
| --- | --- | --- |
| Owned | content `harness` renders and rewrites; never edited by hand | every owned file with its content hash (`harnessw`, `.harness/wrapper.yaml`, `.codex/rules/harness.rules`, task shims, the harness skill and its links); every managed block, with the file it lives in and its content hash; every owned key or entry inside a native file that `harness` merge-renders, including rendered `raw` hooks |
| Seeded | files `harness` writes once; from then on the repository owns them and `render` never rewrites them | `.harness/config.yaml`, `.harness/policy.yaml` and `.harness/hooks.yaml`, written by `scaffold`, and the `.agents/memory/` skeleton, written when the memory module is enabled, by `scaffold` or by the first `render` after the module is turned on; each with the hash of the content it was created with |

It also records the commit every git preset resolved to ([presets](presets.md#resolved-commits)) and the `schema` the
files were rendered with.

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

- in a managed block of `AGENTS.md`: where managed worktrees live and how their branches are named, how local tasks
  are tracked and where PRD files are kept, where Ralph runs keep their state and what a worker may do, the permissions
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

Agents read these conventions from `AGENTS.md` like any other instruction, `bd` and `ralph-tui` find their state where
the conventions put it, and git ignores what they say to ignore.

### Convention blocks and tool blocks

Managed blocks come in two classes:

| Class | States | Ids | After [`remove --eject`](remove.md#eject) |
| --- | --- | --- | --- |
| Convention block | a fact that stays true without `harness`; it never names a `harness` command | `AGENTS.md`: `worktrees`, `tasks`, `ralph`, `policy`, `memory`, `context`; `CLAUDE.md`: `import`; every `.gitignore` block | markers removed, text kept |
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
| `verify` | `harness verify`; the `verify` task shim; allow entries for the command; the `verify` block in `AGENTS.md` |
| `tasks` | `harness task`; the `task` task shim; Beads or PRD tracking; the `tasks` blocks in `AGENTS.md` and `.gitignore`, and the `beads` block when `harness` created the Beads database |
| `ralph` | `harness ralph`; the `ralph` and `ralph:clean` task shims; allow entries for the commands; the `ralph` blocks in `AGENTS.md` and `.gitignore` |
| `memory` | the `.agents/memory/` skeleton (seeded when the module is enabled, never re-rendered) and the `memory` block in `AGENTS.md` |
| `skills` | `harness skills sync` and the pending-skills check in `doctor` |

Always present, whatever the modules: the `.harness/` files, `harnessw`, the rendered policy, the harness skill and
its links, the manifest, the [`harness worktree`](worktrees.md) commands, and these blocks: `commands`, `layout`,
`worktrees`, `lifecycle` and `policy` in `AGENTS.md`; `commands` in `README.md`; `import` in `CLAUDE.md`; `state` and
`worktrees` in `.gitignore`.

[Standing context](context.md) is declared in the profile's `context` section. Entries delivered by hook need the
`hooks` module; `static` entries go into the `context` block of `AGENTS.md` whatever the modules.

## State

State is clone-local, gitignored and disposable. It never leaves the clone unless the repository chose otherwise. It
lives at paths the [profile](profile.md#state-paths) sets, outside `.harness/`; the defaults are shown here.

| Path (profile key) | Owner | Holds |
| --- | --- | --- |
| `.work/` (`state.dir`), in the primary checkout | `harness` | `lifecycle.jsonl` (every lifecycle run of the clone), locks shared by every worktree of the clone |
| `.work/` (`state.dir`), in each checkout | `harness` | `session.json` (managed worktree session), `verified.json` (receipt), `hook-checks.json` (post-edit cache), `context/<session-id>.json` (which triggered context entries a session has received) |
| `.work/worktrees/` (`worktrees.dir`) | `harness` | each managed worktree, at `<dir>/<name>` |
| `.work/prds/` (`tasks.prd_dir`) | `harness` | a gitignored home for PRD files |
| `.work/ralph-worktrees/` (`ralph.worktrees_dir`) | `harness` | ralph-tui's worker checkouts for the parallel tasks of harness-launched Ralph runs. The coordinator and sequential task worktrees live under `worktrees.dir`, as `ralph-<session-id>` and `ralph-<session-id>-<task>` ([ralph](ralph.md)) |
| `.beads/` | `bd` | the Beads database, found by `bd` by walking up from any nested worktree |
| `<coordinator>/.ralph-tui/` | `ralph-tui` | configuration and session state of a harness-launched run, only inside the coordinator worktree |

`.beads/` and `.ralph-tui/` stay where `bd` and `ralph-tui` look for them. The `layout` block of `AGENTS.md` names
every path, and the `.gitignore` blocks ignore each one the repository does not track. No state path may point inside
`.harness/` or `.agents/`: those directories hold committed files only, so the profile refuses such a path.

## Adoption classes

Repositories start in one of three states. `harness` treats each one deterministically; only the leftovers go to an
agent.

| Class | Looks like | Command | What is left for an agent |
| --- | --- | --- | --- |
| Greenfield | no `.claude/settings.json` permissions or hooks, no workflow engine of its own | `harness scaffold` | review the seeded profile; write `AGENTS.md` prose |
| Brownfield, simple | permissions and hooks hand-written in `.claude/settings.json` or `.codex/` | `harness scaffold --adopt` | imported personal entries and allow/deny conflicts to review; `raw` hooks worth modelling |
| Brownfield, complex | its own workflow engine: mise tasks such as `verify` or `ralph:run`, a root `.ralph-tui/`, an existing `.beads/` | `harness scaffold --adopt` | task-name collisions, lane definitions, choosing `ralph.config: inherit` or retiring the old config |

`scaffold --adopt` imports every permission rule and hook losslessly, proves it with an equivalence check, and prints
a to-do of the leftovers; [`harness doctor`](doctor.md) keeps reporting open items until they are resolved. See
[scaffold](scaffold.md#adopt) for the import scopes and rules.

## Development lifecycle

Work moves through five [lifecycle events](lifecycle.md): `start`, `verified`, `completed`, `deliver` and `landed`. A
sixth event, `blocked`, fires out of band at any stage: when a Ralph run stops unfinished, when the launcher skips a
task after its retries, and when a command run without a terminal stops with exit 3 for a person to act. `harness`
fires each event at fixed trigger points in its own commands, and a person or a coordinating agent can fire one by
hand with `harness lifecycle run <event>`. What happens at an event is the repository's or its preset's declared
actions; the binary ships none. Everything that leaves the clone, such as a push, a pull request or a chat
notification, is one of those actions.

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
| A teammate whose machine has not downloaded the pinned release | Committed rendered files give their agent the same permissions and instructions, convention blocks and `static` context included. The first `./harnessw` command or task shim downloads and verifies the release. Until then, every harness hook prints one line on stderr and exits 0, the session-start hook also adds one line to the agent's context, `harness <version> is not cached on this machine; run ./harnessw version once to fetch it.`, `raw` hooks keep running, and hook-delivered context, notification hooks and lifecycle events do nothing. |
| CI | Builds, tests, deploys and releases are unchanged; nothing requires the binary. `./harnessw render --check` and `./harnessw doctor` are optional steps; the wrapper downloads and verifies the pinned release ([wrapper](wrapper.md#ci)). |
| A person who stops using it | `harness remove` undoes the adoption from the manifest; `harness remove --eject` stops managing the repository and keeps the result: convention blocks, `.gitignore` entries, permissions and `raw` hooks ([remove](remove.md)). |
| A person who uses only part of it | Every module is opt-in; disabling one removes its files and blocks on the next render. |

## Built-in invariants

These hold in every repository and cannot be turned off by any repository file or preset
([ADR 0011](../adrs/0011-safety-invariants.md)):

1. Ralph workers never run with permission bypass; bypass flags are refused.
2. Force pushes to the detected default branch (`origin/HEAD`) are denied.
3. mise `:::` task chaining is denied.
4. Verification proofs are replayed outside the tree the agent can write.
5. Completion is withheld until the replayed proof passes.
6. The Ralph worker's tamper check restores the tool-owned files from the session's base commit before every agent
   launch and fails the attempt if the agent changed them. The standing context a worker receives is read from the
   same base commit.
7. Hooks never decide permissions.
8. Lifecycle and notification actions never run inside a Ralph worker: lifecycle actions run in the launcher
   process, the binary refuses `harness lifecycle` inside a worker, and the built-in worker deny covers it in every
   rendered spelling (`harness`, `./harnessw`, `harnessw`, task shims).
9. `deliver` always refuses on the default branch and with uncommitted changes and, when the verify module is enabled,
   without a valid receipt for the work's base.
10. The binary never writes knowledge (prose, memory, domain skills), contains no logic for the platform that hosts
    the team's work, and never writes issues, projects, boards, comments, reviews or labels.
11. The binary never reconfigures state it did not create: an existing `.beads/`, a root `.ralph-tui/`,
    `core.hooksPath`, existing mise tasks, a task runner's tool versions, `CLAUDE.md` content, a `.harness/`
    directory another tool owns.
12. An `agent` action runs its agent under the worker contract: auto permission mode, the worker denies, bypass flags
    refused, the tamper check. It cannot commit, push or fire lifecycle events.
13. Guards are enforced by the binary itself. Policy entries for `harness` commands are defence in depth: a command
    has more spellings (an absolute path, `sh harnessw`) than prefix rules can list.

`./harnessw explain invariants` prints this list with the reasoning for each item.
