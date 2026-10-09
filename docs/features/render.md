# render

`harness render` turns the three repository files, plus any [presets](presets.md), into the files each agent and
tool reads: native permission and hook configuration, the wrapper `harnessw`, task shims, the harness skill and its
links, and the managed blocks inside human-written files. `harness render --check` reports drift without writing, and
`harness render --refresh` moves unpinned presets to their newest upstream commit.

Examples on this page run commands as `./harnessw <command>`, through the repository's [wrapper](wrapper.md); with a
global install, `harness <command>` is equivalent.

## When to use it

| Situation | Command |
| --- | --- |
| You edited `.harness/config.yaml`, `.harness/policy.yaml` or `.harness/hooks.yaml` | `./harnessw render` |
| You turned a module on or off, moved a state path, or changed the `context` section | `./harnessw render` |
| You moved the pin with `./harnessw wrapper` | `./harnessw render`, so the new release refreshes what it renders |
| A preset named by `extends` changed upstream and you want the change | `./harnessw render --refresh` |
| You want CI or a reviewer to confirm rendered files match their sources | `./harnessw render --check` |
| You want to see what would change | `./harnessw render --dry-run` |

## How it works

1. Read the repository files, resolve `extends` at the commit recorded in the manifest (or, with `--refresh`, at the
   current upstream commit), and validate everything against the schema.
2. Compute every output: owned files, owned keys inside native files, managed blocks, links.
3. Compare with the manifest and the working tree.
4. Write what changed, delete what is no longer produced (for example the files and blocks of a module you turned
   off), and rewrite the manifest.

Rendering is deterministic: the same inputs produce the same bytes on linux and macOS, and a second run changes
nothing. Output depends on the repository files, the presets at their recorded commits and the `schema`, not on the
binary's version. See [presets](presets.md#resolved-commits) for how a preset's commit is recorded and moved.

### What render writes

