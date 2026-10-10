# Where new behaviour goes

## Objective

**Problem.** Contributors proposing a new capability, reviewers judging the proposal, and agents advising a repository
all have to decide where the capability belongs: in the tool's code, in a repository's own files, or in guidance an
agent follows. Decided case by case, the same capability is built in two places that drift apart, judgement ends up in
code that guesses wrong in the first repository nobody foresaw, and a rule that must hold is left to prose an agent can
skip. A reviewer then cannot tell that a proposal is in the wrong place until it fails.

**Goal.** Two people placing the same capability independently choose the same home for it. Work with one right answer
comes out the same, and checkable, in every repository, and work that needs judgement stays with the people and agents
who can exercise it.

## Overview

Every new capability has exactly one home, chosen by the rules below ([ADR 0002](../adrs/0002-division-of-work.md)).
They apply to proposals for harness itself and to the advice harness gives repositories through its skill.

## Destinations

| Destination | What it is | Who maintains it |
| --- | --- | --- |
| **Built-in invariant** | a rule about the process, compiled into the binary and about no particular program, that no file or flag can remove | harness |
| **Floor rule of a plugin** | a deny rule or bypass argument a plugin marks as floor: rendered on every render, never in the policy and out of its reach ([plugins](../features/plugins.md#the-floor)) | the plugin's maintainer: harness for a builtin plugin |
| **New command** | a new verb or noun-verb in the binary | harness |
| **Flag on an existing command** | a variation of an operation the binary already performs | harness |
| **Profile field** | a per-repository value that parametrises a deterministic binary behaviour (`config.yaml`; permissions in `policy.yaml`, hooks in `hooks.yaml`, the pinned version in `wrapper.yaml`) | the repository |
| **Declared action** | an action on a lifecycle event (`lifecycle:`) or an agent notification event (`attention:`, `turn_end:`) in `.harness/hooks.yaml`, of kind `run` (a shell command), `http` (a request harness performs) or `agent` (a headless agent run) | the repository |
| **Standing-context entry** | a declared trigger (`always`, `when.paths`, `when.prompt`) that puts an existing skill or repository file in front of an agent, under `context:` in `config.yaml` | the repository |
| **Harness skill or explain topic** | guidance an agent follows to do judgement work around harness | harness (embedded, shipped with the binary) |
| **Plugin** | a named, versioned bundle of data that describes one program to the binary ([plugins](../features/plugins.md)): entries and templates that `harness scaffold` and `harness plugin apply` write into the repository's files on request, renders and floor rules written on every render, operations that fill the version-control or harness contract, and checks. A stack plugin (`go`, `node`, `terraform`, `python`) holds the starting commands, test lanes, validators, module switches and post-edit hooks of one stack; other plugins describe git, an agent harness, or a program such as ralph-tui or Beads | harness for a builtin plugin (embedded, shipped with the release); its author for a fetched plugin; once written, the entries and files are the repository's |
| **Organization or personal skill** | guidance an agent follows for judgement work specific to an organization's conventions, including writing the organization's choices (policy entries, actions, standing-context entries) into a repository's files | the organization; the entries it leads to are the repository's |
| **Left to the repository** | a task in the repository's own task runner (a mise task, a `Makefile` target), a repository skill, prose in `AGENTS.md`, memory | the repository |

## Criteria

| Criterion | Question |
| --- | --- |
| Determinism | Given the same inputs, is there exactly one correct output? |
| Repetition | Is it needed, in the same form, in many unrelated repositories? |
| Safety relevance | Would getting it wrong, or switching it off, let an agent escape its permissions or claim unverified work? |
| Organization specificity | Is it true only for one organization's conventions, tools or vendors? |
| Judgement | Does it require reading prose, weighing options, or writing text? |
| Reach | Does it change something outside the clone (a push, a pull request, a CI trigger, a notification, a call to a service), or react to something that happened there (a merge)? |
| Lifecycle point | Does it belong at a point of the development lifecycle (`start`, `verified`, `completed`, `deliver`, `landed`), at the moment work stops for a person (`blocked`), or at an agent event (`attention`, `turn_end`)? |
| Guidance timing | Is it known guidance that applies at a predictable moment: every session, when certain files are touched, or when certain words appear? |
| Model or tool server | Does it need a model, or a tool that only an MCP server provides? |
| Program | Does it exist to describe or connect one program: a version control system, an agent harness, a loop runner, a task tracker, a skills installer, a task runner or a hosting platform? |

## Decision table

| Determinism | Repetition | Safety relevant | Org specific | Judgement | Destination | Because |
| --- | --- | --- | --- | --- | --- | --- |
| yes | yes | yes | no | no | built-in invariant | a rule every repository needs and none may drop cannot be a setting, because a setting gets lost when a repository's configuration is copied or edited |
| yes | wherever one program is used | yes | no | no | floor rule of that program's plugin | a fact about one program, such as its bypass arguments, changes with the program, so it lives in the program's plugin; marking it floor keeps any repository from dropping it |
| yes | yes | no | no | no | command, flag, or profile field (see below) | one implementation serves every repository and is fixed in one release |
| yes | in every repository of one stack | no | no | no | stack plugin, applied with `harness plugin apply` | stack facts are the same in every repository on that stack, so the binary supplies them; applying on request keeps every write something a person asked for |
| yes | any | no | yes | in applying it | entries in each repository's files, written by an agent following an organization skill | organization content stays out of an open-source binary; it differs between an organization's repositories, so applying it takes judgement, and each repository reviews the result as a diff |
| yes | any | no | yes, or one repository | no | declared action, when it reaches outside the clone at a lifecycle or agent event ([below](#declared-actions)) | side effects outside the clone must be declared, reviewed and guarded |
| yes | no | no | no | no | left to the repository (a task in its own task runner, or a profile value) | one repository's need does not justify a contract every user carries |
| no | yes | any | no | yes | harness skill, with a binary check that proves the outcome | judgement belongs to agents, and the check keeps it honest |
| no | any | any | yes | yes | organization skill | the organization owns its own judgement calls |
| no | no | any | no | yes | repository skill or prose | knowledge is reviewed where it applies |

When guidance already exists as a skill or a file and the only question is when an agent should read it, the
destination is a standing-context entry, whatever the other columns say ([below](#standing-context)). Why: the timing
is deterministic even when the guidance is not.

Support for any program, git and the agent harnesses included, is a plugin or a declared action, whatever the other
columns say, and never a command, a flag, a profile field or code in the binary. The program calls harness through its
commands, such as [`harness worker`](cli-standards.md#the-worker-command) as a loop runner's agent command, and harness
reaches the program through the operations of its plugin and the commands the repository declares. Why: each team picks
different programs and each program follows its own release schedule, so code in the binary for one of them breaks with
its releases and serves no team that chose another, while a plugin or an action is data a repository can replace
([ADR 0022](../adrs/0022-tool-integration.md), [ADR 0023](../adrs/0023-core-and-contracts.md)).

A safety-relevant rule that is organization specific (a deploy deny, a protected branch other than the default) is an
entry in each repository's `policy.yaml`, written there by a person or by an agent following the organization's skill.
The rendered policy enforces it like any other entry. It is neither a built-in invariant nor a floor rule of a builtin
plugin, because neither the binary nor the plugins it ships contain organization-specific content.

## Declared actions

A behaviour is a declared action when all of these hold:

1. It runs without judgement at the moment it fires, or its judgement is delegated to an `agent` action whose result
   a person reviews.
2. It reaches outside the clone, or reacts to something that happened outside it, or tells a person something.
3. It is organization or repository specific: it names a hosting platform, a CI convention, a notification channel
   or a cleanup habit that not every repository shares.
4. It is triggered at a lifecycle point or an agent event, by a person, a
   [coordinating agent](../features/lifecycle.md#the-lifecycle-stage-by-stage) or automatically (`auto: true`).

Why: these are the side effects whose form differs per team, so the team declares them and harness only fires them
under its guards ([ADR 0015](../adrs/0015-development-lifecycle.md), [ADR 0019](../adrs/0019-action-kinds.md)).

A declared action is always declared in the repository's `hooks.yaml`. An action that holds for an organization's
repositories (open a pull request on a given hosting platform, add the organization's CI-trigger commit, post to the
organization's chat) is written into each repository's `hooks.yaml` by a person or by an agent following the
organization's skill, and reviewed there as a diff. Why: an action runs in its repository with the credentials of
whoever fires it, so it must be readable and reviewed where it runs ([ADR 0013](../adrs/0013-organization-content.md)).

A declared action never lives in the binary. The binary contains no logic for the platform that hosts the team's work,
and no action runs that the repository's files do not declare ([ADR 0012](../adrs/0012-write-boundary.md)). A command or
flag that pushes, opens a pull request or calls a hosting platform is a design error, whatever the convenience
([principles.md](principles.md#7-nothing-leaves-the-clone-undeclared)).

### Choosing the kind

| Need | Kind | Because |
| --- | --- | --- |
| Run a CLI the repository already uses (`git`, the hosting platform's CLI) | `run` | the CLI carries its own authentication and conventions |
| Notify a person or a service: a chat message, an incident, a status page | `http` | harness performs the request itself, reads the URL and headers from environment variables and JSON-encodes values, so no `curl`, no shell quoting and no secret on a command line |
| Tell a person the agent is waiting for them, or has finished a turn | `http` (or `run` for a local notifier) on `attention` or `turn_end` | notification events accept `run` and `http` only, so an agent hook never starts another agent |
| Tell a person an unattended run stopped or a task was skipped | `http` on `blocked` | `blocked` fires when a command without a terminal exits 3, and whoever drives an unattended run fires it when the run stops, with the reason in `HARNESS_REASON` |
| Judgement after an event: summarise a merged change, write lessons into memory, triage a failure | `agent` | the agent does the judgement under the [worker contract](../features/ralph.md#the-worker-contract); its edits stay uncommitted for review |
| Use a tool only an MCP server provides | `agent`, or leave it to the coordinating agent | harness is never an MCP client; the agent owns the connection, its credentials and its permissions |

When the behaviour needs judgement before the side effect (which commits belong together, how to word them), split
it: an agent guided by a skill does the judgement, then runs `harness lifecycle run <event>`, so the side effect stays
declared, visible and recorded.

## Standing context

Use a standing-context entry when guidance that already exists must reach the agent at a predictable moment, instead
of relying on someone to invoke it ([ADR 0020](../adrs/0020-standing-context.md)).

| The guidance | Destination | Because |
| --- | --- | --- |
| applies to every session (house rules, coding guidelines) | `context.always`, injected as content | the agent sees it before its first action, and again after compaction |
| must hold even on a machine without harness | `context.always` with `static: true` | it is written into `AGENTS.md`, which every agent reads without the binary |
| applies only to unattended runs (or only to interactive sessions) | the entry with `for: [worker]` (or `[interactive]`) | each role pays only for what applies to it |
| applies when certain files are touched (SQL, migrations, a payment module) | `context.when.paths` | the trigger is the file, not the person's memory |
| applies when certain words appear in the person's message (time zones, a domain term) | `context.when.prompt`, usually `inject: pointer` | a one-line pointer costs one line; the agent reads the skill only when it applies |
| applies only when the agent judges it relevant | a plain skill, loaded by its description | no deterministic trigger exists, so the agent's own matching is the right mechanism |

Always-on content has a budget per role (`context.budget`), because every always-on line is paid in every session and
dilutes attention. A long directive that applies to few tasks is a `when` entry or a plain skill, not an `always`
entry.

## Choosing among command, flag and profile field

For deterministic, repeated, organization-neutral behaviour:

1. **Profile field** when repositories need different values for the same behaviour (the command of a test lane, the
   lint command, the bootstrap commands). The default MUST preserve existing behaviour, so adding the field changes
   nothing for repositories that do not set it.
2. **Flag** when the same repository needs the behaviour sometimes, per invocation (`verify --full`,
   `verify --base <ref>`, `worktree create --no-bootstrap`). A flag that every invocation in a repository would pass is
   a profile field instead, because a flag repeated on every call is a setting written in the wrong place.
3. **New command** when the operation has a different object or a different effect from every existing command. A
   behaviour that changes what an existing command writes is a flag or a field, not a new command, because two
   commands writing the same files compete.

None of the three serves a single program: that is a plugin or a declared action
([above](#decision-table)).

## When behaviour splits

Most capabilities have a deterministic half and a judgement half. Split them, because each half needs a different kind
of owner:

- The binary does the deterministic half and exposes a check.
- The skill tells the agent how to do the judgement half and ends with the command that checks it.
- The binary never performs the judgement half by heuristics; the skill never re-implements the deterministic half
  in prose.

Adoption, taking over a repository's existing hand-written agent setup, is the pattern to follow.
`harness scaffold --adopt` imports what it can map and lists the rest as a to-do, the harness skill guides the agent
through the to-do, and `harness doctor` confirms the result.

Repository content follows the same split ([ADR 0013](../adrs/0013-organization-content.md)):

- Stack facts need no judgement, so they are stack plugins whose entries the binary writes on request
  (`harness scaffold`, `harness plugin apply <name>`), adding only what the repository lacks.
- An organization's conventions differ between its repositories, so an agent following the organization's skill
  decides which entries a repository needs and writes them into its files. `harness render` renders them and
  `harness doctor` checks the result.
- Either way the entries end in the repository's own files and reach review as a diff. The binary never reads an
  organization's conventions from anywhere else.

## Worked examples

| Request | Analysis | Destination |
| --- | --- | --- |
| Render permissions for Claude Code and Codex from one policy | deterministic, every repository, safety relevant | binary: `harness render`, through the renders of the claude and codex plugins |
| Deny force pushes to the default branch | deterministic, every repository that uses git, safety relevant, a fact about git | floor rule of the git plugin |
| Deny chaining a second mise task into one invocation | deterministic, every repository that uses mise, safety relevant, a fact about mise | floor rule of the mise plugin (`mise * ::: *`) |
| Allow `mise run deploy:status` in one repository | deterministic, one repository | `policy.yaml` in that repository |
| Read-only patterns for an organization's observability MCP server | deterministic entries, organization specific, applied with judgement per repository | `shared.allow` entries in each repository's `policy.yaml`, written by an agent following the organization's skill |
| Open a pull request when work is concluded | deterministic command, leaves the clone, hosting-platform specific, belongs at `deliver` | a `deliver` action of kind `run` in `hooks.yaml` (push, then the hosting platform's pull-request command) |
| Add a CI trigger commit before the pull request | deterministic, leaves the clone with the push, organization convention, belongs at `deliver` | a `deliver` action in each repository's `hooks.yaml`, declared before the pull-request action and written there by an agent following the organization's skill |
| Give a Node repository its lint, build and test commands and a lint-on-edit hook | deterministic, the same in every Node repository | stack plugin `node`: applied by `harness scaffold` when its detect rule finds `package.json`, or by `harness plugin apply node` |
| Add a stack's entries to a repository that took it up after adoption | deterministic, stack facts | `harness plugin apply <name>`; the additions are shown as a diff and confirmed |
| Take a newer release's additions to a plugin after an upgrade | deterministic, on request | `./harnessw plugin apply node --dry-run` shows them; `./harnessw plugin apply node` writes them; across repositories, `harness fleet plugin apply node --yes --deliver` |
| Apply an organization's conventions to many repositories | judgement per repository, organization specific | an agent following the organization's skill in each repository, reviewed as a diff in each; `harness fleet` runs harness commands, never agents |
| Post to the team chat when a task completes | deterministic, leaves the clone, a notification channel | a `completed` action of kind `http` with `url_env`, `auto: true` |
| Ping me when the agent is waiting for my answer | deterministic, a notification, per person or repository | an `attention` action of kind `http` (or `run` for a desktop notifier) in `hooks.yaml` |
| Tell me when an unattended [run of a loop runner](../features/ralph.md) stops unfinished | deterministic, a notification at the moment work stops; nothing in the binary knows when a run ends | a `blocked` action of kind `http` with scope `run`, fired by whoever drives the run: `./harnessw lifecycle run blocked --scope run` |
| Write lessons into memory after a change lands | judgement, after an event | a `landed` action of kind `agent`; it runs in a fresh worktree of the default branch, and its edits stay uncommitted there for review |
| Look something up through an MCP-only service before delivery | needs an MCP tool | the coordinating agent before it runs `harness lifecycle run deliver`, or an `agent` action; never the binary |
| Choose which commits of an unattended run go into which pull request and reword them | judgement (grouping, wording), conventions vary by organization | an agent guided by an organization or personal skill; the agent then runs `harness lifecycle run deliver` |
| Remove the worktree after its branch is merged | deterministic, reacts to a merge, every repository | binary: `harness doctor` lists the landed branch and prints the command; `harness worktree remove` fires `landed`, then removes the worktree |
| Delete the remote branch once the work has landed | deterministic, leaves the clone, repository habit, belongs at `landed` | a `landed` action in `hooks.yaml` (`git push origin --delete "$HARNESS_BRANCH"`), `auto: true` if it should run whenever `worktree remove` handles a landed branch |
| Apply the team's coding guidelines in every session without invoking them | known guidance, every session | `context.always` entry naming the skill |
| Apply the SQL review skill whenever SQL or migrations are touched | known guidance, triggered by files | `context.when.paths` entry |
| Point the agent at the time-zone skill when the request mentions time zones | known guidance, triggered by words | `context.when.prompt` entry with `inject: pointer` |
| Keep a must-follow directive in force even where harness is not installed | known guidance, must hold without the binary | `context.always` entry with `static: true` |
| Pick which tests to run for a change | judgement (which code is affected) | agent; the repository declares lanes and `required_when` globs in the profile; `harness verify` records the choice and replays it |
| Lint a file after every edit | deterministic, per-repository command | `hooks.yaml` `post_edit` entry |
| Support a new agent CLI | deterministic translation of the same policy and hooks | a harness plugin that fills the [harness contract](../features/plugins.md#the-harness-contract): shipped in a release, or declared by a repository as a fetched plugin; never code in the binary |
| Use a version control system other than git | the answers every attempt relies on: base revision, files at a revision, reset, worktrees | a plugin that fills the [version-control contract](../features/plugins.md#the-version-control-contract), named by `vcs:` in the profile |
| Finish an adoption: move a hook the importer could not model, review the rules imported from the `local` scope | judgement | harness skill, checked by `harness doctor` |
| Summarise the effective permissions in `AGENTS.md` | deterministic, derived from the policy | binary: the `policy` [convention block](generated-content-standards.md#block-classes) |
| Tell people and agents where worktrees are created and on which branch | deterministic, derived from the profile, must hold without harness | binary: the `worktrees` convention block in `AGENTS.md`, and the git plugin's `worktrees` block in `.gitignore` ([principles.md](principles.md#8-conventions-outlive-the-tool)) |
| Keep worktrees and state somewhere other than `.work/` | deterministic, different values per repository | profile fields `state.dir`, `worktrees.dir`; `harness render` rewrites the blocks that state them |
| Keep harness configuration beside the skills in `.agents/` | `.agents/` is the cross-tool agent-content directory | not allowed: harness configuration lives in `.harness/`, state at the profile's paths |
| Explain in `AGENTS.md` why the team keeps e2e tests mandatory for routes | judgement, one repository | repository prose, outside [managed blocks](generated-content-standards.md#managed-blocks) |
| Record a lesson learned during a session | judgement | agent, in the repository's memory; never the binary |
| Open an issue for each to-do item | judgement, writes to the record of work | agent with its own tools; the binary never writes issues |
| Report worktrees whose branch was merged | deterministic, every repository | a `doctor` check |
| Install an organization's shared skills in a repository | a third-party installer, organization data | the team's own step with the tool of its choice (`npx skills add acme/agent-skills`), then a commit of `.agents/skills/` and `skills-lock.json`; the [`skills` plugin](../features/plugins.md#skills) describes the installer, and standing context names the skills it injects |
| Run ralph-tui over a task list with every result verified | a third-party loop runner, deterministic wiring | the `ralph-tui` plugin: its template `.ralph-tui/config.toml` names `./harnessw worker <agent> --commit` as the agent command; the person runs `ralph-tui` in a managed worktree, never through a harness command |
| Update Beads when work starts, completes or stops | a third-party tracker, deterministic, at lifecycle points | the `beads` plugin: the actions `bead-start`, `bead-close` and `bead-comment` on `start`, `completed` and `blocked`, scope `task`, `auto: true`, which run `bd` with `$HARNESS_TASK` |
| Support another loop runner or task tracker | a third-party program | a plugin, builtin or fetched, or declared actions and a configuration that names `harness worker`; never a command, flag or profile field |
| Pin the harness version a repository runs | deterministic, every repository, affects what every machine runs | binary: `.harness/wrapper.yaml`, the only pin, and the committed wrapper `harnessw`; `harness wrapper --version <tag>` changes the pin after verifying the release's signed checksum file |
| Deliver a harness upgrade to every repository | deterministic tool-owned changes, many repositories | `harness fleet wrapper --version <tag> --deliver`, then, once it lands, `harness fleet render --deliver`: fleet commits on a local branch and fires each repository's own `deliver` actions |
| Stop using harness but keep its conventions, permissions and hooks | deterministic, per repository | binary: `harness remove --eject` |
| Run harness from an agent hook on a machine that has not downloaded it | implicit entry point | `./harnessw --no-download`: the hook skips with one line; it never downloads |

## Anti-patterns

Each of these breaks a principle, for the reason given after it.

- A command that writes free prose, memory or skill content: the binary does only deterministic work. Managed blocks
  hold only fixed convention statements derived from the repository files.
- A setting that turns off an invariant or a floor rule: once either is optional, it gets lost when a repository's
  configuration is copied or edited. For example, if `acme/web` copies its configuration from `acme/api`, where someone
  switched off the git plugin's deny on force pushes to the default branch, `acme/web` allows force pushes without
  anyone there deciding to.
- A key that names a source outside the repository for entries harness renders or runs (an inherited file, an include, a
  remote set of entries), other than a release or a plugin pinned by hash: a rule whose source sits outside the
  repository cannot be read or reviewed where it acts ([ADR 0013](../adrs/0013-organization-content.md)).
- Organization content in a builtin plugin: builtin plugins describe programs and stacks and ship in an open-source
  release, and every other user would carry entries they never use.
- A plugin's entries applied by a command that was not asked to apply them (a render, an upgrade), or a record of which
  entries a plugin added: every write to a repository's files is one someone asked for, and a record would tie the
  repository's own entries to a definition outside its files.
- A program's safety fact kept as a policy entry instead of a floor rule of its plugin: an entry gets lost when the
  configuration is copied or edited.
- A plugin fetched during a hook or an unattended attempt: the attempt would run whatever the source serves at that
  moment, instead of the content the repository pinned.
- A heuristic in the binary that guesses where an agent should decide: a wrong guess silently rewrites a team's
  configuration.
- A skill that lists the steps a command performs instead of calling the command: the description drifts from the
  command.
- An organization name, vendor or convention as a binary default: the binary is open source and serves every
  organization.
- A new command whose only difference from an existing one is a default value: one operation, one command.
- A command or flag that pushes, opens a pull request, or calls a hosting platform's API instead of a declared action:
  side effects outside the clone are declared, reviewed and guarded.
- A command, flag or profile field for one program, binary code that names a program, or binary code that reads a
  program's configuration: the binary would follow that program's releases and formats, and serve no team that chose
  another.
- harness acting as an MCP client, or calling a model: the agent owns those connections; harness configures agents and
  never acts as one.
- A webhook URL, token or header value written literally in `hooks.yaml`: committed files are readable by everyone
  with access, so a secret there is published.
- An `agent` action on a notification event, or an `agent` action that commits or pushes: agents do not start agents
  from agent hooks, and an agent run's edits are reviewed before they leave the clone.
- A declared action that needs judgement to run correctly, as a `run` or `http` action: the judgement belongs to an
  agent before the action fires, or to an `agent` action.
- Copying a skill's text into `AGENTS.md` to make sure the agent reads it: a `context.always` entry (with `static: true`
  where it must hold without the binary) keeps one source.
- A standing-context entry for guidance that applies to few tasks as `always`: every always-on line is paid in every
  session.
- An implicit entry point (a rendered hook) that downloads the binary: a hook must never stall or fail a session.
- harness configuration or state in `.agents/`, or state in `.harness/`: each directory has one owner.
- A convention that exists only in harness's configuration, without a plain-language statement in `AGENTS.md`: it
  would not survive the tool.
- A convention block that names a harness command: eject keeps convention blocks, so the command would no longer
  exist.
