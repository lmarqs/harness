# Profile: `.cook/config.yaml`

## Objective

**Problem.** Every repository has facts only it knows: how to prepare a fresh checkout, how to lint a file and build,
which tests show a change works, which programs serve the stages of its lifecycle and which workers, such as coding
agents, change its code. Workers and the developers directing them need those facts at every step. When the facts live
in someone's memory, in prose a worker may misread, or in a copy of another repository's setup, each worker guesses,
and the same step runs differently from one session to the next.

**Goal.** Every fact a repository's lifecycle depends on is written once in that repository and reviewed like code,
and every worker and tool working there uses the same answer.

## Overview

The profile tells `the-cook` what a repository is and how its lifecycle runs: which
ingredients describe the programs it uses and which one fills version control, which
stations it applied, which modules it uses, how to bootstrap a checkout, how to lint and build, which
verification lanes (named families of test commands) exist, which workers it declares, in which role, and what an
attempt may write, and the standing context workers receive. It is seeded and then repository-owned.
`scaffold` seeds it once, with the stations of a cookbook tag it applies. From then on people and workers edit it,
`the-cook station apply` adds a station's pins, stages and entries, `the-cook ingredient pin` adds or moves an
ingredient's pin, and `the-cook render` reads it. Recipes, those a station adds included, live beside it
in `.cook/recipes/`, not in the profile.

Examples on this page run commands as `./cook <command>`, through the repository's [wrapper](wrapper.md); with a
global install, `the-cook <command>` is equivalent.

## When to edit it

| You want to | Edit |
| --- | --- |
| Take up a way of working or a stack's starting entries | nothing by hand: `./cook station apply <name> --cookbook <source>@<version>` writes `ingredients`, `stations`, `vcs`, `workers` and the entries |
| Make one kind of change the same way every time | nothing here: a recipe in `.cook/recipes/<name>/`, whose `requires` names lanes of `verify.lanes` and roles of `workers` |
| Use a program, move it to a newer version, or stop using it | `ingredients`; `./cook ingredient pin <name> --cookbook <source>@<version>` adds or moves a pin; deleting the pin stops it |
| Use another version control system | `vcs`, after pinning its ingredient |
| Turn a feature on or off | `modules` |
| Change what a new workspace runs before use | `bootstrap` |
| Change how a file is linted or the project is built | `commands` |
| Add, rename or require a test lane | `verify.lanes` |
| Run a repository check when certain files change | `verify.validators` |
| Add a worker, run a worker only unattended, give a worker another ingredient, model or effort, or let it write one more directory | `workers` |
| Move where state or managed workspaces live | `state.dir`, `workspaces.dir` |
| Give workers a directive or a skill without anyone invoking it: always, on certain files, or on certain words | `context` |
| Let the managed blocks in `AGENTS.md` take more or fewer lines | `blocks.agents_md_budget` |

Run `./cook render` after every edit, and `./cook doctor` to check globs and commands.

## Conventions

- **Globs** are matched against paths relative to the repository root. `*` matches within one path segment, `**`
  across segments, `{a,b}` either alternative. Example: `src/**/*.{ts,js}`.
- **Commands** are strings run with `sh -c` from the root of the checkout they apply to (the primary checkout or a
  workspace).
- **Placeholders**: `{file}` is replaced by one repository-relative path, `{pattern}` by one lane pattern; `the-cook`
  shell-quotes each value. These placeholders in profile and hook commands are the only substitutions `the-cook`
  performs. Lifecycle actions get values only through environment variables.
- **Unknown keys** are an error, so a typo never silently disables something.

## Reference

### Top level

