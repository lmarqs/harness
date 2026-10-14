# CLI standards

## Objective

**Problem.** People type the tool's commands, scripts and coding agents read what the commands print, and CI pipelines
and unattended coding agents run them with nobody watching. When each command picks its own names, flags, output and
exit codes, what a person learns on one command fails on the next, a script breaks when a message is reworded, and a
coding agent cannot tell from a failure whether to retry, fix something or stop and ask a person. A command that waits
for an answer nobody is there to give stalls an unattended run.

**Goal.** Anyone who knows one command can predict how any other is invoked and what its output and exit code mean. A
script, a pipeline or a worker in an attempt can run every command with nobody at a terminal and choose its next step
from the result alone.

## Overview

These rules apply to every command, flag, output stream and exit code of `the-cook`. The
[reference surface](#reference-surface) below is the command set they govern. A new command or flag MUST follow them,
and a deviation is a change to this standard, with its reason ([README.md](README.md#changing-a-standard)). Terms used
here in a precise sense, such as worker, attempt, ingredient, station, recipe, recipe run, receipt and lane, are
defined in the [glossary](../features/glossary.md).

## Entry points

A repository runs `the-cook` through the committed wrapper script `cook` at its root. The wrapper resolves the
version pinned in `.cook/wrapper.yaml` from the machine cache, downloads and verifies it on first use, and execs it
with the arguments ([compatibility-standards.md](compatibility-standards.md#installation-and-version-pinning);
reference: [wrapper](../features/wrapper.md)). Why: a committed wrapper makes every machine and pipeline run the
version the repository chose, with no installation step before the first command
([ADR 0017](../adrs/0017-engine-version.md)).

| Entry point | Kind | Behaviour |
| --- | --- | --- |
| `./cook <command>` | explicit, the documented invocation | runs the pinned version; downloads it over HTTPS and verifies its checksum when it is not cached; exits non-zero naming the cause when it cannot: 1 for a failed download or a checksum mismatch, 3 when a person must act first (no downloader or sha256 tool, or a pending pin whose release is not cached) |
| `the-cook <command>` | explicit, optional global install | inside a repository whose `.cook/wrapper.yaml` pins a different version, execs the pinned version from the cache, downloading it through the same verified path (never from a hook); under a pending pin whose release is not cached, runs `the-cook wrapper` itself, the command that completes the pin, and exits 3 for any other command, because nothing can be downloaded against a pending pin; elsewhere runs itself |
| `./cook --no-download <command>` | implicit: rendered worker hooks | runs only a cached binary and never downloads; when none is cached, prints one stderr line naming `./cook`, and for the session-start event also the one context line below, then exits 0 |

- Implicit entry points MUST NOT download. Only an invocation a person or a worker makes on purpose fetches a binary.
  Why: a hook fires on every matching worker event; a download there stalls the session and fails offline.
- When the pinned version is not cached, the rendered session-start hook prints its stderr line and also adds one line
  to the worker's session context: `the-cook <version> is not cached on this machine; run ./cook version once to
  fetch it.` Every other implicit entry point prints only its stderr line. The wrapper prints both lines itself, as
  fixed text from its template, selected by the event the hook names. Why: the worker learns once per session why hooks,
  notifications and standing context are absent, without a message on every tool call, and only the wrapper is present
  to say it ([ADR 0017](../adrs/0017-engine-version.md)).
- Documentation, help examples, the `AGENTS.md` and `README.md` `commands` blocks and the shipped skill write commands
  that run inside a repository as `./cook <command>`, and show `the-cook <command>` as the equivalent when `the-cook`
  is installed globally. Commands that span repositories (`the-cook fleet …`) are written with `the-cook`. Why: a
  command copied from the docs runs as written in a fresh clone without a global install.
- Next-step commands in messages use the same documented form, for the same reason.
- These standards name commands `the-cook <command>` (the program); the rules apply identically through every entry
  point, because every entry point runs the same binary.
- The wrapper is a POSIX `sh` script, because a shell is the one interpreter every supported machine has. Windows is
  not supported.

### Every spelling is the same command

A worker may invoke a command as `./cook <command>`, `the-cook <command>` or `cook <command>`. These three are the
rendered spellings: a policy entry that names a `the-cook` command is written once, as `[the-cook, …]`, and rendered for
each of them ([configuration-standards.md](configuration-standards.md#policy-entries)). Why: an entry rendered for one
spelling would leave the others open.

Guards never rely on those policy entries alone. The binary itself refuses `the-cook lifecycle run` and
`the-cook make` inside an attempt, the unattended run of a worker that [`the-cook attempt`](#the-attempt-command) starts
for one task, and inside an `attempt` action, the unattended run of a worker that a lifecycle event starts. Both set
`COOK_ATTEMPT=1` in the worker's environment, and the binary checks it. It refuses both commands inside a step of a
recipe run as well, where `COOK_RUN` is set, so a recipe never fires an event itself. The binary also enforces the
[`deliver` guards](../features/lifecycle.md#built-in-guards) itself. Why: an absolute path or `sh cook` slips past any
prefix rule, so policy entries are defence in depth and the binary is the enforcement.

## Reference surface

```text
the-cook scaffold [--adopt] [--import <scope>[,<scope>]...] [--cookbook <source>@<version>] [--station <name>]...
                 [--no-detect] [--offline] [--yes] [--dry-run]
the-cook remove [--module <name>]... [--force] [--dry-run]
the-cook remove --eject [--force] [--dry-run]
the-cook render [--check] [--dry-run]
the-cook ingredient list [--cookbook <source>@<version>]
the-cook ingredient show <name> [--cookbook <source>@<version>]
the-cook ingredient pin <name>... --cookbook <source>@<version> [--yes] [--dry-run]
the-cook station list [--cookbook <source>@<version>]
the-cook station show <name> [--cookbook <source>@<version>]
the-cook station apply <name>... [--cookbook <source>@<version>] [--yes] [--dry-run]
the-cook recipe list [--cookbook <source>@<version>]
the-cook recipe show <name> [--cookbook <source>@<version>]
the-cook recipe add <name>... --cookbook <source>@<version> [--yes] [--dry-run]
the-cook recipe update <name> --cookbook <source>@<version> [--yes] [--dry-run]
the-cook recipe create <name> [--dry-run]
the-cook fetch
the-cook doctor [--json]
the-cook explain <topic>
the-cook context show [--role interactive|unattended] [--path <file>]... [--prompt <text>] [--json]
the-cook lifecycle list [--json]
the-cook lifecycle run <event> [--action <name>]... [--scope task|run|change] [--task <id>] [--dry-run]
the-cook workspace create <name> [--base <ref>] [--task <id>] [--no-bootstrap]
the-cook workspace remove <name>
the-cook verify [--lane <name>=<pattern>]... [--skip <name>=<reason>]... [--full] [--base <ref>] [--check]
the-cook attempt <worker> [--commit] [--withhold <text>] [-- <worker args>]
the-cook make <recipe> [--<input> <value>]... [--task <id>] [--here] [--unattended] [--commit] [--withhold <text>]
              [--dry-run] [-- <worker args>]
the-cook make --continue [<workspace>] [--dry-run]
the-cook make --status [<workspace>] [--json]
the-cook fleet <command> [command args...] [--repos <file>] [--deliver] [--continue-on-error]
the-cook version [--check-update]
the-cook wrapper [--version <tag> | --latest] [--dry-run]

Wrapper only:
./cook --no-download <command> [command args...]

Internal:
the-cook hook
```

| Command or flag | Behaviour governed by these standards |
| --- | --- |
| `scaffold` | refuses, exit 3, where a `.cook/` directory exists without a `config.yaml` or `manifest.json` of `the-cook` (a directory another program created), naming the conflict, because `the-cook` never writes into a directory it did not create. Pins the running binary's own version: it fetches that release's signed checksum file over HTTPS, verifies the signature with the keys embedded in the running binary, and writes `wrapper.yaml` only then; it exits 1 when the file is unreachable or the signature does not verify, and writes nothing. With `--offline` it makes no network request and writes the pin with `checksums: pending`, which admits no download until `the-cook wrapper --version <tag>` completes it; a pending pin is opt-in so that a network failure never produces one unnoticed. With `--cookbook <source>@<version>`, it seeds the skeleton and applies, in one plan and one confirmation, every station of that tag whose detect rule matches the repository, as `station apply --cookbook` does: the station pins, the ingredient pins each station lists, the entries (`vcs:` and the `workers:` the stations' stages declare among them), the templates and the recipes each station adds; `--station <name>`, repeatable, adds a station of the tag to the detected set, and `--no-detect` applies only the stations `--station` names. Without `--cookbook` it writes the skeleton with empty `ingredients:` and `stations:`, and its to-do lists each contract no ingredient fills and the commands that need it |
| `scaffold --adopt --import <scope>[,<scope>]...` | imports existing permission and hook configuration into `policy.yaml` and `hooks.yaml`. `--import` takes a comma-separated set from the closed enumeration `project`, `local`, `user`, `none`; the default is `project` with `--adopt` and `none` without it. Imports from `local` and `user` are marked for review in the to-do ([compatibility-standards.md](compatibility-standards.md#adoption-equivalence)). Besides the native settings its worker ingredients name for import, it reads no program's configuration, and it never reads or moves a loop runner's or a tracker's files |
| `remove` | undo: deletes owned content (without `--module`, `cook`, `wrapper.yaml` and the manifest included), the seeded repository files in `.cook/` and the recipes in `.cook/recipes/` (each listed first in its plan, so a person can keep a copy); deletes a seeded file outside `.cook/`, such as a memory skeleton file, only while it matches its recorded hash; lists and leaves any file the manifest does not record, and deletes `.cook/` once nothing else is in it; removes every managed block and owned native key and entry; writes imported project-scope settings back as plain native settings; leaves the state directories in place and lists them ([generated-content-standards.md](generated-content-standards.md#removal)) |
| `remove --eject` | eject: stops managing and keeps the result. Convention blocks lose their markers and stay; tool blocks are removed; `.gitignore` entries stay; rendered native files and keys stay as hand-owned content without their headers; `raw` hooks stay; hooks that call `the-cook` and permission entries that name a `the-cook` command are removed and listed; the shipped skill and its links, `cook`, the owned and seeded files in `.cook/` and the recipes in `.cook/recipes/`, each listed first, are removed; a file the manifest does not record is listed and left, and `.cook/` is deleted once nothing else is in it. Not combined with `--module`; accepts `--force` |
| `wrapper` | without flags, prints the pin, whether it is cached and the newest release, and writes nothing |
| `wrapper --version <tag>`, `wrapper --latest` | the running binary fetches the target release's signed checksum file and verifies the signature with its embedded keys; only then does it download this platform's asset, verify its sha256, and have the new release write `version` and `checksums` into `.cook/wrapper.yaml` and render the repository: `cook`, the shipped skill and every other artefact the new release renders differently. Any failure exits 1 and writes nothing. The pin and its rendered effects reach review as one diff ([ADR 0017](../adrs/0017-engine-version.md)). `the-cook fleet wrapper --version <tag> --deliver` does the same across repositories |
| `explain <topic>` | prints the built-in documentation for one topic: `layout`, `profile`, `policy`, `hooks`, `lifecycle`, `context`, `adoption`, `verify`, `attempt`, `invariants`, `blocks`, `ingredients`, `stations`, `recipes`, `wrapper`, `safety`, `pitfalls` |
| `context show` | prints, without writing anything, the standing context that would be injected for a role (`interactive` or `unattended`, default `interactive`), given the files read or edited (`--path`) and a prompt (`--prompt`), with the entry and trigger behind each item. It is a deterministic preview of what the hooks inject |
| `ingredient list` | prints the ingredients `ingredients:` pins, with their pins, the contract each fills and whether each is cached; with `--cookbook <source>@<version>`, prints that tag's ingredients and which detect rules match the repository; writes nothing |
| `ingredient show <name>` | prints what a pinned ingredient holds, or with `--cookbook <source>@<version>` one from that tag: the contract it fills, with its schema and contract version; its operations; its renders and floor; its checks; writes nothing; an unknown name exits 1 |
| `ingredient pin <name>... --cookbook <source>@<version>` | downloads the tag's `checksums.txt` and the named ingredient archives over HTTPS, verifies each archive against it, shows the `ingredients:` entries to add or move as a diff and asks; then writes them, caches the archives and renders, so the pin and its rendered effect are one diff. A newer version moves a pin. It writes nothing else into the repository's files. `--yes` and `--dry-run` as elsewhere; without a terminal and without `--yes`, exit 3 |
| `station list` | prints the stations `stations:` records as applied, with their pins and whether each is cached; with `--cookbook <source>@<version>`, prints that tag's stations and which detect rules match the repository; writes nothing |
| `station show <name>` | prints what a station `stations:` records holds, or with `--cookbook <source>@<version>` one from that tag: the ingredients it pins; its stages; its entries, as the YAML it would add, grouped by file; its templates; its detect rule; writes nothing; an unknown name exits 1 |
| `station apply <name>...` | applies the named stations, in order, to an adopted repository, which needs `.cook/config.yaml` (otherwise exit 1, naming `the-cook scaffold`). With `--cookbook <source>@<version>`, it downloads the tag's `checksums.txt` and the named stations over HTTPS and verifies each against it; without it, it applies again a station `stations:` records, at its pinned version, and a name `stations:` does not record exits 1, naming `--cookbook`. It plans, as one diff, the station pins, the ingredient pins each station lists, the entries and templates, and the recipes each station pins under `recipes:`: it adds to `.cook/config.yaml`, `.cook/policy.yaml` and `.cook/hooks.yaml` only what they lack, never replacing an item the repository names and reporting each item it kept, keeps the repository's pin of an ingredient it already pins at another sha256 and reports the difference, writes each template whose file is absent, and copies each recipe, verified against the sha256 the station lists for it, into `.cook/recipes/<name>/` with its `origin`, as `recipe add` does, keeping and reporting a recipe directory that exists ([configuration-standards.md](configuration-standards.md#applying-a-station)). It checks the `requires` of each recipe it copies against the profile as that same diff leaves it, so the workers and lanes the stations add count; a missing contract, role or lane exits 3, names it and writes nothing. It shows the diff and asks; then writes it and renders the repository as `render` does, with every ingredient the stations list downloaded when it is not cached and verified against the sha256 the station lists; a failed download or verification writes nothing. Nothing to add exits 0; `--json` lists the pins, entries and recipes added and the items kept |
| `recipe list` | prints the recipes in `.cook/recipes/`, each with its description, its `origin` (or `own`, for a recipe the repository wrote) and whether the profile meets its `requires`; with `--cookbook <source>@<version>`, prints that tag's recipes; writes nothing |
| `recipe show <name>` | prints a recipe's `recipe.yaml` and every file under its `files/`, from `.cook/recipes/`, or with `--cookbook <source>@<version>` from that tag; writes nothing; an unknown name exits 1 |
| `recipe add <name>...` | in an adopted repository (otherwise exit 1, naming `the-cook scaffold`), downloads the tag's `checksums.txt` and the named recipe archives over HTTPS and verifies each against it; refuses, exit 1, a recipe directory that exists, naming `recipe update`; checks each recipe's `requires` against the profile (a missing contract, role or lane exits 3 and names it); plans each recipe's copy into `.cook/recipes/<name>/` with `origin` written. It shows the diff and asks; then writes. A recipe renders nothing, so `recipe add` does not render |
| `recipe update <name>` | merges three versions of a recipe that has an `origin`: the version `origin` names (from the per-user cache by sha256, otherwise downloaded and verified against it), the repository's copy and the version of the tag `--cookbook` names. It shows the diff and asks; then writes the merged file and moves `origin` to the tag. A clean merge exits 0. A merge with conflicts writes them into the file with conflict markers, moves `origin` all the same, and exits 3: the recipe is invalid, and `make` refuses it, exit 1, until a person or a worker resolves the markers. Resolving them is all that is left to do; the binary keeps no record of a merge in progress |
| `recipe create <name>` | writes a skeleton recipe into `.cook/recipes/<name>/`, without `origin`, for the repository's own use; refuses, exit 1, a directory that exists |
| `make <recipe>` | runs one recipe run ([below](#the-make-command)): validates the inputs by type and checks the recipe's `requires` before anything runs; reads the recipe once and keeps its content and digest in the run record; creates the workspace the recipe names, on branch `work/<name>` at the revision of the shared default line of work, as `workspace create` does (`--task` binds it to a task), or with `--here` uses the current managed workspace (Start); runs the steps in order (Implement); runs every lane the change selects and every lane the recipe requires (Verify); replays from the base, commits with `--commit` and completes (Complete). Delivery follows the repository's own lifecycle actions. It prints each step as it runs ([progress](#progress)). A handoff pauses an attended run and stops an unattended one. A run that stops fires `blocked` with the stage, the step and the reason, and keeps the workspace. Exit 0 completed, 2 a lane failed at Verify or in the replay, 3 a missing prerequisite, a handoff or a stopped step, 1 an error |
| `make --continue [<workspace>]` | resumes a paused or stopped recipe run, in the named managed workspace or, without one, the current one: after a handoff, at the step that failed, or at Verify for a run whose lane or replay failed. It runs the recipe content the run record holds and never reads the recipe file again, and it takes the inputs, the task, `--commit` and `--unattended` from the run record |
| `make --status [<workspace>]` | prints the run record (`run.json` in the workspace's state directory): the recipe's name, content and digest, the inputs, the task, whether the run commits and whether it is unattended, each step's result and the current stage; writes nothing |
| `fetch` | downloads every ingredient `ingredients:` pins, every station `stations:` records and the version every recipe's `origin` names that is not in the per-user cache, verifies each against its `sha256` and stores it under its hash, so `recipe update` can merge offline; a mismatch exits 1 and stores nothing. It writes nothing in the repository. Besides it, only `ingredient pin`, `station apply` (with `--cookbook`, or for a pin that is not cached), `recipe add`, `recipe update`, `scaffold --cookbook` and the `--cookbook` reads of `ingredient list`, `ingredient show`, `station list`, `station show`, `recipe list` and `recipe show` download from a cookbook |
| `lifecycle list` | prints every lifecycle event (the out-of-band `blocked` included), its actions, their kinds, `auto` flags and scopes |
| `lifecycle run <event>` | runs the event's declared actions, in declaration order, for one scope; never asks for confirmation; refuses, exit 3, inside an attempt, an `attempt` action or a recipe step, and for `completed` with scope `task` without a valid receipt when the verify module is enabled, whoever fires it |
| `workspace create <name> --task <id>` | records the free-form task id with the workspace; every event fired in that workspace carries it as `COOK_TASK`, beside the `COOK_ACTOR` every managed workspace has, and `start` fires with scope `task` (without `--task`, with scope `change`) |
| `attempt <worker>` | runs one attempt: the worker, unattended, on one task under the attempt contract, in the checkout it starts in; the stable interface other tools call ([below](#the-attempt-command)) |
| `fleet <command> --deliver` | before the command, learns what is shared through the version-control contract's `update` (one note when the ingredient has none); after it, commits the tool-owned changes on a new local branch `<branch_prefix><command>` started at the revision of the shared default line of work, runs `the-cook verify` (no lanes are needed for a change outside `verify.app_paths`), then fires `deliver` (scope `change`) in that repository, running every declared `deliver` action for that scope |

The binary contains no logic for the platform that hosts the team's work: no command and no flag pushes, opens a pull
request or writes to a hosting platform by itself. Those effects exist
only as declared actions ([principles.md](principles.md#7-nothing-leaves-the-clone-undeclared)). Why: delivery
conventions differ per organization and platform, and a declared action is reviewed where the convention is decided.

The binary contains code for no program, the version control system and the workers included
([ADR 0022](../adrs/0022-tool-integration.md)). Each lifecycle stage that needs a program reaches it through the
contract an [ingredient](../features/ingredients.md) fills
([configuration-standards.md](configuration-standards.md#ingredients-and-stations)), the commands the profile declares
or the actions lifecycle events fire, and other programs call `the-cook` through its commands. Why: every team picks
different programs, and code for one of them would follow that program's releases while serving no team that picked
another.

### The attempt command

`the-cook attempt` is the command another tool's configuration names when it starts a worker on one task, such as the
command a loop runner runs for each task. It runs one attempt, the worker unattended under the
[attempt contract](../features/attempts.md#the-attempt-contract), and reports the outcome through its exit code, its
output and the lifecycle events it fires. A loop runner may name [`the-cook make`](#the-make-command) instead, to take
each task through a recipe.

| Part | Contract |
| --- | --- |
| `<worker>` | a worker the profile declares under `workers:`; its ingredient (`workers.<name>.ingredient`, default: the worker's name) says how to start it unattended |
| `--commit` | on a verified attempt, commit the work in the checkout with the verification summary in the commit body |
| `--withhold <text>` | remove every occurrence of the text from the output of an attempt that failed, so a completion signal the caller expects reaches it only from a verified attempt; for example the loop runner configuration a station's template writes passes `--withhold` with that loop runner's completion marker |
| worker arguments | the first argument the command does not recognise, and everything after it, or everything after `--`, goes to the worker; an argument the worker's ingredient marks as a floor bypass argument is refused |
| prompt | read from stdin or from the worker arguments, as the caller passes it. A prompt on stdin reaches the worker with the `always` entries of the `unattended` role before the task text, and the session-start hook adds none; one in the worker arguments reaches it unchanged, and the session-start hook delivers those entries ([context](../features/context.md#delivery-in-the-prompt)) |
| exit code | 0 verified; 1 refused (uncommitted changes, a floor bypass argument, an ingredient whose content does not match its `sha256`) or a failed attempt (tamper check, a base that cannot be read, missing, malformed or failing receipt); 3 a prerequisite is missing (the worker's program, the version-control ingredient's program, a pinned ingredient that is not in the cache: run `./cook fetch`, or a contract no pinned ingredient fills: run `./cook ingredient pin`) |
| output | the worker's output, buffered and printed after the attempt; on failure the full output is kept in `last-attempt-output` and the attempt's changes in `last-attempt.patch`, in the checkout's state directory, and the checkout returns to its base |
| environment given to the worker | `COOK_ATTEMPT=1`; `COOK_TASK` when the checkout is a managed workspace created with `--task`, otherwise unset |
| events | after a verified attempt in a checkout bound to a task (`COOK_TASK` set), `completed` with scope `task`, then `deliver` with scope `task` when the work is concluded, both fired in the attempt command's own process; in an unbound checkout, such as a loop runner's workspace, none |

- Before anything else, the attempt command resolves the version-control ingredient, the worker's ingredient and its
  jail's from the per-user cache, and uses that resolution for every read, comparison, reset and launch of the attempt.
  It never fetches. Why: an ingredient fetched during an attempt would hand the attempt to whoever controls its source,
  and one read from the working tree would hand it to the worker ([ADR 0022](../adrs/0022-tool-integration.md)).
- The attempt command's flags, exit codes, output and environment are a stable interface: changing one is a breaking
  change ([compatibility-standards.md](compatibility-standards.md#versioning)). Why: other tools' configuration names
  them, and those files belong to repositories that upgrade `the-cook` at their own pace
  ([ADR 0022](../adrs/0022-tool-integration.md)).
- The attempt command reads no configuration of the tool that starts it. Why: the attempt contract holds inside the
  command whoever starts it, so knowing the caller adds no guarantee, and a person can always start the caller some way
  the binary cannot see ([ADR 0022](../adrs/0022-tool-integration.md)).
- A prompt on stdin carries the `always` entries of the `unattended` role, placed by the binary before the task text,
  and the session-start hook of that attempt adds none; a prompt among the worker arguments is passed unchanged. Why: a
  worker program may run no hook unattended, so the prompt is where the binary can be sure the entries arrive, and the
  caller's arguments are the caller's, with no way for the binary to tell the prompt among them
  ([ADR 0020](../adrs/0020-standing-context.md)).
- Nothing in the binary knows when a run of a loop runner starts or ends. Events with scope `run`, and `blocked` for a
  task the loop runner gives up on, are fired by whoever drives the run with `the-cook lifecycle run`. Why: only the
  caller sees the run as a whole, and the attempt command fires events only for a task bound to its checkout.

### The make command

`the-cook make <recipe>` runs one recipe run: one pass of one change through the lifecycle's stages, as the recipe in
`.cook/recipes/<recipe>/` describes it ([recipes](../features/recipes.md)). A person runs it, a coordinating worker runs
it for a person, and a loop runner may name `the-cook make <recipe> --task <id> --unattended` as the command it runs for
each task.

| Part | Contract |
| --- | --- |
| `<recipe>` | a recipe in `.cook/recipes/`; an unknown name exits 1 |
| `--<input> <value>` | one flag per input the recipe declares, each validated by its type before anything runs; a `bool` input is a [boolean flag](#booleans), true when present and false when absent, and every other input must be given |
| `--task <id>` | binds the workspace the run creates to a task, as `workspace create --task` does, and fills the `{task}` placeholder |
| `--here` | runs in the current managed workspace instead of creating one, and reads the recipe from the working tree instead of a base commit, for drafting a recipe; refused with `--unattended`, exit 1 |
| `--unattended` | marks a run nobody drives, such as one a loop runner starts for a task; without it the run is attended, with a terminal or without one |
| `--commit` | at Complete, after a passing replay, commits the work with the recipe, its inputs and the verification summary in the commit body, as `the-cook attempt --commit` does |
| `--withhold <text>` | as for `the-cook attempt`: the text is removed from everything the run prints and printed once at the end of a completed run, so the caller sees its completion signal only then |
| `-- <worker args>` | arguments for the worker of every `attempt` step; one the worker's ingredient marks as a floor bypass argument is refused |
| exit code | 0 completed; 1 an error, with nothing created (invalid usage, `--here` with `--unattended`, an unknown recipe, a recipe that fails validation, a missing or invalid input); 2 a lane failed, at Verify or in the replay; 3 a person must act: a contract, role or lane `requires` names, or another missing prerequisite, a step that stopped the run, a lane the lane rules demand that Verify cannot select, a handoff, or `make` inside an attempt or a step |
| events | those of the stages the run passes, at the [trigger points](../features/lifecycle.md#trigger-points) of `the-cook make`, under the same guards as any work; `blocked` when the run stops, with the stage, the step and the reason in `COOK_REASON`, and in an unattended run also at an exit 3 before it starts; nothing when a handoff pauses an attended run |
| environment given to steps | `run` steps get `COOK_RECIPE`, `COOK_RUN` and `COOK_INPUT_<NAME>` ([below](#values-passed-to-user-declared-commands-and-actions)); the worker of an `attempt` step gets `COOK_ATTEMPT=1` |

A loop runner names `the-cook make <recipe> --task <id> --unattended` with the caller-protocol arguments of
`the-cook attempt`: `--commit`, `--withhold <text>` and its own arguments for the worker after `--`. The loop runner
configuration a station's template writes passes `--unattended`.

- `requires` is checked when the recipe is added and again when it runs, before anything is created; a missing
  contract, role or lane exits 3 and names it. Why: a repository changes after a recipe is added, and a run that stops
  at its third step for a lane the profile never declared has wasted the steps before it.
- A run reads its recipe once, when it starts, and keeps the recipe's content and digest in its run record;
  `the-cook make --continue` runs that content and never reads the file again. A run that creates its workspace reads
  the recipe from that workspace's base commit, the revision of the shared default line of work that the version-control
  ingredient's `default_revision` reports, as this checkout last learned it; `make` never runs `update`, so a recipe
  runs in a new workspace once it is on that line and this checkout knows it. `--here` reads the file in the working
  tree instead, so a person can run a recipe while drafting it, and is refused with `--unattended`. Why: the worker of
  an `attempt` step edits the workspace, and a recipe read again after a step would let it rewrite the steps after its
  own and the lanes that judge them; a run nobody drives never follows a draft nobody reviewed.
- The run's Verify stage runs the lanes itself: every lane the change selects and every lane the recipe's
  `verify.require` names, never skipped, and a required lane the change selects nothing for runs in full. The change's
  selection is the one of the receipt a step left in the workspace: a `the-cook verify` that the worker of an `attempt`
  step, or a person at a handoff, runs gives it, and is feedback for whoever ran it, not the run's proof. The receipt
  the run writes records, per lane, the patterns it ran or that it ran in full ([verify](../features/verify.md)), and
  Complete replays it from the base, as for any work. When the lane rules demand a lane the selection neither selects
  nor skips, the run stops at Verify and names it. Why: a selection a worker leaves can miss the lane the kind of change
  needs, and a required lane that runs whatever the change selects checks every instance the same way.
- A recipe can only add: steps, required lanes and requirements. It cannot skip a lane, weaken a guard or fire an event
  itself. Why: a recipe is repository data that a worker may have written, and the guards hold whoever describes the
  work.
- A run stops when a step fails (a `run` step exits non-zero, a `template` step meets a path that exists, an `attempt`
  step fails), when Verify cannot select a lane the lane rules demand, when a lane or the replay fails, and at a handoff
  in an unattended run. A stopped run fires `blocked`
  once, keeps the workspace and exits 3, or 2 when a lane or the replay failed. `the-cook make --continue` resumes it
  once a person has fixed what stopped it, and `the-cook workspace remove` abandons it. Why: the steps before the stop
  already changed the workspace, so starting over would repeat them, and only a person can tell whether the cause is
  fixed. A loop runner treats any non-zero status as a task that is not done, and 3 tells it and its operator that no
  retry helps until a person acts.
- The caller declares whether anyone drives the run. In an attended run, the default, a handoff pauses the run: it
  prints the handoff's text and the command that resumes the run, records where the run paused, fires nothing and exits
  3, and `the-cook make --continue` resumes after the handoff. In a run marked `--unattended`, a handoff stops the run
  as blocked: it fires `blocked` naming the step and exits 3. A loop runner that omits `--unattended` gets, at a
  handoff, a paused run, exit 3 and no completion text, so it never counts the task done. Why: a coordinating worker
  runs commands without a terminal for a person who is there, so only the caller knows whether someone will continue the
  run; a run nobody drives must reach a person, and a caller that omits the flag leaves its task undone, never wrongly
  done.
- `the-cook attempt` stays the primitive: an `attempt` step runs it, and a script that needs one unattended worker on
  one task calls it directly. Why: a recipe run adds a workspace, steps and required lanes around an attempt, which a
  script with its own does not need.
- The make command's flags, exit codes, output and environment are a stable interface, like the attempt command's
  ([compatibility-standards.md](compatibility-standards.md#versioning)). Why: a loop runner's configuration names it,
  and that file belongs to a repository that upgrades `the-cook` at its own pace.
- Permission to start `the-cook make` is permission for every step of the recipe and for the automatic actions its
  events fire, because they run as child processes of `the-cook`, outside a worker's per-command permission checks.

## Command grammar

Four shapes exist. A new command MUST take one of them, because a user who knows the shapes can guess where a
command lives without reading help.

| Shape | Used for | Commands |
| --- | --- | --- |
| `the-cook <verb>` | an operation on the repository as a whole | `scaffold`, `remove`, `render`, `fetch`, `doctor`, `verify`, `explain`, `version`, `wrapper` |
| `the-cook <noun> <verb>` | an operation on one kind of object the repository holds | `workspace create\|remove`, `lifecycle list\|run`, `context show`, `ingredient list\|show\|pin`, `station list\|show\|apply`, `recipe list\|show\|add\|update\|create` |
| `the-cook fleet <command>` | a repository-level command run across a declared list of repositories | `fleet render`, `fleet doctor`, `fleet scaffold`, `fleet wrapper`, … |
| `the-cook attempt <worker>`, `the-cook make <recipe>` | one pass of work `the-cook` drives under its guards: an attempt, in place of the worker's own program, for another tool that starts workers; a recipe run, for a person, a worker or another tool | `attempt claude`, `attempt acme-agent`, `make migration`, `make implement-task` |

The fourth shape is a verb for the pass the command drives, followed by what it drives: `attempt` starts the worker it
names, and `make` makes one change by the recipe it names. Why: a reader of the calling tool's configuration, or of a
log, sees which worker or recipe each task runs on, and `run` keeps the meanings it has as a step kind, an action kind,
a scope and `lifecycle run`.

Internal commands are called by workers, not by people: `the-cook hook` reads a worker's hook payload on stdin. They
appear in help under a separate **Internal** heading, and their interface is the worker contract: each worker ingredient
maps its worker's hook payload onto one neutral worker event
([ingredients](../features/ingredients.md#the-worker-contract)). Where the calling worker's protocol and the rules in
this page differ, their I/O follows the protocol, because the worker, not `the-cook`, defines what a hook must print and
return.

Rules:

- A verb that applies to the whole repository goes at the top level. A verb that applies to one object goes under
  that object's noun, so related operations are listed together in help.
- A noun is singular when it is a kind of object whose instances commands pick by name (`workspace <name>`,
  `ingredient <name>`, `station <name>`, `recipe <name>`, a `lifecycle` event, the `context` of one session) and plural
  only when the operation always covers the whole set, so the name says whether a command can pick what it touches.
- Operands identify the target (`<name>`, `<event>`, `<topic>`, `<worker>`, `<recipe>`); flags modify how the operation
  runs. An operand is never optional in one command and positional-required in its sibling, because siblings are
  learned together.
- A recipe is an operand of `make`, never a command, and its name is lower case ASCII words joined by hyphens,
  `[a-z][a-z0-9-]*`. Why: recipes are always invoked through `make`, so a recipe name never meets a command name, and
  a command a later release adds never collides with a recipe a repository holds.
- Reading or resuming the state an operation left is a flag on that operation, as checking is: `make --status` reads the
  record `make` writes, and `make --continue` resumes the run `make` started, so starting, resuming and reading a recipe
  run sit together in help. Both take a workspace as their operand, the current managed workspace when it is omitted,
  because a recipe run lives in its workspace.
- Inverse operations use the established pairs: `scaffold`/`remove`, `create`/`remove`. New commands reuse these verbs
  before introducing new ones (`ingredient list`, `station show` and `recipe list` reuse `list` and `show`); `delete`,
  `rm`, `destroy`, `init`, `install` and `uninstall` are not used. Why: one verb per meaning keeps the inverse of every
  command guessable.
- `add` and `update` exist for recipes, each for an operation no other verb names: `add` copies a recipe from a
  cookbook into the repository, which owns the copy from then on; `update` merges a newer version into that copy. Why:
  `pin` would promise a pin the repository does not hold, and `apply` a set of entries a recipe does not add.
  `recipe create` writes a skeleton recipe for the repository to fill in, with the verb `workspace create` uses for a
  new object the repository holds.
- `pin` declares an ingredient in `ingredients:`, or moves its pin, and renders; deleting the entry and rendering is
  its inverse, which removes the renders the manifest records under the ingredient's name. `apply` records a station in
  `stations:`, pins the ingredients it lists and writes its entries and template files into the repository's own files,
  and has no inverse. Why: what it writes becomes the repository's own, like anything it wrote itself. It is changed or
  removed by editing the files, and removing `the-cook` deletes the entries with the seeded files in `.cook/` and leaves
  the template files. Deleting the name from `stations:` changes
  nothing rendered, because a station has no renders. `recipe add` and `recipe create` have no inverse either, for the
  same reason: a recipe is changed by editing its file and removed by deleting its directory, and removing `the-cook`
  deletes `.cook/recipes/` after listing each recipe.
- Checking is a flag, not a verb: `render --check`, `verify --check`. A command whose only job is to check is
  `doctor`. Why: a check and the operation it checks share their inputs, so they share a command.

## Naming

- Command names, flag names and enumerated values are lower case ASCII. Multi-word names use hyphens:
  `--check-update`, `--continue-on-error`, `--no-bootstrap`. Why: one case and one separator remove a
  class of typos that workers and people both make.
- Names are full words. Abbreviations are limited to terms users already type elsewhere (`--json`, `-C`),
  because an invented abbreviation must be looked up.
- Commands and flags have no aliases: each has exactly one spelling
  ([principles.md](principles.md#6-one-way-to-do-each-thing)), because an alias doubles what documentation, policy
  entries and workers must know.
- A flag means the same thing on every command that accepts it. `--base <ref>` is always a revision the version-control
  ingredient resolves; `--task <id>` is always a task id from the team's tracker. Why: a flag learned once must not
  change meaning on the next command.
- A recipe's inputs are flags of `the-cook make` and follow the rules for flag names: lower case ASCII words joined by
  hyphens, `[a-z][a-z0-9-]*` (`--name`, `--module-path`). An input never takes the name of a flag of `make` (every flag
  it accepts, global flags included: `continue`, `status`, `task`, `here`, `unattended`, `commit`, `withhold`,
  `dry-run`, `yes`, `help`, `json`, `verbose`); validation refuses a recipe that declares one. Why: an input that
  shadowed a flag would change what the flag means for that one recipe.

## Flags

### Global flags

| Flag | Meaning | Accepted by |
| --- | --- | --- |
| `-C <dir>` | run as if started in `<dir>` (same semantics as `git -C`) | every command |
| `--json` | write the result as JSON on stdout | commands whose output is data |
| `--dry-run` | print the change set and write nothing | every command that mutates the repository or its state, or runs declared actions |
| `--yes` | answer every confirmation with yes | every command that can ask for confirmation |
| `--verbose` | add detail on stderr | every command |

- Global flags MAY appear before or after the command path, because both orders occur in scripts and both are
  unambiguous.
- A global flag given to a command where it has no meaning is an error (exit 1) naming the flag and the command,
  never silently ignored. Example: `the-cook doctor --dry-run` fails because `doctor` writes nothing. Why: an ignored
  `--dry-run` would let a caller believe a mutation was previewed.
- `--verbose` MUST NOT change stdout or the exit code, so adding it while debugging never changes what a script sees.

### Long and short forms

- Every flag has a long form. `-C` is the only short flag besides `-h` (help). A new short flag requires a change to
  this standard, with its reason. Why: short flags are a scarce namespace and are unreadable in committed scripts and
  policy entries.

### Booleans

- A boolean flag is true when present: `--check`, `--full`, `--commit`, `--force`, `--unattended`. A recipe's `bool`
  input is a boolean flag of `the-cook make` like any other, true when present and false when absent.
- A `--no-<x>` flag exists only when the default is on: `--no-bootstrap`, and the wrapper's `--no-download`, which
  belongs to the wrapper rather than the binary and is its first argument (`./cook --no-download hook <event>`). Why:
  the wrapper reads its own flag before it knows whether a binary exists to receive the rest.
- `--x=false` is not a documented form, because one spelling per state keeps policy entries and docs exact.

### Values

- Enumerated values form a closed set listed in help: `--scope task|run|change`, `--role interactive|unattended`, the
  `values` of a recipe's `enum` input. An invalid value is an error that lists the valid ones, so the fix is in the
  message.
- A value given to a recipe's input never begins with a hyphen, whatever the input's type; `make` refuses one, exit 1,
  before anything runs. Why: a recipe passes its inputs to other programs, and a value that begins with a hyphen could
  reach one as an option.
- Paths in flags are relative to the working directory after `-C`. Paths in output are relative to the repository
  root, with forward slashes. Why: output paths must be identical on every machine and pasteable as input.
- Placeholders in help use angle brackets: `<name>`, `<ref>`, `<path>`.

### Repeatable flags

- A flag that takes several values is repeated, never comma-separated: `--module hooks --module verify`,
  `--path src/a.sql --path src/b.sql`. Why: values such as paths and patterns may contain commas.
- The one exception is a flag whose value is a set drawn from a small closed enumeration: it takes a comma-separated
  list and is not repeated (`--import project,local`), because its values cannot contain commas.
- A keyed value uses `name=value`: `--lane unit=src/foo/**`, `--skip e2e="no route changed"`. The name is everything
  before the first `=`; the value is everything after it and MAY contain `=`.
- The order of repeated flags does not change the result. Repeating the same `name` twice with different values is an
  error unless the flag's help says values accumulate. Why: a silent last-one-wins hides a mistake.

### Precedence

When the same setting can come from several places, a command-line flag wins over a `COOK_*` environment variable,
which wins over a repository file, which wins over the built-in default. The built-in invariants and the floor of the
ingredients in use sit above all of them and are not settings. Why: the narrower the scope of a source, the more
deliberate the choice it expresses.

## Output

### Streams

| Stream | Carries |
| --- | --- |
| stdout | the result: human text by default, one JSON document with `--json` |
| stderr | diagnostics: errors, warnings, progress, update notices, `--verbose` detail |

- stdout MUST contain only the result, so `the-cook doctor --json | jq` and `$(the-cook …)` work.
- With `--json`, stdout carries either one complete JSON document or nothing, so a parser never meets half a
  document. Errors still go to stderr in the human format
  ([messages-and-docs.md](messages-and-docs.md#error-messages)); the exit code says which case occurred.

### Human output

- Default output is for a person reading a terminal: short lines, aligned columns for lists, a one-line summary at the
  end of a mutation (`3 files updated, 1 block updated, 0 removed`), so the outcome is readable at a glance.
- A mutation that changes nothing says so in one line (`up to date`), and exits 0, because "nothing to do" is
  success.
- Each status is spelled as a word (`ok`, `drift`, `missing`, `blocked`, `skipped`). Color MAY reinforce a word, never
  replace it, because color is lost in logs and invisible to some readers.

### Color, emoji and terminal control

- Color and cursor control are used only when the stream is a TTY, `NO_COLOR` is unset or empty, and `TERM` is not
  `dumb`. Each stream is tested on its own, because stdout and stderr are often redirected separately.
- Output written to a pipe or file contains no ANSI escape sequences, so logs and worker transcripts stay readable.
- The first line of each message `the-cook` writes for a person (a result, a refusal, a finding, a prompt) starts with
  the icon of its kind ([Icons](#icons)) and one space under the same conditions as color. Otherwise a message on stderr
  starts with `the-cook: `, so every line still says who wrote it, and a result on stdout carries no prefix, so a pipe
  receives only the result ([messages-and-docs.md](messages-and-docs.md#cli-messages)). Why: the icons are for a person
  at a terminal, and the TTY rule keeps logs, CI, workers and parsers byte-stable, as it does for color.
- No icon appears in `--json` output, in hook output or anything else that enters a worker's context (a hook's
  stderr line, the session-start context line), in `the-cook attempt` output and the worker output a recipe run prints
  from its `attempt` steps, in rendered files, or in documentation prose. Why: a worker or a loop runner reads those
  bytes, and a completion signal must match exactly.
- An icon is decoration only: the status word (`ok`, `drift`, `refused`) is always present, and an icon never replaces
  a word, for the same reason color never does.

### Icons

The icons come from the kitchen and the lab, in keeping with the tool's name. Each one marks exactly one kind of
message, so a person who learns the set reads a terminal at a glance, and a message never carries two icons.

| Icon | Kind | Examples |
| --- | --- | --- |
| 😎 | a result or a piece of information, the default | `render` wrote its files; `ingredient list` and `station list` of the repository's pins; `recipe list` of the repository's recipes; `make --status`; `version` |
| 💎 | verified: proof passed | `verify` recorded a receipt; `verify --check` found a valid one; a recipe run completed |
| 🧪 | a check is running | a `verify` lane, a lane of a recipe run at Verify, a `doctor` check in progress |
| 🍳 | files are being written | `scaffold`, `station apply`, `ingredient pin`, `recipe add`, `recipe update`, `recipe create`, `render` while it writes; a recipe run creating its workspace, and its `run` and `template` steps |
| 📖 | an ingredient, a station, a recipe, a cookbook tag or a documentation topic is shown | `ingredient show`, `station show`, `recipe show`, the `--cookbook` reads of `ingredient list`, `station list` and `recipe list`, `explain` |
| 🧾 | a receipt is read or reported | the receipt line of `doctor`, a receipt shown by `explain` |
| 🧂 | a warning: take it with a grain of salt | the update notice, a deprecation note; never changes the exit code |
| 🔥 | a check ran and failed, exit 2 | drift, a `doctor` finding, a failed lane |
| 💥 | an error, exit 1 | invalid input, a failed download, a hash mismatch |
| ⛔ | blocked or refused, exit 3 | a lifecycle guard, a confirmation without a terminal, a missing program, a recipe run stopped by a step, at Verify or by a handoff in an unattended run |
| 🧯 | something is being undone | `remove`, `remove --eject` |
| ⏳ | waiting on something | a lock held by another process, a long download such as `fetch`, a recipe run's `attempt` step while its worker runs, a handoff that pauses an attended run for whoever drives it |

- The icon follows the message's outcome, so a command that fails shows 🔥, 💥 or ⛔ whatever its own icon would be.
  Progress lines carry the icon of the activity (🧪, 🍳, ⏳) and the final line the icon of the outcome.
- A recipe run has no icon of its own: each of its lines takes the icon of its kind, and its last line the icon of its
  outcome ([progress](#progress)). Why: a run is made of activities the table already marks, and an icon for the run
  would put two icons on one line or hide which activity a line reports.
- Only the icons in this table are used. Each is a single code point that is an emoji by default, from Unicode 11 or
  earlier, with no variation selector and no joiner. Why: terminals compute the width of a joined or variation-selector
  sequence differently, so ⚗️ (alembic), ☠️ or 🧑‍🍳 (cook) misalign columns in some of them, and a font from before
  Unicode 11 shows a box instead of a newer emoji.
- Adding or changing an icon is a change to this table, reviewed like any other standard; the meaning of an icon never
  changes, because people learn it.

### JSON

- Keys are `snake_case`, matching the YAML files (`required_when`, `app_paths`), so one name means one thing in both.
- The JSON shape of each command is part of the public contract
  ([compatibility-standards.md](compatibility-standards.md)): fields are added, never renamed or removed, within a major
  version, because workers and scripts parse it.
- Arrays have a documented, deterministic order, so two runs over the same state compare equal.
- Paths are repository-relative with forward slashes. Timestamps are RFC 3339 in UTC, so values are identical across
  machines and time zones.
- Absent and empty are distinct: a field that does not apply is omitted; an empty list is `[]`. Why: "does not apply"
  and "applies, with nothing in it" lead to different next steps.

### Progress

- Progress goes to stderr, so it never mixes with the result.
- On a TTY, a command that runs longer than about two seconds shows what it is doing (current step or repository), so
  a person can tell a slow command from a stuck one.
- Off a TTY, progress is plain lines at milestones (one per lane, task or repository), never spinners or carriage
  returns, because those corrupt logs.
- Long-running commands (`verify --full`, `lifecycle run`, `make`, `fleet …`) end with a summary listing each
  unit and its outcome. `fleet … --deliver` reports `committed, not delivered` for a repository that declares no
  `deliver` actions. Why: a caller that skimmed the progress still learns every outcome.

A recipe run prints one line per step as the step starts, in the recipe's order, after one line for its workspace and
before one line per lane at Verify, and ends with the run's outcome. A recipe run has no icon of its own: each line
carries the icon of its kind, 🍳 for the workspace and for `run` and `template` steps, which write the workspace, ⏳ for
an `attempt` step while its worker runs and for a handoff, and 🧪 for each lane. The last line carries the icon of the
outcome: 💎 completed (exit 0), 🔥 a lane or the replay failed (exit 2), ⛔ a step or Verify stopped the run, a handoff in
an unattended run included (exit 3), 💥 an error (exit 1). An attended run that a handoff pauses ends on the handoff's ⏳
line and the command that resumes it: it exits 3, but it waits for whoever drives the run rather than refusing anything.
Why: a person sees from the icons alone which step is running, which one waits for them, and how the run ended.

```text
🍳 make migration: workspace .work/workspaces/migration-add-index, branch work/migration-add-index, from main
🍳 step 1/4 run: npm run migration:create -- add-index
🍳 step 2/4 template: src/migration/add-index.spec.ts
⏳ step 3/4 attempt: coder
⏳ step 4/4 handoff: Check the generated SQL against production data volumes, then continue.
  run: ./cook make --continue migration-add-index
```

```text
🧪 verify: lane unit
🧪 verify: lane migrations
💎 make migration: completed in migration-add-index; lanes unit, migrations passed; replayed from main
```

Off a TTY the progress lines carry no icon and start with `the-cook: `, as every message on stderr does. A step that
stops the run ends it with the step, the reason and the next command:

```text
the-cook: step 1/4 run: npm run migration:create -- add-index
the-cook: make migration: blocked at step 1/4: npm exited 1
  fix the cause in .work/workspaces/migration-add-index, then run: ./cook make --continue migration-add-index
```

## Exit codes

| Code | Meaning | Examples |
| --- | --- | --- |
| 0 | success; nothing to report or nothing left to do | `render` wrote or found everything up to date; `doctor` found nothing beyond notes; every action of a `lifecycle run` passed, or the event declares no actions; `make` completed a recipe run |
| 1 | error: the command could not do its job | invalid usage, unknown key in a repository file, a state path inside `.cook/` or `.agents/`, I/O failure; `vcs` or a `workers.<name>.ingredient` naming an ingredient that is not in `ingredients:` or does not fill the matching contract; an unknown ingredient or station, or `station apply` in a repository without `.cook/config.yaml`; an ingredient or a station whose archive does not match its `sha256`; two `ingredients:` entries, or two `stations:` entries, with the same name; `station apply` without `--cookbook` naming a station `stations:` does not record; an ingredient, a station or a recipe whose schema, or an ingredient whose contract version, is newer than the binary supports; an unknown recipe, a recipe file that fails validation (an unknown key, a placeholder that names no input, an input named like a flag of `make`), an input value that fails its type or begins with a hyphen, a recipe that still holds a conflict marker; `make --here` with `--unattended`; `recipe add` or `recipe create` given a recipe directory that exists, `recipe update` given a recipe without `origin`; an explicit entry point that cannot obtain the pinned binary (download failure, checksum mismatch); `wrapper --version`, or `scaffold` without `--offline`, meeting a release whose signed checksum file is unreachable or does not verify; `attempt` refused its start (uncommitted changes, a floor bypass argument) or its attempt failed (tamper check, a base that cannot be read, missing, malformed or failing receipt) |
| 2 | a check ran and failed | `render --check` found drift, `doctor` reported a finding (a program an ingredient or a declared command names that is not on `PATH`, a program whose version is outside its ingredient's supported range, a skill named in `context` that does not exist under `.agents/skills/`, an invalid recipe in `.cook/recipes/` or one whose `requires` the repository does not meet, a pinned release whose signed checksum file no longer matches `wrapper.yaml`), `verify --check` met an invalid receipt; a lane of a recipe run failed, at Verify or in the replay; a lifecycle action exited non-zero; `scaffold --adopt` found a project-scope rule no longer in effect |
| 3 | blocked: the next step is a person's decision | confirmation needed with no TTY and no `--yes`; a prerequisite tool a person must install is missing (a program that an ingredient, a declared command or an action names, the worker's program for `the-cook attempt`, the version-control ingredient's program, the wrapper's downloader or sha256 tool); a pinned ingredient is not in the cache when `render` or `the-cook attempt` needs it (run `./cook fetch`; a hook in the same case skips with one stderr line and exits 0); a command needs the version-control contract, or the worker contract for a worker, and no pinned ingredient fills it (run `./cook ingredient pin`; a hook skips the same way); an explicit entry point meets a pending pin whose release is not cached; `remove` refused a hand-edited file without `--force`; `scaffold` found a `.cook/` directory `the-cook` did not create; a recipe's `requires` names a contract, role or lane the repository lacks, at `recipe add`, `recipe update`, `station apply` and `make`; a recipe run stopped by a step, at Verify by a lane the lane rules demand and the selection lacks, or by a handoff, or paused at a handoff; `recipe update` left conflicts marked; `the-cook make` inside an attempt or a recipe step; the repository schema is newer than the binary (only when the pin is bypassed); a lifecycle guard refused (`deliver` on the default branch, with uncommitted changes, or, when the verify module is enabled, without a valid receipt; `completed` with scope `task` without a valid receipt when the verify module is enabled; any event inside an attempt, an `attempt` action or a recipe step) |

- Codes 2 and 3 tell a worker or a script what to do next: 2 means "fix the finding and run again"; 3 means "stop and
  ask a person". A new failure mode MUST map to one of the four codes; no other code is used. Why: a worker acts on the
  code, and four codes with fixed meanings are all it needs to choose between retry, repair and escalation.
- A missing prerequisite tool exits 3, not 1, because installing software on a machine is a person's decision that a
  worker cannot make; the message names the tool and the command that installs it.
- `doctor` notes (an `AGENTS.md` budget overrun, a standing-context budget overrun, a worker that lacks an event a hook
  needs, an automatic `attempt` action, a `the-cook` on `PATH` that is another program, a running binary other than the
  pinned release, a newer release than the pin) never change the exit code, because they inform a choice rather than
  report a failure.
- Invalid usage exits 1, so that 2 stays unambiguous for CI.
- `the-cook attempt` exits 1 for a failed attempt, not 2. Why: its caller is another tool that treats any non-zero
  status as a task that did not pass, and keeping 3 apart tells that tool and its operator when no retry can help.
- When a `the-cook` command run without a TTY exits 3 outside an attempt, `the-cook` fires the out-of-band `blocked`
  lifecycle event (scope of that command's work, or `change`) with `COOK_REASON` set to the one-line refusal.
  `the-cook make` follows the mode its caller declared instead of the terminal: an unattended run fires `blocked` at
  every exit 3, and an attended run only when it stops, never when a handoff pauses it or a check refuses it before it
  starts. Why: an unattended run that stops for a person must reach that person, and `blocked` is where a repository
  declares how.
- A recipe run that stops fires `blocked` once, with or without a terminal, with the stage, the step and the reason in
  `COOK_REASON`, and exits 3, or 2 when a lane or the replay failed. A handoff in an attended run pauses it, with or
  without a terminal: it exits 3 and fires nothing. Why: a stopped run leaves its workspace part way through a change
  until a person continues or abandons it, and `blocked` is where the repository declares who hears about such work,
  while a paused run is continued by whoever drives it.
- `the-cook fleet` reports each repository's code in its summary and exits 0 only when every repository exited 0.
- `the-cook lifecycle run` exits 2 if any action failed. With `on_failure: stop` (the default), the first failure
  stops the event with exit 2. With `on_failure: continue`, the failure is reported in the summary and in the
  lifecycle log, the remaining actions run, and the event still exits 2. A guard refusal exits 3 before any action
  runs. Why: continuing past a failure is a choice about the remaining actions, not a reason to report success.
- A failing automatic event never changes the exit code of the command that fired it: it is reported on stderr and
  recorded in the lifecycle log (`lifecycle.jsonl` in the state directory of the primary checkout). Dry runs are not
  recorded. Why: the command did its own job, and the failure is reported as the action's.
- `the-cook hook` follows the calling worker's hook protocol for its exit code and output, as its ingredient maps it,
  because the worker interprets them.
- Entry points without the binary ([principles.md](principles.md#1-optional-and-transparent)): an explicit entry point
  (`./cook <command>`, a global `the-cook`) that cannot obtain the pinned version exits non-zero naming
  the cause (1 for a failed download, 3 for a missing downloader or sha256 tool or a pending pin); an implicit entry
  point (a rendered worker hook entry, `./cook --no-download`) with no cached binary exits 0 after one stderr line
  naming `./cook`.

## Errors

Every error states what failed, why, and the exact command to run next, so the reader can act without searching.
Format and wording: [messages-and-docs.md](messages-and-docs.md#error-messages).

- An error about a file names the path and, when known, the line and column, so an editor or a worker can jump to it.
- Validation reports every problem it finds in one run, not only the first, so a fix does not take one run per
  mistake.
- Stack traces and internal identifiers appear only with `--verbose`, because they mean nothing to the person who
  must act.

## Confirmations, `--yes` and `--force`

- A command asks for confirmation only before an action that cannot be undone from the repository's own history, that
  reaches beyond the current repository (`fleet … --deliver`), or that writes into the repository's own files
  (`scaffold`, `ingredient pin`, `station apply`, `recipe add`, `recipe update`, a schema migration). Why: everything
  else is reversible from version control, and needless questions train people to answer yes without reading. What
  `the-cook` writes into the repository's own files becomes the repository's, with nothing marking that `the-cook` wrote
  it, so the person accepts it while it is still shown as a change.
- `the-cook lifecycle run` never asks for confirmation, and a lifecycle action fired automatically at its trigger point
  (`auto: true`) never asks either. The gate for workers is the policy. Scaffolded repositories seed
  `[the-cook, lifecycle, run, deliver]` under `ask`, rendered for every rendered spelling, as a seeded entry the
  repository owns and may change ([ADR 0015](../adrs/0015-development-lifecycle.md)). Why: the declared action is the
  reviewed decision; the policy decides who may trigger it.
- `the-cook recipe create` and `the-cook make` never ask. Why: `recipe create` writes only a skeleton, into a
  directory that did not exist, under the name a person typed, so there is nothing to accept but the request; a recipe
  run writes only its own workspace and branch, from a recipe the repository holds, and its work reaches review when it
  is delivered.
- A confirmation is asked only when stdin and stderr are TTYs. Otherwise the command stops before acting and exits 3,
  naming `--yes`, because a prompt nobody can answer would hang an unattended run.
- `--yes` answers confirmations. It never overrides a safety refusal, so scripted runs stay safe.
- `--force` overrides a named safety refusal (for example deleting a hand-edited tool-owned file). It exists only on
  commands with such a refusal, every refusal message names it, and it never overrides a built-in invariant or a floor
  rule. Why: a refusal a person cannot override blocks legitimate work; an override that reaches the invariants removes
  them.

## Dry run

- Every command that writes to the repository, its state directories or another repository MUST accept `--dry-run`,
  because every write must be inspectable before it happens
  ([principles.md](principles.md#5-never-destroy-always-show-what-will-change)).
- A dry run performs every read and validation the real run would, writes nothing, and prints the complete change
  set: paths created, updated and removed; blocks and native keys changed; unified diffs on request with `--verbose`.
  Why: a preview that skips validation can promise a change the real run refuses.
- A dry run exits with the code the real run would have for validation and checks (1, 2 or 3), and 0 otherwise, so a
  dry run in CI predicts the real outcome.
- With `--json`, the change set is the JSON result.
- For a command that runs declared actions (`lifecycle run`, `fleet … --deliver`), the dry run also applies the
  lifecycle guards and prints each action in order with its kind and the `COOK_*` variables it would receive, and
  runs none. It never prints the values of `url_env` or `headers_env` variables, because those carry secrets.
- For `the-cook make` and `the-cook make --continue`, the dry run validates the inputs and checks `requires`, prints
  the workspace and branch the run would use, each remaining step with its inputs substituted, and the lanes the recipe
  requires at Verify, and runs nothing, so a person reads exactly what a recipe would do with these inputs before it
  does it. `recipe add`, `recipe update`, `recipe create` and `station apply` print the diff they would write.

## Idempotency

- Running a mutation again after it succeeded changes nothing and exits 0, because workers and scripts retry.
- A command interrupted part way leaves the repository in a state the same command can complete by running again, so
  an interruption never needs a manual repair.
- Operations on objects follow the same rule: creating a workspace that already exists with the same base succeeds
  without change; a conflicting state (a different base) is an error naming the conflict.

## Non-interactive operation

Workers, scripts and CI run `the-cook` without a terminal. For them:

- No command requires a TTY. Every input a prompt could collect is also available as a flag, so an unattended caller
  can always supply it.
- No command opens a pager or an editor, because nobody is there to close it.
- Behaviour is the same with and without a TTY except for color, progress style and confirmations (which exit 3), so
  what a person tries at the terminal is what a worker gets.
- A handoff never waits on a terminal: the command ends with exit 3, and `the-cook make --continue` resumes the recipe
  run, with a terminal or without, because a command that waits on a terminal stalls a worker, which has none. Whether
  a recipe run is attended comes from `--unattended`, never from the terminal, because a coordinating worker drives
  commands for a person without one.
- Output does not depend on terminal width when stdout is not a TTY, so captured output is reproducible.

## Environment variables

### Variables `the-cook` reads

Every variable `the-cook` or its wrapper reads is prefixed `COOK_` and documented in help and in the features
reference, so the prefix alone tells a reader the variable belongs to `the-cook`.

| Variable | Read by | Effect |
| --- | --- | --- |
| `COOK_HOME` | the wrapper, a global `the-cook` deferring to a pin, and the binary for ingredients and stations | cache directory for downloaded releases, cookbook archives of ingredients and stations (each at `<home>/cookbook/<sha256>.tar.gz`), the update-check result and the result of `the-cook doctor`'s signed-checksum check, and nothing else; default `$XDG_CACHE_HOME/the-cook`, falling back to `~/.cache/the-cook`; each release at `<home>/versions/<version>/the-cook` |
| `COOK_BINARY` | the wrapper | path of a binary to exec instead of the pinned version (air-gapped machines, development of `the-cook` itself); bypasses the pin and its checksum |
| `COOK_NO_UPDATE_CHECK` | the binary | when on, disables the daily release check |
| `COOK_NO_CONTEXT` | the binary | when on, disables standing-context injection for the process tree it is set in, by hooks and in an attempt's prompt, for debugging what a worker sees without it |
| `COOK_ATTEMPT` | the binary | set to `1` by `the-cook attempt`, and so by a recipe run's `attempt` step, and by an `attempt` action, in the environment of the worker they start; the binary then refuses `lifecycle run` and `make`, and the worker's `attention` and `turn_end` hooks do nothing |
| `COOK_RUN` | the binary | set by a recipe run in the environment of its `run` steps; the binary then fires no lifecycle event and refuses `lifecycle run` and `make` |

- Standard variables are honoured with their usual meaning: `NO_COLOR`, `TERM`, the XDG base directories for
  machine-level configuration and cache, because users already set them for every tool.
- No environment variable changes the bytes a given binary writes into a repository, so rendering stays identical
  across machines. `COOK_BINARY` chooses which binary runs; the others affect presentation, notices, cache location,
  context injection or refusals.
- A boolean variable is on when set to `1` or `true` and off when unset, empty, `0` or `false`, so every boolean reads
  the same way.

### Values passed to user-declared commands and actions

The placeholders `{file}` and `{pattern}` in profile and hook commands are the only substitutions `the-cook` performs in
command text, and `the-cook` shell-quotes each value
([configuration-standards.md](configuration-standards.md#values-and-placeholders)). Actions get values only through
environment variables, so a task title, branch name or worker message cannot inject shell syntax. Any new value is an
environment variable, never a new placeholder. A recipe substitutes its inputs and its task id, as `{<input>}` and
`{task}`, only into argument lists, paths and plain text, never into a string a shell reads
([configuration-standards.md](configuration-standards.md#recipes)).

How each action kind receives them ([configuration-standards.md](configuration-standards.md#declared-actions)):

| Kind | Receives values |
| --- | --- |
| `run` | runs with `sh -c` in the workspace and inherits the environment of the process that fired it, plus the variables below; the shell expands them, `the-cook` substitutes nothing |
| `http` | in `json`, every `$NAME` inside a string is replaced by the JSON-encoded value of the environment variable `NAME` (an unset variable becomes an empty string and a note); the URL and header values come only from the variables named by `url_env` and `headers_env` |
| `attempt` | in `prompt`, every `$NAME` is replaced by the variable's value as plain text |

Variables set for lifecycle actions:

| Variable | Value |
| --- | --- |
| `COOK_EVENT` | the lifecycle event (`start`, `verified`, `completed`, `deliver`, `landed`, `blocked`) |
| `COOK_SCOPE` | `task`, `run` or `change` |
| `COOK_REPO` | absolute path of the primary checkout |
| `COOK_WORKSPACE` | absolute path of the workspace the action runs in |
| `COOK_WORKSPACE_NAME` | the workspace's name |
| `COOK_BRANCH` | the work's branch |
| `COOK_BASE` | the base ref the work started from |
| `COOK_DEFAULT_BRANCH` | the default branch the version-control ingredient reports |
| `COOK_TASK` | the task id, for scope `task`: the id recorded with `workspace create --task`, or given with `--task`; unset in a workspace created without `--task` |
| `COOK_ACTOR` | the actor of the managed workspace, `<user>:<workspace>:<random>`, recorded when it was created; set in every managed workspace |
| `COOK_RECEIPT` | path to the receipt JSON |
| `COOK_SUMMARY` | one-line verification summary |
| `COOK_REASON` | the one-line refusal or stop reason, for `blocked`; for a stopped recipe run, the stage, the step and the reason |
| `COOK_RECIPE` | the recipe a recipe run made the work with, as its receipt records; unset for other work |
| `COOK_INPUT_<NAME>` | each input of that recipe run, as its receipt records |

Variables set for the `run` steps of a recipe run, beside the environment of the process that started the run:

| Variable | Value |
| --- | --- |
| `COOK_RECIPE` | the recipe's name |
| `COOK_RUN` | the path of the recipe run's record, `run.json` in the workspace's state directory |
| `COOK_INPUT_<NAME>` | the value of each input, `<NAME>` being the input's name in upper case with hyphens as underscores (`--module-path` gives `COOK_INPUT_MODULE_PATH`) |

A `run` step's argument list never passes through a shell, so an input substituted into an element stays that one
argument. An `attempt` step's prompt and a handoff's text receive `{<input>}` as plain text, so both are a
prompt-injection surface, as an `attempt` action's prompt is: the attempt contract bounds the worker of an `attempt`
step, and the policy bounds an interactive worker that reads a handoff.

Variables set for notification actions (`attention`, `turn_end` in `hooks.yaml`):

| Variable | Value |
| --- | --- |
| `COOK_EVENT` | `attention` or `turn_end` |
| `COOK_WORKER` | the name of the worker that fired the event, as `workers:` declares it (`claude`, `reviewer`) |
| `COOK_MESSAGE` | the worker's own notification text; untrusted, because the worker writes it |
| `COOK_REPO` | absolute path of the primary checkout |
| `COOK_WORKSPACE` | absolute path of the checkout the worker runs in |
| `COOK_BRANCH` | that checkout's branch |

A variable that does not apply to the scope or event is unset, not empty, so an action can tell "not applicable" from
"empty". These names and meanings are part of the public contract
([compatibility-standards.md](compatibility-standards.md#versioning)).

## Help text

Every command and command group answers `--help` (and `-h`) on stdout with exit 0, in this order, so a reader finds
the same information in the same place on every command:

1. One-line summary in the imperative (`Render the workers' native files from .cook/`).
2. Usage line with operands and flags in the grammar above.
3. A short description: what the command reads, what it writes, what it never touches.
4. Flags: name, value placeholder, default, allowed values.
5. Examples: at least one complete, runnable invocation, written as `./cook <command>`.
6. Exit codes that apply, when the command uses more than 0 and 1.
7. See also: the matching `the-cook explain <topic>` and related commands.

Invalid usage prints the usage line and the hint `run: ./cook <command> --help` to stderr and exits 1, so the
caller sees the correct form and where to read more.

`the-cook make <recipe> --help` lists that recipe's inputs as its flags, each with its type, its description and, for an
`enum`, its values, read from the recipe's file, because a recipe's inputs are the flags of its run.

## Version and compatibility output

- `the-cook version` prints the binary version (semantic versioning) and, per repository file, the schema versions it
  reads. With `--json`, the same as data. Why: a schema refusal names the file's schema, the newest schema the binary
  reads and the pinned release that reads the file
  ([configuration-standards.md](configuration-standards.md#schema-versioning)), so the user needs to see what the
  binary in hand reads.
- `the-cook version --check-update` checks for a newer release on request. Moving the repository to it is
  `the-cook wrapper --version <tag>`, because the version a repository runs is a reviewed change, not a machine update.
- Version support is a check of each ingredient in use: `the-cook doctor` runs the ingredient's version command and
  names the version found and the ingredient's supported range, so an unsupported version is found before it renders
  wrong output. A program no ingredient in use describes has no version check. `the-cook doctor` also reports a program
  that an ingredient, a declared command, lane, validator or action, or a recipe's `run` step, names and that is not on
  `PATH`, and each contract no pinned ingredient fills, with the commands unavailable because of it
  ([ADR 0022](../adrs/0022-tool-integration.md)). `the-cook ingredient list --cookbook <source>@<version>` shows which
  ingredients of a tag detect their program in the repository.
- Rules for what a version promises: [compatibility-standards.md](compatibility-standards.md).

## Deprecation

- A deprecated command, flag, operand, value or environment variable keeps working for the window in
  [compatibility-standards.md](compatibility-standards.md#deprecation) and prints one stderr line per invocation:
  `the-cook: --old is deprecated; use --new` (with the exact replacement). Why: scripts and policy entries that use the
  old form keep working while their owners learn the new one.
- Deprecated items stay in help under a **Deprecated** heading until removal, so a reader of an old script can still
  look them up.
- A deprecation warning never changes stdout, the JSON result or the exit code, because a warning must not break the
  scripts it warns.
