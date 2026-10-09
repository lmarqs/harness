# Hooks: `.agents/hooks.yaml`

The hooks file declares, once and agent-neutrally, two kinds of automation:

- **agent hooks**, which run when an agent starts a session or edits a file: format or lint the edited file, run a
  repository validator, add a line of session context. `harness render` writes them into each agent's native hook
  configuration, and `harness hook` runs them when the agent fires an event. Agent hooks give feedback; they never
  decide permissions.
- **lifecycle actions**, under the `lifecycle` key, which run at the [development lifecycle](lifecycle.md)'s events:
  push a branch, open a pull request, clean up after a merge. `harness` runs them itself; they are not rendered into
  agent files.

## When to use it

| You want | Declare |
| --- | --- |
| The edited file formatted or linted right after every agent edit | a `post_edit` entry |
| A repository check run when certain files are edited | `validators` |
| A session in a managed worktree to know its task and tracker | `session_start.context: true` |
| An update notice in agent sessions | `session_start.update_notice: true` |
| A hook `harness` does not model, such as a `PreToolUse` path guard or a `Stop` hook | a `raw` entry |
| Something to happen when work starts, is verified, completed, delivered or lands, such as a push or a pull request | a `lifecycle` action |

Agent hooks need `modules.hooks: true` in the [profile](profile.md). Lifecycle actions do not: the binary fires them
from its own commands.

## How it works

### Rendering

For each declared agent, `render` writes two kinds of hook entries:

| Entry | Events | Command |
| --- | --- | --- |
| harness entries | `SessionStart`; `PostToolUse` (Claude Code: matcher `Edit\|Write\|MultiEdit`; Codex: every tool, filtered by `harness hook`) | the guarded `harness hook` command below |
| raw entries | the event named in `raw`, with its matcher | the `run` command as written |

The harness entry's command is:

```sh
sh -c 'command -v harness >/dev/null 2>&1 && exec harness hook || { echo "harness: not installed, hook skipped" >&2; exit 0; }'
```

### The missing-binary guard

When `harness` is not on the agent's PATH, the guard prints `harness: not installed, hook skipped` on stderr and exits
0. The session continues normally, without lint-on-edit or session context. A teammate who never installs `harness`
loses only that feedback.

Raw entries are rendered as native hook entries running their own commands, not through `harness hook`, so they keep
running without the binary, exactly as they did before adoption. The manifest records them as owned, so `render`
updates them when `hooks.yaml` changes and `render --check` reports a hand edit.

### Payload normalisation

Claude Code and Codex send different JSON payloads on stdin. `harness hook` reads either and reduces it to one event:

| Normalised field | Taken from |
| --- | --- |
| event | the payload's hook event name: session start or post tool use |
| tool | the tool name. Edit tools: Claude Code's `Edit`, `Write`, `MultiEdit`; Codex's `apply_patch` |
| edited files | the tool input's file path, made relative to the checkout; for a patch tool that names no single file, the checkout's uncommitted changes against `HEAD` |
| checkout | the payload's working directory, resolved to the primary checkout or the managed worktree it is in |

Files outside the checkout are ignored.

### Session start

