# Profile: `.harness/config.yaml`

## Objective

**Problem.** Every repository has facts only it knows: how to prepare a fresh checkout, how to lint a file and build,
which tests show a change works, which agents the team uses. Agents and the developers directing them
need those facts at every step. When the facts live in someone's memory, in prose an agent may misread, or in a copy of
another repository's setup, each agent guesses, and the same step runs differently from one session to the next.

**Goal.** Every fact a repository's agent workflow depends on is written once in that repository and reviewed like code,
and every agent and tool working there uses the same answer.

## Overview

The profile tells `harness` what a repository is: which [plugins](plugins.md) describe the programs it uses, which
modules it uses, how to bootstrap a checkout, how to lint and build, which verification lanes (named families of test
commands) exist, which agents it uses and what an unattended [worker](ralph.md) may write, and the standing context
agents receive. It is seeded and then repository-owned. `scaffold` seeds it once, with the plugins whose detect rule
matches and their entries. From then on people and agents edit it, `harness plugin apply` adds a plugin and its entries
to it, and `harness render` reads it.

Examples on this page run commands as `./harnessw <command>`, through the repository's [wrapper](wrapper.md); with a
global install, `harness <command>` is equivalent.

## When to edit it

| You want to | Edit |
| --- | --- |
| Use a program's plugin, or stop using it | `plugins` ([plugins](plugins.md)); `./harnessw plugin apply <name>` adds one with its entries |
| Use another version control system's plugin | `vcs` |
| Turn a feature on or off | `modules` |
| Change what a new worktree runs before use | `bootstrap` |
| Change how a file is linted or the project is built | `commands` |
| Add, rename or require a test lane | `verify.lanes` |
| Run a repository check when certain files change | `verify.validators` |
| Add Codex or another agent, make an agent a worker-only agent, give an agent a fetched harness plugin, or let a Codex worker write one more directory | `agents` |
| Move where state or managed worktrees live | `state.dir`, `worktrees.dir` |
| Give agents a directive or a skill without anyone invoking it: always, on certain files, or on certain words | `context` |
| Let the managed blocks in `AGENTS.md` take more or fewer lines | `blocks.agents_md_budget` |
| Add the starting commands, lanes and post-edit hooks of a stack | nothing by hand: run `./harnessw plugin apply <name>` ([plugins](plugins.md)) |

Run `./harnessw render` after every edit, and `./harnessw doctor` to check globs and commands.

## Conventions

- **Globs** are matched against paths relative to the repository root. `*` matches within one path segment, `**`
  across segments, `{a,b}` either alternative. Example: `src/**/*.{ts,js}`.
- **Commands** are strings run with `sh -c` from the root of the checkout they apply to (the primary checkout or a
  worktree).
- **Placeholders**: `{file}` is replaced by one repository-relative path, `{pattern}` by one lane pattern; `harness`
  shell-quotes each value. These placeholders in profile and hook commands are the only substitutions `harness`
  performs. Lifecycle actions get values only through environment variables.
- **Unknown keys** are an error, so a typo never silently disables something.

## Reference

### Top level

