# CLI reference

## Objective

**Problem.** A developer writing a script or a CI step, and a coding agent deciding what to run, need the exact form of
each command: its flags, what each exit status means and which environment variables change its behaviour. Spread
across the feature pages, these details take many pages to collect, and a misspelled flag or a misread exit status
breaks a pipeline or lets a script treat a refusal as a success.

**Goal.** A reader can look up the exact form of any invocation, what each of its exit statuses means and what in the
environment changes it, without reading the feature pages.

## Overview

Every `the-cook` command, flag, exit code and environment variable is on this page. Each command links to its feature
page for behaviour and examples. The commands serve the stages of the [development lifecycle](lifecycle.md): setting a
repository up (`scaffold`, `station`, `ingredient`, `recipe`, `fetch`, `wrapper`, `render`, `remove`), taking one
change through every stage (`make`: a [recipe](recipes.md) run is one pass of one change through the stages), starting
work (`workspace create`), implementing it (`attempt`, `hook`, `context show`), verifying it (`verify`), and moving it
through the events (`lifecycle`).

In an adopted repository every command runs through the committed [wrapper](wrapper.md), as `./cook <command>`,
which runs the release the repository pins; with a global install, `the-cook <command>` is equivalent. Synopses on this
page use `the-cook`.

## Synopsis

```text
the-cook scaffold [--cookbook <source>@<version>] [--station <name>]... [--no-detect] [--adopt]
                 [--import <scope>[,<scope>]...] [--offline] [--yes] [--dry-run]
the-cook remove [--module <name>]... [--force] [--dry-run]
the-cook remove --eject [--force] [--dry-run]
the-cook render [--check] [--dry-run]
the-cook station list [--cookbook <source>@<version>]
the-cook station show <name> [--cookbook <source>@<version>]
the-cook station apply <name>... [--cookbook <source>@<version>] [--yes] [--dry-run]
the-cook ingredient list [--cookbook <source>@<version>]
the-cook ingredient show <name> [--cookbook <source>@<version>]
the-cook ingredient pin <name>... --cookbook <source>@<version> [--yes] [--dry-run]
the-cook recipe list [--cookbook <source>@<version>]
the-cook recipe show <name> [--cookbook <source>@<version>]
the-cook recipe add <name>... --cookbook <source>@<version> [--yes] [--dry-run]
the-cook recipe update <name> --cookbook <source>@<version> [--yes] [--dry-run]
the-cook recipe create <name> [--dry-run]
the-cook fetch
the-cook wrapper [--version <tag> | --latest] [--yes] [--dry-run]
the-cook doctor [--json]
the-cook explain <topic>
the-cook context show [--role interactive|unattended] [--path <file>]... [--prompt <text>] [--json]
the-cook hook <session-start|post-tool-use|prompt-submit|attention|turn-end>
the-cook lifecycle list [--json]
the-cook lifecycle run <event> [--action <name>]... [--scope task|run|change] [--task <id>] [--dry-run]
the-cook workspace create <name> [--base <ref>] [--task <id>] [--no-bootstrap]
the-cook workspace remove <name>
the-cook verify [--lane <name>=<pattern>]... [--skip <name>=<reason>]... [--full] [--base <ref>] [--check]
the-cook attempt <worker> [--commit] [--withhold <text>] [-- <worker args>...]
the-cook make <recipe> [--<input> <value>]... [--task <id>] [--here] [--unattended] [--commit] [--withhold <text>]
              [--dry-run] [-- <worker args>...]
the-cook make <recipe> --help
the-cook make --continue [<workspace>] [--dry-run]
the-cook make --status [<workspace>]
the-cook fleet <command> [command args...] [--repos <file>] [--deliver] [--continue-on-error]
the-cook version [--check-update]
```

## The wrapper

```text
./cook [--no-download] <command> [args...]
```

`cook` resolves the release pinned in `.cook/wrapper.yaml`, downloads and verifies it when it is not cached, and
execs `the-cook` with the remaining arguments. `--no-download`, which must come first, runs only a cached release. When
none is cached, it prints one line naming `./cook` on stderr, and for `hook session-start` also one line of session
context on stdout, then exits 0. Rendered worker hooks use it.

