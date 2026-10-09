# Compatibility and coexistence

`harness` joins repositories that already have tools, conventions and people. This page lists what it shares a
repository with and how it stays out of the way. The rule behind every row: **`harness` never reconfigures state it did
not create, never writes knowledge, and contains no logic for any hosting platform.** What it sets up does not depend
on it either: every convention is written down in plain text, and an eject keeps it.

Commands on this page run as `./harnessw <command>`, through the repository's [wrapper](wrapper.md); with a global
install, `harness <command>` is equivalent.

## At a glance

| Already in the repository | What `harness` does |
| --- | --- |
| GitHub issues, pull requests, projects | The binary never writes them; delivery is the repository's declared `deliver` actions |
| A `.beads/` database | Uses it as found |
| A root `.ralph-tui/` and direct ralph-tui use | Never reads or writes it, unless `ralph.config: inherit` (read only) |
| mise tasks | Never overwrites them; collisions get `harness:<name>` task shims |
| A pin of `harness` in `mise.toml`, `.mise.toml` or `.tool-versions` | Never edits it; adoption copies it into `.harness/wrapper.yaml`, and `doctor` reports the second pin until it is removed |
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
or any other hosting platform, and never writes issues, projects, boards, comments, reviews or labels. Tasks in Beads
or PRD files are clone-local helpers; linking a task to an issue (for example with a Beads external reference) is an
agent's or a person's job.

Everything that leaves the clone (a push, a pull request, a CI trigger, a notification) is a
[lifecycle action](lifecycle.md) the repository declares in its own `.harness/hooks.yaml`, such as `gh pr create`
under `deliver`. Those actions run with the credentials of whoever fires them, through the
platform's own CLI. A repository that declares none never has anything leave the clone through `harness`.

## Beads

| Situation | Behaviour |
| --- | --- |
| No `.beads/` and `--tracker beads` | `harness` creates a local database: prefix from the profile, no git hooks, no agent files, no remotes, automatic export off |
| An existing `.beads/` | used exactly as found: remotes, sync settings and hooks untouched |
| A database synchronised across machines | works; what `harness` writes into it (claims, notes, verification comments) travels where the repository's synchronisation sends it, and `harness` never configures that synchronisation ([ADR 0012](../adrs/0012-write-boundary.md)) |
| `.beads/` tracked in git | works; no `beads` block is written to `.gitignore` |
| A tracker remote you want agents never to push to | add a `deny` entry to the repository's policy; the built-in base has none |

## ralph-tui

| Situation | Behaviour |
| --- | --- |
| A root `.ralph-tui/config.toml` for direct runs | untouched; direct runs keep working with their own settings |
| Reusing that configuration in harness-launched runs | `ralph.config: inherit` reads it as the base, with the worker-contract keys forced |
| A global `~/.config/ralph-tui/config.toml` | never written; read only to find a bypass setting it would inject into a harness-launched run, which the worker refuses, naming the file and key |
| Direct-run branches (`ralph/session-*`) and sibling `../.ralph-worktrees/` | never cleaned: `harness ralph clean` acts only on sessions it recorded |
| Trackers other than Beads and PRD JSON (for example `beads-bv`) | not supported by `harness ralph`; direct use is unaffected |

