# Compatibility standards

## Objective

**Problem.** Many repositories depend on the tool, along with the scripts and coding agents that drive it and the CI
pipelines that check them, and each repository moves to a new release at its own pace. A release that changes something
they rely on breaks all of them at once, often without a message. Other tools and teammates who never use the tool share
the same repositories, and they pay when adopting it loses a rule someone wrote or changes something they already rely
on. When machines run different releases, or a download is not the release its publisher made, the same commit gives
different results on different machines, or a machine runs code its publisher never released.

**Goal.** A teammate or pipeline without the tool works exactly as before adoption, and what scripts, workers and other
tools rely on changes only through a change someone has seen and reviewed. Every machine runs the same genuine release
for the same commit.

## Overview

`the-cook` makes promises to the people, workers, CI systems and repositories that depend on it. The sections below
state each promise and how it changes.

## Versioning

| Artifact | Version scheme | Where the version appears |
| --- | --- | --- |
| The binary and its CLI | semantic versioning: `MAJOR.MINOR.PATCH` | `the-cook version`, release tags |
| Each repository file (`config.yaml`, `policy.yaml`, `hooks.yaml`, `wrapper.yaml`) | an integer `schema`, per file | the file's first key |
| The version a repository runs | an exact release tag, pinned per repository | `version` in `.cook/wrapper.yaml` |
| An ingredient, with its operations, renders, floor and checks | the tag of the cookbook that publishes it, pinned exactly with the sha256 of its archive; the binary ships none | `version` and `sha256` of its entry under `ingredients:` in `.cook/config.yaml`; `the-cook ingredient list` |
| A station, with its ingredient pins, stages, entries, templates and the pins of the recipes it adds | the tag of the cookbook that publishes it, recorded with the sha256 of its archive when it is applied; the binary ships none | `version` and `sha256` of its entry under `stations:` in `.cook/config.yaml`; `the-cook station list` |
| A recipe | none of its own: the repository's copy is its own file; a copy added from a cookbook records the tag it came from and the sha256 of that archive; the binary ships none | `version` and `sha256` of `origin` in `.cook/recipes/<name>/recipe.yaml`; `the-cook recipe list` |
| The ingredient format, the station format, the recipe format and each contract | an integer `schema` per format, an integer version per contract; an ingredient declares its schema and the contract it fills, a station and a recipe their schema | `schema` and `contract` in `ingredient.yaml`, `schema` in `station.yaml` and in `recipe.yaml`; `the-cook version` prints the ones the running release supports |
| Rendered files and [managed blocks](generated-content-standards.md#managed-blocks) | the schema of their sources | the [generated-file header](generated-content-standards.md#header) and the manifest (`.cook/manifest.json`) |

- Rendered content carries the schema, never the binary version. It
  depends only on the repository files, the pinned release and the ingredients the repository pins by hash, and
  `the-cook wrapper --version <tag>` renders the repository with
  the new release in the same change as the pin, so moving the pin never leaves drift behind (drift is owned content
  that differs from what the pinned release renders). Why: a version stamp
  would change every file on every upgrade and bury the changes that matter.
- The public contract covered by semantic versioning is: command names and grammar, flags, operands, enumerated values,
  `COOK_*` environment variables (those `the-cook` and its wrapper read and those it passes to actions and to the
  workers it starts), exit-code meanings, JSON output shapes, the
  attempt command's and the make command's
  flags, exit codes, output and environment, `the-cook explain` topic names, the ingredient, station and recipe schemas,
  the recipe step kinds and the three contracts, the cookbook layout
  (`<source>/download/<version>/<kind>-<name>.tar.gz`, `<kind>` being `ingredient`, `station` or `recipe`, and
  `checksums.txt`), the repository file schemas (`wrapper.yaml` and `recipe.yaml` included), the wrapper's name,
  location and flags (`cook` at the repository root, `--no-download`), lifecycle event names and trigger points,
  notification event names, action kinds and their keys, standing-context triggers and roles,
  the profile's state path keys and their defaults, managed-block ids, their markers and their class (convention or
  tool), the generated-file header, the manifest location, and the format of the release's signed checksum file. Why:
  each of these is read by scripts, workers, policy entries, ingredients, stations, recipes, other tools' configuration
  or other repositories, and changes there break them. The attempt command and the run command are named in files such
  as a loop runner's configuration, which a repository keeps unchanged across upgrades of `the-cook`.
