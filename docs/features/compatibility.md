# Compatibility and coexistence

## Objective

**Problem.** A repository that adopts the tool already holds tools, settings and records that people depend on: an issue
tracker, a task database, a loop runner's configuration, git hooks, CI pipelines and hand-kept agent settings. A
developer weighing adoption, and a teammate who never chose it, need to know which of these change, because a tool that
rewrites them breaks work in ways that are hard to undo. They also need to know what still works if the repository stops
using the tool.

**Goal.** Everything people and other tools relied on before adoption works the same after it and after removal, and a
reader can check that for each tool before adopting.

## Overview

`harness` shares a repository with the tools, settings and records below, and stays out of their way. It never
reconfigures state it did not create and never writes knowledge. The binary contains code for git and for the agent
harnesses it adapts, Claude Code and Codex CLI, and for no other tool: no loop runner, task tracker, skills installer,
task runner or hosting platform ([ADR 0022](../adrs/0022-tool-integration.md)). Every other tool reaches a repository as
data in its own files: a command the profile declares, a lifecycle action, or an entry or file a [preset](presets.md)
writes. What `harness` sets up does not depend on it either: every convention is written down in plain text, and an
eject keeps it.

Commands on this page run as `./harnessw <command>`, through the repository's [wrapper](wrapper.md); with a global
install, `harness <command>` is equivalent.

## At a glance