| Field | Type | Default | Meaning |
| --- | --- | --- | --- |
| `schema` | integer | required | File format version. `1` for this release line |
| `name` | string | the repository directory name | Repository name, used in messages |
| `ingredients` | list | `[]` | The [ingredients](#ingredients) the repository uses, each pinned |
| `stations` | list | `[]` | The [stations](#stations) the repository applied, each with its pin |
| `vcs` | ingredient name | none | The ingredient that fills the version-control contract; see [`ingredients`](#ingredients). Absent: no ingredient fills it |
| `modules` | map | every module off | Which modules are on |
| `bootstrap` | list of commands | `[]` | Run in order in a new workspace, and once in each checkout an attempt runs in |
| `commands` | map | | Lint and build commands |
| `verify` | map | | Verification paths, lanes and validators |
| `workers` | map | | The [workers](#workers) rendered for this repository, their ingredients and roles, what an attempt may write and reach, and the jail it runs in |
| `context` | map | | [Standing context](#context): directives and skills injected by declared triggers |
| `blocks` | map | | Limits for the managed blocks `the-cook` writes |
| `state` | map | | Where clone-local state lives; see [State paths](#state-paths) |
| `workspaces` | map | | Where managed workspaces live, see [State paths](#state-paths), and whether removing one first learns what is shared, see [`workspaces`](#workspaces) |

### `ingredients`

The ingredients the repository uses: their renders and floor rules are rendered on every
`the-cook render`, and their operations answer the binary's questions and start its workers. Every entry is a
pin with four fields, written by `the-cook station apply` for the ingredients a station lists
and by `the-cook ingredient pin`:

| Field | Type | Meaning |
| --- | --- | --- |
| `name` | string | The ingredient's name; unique within `ingredients` |
| `source` | string | The cookbook: `github:<owner>/<repo>`, or an HTTPS base URL for cookbook releases |
| `version` | string | The cookbook tag, `<source>/download/<version>/ingredient-<name>.tar.gz` |
| `sha256` | string | The archive's sha256, 64 hexadecimal characters, from the tag's `checksums.txt` or the station that lists the ingredient; checked on download and on every load |

```yaml
ingredients:
  - name: git
    source: github:acme/cookbook
    version: v1.2.0
    sha256: d2f0d562418c7d176d0393d8694cc6f6f569f2dff3f7ede3788a2075d3ff5c3f
  - name: claude
    source: github:acme/cookbook
    version: v1.2.0
    sha256: b5fd834d175eb4b74cda3763a33fc04c87b00e8b85e18f8c1c43bf912eca8816
vcs: git
```

`vcs` names the ingredient that fills the version-control contract,
which a station's stages write when it is applied. It must be in `ingredients` and fill that contract, and every
worker's [ingredient](#workers) must be in `ingredients` and fill the worker contract; otherwise the profile fails
validation with exit 1, as do two entries with the same name. Deleting an entry from `ingredients` and rendering
removes that ingredient's renders and its floor.

### `stations`

The stations the repository applied, each with the pin it was applied at: the same four fields as an
entry of `ingredients`, for the archive `station-<name>.tar.gz`, written by `the-cook station apply` and
`scaffold --cookbook`. `stations` is a record, so a
person can tell where the repository started from and apply a newer version later.
Nothing rendered or run depends on it: deleting an entry changes
nothing rendered, and the station's applied entries, templates and recipes stay.
Names are unique within `stations`; an ingredient, a station and a recipe may
share a name.

```yaml
stations:
  - name: node
    source: github:acme/cookbook
    version: v1.2.0
    sha256: a2bce70041251fb8f672f9f5819969951627fdd3868497a17b525ab90a0c01d8
```

### `modules`

| Field | Type | Effect when `true` |
| --- | --- | --- |
| `hooks` | boolean | [hooks](hooks.md) are rendered into the workers' native files |
| `verify` | boolean | `the-cook verify`, its `verify` block, and the receipt that `completed` with scope `task` requires |
| `memory` | boolean | memory skeleton and `memory` block |

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

Lane names are free: `unit`, `e2e`, `validate`, `plan`, `glue`. They appear in the receipt, in `--lane` and `--skip`, in
the `commands` block, and in a recipe's `requires.lanes` and `verify.require`.

### `workers`

A map from worker name to its declaration. A worker is a program that changes the repository for someone, such as a
coding agent, a bot or a codemod. Only declared workers are rendered. Each
worker is served by an ingredient the repository pins that fills the
worker contract. A station's stages declare the workers it uses when it is
applied.

| Field | Type | Default | Meaning |
| --- | --- | --- | --- |
| `ingredient` | ingredient name | the worker's name | The ingredient that serves the worker; it must be in `ingredients` and fill the worker contract. Example: `reviewer: { ingredient: claude, role: interactive }` |
| `role` | `interactive` \| `unattended` | required | `interactive`: people use the worker in this repository; its native files carry the shared policy. `unattended`: the worker runs here only in attempts, started by `the-cook attempt`, an `attempt` action or a recipe's `attempt` step; its native files also carry `unattended.deny`. |
| `writable_roots` | list of paths | `[]` | Directories an attempt may write besides the task checkout, relative to the primary checkout root. Only for workers with `launch.sandbox_roots` or a `jail`. See below for rules |
| `network` | boolean | `false` | Whether the worker's attempts may use the network. `true` passes its ingredient's `launch.network` arguments at every unattended launch, and is a validation error for a worker whose ingredient has none; `false` passes nothing about the network, so the program's default holds, which the ingredient's page in its cookbook explains. |
| `jail.ingredient` | ingredient name | none; required with `jail` | A pinned ingredient that fills the jail contract: the worker's attempts start inside that jail, and their replay runs in it. Requires a version-control ingredient that fills `metadata`. |
| `jail.args` | list of arguments | `[]` | The jail's options this repository chooses, placed before the jail's separator, such as how the worker program's login reaches the jail; a floor bypass argument of the jail is a validation error |
| `model` | string | none | The model the binary starts the worker with, in attempts and `attempt` actions, passed as its ingredient's `launch.model` says; an action's `attempt.model` overrides it. A worker whose ingredient maps no model ignores it, and `doctor` notes it |
| `effort` | string | none | The effort level, passed and overridden like `model`, through `launch.effort` and `attempt.effort` |

`writable_roots` rules:
- Example: a tracker's database directory in the clone.
- No other directory is writable in attempts; the state directory is excluded because it holds every managed workspace.
- For a worker with a jail: an entry containing the version-control metadata or the per-user cache is a validation
  error.

### `context`

Standing context is guidance `the-cook` puts into worker sessions when a declared trigger fires, so nobody has to
invoke a directive or a skill by name.

```yaml
context:
  always:                                   # every session start, and again after compaction
    - skill: coding-guidelines
    - file: .agents/directives/unattended.md
      for: [unattended]
    - file: .agents/directives/house-rules.md
      static: true                          # written into AGENTS.md; holds without the binary
  when:
    - paths: ["**/*.sql", "src/migration/**"]   # after the worker first reads or edits a matching file
      skill: sql-quality-check
    - prompt: [timezone, DST, "time zone"]       # when the person's message contains one
      skill: timezone-handling
      inject: pointer
  budget: 120
```

| Field | Type | Default | Meaning |
| --- | --- | --- | --- |
| `always` | list of entries | `[]` | Injected at every session start, and again each time the worker fires its session-start event, as some coding agents do after compacting their context |
| `when` | list of triggered entries | `[]` | Each injected at most once per session, when its trigger first fires |
| `budget` | integer | `120` | Most lines of always-on content per session and role, `static` entries included. `doctor` notes an overrun and lists each entry with its line count |

An entry:

| Field | Type | Default | Meaning |
| --- | --- | --- | --- |
| `skill` | skill name | | An installed skill, `.agents/skills/<name>/SKILL.md`, without its front matter. Exactly one of `skill` and `file` |
| `file` | path | | A file in the repository, relative to its root, outside the state directories |
| `for` | list of `interactive`, `unattended` | both | The worker [roles](#workers) that receive the entry |
| `inject` | `content` \| `pointer` | `content` in `always`, `pointer` in `when` | `content`: the text itself. `pointer`: one line naming what to read, such as `Apply the timezone-handling skill now: read .agents/skills/timezone-handling/SKILL.md` |
| `static` | boolean | `false` | Only in `always`, and only with `for` covering both roles. The entry is written into the `context` block of `AGENTS.md` instead of injected by hook, so it applies without the binary and survives an eject |
| `paths` | list of globs | | Only in `when`: the trigger is the worker's first read or edit of a matching file in the session. Exactly one of `paths` and `prompt` |
| `prompt` | list of words and phrases | | Only in `when`: the trigger is a person's message that contains one, case-insensitive, as a whole word or phrase |

Sources are repository files and skills committed under `.agents/skills/`; nothing is fetched when a hook runs. An
attempt receives the entries as they are at its base commit. Hook delivery
needs `modules.hooks`; `static` entries do not. Preview what a session would receive with
`the-cook context show`. `COOK_NO_CONTEXT=1` turns injection off for one process
tree.

### `blocks`

| Field | Type | Default | Meaning |
| --- | --- | --- | --- |
| `agents_md_budget` | integer | `40` | Most lines the managed blocks in `AGENTS.md` may take together, the `context` block excepted. Each of those blocks also stays within 8 lines. `render` still writes blocks that exceed it; `doctor` notes the overrun. |

### State paths

State is clone-local, gitignored and disposable, and lives outside `.cook/`. Two keys say where, each relative to the
repository root. Each path is written down as a convention in a managed block of `AGENTS.md` and, through the
version-control ingredient's renders, in an ignore block such as one of `.gitignore` ([block ids](render.md#block-ids)),
so people, workers and other tools find it, with or without `the-cook`.

```yaml
state:
  dir: .work                      # receipts, sessions, locks, lifecycle log, hook cache
workspaces:
  dir: .work/workspaces            # each managed workspace at <dir>/<name>
```

| Field | Type | Default | Holds | Blocks it produces |
| --- | --- | --- | --- | --- |
| `state.dir` | path | `.work` | `lifecycle.jsonl` and locks (primary checkout); `session.json`, `verified.json`, `hook-checks.json`, `run.json`, `last-attempt-output`, `last-attempt.patch` (per checkout) | `.gitignore` `state` (`/.work/`); named in the `AGENTS.md` `layout` block |
| `workspaces.dir` | path | `.work/workspaces` | each managed workspace, at `<dir>/<name>` | `AGENTS.md` `workspaces` (where workspaces are created, their `work/<name>` branch, that they start from committed code); `.gitignore` `workspaces` (`/.work/workspaces/`) |

Rules for the paths:

- No state path may point inside `.cook/` or `.agents/`, or be either directory: those hold committed files only.
  Such a path is a validation error.
- Changing a path re-renders its blocks; state already under the old path is not moved.

### `workspaces`

Besides `dir` ([State paths](#state-paths)), `workspaces` holds whether removing a workspace first learns what is
shared:

```yaml
workspaces:
  update_before_landed: true
```

| Field | Type | Default | Meaning |
| --- | --- | --- | --- |
| `workspaces.update_before_landed` | boolean | `false` | Run the version-control contract's `update` before `workspace remove` detects landed work, so it compares with the shared default line as it is. When `update` fails or is absent, detects against what the checkout knows. |

Nothing else in `the-cook` learns what is shared on its own: `the-cook make`, `the-cook workspace create` and
`the-cook doctor` never run `update`, and `fleet` runs its own. A repository whose work
lands on the shared default line of work somewhere else, such as through a hosting platform's merge, sets the field to
`true`, so removing a workspace fires `landed` without anyone first updating the checkout by hand. A station may write
it as one of its entries.

## Examples

### A Node API

```yaml
# .cook/config.yaml
schema: 1
name: acme-api
ingredients:
  - name: git
    source: github:acme/cookbook
    version: v1.2.0
    sha256: d2f0d562418c7d176d0393d8694cc6f6f569f2dff3f7ede3788a2075d3ff5c3f
  - name: claude
    source: github:acme/cookbook
    version: v1.2.0
    sha256: b5fd834d175eb4b74cda3763a33fc04c87b00e8b85e18f8c1c43bf912eca8816
  - name: acme-agent
    source: github:acme/cookbook
    version: v1.2.0
    sha256: 6d2f26296cf399e1f470582106e0def36fcb3979b54422ecde46767a4b996ea7
stations:
  - name: node
    source: github:acme/cookbook
    version: v1.2.0
    sha256: a2bce70041251fb8f672f9f5819969951627fdd3868497a17b525ab90a0c01d8
vcs: git
modules: { hooks: true, verify: true, memory: true }
bootstrap: [npm ci]
commands:
  lint: npx eslint --fix {file}
  build: npm run build
verify:
  app_paths: [src/**, test/**, package.json, package-lock.json, tsconfig*.json, Dockerfile]
  lint_paths: ["src/**/*.{ts,js}", "test/**/*.ts"]
  lanes:
    unit: { select: "npx vitest run {pattern}", full: npx vitest run }
    e2e:
      select: npx vitest run --config vitest.e2e.config.ts {pattern}
      full: npx vitest run --config vitest.e2e.config.ts
      required_when: [src/**/*.controller.ts, src/**/routes/**, src/**/guards/**]
  validators:
    - { when: [openapi/**, src/**/*.dto.ts], run: npm run openapi:check }
workers:
  claude: { role: interactive }
  acme-agent: { role: unattended, writable_roots: [.tasks], model: acme-large, effort: high }
context:
  always:
    - skill: coding-guidelines
  when:
    - paths: ["src/migrations/**", "**/*.sql"]
      skill: sql-quality-check
```

The repository applied the `node` station and edited its entries to the project's real commands. The team's tracker
keeps its database in `.tasks/` in the clone, so the `acme-agent` worker, whose ingredient opens sandbox roots, may
write it in its attempts, which start it with the model and effort given here.

### A Terraform repository

```yaml
schema: 1
name: acme-infra
ingredients:
  - name: git
    source: github:acme/cookbook
    version: v1.2.0
    sha256: d2f0d562418c7d176d0393d8694cc6f6f569f2dff3f7ede3788a2075d3ff5c3f
  - name: claude
    source: github:acme/cookbook
    version: v1.2.0
    sha256: b5fd834d175eb4b74cda3763a33fc04c87b00e8b85e18f8c1c43bf912eca8816
stations:
  - name: terraform
    source: github:acme/cookbook
    version: v1.2.0
    sha256: 263cf543df68c48c6b032365c3207d73b4d9681807a20c3b1c050fe056d06274
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
workers: { claude: { role: interactive } }
```

`plan` has no `full`: there is no meaningful "plan everything", so `--full` still asks for `--lane plan=<dir>` or a
`--skip plan=<reason>`.

### A Go repository

```yaml
schema: 1
name: acme-cli
ingredients:
  - name: git
    source: github:acme/cookbook
    version: v1.2.0
    sha256: d2f0d562418c7d176d0393d8694cc6f6f569f2dff3f7ede3788a2075d3ff5c3f
  - name: claude
    source: github:acme/cookbook
    version: v1.2.0
    sha256: b5fd834d175eb4b74cda3763a33fc04c87b00e8b85e18f8c1c43bf912eca8816
  - name: codex
    source: github:acme/cookbook
    version: v1.2.0
    sha256: 17accff1f425d5eebd1ea4dec6901b620be626fe85f8633dfe0291ab9d374fa6
stations:
  - name: go
    source: github:acme/cookbook
    version: v1.2.0
    sha256: 37f31d4b7ea8e164ba29a13a13b6aece3d8d1a9435dc7f90fdfac715d339eecc
vcs: git
modules: { hooks: true, verify: true, memory: true }
bootstrap: [go mod download]
commands:
  lint: gofmt -d {file}
  build: go build ./...
verify:
  app_paths: ["**/*.go", go.mod, go.sum]
  lint_paths: ["**/*.go"]
  lanes:
    test: { select: "go test {pattern}", full: go test ./... }
    vet: { select: "go vet {pattern}", full: go vet ./... }
workers: { claude: { role: interactive }, codex: { role: interactive } }
```

Patterns for the Go lanes are package paths:
`./cook verify --lane test=./internal/billing/... --lane vet=./internal/billing/...`.

## Guidelines

- Do make `select` commands fail when they select zero tests, if the test runner allows it; a lane that "passes" with
  no tests proves nothing.
- Do keep `required_when` narrow: list only the files whose change the lane is the only real evidence for.
- Do use the repository's own task runner in commands when it has one (`npm run build`, `make test`), so people and
  workers run the same thing.
- Do keep the state paths out of what your linters, test runners and compilers scan (exclude `.work/**`, or the
  `state.dir` you chose): workspaces nested there hold full copies of the source.
- Do choose the state paths before work starts: moving one later leaves existing workspaces, sessions and receipts under
  the old path.
- Don't put secrets or machine-specific paths in commands; the profile is committed and runs on every machine.
- Don't use `bootstrap` for slow, optional setup; every new workspace runs it, and so does every checkout an attempt
  runs in.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| `unknown key` error | A typo or a field from a newer schema | Fix the key; check `schema` |
| `vcs` or a worker's `ingredient` names an ingredient not in `ingredients` | The pin was deleted, or never written | Pin it with `./cook ingredient pin <name> --cookbook <source>@<version>`, or name a pinned ingredient |
| `vcs` or a worker's `ingredient` names an ingredient that fills another contract | `vcs` needs a version-control ingredient, a worker a worker ingredient | Name an ingredient that fills the contract; `./cook ingredient list` shows each one's contract |
| Two `ingredients` entries, or two `stations` entries, with the same name | A name is unique within each list | Delete one; `./cook ingredient pin` moves an existing pin |
| `writable_roots` is rejected for a worker | Its ingredient opens no sandbox roots and the worker has no jail | Remove the field, use an ingredient that fills `launch.sandbox_roots`, or declare a `jail` |
| `network: true` is rejected for a worker | Its ingredient has no `launch.network` arguments | Remove the field, or use an ingredient that fills `launch.network` |
| A worker's `jail` is rejected | `jail.ingredient` is not pinned or fills another contract, the jail ingredient is incomplete, `jail.args` holds a floor bypass argument of the jail, the jail has no `network` for a worker with `network: true`, or the version-control ingredient has no `metadata` | Fix what the message names |
| `render` exits 3 naming `./cook fetch` | An ingredient in `ingredients` is not in this machine's cache | Run `./cook fetch` |
| `doctor` says a program is not on PATH | A command, lane, validator or action names a program this machine lacks | Install the program, or fix the command in `.cook/config.yaml` or `.cook/hooks.yaml` |
| `doctor` says a glob matches nothing | Wrong path or quoting | YAML needs quotes around globs that start with `*` |
| A lane runs with the literal text `{pattern}` | The command is in `full`, which takes no placeholder | Use `{pattern}` only in `select` |
| `state path must not be inside .cook/ or .agents/` | A state path points into a directory that holds committed files only | Choose a path outside both, such as the default `.work/` |
| `doctor` notes that always-on context exceeds `context.budget` | Too many or too long `always` entries for one role | Move entries to `when` triggers, use `inject: pointer`, or narrow them with `for` |
