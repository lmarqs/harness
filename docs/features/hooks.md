# Hooks: `.cook/hooks.yaml`

## Objective

**Problem.** A developer wants the same things to happen around a coding agent's work in every session: the edited
file formatted and linted right away, a repository check run when certain files change, a message when the coding
agent waits for them or finishes. Each coding agent has its own hook format and events, so these are written by hand
once per program and drift apart, and a hook that fails on a teammate's machine interrupts every session there. The
steps around a change, such as pushing a branch or posting to a chat, are repeated by hand at each stage, and each
person does them differently.

**Goal.** Every worker in the repository gets the same feedback after edits and sends the same notifications, the
steps at each stage of a change run the same way whoever reaches it, and none of it breaks a teammate's session.

## Overview

The hooks file declares, once and neutrally, two kinds of automation:

- **worker hooks**, which run at worker events, when a [worker](workers.md) starts a session, edits a file, waits for a
  person or finishes a turn: format or lint the edited file, run a repository validator, add a line of session
  context, send a notification. `the-cook render` writes them into each worker's native hook configuration, through
  its ingredient, and `the-cook hook` runs them when the worker fires an event. Worker hooks give feedback and notify;
  they never decide permissions.
- **lifecycle actions**, under the `lifecycle` key, which run at the [development lifecycle](lifecycle.md)'s events:
  push a branch, open a pull request, post a chat message, clean up after a merge. `the-cook` runs them itself; they
  are not rendered into any worker's files.

The same worker hooks also deliver the repository's [standing context](context.md), declared in the profile: skills
and directives injected when a session starts, when the worker touches matching files, or when the person's message
contains a listed word.

