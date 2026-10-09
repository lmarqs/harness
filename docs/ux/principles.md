# User-facing principles

Every other page in these standards applies one or more of the principles below. When a case is not covered by a
specific rule, decide it from these principles and record the decision in a [UX record](README.md#proposing-a-change-the-ux-record).

## 1. Optional and transparent

Nobody suffers for not using harness.

- A clone of an adopted repository MUST build, test, deploy and start its agents without the binary installed.
- Implicit entry points degrade quietly; explicit ones fail loudly. A hook rendered by harness (an implicit entry
  point the agent fires on its own) MUST exit 0 after printing one line to stderr when the binary is missing. A mise
  wrapper (an explicit entry point a person or agent runs on purpose) MUST exit 1 and name the install command when
  the binary is missing, so a skipped verification never reads as a pass.
- CI MUST NOT need the binary. `harness render --check` and `harness doctor` are optional CI steps a repository MAY add.
- Files harness writes MUST NOT add a runtime dependency, a required CI step, or a change to build, test, deploy or
  release outputs.
- Adoption and removal are per repository and per module. Removing harness leaves a repository that works with what
  remains (see [generated-content-standards.md](generated-content-standards.md#removal)).

Architecture background: the ADR on optional and transparent adoption in [../adrs/](../adrs/).

## 2. The binary is deterministic; judgement belongs to agents

From the user's side, the split reads: **whatever harness does, you can predict; whatever needs judgement, an agent
does and harness checks.**

- Every binary command produces the same result for the same inputs on every supported machine. A user can read the
  three repository files and know what `harness render` will write.
- When the binary meets something it cannot decide without judgement (an unrecognised hook, a task name collision, a
  `CLAUDE.md` with content), it MUST NOT guess. It leaves the item untouched and lists it as a to-do
  (`harness doctor --json`) that an agent or a person resolves.
- Every judgement step an agent performs ends in a deterministic check the binary owns: `harness render --check`,
  `harness doctor`, `harness verify --check`.

Where a given behaviour belongs is decided by [when-cli-when-skill.md](when-cli-when-skill.md). Architecture
background: the ADR on deterministic work in the binary and variable work to agents.

## 3. Sets the rules, never plays

harness is a meta-harness (see the [glossary](../features/glossary.md)): it configures and constrains the tools that
run models, and it never takes their place.

- The binary never calls a model.
- The binary never writes knowledge: no prose, no memory entries, no domain skills, no policy entries invented on the
  user's behalf. It writes rendered files, managed blocks with derived content, and the three repository files only
  when seeding or migrating them.
- The binary contains no logic for any hosting platform and never writes the team's record of work: no issues,
  projects, boards, comments, reviews or labels. Everything that leaves the clone, a pull request included, is a
  declared lifecycle action ([principle 7](#7-nothing-leaves-the-clone-undeclared)).
- The binary never reconfigures state it did not create: an existing `.beads/`, a root `.ralph-tui/`,
  `core.hooksPath`, existing mise tasks, `CLAUDE.md` content.

## 4. Least surprise

- A command does what its name says and nothing else. Read commands (`doctor`, `explain`, `version`,
  `lifecycle list`, `render --check`, `verify --check`) MUST NOT modify the repository, fire lifecycle events or run
  actions.
- The same invocation in the same state gives the same result. Running a mutation twice changes nothing the second
  time and says so.
- Environment and machine affect presentation (color, notices, verbosity), never the bytes written into a
  repository. A binary upgrade changes those bytes only under the rules in
  [compatibility-standards.md](compatibility-standards.md#rendered-output-across-releases).
- No hidden network access: the binary's own network calls are preset fetches declared by `extends`, the daily
  release check and `skills sync` (through the `skills` CLI); each is documented and the release check can be
  disabled. Any other network access is a declared lifecycle action, visible in `harness lifecycle list`.
- Names, flags and output look the same across commands ([cli-standards.md](cli-standards.md)).

## 5. Never destroy; always show what will change

- Every mutating command accepts `--dry-run` and prints exactly the change set it would apply.
- harness writes only inside content it owns: files and blocks recorded in the manifest, and the native keys it
  owns. Everything else is preserved byte for byte.
- When owned content has been edited by hand, `harness remove` refuses to delete it unless `--force` is given, and
  names each path it refused.
- Every change harness makes outside its gitignored state roots (`.work/`) lands in versioned files, so it reaches
  review as a diff.
- Removal is deterministic: it deletes what the manifest lists and nothing else.

## 6. One way to do each thing

- Each operation has one command and one spelling. There are no command aliases.
- Each fact lives in one file: permissions in `policy.yaml`, agent hooks and lifecycle actions in `hooks.yaml`,
  everything else harness needs in `harness.yaml`. Rendered files are outputs, never a second place to edit.
- A repository has one documented front door: either mise wrappers or the binary (`front_door` in the profile).
  Both reach the same implementation; the `AGENTS.md` command block shows only the chosen one.
- A skill never re-describes what a command does deterministically; it calls the command
  ([skills-standards.md](skills-standards.md)).

## 7. Nothing leaves the clone undeclared

The binary contains no logic for any hosting platform. Everything that leaves the clone (a push, a pull request, a
CI trigger, a notification) happens only through a **lifecycle action**: a command that the repository or one of its
presets declares on a lifecycle event (`start`, `verified`, `completed`, `deliver`, `landed`). Stages, trigger points
and the full reference: [lifecycle guide](../features/lifecycle.md).

- **Declared.** Actions live in `.agents/hooks.yaml` or a preset. The binary ships no actions, so what "deliver" means
  is decided by the repository and its organization.
- **Visible.** `harness lifecycle list` shows every event, action, scope, `auto` flag and source (repository or
  preset).
- **Dry-runnable.** `harness lifecycle run <event> --dry-run` prints each action and the `HARNESS_*` environment it
  would receive, and runs nothing.
- **Recorded.** Every run appends its event, scope, actions, exit statuses and duration to `.work/lifecycle.jsonl`
  in the primary checkout. Dry runs are not recorded. A failing automatic event never changes the exit code of the
  command that fired it; it is reported on stderr and recorded.
- **One action, three triggers.** A person runs `harness lifecycle run <event>`; a coordinating agent runs the same
  command; or harness fires the action at the event's trigger point when the action sets `auto: true`. `auto`
  defaults to false, so nothing leaves the clone automatically unless a committed file says so.
- **Guarded.** Actions never run inside a Ralph worker, and `deliver` always refuses on the default branch and with
  uncommitted changes and, when the verify module is enabled, without a valid receipt. These guards are built-in
  invariants.
- **Code, not settings.** Actions are committed and reviewed like code. They receive values only as environment
  variables, never substituted into the command string.
- **Transparent.** Without the binary no action fires; a person pushes and opens pull requests by hand, exactly as
  before adoption.

Permission to run `harness lifecycle run <event>` is permission for everything that event's actions do: the actions
run as child processes of harness, outside the agent's per-command permission checks. The scaffolded repository policy
therefore seeds `harness lifecycle run deliver` under `ask`, and the repository MAY move it.

## When principles conflict

Apply them in this order:

1. The built-in invariants (see the ADR on invariants). No principle, setting, preset or flag weakens them.
2. Never destroy.
3. Nothing leaves the clone undeclared.
4. Optional and transparent.
5. Deterministic in the binary, judgement to agents.
6. Least surprise.
7. One way to do each thing.

Example: a convenient default that would overwrite a hand-edited file loses to "never destroy"; the command refuses
and names `--force` instead.
