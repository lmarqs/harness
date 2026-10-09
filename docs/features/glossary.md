# Glossary

**The game master.** `harness` is to agents what a game master is to a tabletop game: it rules the world and never
plays. Developers are the players, who decide what gets built. Agents are the characters, who act within the rules.
Repositories are the world. The three repository files are the rulebook, and the built-in invariants are the rules no
table can change.

| Term | Meaning |
| --- | --- |
| Action kind | What a declared action does: `run` (a shell command), `http` (an HTTP request `harness` makes itself, with the URL and header values read from environment variables) or `agent` (a headless agent run under the worker contract). Notification hooks take `run` and `http` only. See [lifecycle](lifecycle.md#action-kinds) |
| Actor | The identity a session or a Ralph run uses to claim tasks: `<user>:<worktree>:<random>` for a managed worktree, `ralph:<session-id>` for a run |
| Adapter | The part of `harness` that knows one agent: its native files, permission and hook formats, hook payloads, and safe worker launch. See [agents](agents.md#the-adapter-contract) |
| Adopt | `harness scaffold --adopt`: take over an existing hand-written agent setup, import what can be expressed, list the rest as a to-do |
| Adoption class | Greenfield, brownfield-simple or brownfield-complex: the state a repository starts from. See [concepts](concepts.md#adoption-classes) |
| Agent-content directory | `.agents/`: skills, memory, agent commands and the review guide. A convention shared with other tools, such as the `skills` CLI, that stays meaningful without `harness`. See [concepts](concepts.md#two-directories-two-owners) |
| Always-on context | The [standing context](context.md) every session of a role receives: `always` entries, `static` ones included. Capped by `context.budget` |
| Application change | A change that touches a file matching `verify.app_paths`; it requires lane selection and a build |
| Attention | The notification event for an agent waiting for a person: a permission prompt, a question, idle input. Its `run` and `http` actions are declared under `attention` in `hooks.yaml`; rendered from Claude Code's `Notification` event |
| Base | The commit a change is measured against: a managed worktree's starting commit, or the merge-base with the default branch |
| Base policy | The built-in policy entries every repository gets and none can remove: no force push to the default branch, no mise `:::` chaining, no `harness lifecycle` for workers, in every rendered spelling of the command (`harness`, `./harnessw`, `harnessw`, task shims). The binary enforces the same guards itself; the entries are defence in depth |
| Bare string | A policy string that is not a native rule string, that is, neither `Tool(specifier)`, nor a tool name on its own such as `WebSearch` or `Read`, nor an `mcp__…` name: shorthand for `Bash(<string>)`, Claude-only. See [policy](policy.md#entry-forms) |
| Block | See managed block |
| Blocked | The out-of-band lifecycle event that fires at any stage when work stops unfinished or needs a person: a Ralph run stopping unfinished, a task skipped after its retries, a command without a terminal exiting 3. Its actions receive the reason in `HARNESS_REASON`. See [lifecycle](lifecycle.md#events) |
| Bootstrap | The profile's list of commands that prepares a new checkout |
| Budget | A limit on the lines `harness` adds to what agents read every session: `blocks.agents_md_budget` for the blocks in `AGENTS.md` (default 40, 8 per block), `context.budget` for always-on context (default 120 per role). `doctor` notes an overrun. See [render](render.md#the-agentsmd-budget) |
| Cache | `$HARNESS_HOME`, default `$XDG_CACHE_HOME/harness`, or `~/.cache/harness` when `XDG_CACHE_HOME` is unset: the per-user directory holding downloaded releases at `versions/<version>/harness`, the update-check result and fetched presets |
| Configuration directory | `.harness/`: the profile, policy, hooks file, `wrapper.yaml`, manifest and an optional Ralph prompt. Versioned, never state, never ignored. Both removal modes delete every file `harness` recorded there, and the directory once nothing else is in it |
| Context trigger | What makes a `when` entry of [standing context](context.md) fire: `paths` (the agent first reads or edits a matching file) or `prompt` (the person's message contains a listed word or phrase) |
| Convention block | A managed block that states, as plain fact, a convention that stays true without `harness`, and never names a `harness` command: `worktrees`, `tasks`, `ralph`, `policy`, `memory` and `context` in `AGENTS.md`, `import` in `CLAUDE.md`, every `.gitignore` block. An eject keeps its text. See [concepts](concepts.md#convention-blocks-and-tool-blocks) |
| Coordinator | The isolated worktree in which a harness-launched ralph-tui run executes |
| Default branch | The branch `origin/HEAD` points to |
| Deliver | The lifecycle event that sends concluded work out of the clone through declared actions (push, pull request, CI trigger). Guarded: never on the default branch or with uncommitted changes and, when the verify module is enabled, never without a valid receipt. See [lifecycle](lifecycle.md) |
| Drift | A difference between an owned file, key or block and what the sources produce now with the pinned release; reported by `render --check`. No owned artefact is exempt |
| Eject | `harness remove --eject`: stop managing a repository and keep the result. Convention blocks lose their markers and keep their text; tool blocks, the harness skill, task shims, hooks that run `harnessw` and permission entries that name a `harness` command are removed; `.gitignore` entries, the other permissions and `raw` hooks stay as hand-owned content; `harnessw` and the owned and seeded files of `.harness/` are deleted, and `.harness/` once nothing else is in it. See [remove](remove.md#eject) |
| Equivalence check | The adoption step that compares, per agent, the effective rule set after import with the one before it; a lost project rule makes adoption exit 2. See [scaffold](scaffold.md#the-equivalence-check) |
| `extends` | A profile or policy field naming a preset source, optionally pinned with `@<ref>`, to add on top of the repository's own files |
| Fingerprint | A hash over the base and the path, mode and content of every changed file; binds a receipt to content |
| Fleet | An explicit list of repositories that `harness fleet` runs a command across |
| Front door | How people reach the commands in a repository: task shims (`mise`) or `./harnessw` directly (`binary`) |
| Greenfield | A repository with no agent permissions, hooks or workflow engine of its own |
| Harness skill | `.agents/skills/harness/SKILL.md`: the tool-owned skill teaching agents the standards and their reasoning |
| Hooks file | `.harness/hooks.yaml`: agent-neutral hook declarations, notification hooks and lifecycle actions |
| Import scope | Where `scaffold --import` reads existing configuration: a comma-separated set from `project`, `local`, `user`, `none`; `project` by default with `--adopt`, `none` without it |
| Interactive | An agent role: people use the agent in this repository |
| Invariant | A rule built into `harness` that no repository file or preset can turn off. See [concepts](concepts.md#built-in-invariants) |
| Issue | A GitHub issue: part of the team's record of work. `harness` never writes issues |
| Knowledge | Prose, memory and skills written by people and agents; never generated by `harness` |
| Inject | How a standing-context entry reaches a session: `content` (the text itself) or `pointer` (one line naming the skill or file to read now) |
| Lifecycle action | A named action a repository or preset declares under an event in `hooks.yaml` `lifecycle`, of one [action kind](lifecycle.md#action-kinds); it runs in the work's checkout and receives values only through `HARNESS_*` environment variables |
| Lifecycle event | One of `start`, `verified`, `completed`, `deliver`, `landed`, and the out-of-band `blocked`: a point in the development lifecycle at which declared actions run. See [lifecycle](lifecycle.md) |
| Landed | The lifecycle event for a recorded branch whose patches are all on the default branch (`git cherry`). `worktree remove` and `ralph clean` fire it before removing the worktree; `harness lifecycle run landed` fires it manually; `doctor` only reports landed branches |
| Lane | A named family of test commands in the profile, with a `select` command, an optional `full` command and `required_when` globs |
| Managed block | Text inside a file people own, between `<!-- harness:<id> -->` and `<!-- /harness:<id> -->` in Markdown, or `# harness:<id>` and `# /harness:<id>` in `.gitignore`, YAML, TOML and shell files; written only by `harness`. Each module owns its block ids. See [render](render.md#managed-blocks) |
| Managed worktree | A worktree created by `harness worktree create` under `worktrees.dir` (default `.work/worktrees/`), with a recorded session |
| Manifest | `.harness/manifest.json`: the record of every owned file, key, entry and block and every seeded file, with hashes, each preset's resolved commit and the schema |
| Memory | Durable, reviewed repository knowledge for agents under `.agents/memory/`, indexed in `AGENTS.md` |
| Merge-render | Writing only owned keys and entries into a native file and preserving everything else byte for byte |
| Meta-harness | A tool that configures and constrains the harnesses that run agents (Claude Code, Codex, ralph-tui) and never calls a model itself |
| Module | An opt-in feature set in the profile: `hooks`, `verify`, `tasks`, `ralph`, `memory`, `skills` |
| Native file | A file an agent reads from a fixed path: `.claude/settings.json`, `.codex/config.toml`, `.codex/hooks.json`, `.codex/rules/` |
| Notification hook | An action declared under `attention` or `turn_end` in `hooks.yaml`: `run` or `http`, fired by the agent's own notification events, never inside a Ralph worker. See [hooks](hooks.md) |
| Native rule string | A policy string in an agent's own rule syntax (`Tool(specifier)`, a bare tool name, `mcp__server__tool`); rendered verbatim for Claude Code, and for Codex only when it is a plain `Bash` prefix |
| Owned | Written, rewritten and tracked by `harness` through the manifest; never edited by hand. Compare seeded |
| Pending pin | A pin without checksums, `checksums: pending`, written by `harness scaffold --offline` because a binary cannot contain its own checksums and offline cannot fetch them. Nothing downloads against it; a cached release still runs. `harness wrapper --version <the pinned tag>` completes it, and `doctor` reports it until then. See [wrapper](wrapper.md#a-pending-pin) |
| Pin | The exact release a repository runs, recorded with the sha256 of each platform's binary in `.harness/wrapper.yaml`, the only place `harness` is pinned; owned content, written by `scaffold` and moved only by `harness wrapper`, after the running binary verifies the release's signed checksum file (see Pending pin); the upgrade renders the repository with the new release in the same commit. A global install defers to it. See [wrapper](wrapper.md) |
| Policy | `.harness/policy.yaml`: which commands agents may run without asking, only after asking, or never |
| PRD file | A JSON file of user stories in ralph-tui's format, usable as a task tracker |
| Preset | Either a built-in stack preset that seeds a new profile, or an `extends` source that adds entries and lifecycle actions at every render, at the commit recorded in the manifest. See [presets](presets.md) |
| Profile | `.harness/config.yaml`: the repository's facts for `harness` |
| Raw entry | A hook in `hooks.yaml` `raw`, rendered as a native hook entry that runs without the binary |
| Receipt | `verified.json` in a checkout's state directory (default `.work/`): the record of a passing `harness verify`, bound to the base and the fingerprint |
| Render | Produce native files, `harnessw`, task shims, the skill, links and blocks from the repository files and presets |
| Replay | The Ralph worker rerunning the lanes an agent's receipt selected, from rules read at the base commit, before allowing completion |
| Role | How an agent is used in a repository: `interactive` or `worker` |
| Schema | The format version carried by every repository file and rendered artefact |
| Scope | What a lifecycle event concerns: `task` (one tracked task), `run` (one Ralph run) or `change` (work without a task, such as a fleet change) |
| Seeded | Written once and owned by the repository from then on: `.harness/config.yaml`, `.harness/policy.yaml` and `.harness/hooks.yaml`, by `scaffold`; the `.agents/memory/` skeleton, when the memory module is enabled, by `scaffold` or the first `render` after it. Recorded in the manifest with the hash it was created with, never re-rendered. See [remove](remove.md#undo-and-eject-compared) |
| Session | A managed worktree's record in `session.json` in its state directory (base, tracker, task, actor), or a Ralph run identified by its session id |
| Skip reason | Free text recorded in the receipt for a lane not run |
| Standing context | Directives and skills `harness` puts into agent sessions when a declared trigger fires (`always`, `paths`, `prompt`), so nobody invokes them by name; declared in the profile's `context` section. See [context](context.md) |
| State | Clone-local, gitignored, disposable data at the paths the profile sets (`state.dir`, `worktrees.dir`, `tasks.prd_dir`, `ralph.worktrees_dir`, all under `.work/` by default), plus `.beads/` and a coordinator's `.ralph-tui/`. See [profile](profile.md#state-paths) |
| Tamper check | The Ralph worker's restore of tool-owned files from the session's base commit before every agent launch, and its comparison after the agent exits; any difference fails the attempt |
| Task | An item in a clone-local tracker (Beads or a PRD file); not a GitHub issue |
| Task shim | A one-line mise task, rendered with `front_door: mise`, that execs `./harnessw <command>`, such as `mise run verify`. See [render](render.md#task-shims) |
| To-do | The list of adoption items `harness` cannot resolve deterministically, printed by `scaffold --adopt` and reported by `doctor` |
| Tool block | A managed block that says how to use `harness` in this repository and may name `./harnessw` commands: `commands`, `layout`, `verify` and `lifecycle` in `AGENTS.md`, `commands` in `README.md`. An eject removes it. See [concepts](concepts.md#convention-blocks-and-tool-blocks) |
| Tracker | The task backend of a worktree or run: `beads`, `prd` or `none` |
| Trigger point | The place in a `harness` command where an event fires automatically, running the actions declared with `auto: true` |
| Trust chain | How a release becomes trusted: each release publishes a checksum file and its signature, every binary embeds the release keys it trusts, and a pin changes only after an already trusted binary verifies the new release's signed checksum file. The first binary a person installs is the root. See [wrapper](wrapper.md#security) |
| Turn end | The notification event for an agent finishing a turn. Its `run` and `http` actions are declared under `turn_end` in `hooks.yaml`; rendered from Claude Code's `Stop` event and Codex's `notify` setting |
| Undo | `harness remove` without `--eject`: delete the owned content, every block and its text included, the seeded files in `.harness/`, and the memory skeleton files that still match the hash they were seeded with; write back the settings adoption imported from the project's native files; list and leave any file the manifest does not record. See [remove](remove.md#undo) |
| Worker | An agent process ralph-tui starts through `harness ralph-worker` for one task; also an agent role meaning "runs here only as a Ralph worker" |
| Wrapper | `harnessw`: the committed POSIX `sh` script at the repository root that runs the pinned release, downloading it and verifying its checksum the first time a machine needs it. The default entry point for people, agents, task shims and hooks. See [wrapper](wrapper.md) |
