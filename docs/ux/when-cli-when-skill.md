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

Every new capability has exactly one home. This page is the rule for choosing it
([ADR 0002](../adrs/0002-division-of-work.md)). It applies to proposals for harness itself and to the advice harness
gives repositories through its skill.

## Destinations

| Destination | What it is | Who maintains it |
| --- | --- | --- |
| **Built-in invariant** | a rule compiled into the binary that no file or flag can remove | harness |
| **New command** | a new verb or noun-verb in the binary | harness |
| **Flag on an existing command** | a variation of an operation the binary already performs | harness |
| **Profile field** | a per-repository value that parametrises a deterministic binary behaviour (`config.yaml`; permissions in `policy.yaml`, hooks in `hooks.yaml`, the pinned version in `wrapper.yaml`) | the repository |
| **Declared action** | an action on a lifecycle event (`lifecycle:`) or an agent notification event (`attention:`, `turn_end:`) in `.harness/hooks.yaml`, of kind `run` (a shell command), `http` (a request harness performs) or `agent` (a headless agent run) | the repository |
| **Standing-context entry** | a declared trigger (`always`, `when.paths`, `when.prompt`) that puts an existing skill or repository file in front of an agent, under `context:` in `config.yaml` | the repository |
| **Harness skill or explain topic** | guidance an agent follows to do judgement work around harness | harness (embedded, shipped with the binary) |
| **Built-in preset** | the starting entries for one technology stack (`go`, `node`, `terraform`, `python`): commands, lanes, validators, module switches and post-edit hooks, which `harness scaffold` and `harness preset apply` write into the repository's files on request | harness (embedded, shipped with the binary); once written, the entries are the repository's |
| **Organization or personal skill** | guidance an agent follows for judgement work specific to an organization's conventions, including writing the organization's choices (policy entries, actions, standing-context entries) into a repository's files | the organization; the entries it leads to are the repository's |
| **Left to the repository** | a mise task, a repository skill, prose in `AGENTS.md`, memory | the repository |

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

## Decision table

