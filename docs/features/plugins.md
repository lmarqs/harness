# Plugins

## Objective

**Problem.** A repository's agent setup touches many programs: a version control system, one or more agent harnesses,
and often a loop runner, a task tracker, a task runner and the tools of its stack. Each has its own files, commands and
arguments that are never safe, and each team picks different ones, so a setup written by hand comes out different in
every repository and loses its safety rules when it is copied. A tool with code for particular programs makes every
repository depend on choices its team did not make, and a setup shared from outside the repository leaves a reviewer
unable to tell what it runs.

**Goal.** A repository gets the setup and the safety rules of each program it uses in one reviewed change, everything
it runs can be read from its own files and the plugins it names, and a team can use a program the release does not
describe without waiting for a new release.

## Overview

A plugin is a named, versioned bundle of data that describes one program to the `harness` binary. The binary contains
code for no program, git and the agent harnesses included: everything it starts, calls or writes files for is
described by a plugin ([ADR 0022](../adrs/0022-tool-integration.md)). A plugin holds up to five parts:

| Part | What it is | When it acts |
| --- | --- | --- |
| [Entries](#entries) | additions to the profile, the policy and the hooks file | once, when the plugin is applied; the repository's own afterwards |
| [Templates](#templates) | whole files | once, when the plugin is applied and the file is absent; the repository's own afterwards |
| [Renders](#renders) | owned files, keys and blocks, the plugin's [floor](#the-floor) rules among them | on every `harness render`, recorded in the manifest under the plugin's name |
| [Operations](#operations) | the commands and mappings that fill one of the binary's two [contracts](#contracts) | whenever the binary needs an answer from the program or starts it |
| [Checks](#checks) | a version command with a supported range, and a detect rule | `doctor` runs the version command; `scaffold` and `doctor` use the detect rule |

The release embeds the [builtin plugins](#builtin-plugins): `git`, `claude`, `codex`, `ralph-tui`, `beads`, `mise`,
`skills`, and the [stack plugins](#stack-plugins) `go`, `node`, `terraform` and `python`. The project's CI tests each
one against the program versions this documentation names. A repository may also declare a
[fetched plugin](#fetched-plugins), pinned by version and sha256, for a program the release does not describe.

The profile lists the plugins a repository uses under `plugins:` ([profile](profile.md#plugins)). `harness scaffold`
writes the list from the plugins whose detect rule matches, and `harness plugin apply <name>` adds one: it writes the
plugin's entries and templates once, then renders, so the additions and their rendered effect are one diff to review.
From then on the entries and templates are the repository's own, like anything written by hand, while the renders and
the floor come from the plugin on every render. Deleting a name from `plugins:` and rendering removes that plugin's
renders and leaves what it applied.

Commands are named `harness <command>` on this page. The examples run them through the repository's wrapper,
`./harnessw <command>`, from the root of a checkout; with a global install, `harness <command>` is equivalent
([wrapper](wrapper.md)).

## When to use it

| Situation | Command |
| --- | --- |
| Set up a new repository with the plugins its files call for | `harness scaffold`: it applies every builtin plugin whose detect rule matches ([scaffold](scaffold.md#plugin-detection)) |
| Set up a new repository with chosen plugins only | `harness scaffold --no-detect --plugin claude --plugin node` |
| The repository gained a stack, such as a Node front end in a Go repository | `./harnessw plugin apply node` |
| See which plugins the pinned release holds, which fit this repository and which are in use | `./harnessw plugin list` |
| Run ralph-tui over the repository's tasks, each attempt under [`harness worker`](ralph.md) | `./harnessw plugin apply ralph-tui` |
| Mark, close and comment on [Beads](tasks.md) tasks from managed worktrees | `./harnessw plugin apply beads` |
| Read everything a plugin holds | `./harnessw plugin show <name>` |
| See what a newer release's plugin adds, after moving the pin | `./harnessw plugin apply <name> --dry-run` |
| Use a program the release has no plugin for | declare a [fetched plugin](#fetched-plugins), then `./harnessw plugin apply <name>` |
| Stop using a program | delete its name from `plugins:` and run `./harnessw render` ([After applying](#after-applying)) |
| Apply a plugin in many repositories, each delivered for review | `harness fleet plugin apply <name> --yes --deliver` ([fleet](fleet.md)) |
| Bring an organization's conventions into a repository | a reviewed change to the repository's files: see [Organization choices](#organization-choices) |

### Organization choices

A plugin holds only facts that are the same in every repository that uses its program or its stack. An organization's
choices, such as the MCP tools its agents may read, the deploy tasks they must never run, a trailer its CI needs on
delivery commits, or the rules for its task tracker, differ between its repositories, so applying them takes judgement.
A person or an agent writes them into each repository's own files, typically an agent following a skill the
organization publishes, and each repository reviews the change as a diff like any other:

```sh
npx skills add acme/agent-skills                        # the organization's skill, installed in the repository
claude "apply the acme agent conventions to this repository"
./harnessw render
git diff .harness/ .claude/settings.json .codex/ AGENTS.md
```

Afterwards the entries are in the repository's files, where its people and agents read them, and the repository
renders the same bytes on every machine without fetching anything.

## Parts of a plugin

### Entries

Entries are additions to the repository's three YAML files: commands, lanes, validators, agent declarations and agent
settings in `.harness/config.yaml`; `worker.deny` and other policy entries in `.harness/policy.yaml`; post-edit hooks
and lifecycle actions in `.harness/hooks.yaml`. `apply` adds only what the files lack, following the
[merge rules](#merge-rules). From then on each entry is the repository's own: people and agents change or delete it by
editing the file, and nothing records which plugin wrote it ([ADR 0013](../adrs/0013-organization-content.md)). A newer
release's entries reach a repository only when someone applies the plugin again ([Applying again](#applying-again)).

Entries fit what a repository may reasonably change, such as a stack's lint command or a tracker's lifecycle actions. A
rule that must hold whatever the repository edits is a [floor](#the-floor) rule instead.

### Templates

A template is a whole file a plugin holds, rendered once with values from the profile, such as the agents it declares.
`apply` writes it only when the file is absent. From then on it is the repository's own file: `harness` never renders
it again, never overwrites it and never removes it, and [`harness remove`](remove.md) leaves it. When the file exists,
`apply` writes nothing to it and reports `kept <path>: exists`.

`harness scaffold` seeds the repository files, such as `.harness/config.yaml`, from templates of its own
([scaffold](scaffold.md#greenfield)). The manifest records those as seeded files, which `harness remove` deletes
([remove](remove.md#undo-and-eject-compared)); nothing records a template a plugin wrote.

A builtin plugin is the one in the running release, which in a repository set up with `harness scaffold` is the release
it pins in `.harness/wrapper.yaml`. A newer release may hold more entries or templates in a plugin; they reach a
repository only when someone applies the plugin again ([Applying again](#applying-again)).

### Renders

A render is content the binary owns and writes on every `harness render`, from the repository's declared data and the
floor rules of the plugins in use: a whole file, keys of a JSON or TOML file, entries in a list such a file shares with
others, or a [managed block](render.md#managed-blocks). The manifest records each render with the plugin's name as its
owner; `render --check` reports a hand edit to it as drift, the next render overwrites the edit, and `harness remove`
deletes it ([concepts](concepts.md#ownership-and-the-manifest)).

| Plugin | Renders |
| --- | --- |
| `claude` | `permissions.allow`, `permissions.ask`, `permissions.deny` and the harness hook entries of `.claude/settings.json`; the `import` block of `CLAUDE.md`; the `.claude/skills/harness` link |
| `codex` | `.codex/rules/harness.rules`; the owned keys of `.codex/config.toml`; the harness entries of `.codex/hooks.json`; the `.codex/skills/harness` link |
| `git` | the `state` and `worktrees` blocks of `.gitignore` |
| `ralph-tui` | the `ralph-tui` block of `.gitignore`: `/.ralph-tui/*` and `!/.ralph-tui/config.toml` |

The binary renders its own content with no plugin: `harnessw`, the harness skill, the blocks of `AGENTS.md` and
`README.md`, and the manifest ([what render writes](render.md#what-render-writes)). When a plugin leaves `plugins:`,
or an agent leaves `agents`, the next render removes what was rendered for it.

### Operations

Operations are what the binary asks of a program and how it starts one. They fill one of the two contracts the binary
declares ([ADR 0023](../adrs/0023-core-and-contracts.md)):

- the [version-control contract](#the-version-control-contract), filled by one plugin per repository, the one the
  profile's `vcs:` names;
- the [harness contract](#the-harness-contract), filled by one plugin per declared agent, the one
  `agents.<name>.plugin` names, by default the plugin named like the agent.

A plugin fills at most one contract, and most fill none: `ralph-tui`, `beads`, `mise`, `skills` and the stack plugins
have no operations. Every operation is a command with placeholders and a declared output kind, or a mapping the binary
reads as data. The binary never parses a program's free text: an answer is a string, a list of lines, bytes or an exit
status.

### Checks

| Check | What it is | Used by |
| --- | --- | --- |
| `version` | a command that prints the program's version, and the range of versions the plugin supports | [`doctor`](doctor.md): a version outside the range is a finding |
| `detect` | file globs whose match shows the program is in use | `scaffold` applies the builtin plugins whose rule matches; `doctor` reports a program whose rule matches while its plugin is not in `plugins:` |

For example, a repository with a `mise.toml` and no `mise` in `plugins:` gets the `doctor` finding
`mise detected; its plugin and its floor are not in use; run ./harnessw plugin apply mise`.

Both checks are optional. A plugin without a version check supports any version of its program. Whether or not a
plugin describes it, `doctor` reports a program that a plugin, a declared command or an action names and that is not
installed.

### The floor

A plugin may mark deny rules and bypass arguments as floor. Floor rules are part of the plugin's renders:

- Every floor deny rule of every plugin in use is rendered into every declared agent's native permissions on every
  render. It is never written into `.harness/policy.yaml`, and the policy cannot remove or weaken it: an entry that
  tries, such as an `allow` for a force push to the default branch, is dropped from the output and reported by `render`
  and `doctor` ([policy](policy.md#the-floor)).
- A harness plugin's floor bypass arguments are refused by `harness worker` and by `agent` actions wherever they appear
  among the agent's arguments, with exit 1 naming the argument ([ralph](ralph.md#the-worker-contract)).

The builtin plugins mark this floor:

| Plugin | Floor |
| --- | --- |
| `git` | deny rules for a force push to the default branch the git plugin reports, in the spellings below |
| `mise` | the deny rule `"mise * ::: *"`: task chaining, which runs a second task in the same command and could carry an unlisted task past every rule. Claude Code only, because Codex cannot match mid-command |
| `claude` | the bypass arguments `--dangerously-skip-permissions`, `--allow-dangerously-skip-permissions` and the `bypassPermissions` permission mode |
| `codex` | the bypass arguments `--dangerously-bypass-approvals-and-sandbox`, `--yolo` and `danger-full-access` |

The git plugin's deny rules, with `{default_branch}` replaced by the branch its `default_branch` operation reports:

```yaml
- [git, push, ["--force", "--force-with-lease", "-f"], origin, ["{default_branch}", "HEAD:{default_branch}"]]
- [git, push, origin, ["+{default_branch}", "+HEAD:{default_branch}"]]
- "git push origin {default_branch} --force*"
- "git push origin {default_branch} -f*"
- "git push origin HEAD:{default_branch} --force*"
- "git push origin HEAD:{default_branch} -f*"
```

The process that applies the floor is built into the binary: refusing bypass arguments before a launch, rendering the
deny rules into every agent's output, and its own `worker.deny` entry `[harness, lifecycle]`, which names the binary's
own command ([concepts](concepts.md#built-in-invariants), [ADR 0011](../adrs/0011-safety-invariants.md)). The `policy`
block of `AGENTS.md` lists each floor rule with the plugin it comes from.

A floor protects only while its plugin is in use: deleting `mise` from `plugins:` removes the `:::` deny with it, and
`doctor` reports the gap while `mise.toml` is still there.

## Contracts

### The version-control contract

The plugin the profile's `vcs:` names fills this contract; `scaffold` writes `vcs: git`. Everything the binary reads
from or changes in the repository's history goes through it: the base commit of a managed worktree or of a worker's
attempt, the files the tamper check restores from that base, the changes `verify` fingerprints, the branches `doctor`
reports as landed, and the default branch the git plugin's floor names. The binary contains none of the commands below;
another version control system is another plugin with the same operations.

| Operation | Placeholders | Output | The git plugin runs |
| --- | --- | --- | --- |
| `head` | | string | `git rev-parse HEAD` |
| `root` | | string | `git rev-parse --show-toplevel` |
| `primary_root` | | string | `cd "$(git rev-parse --git-common-dir)/.." && pwd`, through `sh` |
| `branch` | | string | `git branch --show-current` |
| `default_branch` | | string | `git symbolic-ref --short refs/remotes/origin/HEAD`, without its `origin/` prefix, through `sh` |
| `show` | `{rev}`, `{path}` | bytes | `git show {rev}:{path}` |
| `clean` | | status | `git status --porcelain` through `sh`, clean when it prints nothing: no change to a tracked file and no untracked file that is not ignored |
| `tracked` | `{path}` | status | `git ls-files --error-unmatch {path}`, true when the path is tracked |
| `changed` | `{rev}` | lines | `git diff --name-only {rev}`, then `git ls-files --others --exclude-standard`, through `sh` |
| `patch` | `{rev}` | bytes | `git add --all --intent-to-add`, then `git diff --binary {rev}`, through `sh`, so new files are in the patch |
| `merge_base` | `{a}`, `{b}` | string | `git merge-base {a} {b}` |
| `landed` | `{branch}`, `{target}` | lines | `git cherry {target} {branch}`, keeping the lines that start with `+`, through `sh`: the commits of `{branch}` whose changes are not yet in `{target}`, compared by patch, so a cherry-pick, a rebase or a squash of the same changes counts as landed |
| `reset` | `{rev}` | status | `git reset --hard {rev}`, then `git clean -fd`, through `sh` |
| `commit` | `{message}` | status | `git add --all`, then `git commit -m {message}`, through `sh` |
| `update` | | status | `git fetch origin` |
| `branch_create` | `{branch}`, `{rev}` | status | `git branch {branch} {rev}` |
| `switch` | `{branch}` | status | `git switch {branch}` |
| `worktree_add` | `{path}`, `{branch}`, `{rev}` | status | `git worktree add -b {branch} {path} {rev}` |
| `worktree_remove` | `{path}` | status | `git worktree remove {path}` |

| Output kind | The binary reads |
| --- | --- |
| string | the output's first line, without surrounding whitespace |
| lines | one item per non-empty line |
| bytes | the output as printed |
| status | the exit status only: 0 is true, as for `clean`, or success |

An excerpt of the git plugin's `plugin.yaml` ([the plugin file](#the-plugin-file)):

```yaml
contract: vcs
operations:
  head: { run: [git, rev-parse, HEAD], output: string }
  root: { run: [git, rev-parse, --show-toplevel], output: string }
  default_branch:
    run: [sh, -c, 'ref=$(git symbolic-ref --short refs/remotes/origin/HEAD) && echo "${ref#origin/}"']
    output: string
  show: { run: [git, show, "{rev}:{path}"], output: bytes }
  clean: { run: [sh, -c, 'test -z "$(git status --porcelain)"'], output: status }
  changed:
    run: [sh, -c, 'git diff --name-only "$1" && git ls-files --others --exclude-standard', sh, "{rev}"]
    output: lines
  merge_base: { run: [git, merge-base, "{a}", "{b}"], output: string }
  landed:
    run: [sh, -c, 'git cherry "$1" "$2" | sed -n "s/^+ //p"', sh, "{target}", "{branch}"]
    output: lines
  reset:
    run: [sh, -c, 'git reset --hard "$1" && git clean -fd', sh, "{rev}"]
    output: status
  worktree_add: { run: [git, worktree, add, -b, "{branch}", "{path}", "{rev}"], output: status }
  worktree_remove: { run: [git, worktree, remove, "{path}"], output: status }
```

`git clean -fd` leaves ignored files, so a reset keeps the state directory and the `last-attempt.patch` saved there. The
git program missing is a missing prerequisite: a command that needs the version-control plugin exits 3 and names it.

### The harness contract

One plugin per declared agent fills this contract: the plugin `agents.<name>.plugin` names, by default the plugin named
like the agent. Adding an agent means adding a harness plugin, builtin or [fetched](#fetched-plugins); the policy, the
hooks file and the other plugins do not change ([ADR 0009](../adrs/0009-agent-support.md)). A harness plugin holds:

| Item | Declares |
| --- | --- |
| `launch.command` | the program the binary starts |
| `launch.unattended` | the arguments that start the agent unattended with its permission checks on |
| `launch.worker_deny` | how `worker.deny` reaches the agent: as arguments at launch, or through the rendered rules, which then require `role: worker` |
| `launch.sandbox_roots` | the arguments that open a directory beyond the checkout for writing: the clone's state directory and `agents.<name>.writable_roots` |
| `launch.model`, `launch.effort` | how an `agent` action's model and effort are passed |
| `launch.prompt` | how the binary passes the prompt of an `agent` action |
| `floor.bypass` | the arguments that would turn the agent's permission checks off, refused at every launch ([the floor](#the-floor)) |
| `payload` | the JSON paths in the agent's hook payload that give the neutral event, the session, the tool, the edited and read files, the working directory, the person's prompt text and the agent's notification message; which tools are edits and which are reads; the native event that carries each neutral event: `session-start`, `post-tool-use`, `prompt-submit`, `attention`, `turn-end` |
| `instructions` | how the agent reaches `AGENTS.md`: it reads it directly, or through an import line in a block of its own instruction file |
| `skills` | where the link to the harness skill goes |
| `native_rules` | which keys of the agent's native files hold permission rules, which [`scaffold --adopt`](scaffold.md#how-each-source-becomes-an-entry) copies into the policy |
| renders | the agent's native files, keys and entries, rendered from the neutral model ([renders](#renders)) |
| checks | the agent versions the plugin supports |

A neutral event the agent has no native event for gets nothing rendered, and `doctor` notes the gap
([hooks](hooks.md#rendering)). The two builtin harness plugins fill the contract this way:

| Item | `claude` (Claude Code 2.1.x) | `codex` (Codex CLI 0.16x) |
| --- | --- | --- |
| Unattended | `--permission-mode auto` | the `workspace-write` sandbox, network on |
| Worker denies | at launch, as disallowed tools | through the rendered rules, so `role: worker` |
| Sandbox roots | none: writes as its permissions allow | the state directory and `agents.<name>.writable_roots` |
| Model, effort | `--model <id>`, `--effort <level>` | the model id; `model_reasoning_effort` |
| Floor bypass | `--dangerously-skip-permissions`, `--allow-dangerously-skip-permissions`, `bypassPermissions` | `--dangerously-bypass-approvals-and-sandbox`, `--yolo`, `danger-full-access` |
| Edit tools, read tools | `Edit`, `Write`, `MultiEdit`; `Read` | `apply_patch`; none |
| Native events | `SessionStart`, `PostToolUse`, `UserPromptSubmit`, `Notification` for `attention`, `Stop` for `turn-end` | `SessionStart`, `PostToolUse`, `UserPromptSubmit`, the `notify` key for `turn-end`; none for `attention` |
| Instructions | the `import` block of `CLAUDE.md`, one line: `@AGENTS.md` | reads the root `AGENTS.md` directly |
| Skills | `.claude/skills/harness` | `.codex/skills/harness` |
| Native rules | `permissions.allow`, `ask`, `deny` of `.claude/settings.json` | the `prefix_rule` entries of `.codex/rules/*.rules` |

An excerpt of the claude plugin's `plugin.yaml`, its launch and payload mapping:

```yaml
contract: harness
floor:
  bypass: [--dangerously-skip-permissions, --allow-dangerously-skip-permissions, bypassPermissions]
operations:
  launch:
    command: [claude]
    unattended: [--permission-mode, auto]
    worker_deny:
      flag: --disallowedTools
      item: &rule "{{#prefix}}Bash({{words}} *){{/prefix}}{{#native}}{{string}}{{/native}}{{#bare}}Bash({{string}}){{/bare}}"
    model: [--model, "{model}"]
    effort: [--effort, "{effort}"]
    prompt: { stdin: true, args: [--print] }
  payload:
    event: hook_event_name
    session: session_id
    tool: tool_name
    file: tool_input.file_path
    cwd: cwd
    prompt: prompt
    message: message
    edit_tools: [Edit, Write, MultiEdit]
    read_tools: [Read]
    events:
      session-start: SessionStart
      post-tool-use: PostToolUse
      prompt-submit: UserPromptSubmit
      attention: Notification
      turn-end: Stop
  instructions: { file: CLAUDE.md, block: import, line: "@AGENTS.md" }
  skills: .claude/skills/harness
  native_rules: { file: .claude/settings.json, allow: permissions.allow, ask: permissions.ask, deny: permissions.deny }
renders:
  - file: .claude/settings.json
    keys:
      permissions.allow: { each: allow, item: *rule }
      permissions.ask: { each: ask, item: *rule }
      permissions.deny: { each: deny, item: *rule }
```

With this mapping, the policy entry `[git, push]` becomes `Bash(git push *)` in `permissions.deny`, and under
`harness worker claude` a `worker.deny` entry `[gh]` is passed as `--disallowedTools 'Bash(gh *)'`.

## Builtin plugins

| Plugin | Describes | Contract | Holds | Detected from |
| --- | --- | --- | --- | --- |
| [`git`](#git) | git | version control | operations, `.gitignore` blocks, the force-push floor | `.git` |
| [`claude`](#claude) | Claude Code | harness | the agent declaration, renders, operations, the bypass floor | `.claude/`, `CLAUDE.md` |
| [`codex`](#codex) | Codex CLI | harness | the agent declaration, renders, operations, the bypass floor | `.codex/` |
| [`ralph-tui`](#ralph-tui) | ralph-tui | none | a template, `worker.deny` entries, a `.gitignore` block | nothing |
| [`beads`](#beads) | Beads | none | lifecycle actions, a writable root, a `worker.deny` entry | nothing |
| [`mise`](#mise) | mise | none | the `:::` floor | `mise.toml`, `.mise.toml` |
| [`skills`](#skills) | the `skills` CLI | none | checks only | `skills-lock.json` |
| [`go`, `node`, `terraform`, `python`](#stack-plugins) | a stack | none | commands, lanes, a post-edit hook | the stack's marker files |

A plugin that nothing detects fits any repository, and only `harness plugin apply` or `scaffold --plugin` adds it. No
plugin installs, initialises or configures its program beyond the files it lists.

### `git`

The git plugin fills the [version-control contract](#the-version-control-contract) with git's commands, renders the
`state` and `worktrees` blocks of `.gitignore` (`/.work/` and `/.work/worktrees/` with the default
[state paths](profile.md#state-paths)), and marks the force push to the default branch as [floor](#the-floor). Its
version check runs `git --version` and supports git 2.22 and later 2.x releases, the first with
`git branch --show-current`. Its detect rule is `.git`, a directory in a clone and a file in a worktree.

```yaml
renders:
  - { file: .gitignore, block: state, template: "/{{state_dir}}/" }
  - { file: .gitignore, block: worktrees, template: "/{{worktrees_dir}}/" }
```

### `claude`

The claude plugin fills the [harness contract](#the-harness-contract) for Claude Code 2.1.x. Its entry declares the
agent, `agents: { claude: { role: interactive } }`, when the profile does not; the role is the repository's to change.
It renders the policy into `permissions.allow`, `ask` and `deny` of `.claude/settings.json`, the harness hook entries
and `raw` entries into its `hooks`, the `import` block into `CLAUDE.md` and the `.claude/skills/harness` link; every
other key of `.claude/settings.json` is preserved byte for byte ([render](render.md#merge-rendering-native-files)).
`.claude/settings.local.json` is never written; `scaffold --import local` reads it. The [agents](agents.md) page
describes each rendered file.

### `codex`

The codex plugin fills the [harness contract](#the-harness-contract) for Codex CLI 0.16x. Its entry declares the agent,
`agents: { codex: { role: interactive } }`, when the profile does not. It renders `.codex/rules/harness.rules`, whole,
with `prefix_rule` entries whose decisions are `allow`, `prompt` and `forbidden`; the `.codex/config.toml` keys that
enable project hooks and, for `turn_end` hooks, `notify` when the key is free; the harness and `raw` entries of
`.codex/hooks.json`; and the `.codex/skills/harness` link. A native rule string converts only when it is
`Bash(<words> *)` with a plain word prefix, and bare strings are skipped: the rule file cannot express them, and the
`policy` block of `AGENTS.md` marks them as Claude Code only. `*.local.toml` files are never written.

### `ralph-tui`

The ralph-tui plugin wires [ralph-tui](https://github.com/subsy/ralph-tui), a loop runner that starts an agent per
task, version 0.12.

| Part | Content |
| --- | --- |
| Template | `.ralph-tui/config.toml` |
| Entries | `worker.deny`: `[git, commit]`, `[git, push]`, `[gh]` |
| Render | the `ralph-tui` block of `.gitignore`: `/.ralph-tui/*`, then `!/.ralph-tui/config.toml` |

The template `.ralph-tui/config.toml` makes every agent ralph-tui starts a [`harness worker`](ralph.md): one
`[[agents]]` entry per agent the profile declares, whose command is
`./harnessw worker <agent> --commit --withhold '<promise>COMPLETE</promise>'`. ralph-tui places `defaultFlags` before
its own arguments, so the worker reads its own flags first and hands everything from the first argument it does not
recognise to the agent. The other settings keep ralph-tui from doing what the worker does: it skips no permission
prompts, commits nothing itself (the worker commits a verified attempt), and stops on a non-zero exit. For a profile
that declares Claude Code and Codex:

```toml
autoCommit = false

[errorHandling]
continueOnNonZeroExit = false

[[agents]]
name = "claude"
plugin = "claude"
command = "./harnessw"
defaultFlags = ["worker", "claude", "--commit", "--withhold", "<promise>COMPLETE</promise>"]

[agents.options]
skipPermissions = false

[[agents]]
name = "codex"
plugin = "codex"
command = "./harnessw"
defaultFlags = ["worker", "codex", "--commit", "--withhold", "<promise>COMPLETE</promise>"]

[agents.options]
fullAuto = false
```

The file is the same one [unattended runs](ralph.md#the-ralph-tui-plugin) explains key by key.

`--withhold` keeps ralph-tui's completion signal, `<promise>COMPLETE</promise>`, out of the output of an attempt that
failed verification, so ralph-tui sees it only from a verified one. The `worker.deny` entries leave commits, pushes and
`gh` to the worker and to the repository's `deliver` actions. The `.gitignore` block keeps ralph-tui's session,
progress and iteration logs out of the worker's way: without it the worker refuses the checkout as having uncommitted
changes, and `--commit` would commit them.

The plugin adds no lifecycle actions: nothing fires the events of a ralph-tui run by itself. A run goes like this:

```sh
./harnessw worktree create run-invoice
cd .work/worktrees/run-invoice
ralph-tui run --epic api-42 --headless
./harnessw lifecycle run completed --scope run    # or: blocked --scope run
```

ralph-tui merges a person's `~/.config/ralph-tui/config.toml` into the project's file; `harness` reads neither. A
ralph-tui setting that turns permission checks off reaches the worker as a bypass argument, and the worker refuses it
at the first task.

### `beads`

The beads plugin wires [Beads](https://github.com/steveyegge/beads), a task tracker used through its `bd` CLI, version
1.3.

| Part | Content |
| --- | --- |
| Entries | `start`, `completed` and `blocked` actions with scope `task`; `.beads` in `agents.codex.writable_roots`; `worker.deny`: `[bd, close]` |

The actions bind Beads to [task binding](tasks.md): every event fired in a worktree created with
`./harnessw worktree create <name> --task <id>` carries the bead id in `HARNESS_TASK` and the actor in `HARNESS_ACTOR`.

```yaml
lifecycle:
  start:
    - name: bead-start
      run: bd update "$HARNESS_TASK" --status in_progress --assignee "$HARNESS_ACTOR"
      auto: true
      scope: [task]
  completed:
    - name: bead-close
      run: bd close "$HARNESS_TASK" --reason "verified"
      auto: true
      scope: [task]
  blocked:
    - name: bead-comment
      run: bd comments add "$HARNESS_TASK" "$HARNESS_REASON"
      auto: true
      scope: [task]
```

`bd` treats a missing id as an error, so an action fired without `HARNESS_TASK` fails instead of touching another
bead. `[bd, close]` under `worker.deny` leaves closing to the `completed` action, which runs only after a verified
attempt. `.beads` goes into `agents.codex.writable_roots` when the profile declares Codex, so a Codex worker can write
the database. Nothing creates, configures or localises a Beads database; that is `bd`'s own job. When ralph-tui works
from Beads, it updates bead status itself, and these actions matter for interactive work in managed worktrees.

### `mise`

The mise plugin describes [mise](https://mise.jdx.dev), a tool-version manager and task runner, version 2026.6. It
holds the `:::` deny as [floor](#the-floor), a version check and a detect rule, and nothing else: a declared command,
lane or action that runs `mise run …` runs as written, and the plugin never reads or writes mise's own files. The whole
plugin is the [complete example](#a-complete-plugin-mise) of the plugin file.

### `skills`

The skills plugin describes the `skills` CLI, version 1.7, which installs shared skills into `.agents/skills/` and
records them in `skills-lock.json`. It holds a version check and the detect rule `skills-lock.json`, so `doctor`
reports the CLI missing or outside its range. It has no entries, templates, renders or floor, and `harness` never runs
the CLI: installing shared skills is the team's own step ([skills](skills.md)).

### Stack plugins

| Plugin | Stack | Detected from | Holds |
| --- | --- | --- | --- |
| `go` | Go | `go.mod` | lint and build commands, a `go test` lane, a format-on-edit hook for `*.go` |
| `node` | Node.js | `package.json` | lint and build commands, test lanes, a lint-on-edit hook for source files |
| `terraform` | Terraform | `*.tf` files | a format command, `validate` and `plan` lanes, a format-on-edit hook |
| `python` | Python | `pyproject.toml` or `requirements.txt` | a lint command, a test lane, a lint-on-edit hook |

Commands, lanes and [validators](profile.md#verify) (repository checks run when matching files change) go into
`.harness/config.yaml`; post-edit hooks go into `.harness/hooks.yaml`. `./harnessw plugin show <name>` prints every
entry a plugin holds. Policy entries, lifecycle actions and standing context are choices each repository makes in its
own files, so stack plugins hold none, and they hold no version check: `doctor` reports `go`, `npm` or `terraform`
missing when a declared command names it.

A stack the table does not list gets no stack plugin. `harness scaffold` then seeds the skeleton of each repository
file and the seeded delivery `ask` entry ([policy](policy.md#the-seeded-entry)), and the repository writes its commands
and lanes itself.

## Fetched plugins

A fetched plugin describes a program the release has no plugin for, or describes one differently. The repository
declares it in `plugins:` with its source, version and sha256:

```yaml
# .harness/config.yaml
plugins:
  - git
  - claude
  - node
  - name: just
    source: github:acme/harness-plugins
    version: v1.2.0
    sha256: d2f0d562418c7d176d0393d8694cc6f6f569f2dff3f7ede3788a2075d3ff5c3f
```

| Field | Meaning |
| --- | --- |
| `name` | the plugin's name; it must equal the `name` in its `plugin.yaml` and cannot be the name of a builtin plugin |
| `source` | an HTTPS base URL laid out like the [release source](wrapper.md#release-source), or `github:<owner>/<repo>`, shorthand for `https://github.com/<owner>/<repo>/releases` |
| `version` | the tag to download; it must equal the `version` in its `plugin.yaml` |
| `sha256` | the sha256 of the archive, as its publisher states it |

The archive is `<source>/download/<version>/<name>.tar.gz`, a gzip-compressed tar of the
[plugin directory](#the-plugin-file): `plugin.yaml` at its root and `files/` beside it. Only `harness plugin fetch` and
`harness plugin apply` download it, over HTTPS. The binary stores the archive in the per-user cache as
`$HARNESS_HOME/plugins/<sha256>.tar.gz`, outside the repository, and checks its sha256 every time it loads it: a
mismatch, on download or on load, stops the command with exit 1, and the archive is not used.

Nothing else fetches. `render`, `doctor`, the hooks an agent fires, `harness worker` and `agent` actions read a fetched
plugin only from the cache. When it is not there, `render` exits 3, an agent hook or a worker's attempt fails with
exit 3 before any agent starts, and `doctor` reports a finding, each naming `./harnessw plugin fetch`. A CI job that
renders or runs `doctor` runs `./harnessw plugin fetch` first. An unattended attempt resolves the
version-control plugin and the agent's harness plugin from the release or the cache before anything else and uses that
resolution for every read, comparison, reset and launch of the attempt ([ralph](ralph.md#the-worker-contract)).

A fetched plugin cannot take the name of a builtin plugin: a declaration named `git` with a `source` is refused with
exit 1 by every command that reads the profile, so a builtin plugin is never replaced under the name every page
documents. A fetched plugin may fill either contract: `vcs:` may name it, and so may `agents.<name>.plugin`, for an
agent the release has no harness plugin for ([agents](agents.md#the-harness-contract)).

The release's tests stand behind every builtin plugin, and nothing stands behind a fetched one but its author and the
repository's review. `doctor` notes each fetched plugin as `not verified by the release`, a note that does not change
its exit code; its operations, renders and floor are what its author wrote, and a harness plugin's bypass arguments are
the floor its author marked. `./harnessw plugin show <name>` prints all of it.

## How apply works

`harness plugin apply <name>...` applies one or more plugins, in the order given, to a repository already set up with
`harness scaffold`:

1. **Check.** The repository needs `.harness/config.yaml`; without it, `apply` exits 1 and names `harness scaffold`.
   Every name must be a builtin plugin of the running release or a fetched plugin declared in `plugins:`; an unknown
   name exits 1. A fetched plugin that is not cached is downloaded and verified now; a sha256 mismatch, an unreachable
   source or a builtin name exits 1. On any failure nothing is written.
2. **Plan** the additions: the name in `plugins:`, when it is not there; the entries for `.harness/config.yaml`
   (commands, lanes, validators, agent declarations and settings), `.harness/policy.yaml` (`worker.deny` entries) and
   `.harness/hooks.yaml` (post-edit hooks, lifecycle actions); and the templates whose file is absent, rendered with
   the profile. Each plugin is planned against the files as the plugins before it leave them, following the
   [merge rules](#merge-rules).
3. **Confirm.** Show the additions and every template to write as a diff, and every item kept with its difference or
   `exists`, then ask. `--yes` skips the question. Without a terminal and without `--yes`, nothing is written and the
   command exits 3. `--dry-run` prints the diff and writes nothing.
4. **Write** the additions into the files, and the templates.
5. **Render** the repository, as [`harness render`](render.md) does: the renders and floor of every plugin in use, the
   new ones included, so the additions and their rendered effect are one diff and the drift check passes on it.

When there is nothing to add, `apply` says so, writes nothing and exits 0.

### Merge rules

Applying only adds. The repository's own entries and files stay authoritative.

| Element | Rule |
| --- | --- |
| A name in `plugins:` | added at the end when the list does not hold it |
| A list entry: a post-edit hook declaration, a validator, a `worker.deny` entry, a path in `agents.<name>.writable_roots` | added when the list holds no equal entry, one with the same fields and values |
| A named item: a command, a lane, a lifecycle action, an agent | added when the repository has none by that name; when it has one that differs, the repository's stays and the difference is reported: `kept lanes.unit: differs from plugin node` |
| A template | written when the file is absent; an existing file is the repository's and stays untouched, and is reported: `kept .ralph-tui/config.toml: exists` |
| Everything already in the files | nothing is removed, replaced or reordered; comments, key order and blank lines are preserved |

Additions go at the end of the list or map they belong to; a section the file lacks is added at its end. Two plugins
applied together follow the same rules: when both name the same item, such as `commands.lint`, the first one's entry
is added and the second one's is reported as kept.

### Applying again

Applying is idempotent: a second `apply` of the same plugin by the same release adds nothing, because every entry and
template is already there. Applying again is therefore the way to take a later release's improvements to a plugin's
entries and templates. After moving the pin ([wrapper](wrapper.md#upgrading)), `./harnessw plugin apply node --dry-run`
shows what the new release would add, and `./harnessw plugin apply node` adds it as a diff to review. A plugin's renders
and floor need no applying: the next render after the pin moves takes the new release's.

Applying again also adds back an entry or a template the repository deleted, because nothing records that it was
removed on purpose. Read the diff before confirming. A template the repository kept is never updated by applying again:
to take a newer release's version, delete the file and apply, then carry over your own changes.

### After applying

`plugins:` records which plugins a repository uses, which is what their renders and floor depend on. Nothing records
which entries or templates a plugin wrote: each entry's one source of truth is the file it is in, and `harness` holds no
record it could later use to change those entries by itself ([ADR 0013](../adrs/0013-organization-content.md)). Change
or delete an applied entry or template by editing the file, then run `./harnessw render`.

To stop using a plugin, delete its name from `plugins:` and run `./harnessw render`. The render removes the plugin's
renders, which the manifest records under its name, and its floor; its entries and templates stay as the repository's
own, to edit or delete like anything else. A plugin that `vcs:` or an agent's `plugin` names cannot leave `plugins:`
while it is named: the profile fails validation with exit 1. Both removal modes of [`harness remove`](remove.md) delete
the seeded `.harness/` files whatever they hold, so applied entries go with them; a template a plugin wrote, such as
`.ralph-tui/config.toml`, stays.

## Reference

```text
harness plugin list
harness plugin show <name>
harness plugin apply <name>... [--yes] [--dry-run]
harness plugin fetch
```

`plugin` follows the `harness <noun> <verb>` shape. `apply` has no inverse, because the entries and templates it writes
become the repository's own; a plugin leaves `plugins:` by an edit ([After applying](#after-applying)). Global flags
`-C <dir>`, `--json` and `--verbose` apply to every subcommand; see [CLI reference](cli.md).

### `harness plugin list`

Prints the builtin plugins of the running release and the fetched plugins the profile declares, each with its version,
its source (`release` or the declared source), the contract it fills, whether its detect rule matches in this
repository, and whether it is in `plugins:`. A fetched plugin that is not cached is listed as `not cached`. It works in
a repository that is not scaffolded yet, where nothing is in use, so it also helps choose `scaffold --plugin`.

| Exit code | Meaning |
| --- | --- |
| 0 | Listed |

Files read: the files each detect rule names, `.harness/config.yaml` when present, `$HARNESS_HOME/plugins/`. Files
written: nothing.

### `harness plugin show`

Prints the plugin's `plugin.yaml`, its entries as the YAML they add, grouped by the file they go into, every template
with its path and content, and every render with its target and template. Templates are rendered with the repository's
profile when there is one. It does not compare anything with the repository; `apply --dry-run` shows what applying
would write here, and `render --dry-run` what rendering would change.

| Exit code | Meaning |
| --- | --- |
| 0 | Printed |
| 1 | Unknown plugin name, with the valid names listed; a fetched plugin whose cached archive does not match its `sha256`; a fetched plugin declared under a builtin name |
| 3 | A fetched plugin that is not cached: run `./harnessw plugin fetch` |

Files read: `.harness/config.yaml`, when present; `$HARNESS_HOME/plugins/<sha256>.tar.gz` for a fetched plugin. Files
written: nothing.

### `harness plugin apply`

Applies one or more plugins, in the order given ([How apply works](#how-apply-works)).

| Flag | Default | Meaning |
| --- | --- | --- |
| `<name>...` | required | One or more plugins: a builtin plugin (`git`, `claude`, `codex`, `ralph-tui`, `beads`, `mise`, `skills`, `go`, `node`, `terraform`, `python`) or a fetched plugin declared in `plugins:` |
| `--yes` | off | Apply without asking; required without a terminal |
| `--dry-run` | off | Print the additions and the kept items; write nothing |

With `--json`, the output lists the names added to `plugins:`, the entries added, the templates written and the items
kept, each with its file and, for an entry, its key.

| Exit code | Meaning |
| --- | --- |
| 0 | Applied, or nothing to add; `--dry-run` printed |
| 1 | Error: an unknown plugin, a repository that is not scaffolded, an invalid repository file, a fetched plugin whose source cannot be reached, whose archive does not match its `sha256` or that is declared under a builtin name. Nothing is written |
| 3 | Confirmation needed and there is no terminal: rerun with `--yes`; or the repository files use a newer schema than the running release supports. Nothing is written |

Files read: `.harness/config.yaml`, `.harness/policy.yaml`, `.harness/hooks.yaml`, whether each template's file
exists, `$HARNESS_HOME/plugins/`, and everything [`render`](render.md#reference) reads. Files written:
`$HARNESS_HOME/plugins/<sha256>.tar.gz` for a fetched plugin not cached yet; `.harness/config.yaml`,
`.harness/policy.yaml` and `.harness/hooks.yaml`, additions only; each template whose file was absent; then every file
`render` writes ([what render writes](render.md#what-render-writes)).

### `harness plugin fetch`

Downloads every fetched plugin `plugins:` declares that is not in the cache, checks the archive's sha256 against the
declaration, checks its `plugin.yaml` (a valid schema, the declared name and version, not a builtin name) and stores it
in the cache. A plugin already cached is checked and left. It is one of the two commands that download plugins, with
`apply`; run it once per machine after cloning a repository that declares fetched plugins, and after the declaration
changes.

It takes no arguments.

| Exit code | Meaning |
| --- | --- |
| 0 | Every requested plugin is cached and verified, or there was nothing to fetch |
| 1 | No network, or a source that cannot be reached; an archive whose sha256 differs from the declaration (both hashes are printed); an invalid `plugin.yaml`, or one whose name or version differs from the declaration; a fetched plugin declared under a builtin name. A failing plugin is not stored; the others are |

Files read: `.harness/config.yaml`, `$HARNESS_HOME/plugins/`. Files written: `$HARNESS_HOME/plugins/<sha256>.tar.gz`,
on a verified download only.

### The plugin file

A plugin is a directory:

```text
<name>/
  plugin.yaml      the plugin's description
  files/           template and render sources, when the plugin has any
```

| Field of `plugin.yaml` | Type | Required | Meaning |
| --- | --- | --- | --- |
| `schema` | integer | yes | Format version of the plugin file: `1` |
| `name` | string | yes | Lowercase letters, digits and `-`: the name `plugins:`, `vcs:` and `agents.<name>.plugin` use |
| `version` | string | yes | The plugin's version; for a fetched plugin, the `version` the profile pins |
| `description` | string | no | One line, printed by `plugin list` |
| `contract` | `vcs` \| `harness` | no | The contract `operations` fills; absent when the plugin has no operations |
| `entries` | map of file to YAML | no | [Entries](#entries), grouped by the file they go into: `.harness/config.yaml`, `.harness/policy.yaml`, `.harness/hooks.yaml`. Each value has the shape of that file |
| `templates` | list | no | [Templates](#templates): `path`, where the file is written, relative to the repository root, and `source`, a file under `files/` |
| `renders` | list | no | [Renders](#renders): `file`, the target relative to the repository root, and exactly one of `whole`, `keys`, `entries`, `block` (below) |
| `floor.deny` | list of policy entries | no | Floor deny rules, in the [policy entry forms](policy.md#entry-forms); `{default_branch}` is replaced by what the version-control plugin reports |
| `floor.bypass` | list of arguments | no | Floor bypass arguments; only with `contract: harness` |
| `operations` | map | with `contract` | The operations of the [version-control contract](#the-version-control-contract) or of the [harness contract](#the-harness-contract) |
| `checks.version` | map | no | `run`, a command that prints the version, and `range`, the supported versions, such as `">=2.1 <2.2"`. The first dotted number in the output is the version |
| `checks.detect` | list of globs | no | Paths relative to the repository root whose match shows the program is in use; a trailing `/` matches a directory |

Unknown keys are an error. A command, in `checks` or in an operation, is a list of arguments run without a shell from
the root of the checkout the binary works in. A placeholder such as `{rev}` is replaced inside the argument it
appears in, so a value never splits into more arguments or reaches a shell. An operation that needs a shell names
`sh` and passes values as positional parameters, as the git plugin's `changed` operation does.

The forms of a render:

| Form | Fields | Owns |
| --- | --- | --- |
| `whole` | `whole: true`, `template` | the whole file |
| `keys` | a map from a dotted key path to `{ each, item }`, a list with one `item` per element of the model list `each`, or to `{ value }` | those keys of a JSON or TOML file; every other key is preserved byte for byte ([render](render.md#merge-rendering-native-files)) |
| `entries` | a dotted path to a list, and `{ each, item }` | its own entries in a list the file shares with others, such as the hook entries of `.claude/settings.json` |
| `block` | a block id, and `template` | a [managed block](render.md#managed-blocks) between the `harness:<id>` markers |

A template is logic-free: `{{name}}` inserts a value of the neutral model below, `{{#name}}…{{/name}}` repeats its text
for each element of a list, or renders it once when a value is present, and `{{^name}}…{{/name}}` renders its text when
the value is empty or absent. There are no comparisons, functions or includes. A template's text is inline in
`plugin.yaml` or in a file under `files/`, named by its path.

The neutral model, the values a template reads:

| Value | Holds |
| --- | --- |
| `agents` | the declared agents, each with `name`, `role` and `plugin` |
| `agent` | in a harness plugin's renders: the agent being rendered, with `name` and `role` |
| `allow`, `ask`, `deny` | the policy entries for `agent`'s role, alternatives expanded, each with exactly one of `prefix` (with `words`, the words joined by spaces, and `argv`, the words as a quoted list), `native` or `bare` (with `string`). `deny` holds every floor rule of every plugin in use and, for `role: worker`, `worker.deny` |
| `hooks` | the neutral hook events the declarations need, each with `event` (the native event the plugin's payload mapping names), `matcher` (its edit tools, and its read tools when `when.paths` context exists, joined with `\|`) and `command` |
| `raw` | the `raw` hook entries, each with `event`, `matcher` and `run` |
| `state_dir`, `worktrees_dir` | `state.dir` and `worktrees.dir` from the profile |
| `default_branch` | the default branch the version-control plugin reports |

A hook's `command` is the [hook command](render.md#the-hook-command), which finds the wrapper through the
version-control plugin's `root` operation: with the git plugin, `"$(git rev-parse --show-toplevel)/harnessw"
--no-download hook <event>`.

#### A complete plugin: `mise`

The mise plugin needs no `files/` directory:

```text
mise/
  plugin.yaml
```

```yaml
schema: 1
name: mise
version: 1.0.0
description: mise, a tool-version manager and task runner
checks:
  version: { run: [mise, --version], range: ">=2026.6 <2026.7" }
  detect: [mise.toml, .mise.toml]
floor:
  deny:
    - "mise * ::: *"
```

In a repository whose `plugins:` holds `mise`, `render` adds `Bash(mise * ::: *)` to `permissions.deny` in
`.claude/settings.json` and lists the rule in the `policy` block of `AGENTS.md`, marked as a floor rule of the mise
plugin and as Claude Code only; `doctor` reports a `mise` outside 2026.6. `harness scaffold` applies the plugin when it
finds `mise.toml` or `.mise.toml`.

An excerpt of the ralph-tui plugin shows a template, an entry and a block render:

```yaml
schema: 1
name: ralph-tui
version: 1.0.0
description: ralph-tui, a loop runner that starts an agent per task
templates:
  - { path: .ralph-tui/config.toml, source: files/config.toml }
entries:
  .harness/policy.yaml:
    worker:
      deny:
        - [git, commit]
        - [git, push]
        - [gh]
renders:
  - file: .gitignore
    block: ralph-tui
    template: |
      /.ralph-tui/*
      !/.ralph-tui/config.toml
```

## Examples

### Add a plugin to an adopted repository

The repository was scaffolded with `--no-detect --plugin claude`, and its people already wrote their own `build`
command:

```text
$ ./harnessw plugin apply node
.harness/config.yaml
+  - node
+  lint: npm run lint -- {file}
+  unit: { select: npm test -- {pattern}, full: npm test }
.harness/hooks.yaml
+  - { paths: ["src/**/*.{ts,js}"], run: npx eslint --fix {file} }
kept commands.build: differs from plugin node
apply these additions? [y/N] y
applied node: 3 entries added, 1 kept; rendered .claude/settings.json, AGENTS.md
$ git add -A && git commit -m "chore: apply the harness node plugin"
```

### Read a plugin

```text
$ ./harnessw plugin show go
# plugin.yaml
schema: 1
name: go
version: 1.0.0
description: Go
checks:
  detect: [go.mod]

# .harness/config.yaml
commands:
  lint: gofmt -l {file} | (! grep .)
  build: go build ./...
verify:
  lanes:
    test: { select: go test {pattern}, full: go test ./... }

# .harness/hooks.yaml
post_edit:
  - { paths: ["**/*.go"], run: gofmt -w {file} }
```

### Wire ralph-tui and Beads

The repository already keeps a hand-written `.ralph-tui/config.toml`:

```text
$ ./harnessw plugin apply beads ralph-tui
.harness/config.yaml
+  - beads
+  - ralph-tui
+      writable_roots: [.beads]
.harness/policy.yaml
+    - [bd, close]
+    - [git, commit]
+    - [git, push]
+    - [gh]
.harness/hooks.yaml
+  start:
+    - { name: bead-start, run: bd update "$HARNESS_TASK" --status in_progress --assignee "$HARNESS_ACTOR", auto: true, scope: [task] }
+  completed:
+    - { name: bead-close, run: bd close "$HARNESS_TASK" --reason "verified", auto: true, scope: [task] }
+  blocked:
+    - { name: bead-comment, run: bd comments add "$HARNESS_TASK" "$HARNESS_REASON", auto: true, scope: [task] }
kept .ralph-tui/config.toml: exists
apply these additions? [y/N] y
applied beads, ralph-tui: 8 entries added, 1 kept; rendered .codex/rules/harness.rules, .gitignore, AGENTS.md
```

The existing `.ralph-tui/config.toml` stays as it is. To take the plugin's, move it aside, apply `ralph-tui` again, and
carry over what the old file needs; `./harnessw plugin show ralph-tui` prints the template first.

### Choose plugins before scaffolding

```text
$ cd ~/src/acme-web
$ harness plugin list
plugin     version  source   contract  detected  in use
git        1.0.0    release  vcs       yes       -
claude     1.0.0    release  harness   yes       -
codex      1.0.0    release  harness   no        -
ralph-tui  1.0.0    release  -         -         -
beads      1.0.0    release  -         -         -
mise       1.0.0    release  -         no        -
skills     1.0.0    release  -         no        -
go         1.0.0    release  -         no        -
node       1.0.0    release  -         yes       -
terraform  1.0.0    release  -         no        -
python     1.0.0    release  -         yes       -
$ harness scaffold --no-detect --plugin claude --plugin node --dry-run
```

`requirements.txt` here only pins a documentation tool, so the scaffold takes `node` and leaves `python` out. With
`--no-detect`, `git` is still applied, because no `--vcs` names another version-control plugin.

### A fetched plugin

acme publishes a plugin for [just](https://github.com/casey/just), a command runner its repositories use, with the
sha256 of each release's archive. In `acme/api`, a person adds the declaration from the
[fetched plugins](#fetched-plugins) example to `plugins:`, reads the plugin, and applies it:

```text
$ ./harnessw plugin fetch
fetched just v1.2.0 from github:acme/harness-plugins: sha256 verified
$ ./harnessw plugin show just
$ ./harnessw plugin apply just --yes
applied just: 2 entries added; rendered AGENTS.md
$ ./harnessw doctor
note: plugin just v1.2.0 (github:acme/harness-plugins): not verified by the release
```

Each teammate runs `./harnessw plugin fetch` once on their machine. Until then their `render` and agent hooks exit 3
naming it, and nothing is fetched behind their back.

### Stop using a program

```sh
# delete "- ralph-tui" from plugins: in .harness/config.yaml
./harnessw render        # removes the ralph-tui block from .gitignore
git diff                 # .ralph-tui/config.toml and the worker.deny entries stay, as the repository's own
```

### After an upgrade

```sh
./harnessw wrapper --version v0.10.0
./harnessw plugin apply go --dry-run      # the entries of v0.10.0's go plugin that the files lack
./harnessw plugin apply go --yes
git diff
```

The renders and the floor of every builtin plugin in use already follow v0.10.0: `harness wrapper` renders the
repository with the new release in the same change.

### Many repositories

```sh
harness fleet plugin apply node --yes --deliver
```

Each repository that lacks some of the plugin's entries gets a branch with the additions and their rendered effect,
delivered through its own `deliver` actions. A repository that already holds every entry is reported as unchanged
([fleet](fleet.md)). The command runs without a terminal in each repository, so it needs `--yes`.

## Guidelines

- Do read the diff `apply` shows before confirming: once written, the entries and templates are the repository's own
  and nothing marks where they came from.
- Do edit applied entries to fit the repository: a plugin guesses from the stack or the program, the repository knows
  its real commands, and applying again never overwrites an item you changed.
- Do run `./harnessw plugin apply <name> --dry-run` for the plugins in use after moving the pin to a newer release:
  new entries reach the repository only when someone applies the plugin again.
- Do commit the additions together with the files `apply` rendered, so the diff explains itself.
- Do keep every program the repository uses in `plugins:`, `mise` above all: a plugin's floor holds only while the
  plugin is in use, and `doctor` names a detected program whose plugin is missing.
- Do read a fetched plugin with `./harnessw plugin show <name>` before pinning it, and take its `sha256` from its
  publisher: its operations, renders and floor run in every session and every unattended attempt.
- Do write an organization's choices into each repository through a reviewed change, such as an agent following the
  organization's skill: they differ between repositories, and a plugin holds only what every repository using its
  program shares ([Organization choices](#organization-choices)).
- Don't copy a floor rule into `.harness/policy.yaml` to keep it after removing its plugin: an entry there is editable,
  which is what the floor exists to avoid. Keep the plugin in use.
- Don't apply a plugin again to restore an item you changed: `apply` keeps every item the repository names. Delete
  yours first if you want the plugin's.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| `kept lanes.unit: differs from plugin node` | The repository already has a `unit` lane, and `apply` never replaces an item the repository names | Nothing, if yours is right. To take the plugin's, delete yours and apply again |
| `kept .ralph-tui/config.toml: exists` | The file is there, and `apply` writes a template only when its file is absent | Nothing, if yours is right. To take the plugin's, move yours aside, apply again, and carry over what you need |
| ralph-tui's first task fails with the worker refusing a permission-bypass argument | A ralph-tui configuration, the project's or `~/.config/ralph-tui/config.toml`, turns permission checks off or passes a bypass argument in `defaultFlags` | Keep `skipPermissions = false` under each `[agents.options]` in `.ralph-tui/config.toml` and remove the argument; the worker refuses it whoever passes it ([ralph](ralph.md)) |
| A Beads action fails with a missing id | The event fired in a worktree created without `--task`, so `HARNESS_TASK` is empty | Create the worktree with `./harnessw worktree create <name> --task <id>` ([tasks](tasks.md)) |
| `plugin apply` exits 1 naming `harness scaffold` | The repository has no `.harness/config.yaml` | Run `harness scaffold`, which applies the detected plugins |
| `plugin apply` or `plugin show` exits 1: unknown plugin | The name is neither a builtin plugin of the running release nor a fetched plugin in `plugins:` | `./harnessw plugin list` prints the names |
| `plugin apply` exits 3 | No terminal to confirm, and no `--yes` | Review with `--dry-run`, then rerun with `--yes`; under `fleet`, always pass `--yes` |
| `render`, a hook or `harness worker` exits 3 naming `./harnessw plugin fetch`, or `doctor` reports a fetched plugin not cached | A fetched plugin in `plugins:` is not in this machine's cache, and only `plugin fetch` and `plugin apply` download | Run `./harnessw plugin fetch` |
| Exit 1: a fetched plugin's sha256 differs | The archive at the source is not the one the profile pins: a new upload under the same version, a different `source`, or tampering | Do not change the `sha256` to match. Ask the publisher; pin a version whose hash it states |
| Exit 1: a fetched plugin uses a builtin name | A `plugins:` entry with a `source` is named like a builtin plugin, such as `git` | Rename the fetched plugin; a builtin plugin cannot be replaced |
| Exit 1: `vcs` or an agent's `plugin` names a plugin not in `plugins:` | The plugin was deleted from `plugins:`, or never added | Add it back, or point `vcs:` or the agent at a plugin in use |
| Exit 3 naming `git` | The version-control plugin's program is not installed on this machine | Install git, or the program the `vcs:` plugin names |
| `doctor`: `mise detected; its plugin and its floor are not in use` | `mise.toml` is present and `mise` is not in `plugins:`, so the `:::` deny is not rendered | `./harnessw plugin apply mise` |
| `doctor` notes `not verified by the release` | The plugin is fetched: the release's tests do not cover it | Expected. Review the plugin with `./harnessw plugin show <name>` |
| An entry or a template you deleted is back after `plugin apply` | `apply` adds every entry the files lack and writes every absent template, and nothing records that you removed them | Delete it again, and read the diff before confirming the next apply |
| `plugin apply` after an upgrade adds nothing | The files already hold every entry of the plugin, or equal ones | Expected |
| Only one stack's lint command is in the profile | `commands.lint` is one named item: the first plugin applied wrote it, and the next one's was reported as kept | Write a lint command that covers both stacks, such as a script or a [mise](compatibility.md#mise) task that picks the linter by the file's extension |
| Entries of the wrong stack after `scaffold` | Marker files of another stack are present, such as a `package.json` for tooling in a Go repository | Delete the entries that do not apply from `.harness/config.yaml` and `.harness/hooks.yaml`, delete the plugin from `plugins:`, then `./harnessw render`; on the next repository, `harness scaffold --no-detect --plugin <name>` |
