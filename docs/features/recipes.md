# Recipes

## Objective

**Problem.** A repository's agent setup touches many programs: a version control system, one or more agent harnesses,
and often a loop runner, a task tracker, a task runner and the tools of its stack. Each has its own files, commands and
arguments that are never safe, and each team picks different ones, so a setup written by hand comes out different in
every repository and loses its safety rules when it is copied. A tool with code for particular programs makes every
repository depend on choices its team did not make, and a setup shared from outside the repository leaves a reviewer
unable to tell what it runs.

**Goal.** A repository gets the setup and the safety rules of each program it uses in one reviewed change, everything
it runs can be read from its own files and the recipes it pins, and a team can describe any program without waiting
for a release of `the-cook`.

## Overview

A recipe is a named, versioned bundle of data that describes one program to the `the-cook` binary. The binary contains
code for no program and ships no recipe: everything it starts, calls or writes files for is described by a recipe the
repository pins from a [cookbook](#cookbooks) ([ADR 0022](../adrs/0022-tool-integration.md)). A recipe holds up to
five parts:

| Part | What it is | When it acts |
| --- | --- | --- |
| [Entries](#entries) | additions to the profile, the policy and the hooks file | once, when the recipe is applied; the repository's own afterwards |
| [Templates](#templates) | whole files | once, when the recipe is applied and the file is absent; the repository's own afterwards |
| [Renders](#renders) | owned files, keys and blocks, the recipe's [floor](#the-floor) rules among them | on every `the-cook render`, recorded in the manifest under the recipe's name |
| [Operations](#operations) | the commands and mappings that fill one of the binary's two [contracts](#contracts) | whenever the binary needs an answer from the program or starts it |
| [Checks](#checks) | a version command with a supported range, and a detect rule | `doctor` runs the version command; `scaffold` and `recipe list --cookbook` use the detect rule |

The profile lists the recipes a repository uses under `recipes:` ([profile](profile.md#recipes)), each pinned to a
cookbook, a version and a sha256. [`the-cook recipe pin`](#pinning) writes those entries from a cookbook tag and
renders, and `the-cook scaffold --cookbook <source>@<version>` pins every recipe of the tag whose detect rule matches.
`the-cook recipe apply <name>` then writes a pinned recipe's entries and templates once, and renders, so the additions
and their rendered effect are one diff to review. From then on the entries and templates are the repository's own,
like anything written by hand, while the renders and the floor come from the pinned recipe on every render. Deleting
an entry from `recipes:` and rendering removes that recipe's renders and leaves what it applied.

Commands are named `the-cook <command>` on this page. The examples run them through the repository's wrapper,
`./cook <command>`, from the root of a checkout; with a global install, `the-cook <command>` is equivalent
([wrapper](wrapper.md)). The examples use a cookbook published at `github:acme/cookbook`.

## When to use it

| Situation | Command |
| --- | --- |
| Set up a new repository with the recipes its files call for | `the-cook scaffold --cookbook github:acme/cookbook@v1.2.0`: it pins and applies every recipe of the tag whose detect rule matches ([scaffold](scaffold.md#recipe-detection)) |
| Set up a new repository with chosen recipes only | `the-cook scaffold --cookbook github:acme/cookbook@v1.2.0 --no-detect --recipe git --recipe claude --recipe node` |
| See which recipes a cookbook tag holds and which fit this repository | `./cook recipe list --cookbook github:acme/cookbook@v1.2.0` |
| Read everything a recipe holds before pinning it | `./cook recipe show <name> --cookbook github:acme/cookbook@v1.2.0` |
| The repository takes up another program, or gains a stack | `./cook recipe pin <name> --cookbook github:acme/cookbook@v1.2.0`, then `./cook recipe apply <name>` |
| See which recipes the repository pins, and whether this machine has them | `./cook recipe list` |
| Move a recipe to a newer tag of its cookbook | `./cook recipe pin <name> --cookbook github:acme/cookbook@v1.3.0`, then `./cook recipe apply <name> --dry-run` ([Applying again](#applying-again)) |
| Cache every pinned recipe after cloning, or in CI | `./cook recipe fetch` ([Fetching](#fetching)) |
| Describe a program no cookbook describes | write a recipe and publish it in a cookbook ([Cookbooks](#cookbooks)) |
| Stop using a program | delete its entry from `recipes:` and run `./cook render` ([After applying](#after-applying)) |
| Pin and apply a recipe in many repositories, each delivered for review | `the-cook fleet recipe pin <name> --cookbook <source>@<version> --yes --deliver`, then `the-cook fleet recipe apply <name> --yes --deliver` ([fleet](fleet.md)) |
| Bring an organization's conventions into a repository | a reviewed change to the repository's files: see [Organization choices](#organization-choices) |

### Organization choices

A recipe holds only facts that are the same in every repository that uses its program or its stack. An organization's
choices, such as the MCP tools its agents may read, the deploy tasks they must never run, a trailer its CI needs on
delivery commits, or the rules for its task tracker, differ between its repositories, so applying them takes judgement.
A person or an agent writes them into each repository's own files, typically an agent following a skill the
organization publishes, and each repository reviews the change as a diff like any other:

```sh
# install the organization's skill with the team's skills installer, then ask an agent:
#   "apply the acme agent conventions to this repository"
./cook render
git diff .cook/ AGENTS.md
```

Afterwards the entries are in the repository's files, where its people and agents read them, and the repository
renders the same bytes on every machine without fetching anything.

## Parts of a recipe

### Entries

Entries are additions to the repository's three YAML files: commands, lanes, validators, agent declarations and agent
settings in `.cook/config.yaml`; `worker.deny` and other policy entries in `.cook/policy.yaml`; post-edit hooks
and lifecycle actions in `.cook/hooks.yaml`. `apply` adds only what the files lack, following the
[merge rules](#merge-rules). From then on each entry is the repository's own: people and agents change or delete it by
editing the file, and nothing records which recipe wrote it ([ADR 0013](../adrs/0013-organization-content.md)). A newer
version's entries reach a repository only when someone applies the recipe again ([Applying again](#applying-again)).

Entries fit what a repository may reasonably change, such as a stack's lint command or a tracker's lifecycle actions. A
rule that must hold whatever the repository edits is a [floor](#the-floor) rule instead.

### Templates

A template is a whole file a recipe holds, rendered once with values from the profile, such as the agents it declares.
`apply` writes it only when the file is absent. From then on it is the repository's own file: `the-cook` never renders
it again, never overwrites it and never removes it, and [`the-cook remove`](remove.md) leaves it. When the file exists,
`apply` writes nothing to it and reports `kept <path>: exists`.

`the-cook scaffold` seeds the repository files, such as `.cook/config.yaml`, from templates of its own
([scaffold](scaffold.md#greenfield)). The manifest records those as seeded files, which `the-cook remove` deletes
([remove](remove.md#undo-and-eject-compared)); nothing records a template a recipe wrote.

A recipe's templates are those of the version `recipes:` pins. A newer version may hold more entries or templates;
they reach a repository only when someone applies the recipe again ([Applying again](#applying-again)).

### Renders

A render is content the binary owns and writes on every `the-cook render`, from the repository's declared data and the
floor rules of the recipes in use: a whole file, keys of a JSON or TOML file, entries in a list such a file shares with
others, or a [managed block](render.md#managed-blocks). The manifest records each render with the recipe's name as its
owner; `render --check` reports a hand edit to it as drift, the next render overwrites the edit, and `the-cook remove`
deletes it ([concepts](concepts.md#ownership-and-the-manifest)). A harness recipe renders its agent's native files, the
link to the shipped skill and the import of `AGENTS.md` into the agent's own instruction file, when it needs one; a
version-control recipe renders the ignore rules for the [state paths](profile.md#state-paths).

The binary renders its own content with no recipe: `cook`, the shipped skill, the blocks of `AGENTS.md` and
`README.md`, and the manifest ([what render writes](render.md#what-render-writes)). When a recipe leaves `recipes:`,
or an agent leaves `agents`, the next render removes what was rendered for it.

### Operations

Operations are what the binary asks of a program and how it starts one. They fill one of the two contracts the binary
declares ([ADR 0023](../adrs/0023-core-and-contracts.md)):

- the [version-control contract](#the-version-control-contract), filled by one recipe per repository, the one the
  profile's `vcs:` names;
- the [harness contract](#the-harness-contract), filled by one recipe per declared agent, the one
  `agents.<name>.recipe` names, by default the recipe named like the agent.

A recipe fills at most one contract, and most fill none: a loop runner's, a tracker's, a task runner's or a stack's
recipe has no operations. Every operation is a command with placeholders and a declared output kind, or a mapping the
binary reads as data. The binary never parses a program's free text: an answer is a string, a list of lines, bytes or
an exit status.

### Checks

| Check | What it is | Used by |
| --- | --- | --- |
| `version` | a command that prints the program's version, and the range of versions the recipe supports | [`doctor`](doctor.md): a version outside the range is a finding |
| `detect` | file globs whose match shows the program is in use | `scaffold --cookbook` pins and applies the recipes of the tag whose rule matches; `recipe list --cookbook` shows which match |

Both checks are optional. A recipe without a version check supports any version of its program. Whether or not a
recipe describes it, `doctor` reports a program that a recipe, a declared command or an action names and that is not
installed.

### The floor

A recipe may mark deny rules and bypass arguments as floor. Floor rules are part of the recipe's renders:

- Every floor deny rule of every recipe in use is rendered into every declared agent's native permissions on every
  render. It is never written into `.cook/policy.yaml`, and the policy cannot remove or weaken it: an entry that
  tries, such as an `allow` for a command a floor rule denies, is dropped from the output and reported by `render`
  and `doctor` ([policy](policy.md#the-floor)).
- A harness recipe's floor bypass arguments are refused by `the-cook worker` and by `agent` actions wherever they appear
  among the agent's arguments, with exit 1 naming the argument ([worker](worker.md#the-worker-contract)).

Every floor rule of a program comes from the recipe the repository pinned. A version-control recipe typically denies a
force push to the default branch, writing `{default_branch}` where the branch goes; a harness recipe marks the
arguments that would turn its agent's permission checks off; a task runner's recipe may deny a way of chaining commands
that would carry an unlisted command past every rule.

The process that applies the floor is built into the binary, because it is about the binary and not about a program:
refusing bypass arguments before a launch, rendering the deny rules into every agent's output, replaying from the base,
withholding completion, the tamper check, and its own `worker.deny` entry `[the-cook, lifecycle]`, which names the
binary's own command ([concepts](concepts.md#built-in-invariants), [ADR 0011](../adrs/0011-safety-invariants.md)).
The `policy` block of `AGENTS.md` lists each floor rule with the recipe it comes from.

A floor protects only while its recipe is in use: deleting a recipe from `recipes:` removes its floor with it. Keep
every program the repository uses pinned; `./cook recipe list --cookbook <source>@<version>` shows which recipes of a
tag match the repository's files.

Pinning a recipe is the trust decision for its floor as for everything else it runs: no release of `the-cook` vouches
for a recipe, and its floor is what its author marked ([Trust](#trust)).

## Contracts

### The version-control contract

The recipe the profile's `vcs:` names fills this contract. Everything the binary reads from or changes in the
repository's history goes through it: the base commit of a managed workspace or of a worker's attempt, the files the
tamper check restores from that base, the changes `verify` fingerprints, the branches `doctor` reports as landed, and
the default branch a floor rule names. The binary contains none of the commands that fill it; each version control
system is a recipe with the same operations.

| Operation | Placeholders | Output | Answers |
| --- | --- | --- | --- |
| `head` | | string | the commit the checkout is at |
| `root` | | string | the root of the current checkout |
| `primary_root` | | string | the root of the primary checkout, from the primary checkout or any workspace |
| `branch` | | string | the current branch |
| `default_branch` | | string | the repository's default branch, without a remote's prefix |
| `show` | `{rev}`, `{path}` | bytes | the content of `{path}` at `{rev}` |
| `clean` | | status | true when no tracked file is changed and no untracked file is present that is not ignored |
| `tracked` | `{path}` | status | true when the path is tracked |
| `changed` | `{rev}` | lines | the paths changed since `{rev}`, untracked files that are not ignored included |
| `patch` | `{rev}` | bytes | a patch from `{rev}` to the checkout's current content, new files included |
| `merge_base` | `{a}`, `{b}` | string | the merge base of `{a}` and `{b}` |
| `landed` | `{branch}`, `{target}` | lines | the commits of `{branch}` whose changes are not yet in `{target}`, compared by patch, so a cherry-pick, a rebase or a squash of the same changes counts as landed |
| `reset` | `{rev}` | status | the checkout returned to `{rev}`, untracked files removed except ignored ones, so the state directory and the `last-attempt.patch` saved there stay |
| `commit` | `{message}` | status | every change recorded as one commit with `{message}` |
| `update` | | status | the remote's branches fetched |
| `branch_create` | `{branch}`, `{rev}` | status | `{branch}` created at `{rev}` |
| `switch` | `{branch}` | status | the checkout switched to `{branch}` |
| `workspace_add` | `{path}`, `{branch}`, `{rev}` | status | a new workspace at `{path}`, on a new `{branch}` starting at `{rev}` |
| `workspace_remove` | `{path}` | status | the workspace at `{path}` removed |

| Output kind | The binary reads |
| --- | --- |
| string | the output's first line, without surrounding whitespace |
| lines | one item per non-empty line |
| bytes | the output as printed |
| status | the exit status only: 0 is true, as for `clean`, or success |

The binary finds the repository root itself, as the nearest directory upward that holds `.cook/config.yaml`, so
reading the profile never needs this contract; workspace, base and history operations do. A command that needs the
contract while no recipe in `recipes:` fills it exits 3 and names `the-cook recipe pin`. The version-control recipe's
program missing is a missing prerequisite too: the command exits 3 and names the program.

### The harness contract

One recipe per declared agent fills this contract: the recipe `agents.<name>.recipe` names, by default the recipe named
like the agent. Adding an agent means pinning a harness recipe; the policy, the hooks file and the other recipes do not
change ([ADR 0009](../adrs/0009-agent-support.md)). A harness recipe holds:

| Item | Declares |
| --- | --- |
| `launch.command` | the program the binary starts |
| `launch.unattended` | the arguments that start the agent unattended with its permission checks on |
| `launch.worker_deny` | how `worker.deny` reaches the agent: as arguments at launch, or through the rendered rules, which then require `role: worker` |
| `launch.sandbox_roots` | the arguments that open a directory beyond the checkout for writing: the clone's state directory and `agents.<name>.writable_roots` |
| `launch.model`, `launch.effort` | how an `agent` action's model and effort are passed |
| `launch.prompt` | how the binary passes the prompt of an `agent` action |
| `floor.bypass` | the arguments that would turn the agent's permission checks off, refused at every launch ([the floor](#the-floor)) |
| `payload` | the JSON paths in the agent's hook payload that give the neutral event, the session, the tool, the edited and read files, the working directory, the person's prompt text and the agent's notification message; which tools are edits and which are reads; the native event that carries each neutral event: `session-start`, `post-tool-use`, `prompt-submit`, `attention`, `turn-end` |
| `instructions` | how the agent reaches `AGENTS.md`: it reads it directly, or through an import line in a block of its own instruction file |
| `skills` | where the link to the shipped skill goes |
| `native_rules` | which files and keys hold the agent's permission rules in each [import scope](scaffold.md#import-scopes), the native rule shape that reads back as a command prefix, and the keys that change permission behaviour without being rules, which adoption reports as notes; [`scaffold --adopt`](scaffold.md#how-each-source-becomes-an-entry) imports from them |
| renders | the agent's native files, keys and entries, rendered from the neutral model ([renders](#renders)) |
| checks | the agent versions the recipe supports |

A neutral event the agent has no native event for gets nothing rendered, and `doctor` notes the gap
([hooks](hooks.md#rendering)). An agent declaration whose recipe is not in `recipes:` fails validation with exit 1.
The [example recipe](#an-example-recipe) fills this contract for a harness named `acme-agent`; with its mapping, the
policy entry `[git, push]` becomes `Run(git push *)` in `permissions.deny`, and under `the-cook worker acme-agent` a
`worker.deny` entry `[gh]` is passed as `--deny 'Run(gh *)'`.

## Cookbooks

A cookbook is a published collection of recipes at one source: an HTTPS base URL laid out like the
[release source](wrapper.md#release-source), or `github:<owner>/<repo>`, shorthand for
`https://github.com/<owner>/<repo>/releases`. `the-cook` holds no default cookbook and knows none by name; any number
of cookbooks exist, and a repository may pin recipes from several.

Each tag of a cookbook publishes, under `<source>/download/<version>/`:

| File | Holds |
| --- | --- |
| `<name>.tar.gz`, one per recipe | a gzip-compressed tar of the [recipe directory](#the-recipe-file): `recipe.yaml` at its root and `files/` beside it |
| `checksums.txt` | the sha256 of every archive of the tag, one `<sha256>  <name>.tar.gz` line each |

Every recipe of a tag carries the tag as its `version`. A repository pins from a tag with
[`the-cook recipe pin`](#pinning), which takes the hashes from that tag's `checksums.txt`; `scaffold --cookbook` does
the same for every recipe it picks.

To write a recipe, create its directory following [the recipe file](#the-recipe-file), read it back with
`the-cook recipe show`, and test it against the programs and versions its checks name: a recipe is data, and the
cookbook that publishes it owns those tests. To publish, tag the cookbook, upload one archive per recipe and the
tag's `checksums.txt`. A new version is a new tag; an archive already published under a tag is never replaced, because
every repository that pinned it would then refuse it ([Fetching](#fetching)).

## Pinning

Every entry of `recipes:` has the same four fields:

```yaml
# .cook/config.yaml
recipes:
  - name: git
    source: github:acme/cookbook
    version: v1.2.0
    sha256: d2f0d562418c7d176d0393d8694cc6f6f569f2dff3f7ede3788a2075d3ff5c3f
```

| Field | Meaning |
| --- | --- |
| `name` | the recipe's name; it must equal the `name` in its `recipe.yaml`, and is unique within the profile |
| `source` | the [cookbook](#cookbooks) |
| `version` | the cookbook tag; it must equal the `version` in its `recipe.yaml` |
| `sha256` | the sha256 of the archive, as the tag's `checksums.txt` states it |

`the-cook recipe pin <name>... --cookbook <source>@<version>` writes these entries. It downloads the tag's
`checksums.txt` and the named archives over HTTPS, verifies each archive against it, checks each `recipe.yaml`, shows
the entries to add or move as a diff, asks, writes them, caches the archives and renders, so the declaration and its
rendered effect are one diff. Pinning a name already in `recipes:` at another version or from another cookbook moves
its pin. Two entries with the same name are a validation error, exit 1, for every command that reads the profile.

Pinning declares a recipe and renders its renders and floor; it writes no entries and no templates. `the-cook recipe
apply <name>` does that next ([How apply works](#how-apply-works)).

### Trust

Pinning trusts the cookbook's `checksums.txt` as downloaded over HTTPS at that moment. The pins then enter the
repository only through a reviewed diff, and from then on the binary verifies every archive against its pin, on
download and on every load. No release key vouches for a recipe. A recipe is data, so a reviewer can read all of it
with `the-cook recipe show <name> --cookbook <source>@<version>` before approving the pin: that review is the trust
decision for everything the recipe runs, its operations, renders and floor included.

## Fetching

The binary stores each archive in the per-user cache as `$COOK_HOME/recipes/<sha256>.tar.gz`, outside the repository,
and checks its sha256 every time it loads it: a mismatch, on download or on load, stops the command with exit 1, and
the archive is not used.

Only these commands download recipes, always over HTTPS:

| Command | Downloads |
| --- | --- |
| `the-cook recipe pin` | the tag's `checksums.txt` and the named archives |
| `the-cook recipe fetch` | every pinned recipe that is not cached |
| `the-cook recipe apply` | a pinned recipe it applies that is not cached |
| `the-cook scaffold --cookbook` | the tag's `checksums.txt` and its archives |
| `the-cook recipe list --cookbook`, `the-cook recipe show --cookbook` | the tag's `checksums.txt` and the archives they read |

Nothing else fetches. `render`, `doctor`, the hooks an agent fires, `the-cook worker` and `agent` actions read a
recipe only from the cache. When a pinned recipe is not there, `render` exits 3 naming `./cook recipe fetch`, a
worker's attempt fails with exit 3 before any agent starts, an agent hook prints one line on stderr and exits 0, and
`doctor` reports a finding. A fresh clone works offline once its recipes are cached, and a CI job runs
`./cook recipe fetch` before `render --check` or `doctor`. An unattended attempt resolves the version-control recipe
and the agent's harness recipe from the cache before anything else and uses that resolution for every read,
comparison, reset and launch of the attempt ([worker](worker.md#the-worker-contract)).

## How apply works

`the-cook recipe apply <name>...` applies one or more pinned recipes, in the order given, to a repository already set
up with `the-cook scaffold`:

1. **Check.** The repository needs `.cook/config.yaml`; without it, `apply` exits 1 and names `the-cook scaffold`.
   Every name must be in `recipes:`; a name that is not exits 1 and names `the-cook recipe pin`. A pinned recipe that
   is not cached is downloaded and verified now; a sha256 mismatch or an unreachable source exits 1. On any failure
   nothing is written.
2. **Plan** the additions: the entries for `.cook/config.yaml` (commands, lanes, validators, agent declarations and
   settings), `.cook/policy.yaml` (`worker.deny` entries) and `.cook/hooks.yaml` (post-edit hooks, lifecycle
   actions); and the templates whose file is absent, rendered with the profile. Each recipe is planned against the
   files as the recipes before it leave them, following the [merge rules](#merge-rules).
3. **Confirm.** Show the additions and every template to write as a diff, and every item kept with its difference or
   `exists`, then ask. `--yes` skips the question. Without a terminal and without `--yes`, nothing is written and the
   command exits 3. `--dry-run` prints the diff and writes nothing.
4. **Write** the additions into the files, and the templates.
5. **Render** the repository, as [`the-cook render`](render.md) does: the renders and floor of every recipe in use, so
   the additions and their rendered effect are one diff and the drift check passes on it.

When there is nothing to add, `apply` says so, writes nothing and exits 0.

### Merge rules

Applying only adds. The repository's own entries and files stay authoritative.

| Element | Rule |
| --- | --- |
| A list entry: a post-edit hook declaration, a validator, a `worker.deny` entry, a path in `agents.<name>.writable_roots` | added when the list holds no equal entry, one with the same fields and values |
| A named item: a command, a lane, a lifecycle action, an agent | added when the repository has none by that name; when it has one that differs, the repository's stays and the difference is reported: `kept lanes.unit: differs from recipe node` |
| A template | written when the file is absent; an existing file is the repository's and stays untouched, and is reported: `kept <path>: exists` |
| Everything already in the files | nothing is removed, replaced or reordered; comments, key order and blank lines are preserved |

Additions go at the end of the list or map they belong to; a section the file lacks is added at its end. Two recipes
applied together follow the same rules: when both name the same item, such as `commands.lint`, the first one's entry
is added and the second one's is reported as kept.

### Applying again

Applying is idempotent: a second `apply` of the same pinned version adds nothing, because every entry and template is
already there. Applying again is therefore the way to take a newer version's improvements to a recipe's entries and
templates. After moving the pin with `./cook recipe pin node --cookbook github:acme/cookbook@v1.3.0`,
`./cook recipe apply node --dry-run` shows what the new version would add, and `./cook recipe apply node` adds it as
a diff to review. A recipe's renders and floor need no applying: `recipe pin` renders with the new version in the
same change.

Applying again also adds back an entry or a template the repository deleted, because nothing records that it was
removed on purpose. Read the diff before confirming. A template the repository kept is never updated by applying again:
to take a newer version's, delete the file and apply, then carry over your own changes.

### After applying

`recipes:` records which recipes a repository uses, which is what their renders and floor depend on. Nothing records
which entries or templates a recipe wrote: each entry's one source of truth is the file it is in, and `the-cook` holds
no record it could later use to change those entries by itself ([ADR 0013](../adrs/0013-organization-content.md)).
Change or delete an applied entry or template by editing the file, then run `./cook render`.

To stop using a recipe, delete its entry from `recipes:` and run `./cook render`. The render removes the recipe's
renders, which the manifest records under its name, and its floor; its entries and templates stay as the repository's
own, to edit or delete like anything else. A recipe that `vcs:` or an agent's `recipe` names cannot leave `recipes:`
while it is named: the profile fails validation with exit 1. Both removal modes of [`the-cook remove`](remove.md) delete
the seeded `.cook/` files whatever they hold, so applied entries go with them; a template a recipe wrote stays.

## Reference

```text
the-cook recipe list [--cookbook <source>@<version>]
the-cook recipe show <name> [--cookbook <source>@<version>]
the-cook recipe pin <name>... --cookbook <source>@<version> [--yes] [--dry-run]
the-cook recipe apply <name>... [--yes] [--dry-run]
the-cook recipe fetch
```

`recipe` follows the `the-cook <noun> <verb>` shape. `apply` has no inverse, because the entries and templates it writes
become the repository's own; a recipe leaves `recipes:` by an edit ([After applying](#after-applying)). Global flags
`-C <dir>`, `--json` and `--verbose` apply to every subcommand; see [CLI reference](cli.md).

### `the-cook recipe list`

Without `--cookbook`, prints the recipes `recipes:` declares, each with its pinned version and source, the contract it
fills, and whether it is cached on this machine. It reads only the cache: a recipe that is not cached is listed as
`not cached`, without its contract.

With `--cookbook <source>@<version>`, prints the recipes that tag publishes, each with the contract it fills, whether
its detect rule matches in this repository, and the version `recipes:` pins it at, if any. It works in a repository
that is not scaffolded yet, so it also helps choose `scaffold --recipe`.

| Flag | Default | Meaning |
| --- | --- | --- |
| `--cookbook <source>@<version>` | none | List that cookbook tag's recipes instead of the declared ones |

| Exit code | Meaning |
| --- | --- |
| 0 | Listed |
| 1 | With `--cookbook`: the source cannot be reached, the tag has no `checksums.txt`, or an archive does not match it |

Files read: `.cook/config.yaml` when present, `$COOK_HOME/recipes/`; with `--cookbook`, the tag's `checksums.txt` and
archives and the files each detect rule names. Files written: with `--cookbook`, each verified archive into
`$COOK_HOME/recipes/`.

### `the-cook recipe show`

Prints the recipe's `recipe.yaml`, its entries as the YAML they add, grouped by the file they go into, every template
with its path and content, and every render with its target and template. Templates are rendered with the repository's
profile when there is one. Without `--cookbook` the recipe is a pinned one; with it, the recipe of that name in that
tag. It does not compare anything with the repository; `apply --dry-run` shows what applying would write here, and
`render --dry-run` what rendering would change.

| Flag | Default | Meaning |
| --- | --- | --- |
| `<name>` | required | The recipe |
| `--cookbook <source>@<version>` | none | Read the recipe from that cookbook tag instead of the pinned one |

| Exit code | Meaning |
| --- | --- |
| 0 | Printed |
| 1 | A name `recipes:` does not declare, which names `--cookbook` and `the-cook recipe pin`; a cached archive that does not match its `sha256`; with `--cookbook`, a name the tag does not publish, a source that cannot be reached, or an archive that does not match the tag's `checksums.txt` |
| 3 | A pinned recipe that is not cached: run `./cook recipe fetch` |

Files read: `.cook/config.yaml`, when present; `$COOK_HOME/recipes/<sha256>.tar.gz`; with `--cookbook`, the tag's
`checksums.txt` and the archive. Files written: with `--cookbook`, the verified archive into `$COOK_HOME/recipes/`.

### `the-cook recipe pin`

Pins one or more recipes from one cookbook tag ([Pinning](#pinning)): downloads the tag's `checksums.txt` and the named
archives over HTTPS, verifies each archive against it, checks each `recipe.yaml` (a supported schema and contract
version, the name and the version), shows the `recipes:` entries to add or move as a diff and asks, then writes them,
caches the archives and renders.

| Flag | Default | Meaning |
| --- | --- | --- |
| `<name>...` | required | One or more recipes the tag publishes |
| `--cookbook <source>@<version>` | required | The cookbook and the tag to pin from |
| `--yes` | off | Pin without asking; required without a terminal |
| `--dry-run` | off | Print the entries to add or move; write nothing |

With `--json`, the output lists each entry added or moved, with its previous version for a move.

| Exit code | Meaning |
| --- | --- |
| 0 | Pinned, moved, or already pinned at that version; `--dry-run` printed |
| 1 | Error: a repository that is not scaffolded, an invalid repository file, no network or a source that cannot be reached, a name the tag does not publish, an archive that does not match the tag's `checksums.txt`, an invalid `recipe.yaml`, one whose name or version differs from the request, or one whose schema or contract version is newer than the running release supports (the message names the release needed). Nothing is written |
| 3 | Confirmation needed and there is no terminal: rerun with `--yes`. Nothing is written |

Files read: `.cook/config.yaml`, the tag's `checksums.txt` and the named archives, and everything
[`render`](render.md#reference) reads. Files written: `$COOK_HOME/recipes/<sha256>.tar.gz` for each verified archive;
`recipes:` in `.cook/config.yaml`; then every file `render` writes ([what render writes](render.md#what-render-writes)).

### `the-cook recipe apply`

Applies one or more pinned recipes, in the order given ([How apply works](#how-apply-works)).

| Flag | Default | Meaning |
| --- | --- | --- |
| `<name>...` | required | One or more recipes `recipes:` declares |
| `--yes` | off | Apply without asking; required without a terminal |
| `--dry-run` | off | Print the additions and the kept items; write nothing |

With `--json`, the output lists the entries added, the templates written and the items kept, each with its file and,
for an entry, its key.

| Exit code | Meaning |
| --- | --- |
| 0 | Applied, or nothing to add; `--dry-run` printed |
| 1 | Error: a name `recipes:` does not declare (the message names `the-cook recipe pin`), a repository that is not scaffolded, an invalid repository file, a pinned recipe whose source cannot be reached or whose archive does not match its `sha256`. Nothing is written |
| 3 | Confirmation needed and there is no terminal: rerun with `--yes`; or the repository files use a newer schema than the running release supports. Nothing is written |

Files read: `.cook/config.yaml`, `.cook/policy.yaml`, `.cook/hooks.yaml`, whether each template's file
exists, `$COOK_HOME/recipes/`, and everything [`render`](render.md#reference) reads. Files written:
`$COOK_HOME/recipes/<sha256>.tar.gz` for a pinned recipe not cached yet; `.cook/config.yaml`,
`.cook/policy.yaml` and `.cook/hooks.yaml`, additions only; each template whose file was absent; then every file
`render` writes ([what render writes](render.md#what-render-writes)).

### `the-cook recipe fetch`

Downloads every recipe `recipes:` declares that is not in the cache, checks the archive's sha256 against the
declaration, checks its `recipe.yaml` (a supported schema and contract version, the declared name and version) and
stores it in the cache. A recipe already cached is checked and left. Run it once per machine after cloning, after the
declarations change, and in CI before `render --check` or `doctor`.

It takes no arguments.

| Exit code | Meaning |
| --- | --- |
| 0 | Every declared recipe is cached and verified, or there was nothing to fetch |
| 1 | No network, or a source that cannot be reached; an archive whose sha256 differs from the declaration (both hashes are printed); an invalid `recipe.yaml`, one whose name or version differs from the declaration, or one whose schema or contract version is newer than the running release supports. A failing recipe is not stored; the others are |

Files read: `.cook/config.yaml`, `$COOK_HOME/recipes/`. Files written: `$COOK_HOME/recipes/<sha256>.tar.gz`,
on a verified download only.

### The recipe file

A recipe is a directory:

```text
<name>/
  recipe.yaml      the recipe's description
  files/           template and render sources, when the recipe has any
```

| Field of `recipe.yaml` | Type | Required | Meaning |
| --- | --- | --- | --- |
| `schema` | integer | yes | Format version of the recipe file: `1` |
| `name` | string | yes | Lowercase letters, digits and `-`: the name `recipes:`, `vcs:` and `agents.<name>.recipe` use |
| `version` | string | yes | The cookbook tag the recipe is published under, which `recipes:` pins |
| `description` | string | no | One line, printed by `recipe list` |
| `contract.name` | `vcs` \| `harness` | with `operations` | The contract `operations` fills; `contract` is absent when the recipe has no operations |
| `contract.version` | integer | with `contract.name` | The version of that contract the operations are written for: `1` for both contracts in this release line |
| `entries` | map of file to YAML | no | [Entries](#entries), grouped by the file they go into: `.cook/config.yaml`, `.cook/policy.yaml`, `.cook/hooks.yaml`. Each value has the shape of that file |
| `templates` | list | no | [Templates](#templates): `path`, where the file is written, relative to the repository root, and `source`, a file under `files/` |
| `renders` | list | no | [Renders](#renders): `file`, the target relative to the repository root, and exactly one of `whole`, `keys`, `entries`, `block` (below) |
| `floor.deny` | list of policy entries | no | Floor deny rules, in the [policy entry forms](policy.md#entry-forms); `{default_branch}` is replaced by what the version-control recipe reports |
| `floor.bypass` | list of arguments | no | Floor bypass arguments; only with `contract.name: harness` |
| `operations` | map | with `contract` | The operations of the [version-control contract](#the-version-control-contract) or of the [harness contract](#the-harness-contract) |
| `checks.version` | map | no | `run`, a command that prints the version, and `range`, the supported versions, such as `">=2.1 <2.2"`. The first dotted number in the output is the version |
| `checks.detect` | list of globs | no | Paths relative to the repository root whose match shows the program is in use; a trailing `/` matches a directory |

The binary refuses a recipe whose `schema` or `contract.version` is newer than it supports, with exit 1 naming the
release needed, and reads older ones inside its support window ([ADR 0005](../adrs/0005-schema-compatibility.md)).
`the-cook version` prints the recipe schema and the contract versions the running release supports.

Unknown keys are an error. A command, in `checks` or in an operation, is a list of arguments run without a shell from
the root of the checkout the binary works in. A placeholder such as `{rev}` is replaced inside the argument it appears
in, so a value never splits into more arguments or reaches a shell. An operation that needs a shell names `sh` and
passes values as positional parameters:
`run: [sh, -c, 'tool diff --names "$1" && tool status --untracked', sh, "{rev}"]`.

The forms of a render:

| Form | Fields | Owns |
| --- | --- | --- |
| `whole` | `whole: true`, `template` | the whole file |
| `keys` | a map from a dotted key path to `{ each, item }`, a list with one `item` per element of the model list `each`, or to `{ value }` | those keys of a JSON or TOML file; every other key is preserved byte for byte ([render](render.md#merge-rendering-native-files)) |
| `entries` | a dotted path to a list, and `{ each, item }` | its own entries in a list the file shares with others, such as an agent's hook entries |
| `block` | a block id, and `template` | a [managed block](render.md#managed-blocks) between the `cook:<id>` markers |

A template is logic-free: `{{name}}` inserts a value of the neutral model below, `{{#name}}...{{/name}}` repeats its
text for each element of a list, or renders it once when a value is present, and `{{^name}}...{{/name}}` renders its
text when the value is empty or absent. There are no comparisons, functions or includes. A template's text is inline
in `recipe.yaml` or in a file under `files/`, named by its path.

The neutral model, the values a template reads:

| Value | Holds |
| --- | --- |
| `agents` | the declared agents, each with `name`, `role` and `recipe` |
| `agent` | in a harness recipe's renders: the agent being rendered, with `name` and `role` |
| `allow`, `ask`, `deny` | the policy entries for `agent`'s role, alternatives expanded, each with exactly one of `prefix` (with `words`, the words joined by spaces, and `argv`, the words as a quoted list), `native` or `bare` (with `string`). `deny` holds every floor rule of every recipe in use and, for `role: worker`, `worker.deny` |
| `hooks` | the neutral hook events the declarations need, each with `event` (the native event the recipe's payload mapping names), `matcher` (its edit tools, and its read tools when `when.paths` context exists, joined with `\|`) and `command` |
| `raw` | the `raw` hook entries, each with `event`, `matcher` and `run` |
| `state_dir`, `workspaces_dir` | `state.dir` and `workspaces.dir` from the profile |
| `default_branch` | the default branch the version-control recipe reports |

A hook's `command` is the [hook command](render.md#the-hook-command), which finds the wrapper through the
version-control recipe's `root` operation.

#### An example recipe

`acme-agent` is a harness published in `github:acme/cookbook`. Its recipe shows every part: an entry, a template,
renders, the harness operations, floor arguments and both checks.

```text
acme-agent/
  recipe.yaml
  files/
    agent.toml
```

```yaml
schema: 1
name: acme-agent
version: v1.2.0
description: acme-agent, a coding agent harness
contract: { name: harness, version: 1 }
checks:
  version: { run: [acme-agent, --version], range: ">=3.1 <3.2" }
  detect: [.acme/, ACME.md]
entries:
  .cook/config.yaml:
    agents:
      acme-agent: { role: interactive }
templates:
  - { path: .acme/agent.toml, source: files/agent.toml }
floor:
  bypass: [--skip-permissions, --mode=unrestricted]
operations:
  launch:
    command: [acme-agent]
    unattended: [--mode, unattended]
    worker_deny:
      flag: --deny
      item: &rule "{{#prefix}}Run({{words}} *){{/prefix}}{{#native}}{{string}}{{/native}}{{#bare}}Run({{string}}){{/bare}}"
    sandbox_roots: [--writable, "{path}"]
    model: [--model, "{model}"]
    effort: [--effort, "{effort}"]
    prompt: { stdin: true, args: [--print] }
  payload:
    event: event
    session: session.id
    tool: tool.name
    file: tool.input.path
    cwd: cwd
    prompt: prompt
    message: message
    edit_tools: [edit, write]
    read_tools: [read]
    events:
      session-start: SessionStart
      post-tool-use: AfterTool
      prompt-submit: PromptSubmit
      attention: WaitingForUser
      turn-end: TurnEnd
  instructions: { file: ACME.md, block: import, line: "@AGENTS.md" }
  skills: .acme/skills/the-cook
  native_rules:
    keys: { allow: permissions.allow, ask: permissions.ask, deny: permissions.deny }
    project: [.acme/settings.json]
    local: [.acme/settings.local.json]
    user: [~/.acme/settings.json]
    prefix: "Run({{words}} *)"
    notes: [permissions.mode]
renders:
  - file: .acme/settings.json
    keys:
      permissions.allow: { each: allow, item: *rule }
      permissions.ask: { each: ask, item: *rule }
      permissions.deny: { each: deny, item: *rule }
  - file: .gitignore
    block: acme-agent
    template: |
      /.acme/cache/
```

`files/agent.toml`, written once when `.acme/agent.toml` is absent:

```toml
# acme-agent settings for this repository; the repository's own file
{{#agents}}
[agents.{{name}}]
role = "{{role}}"
{{/agents}}
```

In a repository that pins `acme-agent`, `render` writes the policy into the three `permissions` keys of
`.acme/settings.json`, the import line into the `import` block of `ACME.md`, the skill link and the `.gitignore`
block; `the-cook worker acme-agent` refuses `--skip-permissions` wherever it appears among the agent's arguments; and
`doctor` reports an `acme-agent` outside 3.1. `scaffold --cookbook github:acme/cookbook@v1.2.0` picks the recipe when
it finds `.acme/` or `ACME.md`.

## Examples

### Choose recipes before scaffolding

```text
$ cd ~/src/acme-web
$ the-cook recipe list --cookbook github:acme/cookbook@v1.2.0
recipe      contract  detected  pinned
acme-agent  harness   yes       -
git         vcs       yes       -
node        -         yes       -
python      -         yes       -
tracker     -         no        -
$ the-cook scaffold --cookbook github:acme/cookbook@v1.2.0 --no-detect \
    --recipe git --recipe acme-agent --recipe node --dry-run
```

`requirements.txt` here only pins a documentation tool, so the scaffold takes `node` and leaves `python` out.

### Pin and apply a recipe

The repository was scaffolded with `--no-detect`, and its people already wrote their own `build` command:

```text
$ ./cook recipe show node --cookbook github:acme/cookbook@v1.2.0
$ ./cook recipe pin node --cookbook github:acme/cookbook@v1.2.0
.cook/config.yaml
+  - name: node
+    source: github:acme/cookbook
+    version: v1.2.0
+    sha256: a2bce70041251fb8f672f9f5819969951627fdd3868497a17b525ab90a0c01d8
pin these recipes? [y/N] y
pinned node v1.2.0 from github:acme/cookbook: sha256 verified; rendered AGENTS.md
$ ./cook recipe apply node
.cook/config.yaml
+  lint: npm run lint -- {file}
+  unit: { select: npm test -- {pattern}, full: npm test }
.cook/hooks.yaml
+  - { paths: ["src/**/*.{ts,js}"], run: npx eslint --fix {file} }
kept commands.build: differs from recipe node
apply these additions? [y/N] y
applied node: 3 entries added, 1 kept; rendered AGENTS.md
$ git add -A && git commit -m "chore: pin and apply the node recipe"
```

Each teammate runs `./cook recipe fetch` once on their machine. Until then their `render` exits 3 naming it, their
agent hooks skip with one line, and nothing is fetched behind their back.

### Move a pin

```sh
./cook recipe pin git acme-agent node --cookbook github:acme/cookbook@v1.3.0   # moves three pins, renders with v1.3.0
./cook recipe apply git acme-agent node --dry-run                              # the entries v1.3.0 adds that the files lack
./cook recipe apply git acme-agent node --yes
git diff
```

### Stop using a program

```sh
# delete the "name: tracker" entry from recipes: in .cook/config.yaml
./cook render        # removes the tracker recipe's renders and floor
git diff             # its applied entries and templates stay, as the repository's own
```

### Many repositories

```sh
the-cook fleet recipe pin node --cookbook github:acme/cookbook@v1.2.0 --yes --deliver
the-cook fleet recipe apply node --yes --deliver
```

Each repository that lacks the pin, or some of the recipe's entries, gets a branch with the change and its rendered
effect, delivered through its own `deliver` actions. A repository that already holds them is reported as unchanged
([fleet](fleet.md)). The commands run without a terminal in each repository, so they need `--yes`.

## Guidelines

- Do read a recipe with `the-cook recipe show <name> --cookbook <source>@<version>` before pinning it: its
  operations, renders and floor run in every session and every unattended attempt, and the review of the pin is the
  only thing that vouches for them.
- Do read the diff `apply` shows before confirming: once written, the entries and templates are the repository's own
  and nothing marks where they came from.
- Do edit applied entries to fit the repository: a recipe guesses from the stack or the program, the repository knows
  its real commands, and applying again never overwrites an item you changed.
- Do run `./cook recipe apply <name> --dry-run` after moving a pin: new entries reach the repository only when someone
  applies the recipe again.
- Do commit the pins and additions together with the files `recipe pin` and `apply` rendered, so the diff explains
  itself.
- Do keep every program the repository uses pinned: a recipe's floor holds only while the recipe is in use.
- Do run `./cook recipe fetch` in CI before `render --check` and `doctor`.
- Do write an organization's choices into each repository through a reviewed change, such as an agent following the
  organization's skill: they differ between repositories, and a recipe holds only what every repository using its
  program shares ([Organization choices](#organization-choices)).
- Don't copy a floor rule into `.cook/policy.yaml` to keep it after removing its recipe: an entry there is editable,
  which is what the floor exists to avoid. Keep the recipe in use.
- Don't change a pin's `sha256` by hand to match a download: the hash is what the review approved.
- Don't apply a recipe again to restore an item you changed: `apply` keeps every item the repository names. Delete
  yours first if you want the recipe's.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| `kept lanes.unit: differs from recipe node` | The repository already has a `unit` lane, and `apply` never replaces an item the repository names | Nothing, if yours is right. To take the recipe's, delete yours and apply again |
| `kept <path>: exists` | The file is there, and `apply` writes a template only when its file is absent | Nothing, if yours is right. To take the recipe's, move yours aside, apply again, and carry over what you need |
| `recipe apply` exits 1 naming `the-cook scaffold` | The repository has no `.cook/config.yaml` | Run `the-cook scaffold` |
| `recipe apply` or `recipe show` exits 1 naming `the-cook recipe pin` | The name is not in `recipes:` | `./cook recipe pin <name> --cookbook <source>@<version>` first, or `recipe show --cookbook` to read it unpinned |
| `recipe pin` exits 1: the tag does not publish the name | The name is wrong, or the cookbook added the recipe in a later tag | `./cook recipe list --cookbook <source>@<version>` prints the tag's names |
| `recipe pin` or `recipe apply` exits 3 | No terminal to confirm, and no `--yes` | Review with `--dry-run`, then rerun with `--yes`; under `fleet`, always pass `--yes` |
| `render` or `the-cook worker` exits 3 naming `./cook recipe fetch`, a hook prints one line naming it, or `doctor` reports a recipe not cached | A pinned recipe is not in this machine's cache, and only the commands in [Fetching](#fetching) download | Run `./cook recipe fetch` |
| A command exits 3 naming `the-cook recipe pin` | It needs the version-control contract or an agent's harness contract, and no pinned recipe fills it | Pin a recipe that fills it; `./cook doctor` lists each unfilled contract and the commands it blocks |
| Exit 1: a recipe's sha256 differs | The archive at the source is not the one the profile pins: a new upload under the same version, a different `source`, or tampering | Do not change the `sha256` to match. Ask the cookbook's publisher; pin a tag whose archives match its `checksums.txt` |
| Exit 1: two `recipes:` entries with the same name | A name is unique within a profile | Delete one; `recipe pin` moves an existing pin instead of adding a second |
| Exit 1: a recipe's schema or contract version is newer than the running release supports | The cookbook tag was written for a newer `the-cook` | Move the release pin with `./cook wrapper --version <tag>` to the release the message names, or pin an older tag of the recipe |
| Exit 1: `vcs` or an agent's `recipe` names a recipe not in `recipes:` | The recipe was deleted from `recipes:`, or never pinned | Pin it, or point `vcs:` or the agent at a recipe in use |
| Exit 3 naming a program | The version-control recipe's program, or an agent's, is not installed on this machine | Install it |
| An entry or a template you deleted is back after `recipe apply` | `apply` adds every entry the files lack and writes every absent template, and nothing records that you removed them | Delete it again, and read the diff before confirming the next apply |
| `recipe apply` after moving a pin adds nothing | The files already hold every entry of the recipe, or equal ones | Expected |
| Only one stack's lint command is in the profile | `commands.lint` is one named item: the first recipe applied wrote it, and the next one's was reported as kept | Write a lint command that covers both stacks, such as a script or a task-runner task that picks the linter by the file's extension |
| Entries of the wrong stack after `scaffold` | Marker files of another stack are present, such as a `package.json` for tooling in another stack's repository | Delete the entries that do not apply from `.cook/config.yaml` and `.cook/hooks.yaml`, delete the recipe from `recipes:`, then `./cook render`; on the next repository, `the-cook scaffold --cookbook <source>@<version> --no-detect --recipe <name>` |
