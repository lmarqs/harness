# render

## Objective

**Problem.** Each agent and tool in a repository reads its permissions, hooks and instructions from its own files, in
its own format, and some of those files also hold settings people keep there themselves. Kept by hand, they drift from
each other and from what the team decided: agents get different rules in the same repository, a quick edit silently
undoes a decision, and a reviewer cannot tell whether what is committed still matches what was agreed.

**Goal.** Every file an agent or tool reads follows from the repository's declared choices, byte for byte the same on
every machine, the settings people keep in those files stay as they wrote them, and any hand change to the generated
part is detected.

## Overview

`harness render` turns the three repository files in `.harness/` (the [profile](profile.md), the [policy](policy.md) and
the [hooks file](hooks.md)) into the files each agent and tool reads: native permission and hook configuration, the
wrapper `harnessw`, the harness skill and its links, and the managed blocks inside
human-written files. `harness render --check` reports drift without writing.

Every output belongs either to the binary itself, such as `harnessw` and the `AGENTS.md` blocks, or to one
[plugin](plugins.md#renders) in use: the claude plugin renders `.claude/settings.json` keys, the codex plugin its rules
file, the git plugin its `.gitignore` blocks. The binary contains no format of any program; each plugin's renders
describe its program's files. The manifest records every output with its owner, so deleting a plugin from `plugins:`
in the profile and rendering removes exactly that plugin's renders. What the plugin applied once, its
[entries](plugins.md#entries) and [templates](plugins.md#templates), stays as the repository's own.

Examples on this page run commands as `./harnessw <command>`, through the repository's [wrapper](wrapper.md); with a
global install, `harness <command>` is equivalent.

## When to use it

| Situation | Command |
| --- | --- |
| You edited `.harness/config.yaml`, `.harness/policy.yaml` or `.harness/hooks.yaml` | `./harnessw render` |
| You turned a module on or off, moved a state path, or changed the `context` section | `./harnessw render` |
| You want a stack's starting commands, lanes and post-edit hooks | `./harnessw plugin apply <name>`, which adds the plugin to `plugins:`, writes its entries and renders ([plugins](plugins.md)) |
| You stopped using a program, such as Codex | delete its plugin from `plugins:` (and the agent from `agents`), then `./harnessw render`, which removes that plugin's renders |
| You want CI or a reviewer to confirm rendered files match their sources | `./harnessw render --check` |
| You want to see what would change | `./harnessw render --dry-run` |

## How it works

1. Read the repository files and validate them against the schema. Resolve every plugin `plugins:` lists: a builtin
   plugin from the release, a [fetched plugin](plugins.md#fetched-plugins) from the per-user cache, verified by its
   `sha256`. A fetched plugin that is not cached stops the render with exit 3 and the command that caches it,
   `./harnessw plugin fetch`; `render` never fetches.
2. Compute every output: the binary's own and every plugin's renders, which are owned files, owned keys inside native
   files, managed blocks and links, and the [floor rules](plugins.md#the-floor) of every plugin in use.
3. Compare with the manifest and the working tree.
4. Write what changed, delete what is no longer produced (for example the files and blocks of a module you turned
   off, or the renders of a plugin you deleted from `plugins:`), and rewrite the manifest, which records each output
   with the plugin that owns it.

Floor rules are part of their plugin's renders. They go into every agent's output on every render, never into
`.harness/policy.yaml`, and the policy cannot remove or weaken them. A policy entry that would weaken one, such as an
`allow` for a force push to the default branch, a floor rule of the git plugin, is not a validation error. `render`
leaves it out of every agent's output, renders the floor rule, and prints the entry it dropped.
[`harness doctor`](doctor.md) reports it as a finding until the entry is removed from the policy
([ADR 0011](../adrs/0011-safety-invariants.md)).

A policy entry for a `harness` command, such as `[harness, lifecycle, run, deliver]`, renders once for each spelling an
agent can type: `harness …`, `./harnessw …` and `harnessw …`
([policy](policy.md#entries-for-harness-commands)).

Rendering is deterministic: the same inputs produce the same bytes on linux and macOS, and a second run changes nothing.
Output depends on the repository's own files, the pinned release that renders them and its builtin plugins, and the
fetched plugins the profile pins by hash, and on nothing else: `render` reads nothing from the network, and from the
per-user cache only fetched plugins whose content matches their `sha256`
([ADR 0013](../adrs/0013-organization-content.md), [ADR 0022](../adrs/0022-tool-integration.md)). No output records the
release: only `.harness/wrapper.yaml` names it. A new release can render differently, so
[`harness wrapper --version <tag>`](wrapper.md#upgrading) renders the repository with the new release in the same
change, and the pin and its effect are reviewed as one diff.

### What render writes

| Output | Kind | Owner | Produced when | Contents |
| --- | --- | --- | --- | --- |
| `harnessw` | owned file | the binary | always | the [wrapper](wrapper.md) script from the pinned release's template, mode `0755` |
| `.claude/settings.json` `permissions.allow`, `.ask`, `.deny` | owned keys | claude plugin | an agent whose harness plugin is `claude` | the [policy](policy.md#how-entries-render) for Claude, with the floor rules of every plugin in use |
| `.claude/settings.json` `hooks` entries that run the [hook command](#the-hook-command) | owned entries | claude plugin | that agent and `modules.hooks` | one entry per event the repository uses: `SessionStart` (session context, `always` context), `PostToolUse` (post-edit commands, validators, `when.paths` context), `UserPromptSubmit` (`when.prompt` [context](context.md)), `Notification` (`attention` hooks), `Stop` (`turn_end` hooks) |
| `.claude/settings.json` `raw` hook entries | owned entries | claude plugin | that agent and `modules.hooks` | `raw` hooks from `hooks.yaml`, rendered as native entries that run without the binary |
| `.codex/rules/harness.rules` | owned file | codex plugin | an agent whose harness plugin is `codex` | the policy for Codex, with the floor rules of every plugin in use |
| `.codex/hooks.json` harness and `raw` entries | owned entries | codex plugin | that agent and `modules.hooks` | as for Claude, for each event Codex emits |
| `.codex/config.toml` keys that enable project hooks, and `notify` | owned keys | codex plugin | that agent and `modules.hooks` | `notify` runs the hook command when Codex finishes a turn, for `turn_end` hooks |
| `.agents/skills/harness/SKILL.md` | owned file | the binary | always | the [harness skill](skills.md#the-harness-skill) |
| `.claude/skills/harness`, `.codex/skills/harness` | owned links | claude plugin, codex plugin | the agent is declared, unless the skills directory is already a whole-directory link to `.agents/skills` | `../../.agents/skills/harness` |
| Managed blocks | blocks | see [Block ids](#block-ids) | see [Block ids](#block-ids) | |
| `.harness/manifest.json` | owned file | the binary | always | the ownership record: every output with its owner |

An agent that emits no event for a declared hook or context trigger, such as Codex for `attention`, gets nothing
rendered for it, and [`harness doctor`](doctor.md) notes the gap.

`.harness/wrapper.yaml` is owned but not written by `render`: `scaffold` writes it and
[`harness wrapper`](wrapper.md#harness-wrapper) rewrites it, each after verifying the release's signed checksum file.
The manifest records it, so a hand edit is drift. The seeded files are never re-rendered: `scaffold` writes
`config.yaml`, `policy.yaml` and `hooks.yaml` once, and the memory skeleton is seeded when the memory module is
enabled, by `scaffold` or by the first `render` after the module is turned on. From then on the repository owns them.
The manifest records each with the hash it was created with ([memory](memory.md#the-skeleton)).
Lifecycle actions are not rendered either: `harness` reads them from `hooks.yaml` and runs them itself
([lifecycle](lifecycle.md)).

### Merge-rendering native files

Agents read their configuration from fixed paths that people also edit. A harness plugin's renders therefore own only
specific keys and entries inside those files, never the whole file; which keys is part of the plugin, not of the
binary:

| File | Owned by its plugin's renders | Everything else |
| --- | --- | --- |
| `.claude/settings.json` (claude plugin) | `permissions.allow`, `permissions.ask`, `permissions.deny`; hook entries that run the hook command; hook entries rendered from `hooks.yaml` `raw` | preserved byte for byte: `env`, `model`, `statusLine`, `enabledPlugins`, `$schema`, other hooks, unknown keys |
| `.codex/config.toml` (codex plugin) | the keys that enable the project's hooks, and `notify` | preserved byte for byte |
| `.codex/hooks.json` (codex plugin) | harness hook entries and `raw` entries | preserved |
| `.codex/rules/harness.rules` (codex plugin) | the whole file | other files in `.codex/rules/` are untouched and keep applying |
| `.claude/settings.local.json`, `*.local.toml` | nothing | never written; read only by `harness scaffold --import local` |

`render --check` compares the merged result, so a hand edit to an owned key is drift, and an edit anywhere else is
not.

### The hook command

Every harness hook entry, for every agent, runs the same command with the entry's event as its last argument. It
finds the wrapper at the checkout's root through the version-control plugin's root command; with the git plugin:

```sh
"$(git rev-parse --show-toplevel)/harnessw" --no-download hook <event>
```

`<event>` is the neutral event the entry is for: `session-start`, `post-tool-use`, `prompt-submit`, `attention` or
`turn-end`. The command resolves the wrapper from the root of the checkout the agent works in, primary or worktree,
and never downloads. When the pinned release is not cached, the wrapper prints one line naming `./harnessw` on stderr
and exits 0, and the session continues. For `session-start` it also prints one line of agent context, so the agent
can tell the person. The wrapper prints both lines itself, as fixed text from its template, because no binary is
there to print them:

```text
harness v0.9.0 is not cached on this machine; run ./harnessw version once to fetch it.
```

Until then, hook-delivered context and notification hooks do nothing on that machine; `static` context entries in
`AGENTS.md` still apply. See [wrapper](wrapper.md#which-entry-points-download) and [hooks](hooks.md).

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

Block ids are per module, per plugin and per convention: a module or a plugin owns its blocks, and turning the module
off, deleting the plugin from `plugins:` or [removing](remove.md) it removes them. The column below names the owner;
"core" is the binary. Every block is one of two classes ([ADR 0018](../adrs/0018-conventions.md)):

- A **convention block** states a fact that stays true without `harness`, such as where worktrees live or which
  permissions agents work under. It never names a `harness` command. An [eject](remove.md#eject) removes its markers
  and keeps its text.
- A **tool block** says how to use `harness` in this repository and may name `./harnessw` commands. An eject removes
  it.

| File | Id | Class | Content | Module, produced when |
| --- | --- | --- | --- | --- |
| `AGENTS.md` | `commands` | tool | how to run things, as `./harnessw` commands: lint, build, verify with its lanes, worktree | core, always |
| `AGENTS.md` | `layout` | tool | what `.harness/`, `.agents/` and the state paths are, and who writes each | core, always |
| `AGENTS.md` | `worktrees` | convention | where managed worktrees are created, their branch names, and that they start from committed code | core, always |
| `AGENTS.md` | `verify` | tool | the checks a change needs: lint, build, the lanes and when each is required | `verify` |
| `AGENTS.md` | `lifecycle` | tool | the conditions for delivery and the actions this repository declares for each event | core, always |
| `AGENTS.md` | `policy` | convention | the effective permissions by decision, as the native files enforce them, with each floor rule marked with the plugin it comes from, such as `(*floor rule of the git plugin*)`, and Claude Code-only entries marked; entries for `harness` commands are left out | core, always |
| `AGENTS.md` | `memory` | convention | the files under `.agents/memory/`, linked | `memory` |
| `AGENTS.md` | `context` | convention | the [standing context](context.md) entries declared `static`: their text, or a one-line pointer to the file | core, when the profile declares a `static` entry |
| `README.md` | `commands` | tool | the repository's commands, as in `AGENTS.md` | core, when `README.md` exists |
| `CLAUDE.md` (root, or `.claude/CLAUDE.md` when only that exists) | `import` | convention | one line: `@AGENTS.md` | the claude plugin, unless the file already imports `@AGENTS.md` |
| `.gitignore` | `state` | convention | `/<state.dir>/` | the git plugin, always |
| `.gitignore` | `worktrees` | convention | `/<worktrees.dir>/` | the git plugin, always |
| `.gitignore` | `ralph-tui` | convention | `/.ralph-tui/*` and `!/.ralph-tui/config.toml`: ralph-tui's session files, not its configuration | the ralph-tui plugin, always |

The paths come from the profile ([state paths](profile.md#state-paths)). Every block's text is generated from the
repository files.

`.gitignore` blocks omit any path the repository tracks. Each block lists its own paths even when another block
already covers them: `state` ignores `/.work/` and `worktrees` ignores `/.work/worktrees/`. Removing one block then
never un-ignores a path another still uses.

### The `AGENTS.md` budget

Agents read `AGENTS.md` at the start of every session, so every line `harness` writes there costs every session. The
blocks in `AGENTS.md` stay within a budget:

| Limit | Default | Set by |
| --- | --- | --- |
| all blocks in `AGENTS.md` together, the `context` block excepted | 40 lines | [`blocks.agents_md_budget`](profile.md#blocks) |
| any one block, the `context` block excepted | 8 lines | fixed |
| the `context` block and every other always-on context | 120 lines per role | [`context.budget`](context.md) |

Detail belongs in [`./harnessw explain <topic>`](explain.md) and the harness skill, which agents open when they need
it. A render that exceeds a budget still writes the blocks. [`harness doctor`](doctor.md) reports a note naming each
block and its line count, so a long lane list is visible in review.

### Rules for blocks

- A new block is appended at the end of its file. After that, you may move the whole block, markers included,
  anywhere in the file; `harness` updates it where it is.
- Never edit between the markers. `render` overwrites the edit and `render --check` reports it.
- A block's text states its content as fact; it never says it is managed by `harness`, beyond the marker. A
  convention block also never names a `harness` command, so it stays true after an eject.
- Text from other tools' sections (for example a section that `bd init`, the Beads setup command, added) is untouched;
  blocks are bound to their markers, not to headings.
- Deleting a block's markers by hand is drift: `render` puts the block back. Turn the module off, or run
  [`harness remove`](remove.md), to remove a block.

## Drift check

`./harnessw render --check` computes every output and compares it with the repository, writing nothing.

- Exit 0: everything matches.
- Exit 2: something differs. Each differing file, block or key is listed with the command that fixes it.
- The check reads only the checkout, the pinned release and the fetched plugins the profile pins by hash, so its
  result changes only when the repository's files or its pin change.
- The check runs as the pinned release, and no owned artefact is exempt, the harness skill included
  ([ADR 0007](../adrs/0007-ownership.md)). An upgrade renders the repository with the new release in the same change
  ([wrapper](wrapper.md#upgrading)), so the pinned release's output and the committed files agree, and a difference is
  always a real one. A binary other than the pinned release (`HARNESS_BINARY`) can report differences the pinned
  release would not; `harness doctor` notes when it runs.

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

The check reads nothing outside the checkout, the pinned release and the fetched plugins the profile pins by hash, so
the job needs no credentials. A repository that declares a [fetched plugin](plugins.md#fetched-plugins) adds a
`./harnessw plugin fetch` step before the check. To cache the release download between runs, see
[wrapper](wrapper.md#ci).

## Schema migration

When the repository files use an older `schema` than the running release's, they are migrated. The diff is shown and
confirmed before anything is written (`--yes` skips the question). This happens when
[`harness wrapper --version <tag>`](wrapper.md#harness-wrapper) moves the pin to a release with a newer schema, inside
the same change, and with `render` when a pinned release meets older files. When the files use a newer schema than the
running binary supports, which happens only when the pin is bypassed (for example with `HARNESS_BINARY`), `render`
refuses with exit 3. Its message names the file's schema, the newest schema this binary supports, and the release to
run, the one `.harness/wrapper.yaml` pins:

```text
harness: .harness/config.yaml uses schema 2; this binary (v0.8.2) supports schema 1. Run the pinned release, v0.9.0: ./harnessw render
```

## Reference

```text
harness render [--check] [--dry-run]
```

| Flag | Default | Meaning |
| --- | --- | --- |
| `--check` | off | Compare only; exit 2 on drift |
| `--dry-run` | off | Print what would be written or deleted; write nothing |

Global flags `-C <dir>`, `--yes` and `--verbose` also apply; see [CLI reference](cli.md).

| Exit code | Meaning |
| --- | --- |
| 0 | Rendered, or `--check` found no drift |
| 1 | Error: invalid repository file; `vcs:` or `agents.<name>.plugin` naming a plugin `plugins:` does not list; a fetched plugin whose content does not match its `sha256`, or that uses a builtin plugin's name |
| 2 | `--check` found drift |
| 3 | Repository schema is newer than the running release; a migration needs confirmation without a terminal; a fetched plugin is not cached (`./harnessw plugin fetch`) |

Files read: `.harness/config.yaml`, `.harness/policy.yaml`, `.harness/hooks.yaml`, `.harness/wrapper.yaml`,
`.harness/manifest.json`, the native files listed above, `harnessw`, `AGENTS.md`, `README.md`, `CLAUDE.md`,
`.gitignore`, `.agents/memory/`, the files and skills named by `static` context entries, the plugins `plugins:` lists
(from the release or the per-user cache), the tracked paths as the version-control plugin reports them.
Files written: the outputs listed in [What render writes](#what-render-writes).

## Examples

Allow a new command for agents and render it:

```sh
$EDITOR .harness/policy.yaml        # add [npm, run, typecheck] to shared.allow
./harnessw render
git diff .claude/settings.json .codex/rules/harness.rules AGENTS.md
```

Turn hooks off in a Terraform repository:

```yaml
# .harness/config.yaml
modules: { hooks: false, verify: true, memory: true }
```

```sh
./harnessw render      # removes the hook entries harness owns from .claude/settings.json
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
| `--check` lists `AGENTS.md` block `commands` | Someone edited inside the markers | Move the text outside the markers, run `./harnessw render` |
| `--check` lists `harnessw` | The script was edited by hand | `./harnessw render` restores it |
| A key you added to `settings.json` disappeared | It was inside an owned key (`permissions.*`) | Put the rule in `policy.yaml` |
| The harness skill link is missing | `.claude/skills` is a whole-directory link to `.agents/skills`, so no per-entry link is needed | Nothing to fix |
| `render` exits 3: a fetched plugin is not cached | Nothing on this machine has fetched it; `render` never fetches | `./harnessw plugin fetch`, then `./harnessw render` |
| Files of a program you stopped using are still rendered | Its plugin is still in `plugins:` | Delete it from `plugins:` and run `./harnessw render` |
| `doctor` notes that `AGENTS.md` exceeds its budget | A long lane list or many modules | Remove the lanes and modules the repository does not use, or raise `blocks.agents_md_budget` in the profile if every line earns its place |

## Coexistence

Merge-rendering exists so `harness` can share native files with people and other tools. See
[compatibility](compatibility.md) for settings files, skill-link shapes, other tools' `AGENTS.md` sections and
`.gitignore`.