| Field | Type | Default | Meaning |
| --- | --- | --- | --- |
| `schema` | integer | required | File format version. `1` for this release line |
| `name` | string | the repository directory name | Repository name, used in messages |
| `plugins` | list | required | The [plugins](#plugins) the repository uses |
| `vcs` | plugin name | required | The plugin that fills the version-control contract; see [`plugins`](#plugins) |
| `modules` | map | every module off | Which modules are on |
| `bootstrap` | list of commands | `[]` | Run in order in a new worktree, and once in each checkout a [worker](ralph.md#the-worker-contract) runs in |
| `commands` | map | | Lint and build commands |
| `verify` | map | | Verification paths, lanes and validators |
| `agents` | map | | Agents rendered for this repository, their roles, their harness plugins, and what a Codex worker may write |
| `context` | map | | [Standing context](#context): directives and skills injected by declared triggers |
| `blocks` | map | | Limits for the managed blocks `harness` writes |
| `state` | map | | Where clone-local state lives; see [State paths](#state-paths) |
| `worktrees` | map | | Where managed worktrees live; see [State paths](#state-paths) |

### `plugins`

The [plugins](plugins.md) the repository uses: their renders and floor rules are rendered on every `harness render`,
and their operations answer the binary's questions. An entry is a builtin plugin's name, or a
[fetched plugin](plugins.md#fetched-plugins) as a map:

| Field | Type | Meaning |
| --- | --- | --- |
| `name` | string | The plugin's name; never the name of a builtin plugin |
| `source` | string | `github:<owner>/<repo>`, or an HTTPS base URL laid out like the [release source](wrapper.md#release-source) |
| `version` | string | The version to download, `<source>/download/<version>/<name>.tar.gz` |
| `sha256` | string | The archive's sha256, 64 hexadecimal characters, checked on download and on every load |

```yaml
plugins:
  - git
  - claude
  - codex
  - node
  - name: just
    source: github:acme/harness-plugins
    version: v1.2.0
    sha256: d2f0d562418c7d176d0393d8694cc6f6f569f2dff3f7ede3788a2075d3ff5c3f
vcs: git
```

`vcs` names the plugin that fills the [version-control contract](plugins.md#the-version-control-contract): `git`, which
`scaffold` writes, or a fetched plugin. It must be in `plugins`, and so must every agent's [harness plugin](#agents);
otherwise the profile fails validation with exit 1. Deleting a name from `plugins` and rendering removes that plugin's
renders and its floor; its applied entries and templates stay ([plugins](plugins.md#after-applying)).

### `modules`

| Field | Type | Effect when `true` |
| --- | --- | --- |
| `hooks` | boolean | [hooks](hooks.md) are rendered into agent files |
| `verify` | boolean | [`harness verify`](verify.md), its `verify` block, and the receipt that `completed` with scope `task` requires ([tasks](tasks.md)) |
| `memory` | boolean | [memory](memory.md) skeleton and `memory` block |

An omitted module is off.

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

### `agents`

A map from agent name to its settings. Only declared agents are rendered. Each agent is served by a
[harness plugin](plugins.md#the-harness-contract): the release ships `claude` (Claude Code) and `codex` (Codex CLI),
and a fetched plugin can serve another agent.

| Field | Type | Default | Meaning |
| --- | --- | --- | --- |
| `role` | `interactive` \| `worker` | | `interactive`: people use the agent in this repository; its native files carry the shared policy. `worker`: the agent runs here only as a worker, started by [`harness worker`](ralph.md#the-worker-contract); its native files also carry `worker.deny`. See [agents](agents.md#roles) |
| `plugin` | plugin name | the agent's name | The harness plugin that serves the agent; it must be in `plugins` and fill the harness contract. Example: `reviewer: { role: worker, plugin: acme-review }` |
| `writable_roots` | list of paths | `[]` | Only for an agent whose harness plugin opens sandbox roots, such as `codex`. Directories a Codex worker may write besides its task checkout and the clone's state directory, relative to the root of the primary checkout. Example: `[.beads]`, so a worker can update the clone's Beads database; the [`beads` plugin](plugins.md#beads) adds it. No other directory is writable for a Codex worker |

### `context`

Standing context is guidance `harness` puts into agent sessions when a declared trigger fires, so nobody has to invoke a
directive or a skill by name. The [context](context.md) page covers behaviour per agent, the hook events used, and more
examples.

```yaml
context:
  always:                                   # every session start, and again after compaction
    - skill: coding-guidelines
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

Sources are repository files and skills committed under `.agents/skills/`; nothing is fetched when a hook runs. A
[worker](ralph.md#the-worker-contract) receives the entries as they are at its base commit. Hook delivery
needs `modules.hooks`; `static` entries do not. Preview what a session would receive with
[`harness context show`](context.md#harness-context-show). `HARNESS_NO_CONTEXT=1` turns injection off for one process
tree.

### `blocks`

| Field | Type | Default | Meaning |
| --- | --- | --- | --- |
| `agents_md_budget` | integer | `40` | Most lines the managed blocks in `AGENTS.md` may take together, the `context` block excepted. Each of those blocks also stays within 8 lines. `render` still writes blocks that exceed it; `doctor` notes the overrun. See [the `AGENTS.md` budget](render.md#the-agentsmd-budget) |

### State paths

State is clone-local, gitignored and disposable, and lives outside `.harness/`. Two keys say where, each relative to
the repository root. Each path is written down as a convention in a managed block of `AGENTS.md` and a managed block of
`.gitignore` ([block ids](render.md#block-ids)), so people, agents and other tools find it, with or without `harness`.

```yaml
state:
  dir: .work                      # receipts, sessions, locks, lifecycle log, hook cache
worktrees:
  dir: .work/worktrees            # each managed worktree at <dir>/<name>
```

| Field | Type | Default | Holds | Blocks it produces |
| --- | --- | --- | --- | --- |
| `state.dir` | path | `.work` | in the primary checkout: `lifecycle.jsonl` and the locks shared by every worktree; in each checkout: `session.json`, `verified.json` (the [receipt](verify.md) of a passing `harness verify`), `hook-checks.json`, and in a checkout a worker runs in, the record that `bootstrap` ran, `last-worker-output` and `last-attempt.patch` | `.gitignore` `state` (`/.work/`); named in the `AGENTS.md` `layout` block |
| `worktrees.dir` | path | `.work/worktrees` | each managed worktree, at `<dir>/<name>` | `AGENTS.md` `worktrees` (where worktrees are created, their `work/<name>` branch, that they start from committed code); `.gitignore` `worktrees` (`/.work/worktrees/`) |

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
plugins: [git, claude, codex, node, beads, ralph-tui]
vcs: git
modules: { hooks: true, verify: true, memory: true }
bootstrap: [npm ci]
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
agents:
  claude: { role: interactive }
  codex: { role: worker, writable_roots: [.beads] }
context:
  always:
    - skill: coding-guidelines
  when:
    - paths: ["src/migrations/**", "**/*.sql"]
      skill: sql-quality-check
```

The team tracks tasks with Beads and runs ralph-tui, so a Codex worker may write the clone's `.beads/` database.

### A Terraform repository

```yaml
schema: 1
name: acme-infra
plugins: [git, claude, terraform]
vcs: git
modules: { hooks: true, verify: true, memory: true }
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
plugins: [git, claude, codex, go]
vcs: git
modules: { hooks: true, verify: true, memory: true }
bootstrap: [go mod download]
commands:
  lint: gofmt -l {file} | (! grep .)
  build: go build ./...
verify:
  app_paths: ["**/*.go", go.mod, go.sum]
  lint_paths: ["**/*.go"]
  lanes:
    test: { select: go test {pattern}, full: go test ./... }
    vet: { select: go vet {pattern}, full: go vet ./... }
agents: { claude: { role: interactive }, codex: { role: interactive } }
```

Patterns for the Go lanes are package paths:
`./harnessw verify --lane test=./internal/billing/... --lane vet=./internal/billing/...`.

## Guidelines

- Do make `select` commands fail when they select zero tests, if the test runner allows it; a lane that "passes" with
  no tests proves nothing.
- Do keep `required_when` narrow and meaningful: the files whose change the lane is the only real evidence for.
- Do use the repository's own task runner in commands when it has one (`npm run build`, `make test`), so people and
  agents run the same thing.
- Do keep the state paths out of what your linters, test runners and compilers scan (exclude `.work/**`, or the
  `state.dir` you chose): worktrees nested there hold full copies of the source.
- Do choose the state paths before work starts: moving one later leaves existing worktrees, sessions and receipts under
  the old path.
- Don't put secrets or machine-specific paths in commands; the profile is committed and runs on every machine.
- Don't use `bootstrap` for slow, optional setup; every new worktree runs it, and so does every checkout a worker runs
  in.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| `unknown key` error | A typo or a field from a newer schema | Fix the key; check `schema` |
| `vcs` or an agent's `plugin` names a plugin not in `plugins` | The plugin was deleted from `plugins`, or never added | Add it back with `./harnessw plugin apply <name>`, or name a plugin in use |
| `render` exits 3 naming `./harnessw plugin fetch` | A fetched plugin in `plugins` is not in this machine's cache | Run `./harnessw plugin fetch` |
| `doctor` says a program is not on PATH | A command, lane, validator or action names a program this machine lacks | Install the program, or fix the command in `.harness/config.yaml` or `.harness/hooks.yaml` |
| `doctor` says a glob matches nothing | Wrong path or quoting | YAML needs quotes around globs that start with `*` |
| A lane runs with the literal text `{pattern}` | The command is in `full`, which takes no placeholder | Use `{pattern}` only in `select` |
| `state path must not be inside .harness/ or .agents/` | A state path points into a directory that holds committed files only | Choose a path outside both, such as the default `.work/…` |
| `doctor` notes that always-on context exceeds `context.budget` | Too many or too long `always` entries for one role | Move entries to `when` triggers, use `inject: pointer`, or narrow them with `for` |
