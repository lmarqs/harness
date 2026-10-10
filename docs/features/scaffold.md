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

`the-cook scaffold` sets a repository up for `the-cook`. On a repository with no agent setup it seeds the repository
files from its own templates and, with `--cookbook <source>@<version>`, pins and applies the [recipes](recipes.md) of
that cookbook tag whose detect rule matches (greenfield). With `--adopt` it takes over an existing hand-written setup,
imports every permission rule and hook it finds into the repository files without losing one, and lists what needs
judgement as a to-do for an agent. Either way it writes the repository's [wrapper](wrapper.md), `cook`, pinned to the
release that runs it.

`scaffold` runs from a `the-cook` binary, because a repository has no `cook` until `scaffold` writes it
([installation](installation.md#scaffolding-a-new-repository)). Every later command runs as `./cook <command>`;
with a global install, `the-cook <command>` is equivalent.

## When to use it

| Situation | Command |
| --- | --- |
| No agent permissions or hooks yet | `the-cook scaffold --cookbook github:acme/cookbook@v1.2.0` |
| Hand-written permissions or hooks in an agent's native files, an agent instruction file with content, existing skills | `the-cook scaffold --cookbook github:acme/cookbook@v1.2.0 --adopt` |
| Only the skeleton now, recipes later | `the-cook scaffold`, then [`./cook recipe pin`](recipes.md#pinning) and `./cook recipe apply` |
| A program to wire in after scaffolding, such as a loop runner or a tracker | its recipe: `./cook recipe pin <name> --cookbook <source>@<version>`, then `./cook recipe apply <name>` |
| The repository already has `.cook/config.yaml` | Not `scaffold`: edit the files and run [`./cook render`](render.md) |
| Many repositories at once | [`the-cook fleet scaffold`](fleet.md) |

Not sure which class a repository is in? Run `the-cook scaffold --cookbook <source>@<version> --adopt --dry-run`: on
a greenfield repository it finds nothing to import and behaves like a plain scaffold.

## How it works

### Greenfield

1. **Detect.** With `--cookbook`, download the tag's `checksums.txt` and archives, verify each, and choose the
   recipes: every recipe of the tag whose detect rule matches, plus each `--recipe`
   ([recipe detection](#recipe-detection)). When a chosen recipe fills the version-control contract, ask it for the
   default branch. A `.cook/` directory that holds neither a `the-cook` profile nor a manifest belongs to another
   tool: `scaffold` stops with exit 3 and names it, before planning anything.
2. **Plan.** Build the list of files, blocks and links to write. With `--dry-run`, print it and stop.
3. **Confirm.** Show the plan and ask. `--yes` skips the question. Without a terminal and without `--yes`, nothing is
   written and the command exits 3.
4. **Seed** the repository files from the templates `scaffold` holds, once, then pin and apply the chosen recipes, as
   [`the-cook recipe pin`](recipes.md#pinning) and [`the-cook recipe apply`](recipes.md#how-apply-works) do:
   - `.cook/config.yaml`: its skeleton, with `recipes:` pinning each chosen recipe to the tag and its hash from the
     tag's `checksums.txt`, `vcs:` naming the chosen recipe that fills the version-control contract, plus the
     recipes' entries (agent declarations, commands, lanes, validators); without `--cookbook`, `recipes:` is empty
     and `vcs:` is absent;
   - `.cook/policy.yaml` with one seeded entry, `[the-cook, lifecycle, run, deliver]` under `shared.ask`, rendered for
     each spelling `the-cook` renders, so the agent's own permission prompt asks a person before an agent runs the
     repository's delivery actions. The repository owns this seeded entry and may change it
     ([policy](policy.md#the-seeded-entry), [ADR 0015](../adrs/0015-development-lifecycle.md));
   - `.cook/hooks.yaml`: its skeleton, plus the recipes' post-edit hooks and lifecycle actions;
   - `.agents/memory/` skeleton, when the `memory` module is on;
   - each template of the chosen recipes whose file is absent;
   - the chosen recipes' archives, into the per-user recipe cache.
5. **Pin** the release that runs `scaffold` in `.cook/wrapper.yaml`, an owned file: its exact version, and the
   checksums of its four platform binaries. They come from that release's checksum file, fetched over HTTPS and
   accepted only after its signature verifies against the release keys embedded in the running binary
   ([wrapper](wrapper.md#cookwrapperyaml), [safety](safety.md#the-pinned-binary)). With `--offline`, `scaffold`
   makes no network request and writes a [pending pin](wrapper.md#a-pending-pin) instead: the version and the source,
   with `checksums: pending`.
6. **Render** everything else, exactly as [`the-cook render`](render.md) does: `cook`, the renders and floor rules of
   every chosen recipe (native agent files, skill links, ignore rules), the shipped skill, the other managed blocks,
   the manifest.
7. **List the to-do**: each [contract no chosen recipe fills](#the-to-do), with the commands that need it.

After this, the seeded files belong to the repository. `scaffold` never rewrites them; `render` reads them. People and
agents edit the profile, the policy and the hooks file; the pin changes only through
[`the-cook wrapper`](wrapper.md#the-cook-wrapper).

### Recipe detection

With `--cookbook <source>@<version>`, `scaffold` reads every recipe the tag publishes and chooses each one whose detect
rule matches the repository's files, with the same rules as [`the-cook recipe apply`](recipes.md#how-apply-works). It
pins and applies the chosen recipes in one plan and one confirmation: first the recipe that fills the version-control
contract, then the harness recipes, then the others, each group in name order. The detect rules are the cookbook's data;
`scaffold` holds none of its own.

A polyglot repository gets the recipes of every detected stack. Their lanes and hooks sit side by side. For an item two
recipes name, such as `commands.lint`, the repository gets the first recipe's entry, and the other recipe's differing
entry is reported, as [`recipe apply`](recipes.md#how-apply-works) reports every difference it does not write. A
repository whose stack no recipe of the tag detects gets no stack recipe: the seeded files hold their skeleton, the
agent declarations and the seeded delivery `ask` entry only.

Four flags change the choice:

| Flag | Effect |
| --- | --- |
| `--cookbook <source>@<version>` | the cookbook tag to choose from; without it, `scaffold` pins nothing |
| `--recipe <name>` | adds a recipe of the tag to the detected set; repeatable |
| `--no-detect` | applies only the recipes named with `--recipe` |
| `--vcs <name>` | names the chosen recipe written as `vcs:`, when several fill the [version-control contract](recipes.md#the-version-control-contract) |

Two chosen recipes that fill the version-control contract without `--vcs` naming one, or a `--vcs` that names no
chosen recipe, stop `scaffold` with exit 1 before anything is written.

`recipes:` in the profile records the recipes `scaffold` pinned; their entries and templates are the repository's from
the moment they are written. A program the repository takes up later gets its recipe with
`./cook recipe pin <name> --cookbook <source>@<version>` and `./cook recipe apply <name>`.

### Without a cookbook

`the-cook scaffold` with no `--cookbook` seeds the skeleton of each repository file with an empty `recipes:` and no
`vcs:`, the seeded delivery `ask` entry, the wrapper and the pin, and renders the binary's own files. It downloads no
recipe. Its to-do lists the two contracts no recipe fills and the commands each one blocks; `./cook doctor` keeps
reporting them until `./cook recipe pin` fills them.

### Files created when absent

| File | Created with |
| --- | --- |
| `AGENTS.md` | only the managed blocks; the prose is for people and agents to write |
| An agent's own instruction file, when its harness recipe imports `AGENTS.md` through one | only the `import` block, the line the recipe names |
| `.gitignore` | only the blocks the chosen recipes render into it |

When these files exist, `scaffold` appends its blocks at the end and touches nothing else. `README.md` is never
created; when it exists, it receives the `commands` block. The [block ids](render.md#block-ids) table lists every
block and the module that owns it.

## Adopt

`the-cook scaffold --adopt` runs the greenfield steps, and first imports what the repository, and optionally its
contributors' personal settings, already configure. The import is lossless: every permission rule in the project scope
is still in effect after adoption, or adoption stops. Adoption reads only the files the chosen harness recipes name,
so `--adopt` needs `--cookbook`.

### Import scopes

`--import <scope>[,<scope>...]` chooses where adoption reads from. It takes a comma-separated set from the closed
enumeration `project`, `local`, `user`, `none`. With `--adopt` the default is `project`; without `--adopt` it is
`none`.

| Scope | Reads | Imported entries |
| --- | --- | --- |
| `project` | the committed files each chosen harness recipe's `native_rules` names for the scope, and the hooks in its native hook files | committed as the repository's own |
| `local` | the files `native_rules` names for one person's overrides inside the clone | marked for review in the to-do: they are one person's settings and become committed and shared |
| `user` | the files `native_rules` names in the person's home directory | marked for review in the to-do, for the same reason |
| `none` | nothing | nothing |

The [harness contract](recipes.md#the-harness-contract) says which files and keys hold each agent's rules in each
scope; `scaffold` holds no list of its own. Source files of every scope are read, never written. Personal settings keep
applying to the person who has them, alongside the policy.

### How each source becomes an entry

| Found | Becomes |
| --- | --- |
| A native rule in the shape the harness recipe's `native_rules` reads back as a command prefix | a prefix list under the same decision, for example `[npm, run, test]`, rendered for every agent |
| Any other native rule | a native rule string under the same decision, rendered verbatim for that agent |
| A hook in an agent's native hook file | a modelled declaration in `hooks.yaml` when it has a shape `hooks.yaml` models, otherwise a `raw` entry under its event, with its matcher, rendered as written so it keeps running without `the-cook` |
| A rule file that a harness recipe of the tag names, for an agent no chosen recipe serves | a to-do item for an agent to translate |

See [policy](policy.md#entry-forms) for the three entry forms.

Every imported entry carries a provenance comment naming its file and scope. With the
[example recipe](recipes.md#an-example-recipe) `acme-agent`:

```yaml
shared:
  allow:
    # imported from .acme/settings.json (project)
    - [npm, run, [build, lint, test]]
    # imported from .acme/settings.json (project)
    - "Fetch(domain:pkg.go.dev)"
    # imported from ~/.acme/settings.json (user); review: personal setting, now shared
    - [kubectl, get]
```

### Conflicts and invariants

| Situation | Result |
| --- | --- |
| The same entry under the same decision in several sources | one entry; its provenance comment names every source |
| The same pattern under `allow` and under `deny` | the entry is kept under `deny` only, and a to-do item asks a person to confirm |
| An entry that would weaken a [floor rule](recipes.md#the-floor) of a chosen recipe, such as allowing a force push to the default branch, or a [built-in invariant](concepts.md#built-in-invariants), such as allowing a permission-bypass argument | dropped, and reported by name in the output |

### Not policy: notes

Some settings in an agent's native files change how its permission checks behave without being permission rules,
such as a default permission mode or a sandbox setting. The harness recipe's `native_rules` lists them; adoption leaves
each one where it is and reports it as a note. Every other key of a native file is left as it is too
([render](render.md#merge-rendering-native-files)).

A native rule file the harness recipe reads but does not render stays in place and keeps applying; a note names it,
so the repository can delete it once it has reviewed the imported entries.

### The equivalence check

Before writing anything, adoption renders the new repository files and compares, per agent, the effective rule set
they produce with the rule set in effect before adoption. Every project-scope rule must still be in effect, with the
same decision. Entries dropped for weakening an invariant are reported separately and are not part of the comparison.

When a rule would be lost, adoption writes nothing and exits 2, naming the rule, its source file and the agent:

```text
equivalence: Run(gh api *) from .acme/settings.json (project) is not in effect for acme-agent after adoption
```

### What is kept as it is

| Found | Treatment |
| --- | --- |
| Keys of a native file other than those its harness recipe renders | preserved byte for byte |
| Local override files and user-level settings | never written; read only by `scaffold --import local` and `scaffold --import user` |
| A real skills directory where a harness recipe's `skills` link goes | stays where it is; the shipped skill is added as one link inside |
| A whole-directory link to `.agents/skills` | stays; the shipped skill is reached through it |
| An agent instruction file's content | untouched; the `import` block its harness recipe names is added |
| Files of a program no chosen recipe renders: a tracker's database, a loop runner's directory, a task runner's files, a package manager's scripts | never read, written or moved; a file only matches a recipe's detect rule |
| The version control system's own hooks and settings | untouched |
| A `.cook/` directory that holds neither a profile nor a manifest of `the-cook` | not adopted: `scaffold` refuses with exit 3 and names the directory; nothing is written |
| `.gitignore` entries | untouched; the blocks omit paths the repository tracks |

### The to-do

What adoption cannot resolve deterministically, it prints as a to-do. The same list appears in
[`the-cook doctor`](doctor.md) (and `the-cook doctor --json`) until each item is resolved.

| Item | Raised when | Resolved when |
| --- | --- | --- |
| Personal entry to review | an entry was imported from the `local` or `user` scope | its `review:` marker is gone from the provenance comment: the entry was kept on purpose, or removed |
| Allow/deny conflict | a pattern was both allowed and denied | its `review:` marker is gone: the deny stays, or a person removes it |
| Foreign permission dialect | a rule file of an agent no chosen harness recipe serves was found | an agent has expressed its rules in `policy.yaml` and the file is retired |
| `.agents/AGENTS.md` instead of a root `AGENTS.md` | the repository keeps instructions under `.agents/` | the content is moved to the root `AGENTS.md`, which every harness recipe's `instructions` reach |
| Unfilled contract | no pinned recipe fills the [version-control contract](recipes.md#the-version-control-contract), or no agent is declared and so no recipe fills the [harness contract](recipes.md#the-harness-contract); the item names the commands that exit 3 until one does: workspaces, `verify`, `worker`, `lifecycle run` and landed branches for the first, `worker`, `agent` actions and agent hooks for the second | a pinned recipe fills it: `./cook recipe pin <name> --cookbook <source>@<version>` |

`doctor` derives every item from the repository's current state, so an item closes as soon as its condition is gone.

Adoption also prints notes that need a decision but are not to-do items, because keeping things as they are is valid:

- An agent's own instruction file has content: instructions every agent needs belong in `AGENTS.md`, which every
  agent reaches; instructions for one agent stay in its own file.
- The settings listed under [Not policy](#not-policy-notes).

An agent finishes the adoption with the [shipped skill](skills.md#the-shipped-skill): ask it to "finish
adopting the-cook"; it reads `./cook explain adoption`, edits the repository files, and runs `./cook render --check` and
`./cook doctor` until both pass.

Importing the `local` or `user` scope publishes one person's permissions to everyone who uses the repository. Review
those entries as you would any policy change; see [safety](safety.md).

## Reference

```text
the-cook scaffold [--cookbook <source>@<version>] [--adopt] [--import <scope>[,<scope>]...] [--recipe <name>]...
                 [--no-detect] [--vcs <name>] [--offline] [--yes] [--dry-run]
```

| Flag | Default | Meaning |
| --- | --- | --- |
| `--cookbook <source>@<version>` | none | The [cookbook](recipes.md#cookbooks) tag whose recipes are detected, pinned and applied. Without it, `scaffold` pins no recipe ([without a cookbook](#without-a-cookbook)) |
| `--adopt` | off | Import an existing setup and emit a to-do |
| `--import <scope>[,<scope>]...` | `project` with `--adopt`, else `none` | Scopes to import, as a comma-separated set from the closed enumeration `project`, `local`, `user`, `none` |
| `--recipe <name>` | none | Pin and apply this recipe of the tag besides the detected ones; repeatable; needs `--cookbook` |
| `--no-detect` | off | Pin and apply only the recipes named with `--recipe` |
| `--vcs <name>` | the one chosen recipe that fills the version-control contract | The recipe written as `vcs:`, when several chosen recipes fill the [version-control contract](recipes.md#the-version-control-contract) |
| `--offline` | off | Make no network request. The pin is written [pending](wrapper.md#a-pending-pin), without checksums, and is completed later with `the-cook wrapper --version <tag>`. Cannot be combined with `--cookbook` |
| `--yes` | off | Do not ask for confirmation |
| `--dry-run` | off | Print the plan, the recipes it would pin, the generated `policy.yaml` and `hooks.yaml`, and the equivalence report; write nothing |

Global flags `-C <dir>`, `--json`, `--verbose` also apply; see [CLI reference](cli.md).

| Exit code | Meaning |
| --- | --- |
| 0 | Scaffolded or adopted. Open to-do items, unfilled contracts among them, do not change the exit code |
| 1 | Error, including a repository that already has `.cook/config.yaml`; a `--recipe` the tag does not publish, or `--recipe` without `--cookbook`; a cookbook tag that cannot be reached, or an archive that does not match its `checksums.txt`; a recipe whose schema or contract version is newer than the running release supports; several chosen recipes filling the version-control contract without `--vcs`; `--offline` with `--cookbook`, or `--adopt` without it; a checksum file of the pinned release that cannot be fetched without `--offline`, and one whose signature does not verify. Nothing is written |
| 2 | The equivalence check found a project-scope rule that would not be in effect; nothing was written |
| 3 | A person must act: confirmation needed and no terminal (rerun with `--yes`, or from a terminal), a `.cook/` directory that belongs to another tool, or the version-control recipe's program not installed |

Files read: with `--cookbook`, the tag's `checksums.txt` and archives, the files each of their detect rules names, and
the default branch the chosen version-control recipe reports; the native files, hook files and instruction files the
chosen harness recipes name, `.gitignore`, the skills directories the harness recipes link into; with `--import local`
and `--import user`, the files the harness recipes' `native_rules` name for those scopes. Files written: see
[render](render.md#what-render-writes) (`cook` included), the owned `.cook/wrapper.yaml`, plus the seeded
`.cook/config.yaml` (with `recipes:` and `vcs:`), `.cook/policy.yaml`, `.cook/hooks.yaml` and `.agents/memory/`,
each template of the recipes it applies whose file is absent, and the chosen recipes' archives under
`$COOK_HOME/recipes/`.

## Examples

### A Node API, greenfield

```sh
$ cd ~/src/acme-api
$ the-cook scaffold --cookbook github:acme/cookbook@v1.2.0 --dry-run
detected from github:acme/cookbook@v1.2.0: git, claude, node; default branch: main
would pin: git, claude, node (v1.2.0, sha256 verified against checksums.txt)
would write:
  cook  .cook/wrapper.yaml (v0.9.0)
  .cook/config.yaml  .cook/policy.yaml  .cook/hooks.yaml  .agents/memory/
  .agents/skills/the-cook/SKILL.md
  the renders of git, claude and node
  AGENTS.md (new)  README.md (block)
  .cook/manifest.json
$ the-cook scaffold --cookbook github:acme/cookbook@v1.2.0 --yes
$ git add -A && git commit -m "chore: adopt the-cook"
$ ./cook doctor
```

Then review the seeded lanes in `.cook/config.yaml` against the repository's real test commands.

### Chosen recipes only

```sh
$ cd ~/src/acme-infra
$ the-cook scaffold --cookbook github:acme/cookbook@v1.2.0 --no-detect --recipe git --recipe claude \
    --recipe terraform --yes
```

The profile pins `git`, `claude` and `terraform` under `recipes:`, writes `vcs: git`, and gets the lanes the
`terraform` recipe holds; the `commands` block in `AGENTS.md` shows `./cook verify ...`.

### No cookbook yet

```sh
$ cd ~/src/acme-tools
$ the-cook scaffold --yes
to-do:
  1. no recipe fills the version-control contract: workspaces, verify, worker, lifecycle run and landed branches
     exit 3 until one does; run ./cook recipe pin <name> --cookbook <source>@<version>
  2. no agent is declared, so no recipe fills the harness contract: worker, agent actions and agent hooks are
     unavailable; run ./cook recipe pin <name> --cookbook <source>@<version>
$ ./cook recipe pin git claude --cookbook github:acme/cookbook@v1.2.0 --yes
$ ./cook recipe apply git claude --yes
```

### Offline, completed later

```sh
$ cd ~/src/acme-api
$ the-cook scaffold --offline --yes
the-cook: pin of v0.9.0 is pending; with network access, run: the-cook wrapper --version v0.9.0
$ git add -A && git commit -m "chore: adopt the-cook"
# later, with network access:
$ the-cook wrapper --version v0.9.0       # verifies v0.9.0's signed checksum file, writes the checksums
$ git diff --stat                        # .cook/wrapper.yaml and .cook/manifest.json only
$ git commit -am "chore: complete the release pin" && git push
```

Everything else works offline at once on this machine through the global `the-cook`. Agent hooks and `./cook` start
working here once the pin is complete and the release is in the cache; on other machines they need the completed pin
([wrapper](wrapper.md#a-pending-pin)). Recipes are pinned afterwards with `./cook recipe pin`, which needs the network.

### A repository with a hand-written agent setup

The repository uses `acme-agent`, whose recipe is the [example recipe](recipes.md#an-example-recipe):

```sh
$ cd ~/src/acme-cli
$ the-cook scaffold --cookbook github:acme/cookbook@v1.2.0 --adopt --dry-run
--- .cook/policy.yaml (generated)
schema: 1
shared:
  allow:
    # imported from .acme/settings.json (project)
    - [go, [build, test, vet]]
    # imported from .acme/settings.json (project)
    - "Fetch(domain:pkg.go.dev)"
  ask:
    - [the-cook, lifecycle, run, deliver]
  deny:
    # imported from .acme/settings.json (project)
    - [goreleaser, release]
worker: { deny: [] }
--- .cook/hooks.yaml (generated)
schema: 1
raw:
  AfterTool: [{ matcher: edit|write, run: ./scripts/format-edited.sh }]
  TurnEnd: [{ run: ./scripts/notify.sh }]
--- equivalence
acme-agent: 14 of 14 project rules in effect
dropped: Run(git push --force origin main) weakens a floor rule of the git recipe
note: permissions.mode in .acme/settings.json is not policy; left in place
$ the-cook scaffold --cookbook github:acme/cookbook@v1.2.0 --adopt --yes
note: ACME.md has content; instructions every agent needs belong in AGENTS.md
$ ./cook doctor
```

Then ask an agent to finish the adoption with the shipped skill.

### Importing a person's settings too

```sh
$ the-cook scaffold --cookbook github:acme/cookbook@v1.2.0 --adopt --import project,local --yes
to-do:
  1. review [docker, compose, up] imported from .acme/settings.local.json (local): now shared with everyone
```

## Guidelines

- Do run `--dry-run` first on any repository you did not create, and read the equivalence report.
- Do commit the scaffold as its own commit, so later changes to the repository files are reviewable on their own.
- Do complete a pending pin before you push: until then, no machine without the release in its cache can run
  `./cook`.
- Do review the seeded lanes and commands: recipes guess from the stack, while the repository knows its real commands.
- Do give the to-do to an agent with the shipped skill; it is written for that.
- Do import `local` or `user` only when those settings are meant for everyone, and review each entry they bring.
- Do read the recipes of a cookbook tag with `the-cook recipe show <name> --cookbook <source>@<version>` before
  scaffolding from it: the confirmation is the review of every pin `scaffold` writes.
- Don't run `scaffold` again to "refresh" a repository. Edit the repository files and run `./cook render`. To add a
  program, run `./cook recipe pin` and `./cook recipe apply <name>`. To move to another release, run `./cook wrapper`.
- Don't hand-edit rendered files to finish an adoption; the next render overwrites them and `render --check` fails.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| `.cook/config.yaml already exists` | The repository is already scaffolded | Use `./cook render`; to start over, `./cook remove` first |
| Exit 1: the checksum file of the pinned release cannot be fetched | No network access to the release source, which the pin's checksums come from | Rerun with network access, or with `--offline` and complete the [pending pin](wrapper.md#a-pending-pin) before you push |
| Exit 1: the checksum file's signature does not verify | The file is not signed by a release key the running binary trusts | Do not work around it; obtain `the-cook` again from the project's releases and report it ([safety](safety.md#the-pinned-binary)) |
| Exit 3 in a script | No terminal to confirm | Pass `--yes` |
| Exit 3 naming `.cook/` | The directory holds neither a profile nor a manifest of `the-cook`, so another program created it | `the-cook` never writes into a directory it did not create; the repository cannot be adopted while the directory is there ([greenfield](#greenfield)) |
| Exit 2 naming a rule | The equivalence check found a project rule the new files would not keep in effect; nothing was written | Keep the repository unadopted and report the rule to the publisher of the agent's harness recipe, with the rule and the agent version ([ADR 0016](../adrs/0016-permission-adoption.md)) |
| Wrong stack detected | Marker files of another stack are present (for example a `package.json` for tooling in a repository of another stack) | Before scaffolding, pass `--no-detect` with a `--recipe` for each recipe you want, after checking with `the-cook recipe list --cookbook <source>@<version>`; afterwards, delete the entries that do not apply from `.cook/config.yaml` and `.cook/hooks.yaml`, delete the recipe from `recipes:` and run `./cook render` |
| A rule allowing a force push is missing after adopt | It weakens a floor rule of the version-control recipe and was dropped | Nothing to restore; the floor rule holds while that recipe is in use |
| Exit 1: several recipes fill the version-control contract | The tag publishes more than one, and more than one was detected or named | Pass `--vcs <name>` |
| Exit 3 naming a program | The chosen version-control recipe's program is not installed | Install it, or choose the recipe of the version control system the repository uses with `--vcs` or `--no-detect` |
| The to-do lists an unfilled contract | No recipe pinned so far fills it | `./cook recipe pin <name> --cookbook <source>@<version>`, then `./cook recipe apply <name>` |

## Coexistence

`scaffold` never moves or rewrites what it did not create. See [compatibility](compatibility.md) for the full list,
including other tools' files.