| Already in the repository | What `harness` does |
| --- | --- |
| GitHub issues, pull requests, projects | The binary never writes them; delivery is the repository's declared `deliver` actions |
| A `.beads/` database ([Beads](#beads), a task tracker) | Never reads or writes it; the `beads` preset's actions run `bd` |
| A `.ralph-tui/` and ralph-tui runs ([ralph-tui](#ralph-tui)) | Never reads ralph-tui's configuration; the `ralph-tui` preset writes `.ralph-tui/config.toml` only when it is absent |
| mise and other task runners ([mise](#mise)) | Never reads or writes their files; a declared command may run one |
| Shared skills from the [`skills` CLI](#the-skills-cli) | Never runs the installer; leaves `.agents/skills/` and `skills-lock.json` to it |
| `.claude/settings.json`, `.codex/config.toml` | Owns only specific keys; preserves the rest byte for byte |
| `settings.local.json`, `*.local.toml` | Never writes them; reads them only for `scaffold --import local` |
| `CLAUDE.md` content | Adds one import line in a managed block; leaves the rest |
| `.claude/skills` as a real directory or a link | Leaves its shape; adds one link if needed |
| git hooks, `core.hooksPath`, Husky | Never changes them |
| `.gitignore` | Adds one marked block per convention; leaves every other line |
| Other tools' sections in `AGENTS.md` | Leaves them |
| Other agents' files (`.cursor/`, …) | Leaves them |
| A `.harness/` directory of another tool, such as Harness.io pipelines | Refuses to adopt the repository ([Harness.io](#harnessio)) |
| CI/CD | Requires nothing; `./harnessw render --check` is an optional step; a CI-trigger convention is a `deliver` action |

What stays after `harness` is removed is under [After removal](#after-removal).

## GitHub

GitHub issues, pull requests and projects are the record of the team's work. The binary contains no logic for GitHub
or any other hosting platform, and never writes issues, projects, boards, comments, reviews or labels. A task tracker
such as Beads is the team's choice; linking a task to an issue (for example with a Beads external reference) is an
agent's or a person's job.

Everything that leaves the clone (a push, a pull request, a CI trigger, a notification) is a
[lifecycle action](lifecycle.md) the repository declares in its own `.harness/hooks.yaml`, such as `gh pr create`
under `deliver`. Those actions run with the credentials of whoever fires them, through the
platform's own CLI. A repository that declares none never has anything leave the clone through `harness`.

## Beads

Beads is a task tracker that keeps tasks in a `.beads/` database in the clone and is used through its `bd` CLI.
`harness` has no code for Beads: the binary never runs `bd`, reads `.beads/` or creates a database. The
[`beads` preset](presets.md#beads) wires it, with lifecycle actions that mark a bead in progress on `start`, close it on
`completed` and comment on it on `blocked`, in a managed worktree bound to the bead ([tasks](tasks.md)). Creating and
configuring the database, and every other use of `bd`, is the team's.

| Situation | Behaviour |
| --- | --- |
| No `.beads/` | nothing is created; `bd init` is Beads' own step |
| An existing `.beads/` | never read, written or moved by the binary; remotes, sync settings and hooks are Beads' own |
| The `beads` preset's actions | run `bd` with the credentials of whoever fires the event; what they write travels where the repository's synchronisation sends it, and `harness` never configures that synchronisation ([ADR 0012](../adrs/0012-write-boundary.md)) |
| A Codex worker that writes the database | the `beads` preset adds `.beads` to `agents.codex.writable_roots` |
| `.beads/` tracked in git, or ignored | the team's choice; `harness` writes no `.gitignore` entry for it |
| A tracker remote you want agents never to push to | add a `deny` entry to the repository's policy; the built-in base has none |

## ralph-tui

[ralph-tui](https://github.com/subsy/ralph-tui) works through a list of tasks unattended, starting an agent for each.
`harness` has no code for ralph-tui and reads none of its configuration. The
[`ralph-tui` preset](presets.md#ralph-tui) writes `.ralph-tui/config.toml`, when it is absent, with
`./harnessw worker <agent>` as each agent's command, so every attempt runs under the worker contract and is verified
before ralph-tui sees it complete ([ralph](ralph.md)). From then on the file is the repository's. Starting ralph-tui,
choosing its tracker and firing the events of a run as a whole are the team's.

| Situation | Behaviour |
| --- | --- |
| A `.ralph-tui/config.toml` the repository already has | never read or written; applying the preset reports `kept .ralph-tui/config.toml: exists` |
| A global `~/.config/ralph-tui/config.toml` | never read or written. ralph-tui merges it into every run; a setting there that turns permission checks off reaches the worker as a bypass flag, which the worker refuses at the first task |
| ralph-tui's branches, session state and worktrees | ralph-tui's own; `harness` never cleans them |
| Any tracker ralph-tui supports | the team's choice; `harness` sees only the worker command |
| A run as a whole | nothing fires run-scope events by itself; whoever drives the run fires `./harnessw lifecycle run completed --scope run` or `blocked --scope run` |
| ralph-tui started with an agent command other than `harness worker` | outside `harness`: the worker contract holds only for agents the binary starts |

## mise

[mise](https://mise.jdx.dev) is a tool-version manager and task runner. `harness` has no code for mise or any other task
runner: it writes no tasks and reads no task runner's configuration. A repository uses its task runner through its own
commands: a declared command, lane or action may run `mise run lint`, and `harness` runs it like any other command.
People and agents run `harness` itself as `./harnessw <command>`.

| Situation | Behaviour |
| --- | --- |
| mise tasks, `mise.toml`, `.mise.toml`, `.tool-versions` | never read or written |
| A declared command, lane or action that runs `mise run …` | runs as written; `doctor` reports it when `mise` is not on PATH |
| mise `:::` task chaining | denied for agents by the built-in base, since it would let an allowed task carry an unlisted one |
| `harness` installed with `mise use -g` | an install option ([installation](installation.md#global-install-optional)); a `harness` that mise puts on PATH defers to `.harness/wrapper.yaml` |
| A pin of `harness` in the repository's own `mise.toml` or `.tool-versions` | never read: `.harness/wrapper.yaml` is the pin `harness` runs, and a second pin drifts from it, so keep `harness` out of the repository's task-runner files |

## The `skills` CLI

The `skills` CLI installs shared skills into `.agents/skills/` and records them in `skills-lock.json`. `harness` has no
code for it and never runs it. Installing shared skills is the team's own step, with this tool or another:

```sh
npx skills add acme/agent-skills
git add .agents/skills/ skills-lock.json && git commit -m "chore: add acme agent skills"
```

The profile's `context` names skills by name, and `doctor` reports a skill named there that does not exist under
`.agents/skills/` ([skills](skills.md)). The harness skill, `.agents/skills/harness/SKILL.md`, is the one skill
`harness` writes.

## Native agent files

| File or shape | Behaviour |
| --- | --- |
| `.claude/settings.json` with `env`, `model`, `statusLine`, `$schema`, plugins, foreign hooks | preserved byte for byte; `harness` owns `permissions.*`, its own hook entries and the `raw` entries it renders |
| `notify` in `.codex/config.toml` | owned when the repository declares [`turn_end`](hooks.md) actions and the key is absent or already the harness hook command. A `notify` program of the repository's own is left as it is, `turn_end` is not rendered for Codex, and `doctor` notes it |
| Hand-written permissions and hooks | imported losslessly by [`scaffold --adopt`](scaffold.md#adopt), with an equivalence check |
| Other `.codex/rules/*.rules` files | imported by `scaffold --adopt`; left in place and still applying |
| `.claude/settings.local.json`, `*.local.toml` | never written; read only by `scaffold --import local`, which marks what it imports for review |
| User-level settings (`~/.claude/settings.json`, `~/.codex/rules/`) | never written; read only by `scaffold --import user` |
| `defaultMode`, `additionalDirectories`, Codex `approval_policy` and `sandbox_mode`, MCP server definitions, managed settings | not policy; left in place, reported as notes by adoption and `doctor` |
| `.claude/skills` as a whole-directory link to `.agents/skills` | kept; the harness skill is reached through it |
| `.claude/skills` as a real directory, or with per-skill links from the `skills` CLI | kept; one per-entry link `harness` is added |
| Real `.claude/commands/`, `.claude/agents/` | kept where they are |
| `CLAUDE.md` with content, at the root or in `.claude/` | content untouched; an `import` block with `@AGENTS.md` is added |
| `AGENTS.md` only under `.agents/` | a to-do item: Codex reads only the root `AGENTS.md` |
| `.claude/worktrees/` | Claude Code's; never scanned or cleaned |

Merge-rendering rewrites only the owned keys and entries; every other key keeps its bytes, formatting included.

## git

- `harness` never changes `core.hooksPath` and never installs git hooks.
- Its own `git worktree add` runs with `core.hooksPath=/dev/null` for that one command, so a hook from the primary
  checkout does not run during worktree creation.
- The force-push deny names the default branch `origin/HEAD` points to: `main`, `master` or any other name.

## `.gitignore`

Each convention that creates a path adds its own marked block: `state` and `worktrees`
([block ids](render.md#block-ids)). A block writes its own entries even when another block's entry already covers them,
such as `/.work/worktrees/` under `/.work/`, so removing one never un-ignores another's paths. Paths the repository
tracks are left out of the blocks. Every other line is untouched.

```gitignore
# harness:state
/.work/
# /harness:state
```

## Harness.io

The name `harness` and the `.harness/` directory are also used by Harness.io, a CI/CD platform: its CLI is called
`harness`, and it keeps pipeline definitions in a `.harness/` directory of the repository. The two serve different
audiences, and a repository uses one of them.

| Situation | Behaviour |
| --- | --- |
| A `.harness/` directory with no `harness` profile (`config.yaml`) and no manifest | `scaffold` refuses with exit 3 and names the directory; nothing is written |
| Harness.io's `harness` CLI earlier on `PATH` | `./harnessw` is unaffected: it always runs the pinned binary from its own cache. Only a global `harness` command can be shadowed; `doctor` reports when `harness` on `PATH` is not this tool |

## Other tools in `AGENTS.md`

Other tools may add their own sections to `AGENTS.md`, such as Beads through `bd init`. `harness` blocks are bound to
their markers, never to headings, so the sections coexist. The two may say similar things; trim the other tool's section
if it reads redundantly.

## Monorepos

`harness` operates at the git repository root only: one profile, one policy and one hooks file per repository. Nested
`.claude/` directories in sub-projects are left alone. Agent sessions started in a subdirectory are out of scope; see
[pitfalls](pitfalls.md).

## CI/CD

A repository's CI/CD is unchanged by adopting `harness`:

- no build, test, deploy or release step requires the binary or the wrapper;
- rendered files add no dependency and change no build output;
- hooks and lifecycle actions do not run in CI;
- `./harnessw render --check` and `./harnessw doctor` are optional steps you may add ([render](render.md#drift-check));
  the wrapper downloads the pinned release over HTTPS and verifies its checksum, so CI runs exactly the release the
  pull request pins ([wrapper](wrapper.md#ci));
- the drift check reads only the repository's files and the pinned release, so its result changes only with a
  commit to the repository ([ADR 0013](../adrs/0013-organization-content.md)).

## Versions

- Within one repository there is no version skew: `.harness/wrapper.yaml` pins one release, and both `./harnessw` and
  a global install run it.
- A pin changes only through `harness wrapper` or `scaffold`, which write its checksums after the running binary
  verifies the release's signed checksum file with the release keys it embeds. `scaffold --offline` leaves them
  [pending](wrapper.md#a-pending-pin), and nothing downloads against a pending pin. The wrapper then checks each
  download against the committed checksums. `doctor`, when online, re-verifies the pinned release's signed checksum file
  against `wrapper.yaml` ([wrapper](wrapper.md#security)).
- Rendered output depends on the sources and on the pinned release, and never records the release; rendered files
  carry the `schema`.
- `harness wrapper --version <tag>` renders the repository with the new release in the same change, migrating
  older-schema files, so the pin and every artefact the new release renders differently land in one diff
  ([wrapper](wrapper.md#upgrading)). A binary older than the repository's schema refuses, naming the pinned release to
  run. That happens only when the pin is bypassed, through `HARNESS_BINARY` or a global binary too old to defer to the
  pin ([installation](installation.md#version-skew)).
- The documentation describes Claude Code 2.1.x and Codex CLI 0.16x. The tool presets and the examples that run other
  tools describe Beads (`bd`) 1.3.1, ralph-tui 0.12, mise 2026.6 and the `skills` CLI 1.7.
- `harness doctor` checks the versions of git, `claude` and `codex` against the ranges the release supports, which
  `harness doctor --json` lists, and reports a version outside its range as a finding. It checks no other tool's
  version; it reports a program that a declared command, lane, validator or action names and that is not on PATH.
- The pinned release runs the drift check, which fails on any difference, the harness skill included
  ([ADR 0007](../adrs/0007-ownership.md)). A binary other than the pinned release, run through `HARNESS_BINARY`, gets a
  `doctor` note, because its drift results may differ.

## After removal

`harness` writes every convention it establishes into plain text that other tools and people already read, so
removing it does not strand the repository ([remove](remove.md)):

| After `remove --eject` | Still works |
| --- | --- |
| `AGENTS.md` keeps the text of its convention blocks without markers: where worktrees live, the effective permissions, where memory is, static standing context | agents and people follow it as ordinary instructions |
| `CLAUDE.md` keeps its `@AGENTS.md` line | Claude Code still reads `AGENTS.md` |
| `.gitignore` keeps every entry | state stays out of commits |
| Native settings keep their permissions and `raw` hooks as hand-owned content | agents keep the same rules |
| `.beads/`, which `harness` never touched, and the files a preset wrote, such as `.ralph-tui/config.toml`, stay: they are the repository's own | `bd`; ralph-tui once its agent command, `./harnessw worker`, is replaced |

The eject removes the tool blocks, which describe how to use `harness` (`commands`, `layout`, `verify` and `lifecycle`
in `AGENTS.md`, `commands` in `README.md`), together with the harness skill and its links, so no file `harness` rendered
names a `harness` command afterwards. A file a preset wrote is the repository's own and keeps what it says, such as the
agent command in `.ralph-tui/config.toml`.

An undo, `harness remove` without `--eject`, instead returns the repository to the setup it had before adoption: every
block and its text is removed, and the settings adoption imported are written back. Both modes delete `harnessw`, the
files `.harness/` holds that `harness` owns or seeded, and then `.harness/` once nothing else is in it. A file the
manifest does not record, such as a script the team keeps in `.harness/`, is listed and left. Both leave state where it
is.

## Not supported

- Windows: the wrapper is a POSIX `sh` script, and releases are built for linux and macOS only.
- Projects that are not at the git repository root, and agent sessions started in a subdirectory.
