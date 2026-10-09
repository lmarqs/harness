# Compatibility standards

What harness promises to the people, agents, CI systems and repositories that depend on it, and how those promises
change.

## Versioning

| Artifact | Version scheme | Where the version appears |
| --- | --- | --- |
| The binary and its CLI | semantic versioning: `MAJOR.MINOR.PATCH` | `harness version`, release tags |
| Each repository file (`harness.yaml`, `policy.yaml`, `hooks.yaml`) | an integer `schema`, per file | the file's first key |
| Rendered files and managed blocks | the schema of their sources | the generated-file header and the manifest |

- Rendered content carries the schema, never the binary version, so upgrading the binary alone never makes
  `render --check` fail.
- The public contract covered by semantic versioning is: command names and grammar, flags, operands, enumerated values,
  `HARNESS_*` environment variables (those harness reads and those it passes to lifecycle actions), exit-code
  meanings, JSON output shapes, `harness explain` topic names, the repository file schemas, lifecycle event names and
  trigger points, managed-block ids and markers, the generated-file header, and the manifest location.
- Human-readable output text is not part of the contract and may change in any release.

## What counts as breaking

| Change | Breaking | Not breaking |
| --- | --- | --- |
| Commands and flags | removing or renaming; changing an operand's meaning; making an optional flag required | adding a command, a flag or an enumerated value |
| Exit codes | changing the meaning of a code; mapping an existing situation to a different code | mapping a new situation to an existing code |
| Lifecycle | removing or renaming an event or an action variable; moving a trigger point; changing the `auto` default | adding an action variable |
| JSON output | removing, renaming or retyping a field; changing array order | adding a field |
| Repository files | removing, renaming or redefining a key; changing a default; tightening validation of values that used to be accepted | adding an optional key whose absence keeps existing behaviour |
| Rendered output | different bytes for unchanged inputs and schema | different bytes because the inputs changed |
| Managed blocks and header | changing marker syntax, a block id or the header line | adding a new block id |
| Invariants | weakening one (never allowed) | adding one; it changes rendered output and therefore follows the rendered-output rule |
| Supported agents and tools | removing an agent adapter | adding an agent or a version; narrowing a version range in a minor release, listed in its notes |

### Rendered output across releases

Within one major version and one schema, rendered bytes for unchanged inputs do not change. When an agent's own
format change forces different output, or a new invariant adds an entry, the release is treated as breaking: its notes
name the affected files, and `harness render --check` and `harness doctor` report the drift until `harness render`
runs.

