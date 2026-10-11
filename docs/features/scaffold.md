# scaffold

## Objective

**Problem.** A developer setting a repository up to run its development lifecycle with workers, such as coding agents,
has to set up permissions, hooks, instructions and working conventions, and done by hand, each repository comes out
different. Many repositories already have such a setup, built up by hand, and moving it to a new arrangement risks
losing a rule: a coding agent gains a permission someone denied, or unattended runs stall on a prompt for a command
someone had allowed, and nobody notices until it matters. Part of what is already there needs a person's judgement to
carry over, not a mechanical copy.

**Goal.** Any repository, with or without a setup of its own, gets a working one in one reviewed change, in which every
permission rule and hook that applied before still applies, and anything that cannot be kept is named before anything is
written. Whatever needs a person's judgement is listed as work to do, not guessed.

## Overview

`the-cook scaffold` sets a repository up for `the-cook`. On a repository with no setup of its own it seeds the
repository files from its own templates and, with `--cookbook <source>@<version>`, applies the [stations](stations.md)
of that cookbook tag whose detect rule matches, or those named with `--station`, which pins the
[ingredients](ingredients.md) they list and copies the [recipes](recipes.md) they add (greenfield). With `--adopt` it
takes over an existing hand-written setup, imports every permission rule and hook it finds into the repository files
without losing one, and lists what needs judgement as a to-do for a person or a coding agent. Either way it writes the
repository's [wrapper](wrapper.md), `cook`, pinned to the release that runs it.

