# Compatibility and coexistence

`harness` joins repositories that already have tools, conventions and people. This page lists what it shares a
repository with and how it stays out of the way. The rule behind every row: **`harness` never reconfigures state it did
not create, never writes knowledge, and contains no logic for any hosting platform.**

## At a glance

| Already in the repository | What `harness` does |
| --- | --- |
| GitHub issues, pull requests, projects | The binary never writes them; delivery is the repository's declared `deliver` actions |
| A `.beads/` database | Uses it as found |
| A root `.ralph-tui/` and direct ralph-tui use | Never reads or writes it, unless `ralph.config: inherit` (read only) |
| mise tasks | Never overwrites them; collisions get `harness:<name>` wrappers |
| `.claude/settings.json`, `.codex/config.toml` | Owns only specific keys; preserves the rest byte for byte |
| `settings.local.json`, `*.local.toml` | Never writes them; reads them only for `scaffold --import local` |
| `CLAUDE.md` content | Adds one import line in a managed block; leaves the rest |
| `.claude/skills` as a real directory or a link | Leaves its shape; adds one link if needed |
| git hooks, `core.hooksPath`, Husky | Never changes them |
| `.gitignore` | Adds one marked block; leaves every other line |
| Other tools' sections in `AGENTS.md` | Leaves them |
| Other agents' files (`.cursor/`, …) | Leaves them |
| CI/CD | Requires nothing; a CI-trigger convention is a `deliver` action |

## GitHub

GitHub issues, pull requests and projects are the record of the team's work. The binary contains no logic for GitHub
or any other hosting platform, and never writes issues, projects, boards, comments, reviews or labels. Tasks in Beads
or PRD files are clone-local helpers; linking a task to an issue (for example with a Beads external reference) is an
agent's or a person's job.

Everything that leaves the clone (a push, a pull request, a CI trigger, a notification) is a
[lifecycle action](lifecycle.md) the repository or its [preset](presets.md#lifecycle-actions-in-a-preset) declares,
such as `gh pr create` under `deliver`. Those actions run with the credentials of whoever fires them, through the
platform's own CLI. A repository that declares none never has anything leave the clone through `harness`.

## Beads

| Situation | Behaviour |
| --- | --- |
| No `.beads/` and `--tracker beads` | `harness` creates a local database: prefix from the profile, no git hooks, no agent files, no remotes, automatic export off |
| An existing `.beads/` | used exactly as found: remotes, sync settings and hooks untouched |
| A database synchronised across machines | works; claims made by sessions and Ralph workers become visible on the other machines |
| `.beads/` tracked in git | works; the `ignore` block omits `/.beads/` |
| A tracker remote you want agents never to push to | add a `deny` entry to the policy or a preset; the built-in base has none |

## ralph-tui

| Situation | Behaviour |
| --- | --- |
| A root `.ralph-tui/config.toml` for direct runs | untouched; direct runs keep working with their own settings |
| Reusing that configuration in harness-launched runs | `ralph.config: inherit` reads it as the base, with the worker-contract keys forced |
| A global `~/.config/ralph-tui/config.toml` | never written; a bypass setting it injects is refused by the worker, with the key named |
| Direct-run branches (`ralph/session-*`) and sibling `../.ralph-worktrees/` | never cleaned: `harness ralph clean` acts only on sessions it recorded |
| Trackers other than Beads and PRD JSON (for example `beads-bv`) | not supported by `harness ralph`; direct use is unaffected |

Harness-launched runs keep all their ralph-tui state inside the coordinator worktree under `.work/`.

## mise

| Situation | Behaviour |
| --- | --- |
| No mise | use `front_door: binary`; no wrappers are written |
| A task named `verify`, `task`, `worktree:create`, `worktree:remove`, `ralph` or `ralph:clean` | kept; the wrapper is written as `harness:<name>` and the collision is a to-do item until the repository removes or renames its task |
| mise `:::` task chaining | denied for agents by the built-in base, since it would let an allowed task carry an unlisted one |

## Native agent files

| File or shape | Behaviour |
| --- | --- |
| `.claude/settings.json` with `env`, `model`, `statusLine`, `$schema`, plugins, foreign hooks | preserved byte for byte; `harness` owns `permissions.*`, its own hook entries and the `raw` entries it renders |
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

One block marked `# harness:begin ignore` … `# harness:end ignore` lists `/.work/` and, with the tasks module,
`/.beads/`. Paths the repository tracks are left out of the block. Every other line is untouched.

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

- no build, test, deploy or release step requires the binary;
- rendered files add no dependency and change no build output;
- hooks and lifecycle actions do not run in CI;
- `harness render --check` and `harness doctor` are optional steps you may add ([render](render.md#drift-check));
  presets are checked at their recorded commits, so an upstream preset change never fails CI by itself.

## Versions

- Rendered files carry the `schema`, not the binary version, so a teammate on another release renders the same bytes.
- An older binary refuses a newer schema with a message to update; a newer binary migrates an older schema with a
  shown diff ([installation](installation.md#version-skew)).
- `harness doctor` checks `claude`, `codex`, `bd` and `ralph-tui` against the release's supported versions and
  reports a version outside that range as a finding.
- A harness skill that differs only because the binary is newer is a `doctor` note, not drift.

## Not supported

- Windows.
- Projects that are not at the git repository root, and agent sessions started in a subdirectory.
- ralph-tui tracker plugins other than Beads and PRD JSON in harness-launched runs.
