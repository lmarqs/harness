# Hooks: `.harness/hooks.yaml`

The hooks file declares, once and agent-neutrally, two kinds of automation:

- **agent hooks**, which run when an agent starts a session, edits a file, waits for a person or finishes a turn:
  format or lint the edited file, run a repository validator, add a line of session context, send a notification.
  `harness render` writes them into each agent's native hook configuration, and `harness hook` runs them when the
  agent fires an event. Agent hooks give feedback and notify; they never decide permissions.
- **lifecycle actions**, under the `lifecycle` key, which run at the [development lifecycle](lifecycle.md)'s events:
  push a branch, open a pull request, post a chat message, clean up after a merge. `harness` runs them itself; they
  are not rendered into agent files.

The same agent hooks also deliver the repository's [standing context](context.md), declared in the profile: skills
and directives injected when a session starts, when the agent touches matching files, or when the person's message
contains a listed word.

Commands are named `harness <command>` on this page. Run them through the repository's wrapper,
`./harnessw <command>`, from the root of a checkout; with a global install, `harness <command>` is equivalent
([wrapper](wrapper.md)).

## When to use it

| You want | Declare |
| --- | --- |
| The edited file formatted or linted right after every agent edit | a `post_edit` entry |
| A repository check run when certain files are edited | `validators` |
| A session in a managed worktree to know its task and tracker | `session_start.context: true` |
| An update notice in agent sessions | `session_start.update_notice: true` |
| A message when an agent waits for you: a permission prompt, a question, idle input | an `attention` action |
| A message when an agent finishes a turn | a `turn_end` action |
| A skill or directive in the agent's context without invoking it | a `context` entry in the profile ([context](context.md)) |
| A hook `harness` does not model, such as a `PreToolUse` path guard | a `raw` entry |
| Something to happen when work starts, is verified, completed, delivered, lands or stops for a person, such as a push, a pull request or a chat message | a `lifecycle` action |

Agent hooks, notification hooks and hook-delivered standing context need `modules.hooks: true` in the
[profile](profile.md). Lifecycle actions do not: the binary fires them from its own commands.

## How it works

### Rendering

For each declared agent, `render` writes two kinds of hook entries:

| Entry | Events | Command |
| --- | --- | --- |
| harness entries | the events in the table below that the declarations need | the wrapper command below |
| raw entries | the event named in `raw`, with its matcher | the `run` command as written |

The events `harness` renders, per agent:

| Purpose | Claude Code 2.1.x | Codex CLI 0.16x | Rendered when |
| --- | --- | --- | --- |
| Session context, update notice, `always` context | `SessionStart` | `SessionStart` | always, with `modules.hooks` |
| `post_edit`, validators, `when.paths` context | `PostToolUse` (matcher `Edit\|Write\|MultiEdit`, and `Read` when `when.paths` entries exist) | `PostToolUse` (every tool, filtered by `harness hook`) | `post_edit`, validators or `when.paths` entries exist |
| `when.prompt` context | `UserPromptSubmit` | `UserPromptSubmit` | `when.prompt` entries exist |
| `attention` | `Notification` | none | `attention` actions exist |
| `turn_end` | `Stop` | the `notify` key in `.codex/config.toml` | `turn_end` actions exist |

An agent without an event gets nothing rendered for it, and [`harness doctor`](doctor.md) reports the gap as a note:
Codex has no event for an agent waiting for a person, so `attention` never reaches a Codex session.

The harness entry's command is:

```sh
"$(git rev-parse --show-toplevel)/harnessw" --no-download hook
```

It runs the wrapper committed in the checkout the agent works in, the primary checkout or a worktree, so the hook
runs the version that checkout pins. It never depends on `harness` being on the agent's `PATH`.

### The missing-binary guard

An agent hook is an implicit entry point: it runs because an agent started a session or edited a file, not because
someone asked for `harness`. Implicit entry points never download. With `--no-download`, the wrapper runs the pinned
binary only when it is already in the cache ([wrapper](wrapper.md)). When it is not, on a fresh clone, a new machine,
or after the pin moved to a release this machine has not fetched, the wrapper prints one line on stderr naming
`./harnessw` and exits 0, without running `harness hook`:

