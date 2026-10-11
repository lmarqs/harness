# Compatibility and coexistence

## Objective

**Problem.** A repository that adopts the tool already holds tools, settings and records that people depend on: an issue
tracker, a task database, a loop runner's configuration, version-control hooks, CI pipelines and hand-kept settings of
its coding agents. A developer weighing adoption, and a teammate who never chose it, need to know which of these change,
because a tool that rewrites them breaks work in ways that are hard to undo. They also need to know what still works if
the repository stops using the tool.

**Goal.** Everything people and other tools relied on before adoption works the same after it and after removal, and a
reader can check that for each tool before adopting.

## Overview

`the-cook` runs the lifecycle a repository declares, and shares the repository with the tools, settings and records
below while staying out of their way. It never reconfigures state it did not create and never writes knowledge. The
binary contains code for no external program and ships no ingredient, station or recipe: version control, every worker
and every other program are described by the [ingredients](ingredients.md) the repository pins from a cookbook, and the
binary has no code for any loop runner, task tracker, skills installer, task runner or hosting platform
([ADR 0022](../adrs/0022-tool-integration.md)). A program reaches a repository as data: an ingredient's renders, a
station's entries and templates, a recipe's steps, a command the profile declares, or a lifecycle action. What
`the-cook` sets up does not depend on it either: every convention is written down in plain text, and an eject keeps it.

How each program behaves next to `the-cook`, and what its ingredient does about it, is documented with that ingredient,
in the cookbook that publishes it. This page states the rules the binary and every ingredient, station and recipe
follow.

Commands on this page run as `./cook <command>`, through the repository's [wrapper](wrapper.md); with a global
install, `the-cook <command>` is equivalent.

## At a glance