| Output | Kind | Produced when | Contents |
| --- | --- | --- | --- |
| `harnessw` | owned file | always | the [wrapper](wrapper.md) script from the pinned release's template, mode `0755` |
| `.claude/settings.json` `permissions.allow`, `.ask`, `.deny` | owned keys | `agents.claude` declared | the [policy](policy.md#how-entries-render) for Claude |
| `.claude/settings.json` `hooks` entries that run the [hook command](#the-hook-command) | owned entries | `agents.claude` and `modules.hooks` | one entry per event the repository uses: `SessionStart` (session context, `always` context), `PostToolUse` (post-edit commands, validators, `when.paths` context), `UserPromptSubmit` (`when.prompt` [context](context.md)), `Notification` (`attention` hooks), `Stop` (`turn_end` hooks) |
| `.claude/settings.json` `raw` hook entries | owned entries | `agents.claude` and `modules.hooks` | `raw` hooks from `hooks.yaml`, rendered as native entries that run without the binary |
| `.codex/rules/harness.rules` | owned file | `agents.codex` declared | the policy for Codex |
| `.codex/hooks.json` harness and `raw` entries | owned entries | `agents.codex` and `modules.hooks` | as for Claude, for each event Codex emits |
| `.codex/config.toml` keys that enable project hooks, and `notify` | owned keys | `agents.codex` and `modules.hooks` | `notify` runs the hook command when Codex finishes a turn, for `turn_end` hooks |
| `.agents/skills/harness/SKILL.md` | owned file | always | the [harness skill](skills.md#the-harness-skill) |
| `.claude/skills/harness`, `.codex/skills/harness` | owned links | always, unless the skills directory is already a whole-directory link to `.agents/skills` | `../../.agents/skills/harness` |
| `.mise/tasks/worktree/create`, `.mise/tasks/worktree/remove` | owned files | `front_door: mise` | [task shims](#task-shims) |
| `.mise/tasks/verify` | owned file | `front_door: mise` and `modules.verify` | task shim |
| `.mise/tasks/task` | owned file | `front_door: mise` and `modules.tasks` | task shim |
| `.mise/tasks/ralph/_default`, `.mise/tasks/ralph/clean` | owned files | `front_door: mise` and `modules.ralph` | task shims for `mise run ralph` and `mise run ralph:clean` |
| Managed blocks | blocks | see [Block ids](#block-ids) | |
| `.harness/manifest.json` | owned file | always | the ownership record, including each preset's resolved commit |

An agent that emits no event for a declared hook or context trigger, such as Codex for `attention`, gets nothing
rendered for it, and [`harness doctor`](doctor.md) notes the gap.

`.harness/wrapper.yaml` is not rendered: `scaffold` writes it and [`harness wrapper`](wrapper.md#harness-wrapper)
rewrites it, each after verifying the release's signed checksum file. The seeded files (`config.yaml`, `policy.yaml`,
`hooks.yaml` and the memory skeleton) are never rendered: `scaffold` writes them once and the repository owns them.
Lifecycle actions are not rendered either: `harness` reads them from `hooks.yaml` and runs them itself
([lifecycle](lifecycle.md)).

### Merge-rendering native files

Agents read their configuration from fixed paths that people also edit. `harness` therefore owns only specific keys
and entries inside those files, never the whole file:

| File | `harness` owns | Everything else |
| --- | --- | --- |
| `.claude/settings.json` | `permissions.allow`, `permissions.ask`, `permissions.deny`; hook entries that run the hook command; hook entries rendered from `hooks.yaml` `raw` | preserved byte for byte: `env`, `model`, `statusLine`, `enabledPlugins`, `$schema`, other hooks, unknown keys |
| `.codex/config.toml` | the keys that enable the project's hooks, and `notify` | preserved byte for byte |
| `.codex/hooks.json` | harness hook entries and `raw` entries | preserved |
| `.codex/rules/harness.rules` | the whole file | other files in `.codex/rules/` are untouched and keep applying |
| `.claude/settings.local.json`, `*.local.toml` | nothing | never written; read only by `harness scaffold --import local` |

`render --check` compares the merged result, so a hand edit to an owned key is drift, and an edit anywhere else is
not.

### The hook command

Every harness hook entry, for every agent and event, runs the same command:

```sh
"$(git rev-parse --show-toplevel)/harnessw" --no-download hook
```

It resolves the wrapper from the root of the checkout the agent works in, primary or worktree, and never downloads:
when the pinned release is not cached, it prints one line naming `./harnessw` on stderr and exits 0, and the session
continues. The session-start entry also adds one line to the agent's context, so the agent can tell the person:

```text
harness v0.9.0 is not cached on this machine; run ./harnessw version once to fetch it.
```

Until then, hook-delivered context and notification hooks do nothing on that machine; `static` context entries in
`AGENTS.md` still apply. See [wrapper](wrapper.md#which-entry-points-download) and [hooks](hooks.md).

### Task shims

With `front_door: mise`, `render` writes a task shim for each command people and agents run often: a one-line mise
task that starts with the generated header and hands its arguments to the repository's wrapper:

```sh
#!/bin/sh
# generated by harness (schema 1): edit .harness/config.yaml and run ./harnessw render
exec ./harnessw verify "$@"
```

| Task shim | Runs |
| --- | --- |
| `mise run verify …` | `./harnessw verify …` |
| `mise run task …` | `./harnessw task …` |
| `mise run worktree:create …` | `./harnessw worktree create …` |
| `mise run worktree:remove …` | `./harnessw worktree remove …` |
| `mise run ralph …` | `./harnessw ralph …` |
| `mise run ralph:clean` | `./harnessw ralph clean` |

When the repository already defines a task with one of these names, `harness` never overwrites it. It writes the
shim under `harness:<name>` instead (for example `mise run harness:verify`, from `.mise/tasks/harness/verify`)
and lists the collision in the [adoption to-do](scaffold.md#the-to-do). Once the repository's own task is gone, the
next render writes the plain name.

A task shim is an explicit entry point: through `./harnessw` it downloads the pinned release when it is not
cached, and when it cannot, it exits non-zero. A verification or task command that silently succeeded would claim
work that did not happen.

## Managed blocks

A managed block is text `harness` owns inside a file people own. Only the text between the markers is generated.

```markdown
Text people wrote stays exactly as it is.

<!-- harness:commands -->
...generated...
<!-- /harness:commands -->

More text people wrote.
```

| File kind | Opening marker | Closing marker |
| --- | --- | --- |
| Markdown | `<!-- harness:<id> -->` | `<!-- /harness:<id> -->` |
| Hash-comment files: `.gitignore`, YAML, TOML, shell | `# harness:<id>` | `# /harness:<id>` |

```gitignore
# harness:state
/.work/
# /harness:state

# harness:worktrees
/.work/worktrees/
# /harness:worktrees
```

### Block ids

Block ids are per module and per convention: a module owns its blocks, and turning the module off or
[removing](remove.md) it removes them. Every block is one of two classes ([ADR 0018](../adrs/0018-conventions.md)):

- A **convention block** states a fact that stays true without `harness`, such as where worktrees live or which
  permissions agents work under. It never names a `harness` command. An [eject](remove.md#eject) removes its markers
  and keeps its text.
- A **tool block** says how to use `harness` in this repository and may name `./harnessw` commands. An eject removes
  it.

| File | Id | Class | Content | Module, produced when |
| --- | --- | --- | --- | --- |
| `AGENTS.md` | `commands` | tool | how to run things: `./harnessw` commands, or the task shims with `front_door: mise`; lint, build, verify with its lanes, worktree, task, ralph | core, always |
| `AGENTS.md` | `layout` | tool | what `.harness/`, `.agents/` and the state paths are, and who writes each | core, always |
| `AGENTS.md` | `worktrees` | convention | where managed worktrees are created, their branch names, and that they start from committed code | core, always |
| `AGENTS.md` | `verify` | tool | the checks a change needs: lint, build, the lanes and when each is required | `verify` |
| `AGENTS.md` | `tasks` | convention | that local tasks stay in the clone, the tracker in use, and where PRD files are kept | `tasks` |
| `AGENTS.md` | `ralph` | convention | where Ralph runs keep their worktrees and ralph-tui state, and what a worker may do | `ralph` |
| `AGENTS.md` | `lifecycle` | tool | the conditions for delivery and the actions this repository declares for each event | core, always |
| `AGENTS.md` | `policy` | convention | the effective permissions by decision, as the native files enforce them, with built-in and Claude Code-only entries marked; entries for `harness` commands are left out | core, always |
| `AGENTS.md` | `memory` | convention | the files under `.agents/memory/`, linked | `memory` |
| `AGENTS.md` | `context` | convention | the [standing context](context.md) entries declared `static`: their text, or a one-line pointer to the file | core, when the profile declares a `static` entry |
| `README.md` | `commands` | tool | the repository's commands, as in `AGENTS.md` | core, when `README.md` exists |
| `CLAUDE.md` (root, or `.claude/CLAUDE.md` when only that exists) | `import` | convention | one line: `@AGENTS.md` | core, unless the file already imports `@AGENTS.md` |
| `.gitignore` | `state` | convention | `/<state.dir>/` | core, always |
| `.gitignore` | `worktrees` | convention | `/<worktrees.dir>/` | core, always |
| `.gitignore` | `tasks` | convention | `/<tasks.prd_dir>/` | `tasks` |
| `.gitignore` | `ralph` | convention | `/.ralph-tui/` and `/<ralph.worktrees_dir>/` | `ralph` |
| `.gitignore` | `beads` | convention | `/.beads/` | `tasks`, only when `harness` created the Beads database and the path is not tracked |

The paths come from the profile ([state paths](profile.md#state-paths)); a [preset](presets.md) can supply block text
from its `blocks/<id>.md`, and that text follows the class of its block.

`.gitignore` blocks omit any path the repository tracks. Each block lists its own module's paths even when another
block already covers them: `state` ignores `/.work/` and `worktrees` ignores `/.work/worktrees/`. Removing one module
then never un-ignores a path another module still uses.

### The `AGENTS.md` budget

Agents read `AGENTS.md` at the start of every session, so every line `harness` writes there costs every session. The
blocks in `AGENTS.md` stay within a budget:

| Limit | Default | Set by |
| --- | --- | --- |
| all blocks in `AGENTS.md` together, the `context` block excepted | 40 lines | [`blocks.agents_md_budget`](profile.md#blocks) |
| any one block, the `context` block excepted | 8 lines | fixed |
| the `context` block and every other always-on context | 120 lines per role | [`context.budget`](context.md) |

Detail belongs in [`./harnessw explain <topic>`](explain.md) and the harness skill, which agents open when they need
it. A render that exceeds a budget still writes the blocks; [`harness doctor`](doctor.md) reports a note naming each
block and its line count, so a long preset block or a long lane list is visible in review.

### Rules for blocks

- A new block is appended at the end of its file. After that, you may move the whole block, markers included,
  anywhere in the file; `harness` updates it where it is.
- Never edit between the markers. `render` overwrites the edit and `render --check` reports it.
- A block's text states its content as fact; it never says it is managed by `harness`, beyond the marker. A
  convention block also never names a `harness` command, so it stays true after an eject.
- Text from other tools' sections (for example a section `bd init` added) is untouched; blocks are bound to their
  markers, not to headings.
- Deleting a block's markers by hand is drift: `render` puts the block back. Turn the module off, or run
  [`harness remove`](remove.md), to remove a block.

## Drift check

`./harnessw render --check` computes every output and compares it with the repository, writing nothing.

- Exit 0: everything matches.
- Exit 2: something differs. Each differing file, block or key is listed with the command that fixes it.
- Presets are compared at their recorded commits, so a change upstream never fails the check by itself.
- A harness skill that differs only because the pinned release is newer than the one that last rendered is not
  drift: `harness doctor` reports it as a note, and `./harnessw render` refreshes the skill. Moving the pin alone
  never fails the check.

Run it in CI as an optional step. The wrapper downloads and verifies the release the pull request pins:

```yaml
# .github/workflows/harness.yml
name: harness
on: [pull_request]
jobs:
  render-check:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - run: ./harnessw render --check
        env:
          HARNESS_NO_UPDATE_CHECK: "1"
```

If the repository uses an `extends` preset from a private source, the CI job needs read access to that source. To
cache the download between runs, see [wrapper](wrapper.md#ci).

## Schema migration

When the repository files use an older `schema` than the running release's, `render` migrates them, shows the diff
and asks before writing (`--yes` skips the question). This happens after the pin moves to a newer release. When they
use a newer one, which happens only when the pin is bypassed, `render` refuses and names the schema (exit 3).

## Reference

```text
harness render [--check] [--refresh] [--dry-run]
```

| Flag | Default | Meaning |
| --- | --- | --- |
| `--check` | off | Compare only; exit 2 on drift |
| `--refresh` | off | Re-resolve every preset declared without `@<ref>` to its current upstream commit and record it |
| `--dry-run` | off | Print what would be written or deleted; write nothing |

| Exit code | Meaning |
| --- | --- |
| 0 | Rendered, or `--check` found no drift |
| 1 | Error: invalid repository file, a preset commit that can be neither found nor fetched |
| 2 | `--check` found drift |
| 3 | Repository schema is newer than the running release, or a migration needs confirmation without a terminal |

Files read: `.harness/config.yaml`, `.harness/policy.yaml`, `.harness/hooks.yaml`, `.harness/wrapper.yaml`,
`.harness/manifest.json`, presets at their recorded commits (from `$HARNESS_HOME` or their source), the native files
listed above, `harnessw`, `AGENTS.md`, `README.md`, `CLAUDE.md`, `.gitignore`, `.mise/tasks/`, `.agents/memory/`, the
files and skills named by `static` context entries with `skills-lock.json`, the git index (tracked paths).
Files written: the outputs listed in [What render writes](#what-render-writes).

## Examples

Allow a new command for agents and render it:

```sh
$EDITOR .harness/policy.yaml        # add [npm, run, typecheck] to shared.allow
./harnessw render
git diff .claude/settings.json .codex/rules/harness.rules AGENTS.md
```

Turn Ralph off in a Terraform repository:

```yaml
# .harness/config.yaml
modules: { hooks: true, verify: true, tasks: true, ralph: false, memory: true, skills: true }
```

```sh
./harnessw render      # deletes .mise/tasks/ralph/*, the Ralph allow entries and the ralph blocks
```

## Guidelines

- Do commit rendered files together with the source edit that produced them, so the diff explains itself.
- Do review rendered permission diffs in pull requests: they are the effective change.
- Don't edit owned keys, owned files, `harnessw` or block contents by hand; edit the source and render.
- Don't add `.claude/settings.json` or `harnessw` to `.gitignore`: teammates rely on the committed files.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| `--check` fails in CI but passes locally | Uncommitted rendered files or manifest | Commit the rendered files and the manifest |
| A preset change upstream does not show up | `render` uses the recorded commit | `./harnessw render --refresh`, or move the preset's `@<ref>` |
| `--check` lists `AGENTS.md` block `commands` | Someone edited inside the markers | Move the text outside the markers, run `./harnessw render` |
| `--check` lists `harnessw` | The script was edited by hand | `./harnessw render` restores it |
| A key you added to `settings.json` disappeared | It was inside an owned key (`permissions.*`) | Put the rule in `policy.yaml` |
| The harness skill link is missing | `.claude/skills` is a whole-directory link to `.agents/skills`, so no per-entry link is needed | Nothing to fix |
| A task shim exits 1 naming `curl`, `wget` or a checksum | `./harnessw` could not download the pinned release | See [wrapper](wrapper.md#troubleshooting) |
| `doctor` notes that `AGENTS.md` exceeds its budget | A long lane list, a long preset block or many modules | Shorten the preset block, or raise `blocks.agents_md_budget` in the profile if every line earns its place |

## Coexistence

Merge-rendering exists so `harness` can share native files with people and other tools. See
[compatibility](compatibility.md) for settings files, skill-link shapes, other tools' `AGENTS.md` sections and
`.gitignore`.
