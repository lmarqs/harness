# Profile: `.agents/harness.yaml`

The profile tells `harness` what a repository is: which modules it uses, how to bootstrap a checkout, how to lint and
build, which verification lanes exist, which task tracker and agents it uses, Ralph defaults and skill sources. It is
repository-owned: `scaffold` seeds it once from a [stack preset](presets.md#stack-presets), and from then on people and
agents edit it and `harness render` reads it.

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

Run `harness render` after every edit, and `harness doctor` to check globs and commands.

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
| `extends` | string | none | A preset source, optionally pinned with `@<ref>`; see [presets](presets.md#extends) |
| `modules` | map | every module off | Which modules are on |
| `front_door` | `mise` \| `binary` | set by `scaffold` | Whether mise wrappers are rendered |
| `bootstrap` | list of commands | `[]` | Run in order in a new worktree, and once in each Ralph task worktree |
| `commands` | map | | Lint and build commands |
| `verify` | map | | Verification paths, lanes and validators |
| `tracker` | map | `default: none` | Task tracker defaults |
| `agents` | map | | Agents rendered for this repository and their roles |
| `ralph` | map | | Ralph defaults |
| `skills` | map | | Skill sources |

### `modules`

| Field | Type | Effect when `true` |
| --- | --- | --- |
| `hooks` | boolean | [hooks](hooks.md) are rendered into agent files |
| `verify` | boolean | [`harness verify`](verify.md) and its wrapper |
| `tasks` | boolean | [`harness task`](tasks.md) and its wrapper |
| `ralph` | boolean | [`harness ralph`](ralph.md) and its wrappers |
| `memory` | boolean | [memory](memory.md) skeleton and `memory-index` block |
| `skills` | boolean | [`harness skills sync`](skills.md) and the pending check |

An omitted module is off.

### `front_door`

| Value | Effect |
| --- | --- |
| `mise` | mise task wrappers are rendered; the `commands` block shows `mise run …` |
| `binary` | no wrappers; the `commands` block shows `harness …` |

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

`ralph.agents.<name>`:

| Field | Type | Meaning |
| --- | --- | --- |
| `model` | string | Full model id passed to the agent |
| `effort` | string | Reasoning effort level passed to the agent (`--effort` for Claude, `model_reasoning_effort` for Codex) |

### `skills`

| Field | Type | Meaning |
| --- | --- | --- |
| `sources` | list of strings | Sources for the `skills` CLI, as it accepts them (`owner/repo`, a git URL). See [skills](skills.md) |

## Examples

### A Node API

```yaml
# .agents/harness.yaml
schema: 1
name: acme-api
extends: github:acme/agent-presets//harness
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

Patterns for the Go lanes are package paths: `harness verify --lane test=./internal/billing/... --lane vet=./internal/billing/...`.

## Guidelines

- Do make `select` commands fail when they select zero tests, if the test runner allows it; a lane that "passes" with
  no tests proves nothing.
- Do keep `required_when` narrow and meaningful: the files whose change the lane is the only real evidence for.
- Do use mise tasks in commands when the repository has them, so people and agents run the same thing.
- Don't put secrets or machine-specific paths in commands; the profile is committed and runs on every machine.
- Don't use `bootstrap` for slow, optional setup; every new worktree and every Ralph task worktree runs it.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| `unknown key` error | A typo or a field from a newer schema | Fix the key; check `schema` |
| `doctor` says a glob matches nothing | Wrong path or quoting | YAML needs quotes around globs that start with `*` |
| A lane runs with the literal text `{pattern}` | The command is in `full`, which takes no placeholder | Use `{pattern}` only in `select` |
| Ralph refuses `--model` | `ralph.config: inherit` and the inherited agent has no model to replace | Set a model on that agent in the inherited config, or use `managed` |
