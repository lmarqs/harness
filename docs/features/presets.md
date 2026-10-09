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

A preset is a set of starting entries for one technology stack, embedded in the `harness` binary: how to lint an
edited file, how to build, which test lanes to run, which command formats or lints a file after an agent edits it. The
binary holds four: `go`, `node`, `terraform` and `python`. `harness preset apply <name>` writes a preset's entries into
the repository's own files, `.harness/config.yaml` and `.harness/hooks.yaml`, adds only what those files lack, and
renders the repository, so the additions and their rendered effect are one diff to review. `harness scaffold` applies
the presets of the stacks it detects the same way.

Once written, the entries are the repository's own, like entries anyone wrote by hand. Nothing records which presets a
repository applied, and people and agents change or delete the entries by editing the file. What `harness` renders and
runs in a repository comes from that repository's files and the release it pins, and from nothing else: no inheritance,
no includes, no source outside the repository. Rendering needs no network
([ADR 0013](../adrs/0013-organization-content.md)).

Commands are named `harness <command>` on this page. The examples run them through the repository's wrapper,
`./harnessw <command>`, from the root of a checkout; with a global install, `harness <command>` is equivalent
([wrapper](wrapper.md)).

## When to use it

| Situation | Command |
| --- | --- |
| Set up a new repository with the entries of its stacks | `harness scaffold`: it detects the stacks and applies their presets ([scaffold](scaffold.md#stack-detection-and-presets)) |
| Set up a new repository with one chosen preset, or with none | `harness scaffold --preset <name>`, `harness scaffold --preset none` |
| The repository gained a stack, such as a Node front end in a Go repository | `./harnessw preset apply node` |
| See which presets the pinned release holds, and which fit this repository | `./harnessw preset list` |
| Read the entries a preset holds | `./harnessw preset show <name>` |
| See what a newer release's preset adds, after moving the pin | `./harnessw preset apply <name> --dry-run` |
| Apply a preset in many repositories, each delivered for review | `harness fleet preset apply <name> --yes --deliver` ([fleet](fleet.md)) |
| Bring an organization's conventions into a repository | a reviewed change to the repository's files: see [Organization choices](#organization-choices) |

### Organization choices

A preset holds only facts that are the same in every repository on its stack. An organization's choices, such as the
MCP tools its agents may read, the deploy tasks they must never run, a trailer its CI needs on delivery commits, or the
rules for its tracker, differ between its repositories, so applying them takes judgement. A person or an agent writes
them into each repository's own files, typically an agent following a skill the organization publishes, and each
repository reviews the change as a diff like any other:

```sh
npx skills add acme/agent-skills                        # the organization's skill, installed in the repository
claude "apply the acme agent conventions to this repository"
./harnessw render
git diff .harness/ .claude/settings.json .codex/ AGENTS.md
```

Afterwards the entries are in the repository's files, where its people and agents read them, and the repository
renders the same bytes on every machine without fetching anything.

## The presets

| Preset | Stack | Fits a repository with | Holds |
| --- | --- | --- | --- |
| `go` | Go | `go.mod` | lint and build commands, a `go test` lane, a format-on-edit hook for `*.go` |
| `node` | Node.js | `package.json` | lint and build commands, test lanes, a lint-on-edit hook for source files |
| `terraform` | Terraform | `*.tf` files | a format command, `validate` and `plan` lanes, a format-on-edit hook, `modules.ralph: false` |
| `python` | Python | `pyproject.toml` or `requirements.txt` | a lint command, a test lane, a lint-on-edit hook |

Commands, lanes, validators and module switches go into `.harness/config.yaml`; post-edit hooks go into
`.harness/hooks.yaml`. `./harnessw preset show <name>` prints every entry a preset holds. Policy entries, lifecycle
actions and standing context are choices each repository makes in its own files, so presets hold none.

The presets are those of the running release, which in an adopted repository is the pinned one. A newer release may
hold more entries in a preset; they reach a repository only when someone applies the preset again
([Applying again](#applying-again)).

A stack the table does not list gets no preset. `harness scaffold` then seeds the skeleton of each repository file and
the seeded delivery `ask` entry ([policy](policy.md#the-seeded-entry)), and the repository writes its commands and
lanes itself.

## How apply works

`harness preset apply <name>...` applies one or more presets, in the order given, to an adopted repository:

1. **Check.** The repository needs `.harness/config.yaml`; without it, `apply` exits 1 and names `harness scaffold`.
   Every name must be a preset of the running release; an unknown name exits 1. On either failure nothing is written.
2. **Plan** the additions to the files the presets hold entries for: `.harness/config.yaml` (commands, lanes,
   validators, module switches) and `.harness/hooks.yaml` (post-edit hooks). Each preset is planned against the files
   as the presets before it leave them, following the [merge rules](#merge-rules).
3. **Confirm.** Show the additions as a diff, and every item kept with its difference, then ask. `--yes` skips the
   question. Without a terminal and without `--yes`, nothing is written and the command exits 3. `--dry-run` prints
   the diff and writes nothing.
4. **Write** the additions into the files.
5. **Render** the repository, as [`harness render`](render.md) does, so the additions and their rendered effect are
   one diff and the drift check passes on it.

When there is nothing to add, `apply` says so, writes nothing and exits 0.

### Merge rules

Applying only adds. The repository's own entries stay authoritative.

| Element | Rule |
| --- | --- |
| A list entry: a post-edit hook declaration, a validator | added when the list holds no equal entry, one with the same fields and values |
| A named item: a command, a lane | added when the repository has none by that name; when it has one that differs, the repository's stays and the difference is reported: `kept lanes.unit: differs from preset node` |
| A scalar key, such as `modules.ralph` | written only when the file lacks it; a value already there is the repository's choice and stays, and a value that differs from the preset's is reported: `kept modules.ralph: differs from preset terraform` |
| Everything already in the files | nothing is removed, replaced or reordered; comments, key order and blank lines are preserved |

Additions go at the end of the list or map they belong to; a section the file lacks is added at its end. Two presets
applied together follow the same rules: when both name the same item, such as `commands.lint`, the first one's entry
is added and the second one's is reported as kept.

### Applying again

Applying is idempotent: a second `apply` of the same preset by the same release adds nothing, because every entry is
already there. That makes applying again the way to take a later release's improvements to a preset: after moving the
pin ([wrapper](wrapper.md#upgrading)), `./harnessw preset apply node --dry-run` shows what the new release would add,
and `./harnessw preset apply node` adds it, as a diff to review.

Applying again also adds back an entry the repository deleted, because nothing records that it was removed on purpose.
Read the diff before confirming.

### After applying

Nothing records which presets a repository applied: not the manifest, not a comment in the file. Each entry's one
source of truth is the file it is in, and `harness` holds no record it could later use to change those entries by
itself ([ADR 0013](../adrs/0013-organization-content.md)). Change or delete an applied entry by editing the file, then
run `./harnessw render`.

No command removes a preset: its entries are the repository's like any other, and leave the same way, by editing the
file. Both removal modes of [`harness remove`](remove.md) delete the seeded `.harness/` files whatever they hold, so
applied entries go with them.

## Reference

```text
harness preset list
harness preset show <name>
harness preset apply <name>... [--yes] [--dry-run]
```

`preset` follows the `harness <noun> <verb>` shape. `apply` has no inverse, because the entries it writes become the
repository's own and are removed like any other entry.

### `harness preset list`

Prints the presets of the running release, each with the stack it fits, its marker files, and whether those files are
present in this repository. It reads only the marker files and works in a repository that is not scaffolded yet, so it
also helps choose `scaffold --preset`.

| Exit code | Meaning |
| --- | --- |
| 0 | Listed |

Files read: the marker files of each stack. Files written: nothing.

### `harness preset show`

Prints every entry the preset holds, as the YAML it adds, grouped by the file it goes into. It does not compare the
entries with the repository; `apply --dry-run` shows what applying would add here.

| Exit code | Meaning |
| --- | --- |
| 0 | Printed |
| 1 | Unknown preset name; the valid names are listed |

Files read: nothing. Files written: nothing.

### `harness preset apply`

Applies one or more presets, in the order given ([How apply works](#how-apply-works)).

| Flag | Default | Meaning |
| --- | --- | --- |
| `<name>...` | required | One or more presets: `go`, `node`, `terraform`, `python` |
| `--yes` | off | Apply without asking; required without a terminal |
| `--dry-run` | off | Print the additions and the kept items; write nothing |

Global flags `-C <dir>`, `--json` and `--verbose` also apply; see [CLI reference](cli.md). With `--json`, the output
lists the entries added and the items kept, each with its file and key.

| Exit code | Meaning |
| --- | --- |
| 0 | Applied, or nothing to add; `--dry-run` printed |
| 1 | Error: an unknown preset, a repository that is not scaffolded, an invalid repository file. Nothing is written |
| 3 | Confirmation needed and there is no terminal: rerun with `--yes`; or the repository files use a newer schema than the running release supports. Nothing is written |

Files read: `.harness/config.yaml`, `.harness/hooks.yaml`, and everything [`render`](render.md#reference) reads.
Files written: `.harness/config.yaml` and `.harness/hooks.yaml`, additions only; then every file `render` writes
([what render writes](render.md#what-render-writes)).

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

### Choose a preset before scaffolding

```text
$ cd ~/src/acme-web
$ harness preset list
preset     stack       marker files                       in this repository
go         Go          go.mod                             no
node       Node.js     package.json                       yes
terraform  Terraform   *.tf                               no
python     Python      pyproject.toml, requirements.txt   yes
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
delivered through its own `deliver` actions; a repository that already holds every entry is reported as unchanged
([fleet](fleet.md)). The command runs without a terminal in each repository, so it needs `--yes`.

## Guidelines

- Do read the diff `apply` shows before confirming: once written, the entries are the repository's own and nothing
  marks where they came from.
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
| `preset apply` exits 1 naming `harness scaffold` | The repository has no `.harness/config.yaml` | Run `harness scaffold`, which applies the detected stacks' presets |
| `preset apply` or `preset show` exits 1: unknown preset | The name is not a preset of the running release | `./harnessw preset list` prints the names |
| `preset apply` exits 3 | No terminal to confirm, and no `--yes` | Review with `--dry-run`, then rerun with `--yes`; under `fleet`, always pass `--yes` |
| An entry you deleted is back after `preset apply` | `apply` adds every entry the files lack, and nothing records that you removed it | Delete it again, and read the diff before confirming the next apply |
| `preset apply` after an upgrade adds nothing | The files already hold every entry of the preset, or equal ones | Expected |
| Only one stack's lint command is in the profile | `commands.lint` is one named item: the first preset applied wrote it, and the next one's was reported as kept | Write a lint command that covers both stacks, such as a mise task that dispatches on the file's extension |
| Entries of the wrong stack after `scaffold` | Marker files of another stack are present, such as a `package.json` for tooling in a Go repository | Delete the entries that do not apply from `.harness/config.yaml` and `.harness/hooks.yaml`, then `./harnessw render`; on the next repository, `harness scaffold --preset <name>` |