Harness-launched runs keep their ralph-tui configuration and session state in the coordinator worktree's own
`.ralph-tui/`. The coordinator is `<worktrees.dir>/ralph-<session-id>` (default `.work/worktrees/`), sequential task
worktrees sit beside it as `ralph-<session-id>-<task>`, and only ralph-tui's parallel worker checkouts go under
`ralph.worktrees_dir` (default `.work/ralph-worktrees/`) ([ralph](ralph.md#how-tasks-run)). The `ralph` blocks of
`AGENTS.md` and `.gitignore` write that down, so the layout stays readable after an eject.

## mise

| Situation | Behaviour |
| --- | --- |
| No mise | use `front_door: binary`; no task shims are written, and people and agents run `./harnessw` |
| Task shims | each one execs `./harnessw <command>`, so it runs the pinned release and downloads it when it is not cached |
| A task named `verify`, `task`, `worktree:create`, `worktree:remove`, `ralph` or `ralph:clean` | kept; the task shim is written as `harness:<name>` and the collision is a to-do item until the repository removes or renames its task |
| A pin of `harness` in `mise.toml`, `.mise.toml` or `.tool-versions` (`github:lmarqs/harness` or `harness`) | never edited. `.harness/wrapper.yaml` is the only pin: adoption copies the version into it and lists removing the task-runner pin as a to-do, and `doctor` reports a second pin as a finding, because two pins drift apart. A `harness` that mise puts on PATH defers to `wrapper.yaml` |
| mise `:::` task chaining | denied for agents by the built-in base, since it would let an allowed task carry an unlisted one |

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

Each convention that creates a path adds its own marked block, owned by its module: `state`, `worktrees`, `tasks`,
`ralph`, and `beads` when `harness` created the Beads database ([block ids](render.md#block-ids)). A module writes its
own entries even when another module's entry already covers them, such as `/.work/worktrees/` under `/.work/`, so
removing one module never un-ignores another module's paths. Paths the repository tracks are left out of the blocks.
Every other line is untouched.

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

Tools such as `bd init` may add their own sections to `AGENTS.md`. `harness` blocks are bound to their markers, never to
headings, so the sections coexist. The two may say similar things; trim the other tool's section if it reads
redundantly.

## Monorepos

`harness` operates at the git repository root only: one profile, one policy and one hooks file per repository. Nested
`.claude/` directories in sub-projects are left alone. Agent sessions started in a subdirectory are out of scope; see
[pitfalls](pitfalls.md).

## CI/CD

An adopted repository's CI/CD is unchanged:

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
  a global install run it. It is the only pin; a second pin in a task runner's file is a `doctor` finding.
- A pin changes only through `harness wrapper` or `scaffold`, which write its checksums after the running binary
  verifies the release's signed checksum file with the release keys it embeds; `scaffold --offline` leaves them
  [pending](wrapper.md#a-pending-pin), and nothing downloads against a pending pin. The wrapper then checks each
  download against the committed checksums. `doctor`, when online, re-verifies the pinned release's signed checksum
  file against `wrapper.yaml` ([wrapper](wrapper.md#security)).
- Rendered output depends on the sources and on the pinned release, and never records the release; rendered files
  carry the `schema`.
- `harness wrapper --version <tag>` renders the repository with the new release in the same change, migrating
  older-schema files, so the pin and every artefact the new release renders differently land in one diff
  ([wrapper](wrapper.md#upgrading)). A binary older than the repository's schema refuses, naming the pinned release to
  run; that happens only when the pin is bypassed, through `HARNESS_BINARY` or a global binary too old to defer to the
  pin ([installation](installation.md#version-skew)).
- `harness doctor` checks `claude`, `codex`, `bd` and `ralph-tui`, and `mise` when task shims are rendered and the
  `skills` CLI when the skills module is enabled, against the release's supported versions, and reports a version
  outside that range as a finding.
- The drift check is run by the pinned release and fails on any difference, the harness skill included
  ([ADR 0007](../adrs/0007-ownership.md)). A binary other than the pinned release, run through `HARNESS_BINARY`, gets a
  `doctor` note, because its drift results may differ.

## After removal

`harness` writes every convention it establishes into plain text that other tools and people already read, so
removing it does not strand the repository ([remove](remove.md)):

| After `remove --eject` | Still works |
| --- | --- |
| `AGENTS.md` keeps the text of its convention blocks without markers: where worktrees live, how tasks are tracked, where PRD files and Ralph state are, the effective permissions, where memory is, static standing context | agents and people follow it as ordinary instructions |
| `CLAUDE.md` keeps its `@AGENTS.md` line | Claude Code still reads `AGENTS.md` |
| `.gitignore` keeps every entry | state stays out of commits |
| Native settings keep their permissions and `raw` hooks as hand-owned content | agents keep the same rules |
| `.beads/` and the coordinator's `.ralph-tui/` stay where `bd` and ralph-tui look | `bd` and direct ralph-tui runs |

The tool blocks, which describe how to use `harness` (`commands`, `layout`, `verify` and `lifecycle` in `AGENTS.md`,
`commands` in `README.md`), are removed by the eject, together with the harness skill, its links and the task shims, so
nothing left behind names a command that no longer exists.

An undo, `harness remove` without `--eject`, instead returns the repository to the setup it had before adoption: every
block and its text is removed, and the settings adoption imported are written back. Both modes delete `harnessw`, the
files `.harness/` holds that `harness` owns or seeded, and then `.harness/` once nothing else is in it; a file the
manifest does not record, such as `.harness/ralph/prompt.hbs`, is listed and left. Both leave state where it is.

## Not supported

- Windows: the wrapper is a POSIX `sh` script, and releases are built for linux and macOS only.
- Projects that are not at the git repository root, and agent sessions started in a subdirectory.
- ralph-tui tracker plugins other than Beads and PRD JSON in harness-launched runs.