```text
harness: v0.9.0 not downloaded, hook skipped; run: ./harnessw version
```

The session continues normally, without lint-on-edit, standing context or notifications, until the pinned version is
cached: any explicit command, such as `./harnessw version` or a task shim, downloads and verifies it. A teammate who
never runs one loses only that feedback.

The session-start entry is the one exception to silence: so that the agent itself knows why it has no standing
context, it adds one line to the session context instead of printing to stderr:

```text
harness v0.9.0 is not cached on this machine; run ./harnessw version once to fetch it.
```

It still never downloads and still exits 0. Directives a session must never miss belong in
[static context](context.md#static-entries), which is part of `AGENTS.md`.

Raw entries are rendered as native hook entries running their own commands, not through `harness hook`, so they keep
running without the binary, exactly as they did before adoption. The manifest records them as owned, so `render`
updates them when `hooks.yaml` changes and `render --check` reports a hand edit.

### Payload normalisation

Claude Code and Codex send different JSON payloads on stdin. `harness hook` reads either and reduces it to one event:

| Normalised field | Taken from |
| --- | --- |
| event | the payload's hook event name: session start, post tool use, prompt submission, waiting for a person (`attention`) or turn end (`turn_end`) |
| session | the payload's session id, for the once-per-session [context](context.md) entries |
| tool | the tool name. Edit tools: Claude Code's `Edit`, `Write`, `MultiEdit`; Codex's `apply_patch`. Read tool: Claude Code's `Read` |
| read and edited files | the tool input's file path, made relative to the checkout; for a patch tool that names no single file, the checkout's uncommitted changes against `HEAD` |
| prompt | the person's message, on prompt submission |
| message | the agent's own notification text, on `attention` and `turn_end` |
| checkout | the payload's working directory, resolved to the primary checkout or the managed worktree it is in |

Files outside the checkout are ignored.

### Session start

| Setting | Output, as additional context for the agent |
| --- | --- |
| `context: true` | In a [managed worktree](worktrees.md), one line: the tracker, the task, and the command to verify. Outside managed worktrees, nothing |
| `update_notice: true` | One line when a release newer than the pinned version exists, naming the upgrade command, `./harnessw wrapper --version <tag>`. The check runs at most once a day, is cached, never blocks, and is off with `HARNESS_NO_UPDATE_CHECK=1` ([upgrading](wrapper.md#upgrading)) |
| `context.always` in the profile | The `always` entries for the session's role, each headed by one line naming it ([context](context.md)) |

### Post edit

For each edited file:

1. Every `post_edit` entry whose `paths` match and whose `exclude` does not runs its `run` command with `{file}`
   replaced by the file's repository-relative path, shell-quoted. `{file}` and `{pattern}` in profile and hook
   commands are the only substitutions `harness` performs; lifecycle actions get values only through environment
   variables.
2. A content cache, `hook-checks.json` in the checkout's state directory (`state.dir`, default `.work`), records a
   hash of each file after a successful run; an edit that leaves the content identical to the cached hash skips the
   command.
3. Every validator whose `when` matches any edited file runs once.
4. The first time in the session a read or edited file matches a `when.paths` [context](context.md) entry, the entry
   is added to the agent's context.

If a command or validator fails, `harness hook` exits 2 with the command's output on stderr. Claude Code shows that
output to the model as feedback, so the agent fixes the problem in its next step. The edit itself is never undone, and
no tool call is blocked.

### Prompt submission

When the person's message contains a word or phrase of a `when.prompt` [context](context.md) entry, compared
case-insensitively as whole words, the entry is added to the agent's context, once per session. Nothing else runs on
prompt submission.

### Agent notifications

`attention` and `turn_end` run declared actions when an agent needs a person or finishes, so a person away from the
terminal learns about it:

```yaml
attention:        # the agent is waiting for a person: a permission prompt, a question, idle input
  - name: ping-me
    http:
      url_env: CHAT_WEBHOOK_URL
      json: { text: "$HARNESS_AGENT needs you in $HARNESS_REPO ($HARNESS_BRANCH): $HARNESS_MESSAGE" }
turn_end:         # the agent finished a turn
  - name: done
    run: notify-send "agent finished in $HARNESS_WORKTREE"
```

| Event | Claude Code | Codex |
| --- | --- | --- |
| `attention` | `Notification`: a permission prompt, or input idle | not rendered: Codex has no such event |
| `turn_end` | `Stop` | `notify`, called after each turn |

Values reach the actions as environment variables, with the same rules as lifecycle actions:

| Variable | Value |
| --- | --- |
| `HARNESS_EVENT` | `attention` or `turn_end` |
| `HARNESS_AGENT` | the agent's name, `claude` or `codex` |
| `HARNESS_MESSAGE` | the agent's own notification text, when it sends one. Untrusted: the agent writes it, and its input reaches it |
| `HARNESS_REPO` | absolute path of the primary checkout |
| `HARNESS_WORKTREE` | absolute path of the checkout the agent works in |
| `HARNESS_BRANCH` | the checkout's branch |

- Actions are of kind `run` or `http` ([action kinds](lifecycle.md#action-kinds)); an `agent` action here is an
  error, so an agent hook never starts another agent.
- `harness hook` exits 0 on these events whatever the actions return, so a failed notification never keeps an agent
  working or interrupts it. A failure is printed on stderr.
- They do not run in Ralph worker sessions: an unattended run reports through the lifecycle event
  [`blocked`](lifecycle.md#events).
- Like every agent hook, they do nothing on a machine where the pinned release is not cached
  ([the missing-binary guard](#the-missing-binary-guard)).
- The `notify` key of `.codex/config.toml` holds one program. `harness` owns it only when it rendered it: when the file
  already sets `notify` to a program of its own, `render` keeps that program, renders no `turn_end` for Codex, and
  `harness doctor` notes it.

### Lifecycle actions

The `lifecycle` key maps each event to an ordered list of actions. The events are `start`, `verified`, `completed`,
`deliver`, `landed` and the out-of-band `blocked`; [lifecycle](lifecycle.md) is the full reference: trigger points,
scopes, guards, action kinds, the environment variables actions receive, and the log.

```yaml
lifecycle:
  deliver:
    - name: push
      run: git push -u origin "$HARNESS_BRANCH"
    - name: open-pr
      run: gh pr create --draft --fill --base "$HARNESS_DEFAULT_BRANCH" --head "$HARNESS_BRANCH"
      auto: false
      scope: [task, change]        # default: all scopes of the event
      on_failure: stop             # stop (default) | continue
  landed:
    - name: delete-remote-branch
      run: git push origin --delete "$HARNESS_BRANCH"
      auto: true
  blocked:
    - name: notify
      http: { url_env: CHAT_WEBHOOK_URL, json: { text: "Blocked in $HARNESS_REPO: $HARNESS_REASON" } }
      auto: true
```

In short:

- an action is a shell command (`run`), an HTTP request `harness` makes (`http`), or a headless agent run under the
  worker contract (`agent`) ([action kinds](lifecycle.md#action-kinds));
- actions run in the work's checkout, in declaration order, and the first failure stops the event with exit 2 unless
  the action says `on_failure: continue`, in which case the remaining actions run and the event still exits 2;
- values reach an action only as environment variables, never substituted into `run`, so a task title or a branch
  name cannot inject a command; secrets for `http` come only from the variables `url_env` and `headers_env` name;
- `auto: true` runs the action at the event's automatic trigger point; every action also runs when a person or a
  coordinating agent fires the event with `harness lifecycle run <event>`;
- lifecycle actions never run inside a Ralph worker;
- the binary ships no actions: the repository or its [preset](presets.md#lifecycle-actions-in-a-preset) declares them.

An action runs with the permissions of the process that fired it, outside any agent's permission checks. Review
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
| `session_start.context` | boolean | `true` | Add the managed-worktree context line |
| `session_start.update_notice` | boolean | `true` | Add the update notice line |
| `raw` | map of event name to list | `{}` | Hooks `harness` does not model, rendered as written |
| `raw.<Event>[].matcher` | string | none | The agent's matcher for the event (for example a tool-name pattern) |
| `raw.<Event>[].run` | command | required | The hook command |
| `attention` | list | `[]` | Actions run when the agent waits for a person |
| `turn_end` | list | `[]` | Actions run when the agent finishes a turn |
| `attention[].name`, `turn_end[].name` | string | required | Unique within the list |
| `attention[].run`, `turn_end[].run` | command | one of `run`, `http` | A shell command ([action kinds](lifecycle.md#action-kinds)) |
| `attention[].http`, `turn_end[].http` | map | one of `run`, `http` | An HTTP request ([action kinds](lifecycle.md#http)) |
| `lifecycle` | map of event name to list | `{}` | Lifecycle actions; events `start`, `verified`, `completed`, `deliver`, `landed`, `blocked` |
| `lifecycle.<event>[].name` | string | required | Unique within the event; `--action` and the log refer to it |
| `lifecycle.<event>[].run`, `.http`, `.agent` | command or map | exactly one | The action's kind ([action kinds](lifecycle.md#action-kinds)) |
| `lifecycle.<event>[].auto` | boolean | `false` | Also run at the event's automatic trigger point |
| `lifecycle.<event>[].scope` | list of `task`, `run`, `change` | every scope of the event | Scopes the action applies to |
| `lifecycle.<event>[].on_failure` | `stop` \| `continue` | `stop` | `stop`: the first failure stops the event with exit 2. `continue`: the remaining actions run, and the event still exits 2 |

Event names in `raw` are the agents' native names, for example `SessionStart`, `PreToolUse`, `PostToolUse`, `Stop`.
An event an agent does not support is not rendered for that agent.

`harness hook` exit codes:

| Exit code | Meaning |
| --- | --- |
| 0 | Nothing to do, every command passed, or the event is `attention` or `turn_end`, whatever their actions returned |
| 2 | A post-edit command or validator failed; its output is on stderr for the agent |

When the pinned binary is not cached, the wrapper exits 0 and `harness hook` does not run. `HARNESS_NO_CONTEXT=1`
turns off standing-context injection only ([context](context.md#environment)).

Files read: stdin (the payload), `.harness/wrapper.yaml` (by the wrapper), `.harness/hooks.yaml`,
`.harness/config.yaml`, `session.json` and `hook-checks.json` in the checkout's state directory, the read and edited
files, the skills and files `context` entries name, `skills-lock.json`, the cache directory (`$HARNESS_HOME`, default
`$XDG_CACHE_HOME/harness`, otherwise `~/.cache/harness`) for the binary and the update check.
Files written: `hook-checks.json`, `context/<session-id>.json` in the state directory, whatever the declared commands
write (for example a formatter fixing the file).

## Examples

### A Node API

```yaml
# .harness/hooks.yaml
schema: 1
post_edit:
  - paths: [src/**/*.ts, test/**/*.ts]
    exclude: [src/migrations/**]
    run: npx eslint --fix {file}
validators: { from: verify.validators }
session_start: { context: true, update_notice: true }
attention:
  - name: ping-me
    http:
      url_env: CHAT_WEBHOOK_URL
      json: { text: "$HARNESS_AGENT needs you in $HARNESS_REPO ($HARNESS_BRANCH): $HARNESS_MESSAGE" }
raw:
  SessionStart: [{ run: bd prime --hook-json }]
```

`CHAT_WEBHOOK_URL` is set in each developer's own shell profile; a developer who does not set it gets an `attention`
failure on stderr, and the session continues.

### A desktop notification when the agent finishes

```yaml
schema: 1
turn_end:
  - name: done
    run: notify-send --app-name=agent "Turn finished" "$HARNESS_AGENT in $HARNESS_WORKTREE"
```

### A Terraform repository

```yaml
schema: 1
post_edit:
  - { paths: ["**/*.tf"], run: terraform fmt {file} }
validators:
  - { when: [modules/**/variables.tf], run: ./scripts/check-variable-docs.sh }
session_start: { context: true, update_notice: false }
raw:
  PreToolUse: [{ matcher: Edit|Write, run: ./scripts/protect-state-files.sh }]
```

### A Go repository

```yaml
schema: 1
post_edit:
  - { paths: ["**/*.go"], exclude: ["**/*_gen.go"], run: gofmt -w {file} }
validators: { from: verify.validators }
session_start: { context: true, update_notice: true }
raw: {}
```

## Guidelines

- Do keep post-edit commands fast and single-file. They run after every edit; slow checks belong in a
  [verify lane](verify.md).
- Do make post-edit commands fix what they can (`--fix`, `-w`) so the agent sees only what needs judgement.
- Do use `raw` for path guards (`PreToolUse`) and keep them small and declared in this file, not hidden in scripts
  wired by hand.
- Don't use hooks to allow or deny commands; put that in the [policy](policy.md).
- Don't depend on hooks for correctness: they skip on any machine where the pinned binary is not cached yet.
  `harness verify` is the proof.
- Do keep lifecycle actions short and readable in the file itself; a script an action calls is part of the action
  and is reviewed with it.
- Don't put secrets in lifecycle or notification actions: they inherit the environment of the process that fires
  them. An `http` action reads its URL and headers from the variables it names.
- Do keep notification actions fast. They run inside the agent's hook call; an `http` action stops at its `timeout`.
- Don't send `HARNESS_MESSAGE` anywhere it would be read as an instruction: it is the agent's text, shaped by
  everything the agent read.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| `harness: v0.9.0 not downloaded, hook skipped; run: ./harnessw version` in the session | The pinned version is not cached on this machine, and hooks never download | Run `./harnessw version` once in the repository |
| Hooks never run | `modules.hooks` is off, the folder is not trusted by the agent, or the agent's hook feature is disabled | Turn the module on and render; trust the folder |
| Lint runs on every edit even when nothing changed | The command rewrites the file each time | Make the command idempotent |
| A Codex session ignores hooks | Codex reads project hooks only when its hooks feature is enabled; `harness` renders that key in `.codex/config.toml` | `harness render`, then `harness doctor` |
| A raw hook stopped running | It was removed from `hooks.yaml`, or its command is not executable | Restore the entry; `chmod +x` the script |
| A lifecycle action never runs on its own | It has no `auto: true`, or the event's guard did not let it fire | `harness lifecycle list`; read the [lifecycle log](lifecycle.md#the-lifecycle-log) |
| The session starts with `harness v0.9.0 is not cached on this machine; run ./harnessw version once to fetch it.` | The pinned release is not cached; the session has no standing context and no notifications | Run `./harnessw version` once |
| No `attention` notification from Codex | Codex has no event for an agent waiting for a person; `doctor` notes it | Use `turn_end`, which Codex reports through `notify` |
| No `turn_end` from Codex; `doctor` notes a `notify` program of the repository's own | `.codex/config.toml` already sets `notify` to another program | Move that program into a `turn_end` `run` action, remove the key, then `harness render` |
| No notification on one machine | The variable in `url_env` is not set there, or the pinned release is not cached there | Set the variable in that shell; run `./harnessw version` once |
| A notification arrives for every turn of a worker run | It cannot: notification hooks do not run in worker sessions | Declare `blocked` and `completed` lifecycle actions for unattended runs |

## Coexistence

Hook entries `harness` did not render (for example one a plugin or a person added to `.claude/settings.json`) are
preserved. `settings.local.json` hooks are never touched. See [compatibility](compatibility.md).

After [`harness remove --eject`](remove.md#eject), raw entries stay in the native files as the repository's own hooks
and keep working. The harness entries, including notifications and standing-context injection, are removed, and listed
in the output, because they call a wrapper that eject deletes. Static context entries stay in `AGENTS.md`.

The reasoning for hooks that give feedback but never decide permissions is in
[ADR 0011](../adrs/0011-safety-invariants.md); for action kinds and notification events, in
[ADR 0019](../adrs/0019-action-kinds.md).
