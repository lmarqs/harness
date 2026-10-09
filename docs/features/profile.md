# Profile: `.harness/config.yaml`

The profile tells `harness` what a repository is: which modules it uses, how to bootstrap a checkout, how to lint and
build, which verification lanes exist, which task tracker and agents it uses, Ralph defaults, skill sources, and the
standing context agents receive. It is seeded and then repository-owned: `scaffold` seeds it once, with the entries
of the [presets](presets.md) of the stacks it detects, and from then on people and agents edit it,
`harness preset apply` adds a stack's entries to it, and `harness render` reads it.

Examples on this page run commands as `./harnessw <command>`, through the repository's [wrapper](wrapper.md); with a
global install, `harness <command>` is equivalent.

## When to edit it

| You want to | Edit |
| --- | --- |
| Turn a feature on or off | `modules` |
| Change what a new worktree runs before use | `bootstrap` |
| Change how a file is linted or the project is built | `commands` |
| Add, rename or require a test lane | `verify.lanes` |
| Run a repository check when certain files change | `verify.validators` |
| Use Beads or PRD files by default | `tracker` |
| Add Codex, or make an agent a Ralph-only worker | `agents` |
| Change the Ralph model, effort or iteration budget | `ralph` |
| Install shared skills | `skills.sources` |
| Move where state, managed worktrees, PRD files or Ralph worktrees live | `state.dir`, `worktrees.dir`, `tasks.prd_dir`, `ralph.worktrees_dir` |
| Give agents a directive or a skill without anyone invoking it: always, on certain files, or on certain words | `context` |
| Let the managed blocks in `AGENTS.md` take more or fewer lines | `blocks.agents_md_budget` |
| Add the starting commands, lanes and module switches of a stack | nothing by hand: run `./harnessw preset apply <name>` ([presets](presets.md)) |

Run `./harnessw render` after every edit, and `./harnessw doctor` to check globs and commands.

## Conventions

- **Globs** are matched against paths relative to the repository root. `*` matches within one path segment, `**`
  across segments, `{a,b}` either alternative. Example: `src/**/*.{ts,js}`.
- **Commands** are strings run with `sh -c` from the root of the checkout they apply to (the primary checkout or a
  worktree).
- **Placeholders**: `{file}` is replaced by one repository-relative path, `{pattern}` by one lane pattern; `harness`
  shell-quotes each value. These placeholders in profile and hook commands are the only substitutions `harness`
  performs; lifecycle actions get values only through environment variables.
- **Unknown keys** are an error, so a typo never silently disables something.

## Reference

### Top level