| Already in the repository | What `the-cook` does |
| --- | --- |
| Issues, pull requests and boards on a hosting platform | The binary never writes them; delivery is the repository's declared `deliver` actions |
| A task tracker's data in the clone ([task trackers](#task-trackers)) | Never reads or writes it; tracker actions the repository declares, or a station wrote, run the tracker's own command |
| A loop runner's configuration and runs ([loop runners](#loop-runners)) | Never reads its configuration; a station may write a configuration template, only when it is absent |
| Task runners ([task runners](#task-runners)) | Never reads or writes their files; a declared command or a recipe's `run` step may run one; an ingredient's floor may deny workers some of its forms |
| Shared skills from a skills installer ([skills installers](#skills-installers)) | Never runs the installer; leaves `.agents/skills/` and the installer's lock file to it |
| A worker's project settings files | The worker ingredient's renders own only specific keys; the rest is preserved byte for byte ([native worker files](#native-worker-files)) |
| A worker's local and user settings | Never writes them; reads them only for `scaffold --import local` or `--import user` |
| A worker's own instructions file | When the worker ingredient imports `AGENTS.md` through it, adds one import line in a managed block; leaves the rest |
| A worker's skills directory, as a real directory or a link | Leaves its shape; adds one link if needed |
| Version-control hooks and hook managers | Never installs or changes them |
| `.gitignore` | Each ingredient in use that needs it adds its own marked blocks; every other line is left |
| Other tools' sections in `AGENTS.md` | Leaves them |
| Files of programs no ingredient in use describes | Leaves them |
| A `.cook/` directory that `the-cook` did not create | Refuses to adopt the repository and never changes the directory ([scaffold](scaffold.md#greenfield)) |
| CI/CD | Requires nothing; `./cook render --check` is an optional step; a CI-trigger convention is a `deliver` action |

What stays after `the-cook` is removed is under [After removal](#after-removal).

## Hosting platforms

Issues, pull requests and projects on the hosting platform are the record of the team's work. The binary contains no
logic for any hosting platform, and never writes issues, projects, boards, comments, reviews or labels. Linking a task
of a tracker to an issue is a person's or a coding agent's job.

Everything that leaves the clone (a push, a pull request, a CI trigger, a notification) is a
[lifecycle action](lifecycle.md) the repository declares in its own `.cook/hooks.yaml`, such as a command that opens a
pull request under `deliver`. Those actions run with the credentials of whoever fires them, through the platform's own
CLI or API. A repository that declares none never has anything leave the clone through `the-cook`.

## Task trackers

The binary has no code for any task tracker: it never runs a tracker's command of its own accord, reads its data or
creates it. A tracker is wired by lifecycle actions on `start`, `completed` and `blocked`, which the repository declares
or a station such as `agentic-coding` writes as entries, in a managed workspace bound to a task ([tasks](tasks.md)).
Creating and configuring the tracker's data, and every other use of the tracker, is the team's.

| Situation | Behaviour |
| --- | --- |
| No tracker data in the clone | nothing is created; initialising it is the tracker's own step |
| Existing tracker data | never read, written or moved by the binary; its remotes, sync settings and hooks are the tracker's own |
| Tracker actions | run the tracker's command with the credentials of whoever fires the event; what they write travels where the repository's synchronisation sends it, and `the-cook` never configures that synchronisation ([ADR 0012](../adrs/0012-write-boundary.md)) |
| A sandboxed worker that writes the tracker's data | the data directory is listed in `workers.<name>.writable_roots`, by hand or by the station that wires the tracker ([profile](profile.md#workers)) |
| Tracker data tracked in version control, or ignored | the team's choice; the binary writes no `.gitignore` entry for it |
| A tracker remote workers must never push to | a `deny` entry in the repository's policy |

## Loop runners

A loop runner works through a list of tasks unattended, starting a worker for each. The binary has no code for any loop
runner and reads none of its configuration. A station such as `agentic-coding` can write the runner's configuration as a
[template](stations.md#templates), only when it is absent, with `./cook make implement-task --task <id> --unattended` as
the command it runs for each task, and add the `implement-task` [recipe](recipes.md) that command runs, so every task is
attempted under the attempt contract and verified before the runner sees it complete. A team's own script may name
`./cook attempt <worker>` instead ([attempts](attempts.md)). From then on the file is the repository's. Starting the
runner, choosing its task source and firing the events of a run as a whole are the team's.

| Situation | Behaviour |
| --- | --- |
| A runner configuration the repository already has | never read or written; applying a station whose template it is reports it as kept |
| A per-user runner configuration | never read or written. A setting there that reaches the attempt as a bypass argument of the worker's ingredient is refused at the first task |
| The runner's branches, session state and workspaces | the runner's own; `the-cook` never cleans them |
| The runner's task source | the team's choice; `the-cook` sees only the command the runner runs for each task |
| A run as a whole | nothing fires run-scope events by itself; whoever drives the run fires `./cook lifecycle run completed --scope run` or `blocked --scope run` |
| A runner whose command for each task is neither `the-cook make` nor `the-cook attempt` | outside `the-cook`: the attempt contract holds only for workers the binary starts |

## Task runners

The binary has no code for any task runner: it writes no tasks and reads no task runner's configuration. A repository
uses its task runner through its own commands: a declared command, lane or action, or a recipe's `run` step, may run a
task, and `the-cook` runs it like any other command. People and workers run `the-cook` itself as `./cook <command>`.

| Situation | Behaviour |
| --- | --- |
| A task runner's files | never read or written |
| A declared command, lane or action that runs the task runner | runs as written; `doctor` reports it when the program is not on PATH |
| A recipe's `run` step that runs the task runner | runs as written, with the inputs substituted into its arguments and never through a shell |
| A task runner whose ingredient is not in `ingredients:` | when a cookbook's ingredient for it has a detect rule, `ingredient list --cookbook` shows the match; the ingredient's floor is not in use until it is pinned |
| A task-runner form that lets an allowed task carry an unlisted one | denied for workers by the ingredient's floor, when the ingredient is in use ([policy](policy.md#the-floor)) |
| `the-cook` installed through a tool-version manager | an install option ([installation](installation.md#global-install-optional)); a `the-cook` it puts on PATH defers to `.cook/wrapper.yaml` |
| A pin of `the-cook` in the task runner's own tool list | never read: `.cook/wrapper.yaml` is the pin `the-cook` runs, and a second pin drifts from it, so keep `the-cook` out of the task runner's files |

## Skills installers

A skills installer puts shared skills into `.agents/skills/` and may record them in a lock file. The binary has no code
for any installer and never runs one of its own accord ([ADR 0004](../adrs/0004-runtime-dependencies.md)). Installing
shared skills is the team's own step, with the installer of its choice, committed with its lock file.

The profile's `context` names skills by name, and `doctor` reports a skill named there that does not exist under
`.agents/skills/` ([skills](skills.md)). The shipped skill, `.agents/skills/the-cook/SKILL.md`, is the one skill
`the-cook` writes.

## Native worker files

Each worker's own files, its settings, rules, hooks and instructions, are rendered only through its ingredient
([the worker contract](ingredients.md#the-worker-contract)), which names the keys and entries it owns.

| File or shape | Behaviour |
| --- | --- |
| A project settings file with keys no render owns | preserved byte for byte; the worker ingredient's renders own only the keys and entries it names |
| A notification key the repository already sets to a program of its own | left as it is; the notification is not rendered for that worker, and `doctor` notes it |
| Hand-written permissions and hooks | imported losslessly by [`scaffold --adopt`](scaffold.md#adopt), with an equivalence check |
| Local and user settings | never written; read only by `scaffold --import local` or `--import user`, which marks what it imports for review |
| Mode, sandbox and approval settings, MCP server definitions, managed settings | not policy; left in place, reported as notes by adoption and `doctor` |
| A worker's skills directory that is a whole-directory link to `.agents/skills` | kept; the shipped skill is reached through it |
| A worker's skills directory that is a real directory | kept; one per-entry link `the-cook` is added |
| A worker's instructions file with content | content untouched; when the worker ingredient imports `AGENTS.md` through it, an `import` block is added |
| A worker's own worktrees or workspaces | the worker's; never scanned or cleaned |

Merge-rendering rewrites only the owned keys and entries; every other key keeps its bytes, formatting included.

## Version control

The binary contains no code for any version control system. The repository's version-control ingredient fills the
[version-control contract](ingredients.md#the-version-control-contract), and `vcs:` in the profile names it. The start,
complete and landed stages of the lifecycle reach version control only through it.

- `the-cook` never installs or changes version-control hooks.
- Workspace creation, the base, history and landed detection run through the ingredient's operations. A command that
  needs them while no pinned ingredient fills the contract exits 3 and names `the-cook station apply` and
  `the-cook ingredient pin`; reading the profile does not need them, because the binary finds the repository root
  itself.
- The floor that protects the default branch is the ingredient's.
- `the-cook` learns what is shared only through the contract's optional `update`: `the-cook fleet --deliver` runs it,
  and `the-cook workspace remove` runs it when the profile sets `workspaces.update_before_landed`. Otherwise every
  command works with what the checkout knows, and the version control system's own sharing, such as a person updating
  the clone by hand, is the team's.
- Every command that reads history needs the program the ingredient runs; `doctor` reports it when it is missing.

## `.gitignore`

Each ingredient in use that creates a path adds its own marked block, such as the version-control ingredient's `state`
and `workspaces` ([block ids](render.md#block-ids)). A block writes its own entries even when another block's entry
already covers them, such as `/.work/workspaces/` under `/.work/`, so removing one never un-ignores another's paths.
Paths the repository tracks are left out of the blocks. Every other line is untouched.

```gitignore
# cook:state
/.work/
# /cook:state
```

## Other tools in `AGENTS.md`

Other tools may add their own sections to `AGENTS.md`, such as a tracker when it initialises its data. `the-cook`
blocks are bound to their markers, never to headings, so the sections coexist. The two may say similar things; trim
the other tool's section if it reads redundantly.

## Monorepos

`the-cook` operates at the repository root only, as the version-control ingredient reports it: one profile, one policy
and one hooks file per repository. A worker's settings directories nested in sub-projects are left alone. Worker
sessions started in a subdirectory are out of scope; see [pitfalls](pitfalls.md).

## CI/CD

A repository's CI/CD is unchanged by adopting `the-cook`:

- no build, test, deploy or release step requires the binary or the wrapper;
- rendered files add no dependency and change no build output;
- hooks and lifecycle actions do not run in CI;
- `./cook render --check` and `./cook doctor` are optional steps you may add ([render](render.md#drift-check));
  the wrapper downloads the pinned release over HTTPS and verifies its checksum, so CI runs exactly the release the
  pull request pins ([wrapper](wrapper.md#ci));
- a job that runs either step runs `./cook fetch` first, which downloads every pinned ingredient and station and every
  recipe's origin version that the job's cache lacks, and verifies each against its sha256;
- the drift check reads only the repository's files, the pinned release and the pinned ingredients, so its result
  changes only with a commit to the repository ([ADR 0013](../adrs/0013-organization-content.md)).

## Versions

- Within one repository there is no version skew: `.cook/wrapper.yaml` pins one release, and both `./cook` and a global
  install run it.
- The release pin changes only through `the-cook wrapper` or `scaffold`, which write its checksums after the running
  binary verifies the release's signed checksum file with the release keys it embeds. `scaffold --offline` leaves them
  [pending](wrapper.md#a-pending-pin), and nothing downloads against a pending pin. The wrapper then checks each
  download against the committed checksums. `doctor`, when online, re-verifies the pinned release's signed checksum file
  against `wrapper.yaml` ([wrapper](wrapper.md#security)).
- Rendered output depends on the sources, on the pinned release and on the ingredients the profile pins by sha256, and
  never records the release; rendered files carry the `schema`.
- An ingredient's pin changes only through `the-cook ingredient pin`, `the-cook station apply` or `scaffold --cookbook`,
  which verify each archive against the cookbook's `checksums.txt`, as downloaded over HTTPS at that moment, and show
  the pins as a diff for review. The binary then verifies every archive against its pin on download and on every load,
  and refuses a mismatch with exit 1. A recipe's copy changes through `the-cook recipe add`, `recipe update` or a
  station that adds it, which verify the recipe's archive the same way and show the copy as a diff, and through any
  edit, like every file of the repository's own. Only those commands and `fetch` download from a cookbook; every other
  command reads the per-user cache, so a clone works offline once its ingredients are cached
  ([ingredients](ingredients.md), [recipes](recipes.md)).
- Each ingredient declares its schema and, when it fills a contract, the contract version it is written for; each
  station and each recipe declares its schema. The binary refuses an ingredient, a station or a recipe whose schema is
  newer than it supports, naming the release needed ([ADR 0005](../adrs/0005-schema-compatibility.md)).
- `the-cook wrapper --version <tag>` renders the repository with the new release in the same change, migrating
  older-schema files, so the pin and every artefact the new release renders differently land in one diff
  ([wrapper](wrapper.md#upgrading)). A binary older than the repository's schema refuses, naming the pinned release to
  run. That happens only when the pin is bypassed, through `COOK_BINARY` or a global binary too old to defer to the
  pin ([installation](installation.md#version-skew)).
- `the-cook doctor` runs the version check of every ingredient in use against the range that ingredient declares,
  which `the-cook doctor --json` and `the-cook ingredient show <name>` list, and reports a version outside its range as
  a finding ([Supported program versions](#supported-program-versions)). A program no ingredient in use describes gets
  no version check; `doctor` still reports a program that an ingredient, a declared command, a lane, a validator or an
  action names and that is not on PATH.
- The pinned release runs the drift check, which fails on any difference, the shipped skill included
  ([ADR 0007](../adrs/0007-ownership.md)). A binary other than the pinned release, run through `COOK_BINARY`, gets a
  `doctor` note, because its drift results may differ.

### Supported program versions

Each ingredient declares the versions of its program it supports, and the cookbook that publishes it tests it against
the programs and versions it names. `the-cook`'s own tests cover the core with stand-in ingredients and stations, so the
range an ingredient declares is what its cookbook stands behind. `the-cook doctor --json` and
`the-cook ingredient show <name>` list the range of each ingredient in use.

## After removal

`the-cook` writes every convention it establishes into plain text that other tools and people already read, so
removing it does not strand the repository ([remove](remove.md)):

| After `remove --eject` | Still works |
| --- | --- |
| `AGENTS.md` keeps the text of its convention blocks without markers: where workspaces live, the effective permissions, where memory is, static standing context | workers and people follow it as ordinary instructions |
| A worker's instructions file keeps its import of `AGENTS.md` | that worker still reads `AGENTS.md` |
| `.gitignore` keeps every entry | state stays out of commits |
| Native settings keep their permissions and `raw` hooks as hand-owned content | workers keep the same rules |
| A tracker's data, which `the-cook` never touched, and the templates a station wrote, such as a loop runner's configuration, stay: they are the repository's own | the tracker; the loop runner once its command for each task, `./cook make` or `./cook attempt`, is replaced |

The eject removes the tool blocks, which describe how to use `the-cook` (`commands`, `layout`, `verify` and `lifecycle`
in `AGENTS.md`, `commands` in `README.md`), together with the shipped skill and its links, so no file `the-cook`
rendered names a `the-cook` command afterwards. A template a station wrote is the repository's own and keeps what it
says, such as the command for each task in a loop runner's configuration.

An undo, `the-cook remove` without `--eject`, instead returns the repository to the setup it had before adoption: every
block and its text is removed, and the settings adoption imported are written back. Both modes delete `cook`, the files
`.cook/` holds that `the-cook` owns or seeded, and the recipes in `.cook/recipes/`, which only `the-cook` runs, each
listed in the plan first so a person can keep a copy; then `.cook/` once nothing else is in it. A file the manifest does
not record, such as a script the team keeps in `.cook/`, is listed and left. Both leave state where it is.

## Not supported

- Windows: the wrapper is a POSIX `sh` script, and releases are built for linux and macOS only.
- Projects that are not at the repository root, and worker sessions started in a subdirectory.