| Determinism | Repetition | Safety relevant | Org specific | Judgement | Destination | Because |
| --- | --- | --- | --- | --- | --- | --- |
| yes | yes | yes | no | no | built-in invariant | a rule every repository needs and none may drop cannot be a setting, or it is lost by copying |
| yes | yes | no | no | no | command, flag, or profile field (see below) | one implementation serves every repository and is fixed in one release |
| yes | in every repository of one stack | no | no | no | built-in preset, applied with `harness preset apply` | stack facts are the same in every repository on that stack, so the binary supplies them; applying on request keeps every write something a person asked for |
| yes | any | no | yes | no | entries in each repository's files, written by an agent following an organization skill | organization content stays out of an open-source binary; it differs between an organization's repositories, so applying it takes judgement, and each repository reviews the result as a diff |
| yes | any | no | yes, or one repository | no | declared action, when it reaches outside the clone at a lifecycle or agent event ([below](#declared-actions)) | side effects outside the clone must be declared, reviewed and guarded |
| yes | no | no | no | no | left to the repository (a mise task or a profile value) | one repository's need does not justify a contract every user carries |
| no | yes | any | no | yes | harness skill, with a binary check that proves the outcome | judgement belongs to agents, and the check keeps it honest |
| no | any | any | yes | yes | organization skill | the organization owns its own judgement calls |
| no | no | any | no | yes | repository skill or prose | knowledge is reviewed where it applies |

When guidance already exists as a skill or a file and the only question is when an agent should read it, the
destination is a standing-context entry, whatever the other columns say ([below](#standing-context)). Why: the timing
is deterministic even when the guidance is not.

A safety-relevant rule that is organization specific (a deploy deny, a protected branch other than the default) is an
entry in each repository's `policy.yaml`, written there by a person or by an agent following the organization's skill.
It is enforced by the rendered policy like any other entry, but it is not a built-in invariant, because the binary
contains no organization-specific content.

## Declared actions

A behaviour is a declared action when all of these hold:

1. It runs without judgement at the moment it fires, or its judgement is delegated to an `agent` action whose result
   a person reviews.
2. It reaches outside the clone, or reacts to something that happened outside it, or tells a person something.
3. It is organization or repository specific: it names a hosting platform, a CI convention, a notification channel
   or a cleanup habit that not every repository shares.
4. It is triggered at a lifecycle point or an agent event, by a person, a coordinating agent or automatically
   (`auto: true`).

Why: these are the side effects whose form differs per team, so the team declares them and harness only fires them
under its guards ([ADR 0015](../adrs/0015-development-lifecycle.md), [ADR 0019](../adrs/0019-action-kinds.md)).

Where it is declared: in the repository's **`hooks.yaml`**, always. An action that holds for an organization's
repositories (open a pull request on a given hosting platform, add the organization's CI-trigger commit, post to the
organization's chat) is written into each repository's `hooks.yaml` by a person or by an agent following the
organization's skill, and reviewed there as a diff. Why: an action runs in its repository with the credentials of
whoever fires it, so it must be readable and reviewed where it runs ([ADR 0013](../adrs/0013-organization-content.md)).

Never in the binary. The binary contains no logic for the platform that hosts the team's work and ships no actions
([ADR 0012](../adrs/0012-write-boundary.md)); a command or flag that pushes, opens a pull request or calls a hosting
platform is a design error, whatever the convenience ([principles.md](principles.md#7-nothing-leaves-the-clone-undeclared)).

### Choosing the kind

| Need | Kind | Because |
| --- | --- | --- |
| Run a CLI the repository already uses (`git`, the hosting platform's CLI) | `run` | the CLI carries its own authentication and conventions |
| Notify a person or a service: a chat message, an incident, a status page | `http` | harness performs the request itself, reads the URL and headers from environment variables and JSON-encodes values, so no `curl`, no shell quoting and no secret on a command line |
| Tell a person the agent is waiting for them, or has finished a turn | `http` (or `run` for a local notifier) on `attention` or `turn_end` | notification events accept `run` and `http` only, so an agent hook never starts another agent |
| Tell a person an unattended run stopped or a task was skipped | `http` on `blocked` | `blocked` fires wherever work stops for a person, with `HARNESS_REASON` |
| Judgement after an event: summarise a merged change, write lessons into memory, triage a failure | `agent` | the agent does the judgement under the worker contract; its edits stay uncommitted for review |
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

1. **Profile field** when repositories need different values for the same behaviour (a lane's command, the lint
   command, the tracker). The default MUST preserve existing behaviour, so adding the field changes nothing for
   repositories that do not set it.
2. **Flag** when the same repository needs the behaviour sometimes, per invocation (`--full`, `--base <ref>`,
   `--no-bootstrap`). A flag that every invocation in a repository would pass is a profile field instead, because a
   flag repeated on every call is a setting written in the wrong place.
3. **New command** when the operation has a different object or a different effect from every existing command. A
   behaviour that changes what an existing command writes is a flag or a field, not a new command, because two
   commands writing the same files compete.

## When behaviour splits

Most capabilities have a deterministic half and a judgement half. Split them, because each half needs a different kind
of owner:

- The binary does the deterministic half and exposes a check.
- The skill tells the agent how to do the judgement half and ends with the command that checks it.
- The binary never performs the judgement half by heuristics; the skill never re-implements the deterministic half
  in prose.

Adoption is the model: `harness scaffold --adopt` imports what it can map and lists the rest as a to-do; the harness
skill guides the agent through the to-do; `harness doctor` confirms the result.

Repository content follows the same split ([ADR 0013](../adrs/0013-organization-content.md)):

- Stack facts need no judgement, so they are built-in presets the binary writes on request (`harness scaffold`,
  `harness preset apply <name>`), adding only what the repository lacks.
- An organization's conventions differ between its repositories, so an agent following the organization's skill
  decides which entries a repository needs and writes them into its files; `harness render` renders them and
  `harness doctor` checks the result.
- Either way the entries end in the repository's own files and reach review as a diff; the binary never reads an
  organization's conventions from anywhere else.

## Worked examples

Each row shows a request and where it goes.

| Request | Analysis | Destination |
| --- | --- | --- |
| Render permissions for Claude Code and Codex from one policy | deterministic, every repository, safety relevant | binary: `harness render` |
| Deny force pushes to the default branch | deterministic, every repository, safety relevant, organization neutral | built-in invariant |
| Allow `mise run deploy:status` in one repository | deterministic, one repository | `policy.yaml` in that repository |
| Read-only patterns for an organization's observability MCP server | deterministic entries, organization specific, applied with judgement per repository | `shared.allow` entries in each repository's `policy.yaml`, written by an agent following the organization's skill |
| Open a pull request when work is concluded | deterministic command, leaves the clone, hosting-platform specific, belongs at `deliver` | a `deliver` action of kind `run` in `hooks.yaml` (push, then the hosting platform's pull-request command) |
| Add a CI trigger commit before the pull request | deterministic, leaves the clone with the push, organization convention, belongs at `deliver` | a `deliver` action in each repository's `hooks.yaml`, declared before the pull-request action and written there by an agent following the organization's skill |
| Give a Node repository its lint, build and test commands and a lint-on-edit hook | deterministic, the same in every Node repository | built-in preset `node`: applied by `harness scaffold` when it detects `package.json`, or by `harness preset apply node` |
| Add a stack's entries to a repository that took it up after adoption | deterministic, stack facts | `harness preset apply <name>`; the additions are shown as a diff and confirmed |
| Take a newer release's additions to a preset after an upgrade | deterministic, on request | `./harnessw preset apply node --dry-run` shows them; `./harnessw preset apply node` writes them; across repositories, `harness fleet preset apply node --yes --deliver` |
| Apply an organization's conventions to many repositories | judgement per repository, organization specific | an agent following the organization's skill in each repository, reviewed as a diff in each; `harness fleet` runs harness commands, never agents |
| Post to the team chat when a task completes | deterministic, leaves the clone, a notification channel | a `completed` action of kind `http` with `url_env`, `auto: true` |
| Ping me when the agent is waiting for my answer | deterministic, a notification, per person or repository | an `attention` action of kind `http` (or `run` for a desktop notifier) in `hooks.yaml` |
| Tell me when an unattended Ralph run stops unfinished | deterministic, a notification at the moment work stops | a `blocked` action of kind `http`, `auto: true` |
| Write lessons into memory after a change lands | judgement, after an event | a `landed` action of kind `agent`; it runs in a fresh worktree of the default branch, and its edits stay uncommitted there for review |
| Look something up through an MCP-only service before delivery | needs an MCP tool | the coordinating agent before it runs `harness lifecycle run deliver`, or an `agent` action; never the binary |
| Choose which Ralph task commits go into which pull request and reword them | judgement (grouping, wording), conventions vary by organization | an agent guided by an organization or personal skill; the agent then runs `harness lifecycle run deliver` |
| Remove the worktree after its branch is merged | deterministic, reacts to a merge, every repository | binary: `harness doctor` lists the landed branch and prints the command; `harness worktree remove` fires `landed`, then removes the worktree |
| Delete the remote branch once the work has landed | deterministic, leaves the clone, repository habit, belongs at `landed` | a `landed` action in `hooks.yaml` (`git push origin --delete "$HARNESS_BRANCH"`), `auto: true` if it should run whenever `worktree remove` or `ralph clean` handles a landed branch |
| Apply the team's coding guidelines in every session without invoking them | known guidance, every session | `context.always` entry naming the skill |
| Apply the SQL review skill whenever SQL or migrations are touched | known guidance, triggered by files | `context.when.paths` entry |
| Point the agent at the time-zone skill when the request mentions time zones | known guidance, triggered by words | `context.when.prompt` entry with `inject: pointer` |
| Keep a must-follow directive in force even where harness is not installed | known guidance, must hold without the binary | `context.always` entry with `static: true` |
| Pick which tests to run for a change | judgement (which code is affected) | agent; the repository declares lanes and `required_when` globs in the profile; `harness verify` records the choice and replays it |
| Lint a file after every edit | deterministic, per-repository command | `hooks.yaml` `post_edit` entry |
| Support a new agent CLI | deterministic translation of the same policy and hooks | adapter in the binary |
| Finish an adoption: reconcile a colliding `verify` task, move a hook the importer could not model | judgement | harness skill, checked by `harness doctor` |
| Summarise the effective permissions in `AGENTS.md` | deterministic, derived from the policy | binary: the `policy` convention block |
| Tell people and agents where worktrees are created and on which branch | deterministic, derived from the profile, must hold without harness | binary: the `worktrees` convention blocks in `AGENTS.md` and `.gitignore` ([principles.md](principles.md#8-conventions-outlive-the-tool)) |
| Keep worktrees and state somewhere other than `.work/` | deterministic, different values per repository | profile fields `state.dir`, `worktrees.dir`, `tasks.prd_dir`, `ralph.worktrees_dir`; `harness render` rewrites the blocks that state them |
| Keep harness configuration beside the skills in `.agents/` | `.agents/` is the cross-tool agent-content directory | not allowed: harness configuration lives in `.harness/`, state at the profile's paths |
| Explain in `AGENTS.md` why the team keeps e2e tests mandatory for routes | judgement, one repository | repository prose, outside managed blocks |
| Record a lesson learned during a session | judgement | agent, in the repository's memory; never the binary |
| Open an issue for each to-do item | judgement, writes to the record of work | agent with its own tools; the binary never writes issues |
| Report worktrees whose branch was merged | deterministic, every repository | a `doctor` check |
| Install an organization's shared skills in every repository | deterministic, organization data | `skills.sources` in each profile plus `harness skills sync`, run across repositories with `harness fleet` |
| Pin the harness version a repository runs | deterministic, every repository, affects what every machine runs | binary: `.harness/wrapper.yaml`, the only pin, and the committed wrapper `harnessw`; `harness wrapper --version <tag>` changes the pin after verifying the release's signed checksum file |
| Deliver a harness upgrade to every repository | deterministic tool-owned changes, many repositories | `harness fleet wrapper --version <tag> --deliver`, then, once it lands, `harness fleet render --deliver`: fleet commits on a local branch and fires each repository's own `deliver` actions |
| Stop using harness but keep its conventions, permissions and hooks | deterministic, per repository | binary: `harness remove --eject` |
| Run harness from an agent hook on a machine that has not downloaded it | implicit entry point | `./harnessw --no-download`: the hook skips with one line; it never downloads |

## Anti-patterns

Each of these breaks a principle; the reason is given with it.

- A command that writes free prose, memory or skill content: the binary does only deterministic work. Managed blocks
  hold only fixed convention statements derived from the repository files.
- A setting that turns off an invariant: the invariants are lost by copying as soon as they are optional.
- A key that names a source outside the repository for entries harness renders or runs (an inherited file, an
  include, a remote set of entries): a rule whose source sits outside the repository cannot be read or reviewed where
  it acts ([ADR 0013](../adrs/0013-organization-content.md)).
- Organization content in a built-in preset: presets are stack facts shipped in an open-source binary, and every other
  user would carry entries they never use.
- A preset applied by a command that was not asked to apply it (a render, an upgrade), or a record of which presets a
  repository applied: every write to a repository's files is one someone asked for, and a record would tie the
  repository's own entries to a definition outside its files.
- A heuristic in the binary that guesses where an agent should decide: a wrong guess silently rewrites a team's
  configuration.
- A skill that lists the steps a command performs instead of calling the command: the description drifts from the
  command.
- An organization name, vendor or convention as a binary default: the binary is open source and serves every
  organization.
- A new command whose only difference from an existing one is a default value: one operation, one command.
- A command or flag that pushes, opens a pull request, or calls a hosting platform's API instead of a declared action:
  side effects outside the clone are declared, reviewed and guarded.
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
