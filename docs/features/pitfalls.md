# Pitfalls, gaps and traps

## Objective

**Problem.** Workers such as coding agents, their permission systems, version-control workspaces and task trackers
each have behaviours that surprise a careful developer at some stage of the lifecycle: a rule that reads as forbidding a
command lets a variant of it through, an unattended run stalls on a question nobody answers, a check passes without
testing the change. A developer or reviewer usually meets
each of these alone and after something has gone wrong, with the explanation spread across several vendors'
documentation, or written nowhere.

**Goal.** A reader who meets a surprising behaviour finds it in one place, with why it happens and what to do about it,
and can check a setup against the known traps before relying on it.

## Overview

Each entry below is a place where workers, permission systems, workspaces or trackers behave differently from what a
reasonable person expects, with what `the-cook` does about it. Every entry has four parts: the **symptom** you see,
**why** it happens, what **`the-cook` does** about it, and what **you do**.

For the principles behind these entries, read [safety](safety.md). For the terms, see the [glossary](glossary.md). The
entries below are about the core: the lifecycle, its guards and proof, and the two contracts. A trap of one particular
program, a coding agent, a loop runner, a task tracker or a task runner, is documented with the
[ingredient](ingredients.md) that describes it, in the cookbook that publishes that ingredient.

Commands are named `the-cook <command>` on this page. Run them through the repository's wrapper,
`./cook <command>`, from the root of a checkout; with a global install, `the-cook <command>` is equivalent
([wrapper](wrapper.md)).

