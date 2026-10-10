# Glossary

## Objective

**Problem.** The documentation uses many words in a precise sense of its own, such as owned, seeded, receipt, lane or
landed, and readers arrive at a page from a search or a link without having read the page that defines them. A developer
or an agent who reads such a word in its everyday sense draws the wrong conclusion, for example about what removal
deletes or when work counts as done.

**Goal.** A reader who meets an unfamiliar term on any page can learn its exact meaning, and where it is explained,
without reading the pages before it.

## Terms

**The game master.** `harness` is to agents what a game master is to a tabletop game: it rules the world and never
plays. Developers are the players, who decide what gets built. Agents are the characters, who act within the rules.
Repositories are the world. The three repository files, `.harness/config.yaml`, `.harness/policy.yaml` and
`.harness/hooks.yaml`, are the rulebook, and the built-in invariants are the rules no table can change.

| Term | Meaning |
| --- | --- |
| Action kind | What a declared action does: `run` (a shell command), `http` (an HTTP request `harness` makes itself, with the URL and header values read from environment variables) or `agent` (a headless agent run under the [worker contract](ralph.md#the-worker-contract)). Notification hooks take `run` and `http` only. See [lifecycle](lifecycle.md#action-kinds) |
| Actor | The identity of the work in a managed worktree, `<user>:<worktree>:<random>`, recorded when the worktree is created and passed to every event fired there as `HARNESS_ACTOR`, so a tracker's actions can assign a task to it. See [tasks](tasks.md) |
| Adapter | The part of `harness` that knows one agent: its native files, permission and hook formats, hook payloads, and safe worker launch. See [agents](agents.md#the-adapter-contract) |
| Adopt | `harness scaffold --adopt`: take over an existing hand-written agent setup, import what can be expressed, list the rest as a to-do |
| Adopted repository | A repository set up for `harness`, by `harness scaffold` with or without `--adopt`: it holds `.harness/`, the committed wrapper `harnessw` and the files `harness` renders. See [scaffold](scaffold.md) |
| Adoption class | Greenfield, brownfield-simple or brownfield-complex: the state a repository starts from. See [concepts](concepts.md#adoption-classes) |
| Agent-content directory | `.agents/`: skills, memory, agent commands and the review guide. A convention shared with other tools, such as the `skills` CLI, that stays meaningful without `harness`. See [concepts](concepts.md#two-directories-two-owners) |
| Always-on context | The [standing context](context.md) every session of a role receives: `always` entries, `static` ones included. Capped by `context.budget` |
| Application change | A change that touches a file matching `verify.app_paths`; it requires lane selection and a build |
| Attention | The notification event for an agent waiting for a person: a permission prompt, a question, idle input. Its `run` and `http` actions are declared under `attention` in `hooks.yaml`; rendered from Claude Code's `Notification` event |
| Base | The commit a change is measured against: a managed worktree's starting commit, or the merge-base with the default branch |
| Base policy | The built-in policy entries every repository gets and none can remove: no force push to the default branch, no mise `:::` chaining, no `harness lifecycle` for workers, in every rendered spelling of the command (`harness`, `./harnessw`, `harnessw`). The binary enforces the same guards itself; the entries are defence in depth |
| Bare string | A policy string that is not a native rule string, that is, neither `Tool(specifier)`, nor a tool name on its own such as `WebSearch` or `Read`, nor an `mcp__…` name: shorthand for `Bash(<string>)`, Claude-only. See [policy](policy.md#entry-forms) |
| Beads | A third-party task tracker that keeps tasks in a `.beads/` database in the clone and is used through its `bd` CLI. `harness` has no code for it; the `beads` [tool preset](presets.md) wires it with lifecycle actions. See [tasks](tasks.md) |
| Block | See managed block |
| Blocked | The out-of-band lifecycle event for work that stops unfinished or needs a person. `harness` fires it when a command without a terminal exits 3; a person, a coordinating agent or a script around a loop runner fires it with `harness lifecycle run blocked`, for example when an unattended run stops unfinished. Its actions receive the reason in `HARNESS_REASON`. See [lifecycle](lifecycle.md#events) |
| Bootstrap | The profile's list of commands that prepares a checkout: run in a new managed worktree, and once per checkout a worker runs in |
| Budget | A limit on the lines `harness` adds to what agents read every session: `blocks.agents_md_budget` for the blocks in `AGENTS.md` (default 40, 8 per block), `context.budget` for always-on context (default 120 per role). `doctor` notes an overrun. See [render](render.md#the-agentsmd-budget) |
| Cache | `$HARNESS_HOME`, default `$XDG_CACHE_HOME/harness`, or `~/.cache/harness` when `XDG_CACHE_HOME` is unset: the per-user directory holding downloaded releases at `versions/<version>/harness`, the update-check result and the result of `doctor`'s signed-checksum check, and nothing else |
| Configuration directory | `.harness/`: the profile, policy, hooks file, `wrapper.yaml` and manifest. Versioned, never state, never ignored. Both removal modes delete every file `harness` recorded there, and the directory once nothing else is in it |
| Context trigger | What makes a `when` entry of [standing context](context.md) fire: `paths` (the agent first reads or edits a matching file) or `prompt` (the person's message contains a listed word or phrase) |
| Convention block | A managed block that states, as plain fact, a convention that stays true without `harness`, and never names a `harness` command: `worktrees`, `policy`, `memory` and `context` in `AGENTS.md`, `import` in `CLAUDE.md`, every `.gitignore` block. An eject keeps its text. See [concepts](concepts.md#convention-blocks-and-tool-blocks) |
| Coordinating agent | An agent session, attended or left alone, that drives work through `harness` commands for a person, for example starting a loop runner in a managed worktree, arranging the run's commits and firing `completed` or `blocked` with scope `run`, then `deliver`. Work that needs an MCP tool is its job, since `harness` never connects to an MCP server. See [safety](safety.md#unattended-runs) |
| Default branch | The branch `origin/HEAD` points to |
| Deliver | The lifecycle event that sends concluded work out of the clone through declared actions (push, pull request, CI trigger). Guarded: never on the default branch or with uncommitted changes and, when the verify module is enabled, never without a valid receipt. See [lifecycle](lifecycle.md) |
| Drift | A difference between an owned file, key or block and what the sources produce now with the pinned release; reported by `render --check`. No owned artefact is exempt |
| Eject | `harness remove --eject`: stop managing a repository and keep the result. Convention blocks lose their markers and keep their text. Tool blocks, the harness skill, hooks that run `harnessw` and permission entries that name a `harness` command are removed. `.gitignore` entries, the other permissions and `raw` hooks stay as hand-owned content. `harnessw` and the owned and seeded files of `.harness/` are deleted, and `.harness/` once nothing else is in it. See [remove](remove.md#eject) |
| Equivalence check | The adoption step that compares, per agent, the effective rule set after import with the one before it; a lost project rule makes adoption exit 2. See [scaffold](scaffold.md#the-equivalence-check) |
| Fingerprint | A hash over the base and the path, mode and content of every changed file; binds a receipt to content |
| Fleet | An explicit list of repositories that `harness fleet` runs a command across |
| Greenfield | A repository with no agent permissions, hooks or workflow engine of its own |
| Harness skill | `.agents/skills/harness/SKILL.md`: the tool-owned skill teaching agents the standards and their reasoning |
| Hooks file | `.harness/hooks.yaml`: agent-neutral hook declarations, notification hooks and lifecycle actions |
| Import scope | Where `scaffold --import` reads existing configuration: a comma-separated set from `project`, `local`, `user`, `none`; `project` by default with `--adopt`, `none` without it |
| Interactive | An agent role: people use the agent in this repository |
| Invariant | A rule built into `harness` that no repository file or flag can turn off. See [concepts](concepts.md#built-in-invariants) |
| Issue | A GitHub issue: part of the team's record of work. `harness` never writes issues |
| Knowledge | Prose, memory and skills written by people and agents; never generated by `harness` |
| Inject | How a standing-context entry reaches a session: `content` (the text itself) or `pointer` (one line naming the skill or file to read now) |
| Lifecycle action | A named action a repository declares under an event in `hooks.yaml` `lifecycle`, of one [action kind](lifecycle.md#action-kinds); it runs in the work's checkout and receives values only through `HARNESS_*` environment variables |
| Lifecycle event | One of `start`, `verified`, `completed`, `deliver`, `landed`, and the out-of-band `blocked`: a point in the development lifecycle at which declared actions run. See [lifecycle](lifecycle.md) |
| Landed | The lifecycle event for a recorded branch whose patches are all on the default branch (`git cherry`). `worktree remove` fires it before removing the worktree; `harness lifecycle run landed` fires it manually; `doctor` only reports landed branches |
| Lane | A named family of test commands in the profile, with a `select` command, an optional `full` command and `required_when` globs |
| Loop runner | A third-party program that works through a list of tasks and starts an agent for each, such as ralph-tui. `harness` has no code for any; a loop runner connects by naming `harness worker` as its agent command. See [ralph](ralph.md) |
| Managed block | Text inside a file people own, between `<!-- harness:<id> -->` and `<!-- /harness:<id> -->` in Markdown, or `# harness:<id>` and `# /harness:<id>` in `.gitignore`, YAML, TOML and shell files; written only by `harness`. Each module owns its block ids. See [render](render.md#managed-blocks) |
| Managed worktree | A worktree created by `harness worktree create` under `worktrees.dir` (default `.work/worktrees/`), with a recorded session |
| Manifest | `.harness/manifest.json`: the record of every owned file, key, entry and block and every seeded file, with hashes and the schema |
| Memory | Durable, reviewed repository knowledge for agents under `.agents/memory/`, indexed in `AGENTS.md` |
| Merge-render | Writing only owned keys and entries into a native file and preserving everything else byte for byte |
| Meta-harness | A tool that configures and constrains the harnesses that run agents (Claude Code, Codex CLI) and never calls a model itself |
| mise | A third-party tool-version manager and task runner ([mise](https://mise.jdx.dev)), and one way to install `harness` (`mise use -g github:lmarqs/harness@<tag>`). `harness` has no code for it; the built-in base denies its `:::` task chaining. See [compatibility](compatibility.md#mise) |
| Module | An opt-in feature set in the profile: `hooks`, `verify`, `memory` |
| Native file | A file an agent reads from a fixed path: `.claude/settings.json`, `.codex/config.toml`, `.codex/hooks.json`, `.codex/rules/` |
| Notification hook | An action declared under `attention` or `turn_end` in `hooks.yaml`: `run` or `http`, fired by the agent's own notification events, never under `harness worker`. See [hooks](hooks.md) |
| Native rule string | A policy string in an agent's own rule syntax (`Tool(specifier)`, a bare tool name, `mcp__server__tool`); rendered verbatim for Claude Code, and for Codex only when it is a plain `Bash` prefix |
| Owned | Written, rewritten and tracked by `harness` through the manifest; never edited by hand. Compare seeded |
| Pending pin | A pin without checksums, `checksums: pending`, written by `harness scaffold --offline` because a binary cannot contain its own checksums and offline cannot fetch them. Nothing downloads against it; a cached release still runs. `harness wrapper --version <the pinned tag>` completes it, and `doctor` reports it until then. See [wrapper](wrapper.md#a-pending-pin) |
| Pin | The exact release a repository runs, recorded with the sha256 of each platform's binary in `.harness/wrapper.yaml`, the only place `harness` is pinned. It is owned content, written by `scaffold` and moved only by `harness wrapper`, after the running binary verifies the release's signed checksum file (see Pending pin). The upgrade renders the repository with the new release in the same change. A global install defers to it. See [wrapper](wrapper.md) |
| Policy | `.harness/policy.yaml`: which commands agents may run without asking, only after asking, or never |
| PRD file | ralph-tui's term for a JSON file of user stories, one of the task sources it reads. `harness` has no code for it |
| Preset | A named bundle built into the release that a person applies with `harness preset apply <name>`, and that `scaffold` applies for the stacks it detects. It holds entries, merged into the profile, policy and hooks file only where missing, and zero or more templates. Stack presets: `go`, `node`, `terraform`, `python`. Tool presets: `ralph-tui`, `beads`. Once written, the entries and files are the repository's own, and nothing records where they came from. See [presets](presets.md) |
| Profile | `.harness/config.yaml`: the repository's facts for `harness` |
| Ralph | An unattended run of a loop runner whose agent command is `harness worker`, such as ralph-tui set up by its tool preset: the loop runner picks each task, and `harness worker` runs the agent for it under the worker contract. See [ralph](ralph.md) |
| ralph-tui | A third-party loop runner ([ralph-tui](https://github.com/subsy/ralph-tui)) that schedules tasks from Beads or a PRD file and starts an agent process for each. `harness` has no code for it; the `ralph-tui` tool preset writes a `.ralph-tui/config.toml` whose agent command is `./harnessw worker`. See [ralph](ralph.md) |
| Raw entry | A hook in `hooks.yaml` `raw`, rendered as a native hook entry that runs without the binary |
| Receipt | `verified.json` in a checkout's state directory (default `.work/`): the record of a passing `harness verify`, bound to the base and the fingerprint |
| Render | Produce native files, `harnessw`, the skill, links and blocks from the repository files and the pinned release |
| Replay | `harness worker` rerunning the lanes an agent's receipt selected, with the profile read from the base commit, before it reports the attempt as verified |
| Role | How an agent is used in a repository: `interactive` or `worker` |
| Schema | The format version carried by every repository file and rendered artefact |
| Scope | What a lifecycle event concerns: `task` (one tracked task), `run` (a whole unattended run, fired only by whoever drives it) or `change` (work without a task, such as a fleet change) |
| Seeded | Written once and owned by the repository from then on: `.harness/config.yaml`, `.harness/policy.yaml` and `.harness/hooks.yaml`, by `scaffold`; the `.agents/memory/` skeleton, when the memory module is enabled, by `scaffold` or the first `render` after it. Recorded in the manifest with the hash it was created with, never re-rendered. See [remove](remove.md#undo-and-eject-compared) |
| Session | A managed worktree's record in `session.json` in its state directory: the base, the task id given with `--task`, the actor |
| Skip reason | Free text recorded in the receipt for a lane not run |
| Standing context | Directives and skills `harness` puts into agent sessions when a declared trigger fires (`always`, `paths`, `prompt`), so nobody invokes them by name; declared in the profile's `context` section. See [context](context.md) |
| State | Clone-local, gitignored, disposable data `harness` writes at the paths the profile sets (`state.dir` and `worktrees.dir`, both under `.work/` by default). See [profile](profile.md#state-paths) |
| Tamper check | `harness worker`'s restore of tool-owned files from the base commit before it starts the agent, and its comparison after the agent exits of those files, the files under `.harness/` and the files standing context names; any difference fails the attempt. `agent` actions run it too |
| Task | A unit of work in the tracker a team uses, named by a free-form id. `harness worktree create --task <id>` binds a worktree to it, and every event fired there carries it as `HARNESS_TASK`. See [tasks](tasks.md) |
| Template | A whole file in a preset or in `scaffold`, rendered once with values from the profile and written only when the file is absent. From then on it is the repository's own file: `harness` never re-renders or removes it, and `preset apply` reports `kept <path>: exists` and leaves it. Example: the `ralph-tui` preset's `.ralph-tui/config.toml`. See [presets](presets.md) |
| To-do | The list of adoption items `harness` cannot resolve deterministically, printed by `scaffold --adopt` and reported by `doctor` |
| Tool block | A managed block that says how to use `harness` in this repository and may name `./harnessw` commands: `commands`, `layout`, `verify` and `lifecycle` in `AGENTS.md`, `commands` in `README.md`. An eject removes it. See [concepts](concepts.md#convention-blocks-and-tool-blocks) |
| Tool preset | A preset that wires a third-party tool `harness` has no code for. `ralph-tui`: a `.ralph-tui/config.toml` template naming `./harnessw worker` as the agent command, and `worker.deny` entries. `beads`: lifecycle actions that update Beads on `start`, `completed` and `blocked`, a Codex writable root and a `worker.deny` entry. See [presets](presets.md) |
| Tracker | The tool a team keeps its tasks in, such as Beads or an issue tracker; `harness` has no code for any. Lifecycle actions on `start`, `completed` and `blocked` that read `HARNESS_TASK` and `HARNESS_ACTOR` wire it, as the `beads` tool preset does. See [tasks](tasks.md) |
| Trigger point | The place in a `harness` command where an event fires automatically, running the actions declared with `auto: true` |
| Trust chain | How a release becomes trusted: each release publishes a checksum file and its signature, every binary embeds the release keys it trusts, and a pin changes only after an already trusted binary verifies the new release's signed checksum file. The first binary a person installs is the root. See [wrapper](wrapper.md#security) |
| Turn end | The notification event for an agent finishing a turn. Its `run` and `http` actions are declared under `turn_end` in `hooks.yaml`; rendered from Claude Code's `Stop` event and Codex's `notify` setting |
| Undo | `harness remove` without `--eject`: delete the owned content, every block and its text included, the seeded files in `.harness/`, and the memory skeleton files that still match the hash they were seeded with; write back the settings adoption imported from the project's native files; list and leave any file the manifest does not record. See [remove](remove.md#undo) |
| Validator | A repository check: a command run when a changed or edited file matches its `when` globs. Those under `verify.validators` in the profile run in `harness verify`; those under `validators` in `hooks.yaml`, which may reuse the profile's, run after an agent edits a file. See [profile](profile.md#verify), [hooks](hooks.md) |
| Worker | `harness worker <agent>`: runs one agent headless for one task under the worker contract, in the checkout it starts in, and exits 0 only for a verified attempt. A loop runner names it as its agent command. Also an agent role meaning the agent runs here only unattended, under `harness worker` or an `agent` action. See [ralph](ralph.md) |
| Worker contract | The rules `harness worker` and every `agent` action run their agent under: a pinned permission mode with `worker.deny` applied, bypass flags refused, rules read from the base commit, the tamper check, and no pushes or lifecycle events of the agent's own. `harness worker` also reports an attempt as verified only after a passing replay, and releases a loop runner's completion signal only then. See [ralph](ralph.md#the-worker-contract) |
| Wrapper | `harnessw`: the committed POSIX `sh` script at the repository root that runs the pinned release, downloading it and verifying its checksum the first time a machine needs it. The default entry point for people, agents, hooks and the loop runners that call `./harnessw worker`. See [wrapper](wrapper.md) |