Jump to [field reference](#reference), [examples](#examples), [guidelines](#guidelines),
[troubleshooting](#troubleshooting).

Commands are named `the-cook <command>` on this page. Run them through the repository's wrapper,
`./cook <command>`, from the root of a checkout; with a global install, `the-cook <command>` is equivalent
([wrapper](wrapper.md)).

## When to use it

| You want | Declare |
| --- | --- |
| The edited file formatted or linted right after every edit a worker makes | a `post_edit` entry |
| The stack's usual format-on-edit or lint-on-edit hook | `./cook station apply <name> --cookbook <source>@<version>` with a stack station, such as `node`, whose [entries](stations.md#entries) add it as a `post_edit` entry |
| A repository check run when certain files are edited | `validators` |
| A session in a [managed workspace](workspaces.md) to know its task | `session_start.context: true` |
| A message when a worker waits for you: a permission prompt, a question, idle input | an `attention` action |
| A message when a worker finishes a turn | a `turn_end` action |
| A skill or directive in the worker's context without invoking it | a `context` entry in the profile ([context](context.md)) |
| A hook `the-cook` does not model, such as a path guard before a tool runs | a `raw` entry |
| Something to happen when work starts, is verified, completed, delivered, lands or stops for a person, such as a push, a pull request or a chat message | a `lifecycle` action |

Worker hooks, notification hooks and hook-delivered standing context need `modules.hooks: true` in the
[profile](profile.md). Lifecycle actions do not: the binary fires them from its own commands.

## How it works

### Rendering

For each declared worker whose ingredient maps hook payloads, `render` writes two kinds of hook entries through the
worker's [ingredient](workers.md#the-worker-contract), whose renders own the program's native hook configuration:

| Entry | Events | Command |
| --- | --- | --- |
| `the-cook hook` entries | the events in the table below that the declarations need | the wrapper command below |
| raw entries | the event named in `raw`, with its matcher | the `run` command as written |

Each worker ingredient maps the neutral events onto its program's native events, and its page in its cookbook lists
the mapping:

| Purpose | Neutral event | Rendered when |
| --- | --- | --- |
| Session context, `always` context | `session-start` | always, with `modules.hooks` |
| `post_edit`, validators, `when.paths` context | `post-tool-use`, for the tools the ingredient names as edits and reads | `post_edit`, validators or `when.paths` entries exist |
| `when.prompt` context | `prompt-submit` | `when.prompt` entries exist |
| `attention` | `attention` | `attention` actions exist |
| `turn_end` | `turn-end` | `turn_end` actions exist |

A neutral event the ingredient maps to no native event gets nothing rendered for that worker, and
[`the-cook doctor`](doctor.md) reports the gap as a note. For example, a worker with no event for waiting for a person
gets no `attention` entry, and `attention` never reaches its sessions. A worker whose ingredient declares no hook
payload mapping, such as a codemod, gets no hook entry at all.

The command of a `the-cook hook` entry starts the wrapper from the root of the checkout, which the version-control
ingredient's `root` operation finds ([the hook command](render.md#the-hook-command)):

```sh
"$(<root operation>)/cook" --no-download hook <event>
```

`<event>` is the neutral event the entry is for: `session-start`, `post-tool-use`, `prompt-submit`, `attention` or
`turn-end`. The command runs the wrapper committed in the checkout the worker works in, the primary checkout or a
workspace, so the hook runs the version that checkout pins. It never depends on `the-cook` being on the worker's
`PATH`.

### The missing-binary guard

A worker hook is an implicit entry point: it runs because a worker started a session or edited a file, not because
someone asked for `the-cook`. Implicit entry points never download. With `--no-download`, the wrapper runs the pinned
binary only when it is already in the cache ([wrapper](wrapper.md)). When it is not, on a fresh clone, a new machine,
or after the pin moved to a release this machine has not fetched, the wrapper prints one line on stderr naming
`./cook` and exits 0, without running `the-cook hook`:

```text
the-cook: v0.9.0 not downloaded, hook skipped; run: ./cook version
```

The session continues normally, without lint-on-edit, standing context or notifications, until the pinned version is
cached. Any explicit command, such as `./cook version`, downloads and verifies it. A teammate who never runs one
loses only that feedback.

The session-start entry also adds one line to the session context, so that the worker itself knows why it has no
standing context:

```text
the-cook v0.9.0 is not cached on this machine; run ./cook version once to fetch it.
```

The wrapper prints both lines itself, as fixed text from its template, because no binary is there to print them. It
recognises the session-start entry by the `session-start` argument. It still never downloads and still exits 0.
Directives a session must never miss belong in [static context](context.md#static-entries), which is part of
`AGENTS.md`.

Raw entries are rendered as native hook entries running their own commands, not through `the-cook hook`, so they keep
running without the binary. [`the-cook scaffold --adopt`](scaffold.md#how-each-source-becomes-an-entry) turns each
existing hook it cannot model into a raw entry, so that hook runs after adoption exactly as it did before. The manifest
records raw entries as owned, so `render` updates them when `hooks.yaml` changes and `render --check` reports a hand
edit.

### Payload normalisation

Each worker's program sends its own JSON payload on stdin. The binary knows no program's payload: the worker's
ingredient maps it, by JSON paths, onto one neutral event, and `the-cook hook` acts on that event. The ingredient also
says which tools are edits and which are reads, and which native events are `attention`, `turn_end` and each
standing-context trigger ([the worker contract](workers.md#the-worker-contract)).

| Normalised field | Mapped by the worker ingredient from |
| --- | --- |
| event | the payload's hook event name: session start, post tool use, prompt submission, waiting for a person (`attention`) or turn end (`turn_end`) |
| session | the payload's session id, for the once-per-session [context](context.md) entries |
| tool | the tool name, and whether the ingredient lists it as an edit tool or a read tool |
| read and edited files | the tool input's file path, made relative to the checkout; for a patch tool that names no single file, the checkout's uncommitted changes against its head commit, as the version-control ingredient lists them |
| prompt | the person's message, on prompt submission |
| message | the worker's own notification text, on `attention` and `turn_end` |
| checkout | the payload's working directory, resolved to the primary checkout or the managed workspace it is in |

Files outside the checkout are ignored.

### Session start

| Setting | Output, as additional context for the worker |
| --- | --- |
| `context: true` | In a [managed workspace](workspaces.md), one line: the task id given with `--task`, when there is one, and the command to verify. Outside managed workspaces, nothing |
| `context.always` in the profile | The `always` entries for the session's role, each headed by one line naming it ([context](context.md)). Under `COOK_CONTEXT_IN_PROMPT=1`, which an attempt sets when its prompt arrived on stdin, none: the binary put them in the prompt ([delivery in the prompt](context.md#delivery-in-the-prompt)) |

Worker sessions never receive an update notice: moving the pin is a reviewed supply-chain decision, so the notice goes
to people, on the stderr of explicit commands and in `the-cook doctor` ([update notice](installation.md#update-notice)).

### Post edit

For each edited file:

1. Every `post_edit` entry whose `paths` match and whose `exclude` does not runs its `run` command with `{file}`
   replaced by the file's repository-relative path, shell-quoted. `{file}` and `{pattern}` in profile and hook
   commands are the only substitutions `the-cook` performs. Lifecycle actions get values only through environment
   variables.
2. A content cache, `hook-checks.json` in the checkout's state directory (`state.dir`, default `.work`), records a
   hash of each file after a successful run. An edit that leaves the content identical to the cached hash skips the
   command.
3. Every validator whose `when` matches any edited file runs once.
4. The first time in the session a read or edited file matches a `when.paths` [context](context.md) entry, the entry
   is added to the worker's context.

If a command or validator fails, `the-cook hook` exits 2 with the command's output on stderr. A coding agent that
shows a hook's stderr to its model as feedback, as its ingredient's page states, fixes the problem in its next step.
The edit itself is never undone, and no tool call is blocked.

Post-edit hooks run on a worker's edits only. The files that the `run` and `template` steps of a
[recipe](recipes.md#step-kinds) write fire no hook, because no worker event reports them; the lint command and the lanes
of the run's Verify stage check them ([verify](verify.md#the-run)). The worker of an `attempt` step gets post-edit
feedback as in any attempt.

### Prompt submission

When the person's message contains a word or phrase of a `when.prompt` [context](context.md) entry, compared
case-insensitively as whole words, the entry is added to the worker's context, once per session. Nothing else runs on
prompt submission.

### Worker notifications

`attention` and `turn_end` run declared actions when a worker needs a person or finishes, so a person away from the
terminal learns about it:

```yaml
attention:        # the worker is waiting for a person: a permission prompt, a question, idle input
  - name: ping-me
    http:
      url_env: CHAT_WEBHOOK_URL
      json: { text: "$COOK_WORKER needs you in $COOK_REPO ($COOK_BRANCH): $COOK_MESSAGE" }
turn_end:         # the worker finished a turn
  - name: done
    run: notify-send "$COOK_WORKER finished in $COOK_WORKSPACE"
```

Each worker ingredient names the native events that carry `attention` and `turn_end` for its program; a worker with no
such event gets nothing rendered for it, and `the-cook doctor` notes the gap.

Values reach the actions as environment variables, with the same rules as [lifecycle actions](#lifecycle-actions):

| Variable | Value |
| --- | --- |
| `COOK_EVENT` | `attention` or `turn_end` |
| `COOK_WORKER` | the worker's name, such as `claude` or `codex` |
| `COOK_MESSAGE` | the worker's own notification text, when it sends one. Untrusted: the worker writes it, and its input reaches it |
| `COOK_REPO` | absolute path of the primary checkout |
| `COOK_WORKSPACE` | absolute path of the checkout the worker works in |
| `COOK_BRANCH` | the checkout's branch |

- Actions are of kind `run` or `http` ([action kinds](lifecycle.md#action-kinds)); an `attempt` action here is an
  error, so a worker hook never starts an attempt.
- `the-cook hook` exits 0 on these events whatever the actions return, so a failed notification never keeps a worker
  working or interrupts it. A failure is printed on stderr.
- They do nothing in an attempt: `the-cook attempt`, `attempt` actions and a recipe's `attempt` steps set
  `COOK_ATTEMPT=1` for the worker they start. An unattended run reports through
  [lifecycle events](lifecycle.md#trigger-points) instead: in a workspace bound to a task, `the-cook attempt` fires
  `completed` after a verified attempt; `the-cook make` fires `completed` after its replay, and `blocked` when it
  stops; whatever drives a loop runner's run fires `blocked` and the events of scope `run`.
- Like every worker hook, they do nothing on a machine where the pinned release is not cached
  ([the missing-binary guard](#the-missing-binary-guard)).
- A native setting that holds a single notification program is owned by the worker ingredient's renders only when they
  wrote it. When the file already sets it to a program of its own, `render` keeps that program, renders no `turn_end`
  for that worker, and `the-cook doctor` notes it.

### Lifecycle actions

The `lifecycle` key maps each event to an ordered list of actions. The events are `start`, `verified`, `completed`,
`deliver`, `landed` and the out-of-band `blocked`. The [lifecycle](lifecycle.md) page is the full reference for trigger
points, scopes, guards, action kinds, the environment variables actions receive, and the log.

```yaml
lifecycle:
  deliver:
    - name: push
      run: ./scripts/push-branch.sh "$COOK_BRANCH"
    - name: open-pr
      run: ./scripts/open-draft-pr.sh "$COOK_DEFAULT_BRANCH" "$COOK_BRANCH"
      auto: false
      scope: [task, change]        # default: all scopes of the event
      on_failure: stop             # stop (default) | continue
  landed:
    - name: delete-remote-branch
      run: ./scripts/delete-remote-branch.sh "$COOK_BRANCH"
      auto: true
  blocked:
    - name: notify
      http: { url_env: CHAT_WEBHOOK_URL, json: { text: "Blocked in $COOK_REPO: $COOK_REASON" } }
      auto: true
```

The rules for actions:

- an action is a shell command (`run`), an HTTP request `the-cook` makes (`http`), or an attempt of a worker on a
  prompt, under the [attempt contract](attempts.md#the-attempt-contract) (`attempt`)
  ([action kinds](lifecycle.md#action-kinds));
- actions run in the work's checkout, in declaration order; the first failure stops the event with exit 2 unless the
  action says `on_failure: continue`, in which case the remaining actions run and the event still exits 2;
- values reach an action only as environment variables, never substituted into `run`, so a task title or a branch
  name cannot inject a command;
- secrets for `http` come only from the variables `url_env` and `headers_env` name;
- `auto: true` runs the action at the event's automatic trigger point; every action also runs when a person or a
  coordinating worker fires the event with `the-cook lifecycle run <event>`;
- lifecycle actions never run inside the worker of an attempt, whether `the-cook attempt`, an `attempt` action or a
  recipe's `attempt` step started it;
- every action is one the repository declares in this file: the seeded files hold none, a
  [station](stations.md#entries) that declares actions writes them here, as entries, only when a person applies it,
  and a recipe declares none: a recipe run fires the events, and the actions here run at them.

An action runs with the permissions of the process that fired it, outside any worker's permission checks. Review
lifecycle actions like code; see [safety](safety.md).

## Reference

| Field | Type | Default | Meaning |
| --- | --- | --- | --- |
| `schema` | integer | required | `1` |
| `post_edit` | list | `[]` | Commands run on edited files |
| `post_edit[].paths` | list of globs | required | Files the command applies to |
| `post_edit[].exclude` | list of globs | `[]` | Files excluded from `paths` |
| `post_edit[].run` | command with `{file}` | required | Run once per matching edited file, from the checkout root |
| `validators` | `{ from: verify.validators }` or a list | none | `from: verify.validators` reuses the profile's validators; a list declares hook-only validators |
| `validators[].when` | list of globs | required | Run when an edited file matches |
| `validators[].run` | command | required | The check |
| `session_start.context` | boolean | `true` | Add the managed-workspace context line |
| `raw` | map of event name to list | `{}` | Hooks `the-cook` does not model, rendered as written |
| `raw.<Event>[].matcher` | string | none | The program's matcher for the event (for example a tool-name pattern) |
| `raw.<Event>[].run` | command | required | The hook command |
| `attention` | list | `[]` | Actions run when the worker waits for a person |
| `turn_end` | list | `[]` | Actions run when the worker finishes a turn |
| `attention[].name`, `turn_end[].name` | string | required | Unique within the list |
| `attention[].run`, `turn_end[].run` | command | one of `run`, `http` | A shell command ([action kinds](lifecycle.md#action-kinds)) |
| `attention[].http`, `turn_end[].http` | map | one of `run`, `http` | An HTTP request ([action kinds](lifecycle.md#http)) |
| `lifecycle` | map of event name to list | `{}` | Lifecycle actions; events `start`, `verified`, `completed`, `deliver`, `landed`, `blocked` |
| `lifecycle.<event>[].name` | string | required | Unique within the event; `--action` and the log refer to it |
| `lifecycle.<event>[].run`, `.http`, `.attempt` | command or map | exactly one | The action's kind ([action kinds](lifecycle.md#action-kinds)) |
| `lifecycle.<event>[].auto` | boolean | `false` | Also run at the event's automatic trigger point |
| `lifecycle.<event>[].scope` | list of `task`, `run`, `change` | every scope of the event | Scopes the action applies to |
| `lifecycle.<event>[].on_failure` | `stop` \| `continue` | `stop` | `stop`: the first failure stops the event with exit 2. `continue`: the remaining actions run, and the event still exits 2 |

Event names in `raw` are the programs' native event names, as each worker ingredient's page lists them. An event a
worker does not support is not rendered for that worker.

`the-cook hook` exit codes:

| Exit code | Meaning |
| --- | --- |
| 0 | Nothing to do, every command passed, or the event is `attention` or `turn_end`, whatever their actions returned |
| 2 | A post-edit command or validator failed; its output is on stderr for the worker |

When the pinned binary is not cached, the wrapper exits 0 and `the-cook hook` does not run. When a pinned
[ingredient](ingredients.md) the hook needs is not cached, `the-cook hook` prints one line on stderr naming
`./cook fetch` and exits 0; when no pinned ingredient fills a contract the hook needs, it prints one line naming
`./cook ingredient pin` and exits 0. Hooks never fetch and never fail. `COOK_NO_CONTEXT=1` turns off standing-context
injection only ([context](context.md#environment)).

Files read: stdin (the payload), `.cook/wrapper.yaml` (by the wrapper), `.cook/hooks.yaml`,
`.cook/config.yaml`, `session.json` and `hook-checks.json` in the checkout's state directory, the read and edited
files, the skills and files `context` entries name, the cache directory (`$COOK_HOME`, default
`$XDG_CACHE_HOME/the-cook`, otherwise `~/.cache/the-cook`) for the binary and the pinned ingredients.
Files written: `hook-checks.json`, `context/<session-id>.json` in the state directory, whatever the declared commands
write (for example a formatter fixing the file).

## Examples

### Repository scripts on edit, and a ping when a worker waits

```yaml
# .cook/hooks.yaml
schema: 1
post_edit:
  - paths: [src/**, test/**]
    exclude: [src/migrations/**]
    run: ./scripts/lint-file.sh {file}
validators: { from: verify.validators }
session_start: { context: true }
attention:
  - name: ping-me
    http:
      url_env: CHAT_WEBHOOK_URL
      json: { text: "$COOK_WORKER needs you in $COOK_REPO ($COOK_BRANCH): $COOK_MESSAGE" }
```

`CHAT_WEBHOOK_URL` is set in each developer's own shell profile; a developer who does not set it gets an `attention`
failure on stderr, and the session continues.

### A desktop notification when a worker finishes

```yaml
schema: 1
turn_end:
  - name: done
    run: notify-send --app-name=the-cook "Turn finished" "$COOK_WORKER in $COOK_WORKSPACE"
```

### A path guard as a raw entry

With a worker ingredient named `claude`, pinned from `github:acme/cookbook`, whose page lists `PreToolUse` as the
native event before a tool runs:

```yaml
schema: 1
post_edit:
  - { paths: ["**/*.tf"], run: "./scripts/format-file.sh {file}" }
validators:
  - { when: [modules/**/variables.tf], run: ./scripts/check-variable-docs.sh }
session_start: { context: true }
raw:
  PreToolUse: [{ matcher: Edit|Write, run: ./scripts/protect-state-files.sh }]
```

The `raw` entry runs as a native hook, whether or not the pinned release is cached, and is rendered only for workers
that have that event.

## Guidelines

- Do keep post-edit commands fast and single-file. They run after every edit; slow checks belong in a
  [verify lane](verify.md).
- Do make post-edit commands fix what they can (`--fix`, `-w`) so the worker sees only what needs judgement.
- Do use `raw` for path guards before a tool runs, and keep them small and declared in this file, not hidden in scripts
  wired by hand.
- Don't use hooks to allow or deny commands; put that in the [policy](policy.md).
- Don't depend on hooks for correctness: they skip on any machine where the pinned binary is not cached yet, and
  `the-cook verify` is the proof.
- Do keep lifecycle actions short and readable in the file itself; a script an action calls is part of the action
  and is reviewed with it.
- Don't put secrets in lifecycle or notification actions: they inherit the environment of the process that fires
  them. An `http` action reads its URL and headers from the variables it names.
- Do keep notification actions fast. They run inside the worker's hook call; an `http` action stops at its `timeout`.
- Don't send `COOK_MESSAGE` anywhere it would be read as an instruction: it is the worker's text, shaped by
  everything the worker read.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| `the-cook: v0.9.0 not downloaded, hook skipped; run: ./cook version` in the session | The pinned version is not cached on this machine, and hooks never download | Run `./cook version` once in the repository |
| Hooks never run | `modules.hooks` is off, the folder is not trusted by the worker's program, or the program's hook feature is disabled | Turn the module on and render; trust the folder |
| Lint runs on every edit even when nothing changed | The command rewrites the file each time | Make the command idempotent |
| One worker's sessions ignore hooks | The program reads project hooks only when a setting enables them, which its ingredient renders | `the-cook render`, then `the-cook doctor` |
| A worker gets no hooks at all | Its ingredient declares no hook payload mapping | Expected for such a program, such as a codemod; `the-cook verify` still checks its work |
| A raw hook stopped running | It was removed from `hooks.yaml`, or its command is not executable | Restore the entry; `chmod +x` the script |
| A lifecycle action never runs on its own | It has no `auto: true`, or the event's guard did not let it fire | `the-cook lifecycle list`; read the [lifecycle log](lifecycle.md#the-lifecycle-log) |
| The session starts with `the-cook v0.9.0 is not cached on this machine; run ./cook version once to fetch it.` | The pinned release is not cached; the session has no standing context and no notifications | Run `./cook version` once |
| No `attention` notification from one worker | Its ingredient maps no native event to waiting for a person; `doctor` notes it | Use `turn_end`, where the worker reports it |
| No `turn_end` from one worker; `doctor` notes a notification program of the repository's own | The program's single-program notification setting already names another program | Move that program into a `turn_end` `run` action, remove the setting, then `the-cook render` |
| No notification on one machine | The variable in `url_env` is not set there, or the pinned release is not cached there | Set the variable in that shell; run `./cook version` once |
| A file a recipe's `run` or `template` step wrote was never linted on write | Post-edit hooks run on worker edits only; no worker event reports a step's files | Expected: the run's Verify stage lints each changed file that matches `verify.lint_paths` |
| A notification arrives for every turn of an unattended run | It cannot: notification hooks do nothing in an attempt (`COOK_ATTEMPT=1`) | Declare `completed` and `blocked` lifecycle actions for unattended runs, and have whatever drives the run fire `blocked` and the events of scope `run` |

## Coexistence

Hook entries `the-cook` did not render (for example one a coding agent's plugin or a person added to its settings
file) are preserved. Hooks in a program's local override files are never touched. See
[compatibility](compatibility.md).

After [`the-cook remove --eject`](remove.md#eject), raw entries stay in the native files as the repository's own hooks
and keep working. The `the-cook hook` entries, including notifications and standing-context injection, are removed, and
listed in the output, because they call a wrapper that eject deletes. Static context entries stay in `AGENTS.md`.

The reasoning for hooks that give feedback but never decide permissions is in
[ADR 0011](../adrs/0011-safety-invariants.md); for action kinds and notification events, in
[ADR 0019](../adrs/0019-action-kinds.md).
