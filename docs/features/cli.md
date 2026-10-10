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
repository up (`scaffold`, `recipe`, `ingredient`, `fetch`, `wrapper`, `render`, `remove`), starting work
(`workspace create`), implementing it (`attempt`, `hook`, `context show`), verifying it (`verify`), and moving it
through the events (`lifecycle`).

In an adopted repository every command runs through the committed [wrapper](wrapper.md), as `./cook <command>`,
which runs the release the repository pins; with a global install, `the-cook <command>` is equivalent. Synopses on this
page use `the-cook`.

## Synopsis

```text
the-cook scaffold [--cookbook <source>@<version>] [--recipe <name>]... [--no-detect] [--adopt]
                 [--import <scope>[,<scope>]...] [--offline] [--yes] [--dry-run]
the-cook remove [--module <name>]... [--force] [--dry-run]
the-cook remove --eject [--force] [--dry-run]
the-cook render [--check] [--dry-run]
the-cook recipe list [--cookbook <source>@<version>]
the-cook recipe show <name> [--cookbook <source>@<version>]
the-cook recipe apply <name>... [--cookbook <source>@<version>] [--yes] [--dry-run]
the-cook ingredient list [--cookbook <source>@<version>]
the-cook ingredient show <name> [--cookbook <source>@<version>]
the-cook ingredient pin <name>... --cookbook <source>@<version> [--yes] [--dry-run]
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
| `--json` | commands whose output is data: `doctor`, `context show`, `lifecycle list`, `recipe list`, `recipe apply` (pins added and moved, entries added, items kept), `ingredient list`, `ingredient pin` (pins added and moved), `fleet`, `scaffold` (the to-do), `version` | Print one JSON document instead of text |
| `--dry-run` | commands that change files or run actions: `scaffold`, `remove`, `render`, `wrapper`, `recipe apply`, `ingredient pin`, `workspace`, `lifecycle run`, `fleet` (passed to each repository's command; no branch, commit or delivery) | Print what would change or run; change nothing |
| `--yes` | commands that ask for confirmation: `scaffold`, `remove` (with or without `--eject`), `render` and `wrapper` (schema migration), `recipe apply`, `ingredient pin`, `fleet` (passed through) | Answer yes; required without a terminal |
| `--verbose` | every command | Print each command `the-cook` runs and why |

Commands never prompt without a terminal: where confirmation is needed and `--yes` is absent, they stop with exit 3.
`the-cook lifecycle run` never asks for confirmation. The gate for workers is the policy
([the seeded entry](policy.md#the-seeded-entry)), and inside an [attempt](attempts.md#the-attempt-contract), started by
`the-cook attempt` or by an `attempt` action, the binary refuses it whatever the policy says.

## Exit codes

| Code | Meaning | Typical causes |
| --- | --- | --- |
| 0 | Success | |
| 1 | Error | invalid flags or files, a failed external command, a failed download or checksum check, a release whose signed checksum file does not verify; an [ingredient or recipe archive](ingredients.md#fetching) that does not match its `sha256`, its tag's `checksums.txt` or the sha256 its recipe lists, or whose schema or contract version is newer than the running release supports; two `ingredients:` entries, or two `recipes:` entries, with the same name; `vcs:` or `workers.<name>.ingredient` naming an ingredient that `ingredients:` does not pin, or one that does not fill the contract; `recipe apply` or `recipe show` without `--cookbook` naming a recipe `recipes:` does not record |
| 2 | A check or an action failed | `render --check` drift; a `doctor` finding; `verify` lint, validator, build or lane failure; `verify --check` without a valid [receipt](verify.md); a failing post-edit hook; the adoption equivalence check; a failing action in `lifecycle run` or `fleet --deliver` |
| 3 | Blocked: a person must act | a confirmation without a terminal; a prerequisite a person must install, such as a program an ingredient, a declared command or an action names, the version-control ingredient's program, `curl` or `wget` for the wrapper, or the program `the-cook attempt` starts; a contract no pinned ingredient fills, for a command that needs it (`the-cook recipe apply` or `the-cook ingredient pin` fills it); a pinned ingredient missing from the cache at `render` or an attempt (`./cook fetch` caches it); uncommitted changes where committed state is required; a locked workspace; a repository schema newer than the running release; a lifecycle guard refusing an event, such as `completed` with scope `task` without a valid [receipt](verify.md); an owned path edited by hand before `remove`; a `.cook/` directory another tool owns, at `scaffold` |

A command run without a terminal that exits 3 fires the [`blocked`](lifecycle.md#events) lifecycle event for the
scope of its work, with the refusal in `COOK_REASON`, so a declared notification reaches the person who must act.
A command a worker runs inside an attempt (`COOK_ATTEMPT=1`) fires nothing.

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
| `workspace remove` | `landed` once per recorded branch whose patches are on the default branch, before its workspace is removed |
| `fleet --deliver` | `deliver` (scope `change`) in each repository with a committed change, after `the-cook verify`; every declared `deliver` action for scope `change` runs |
| any command run without a terminal that exits 3 | `blocked`, for the scope of the command's work, otherwise `change` |

No command fires an event of scope `run`. When a loop runner skips a task or stops a run, whoever drives the run fires
`blocked` or `completed` with `the-cook lifecycle run` ([lifecycle](lifecycle.md)).

`the-cook doctor` never fires events or runs actions: it lists landed branches and prints the command that fires
`landed` for each.

## Commands

| Command | Does | Exit codes | Page |
| --- | --- | --- | --- |
| `scaffold` | Set up a repository, `cook` and `wrapper.yaml` included. With `--cookbook <source>@<version>`, apply the [recipes](recipes.md) of that tag whose detect rule matches, and those `--recipe` names (`--no-detect` applies only those named), pinning the ingredients they list; without it, seed empty `ingredients:` and `recipes:` and list each unfilled contract in the to-do. Pin the running release after verifying its signed checksum file, or, with `--offline`, write a [pending pin](wrapper.md#a-pending-pin) and make no network request; `--adopt` imports an existing setup, `--import` chooses the scopes | 0 set up; 1 error, already scaffolded, a cookbook tag unreachable or an archive not matching its `checksums.txt` or its recipe, the release's checksum file unreachable without `--offline`, or not verified; 2 the equivalence check lost a rule; 3 confirmation needed, a `.cook/` directory another tool owns, or the version-control ingredient's program missing | [scaffold](scaffold.md) |
| `remove` | Undo the adoption, or one module, using the manifest; `--eject` stops managing and keeps the result | 0 removed or ejected; 1 error; 3 an owned path changed by hand, or confirmation needed | [remove](remove.md) |
| `render` | Render native files, `cook`, the skill, blocks; `--check` for drift | 0 rendered or no drift; 1 invalid file; 2 drift; 3 newer schema, migration needs confirmation, or a pinned ingredient not cached | [render](render.md) |
| `recipe list` | List the recipes `recipes:` records, each with its pin and whether it is cached; with `--cookbook <source>@<version>`, list that tag's recipes, each with whether its detect rule matches this repository, the ingredients it lists and the version `recipes:` records it at | 0 listed; 1 with `--cookbook`, a tag unreachable or not matching its `checksums.txt` | [recipes](recipes.md#the-cook-recipe-list) |
| `recipe show <name>` | Print every part a recipe holds: the ingredients it pins, its stages, entries as YAML grouped by file, templates with the path each is written to; a recorded recipe, or with `--cookbook` the recipe of that tag | 0 printed; 1 an unrecorded name without `--cookbook`, a name the tag does not publish, an unreachable source, a hash mismatch | [recipes](recipes.md#the-cook-recipe-show) |
| `recipe apply <name>...` | With `--cookbook`, download the tag's `checksums.txt` and the named recipes and verify them; without it, take each recipe at the version `recipes:` records. Fetch and verify the ingredients they list, then plan the recipe pins, the ingredient pins, the stages, the entries the repository's three files lack and each template whose file is absent; show one diff and confirm; write; render. Keep and report every item and pin the repository already holds and every template file that exists | 0 applied, or nothing to add; 1 not scaffolded, invalid repository file, an unrecorded name without `--cookbook`, a name the tag does not publish, a hash mismatch, an unreachable source, an invalid or too new `recipe.yaml` or `ingredient.yaml`, invalid stages; 3 confirmation needed without a terminal, or newer schema | [recipes](recipes.md#the-cook-recipe-apply) |
| `ingredient list` | List the ingredients `ingredients:` pins, each with its pin, the contract it fills, what names it and whether it is cached; with `--cookbook <source>@<version>`, list that tag's ingredients, each with the contract it fills, whether its detect rule matches this repository and the version `ingredients:` pins it at | 0 listed; 1 with `--cookbook`, a tag unreachable or not matching its `checksums.txt` | [ingredients](ingredients.md#the-cook-ingredient-list) |
| `ingredient show <name>` | Print every part an ingredient holds: operations, renders with their targets and templates, floor rules and checks; a pinned ingredient, or with `--cookbook` the ingredient of that tag | 0 printed; 1 an unpinned name without `--cookbook`, a name the tag does not publish, a hash mismatch; 3 a pinned ingredient not cached | [ingredients](ingredients.md#the-cook-ingredient-show) |
| `ingredient pin <name>... --cookbook <source>@<version>` | Download the tag's `checksums.txt` and the named ingredients over HTTPS, verify each archive against it, show the `ingredients:` entries to add or move as a diff and confirm, write them, cache the archives, then render; a newer version moves a pin. Writes nothing else into the profile | 0 pinned, moved or already pinned; 1 not scaffolded, unreachable source, a name the tag does not publish, a hash mismatch, an invalid or too new `ingredient.yaml`, a moved pin that no longer fills a contract the profile needs; 3 confirmation needed without a terminal | [ingredients](ingredients.md#the-cook-ingredient-pin) |
| `fetch` | Download every ingredient `ingredients:` pins and every recipe `recipes:` records that is not cached, verify each against its pin and cache it in the user's cache directory; run after cloning and in CI before `render --check` or `doctor` | 0 cached; 1 a hash mismatch, an invalid or too new `ingredient.yaml` or `recipe.yaml`, no network | [ingredients](ingredients.md#the-cook-fetch) |
| `wrapper` | Pin a release in `.cook/wrapper.yaml` (`--version`, `--latest`): verify the release's signed checksum file with the running release's embedded keys, then download and verify this platform's binary, then have the new release render the repository: `cook`, `wrapper.yaml`, every file it renders differently, and a migration of older-schema files after confirmation. Without a flag, print the pin, whether it is cached and the newest release, and write nothing | 0 pinned, a pending pin completed, already pinned, or status printed; 1 unknown tag, no network, a signature that does not verify, missing checksums, checksum mismatch, invalid `wrapper.yaml`; 3 a migration needs confirmation without a terminal; nothing is written unless every step passes | [wrapper](wrapper.md#the-cook-wrapper) |
| `doctor` | Read-only health checks: each contract no pinned ingredient fills, with the commands unavailable because of it, the version check of every pinned ingredient, ingredients missing from the cache or not matching their pin, the wrapper, the release signature and the cached release, the adoption to-do, notes, landed branches with the command that fires `landed` | 0 no findings; 1 cannot run; 2 a finding or an open to-do item | [doctor](doctor.md) |
| `explain <topic>` | Built-in documentation. Topics: `layout`, `profile`, `policy`, `hooks`, `lifecycle`, `context`, `adoption`, `verify`, `attempt`, `invariants`, `blocks`, `ingredients`, `recipes`, `wrapper`, `safety`, `pitfalls` | 0 printed; 1 unknown topic | [explain](explain.md) |
| `context show` | Preview the standing context a session would receive for a role, paths and a prompt, and why | 0 printed; 1 invalid `context` declarations, a missing skill or file, an unknown role | [context](context.md#the-cook-context-show) |
| `hook <event>` | Handle a worker event read from stdin, a payload the worker's [ingredient](ingredients.md#the-worker-contract) maps to a neutral event: run post-edit commands and validators, add session context, inject standing context, run `attention` and `turn_end` hooks. Called by rendered hook entries through `cook --no-download`, with the entry's event as argument | 0 nothing to do, passed, or skipped because an ingredient it needs is not cached (one stderr line names `./cook fetch`); 2 a post-edit command or validator failed | [hooks](hooks.md) |
| `lifecycle list` | List events, their actions, `auto` flags and scopes | 0 listed; 1 invalid hooks file | [lifecycle](lifecycle.md) |
| `lifecycle run` | Run an event's actions by hand, or as a coordinating worker, `blocked` included; never asks for confirmation | 0 every action passed, or none declared; 1 error; 2 an action failed; 3 a guard refused (`completed` with scope `task` without a valid receipt while the verify module is on, `deliver` refused, any event inside an attempt) | [lifecycle](lifecycle.md) |
| `workspace create` | Create a managed task workspace; `--task` records a free-form task id that every event fired in it carries; fires `start` | 0 created; 1 error | [workspaces](workspaces.md) |
| `workspace remove` | Remove a clean managed workspace, keeping its branch; fires `landed` | 0 removed; 1 error; 3 uncommitted changes or a locked workspace | [workspaces](workspaces.md) |
| `verify` | Run checks and write the receipt; `--check` validates it; fires `verified` | 0 passed, or valid receipt; 1 lane rule not met, files changed during verification; 2 a check failed, or no valid receipt | [verify](verify.md) |
| `attempt <worker>` | Run one attempt of a declared worker on one task in the current checkout, under the attempt contract: resolve the version-control ingredient and the worker's ingredient from the cache, refuse the bypass arguments the worker's ingredient marks as floor, restore and then compare the tool-owned files, replay the lanes of the receipt the worker wrote; with `--commit`, commit a verified attempt; remove every occurrence of the `--withhold` text from a failed attempt's output. The first argument it does not recognise and every argument after it, or everything after `--`, goes to the worker; `COOK_TASK` reaches the worker when the checkout is a managed workspace created with `--task`. The command a loop runner's configuration names for each task; fires `completed` and `deliver` after a verified attempt in a workspace bound to a task | 0 verified; 1 refused (uncommitted changes, a bypass argument) or a failed attempt; 3 the worker's program or the version-control ingredient's program is missing, an ingredient the attempt needs is not cached, or no pinned ingredient fills a contract it needs | [attempts](attempts.md#the-attempt-contract) |
| `fleet` | Run a command across a list of repositories; `--deliver` commits and delivers. `fleet wrapper --version <tag> --deliver` moves every repository's pin | the highest per-repository code; a refused dirty repository counts as 3, a failing `deliver` action as 2, a `deliver` guard refusal as 3 | [fleet](fleet.md) |
| `version` | Print the version, the supported repository schema, ingredient and recipe schemas and contract versions; `--check-update` checks for a newer release now | 0 printed; 1 error | [installation](installation.md#update-notice) |

## Files read and written

Paths under `.work/` are shown at their defaults; the profile moves them with `state.dir` and `workspaces.dir`
([profile](profile.md#state-paths)).

| Command | Reads | Writes |
| --- | --- | --- |
| `cook` | `.cook/wrapper.yaml`, `$COOK_HOME/versions/<version>/the-cook` | `$COOK_HOME/versions/<version>/the-cook`, on a verified download only |
| `scaffold` | with `--cookbook`, the tag's `checksums.txt` and recipe archives, the files each detect rule names and the ingredients the chosen recipes list; what the version-control ingredient reports, the native files and instruction files the chosen worker ingredients name, `.gitignore`; with `--import`, the permission and hook files of each chosen scope; the running release's checksum file and its signature | the three repository files, `.cook/wrapper.yaml`, `.agents/memory/` skeleton, `ingredients:`, `recipes:`, `vcs:` and `workers:` in the profile, each template of the recipes it applies, the user's cookbook cache, everything `render` writes (`cook` included) |
| `remove` | the manifest and the paths it lists; `.cook/policy.yaml` and `.cook/hooks.yaml` for the provenance of imported entries | deletes owned paths, `cook`, every file the manifest records in `.cook/`, and `.cook/` once nothing else is in it (an unrecorded file is listed and left); on undo, the memory skeleton files still matching their seeded hash; rewrites native files and block hosts (undo restores imported settings; `--eject` keeps rendered content and strips markers); `.cook/config.yaml` with `--module` |
| `render` | the three repository files, `.cook/wrapper.yaml`, the manifest, native files, block hosts, `.agents/memory/`, the files and skills of `static` context entries, the ingredients `ingredients:` pins, from the cache, what the version-control ingredient reports | `cook`, `.agents/skills/the-cook/`, the renders of every pinned ingredient (owned files, keys and entries in native files, skill links, instruction-file imports, `.gitignore` blocks), blocks in `AGENTS.md` and `README.md`, the manifest; the `.agents/memory/` skeleton, once, the first time it renders with the memory module on |
| `recipe list` | the profile's `recipes:`, the user's cookbook cache; with `--cookbook`, the tag's `checksums.txt` and archives and the files each detect rule names | with `--cookbook`, the user's cookbook cache |
| `recipe show` | the recipe, from the cache or its source; with `--cookbook`, from the tag | each verified archive it downloads, into the user's cookbook cache |
| `recipe apply` | the three repository files, the path of each template, with `--cookbook` the tag's `checksums.txt` and the named recipes, the ingredients they list, everything `render` reads | the three repository files (pins and additions only), each template whose file is absent, the user's cookbook cache for each archive not cached yet, then everything `render` writes |
| `ingredient list` | the profile's `ingredients:`, the user's cookbook cache; with `--cookbook`, the tag's `checksums.txt` and archives and the files each detect rule names | with `--cookbook`, the user's cookbook cache |
| `ingredient show` | the ingredient, from the cache; with `--cookbook`, from the tag | with `--cookbook`, the user's cookbook cache |
| `ingredient pin` | the profile, the tag's `checksums.txt` and the named archives, everything `render` reads | the user's cookbook cache, `ingredients:` in the profile, then everything `render` writes |
| `fetch` | the profile's `ingredients:` and `recipes:`, the user's cookbook cache, each pin's `source` | the user's cookbook cache |
| `wrapper` | `.cook/wrapper.yaml`, the manifest, the target release's checksum file and its signature, everything `render` reads | `$COOK_HOME/versions/<version>/the-cook`, then `.cook/wrapper.yaml`, the manifest and every file `render` writes that the new release renders differently (`cook` included), and migrated repository files, only after every verification passed |
| `doctor` | everything `render` reads, `cook`, `$COOK_HOME`, `PATH`, the version command of every pinned ingredient, the user's cookbook cache, `.agents/skills/`, `.work/` session records, `.work/lifecycle.jsonl`, online the pinned release's signed checksum file | `$COOK_HOME` (update and signature checks) only |
| `explain` | nothing | nothing |
| `context show` | the profile, the skills and files `context` entries name; with `--role unattended`, those files at `HEAD` | nothing |
| `hook` | stdin, the profile, the hooks file, the skills and files `context` entries name, `.work/session.json`, `.work/hook-checks.json`, `.work/context/<session-id>.json` | `.work/hook-checks.json`, `.work/context/<session-id>.json`; whatever declared commands and `attention` or `turn_end` actions write |
| `lifecycle list` | the hooks file | nothing |
| `lifecycle run` | the hooks file, `.work/session.json`, `.work/verified.json`, the version-control ingredient | `.work/lifecycle.jsonl`; whatever the actions write |
| `workspace create` | the profile, the version-control ingredient | `.work/workspaces/<name>/`, branch `work/<name>`, its `.work/session.json` (base, task, actor); `.work/lifecycle.jsonl` |
| `workspace remove` | the version-control ingredient, the hooks file | removes `.work/workspaces/<name>/`; `.work/lifecycle.jsonl`; whatever `landed` actions write; for a `landed` `attempt` action, a recorded workspace `.work/workspaces/landed-<name>-<n>` on branch `work/landed-<name>-<n>` |
| `verify` | the profile, the version-control ingredient, changed files, `.work/session.json` | `.work/verified.json`; `.work/lifecycle.jsonl` |
| `attempt` | the version-control ingredient and the worker's ingredient, from the cache; the profile, policy, hooks file and the files and skills `context` entries name, from the base commit; `.work/session.json`; the receipt `.work/verified.json` the worker wrote | the tool-owned files it restores from the base commit; in the checkout's `.work/`, the record that `bootstrap` ran and, after a failed attempt, `last-attempt-output` and `last-attempt.patch`, and the checkout returned to its base; with `--commit`, a commit on the checkout's branch; `.work/lifecycle.jsonl`; whatever the worker and the `completed` and `deliver` actions write |
| `fleet` | the fleet file | whatever the command writes; with `--deliver`, a local branch, a commit and a receipt per changed repository, `.work/lifecycle.jsonl`, and whatever its `deliver` actions write |
| `version` | `$COOK_HOME` | `$COOK_HOME` with `--check-update` |

## Environment variables

Variables that configure `cook` and `the-cook`:

| Variable | Read by | Effect |
| --- | --- | --- |
| `COOK_HOME` | `cook`, `the-cook` | The per-user cache directory, default `$XDG_CACHE_HOME/the-cook`, or `~/.cache/the-cook` when `XDG_CACHE_HOME` is unset: downloaded releases at `versions/<version>/the-cook`, ingredient and recipe archives at `cookbook/<sha256>.tar.gz`, the update-check result and the signature-check result of `doctor` |
| `COOK_BINARY` | `cook` | A path to a `the-cook` binary to exec instead of the pinned release; no download and no checksum check, so it bypasses the pin, and `the-cook doctor` notes it ([wrapper](wrapper.md#air-gapped-machines)) |
| `COOK_NO_UPDATE_CHECK=1` | `the-cook` | Disable the daily release check on explicit commands and in `doctor` |
| `COOK_NO_CONTEXT=1` | `the-cook hook` | Inject no [standing context](context.md) in this environment and every process started from it; `static` entries in `AGENTS.md` stay |
| `COOK_ATTEMPT=1` | `the-cook` | Set by `the-cook attempt` for the worker it starts and by `attempt` actions, never by hand: selects the `unattended` role for standing context, makes the binary refuse `the-cook lifecycle run`, keeps every command from firing events, and makes `attention` and `turn_end` hooks do nothing |

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
| `COOK_REASON` | `blocked` only: the one-line refusal or stop reason |

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
| `$COOK_HOME` (default `$XDG_CACHE_HOME/the-cook`, or `~/.cache/the-cook`) | `versions/<version>/the-cook` for each downloaded release, `cookbook/<sha256>.tar.gz` for each ingredient and recipe archive, the update-check and signature-check results |
| `~/.config/the-cook/fleet.yaml` | the default fleet file |
