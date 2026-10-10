# Recipes

## Objective

**Problem.** A repository's agent setup touches many programs: a version control system, one or more agent harnesses,
and often a loop runner, a task tracker, a task runner and the tools of its stack. Each has its own files, commands and
arguments that are never safe, and each team picks different ones, so a setup written by hand comes out different in
every repository and loses its safety rules when it is copied. A tool with code for particular programs makes every
repository depend on choices its team did not make, and a setup shared from outside the repository leaves a reviewer
unable to tell what it runs.

**Goal.** A repository gets the setup and the safety rules of each program it uses in one reviewed change, everything
it runs can be read from its own files and the recipes it names, and a team can use a program the release does not
describe without waiting for a new release.

## Overview

A recipe is a named, versioned bundle of data that describes one program to the `the-cook` binary. The binary contains
code for no program, git and the agent harnesses included: everything it starts, calls or writes files for is
described by a recipe ([ADR 0022](../adrs/0022-tool-integration.md)). A recipe holds up to five parts:

| Part | What it is | When it acts |
| --- | --- | --- |
| [Entries](#entries) | additions to the profile, the policy and the hooks file | once, when the recipe is applied; the repository's own afterwards |
| [Templates](#templates) | whole files | once, when the recipe is applied and the file is absent; the repository's own afterwards |
| [Renders](#renders) | owned files, keys and blocks, the recipe's [floor](#the-floor) rules among them | on every `the-cook render`, recorded in the manifest under the recipe's name |
| [Operations](#operations) | the commands and mappings that fill one of the binary's two [contracts](#contracts) | whenever the binary needs an answer from the program or starts it |
| [Checks](#checks) | a version command with a supported range, and a detect rule | `doctor` runs the version command; `scaffold` and `doctor` use the detect rule |

The release embeds the [builtin recipes](#builtin-recipes): `git`, `claude`, `codex`, `ralph-tui`, `beads`, `mise`,
`skills`, and the [stack recipes](#stack-recipes) `go`, `node`, `terraform` and `python`. The project's CI tests each
one against the program versions this documentation names. A repository may also declare a
[fetched recipe](#fetched-recipes), pinned by version and sha256, for a program the release does not describe. A fetched
recipe comes from a [cookbook](#fetched-recipes): a published collection of recipes at one source.

The profile lists the recipes a repository uses under `recipes:` ([profile](profile.md#recipes)). `the-cook scaffold`
writes the list from the recipes whose detect rule matches, and `the-cook recipe apply <name>` adds one: it writes the
recipe's entries and templates once, then renders, so the additions and their rendered effect are one diff to review.
From then on the entries and templates are the repository's own, like anything written by hand, while the renders and
the floor come from the recipe on every render. Deleting a name from `recipes:` and rendering removes that recipe's
renders and leaves what it applied.

Commands are named `the-cook <command>` on this page. The examples run them through the repository's wrapper,
`./cook <command>`, from the root of a checkout; with a global install, `the-cook <command>` is equivalent
([wrapper](wrapper.md)).

## When to use it

| Situation | Command |
| --- | --- |
| Set up a new repository with the recipes its files call for | `the-cook scaffold`: it applies every builtin recipe whose detect rule matches ([scaffold](scaffold.md#recipe-detection)) |
| Set up a new repository with chosen recipes only | `the-cook scaffold --no-detect --recipe claude --recipe node` |
| The repository gained a stack, such as a Node front end in a Go repository | `./cook recipe apply node` |
| See which recipes the pinned release holds, which fit this repository and which are in use | `./cook recipe list` |
| Run ralph-tui over the repository's tasks, each attempt under [`the-cook worker`](ralph.md) | `./cook recipe apply ralph-tui` |
| Mark, close and comment on [Beads](tasks.md) tasks from managed workspaces | `./cook recipe apply beads` |
| Read everything a recipe holds | `./cook recipe show <name>` |
| See what a newer release's recipe adds, after moving the pin | `./cook recipe apply <name> --dry-run` |
| Use a program the release has no recipe for | declare a [fetched recipe](#fetched-recipes), then `./cook recipe apply <name>` |
| Stop using a program | delete its name from `recipes:` and run `./cook render` ([After applying](#after-applying)) |
| Apply a recipe in many repositories, each delivered for review | `the-cook fleet recipe apply <name> --yes --deliver` ([fleet](fleet.md)) |
| Bring an organization's conventions into a repository | a reviewed change to the repository's files: see [Organization choices](#organization-choices) |

### Organization choices

A recipe holds only facts that are the same in every repository that uses its program or its stack. An organization's
choices, such as the MCP tools its agents may read, the deploy tasks they must never run, a trailer its CI needs on
delivery commits, or the rules for its task tracker, differ between its repositories, so applying them takes judgement.
A person or an agent writes them into each repository's own files, typically an agent following a skill the
organization publishes, and each repository reviews the change as a diff like any other:

```sh
npx skills add acme/agent-skills                        # the organization's skill, installed in the repository
claude "apply the acme agent conventions to this repository"
./cook render
git diff .cook/ .claude/settings.json .codex/ AGENTS.md
```

Afterwards the entries are in the repository's files, where its people and agents read them, and the repository
renders the same bytes on every machine without fetching anything.

## Parts of a recipe

### Entries

Entries are additions to the repository's three YAML files: commands, lanes, validators, agent declarations and agent
settings in `.cook/config.yaml`; `worker.deny` and other policy entries in `.cook/policy.yaml`; post-edit hooks
and lifecycle actions in `.cook/hooks.yaml`. `apply` adds only what the files lack, following the
[merge rules](#merge-rules). From then on each entry is the repository's own: people and agents change or delete it by
editing the file, and nothing records which recipe wrote it ([ADR 0013](../adrs/0013-organization-content.md)). A newer
release's entries reach a repository only when someone applies the recipe again ([Applying again](#applying-again)).

Entries fit what a repository may reasonably change, such as a stack's lint command or a tracker's lifecycle actions. A
rule that must hold whatever the repository edits is a [floor](#the-floor) rule instead.

### Templates

A template is a whole file a recipe holds, rendered once with values from the profile, such as the agents it declares.
`apply` writes it only when the file is absent. From then on it is the repository's own file: `the-cook` never renders
it again, never overwrites it and never removes it, and [`the-cook remove`](remove.md) leaves it. When the file exists,
`apply` writes nothing to it and reports `kept <path>: exists`.

`the-cook scaffold` seeds the repository files, such as `.cook/config.yaml`, from templates of its own
([scaffold](scaffold.md#greenfield)). The manifest records those as seeded files, which `the-cook remove` deletes
([remove](remove.md#undo-and-eject-compared)); nothing records a template a recipe wrote.

A builtin recipe is the one in the running release, which in a repository set up with `the-cook scaffold` is the release
it pins in `.cook/wrapper.yaml`. A newer release may hold more entries or templates in a recipe; they reach a
repository only when someone applies the recipe again ([Applying again](#applying-again)).

### Renders

A render is content the binary owns and writes on every `the-cook render`, from the repository's declared data and the
floor rules of the recipes in use: a whole file, keys of a JSON or TOML file, entries in a list such a file shares with
others, or a [managed block](render.md#managed-blocks). The manifest records each render with the recipe's name as its
owner; `render --check` reports a hand edit to it as drift, the next render overwrites the edit, and `the-cook remove`
deletes it ([concepts](concepts.md#ownership-and-the-manifest)).

| Recipe | Renders |
| --- | --- |
| `claude` | `permissions.allow`, `permissions.ask`, `permissions.deny` and the `the-cook hook` entries of `.claude/settings.json`; the `import` block of `CLAUDE.md`; the `.claude/skills/the-cook` link |
| `codex` | `.codex/rules/the-cook.rules`; the owned keys of `.codex/config.toml`; the `the-cook hook` entries of `.codex/hooks.json`; the `.codex/skills/the-cook` link |
| `git` | the `state` and `workspaces` blocks of `.gitignore` |
| `ralph-tui` | the `ralph-tui` block of `.gitignore`: `/.ralph-tui/*` and `!/.ralph-tui/config.toml` |

The binary renders its own content with no recipe: `cook`, the shipped skill, the blocks of `AGENTS.md` and
`README.md`, and the manifest ([what render writes](render.md#what-render-writes)). When a recipe leaves `recipes:`,
or an agent leaves `agents`, the next render removes what was rendered for it.

### Operations

Operations are what the binary asks of a program and how it starts one. They fill one of the two contracts the binary
declares ([ADR 0023](../adrs/0023-core-and-contracts.md)):

- the [version-control contract](#the-version-control-contract), filled by one recipe per repository, the one the
  profile's `vcs:` names;
- the [harness contract](#the-harness-contract), filled by one recipe per declared agent, the one
  `agents.<name>.recipe` names, by default the recipe named like the agent.

A recipe fills at most one contract, and most fill none: `ralph-tui`, `beads`, `mise`, `skills` and the stack recipes
have no operations. Every operation is a command with placeholders and a declared output kind, or a mapping the binary
reads as data. The binary never parses a program's free text: an answer is a string, a list of lines, bytes or an exit
status.

### Checks

| Check | What it is | Used by |
| --- | --- | --- |
| `version` | a command that prints the program's version, and the range of versions the recipe supports | [`doctor`](doctor.md): a version outside the range is a finding |
| `detect` | file globs whose match shows the program is in use | `scaffold` applies the builtin recipes whose rule matches; `doctor` reports a program whose rule matches while its recipe is not in `recipes:` |

For example, a repository with a `mise.toml` and no `mise` in `recipes:` gets the `doctor` finding
`mise detected; its recipe and its floor are not in use; run ./cook recipe apply mise`.

Both checks are optional. A recipe without a version check supports any version of its program. Whether or not a
recipe describes it, `doctor` reports a program that a recipe, a declared command or an action names and that is not
installed.

### The floor

A recipe may mark deny rules and bypass arguments as floor. Floor rules are part of the recipe's renders:

- Every floor deny rule of every recipe in use is rendered into every declared agent's native permissions on every
  render. It is never written into `.cook/policy.yaml`, and the policy cannot remove or weaken it: an entry that
  tries, such as an `allow` for a force push to the default branch, is dropped from the output and reported by `render`
  and `doctor` ([policy](policy.md#the-floor)).
- A harness recipe's floor bypass arguments are refused by `the-cook worker` and by `agent` actions wherever they appear
  among the agent's arguments, with exit 1 naming the argument ([ralph](ralph.md#the-worker-contract)).

The builtin recipes mark this floor:

| Recipe | Floor |
| --- | --- |
| `git` | deny rules for a force push to the default branch the git recipe reports, in the spellings below |
| `mise` | the deny rule `"mise * ::: *"`: task chaining, which runs a second task in the same command and could carry an unlisted task past every rule. Claude Code only, because Codex cannot match mid-command |
| `claude` | the bypass arguments `--dangerously-skip-permissions`, `--allow-dangerously-skip-permissions` and the `bypassPermissions` permission mode |
| `codex` | the bypass arguments `--dangerously-bypass-approvals-and-sandbox`, `--yolo` and `danger-full-access` |

The git recipe's deny rules, with `{default_branch}` replaced by the branch its `default_branch` operation reports:

```yaml
- [git, push, ["--force", "--force-with-lease", "-f"], origin, ["{default_branch}", "HEAD:{default_branch}"]]
- [git, push, origin, ["+{default_branch}", "+HEAD:{default_branch}"]]
- "git push origin {default_branch} --force*"
- "git push origin {default_branch} -f*"
- "git push origin HEAD:{default_branch} --force*"
- "git push origin HEAD:{default_branch} -f*"
```

The process that applies the floor is built into the binary: refusing bypass arguments before a launch, rendering the
deny rules into every agent's output, and its own `worker.deny` entry `[the-cook, lifecycle]`, which names the binary's
own command ([concepts](concepts.md#built-in-invariants), [ADR 0011](../adrs/0011-safety-invariants.md)). The `policy`
block of `AGENTS.md` lists each floor rule with the recipe it comes from.

A floor protects only while its recipe is in use: deleting `mise` from `recipes:` removes the `:::` deny with it, and
`doctor` reports the gap while `mise.toml` is still there.

## Contracts

### The version-control contract

The recipe the profile's `vcs:` names fills this contract; `scaffold` writes `vcs: git`. Everything the binary reads
from or changes in the repository's history goes through it: the base commit of a managed workspace or of a worker's
attempt, the files the tamper check restores from that base, the changes `verify` fingerprints, the branches `doctor`
reports as landed, and the default branch the git recipe's floor names. The binary contains none of the commands below;
another version control system is another recipe with the same operations.

| Operation | Placeholders | Output | The git recipe runs |
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
| `workspace_add` | `{path}`, `{branch}`, `{rev}` | status | `git worktree add -b {branch} {path} {rev}` |
| `workspace_remove` | `{path}` | status | `git worktree remove {path}` |

| Output kind | The binary reads |
| --- | --- |
| string | the output's first line, without surrounding whitespace |
| lines | one item per non-empty line |
| bytes | the output as printed |
| status | the exit status only: 0 is true, as for `clean`, or success |

An excerpt of the git recipe's `recipe.yaml` ([the recipe file](#the-recipe-file)):

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
  workspace_add: { run: [git, worktree, add, -b, "{branch}", "{path}", "{rev}"], output: status }
  workspace_remove: { run: [git, worktree, remove, "{path}"], output: status }
```

`git clean -fd` leaves ignored files, so a reset keeps the state directory and the `last-attempt.patch` saved there. The
git program missing is a missing prerequisite: a command that needs the version-control recipe exits 3 and names it.

### The harness contract

One recipe per declared agent fills this contract: the recipe `agents.<name>.recipe` names, by default the recipe named
like the agent. Adding an agent means adding a harness recipe, builtin or [fetched](#fetched-recipes); the policy, the
hooks file and the other recipes do not change ([ADR 0009](../adrs/0009-agent-support.md)). A harness recipe holds:

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
| `skills` | where the link to the shipped skill goes |
| `native_rules` | which keys of the agent's native files hold permission rules, which [`scaffold --adopt`](scaffold.md#how-each-source-becomes-an-entry) copies into the policy |
| renders | the agent's native files, keys and entries, rendered from the neutral model ([renders](#renders)) |
| checks | the agent versions the recipe supports |

A neutral event the agent has no native event for gets nothing rendered, and `doctor` notes the gap
([hooks](hooks.md#rendering)). The two builtin harness recipes fill the contract this way:

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
| Skills | `.claude/skills/the-cook` | `.codex/skills/the-cook` |
| Native rules | `permissions.allow`, `ask`, `deny` of `.claude/settings.json` | the `prefix_rule` entries of `.codex/rules/*.rules` |

An excerpt of the claude recipe's `recipe.yaml`, its launch and payload mapping:

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
  skills: .claude/skills/the-cook
  native_rules: { file: .claude/settings.json, allow: permissions.allow, ask: permissions.ask, deny: permissions.deny }
renders:
  - file: .claude/settings.json
    keys:
      permissions.allow: { each: allow, item: *rule }
      permissions.ask: { each: ask, item: *rule }
      permissions.deny: { each: deny, item: *rule }
```

With this mapping, the policy entry `[git, push]` becomes `Bash(git push *)` in `permissions.deny`, and under
`the-cook worker claude` a `worker.deny` entry `[gh]` is passed as `--disallowedTools 'Bash(gh *)'`.

## Builtin recipes

| Recipe | Describes | Contract | Holds | Detected from |
| --- | --- | --- | --- | --- |
| [`git`](#git) | git | version control | operations, `.gitignore` blocks, the force-push floor | `.git` |
| [`claude`](#claude) | Claude Code | harness | the agent declaration, renders, operations, the bypass floor | `.claude/`, `CLAUDE.md` |
| [`codex`](#codex) | Codex CLI | harness | the agent declaration, renders, operations, the bypass floor | `.codex/` |
| [`ralph-tui`](#ralph-tui) | ralph-tui | none | a template, `worker.deny` entries, a `.gitignore` block | nothing |
| [`beads`](#beads) | Beads | none | lifecycle actions, a writable root, a `worker.deny` entry | nothing |
| [`mise`](#mise) | mise | none | the `:::` floor | `mise.toml`, `.mise.toml` |
| [`skills`](#skills) | the `skills` CLI | none | checks only | `skills-lock.json` |
| [`go`, `node`, `terraform`, `python`](#stack-recipes) | a stack | none | commands, lanes, a post-edit hook | the stack's marker files |

A recipe that nothing detects fits any repository, and only `the-cook recipe apply` or `scaffold --recipe` adds it. No
recipe installs, initialises or configures its program beyond the files it lists.

### `git`

The git recipe fills the [version-control contract](#the-version-control-contract) with git's commands, renders the
`state` and `workspaces` blocks of `.gitignore` (`/.work/` and `/.work/workspaces/` with the default
[state paths](profile.md#state-paths)), and marks the force push to the default branch as [floor](#the-floor). Its
version check runs `git --version` and supports git 2.22 and later 2.x releases, the first with
`git branch --show-current`. Its detect rule is `.git`, a directory in a clone and a file in a git worktree.

```yaml
renders:
  - { file: .gitignore, block: state, template: "/{{state_dir}}/" }
  - { file: .gitignore, block: workspaces, template: "/{{workspaces_dir}}/" }
```

### `claude`

The claude recipe fills the [harness contract](#the-harness-contract) for Claude Code 2.1.x. Its entry declares the
agent, `agents: { claude: { role: interactive } }`, when the profile does not; the role is the repository's to change.
It renders the policy into `permissions.allow`, `ask` and `deny` of `.claude/settings.json`, the `the-cook hook` entries
and `raw` entries into its `hooks`, the `import` block into `CLAUDE.md` and the `.claude/skills/the-cook` link; every
other key of `.claude/settings.json` is preserved byte for byte ([render](render.md#merge-rendering-native-files)).
`.claude/settings.local.json` is never written; `scaffold --import local` reads it. The [agents](agents.md) page
describes each rendered file.

### `codex`

The codex recipe fills the [harness contract](#the-harness-contract) for Codex CLI 0.16x. Its entry declares the agent,
`agents: { codex: { role: interactive } }`, when the profile does not. It renders `.codex/rules/the-cook.rules`, whole,
with `prefix_rule` entries whose decisions are `allow`, `prompt` and `forbidden`; the `.codex/config.toml` keys that
enable project hooks and, for `turn_end` hooks, `notify` when the key is free; the `the-cook hook` and `raw` entries of
`.codex/hooks.json`; and the `.codex/skills/the-cook` link. A native rule string converts only when it is
`Bash(<words> *)` with a plain word prefix, and bare strings are skipped: the rule file cannot express them, and the
`policy` block of `AGENTS.md` marks them as Claude Code only. `*.local.toml` files are never written.

### `ralph-tui`

The ralph-tui recipe wires [ralph-tui](https://github.com/subsy/ralph-tui), a loop runner that starts an agent per
task, version 0.12.

| Part | Content |
| --- | --- |
| Template | `.ralph-tui/config.toml` |
| Entries | `worker.deny`: `[git, commit]`, `[git, push]`, `[gh]` |
| Render | the `ralph-tui` block of `.gitignore`: `/.ralph-tui/*`, then `!/.ralph-tui/config.toml` |

The template `.ralph-tui/config.toml` makes every agent ralph-tui starts a [`the-cook worker`](ralph.md): one
`[[agents]]` entry per agent the profile declares, whose command is
`./cook worker <agent> --commit --withhold '<promise>COMPLETE</promise>'`. ralph-tui places `defaultFlags` before
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
command = "./cook"
defaultFlags = ["worker", "claude", "--commit", "--withhold", "<promise>COMPLETE</promise>"]

[agents.options]
skipPermissions = false

[[agents]]
name = "codex"
plugin = "codex"
command = "./cook"
defaultFlags = ["worker", "codex", "--commit", "--withhold", "<promise>COMPLETE</promise>"]

[agents.options]
fullAuto = false
```

The file is the same one [unattended runs](ralph.md#the-ralph-tui-recipe) explains key by key.

`--withhold` keeps ralph-tui's completion signal, `<promise>COMPLETE</promise>`, out of the output of an attempt that
failed verification, so ralph-tui sees it only from a verified one. The `worker.deny` entries leave commits, pushes and
`gh` to the worker and to the repository's `deliver` actions. The `.gitignore` block keeps ralph-tui's session,
progress and iteration logs out of the worker's way: without it the worker refuses the checkout as having uncommitted
changes, and `--commit` would commit them.

The recipe adds no lifecycle actions: nothing fires the events of a ralph-tui run by itself. A run goes like this:

```sh
./cook workspace create run-invoice
cd .work/workspaces/run-invoice
ralph-tui run --epic api-42 --headless
./cook lifecycle run completed --scope run    # or: blocked --scope run
```

ralph-tui merges a person's `~/.config/ralph-tui/config.toml` into the project's file; `the-cook` reads neither. A
ralph-tui setting that turns permission checks off reaches the worker as a bypass argument, and the worker refuses it
at the first task.

### `beads`

The beads recipe wires [Beads](https://github.com/steveyegge/beads), a task tracker used through its `bd` CLI, version
1.3.

| Part | Content |
| --- | --- |
| Entries | `start`, `completed` and `blocked` actions with scope `task`; `.beads` in `agents.codex.writable_roots`; `worker.deny`: `[bd, close]` |

The actions bind Beads to [task binding](tasks.md): every event fired in a workspace created with
`./cook workspace create <name> --task <id>` carries the bead id in `COOK_TASK` and the actor in `COOK_ACTOR`.

```yaml
lifecycle:
  start:
    - name: bead-start
      run: bd update "$COOK_TASK" --status in_progress --assignee "$COOK_ACTOR"
      auto: true
      scope: [task]
  completed:
    - name: bead-close
      run: bd close "$COOK_TASK" --reason "verified"
      auto: true
      scope: [task]
  blocked:
    - name: bead-comment
      run: bd comments add "$COOK_TASK" "$COOK_REASON"
      auto: true
      scope: [task]
```

`bd` treats a missing id as an error, so an action fired without `COOK_TASK` fails instead of touching another
bead. `[bd, close]` under `worker.deny` leaves closing to the `completed` action, which runs only after a verified
attempt. `.beads` goes into `agents.codex.writable_roots` when the profile declares Codex, so a Codex worker can write
the database. Nothing creates, configures or localises a Beads database; that is `bd`'s own job. When ralph-tui works
from Beads, it updates bead status itself, and these actions matter for interactive work in managed workspaces.

### `mise`

The mise recipe describes [mise](https://mise.jdx.dev), a tool-version manager and task runner, version 2026.6. It
holds the `:::` deny as [floor](#the-floor), a version check and a detect rule, and nothing else: a declared command,
lane or action that runs `mise run …` runs as written, and the recipe never reads or writes mise's own files. The whole
recipe is the [complete example](#a-complete-recipe-mise) of the recipe file.

### `skills`

The skills recipe describes the `skills` CLI, version 1.7, which installs shared skills into `.agents/skills/` and
records them in `skills-lock.json`. It holds a version check and the detect rule `skills-lock.json`, so `doctor`
reports the CLI missing or outside its range. It has no entries, templates, renders or floor, and `the-cook` never runs
the CLI: installing shared skills is the team's own step ([skills](skills.md)).

### Stack recipes

| Recipe | Stack | Detected from | Holds |
| --- | --- | --- | --- |
| `go` | Go | `go.mod` | lint and build commands, a `go test` lane, a format-on-edit hook for `*.go` |
| `node` | Node.js | `package.json` | lint and build commands, test lanes, a lint-on-edit hook for source files |
| `terraform` | Terraform | `*.tf` files | a format command, `validate` and `plan` lanes, a format-on-edit hook |
| `python` | Python | `pyproject.toml` or `requirements.txt` | a lint command, a test lane, a lint-on-edit hook |

Commands, lanes and [validators](profile.md#verify) (repository checks run when matching files change) go into
`.cook/config.yaml`; post-edit hooks go into `.cook/hooks.yaml`. `./cook recipe show <name>` prints every
entry a recipe holds. Policy entries, lifecycle actions and standing context are choices each repository makes in its
own files, so stack recipes hold none, and they hold no version check: `doctor` reports `go`, `npm` or `terraform`
missing when a declared command names it.

A stack the table does not list gets no stack recipe. `the-cook scaffold` then seeds the skeleton of each repository
file and the seeded delivery `ask` entry ([policy](policy.md#the-seeded-entry)), and the repository writes its commands
and lanes itself.

## Fetched recipes

A fetched recipe describes a program the release has no recipe for, or describes one differently. The repository
declares it in `recipes:` with its source, version and sha256:

```yaml
# .cook/config.yaml
recipes:
  - git
  - claude
  - node
  - name: just
    source: github:acme/cookbook
    version: v1.2.0
    sha256: d2f0d562418c7d176d0393d8694cc6f6f569f2dff3f7ede3788a2075d3ff5c3f
```

| Field | Meaning |
| --- | --- |
| `name` | the recipe's name; it must equal the `name` in its `recipe.yaml` and cannot be the name of a builtin recipe |
| `source` | an HTTPS base URL laid out like the [release source](wrapper.md#release-source), or `github:<owner>/<repo>`, shorthand for `https://github.com/<owner>/<repo>/releases` |
| `version` | the tag to download; it must equal the `version` in its `recipe.yaml` |
| `sha256` | the sha256 of the archive, as its publisher states it |

A cookbook is a published collection of recipes at one source, such as `github:acme/cookbook`, and every fetched recipe
comes from one: its entry's `source` is the cookbook's URL. Each tag of a cookbook holds one archive per recipe, so
several entries may name the same cookbook, each pinned by its own `version` and `sha256`. The recipes a release of
`the-cook` ships are its builtin recipes, and no cookbook can replace them.

The archive is `<source>/download/<version>/<name>.tar.gz`, a gzip-compressed tar of the
[recipe directory](#the-recipe-file): `recipe.yaml` at its root and `files/` beside it. Only `the-cook recipe fetch` and
`the-cook recipe apply` download it, over HTTPS. The binary stores the archive in the per-user cache as
`$COOK_HOME/recipes/<sha256>.tar.gz`, outside the repository, and checks its sha256 every time it loads it: a
mismatch, on download or on load, stops the command with exit 1, and the archive is not used.

Nothing else fetches. `render`, `doctor`, the hooks an agent fires, `the-cook worker` and `agent` actions read a fetched
recipe only from the cache. When it is not there, `render` exits 3, an agent hook or a worker's attempt fails with
exit 3 before any agent starts, and `doctor` reports a finding, each naming `./cook recipe fetch`. A CI job that
renders or runs `doctor` runs `./cook recipe fetch` first. An unattended attempt resolves the
version-control recipe and the agent's harness recipe from the release or the cache before anything else and uses that
resolution for every read, comparison, reset and launch of the attempt ([ralph](ralph.md#the-worker-contract)).

A fetched recipe cannot take the name of a builtin recipe: a declaration named `git` with a `source` is refused with
exit 1 by every command that reads the profile, so a builtin recipe is never replaced under the name every page
documents. A fetched recipe may fill either contract: `vcs:` may name it, and so may `agents.<name>.recipe`, for an
agent the release has no harness recipe for ([agents](agents.md#the-harness-contract)).

The release's tests stand behind every builtin recipe, and nothing stands behind a fetched one but its author and the
repository's review. `doctor` notes each fetched recipe as `not verified by the release`, a note that does not change
its exit code; its operations, renders and floor are what its author wrote, and a harness recipe's bypass arguments are
the floor its author marked. `./cook recipe show <name>` prints all of it.

## How apply works

`the-cook recipe apply <name>...` applies one or more recipes, in the order given, to a repository already set up with
`the-cook scaffold`:

1. **Check.** The repository needs `.cook/config.yaml`; without it, `apply` exits 1 and names `the-cook scaffold`.
   Every name must be a builtin recipe of the running release or a fetched recipe declared in `recipes:`; an unknown
   name exits 1. A fetched recipe that is not cached is downloaded and verified now; a sha256 mismatch, an unreachable
   source or a builtin name exits 1. On any failure nothing is written.
2. **Plan** the additions: the name in `recipes:`, when it is not there; the entries for `.cook/config.yaml`
   (commands, lanes, validators, agent declarations and settings), `.cook/policy.yaml` (`worker.deny` entries) and
   `.cook/hooks.yaml` (post-edit hooks, lifecycle actions); and the templates whose file is absent, rendered with
   the profile. Each recipe is planned against the files as the recipes before it leave them, following the
   [merge rules](#merge-rules).
3. **Confirm.** Show the additions and every template to write as a diff, and every item kept with its difference or
   `exists`, then ask. `--yes` skips the question. Without a terminal and without `--yes`, nothing is written and the
   command exits 3. `--dry-run` prints the diff and writes nothing.
4. **Write** the additions into the files, and the templates.
5. **Render** the repository, as [`the-cook render`](render.md) does: the renders and floor of every recipe in use, the
   new ones included, so the additions and their rendered effect are one diff and the drift check passes on it.

When there is nothing to add, `apply` says so, writes nothing and exits 0.

### Merge rules

Applying only adds. The repository's own entries and files stay authoritative.

| Element | Rule |
| --- | --- |
| A name in `recipes:` | added at the end when the list does not hold it |
| A list entry: a post-edit hook declaration, a validator, a `worker.deny` entry, a path in `agents.<name>.writable_roots` | added when the list holds no equal entry, one with the same fields and values |
| A named item: a command, a lane, a lifecycle action, an agent | added when the repository has none by that name; when it has one that differs, the repository's stays and the difference is reported: `kept lanes.unit: differs from recipe node` |
| A template | written when the file is absent; an existing file is the repository's and stays untouched, and is reported: `kept .ralph-tui/config.toml: exists` |
| Everything already in the files | nothing is removed, replaced or reordered; comments, key order and blank lines are preserved |

Additions go at the end of the list or map they belong to; a section the file lacks is added at its end. Two recipes
applied together follow the same rules: when both name the same item, such as `commands.lint`, the first one's entry
is added and the second one's is reported as kept.

### Applying again

Applying is idempotent: a second `apply` of the same recipe by the same release adds nothing, because every entry and
template is already there. Applying again is therefore the way to take a later release's improvements to a recipe's
entries and templates. After moving the pin ([wrapper](wrapper.md#upgrading)), `./cook recipe apply node --dry-run`
shows what the new release would add, and `./cook recipe apply node` adds it as a diff to review. A recipe's renders
and floor need no applying: the next render after the pin moves takes the new release's.

Applying again also adds back an entry or a template the repository deleted, because nothing records that it was
removed on purpose. Read the diff before confirming. A template the repository kept is never updated by applying again:
to take a newer release's version, delete the file and apply, then carry over your own changes.

### After applying

`recipes:` records which recipes a repository uses, which is what their renders and floor depend on. Nothing records
which entries or templates a recipe wrote: each entry's one source of truth is the file it is in, and `the-cook` holds
no record it could later use to change those entries by itself ([ADR 0013](../adrs/0013-organization-content.md)).
Change or delete an applied entry or template by editing the file, then run `./cook render`.

To stop using a recipe, delete its name from `recipes:` and run `./cook render`. The render removes the recipe's
renders, which the manifest records under its name, and its floor; its entries and templates stay as the repository's
own, to edit or delete like anything else. A recipe that `vcs:` or an agent's `recipe` names cannot leave `recipes:`
while it is named: the profile fails validation with exit 1. Both removal modes of [`the-cook remove`](remove.md) delete
the seeded `.cook/` files whatever they hold, so applied entries go with them; a template a recipe wrote, such as
`.ralph-tui/config.toml`, stays.

## Reference

```text
the-cook recipe list
the-cook recipe show <name>
the-cook recipe apply <name>... [--yes] [--dry-run]
the-cook recipe fetch
```

`recipe` follows the `the-cook <noun> <verb>` shape. `apply` has no inverse, because the entries and templates it writes
become the repository's own; a recipe leaves `recipes:` by an edit ([After applying](#after-applying)). Global flags
`-C <dir>`, `--json` and `--verbose` apply to every subcommand; see [CLI reference](cli.md).

### `the-cook recipe list`

Prints the builtin recipes of the running release and the fetched recipes the profile declares, each with its version,
its source (`release` or the declared source), the contract it fills, whether its detect rule matches in this
repository, and whether it is in `recipes:`. A fetched recipe that is not cached is listed as `not cached`. It works in
a repository that is not scaffolded yet, where nothing is in use, so it also helps choose `scaffold --recipe`.

| Exit code | Meaning |
| --- | --- |
| 0 | Listed |

Files read: the files each detect rule names, `.cook/config.yaml` when present, `$COOK_HOME/recipes/`. Files
written: nothing.

### `the-cook recipe show`

Prints the recipe's `recipe.yaml`, its entries as the YAML they add, grouped by the file they go into, every template
with its path and content, and every render with its target and template. Templates are rendered with the repository's
profile when there is one. It does not compare anything with the repository; `apply --dry-run` shows what applying
would write here, and `render --dry-run` what rendering would change.

| Exit code | Meaning |
| --- | --- |
| 0 | Printed |
| 1 | Unknown recipe name, with the valid names listed; a fetched recipe whose cached archive does not match its `sha256`; a fetched recipe declared under a builtin name |
| 3 | A fetched recipe that is not cached: run `./cook recipe fetch` |

Files read: `.cook/config.yaml`, when present; `$COOK_HOME/recipes/<sha256>.tar.gz` for a fetched recipe. Files
written: nothing.

### `the-cook recipe apply`

Applies one or more recipes, in the order given ([How apply works](#how-apply-works)).

| Flag | Default | Meaning |
| --- | --- | --- |
| `<name>...` | required | One or more recipes: a builtin recipe (`git`, `claude`, `codex`, `ralph-tui`, `beads`, `mise`, `skills`, `go`, `node`, `terraform`, `python`) or a fetched recipe declared in `recipes:` |
| `--yes` | off | Apply without asking; required without a terminal |
| `--dry-run` | off | Print the additions and the kept items; write nothing |

With `--json`, the output lists the names added to `recipes:`, the entries added, the templates written and the items
kept, each with its file and, for an entry, its key.

| Exit code | Meaning |
| --- | --- |
| 0 | Applied, or nothing to add; `--dry-run` printed |
| 1 | Error: an unknown recipe, a repository that is not scaffolded, an invalid repository file, a fetched recipe whose source cannot be reached, whose archive does not match its `sha256` or that is declared under a builtin name. Nothing is written |
| 3 | Confirmation needed and there is no terminal: rerun with `--yes`; or the repository files use a newer schema than the running release supports. Nothing is written |

Files read: `.cook/config.yaml`, `.cook/policy.yaml`, `.cook/hooks.yaml`, whether each template's file
exists, `$COOK_HOME/recipes/`, and everything [`render`](render.md#reference) reads. Files written:
`$COOK_HOME/recipes/<sha256>.tar.gz` for a fetched recipe not cached yet; `.cook/config.yaml`,
`.cook/policy.yaml` and `.cook/hooks.yaml`, additions only; each template whose file was absent; then every file
`render` writes ([what render writes](render.md#what-render-writes)).

### `the-cook recipe fetch`

Downloads every fetched recipe `recipes:` declares that is not in the cache, checks the archive's sha256 against the
declaration, checks its `recipe.yaml` (a valid schema, the declared name and version, not a builtin name) and stores it
in the cache. A recipe already cached is checked and left. It is one of the two commands that download recipes, with
`apply`; run it once per machine after cloning a repository that declares fetched recipes, and after the declaration
changes.

It takes no arguments.

| Exit code | Meaning |
| --- | --- |
| 0 | Every requested recipe is cached and verified, or there was nothing to fetch |
| 1 | No network, or a source that cannot be reached; an archive whose sha256 differs from the declaration (both hashes are printed); an invalid `recipe.yaml`, or one whose name or version differs from the declaration; a fetched recipe declared under a builtin name. A failing recipe is not stored; the others are |

Files read: `.cook/config.yaml`, `$COOK_HOME/recipes/`. Files written: `$COOK_HOME/recipes/<sha256>.tar.gz`,
on a verified download only.

### The recipe file

A recipe is a directory:

```text
<name>/
  recipe.yaml      the recipe's description
  files/           template and render sources, when the recipe has any
```

| Field of `recipe.yaml` | Type | Required | Meaning |
| --- | --- | --- | --- |
| `schema` | integer | yes | Format version of the recipe file: `1` |
| `name` | string | yes | Lowercase letters, digits and `-`: the name `recipes:`, `vcs:` and `agents.<name>.recipe` use |
| `version` | string | yes | The recipe's version; for a fetched recipe, the `version` the profile pins |
| `description` | string | no | One line, printed by `recipe list` |
| `contract` | `vcs` \| `the-cook` | no | The contract `operations` fills; absent when the recipe has no operations |
| `entries` | map of file to YAML | no | [Entries](#entries), grouped by the file they go into: `.cook/config.yaml`, `.cook/policy.yaml`, `.cook/hooks.yaml`. Each value has the shape of that file |
| `templates` | list | no | [Templates](#templates): `path`, where the file is written, relative to the repository root, and `source`, a file under `files/` |
| `renders` | list | no | [Renders](#renders): `file`, the target relative to the repository root, and exactly one of `whole`, `keys`, `entries`, `block` (below) |
| `floor.deny` | list of policy entries | no | Floor deny rules, in the [policy entry forms](policy.md#entry-forms); `{default_branch}` is replaced by what the version-control recipe reports |
| `floor.bypass` | list of arguments | no | Floor bypass arguments; only with `contract: harness` |
| `operations` | map | with `contract` | The operations of the [version-control contract](#the-version-control-contract) or of the [harness contract](#the-harness-contract) |
| `checks.version` | map | no | `run`, a command that prints the version, and `range`, the supported versions, such as `">=2.1 <2.2"`. The first dotted number in the output is the version |
| `checks.detect` | list of globs | no | Paths relative to the repository root whose match shows the program is in use; a trailing `/` matches a directory |

Unknown keys are an error. A command, in `checks` or in an operation, is a list of arguments run without a shell from
the root of the checkout the binary works in. A placeholder such as `{rev}` is replaced inside the argument it
appears in, so a value never splits into more arguments or reaches a shell. An operation that needs a shell names
`sh` and passes values as positional parameters, as the git recipe's `changed` operation does.

The forms of a render:

| Form | Fields | Owns |
| --- | --- | --- |
| `whole` | `whole: true`, `template` | the whole file |
| `keys` | a map from a dotted key path to `{ each, item }`, a list with one `item` per element of the model list `each`, or to `{ value }` | those keys of a JSON or TOML file; every other key is preserved byte for byte ([render](render.md#merge-rendering-native-files)) |
| `entries` | a dotted path to a list, and `{ each, item }` | its own entries in a list the file shares with others, such as the hook entries of `.claude/settings.json` |
| `block` | a block id, and `template` | a [managed block](render.md#managed-blocks) between the `cook:<id>` markers |

A template is logic-free: `{{name}}` inserts a value of the neutral model below, `{{#name}}…{{/name}}` repeats its text
for each element of a list, or renders it once when a value is present, and `{{^name}}…{{/name}}` renders its text when
the value is empty or absent. There are no comparisons, functions or includes. A template's text is inline in
`recipe.yaml` or in a file under `files/`, named by its path.

The neutral model, the values a template reads:

| Value | Holds |
| --- | --- |
| `agents` | the declared agents, each with `name`, `role` and `recipe` |
| `agent` | in a harness recipe's renders: the agent being rendered, with `name` and `role` |
| `allow`, `ask`, `deny` | the policy entries for `agent`'s role, alternatives expanded, each with exactly one of `prefix` (with `words`, the words joined by spaces, and `argv`, the words as a quoted list), `native` or `bare` (with `string`). `deny` holds every floor rule of every recipe in use and, for `role: worker`, `worker.deny` |
| `hooks` | the neutral hook events the declarations need, each with `event` (the native event the recipe's payload mapping names), `matcher` (its edit tools, and its read tools when `when.paths` context exists, joined with `\|`) and `command` |
| `raw` | the `raw` hook entries, each with `event`, `matcher` and `run` |
| `state_dir`, `workspaces_dir` | `state.dir` and `workspaces.dir` from the profile |
| `default_branch` | the default branch the version-control recipe reports |

A hook's `command` is the [hook command](render.md#the-hook-command), which finds the wrapper through the
version-control recipe's `root` operation: with the git recipe, `"$(git rev-parse --show-toplevel)/cook"
--no-download hook <event>`.

#### A complete recipe: `mise`

The mise recipe needs no `files/` directory:

```text
mise/
  recipe.yaml
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

In a repository whose `recipes:` holds `mise`, `render` adds `Bash(mise * ::: *)` to `permissions.deny` in
`.claude/settings.json` and lists the rule in the `policy` block of `AGENTS.md`, marked as a floor rule of the mise
recipe and as Claude Code only; `doctor` reports a `mise` outside 2026.6. `the-cook scaffold` applies the recipe when it
finds `mise.toml` or `.mise.toml`.

An excerpt of the ralph-tui recipe shows a template, an entry and a block render:

```yaml
schema: 1
name: ralph-tui
version: 1.0.0
description: ralph-tui, a loop runner that starts an agent per task
templates:
  - { path: .ralph-tui/config.toml, source: files/config.toml }
entries:
  .cook/policy.yaml:
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

### Add a recipe to an adopted repository

The repository was scaffolded with `--no-detect --recipe claude`, and its people already wrote their own `build`
command:

```text
$ ./cook recipe apply node
.cook/config.yaml
+  - node
+  lint: npm run lint -- {file}
+  unit: { select: npm test -- {pattern}, full: npm test }
.cook/hooks.yaml
+  - { paths: ["src/**/*.{ts,js}"], run: npx eslint --fix {file} }
kept commands.build: differs from recipe node
apply these additions? [y/N] y
applied node: 3 entries added, 1 kept; rendered .claude/settings.json, AGENTS.md
$ git add -A && git commit -m "chore: apply the node recipe"
```

### Read a recipe

```text
$ ./cook recipe show go
# recipe.yaml
schema: 1
name: go
version: 1.0.0
description: Go
checks:
  detect: [go.mod]

# .cook/config.yaml
commands:
  lint: gofmt -l {file} | (! grep .)
  build: go build ./...
verify:
  lanes:
    test: { select: go test {pattern}, full: go test ./... }

# .cook/hooks.yaml
post_edit:
  - { paths: ["**/*.go"], run: gofmt -w {file} }
```

### Wire ralph-tui and Beads

The repository already keeps a hand-written `.ralph-tui/config.toml`:

```text
$ ./cook recipe apply beads ralph-tui
.cook/config.yaml
+  - beads
+  - ralph-tui
+      writable_roots: [.beads]
.cook/policy.yaml
+    - [bd, close]
+    - [git, commit]
+    - [git, push]
+    - [gh]
.cook/hooks.yaml
+  start:
+    - { name: bead-start, run: bd update "$COOK_TASK" --status in_progress --assignee "$COOK_ACTOR", auto: true, scope: [task] }
+  completed:
+    - { name: bead-close, run: bd close "$COOK_TASK" --reason "verified", auto: true, scope: [task] }
+  blocked:
+    - { name: bead-comment, run: bd comments add "$COOK_TASK" "$COOK_REASON", auto: true, scope: [task] }
kept .ralph-tui/config.toml: exists
apply these additions? [y/N] y
applied beads, ralph-tui: 8 entries added, 1 kept; rendered .codex/rules/the-cook.rules, .gitignore, AGENTS.md
```

The existing `.ralph-tui/config.toml` stays as it is. To take the recipe's, move it aside, apply `ralph-tui` again, and
carry over what the old file needs; `./cook recipe show ralph-tui` prints the template first.

### Choose recipes before scaffolding

```text
$ cd ~/src/acme-web
$ the-cook recipe list
recipe     version  source   contract  detected  in use
git        1.0.0    release  vcs       yes       -
claude     1.0.0    release  the-cook   yes       -
codex      1.0.0    release  the-cook   no        -
ralph-tui  1.0.0    release  -         -         -
beads      1.0.0    release  -         -         -
mise       1.0.0    release  -         no        -
skills     1.0.0    release  -         no        -
go         1.0.0    release  -         no        -
node       1.0.0    release  -         yes       -
terraform  1.0.0    release  -         no        -
python     1.0.0    release  -         yes       -
$ the-cook scaffold --no-detect --recipe claude --recipe node --dry-run
```

`requirements.txt` here only pins a documentation tool, so the scaffold takes `node` and leaves `python` out. With
`--no-detect`, `git` is still applied, because no `--vcs` names another version-control recipe.

### A fetched recipe

acme publishes a recipe for [just](https://github.com/casey/just), a command runner its repositories use, with the
sha256 of each release's archive. In `acme/api`, a person adds the declaration from the
[fetched recipes](#fetched-recipes) example to `recipes:`, reads the recipe, and applies it:

```text
$ ./cook recipe fetch
fetched just v1.2.0 from github:acme/cookbook: sha256 verified
$ ./cook recipe show just
$ ./cook recipe apply just --yes
applied just: 2 entries added; rendered AGENTS.md
$ ./cook doctor
note: recipe just v1.2.0 (github:acme/cookbook): not verified by the release
```

Each teammate runs `./cook recipe fetch` once on their machine. Until then their `render` and agent hooks exit 3
naming it, and nothing is fetched behind their back.

### Stop using a program

```sh
# delete "- ralph-tui" from recipes: in .cook/config.yaml
./cook render        # removes the ralph-tui block from .gitignore
git diff                 # .ralph-tui/config.toml and the worker.deny entries stay, as the repository's own
```

### After an upgrade

```sh
./cook wrapper --version v0.10.0
./cook recipe apply go --dry-run      # the entries of v0.10.0's go recipe that the files lack
./cook recipe apply go --yes
git diff
```

The renders and the floor of every builtin recipe in use already follow v0.10.0: `the-cook wrapper` renders the
repository with the new release in the same change.

### Many repositories

```sh
the-cook fleet recipe apply node --yes --deliver
```

Each repository that lacks some of the recipe's entries gets a branch with the additions and their rendered effect,
delivered through its own `deliver` actions. A repository that already holds every entry is reported as unchanged
([fleet](fleet.md)). The command runs without a terminal in each repository, so it needs `--yes`.

## Guidelines

- Do read the diff `apply` shows before confirming: once written, the entries and templates are the repository's own
  and nothing marks where they came from.
- Do edit applied entries to fit the repository: a recipe guesses from the stack or the program, the repository knows
  its real commands, and applying again never overwrites an item you changed.
- Do run `./cook recipe apply <name> --dry-run` for the recipes in use after moving the pin to a newer release:
  new entries reach the repository only when someone applies the recipe again.
- Do commit the additions together with the files `apply` rendered, so the diff explains itself.
- Do keep every program the repository uses in `recipes:`, `mise` above all: a recipe's floor holds only while the
  recipe is in use, and `doctor` names a detected program whose recipe is missing.
- Do read a fetched recipe with `./cook recipe show <name>` before pinning it, and take its `sha256` from its
  publisher: its operations, renders and floor run in every session and every unattended attempt.
- Do write an organization's choices into each repository through a reviewed change, such as an agent following the
  organization's skill: they differ between repositories, and a recipe holds only what every repository using its
  program shares ([Organization choices](#organization-choices)).
- Don't copy a floor rule into `.cook/policy.yaml` to keep it after removing its recipe: an entry there is editable,
  which is what the floor exists to avoid. Keep the recipe in use.
- Don't apply a recipe again to restore an item you changed: `apply` keeps every item the repository names. Delete
  yours first if you want the recipe's.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| `kept lanes.unit: differs from recipe node` | The repository already has a `unit` lane, and `apply` never replaces an item the repository names | Nothing, if yours is right. To take the recipe's, delete yours and apply again |
| `kept .ralph-tui/config.toml: exists` | The file is there, and `apply` writes a template only when its file is absent | Nothing, if yours is right. To take the recipe's, move yours aside, apply again, and carry over what you need |
| ralph-tui's first task fails with the worker refusing a permission-bypass argument | A ralph-tui configuration, the project's or `~/.config/ralph-tui/config.toml`, turns permission checks off or passes a bypass argument in `defaultFlags` | Keep `skipPermissions = false` under each `[agents.options]` in `.ralph-tui/config.toml` and remove the argument; the worker refuses it whoever passes it ([ralph](ralph.md)) |
| A Beads action fails with a missing id | The event fired in a workspace created without `--task`, so `COOK_TASK` is empty | Create the workspace with `./cook workspace create <name> --task <id>` ([tasks](tasks.md)) |
| `recipe apply` exits 1 naming `the-cook scaffold` | The repository has no `.cook/config.yaml` | Run `the-cook scaffold`, which applies the detected recipes |
| `recipe apply` or `recipe show` exits 1: unknown recipe | The name is neither a builtin recipe of the running release nor a fetched recipe in `recipes:` | `./cook recipe list` prints the names |
| `recipe apply` exits 3 | No terminal to confirm, and no `--yes` | Review with `--dry-run`, then rerun with `--yes`; under `fleet`, always pass `--yes` |
| `render`, a hook or `the-cook worker` exits 3 naming `./cook recipe fetch`, or `doctor` reports a fetched recipe not cached | A fetched recipe in `recipes:` is not in this machine's cache, and only `recipe fetch` and `recipe apply` download | Run `./cook recipe fetch` |
| Exit 1: a fetched recipe's sha256 differs | The archive at the source is not the one the profile pins: a new upload under the same version, a different `source`, or tampering | Do not change the `sha256` to match. Ask the publisher; pin a version whose hash it states |
| Exit 1: a fetched recipe uses a builtin name | A `recipes:` entry with a `source` is named like a builtin recipe, such as `git` | Rename the fetched recipe; a builtin recipe cannot be replaced |
| Exit 1: `vcs` or an agent's `recipe` names a recipe not in `recipes:` | The recipe was deleted from `recipes:`, or never added | Add it back, or point `vcs:` or the agent at a recipe in use |
| Exit 3 naming `git` | The version-control recipe's program is not installed on this machine | Install git, or the program the `vcs:` recipe names |
| `doctor`: `mise detected; its recipe and its floor are not in use` | `mise.toml` is present and `mise` is not in `recipes:`, so the `:::` deny is not rendered | `./cook recipe apply mise` |
| `doctor` notes `not verified by the release` | The recipe is fetched: the release's tests do not cover it | Expected. Review the recipe with `./cook recipe show <name>` |
| An entry or a template you deleted is back after `recipe apply` | `apply` adds every entry the files lack and writes every absent template, and nothing records that you removed them | Delete it again, and read the diff before confirming the next apply |
| `recipe apply` after an upgrade adds nothing | The files already hold every entry of the recipe, or equal ones | Expected |
| Only one stack's lint command is in the profile | `commands.lint` is one named item: the first recipe applied wrote it, and the next one's was reported as kept | Write a lint command that covers both stacks, such as a script or a [mise](compatibility.md#mise) task that picks the linter by the file's extension |
| Entries of the wrong stack after `scaffold` | Marker files of another stack are present, such as a `package.json` for tooling in a Go repository | Delete the entries that do not apply from `.cook/config.yaml` and `.cook/hooks.yaml`, delete the recipe from `recipes:`, then `./cook render`; on the next repository, `the-cook scaffold --no-detect --recipe <name>` |