The harness skill is the one owned file whose content follows the binary: `harness render` refreshes it, and a skill
that differs from the embedded one only because the binary is newer is a `doctor` note, not drift
([generated-content-standards.md](generated-content-standards.md#idempotency-and-drift)).

### Presets across upstream changes

A preset is an input, and its version is recorded like any other input:

- The manifest records the commit every preset resolved to. `harness render` and `harness render --check` reuse that
  commit, so a change upstream never fails a check or changes rendered bytes by itself.
- `harness render --refresh` re-resolves presets whose `extends` has no `@<ref>`, records the new commits, and renders;
  the resulting diff reaches review like any other change.
- A preset pinned with `@<ref>` changes only when the repository edits the ref.
- `harness doctor` reports a newer upstream commit for an unpinned preset as a note, never as a finding.

## Adoption equivalence

`harness scaffold --adopt` promises that adopting harness changes no permission a project already enforces:

- After importing, the binary renders the policy and compares the effective rule set per agent with the set in effect
  before adoption. Every project-scope rule stays in effect, or adoption exits 2 naming each rule that would be lost,
  and writes nothing.
- An entry that would weaken a built-in invariant (allowing a permission-bypass flag, or a force push to the default
  branch) is dropped and reported; it is the only project-scope rule adoption does not carry over.
- Exact duplicates merge. The same pattern under allow and deny resolves to deny and becomes a to-do item.
- Every imported entry carries a provenance comment naming its source file and scope.
- Importing the `local` or `user` scope (`--import local`, `--import user`) publishes personal rules: they move from
  one person's machine into a committed, shared `policy.yaml`. Each such entry is marked for review in the to-do. The
  source files are read and never written.
- Settings that are not policy (`defaultMode`, `additionalDirectories`, Codex `approval_policy` and `sandbox_mode`, MCP
  server definitions, enterprise or managed settings) are reported as notes and left in place.
- `--dry-run` prints the generated `policy.yaml` and `hooks.yaml` and the equivalence report, and writes nothing.

## Releases and deprecation

- Breaking changes ship only in a major release. Before 1.0, they MAY ship in a minor release and are listed first in
  its notes.
- Patch releases contain fixes only and never change rendered output for unchanged inputs.

### Deprecation

- A command, flag, value, environment variable or file key is deprecated before it is removed. It keeps working for at
  least one minor release before 1.0, and until the next major release from 1.0.
- While deprecated, it prints one stderr line per invocation naming the replacement
  ([cli-standards.md](cli-standards.md#deprecation)), and `harness doctor` reports deprecated keys in repository files.
- A schema is readable for at least the same window after its successor appears. Its migration is available for as
  long as it is readable.
- Deprecation never changes stdout, JSON output or exit codes.

## Supported agents and tools

- harness supports the agents it has adapters for: `claude` (Claude Code) and `codex` (Codex CLI). Each adapter has a
  public contract and a documented range of supported agent versions.
- The tools harness drives (`bd`, `ralph-tui`, `mise`, the `skills` CLI) also have documented supported ranges.
- `harness doctor` compares installed versions against these ranges and reports each one out of range as a finding
  (exit 2), naming the version found and the range supported. harness never renders silently for an unsupported
  version.
- The supported ranges are published in the features reference and in `harness doctor --json`.

## Transparency for people and CI that do not use harness

An adopted repository behaves for non-users exactly as it did before adoption:

- A clone builds, tests, deploys and starts its agents without the binary.
- Implicit entry points degrade: rendered agent hooks exit 0 after one stderr line when the binary is missing.
- Explicit entry points fail: a mise wrapper run without the binary says that harness is missing and how to install
  it, and exits 1, so a skipped verification never reads as a pass.
- Raw hooks declared in `hooks.yaml` render as native entries that run their own command, so they keep working
  without the binary.
- Lifecycle actions fire only through the binary. Without it, a person pushes, opens pull requests and cleans up by
  hand, exactly as before adoption.
- No generated file adds a runtime dependency, a required CI step, or a change to build, test, deploy or release
  outputs.
- CI never needs the binary. `harness render --check` and `harness doctor` are optional steps. They need the network
  only to fetch a preset that is not cached, and the release check never affects their result.
- Managed blocks are plain Markdown or comments and read correctly without harness.

## Coexistence

harness shares repositories with other tools and conventions. It adapts to them, never the reverse.

| Already present | harness behaviour |
| --- | --- |
| A hosting platform's issues, projects, boards, comments, reviews and labels as the record of work | never written; the binary has no hosting-platform logic; tasks stay clone-local; linking work to issues is an agent's job |
| Pull requests, pushes and CI conventions | produced only by lifecycle actions the repository or its preset declares; the binary ships none |
| An existing `.beads/` | used as found; never initialised again, localised or reconfigured |
| A root `.ralph-tui/` and a global ralph-tui configuration | never read or written by harness-launched runs, unless the profile sets `ralph.config: inherit` |
| Branches and worktrees from direct ralph-tui runs | `harness ralph clean` touches only sessions harness recorded |
| `.claude/worktrees/` | Claude Code's; never scanned or cleaned |
| An existing mise task with a name harness would use | never overwritten; harness generates `harness:<name>` and lists the collision as a to-do |
| `core.hooksPath`, Husky and other git hooks | never changed |
| `CLAUDE.md` with content | left as written; only the one-line `import` block is added |
| Sections other tools write into `AGENTS.md` | untouched; harness writes only inside its own markers |
| Hand-written `.claude/settings.json` or Codex configuration | merge-rendered: harness owns its keys, everything else is preserved |
| Real `.claude/skills` or `.claude/commands` directories, and whole-directory links | left as they are; harness links per entry |
| Local override files (`settings.local.json`, `*.local.toml`) | never written; read only by `harness scaffold --import local` |
| User-level agent settings (`~/.claude/settings.json`, `~/.codex/rules/`) | never written; read only by `harness scaffold --import user` |

A new feature MUST add its row to this table when it meets a file, directory, branch or setting another tool may own.

## Installation and version skew

- harness is installed per machine (`mise use -g github:lmarqs/harness@<tag>` or a release download). A repository
  MAY pin a version in its own `mise.toml`.
- Different machines may run different versions against the same repository. The schema rules in
  [configuration-standards.md](configuration-standards.md#schema-versioning) make this safe: an older binary refuses a
  newer file with a message naming the update command; it never renders something different.

## Update notices

- `harness doctor` and the SessionStart hook check for a newer release at most once per day per machine, using a
  cached result.
- The check never blocks: a network failure or timeout is silent, and the check never changes a command's exit code
  or stdout.
- A notice is one stderr line naming the running version, the available version and the update command.
- `HARNESS_NO_UPDATE_CHECK=1` disables the check on a machine. `session_start.update_notice: false` in `hooks.yaml`
  disables the notice in a repository's sessions.
- `harness version --check-update` checks on request.
