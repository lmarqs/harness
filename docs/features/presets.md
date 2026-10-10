# Presets

## Objective

**Problem.** Repositories on the same technology stack need the same starting commands: how to lint a file an agent has
just edited, how to build, which tests show a change works. A developer setting up a repository, or a maintainer of many
on one stack, writes them from scratch or copies them from another repository, and each copy comes out a little
different. Shared from a place outside the repository instead, they leave a reviewer or an agent reading the repository
unable to tell what it runs.

**Goal.** A repository on a common stack gets working lint, build and test commands in one reviewed change, and
everything it runs afterwards can be read from its own files.

## Overview

A preset is a named bundle embedded in the `harness` binary, which a person applies with `harness preset apply <name>`.
It holds **entries**, merged into the repository's three YAML files (`.harness/config.yaml`, `.harness/policy.yaml`,
`.harness/hooks.yaml`), and zero or more **templates**: whole files the preset writes when they are absent
([Templates](#templates)). The binary holds two kinds:

- **Stack presets**, `go`, `node`, `terraform` and `python`: how to lint an edited file, how to build, which test lanes
  (named families of test commands, such as `unit` or `e2e`) to run, which command formats or lints a file after an
  agent edits it. `harness scaffold` applies the presets of the stacks it detects.
- **Tool presets**, `ralph-tui` and `beads`: the entries and files that wire a tool around the agents into the
  repository. `harness` has no code for these tools; a tool preset writes the configuration the tool reads and the
  actions that call it ([ADR 0022](../adrs/0022-tool-integration.md)).

`apply` adds only what the files lack, writes only templates whose file is absent, and renders the repository, so the
additions and their rendered effect are one diff to review.

Once written, the entries and templates are the repository's own, like anything written by hand. Nothing records which
presets a repository applied, and people and agents change or delete what a preset wrote by editing the file. What
`harness` renders and runs in a repository comes only from that repository's files and the release it pins. There are
no includes and no source outside the repository. Rendering needs no network
([ADR 0013](../adrs/0013-organization-content.md)).

Commands are named `harness <command>` on this page. The examples run them through the repository's wrapper,
`./harnessw <command>`, from the root of a checkout; with a global install, `harness <command>` is equivalent
([wrapper](wrapper.md)).

## When to use it

| Situation | Command |
| --- | --- |
| Set up a new repository with the entries of its stacks | `harness scaffold`: it detects the stacks and applies their presets ([scaffold](scaffold.md#stack-detection-and-presets)) |
| Set up a new repository with one chosen stack preset, or with none | `harness scaffold --preset <name>`, `harness scaffold --preset none` |
| The repository gained a stack, such as a Node front end in a Go repository | `./harnessw preset apply node` |
| See which presets the pinned release holds, and which fit this repository | `./harnessw preset list` |
| Run ralph-tui over the repository's tasks, each attempt under [`harness worker`](ralph.md) | `./harnessw preset apply ralph-tui` |
| Mark, close and comment on [Beads](tasks.md) tasks from managed worktrees | `./harnessw preset apply beads` |
| Read the entries and templates a preset holds | `./harnessw preset show <name>` |
| See what a newer release's preset adds, after moving the pin | `./harnessw preset apply <name> --dry-run` |
| Apply a preset in many repositories, each delivered for review | `harness fleet preset apply <name> --yes --deliver` ([fleet](fleet.md)) |
| Bring an organization's conventions into a repository | a reviewed change to the repository's files: see [Organization choices](#organization-choices) |

### Organization choices

A preset holds only facts that are the same in every repository on its stack, or in every repository that uses its tool.
An organization's choices, such as the MCP tools its agents may read, the deploy tasks they must never run, a trailer
its CI needs on delivery commits, or the rules for its task tracker, differ between its repositories, so applying them
takes judgement. A person or an agent writes them into each repository's own files, typically an agent following a skill
the organization publishes, and each repository reviews the change as a diff like any other:

```sh
npx skills add acme/agent-skills                        # the organization's skill, installed in the repository
claude "apply the acme agent conventions to this repository"
./harnessw render
git diff .harness/ .claude/settings.json .codex/ AGENTS.md
```

Afterwards the entries are in the repository's files, where its people and agents read them, and the repository
renders the same bytes on every machine without fetching anything.

## The presets

### Stack presets

| Preset | Stack | Fits a repository with | Holds |
| --- | --- | --- | --- |
| `go` | Go | `go.mod` | lint and build commands, a `go test` lane, a format-on-edit hook for `*.go` |
| `node` | Node.js | `package.json` | lint and build commands, test lanes, a lint-on-edit hook for source files |
| `terraform` | Terraform | `*.tf` files | a format command, `validate` and `plan` lanes, a format-on-edit hook |
| `python` | Python | `pyproject.toml` or `requirements.txt` | a lint command, a test lane, a lint-on-edit hook |

Commands, lanes and [validators](profile.md#verify) (repository checks run when matching files change) go into
`.harness/config.yaml`; post-edit hooks go into `.harness/hooks.yaml`. `./harnessw preset show <name>` prints every
entry a preset holds. Policy entries, lifecycle actions and standing context are choices each repository makes in its
own files, so stack presets hold none.

A stack the table does not list gets no preset. `harness scaffold` then seeds the skeleton of each repository file and
the seeded delivery `ask` entry ([policy](policy.md#the-seeded-entry)), and the repository writes its commands and lanes
itself.

### Tool presets

A tool preset fits any repository; nothing detects it, and only `harness preset apply` writes it. It never installs,
initialises or configures the tool beyond the files it lists.

| Preset | Tool | Templates | Entries |
| --- | --- | --- | --- |
| `ralph-tui` | [ralph-tui](https://github.com/subsy/ralph-tui), a loop runner that starts an agent per task | `.ralph-tui/config.toml` | `worker.deny`: `[git, commit]`, `[git, push]`, `[gh]` |
| `beads` | [Beads](https://github.com/steveyegge/beads), a task tracker used through its `bd` CLI | none | `start`, `completed` and `blocked` actions with scope `task`; `.beads` in `agents.codex.writable_roots`; `worker.deny`: `[bd, close]` |

#### `ralph-tui`

The template `.ralph-tui/config.toml` makes every agent ralph-tui starts a [`harness worker`](ralph.md): one
`[[agents]]` entry per agent the profile declares, whose command is
`./harnessw worker <agent> --commit --withhold '<promise>COMPLETE</promise>'`. ralph-tui places `defaultFlags` before
its own arguments, so the worker reads its own flags first and hands everything from the first argument it does not
recognise to the agent. The other settings keep ralph-tui from doing what the worker does: it skips no permission
prompts, commits nothing itself (the worker commits a verified attempt), and stops on a non-zero exit. For a profile
that declares Claude Code and Codex:

```toml
autoCommit = false

[errorHandling]
continueOnNonZeroExit = false

[[agents]]
name = "claude"
plugin = "claude"
command = "./harnessw"
defaultFlags = ["worker", "claude", "--commit", "--withhold", "<promise>COMPLETE</promise>"]

[agents.options]
skipPermissions = false

[[agents]]
name = "codex"
plugin = "codex"
command = "./harnessw"
defaultFlags = ["worker", "codex", "--commit", "--withhold", "<promise>COMPLETE</promise>"]

[agents.options]
fullAuto = false
```

The file is the same one [unattended runs](ralph.md#the-ralph-tui-preset) explains key by key.

`--withhold` keeps ralph-tui's completion signal, `<promise>COMPLETE</promise>`, out of the output of an attempt that
failed verification, so ralph-tui sees it only from a verified one. The `worker.deny` entries leave commits, pushes and
`gh` to the worker and to the repository's `deliver` actions.

The preset adds no lifecycle actions: nothing fires the events of a ralph-tui run by itself. A run goes like this:

```sh
./harnessw worktree create run-invoice
cd .work/worktrees/run-invoice
ralph-tui run --epic api-42 --headless
./harnessw lifecycle run completed --scope run    # or: blocked --scope run
```

ralph-tui merges a person's `~/.config/ralph-tui/config.toml` into the project's file; `harness` reads neither. A
ralph-tui setting that turns permission checks off reaches the worker as a bypass flag, and the worker refuses it at the
first task.

#### `beads`

The actions bind Beads to [task binding](tasks.md): every event fired in a worktree created with
`./harnessw worktree create <name> --task <id>` carries the bead id in `HARNESS_TASK` and the actor in `HARNESS_ACTOR`.

```yaml
lifecycle:
  start:
    - name: bead-start
      run: bd update "$HARNESS_TASK" --status in_progress --assignee "$HARNESS_ACTOR"
      auto: true
      scope: [task]
  completed:
    - name: bead-close
      run: bd close "$HARNESS_TASK" --reason "verified"
      auto: true
      scope: [task]
  blocked:
    - name: bead-comment
      run: bd comments add "$HARNESS_TASK" "$HARNESS_REASON"
      auto: true
      scope: [task]
```

`bd` treats a missing id as an error, so an action fired without `HARNESS_TASK` fails instead of touching another
bead. `[bd, close]` under `worker.deny` leaves closing to the `completed` action, which runs only after a verified
attempt. `.beads` goes into `agents.codex.writable_roots` when the profile declares Codex, so a Codex worker can write
the database. Nothing creates, configures or localises a Beads database; that is `bd`'s own job. When ralph-tui works
from Beads, it updates bead status itself, and these actions matter for interactive work in managed worktrees.

### Templates

A template is a whole file a preset holds, rendered once with values from the profile, such as the agents it declares.
`apply` writes it only when the file is absent. From then on it is the repository's own file: `harness` never renders
it again, never overwrites it and never removes it, and [`harness remove`](remove.md) leaves it. When the file exists,
`apply` writes nothing to it and reports `kept <path>: exists`.

`harness scaffold` seeds the repository files, such as `.harness/config.yaml`, from templates of its own
([scaffold](scaffold.md#greenfield)). The manifest records those as seeded files, which `harness remove` deletes
([remove](remove.md#undo-and-eject-compared)); nothing records a template a preset wrote.

The presets are those of the running release, which in a repository set up with `harness scaffold` is the release it
pins in `.harness/wrapper.yaml`. A newer release may hold more entries or templates in a preset; they reach a repository
only when someone applies the preset again ([Applying again](#applying-again)).

## How apply works

`harness preset apply <name>...` applies one or more presets, in the order given, to a repository already set up with
`harness scaffold`:

1. **Check.** The repository needs `.harness/config.yaml`; without it, `apply` exits 1 and names `harness scaffold`.
   Every name must be a preset of the running release; an unknown name exits 1. On either failure nothing is written.
2. **Plan** the additions to the files the presets hold entries for: `.harness/config.yaml` (commands, lanes,
   validators, agent settings), `.harness/policy.yaml` (`worker.deny` entries) and `.harness/hooks.yaml` (post-edit
   hooks, lifecycle actions); and the templates whose file is absent, rendered with the profile. Each preset is planned
   against the files as the presets before it leave them, following the [merge rules](#merge-rules).
3. **Confirm.** Show the additions and every template to write as a diff, and every item kept with its difference or
   `exists`, then ask. `--yes` skips the question. Without a terminal and without `--yes`, nothing is written and the
   command exits 3. `--dry-run` prints the diff and writes nothing.
4. **Write** the additions into the files, and the templates.
5. **Render** the repository, as [`harness render`](render.md) does, so the additions and their rendered effect are
   one diff and the drift check passes on it.

When there is nothing to add, `apply` says so, writes nothing and exits 0.

### Merge rules

Applying only adds. The repository's own entries and files stay authoritative.

| Element | Rule |
| --- | --- |
| A list entry: a post-edit hook declaration, a validator, a `worker.deny` entry, a path in `agents.codex.writable_roots` | added when the list holds no equal entry, one with the same fields and values |
| A named item: a command, a lane, a lifecycle action | added when the repository has none by that name; when it has one that differs, the repository's stays and the difference is reported: `kept lanes.unit: differs from preset node` |
| A template | written when the file is absent; an existing file is the repository's and stays untouched, and is reported: `kept .ralph-tui/config.toml: exists` |
| Everything already in the files | nothing is removed, replaced or reordered; comments, key order and blank lines are preserved |

Additions go at the end of the list or map they belong to; a section the file lacks is added at its end. Two presets
applied together follow the same rules: when both name the same item, such as `commands.lint`, the first one's entry
is added and the second one's is reported as kept.

### Applying again

Applying is idempotent: a second `apply` of the same preset by the same release adds nothing, because every entry and
template is already there. Applying again is therefore the way to take a later release's improvements to a preset. After
moving the pin ([wrapper](wrapper.md#upgrading)), `./harnessw preset apply node --dry-run` shows what the new release
would add, and `./harnessw preset apply node` adds it as a diff to review.

Applying again also adds back an entry or a template the repository deleted, because nothing records that it was
removed on purpose. Read the diff before confirming. A template the repository kept is never updated by applying again:
to take a newer release's version, delete the file and apply, then carry over your own changes.

### After applying

Nothing records which presets a repository applied, not even the manifest or a comment in the file. Each entry's one
source of truth is the file it is in, and `harness` holds no record it could later use to change those entries by itself
([ADR 0013](../adrs/0013-organization-content.md)). Change or delete an applied entry or template by editing the file,
then run `./harnessw render`.

No command removes a preset: what it wrote is the repository's like anything else, and leaves the same way, by editing
or deleting the file. Both removal modes of [`harness remove`](remove.md) delete the seeded `.harness/` files whatever
they hold, so applied entries go with them; a template a preset wrote, such as `.ralph-tui/config.toml`, stays.

## Reference

```text
harness preset list
harness preset show <name>
harness preset apply <name>... [--yes] [--dry-run]
```

`preset` follows the `harness <noun> <verb>` shape. `apply` has no inverse, because the entries and templates it writes
become the repository's own and are removed like anything else.

### `harness preset list`

Prints the presets of the running release, stack and tool presets alike, each with its kind and the stack or tool it is
for. A stack preset also shows its marker files and whether they are present in this repository; a tool preset has no
marker files. It reads only the marker files and works in a repository that is not scaffolded yet, so it also helps
choose `scaffold --preset`.

| Exit code | Meaning |
| --- | --- |
| 0 | Listed |

Files read: the marker files of each stack. Files written: nothing.

### `harness preset show`

Prints every entry the preset holds, as the YAML it adds, grouped by the file it goes into, and every template with its
path and content. Templates are rendered with the repository's profile when there is one. It does not compare anything
with the repository; `apply --dry-run` shows what applying would write here.

| Exit code | Meaning |
| --- | --- |
| 0 | Printed |
| 1 | Unknown preset name; the valid names are listed |

Files read: `.harness/config.yaml`, when present. Files written: nothing.

### `harness preset apply`

Applies one or more presets, in the order given ([How apply works](#how-apply-works)).

| Flag | Default | Meaning |
| --- | --- | --- |
| `<name>...` | required | One or more presets: `go`, `node`, `terraform`, `python`, `ralph-tui`, `beads` |
| `--yes` | off | Apply without asking; required without a terminal |
| `--dry-run` | off | Print the additions and the kept items; write nothing |

Global flags `-C <dir>`, `--json` and `--verbose` also apply; see [CLI reference](cli.md). With `--json`, the output
lists the entries added, the templates written and the items kept, each with its file and, for an entry, its key.

| Exit code | Meaning |
| --- | --- |
| 0 | Applied, or nothing to add; `--dry-run` printed |
| 1 | Error: an unknown preset, a repository that is not scaffolded, an invalid repository file. Nothing is written |
| 3 | Confirmation needed and there is no terminal: rerun with `--yes`; or the repository files use a newer schema than the running release supports. Nothing is written |

Files read: `.harness/config.yaml`, `.harness/policy.yaml`, `.harness/hooks.yaml`, whether each template's file
exists, and everything [`render`](render.md#reference) reads. Files written: `.harness/config.yaml`,
`.harness/policy.yaml` and `.harness/hooks.yaml`, additions only; each template whose file was absent; then every file
`render` writes ([what render writes](render.md#what-render-writes)).

## Examples

### Add a preset to an adopted repository

The repository was scaffolded with `--preset none`, and its people already wrote their own `build` command:

```text
$ ./harnessw preset apply node
.harness/config.yaml
+  lint: npm run lint -- {file}
+  unit: { select: npm test -- {pattern}, full: npm test }
.harness/hooks.yaml
+  - { paths: ["src/**/*.{ts,js}"], run: npx eslint --fix {file} }
kept commands.build: differs from preset node
apply these additions? [y/N] y
applied node: 3 entries added, 1 kept; rendered .claude/settings.json, .codex/hooks.json, AGENTS.md
$ git add -A && git commit -m "chore: apply the harness node preset"
```

### Read a preset

```text
$ ./harnessw preset show go
# .harness/config.yaml
commands:
  lint: gofmt -l {file} | (! grep .)
  build: go build ./...
verify:
  lanes:
    test: { select: go test {pattern}, full: go test ./... }

# .harness/hooks.yaml
post_edit:
  - { paths: ["**/*.go"], run: gofmt -w {file} }
```

### Wire ralph-tui and Beads

The repository already keeps a hand-written `.ralph-tui/config.toml`:

```text
$ ./harnessw preset apply beads ralph-tui
.harness/config.yaml
+      writable_roots: [.beads]
.harness/policy.yaml
+    - [bd, close]
+    - [git, commit]
+    - [git, push]
+    - [gh]
.harness/hooks.yaml
+  start:
+    - { name: bead-start, run: bd update "$HARNESS_TASK" --status in_progress --assignee "$HARNESS_ACTOR", auto: true, scope: [task] }
+  completed:
+    - { name: bead-close, run: bd close "$HARNESS_TASK" --reason "verified", auto: true, scope: [task] }
+  blocked:
+    - { name: bead-comment, run: bd comments add "$HARNESS_TASK" "$HARNESS_REASON", auto: true, scope: [task] }
kept .ralph-tui/config.toml: exists
apply these additions? [y/N] y
applied beads, ralph-tui: 8 entries added, 1 kept; rendered AGENTS.md
```

The existing `.ralph-tui/config.toml` stays as it is. To take the preset's, move it aside, apply `ralph-tui` again, and
carry over what the old file needs; `./harnessw preset show ralph-tui` prints the template first.

### Choose a preset before scaffolding

```text
$ cd ~/src/acme-web
$ harness preset list
preset     kind   for         marker files                       in this repository
go         stack  Go          go.mod                             no
node       stack  Node.js     package.json                       yes
terraform  stack  Terraform   *.tf                               no
python     stack  Python      pyproject.toml, requirements.txt   yes
ralph-tui  tool   ralph-tui   -                                  -
beads      tool   Beads       -                                  -
$ harness scaffold --preset node --dry-run
```

`requirements.txt` here only pins a documentation tool, so the scaffold takes `node` alone.

### After an upgrade

```sh
./harnessw wrapper --version v0.10.0
./harnessw preset apply go --dry-run      # what v0.10.0's go preset holds that the files lack
./harnessw preset apply go --yes
git diff
```

### Many repositories

```sh
harness fleet preset apply node --yes --deliver
```

Each repository that lacks some of the preset's entries gets a branch with the additions and their rendered effect,
delivered through its own `deliver` actions. A repository that already holds every entry is reported as unchanged
([fleet](fleet.md)). The command runs without a terminal in each repository, so it needs `--yes`.

## Guidelines

- Do read the diff `apply` shows before confirming: once written, the entries and templates are the repository's own
  and nothing marks where they came from.
- Do edit applied entries to fit the repository: a preset guesses from the stack, the repository knows its real
  commands, and applying again never overwrites an item you changed.
- Do run `./harnessw preset apply <name> --dry-run` for the repository's stacks after moving the pin to a newer
  release: new preset entries reach the repository only when someone applies the preset again.
- Do commit the additions together with the files `apply` rendered, so the diff explains itself.
- Do write an organization's choices into each repository through a reviewed change, such as an agent following the
  organization's skill: they differ between repositories, and a preset holds only what every repository on a stack
  shares ([Organization choices](#organization-choices)).
- Don't apply a preset again to restore an item you changed: `apply` keeps every item the repository names. Delete
  yours first if you want the preset's.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| `kept lanes.unit: differs from preset node` | The repository already has a `unit` lane, and `apply` never replaces an item the repository names | Nothing, if yours is right. To take the preset's, delete yours and apply again |
| `kept .ralph-tui/config.toml: exists` | The file is there, and `apply` writes a template only when its file is absent | Nothing, if yours is right. To take the preset's, move yours aside, apply again, and carry over what you need |
| ralph-tui's first task fails with the worker refusing a permission-bypass flag | A ralph-tui configuration, the project's or `~/.config/ralph-tui/config.toml`, turns permission checks off or passes a bypass flag in `defaultFlags` | Keep `skipPermissions = false` under `[agentOptions]` in `.ralph-tui/config.toml` and remove the flag; the worker refuses it whoever passes it ([ralph](ralph.md)) |
| A Beads action fails with a missing id | The event fired in a worktree created without `--task`, so `HARNESS_TASK` is empty | Create the worktree with `./harnessw worktree create <name> --task <id>` ([tasks](tasks.md)) |
| `preset apply` exits 1 naming `harness scaffold` | The repository has no `.harness/config.yaml` | Run `harness scaffold`, which applies the detected stacks' presets |
| `preset apply` or `preset show` exits 1: unknown preset | The name is not a preset of the running release | `./harnessw preset list` prints the names |
| `preset apply` exits 3 | No terminal to confirm, and no `--yes` | Review with `--dry-run`, then rerun with `--yes`; under `fleet`, always pass `--yes` |
| An entry or a template you deleted is back after `preset apply` | `apply` adds every entry the files lack and writes every absent template, and nothing records that you removed them | Delete it again, and read the diff before confirming the next apply |
| `preset apply` after an upgrade adds nothing | The files already hold every entry of the preset, or equal ones | Expected |
| Only one stack's lint command is in the profile | `commands.lint` is one named item: the first preset applied wrote it, and the next one's was reported as kept | Write a lint command that covers both stacks, such as a script or a [mise](compatibility.md#mise) task that picks the linter by the file's extension |
| Entries of the wrong stack after `scaffold` | Marker files of another stack are present, such as a `package.json` for tooling in a Go repository | Delete the entries that do not apply from `.harness/config.yaml` and `.harness/hooks.yaml`, then `./harnessw render`; on the next repository, `harness scaffold --preset <name>` |
