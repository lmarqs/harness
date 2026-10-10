# Glossary

## Objective

**Problem.** The documentation uses many words in a precise sense of its own, such as owned, seeded, receipt, lane or
landed, and readers arrive at a page from a search or a link without having read the page that defines them. A developer
or an agent who reads such a word in its everyday sense draws the wrong conclusion, for example about what removal
deletes or when work counts as done.

**Goal.** A reader who meets an unfamiliar term on any page can learn its exact meaning, and where it is explained,
without reading the pages before it.

## Terms

**The game master.** `the-cook` is to agents what a game master is to a tabletop game: it rules the world and never
plays. Developers are the players, who decide what gets built. Agents are the characters, who act within the rules.
Repositories are the world. The three repository files, `.cook/config.yaml`, `.cook/policy.yaml` and
`.cook/hooks.yaml`, are the rulebook, and the built-in invariants and the floor of the recipes in use are the rules
no table can change.

| Term | Meaning |
| --- | --- |
| Action kind | What a declared action does: `run` (a shell command), `http` (an HTTP request `the-cook` makes itself, with the URL and header values read from environment variables) or `agent` (a headless agent run under the [worker contract](worker.md#the-worker-contract)). Notification hooks take `run` and `http` only. See [lifecycle](lifecycle.md#action-kinds) |
| Actor | The identity of the work in a managed workspace, `<user>:<workspace>:<random>`, recorded when the workspace is created and passed to every event fired there as `COOK_ACTOR`, so a tracker's actions can assign a task to it. See [tasks](tasks.md) |
| Adopt | `the-cook scaffold --adopt`: take over an existing hand-written agent setup, import what can be expressed, list the rest as a to-do |
| Adopted repository | A repository set up for `the-cook`, by `the-cook scaffold` with or without `--adopt`: it holds `.cook/`, the committed wrapper `cook` and the files `the-cook` renders. See [scaffold](scaffold.md) |
| Adoption class | Greenfield, brownfield-simple or brownfield-complex: the state a repository starts from. See [concepts](concepts.md#adoption-classes) |
| Agent-content directory | `.agents/`: skills, memory, agent commands and the review guide. A convention shared with other tools, such as a skills installer, that stays meaningful without `the-cook`. See [concepts](concepts.md#two-directories-two-owners) |
| Always-on context | The [standing context](context.md) every session of a role receives: `always` entries, `static` ones included. Capped by `context.budget` |
| Application change | A change that touches a file matching `verify.app_paths`; it requires lane selection and a build |
| Attention | The notification event for an agent waiting for a person: a permission prompt, a question, idle input. Its `run` and `http` actions are declared under `attention` in `hooks.yaml`; rendered from the native event the agent's harness recipe maps to it |
| Base | The commit a change is measured against: a managed workspace's starting commit, or the merge-base with the default branch |
| Bare string | A policy string that is not a native rule string, that is, neither `Tool(specifier)`, nor a tool name on its own such as `WebSearch` or `Read`, nor an `mcp__...` name: shorthand for a shell command rule, rendered only for agents whose harness recipe can express it. See [policy](policy.md#entry-forms) |
| Block | See managed block |
| Blocked | The out-of-band lifecycle event for work that stops unfinished or needs a person. `the-cook` fires it when a command without a terminal exits 3; a person, a coordinating agent or a script around a loop runner fires it with `the-cook lifecycle run blocked`, for example when an unattended run stops unfinished. Its actions receive the reason in `COOK_REASON`. See [lifecycle](lifecycle.md#events) |
| Bootstrap | The profile's list of commands that prepares a checkout: run in a new managed workspace, and once per checkout a worker runs in |
| Budget | A limit on the lines `the-cook` adds to what agents read every session: `blocks.agents_md_budget` for the blocks in `AGENTS.md` (default 40, 8 per block), `context.budget` for always-on context (default 120 per role). `doctor` notes an overrun. See [render](render.md#the-agentsmd-budget) |
| Cache | `$COOK_HOME`, default `$XDG_CACHE_HOME/the-cook`, or `~/.cache/the-cook` when `XDG_CACHE_HOME` is unset: the per-user directory holding downloaded releases at `versions/<version>/the-cook`, recipes at `recipes/<sha256>.tar.gz`, the update-check result and the result of `doctor`'s signed-checksum check, and nothing else |
| Checks | The part of a recipe that `doctor`, `scaffold --cookbook` and `recipe list --cookbook` use: a version command with the supported range, and a detect rule, file globs whose match shows the program is in use. See [recipes](recipes.md#checks) |
| Configuration directory | `.cook/`: the profile, policy, hooks file, `wrapper.yaml` and manifest. Versioned, never state, never ignored. Both removal modes delete every file `the-cook` recorded there, and the directory once nothing else is in it |
| Context trigger | What makes a `when` entry of [standing context](context.md) fire: `paths` (the agent first reads or edits a matching file) or `prompt` (the person's message contains a listed word or phrase) |
| Contract | One of the two interfaces the binary declares and a recipe's operations fill: the version-control contract, filled by the recipe `vcs:` names, and the harness contract, filled by one recipe per declared agent. A recipe fills at most one, and declares the contract's version it is written for. A command that needs a contract no pinned recipe fills exits 3 naming `recipe pin`. See [recipes](recipes.md#contracts) |
| Convention block | A managed block that states, as plain fact, a convention that stays true without `the-cook`, and never names a `the-cook` command: `workspaces`, `policy`, `memory` and `context` in `AGENTS.md`, the `import` block a harness recipe renders into its agent's instruction file, every `.gitignore` block. An eject keeps its text. See [concepts](concepts.md#convention-blocks-and-tool-blocks) |
| Cookbook | A source that publishes recipes by version: an HTTPS base URL or `github:<owner>/<repo>`, where each tag holds one archive per recipe, `<name>.tar.gz`, and one checksum file, `checksums.txt`, with the sha256 of every archive of the tag. Every recipe a repository uses is pinned from a cookbook; `the-cook` holds no default cookbook. See [recipes](recipes.md#cookbooks) |
| Coordinating agent | An agent session, attended or left alone, that drives work through `the-cook` commands for a person, for example starting a loop runner in a managed workspace, arranging the run's commits and firing `completed` or `blocked` with scope `run`, then `deliver`. Work that needs an MCP tool is its job, since `the-cook` never connects to an MCP server. See [safety](safety.md#unattended-runs) |
| Default branch | The branch the version-control recipe reports as the default |
| Deliver | The lifecycle event that sends concluded work out of the clone through declared actions (push, pull request, CI trigger). Guarded: never on the default branch or with uncommitted changes and, when the verify module is enabled, never without a valid receipt. See [lifecycle](lifecycle.md) |
| Drift | A difference between an owned file, key or block and what the sources produce now with the pinned release; reported by `render --check`. No owned artefact is exempt |
| Eject | `the-cook remove --eject`: stop managing a repository and keep the result. Convention blocks lose their markers and keep their text. Tool blocks, the shipped skill, hooks that run `cook` and permission entries that name a `the-cook` command are removed. `.gitignore` entries, the other permissions and `raw` hooks stay as hand-owned content. `cook` and the owned and seeded files of `.cook/` are deleted, and `.cook/` once nothing else is in it. See [remove](remove.md#eject) |
| Entries | The part of a recipe that adds to the profile, the policy and the hooks file: applied once by `recipe apply` or `scaffold --cookbook`, only where missing, and the repository's own afterwards. See [recipes](recipes.md#entries) |
| Equivalence check | The adoption step that compares, per agent, the effective rule set after import with the one before it; a lost project rule makes adoption exit 2. See [scaffold](scaffold.md#the-equivalence-check) |
| Fingerprint | A hash over the base and the path, mode and content of every changed file; binds a receipt to content |
| Fleet | An explicit list of repositories that `the-cook fleet` runs a command across |
| Floor | Deny rules and bypass arguments a recipe marks as facts no repository may lose: rendered into every agent's output on every render, refused at every unattended launch, never written into the policy and out of its reach. Examples: a version-control recipe's deny of a force push to the default branch, a harness recipe's bypass arguments. See [recipes](recipes.md#the-floor) |
| Greenfield | A repository with no agent permissions, hooks or workflow engine of its own |
| Harness recipe | A recipe that fills the harness contract for one agent: its native files, its launch, its bypass arguments, its hook payload mapping. Named by `agents.<name>.recipe`, by default the agent's name. See [agents](agents.md#the-harness-contract) |
| Hooks file | `.cook/hooks.yaml`: agent-neutral hook declarations, notification hooks and lifecycle actions |
| Import scope | Where `scaffold --import` reads existing configuration: a comma-separated set from `project`, `local`, `user`, `none`; `project` by default with `--adopt`, `none` without it |
| Interactive | An agent role: people use the agent in this repository |
| Invariant | A rule of the process built into the binary that no repository file or flag can turn off. The facts about a program it applies, such as an agent's bypass arguments, come from that program's recipe as floor. See [concepts](concepts.md#built-in-invariants) |
| Issue | A GitHub issue: part of the team's record of work. `the-cook` never writes issues |
| Knowledge | Prose, memory and skills written by people and agents; never generated by `the-cook` |
| Inject | How a standing-context entry reaches a session: `content` (the text itself) or `pointer` (one line naming the skill or file to read now) |
| Lifecycle action | A named action a repository declares under an event in `hooks.yaml` `lifecycle`, of one [action kind](lifecycle.md#action-kinds); it runs in the work's checkout and receives values only through `COOK_*` environment variables |
| Lifecycle event | One of `start`, `verified`, `completed`, `deliver`, `landed`, and the out-of-band `blocked`: a point in the development lifecycle at which declared actions run. See [lifecycle](lifecycle.md) |
| Landed | The lifecycle event for a recorded branch whose patches are all on the default branch, as the version-control recipe's `landed` operation reports. `workspace remove` fires it before removing the workspace; `the-cook lifecycle run landed` fires it manually; `doctor` only reports landed branches |
| Lane | A named family of test commands in the profile, with a `select` command, an optional `full` command and `required_when` globs |
| Loop runner | A third-party program that works through a list of tasks and starts an agent for each. The binary contains no code for any; a loop runner connects by naming `the-cook worker` as its agent command, typically through a template its recipe writes. See [worker](worker.md) |
| Managed block | Text inside a file people own, between `<!-- cook:<id> -->` and `<!-- /cook:<id> -->` in Markdown, or `# cook:<id>` and `# /cook:<id>` in `.gitignore`, YAML, TOML and shell files; written only by `the-cook`. Each module owns its block ids. See [render](render.md#managed-blocks) |
| Managed workspace | A workspace created by `the-cook workspace create` under `workspaces.dir` (default `.work/workspaces/`), with a recorded session |
| Manifest | `.cook/manifest.json`: the record of every owned file, key, entry and block and every seeded file, with hashes and the schema |
| Memory | Durable, reviewed repository knowledge for agents under `.agents/memory/`, indexed in `AGENTS.md` |
| Merge-render | Writing only owned keys and entries into a native file and preserving everything else byte for byte |
| Meta-harness | A tool that configures and constrains the harnesses that run agents and never calls a model itself |
| Module | An opt-in feature set in the profile: `hooks`, `verify`, `memory` |
| Native file | A file an agent reads from a fixed path, which its harness recipe names and renders |
| Notification hook | An action declared under `attention` or `turn_end` in `hooks.yaml`: `run` or `http`, fired by the agent's own notification events, never under `the-cook worker`. See [hooks](hooks.md) |
| Native rule string | A policy string in an agent's own rule syntax (`Tool(specifier)`, a bare tool name, `mcp__server__tool`); rendered verbatim for the agents whose harness recipe can express it |
| Operations | The part of a recipe that fills a contract: commands with placeholders and a declared output kind (string, lines, bytes or status), and mappings the binary reads as data. See [recipes](recipes.md#operations) |
| Owned | Written, rewritten and tracked by `the-cook` through the manifest; never edited by hand. Compare seeded |
| Pending pin | A pin without checksums, `checksums: pending`, written by `the-cook scaffold --offline` because a binary cannot contain its own checksums and offline cannot fetch them. Nothing downloads against it; a cached release still runs. `the-cook wrapper --version <the pinned tag>` completes it, and `doctor` reports it until then. See [wrapper](wrapper.md#a-pending-pin) |
| Pin | The exact version of something a repository runs, recorded in its files and moved only by a reviewed diff. The release pin: the release recorded with the sha256 of each platform's binary in `.cook/wrapper.yaml`, owned content, written by `scaffold` and moved only by `the-cook wrapper`, after the running binary verifies the release's signed checksum file (see Pending pin); a global install defers to it ([wrapper](wrapper.md)). A recipe pin: a `recipes:` entry with `name`, `source` (the cookbook), `version` (the tag) and `sha256` (from the tag's `checksums.txt`), written and moved by `the-cook recipe pin` or `scaffold --cookbook`; the binary checks every archive against it on download and on every load ([recipes](recipes.md#pinning)). Each upgrade renders the repository in the same change |
| Policy | `.cook/policy.yaml`: which commands agents may run without asking, only after asking, or never |
| Profile | `.cook/config.yaml`: the repository's facts for `the-cook` |
| Ralph | An unattended run of a loop runner whose agent command is `the-cook worker`: the loop runner picks each task, and `the-cook worker` runs the agent for it under the worker contract. See [worker](worker.md) |
| Raw entry | A hook in `hooks.yaml` `raw`, rendered as a native hook entry that runs without the binary |
| Receipt | `verified.json` in a checkout's state directory (default `.work/`): the record of a passing `the-cook verify`, bound to the base and the fingerprint |
| Recipe | A named, versioned bundle of data that describes one program to the binary, which contains code for none and ships no recipe: up to five parts, entries, templates, renders, operations and checks. Published in a cookbook and pinned in the profile under `recipes:`; cached under its hash and checked on every load. See [recipes](recipes.md) |
| Render | Produce native files, `cook`, the skill, links and blocks from the repository files, the pinned release and the recipes in use. Also one of the five parts of a recipe: content the binary owns and writes on every render, recorded in the manifest under the recipe's name. See [render](render.md), [recipes](recipes.md#renders) |
| Replay | `the-cook worker` rerunning the lanes an agent's receipt selected, with the profile read from the base commit, before it reports the attempt as verified |
| Role | How an agent is used in a repository: `interactive` or `worker` |
| Schema | The format version carried by every repository file and rendered artefact |
| Scope | What a lifecycle event concerns: `task` (one tracked task), `run` (a whole unattended run, fired only by whoever drives it) or `change` (work without a task, such as a fleet change) |
| Seeded | Written once and owned by the repository from then on: `.cook/config.yaml`, `.cook/policy.yaml` and `.cook/hooks.yaml`, by `scaffold`; the `.agents/memory/` skeleton, when the memory module is enabled, by `scaffold` or the first `render` after it. Recorded in the manifest with the hash it was created with, never re-rendered. See [remove](remove.md#undo-and-eject-compared) |
| Session | A managed workspace's record in `session.json` in its state directory: the base, the task id given with `--task`, the actor |
| Shipped skill | `.agents/skills/the-cook/SKILL.md`: the tool-owned skill teaching agents the standards and their reasoning |
| Skip reason | Free text recorded in the receipt for a lane not run |
| Standing context | Directives and skills `the-cook` puts into agent sessions when a declared trigger fires (`always`, `paths`, `prompt`), so nobody invokes them by name; declared in the profile's `context` section. See [context](context.md) |
| State | Clone-local, gitignored, disposable data `the-cook` writes at the paths the profile sets (`state.dir` and `workspaces.dir`, both under `.work/` by default). See [profile](profile.md#state-paths) |
| Tamper check | `the-cook worker`'s restore of tool-owned files from the base commit before it starts the agent, and its comparison after the agent exits of those files, the files under `.cook/` and the files standing context names; any difference fails the attempt. `agent` actions run it too |
| Task | A unit of work in the tracker a team uses, named by a free-form id. `the-cook workspace create --task <id>` binds a workspace to it, and every event fired there carries it as `COOK_TASK`. See [tasks](tasks.md) |
| Template | A whole file in a recipe or in `scaffold`, rendered once with values from the profile and written only when the file is absent. From then on it is the repository's own file: `the-cook` never re-renders or removes it, and `recipe apply` reports `kept <path>: exists` and leaves it. Example: a loop runner recipe's configuration file. One of the five parts of a recipe. See [recipes](recipes.md#templates) |
| To-do | The list of adoption items `the-cook` cannot resolve deterministically, printed by `scaffold --adopt` and reported by `doctor` |
| Tool block | A managed block that says how to use `the-cook` in this repository and may name `./cook` commands: `commands`, `layout`, `verify` and `lifecycle` in `AGENTS.md`, `commands` in `README.md`. An eject removes it. See [concepts](concepts.md#convention-blocks-and-tool-blocks) |
| Tracker | The tool a team keeps its tasks in, such as an issue tracker or a tracker that keeps its database in the clone; the binary contains no code for any. Lifecycle actions on `start`, `completed` and `blocked` that read `COOK_TASK` and `COOK_ACTOR` wire it, as a tracker's recipe may add as entries. See [tasks](tasks.md) |
| Trigger point | The place in a `the-cook` command where an event fires automatically, running the actions declared with `auto: true` |
| Trust chain | How a release becomes trusted: each release publishes a checksum file and its signature, every binary embeds the release keys it trusts, and a pin changes only after an already trusted binary verifies the new release's signed checksum file. The first binary a person installs is the root. See [wrapper](wrapper.md#security) |
| Turn end | The notification event for an agent finishing a turn. Its `run` and `http` actions are declared under `turn_end` in `hooks.yaml`; rendered from the native event or setting the agent's harness recipe maps to it |
| Undo | `the-cook remove` without `--eject`: delete the owned content, every block and its text included, the seeded files in `.cook/`, and the memory skeleton files that still match the hash they were seeded with; write back the settings adoption imported from the project's native files; list and leave any file the manifest does not record. See [remove](remove.md#undo) |
| Validator | A repository check: a command run when a changed or edited file matches its `when` globs. Those under `verify.validators` in the profile run in `the-cook verify`; those under `validators` in `hooks.yaml`, which may reuse the profile's, run after an agent edits a file. See [profile](profile.md#verify), [hooks](hooks.md) |
| Version-control recipe | The recipe `vcs:` names: it answers every question about the repository's history, such as the base commit, the default branch and what changed. See [recipes](recipes.md#the-version-control-contract) |
| Worker | `the-cook worker <agent>`: runs one agent headless for one task under the worker contract, in the checkout it starts in, and exits 0 only for a verified attempt. A loop runner names it as its agent command. Also an agent role meaning the agent runs here only unattended, under `the-cook worker` or an `agent` action. See [worker](worker.md) |
| Worker contract | The rules `the-cook worker` and every `agent` action run their agent under: a pinned permission mode with `worker.deny` applied, bypass flags refused, rules read from the base commit, the tamper check, and no pushes or lifecycle events of the agent's own. `the-cook worker` also reports an attempt as verified only after a passing replay, and releases a loop runner's completion signal only then. See [worker](worker.md#the-worker-contract) |
| Workspace | A separate working copy of the clone for one task, with its own branch, made and removed by the [version-control recipe](recipes.md#the-version-control-contract). The core never uses a version control system's own name for it |
| Wrapper | `cook`: the committed POSIX `sh` script at the repository root that runs the pinned release, downloading it and verifying its checksum the first time a machine needs it. The default entry point for people, agents, hooks and the loop runners that call `./cook worker`. See [wrapper](wrapper.md) |
