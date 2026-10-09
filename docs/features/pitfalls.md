# Pitfalls, gaps and traps

The places where agents, permission systems, worktrees and trackers behave differently from what a reasonable person
expects, and what `harness` does about each. Every entry has four parts: the **symptom** you see, **why** it happens,
what **`harness` does** about it, and what **you do**.

For the principles behind these entries, read [safety](safety.md). For the terms, see the [glossary](glossary.md).

Commands are named `harness <command>` on this page. Run them through the repository's wrapper,
`./harnessw <command>`, from the root of a checkout; with a global install, `harness <command>` is equivalent
([wrapper](wrapper.md)).

- [Permissions](#permissions)
- [Agents and trust](#agents-and-trust)
- [Worktrees and environment](#worktrees-and-environment)
- [Ralph, trackers and verification](#ralph-trackers-and-verification)
- [Delivery](#delivery)
- [Notifications and agent actions](#notifications-and-agent-actions)
- [Standing context](#standing-context)
- [Configuration and versions](#configuration-and-versions)
- [The wrapper](#the-wrapper)
- [Removal](#removal)
- [Scope limits](#scope-limits)

## Permissions

### A prefix matches the leading words only

- **Symptom.** `[kubectl, patch]` is denied, yet an agent runs `kubectl -n prod patch deployment api …` without a
  prompt, or the classifier decides it.
- **Why.** A prefix entry matches the first words of the command. A flag before the subcommand changes the first
  words, so the command matches no entry and falls to the agent's default.
- **What `harness` does.** Renders prefixes exactly; it does not parse each tool's flags.
- **What you do.** Leave production and cloud writes unlisted for people, and deny the whole tool to workers
  (`worker.deny: [[kubectl]]`). Where Claude Code must also stop the flag-first spelling, add a raw string such as
  `"kubectl * patch *"`, knowing it is Claude-only.

### `git -C` and `git -c` slip past git prefixes

- **Symptom.** `[git, push]` is denied to workers, yet `git -C ../other push` or `git -c core.hooksPath=/dev/null
  commit` runs.
- **Why.** `-C <dir>` and `-c <key=value>` come before the subcommand, so the leading words are `git -C`, not
  `git push`. `-C` also reaches other checkouts of the clone.
- **What `harness` does.** Nothing beyond rendering; the policy decides.
- **What you do.** Put `[git, ["-C", "-c"]]` in `worker.deny`, next to the subcommands that leave the worktree
  ([policy](policy.md)).

### A task runner runs whatever its tasks say

- **Symptom.** `[npm, run, test]` is allowed, and the agent runs a test script it just edited to do something else;
  or `mise run deliver` runs `harness lifecycle run deliver` although that command is under `ask`.
- **Why.** A rule matches the command the agent starts, not what that command runs. A task runner, a script, a
  `Makefile` target or a wrapper runs code the agent can read and often edit.
- **What `harness` does.** Denies mise `:::` chaining in the built-in base, so one allowed task cannot carry an
  unlisted one ([policy](policy.md#the-built-in-base)). Replays a worker's checks from the base commit, so a worker
  cannot change the checks it is measured by.
- **What you do.** Allow tasks whose effects stay in the clone. Give a wrapper of a guarded command the same decision
  as the command it wraps. Treat "edit files" plus "run the test runner" as "run any code", and keep production
  credentials out of the agent's environment.

### The wrapper is a second spelling of every `harness` command

- **Symptom.** `[harness, lifecycle, run, deliver]` is under `ask`, yet an agent runs
  `/home/dev/acme-api/harnessw lifecycle run deliver` or `sh harnessw lifecycle run deliver` without a prompt.
- **Why.** A prefix matches leading words. `harness …`, `./harnessw …`, `harnessw …`, a task shim, an absolute path to
  the wrapper and `sh harnessw …` all start with different words.
- **What `harness` does.** Renders every entry that names `harness` for each spelling it can know: `harness …`,
  `./harnessw …`, `harnessw …`, and the task shim where one exists ([policy](policy.md#entries-for-harness-commands)).
  It cannot enumerate absolute paths or interpreters, so the guards that matter live in the binary: `harness
  lifecycle run` refuses inside a Ralph worker whatever the spelling, because the worker launcher sets
  `HARNESS_WORKER=1`, and the `deliver` guards hold whoever runs the command ([safety](safety.md#guards-live-in-the-binary)).
- **What you do.** Write each entry for a `harness` command once, as `[harness, …]`. Treat the policy entries as
  defence in depth and the binary's refusals as the guarantee; deny in `worker.deny` what workers must never run.

### Codex cannot match mid-command patterns

- **Symptom.** A deny works in Claude Code and not in Codex; the `policy` block lists it as Claude-only.
- **Why.** Codex rules match command prefixes. Raw strings, native rule strings other than a pure `Bash(<prefix> *)`,
  and flag-last spellings such as `git push origin main --force` have no Codex form.
- **What `harness` does.** Renders every prefix list for both agents, renders the other forms for Claude Code only,
  and lists them in the `policy` block. The built-in `:::` deny and the flag-last force-push denies are
  Claude-only for this reason.
- **What you do.** Express every deny that both agents must enforce as a prefix list. Where only a mid-command form
  exists, deny the whole tool to Codex workers.

### An `ask` entry stalls unattended runs

- **Symptom.** An unattended coordinator or a Claude Code session in auto mode stops and waits.
- **Why.** A matching `ask` prompts even in auto mode, and no `allow` from any settings scope overrides it.
- **What `harness` does.** Renders `ask` as written. The built-in base contains no `ask`; `scaffold` seeds one, on
  `harness lifecycle run deliver`.
- **What you do.** Keep `ask` for what must have a person every time: a merge past branch protection, delivery when
  you want to see it. Before an unattended run, list the `ask` entries it reaches and accept each stop or move the
  entry.

### An `allow` entry skips the auto-mode classifier

- **Symptom.** A Claude Code session in auto mode runs a command you expected it to judge, such as a push to the
  default branch under `allow: [[git]]`.
- **Why.** An `allow` decides before the classifier is consulted. The classifier judges only commands no entry
  matches.
- **What `harness` does.** Denies force push to the default branch in the built-in base; nothing else.
- **What you do.** Write each `allow` as narrow as its reason. Rely on branch protection, not on the agent, for what
  reaches the default branch.

### Settings outside the policy widen or narrow permissions

- **Symptom.** An agent runs a command the policy does not allow, or prompts for one it allows, on one person's
  machine only.
- **Why.** Claude Code also reads `.claude/settings.local.json`, the user's `~/.claude/settings.json` and managed
  settings; Codex reads user rules and its own approval and sandbox settings. They combine with the project's rules.
- **What `harness` does.** Never writes local, user or managed settings and never reads managed settings. Local and
  user settings are read only by `harness scaffold --import local,user`, which copies them into the policy for review
  ([safety](safety.md#importing-personal-scopes)); after that, `harness` cannot see changes to them.
  Adoption reports `defaultMode`, `approval_policy` and `sandbox_mode` as notes.
- **What you do.** When behaviour differs between machines, compare those files first. Before an unattended run, check
  them on the machine that runs it.

### MCP server definitions are not managed

- **Symptom.** Two clones of the same repository offer different MCP tools; an MCP rule in the policy matches nothing.
- **Why.** Which MCP servers exist, and their credentials, live in each person's settings. The policy only decides
  how their tools are approved.
- **What `harness` does.** Renders MCP tool rules (`mcp__server__tool` with globs) for Claude Code; adoption reports MCP
  server definitions as notes and leaves them in place.
- **What you do.** Document the servers the repository expects in `AGENTS.md`. Allow read-only tools by name pattern;
  leave write tools unlisted.

### A local command follows an environment file to a remote system

- **Symptom.** An allowed `migration:run` or a seed task changes a shared database.
- **Why.** The command is local; its target comes from an environment file (`.env`, `.env.local`) that can point
  anywhere.
- **What `harness` does.** Nothing: it does not read environment files.
- **What you do.** Allow such tasks only when every environment file the clone uses points at local services. Deny
  them to workers when that cannot be guaranteed. Keep remote credentials out of the files a worktree inherits.

### The agents' permission systems are not the same boundary

- **Symptom.** The same policy behaves differently in Claude Code and Codex: Codex runs an unlisted command without a
  prompt; Claude Code prompts for one Codex runs.
- **Why.** Claude Code permissions approve tool calls. Codex rules govern escalation out of its sandbox, and inside the
  sandbox Codex runs commands on its own.
- **What `harness` does.** Renders the same intent for both and pins each agent's worker mode
  ([agents](agents.md#worker-launch)).
- **What you do.** Judge a policy per agent. Rely on the sandbox for Codex and on `deny` and the classifier for Claude
  Code.

## Agents and trust

### Claude Code ignores project permissions in an untrusted folder

- **Symptom.** Every command prompts in a worktree, or project hooks do not run.
- **Why.** Claude Code applies project settings only in folders the person trusted. Worktrees nested in the primary
  checkout inherit its trust; a worktree created as a sibling directory does not.
- **What `harness` does.** Creates its worktrees in the configured worktrees directory, inside the primary checkout
  (`.work/worktrees/` by default). Never changes trust ([agents](agents.md#trust)).
- **What you do.** Trust the primary checkout once. Create worktrees with `harness worktree create`, not as siblings.

### Permitting `harness lifecycle run` permits its actions

- **Symptom.** An agent allowed to run `harness lifecycle run deliver` pushes, opens a pull request or runs a command
  the policy denies to it.
- **Why.** Actions run as child processes of `harness`, outside the agent's per-command checks. The permission to
  start the event is the permission for every action declared on it.
- **What `harness` does.** Seeds `[harness, lifecycle, run, deliver]` under `ask` in a scaffolded policy, in
  every rendered spelling; denies `[harness, lifecycle]` to workers in the built-in base and refuses `lifecycle run` inside a
  worker in the binary; refuses unsafe delivery with its guards ([lifecycle](lifecycle.md#built-in-guards)).
- **What you do.** Read `harness lifecycle list` before allowing the command, and review every change to the actions
  like code ([safety](safety.md#lifecycle-actions-are-code)).

### Permitting a command with a trigger point permits its automatic actions

- **Symptom.** An agent allowed to run `harness task complete` also pushes a branch and opens a pull request.
- **Why.** `harness task complete` fires `completed`, then `deliver`; every action with `auto: true` runs. The same
  holds for every command with a trigger point.
- **What `harness` does.** Runs only actions with `auto: true` at a trigger point; `auto` defaults to `false`.
- **What you do.** Set `auto: true` only on actions you accept running every time an allowed command fires them.

### Task text addresses the agent that reads it

- **Symptom.** A worker does something no one asked for, following text in a task description or a pasted issue.
- **Why.** Agents act on what they read. Anyone who can write a task, an issue or a comment can address the agent.
- **What `harness` does.** Bounds what a worker can do: pinned mode, `worker.deny`, tamper check, replay, no lifecycle
  actions inside workers.
- **What you do.** Run workers on tasks a person wrote or read. Scope runs with `--epic`.

### Hooks skip until the pinned binary is cached

- **Symptom.** A teammate's agent edits files without lint-on-edit, standing context or notifications; the session
  starts with `harness v0.9.0 is not cached on this machine; run ./harnessw version once to fetch it.` and later hooks
  print `harness: v0.9.0 not downloaded, hook skipped; run: ./harnessw version`. It happens after cloning, on a new
  machine, and after the pin moves to a new release.
- **Why.** Rendered hooks are implicit entry points and never download: they run `harnessw --no-download`, which exits
  0 when the pinned version is not in the cache ([hooks](hooks.md#the-missing-binary-guard)).
- **What `harness` does.** Prints one stderr line on every harness hook and, at session start, also adds the one
  session-context line; then continues. Raw hooks and static context entries work regardless. Explicit entry points,
  `./harnessw <command>` and the task shims, download and verify the pinned version, and exit non-zero when they
  cannot, so a skipped verification never reads as a pass.
- **What you do.** Run `./harnessw version` once on each machine after cloning and after each upgrade. Treat hooks as
  feedback; `harness verify` and CI are the proof. Declare directives every session must have as
  [static context](context.md#static-entries).

## Worktrees and environment

### The repository's own globs pick up nested worktrees

- **Symptom.** Lint, tests or the type checker in the primary checkout report duplicate modules, run twice or fail on
  files under the worktrees directory (`.work/worktrees/` by default).
- **Why.** Managed worktrees are nested in the primary checkout. A glob such as `**/*.ts`, a test root or a
  `tsconfig.json` `include` walks into them; `.gitignore` does not stop tools that do not read it.
- **What `harness` does.** Writes the state and worktree directories into the `state` and `worktrees` blocks of
  `.gitignore`. It never edits tool configuration.
- **What you do.** Exclude the state and worktree directories (`.work/**` with the defaults) in the linter, the test
  runner, the type checker and the editor's file watcher.

### Worktrees inherit the parent's tool configuration and dependencies

- **Symptom.** A build in a worktree uses the primary checkout's dependencies, a tool version from its configuration,
  or fails on a relative tool path.
- **Why.** Tools resolve configuration and dependencies by walking up directories. A nested worktree without its own
  finds the primary checkout's: mise configuration, `node_modules`, a virtual environment.
- **What `harness` does.** Runs the profile's `bootstrap` in every new worktree and in every Ralph task checkout
  ([worktrees](worktrees.md#nesting)).
- **What you do.** Make `bootstrap` install everything a build needs in the worktree itself. Do not skip it with
  `--no-bootstrap` for a worktree that builds.

### Shared local services collide across worktrees

- **Symptom.** End-to-end tests fail at random while two worktrees or two parallel workers run them; a database reset
  in one worktree breaks another.
- **Why.** Worktrees share the machine: fixed ports, database names, container names and volumes are the same in
  every checkout of the clone.
- **What `harness` does.** Isolates files, not services. It does not serialise or namespace them.
- **What you do.** Give each checkout its own names and ports (derived from the worktree path), or serialise the
  commands with a lock in the repository's own task. Deny service resets to workers, and size `--workers` to what the
  services allow.

## Ralph, trackers and verification

### Ralph starts from committed code only

- **Symptom.** A run ignores a change you just made, including a change to `.harness/`; workers check against the old
  rules.
- **Why.** The coordinator and every task checkout are created from a commit, and the worker reads the profile, the
  policy and the hooks file from the session's base commit. A new worktree also contains only committed code.
- **What `harness` does.** Refuses to launch with uncommitted changes.
- **What you do.** Commit before `harness ralph run` and before `harness worktree create`.

### ralph-tui behaviours the launcher absorbs

- **Symptom.** Without the launcher, direct ralph-tui runs show the behaviours in the table below.
- **Why.** These are properties of ralph-tui and `bd` that conflict with safe unattended runs.
- **What `harness` does.** Absorbs each one, as listed.
- **What you do.** Run through `harness ralph`, and shape tasks as the last column says.

| ralph-tui or `bd` behaviour | What the launcher does | What you do |
| --- | --- | --- |
| Claude's `--model` accepts only the aliases `sonnet`, `opus`, `haiku` | the worker substitutes the full model id into the agent's invocation | give each agent a `model` in the profile, or in the inherited configuration with `ralph.config: inherit` |
| No effort is passed to agents | the worker substitutes the effort (Claude `--effort`, Codex `model_reasoning_effort`) | set `effort` per agent in the profile |
| Acceptance criteria are read from a `## Acceptance Criteria` section of the description, not from `bd`'s acceptance field | nothing to absorb: the embedded prompt and the task shape follow it | end each task description with that section, as a `- [ ]` checklist |
| `bd create --parent` copies the parent's labels | nothing to absorb | pass `--no-inherit-labels` |
| The parallel scheduler ignores dependencies on tasks outside its plan | the `bd` shim lists only tasks of the run that can run: ready, in progress, or waiting only on other tasks of the run | finish or unblock outside dependencies, then resume |
| Worker paths are force-deleted before reuse | resumed parallel workers get a new directory; the previous run's checkouts stay for repair | repair failed tasks before cleaning |

### A run without `--epic` takes every ready task in the clone

- **Symptom.** A Beads run picks up tasks from other work, including tasks imported from issues.
- **Why.** Without `--epic`, ralph-tui takes every ready, unowned task in the database.
- **What `harness` does.** Passes `--epic` through; it does not guess one.
- **What you do.** Always pass `--epic` with Beads.

### A dependency on an epic blocks every child

- **Symptom.** No task of an epic is ready, although none of them depends on anything.
- **Why.** `bd ready` treats every descendant of a blocked task as blocked. A dependency put on the epic holds all its
  children.
- **What `harness` does.** Nothing; it reads readiness from `bd`.
- **What you do.** Put each dependency on the story that needs it, not on the epic.

### A synchronised Beads database exposes claims to other machines

- **Symptom.** Tasks show as claimed on a teammate's machine by an actor such as `ralph:3f9c1a7b2e44`.
- **Why.** An existing `.beads/` synchronised across machines carries claims and comments with it.
- **What `harness` does.** Uses an existing database exactly as found; a database it creates is local, without
  remotes ([tasks](tasks.md#trackers)).
- **What you do.** Run agents against a local database, or accept that claims are shared. Deny the tracker's remote
  sync to agents in the policy when it must stay manual.

### `bd init` installs git hooks and `AGENTS.md` sections

- **Symptom.** After a hand-run `bd init`, commits run Beads hooks and `AGENTS.md` gains a Beads section.
- **Why.** `bd init` installs git hooks and agent instructions unless told not to.
- **What `harness` does.** Initialises Beads without git hooks and without agent files when it creates the database;
  never changes `core.hooksPath`.
- **What you do.** Let `harness` create the database, or run `bd init --skip-hooks --skip-agents`.

### The receipt lives in the worker's writable tree

- **Symptom.** A worker's receipt lists lanes that passed, and the task still fails.
- **Why.** The worker can write its receipt (`.work/verified.json` by default), so the receipt cannot be proof.
- **What `harness` does.** Uses the receipt only to select lanes and skip reasons, then replays them from the base
  commit's rules. The replay is the proof ([verify](verify.md#who-relies-on-it)).
- **What you do.** Read `last-worker-output` in the task checkout's state directory, and run the lanes there
  yourself.

### A lane passes with zero tests, or with unrelated tests

- **Symptom.** `harness verify` passes, and the change is untested.
- **Why.** Many test runners exit 0 when a pattern matches nothing. Tests that pass without exercising the change prove
  nothing about it.
- **What `harness` does.** Enforces that an application change selects at least one lane and that `required_when`
  lanes run ([verify](verify.md#lane-rules)). It cannot judge what a test exercises.
- **What you do.** Configure the runner to fail when nothing matches. Select the tests that exercise what changed.

### A skip reason is free text

- **Symptom.** A lane is skipped with `"n/a"`.
- **Why.** No check can judge a reason; it is recorded, not evaluated.
- **What `harness` does.** Records the reason in the receipt and in the task's commit body. Never allows every lane to
  be skipped, and never a `required_when` lane.
- **What you do.** Review skip reasons in the pull request like code.

### Worker output appears only at the end

- **Symptom.** A worker looks hung: nothing streams for many minutes.
- **Why.** The worker wrapper buffers the agent's output so it can withhold completion until the replay passes.
- **What `harness` does.** Prints the output after a passing replay; keeps it in `last-worker-output`, in the task
  checkout's state directory, after a failure.
- **What you do.** Watch ralph-tui's progress, not the agent's tokens. Stopping Ralph stops the agent and its
  processes.

## Delivery

### Delivery after a Ralph run needs judgement

- **Symptom.** The run's branch holds one commit per task, with the messages the tasks produced, on top of the commit
  the run was launched from.
- **Why.** Which tasks form one pull request, and how their messages read, depends on the repository and the
  reviewers.
- **What `harness` does.** Keeps the result on a local branch and fires nothing that leaves the clone unless an action
  says so.
- **What you do.** Have a coordinating agent cherry-pick the task commits onto a branch from the default branch,
  reword them, verify, then run `harness lifecycle run deliver` ([lifecycle](lifecycle.md#ralph-delivery-per-run-or-delivery-after-judgement)).

### An automatic run-scope deliver opens one pull request per run

- **Symptom.** A finished run becomes a single pull request with every task in it.
- **Why.** A `deliver` action with `auto: true` and scope `run` delivers the run's branch as one unit.
- **What `harness` does.** Exactly that, when declared.
- **What you do.** Use run-scope automatic delivery only when one run is one reviewable unit. Otherwise deliver after
  judgement, or deliver per task.

### The automatic deliver skips uncommitted work

- **Symptom.** `harness task complete` succeeds, and nothing is pushed.
- **Why.** The automatic `deliver` fires only for concluded work: committed, clean, off the default branch and, when
  the verify module is enabled, with a valid receipt.
- **What `harness` does.** Skips the event, names the unmet condition, and records it in the
  [lifecycle log](lifecycle.md#the-lifecycle-log).
- **What you do.** Commit before `harness task complete`, or run `harness lifecycle run deliver` afterwards.

### A rebase invalidates the receipt

- **Symptom.** `deliver` exits 3 after rebasing on the default branch.
- **Why.** The receipt is bound to the content against the base. A rebase changes that content.
- **What `harness` does.** Refuses delivery without a valid receipt when the verify module is enabled.
- **What you do.** Run `harness verify` again after a rebase or a merge, then deliver.

### A squash merge hides landed work

- **Symptom.** A merged branch is never reported as landed; `harness ralph clean` keeps it.
- **Why.** Landed work is detected by patch (`git cherry`). A squash of several commits produces one patch that
  matches none of them.
- **What `harness` does.** Treats only matching patches as landed, so it never deletes unmerged work.
- **What you do.** Delete such branches and their worktrees by hand once you have checked the merge.

### Actions that are not idempotent fail on retry

- **Symptom.** Running `deliver` a second time fails at `gh pr create`.
- **Why.** The first run created the pull request; a failed event is retried by running all its actions again.
- **What `harness` does.** Runs the declared actions as written.
- **What you do.** Make each action safe to repeat: `gh pr view "$HARNESS_BRANCH" || gh pr create …`.

## Notifications and agent actions

### Notifications are silent on a machine without the pinned binary

- **Symptom.** A developer gets chat messages when their agent waits for them on one machine and not on another, or
  an `attention` action never fires after a fresh clone.
- **Why.** `attention` and `turn_end` are agent hooks, and agent hooks are implicit entry points: they never download,
  so they do nothing until the pinned release is in the machine's cache. The variable an `http` action reads must also
  be set in that machine's environment.
- **What `harness` does.** Prints one line on stderr on every harness hook and, at session start, adds one line to the
  session's context: `harness v0.9.0 is not cached on this machine; run ./harnessw version once to fetch it.` Every
  hook exits 0.
- **What you do.** Run `./harnessw version` once on every machine, and set the notification's variables in each
  developer's own shell profile. Do not rely on notifications for unattended runs; declare `blocked` and `completed`
  lifecycle actions, which the launcher fires.

### Codex sessions never send `attention`

- **Symptom.** `attention` notifications arrive from Claude Code sessions and never from Codex.
- **Why.** Codex has no hook event for an agent waiting for a person. It reports a finished turn through its `notify`
  setting only.
- **What `harness` does.** Renders `turn_end` for Codex through `notify`, renders nothing for `attention`, and reports
  the gap as a `doctor` note ([hooks](hooks.md#agent-notifications)).
- **What you do.** Use `turn_end` when Codex sessions must notify.

### Every action sees every secret in the environment

- **Symptom.** A webhook token exported for one notification appears in the environment of a `run` action from a
  preset, or of an `agent` action.
- **Why.** Actions inherit the environment of the process that fires them. `url_env` and `headers_env` keep secrets
  out of the committed file, not out of the other actions.
- **What `harness` does.** Never writes those values to output, `--dry-run` or the lifecycle log.
- **What you do.** Export only the variables the declared actions need, in the shell or CI job that fires them. Review
  every action, preset actions included, as code that can read them ([safety](safety.md#secrets)).

### A landed `agent` action leaves a worktree with uncommitted edits

- **Symptom.** After `landed`, a worktree `.work/worktrees/landed-<slug>-<n>` holds changes nobody committed, and more
  of them appear with every landed branch.
- **Why.** An `agent` action cannot commit. On `landed`, the work's own worktree is about to be removed and the primary
  checkout holds whatever you are doing, so `harness` gives the agent a fresh worktree of the default branch, on branch
  `work/landed-<slug>-<n>`, and leaves the agent's edits there ([lifecycle](lifecycle.md)).
- **What `harness` does.** Records the worktree like any managed worktree, never touches the primary checkout, and
  lists the worktree and its changed paths in the action's output and in the lifecycle log.
- **What you do.** Review each such worktree: commit its edits and deliver them as a change, or remove it with
  `./harnessw worktree remove landed-<slug>-<n>`. Keep such actions manual if nobody reviews them promptly.

### Values in an `agent` prompt address the agent

- **Symptom.** An `agent` action does something its prompt never asked for, following text in a task title or a
  refusal reason.
- **Why.** `$NAME` in `agent.prompt` is replaced by the variable's value as plain text, and the agent reads that text
  as part of its instructions.
- **What `harness` does.** Runs the agent under the worker contract, which bounds what it can do
  ([safety](safety.md#agent-actions-and-prompt-injection)).
- **What you do.** Write prompts that point the agent at files and branches instead of pasting values written by
  people and agents.

## Standing context

### Always-on context costs every session

- **Symptom.** Sessions start slower and cost more, and the agent follows a long injected directive while missing a
  detail of the task.
- **Why.** Every line of `always` content is read at the start of every session, and again after each compaction in
  Claude Code. It competes with the task for the agent's attention.
- **What `harness` does.** Reports a `doctor` note when a role's always-on content exceeds `context.budget`, listing
  each entry's line count ([context](context.md#budget)).
- **What you do.** Keep `always` entries short, switch long ones to `inject: pointer`, and move guidance that applies
  to some files only behind a `paths` trigger.

### Prompt words match unrelated messages

- **Symptom.** A skill is injected when the person writes "update the date on the release notes".
- **Why.** A `prompt` trigger matches whole words, case-insensitively, wherever they appear. It cannot tell what the
  person means.
- **What `harness` does.** Injects each `when` entry at most once per session.
- **What you do.** Choose specific words and phrases ("DST", "time zone"), and prefer `paths` triggers.

### A skill is loaded twice

- **Symptom.** The transcript shows the injected pointer and then the agent loading the same skill on its own.
- **Why.** The agent also matches installed skills by their descriptions and loads one it judges relevant, whether or
  not `harness` injected it.
- **What `harness` does.** Injects pointers, not content, for `when` entries by default, so the duplicate is one line.
- **What you do.** Keep injected skills as pointers, or narrow the skill's description so the agent matches it less
  often.

### Context is missing on a machine without the pinned binary

- **Symptom.** The same repository's sessions follow the coding guidelines on one machine and not on another.
- **Why.** Hook-delivered context comes from `harness hook`, which does not run until the pinned release is cached.
- **What `harness` does.** Adds the not-cached line to the session's context, so the agent can tell the person.
- **What you do.** Run `./harnessw version` once per machine. Declare what every session must have as
  [static context](context.md#static-entries), which is part of `AGENTS.md`.

### Codex reads files without firing `paths` entries

- **Symptom.** A `paths` entry fires when a Codex session patches a migration, and not when it only reads one.
- **Why.** Codex reads files through shell commands, which name no file `harness` can match.
- **What `harness` does.** Matches the files Codex's patches touch.
- **What you do.** Accept edit-only triggering for Codex, or make the entry `always` for a role that needs it.

## Configuration and versions

### Hand-edited rendered files are drift

- **Symptom.** `harness render --check` exits 2; an edit to `.claude/settings.json` permissions or inside a managed
  block disappears after `harness render`.
- **Why.** Rendered files and blocks are outputs of the three repository files and presets.
- **What `harness` does.** Rewrites what it owns, preserves everything else byte for byte, and reports differences
  ([render](render.md#drift-check)).
- **What you do.** Edit the source (`policy.yaml`, `hooks.yaml`, `config.yaml`) and render.

### A preset change does not reach the repository by itself

- **Symptom.** A preset was changed upstream, and `harness render` changes nothing.
- **Why.** The manifest records the resolved commit of every preset, and `render` and `render --check` reuse it, so
  an upstream change never fails a check by itself.
- **What `harness` does.** Reports a newer upstream commit as a note in `harness doctor`; `harness render --refresh`
  re-resolves presets that are not pinned with `@<ref>`.
- **What you do.** Run `harness render --refresh` (or change the `@<ref>`), review the diff, commit. Across many
  repositories, use [fleet](fleet.md).

### A mise task with the same name keeps its name

- **Symptom.** `mise run verify` runs the repository's old task, not `harness verify`.
- **Why.** `harness` never overwrites an existing task; the task shim is written as `harness:verify`.
- **What `harness` does.** Writes the namespaced task shim and lists the collision as a to-do until it is resolved
  ([render](render.md#task-shims)).
- **What you do.** Remove or rename the old task, or keep both and say which one agents use.

### Another tool's `.harness/` directory blocks scaffolding

- **Symptom.** `harness scaffold` exits 3 naming a foreign `.harness/` directory.
- **Why.** Another product, a CI/CD platform, keeps its pipelines in a `.harness/` directory and ships a CLI also named
  `harness`. A `.harness/` without this tool's `config.yaml` or `manifest.json` belongs to someone else.
- **What `harness` does.** Refuses to write into it, and never changes it ([compatibility](compatibility.md)).
- **What you do.** Decide whether the repository uses the other product. Repositories that do are outside this tool's
  audience.

## The wrapper

### The first run needs the network

- **Symptom.** On a fresh clone, a new machine, or after the pin moves to a new release, `./harnessw <command>` or a
  task shim fails to download, and agent sessions show a skipped hook.
- **Why.** The wrapper runs only the pinned version. The first time a machine needs it, the wrapper downloads the
  release asset with `curl` or `wget` and checks it with `sha256sum` or `shasum`. Without network access, or without
  those tools, there is nothing to run. Afterwards the wrapper runs offline, from the cache.
- **What `harness` does.** Explicit entry points exit non-zero and say why; hooks skip with one line and exit 0.
  Nothing partly downloaded is cached.
- **What you do.** Run `./harnessw version` once while online, after cloning and after each upgrade. On an air-gapped
  machine, bring the pinned release in, check it against the checksum in `.harness/wrapper.yaml`, and pre-fill the
  cache with it or point `HARNESS_BINARY` at it ([air-gapped machines](wrapper.md#air-gapped-machines)).

### A checksum mismatch refuses the download

- **Symptom.** `./harnessw` exits 1 naming a checksum mismatch. Nothing is cached, so the next run downloads again.
- **Why.** The sha256 of the downloaded asset differs from the one `.harness/wrapper.yaml` pins for this platform: a
  corrupted download, a proxy that rewrites responses, a pin edited by hand, or a release asset replaced after the pin
  was written.
- **What `harness` does.** Refuses to cache or run the asset. It never falls back to another version and never skips
  the check.
- **What you do.** Retry once. If the mismatch persists, read `git log -p .harness/wrapper.yaml` and compare the pinned
  checksum with the release's published checksum file. When the pin matches the release and the download does not,
  investigate the network path or the release; do not work around it by editing the checksum or setting
  `HARNESS_BINARY` ([wrapper](wrapper.md#troubleshooting)).

### Bypassing the pin runs another version

- **Symptom.** A command exits 3 naming a schema newer than the binary supports, or one person's renders differ from
  everyone else's.
- **Why.** The pin holds only while the pinned binary runs. `HARNESS_BINARY` makes the wrapper run whatever binary it
  names, and a global `harness` from a release without wrapper deferral runs itself instead of the pinned version.
- **What `harness` does.** A global binary with wrapper deferral execs the pinned version inside any repository that
  has `.harness/wrapper.yaml`. A binary older than the repository's schema refuses rather than render a format it does
  not know, naming the pinned release to run. `harness doctor` notes when it runs as another release than the pin.
- **What you do.** Run `./harnessw`, or update the global binary. Set `HARNESS_BINARY` only on an air-gapped machine
  or while developing `harness`, and unset it afterwards.

### Editing `wrapper.yaml` by hand breaks the checksums

- **Symptom.** After `version` was changed by hand in `.harness/wrapper.yaml`, every download fails with a checksum
  mismatch, or fails on one platform.
- **Why.** Checksums belong to one version. A hand-edited `version` keeps the previous version's checksums, and a
  platform without a checksum has nothing to verify against.
- **What `harness` does.** Refuses every download that does not match
  ([A checksum mismatch refuses the download](#a-checksum-mismatch-refuses-the-download)).
- **What you do.** Upgrade with `./harnessw wrapper --version <tag>` (or `--latest`), which verifies the release's
  signed checksum file and writes the version and the checksum of every platform from it, and review the diff. Across
  repositories, run `harness fleet wrapper --version <tag> --deliver` ([fleet](fleet.md)).

### A second pin in the task runner drifts from the wrapper

- **Symptom.** `harness doctor` reports a finding for `mise.toml` (or `.mise.toml`, `.tool-versions`), or `mise exec`
  and `./harnessw` run different releases.
- **Why.** The task runner's tool list also pins `harness`, so two files answer the same question, and an upgrade
  moves only one of them.
- **What `harness` does.** Treats `.harness/wrapper.yaml` as the only pin, reports the second one, and never edits the
  task runner's file. Adoption copies such a pin into `wrapper.yaml` and lists its removal in the to-do
  ([wrapper](wrapper.md#one-pin)).
- **What you do.** Remove `harness` from the task runner's tool list.

### Another program named `harness` comes first on `PATH`

- **Symptom.** `harness doctor` or `harness version` prints something unrelated, or `doctor` reports that the
  `harness` on `PATH` is not this tool.
- **Why.** Another product ships a CLI named `harness`. The global command resolves through `PATH`, so whichever comes
  first runs.
- **What `harness` does.** Nothing it can do changes `PATH`. `./harnessw` always runs the pinned binary from its own
  cache, so wrapper invocations, task shims and hooks are never affected.
- **What you do.** Use `./harnessw` inside repositories, and put this tool's global binary earlier on `PATH` only if
  you want the global command.

## Removal

### After eject, nothing enforces the conventions

- **Symptom.** After `harness remove --eject`, `AGENTS.md` still describes where worktrees go, how tasks are tracked,
  how Ralph runs and what agents may run, yet a worker commits its own work, delivery has no guard, and lint-on-edit,
  notifications and hook-delivered standing context have stopped.
- **Why.** Eject stops managing and keeps the result. Convention blocks (`worktrees`, `tasks`, `ralph`, `policy`,
  `memory`, `context`) keep their text without markers, and the `.gitignore` entries, the rendered permissions and the
  raw hooks stay as the repository's own. Tool blocks (`commands`, `layout`, `verify`, `lifecycle`) are removed, because
  they describe `harness` commands that do not exist after the eject, and so are the hooks that called `harness`. Receipts, the
  worker contract, the tamper check, the lifecycle guards and the drift check need the binary, so they stop.
- **What `harness` does.** Lists every block, hook and task shim it removed ([remove](remove.md#eject)), and nothing
  after that.
- **What you do.** Decide which guarantees the repository still needs and move each one where something enforces it:
  CI for the checks, branch protection for what lands, the agents' own permissions for what they may run. Edit the
  convention text by hand when a convention changes, since nothing re-renders it. Run `harness remove` without
  `--eject` when the conventions should go too.

## Scope limits

### Sessions started in a monorepo subdirectory are out of scope

- **Symptom.** An agent started in `services/billing/` does not apply the repository's rules, or applies a nested
  `.claude/` directory's instead.
- **Why.** `harness` operates at the git root: one profile, one policy, one hooks file per repository. Whether an
  agent started in a subdirectory reads the root's settings is the agent's behaviour.
- **What `harness` does.** Renders at the root and leaves nested `.claude/` directories alone
  ([compatibility](compatibility.md#monorepos)).
- **What you do.** Start agents at the repository root.

### `harness` never writes the team's record

- **Symptom.** A task completes and its issue is unchanged; a delivered branch has no comment on its pull request.
- **Why.** Issues, comments, reviews, labels and boards are the team's record. The binary writes none of them, and
  lifecycle actions deliver work, they do not report on it.
- **What `harness` does.** Keeps task state in the clone and leaves the record to people, agents and CI.
- **What you do.** Have the coordinating agent update the issue or the pull request as its skill says, or let CI
  report.

### Claude Code's own worktrees are not managed

- **Symptom.** Worktrees under `.claude/worktrees/` are not listed, cleaned or bootstrapped.
- **Why.** They belong to Claude Code.
- **What `harness` does.** Never scans or touches `.claude/worktrees/`, and never cleans branches it did not record.
- **What you do.** Use `harness worktree create` for work that needs a session, a receipt base or a tracker.
