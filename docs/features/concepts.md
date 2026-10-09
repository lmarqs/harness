# Concepts

This page explains how `harness` divides a repository into layers, how it knows which files it owns, how it adapts to
repositories in different starting states, and why a repository keeps working without it.

## One split: deterministic work and variable work

`harness` does the work that has one right answer: scaffolding, importing, rendering, checking drift, verifying,
launching runs, firing lifecycle events, removing what it wrote. Agents and people do the work that needs judgement:
writing `AGENTS.md` prose, deciding which commands belong in `allow`, reviewing an adoption's to-do, arranging a Ralph
run's commits for review, writing memory and skills. The embedded
[harness skill](skills.md#the-harness-skill) and [`harness explain`](explain.md) teach agents how to do that part.

## Layers

| Layer | Who writes it | Committed | In an adopted repository |
| --- | --- | --- | --- |
| Engine | nobody in the repository; installed per machine | no | the `harness` binary |
| Profile, policy, hooks | people and agents; seeded once by `scaffold` | yes | `.agents/harness.yaml`, `.agents/policy.yaml`, `.agents/hooks.yaml` |
| Rendered | `harness render`; never edited by hand | yes | owned keys of `.claude/settings.json` and `.codex/config.toml`, owned entries of `.codex/hooks.json`, `.codex/rules/harness.rules`, mise wrappers under `.mise/tasks/`, `.agents/skills/harness/SKILL.md`, skill links |
| Managed blocks | `harness render`, only between markers | yes | blocks inside `AGENTS.md`, `README.md`, `CLAUDE.md`, `.gitignore` |
| Knowledge | people and agents; never generated | yes | `AGENTS.md` prose, `.agents/memory/*`, domain skills, review guides |
| Presets | an organization or a person, in their own repository | in their own repository | sources named by `extends` |
| State | `harness`, `bd`, `ralph-tui` | never (gitignored) | `.work/`, `.beads/`, `<coordinator>/.ralph-tui/` |

The three repository files are the contract between a repository and the tool. Each has one audience:

| File | Answers | Reference |
| --- | --- | --- |
| `.agents/harness.yaml` (the profile) | What is this repository: modules, commands, lanes, tracker, agents, Ralph defaults, skill sources? | [profile](profile.md) |
| `.agents/policy.yaml` (the policy) | What may agents run without asking, only after asking, or never? | [policy](policy.md) |
| `.agents/hooks.yaml` (the hooks file) | What runs when an agent starts a session or edits a file, and what runs at each lifecycle event? | [hooks](hooks.md), [lifecycle](lifecycle.md) |

Rendered files are committed on purpose: a clone works without the binary, and reviewers see every permission change
as a diff.

### Repository layout after adoption

```text
.agents/
  harness.yaml              profile
  policy.yaml               policy (repository entries only; the built-in base is not here)
  hooks.yaml                agent hook declarations and lifecycle actions
  .harness/manifest.json    ownership record
  skills/harness/SKILL.md   the harness skill (owned)
  skills/<name>/            domain skills and skills installed by the skills CLI (knowledge)
  memory/                   memory skeleton, then knowledge
  ralph/prompt.hbs          optional override of the embedded Ralph prompt
.claude/
  settings.json             owned keys: permissions, harness hook entries
  settings.local.json       never written; read only by scaffold --import local
  skills/harness -> ../../.agents/skills/harness
.codex/
  config.toml               owned keys only
  hooks.json                owned entries only
  rules/harness.rules       owned file
  skills/harness -> ../../.agents/skills/harness
.mise/tasks/                one-line wrappers (front_door: mise)
AGENTS.md  README.md  CLAUDE.md  .gitignore   managed blocks
.work/  .beads/             state, gitignored
```

## Ownership and the manifest

`harness` never guesses what it owns. `.agents/.harness/manifest.json` records:

- every file `harness` owns, with its content hash;
- every managed block, with the file it lives in and its content hash;
- every owned key or entry inside a native file that `harness` merge-renders, including rendered `raw` hooks;
- the commit every git preset resolved to ([presets](presets.md#resolved-commits));
- the `schema` the files were rendered with.

The manifest is committed and rewritten by `render`, `scaffold` and `remove`. Do not edit it.

How the commands use it:

| Command | Uses the manifest to |
| --- | --- |
| [`render`](render.md) | write exactly the owned set, and drop files and blocks that are no longer produced |
| [`render --check`](render.md#drift-check) | compare the owned set against what the sources produce now |
| [`remove`](remove.md) | delete exactly what is listed, unrender blocks and owned keys, and refuse a path whose hash changed unless `--force` |
| [`doctor`](doctor.md) | report missing, modified or dangling owned paths |

### Marking

Ownership is also visible in the files themselves:

| Where | Marker |
| --- | --- |
| Owned whole files that allow comments | first line `# generated by harness (schema 1): edit .agents/<source> and run harness render`; scripts carry it right after their `#!` line, and `SKILL.md` as the first line inside its front matter |
| Managed blocks in Markdown | `<!-- harness:begin <id> -->` … `<!-- harness:end <id> -->` |
| Managed blocks in `.gitignore`, YAML, TOML | `# harness:begin <id>` … `# harness:end <id>` |
| Owned keys in JSON files | none in the file; the manifest is the record |

Text outside markers, and keys `harness` does not own, are preserved byte for byte.

## Modules

The profile's `modules:` map turns features on and off. Each module is opt-in and removable on its own.

| Module | Adds to the repository |
| --- | --- |
| `hooks` | harness hook entries in each agent's native files, from `.agents/hooks.yaml` |
| `verify` | `harness verify`; the `verify` wrapper; allow entries for the command |
| `tasks` | `harness task`; the `task` wrapper; Beads or PRD tracking |
| `ralph` | `harness ralph`; the `ralph` and `ralph:clean` wrappers; allow entries for the commands |
| `memory` | the `.agents/memory/` skeleton (seeded once) and the `memory-index` block |
| `skills` | `harness skills sync` and the pending-skills check in `doctor` |

Always present, whatever the modules: the three repository files, the rendered policy, the harness skill and its
links, the manifest, the `commands`, `files`, `policy-summary`, `import` and `ignore` blocks, and the
[`harness worktree`](worktrees.md) commands.

## State roots

State is clone-local, gitignored and disposable. It never leaves the clone unless the repository chose otherwise.

| Root | Owner | Holds |
| --- | --- | --- |
| `.work/` (primary checkout) | `harness` | `worktrees/`, `ralph-worktrees/`, `prds/` (a gitignored home for PRD files), `lifecycle.jsonl` (every lifecycle run of the clone), locks shared by every worktree of the clone |
| `.work/` (each checkout) | `harness` | `session.json` (managed worktree session), `verified.json` (receipt), `hook-checks.json` (post-edit cache) |
| `.beads/` | `bd` | the Beads database, found by `bd` by walking up from any nested worktree |
| `<coordinator>/.ralph-tui/` | `ralph-tui` | configuration and session state of a harness-launched run, only inside the coordinator worktree |

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

Work moves through five [lifecycle events](lifecycle.md): `start`, `verified`, `completed`, `deliver` and `landed`.
`harness` fires each one at a fixed trigger point in its own commands, and a person or a coordinating agent can fire
one by hand with `harness lifecycle run <event>`. What happens at an event is the repository's or its preset's
declared actions; the binary ships none. Everything that leaves the clone, such as a push or a pull request, is one
of those actions.

| Page | Covers |
| --- | --- |
| [Development lifecycle](lifecycle.md) | the stages from plan to landed, who acts at each, and the full event reference |
| [Safety guidelines](safety.md) | the threat model, the layers of defence, placing policy entries, unattended runs, secrets |
| [Pitfalls, gaps and traps](pitfalls.md) | what goes wrong, why, what `harness` does about it, and what you do |

## Optional and transparent

A repository adopts `harness` without forcing anyone else to.

| Who | What they experience |
| --- | --- |
| A teammate without the binary | Committed rendered files give their agent the same permissions and instructions. Hooks print `harness: not installed, hook skipped` on stderr and exit 0; `raw` hooks keep running. mise wrappers, being explicit commands, exit non-zero. Lifecycle events do not fire. |
| CI | Builds, tests, deploys and releases are unchanged; nothing requires the binary. `harness render --check` and `harness doctor` are optional steps. |
| A person who stops using it | `harness remove` deletes exactly what the manifest lists; the repository keeps working with what remains. |
| A person who uses only part of it | Every module is opt-in; disabling one removes its files on the next render. |

## Built-in invariants

These hold in every repository and cannot be turned off by any repository file or preset:

1. Ralph workers never run with permission bypass; bypass flags are refused.
2. Force pushes to the detected default branch (`origin/HEAD`) are denied.
3. mise `:::` task chaining is denied.
4. Verification proofs are replayed outside the tree the agent can write.
5. Completion is withheld until the replayed proof passes.
6. The Ralph worker's tamper check restores the tool-owned files from the session's base commit before every agent
   launch and fails the attempt if the agent changed them.
7. Hooks never decide permissions.
8. Lifecycle actions never run inside a Ralph worker: `harness lifecycle` is denied to workers, and lifecycle actions
   run in the launcher process.
9. `deliver` always refuses on the default branch and with uncommitted changes and, when the verify module is enabled,
   without a valid receipt for the work's base.
10. The binary never writes knowledge (prose, memory, domain skills), contains no logic for any hosting platform, and
    never writes issues, projects, boards, comments, reviews or labels.
11. The binary never reconfigures state it did not create: an existing `.beads/`, a root `.ralph-tui/`,
    `core.hooksPath`, existing mise tasks, `CLAUDE.md` content.

`harness explain invariants` prints this list with the reasoning for each item.