| Setting | Output, as additional context for the agent |
| --- | --- |
| `context: true` | In a [managed worktree](worktrees.md), one line: the tracker, the task, and the command to verify. Outside managed worktrees, nothing |
| `update_notice: true` | One line when a newer `harness` release exists, from the [daily cached check](installation.md#update-notice) |

### Post edit

For each edited file:

1. Every `post_edit` entry whose `paths` match and whose `exclude` does not runs its `run` command with `{file}`
   replaced by the file's repository-relative path, shell-quoted. `{file}` and `{pattern}` in profile and hook
   commands are the only substitutions `harness` performs; lifecycle actions get values only through environment
   variables.
2. A content cache in `.work/hook-checks.json` (per checkout) records a hash of each file after a successful run;
   an edit that leaves the content identical to the cached hash skips the command.
3. Every validator whose `when` matches any edited file runs once.

If a command or validator fails, `harness hook` exits 2 with the command's output on stderr. Claude Code shows that
output to the model as feedback, so the agent fixes the problem in its next step. The edit itself is never undone, and
no tool call is blocked.

### Lifecycle actions

The `lifecycle` key maps each event to an ordered list of actions. The events are `start`, `verified`, `completed`,
`deliver` and `landed`; [lifecycle](lifecycle.md) is the full reference: trigger points, scopes, guards, the
environment variables actions receive, and the log.

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
```

In short:

- an action runs with `sh -c` in the work's checkout, in declaration order, and the first failure stops the event
  with exit 2 unless the action says `on_failure: continue`, in which case the remaining actions run and the event
  still exits 2;
- values reach an action only as `HARNESS_*` environment variables, never substituted into `run`, so a task title or a
  branch name cannot inject a command;
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
| `lifecycle` | map of event name to list | `{}` | Lifecycle actions; events `start`, `verified`, `completed`, `deliver`, `landed` |
| `lifecycle.<event>[].name` | string | required | Unique within the event; `--action` and the log refer to it |
| `lifecycle.<event>[].run` | command | required | Run with `sh -c` in the work's checkout |
| `lifecycle.<event>[].auto` | boolean | `false` | Also run at the event's automatic trigger point |
| `lifecycle.<event>[].scope` | list of `task`, `run`, `change` | every scope of the event | Scopes the action applies to |
| `lifecycle.<event>[].on_failure` | `stop` \| `continue` | `stop` | `stop`: the first failure stops the event with exit 2. `continue`: the remaining actions run, and the event still exits 2 |

Event names in `raw` are the agents' native names, for example `SessionStart`, `PreToolUse`, `PostToolUse`, `Stop`.
An event an agent does not support is not rendered for that agent.

`harness hook` exit codes:

| Exit code | Meaning |
| --- | --- |
| 0 | Nothing to do, or every command passed |
| 2 | A post-edit command or validator failed; its output is on stderr for the agent |

Files read: stdin (the payload), `.agents/hooks.yaml`, `.agents/harness.yaml`, `.work/session.json`,
`.work/hook-checks.json`, the edited files, `~/.cache/harness/` (update check).
Files written: `.work/hook-checks.json`, whatever the declared commands write (for example a formatter fixing the
file).

## Examples

### A Node API

```yaml
# .agents/hooks.yaml
schema: 1
post_edit:
  - paths: [src/**/*.ts, test/**/*.ts]
    exclude: [src/migrations/**]
    run: npx eslint --fix {file}
validators: { from: verify.validators }
session_start: { context: true, update_notice: true }
raw:
  SessionStart: [{ run: bd prime --hook-json }]
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
- Don't depend on hooks for correctness: they do not run for teammates without the binary. `harness verify` is the
  proof.
- Do keep lifecycle actions short and readable in the file itself; a script an action calls is part of the action
  and is reviewed with it.
- Don't put secrets in lifecycle actions: they inherit the environment of the process that fires them.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| `harness: not installed, hook skipped` in the session | The agent's PATH has no `harness` | Install it, or start the agent from a shell where it resolves |
| Hooks never run | `modules.hooks` is off, the folder is not trusted by the agent, or the agent's hook feature is disabled | Turn the module on and render; trust the folder |
| Lint runs on every edit even when nothing changed | The command rewrites the file each time | Make the command idempotent |
| A Codex session ignores hooks | Codex reads project hooks only when its hooks feature is enabled; `harness` renders that key in `.codex/config.toml` | `harness render`, then `harness doctor` |
| A raw hook stopped running | It was removed from `hooks.yaml`, or its command is not executable | Restore the entry; `chmod +x` the script |
| A lifecycle action never runs on its own | It has no `auto: true`, or the event's guard did not let it fire | `harness lifecycle list`; read `.work/lifecycle.jsonl` |

## Coexistence

Hook entries `harness` did not render (for example one a plugin or a person added to `.claude/settings.json`) are
preserved. `settings.local.json` hooks are never touched. See [compatibility](compatibility.md).