`scaffold` runs from a `the-cook` binary, because a repository has no `cook` until `scaffold` writes it
([installation](installation.md#scaffolding-a-new-repository)). Every later command runs as `./cook <command>`;
with a global install, `the-cook <command>` is equivalent.

## When to use it

| Situation | Command |
| --- | --- |
| No worker permissions or hooks yet | `the-cook scaffold --cookbook github:acme/cookbook@v1.2.0 --station agentic-coding` |
| Hand-written permissions or hooks in a worker's native files, an instruction file with content, existing skills | `the-cook scaffold --cookbook github:acme/cookbook@v1.2.0 --station agentic-coding --adopt` |
| Only the skeleton now, stations later | `the-cook scaffold`, then [`./cook station apply <name> --cookbook <source>@<version>`](stations.md#how-apply-works) |
| A program to wire in after scaffolding, such as a loop runner or a tracker | a station that uses it: `./cook station apply <name> --cookbook <source>@<version>`; or its ingredient alone: `./cook ingredient pin <name> --cookbook <source>@<version>` |
| The repository already has `.cook/config.yaml` | Not `scaffold`: edit the files and run [`./cook render`](render.md) |
| Many repositories at once | [`the-cook fleet scaffold`](fleet.md) |

Not sure which class a repository is in? Run `the-cook scaffold --cookbook <source>@<version> --adopt --dry-run`: on
a greenfield repository it finds nothing to import and behaves like a plain scaffold.

## How it works

### Greenfield

1. **Detect.** With `--cookbook`, download the tag's `checksums.txt` and its station archives, verify each, and choose
   the stations: every station of the tag whose detect rule matches, plus each `--station`
   ([station detection](#station-detection)). Then download every ingredient the chosen stations list and verify it
   against the sha256 its station lists, and every recipe they add, verified against the tag's `checksums.txt`. When the
   chosen stations' stages name a version-control ingredient, ask it for the default branch. A `.cook/` directory that
   holds neither a `the-cook` profile nor a manifest belongs to another tool: `scaffold` stops with exit 3 and names it,
   before planning anything.
2. **Plan.** Build the list of files, blocks and links to write. With `--dry-run`, print it and stop.
3. **Confirm.** Show the plan and ask. `--yes` skips the question. Without a terminal and without `--yes`, nothing is
   written and the command exits 3.
4. **Seed** the repository files from the templates `scaffold` holds, once, then apply the chosen stations, as
   [`the-cook station apply`](stations.md#how-apply-works) does:
   - `.cook/config.yaml`: its skeleton, with `ingredients:` pinning every ingredient the chosen stations list, at the
     sha256 each lists, `stations:` recording each chosen station at the tag and its hash from the tag's
     `checksums.txt`, `vcs:` and `workers:` from the stations' [stages](stations.md#stages), plus the stations' entries
     (commands, lanes, validators, standing context); without `--cookbook`, `ingredients:` and `stations:` are empty
     and `vcs:` and `workers:` are absent;
   - `.cook/policy.yaml` with one seeded entry, `[the-cook, lifecycle, run, deliver]` under `shared.ask`, rendered for
     each spelling `the-cook` renders, so a coding agent's own permission prompt asks a person before it runs the
     repository's delivery actions, plus the stations' policy entries, such as `unattended.deny`. The repository owns
     this seeded entry and may change it ([policy](policy.md#the-seeded-entry),
     [ADR 0015](../adrs/0015-development-lifecycle.md));
   - `.cook/hooks.yaml`: its skeleton, plus the stations' post-edit hooks and lifecycle actions;
   - `.agents/memory/` skeleton, when the `memory` module is on;
   - each template of the chosen stations whose file is absent;
   - `.cook/recipes/<name>/` for each recipe the chosen stations add, with its `origin`, once its `requires` holds in
     the seeded profile;
   - the archives of the chosen stations, of their ingredients and of their recipes, into the per-user cache.
5. **Pin** the release that runs `scaffold` in `.cook/wrapper.yaml`, an owned file: its exact version, and the
   checksums of its four platform binaries. They come from that release's checksum file, fetched over HTTPS and
   accepted only after its signature verifies against the release keys embedded in the running binary
   ([wrapper](wrapper.md#cookwrapperyaml), [safety](safety.md#the-pinned-binary)). With `--offline`, `scaffold`
   makes no network request and writes a [pending pin](wrapper.md#a-pending-pin) instead: the version and the source,
   with `checksums: pending`.
6. **Render** everything else, exactly as [`the-cook render`](render.md) does: `cook`, the renders and floor rules of
   every pinned ingredient (the workers' native files, skill links, ignore rules), the shipped skill, the other managed
   blocks, the manifest.
7. **List the to-do**: each [contract no pinned ingredient fills](#the-to-do), with the commands that need it.

After this, the seeded files belong to the repository, and so do the recipes in `.cook/recipes/`. `scaffold` never
rewrites them; `render` reads them. People and workers edit the profile, the policy and the hooks file; the pin changes
only through [`the-cook wrapper`](wrapper.md#the-cook-wrapper).

### Station detection

With `--cookbook <source>@<version>`, `scaffold` reads every station the tag publishes and chooses each one whose
[detect rule](stations.md#detect-rule) matches the repository's files, plus each one `--station` names. It applies the
chosen stations in one plan and one confirmation, with the same [merge rules](stations.md#merge-rules) as
`the-cook station apply`: first the stations `--station` names, in the order given, then the detected ones in name
order. The detect rules are the cookbook's data; `scaffold` holds none of its own.

A polyglot repository gets the stations of every detected stack. Their lanes and hooks sit side by side. For an item two
stations name, such as `commands.lint` or `vcs`, the repository gets the first station's entry, and the other station's
differing entry is reported, as [`station apply`](stations.md#how-apply-works) reports every difference it does not
write. A repository whose stack no station of the tag detects gets no stack station: the seeded files hold their
skeleton, what the other chosen stations add, and the seeded delivery `ask` entry.

Three flags change the choice:

| Flag | Effect |
| --- | --- |
| `--cookbook <source>@<version>` | the cookbook tag to choose from; without it, `scaffold` applies and pins nothing |
| `--station <name>` | adds a station of the tag to the detected set, ahead of the detected ones; repeatable |
| `--no-detect` | applies only the stations named with `--station` |

`stations:` in the profile records the stations `scaffold` applied; their entries, templates and recipes are the
repository's from the moment they are written. A program the repository takes up later comes with a station,
`./cook station apply <name> --cookbook <source>@<version>`, or on its own, `./cook ingredient pin <name> --cookbook
<source>@<version>`.

### Without a cookbook

`the-cook scaffold` with no `--cookbook` seeds the skeleton of each repository file, with empty `ingredients:` and
`stations:` and no `vcs:` or workers, the seeded delivery `ask` entry, the wrapper and the pin, and renders the
binary's own files. It downloads nothing from a cookbook. Its to-do lists the two contracts no ingredient fills and
the commands each one blocks; `./cook doctor` keeps reporting them until an applied station, or a pinned ingredient
named in the profile, fills them.

### Files created when absent

| File | Created with |
| --- | --- |
| `AGENTS.md` | only the managed blocks; the prose is for people and workers to write |
| A worker's own instruction file, when its ingredient imports `AGENTS.md` through one | only the `import` block, the line the ingredient names |
| `.gitignore` | only the blocks the pinned ingredients render into it |

When these files exist, `scaffold` appends its blocks at the end and touches nothing else. `README.md` is never
created; when it exists, it receives the `commands` block. The [block ids](render.md#block-ids) table lists every
block and the module that owns it.

## Adopt

`the-cook scaffold --adopt` runs the greenfield steps, and first imports what the repository, and optionally its
contributors' personal settings, already configure. The import is lossless: every permission rule in the project scope
is still in effect after adoption, or adoption stops. Adoption reads only the files the worker ingredients of the
chosen stations name, so `--adopt` needs `--cookbook`.

### Import scopes

`--import <scope>[,<scope>...]` chooses where adoption reads from. It takes a comma-separated set from the closed
enumeration `project`, `local`, `user`, `none`. With `--adopt` the default is `project`; without `--adopt` it is
`none`.

| Scope | Reads | Imported entries |
| --- | --- | --- |
| `project` | the committed files each chosen worker ingredient's `native_rules` names for the scope, and the hooks in its native hook files | committed as the repository's own |
| `local` | the files `native_rules` names for one person's overrides inside the clone | marked for review in the to-do: they are one person's settings and become committed and shared |
| `user` | the files `native_rules` names in the person's home directory | marked for review in the to-do, for the same reason |
| `none` | nothing | nothing |

The [worker contract](ingredients.md#the-worker-contract) says which files and keys hold each worker's rules in each
scope; `scaffold` holds no list of its own. Source files of every scope are read, never written. Personal settings
keep applying to the person who has them, alongside the policy.

### How each source becomes an entry

| Found | Becomes |
| --- | --- |
| A native rule in the shape the worker ingredient's `native_rules` reads back as a command prefix | a prefix list under the same decision, for example `[npm, run, test]`, rendered for every worker |
| Any other native rule | a native rule string under the same decision, rendered verbatim for that worker |
| A hook in a worker's native hook file | a modelled declaration in `hooks.yaml` when it has a shape `hooks.yaml` models, otherwise a `raw` entry under its event, with its matcher, rendered as written so it keeps running without `the-cook` |
| A rule file that a worker ingredient of the tag names, for a worker no chosen station declares | a to-do item for a person or a coding agent to translate |

See [policy](policy.md#entry-forms) for the three entry forms.

Every imported entry carries a provenance comment naming its file and scope. With the
[example ingredient](ingredients.md#an-example-ingredient) `acme-agent`:

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
| An entry that would weaken a [floor rule](ingredients.md#the-floor) of a pinned ingredient, such as allowing a force push to the default branch, or a [built-in invariant](concepts.md#built-in-invariants), such as allowing a permission-bypass argument | dropped, and reported by name in the output |

### Not policy: notes

Some settings in a worker's native files change how its permission checks behave without being permission rules,
such as a default permission mode or a sandbox setting. The worker ingredient's `native_rules` lists them; adoption
leaves each one where it is and reports it as a note. Every other key of a native file is left as it is too
([render](render.md#merge-rendering-native-files)).

A native rule file the worker ingredient reads but does not render stays in place and keeps applying; a note names it,
so the repository can delete it once it has reviewed the imported entries.

### The equivalence check

Before writing anything, adoption renders the new repository files and compares, per worker, the effective rule set
they produce with the rule set in effect before adoption. Every project-scope rule must still be in effect, with the
same decision. Entries dropped for weakening an invariant are reported separately and are not part of the comparison.

When a rule would be lost, adoption writes nothing and exits 2, naming the rule, its source file and the worker:

```text
equivalence: Run(acme-deploy status *) from .acme/settings.json (project) is not in effect for acme-agent after adoption
```

### What is kept as it is

| Found | Treatment |
| --- | --- |
| Keys of a native file other than those its worker ingredient renders | preserved byte for byte |
| Local override files and user-level settings | never written; read only by `scaffold --import local` and `scaffold --import user` |
| A real skills directory where a worker ingredient's `skills` link goes | stays where it is; the shipped skill is added as one link inside |
| A whole-directory link to `.agents/skills` | stays; the shipped skill is reached through it |
| A worker's instruction file's content | untouched; the `import` block its worker ingredient names is added |
| Files of a program no pinned ingredient renders: a tracker's database, a loop runner's directory, a task runner's files, a package manager's scripts | never read, written or moved; a file only matches a detect rule |
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
| Foreign permission dialect | a rule file of a worker no chosen station declares was found | a person or a coding agent has expressed its rules in `policy.yaml` and the file is retired |
| `.agents/AGENTS.md` instead of a root `AGENTS.md` | the repository keeps instructions under `.agents/` | the content is moved to the root `AGENTS.md`, which every worker ingredient's `instructions` reach |
| Unfilled contract | `vcs:` names no pinned ingredient that fills the [version-control contract](ingredients.md#the-version-control-contract), or no worker is declared and so no ingredient fills a [worker contract](ingredients.md#the-worker-contract); the item names the commands that exit 3 until one does: workspaces, `verify`, `attempt`, `lifecycle run` and landed branches for the first, `attempt`, `attempt` actions and worker hooks for the second | a station whose stages fill it is applied, `./cook station apply <name> --cookbook <source>@<version>`, or an ingredient that fills it is pinned with `./cook ingredient pin` and named in `vcs:` or a worker's `ingredient` |

`doctor` derives every item from the repository's current state, so an item closes as soon as its condition is gone.

Adoption also prints notes that need a decision but are not to-do items, because keeping things as they are is valid:

- A worker's own instruction file has content: instructions every worker needs belong in `AGENTS.md`, which every
  worker reaches; instructions for one worker stay in its own file.
- The settings listed under [Not policy](#not-policy-notes).

A coding agent finishes the adoption with the [shipped skill](skills.md#the-shipped-skill): ask it to "finish
adopting the-cook"; it reads `./cook explain adoption`, edits the repository files, and runs `./cook render --check` and
`./cook doctor` until both pass.

Importing the `local` or `user` scope publishes one person's permissions to everyone who uses the repository. Review
those entries as you would any policy change; see [safety](safety.md).

## Reference

```text
the-cook scaffold [--cookbook <source>@<version>] [--station <name>]... [--no-detect] [--adopt]
                 [--import <scope>[,<scope>]...] [--offline] [--yes] [--dry-run]
```

| Flag | Default | Meaning |
| --- | --- | --- |
| `--cookbook <source>@<version>` | none | The [cookbook](stations.md#cookbooks) tag whose stations are detected and applied, and whose ingredients they pin. Without it, `scaffold` applies no station ([without a cookbook](#without-a-cookbook)) |
| `--station <name>` | none | Apply this station of the tag besides the detected ones, ahead of them; repeatable, applied in the order given; needs `--cookbook` |
| `--no-detect` | off | Apply only the stations named with `--station` |
| `--adopt` | off | Import an existing setup and emit a to-do |
| `--import <scope>[,<scope>]...` | `project` with `--adopt`, else `none` | Scopes to import, as a comma-separated set from the closed enumeration `project`, `local`, `user`, `none` |
| `--offline` | off | Make no network request. The pin is written [pending](wrapper.md#a-pending-pin), without checksums, and is completed later with `the-cook wrapper --version <tag>`. Cannot be combined with `--cookbook` |
| `--yes` | off | Do not ask for confirmation |
| `--dry-run` | off | Print the plan, the stations it would apply, the ingredients they pin and the recipes they add, the generated `policy.yaml` and `hooks.yaml`, and the equivalence report; write nothing |

Global flags `-C <dir>`, `--json`, `--verbose` also apply; see [CLI reference](cli.md).

| Exit code | Meaning |
| --- | --- |
| 0 | Scaffolded or adopted. Open to-do items, unfilled contracts among them, do not change the exit code |
| 1 | Error, including a repository that already has `.cook/config.yaml`; a `--station` the tag does not publish, or `--station` without `--cookbook`; a cookbook tag that cannot be reached; an archive that does not match its tag's `checksums.txt` or the sha256 its station lists; a station, ingredient or recipe whose schema or contract version is newer than the running release supports; stages that name an ingredient their station does not list, or one that does not fill the contract the stage needs; `--offline` with `--cookbook`, or `--adopt` without it; a checksum file of the pinned release that cannot be fetched without `--offline`, and one whose signature does not verify. Nothing is written |
| 2 | The equivalence check found a project-scope rule that would not be in effect; nothing was written |
| 3 | A person must act: confirmation needed and no terminal (rerun with `--yes`, or from a terminal), a `.cook/` directory that belongs to another tool, the version-control ingredient's program not installed, or a contract, role or lane a recipe of the chosen stations requires that the seeded profile lacks, named. Nothing is written |

Files read: with `--cookbook`, the tag's `checksums.txt` and station archives, the files each of their detect rules
names, the ingredient archives the chosen stations list and the recipe archives they add, and the default branch the
version-control ingredient reports;
the native files, hook files and instruction files the chosen worker ingredients name, `.gitignore`, the skills
directories the worker ingredients link into; with `--import local` and `--import user`, the files the worker
ingredients' `native_rules` name for those scopes. Files written: see [render](render.md#what-render-writes) (`cook`
included), the owned `.cook/wrapper.yaml`, plus the seeded `.cook/config.yaml` (with `ingredients:`, `stations:`,
`vcs:` and `workers:`), `.cook/policy.yaml`, `.cook/hooks.yaml` and `.agents/memory/`, each template of the stations
it applies whose file is absent, `.cook/recipes/<name>/` for each recipe they add, and the archives of the chosen
stations, their ingredients and their recipes under `$COOK_HOME/cookbook/`.

## Examples

### A Node API, greenfield

```sh
$ cd ~/src/acme-api
$ the-cook scaffold --cookbook github:acme/cookbook@v1.2.0 --station agentic-coding --dry-run
named: agentic-coding; detected from github:acme/cookbook@v1.2.0: node; default branch: main
would apply: agentic-coding, node (v1.2.0, sha256 verified against checksums.txt)
would pin: git, claude, acme-agent, acme-loop, acme-tasks (v1.2.0, sha256 verified against agentic-coding)
would add recipes: implement-task (v1.2.0, sha256 verified against agentic-coding)
would write:
  cook  .cook/wrapper.yaml (v0.9.0)
  .cook/config.yaml  .cook/policy.yaml  .cook/hooks.yaml  .agents/memory/  .cook/recipes/implement-task/
  .acme-loop.toml  .agents/directives/unattended.md
  .agents/skills/the-cook/SKILL.md
  the renders of git, claude, acme-agent, acme-loop and acme-tasks
  AGENTS.md (new)  README.md (block)
  .cook/manifest.json
$ the-cook scaffold --cookbook github:acme/cookbook@v1.2.0 --station agentic-coding --yes
$ git add -A && git commit -m "chore: adopt the-cook"
$ ./cook doctor
```

Then review the seeded lanes in `.cook/config.yaml` against the repository's real test commands.

### Chosen stations only

```sh
$ cd ~/src/acme-infra
$ the-cook scaffold --cookbook github:acme/cookbook@v1.2.0 --no-detect --station agentic-coding \
    --station terraform --yes
```

The profile pins the five ingredients `agentic-coding` lists under `ingredients:`, records `agentic-coding` and
`terraform` under `stations:`, writes `vcs: git` and the two workers from the stages of `agentic-coding`, copies its
`implement-task` recipe, and gets the lanes the `terraform` station holds; the `commands` block in `AGENTS.md` shows
`./cook verify ...`.

### No cookbook yet

```sh
$ cd ~/src/acme-tools
$ the-cook scaffold --yes
to-do:
  1. no ingredient fills the version-control contract: workspaces, verify, attempt, lifecycle run and landed
     branches exit 3 until one does; run ./cook station apply <name> --cookbook <source>@<version>
  2. no worker is declared, so no ingredient fills a worker contract: attempt, attempt actions and worker hooks
     are unavailable; run ./cook station apply <name> --cookbook <source>@<version>
$ ./cook station apply agentic-coding --cookbook github:acme/cookbook@v1.2.0 --yes
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

Everything else works offline at once on this machine through the global `the-cook`. Worker hooks and `./cook` start
working here once the pin is complete and the release is in the cache; on other machines they need the completed pin
([wrapper](wrapper.md#a-pending-pin)). Stations are applied afterwards with `./cook station apply`, which needs the
network.

### A repository with a hand-written setup

The repository uses `acme-agent`, whose ingredient is the [example ingredient](ingredients.md#an-example-ingredient),
and `agentic-coding` declares a worker for it:

```sh
$ cd ~/src/acme-cli
$ the-cook scaffold --cookbook github:acme/cookbook@v1.2.0 --station agentic-coding --adopt --dry-run
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
    - [acme-release, publish]
unattended:
  deny:
    - [git, push]
    - [acme-tasks, sync]
--- .cook/hooks.yaml (generated)
schema: 1
raw:
  AfterTool: [{ matcher: edit|write, run: ./scripts/format-edited.sh }]
  TurnEnd: [{ run: ./scripts/notify.sh }]
--- equivalence
acme-agent: 14 of 14 project rules in effect
dropped: Run(git push --force origin main) weakens a floor rule of the git ingredient
note: permissions.mode in .acme/settings.json is not policy; left in place
$ the-cook scaffold --cookbook github:acme/cookbook@v1.2.0 --station agentic-coding --adopt --yes
note: ACME.md has content; instructions every worker needs belong in AGENTS.md
$ ./cook doctor
```

Then ask a coding agent to finish the adoption with the shipped skill.

### Importing a person's settings too

```sh
$ the-cook scaffold --cookbook github:acme/cookbook@v1.2.0 --station agentic-coding --adopt --import project,local --yes
to-do:
  1. review [docker, compose, up] imported from .acme/settings.local.json (local): now shared with everyone
```

## Guidelines

- Do run `--dry-run` first on any repository you did not create, and read the equivalence report.
- Do commit the scaffold as its own commit, so later changes to the repository files are reviewable on their own.
- Do complete a pending pin before you push: until then, no machine without the release in its cache can run
  `./cook`.
- Do review the seeded lanes and commands: stations guess from the stack, while the repository knows its real commands.
- Do give the to-do to a coding agent with the shipped skill; it is written for that.
- Do import `local` or `user` only when those settings are meant for everyone, and review each entry they bring.
- Do read the stations of a cookbook tag with `the-cook station show <name> --cookbook <source>@<version>`, and the
  ingredients they list with `the-cook ingredient show`, before scaffolding from it: the confirmation is the review of
  every pin `scaffold` writes.
- Don't run `scaffold` again to "refresh" a repository. Edit the repository files and run `./cook render`. To add a way
  of working or a stack, run `./cook station apply <name> --cookbook <source>@<version>`; to add a program, run
  `./cook ingredient pin`; to add a kind of change, run `./cook recipe add` or `./cook recipe create`. To move to
  another release, run `./cook wrapper`.
- Don't hand-edit rendered files to finish an adoption; the next render overwrites them and `render --check` fails.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| `.cook/config.yaml already exists` | The repository is already scaffolded | Use `./cook render`; to start over, `./cook remove` first |
| Exit 1: the checksum file of the pinned release cannot be fetched | No network access to the release source, which the pin's checksums come from | Rerun with network access, or with `--offline` and complete the [pending pin](wrapper.md#a-pending-pin) before you push |
| Exit 1: the checksum file's signature does not verify | The file is not signed by a release key the running binary trusts | Do not work around it; obtain `the-cook` again from the project's releases and report it ([safety](safety.md#the-pinned-binary)) |
| Exit 3 in a script | No terminal to confirm | Pass `--yes` |
| Exit 3 naming `.cook/` | The directory holds neither a profile nor a manifest of `the-cook`, so another program created it | `the-cook` never writes into a directory it did not create; the repository cannot be adopted while the directory is there ([greenfield](#greenfield)) |
| Exit 2 naming a rule | The equivalence check found a project rule the new files would not keep in effect; nothing was written | Keep the repository unadopted and report the rule to the publisher of the worker's ingredient, with the rule and the worker's version ([ADR 0016](../adrs/0016-permission-adoption.md)) |
| Wrong stack detected | Marker files of another stack are present (for example a `package.json` for tooling in a repository of another stack) | Before scaffolding, pass `--no-detect` with a `--station` for each station you want, after checking with `the-cook station list --cookbook <source>@<version>`; afterwards, delete the entries that do not apply from `.cook/config.yaml` and `.cook/hooks.yaml`, and the station's record from `stations:` |
| A rule allowing a force push is missing after adopt | It weakens a floor rule of the version-control ingredient and was dropped | Nothing to restore; the floor rule holds while that ingredient is pinned |
| `kept vcs: differs from station <name>` | Two chosen stations name different version-control ingredients, and the first one's was written | Nothing, if the first is right; otherwise edit `vcs:` and delete the pin of the ingredient no longer named |
| Exit 3 naming a program | The version-control ingredient's program is not installed | Install it, or choose a station whose stages name the version control system the repository uses, with `--no-detect` and `--station` |
| Exit 3 naming a contract, a role or a lane | A recipe a chosen station adds requires it, and the seeded profile lacks it | Choose stations that provide it together, such as a stack station for the lane, or apply the station after scaffolding and declare what is missing first |
| The to-do lists an unfilled contract | No ingredient pinned so far fills it | `./cook station apply <name> --cookbook <source>@<version>` with a station whose stages fill it, or `./cook ingredient pin <name> --cookbook <source>@<version>` and name it in `vcs:` or a worker |

## Coexistence

`scaffold` never moves or rewrites what it did not create. See [compatibility](compatibility.md) for the full list,
including other tools' files.