- Human-readable output text is not part of the contract and may change in any release, because people read it and
  JSON exists for programs.

## What counts as breaking

| Change | Breaking | Not breaking |
| --- | --- | --- |
| Commands and flags | removing or renaming; changing an operand's meaning; making an optional flag required | adding a command, a flag or an enumerated value |
| Exit codes | changing the meaning of a code; mapping an existing situation to a different code | mapping a new situation to an existing code |
| Lifecycle and notification events | removing or renaming an event or an action variable; moving a trigger point; changing the `auto` default | adding an action variable |
| Action kinds | removing or renaming a kind or one of its keys; changing how a kind receives values | adding an optional key whose absence keeps existing behaviour |
| Standing context | removing a trigger or a role; changing what a trigger matches | adding an optional key whose absence keeps existing behaviour |
| JSON output | removing, renaming or retyping a field; changing array order | adding a field |
| Repository files | removing, renaming or redefining a key; changing a default; tightening validation of values that used to be accepted | adding an optional key whose absence keeps existing behaviour |
| Rendered output | for unchanged inputs and schema, a change in what the rendered files make a worker do, other than a new invariant or floor rule | for unchanged inputs and schema, different bytes that leave worker behaviour unchanged (the shipped skill's text, formatting), shipped in a minor release and reviewed in the upgrade diff; different bytes because the inputs changed |
| Managed blocks and header | changing marker syntax, a block id, a block's class or the header line | adding a new block id |
| Invariants | weakening one (never allowed) | adding one; it changes rendered output and therefore follows the rendered-output rule |
| Ingredient, station and recipe schemas, contracts | removing, renaming or redefining a field, an operation or a step kind; changing an operation's output kind; changing how a step kind substitutes inputs; dropping an ingredient, station or recipe schema, or a contract version, from the support window before its deprecation ends | adding an optional field, operation or step kind whose absence keeps existing behaviour; reading a new schema or contract version |
| Release trust | removing a key a released binary trusts without a rotation release ([below](#release-trust)) | introducing the next key in a release signed by the current one |


Moving a block between the convention and tool [classes](generated-content-standards.md#block-classes) is breaking
because it changes what `the-cook remove --eject` keeps in a repository.

### Rendered output across releases

Rendered bytes depend on the repository files, the pinned release and the ingredients the repository pins by hash, and
on nothing else. A patch release never changes them for unchanged inputs. A minor release may change them, because the
shipped skill teaches the commands of the release that renders it and a rendering fix changes bytes. When a new
invariant adds an entry, the release is breaking and its notes name the affected files. A program's own format change
reaches a repository as a new version of the ingredient that describes it, through a moved ingredient pin
([below](#ingredients-and-stations-across-versions)).

Every such change reaches a repository the same way: `the-cook wrapper --version <tag>` renders the repository with the
new release in the same change as the pin, `cook` and the shipped skill included, so the pin and its effects reach
review as one diff. The pinned release runs the drift check (`the-cook render --check`), and no owned file is exempt
from it. A binary other than the pinned release (`COOK_BINARY`) gets a `the-cook doctor` note naming both versions,
because its drift results may differ. Why: with upgrades rendered in their own diff, the pinned release's output and the
repository always agree, and an exemption would hide exactly the hand edits the check exists to catch.

### Ingredients and stations across versions

An ingredient's renders and floor are inputs to rendering, and they change only when the repository moves the
ingredient's pin, that is, changes its `version` and `sha256`; moving the release pin changes none of them. A station is
not an input to rendering: its pins, entries and templates take effect only once they are written into the repository's
files, and from then on they are the repository's own. Why: a check whose result changes when someone else publishes a
change fails pipelines that changed nothing, and an entry that changes without a change in the repository cannot be
explained from the repository.

- Moving an ingredient's pin with `the-cook ingredient pin` renders in the same change, so the newer version's renders
  and floor reach review as one diff with the pin.
- A newer version of a station reaches a repository only when someone applies it:
  `./cook station apply node --cookbook github:acme/cookbook@v1.3.0 --dry-run` shows what it would add, and applying
  adds only what the repository lacks and moves the station's entry in `stations:` to that version. It never moves an
  ingredient pin the repository holds, and reports each ingredient whose pin differs from the one the station lists. A
  newer template is written only where its file is absent; where the file exists, applying reports `kept <path>: exists`
  and leaves it, because the file is the repository's own.
- A release reads every ingredient and station schema and contract version in its support window and refuses a newer
  one, naming the release needed, so moving the release pin never
  silently changes how a pinned ingredient or a station is read.
- Nothing records which entries a station added, so no command reports that a station gained entries. Why: such a report
  would nag a repository that removed an entry on purpose. A repository that wants a newer station's entries applies it
  again and reads the diff.

### Recipes across versions

A recipe is not an input to rendering, and the repository's copy is its own file, so a newer version of a recipe
reaches a repository only when someone merges it into that copy. Why: a step that changes without a change in the
repository cannot be explained from the repository, and the repository's edits to its copy are its own decisions.

- `the-cook recipe update <name> --cookbook <source>@<version>` merges three versions: the repository's copy, the
  version its `origin` names, read from the per-user cache by its sha256, and the tag's version. Clean hunks apply,
  and `origin` moves to the tag; the result is shown as a diff and confirmed before it is written. A clean merge exits
  0. A merge with conflicts writes them into the copy with conflict markers, moves `origin` all the same and exits 3;
  the recipe is invalid, and no run starts from it, until a person or a worker resolves the markers, which is all that
  is left to do: the binary keeps no record of a merge in progress. Why: a merge from the version the copy came from
  keeps every edit of the repository's that the newer version does not touch, and a conflict is a judgement the binary
  does not make.
- `the-cook fetch` caches the version every recipe's `origin` names, so a clone can merge offline once it has fetched.
- Applying a newer version of a station never changes a recipe the repository already has: it reports the directory as
  kept, and `the-cook recipe update` merges the newer recipe.
- Moving the release pin never changes a recipe. A release reads every recipe schema in its support window and refuses
  a newer one, naming the release needed, and a step kind keeps its
  meaning across releases within a major version.
- A recipe without `origin` is the repository's own, with no other version to merge from.

## Adoption equivalence

`the-cook scaffold --adopt` promises that adopting `the-cook` changes no permission a project already enforces. Why: a
lost deny widens what workers may do and a lost allow stalls attempts on prompts, so an adoption that drops rules
is worse than none.

- After importing, the binary renders the policy and compares the effective rule set per worker with the set in effect
  before adoption. Every project-scope rule (one in the repository's committed worker settings, the files each worker
  ingredient names) stays in effect, or adoption exits 2 naming each rule that would be lost, and writes nothing.
- An entry that would weaken a floor rule of an ingredient in use (allowing a bypass argument a worker ingredient
  marks, or a force push to the default branch a version-control ingredient denies) is dropped and reported. It is the
  only project-scope rule adoption does not carry over, because floor rules hold in every repository that uses the
  ingredient.
- Exact duplicates merge. The same pattern under allow and deny resolves to deny and becomes a to-do item, because
  the narrower reading is the safe one until a person decides.
- Every imported entry carries a provenance comment naming its source file and scope
  (`# imported from <file> (project)`), so a reviewer can trace it.
- Importing the `local` or `user` scope (`--import local`, `--import user`) publishes personal rules: they move from
  one person's machine into a committed, shared `policy.yaml`. Each such entry is marked for review in the to-do. The
  source files are read and never written.
- Settings that are not policy (a default permission mode, extra directories, approval and sandbox settings, MCP server
  definitions, enterprise or managed settings, as each worker ingredient lists them) are reported as notes and left in
  place, because they are not permission rules and `the-cook` does not own them.
- `--dry-run` prints the generated `policy.yaml` and `hooks.yaml` and the equivalence report, and writes nothing.

## Releases and deprecation

- Breaking changes ship only in a major release. Before 1.0, they MAY ship in a minor release and are listed first in
  its notes. Why: the version number is the one signal every dependent reads before upgrading.
- Patch releases contain fixes only and never change rendered output for unchanged inputs, so a patch upgrade never
  produces a diff.

### Deprecation

- A command, flag, value, environment variable or file key is deprecated before it is removed. It keeps working for at
  least one minor release before 1.0, and until the next major release from 1.0. Why: repositories upgrade at their
  own pace through their pins.
- While deprecated, it prints one stderr line per invocation naming the replacement, and `the-cook doctor` reports
  deprecated keys in repository files.
- A schema is readable for at least the same window after its successor appears. Its migration is available for as
  long as it is readable.
- Deprecation never changes stdout, JSON output or exit codes, because a warning must not break what it warns.

## Supported workers and tools

- `the-cook` supports any worker a repository describes with an ingredient it pins. The
  worker contract such an ingredient fills is public.
- Every supported version range lives in the checks of the ingredient that describes the program. A program no
  ingredient in use describes has no range, because the binary contains code and data for no program.
- `the-cook doctor` runs the version command of each ingredient in use and reports each program outside its ingredient's
  range as a finding (exit 2), naming the version found and the range supported. `the-cook` never renders silently for
  an unsupported version, because output rendered for a format the program no longer reads fails without a message.
- `the-cook doctor` also reports, as a finding, a program that an ingredient, a declared command, lane, validator or
  action, or a recipe's `run` step, names and that is not on `PATH`. Why: a declared command that names a missing
  program fails for everyone who runs it, and reporting that needs no knowledge of the program.
- `the-cook ingredient list --cookbook <source>@<version>` shows which ingredients of a tag detect their program in the
  repository. Why: a program in use without its ingredient runs without that ingredient's floor, so a person needs to
  see the gap before choosing what to pin.
- The project's CI tests the core with stand-in ingredients, stations and recipes in its own fixtures, none named after
  or describing a real program, and checks that the binary's source and embedded assets name no external program. Each
  cookbook tests its ingredients against the real programs and versions their checks name, its stations by applying them
  and its recipes by running them, and owns the ranges and the floor. Why: an ingredient writes its program's format and
  carries its floor, so whoever publishes it is the one who can test both.
- Each worker ingredient documents which worker events it renders for hooks, notifications (`attention`, `turn_end`)
  and standing context. Where a worker lacks an event, nothing is rendered for it and `the-cook doctor` reports a note,
  so a missing notification or injection is explained.
- Each jail ingredient's page says what no argument of the jail contract
  can declare for its program: that stdin, stdout and stderr pass through unchanged, that the jail exits with the
  confined command's status, that a read share inside the working directory takes precedence over it, what the jail
  reaches on the network by default, and how a worker program's login and settings reach it. Why: the binary relies on
  each of these and can check none of them at launch, and a jail that broke one would fail an attempt, or open a path
  read-write, without any message.
- Each worker ingredient's page also says what its program reaches on the network by default, and which arguments its
  `launch.network` passes, if it has one. Why: `workers.<name>.network: false` passes nothing, so the program's own
  default decides what an attempt reaches, and a repository can only choose well when that default is written down.
- `the-cook doctor --json` lists the range of every ingredient in use, because a range belongs to the ingredient version
  that was tested against it.

## Transparency for people and CI that do not use `the-cook`

An adopted repository behaves for non-users exactly as it did before adoption:

- A clone builds, tests, deploys and starts its workers without the binary.
- Implicit entry points degrade: rendered worker hooks run
  `./cook --no-download`, never download, and exit 0 after one stderr line naming `./cook` when no binary is
  cached. The session-start hook also adds one line to the worker's context saying the pinned version is not cached and
  how to fetch it; the wrapper prints both lines itself.
- On a machine without the cached binary, hook-delivered standing context and notification actions do nothing.
  Standing-context entries declared `static` are written into `AGENTS.md` and reach workers without the binary, so a
  directive that must always apply does not depend on it.
- Explicit entry points fetch or fail: `./cook <command>` and a global `the-cook` download and verify the pinned
  version on first use. When they cannot, they exit non-zero naming the cause, so a
  skipped verification never reads as a pass.
- Raw hooks declared in `hooks.yaml` render as native entries that run their own command, so they keep working
  without the binary.
- Actions fire only through the binary. Without it, a person pushes, opens pull requests and cleans up by hand,
  exactly as before adoption.
- Recipes run only through the binary. Without it, a person makes the same change by hand, and the generators a
  recipe's `run` steps call keep working on their own.
- No generated file adds a runtime dependency, a required CI step, or a change to build, test, deploy or release
  outputs.
- CI never needs the binary or the wrapper. A CI that opts in runs `./cook render --check` and `./cook doctor`. They
  need the network only to download the pinned version (verified against its checksum) and to re-verify its signed
  checksum file. Because every ingredient a repository uses is pinned, such a job runs `./cook fetch` before them, which
  downloads each uncached ingredient and station, and the version each recipe's `origin` names, and verifies it against
  its `sha256`. The check for a newer release ([update notices](#update-notices)) never affects their result.
- Managed blocks are plain Markdown or comments and read correctly without `the-cook`. Convention blocks state the
  repository's conventions as fact, and `the-cook remove --eject` keeps them as the repository's own text
  ([principles.md](principles.md#8-conventions-outlive-the-tool)).

## Coexistence

`the-cook` shares repositories with other tools and conventions. It adapts to them, never the reverse, because their
owners depend on them as they are.

| Already present | Behaviour of `the-cook` |
| --- | --- |
| A hosting platform's issues, projects, boards, comments, reviews and labels as the record of work | never written; the binary has no logic for the platform that hosts the team's work; task state `the-cook` creates stays clone-local; linking work to issues is a coding agent's job |
| Pull requests, pushes, CI conventions and notification channels | produced only by actions the repository declares in `hooks.yaml`; the binary ships none |
| MCP servers a worker is configured to use | never contacted by `the-cook`; work that needs them is an `attempt` action or the job of the coordinating worker |
| A task tracker's existing data | never read, initialised or configured by the binary; actions the repository declares, such as those a station applied for the tracker, run the tracker's commands on it, and what they write travels wherever the repository synchronises it |
| A loop runner's repository and global configuration | never read; a station's template writes the loop runner's repository file only when it is absent, and from then on it is the repository's file. Why: the binary reads no program's configuration; a bypass argument set there reaches `the-cook attempt`, which refuses it at the first task |
| A loop runner, a tracker or a script around them that a person runs outside the binary | neither detected nor prevented; the invariants hold for what the binary renders and for every worker the binary starts (`the-cook attempt`, `attempt` actions) |
| Workspaces a worker keeps in its own directories | the worker's; never scanned or cleaned |
| A generator the repository already uses (a framework's command that creates a module or a migration, a task-runner task) | never replaced or read; a recipe's `run` step calls it by its argument list, and it keeps working without `the-cook` |
| A task runner's tasks and tool list (its configuration files, a `Makefile`, `package.json` scripts) | never read or written, though a file's presence can match the detect rule of an ingredient or a station; a task that runs `./cook` is the repository's own; a `the-cook` a tool installs globally defers to `wrapper.yaml` inside the repository |
| A `.cook/` directory another program created | never written; `the-cook scaffold` refuses with exit 3 and names the conflict |
| Another program named `the-cook` earlier on `PATH` | `./cook` always runs its own pinned binary from its cache, so wrapper invocations are unaffected; only the optional global command can be shadowed, and `the-cook doctor` reports it as a note |
| The version control system's hook settings and hook managers | never changed |
| A worker's instructions file with content | left as written; only the managed block its ingredient renders is added |
| Sections other tools write into `AGENTS.md`, and entries other tools or people write into the version control system's ignore file | untouched; `the-cook` writes only inside its own markers |
| `.agents/` content from other tools and people (skills a skills installer put there, memory, commands, `REVIEW.md`) | untouched; `the-cook` owns only `.agents/skills/the-cook/` and never puts its configuration or state in `.agents/` |
| A skills installer's lock file | never read or written; standing context delivers a skill as the file committed under `.agents/skills/` |
| A worker's own description-based skill loading | left as it is; a skill injected by standing context may also load by its description, so injected skills are written to read correctly twice ([skills-standards.md](skills-standards.md#skills-used-as-standing-context)) |
| A global `the-cook` install of another version | defers to the repository's pin inside the repository; runs itself elsewhere |
| Hand-written worker settings | merge-rendered: the renders of each worker's ingredient own their keys, everything else is preserved |
| Real skill or command directories in a worker's native directory, and whole-directory links | left as they are; `the-cook` links per entry, such as one link to `.agents/skills/the-cook` |
| Local override files a worker reads | never written; read only by `the-cook scaffold --import local` |
| User-level worker settings | never written; read only by `the-cook scaffold --import user` |

A new feature MUST add its row to this table when it meets a file, directory, branch, setting or service another tool
may own, because coexistence is decided per thing, not in general.

## Installation and version pinning

Why: one pinned release per repository means every machine and pipeline runs the version the repository chose, and a
change of version is a reviewed diff ([ADR 0017](../adrs/0017-engine-version.md)).

- The version is pinned per repository, in exactly one place. `.cook/wrapper.yaml` names an exact release tag, its
  source (default `github:lmarqs/the-cook`, or a mirror at any HTTPS base URL laid out the same way) and the sha256
  checksum of the release asset for each supported platform
  (`linux-amd64`, `linux-arm64`, `darwin-amd64`, `darwin-arm64`). The committed wrapper `cook` runs that version,
  so every machine runs the same version against one repository and version skew within a repository does not occur.
- The wrapper looks for the pinned version in the cache (`$COOK_HOME`, default `$XDG_CACHE_HOME/the-cook`, falling back
  to `~/.cache/the-cook`, at `<home>/versions/<version>/the-cook`). When it is absent, the wrapper downloads the raw
  per-platform release asset over HTTPS with `curl` or `wget`, verifies its sha256 against `wrapper.yaml`, and only then
  caches and execs it. Afterwards it runs offline.
- The wrapper MUST download only over HTTPS and MUST NOT cache or execute a binary whose checksum does not match
  `wrapper.yaml`. A mismatch exits 1 and leaves nothing cached. Why: the committed checksum is what the repository
  reviewed, and anything else is not the release it chose.
- First-run requirements are a POSIX `sh`, `curl` or `wget`, and `sha256sum` or `shasum`. They are requirements of the
  wrapper's first run, not runtime dependencies of the binary or of the repository. Windows is not supported.
- A global install of `the-cook` is optional. Inside a repository whose `wrapper.yaml` pins a different version, it
  execs the pinned version from the cache, downloading it through the same verified path when invoked explicitly and
  never from a hook. Outside such a repository it runs itself. Under a pending pin whose release is not cached, it runs
  `the-cook wrapper` itself and refuses every other command with exit 3. Why: nothing can be downloaded against a
  pending pin, and `the-cook wrapper` is the command that completes it.
- `COOK_BINARY=<path>` makes the wrapper exec that binary instead of the pinned one (air-gapped machines,
  development of `the-cook` itself). It bypasses the pin and its checksum.
- The schema rules in [configuration-standards.md](configuration-standards.md#schema-versioning) apply: a binary
  refuses a repository file newer than it reads, and never renders something different. With the wrapper, this
  refusal occurs only when the pin is bypassed (`COOK_BINARY`, or a global binary too old to defer to the pin).
- Upgrading is a reviewed change to the repository: `the-cook wrapper --version <tag>` (or `--latest`) rewrites the
  version and checksums in `wrapper.yaml` and renders the repository with the new release in the same change, under
  the trust rules below. Across repositories: `the-cook fleet wrapper --version <tag> --deliver`.
- The release source serves plain files over HTTPS at fixed paths: a release's assets, `checksums.txt` and
  `checksums.txt.sig` at `<source>/download/<tag>/`, and the newest release's at `<source>/latest/download/`. No
  platform API is called. Why: a mirror is then a copy of files, for networks that cannot reach the project's release
  host, and the binary carries no platform logic.

### Release trust

Why: a checksum copied by whoever upgrades proves only that the download matches what they saw. It does not prove the
release came from its publisher. Signed checksum files, verified by a binary that is already trusted, carry that proof
from one release to the next.

- Every release publishes `checksums.txt` (the sha256 of each platform asset) and `checksums.txt.sig`, an ed25519
  signature over it.
- Every binary embeds the list of release public keys it trusts. A release signed by the current key introduces the
  next key; later binaries trust both until the old one is retired.
- `the-cook wrapper --version <tag>` downloads that release's `checksums.txt` and its signature, verifies the signature
  with the keys embedded in the running binary, and only then writes `version` and `checksums` into `wrapper.yaml`. A
  release whose signature does not verify is refused (exit 1) and nothing is written.
- `the-cook scaffold` pins the running binary's own version the same way, from the verified checksum file of that
  release, so the first pin is as trustworthy as every later one. With `--offline` it writes the pin with
  `checksums: pending` instead. The wrapper downloads nothing against a pending pin, and `the-cook wrapper --version
  <tag>` completes it through the same verification, so deferring the check never skips it.
- `cook` verifies only the sha256 of the downloaded asset against `wrapper.yaml`. Signatures are checked by
  binaries, never by the shell script, because a POSIX shell has no portable signature tool and the wrapper must run
  on a bare machine.
- The trust root is the first binary a person installs (a release download or a package manager). From then on every
  pin change is verified by an already-trusted binary and is visible as a `wrapper.yaml` diff in review.
- `the-cook doctor`, when online, re-verifies the pinned release's signed checksum file against `wrapper.yaml` (the
  result is cached for a day) and reports a mismatch as a finding, so a hand-edited checksum does not pass unnoticed.

Wrapper reference: [wrapper](../features/wrapper.md). Installing the optional global binary:
[installation](../features/installation.md).

## Update notices

Why: a repository's pin moves only when someone decides, so people need to learn that a newer release exists without
anything changing on its own.

- Explicit commands and `the-cook doctor` check for a newer release at most once per day per machine, using a cached
  result, so the check costs one request a day.
- The check reads the newest release's `checksums.txt` and its signature from the release source, verifies the
  signature with the running binary's embedded keys, and takes the tag from that file. A release whose signature does
  not verify produces no notice, so a compromised host cannot advertise a release.
- The check never blocks: a network failure or timeout is silent, and the check never changes a command's exit code
  or stdout, because a notice must never break the work it accompanies.
- A notice is one stderr line on an explicit command, and a `the-cook doctor` note, naming the pinned version, the
  available version and the exact upgrade command with its tag (`./cook wrapper --version <tag>`).
- Notices never appear in a worker's session context and never come from hooks. Why: moving the pin is a reviewed
  supply-chain decision, and a notice in a worker's context invites the worker to make it.
- `COOK_NO_UPDATE_CHECK=1` disables the check on a machine.
- `the-cook version --check-update` checks on request.