- [Permissions](#permissions)
- [Workers and trust](#workers-and-trust)
- [Workspaces and environment](#workspaces-and-environment)
- [Attempts, trackers and verification](#attempts-trackers-and-verification)
- [Delivery](#delivery)
- [Notifications and attempt actions](#notifications-and-attempt-actions)
- [Standing context](#standing-context)
- [Configuration and versions](#configuration-and-versions)
- [The wrapper](#the-wrapper)
- [Removal](#removal)
- [Scope limits](#scope-limits)

## Permissions

### A prefix matches the leading words only

- **Symptom.** The policy denies `[kubectl, patch]`, a [prefix list](policy.md#entry-forms) that matches commands whose
  leading words are `kubectl patch`, yet a coding agent runs `kubectl -n prod patch deployment api …` without a
  prompt, or the worker's own judgement decides it.
- **Why.** A prefix entry matches the first words of the command. A flag before the subcommand changes the first
  words, so the command matches no entry and falls to the worker's default. A global option that changes where a
  command acts, such as one naming another directory, slips past the same way and can reach other checkouts.
- **What `the-cook` does.** Renders prefixes exactly; it does not parse each tool's flags.
- **What you do.** Leave production and cloud writes unlisted for people, and deny the whole tool to workers
  (`unattended.deny: [[kubectl]]`), or deny the global options that come before the subcommand. Where a worker must
  also stop the flag-first spelling, add a raw string such as `"kubectl * patch *"`; only workers whose ingredient
  renders mid-command patterns enforce it.

### A task runner runs whatever its tasks say

- **Symptom.** `[npm, run, test]` is allowed, and a worker runs a test script it just edited to do something else; or
  a `deliver` task the repository defines in its task runner runs `the-cook lifecycle run deliver` although that
  command is under `ask`.
- **Why.** A rule matches the command the worker starts, not what that command runs. A task runner, a script, a
  `Makefile` target or a wrapper runs code the worker can read and often edit.
- **What `the-cook` does.** Renders the floor rules of the ingredients in use, such as a task-runner ingredient's deny
  of a form that chains one task onto another, so one allowed task cannot carry an unlisted one
  ([policy](policy.md#the-floor)). Such a rule holds only where its ingredient is pinned. Replays an attempt's checks
  from the base commit, so a worker cannot change the checks it is measured by.
- **What you do.** Allow tasks whose effects stay in the clone. Give a wrapper of a guarded command the same decision
  as the command it wraps. Treat "edit files" plus "run the test runner" as "run any code", and keep production
  credentials out of the worker's environment.

### The wrapper is a second spelling of every `the-cook` command

- **Symptom.** `[the-cook, lifecycle, run, deliver]` is under `ask`, yet a worker runs
  `/home/dev/acme-api/cook lifecycle run deliver` or `sh cook lifecycle run deliver` without a prompt.
- **Why.** A prefix matches leading words. `the-cook …`, `./cook …`, `cook …`, an absolute path to the wrapper
  and `sh cook …` all start with different words.
- **What `the-cook` does.** Renders every entry that names `the-cook` for each spelling it can know: `the-cook …`,
  `./cook …` and `cook …` ([policy](policy.md#entries-for-the-cook-commands)). It cannot enumerate absolute
  paths or interpreters, so the guards that matter live in the binary: `the-cook lifecycle run` refuses inside an
  attempt whatever the spelling, because `the-cook attempt` sets `COOK_ATTEMPT=1`, and the `deliver` guards hold
  whoever runs the command ([safety](safety.md#guards-live-in-the-binary)).
- **What you do.** Write each entry for a `the-cook` command once, as `[the-cook, …]`. Treat the policy entries as
  defence in depth and the binary's refusals as the guarantee; deny in `unattended.deny` what attempts must never run.

### A worker cannot enforce every rule form

- **Symptom.** A deny works in one worker and not in another; the `policy` block in `AGENTS.md` lists it as limited to
  some workers.
- **Why.** Workers' rule languages differ. Some match only command prefixes, so a raw string, a mid-command pattern or
  a flag-last spelling has no form there.
- **What `the-cook` does.** Renders every prefix list for every worker, renders the other forms only for workers whose
  ingredient can express them, and lists the rest in the `policy` block. An ingredient's floor rule in such a form holds
  only in those workers.
- **What you do.** Express every deny that all workers must enforce as a prefix list. Where only a mid-command form
  exists, deny the whole tool in `unattended.deny` for the workers that cannot express it.

### An `ask` entry stalls unattended runs

- **Symptom.** An unattended coordinating worker, or a session in a worker's autonomous mode, stops and waits.
- **Why.** A matching `ask` prompts for a person. Whether a worker's autonomous mode or another settings scope can
  answer it instead is the worker's behaviour, which its ingredient documents.
- **What `the-cook` does.** Renders `ask` as written. No ingredient's floor rule is an `ask`; `scaffold` seeds one, on
  `the-cook lifecycle run deliver`.
- **What you do.** Keep `ask` for what must have a person every time: a merge past branch protection, delivery when
  you want to see it. Before an unattended run, list the `ask` entries it reaches and accept each stop or move the
  entry.

### Settings outside the policy widen or narrow permissions

- **Symptom.** A worker runs a command the policy does not allow, or prompts for one it allows, on one person's
  machine only.
- **Why.** A worker also reads local, user and managed settings, and its own mode, approval and sandbox settings.
  They combine with the project's rules.
- **What `the-cook` does.** Never writes local, user or managed settings and never reads managed settings. Local and
  user settings are read only by `the-cook scaffold --import local,user`, which copies them into the policy for review
  ([safety](safety.md#importing-personal-scopes)); after that, `the-cook` cannot see changes to them.
  Adoption reports the mode, approval and sandbox settings it finds as notes.
- **What you do.** When behaviour differs between machines, compare those files first. Before an unattended run, check
  them on the machine that runs it.

### MCP server definitions are not managed

- **Symptom.** Two clones of the same repository offer different MCP tools; an MCP rule in the policy matches nothing.
- **Why.** Which MCP servers exist, and their credentials, live in each person's settings. The policy only decides
  how their tools are approved.
- **What `the-cook` does.** Renders MCP tool rules for workers whose ingredient can express them; adoption reports MCP
  server definitions as notes and leaves them in place.
- **What you do.** Document the servers the repository expects in `AGENTS.md`. Allow read-only tools by name pattern;
  leave write tools unlisted.

### A local command follows an environment file to a remote system

- **Symptom.** An allowed `migration:run` or a seed task changes a shared database.
- **Why.** The command is local; its target comes from an environment file (`.env`, `.env.local`) that can point
  anywhere.
- **What `the-cook` does.** Nothing: it does not read environment files.
- **What you do.** Allow such tasks only when every environment file the clone uses points at local services. Deny
  them to attempts when that cannot be guaranteed. Keep remote credentials out of the files a workspace inherits.

### Workers' permission systems are not the same boundary

- **Symptom.** The same policy behaves differently in two workers: one runs an unlisted command without a prompt, the
  other prompts for a command the first runs.
- **Why.** In one worker permissions approve each tool call; in another, rules govern only escalation out of a sandbox,
  inside which the worker runs commands on its own.
- **What `the-cook` does.** Renders the same intent for every worker and pins each worker's unattended launch
  ([workers](workers.md)).
- **What you do.** Judge a policy per worker, with what its ingredient says about its boundary.

## Workers and trust

### A worker ignores project settings in an untrusted folder

- **Symptom.** Every command prompts in a workspace, or project hooks do not run.
- **Why.** A worker may apply project settings only in folders the person trusted. Workspaces nested in the primary
  checkout can inherit its trust; a workspace created as a sibling directory may not.
- **What `the-cook` does.** Creates its workspaces in the configured workspaces directory, inside the primary checkout
  (`.work/workspaces/` by default). Never changes trust ([workers](workers.md)).
- **What you do.** Trust the primary checkout once. Create workspaces with `the-cook workspace create`, not as siblings.

### Permitting `the-cook lifecycle run` permits its actions

- **Symptom.** A worker allowed to run `the-cook lifecycle run deliver` pushes, opens a pull request or runs a command
  the policy denies to it.
- **Why.** Actions run as child processes of `the-cook`, outside the worker's per-command checks. The permission to
  start the event is the permission for every action declared on it.
- **What `the-cook` does.** Seeds `[the-cook, lifecycle, run, deliver]` under `ask` in a scaffolded policy, in every
  rendered spelling; renders `[the-cook, lifecycle]` into every worker's unattended denies itself and refuses
  `lifecycle run` inside an attempt in the binary; refuses unsafe delivery with its guards
  ([lifecycle](lifecycle.md#built-in-guards)).
- **What you do.** Read `the-cook lifecycle list` before allowing the command, and review every change to the actions
  like code ([safety](safety.md#lifecycle-actions-are-code)).

### Permitting a command with a trigger point permits its automatic actions

- **Symptom.** A worker allowed to run `the-cook attempt` in a workspace bound to a task also pushes a branch and opens
  a pull request.
- **Why.** After a verified attempt, `the-cook attempt` fires `completed`, then `deliver`; every action with
  `auto: true` runs. The same holds for every command with a [trigger point](lifecycle.md#trigger-points).
- **What `the-cook` does.** Runs only actions with `auto: true` at a trigger point; `auto` defaults to `false`.
- **What you do.** Set `auto: true` only on actions you accept running every time an allowed command fires them.

### Task text addresses the worker that reads it

- **Symptom.** An attempt does something no one asked for, following text in a task description or a pasted issue.
- **Why.** Coding agents act on what they read. Anyone who can write a task, an issue or a comment can address the
  worker.
- **What `the-cook` does.** Bounds what an attempt can do with the [attempt contract](attempts.md#the-attempt-contract):
  pinned launch, `unattended.deny`, tamper check, replay, no lifecycle actions inside attempts.
- **What you do.** Run attempts on tasks a person wrote or read. Limit a loop runner's run to the tasks you mean it to
  take.

### Hooks skip until the pinned binary is cached

- **Symptom.** A teammate's worker edits files without lint-on-edit, standing context or notifications; the session
  starts with `the-cook v0.9.0 is not cached on this machine; run ./cook version once to fetch it.` and later hooks
  print `the-cook: v0.9.0 not downloaded, hook skipped; run: ./cook version`. It happens after cloning, on a new
  machine, and after the pin moves to a new release.
- **Why.** Rendered hooks are implicit entry points and never download: they run `cook --no-download`, which exits
  0 when the pinned version is not in the cache ([hooks](hooks.md#the-missing-binary-guard)).
- **What `the-cook` does.** Prints one stderr line on every `the-cook hook` entry and, at session start, also adds the
  one session-context line; then continues. Raw hooks and static context entries work regardless. Explicit entry points,
  such as `./cook <command>`, download and verify the pinned version, and exit non-zero when they cannot, so a skipped
  verification never reads as a pass.
- **What you do.** Run `./cook version` once on each machine after cloning and after each upgrade. Treat hooks as
  feedback; `the-cook verify` and CI are the proof. Declare directives every session must have as
  [static context](context.md#static-entries).

## Workspaces and environment

### The repository's own globs pick up nested workspaces

- **Symptom.** Lint, tests or the type checker in the primary checkout report duplicate modules, run twice or fail on
  files under the workspaces directory (`.work/workspaces/` by default).
- **Why.** Managed workspaces are nested in the primary checkout. A glob such as `**/*.ts`, a test root or a type
  checker's include list walks into them; `.gitignore` does not stop tools that do not read it.
- **What `the-cook` does.** Writes the state and workspace directories into the `state` and `workspaces` blocks of
  `.gitignore`. It never edits tool configuration.
- **What you do.** Exclude the state and workspace directories (`.work/**` with the defaults) in the linter, the test
  runner, the type checker and the editor's file watcher.

### Workspaces inherit the parent's tool configuration and dependencies

- **Symptom.** A build in a workspace uses the primary checkout's dependencies, a tool version from its configuration,
  or fails on a relative tool path.
- **Why.** Tools resolve configuration and dependencies by walking up directories. A nested workspace without its own
  finds the primary checkout's: a tool-version file, installed dependencies, a virtual environment.
- **What `the-cook` does.** Runs the profile's `bootstrap` in every new workspace, and once in every checkout an
  attempt runs in ([workspaces](workspaces.md#nesting)).
- **What you do.** Make `bootstrap` install everything a build needs in the workspace itself. Do not skip it with
  `--no-bootstrap` for a workspace that builds.

### Shared local services collide across workspaces

- **Symptom.** End-to-end tests fail at random while two workspaces or two parallel attempts run them; a database reset
  in one workspace breaks another.
- **Why.** Workspaces share the machine: fixed ports, database names, container names and volumes are the same in
  every checkout of the clone.
- **What `the-cook` does.** Isolates files, not services. It does not serialise or namespace them.
- **What you do.** Give each checkout its own names and ports (derived from the workspace path, such as a database
  `api_invoice_rounding` for `.work/workspaces/invoice-rounding`), or serialise the commands with a lock in the
  repository's own task. Deny service resets in `unattended.deny`, and size the loop runner's parallelism to what the
  services allow.

## Attempts, trackers and verification

### An attempt starts from committed code only

- **Symptom.** A run ignores a change you just made, including a change to `.cook/`, and its attempts check against
  the old rules; or `the-cook attempt` refuses to start, naming uncommitted changes.
- **Why.** `the-cook attempt` reads the profile, the policy and the hooks file from its base commit, the `HEAD` of the
  checkout it runs in. A new workspace also contains only committed code.
- **What `the-cook` does.** `the-cook attempt` refuses to start with uncommitted changes, with exit 1.
- **What you do.** Commit before `the-cook workspace create` and before starting the loop runner, and keep the loop
  runner's own state files ignored, so they never count as uncommitted changes.

### A loop runner's own behaviour shapes a run

- **Symptom.** A run takes tasks you did not mean it to take, passes the worker a model or effort other than the one
  you expected, or starts a task before work it depends on.
- **Why.** Which tasks a run takes, in which order, and what the runner passes to the command it runs for each task are
  the loop runner's own behaviour. `the-cook` has no code for any loop runner.
- **What `the-cook` does.** Nothing beyond the attempt contract: `the-cook attempt` passes the worker the arguments the
  runner gives it, after refusing every floor bypass argument of the worker's ingredient among them.
- **What you do.** Read the traps the cookbook documents for the loop runner and the tracker the station uses, and
  configure the runner and shape the tasks accordingly.

### A synchronised tracker exposes claims to other machines

- **Symptom.** Tasks show as assigned on a teammate's machine to an actor such as `ana:invoice-rounding:5c1e`.
- **Why.** Tracker data synchronised across machines carries assignments and comments with it, including those the
  repository's tracker actions write.
- **What `the-cook` does.** Nothing: it neither creates nor configures a tracker's data, and the actions use whatever
  data the tracker's command finds ([tasks](tasks.md#trackers)).
- **What you do.** Run workers against local tracker data, or accept that claims are shared. Deny the tracker's remote
  sync to workers in the policy when it must stay manual.

### The receipt lives in the worker's writable tree

- **Symptom.** An attempt's receipt lists lanes that passed, and the task still fails.
- **Why.** The worker can write its receipt, the record of a passing `the-cook verify` (`.work/verified.json` by
  default), so the receipt cannot be proof.
- **What `the-cook` does.** Uses the receipt only to select lanes and skip reasons, then replays them from the base
  commit's rules. The replay is the proof ([verify](verify.md#who-relies-on-it)).
- **What you do.** Read `last-attempt-output` in the state directory of the checkout the attempt ran in, apply
  `last-attempt.patch` from the same directory to see the attempt's changes, since the failed attempt returned the
  checkout to its base, and run the lanes there yourself.

### A lane passes with zero tests, or with unrelated tests

- **Symptom.** `the-cook verify` passes, and the change is untested.
- **Why.** Many test runners exit 0 when a pattern matches nothing. Tests that pass without exercising the change prove
  nothing about it.
- **What `the-cook` does.** Enforces that an application change selects at least one lane and that `required_when`
  lanes run ([verify](verify.md#lane-rules)). It cannot judge what a test exercises.
- **What you do.** Configure the runner to fail when nothing matches. Select the tests that exercise what changed.

### A skip reason is free text

- **Symptom.** A lane is skipped with `"n/a"`.
- **Why.** No check can judge a reason; it is recorded, not evaluated.
- **What `the-cook` does.** Records the reason in the receipt and in the task's commit body. Never allows every lane to
  be skipped, and never a `required_when` lane.
- **What you do.** Review skip reasons in the pull request like code.

### Attempt output appears only at the end

- **Symptom.** An attempt looks hung: nothing streams for many minutes.
- **Why.** `the-cook attempt`, which a loop runner runs for each task, buffers the worker's output so it can
  withhold completion until the replay passes.
- **What `the-cook` does.** Prints the output after a passing replay; keeps it in `last-attempt-output`, in the state
  directory of the checkout it ran in, after a failure.
- **What you do.** Watch the loop runner's progress, not the worker's output.

## Delivery

### Delivery after a loop runner's run needs judgement

- **Symptom.** The run's branch holds one commit per task, with the messages the tasks produced, on top of the commit
  the run started from.
- **Why.** Which tasks form one pull request, and how their messages read, depends on the repository and the
  reviewers.
- **What `the-cook` does.** Keeps the result on a local branch and fires nothing that leaves the clone unless an action
  says so.
- **What you do.** Have a coordinating worker cherry-pick the task commits onto a branch from the default branch, reword
  them, verify, then run `the-cook lifecycle run deliver`
  ([lifecycle](lifecycle.md#ralph-delivery-per-run-or-delivery-after-judgement)).

### A run-scope deliver opens one pull request per run

- **Symptom.** A finished run becomes a single pull request with every task in it.
- **Why.** `deliver` actions for scope `run` deliver the run's branch as one unit when the caller fires
  `the-cook lifecycle run deliver --scope run`.
- **What `the-cook` does.** Exactly that, when declared and fired.
- **What you do.** Fire run-scope delivery only when one run is one reviewable unit. Otherwise deliver after judgement,
  or deliver per task.

### The automatic deliver skips uncommitted work

- **Symptom.** `the-cook attempt` reports a verified attempt in a workspace bound to a task, and nothing is pushed.
- **Why.** The automatic `deliver` fires only for concluded work: committed, clean, off the default branch and, when
  the verify module is enabled, with a valid receipt.
- **What `the-cook` does.** Skips the event, names the unmet condition, and records it in the
  [lifecycle log](lifecycle.md#the-lifecycle-log).
- **What you do.** Pass `--commit` to `the-cook attempt`, or commit and run `the-cook lifecycle run deliver` afterwards.

### A rebase invalidates the receipt

- **Symptom.** `deliver` exits 3 after rebasing on the default branch.
- **Why.** The receipt is bound to the content against the base. A rebase changes that content.
- **What `the-cook` does.** Refuses delivery without a valid receipt when the verify module is enabled.
- **What you do.** Run `the-cook verify` again after a rebase or a merge, then deliver.

### A squash merge hides landed work

- **Symptom.** A merged branch is never reported as landed; `the-cook workspace remove` fires no `landed` for it.
- **Why.** Landed work is detected by patch equivalence, through the version-control ingredient's landed operation. A
  squash of several commits produces one patch that matches none of them.
- **What `the-cook` does.** Treats only matching patches as landed, so it never deletes unmerged work.
- **What you do.** Delete such branches and their workspaces by hand once you have checked the merge.

### Actions that are not idempotent fail on retry

- **Symptom.** Running `deliver` a second time fails at the action that opens the pull request.
- **Why.** The first run created the pull request; a failed event is retried by running all its actions again.
- **What `the-cook` does.** Runs the declared actions as written.
- **What you do.** Make each action safe to repeat: look the pull request up first and create it only when it is
  absent ([lifecycle](lifecycle.md#delivery-push-and-a-draft-pull-request)).

## Notifications and attempt actions

### Notifications are silent on a machine without the pinned binary

- **Symptom.** A developer gets chat messages when their worker waits for them on one machine and not on another, or
  an `attention` action never fires after a fresh clone.
- **Why.** `attention` and `turn_end` are worker hooks, and worker hooks are implicit entry points: they never download,
  so they do nothing until the pinned release is in the machine's cache. The variable an `http` action reads must also
  be set in that machine's environment.
- **What `the-cook` does.** Prints one line on stderr on every `the-cook hook` entry and, at session start, adds one
  line to the session's context: `the-cook v0.9.0 is not cached on this machine; run ./cook version once to fetch it.`
  Every hook exits 0.
- **What you do.** Run `./cook version` once on every machine, and set the notification's variables in each
  developer's own shell profile. Do not rely on notifications for unattended runs; declare `completed` and `blocked`
  lifecycle actions, and have whatever drives the run fire `blocked` and the events of scope `run`.

### A worker without a waiting event never sends `attention`

- **Symptom.** `attention` notifications arrive from sessions of one worker and never from another.
- **Why.** Not every worker has a hook event for waiting for a person; some report only a finished turn.
- **What `the-cook` does.** Renders `attention` and `turn_end` only on the native events the worker's ingredient maps
  them to, and reports an unmapped one as a `doctor` note ([hooks](hooks.md#worker-notifications)).
- **What you do.** Use `turn_end` for sessions of a worker without a waiting event.

### Every action sees every secret in the environment

- **Symptom.** A webhook token exported for one notification appears in the environment of every other `run` action,
  and of an `attempt` action.
- **Why.** Actions inherit the environment of the process that fires them. `url_env` and `headers_env` keep secrets
  out of the committed file, not out of the other actions.
- **What `the-cook` does.** Never writes those values to output, `--dry-run` or the lifecycle log.
- **What you do.** Export only the variables the declared actions need, in the shell or CI job that fires them. Review
  every action as code that can read them ([safety](safety.md#secrets)).

### A landed `attempt` action leaves a workspace with uncommitted edits

- **Symptom.** After `landed`, a workspace `.work/workspaces/landed-<name>-<n>`, named after the landed work's
  workspace, holds changes nobody committed, and more of them appear with every landed branch.
- **Why.** An `attempt` action cannot commit. On `landed`, the work's own workspace is about to be removed and the
  primary checkout holds whatever you are doing, so `the-cook` gives the worker a fresh workspace of the default branch,
  on branch `work/landed-<name>-<n>`, and leaves the worker's edits there ([lifecycle](lifecycle.md#attempt)).
- **What `the-cook` does.** Records the workspace like any managed workspace, never touches the primary checkout, and
  lists the workspace and its changed paths in the action's output and in the lifecycle log.
- **What you do.** Review each such workspace: commit its edits and deliver them as a change, or remove it with
  `./cook workspace remove landed-<name>-<n>`. Keep such actions manual if nobody reviews them promptly.

### Values in an `attempt` prompt address the worker

- **Symptom.** An `attempt` action does something its prompt never asked for, following text in a task title or a
  refusal reason.
- **Why.** `$NAME` in `attempt.prompt` is replaced by the variable's value as plain text, and the worker reads that
  text as part of its instructions.
- **What `the-cook` does.** Runs the worker under the attempt contract, which bounds what it can do
  ([safety](safety.md#attempt-actions-and-prompt-injection)).
- **What you do.** Write prompts that point the worker at files and branches instead of pasting values written by
  people and workers.

## Standing context

### Always-on context costs every session

- **Symptom.** Sessions start slower and cost more, and the coding agent follows a long injected directive while
  missing a detail of the task.
- **Why.** Every line of `always` content is read at the start of every session, and again whenever the worker fires
  its session-start event, which some workers do after a compaction. It competes with the task for the worker's
  attention.
- **What `the-cook` does.** Reports a `doctor` note when a role's always-on content exceeds `context.budget`, listing
  each entry's line count ([context](context.md#budget)).
- **What you do.** Keep `always` entries short, switch long ones to `inject: pointer`, and move guidance that applies
  to some files only behind a `paths` trigger.

### Prompt words match unrelated messages

- **Symptom.** A skill whose `prompt` trigger lists the word "date" is injected when the person writes "update the date
  on the release notes".
- **Why.** A `prompt` trigger matches whole words, case-insensitively, wherever they appear. It cannot tell what the
  person means.
- **What `the-cook` does.** Injects each `when` entry at most once per session.
- **What you do.** Choose specific words and phrases ("DST", "time zone"), and prefer `paths` triggers.

### A skill is loaded twice

- **Symptom.** The transcript shows the injected pointer and then the coding agent loading the same skill on its own.
- **Why.** A coding agent also matches installed skills by their descriptions and loads one it judges relevant, whether
  or not `the-cook` injected it.
- **What `the-cook` does.** Injects pointers, not content, for `when` entries by default, so the duplicate is one line.
- **What you do.** Keep injected skills as pointers, or narrow the skill's description so the worker matches it less
  often.

### Context is missing on a machine without the pinned binary

- **Symptom.** The same repository's sessions follow the coding guidelines on one machine and not on another.
- **Why.** Hook-delivered context comes from `the-cook hook`, which does not run until the pinned release is cached.
- **What `the-cook` does.** Adds the not-cached line to the session's context, so the worker can tell the person.
- **What you do.** Run `./cook version` once per machine. Declare what every session must have as
  [static context](context.md#static-entries), which is part of `AGENTS.md`. A static entry reaches every role, so a
  directive only unattended or only interactive sessions must receive cannot be static: it reaches its role through the
  hook, and only where the pinned release is cached.

### A worker that reads through shell commands fires `paths` entries only on edits

- **Symptom.** A `paths` entry fires when a session patches a migration, and not when it only reads one.
- **Why.** A worker that reads files through shell commands names no file `the-cook` can match.
- **What `the-cook` does.** Matches the files the payload names, as the worker's ingredient maps it, such as the files a
  patch touches.
- **What you do.** Accept edit-only triggering for that worker, or make the entry `always` for a role that needs it.

## Configuration and versions

### Hand-edited rendered files are drift

- **Symptom.** `the-cook render --check` exits 2; an edit to the permissions in a worker's settings file or inside a
  managed block disappears after `the-cook render`.
- **Why.** Rendered files and blocks are outputs of the three repository files (`.cook/config.yaml`,
  `.cook/policy.yaml` and `.cook/hooks.yaml`), the pinned release and the pinned ingredients.
- **What `the-cook` does.** Rewrites what it owns, preserves everything else byte for byte, and reports differences
  ([render](render.md#drift-check)).
- **What you do.** Edit the source (`policy.yaml`, `hooks.yaml`, `config.yaml`) and render.

### A newer station's entries reach a repository only when it is applied again

- **Symptom.** A newer version of a station the repository applied holds an entry the repository lacks, such as a lane
  or a post-edit hook, and neither `the-cook render` nor moving an ingredient pin adds it.
- **Why.** A station is applied once: its entries are written into the repository's files when someone applies it, and
  from then on they are the repository's own. `stations:` records which version was applied, not which entries it
  wrote, and has no effect on renders; only the pinned ingredients render on every render
  ([ADR 0013](../adrs/0013-organization-content.md)).
- **What `the-cook` does.** `the-cook station apply <name> --cookbook <source>@<version>` adds only the entries the
  repository lacks: every entry it holds stays as it is, an item it names that differs from the station's is kept and
  reported, and a second apply of the same version adds nothing. It also moves the station's record in `stations:` and
  the ingredient pins the station lists ([stations](stations.md)).
- **What you do.** Run `./cook station apply <name> --cookbook <source>@<version> --dry-run` with the newer version to
  see what it would add, then apply it, review the diff and commit. Applying again also adds back an entry the
  repository deleted on purpose, so read the diff before confirming. Across many repositories, use [fleet](fleet.md).

### A command that needs a contract no ingredient fills refuses

- **Symptom.** `./cook workspace create` or `./cook attempt` exits 3 naming `the-cook station apply` and
  `the-cook ingredient pin`, while `./cook doctor` works and lists the unfilled contract with the commands it makes
  unavailable.
- **Why.** The binary has no code for any program. Workspaces, bases and history need an ingredient that fills the
  version-control contract, and starting a worker needs an ingredient that fills the worker contract. Reading the
  profile needs neither: the binary finds the repository root as the nearest directory upward that holds
  `.cook/config.yaml`.
- **What `the-cook` does.** Refuses with exit 3 before acting, and never downloads an ingredient on its own. Hooks
  print one stderr line and exit 0.
- **What you do.** Pin an ingredient that fills the contract, from a cookbook whose `ingredient show` output you have
  read: `./cook ingredient pin <name> --cookbook <source>@<version>`, or apply a station that lists one; then review and
  commit the diff.

### A fresh clone has no ingredients cached

- **Symptom.** On a new machine or in a CI job, `./cook render` exits 3 and `./cook doctor` reports a finding, each
  naming `./cook fetch`, and worker hooks print one stderr line.
- **Why.** Only `ingredient pin`, `station apply`, `fetch` and `scaffold --cookbook` download ingredients and stations.
  Every other command reads them from the per-user cache, which a fresh clone or a new CI runner does not have.
- **What `the-cook` does.** Downloads nothing implicitly; verifies every archive against its pin when it is
  downloaded and every time it is loaded, and refuses a mismatch with exit 1.
- **What you do.** Run `./cook fetch` once per machine while online, and as the first step of a CI job that renders or
  runs `doctor`. After that the clone works offline.

### Pinning trusts the cookbook at that moment

- **Symptom.** A pinned ingredient runs a command, renders a rule or marks a floor nobody on the team read, or an
  applied station wrote entries nobody read.
- **Why.** `the-cook station apply` and `the-cook ingredient pin` trust the cookbook's `checksums.txt` as it is
  downloaded over HTTPS at that moment. No release key vouches for an ingredient or a station. From then on the binary
  verifies each archive against its pin, so the pin fixes its bytes, but the pin itself is only as trustworthy as the
  review of the diff that added it.
- **What `the-cook` does.** Shows the pins, and a station's entries and templates, as one diff and asks before writing
  them, and renders in the same change, so the declaration and its effect are one diff.
- **What you do.** Read each one with `./cook ingredient show <name> --cookbook <source>@<version>` or
  `./cook station show <name> --cookbook <source>@<version>` before approving the pin, and review a pin move like any
  change to the policy.

## The wrapper

### The first run needs the network

- **Symptom.** On a fresh clone, a new machine, or after the pin moves to a new release, `./cook <command>` fails
  to download, and worker sessions show a skipped hook.
- **Why.** The wrapper runs only the pinned version. The first time a machine needs it, the wrapper downloads the
  release asset with `curl` or `wget` and checks it with `sha256sum` or `shasum`. Without network access, or without
  those tools, there is nothing to run. Afterwards the wrapper runs offline, from the cache.
- **What `the-cook` does.** Explicit entry points exit non-zero and say why; hooks skip with one line and exit 0.
  Nothing partly downloaded is cached.
- **What you do.** Run `./cook version` once while online, after cloning and after each upgrade. On an air-gapped
  machine, bring the pinned release in, check it against the checksum in `.cook/wrapper.yaml`, and pre-fill the
  cache with it or point `COOK_BINARY` at it ([air-gapped machines](wrapper.md#air-gapped-machines)).

### A pending pin stops every machine without the release

- **Symptom.** After a pull, `./cook <command>` exits 3 with
  `the-cook: pin of v0.9.0 is pending, nothing downloaded; with network access, run: the-cook wrapper --version v0.9.0`,
  and worker hooks skip. The machine that scaffolded the repository works.
- **Why.** The repository was scaffolded with `--offline`, which writes `.cook/wrapper.yaml` with
  `checksums: pending`, and that pin was pushed before it was completed. The wrapper has no checksum to verify a
  download against, so it downloads nothing; only a machine that already has the release in its cache can run it.
- **What `the-cook` does.** Refuses every download against a pending pin, runs a release already in the cache, and
  reports the pending pin as a `doctor` finding, so a CI job that runs `doctor` stops it before it merges
  ([wrapper](wrapper.md#a-pending-pin)).
- **What you do.** With network access and any installed `the-cook`, run `the-cook wrapper --version <the pinned tag>`
  and commit the result: the diff is the checksums and the manifest. Complete a pending pin before you push it.

### A checksum mismatch refuses the download

- **Symptom.** `./cook` exits 1 naming a checksum mismatch. Nothing is cached, so the next run downloads again.
- **Why.** The sha256 of the downloaded asset differs from the one `.cook/wrapper.yaml` pins for this platform: a
  corrupted download, a proxy that rewrites responses, a pin edited by hand, or a release asset replaced after the pin
  was written.
- **What `the-cook` does.** Refuses to cache or run the asset. It never falls back to another version and never skips
  the check.
- **What you do.** Retry once. If the mismatch persists, read the history of `.cook/wrapper.yaml` and compare the pinned
  checksum with the release's published checksum file. When the pin matches the release and the download does not,
  investigate the network path or the release; do not work around it by editing the checksum or setting `COOK_BINARY`
  ([wrapper](wrapper.md#troubleshooting)).

### Bypassing the pin runs another version

- **Symptom.** A command exits 3 naming a schema newer than the binary supports, or one person's renders differ from
  everyone else's.
- **Why.** The pin holds only while the pinned binary runs. `COOK_BINARY` makes the wrapper run whatever binary it
  names, and a global `the-cook` from a release without wrapper deferral runs itself instead of the pinned version.
- **What `the-cook` does.** A global binary with wrapper deferral execs the pinned version inside any repository that
  has `.cook/wrapper.yaml`. A binary older than the repository's schema refuses rather than render a format it does
  not know, naming the pinned release to run. `the-cook doctor` notes when it runs as another release than the pin.
- **What you do.** Run `./cook`, or update the global binary. Set `COOK_BINARY` only on an air-gapped machine
  or while developing `the-cook`, and unset it afterwards.

### Editing `wrapper.yaml` by hand breaks the checksums

- **Symptom.** After `version` was changed by hand in `.cook/wrapper.yaml`, every download fails with a checksum
  mismatch, or fails on one platform.
- **Why.** Checksums belong to one version. A hand-edited `version` keeps the previous version's checksums, and a
  platform without a checksum has nothing to verify against.
- **What `the-cook` does.** Refuses every download that does not match
  ([A checksum mismatch refuses the download](#a-checksum-mismatch-refuses-the-download)).
- **What you do.** Upgrade with `./cook wrapper --version <tag>` (or `--latest`), which verifies the release's
  signed checksum file and writes the version and the checksum of every platform from it, and review the diff. Across
  repositories, run `the-cook fleet wrapper --version <tag> --deliver` ([fleet](fleet.md)).

### A second pin in the task runner drifts from the wrapper

- **Symptom.** The task runner and `./cook` run different releases of `the-cook`.
- **Why.** The task runner's tool list also pins `the-cook`, so two files answer the same question, and an upgrade
  moves only one of them.
- **What `the-cook` does.** Reads its version only from `.cook/wrapper.yaml`, and never reads or edits the task
  runner's file ([wrapper](wrapper.md#one-pin)).
- **What you do.** Remove `the-cook` from the task runner's tool list.

## Removal

### After eject, nothing enforces the conventions

- **Symptom.** After `the-cook remove --eject`, `AGENTS.md` still describes where workspaces go and what workers may
  run, yet a worker commits its own work, delivery has no guard, and lint-on-edit,
  notifications and hook-delivered standing context have stopped.
- **Why.** Eject stops managing and keeps the result. Convention blocks (`workspaces`, `policy`, `memory`, `context`)
  keep their text without markers, and the `.gitignore` entries, the rendered permissions and the
  raw hooks stay as the repository's own. Tool blocks (`commands`, `layout`, `verify`, `lifecycle`) are removed, because
  they describe `the-cook` commands that do not exist after the eject, and so are the hooks that called `the-cook`.
  Receipts, the attempt contract, the tamper check, the lifecycle guards and the drift check need the binary, so they
  stop.
- **What `the-cook` does.** Lists every block and hook it removed ([remove](remove.md#eject)), and nothing after
  that.
- **What you do.** Decide which guarantees the repository still needs and move each one where something enforces it:
  CI for the checks, branch protection for what lands, the workers' own permissions for what they may run. Edit the
  convention text by hand when a convention changes, since nothing re-renders it. Run `the-cook remove` without
  `--eject` when the conventions should go too.

## Scope limits

### Sessions started in a monorepo subdirectory are out of scope

- **Symptom.** A worker started in `services/billing/` does not apply the repository's rules, or applies the settings
  of a worker directory nested there instead.
- **Why.** `the-cook` operates at the repository root: one profile, one policy, one hooks file per repository. Whether
  a worker started in a subdirectory reads the root's settings is the worker's behaviour.
- **What `the-cook` does.** Renders at the root and leaves nested worker directories alone
  ([compatibility](compatibility.md#monorepos)).
- **What you do.** Start workers at the repository root.

### `the-cook` never writes the team's record

- **Symptom.** A task completes and its issue is unchanged; a delivered branch has no comment on its pull request.
- **Why.** Issues, comments, reviews, labels and boards are the team's record. The binary writes none of them, and
  lifecycle actions deliver work and do not report on it.
- **What `the-cook` does.** Leaves the record to people, workers and CI.
- **What you do.** Have the coordinating worker update the issue or the pull request, following a skill that says how
  the team records work, or let CI report.

### A worker's own workspaces are not managed

- **Symptom.** Worktrees or workspaces a worker creates for itself are not listed, cleaned or bootstrapped.
- **Why.** They belong to the worker.
- **What `the-cook` does.** Never scans or touches them, and never cleans branches it did not record.
- **What you do.** Use `the-cook workspace create` for work that needs a session, a receipt base or a task id.
