# Compatibility standards

## Objective

**Problem.** Many repositories depend on the tool, along with the scripts and agents that drive it and the CI pipelines
that check them, and each repository moves to a new release at its own pace. A release that changes something they rely
on breaks all of them at once, often without a message. Other tools and teammates who never use the tool share the same
repositories, and they pay when adopting it loses a rule someone wrote or changes something they already rely on. When
machines run different releases, or a download is not the release its publisher made, the same commit gives different
results on different machines, or a machine runs code its publisher never released.

**Goal.** A teammate or pipeline without the tool works exactly as before adoption, and what scripts, agents and other
tools rely on changes only through a change someone has seen and reviewed. Every machine runs the same genuine release
for the same commit.

## Overview

`the-cook` makes promises to the people, agents, CI systems and repositories that depend on it. The sections below state
each promise and how it changes.

## Versioning

| Artifact | Version scheme | Where the version appears |
| --- | --- | --- |
| The binary and its CLI | semantic versioning: `MAJOR.MINOR.PATCH` | `the-cook version`, release tags |
| Each repository file (`config.yaml`, `policy.yaml`, `hooks.yaml`, `wrapper.yaml`) | an integer `schema`, per file | the file's first key |
| The version a repository runs | an exact release tag, pinned per repository | `version` in `.cook/wrapper.yaml` |
| Builtin recipes, with their entries, templates, renders, floor rules and checks | none of their own: each release holds its version of every builtin recipe | `the-cook recipe list` and `the-cook recipe show <name>` of the running release |
| A fetched recipe | its own version, pinned exactly with the hash of its content | `version` and `sha256` of its entry under `recipes:` in `.cook/config.yaml` |
| Rendered files and [managed blocks](generated-content-standards.md#managed-blocks) | the schema of their sources | the [generated-file header](generated-content-standards.md#header) and the manifest (`.cook/manifest.json`) |

- Rendered content carries the schema, never the binary version ([ADR 0005](../adrs/0005-schema-compatibility.md)). It
  depends only on the repository files, the pinned release and the recipes the repository pins by hash
  ([ADR 0013](../adrs/0013-organization-content.md)), and `the-cook wrapper --version <tag>` renders the repository with
  the new release in the same change as the pin, so moving the pin never leaves drift behind (drift is owned content
  that differs from what the pinned release renders) ([ADR 0017](../adrs/0017-engine-version.md)). Why: a version stamp
  would change every file on every upgrade and bury the changes that matter.
- The public contract covered by semantic versioning is: command names and grammar, flags, operands, enumerated values,
  `COOK_*` environment variables (those `the-cook` and its wrapper read and those it passes to actions and to the agents
  it starts), exit-code meanings, JSON output shapes, the [worker command](cli-standards.md#the-worker-command)'s flags,
  exit codes, output and environment, `the-cook explain` topic names, the names of the builtin recipes, the recipe
  schema and its two contracts, the repository file schemas (`wrapper.yaml` included), the wrapper's name, location and
  flags (`cook` at the repository root, `--no-download`), lifecycle event names and trigger points, notification event
  names, action kinds and their keys, [standing-context](../features/context.md) triggers and roles, the profile's state
  path keys and their defaults, managed-block ids, their markers and their class (convention or tool), the
  generated-file header, the manifest location, and the format of the release's signed checksum file. Why: each of these
  is read by scripts, agents, policy entries, recipes, other tools' configuration or other repositories, and changes
  there break them. The worker command is named in files such as a loop runner's configuration, which a repository keeps
  unchanged across upgrades of `the-cook` ([ADR 0022](../adrs/0022-tool-integration.md)).
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
| Rendered output | for unchanged inputs and schema, a change in what the rendered files make an agent do, other than a new [invariant](../features/concepts.md#built-in-invariants) or floor rule | for unchanged inputs and schema, different bytes that leave agent behaviour unchanged (the shipped skill's text, formatting), shipped in a minor release and reviewed in the upgrade diff; different bytes because the inputs changed |
| Managed blocks and header | changing marker syntax, a block id, a block's class or the header line | adding a new block id |
| Invariants and the floor rules of builtin recipes | weakening one (never allowed) | adding one; it changes rendered output and therefore follows the rendered-output rule |
| Supported agents and tools | removing a builtin recipe, a harness recipe included | adding a builtin recipe or a supported version; narrowing a version range in a minor release, listed in its notes |
| Recipe schema and contracts | removing, renaming or redefining a field or an operation; changing an operation's output kind | adding an optional field or operation whose absence keeps existing behaviour |
| Release trust | removing a key a released binary trusts without a rotation release ([below](#release-trust)) | introducing the next key in a release signed by the current one |
| Builtin recipes | renaming one | changing the entries or templates one holds, because a repository's files change only when someone applies a recipe, and applying only adds; a change to a recipe's renders follows the rendered-output rule ([below](#recipes-across-releases)) |

Moving a block between the convention and tool [classes](generated-content-standards.md#block-classes) is breaking
because it changes what `the-cook remove --eject` keeps in a repository.

### Rendered output across releases

Rendered bytes depend on the repository files, the pinned release and the recipes the repository pins by hash, and on
nothing else. A patch release never changes them for unchanged inputs. A minor release may change them, because the
shipped skill teaches the commands of the release that renders it and a rendering fix changes bytes. When a program's
own format change forces different output, or a new invariant or floor rule adds an entry, the release is breaking and
its notes name the affected files.

Every such change reaches a repository the same way: `the-cook wrapper --version <tag>` renders the repository with the
new release in the same change as the pin, `cook` and the shipped skill included, so the pin and its effects reach
review as one diff. The pinned release runs the drift check (`the-cook render --check`), and no owned file is exempt
from it. A binary other than the pinned release (`COOK_BINARY`) gets a `the-cook doctor` note naming both versions,
because its drift results may differ. Why: with upgrades rendered in their own diff, the pinned release's output and the
repository always agree, and an exemption would hide exactly the hand edits the check exists to catch
([ADR 0007](../adrs/0007-ownership.md)).

### Recipes across releases

A recipe's entries and templates are not inputs to rendering: they take effect only once they are written into the
repository's files, and from then on they are the repository's own. A recipe's renders and floor rules are inputs: those
of a builtin recipe change with the release that holds it, under the rules for rendered output above, and those of a
fetched recipe change only when the repository changes its `version` and `sha256`. Every builtin recipe is versioned
content of the release, under the same rules. Why: a check whose result changes when someone else publishes a change
fails pipelines that changed nothing, and an entry that changes without a change in the repository cannot be explained
from the repository ([ADR 0013](../adrs/0013-organization-content.md), [ADR 0022](../adrs/0022-tool-integration.md)).

- Moving the pin never applies a recipe's entries or templates. A newer release's additions to a builtin recipe reach
  a repository only when someone applies it again: `./cook recipe apply node --dry-run` shows what it would add,
  and applying adds only what the repository lacks. A newer template is written only where its file is absent; where
  the file exists, applying reports `kept <path>: exists` and leaves it, because the file is the repository's own.
- Moving the pin re-renders every builtin recipe in use, so a newer release's renders and floor rules reach review in
  the upgrade's diff ([above](#rendered-output-across-releases)).
- Nothing records which entries a recipe added, so no command reports that a recipe gained entries. Why: such a report
  would nag a repository that removed an entry on purpose. A repository that wants a newer recipe's entries applies it
  again and reads the diff.

## Adoption equivalence

`the-cook scaffold --adopt` promises that adopting `the-cook` changes no permission a project already enforces. Why: a
lost deny widens what agents may do and a lost allow stalls unattended runs on prompts, so an adoption that drops rules
is worse than none ([ADR 0016](../adrs/0016-permission-adoption.md)).

- After importing, the binary renders the policy and compares the effective rule set per agent with the set in effect
  before adoption. Every project-scope rule (one in the repository's committed agent settings, such as
  `.claude/settings.json` or `.codex/rules/*.rules`) stays in effect, or adoption exits 2 naming each rule that would be
  lost, and writes nothing ([the equivalence check](../features/scaffold.md#the-equivalence-check)).
- An entry that would weaken a floor rule of a recipe in use (allowing a bypass argument the claude recipe marks, or a
  force push to the default branch, a floor rule of the git recipe) is dropped and reported. It is the only
  project-scope rule adoption does not carry over, because floor rules hold in every repository that uses the recipe.
- Exact duplicates merge. The same pattern under allow and deny resolves to deny and becomes a
  [to-do](../features/scaffold.md#the-to-do) item, because the narrower reading is the safe one until a person decides.
- Every imported entry carries a provenance comment naming its source file and scope
  (`# imported from .claude/settings.json (project)`), so a reviewer can trace it.
- Importing the `local` or `user` scope (`--import local`, `--import user`) publishes personal rules: they move from
  one person's machine into a committed, shared `policy.yaml`. Each such entry is marked for review in the to-do. The
  source files are read and never written.
- Settings that are not policy (`defaultMode`, `additionalDirectories`, Codex `approval_policy` and `sandbox_mode`, MCP
  server definitions, enterprise or managed settings) are reported as notes and left in place, because they are not
  permission rules and `the-cook` does not own them.
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
- While deprecated, it prints one stderr line per invocation naming the replacement
  ([cli-standards.md](cli-standards.md#deprecation)), and `the-cook doctor` reports deprecated keys in repository files.
- A schema is readable for at least the same window after its successor appears. Its migration is available for as
  long as it is readable.
- Deprecation never changes stdout, JSON output or exit codes, because a warning must not break what it warns.

## Supported agents and tools

- `the-cook` supports the agents the release ships harness recipes for, `claude` (Claude Code) and `codex` (Codex CLI),
  and any agent a repository describes with a fetched harness recipe. The
  [harness contract](../features/recipes.md#the-harness-contract) such a recipe fills is public
  ([ADR 0009](../adrs/0009-agent-support.md)).
- Every supported version range lives in the checks of the recipe that describes the program: git's in the git recipe,
  Claude Code's in the claude recipe, mise's in the mise recipe. A program no recipe in use describes has no range,
  because the binary contains code for no program ([ADR 0022](../adrs/0022-tool-integration.md)).
- `the-cook doctor` runs the version command of each recipe in use and reports each program outside its recipe's range
  as a finding (exit 2), naming the version found and the range supported. `the-cook` never renders silently for an
  unsupported version, because output rendered for a format the program no longer reads fails without a message.
- `the-cook doctor` also reports, as findings, a program that a recipe, a declared command, lane, validator or action
  names and that is not on `PATH`, and a program whose recipe's detect rule matches the repository while the recipe is
  not in `recipes:`. Why: a declared command that names a missing program fails for everyone who runs it, which needs
  no knowledge of the program to report, and a program in use without its recipe runs without that recipe's floor.
- Each builtin recipe is tested in the project's CI against the program versions the features reference names
  ([compatibility](../features/compatibility.md#versions)): the git recipe's operations, the harness recipes' renders,
  launches and hook payload mappings, the ralph-tui and Beads recipes end to end. Why: a recipe writes its program's
  format and carries its floor, so the release stands behind both only where it tested them.
- `the-cook doctor` reports every fetched recipe in use as not verified by the release, because no test of the project
  stands behind it: its author owns its ranges and its floor.
- Each harness recipe documents which agent events it renders for hooks, notifications (`attention`, `turn_end`) and
  standing context. Where an agent lacks an event, nothing is rendered for it and `the-cook doctor` reports a note, so
  a missing notification or injection is explained.
- Each release carries the ranges of its builtin recipes, and `the-cook doctor --json` lists the range of every recipe
  in use, because a range belongs to the recipe version that was tested against it. The features reference names the
  versions the documentation describes ([compatibility](../features/compatibility.md#versions)).

## Transparency for people and CI that do not use `the-cook`

An adopted repository behaves for non-users exactly as it did before adoption
([ADR 0010](../adrs/0010-dependence-on-the-binary.md)):

- A clone builds, tests, deploys and starts its agents without the binary.
- Implicit entry points ([cli-standards.md](cli-standards.md#entry-points)) degrade: rendered agent hooks run
  `./cook --no-download`, never download, and exit 0 after one stderr line naming `./cook` when no binary is
  cached. The session-start hook also adds one line to the agent's context saying the pinned version is not cached and
  how to fetch it; the wrapper prints both lines itself.
- On a machine without the cached binary, hook-delivered standing context and notification actions do nothing.
  Standing-context entries declared `static` are written into `AGENTS.md` and reach agents without the binary, so a
  directive that must always apply does not depend on it.
- Explicit entry points fetch or fail: `./cook <command>` and a global `the-cook` download and verify the pinned
  version on first use. When they cannot, they exit non-zero naming the cause, so a
  skipped verification never reads as a pass.
- Raw hooks declared in `hooks.yaml` render as native entries that run their own command, so they keep working
  without the binary.
- Actions fire only through the binary. Without it, a person pushes, opens pull requests and cleans up by hand,
  exactly as before adoption.
- No generated file adds a runtime dependency, a required CI step, or a change to build, test, deploy or release
  outputs.
- CI never needs the binary or the wrapper. A CI that opts in runs `./cook render --check` and `./cook doctor`.
  They need the network only to download the pinned version (verified against its checksum) and to re-verify its signed
  checksum file. A repository that declares a fetched recipe adds `./cook recipe fetch` before them, which downloads
  the recipe and verifies it against its `sha256`. The check for a newer release ([update notices](#update-notices))
  never affects their result.
- Managed blocks are plain Markdown or comments and read correctly without `the-cook`. Convention blocks state the
  repository's conventions as fact, and `the-cook remove --eject` keeps them as the repository's own text
  ([principles.md](principles.md#8-conventions-outlive-the-tool)).

## Coexistence

`the-cook` shares repositories with other tools and conventions. It adapts to them, never the reverse, because their
owners depend on them as they are ([ADR 0012](../adrs/0012-write-boundary.md)).

| Already present | Behaviour of `the-cook` |
| --- | --- |
| A hosting platform's issues, projects, boards, comments, reviews and labels as the record of work | never written; the binary has no logic for the platform that hosts the team's work; task state `the-cook` creates stays clone-local; linking work to issues is an agent's job |
| Pull requests, pushes, CI conventions and notification channels | produced only by actions the repository declares in `hooks.yaml`; the binary ships none |
| MCP servers an agent is configured to use | never contacted by `the-cook`; work that needs them is an `agent` action or the job of the [coordinating agent](../features/lifecycle.md#the-lifecycle-stage-by-stage) |
| An existing `.beads/` | never read, initialised or configured by the binary; actions the repository declares, such as those of the `beads` recipe, run `bd` on it, and what they write travels wherever the repository synchronises it ([ADR 0022](../adrs/0022-tool-integration.md)) |
| A `.ralph-tui/config.toml` and a global ralph-tui configuration (`~/.config/ralph-tui/config.toml`) | never read; the `ralph-tui` recipe's template writes `.ralph-tui/config.toml` only when it is absent, and from then on it is the repository's file. Why: the binary reads no program's configuration; a bypass argument set there reaches `the-cook worker`, which refuses it at the first task ([ADR 0022](../adrs/0022-tool-integration.md)) |
| A loop runner, a tracker or a script around them that a person runs outside the binary | neither detected nor prevented; the invariants hold for what the binary renders and for every agent the binary starts (`the-cook worker`, `agent` actions) ([ADR 0022](../adrs/0022-tool-integration.md)) |
| `.claude/worktrees/` | Claude Code's; never scanned or cleaned |
| A task runner's tasks and tool list (`mise.toml`, `.mise/tasks/`, `.tool-versions`, a `Makefile`, `package.json` scripts) | never read or written, though a file's presence can match a recipe's detect rule (`mise.toml` for the mise recipe); a task that runs `./cook` is the repository's own; a `the-cook` a tool installs globally defers to `wrapper.yaml` inside the repository ([ADR 0022](../adrs/0022-tool-integration.md)) |
| A `.cook/` directory another program created | never written; `the-cook scaffold` refuses with exit 3 and names the conflict |
| Another program named `the-cook` earlier on `PATH` | `./cook` always runs its own pinned binary from its cache, so wrapper invocations are unaffected; only the optional global command can be shadowed, and `the-cook doctor` reports it as a note |
| `core.hooksPath`, Husky and other git hooks | never changed |
| `CLAUDE.md` with content | left as written; only the one-line `import` block is added |
| Sections other tools write into `AGENTS.md`, and entries other tools or people write into `.gitignore` | untouched; `the-cook` writes only inside its own markers |
| `.agents/` content from other tools and people (skills installed by the `skills` CLI, memory, commands, `REVIEW.md`) | untouched; `the-cook` owns only `.agents/skills/the-cook/` and never puts its configuration or state in `.agents/` |
| `skills-lock.json` from the `skills` CLI | never read or written; standing context delivers a skill as the file committed under `.agents/skills/` ([ADR 0022](../adrs/0022-tool-integration.md)) |
| An agent's own description-based skill loading | left as it is; a skill injected by standing context may also load by its description, so injected skills are written to read correctly twice ([skills-standards.md](skills-standards.md#skills-used-as-standing-context)) |
| A global `the-cook` install of another version | defers to the repository's pin inside the repository; runs itself elsewhere |
| Hand-written `.claude/settings.json` or Codex configuration | merge-rendered: the renders of the claude and codex recipes own their keys, everything else is preserved |
| Real `.claude/skills` or `.claude/commands` directories, and whole-directory links | left as they are; `the-cook` links per entry, for example `.claude/skills/the-cook -> ../../.agents/skills/the-cook` |
| Local override files (`settings.local.json`, `*.local.toml`) | never written; read only by `the-cook scaffold --import local` |
| User-level agent settings (`~/.claude/settings.json`, `~/.codex/rules/`) | never written; read only by `the-cook scaffold --import user` |

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
  host, and the binary carries no platform logic ([ADR 0012](../adrs/0012-write-boundary.md)).

### Release trust

Why: a checksum copied by whoever upgrades proves only that the download matches what they saw. It does not prove the
release came from its publisher. Signed checksum files, verified by a binary that is already trusted, carry that proof
from one release to the next ([ADR 0021](../adrs/0021-release-trust.md)).

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
  <tag>` completes it through the same verification, so deferring the check never skips it
  ([ADR 0017](../adrs/0017-engine-version.md)).
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
  not verify produces no notice, so a compromised host cannot advertise a release
  ([ADR 0017](../adrs/0017-engine-version.md)).
- The check never blocks: a network failure or timeout is silent, and the check never changes a command's exit code
  or stdout, because a notice must never break the work it accompanies.
- A notice is one stderr line on an explicit command, and a `the-cook doctor` note, naming the pinned version, the
  available version and the exact upgrade command with its tag (`./cook wrapper --version <tag>`).
- Notices never appear in an agent's session context and never come from hooks. Why: moving the pin is a reviewed
  supply-chain decision, and a notice in an agent's context invites the agent to make it.
- `COOK_NO_UPDATE_CHECK=1` disables the check on a machine.
- `the-cook version --check-update` checks on request.
