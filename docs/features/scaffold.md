# scaffold

## Objective

**Problem.** A developer bringing agents into a repository has to set up permissions, hooks, instructions and working
conventions, and done by hand, each repository comes out different. Many repositories already have such a setup, built
up by hand, and moving it to a new arrangement risks losing a rule: agents gain a permission someone denied, or
unattended runs stall on a prompt for a command someone had allowed, and nobody notices until it matters. Part of what
is already there needs a person's judgement to carry over, not a mechanical copy.

**Goal.** Any repository, with or without an agent setup, gets a working one in one reviewed change, in which every
permission rule and hook that applied before still applies, and anything that cannot be kept is named before anything is
written. Whatever needs a person's judgement is listed as work to do, not guessed.

## Overview

`harness scaffold` sets a repository up for `harness`. On a repository with no agent setup it seeds the repository files
from its own templates and applies the [plugins](plugins.md) whose detect rule matches (greenfield). With `--adopt` it
takes over an existing hand-written setup, imports every permission rule and hook it finds into the repository files
without losing one, and lists what needs judgement as a to-do for an agent. Either way it writes the repository's
[wrapper](wrapper.md), `harnessw`, pinned to the release that runs it.

`scaffold` runs from a `harness` binary, because a repository has no `harnessw` until `scaffold` writes it
([installation](installation.md#scaffolding-a-new-repository)). Every later command runs as `./harnessw <command>`;
with a global install, `harness <command>` is equivalent.

## When to use it

| Situation | Command |
| --- | --- |
| No `.claude/settings.json` permissions or hooks | `harness scaffold` |
| Hand-written permissions or hooks in `.claude/settings.json`, `CLAUDE.md` with content, existing skills | `harness scaffold --adopt` |
| ralph-tui or Beads to wire in, after scaffolding | their [plugins](plugins.md#builtin-plugins): `./harnessw plugin apply ralph-tui`, `./harnessw plugin apply beads` |
| The repository already has `.harness/config.yaml` | Not `scaffold`: edit the files and run [`./harnessw render`](render.md) |
| A `.harness/` directory that another tool owns, such as Harness.io pipelines | `scaffold` refuses; see [compatibility](compatibility.md#harnessio) |
| Many repositories at once | [`harness fleet scaffold`](fleet.md) |

Not sure which class a repository is in? Run `harness scaffold --adopt --dry-run`: on a greenfield repository it
finds nothing to import and behaves like a plain scaffold.

## How it works

### Greenfield

1. **Detect.** Choose the plugins: every builtin plugin whose detect rule matches, plus each `--plugin`
   ([plugin detection](#plugin-detection)). Ask the version-control plugin for the default branch; with the git
   plugin, the branch `origin/HEAD` names. A `.harness/` directory that holds neither a `harness` profile nor a
   manifest belongs to another tool: `scaffold` stops with exit 3 and names it, before planning anything.
2. **Plan.** Build the list of files, blocks and links to write. With `--dry-run`, print it and stop.
3. **Confirm.** Show the plan and ask. `--yes` skips the question. Without a terminal and without `--yes`, nothing is
   written and the command exits 3.
4. **Seed** the repository files from the templates `scaffold` holds, once, then apply the chosen plugins to them, as
   [`harness plugin apply`](plugins.md#how-apply-works) does:
   - `.harness/config.yaml`: its skeleton, with `plugins:` listing the chosen plugins and `vcs: git` (or the plugin
     `--vcs` names), plus the plugins' entries (agent declarations, commands, lanes, validators);
   - `.harness/policy.yaml` with one seeded entry, `[harness, lifecycle, run, deliver]` under `shared.ask`, rendered for
     each spelling `harness` renders, so the agent's own permission prompt asks a person before an agent runs the
     repository's delivery actions. The repository owns this seeded entry and may change it
     ([policy](policy.md#the-seeded-entry), [ADR 0015](../adrs/0015-development-lifecycle.md));
   - `.harness/hooks.yaml`: its skeleton, plus the plugins' post-edit hooks, with no `lifecycle` actions: no plugin
     `scaffold` detects holds one;
   - `.agents/memory/` skeleton, when the `memory` module is on;
   - each template of the chosen plugins whose file is absent.
5. **Pin** the release that runs `scaffold` in `.harness/wrapper.yaml`, an owned file: its exact version, and the
   checksums of its four platform binaries. They come from that release's checksum file, fetched over HTTPS and
   accepted only after its signature verifies against the release keys embedded in the running binary
   ([wrapper](wrapper.md#harnesswrapperyaml), [safety](safety.md#the-pinned-binary)). With `--offline`, `scaffold`
   makes no network request and writes a [pending pin](wrapper.md#a-pending-pin) instead: the version and the source,
   with `checksums: pending`.
6. **Render** everything else, exactly as [`harness render`](render.md) does: `harnessw`, the renders and floor rules of
   every chosen plugin (native agent files, skill links, `.gitignore` blocks), the harness skill, the other managed
   blocks, the manifest.

After this, the seeded files belong to the repository. `scaffold` never rewrites them; `render` reads them. People and
agents edit the profile, the policy and the hooks file; the pin changes only through
[`harness wrapper`](wrapper.md#harness-wrapper).

### Plugin detection

`scaffold` applies every builtin [plugin](plugins.md#builtin-plugins) whose detect rule matches, in the order of this
table, with the same rules as [`harness plugin apply`](plugins.md#how-apply-works):

| Plugin | Detected from | Adds |
| --- | --- | --- |
| `git` | `.git` | the version-control operations, the `state` and `worktrees` blocks of `.gitignore`, the force-push floor |
| `claude` | `.claude/`, `CLAUDE.md` | `agents.claude` with `role: interactive`, Claude Code's native files, its bypass floor |
| `codex` | `.codex/` | `agents.codex` with `role: interactive`, Codex's native files, its bypass floor |
| `mise` | `mise.toml`, `.mise.toml` | the `:::` floor |
| `skills` | `skills-lock.json` | the `skills` CLI's version check |
| `go` | `go.mod` | lint and build commands, a `go test` lane, a format-on-edit hook for `*.go` |
| `node` | `package.json` | lint and build commands, test lanes, a lint-on-edit hook for source files |
| `terraform` | `*.tf` files | format command, `validate` and `plan` lanes, a format-on-edit hook |
| `python` | `pyproject.toml` or `requirements.txt` | lint command, a test lane, a lint-on-edit hook |

When neither `claude` nor `codex` is detected, as in a repository with no agent files yet, `scaffold` applies both, so
the repository gets an agent setup. A polyglot repository gets the plugins of every detected stack. Their lanes and
hooks sit side by side. For an item two plugins name, such as `commands.lint`, the repository gets the first plugin's
entry, and the other plugin's differing entry is reported, as [`plugin apply`](plugins.md#how-apply-works) reports
every difference it does not write. A repository whose stack is not recognised gets no stack plugin: the seeded files
hold their skeleton, the agent declarations and the seeded delivery `ask` entry only.

Three flags change the choice:

| Flag | Effect |
| --- | --- |
| `--plugin <name>` | adds a plugin to the detected set; repeatable. Example: `--plugin ralph-tui --plugin beads` |
| `--no-detect` | applies only the plugins named with `--plugin`, and the version-control plugin |
| `--vcs <name>` | names the plugin that fills the [version-control contract](plugins.md#the-version-control-contract) and is written as `vcs:`; default `git` |

`plugins:` in the profile records the plugins `scaffold` applied; their entries and templates are the repository's from
the moment they are written. A program the repository takes up later gets its plugin with
`./harnessw plugin apply <name>`. `ralph-tui` and `beads` have no detect rule, so `scaffold` applies them only when
`--plugin` names them; a repository wires ralph-tui or Beads with `./harnessw plugin apply ralph-tui` or
`./harnessw plugin apply beads`. A [fetched plugin](plugins.md#fetched-plugins) is declared in the profile after
scaffolding, never by `scaffold`.

### Files created when absent

| File | Created with |
| --- | --- |
| `AGENTS.md` | only the managed blocks; the prose is for people and agents to write |
| `CLAUDE.md` | only the `import` block, a single `@AGENTS.md` line |
| `.gitignore` | only the blocks of the chosen plugins: `state` and `worktrees` from the git plugin |

When these files exist, `scaffold` appends its blocks at the end and touches nothing else. `README.md` is never
created; when it exists, it receives the `commands` block. The [block ids](render.md#block-ids) table lists every
block and the module that owns it.

## Adopt

`harness scaffold --adopt` runs the greenfield steps, and first imports what the repository, and optionally its
contributors' personal settings, already configure. The import is lossless: every permission rule in the project scope
is still in effect after adoption, or adoption stops.

### Import scopes

`--import <scope>[,<scope>...]` chooses where adoption reads from. It takes a comma-separated set from the closed
enumeration `project`, `local`, `user`, `none`. With `--adopt` the default is `project`; without `--adopt` it is
`none`.

| Scope | Reads | Imported entries |
| --- | --- | --- |
| `project` | the native rules of each chosen harness plugin: `.claude/settings.json` `permissions.allow`, `ask`, `deny` for `claude`, every `.codex/rules/*.rules` file for `codex`; hooks in `.claude/settings.json` and `.codex/hooks.json`; any other permission source it recognises | committed as the repository's own |
| `local` | `.claude/settings.local.json`, `.codex/*.local.toml` | marked for review in the to-do: they are one person's settings and become committed and shared |
| `user` | `~/.claude/settings.json`, `~/.codex/rules/` | marked for review in the to-do, for the same reason |
| `none` | nothing | nothing |

Source files of every scope are read, never written. Personal settings keep applying to the person who has them,
alongside the policy.

### How each source becomes an entry

| Found | Becomes |
| --- | --- |
| `Bash(<prefix> *)` rule with a plain word prefix | a prefix list under the same decision, for example `[npm, run, test]`, rendered for both agents |
| Any other Claude Code rule: `Bash(...)` with a mid-pattern `*`, `WebFetch(domain:...)`, `Read(...)`, `Edit(...)`, a bare tool name, `mcp__server__tool` with globs | a native rule string under the same decision, rendered verbatim for Claude Code |
| Codex `prefix_rule` with decision `allow`, `prompt` or `forbidden` | a prefix list under `allow`, `ask` or `deny` |
| A hook in `.claude/settings.json` or `.codex/hooks.json` | a modelled declaration in `hooks.yaml` when it has a shape `hooks.yaml` models, otherwise a `raw` entry under its event, with its matcher, rendered as written so it keeps running without `harness` |
| A permission file in another tool's dialect | a to-do item for an agent to translate |

See [policy](policy.md#entry-forms) for the three entry forms.

Every imported entry carries a provenance comment naming its file and scope:

```yaml
shared:
  allow:
    # imported from .claude/settings.json (project)
    - [npm, run, [build, lint, test]]
    # imported from .claude/settings.json (project)
    - "WebFetch(domain:pkg.go.dev)"
    # imported from ~/.claude/settings.json (user); review: personal setting, now shared
    - [kubectl, get]
```

### Conflicts and invariants

| Situation | Result |
| --- | --- |
| The same entry under the same decision in several sources | one entry; its provenance comment names every source |
| The same pattern under `allow` and under `deny` | the entry is kept under `deny` only, and a to-do item asks a person to confirm |
| An entry that would weaken a [floor rule](plugins.md#the-floor) of a chosen plugin, such as allowing a force push to the default branch, or a [built-in invariant](concepts.md#built-in-invariants), such as allowing a permission-bypass argument | dropped, and reported by name in the output |

### Not policy: notes

These settings are not permission rules. Adoption leaves them where they are and reports each one as a note:

- Claude Code `defaultMode` and `additionalDirectories`;
- Codex `approval_policy` and `sandbox_mode`;
- MCP server definitions;
- enterprise and managed settings.

Other `.codex/rules/*.rules` files stay in place and keep applying; a note names them, so the repository can delete
them once it has reviewed the imported entries.

### The equivalence check

Before writing anything, adoption renders the new repository files and compares, per agent, the effective rule set
they produce with the rule set in effect before adoption. Every project-scope rule must still be in effect, with the
same decision. Entries dropped for weakening an invariant are reported separately and are not part of the comparison.

When a rule would be lost, adoption writes nothing and exits 2, naming the rule, its source file and the agent:

```text
equivalence: Bash(gh api *) from .claude/settings.json (project) is not in effect for claude after adoption
```

### What is kept as it is

| Found | Treatment |
| --- | --- |
| Keys of `.claude/settings.json` other than `permissions` and harness hook entries (`env`, `model`, `statusLine`, `$schema`, …) | preserved byte for byte |
| `.claude/settings.local.json`, `*.local.toml`, user-level settings | never written; local override files are read only by `scaffold --import local`, user-level settings only by `scaffold --import user` |
| Real `.claude/skills/`, `.claude/commands/`, `.claude/agents/` directories | stay where they are; the harness skill is added as one link inside |
| Whole-directory links (`.claude/skills -> ../.agents/skills`) | stay; the harness skill is reached through them |
| `CLAUDE.md` content | untouched; the one-line `import` block is added (root `CLAUDE.md`, or `.claude/CLAUDE.md` when only that exists) |
| A `.beads/` or a root `.ralph-tui/` | never read, written or moved |
| Task-runner files: `mise.toml`, `.mise/tasks/`, a `Makefile`, a package manager's scripts | never written, and their content never read; a `package.json` or a `mise.toml` only matches a plugin's detect rule |
| `core.hooksPath`, git hooks, Husky | untouched |
| A `.harness/` directory without a `harness` profile or manifest, such as Harness.io pipelines | not adopted: `scaffold` refuses with exit 3 and names the directory; nothing is written |
| `.gitignore` entries | untouched; the blocks omit paths the repository tracks |
| `.claude/worktrees/` | Claude Code's; never scanned |

### The to-do

What adoption cannot resolve deterministically, it prints as a to-do. The same list appears in
[`harness doctor`](doctor.md) (and `harness doctor --json`) until each item is resolved.

| Item | Raised when | Resolved when |
| --- | --- | --- |
| Personal entry to review | an entry was imported from the `local` or `user` scope | its `review:` marker is gone from the provenance comment: the entry was kept on purpose, or removed |
| Allow/deny conflict | a pattern was both allowed and denied | its `review:` marker is gone: the deny stays, or a person removes it |
| Foreign permission dialect | a permission file in another tool's format was found | an agent has expressed its rules in `policy.yaml` and the file is retired |
| `.agents/AGENTS.md` instead of a root `AGENTS.md` | the repository keeps instructions under `.agents/` | the content is moved to the root `AGENTS.md`, which Codex reads |

`doctor` derives every item from the repository's current state, so an item closes as soon as its condition is gone.

Adoption also prints notes that need a decision but are not to-do items, because keeping things as they are is valid:

- `CLAUDE.md` has content: instructions every agent needs belong in `AGENTS.md`, which Codex reads too;
  Claude-specific ones stay in `CLAUDE.md`.
- The settings listed under [Not policy](#not-policy-notes).

An agent finishes the adoption with the [harness skill](skills.md#the-harness-skill): ask it to "finish the harness
adoption"; it reads `./harnessw explain adoption`, edits the repository files, and runs `./harnessw render --check` and
`./harnessw doctor` until both pass.

Importing the `local` or `user` scope publishes one person's permissions to everyone who uses the repository. Review
those entries as you would any policy change; see [safety](safety.md).

## Reference

```text
harness scaffold [--adopt] [--import <scope>[,<scope>]...] [--plugin <name>]... [--no-detect] [--vcs <name>]
                 [--offline] [--yes] [--dry-run]
```

| Flag | Default | Meaning |
| --- | --- | --- |
| `--adopt` | off | Import an existing setup and emit a to-do |
| `--import <scope>[,<scope>]...` | `project` with `--adopt`, else `none` | Scopes to import, as a comma-separated set from the closed enumeration `project`, `local`, `user`, `none` |
| `--plugin <name>` | none | Apply this builtin [plugin](plugins.md#builtin-plugins) besides the detected ones; repeatable |
| `--no-detect` | off | Apply only the plugins named with `--plugin`, and the version-control plugin |
| `--vcs <name>` | `git` | The plugin that fills the [version-control contract](plugins.md#the-version-control-contract), written as `vcs:` and always applied |
| `--offline` | off | Make no network request. The pin is written [pending](wrapper.md#a-pending-pin), without checksums, and is completed later with `harness wrapper --version <tag>` |
| `--yes` | off | Do not ask for confirmation |
| `--dry-run` | off | Print the plan, the generated `policy.yaml` and `hooks.yaml`, and the equivalence report; write nothing |

Global flags `-C <dir>`, `--json`, `--verbose` also apply; see [CLI reference](cli.md).

| Exit code | Meaning |
| --- | --- |
| 0 | Scaffolded or adopted. With `--adopt`, open to-do items do not change the exit code |
| 1 | Error, including a repository that already has `.harness/config.yaml`, an unknown plugin name, a checksum file of the pinned release that cannot be fetched without `--offline`, and one whose signature does not verify. Nothing is written |
| 2 | The equivalence check found a project-scope rule that would not be in effect; nothing was written |
| 3 | A person must act: confirmation needed and no terminal (rerun with `--yes`, or from a terminal), a `.harness/` directory that belongs to another tool, or the version-control plugin's program, such as git, not installed |

Files read: the files each builtin plugin's detect rule names, the default branch the version-control plugin reports,
`.claude/settings.json`, `.codex/rules/`, `.codex/hooks.json`, `CLAUDE.md`, `.claude/CLAUDE.md`, `.gitignore`,
`.claude/skills`; with `--import local`, `.claude/settings.local.json` and `.codex/*.local.toml`; with `--import user`,
`~/.claude/settings.json` and `~/.codex/rules/`. Files written: see [render](render.md#what-render-writes) (`harnessw`
included), the owned `.harness/wrapper.yaml`, plus the seeded `.harness/config.yaml` (with `plugins:` and `vcs:`),
`.harness/policy.yaml`, `.harness/hooks.yaml` and `.agents/memory/`, and each template of the plugins it applies whose
file is absent.

## Examples

### A Node API, greenfield

```sh
$ cd ~/src/acme-api
$ harness scaffold --dry-run
detected: git (.git), node (package.json); no agent files: applying claude, codex; default branch: main
would write:
  harnessw  .harness/wrapper.yaml (v0.9.0)
  .harness/config.yaml  .harness/policy.yaml  .harness/hooks.yaml  .agents/memory/
  .agents/skills/harness/SKILL.md  .claude/skills/harness  .codex/skills/harness
  .claude/settings.json  .codex/config.toml  .codex/hooks.json  .codex/rules/harness.rules
  AGENTS.md (new)  CLAUDE.md (new)  README.md (block)  .gitignore (blocks)
  .harness/manifest.json
$ harness scaffold --yes
$ git add -A && git commit -m "chore: adopt harness"
$ ./harnessw doctor
```

Then review the seeded lanes in `.harness/config.yaml` against the repository's real test commands.

### A Terraform repository, greenfield

```sh
$ cd ~/src/acme-infra
$ harness scaffold --no-detect --plugin claude --plugin terraform --yes
```

The profile lists `git`, `claude` and `terraform` under `plugins:` and gets `validate` and `plan` lanes, and the
`commands` block in `AGENTS.md` shows `./harnessw verify …`.

### Offline, completed later

```sh
$ cd ~/src/acme-api
$ harness scaffold --offline --yes
harness: pin of v0.9.0 is pending; with network access, run: harness wrapper --version v0.9.0
$ git add -A && git commit -m "chore: adopt harness"
# later, with network access:
$ harness wrapper --version v0.9.0       # verifies v0.9.0's signed checksum file, writes the checksums
$ git diff --stat                        # .harness/wrapper.yaml and .harness/manifest.json only
$ git commit -am "chore: complete the harness pin" && git push
```

Everything else works offline at once on this machine through the global `harness`. Agent hooks and `./harnessw` start
working here once the pin is complete and the release is in the cache; on other machines they need the completed pin
([wrapper](wrapper.md#a-pending-pin)).

### A Go repository with hand-written Claude and Codex settings

```sh
$ cd ~/src/acme-cli
$ harness scaffold --adopt --dry-run
--- .harness/policy.yaml (generated)
schema: 1
shared:
  allow:
    # imported from .claude/settings.json (project)
    - [go, [build, test, vet]]
    # imported from .claude/settings.json (project)
    - "WebFetch(domain:pkg.go.dev)"
    # imported from .codex/rules/default.rules (project)
    - [golangci-lint, run]
  ask:
    - [harness, lifecycle, run, deliver]
  deny:
    # imported from .claude/settings.json (project)
    - [goreleaser, release]
worker: { deny: [] }
--- .harness/hooks.yaml (generated)
schema: 1
raw:
  PostToolUse: [{ matcher: Edit|Write, run: ./scripts/format-edited.sh }]
  Stop: [{ run: ./scripts/notify.sh }]
--- equivalence
claude: 14 of 14 project rules in effect
codex: 3 of 3 project rules in effect
dropped: Bash(git push --force origin main) weakens a floor rule of the git plugin
note: .codex/rules/default.rules still applies; delete it once the import is reviewed
note: defaultMode in .claude/settings.json is not policy; left in place
$ harness scaffold --adopt --yes
note: CLAUDE.md has content; instructions every agent needs belong in AGENTS.md
$ claude "finish the harness adoption"
$ ./harnessw doctor
```

### Importing a person's settings too

```sh
$ harness scaffold --adopt --import project,local --yes
to-do:
  1. review [docker, compose, up] imported from .claude/settings.local.json (local): now shared with everyone
```

## Guidelines

- Do run `--dry-run` first on any repository you did not create, and read the equivalence report.
- Do commit the scaffold as its own commit, so later changes to the repository files are reviewable on their own.
- Do complete a pending pin before you push: until then, no machine without the release in its cache can run
  `./harnessw`.
- Do review the seeded lanes and commands: plugins guess from the stack, while the repository knows its real commands.
- Do give the to-do to an agent with the harness skill; it is written for that.
- Do import `local` or `user` only when those settings are meant for everyone, and review each entry they bring.
- Don't run `scaffold` again to "refresh" a repository. Edit the repository files and run `./harnessw render`. To add a
  stack's entries, run `./harnessw plugin apply <name>`. To move to another release, run `./harnessw wrapper`.
- Don't hand-edit rendered files to finish an adoption; the next render overwrites them and `render --check` fails.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| `.harness/config.yaml already exists` | The repository is already scaffolded | Use `./harnessw render`; to start over, `./harnessw remove` first |
| Exit 1: the checksum file of the pinned release cannot be fetched | No network access to the release source, which the pin's checksums come from | Rerun with network access, or with `--offline` and complete the [pending pin](wrapper.md#a-pending-pin) before you push |
| Exit 1: the checksum file's signature does not verify | The file is not signed by a release key the running binary trusts | Do not work around it; obtain `harness` again from the project's releases and report it ([safety](safety.md#the-pinned-binary)) |
| Exit 3 in a script | No terminal to confirm | Pass `--yes` |
| Exit 3 naming `.harness/` | The directory belongs to another tool, such as Harness.io pipelines | `harness` cannot share it; see [compatibility](compatibility.md#harnessio) |
| Exit 2 naming a rule | The equivalence check found a project rule the new files would not keep in effect; nothing was written | Keep the repository unadopted and report the rule to the `harness` project, with the rule and the agent version ([ADR 0016](../adrs/0016-permission-adoption.md)) |
| Wrong stack detected | Marker files of another stack are present (for example a `package.json` for tooling in a Go repository) | Before scaffolding, pass `--no-detect` with a `--plugin` for each plugin you want, after checking with `harness plugin list`; afterwards, delete the entries that do not apply from `.harness/config.yaml` and `.harness/hooks.yaml`, delete the plugin from `plugins:` and run `./harnessw render`; `./harnessw plugin apply <name>` adds the right stack's entries if they are missing |
| A rule allowing a force push is missing after adopt | It weakens a floor rule of the git plugin and was dropped | Nothing to restore; the floor rule holds while the git plugin is in use |
| Exit 3 naming git | The version-control plugin's program is not installed | Install git, or pass `--vcs <name>` for the plugin of the version control system the repository uses |

## Coexistence

`scaffold` never moves or rewrites what it did not create. See [compatibility](compatibility.md) for the full list,
including GitHub, Beads, ralph-tui, mise and other agents' files.