| Exit code | Meaning |
| --- | --- |
| any | the exit code of `the-cook`, which the wrapper execs |
| 0 | `--no-download`, and the pinned release is not cached; nothing ran |
| 1 | the wrapper could not run `the-cook`: invalid `wrapper.yaml`, unsupported platform, a failed download, a checksum mismatch |
| 3 | a person must act first: install `curl` or `wget`, or `sha256sum` or `shasum`; or complete a [pending pin](wrapper.md#a-pending-pin) whose release is not cached |

A global `the-cook` defers to the pin: inside a repository whose `wrapper.yaml` pins another release, it execs that
release, downloading it through the same verified path only when the command was typed, never from a hook. See
[wrapper](wrapper.md#a-global-install-defers-to-the-pin).

## Global flags

| Flag | Applies to | Meaning |
| --- | --- | --- |
| `-C <dir>` | every command | Run as if started in `<dir>` |
| `--json` | commands whose output is data: `doctor`, `context show`, `lifecycle list`, `station list`, `station apply` (pins added and moved, entries added, recipes added, items kept), `ingredient list`, `ingredient pin` (pins added and moved), `recipe list`, `make --status` (the run record), `fleet`, `scaffold` (the to-do), `version` | Print one JSON document instead of text |
| `--dry-run` | commands that change files or run actions: `scaffold`, `remove`, `render`, `wrapper`, `station apply`, `ingredient pin`, `recipe add`, `recipe update`, `recipe create` (the skeleton), `make` (the workspace and every step, with its inputs substituted), `make --continue` (the steps left to run), `workspace`, `lifecycle run`, `fleet` (passed to each repository's command; no branch, commit or delivery) | Print what would change or run; change nothing |
| `--yes` | commands that ask for confirmation: `scaffold`, `remove` (with or without `--eject`), `render` and `wrapper` (schema migration), `station apply`, `ingredient pin`, `recipe add`, `recipe update`, `fleet` (passed through) | Answer yes; required without a terminal |
| `--verbose` | every command | Print each command `the-cook` runs and why |
| `--help`, `-h` | every command and command group; `make <recipe> --help` also lists the recipe's inputs, each with its type and description | Print the usage and exit 0; run nothing |

A recipe's inputs are flags of `make`, so an input may not take the name of a flag `make` accepts, global flags
included: `continue`, `status`, `task`, `here`, `unattended`, `commit`, `withhold`, `dry-run`, `yes`, `help`, `json` or
`verbose` ([inputs](recipes.md#inputs)).

Commands never prompt without a terminal: where confirmation is needed and `--yes` is absent, they stop with exit 3.
`the-cook lifecycle run` never asks for confirmation. The gate for workers is the policy
([the seeded entry](policy.md#the-seeded-entry)), and inside an [attempt](attempts.md#the-attempt-contract), started by
`the-cook attempt` or by an `attempt` action, the binary refuses it whatever the policy says.

## Exit codes

| Code | Meaning | Typical causes |
| --- | --- | --- |
| 0 | Success | |
| 1 | Error | invalid flags or files, a failed external command, a failed download or checksum check, a release whose signed checksum file does not verify; an [ingredient, station or recipe archive](ingredients.md#fetching) that does not match its `sha256`, its tag's `checksums.txt`, the sha256 its station lists or its recipe's `origin`, or whose schema or contract version is newer than the running release supports; two `ingredients:` entries, or two `stations:` entries, with the same name; `vcs:` or `workers.<name>.ingredient` naming an ingredient that `ingredients:` does not pin, or one that does not fill the contract; `station apply` or `station show` without `--cookbook` naming a station `stations:` does not record; an invalid [recipe](recipes.md#the-recipe-file), one with an input named like a flag of `make`, or one holding a conflict marker `recipe update` left; a run's input missing, unknown or of the wrong type, or a value beginning with `-`; `make --here` with `--unattended`; `recipe add` naming a recipe the repository already has |
| 2 | A check or an action failed | `render --check` drift; a `doctor` finding; `verify` lint, validator, build or lane failure; `verify --check` without a valid [receipt](verify.md); a lane failing at a recipe run's Verify or in its replay; a failing post-edit hook; the adoption equivalence check; a failing action in `lifecycle run` or `fleet --deliver` |
| 3 | Blocked: a person must act | a confirmation without a terminal; a prerequisite a person must install, such as a program an ingredient, a declared command or an action names, the version-control ingredient's program, `curl` or `wget` for the wrapper, or the program `the-cook attempt` starts; a contract no pinned ingredient fills, for a command that needs it (`the-cook station apply` or `the-cook ingredient pin` fills it); a pinned ingredient missing from the cache at `render` or an attempt (`./cook fetch` caches it); uncommitted changes where committed state is required; a locked workspace; a repository schema newer than the running release; a lifecycle guard refusing an event, such as `completed` with scope `task` without a valid [receipt](verify.md); an owned path edited by hand before `remove`; a `.cook/` directory another tool owns, at `scaffold`; a contract, role or lane a recipe requires that the repository lacks, at `recipe add`, `recipe update`, `station apply` or `make`; a recipe run stopped by a step, paused or stopped at a handoff, or stopped at Verify by a lane the lane rules demand and nobody selected; conflicts `recipe update` left marked for a person |

A command run without a terminal that exits 3 fires the [`blocked`](lifecycle.md#events) lifecycle event for the
scope of its work, with the refusal in `COOK_REASON`, so a declared notification reaches the person who must act.
A command a worker runs inside an attempt (`COOK_ATTEMPT=1`), or that a recipe's `run` step starts (`COOK_RUN`), fires
nothing. `the-cook make` goes by `--unattended`, which its caller passes when nobody drives the run, and never by the
terminal: a recipe run that stops fires `blocked` once, attended or not; a handoff pauses an attended run and fires
nothing; and an exit 3 before the run starts fires `blocked` only with `--unattended`
([attended and unattended runs](recipes.md#attended-and-unattended-runs)).

### Commands that fire lifecycle events

Some commands fire a [lifecycle event](lifecycle.md) when they succeed, which runs the actions the repository declares
with `auto: true`. The command's own work stands whatever the actions do: a written receipt stays written, an
attempt's commit stays committed. A failing automatic event never changes the exit code of the command that fired it.
It is reported on stderr and recorded in `.work/lifecycle.jsonl` in the primary checkout, and
`the-cook lifecycle run <event>` retries it. When the work is not concluded (uncommitted changes, the default branch,
or no valid receipt when the verify module is enabled), the automatic `deliver` does not fire, and the command says
which condition is unmet.

| Command | Fires |
| --- | --- |
| `workspace create` | `start`: scope `task` with `--task`, otherwise `change` |
| `verify` (never with `--check`) | `verified` |
| `attempt` | after a verified attempt in a workspace bound to a task: `completed` (scope `task`), then `deliver` (scope `task`) when the work is concluded; both run in the `the-cook attempt` process, never inside the worker |
| `make` | `start` when it creates the workspace (scope `task` with `--task`, otherwise `change`); `verified` at Verify; after a passing replay in a run with a task, `completed` (scope `task`), then `deliver` (scope `task`) when the work is concluded; `blocked` when it stops, for the scope of its work, never when a handoff pauses it |
| `workspace remove` | `landed` once per recorded branch whose patches are on the default branch, before its workspace is removed |
| `fleet --deliver` | `deliver` (scope `change`) in each repository with a committed change, after `the-cook verify`; every declared `deliver` action for scope `change` runs |
| any other command run without a terminal that exits 3, and `make --unattended` when it exits 3 before the run starts | `blocked`, for the scope of the command's work, otherwise `change` |

No command fires an event of scope `run`, `the-cook make` included: that scope is a loop runner's whole run, and a
recipe run's events have scope `task` or `change`. When a loop runner skips a task or stops a run, whoever drives the
run fires `blocked` or `completed` with `the-cook lifecycle run` ([lifecycle](lifecycle.md)).

`the-cook doctor` never fires events or runs actions: it lists landed branches and prints the command that fires
`landed` for each.

## Commands

| Command | Does | Exit codes | Page |
| --- | --- | --- | --- |
| `scaffold` | Set up a repository, `cook` and `wrapper.yaml` included. With `--cookbook <source>@<version>`, apply the [stations](stations.md) of that tag whose detect rule matches, and those `--station` names (`--no-detect` applies only those named), pinning the ingredients they list and copying the recipes they add; without it, seed empty `ingredients:` and `stations:` and list each unfilled contract in the to-do. Pin the running release after verifying its signed checksum file, or, with `--offline`, write a [pending pin](wrapper.md#a-pending-pin) and make no network request; `--adopt` imports an existing setup, `--import` chooses the scopes | 0 set up; 1 error, already scaffolded, a cookbook tag unreachable or an archive not matching its `checksums.txt` or its station, the release's checksum file unreachable without `--offline`, or not verified; 2 the equivalence check lost a rule; 3 confirmation needed, a `.cook/` directory another tool owns, or the version-control ingredient's program missing | [scaffold](scaffold.md) |
| `remove` | Undo the adoption, or one module, using the manifest; `--eject` stops managing and keeps the result | 0 removed or ejected; 1 error; 3 an owned path changed by hand, or confirmation needed | [remove](remove.md) |
| `render` | Render native files, `cook`, the skill, blocks; `--check` for drift | 0 rendered or no drift; 1 invalid file; 2 drift; 3 newer schema, migration needs confirmation, or a pinned ingredient not cached | [render](render.md) |
| `station list` | List the stations `stations:` records, each with its pin and whether it is cached; with `--cookbook <source>@<version>`, list that tag's stations, each with whether its detect rule matches this repository, the ingredients it lists, the recipes it adds and the version `stations:` records it at | 0 listed; 1 with `--cookbook`, a tag unreachable or not matching its `checksums.txt` | [stations](stations.md#the-cook-station-list) |
| `station show <name>` | Print every part a station holds: the ingredients it pins, its stages, entries as YAML grouped by file, templates with the path each is written to, the recipes it adds; a recorded station, or with `--cookbook` the station of that tag | 0 printed; 1 an unrecorded name without `--cookbook`, a name the tag does not publish, an unreachable source, a hash mismatch | [stations](stations.md#the-cook-station-show) |
| `station apply <name>...` | With `--cookbook`, download the tag's `checksums.txt` and the named stations and verify them; without it, take each station at the version `stations:` records. Fetch the ingredients they list and the recipes they add, and verify each against the sha256 its station lists, then plan the station pins, the ingredient pins, the stages, the entries the repository's three files lack, each template whose file is absent and a copy of each recipe the stations add whose directory is absent; show one diff and confirm; write; render. Keep and report every item and pin the repository already holds, every template file that exists and every recipe directory that exists | 0 applied, or nothing to add; 1 not scaffolded, invalid repository file, an unrecorded name without `--cookbook`, a name the tag does not publish, a hash mismatch, an unreachable source, an invalid or too new `station.yaml`, `ingredient.yaml` or `recipe.yaml`, invalid stages; 3 confirmation needed without a terminal, newer schema, or a requirement of a recipe it adds that the plan leaves unmet | [stations](stations.md#the-cook-station-apply) |
| `ingredient list` | List the ingredients `ingredients:` pins, each with its pin, the contract it fills, what names it and whether it is cached; with `--cookbook <source>@<version>`, list that tag's ingredients, each with the contract it fills, whether its detect rule matches this repository and the version `ingredients:` pins it at | 0 listed; 1 with `--cookbook`, a tag unreachable or not matching its `checksums.txt` | [ingredients](ingredients.md#the-cook-ingredient-list) |
| `ingredient show <name>` | Print every part an ingredient holds: operations, renders with their targets and templates, floor rules and checks; a pinned ingredient, or with `--cookbook` the ingredient of that tag | 0 printed; 1 an unpinned name without `--cookbook`, a name the tag does not publish, a hash mismatch; 3 a pinned ingredient not cached | [ingredients](ingredients.md#the-cook-ingredient-show) |
| `ingredient pin <name>... --cookbook <source>@<version>` | Download the tag's `checksums.txt` and the named ingredients over HTTPS, verify each archive against it, show the `ingredients:` entries to add or move as a diff and confirm, write them, cache the archives, then render; a newer version moves a pin. Writes nothing else into the profile | 0 pinned, moved or already pinned; 1 not scaffolded, unreachable source, a name the tag does not publish, a hash mismatch, an invalid or too new `ingredient.yaml`, a moved pin that no longer fills a contract the profile needs; 3 confirmation needed without a terminal | [ingredients](ingredients.md#the-cook-ingredient-pin) |
| `recipe list` | List the recipes in `.cook/recipes/`, each with its origin, or `own`, and whether the profile meets its `requires`; with `--cookbook <source>@<version>`, list that tag's recipes and the version `origin` records for each the repository has | 0 listed; 1 with `--cookbook`, a tag unreachable or not matching its `checksums.txt` | [recipes](recipes.md#the-cook-recipe-list) |
| `recipe show <name>` | Print a recipe's `recipe.yaml` and every file under its `files/`; one in `.cook/recipes/`, or with `--cookbook` the recipe of that tag | 0 printed; 1 a name the repository does not hold without `--cookbook`, a name the tag does not publish, an unreachable source, a hash mismatch | [recipes](recipes.md#the-cook-recipe-show) |
| `recipe add <name>... --cookbook <source>@<version>` | Download the tag's `checksums.txt` and the named recipes, verify them, check each one's `requires` against the profile, then copy each into `.cook/recipes/<name>/` with `origin` written; show one diff and confirm. Renders nothing | 0 added; 1 not scaffolded, a recipe directory that exists (the message names `recipe update`), an unreachable source, a name the tag does not publish, a hash mismatch, an invalid or too new `recipe.yaml`; 3 a requirement the repository lacks, or confirmation needed without a terminal | [recipes](recipes.md#the-cook-recipe-add) |
| `recipe update <name> --cookbook <source>@<version>` | Merge a newer version into an added recipe three ways, from the version its `origin` names, cached by sha256; show the diff and confirm; write the result and move `origin` to the newer version. Conflicts are written with their markers for a person or a worker, and the recipe cannot run until they are resolved; nothing else is left to do | 0 updated and `origin` moved, or already at that version; 1 no such recipe, one without `origin`, an unreachable source, a hash mismatch, an invalid or too new `recipe.yaml`; 3 conflicts left marked, a requirement the repository lacks, or confirmation needed without a terminal | [recipes](recipes.md#the-cook-recipe-update) |
| `recipe create <name>` | Write a skeleton recipe for the repository's own use into `.cook/recipes/<name>/recipe.yaml`; `--dry-run` prints it | 0 written or printed; 1 not scaffolded, an invalid name, a recipe directory that exists | [recipes](recipes.md#the-cook-recipe-create) |
| `fetch` | Download every ingredient `ingredients:` pins, every station `stations:` records and the version every added recipe came from, that is not cached, verify each against its pin or its `origin` and cache it in the user's cache directory; run after cloning and in CI before `render --check` or `doctor` | 0 cached; 1 a hash mismatch, an invalid or too new `ingredient.yaml`, `station.yaml` or `recipe.yaml`, no network | [ingredients](ingredients.md#the-cook-fetch) |
| `wrapper` | Pin a release in `.cook/wrapper.yaml` (`--version`, `--latest`): verify the release's signed checksum file with the running release's embedded keys, then download and verify this platform's binary, then have the new release render the repository: `cook`, `wrapper.yaml`, every file it renders differently, and a migration of older-schema files after confirmation. Without a flag, print the pin, whether it is cached and the newest release, and write nothing | 0 pinned, a pending pin completed, already pinned, or status printed; 1 unknown tag, no network, a signature that does not verify, missing checksums, checksum mismatch, invalid `wrapper.yaml`; 3 a migration needs confirmation without a terminal; nothing is written unless every step passes | [wrapper](wrapper.md#the-cook-wrapper) |
| `doctor` | Read-only health checks: each contract no pinned ingredient fills, with the commands unavailable because of it, the version check of every pinned ingredient, ingredients missing from the cache or not matching their pin, each recipe's file, requirements and cached origin, the wrapper, the release signature and the cached release, the adoption to-do, notes, landed branches with the command that fires `landed` | 0 no findings; 1 cannot run; 2 a finding or an open to-do item | [doctor](doctor.md) |
| `explain <topic>` | Built-in documentation. Topics: `layout`, `profile`, `policy`, `hooks`, `lifecycle`, `context`, `adoption`, `verify`, `attempt`, `invariants`, `blocks`, `ingredients`, `stations`, `recipes`, `wrapper`, `safety`, `pitfalls` | 0 printed; 1 unknown topic | [explain](explain.md) |
| `context show` | Preview the standing context a session would receive for a role, paths and a prompt, and why | 0 printed; 1 invalid `context` declarations, a missing skill or file, an unknown role | [context](context.md#the-cook-context-show) |
| `hook <event>` | Handle a worker event read from stdin, a payload the worker's [ingredient](ingredients.md#the-worker-contract) maps to a neutral event: run post-edit commands and validators, add session context, inject standing context, run `attention` and `turn_end` hooks. Called by rendered hook entries through `cook --no-download`, with the entry's event as argument | 0 nothing to do, passed, or skipped because an ingredient it needs is not cached (one stderr line names `./cook fetch`); 2 a post-edit command or validator failed | [hooks](hooks.md) |
| `lifecycle list` | List events, their actions, `auto` flags and scopes | 0 listed; 1 invalid hooks file | [lifecycle](lifecycle.md) |
| `lifecycle run` | Run an event's actions by hand, or as a coordinating worker, `blocked` included; never asks for confirmation | 0 every action passed, or none declared; 1 error; 2 an action failed; 3 a guard refused (`completed` with scope `task` without a valid receipt while the verify module is on, `deliver` refused, any event inside an attempt) | [lifecycle](lifecycle.md) |
| `workspace create` | Create a managed task workspace; `--task` records a free-form task id that every event fired in it carries; fires `start` | 0 created; 1 error | [workspaces](workspaces.md) |
| `workspace remove` | Remove a clean managed workspace, keeping its branch; fires `landed` | 0 removed; 1 error; 3 uncommitted changes or a locked workspace | [workspaces](workspaces.md) |
| `verify` | Run checks and write the receipt; `--check` validates it; fires `verified` | 0 passed, or valid receipt; 1 lane rule not met, files changed during verification; 2 a check failed, or no valid receipt | [verify](verify.md) |
| `attempt <worker>` | Run one attempt of a declared worker on one task in the current checkout, under the attempt contract: resolve the version-control ingredient and the worker's ingredient from the cache, refuse the bypass arguments the worker's ingredient marks as floor, restore and then compare the tool-owned files, replay the lanes of the receipt the worker wrote; with `--commit`, commit a verified attempt; remove every occurrence of the `--withhold` text from a failed attempt's output. The first argument it does not recognise and every argument after it, or everything after `--`, goes to the worker; `COOK_TASK` reaches the worker when the checkout is a managed workspace created with `--task`. The command a loop runner's configuration names for each task; fires `completed` and `deliver` after a verified attempt in a workspace bound to a task | 0 verified; 1 refused (uncommitted changes, a bypass argument) or a failed attempt; 3 the worker's program or the version-control ingredient's program is missing, an ingredient the attempt needs is not cached, or no pinned ingredient fills a contract it needs | [attempts](attempts.md#the-attempt-contract) |
| `make <recipe>` | Take one change through the stages with a [recipe](recipes.md): validate the inputs and the recipe's `requires`; Start, a managed workspace on `work/<name>` from the default branch, or the current one with `--here`; read the recipe once, from the new workspace's base commit or with `--here` from the working tree, and keep it in the run record; Implement, the steps in order (`run`, `template`, `attempt`, `handoff`), printing each as it runs; Verify, every lane the change selects and every lane the recipe requires, run by the binary, a required lane the change selects nothing for in full; Complete, the replay from the base, a commit with `--commit`, and `completed` for a task. `--unattended` marks a run nobody drives, where a handoff stops the run as blocked instead of pausing it; `--continue` resumes a paused or stopped run with the recipe content its record holds; `--status` prints the run record; `--help` lists the recipe's inputs. The command a loop runner may name for each task, with `--unattended` and the caller-protocol arguments of `attempt` | 0 completed; 1 an error before anything is created (an unknown or invalid recipe, an input missing or invalid, a workspace or branch that exists, `--here` with `--unattended`); 2 a lane failed at Verify or in the replay; 3 a missing requirement or prerequisite, a step that stopped the run, a handoff, a lane Verify cannot select | [recipes](recipes.md#the-cook-make) |
| `fleet` | Run a command across a list of repositories; `--deliver` commits and delivers. `fleet wrapper --version <tag> --deliver` moves every repository's pin | the highest per-repository code; a refused dirty repository counts as 3, a failing `deliver` action as 2, a `deliver` guard refusal as 3 | [fleet](fleet.md) |
| `version` | Print the version, the supported repository schema, ingredient, station and recipe schemas and contract versions; `--check-update` checks for a newer release now | 0 printed; 1 error | [installation](installation.md#update-notice) |

## Files read and written

Paths under `.work/` are shown at their defaults; the profile moves them with `state.dir` and `workspaces.dir`
([profile](profile.md#state-paths)).

| Command | Reads | Writes |
| --- | --- | --- |
| `cook` | `.cook/wrapper.yaml`, `$COOK_HOME/versions/<version>/the-cook` | `$COOK_HOME/versions/<version>/the-cook`, on a verified download only |
| `scaffold` | with `--cookbook`, the tag's `checksums.txt` and station archives, the files each detect rule names, the ingredients the chosen stations list and the recipes they add; what the version-control ingredient reports, the native files and instruction files the chosen worker ingredients name, `.gitignore`; with `--import`, the permission and hook files of each chosen scope; the running release's checksum file and its signature | the three repository files, `.cook/wrapper.yaml`, `.agents/memory/` skeleton, `ingredients:`, `stations:`, `vcs:` and `workers:` in the profile, each template of the stations it applies, `.cook/recipes/<name>/` for each recipe they add, the user's cookbook cache, everything `render` writes (`cook` included) |
| `remove` | the manifest and the paths it lists; `.cook/policy.yaml` and `.cook/hooks.yaml` for the provenance of imported entries | deletes owned paths, `cook`, every file the manifest records in `.cook/`, `.cook/recipes/`, each recipe listed in the plan first, and `.cook/` once nothing else is in it (an unrecorded file is listed and left); on undo, the memory skeleton files still matching their seeded hash; rewrites native files and block hosts (undo restores imported settings; `--eject` keeps rendered content and strips markers); `.cook/config.yaml` with `--module` |
| `render` | the three repository files, `.cook/wrapper.yaml`, the manifest, native files, block hosts, `.agents/memory/`, the files and skills of `static` context entries, the ingredients `ingredients:` pins, from the cache, what the version-control ingredient reports | `cook`, `.agents/skills/the-cook/`, the renders of every pinned ingredient (owned files, keys and entries in native files, skill links, instruction-file imports, `.gitignore` blocks), blocks in `AGENTS.md` and `README.md`, the manifest; the `.agents/memory/` skeleton, once, the first time it renders with the memory module on |
| `station list` | the profile's `stations:`, the user's cookbook cache; with `--cookbook`, the tag's `checksums.txt` and archives and the files each detect rule names | with `--cookbook`, the user's cookbook cache |
| `station show` | the station, from the cache or its source; with `--cookbook`, from the tag | each verified archive it downloads, into the user's cookbook cache |
| `station apply` | the three repository files, the path of each template, `.cook/recipes/`, with `--cookbook` the tag's `checksums.txt` and the named stations, the ingredients they list, the recipes they add that the repository lacks, everything `render` reads | the three repository files (pins and additions only), each template whose file is absent, `.cook/recipes/<name>/` for each recipe whose directory is absent, the user's cookbook cache for each archive not cached yet, then everything `render` writes |
| `ingredient list` | the profile's `ingredients:`, the user's cookbook cache; with `--cookbook`, the tag's `checksums.txt` and archives and the files each detect rule names | with `--cookbook`, the user's cookbook cache |
| `ingredient show` | the ingredient, from the cache; with `--cookbook`, from the tag | with `--cookbook`, the user's cookbook cache |
| `ingredient pin` | the profile, the tag's `checksums.txt` and the named archives, everything `render` reads | the user's cookbook cache, `ingredients:` in the profile, then everything `render` writes |
| `recipe list` | `.cook/recipes/`, the profile; with `--cookbook`, the tag's `checksums.txt` and archives | with `--cookbook`, the user's cookbook cache |
| `recipe show` | `.cook/recipes/<name>/`; with `--cookbook`, the tag's `checksums.txt` and the archive | with `--cookbook`, the user's cookbook cache |
| `recipe add` | the profile, `.cook/recipes/`, the tag's `checksums.txt` and the named archives | `.cook/recipes/<name>/` for each recipe, with `origin` written; the user's cookbook cache |
| `recipe update` | the profile, `.cook/recipes/<name>/`, the version its `origin` names, from the cache or its source, the tag's `checksums.txt` and the archive | `.cook/recipes/<name>/`, merged, with conflicts marked and `origin` moved; the user's cookbook cache |
| `recipe create` | the profile, `.cook/recipes/` | `.cook/recipes/<name>/recipe.yaml`, except with `--dry-run` |
| `fetch` | the profile's `ingredients:` and `stations:`, the `origin` of each recipe in `.cook/recipes/`, the user's cookbook cache, each pin's `source` | the user's cookbook cache |
| `wrapper` | `.cook/wrapper.yaml`, the manifest, the target release's checksum file and its signature, everything `render` reads | `$COOK_HOME/versions/<version>/the-cook`, then `.cook/wrapper.yaml`, the manifest and every file `render` writes that the new release renders differently (`cook` included), and migrated repository files, only after every verification passed |
| `doctor` | everything `render` reads, `cook`, `$COOK_HOME`, `PATH`, the version command of every pinned ingredient, the user's cookbook cache, `.cook/recipes/`, `.agents/skills/`, `.work/` session records, `.work/lifecycle.jsonl`, online the pinned release's signed checksum file | `$COOK_HOME` (update and signature checks) only |
| `explain` | nothing | nothing |
| `context show` | the profile, the skills and files `context` entries name; with `--role unattended`, those files at `HEAD` | nothing |
| `hook` | stdin, the profile, the hooks file, the skills and files `context` entries name, `.work/session.json`, `.work/hook-checks.json`, `.work/context/<session-id>.json` | `.work/hook-checks.json`, `.work/context/<session-id>.json`; whatever declared commands and `attention` or `turn_end` actions write |
| `lifecycle list` | the hooks file | nothing |
| `lifecycle run` | the hooks file, `.work/session.json`, `.work/verified.json`, the version-control ingredient | `.work/lifecycle.jsonl`; whatever the actions write |
| `workspace create` | the profile, the version-control ingredient | `.work/workspaces/<name>/`, branch `work/<name>`, its `.work/session.json` (base, task, actor); `.work/lifecycle.jsonl` |
| `workspace remove` | the version-control ingredient, the hooks file | removes `.work/workspaces/<name>/`; `.work/lifecycle.jsonl`; whatever `landed` actions write; for a `landed` `attempt` action, a recorded workspace `.work/workspaces/landed-<name>-<n>` on branch `work/landed-<name>-<n>` |
| `verify` | the profile, the version-control ingredient, changed files, `.work/session.json` | `.work/verified.json`; `.work/lifecycle.jsonl` |
| `attempt` | the version-control ingredient and the worker's ingredient, from the cache; the profile, policy, hooks file and the files and skills `context` entries name, from the base commit; `.work/session.json`; the receipt `.work/verified.json` the worker wrote | the tool-owned files it restores from the base commit; in the checkout's `.work/`, the record that `bootstrap` ran and, after a failed attempt, `last-attempt-output` and `last-attempt.patch`, and the checkout returned to its base; with `--commit`, a commit on the checkout's branch; `.work/lifecycle.jsonl`; whatever the worker and the `completed` and `deliver` actions write |
| `make` | the recipe, the profile, the policy and the hooks file, from the base commit of the workspace it creates, or from the working tree with `--here`; with `--continue`, the recipe content `run.json` holds; the version-control ingredient and the workers' ingredients, from the cache; stdin, for an `attempt` step without a prompt; the receipt a step left in the workspace; `run.json` with `--continue` and `--status` | unless `--here`, what `workspace create` writes: `.work/workspaces/<name>/`, branch `work/<name>`, its session; `run.json` in the workspace's state directory, with the recipe's content and digest; whatever the steps write; the receipt `verified.json`, with the recipe, its digest and the inputs; with `--commit`, a commit on the workspace's branch; `.work/lifecycle.jsonl`; whatever the actions of the events it fires write |
| `fleet` | the fleet file | whatever the command writes; with `--deliver`, a local branch, a commit and a receipt per changed repository, `.work/lifecycle.jsonl`, and whatever its `deliver` actions write |
| `version` | `$COOK_HOME` | `$COOK_HOME` with `--check-update` |

## Environment variables

Variables that configure `cook` and `the-cook`:

| Variable | Read by | Effect |
| --- | --- | --- |
| `COOK_HOME` | `cook`, `the-cook` | The per-user cache directory, default `$XDG_CACHE_HOME/the-cook`, or `~/.cache/the-cook` when `XDG_CACHE_HOME` is unset: downloaded releases at `versions/<version>/the-cook`, ingredient, station and recipe archives at `cookbook/<sha256>.tar.gz`, the update-check result and the signature-check result of `doctor` |
| `COOK_BINARY` | `cook` | A path to a `the-cook` binary to exec instead of the pinned release; no download and no checksum check, so it bypasses the pin, and `the-cook doctor` notes it ([wrapper](wrapper.md#air-gapped-machines)) |
| `COOK_NO_UPDATE_CHECK=1` | `the-cook` | Disable the daily release check on explicit commands and in `doctor` |
| `COOK_NO_CONTEXT=1` | `the-cook hook` | Inject no [standing context](context.md) in this environment and every process started from it; `static` entries in `AGENTS.md` stay |
| `COOK_ATTEMPT=1` | `the-cook` | Set by `the-cook attempt` for the worker it starts and by `attempt` actions, never by hand: selects the `unattended` role for standing context, makes the binary refuse `the-cook lifecycle run`, keeps every command from firing events, and makes `attention` and `turn_end` hooks do nothing |
| `COOK_RUN` | `the-cook` | Set by `the-cook make` for its `run` steps, never by hand: the path of the run record. Keeps every command from firing events, and makes the binary refuse `the-cook lifecycle run` and `the-cook make` |

A recipe's [`run` steps](recipes.md#run) receive these variables, on top of the environment of the run:

| Variable | Value |
| --- | --- |
| `COOK_RECIPE` | the recipe's name |
| `COOK_RUN` | the path of the run record, `run.json` in the workspace's state directory |
| `COOK_INPUT_<NAME>` | the value of each input of the run, `<NAME>` being the input's name in upper case with each `-` as `_`, such as `COOK_INPUT_MODULE_PATH` for `module-path`; `true` or `false` for a `bool` |

Lifecycle actions receive these variables, on top of the environment of the process that fires the event. Values reach
actions only through these variables. `run` commands read them as shell variables, and `http` and `attempt` actions
expand `$NAME` in their body or prompt ([action kinds](lifecycle.md#action-kinds)). `the-cook` never substitutes a value
into a command string. A variable that does not apply is unset; see [lifecycle](lifecycle.md#environment) for when each
is set.

| Variable | Value |
| --- | --- |
| `COOK_EVENT` | the event: `start`, `verified`, `completed`, `deliver`, `landed`, `blocked` |
| `COOK_SCOPE` | the scope: `task`, `run` or `change` |
| `COOK_REPO` | the primary checkout's root |
| `COOK_WORKSPACE` | the path of the checkout the actions run in |
| `COOK_WORKSPACE_NAME` | the managed workspace's name |
| `COOK_BRANCH` | the work's branch |
| `COOK_BASE` | the work's base commit |
| `COOK_DEFAULT_BRANCH` | the default branch the version-control ingredient reports |
| `COOK_TASK` | the task id the managed workspace was created with (`workspace create --task`) |
| `COOK_ACTOR` | the managed workspace's actor, `<user>:<workspace>:<random>` |
| `COOK_RECEIPT` | the path of the receipt JSON |
| `COOK_SUMMARY` | the one-line verification summary |
| `COOK_REASON` | `blocked` only: the one-line refusal or stop reason; for a stopped recipe run, its stage, its step and why |
| `COOK_RECIPE` | the recipe whose run the work's receipt records |
| `COOK_INPUT_<NAME>` | each input of that run, `<NAME>` being the input's name in upper case with each `-` as `_` |

An `http` action reads its URL and header values from the variables its `url_env` and `headers_env` name, so secrets
stay out of committed files; `the-cook` never logs those values.

[Notification hooks](hooks.md), the `attention` and `turn_end` actions in `.cook/hooks.yaml`, receive these
variables:

| Variable | Value |
| --- | --- |
| `COOK_EVENT` | `attention` (the worker waits for a person) or `turn_end` (the worker finished a turn) |
| `COOK_WORKER` | the worker's name as the profile declares it |
| `COOK_MESSAGE` | the worker's own notification text; untrusted |
| `COOK_REPO` | the primary checkout's root |
| `COOK_WORKSPACE` | the path of the checkout the worker works in |
| `COOK_BRANCH` | that checkout's branch |

## User-level paths

| Path | Holds |
| --- | --- |
| `$COOK_HOME` (default `$XDG_CACHE_HOME/the-cook`, or `~/.cache/the-cook`) | `versions/<version>/the-cook` for each downloaded release, `cookbook/<sha256>.tar.gz` for each ingredient, station and recipe archive, the update-check and signature-check results |
| `~/.config/the-cook/fleet.yaml` | the default fleet file |
