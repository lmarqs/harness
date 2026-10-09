# Compatibility standards

What harness promises to the people, agents, CI systems and repositories that depend on it, and how those promises
change. Why: many repositories and machines depend on one binary, so every promise it breaks is broken everywhere at
once.

## Versioning

| Artifact | Version scheme | Where the version appears |
| --- | --- | --- |
| The binary and its CLI | semantic versioning: `MAJOR.MINOR.PATCH` | `harness version`, release tags |
| Each repository file (`config.yaml`, `policy.yaml`, `hooks.yaml`, `wrapper.yaml`) | an integer `schema`, per file | the file's first key |
| The version a repository runs | an exact release tag, pinned per repository | `version` in `.harness/wrapper.yaml` |
| Rendered files and managed blocks | the schema of their sources | the generated-file header and the manifest |

- Rendered content carries the schema, never the binary version ([ADR 0005](../adrs/0005-schema-compatibility.md)).
  It depends on the sources, the recorded preset commits and the pinned release, and `harness wrapper --version <tag>`
  renders the repository with the new release in the same change as the pin, so moving the pin never leaves drift
  behind ([ADR 0017](../adrs/0017-engine-version.md)). Why: a version stamp would change every file on every upgrade
  and bury the changes that matter.
- The public contract covered by semantic versioning is: command names and grammar, flags, operands, enumerated values,
  `HARNESS_*` environment variables (those harness and its wrapper read and those it passes to actions), exit-code
  meanings, JSON output shapes, `harness explain` topic names, the repository file schemas (`wrapper.yaml` included),
  the wrapper's name, location and flags (`harnessw` at the repository root, `--no-download`), lifecycle event names
  and trigger points, notification event names, action kinds and their keys, standing-context triggers and roles, the
  profile's state path keys and their defaults, managed-block ids, their markers and their class (convention or tool),
  the generated-file header, the manifest location, and the format of the release's signed checksum file. Why: each of
  these is read by scripts, agents, policy entries or other repositories, and changes there break them.
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
| Rendered output | for unchanged inputs and schema, a change in what the rendered files make an agent do, other than a new invariant | for unchanged inputs and schema, different bytes that leave agent behaviour unchanged (the harness skill's text, formatting), shipped in a minor release and reviewed in the upgrade diff; different bytes because the inputs changed |
| Managed blocks and header | changing marker syntax, a block id, a block's class or the header line | adding a new block id |
| Invariants | weakening one (never allowed) | adding one; it changes rendered output and therefore follows the rendered-output rule |
| Supported agents and tools | removing an agent adapter | adding an agent or a version; narrowing a version range in a minor release, listed in its notes |
| Release trust | removing a key a released binary trusts without a rotation release | introducing the next key in a release signed by the current one |

Moving a block between the convention and tool classes is breaking because it changes what `harness remove --eject`
keeps in a repository.

### Rendered output across releases

Rendered bytes depend on the repository files, the recorded preset commits and the pinned release. A patch release
never changes them for unchanged inputs. A minor release may: the harness skill teaches the commands of the release
that renders it, and a rendering fix changes bytes. When an agent's own format change forces different output, or a new
invariant adds an entry, the release is breaking and its notes name the affected files.

Every such change reaches a repository the same way: `harness wrapper --version <tag>` renders the repository with the
new release in the same change as the pin, `harnessw` and the harness skill included, so the pin and its effects reach
review as one diff. The drift check is run by the pinned release, and no owned file is exempt from it. A binary other
than the pinned release (`HARNESS_BINARY`) gets a `harness doctor` note naming both versions, because its drift
results may differ. Why: with upgrades rendered in their own diff, the pinned release's output and the repository
always agree, and an exemption would hide exactly the hand edits the check exists to catch
([ADR 0007](../adrs/0007-ownership.md)).

### Presets across upstream changes

A preset is an input, and its version is recorded like any other input. Why: a check whose result changes when
someone else publishes a commit fails pipelines that changed nothing ([ADR 0013](../adrs/0013-organization-content.md)).

- The manifest records the commit every preset resolved to. `harness render` and `harness render --check` reuse that
  commit, so a change upstream never fails a check or changes rendered bytes by itself.
- `harness render --refresh` re-resolves presets whose `extends` has no `@<ref>`, records the new commits, and renders;
  the resulting diff reaches review like any other change.
- A preset pinned with `@<ref>` changes only when the repository edits the ref.
- `harness doctor` reports a newer upstream commit for an unpinned preset as a note, never as a finding, because
  staying on a reviewed commit is not a failure.

## Adoption equivalence

`harness scaffold --adopt` promises that adopting harness changes no permission a project already enforces. Why: a lost
deny widens what agents may do and a lost allow stalls unattended runs on prompts, so an adoption that drops rules is
worse than none ([ADR 0016](../adrs/0016-permission-adoption.md)).

- After importing, the binary renders the policy and compares the effective rule set per agent with the set in effect
  before adoption. Every project-scope rule stays in effect, or adoption exits 2 naming each rule that would be lost,
  and writes nothing.
- An entry that would weaken a built-in invariant (allowing a permission-bypass flag, or a force push to the default
  branch) is dropped and reported; it is the only project-scope rule adoption does not carry over, because the
  invariants hold in every repository.
- Exact duplicates merge. The same pattern under allow and deny resolves to deny and becomes a to-do item, because the
  narrower reading is the safe one until a person decides.
- Every imported entry carries a provenance comment naming its source file and scope, so a reviewer can trace it.
- Importing the `local` or `user` scope (`--import local`, `--import user`) publishes personal rules: they move from
  one person's machine into a committed, shared `policy.yaml`. Each such entry is marked for review in the to-do. The
  source files are read and never written.
- Settings that are not policy (`defaultMode`, `additionalDirectories`, Codex `approval_policy` and `sandbox_mode`, MCP
  server definitions, enterprise or managed settings) are reported as notes and left in place, because they are not
  permission rules and harness does not own them.
- A harness pin in a task runner's tool list (`mise.toml`, `.mise.toml`, `.tool-versions`) is copied into
  `wrapper.yaml`, and removing it from that file is listed as a to-do. harness never edits the task runner's file,
  because it did not create it.
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
  ([cli-standards.md](cli-standards.md#deprecation)), and `harness doctor` reports deprecated keys in repository files.
- A schema is readable for at least the same window after its successor appears. Its migration is available for as
  long as it is readable.
- Deprecation never changes stdout, JSON output or exit codes, because a warning must not break what it warns.

## Supported agents and tools

- harness supports the agents it has adapters for: `claude` (Claude Code) and `codex` (Codex CLI). Each adapter has a
  public contract and a documented range of supported agent versions
  ([ADR 0009](../adrs/0009-agent-support.md)).
- The tools harness drives (`bd`, `ralph-tui`, `mise`, the `skills` CLI) also have documented supported ranges.
- `harness doctor` compares installed versions against these ranges and reports each one out of range as a finding
  (exit 2), naming the version found and the range supported. harness never renders silently for an unsupported
  version, because output rendered for a format the agent no longer reads fails without a message.
- Each adapter documents which agent events it renders for hooks, notifications (`attention`, `turn_end`) and
  standing context. Where an agent lacks an event, nothing is rendered for it and `harness doctor` reports a note, so
  a missing notification or injection is explained, not silent.
- The supported ranges are published in the features reference and in `harness doctor --json`.

## Transparency for people and CI that do not use harness

An adopted repository behaves for non-users exactly as it did before adoption
([ADR 0010](../adrs/0010-dependence-on-the-binary.md)):

- A clone builds, tests, deploys and starts its agents without the binary.
- Implicit entry points degrade: rendered agent hooks run `./harnessw --no-download`, never download, and exit 0
  after one stderr line naming `./harnessw` when no binary is cached. The session-start hook also adds one line to the
  agent's context saying the pinned version is not cached and how to fetch it; the wrapper prints both lines itself.
- On a machine without the cached binary, hook-delivered standing context and notification actions do nothing.
  Standing-context entries declared `static` are written into `AGENTS.md` and reach agents without the binary, so a
  directive that must always apply does not depend on it.
- Explicit entry points fetch or fail: `./harnessw <command>` and the task shims that exec it download and verify
  the pinned version on first use; when they cannot (no downloader, no network, checksum mismatch) they exit 1 naming
  the cause, so a skipped verification never reads as a pass.
- Raw hooks declared in `hooks.yaml` render as native entries that run their own command, so they keep working
  without the binary.
- Actions fire only through the binary. Without it, a person pushes, opens pull requests and cleans up by hand,
  exactly as before adoption.
- No generated file adds a runtime dependency, a required CI step, or a change to build, test, deploy or release
  outputs.
- CI never needs the binary or the wrapper. A CI that opts in runs `./harnessw render --check` and `./harnessw doctor`.
  They need the network only to download the pinned version (verified against its checksum), to re-verify its signed
  checksum file and to fetch a preset that is not cached; the release check never affects their result.
- Managed blocks are plain Markdown or comments and read correctly without harness. Convention blocks state the
  repository's conventions as fact, and `harness remove --eject` keeps them as the repository's own text
  ([principles.md](principles.md#8-conventions-outlive-the-tool)).

## Coexistence

harness shares repositories with other tools and conventions. It adapts to them, never the reverse, because their
owners depend on them as they are ([ADR 0012](../adrs/0012-write-boundary.md)).

| Already present | harness behaviour |
| --- | --- |
| A hosting platform's issues, projects, boards, comments, reviews and labels as the record of work | never written; the binary has no logic for the platform that hosts the team's work; task state harness creates stays clone-local; linking work to issues is an agent's job |
| Pull requests, pushes, CI conventions and notification channels | produced only by actions the repository or its preset declares; the binary ships none |
| MCP servers an agent is configured to use | never contacted by harness; work that needs them is an `agent` action or the coordinating agent's job |
| An existing `.beads/` | used as found; never initialised again, localised or reconfigured; when the repository synchronises it, the claims and comments harness writes there travel where that synchronisation sends them ([ADR 0012](../adrs/0012-write-boundary.md)) |
| A root `.ralph-tui/` and a global ralph-tui configuration (`~/.config/ralph-tui/config.toml`) | never written; read only when the profile sets `ralph.config: inherit`, or to find and name a bypass setting that ralph-tui would apply to a run harness launches, which harness then refuses. Why: ralph-tui merges that configuration into every run it starts, so harness must see it to refuse what it would inject ([ADR 0012](../adrs/0012-write-boundary.md)) |
| Branches and worktrees from direct ralph-tui runs | `harness ralph clean` touches only sessions harness recorded |
| `.claude/worktrees/` | Claude Code's; never scanned or cleaned |
| An existing mise task with a name harness would use | never overwritten; harness generates `harness:<name>` and lists the collision as a to-do |
| A harness pin in `mise.toml`, `.mise.toml` or `.tool-versions` | never edited; `harness doctor` reports it as a finding, because the pin lives only in `wrapper.yaml`; adoption copies it there and lists its removal as a to-do |
| A `.harness/` directory another product created (for example a CI/CD platform's pipeline folder of the same name) | never written; `harness scaffold` refuses with exit 3 and names the conflict |
| Another program named `harness` earlier on `PATH` | `./harnessw` always runs its own pinned binary from its cache, so wrapper invocations are unaffected; only the optional global command can be shadowed, and `harness doctor` reports it as a note |
| `core.hooksPath`, Husky and other git hooks | never changed |
| `CLAUDE.md` with content | left as written; only the one-line `import` block is added |
| Sections other tools write into `AGENTS.md`, and entries other tools or people write into `.gitignore` | untouched; harness writes only inside its own markers |
| `.agents/` content from other tools and people (skills installed by the `skills` CLI, memory, commands, `REVIEW.md`) | untouched; harness owns only `.agents/skills/harness/` and never puts its configuration or state in `.agents/` |
| `skills-lock.json` from the `skills` CLI | read, never written; standing context injects a locked skill only when its installed content matches the locked hash |
| An agent's own description-based skill loading | left as it is; a skill injected by standing context may also load by its description, so injected skills are written to read correctly twice ([skills-standards.md](skills-standards.md#skills-used-as-standing-context)) |
| A global `harness` install of another version | defers to the repository's pin inside the repository; runs itself elsewhere |
| Hand-written `.claude/settings.json` or Codex configuration | merge-rendered: harness owns its keys, everything else is preserved |
| Real `.claude/skills` or `.claude/commands` directories, and whole-directory links | left as they are; harness links per entry |
| Local override files (`settings.local.json`, `*.local.toml`) | never written; read only by `harness scaffold --import local` |
| User-level agent settings (`~/.claude/settings.json`, `~/.codex/rules/`) | never written; read only by `harness scaffold --import user` |

A new feature MUST add its row to this table when it meets a file, directory, branch, setting or service another tool
may own, because coexistence is decided per thing, not in general.

## Installation and version pinning

Why: one pinned release per repository means every machine and pipeline runs the version the repository chose, and a
change of version is a reviewed diff ([ADR 0017](../adrs/0017-engine-version.md)).

- The version is pinned per repository, in exactly one place. `.harness/wrapper.yaml` names an exact release tag, its
  source (default `github:lmarqs/harness`, or a mirror at any HTTPS base URL laid out the same way) and the sha256
  checksum of the release asset for each supported platform
  (`linux-amd64`, `linux-arm64`, `darwin-amd64`, `darwin-arm64`). The committed wrapper `harnessw` runs that version,
  so every machine runs the same version against one repository and version skew within a repository does not occur.
- The wrapper looks for the pinned version in the cache (`$HARNESS_HOME`, default `$XDG_CACHE_HOME/harness`, falling
  back to `~/.cache/harness`, at `<home>/versions/<version>/harness`). When it is absent, the wrapper downloads the raw
  per-platform release asset over HTTPS with `curl` or `wget`, verifies its sha256 against `wrapper.yaml`, and only
  then caches and execs it. Afterwards it runs offline.
- The wrapper MUST download only over HTTPS and MUST NOT cache or execute a binary whose checksum does not match
  `wrapper.yaml`; a mismatch exits 1 and leaves nothing cached. Why: the committed checksum is what the repository
  reviewed, and anything else is not the release it chose.
- First-run requirements are a POSIX `sh`, `curl` or `wget`, and `sha256sum` or `shasum`. They are requirements of the
  wrapper's first run, not runtime dependencies of the binary or of the repository. Windows is not supported.
- A global install of `harness` is optional. Inside a repository whose `wrapper.yaml` pins a different version, it
  execs the pinned version from the cache, downloading it through the same verified path when invoked explicitly and
  never from a hook. Outside such a repository it runs itself.
- `HARNESS_BINARY=<path>` makes the wrapper exec that binary instead of the pinned one (air-gapped machines,
  development of harness itself). It bypasses the pin and its checksum.
- The schema rules in [configuration-standards.md](configuration-standards.md#schema-versioning) apply: a binary
  refuses a repository file newer than it reads, and never renders something different. With the wrapper, this
  refusal occurs only when the pin is bypassed (`HARNESS_BINARY`, or a global binary too old to defer to the pin).
- Upgrading is a reviewed change to the repository: `harness wrapper --version <tag>` (or `--latest`) rewrites the
  version and checksums in `wrapper.yaml` and renders the repository with the new release in the same change, under
  the trust rules below. Across repositories: `harness fleet wrapper --version <tag> --deliver`.
- The release source serves plain files over HTTPS at fixed paths: a release's assets, `checksums.txt` and
  `checksums.txt.sig` at `<source>/download/<tag>/`, and the newest release's at `<source>/latest/download/`. No
  platform API is called. Why: a mirror is then a copy of files, for networks that cannot reach the project's release
  host, and the binary carries no platform logic ([ADR 0012](../adrs/0012-write-boundary.md)).

### Release trust

Why: a checksum copied by whoever upgrades proves only that the download matches what they saw; it does not prove the
release came from its publisher. Signed checksum files, verified by a binary that is already trusted, carry that proof
from one release to the next ([ADR 0021](../adrs/0021-release-trust.md)).

- Every release publishes `checksums.txt` (the sha256 of each platform asset) and `checksums.txt.sig`, an ed25519
  signature over it.
- Every binary embeds the list of release public keys it trusts. A release signed by the current key introduces the
  next key; later binaries trust both until the old one is retired.
- `harness wrapper --version <tag>` downloads that release's `checksums.txt` and its signature, verifies the signature
  with the keys embedded in the running binary, and only then writes `version` and `checksums` into `wrapper.yaml`. A
  release whose signature does not verify is refused (exit 1) and nothing is written.
- `harness scaffold` pins the running binary's own version the same way, from the verified checksum file of that
  release, so the first pin is as trustworthy as every later one.
- `harnessw` verifies only the sha256 of the downloaded asset against `wrapper.yaml`. Signatures are checked by
  binaries, never by the shell script, because a POSIX shell has no portable signature tool and the wrapper must run
  on a bare machine.
- The trust root is the first binary a person installs (a release download or a package manager). From then on every
  pin change is verified by an already-trusted binary and is visible as a `wrapper.yaml` diff in review.
- `harness doctor`, when online, re-verifies the pinned release's signed checksum file against `wrapper.yaml` (the
  result is cached for a day) and reports a mismatch as a finding, so a hand-edited checksum does not pass unnoticed.

Wrapper reference: [wrapper](../features/wrapper.md). Installing the optional global binary:
[installation](../features/installation.md).

## Update notices

Why: a repository's pin moves only when someone decides, so people need to learn that a newer release exists without
anything changing on its own.

- Explicit commands and `harness doctor` check for a newer release at most once per day per machine, using a cached
  result, so the check costs one request a day.
- The check reads the newest release's `checksums.txt` and its signature from the release source, verifies the
  signature with the running binary's embedded keys, and takes the tag from that file. A release whose signature does
  not verify produces no notice, so a compromised host cannot advertise a release
  ([ADR 0017](../adrs/0017-engine-version.md)).
- The check never blocks: a network failure or timeout is silent, and the check never changes a command's exit code
  or stdout, because a notice must never break the work it accompanies.
- A notice is one stderr line on an explicit command, and a `harness doctor` note, naming the pinned version, the
  available version and the exact upgrade command with its tag (`./harnessw wrapper --version <tag>`).
- Notices never appear in an agent's session context and never come from hooks. Why: moving the pin is a reviewed
  supply-chain decision, and a notice in an agent's context invites the agent to make it.
- `HARNESS_NO_UPDATE_CHECK=1` disables the check on a machine.
- `harness version --check-update` checks on request.
