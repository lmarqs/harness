# Presets

`harness` uses two kinds of presets:

| Kind | Where it comes from | When it applies | What it does |
| --- | --- | --- | --- |
| [Stack preset](#stack-presets) | built into the binary: `go`, `node`, `terraform`, `python`, `generic` | once, at `harness scaffold` | seeds the first version of the profile and the hooks file |
| [`extends` preset](#extends) | a source an organization or a person maintains | at every `harness render`, at the commit recorded in the manifest | adds policy entries, hook declarations, lifecycle and notification actions, standing-context entries, lane defaults and block text on top of the repository's own |

The binary contains no organization-specific entry and no lifecycle action. Anything an organization wants in every
repository (its MCP read rules, its deploy denies, its tracker conventions, how work is delivered) lives in an
`extends` preset it owns.

Commands are named `harness <command>` on this page. The examples run them through the repository's wrapper,
`./harnessw <command>`, from the root of a checkout; with a global install, `harness <command>` is equivalent
([wrapper](wrapper.md)).

## Stack presets

A stack preset is chosen by detection or by `harness scaffold --preset <name>`. It writes values into
`.harness/config.yaml` and `.harness/hooks.yaml`; after that, those files are the repository's and the preset plays no
further part.

| Preset | Detected from | Seeds |
| --- | --- | --- |
| `node` | `package.json` | lint and build commands, test lanes, lint-on-edit for source files |
| `go` | `go.mod` | lint and build commands, a `go test` lane, format-on-edit for `*.go` |
| `terraform` | `*.tf` files | format command, `validate` and `plan` lanes, format-on-edit, Ralph off |
| `python` | `pyproject.toml` or `requirements.txt` | lint command, a test lane, lint-on-edit |
| `generic` | nothing recognised | no lanes, no post-edit hook |

A repository with several stacks gets them merged. See [scaffold](scaffold.md#stack-detection-and-presets).

## `extends`

### When to use it

- Several repositories share policy entries: an organization's deny list, read-only MCP tools, tracker rules.
- Several repositories share lane definitions or validators.
- Several repositories deliver the same way: push, open a pull request, trigger CI.
- An organization wants the same paragraph (for example its pull-request conventions) inside the managed blocks of
  every `AGENTS.md`.
- An organization wants the same guidance skills in every session, without anyone invoking them by hand
  ([context](context.md)).

### Sources

| Form | Example | Resolved by |
| --- | --- | --- |
| Path | `../presets/harness` | the filesystem, relative to the repository root |
| Git URL | `https://github.com/acme/agent-presets.git//harness`, `git@github.com:acme/agent-presets.git//harness@v2` | `git`, with your credentials |
| GitHub shorthand | `github:acme/agent-presets//harness`, `github:acme/agent-presets//harness@v2` | `git`, against GitHub |

`//` separates the repository from the directory inside it. `@<ref>` at the end pins a git source to a tag, a branch
or a commit; without it, the source follows its default branch.

### Resolved commits

The manifest records the commit every git preset resolved to. `render` and `render --check` reuse the recorded
commit, so a change upstream never fails a check by itself, and every clone renders the same bytes.

| Event | What happens |
| --- | --- |
| First render, or the `extends` value changed (a new source or a new `@<ref>`) | the source is resolved and its commit recorded |
| `harness render` | the recorded commit is used |
| `harness render --check` | the recorded commit is used |
| `harness render --refresh` | every preset declared without `@<ref>` is re-resolved to the current commit of its default branch, and the new commit recorded |
| `harness doctor` | a newer upstream commit for a preset without `@<ref>` is reported as a note |

To move a pinned preset, change its `@<ref>` and run `harness render`. Fetched commits are cached in the cache
directory (`$HARNESS_HOME`, by default `$XDG_CACHE_HOME/harness`, or `~/.cache/harness` when `XDG_CACHE_HOME` is
unset); a render needs the network only when the recorded commit is not cached, and fails (exit 1) when it can neither
find nor fetch it. A path source is read from disk at every
render.

### Contents of a preset

A preset directory mirrors a repository's `.harness/` directory. It holds any of these files, each in the same schema
as the repository file of the same name:

| File | May contain | Effect |
| --- | --- | --- |
| `config.yaml` | `verify.lanes`, `verify.validators`, `context` | lanes the repository does not declare are added; validators and standing-context entries are added |
| `policy.yaml` | `shared.allow`, `shared.ask`, `shared.deny`, `worker.deny` | entries are added to the repository's |
| `hooks.yaml` | `post_edit`, `validators`, `raw`, `lifecycle`, `attention`, `turn_end` | declarations and actions are added to the repository's |
| `blocks/<id>.md` | Markdown | the text is placed inside the managed block with that id, after the generated content: `blocks/lifecycle.md` adds to the `lifecycle` block of `AGENTS.md`, `blocks/policy.md` to the `policy` block |

Text for a convention block, such as `policy`, `tasks` or `worktrees`, must state facts that stay true without
`harness` and name no `harness` command, because an eject keeps it; text for a tool block, such as `commands` or
`lifecycle`, may name `./harnessw` commands. Preset text counts against the `AGENTS.md` block budget, and `doctor`
notes a budget it pushes over ([render](render.md#the-agentsmd-budget)).

A standing-context entry in a preset names a skill or a file the way a repository entry does: a skill installed under
`.agents/skills/`, or a path in the repository. Nothing is fetched from the preset when a hook runs, so an adopter
installs the skills its preset names, and `doctor` reports an entry whose skill or file is missing as a finding.

A preset's files do not carry `extends` of their own.

### Merge rules

Repository entries are applied after preset entries.

| Element | Rule |
| --- | --- |
| Policy entries | union of built-in base, module entries, preset and repository; precedence is still deny > ask > allow |
| Hook declarations | union: preset entries run alongside the repository's |
| Lifecycle and notification actions | per event, the preset's actions run first, then the repository's; a repository action with the same `name` replaces the preset's in its place |
| Standing-context entries | union; a repository entry naming the same skill or file replaces the preset's; both count against `context.budget` |
| Lanes | a lane in the repository's profile replaces a preset lane with the same name, as a whole |
| Validators | union |
| Block text | appended inside the block; the repository cannot edit it, only stop extending the preset |

Nothing in a preset can remove a built-in invariant. `harness lifecycle list` shows each action's source.

### Profile `extends` and policy `extends`

| Declared in | Brings |
| --- | --- |
| `.harness/config.yaml` | everything the preset contains |
| `.harness/policy.yaml` | only the preset's `policy.yaml` |

Use the profile form for an organization preset. Use the policy form when a repository wants another source's policy
entries without its lanes, hooks or block text.

### Example: an organization preset

```text
acme/agent-presets
└── harness/
    ├── config.yaml
    ├── policy.yaml
    ├── hooks.yaml
    └── blocks/
        └── commands.md
```

```yaml
# harness/config.yaml
schema: 1
verify:
  validators:
    # Every acme repository checks its CI workflows when they change.
    - { when: [.github/workflows/**], run: actionlint }
```

```yaml
# harness/policy.yaml
schema: 1
shared:
  allow:
    - [mise, run, ["deploy:status", "deploy:diff"]]
    # Read-only observability tools.
    - "mcp__acme_observability__search_*"
    - "mcp__acme_observability__get_*"
  deny:
    # Beads databases are clone-local in every acme repository.
    - [bd, dolt, [push, pull]]
worker:
  deny:
    - [mise, run, ["deploy:run", "deploy:rollback"]]
    - [aws]
    - [kubectl, [apply, delete, edit, patch, scale]]
```

```markdown
<!-- harness/blocks/commands.md -->
Pull requests follow the acme convention: `<type>(<scope>): <description>`.
```

Each repository then declares:

```yaml
# .harness/config.yaml
extends: github:acme/agent-presets//harness@v3
```

```sh
./harnessw render
git diff AGENTS.md .claude/settings.json .harness/manifest.json
```

## Lifecycle actions in a preset

A preset is where delivery conventions live, so the binary needs no knowledge of any hosting platform. A `github`
preset that delivers through pull requests:

```yaml
# github/hooks.yaml
schema: 1
lifecycle:
  deliver:
    - name: push
      run: git push -u origin "$HARNESS_BRANCH"
    - name: open-pr
      run: gh pr create --draft --fill --base "$HARNESS_DEFAULT_BRANCH" --head "$HARNESS_BRANCH"
      scope: [task, change]
```

A repository extends one preset, and a preset does not extend another, so an organization preset declares its whole
delivery sequence. One whose CI runs only on commits carrying a trigger adds that commit before the push:

```yaml
# acme/agent-presets/harness/hooks.yaml
schema: 1
lifecycle:
  deliver:
    - name: ci-trigger
      run: git commit --allow-empty -m "chore: trigger ci #ci"
    - name: push
      run: git push -u origin "$HARNESS_BRANCH"
    - name: open-pr
      run: gh pr create --draft --fill --base "$HARNESS_DEFAULT_BRANCH" --head "$HARNESS_BRANCH"
      scope: [task, change]
```

An empty commit changes no content, so the receipt stays valid. A repository that wants a different pull-request
command declares its own `open-pr` action, which replaces the preset's in place. See [lifecycle](lifecycle.md) for
events, scopes and guards.

Actions in a preset come in the same three kinds as a repository's: `run` (a shell command), `http` (a request
`harness` performs itself) and `agent` (a headless agent run under the worker contract). An `http` action takes its URL
and header values only from environment variables, so a preset can declare an organization's notification without
holding its secret:

```yaml
# acme/agent-presets/harness/hooks.yaml (continued)
lifecycle:
  blocked:
    - name: notify-team
      http:
        url_env: ACME_CHAT_WEBHOOK_URL
        json: { text: "$HARNESS_REPO is blocked: $HARNESS_REASON" }
      auto: true
      on_failure: continue
attention:
  - name: notify-me
    http:
      url_env: ACME_CHAT_WEBHOOK_URL
      json: { text: "$HARNESS_AGENT is waiting for you in $HARNESS_REPO: $HARNESS_MESSAGE" }
```

Each person who wants the notification sets `ACME_CHAT_WEBHOOK_URL` in their own environment; where it is unset, the
action fails and, with `on_failure: continue`, nothing else is affected.

## Guidelines

- Do keep presets small and commented: every adopter's reviewers read their effect in rendered diffs and in
  `harness lifecycle list`.
- Do pin presets with `@<ref>` where a change must be adopted deliberately, and move the ref in a reviewed change.
- Do run `harness fleet render --refresh --deliver` after changing an unpinned preset, so every adopter picks the
  change up through its own delivery ([fleet](fleet.md)).
- Do review lifecycle actions in a preset as code: they run in every adopter's clone with the permissions of whoever
  fires them.
- Do keep secrets out of presets: `http` actions read URLs and headers from `*_env` variables, and `run` actions read
  credentials from the environment of whoever fires them.
- Don't declare `auto: true` on an `agent` action in a preset unless every adopter wants that cost on every event;
  `doctor` lists automatic agent actions as notes.
- Don't put repository facts (commands, lanes specific to one codebase) in a preset.
- Don't name a path preset outside the repository unless every clone has it at the same relative location.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| `doctor` notes a newer preset commit | The preset changed upstream; the repository still renders the recorded commit | `harness render --refresh`, review the diff, commit |
| `render` fails resolving a private source in CI | The job has no read access to it and the recorded commit is not cached | Give the job credentials for the preset repository |
| A preset action does not run | The repository declares an action with the same `name` | Rename the repository's action, or remove it |
| A preset lane is ignored | The repository declares a lane with the same name | Remove or rename the repository's lane |
| A preset entry cannot be removed | Entries are additive | Stop extending that preset, or change the preset |