| Field | Type | Default | Meaning |
| --- | --- | --- | --- |
| `schema` | integer | required | File format version. `1` for this release line |
| `name` | string | the repository directory name | Repository name, used in messages and as the default Beads prefix |
| `modules` | map | every module off | Which modules are on |
| `front_door` | `mise` \| `binary` | set by `scaffold` | Whether task shims are rendered |
| `bootstrap` | list of commands | `[]` | Run in order in a new worktree, and once in each Ralph task worktree |
| `commands` | map | | Lint and build commands |
| `verify` | map | | Verification paths, lanes and validators |
| `tracker` | map | `default: none` | Task tracker defaults |
| `tasks` | map | | Where PRD files live; see [State paths](#state-paths) |
| `agents` | map | | Agents rendered for this repository and their roles |
| `ralph` | map | | Ralph defaults |
| `skills` | map | | Skill sources |
| `context` | map | | [Standing context](#context): directives and skills injected by declared triggers |
| `blocks` | map | | Limits for the managed blocks `harness` writes |
| `state` | map | | Where clone-local state lives; see [State paths](#state-paths) |
| `worktrees` | map | | Where managed worktrees live; see [State paths](#state-paths) |

### `modules`

| Field | Type | Effect when `true` |
| --- | --- | --- |
| `hooks` | boolean | [hooks](hooks.md) are rendered into agent files |
| `verify` | boolean | [`harness verify`](verify.md) and its task shim |
| `tasks` | boolean | [`harness task`](tasks.md) and its task shim |
| `ralph` | boolean | [`harness ralph`](ralph.md) and its task shims |
| `memory` | boolean | [memory](memory.md) skeleton and `memory` block |
| `skills` | boolean | [`harness skills sync`](skills.md) and the pending check |

An omitted module is off.

### `front_door`

| Value | Effect |
| --- | --- |
| `mise` | [task shims](render.md#task-shims), one-line mise tasks that exec `./harnessw <command>`, are rendered; the `commands` block shows `mise run …` |
| `binary` | no task shims; the `commands` block shows `./harnessw …` |

### `commands`

| Field | Type | Meaning |
| --- | --- | --- |
| `lint` | command with `{file}` | Lints one file. `verify` runs it for each changed file matching `verify.lint_paths` |
| `build` | command | Builds the project. `verify` runs it when the change touches `verify.app_paths`, and with `--full` |

### `verify`

| Field | Type | Meaning |
| --- | --- | --- |
| `app_paths` | list of globs | Files whose change is an application change: it triggers the build and requires lane selection |
| `lint_paths` | list of globs | Files linted with `commands.lint` when changed |
| `lanes` | map of lane name to lane | Named families of test commands |
| `validators` | list of validators | Repository checks run when matching files change |

A lane:

| Field | Type | Default | Meaning |
| --- | --- | --- | --- |
| `select` | command with `{pattern}` | required | Runs the lane for one pattern. `verify --lane <name>=<pattern>` runs it once per pattern |
| `full` | command | none | Runs the whole lane. `verify --full` runs it. A lane without `full` keeps the selection rules under `--full` |
| `required_when` | list of globs | `[]` | When a changed file matches, this lane cannot be skipped |

A validator:

| Field | Type | Meaning |
| --- | --- | --- |
| `when` | list of globs | Run when any changed file matches |
| `run` | command | The check |

Lane names are free: `unit`, `e2e`, `validate`, `plan`, `glue`. They appear in the receipt, in `--lane` and `--skip`,
and in the `commands` block. The rules that use them are on the [verify](verify.md#lane-rules) page.

### `tracker`

| Field | Type | Default | Meaning |
| --- | --- | --- | --- |
| `default` | `beads` \| `prd` \| `none` | `none` | Tracker for `harness worktree create` when `--tracker` is omitted |
| `beads_prefix` | string | `name` | Issue prefix when `harness` initialises a new Beads database |

### `agents`

A map from agent name to its settings. Only declared agents are rendered. Agents: `claude` (Claude Code) and `codex`
(Codex CLI).

| Field | Type | Meaning |
| --- | --- | --- |
| `role` | `interactive` \| `worker` | `interactive`: people use the agent in this repository; its native files carry the shared policy. `worker`: the agent runs here only as a Ralph worker; its native files also carry `worker.deny`. See [agents](agents.md#roles) |

### `ralph`

| Field | Type | Default | Meaning |
| --- | --- | --- | --- |
| `config` | `managed` \| `inherit` | `managed` | `managed`: the coordinator's ralph-tui configuration is generated from this profile. `inherit`: the repository's root `.ralph-tui/config.toml` is the base, with the worker-contract keys forced. See [ralph](ralph.md#managed-and-inherited-configuration) |
| `default_agent` | agent name | required with `modules.ralph` | Agent used when `--agent` is omitted |
| `max_iterations` | integer | required with `modules.ralph` | Iteration budget when `--iterations` is omitted. `0` is unlimited |
| `agents` | map of agent name to `{model, effort}` | | Per-agent defaults for workers |
| `worktrees_dir` | path | `.work/ralph-worktrees` | Where ralph-tui keeps the worker checkouts of a harness-launched run's parallel tasks; see [State paths](#state-paths) |

`ralph.agents.<name>`:

| Field | Type | Meaning |
| --- | --- | --- |
| `model` | string | Full model id passed to the agent |
| `effort` | string | Reasoning effort level passed to the agent (`--effort` for Claude, `model_reasoning_effort` for Codex) |

### `skills`

| Field | Type | Meaning |
| --- | --- | --- |
| `sources` | list of strings | Sources for the `skills` CLI, as it accepts them (`owner/repo`, a git URL). See [skills](skills.md) |

### `context`

Standing context is guidance `harness` puts into agent sessions when a declared trigger fires, so nobody has to invoke
a directive or a skill by name. Behaviour per agent, the hook events used, and more examples: [context](context.md).

```yaml
context:
  always:                                   # every session start, and again after compaction
    - skill: karpathy-guidelines
    - file: .agents/directives/worker-rules.md
      for: [worker]
    - file: .agents/directives/house-rules.md
      static: true                          # written into AGENTS.md; holds without the binary
  when:
    - paths: ["**/*.sql", "src/migration/**"]   # after the agent first reads or edits a matching file
      skill: sql-quality-check
    - prompt: [timezone, DST, "time zone"]       # when the person's message contains one
      skill: timezone-handling
      inject: pointer
  budget: 120
```

| Field | Type | Default | Meaning |
| --- | --- | --- | --- |
| `always` | list of entries | `[]` | Injected at every session start, and again each time the agent fires its session-start event, as Claude Code does after compacting its context |
| `when` | list of triggered entries | `[]` | Each injected at most once per session, when its trigger first fires |
| `budget` | integer | `120` | Most lines of always-on content per session and role, `static` entries included. `doctor` notes an overrun and lists each entry with its line count |

An entry:

| Field | Type | Default | Meaning |
| --- | --- | --- | --- |
| `skill` | skill name | | An installed skill, `.agents/skills/<name>/SKILL.md`, without its front matter. Exactly one of `skill` and `file` |
| `file` | path | | A file in the repository, relative to its root, outside the state directories |
| `for` | list of `interactive`, `worker` | both | The agent [roles](#agents) that receive the entry |
| `inject` | `content` \| `pointer` | `content` in `always`, `pointer` in `when` | `content`: the text itself. `pointer`: one line naming what to read, such as `Apply the timezone-handling skill now: read .agents/skills/timezone-handling/SKILL.md` |
| `static` | boolean | `false` | Only in `always`, and only with `for` covering both roles. The entry is written into the `context` block of `AGENTS.md` instead of injected by hook, so it applies without the binary and survives an eject |
| `paths` | list of globs | | Only in `when`: the trigger is the agent's first read or edit of a matching file in the session. Exactly one of `paths` and `prompt` |
| `prompt` | list of words and phrases | | Only in `when`: the trigger is a person's message that contains one, case-insensitive, as a whole word or phrase |

Sources are repository files and skills installed under `.agents/skills/`; nothing is fetched when a hook runs. A skill
listed in `skills-lock.json` is injected only while it matches its locked hash, and `doctor` reports a mismatch as a
finding. A Ralph worker receives the entries as they are at its session's base commit. Hook delivery needs
`modules.hooks`; `static` entries do not. Preview what a session would receive with
[`harness context show`](context.md#harness-context-show); `HARNESS_NO_CONTEXT=1` turns injection off for one process
tree.

### `blocks`

| Field | Type | Default | Meaning |
| --- | --- | --- | --- |
| `agents_md_budget` | integer | `40` | Most lines the managed blocks in `AGENTS.md` may take together, the `context` block excepted. Each of those blocks also stays within 8 lines. `render` still writes blocks that exceed it; `doctor` notes the overrun. See [the `AGENTS.md` budget](render.md#the-agentsmd-budget) |

### State paths

State is clone-local, gitignored and disposable, and lives outside `.harness/`. Four keys say where; each path is
relative to the repository root, and each is written down as a convention in a managed block of `AGENTS.md` and a
managed block of `.gitignore` ([block ids](render.md#block-ids)), so people, agents, ralph-tui and `bd` find it, with
or without `harness`.

```yaml
state:
  dir: .work                      # receipts, sessions, locks, lifecycle log, hook cache
worktrees:
  dir: .work/worktrees            # each managed worktree at <dir>/<name>
tasks:
  prd_dir: .work/prds
ralph:
  worktrees_dir: .work/ralph-worktrees
```

| Field | Type | Default | Holds | Blocks it produces |
| --- | --- | --- | --- | --- |
| `state.dir` | path | `.work` | in the primary checkout: `lifecycle.jsonl` and the locks shared by every worktree; in each checkout: `session.json`, `verified.json` (the receipt), `hook-checks.json` | `.gitignore` `state` (`/.work/`); named in the `AGENTS.md` `layout` block |
| `worktrees.dir` | path | `.work/worktrees` | each managed worktree, at `<dir>/<name>` | `AGENTS.md` `worktrees` (where worktrees are created, their `work/<name>` branch, that they start from committed code); `.gitignore` `worktrees` (`/.work/worktrees/`) |
| `tasks.prd_dir` | path | `.work/prds` | PRD files | `AGENTS.md` `tasks`; `.gitignore` `tasks` (`/.work/prds/`); with `modules.tasks` |
| `ralph.worktrees_dir` | path | `.work/ralph-worktrees` | ralph-tui's worker checkouts for the parallel tasks of harness-launched runs. The coordinator and sequential task worktrees live under `worktrees.dir`, as `ralph-<session-id>` and `ralph-<session-id>-<task>`; ralph-tui state stays in the coordinator's `.ralph-tui/` | `AGENTS.md` `ralph`; `.gitignore` `ralph` (`/.ralph-tui/`, `/.work/ralph-worktrees/`); with `modules.ralph` |

Two paths are not configurable, because other tools find them on their own: the Beads database stays at `.beads/`,
where `bd` looks, and ralph-tui state stays in the coordinator worktree's `.ralph-tui/`.

Rules for the paths:

- No state path may point inside `.harness/` or `.agents/`, or be either directory: those hold committed files only.
  Such a path is a validation error.
- Changing a path re-renders its blocks; state already under the old path is not moved.

## Examples

### A Node API

```yaml
# .harness/config.yaml
schema: 1
name: acme-api
modules: { hooks: true, verify: true, tasks: true, ralph: true, memory: true, skills: true }
front_door: mise
bootstrap: [mise trust, mise install, npm ci]
commands:
  lint: npx eslint --fix {file}
  build: npm run build
verify:
  app_paths: [src/**, test/**, package.json, package-lock.json, tsconfig*.json, Dockerfile]
  lint_paths: [src/**/*.{ts,js}, test/**/*.ts]
  lanes:
    unit: { select: npx vitest run {pattern}, full: npx vitest run }
    e2e:
      select: npx vitest run --config vitest.e2e.config.ts {pattern}
      full: npx vitest run --config vitest.e2e.config.ts
      required_when: [src/**/*.controller.ts, src/**/routes/**, src/**/guards/**]
  validators:
    - { when: [openapi/**, src/**/*.dto.ts], run: npm run openapi:check }
tracker: { default: beads, beads_prefix: acme-api }
agents: { claude: { role: interactive }, codex: { role: worker } }
ralph:
  config: managed
  default_agent: claude
  max_iterations: 5
  agents:
    claude: { model: claude-sonnet-4-5, effort: medium }
    codex: { model: gpt-5-codex, effort: high }
skills: { sources: [lmarqs/skills] }
context:
  always:
    - skill: karpathy-guidelines
  when:
    - paths: ["src/migrations/**", "**/*.sql"]
      skill: sql-quality-check
```

### A Terraform repository

```yaml
schema: 1
name: acme-infra
modules: { hooks: true, verify: true, tasks: false, ralph: false, memory: true, skills: false }
front_door: binary
bootstrap: [terraform -chdir=envs/staging init -backend=false]
commands:
  lint: terraform fmt -check {file}
  build: terraform -chdir=envs/staging validate
verify:
  app_paths: ["**/*.tf", "**/*.tfvars", "**/.terraform.lock.hcl"]
  lint_paths: ["**/*.tf"]
  lanes:
    validate: { select: "terraform -chdir={pattern} validate", full: "terraform -chdir=envs/staging validate" }
    plan:
      select: "terraform -chdir={pattern} plan -input=false -lock=false"
      required_when: [modules/**]
agents: { claude: { role: interactive } }
```

`plan` has no `full`: there is no meaningful "plan everything", so `--full` still asks for `--lane plan=<dir>` or a
`--skip plan=<reason>`.

### A Go repository

```yaml
schema: 1
name: acme-cli
modules: { hooks: true, verify: true, tasks: true, ralph: true, memory: true, skills: true }
front_door: mise
bootstrap: [mise trust, mise install, go mod download]
commands:
  lint: gofmt -l {file} | (! grep .)
  build: go build ./...
verify:
  app_paths: ["**/*.go", go.mod, go.sum]
  lint_paths: ["**/*.go"]
  lanes:
    test: { select: go test {pattern}, full: go test ./... }
    vet: { select: go vet {pattern}, full: go vet ./... }
tracker: { default: prd }
agents: { claude: { role: interactive }, codex: { role: interactive } }
ralph: { config: managed, default_agent: claude, max_iterations: 8, agents: { claude: { model: claude-sonnet-4-5, effort: high } } }
skills: { sources: [lmarqs/skills] }
```

Patterns for the Go lanes are package paths:
`./harnessw verify --lane test=./internal/billing/... --lane vet=./internal/billing/...`.

## Guidelines

- Do make `select` commands fail when they select zero tests, if the test runner allows it; a lane that "passes" with
  no tests proves nothing.
- Do keep `required_when` narrow and meaningful: the files whose change the lane is the only real evidence for.
- Do use mise tasks in commands when the repository has them, so people and agents run the same thing.
- Do keep the state paths out of what your linters, test runners and compilers scan (exclude `.work/**`, or the
  `state.dir` you chose): worktrees nested there hold full copies of the source.
- Do choose the state paths before work starts: moving one later leaves existing worktrees, sessions and PRD files
  under the old path.
- Don't put secrets or machine-specific paths in commands; the profile is committed and runs on every machine.
- Don't use `bootstrap` for slow, optional setup; every new worktree and every Ralph task worktree runs it.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| `unknown key` error | A typo or a field from a newer schema | Fix the key; check `schema` |
| `doctor` says a glob matches nothing | Wrong path or quoting | YAML needs quotes around globs that start with `*` |
| A lane runs with the literal text `{pattern}` | The command is in `full`, which takes no placeholder | Use `{pattern}` only in `select` |
| Ralph refuses `--model` | `ralph.config: inherit` and the inherited agent has no model to replace | Set a model on that agent in the inherited config, or use `managed` |
| `state path must not be inside .harness/ or .agents/` | A state path points into a directory that holds committed files only | Choose a path outside both, such as the default `.work/…` |
| A context skill is never injected, and `doctor` reports a hash mismatch | The installed skill differs from its entry in `skills-lock.json` | Reinstall it with `./harnessw skills sync`, or update the lock through the `skills` CLI |
| `doctor` notes that always-on context exceeds `context.budget` | Too many or too long `always` entries for one role | Move entries to `when` triggers, use `inject: pointer`, or narrow them with `for` |
