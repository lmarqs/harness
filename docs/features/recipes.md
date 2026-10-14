# Recipes

## Objective

**Problem.** A repository makes some kinds of change again and again: a database migration, a new module, one task
from a tracker. Each time someone repeats the same steps from memory or from a page of instructions: the generator to
run, the files to add, what to ask a worker, what a person has to check, which tests prove the result. A generator
stops once the files exist, before anything checks them; written instructions for a coding agent guarantee nothing;
and a step someone forgets shows up in review, or after the merge.

**Goal.** A kind of change a repository makes often is written down once, in the repository, as steps anyone can run
with a few inputs, and every run takes that change through the whole lifecycle: it starts in a workspace of its own,
its steps run in order, the lanes it needs prove it, and it is delivered the way the repository delivers every change.

## Overview

`the-cook` declares a software development lifecycle: the stages a change goes through, the events fired between them,
the guards that hold between stages and the proof a change carries ([lifecycle](lifecycle.md)). A recipe is one kind
of change, made many times: the inputs it takes, what it requires of the repository, the steps that make the change,
mapped onto the stages, and the lanes that must prove it ([ADR 0025](../adrs/0025-recipes.md)). A run of a recipe is
one pass of one change through the stages.

A recipe is one of three units a repository takes from cookbooks or writes itself, and one rule separates them: facts
about programs are pinned and rendered; practices are copied and become the repository's own
([ADR 0025](../adrs/0025-recipes.md)).

| Unit | What it is | How a repository holds it | When it acts |
| --- | --- | --- | --- |
| [Ingredient](ingredients.md) | facts about one program: the contract it fills, its renders, its floor, its checks | pinned by cookbook, version and sha256 in `ingredients:` | on every render and every launch |
| [Station](stations.md) | what one way of working needs in place: the ingredients to pin, the workers and their roles, entries for the profile, the policy and the hooks file, templates, and the recipes to add | applied once; recorded in `stations:` for provenance and for applying a newer version | once, and again to take a newer version |
| Recipe | one kind of change, made many times: inputs, requirements, steps mapped onto the stages, required lanes | copied into `.cook/recipes/<name>/` with its origin recorded in the file, or written there by the repository; the repository's own to edit | every time someone runs it |

A recipe maps onto the stages of the lifecycle like this:

| Stage | Recipe key | What a run does |
| --- | --- | --- |
| [Plan](lifecycle.md#plan) | `inputs`, `requires` | takes the inputs, and the task id if there is one, from whoever runs it; before anything else, validates each input by type and checks the contracts, roles and lanes the recipe requires |
| [Start](lifecycle.md#start) | `workspace` | creates a managed workspace and its branch `work/<name>` at the revision of the shared default line of work, as `the-cook workspace create` does, and fires `start` ([Start](#start)) |
| [Implement](lifecycle.md#implement) | `steps` | runs each [step](#step-kinds) in order: a command, a template, an attempt by an unattended worker, or a handoff to whoever drives the run ([Implement](#implement)) |
| [Verify](lifecycle.md#verify) | `verify` | runs every lane the change selects and every lane the recipe requires, a required lane the change selects nothing for in full, and writes the receipt ([Verify](#verify)) |
| [Complete](lifecycle.md#complete) | none | replays the receipt from the base and fires `completed` for a task, as for any work ([Complete](#complete)) |
| [Deliver](lifecycle.md#deliver), Review and merge, [Landed](lifecycle.md#landed) | none: a recipe declares no lifecycle action | as for any work: the repository's own actions, under the same guards ([Deliver and Landed](#deliver-and-landed)) |

A recipe lives in the repository, in `.cook/recipes/<name>/`, and is the repository's own to edit, like the profile.
The repository writes one with [`the-cook recipe create`](#the-cook-recipe-create), copies one a cookbook publishes
with [`the-cook recipe add`](#the-cook-recipe-add), or receives one from a station that adds it
([stations](stations.md#recipes)). A copy records where it came from in its `origin` key, so
[`the-cook recipe update`](#the-cook-recipe-update) can merge a newer version into it without losing the repository's
edits. Nothing in a recipe is rendered, and a recipe acts only when someone runs it with
[`the-cook make`](#the-cook-make).

Commands are named `the-cook <command>` on this page. The examples run them through the repository's wrapper,
`./cook <command>`, from the root of a checkout; with a global install, `the-cook <command>` is equivalent
([wrapper](wrapper.md)). The examples use three recipes of a Node API, `acme-api`: `migration`, the repository's own;
`nestjs-module`, added from a cookbook published at `github:acme/cookbook`; and `implement-task`, which the
[`agentic-coding` station](stations.md#the-agentic-coding-station) of that cookbook adds and a loop runner runs for
each task of a tracker.

## When to use it

| Situation | Command |
| --- | --- |
| The repository makes one kind of change often, and people repeat its steps by hand | `./cook recipe create <name>`, then edit `.cook/recipes/<name>/recipe.yaml` ([the recipe file](#the-recipe-file)) |
| Make one such change | `./cook make <name> --<input> <value>...` ([the run](#the-run)) |
| See which inputs a recipe takes | `./cook make <name> --help` |
| See the steps a run would take, with its inputs substituted, before running it | `./cook make <name> --<input> <value>... --dry-run` |
| Resume a run that paused at a handoff, or that a step stopped | `./cook make --continue <workspace>` |
| See where a run is | `./cook make --status <workspace>` |
| See the repository's recipes, or the ones a cookbook tag publishes | `./cook recipe list`; `./cook recipe list --cookbook github:acme/cookbook@v1.2.0` |
| Read a recipe before adding it | `./cook recipe show <name> --cookbook github:acme/cookbook@v1.2.0` |
| Take up a recipe a cookbook publishes | `./cook recipe add <name> --cookbook github:acme/cookbook@v1.2.0` |
| Take a newer version of an added recipe and keep the repository's edits | `./cook recipe update <name> --cookbook github:acme/cookbook@v1.3.0` |
| Have a loop runner take each task of a tracker through the lifecycle | name `./cook make <recipe> --task <id> --unattended` as its command for each task ([example](#a-loop-runner-runs-implement-task-for-each-task)) |
| Set up what a way of working needs, once | not a recipe: a [station](stations.md) |
| Make a change once | not a recipe: `./cook workspace create <name>`, then the stages by hand ([lifecycle](lifecycle.md)) |

### What is not a recipe

Something that changes nothing in the repository, such as watching a running system, is not a recipe: it has no
workspace, receipt or delivery. Acting on what monitoring found is one: a recipe that takes the alert as an input and
whose `attempt` step has a worker follow a monitoring skill. Setting a repository up is not a recipe either: what a way
of working needs in place, its pins, workers, entries and templates, is a [station](stations.md), applied once.

### Writing one

**Authoring path (repository's own):** `./cook recipe create <name>` → edit [recipe file](#the-recipe-file) (inputs,
requires, workspace, steps, verify) → draft templates in `files/` → test with `--here` in a managed workspace →
commit.

**Authoring path (for a cookbook):** write [recipe file](#the-recipe-file) in `recipes/<name>/` (without `origin`) →
test in sample repositories → [cookbooks](stations.md#cookbooks) (build archive, checksums.txt, publish).

## The recipe file

A recipe is a directory of `.cook/recipes/`, named like the recipe:

```text
.cook/recipes/<name>/
  recipe.yaml   the recipe
  files/        the sources of its template steps, when it has any
```

```yaml
# .cook/recipes/migration/recipe.yaml
schema: 1
name: migration
description: Add a database migration
inputs:
  name: { type: slug, description: what the migration does }
requires:
  contracts: [vcs, worker]
  roles: [unattended]
  lanes: [unit, migrations]
workspace: "migration-{name}"                                                  # Start
steps:                                                                         # Implement, in order
  - run: [npm, run, migration:create, --, "{name}"]
  - template: { from: down-test.ts, to: "src/migration/{name}.spec.ts" }
  - attempt: { worker: coder, prompt: "Write the up and down steps of the migration {name}." }
  - handoff: "Check the generated SQL against production data volumes, then continue."
verify: { require: [unit, migrations] }                                        # Verify
```

| Key | Type | Required | Meaning |
| --- | --- | --- | --- |
| `schema` | integer | yes | Format version of the recipe file: `1` |
| `name` | string | yes | `[a-z][a-z0-9-]*`: lowercase letters, digits and `-`, starting with a letter. The name of the recipe's directory and the name `the-cook make` takes |
| `description` | string | no | One line, printed by `recipe list`, by `make <name> --help` and when a run starts |
| `origin` | `{ source, version, sha256 }` | no | Where the recipe was copied from: the [cookbook](stations.md#cookbooks), the tag, and the sha256 of the archive, as the tag's `checksums.txt` states it or as the station that added the recipe lists it. Written by `recipe add` and `station apply`, moved by `recipe update`; absent from the repository's own recipes and from a recipe as a cookbook publishes it. A run never reads it |
| `inputs` | map of input name to input | no | The values a run takes ([inputs](#inputs)) |
| `inputs.<name>.type` | `string` \| `slug` \| `enum` \| `bool` | yes | What the value may be |
| `inputs.<name>.values` | list of strings | with `enum` | The values an `enum` input accepts |
| `inputs.<name>.description` | string | no | One line, printed by `make <name> --help`, by `recipe show` and when the input is missing or invalid |
| `requires.contracts` | list of `vcs`, `worker` | no | The [contracts](ingredients.md#contracts) the repository must have filled: `vcs`, a pinned ingredient the profile's `vcs:` names; `worker`, at least one declared worker |
| `requires.roles` | list of `interactive`, `unattended` | no | For each role, at least one worker the profile declares with it ([workers](workers.md#roles)) |
| `requires.lanes` | list of lane names | no | Lanes the profile must declare under `verify.lanes` ([profile](profile.md#verify)); it holds every lane of `verify.require` |
| `workspace` | string | yes | The name of the managed workspace a run creates, with [placeholders](#placeholders): the workspace is `<workspaces.dir>/<name>` and its branch `work/<name>` ([Start](#start)) |
| `steps` | list of steps | yes | The steps in the order they run, each a map with exactly one key, its [kind](#step-kinds) ([Implement](#implement)) |
| `verify.require` | list of lane names | no | Lanes every run runs, and never skips, on top of those the change selects ([Verify](#verify)) |

Unknown keys are an error. The binary refuses a recipe whose `schema` is newer than it supports, with exit 1 naming the
release needed, and reads older ones inside its support window;
`the-cook version` prints the recipe schema the running release supports. A recipe is also invalid when a lane of
`verify.require` is missing from `requires.lanes`, when an input takes the name of a flag of `the-cook make`, when a
placeholder names no input, when a step has no kind or more than one, and when any of its files holds a conflict
marker left by `recipe update`. Every command that reads a recipe checks it, and `the-cook doctor` reports each invalid
one ([doctor](doctor.md#how-it-works)).

Names are unique within `.cook/recipes/`, because each recipe is a directory there, and a station and a recipe may
share a name. A recipe runs only as `the-cook make <name>`, so its name never meets a command's.

### Inputs

An input name is `[a-z][a-z0-9-]*`: lowercase letters, digits and `-`, starting with a letter, such as `module-path`. A
run takes each input as `--<name> <value>`, fills the `{<name>}` [placeholders](#placeholders) with it and passes it to
`run` steps as `COOK_INPUT_<NAME>`: the name in upper case, each `-` written as `_`, so `module-path` reaches a step as
`COOK_INPUT_MODULE_PATH`. An input may not take the name of a flag `the-cook make` accepts, global flags included:
`continue`, `status`, `task`, `here`, `unattended`, `commit`, `withhold`, `dry-run`, `yes`, `help`, `json` or `verbose`.

| Type | Accepts |
| --- | --- |
| `string` | any text that does not begin with `-` |
| `slug` | `[a-z0-9][a-z0-9-]*`: lowercase letters, digits and `-`, starting with a letter or a digit, as a workspace or a file name needs |
| `enum` | one of the strings in `values` |
| `bool` | a flag: `--<name>`, with no value, makes it true; without the flag it is false |

No value of any type may begin with `-`, so no value reads as an option. Every input is required, except that a
`bool` is false without its flag. A run validates every input before anything runs: a missing input, an unknown one,
or a value its type does not accept exits 1, names the input with its description, and creates nothing.
`the-cook make <name> --help` lists the recipe's inputs, each with its type, the values of an `enum`, and its
description.

Inputs are plain text. In a `run` step a value stays one argument and never reaches a shell. In a prompt and in a
handoff's text it is substituted as it is, so a value a person or a tracker supplies is a prompt-injection surface, as
in `attempt` actions ([safety](safety.md#attempt-actions-and-prompt-injection)).

### Placeholders

`{<input>}` is replaced by the input's value, and `{task}` by the task id given with `--task`, in `workspace`, in each
argument of a `run` step, in `template.to`, in `attempt.prompt` and in a handoff's text. A `bool` input's value is
`true` or `false`. A value is replaced inside the string it appears in, so it never splits one argument into more.
`{task}` in a run without `--task` exits 1 before anything runs. The sources of `template` steps use the
[template language](ingredients.md#the-template-language) instead.

## Step kinds

Steps are the Implement stage of a run. Their kinds are a closed set the binary defines; a recipe cannot add one.

| Kind | Does | Stops or pauses the run when |
| --- | --- | --- |
| [`run`](#run) | runs a command, an argument list, in the workspace | it exits non-zero |
| [`template`](#template) | writes one file from the recipe's `files/`, rendered with the inputs | the path exists |
| [`attempt`](#attempt) | starts a declared unattended worker on a prompt, under the attempt contract | the worker exits non-zero, or the tamper check finds a difference |
| [`handoff`](#handoff) | prints its text for whoever drives the run | always: it pauses an attended run, and stops an unattended one as blocked |

### `run`

```yaml
- run: [npm, run, migration:create, --, "{name}"]
```

An argument list. Each element has its placeholders replaced and stays one argument, and the list is never passed
through a shell, so a value can neither split into more arguments nor reach one. A step that needs a shell names `sh`
and passes values as positional parameters: `run: [sh, -c, 'npm run migration:create -- "$1" && npm run lint', sh,
"{name}"]`.

The command runs as the binary does, with the rights of whoever started the run and outside every worker's permission
checks, from the root of the workspace, like a [`run` lifecycle action](lifecycle.md#run), and, after an `attempt` step
of a worker with a [jail](#attempt), inside that jail. Its output is printed as it runs. It inherits the run's
environment, plus:

| Variable | Value |
| --- | --- |
| `COOK_RECIPE` | the recipe's name |
| `COOK_RUN` | the path of the [run record](#the-run-record), `run.json` |
| `COOK_INPUT_<NAME>` | the value of each input, `<NAME>` being its name in upper case with each `-` as `_`; `true` or `false` for a `bool` |

A `the-cook` command started from a step, where `COOK_RUN` is set, fires no lifecycle event; `the-cook lifecycle run`
and `the-cook make` refuse there with exit 3, so a recipe never fires an event itself ([guards](#guards)). A non-zero
exit stops the run ([stopping and continuing](#stopping-and-continuing)).

### `template`

```yaml
- template: { from: down-test.ts, to: "src/migration/{name}.spec.ts" }
```

| Field | Meaning |
| --- | --- |
| `from` | a file under the recipe's `files/` |
| `to` | where the file is written, relative to the root of the workspace, with placeholders replaced; it must stay inside the workspace |

The content is rendered in the [template language](ingredients.md#the-template-language), whose values here are the
inputs and `task`: `{{name}}` inserts the input `name`, `{{module-path}}` the input `module-path`, and a `bool` input
renders a `{{#flag}}...{{/flag}}` section when it is true and a `{{^flag}}...{{/flag}}` one when it is false. The step
creates the directories the path needs. It refuses a path that exists: it writes nothing and stops the run, so a
template never overwrites a file.

### `attempt`

```yaml
- attempt: { worker: coder, prompt: "Write the up and down steps of the migration {name}." }
```

| Field | Default | Meaning |
| --- | --- | --- |
| `worker` | the only worker declared with role `unattended` | The [declared worker](workers.md) to start, which must have role `unattended`; required when the profile declares more than one |
| `prompt` | the prompt the caller of `the-cook make` passes on stdin | What the worker is asked to do, with placeholders replaced |
| `model` | the worker's configured model | Override for this step; a worker whose ingredient maps no model ignores it |
| `effort` | the worker's configured effort | Override for this step, as for `model` |

The step starts the worker unattended in the workspace, under the [attempt contract](attempts.md#the-attempt-contract):
the launch its worker ingredient pins, `unattended.deny` and the floor applied, its floor bypass arguments refused,
`COOK_ATTEMPT=1` in its environment, its rules read from the workspace's head commit, and the tamper check around it.
The tamper check compares `.cook/` and the tool-owned files with their state when the step began, the uncommitted edits
of the steps before it included, and restores that state, so a `--here` run of an uncommitted draft recipe keeps its
draft. Like an [`attempt` action](lifecycle.md#attempt), the step runs on the uncommitted edits of the steps before it,
and the worker cannot commit, push or fire lifecycle events. Its edits stay in the workspace for the next steps.
Arguments given to `the-cook make` after `--` go to the worker of every `attempt` step, after those its ingredient pins,
and a floor bypass argument among them is refused. When the prompt reaches the worker on stdin, it carries the `always`
entries of the `unattended` [standing context](context.md#delivery-in-the-prompt) before the step's text, as in every
attempt.

A worker declared with a [jail](workers.md#jails) runs the step inside it. From then until the run ends, the later `run`
steps, the Verify stage and the replay of Complete run in that jail too, with the same shares, variables, network and
`jail.args`, because they run code the worker wrote; the steps before the first such `attempt` step, the
version-control operations, the tamper check, the commit and the lifecycle actions run outside it
([what runs in the jail](ingredients.md#what-runs-in-the-jail)). A `run` step that needs a variable or a service the
jail withholds therefore passes before that step and fails after it, until `jail.args` lets it through.

Completion belongs to the run, not to the step: the step passes when the worker exits 0 and the tamper check passes,
and the receipt, the replay and `completed` come at the run's [Verify](#verify) and [Complete](#complete) stages. A
worker that runs `./cook verify` gets feedback on its change and leaves the selection the run's Verify starts from; the
proof is the run's own Verify and the replay. A failed attempt stops the run as blocked, naming the step, and leaves
the worker's changes in the workspace: unlike `the-cook attempt` run on its own, the step never returns the workspace
to its base. `the-cook make --continue` runs the step again on the tree as it is, once a person has inspected the
changes or discarded them.

A run whose `attempt` step names a worker that is not declared, or not with role `unattended`, exits 3 before anything
runs and names it. A run with an `attempt` step without a `prompt`, whose caller passes none on stdin, exits 1 before
anything runs.

### `handoff`

```yaml
- handoff: "Check the generated SQL against production data volumes, then continue."
```

A string, with placeholders replaced. Behavior depends on whether someone drives the run
([attended and unattended runs](#attended-and-unattended-runs)): an attended run pauses (prints text, fires nothing,
exits 3); an unattended run stops as blocked (fires `blocked`, exits 3). In both cases, `the-cook make --continue`
resumes with the step after the handoff.

## The run

`the-cook make <recipe> [--<input> <value>]...` takes one change through the stages of the lifecycle and prints each
step as it runs ([reference](#the-cook-make)).

A run reads its recipe once. When it starts, the binary reads the recipe's files, `recipe.yaml` and every file under
`files/`, and keeps their content and digest in the [run record](#the-run-record). Every later stage, and
`the-cook make --continue`, runs that content and never reads the files again, so no step can change the recipe of the
run it is in. A run that creates its workspace reads the recipe, with the profile, the policy and the hooks file, from
that workspace's base commit: the revision the shared default line of work is at, as the version-control ingredient's
`default_revision` reports it from what this checkout last learned
([the version-control contract](ingredients.md#the-version-control-contract)). A recipe therefore runs in a new
workspace once it is on the shared default line of work (for git, pushed), not merely committed in this clone. With
`--here`, a run reads them from the working tree of the current workspace instead, which is the way to run a draft;
`--here` is refused with `--unattended`.

### Attended and unattended runs

The caller declares whether someone drives the run. A run is attended unless it is started with `--unattended`:
whoever starts it, a person at a terminal or a coordinating worker that runs commands for a person without one, is there
to do what a handoff asks. `--unattended` marks a run nobody drives, as a loop runner's for each task; the loop runner's
configuration a station writes passes it ([example](#a-loop-runner-runs-implement-task-for-each-task)). Whether a
terminal is attached changes nothing.

| | Attended | Unattended, with `--unattended` |
| --- | --- | --- |
| A handoff | pauses the run: prints its text, fires nothing, exits 3 | stops the run as blocked: fires `blocked` naming the step, exits 3 |
| A step, Verify, a lane or the replay that stops the run | fires `blocked` | the same |
| Exit 3 before the run starts, such as a missing requirement | fires nothing | fires `blocked`, as any other command that exits 3 without a terminal does |
| The recipe | read from the new workspace's base commit, or with `--here` from the working tree | read from the new workspace's base commit; `--here` is refused |

The run record keeps the mode, and `make --continue` resumes the run in it. A loop runner whose command lacks
`--unattended` starts attended runs: at a handoff each one pauses, exits 3 and prints no completion text, so the loop
runner never counts the task done.

### Start

1. **Check.** `the-cook make` refuses to run inside an attempt (`COOK_ATTEMPT=1`) or inside a step (`COOK_RUN`), with
   exit 3. It reads the recipe at the revision the version-control ingredient's `default_revision` reports, and checks
   it: a valid recipe file, every input valid for its type, every contract, role and lane `requires` names, and the
   worker of each `attempt` step. A failure exits 1, or 3 for a missing requirement, which the message names, and
   creates nothing. It does not run `update`: the revision is the one this checkout last learned.
2. **Create** the managed workspace the `workspace` key names, `<workspaces.dir>/<name>` on a new branch `work/<name>`
   at that revision, as `the-cook workspace create <name> --base <default revision>` does
   ([workspaces](workspaces.md#create)): the session with the base, the task given with `--task` and the actor; the
   profile's `bootstrap`; then the `start` event, with scope `task` when the run has a task and `change` otherwise.
3. **Record** the run in `run.json` in the new workspace's state directory, with the recipe's content and digest
   ([the run record](#the-run-record)), and print the workspace's path.

With `--here`, the run reads the recipe from the working tree and uses the managed workspace the current directory is
in instead: it creates nothing, runs no `bootstrap` and fires no `start`, and its events carry the task given with
`--task`, or else the workspace's own. It refuses, with exit 1, `--unattended`, a checkout that is not a managed
workspace, and a workspace whose record shows a run that is not completed, naming `--continue`.

### Implement

The steps run in order, each printed with its number and kind before it runs, and each result is written into the run
record. A step that fails stops the run ([stopping and continuing](#stopping-and-continuing)).

### Verify

When the last step has run, the run verifies the change itself, in the workspace and against the workspace's base, as
[`the-cook verify`](verify.md) does: lint, validators, build and lanes. It runs every lane the change selects and every
lane of `verify.require`:

- The change's selection, the lanes with their patterns and the skip reasons, is the one of the receipt a step left in
  the workspace: the worker of an `attempt` step, or a person at a handoff, runs `./cook verify` with the patterns the
  change needs, as for any change. That `verify` is feedback for whoever ran it, not the proof. When no step left a
  receipt, the change selects no lane.
- Every lane of `verify.require` runs and is never skipped: with the patterns the selection gives it, or in full, with
  its `full` command, when the selection gives it none or skips it.
- The [lane rules](verify.md#lane-rules) hold for the other lanes as for any change, each required lane counting as
  selected.

When the lane rules demand a lane the selection neither selects nor skips, or a required lane without patterns has no
`full` command, the run stops at Verify with exit 3 and names the lane: a person or a worker runs `./cook verify` with
it selected in the workspace, and `the-cook make --continue` verifies again. A lane that fails stops the run with exit
2.

The receipt records, for each lane, the patterns it ran or that it ran in full, and the recipe, the digest of the
recipe the run read and the inputs. The run fires `verified`, as `the-cook verify` does.

```json
{
  "base": "4f1c2a9e0b7d3c5a8e6f1b2d4c7a9e0f3b5d8c1a",
  "fingerprint": "c9b2e1d3f00cff5ef8984808250ea2f22efbf15bf342c0cd3c969aa8432be07c",
  "lanes": {
    "unit": { "patterns": ["migration"], "full": false },
    "migrations": { "full": true }
  },
  "skipped": { "e2e": "no route, controller or guard changed" },
  "full": false,
  "recipe": {
    "name": "migration",
    "digest": "d7ff2e7deb56e513d78fa6c7529c07dd16619ae419cbb3de75acd8d433cee143",
    "inputs": { "name": "add-invoice-index" }
  }
}
```

### Complete

Once the receipt is written, the run replays it from the base, as `the-cook attempt` does: it runs the receipt's lanes,
each with its patterns or in full, and its skip reasons again against the final content, with the profile read from the
base commit. The replay is the proof. When it passes:

1. with `--commit`, the run commits the work through the version-control ingredient, with the recipe, its inputs and
   the verification summary in the commit body;
2. in a run with a task, it fires `completed` with scope `task`, then `deliver` with scope `task` when the work is
   [concluded](lifecycle.md#scopes), as `the-cook attempt` does. A run without a task fires neither, because work of
   scope `change` has no `completed` event;
3. with `--withhold <text>`, it prints the text;
4. the record shows the run as completed, and it exits 0.

A failing replay stops the run at Complete with exit 2.

### Deliver and Landed

A recipe declares no lifecycle action. The repository's own `deliver` actions deliver the work, under the same guards
as any work: automatically after `completed` for a task, with `auto: true`, or by hand with
`./cook lifecycle run deliver` in the workspace ([lifecycle](lifecycle.md#deliver)). While the receipt records the run,
every action fired in the workspace also receives `COOK_RECIPE` and `COOK_INPUT_<NAME>`, so a delivery action can, for
example, title a pull request after the recipe and its inputs. After the merge, `./cook workspace remove <name>` fires
`landed` and removes the workspace, as for any work.

### The run record

`run.json` in the workspace's state directory, by default `.work/workspaces/<name>/.work/run.json`, holds the run.
`the-cook make --status` prints it, and with `--json` prints it as it is. Shortened here: `content` holds each file
whole.

```json
{
  "recipe": "migration",
  "digest": "d7ff2e7deb56e513d78fa6c7529c07dd16619ae419cbb3de75acd8d433cee143",
  "content": {
    "recipe.yaml": "schema: 1\nname: migration\ndescription: Add a database migration\n",
    "files/down-test.ts": "import { describe, it } from 'vitest';\n"
  },
  "inputs": { "name": "add-invoice-index" },
  "task": null,
  "commit": false,
  "unattended": false,
  "stage": "implement",
  "status": "paused",
  "steps": [
    { "kind": "run", "status": "passed" },
    { "kind": "template", "status": "passed" },
    { "kind": "attempt", "status": "passed" },
    { "kind": "handoff", "status": "paused" }
  ]
}
```

| Field | Meaning |
| --- | --- |
| `recipe` | the recipe's name |
| `digest` | the sha256 over the recipe's files as the run read them when it started, `recipe.yaml` and every file under `files/` |
| `content` | those files, by path relative to the recipe's directory: the recipe every stage and `make --continue` run |
| `inputs` | each input's value |
| `task` | the task id given with `--task`, or `null` |
| `commit` | whether the run commits the work at Complete |
| `unattended` | whether the run was started with `--unattended` |
| `stage` | the stage the run is at: `start`, `implement`, `verify` or `complete` |
| `status` | `running`; `paused` at a handoff in an attended run; `stopped` by a step, a lane, the replay, or a handoff in an unattended run; or `completed` |
| `steps` | each step's `kind` and `status`: `passed`, `failed`, `paused` or `pending` |
| `reason` | for a stopped run, what stopped it: the stage, the step and why |

### Stopping and continuing

A run stops when a step fails (a `run` step exits non-zero, a `template` step finds its path, an `attempt` step
fails), when Verify cannot select a lane the lane rules demand, when a lane or the replay fails, and at a handoff in
an unattended run. A stopped run:

- fires `blocked` once, with scope `task` when the run has a task and `change` otherwise, and the stage, the step and
  the reason in `COOK_REASON`, such as `migration step 1 (run): exit 1`;
- keeps the workspace and everything the steps changed in it, so a person can see what happened;
- records where it stopped, and exits 3, or 2 when a lane or the replay failed.

`the-cook make --continue [<workspace>]` resumes a paused or stopped run once a person, or a worker in their session,
has done what the handoff asks or fixed what stopped it. It takes the inputs, the task, `--commit` and `--unattended`
from the record, and runs the recipe content the record holds: a change to the recipe's files reaches the next run,
never this one. With `--dry-run` it prints the steps it would run, with the inputs substituted, and runs nothing.

| The run | Resumes with |
| --- | --- |
| paused at a handoff, or stopped at one in an unattended run | the step after the handoff |
| stopped by a failed step | that step, run again on the workspace as it is, after a person inspected or discarded what the step changed |
| stopped at Verify, or by the replay | Verify |

A completed run cannot be continued. To abandon a run, remove its workspace with
[`the-cook workspace remove`](workspaces.md#remove), which refuses uncommitted changes: discard them first with the
version control system. The branch `work/<name>` stays, so a later run that would create the same workspace needs that
branch deleted first.

## Guards

A recipe can only add: steps, required lanes and requirements. It cannot skip a lane, weaken a guard or fire an event
itself.

| Guard | Effect |
| --- | --- |
| Lanes are only added | the run itself runs every lane of `verify.require`, in full when the change selects nothing for it, on top of the lanes the change selects, and never skips one; the [lane rules](verify.md#lane-rules) of `verify` hold for a run as for any change. No key of a recipe removes a lane, a deny or a guard |
| The lifecycle's guards hold | the `completed` guard on a task and the `deliver` guards apply to a run's work as to any work ([built-in guards](lifecycle.md#built-in-guards)) |
| Events come from the stages | a run fires `start`, `verified`, `completed`, `deliver` and `blocked` only where the lifecycle fires them. Inside a step, `the-cook` commands fire no event, and `the-cook lifecycle run` and `the-cook make` refuse with exit 3 |
| `attempt` steps run under the attempt contract | the pinned launch, the unattended denies, the floor and the tamper check, as for every attempt. The tamper check compares the files under `.cook/` too, `.cook/recipes/` included, with their state when the step began, so the worker of a step cannot change a recipe, and a `--here` run keeps the uncommitted draft it runs |
| A run reads its recipe once | ([The run](#the-run)): from the base commit when it starts; `--here` reads the working tree and is refused with `--unattended` |
| Requirements are checked | `requires` is checked when the recipe is added and when it runs: a missing contract, role or lane exits 3 and names it, before anything is written or created |
| Inputs are validated | every input is checked against its type before anything runs, and stays one argument in a `run` step |

A `run` step runs with the rights of whoever started the run, outside every worker's permission checks, like a
lifecycle action ([safety](safety.md#lifecycle-actions-are-code)). Adding a recipe from a cookbook is therefore the
trust decision for everything its steps run: `recipe add` shows every file it copies as a diff to review, and from
then on the recipe changes only through the repository's own reviewed changes.

## Where recipes come from

### Writing one

[`the-cook recipe create <name>`](#the-cook-recipe-create) writes a skeleton in `.cook/recipes/<name>/`. Edit it into
the steps the change needs, try it with `./cook make <name> --here` in a managed workspace that holds the draft, which
reads the recipe from the working tree, and commit it like any other file. Once it is on the shared default line of
work (for git, pushed and merged), every run of it gets a workspace of its own.

### Adding one from a cookbook

[`the-cook recipe add <name> --cookbook <source>@<version>`](#the-cook-recipe-add) downloads the recipe from the tag,
verifies it against the tag's `checksums.txt`, checks its `requires` against the profile, and copies it into
`.cook/recipes/<name>/` with `origin` written, as one diff to review. From then on the copy is the repository's own:
people and workers edit it, and nothing fetches it again or renders from it.

### Updating an added recipe

[`the-cook recipe update <name> --cookbook <source>@<newer>`](#the-cook-recipe-update) merges a newer version into the
copy three ways: the version the copy came from, which `origin` names and the cache holds by its sha256, the copy as
the repository edited it, and the newer version. Hunks only one side changed apply cleanly. Hunks both sides changed
differently are written between conflict markers for a person or a worker to resolve. Either way `origin` moves to the
newer version at once: a clean merge exits 0, and one that leaves conflicts exits 3. A recipe that holds a conflict
marker is invalid, and `the-cook make` refuses it with exit 1 until the markers are resolved; resolving them is all
that is left to do, because the binary keeps no record of a pending merge.
[`the-cook fetch`](ingredients.md#the-cook-fetch) caches the version every added recipe came from, so the merge needs no
download of it.

### Recipes a station adds

A station lists recipes of its own cookbook tag, each with the sha256 of its archive. Applying the station verifies
each one against that hash and copies it into `.cook/recipes/<name>/` with its `origin`, as `recipe add` does, in the
same diff as the rest of the station ([stations](stations.md#recipes)). A recipe directory that already exists is kept
and reported, never overwritten; `recipe update` takes a newer version of it.

### Removing one

Delete its directory and commit; `the-cook` holds no other record of it. [`the-cook remove`](remove.md) deletes
`.cook/recipes/` with the rest of `.cook/`, in both removal modes, and lists each recipe in its plan, so a person can
keep a copy first.

### Publishing one

A cookbook holds each recipe in `recipes/<name>/`, as `recipe.yaml` and `files/`, without `origin`, and publishes it
under each tag as `recipe-<name>.tar.gz`, listed in the tag's `checksums.txt`; a station of the tag that adds it lists
the archive's sha256 ([cookbooks](stations.md#cookbooks)). A recipe meant for many repositories names no worker in its
`attempt` steps, because worker names are each repository's own, and requires only the lanes and roles it needs. The
cookbook tests it by running it in sample repositories.

## Reference

```text
the-cook make <recipe> [--<input> <value>]... [--task <id>] [--here] [--unattended] [--commit] [--withhold <text>]
              [--dry-run] [-- <worker args>...]
the-cook make <recipe> --help
the-cook make --continue [<workspace>] [--dry-run]
the-cook make --status [<workspace>]
the-cook recipe list [--cookbook <source>@<version>]
the-cook recipe show <name> [--cookbook <source>@<version>]
the-cook recipe add <name>... --cookbook <source>@<version> [--yes] [--dry-run]
the-cook recipe update <name> --cookbook <source>@<version> [--yes] [--dry-run]
the-cook recipe create <name> [--dry-run]
```

`recipe` follows the `the-cook <noun> <verb>` shape; `make` is a command of its own, because it is how a change goes
through the lifecycle, and a recipe runs only through it. Global flags `-C <dir>`, `--json` and `--verbose` apply to
every subcommand; see [CLI reference](cli.md).

**Jump to:** [`make`](#the-cook-make) | [`list`](#the-cook-recipe-list) | [`show`](#the-cook-recipe-show) |
[`add`](#the-cook-recipe-add) | [`update`](#the-cook-recipe-update) | [`create`](#the-cook-recipe-create) |
[flags](#the-cook-make) | [exit codes](#the-cook-make) | [recipe file](#the-recipe-file) | [step kinds](#step-kinds)

### `the-cook make`

Runs a recipe, or with `--continue` resumes a run, or with `--status` prints one ([the run](#the-run)).

| Argument or flag | Default | Meaning |
| --- | --- | --- |
| `<recipe>` | required, except with `--continue` and `--status` | A recipe in `.cook/recipes/` |
| `--<input> <value>` | none | One per input the recipe declares; a `bool` input is `--<input>` alone. Every input but a `bool` is required ([inputs](#inputs)) |
| `--help` | off | With `<recipe>`, print the recipe's description and its inputs, each with its type, the values of an `enum` and its description; run nothing |
| `--task <id>` | none; with `--here`, the workspace's task | Bind the run to a task: the workspace is created with `--task <id>`, `{task}` holds the id, and the run's events have scope `task` and carry the id in `COOK_TASK` |
| `--here` | off | Run in the managed workspace the current directory is in, with the recipe read from its working tree, instead of creating one; for drafting a recipe. Refused with `--unattended` |
| `--unattended` | off | Mark a run nobody drives, as a loop runner's: a handoff stops it as blocked instead of pausing it ([attended and unattended runs](#attended-and-unattended-runs)) |
| `--commit` | off | At Complete, after a passing replay, commit the work, with the recipe, its inputs and the verification summary in the commit body |
| `--withhold <text>` | none | Remove every occurrence of the text from everything the run prints, and print the text once at the end of a completed run: a loop runner's completion signal |
| `--dry-run` | off | Check the recipe, the inputs and the requirements, then print the workspace, the branch and every step with its inputs substituted; create and run nothing. With `--continue`, print the steps left to run |
| `--continue [<workspace>]` | off | Resume the paused or stopped run of the named managed workspace, or of the one the current directory is in, with the recipe content its record holds |
| `--status [<workspace>]` | off | Print the run record of the named managed workspace, or of the one the current directory is in; with `--json`, the record as it is |
| `-- <worker args>...` | none | Arguments for the worker of every `attempt` step |

A loop runner names `./cook make <recipe> --task <id> --unattended` as its command for each task, with the same
caller-protocol arguments as [`the-cook attempt`](attempts.md#how-a-loop-runner-calls-it): `--commit`,
`--withhold <signal>`, and its own arguments for the worker after `--`. Any exit status other than 0 is a task that is
not done. Without `--unattended` the run is attended, and a handoff pauses it with exit 3 and no completion text.

| Exit code | Meaning |
| --- | --- |
| 0 | Completed: the replay passed, and the work was committed with `--commit`; with `--status` or `--help`, printed; with `--dry-run`, printed |
| 1 | Error, and nothing is created: no recipe of that name where the run reads it ([the run](#the-run)); an invalid recipe file (an unknown key, a schema newer than the running release supports, an input named like a flag of `make`, an unknown placeholder, a conflict marker); a missing, unknown or invalid input; `{task}` without `--task`; an `attempt` step without a prompt and no prompt on stdin; a workspace name that is not valid, or whose workspace or branch exists; `--here` with `--unattended`, outside a managed workspace, or in one whose run is not completed; `--continue` or `--status` where there is no run record; `--continue` on a completed run |
| 2 | A lane failed, at Verify or in the replay: the run is stopped and fired `blocked` |
| 3 | A person must act: a contract, role or lane the recipe requires and the repository lacks, or an `attempt` step whose worker is not declared with role `unattended`, each named, before anything is created; a missing prerequisite, such as a program or an ingredient that is not cached; a step that stopped the run; a handoff, which pauses an attended run and stops an unattended one; a lane Verify cannot select, named; `the-cook make` inside an attempt or a step |

Environment: `run` steps receive `COOK_RECIPE`, `COOK_RUN` and `COOK_INPUT_<NAME>` ([`run`](#run)); the worker of an
`attempt` step receives `COOK_ATTEMPT=1`; the actions of the events the run fires receive the
[lifecycle variables](lifecycle.md#environment), and while the receipt records the run also `COOK_RECIPE` and
`COOK_INPUT_<NAME>`.

Files read: the recipe directory, the profile, the policy and the hooks file, from the base commit of the workspace the
run creates, or from the working tree with `--here`; with `--continue`, the recipe content `run.json` holds instead of
the recipe directory; the version-control ingredient and the workers' ingredients, from the cache; stdin, for an
`attempt` step without a prompt; the receipt a step left in the workspace. Files written: what
[`workspace create`](workspaces.md#reference) writes, the workspace, its branch and its session, unless `--here`;
`run.json` in the workspace's state directory, with the recipe's content and digest; whatever the steps write; the
receipt `verified.json`; with `--commit`, a commit on the workspace's branch; `.work/lifecycle.jsonl` in the primary
checkout, for the events the run fires.

### `the-cook recipe list`

Without `--cookbook`, prints the recipes in `.cook/recipes/`, each with its description, its origin
(`<source>@<version>`, or `own` for a recipe without `origin`), and whether the profile meets its `requires`; an
invalid recipe is listed with what makes it invalid. It reads only the repository's files.

With `--cookbook <source>@<version>`, prints the recipes that tag publishes, each with its description, its
requirements, and the version `origin` records for a recipe of that name in `.cook/recipes/`, if any. It works in a
repository that is not scaffolded yet.

| Flag | Default | Meaning |
| --- | --- | --- |
| `--cookbook <source>@<version>` | none | List that cookbook tag's recipes instead of the repository's |

| Exit code | Meaning |
| --- | --- |
| 0 | Listed |
| 1 | With `--cookbook`: the source cannot be reached, the tag has no `checksums.txt`, or an archive does not match it |

Files read: `.cook/recipes/`, `.cook/config.yaml` when present; with `--cookbook`, the tag's `checksums.txt` and
archives. Files written: with `--cookbook`, each verified archive into `$COOK_HOME/cookbook/`.

### `the-cook recipe show`

Prints the recipe's `recipe.yaml` and every file under its `files/`: a recipe in `.cook/recipes/`, or with
`--cookbook` the recipe of that name in that tag. It compares nothing with the repository; `recipe add --dry-run` and
`recipe update --dry-run` show what would change.

| Flag | Default | Meaning |
| --- | --- | --- |
| `<name>` | required | The recipe |
| `--cookbook <source>@<version>` | none | Read the recipe from that cookbook tag instead of the repository |

| Exit code | Meaning |
| --- | --- |
| 0 | Printed |
| 1 | A name `.cook/recipes/` does not hold, which names `--cookbook`; with `--cookbook`, a name the tag does not publish, a source that cannot be reached, or an archive that does not match the tag's `checksums.txt` |

Files read: `.cook/recipes/<name>/`; with `--cookbook`, the tag's `checksums.txt` and the archive. Files written: with
`--cookbook`, the verified archive into `$COOK_HOME/cookbook/`.

### `the-cook recipe add`

Copies one or more recipes a cookbook tag publishes into the repository:

1. **Check.** The repository needs `.cook/config.yaml`; without it, `add` exits 1 and names `the-cook scaffold`.
   `.cook/recipes/<name>/` must not exist; one that exists exits 1 and names `recipe update`. `add` downloads the tag's
   `checksums.txt` and each `recipe-<name>.tar.gz` over HTTPS, verifies each archive against it, checks each
   `recipe.yaml`, and checks each recipe's `requires` against the profile: a missing contract, role or lane exits 3 and
   names it. On any failure nothing is written.
2. **Plan** each recipe's directory, `recipe.yaml` with `origin` written (the cookbook, the tag and the archive's
   sha256) and every file of its `files/`.
3. **Confirm.** Show every file to write as one diff, then ask. `--yes` skips the question. Without a terminal and
   without `--yes`, nothing is written and the command exits 3. `--dry-run` prints the diff and writes nothing.
4. **Write** the directories, and cache each archive.

A recipe renders nothing, so `add` does not render.

| Flag | Default | Meaning |
| --- | --- | --- |
| `<name>...` | required | One or more recipes the tag publishes |
| `--cookbook <source>@<version>` | required | The cookbook and the tag to copy from |
| `--yes` | off | Write without asking; required without a terminal |
| `--dry-run` | off | Print the files to write; write nothing |

| Exit code | Meaning |
| --- | --- |
| 0 | Added; `--dry-run` printed |
| 1 | Error: a repository that is not scaffolded; a recipe directory that exists, which names `recipe update`; no network or a source that cannot be reached; a name the tag does not publish; an archive that does not match the tag's `checksums.txt`; an invalid `recipe.yaml`, one whose name differs from the request, or one whose schema is newer than the running release supports (the message names the release needed). Nothing is written |
| 3 | A contract, role or lane a recipe requires that the repository lacks, named; or confirmation needed and there is no terminal: rerun with `--yes`. Nothing is written |

Files read: `.cook/config.yaml`, `.cook/recipes/`, the tag's `checksums.txt` and the named archives. Files written:
`.cook/recipes/<name>/` for each recipe; `$COOK_HOME/cookbook/<sha256>.tar.gz` for each verified archive.

### `the-cook recipe update`

Merges a newer version of an added recipe into the repository's copy:

1. **Check.** `.cook/recipes/<name>/` must exist and hold an `origin`; a recipe without one is the repository's own,
   and `update` exits 1. `update` downloads the tag's `checksums.txt` and `recipe-<name>.tar.gz`, verifies the archive
   against it, checks its `recipe.yaml` and its `requires` against the profile. The version the copy came from is the
   merge base: `update` reads it from the cache by `origin.sha256`, or downloads it from `origin.source` and verifies
   it against that hash.
2. **Merge** each file three ways, line by line: the base, the copy, and the newer version. A hunk only one side
   changed takes that side, and a hunk both sides changed the same way applies once. A hunk both sides changed
   differently is a conflict: the file keeps both, between conflict markers. A file only the newer version adds is
   added; a file it deletes is deleted, or a conflict when the copy changed it. `origin` moves to the newer version
   whether or not the merge left conflicts.
3. **Confirm.** Show the result as one diff, with every conflict, then ask, as `add` does.
4. **Write** the merged files with `origin` moved, and cache the archive. When the merge left conflicts, `update`
   exits 3 once it has written them: the recipe needs a person or a worker before it runs again.

```text
<<<<<<< .cook/recipes/nestjs-module (this repository)
  lanes: [unit, e2e, contract]
||||||| github:acme/cookbook@v1.2.0
  lanes: [unit, e2e]
=======
  lanes: [unit, e2e, openapi]
>>>>>>> github:acme/cookbook@v1.3.0
```

A recipe with a conflict left in any of its files is invalid: `make` refuses it with exit 1 and `doctor` reports it
until a person or a worker resolves the conflict. Resolving the markers is all that is left: `origin` already names the
newer version and the binary keeps no record of a pending merge, so the resolution is an ordinary change for the
repository to review. Under `fleet --deliver`, a repository where `recipe update` exits 3 is not delivered, as for any
command that exits non-zero there ([fleet](fleet.md)).

| Flag | Default | Meaning |
| --- | --- | --- |
| `<name>` | required | A recipe in `.cook/recipes/` with an `origin` |
| `--cookbook <source>@<version>` | required | The cookbook and the tag to take the newer version from |
| `--yes` | off | Write without asking; required without a terminal |
| `--dry-run` | off | Print the merged result and its conflicts; write nothing |

| Exit code | Meaning |
| --- | --- |
| 0 | Updated: the merge was clean and `origin` moved; already at that version; `--dry-run` printed |
| 1 | Error: no recipe of that name, or one without `origin`; no network or a source that cannot be reached; a name the tag does not publish; an archive that does not match the tag's `checksums.txt`, or a base that does not match `origin.sha256`; an invalid or too new `recipe.yaml`. Nothing is written |
| 3 | The merge left conflicts: the files are written with their markers, which the output lists, and `origin` names the newer version. Or, with nothing written: a contract, role or lane the newer version requires that the repository lacks, named; or confirmation needed and there is no terminal |

Files read: `.cook/config.yaml`, `.cook/recipes/<name>/`, `$COOK_HOME/cookbook/<sha256>.tar.gz` for the base, the
tag's `checksums.txt` and the archive, and `origin.source` when the base is not cached. Files written:
`.cook/recipes/<name>/`, merged, with `origin` moved; each verified archive into `$COOK_HOME/cookbook/`.

### `the-cook recipe create`

Writes a skeleton recipe for the repository's own use into `.cook/recipes/<name>/recipe.yaml`, without `origin`, and
prints its path. It writes nothing else and asks nothing: the file is new, and the repository reviews it when it
commits it. With `--dry-run` it prints the skeleton and writes nothing.

```yaml
schema: 1
name: release-notes
description: one line saying what change this recipe makes
inputs:
  name: { type: slug, description: what the change is called }
requires:
  contracts: [vcs]
workspace: "release-notes-{name}"
steps:
  - handoff: "Make the change, run ./cook verify with the lanes it needs, then continue."
verify: { require: [] }
```

| Flag | Default | Meaning |
| --- | --- | --- |
| `<name>` | required | The recipe's name: `[a-z][a-z0-9-]*` |
| `--dry-run` | off | Print the skeleton; write nothing |

| Exit code | Meaning |
| --- | --- |
| 0 | Written; `--dry-run` printed |
| 1 | A repository that is not scaffolded, an invalid name, or a recipe directory that exists |

Files read: `.cook/config.yaml`, `.cook/recipes/`. Files written: `.cook/recipes/<name>/recipe.yaml`.

## Examples

### The repository's own recipe: `migration`

`acme-api` adds a database migration every few weeks, and each time someone forgets the down test or the volume check.
A developer writes the [recipe above](#the-recipe-file), with the test template it names, and commits it through a
reviewed pull request:

```sh
./cook recipe create migration
$EDITOR .cook/recipes/migration/recipe.yaml .cook/recipes/migration/files/down-test.ts
git add .cook/recipes/migration && git commit -m "chore: add the migration recipe"
```

Once the commit is pushed and on the shared default line of work, each migration is one run. The profile declares the
worker `coder` with role `unattended` and the lanes `unit`, `e2e` and `migrations`. Each line of the run carries the
icon of its kind: 🍳 the workspace and the `run` and `template` steps, ⏳ the `attempt` step and the handoff, 🧪 each lane,
and the outcome's icon last ([CLI standards](../ux/cli-standards.md#icons)):

```text
$ ./cook make migration --name add-invoice-index
🍳 make migration: workspace .work/workspaces/migration-add-invoice-index, branch work/migration-add-invoice-index, from main
🍳 step 1/4 run: npm run migration:create -- add-invoice-index
🍳 step 2/4 template: src/migration/add-invoice-index.spec.ts
⏳ step 3/4 attempt: coder
⏳ step 4/4 handoff: Check the generated SQL against production data volumes, then continue.
  run: ./cook make --continue migration-add-invoice-index
$ cd .work/workspaces/migration-add-invoice-index
$ ./cook verify --lane unit=migration --skip e2e="no route, controller or guard changed" \
    --skip migrations="the migration recipe runs it in full"
$ ./cook make --continue
🧪 verify: lane unit
🧪 verify: lane migrations
💎 make migration: completed in migration-add-invoice-index; lanes unit, migrations passed; replayed from main
  commit the work, then run: ./cook lifecycle run deliver
$ git add -A && git commit -m "feat(db): add the invoice index migration"
$ ./cook lifecycle run deliver
```

The developer read the SQL at the handoff and ran `./cook verify` for feedback, which gave the run its selection. The
run ran `unit` with that pattern and `migrations`, which the recipe requires, in full, though the developer's selection
skipped it; then it replayed both from the base. The run had no task and no `--commit`, so the developer committed and
delivered by hand.

### A recipe from a cookbook: `nestjs-module`

```text
$ ./cook recipe show nestjs-module --cookbook github:acme/cookbook@v1.2.0
$ ./cook recipe add nestjs-module --cookbook github:acme/cookbook@v1.2.0
verified against github:acme/cookbook@v1.2.0: nestjs-module
requires contracts vcs, worker; role unattended; lanes unit, e2e: met
new .cook/recipes/nestjs-module/recipe.yaml
new .cook/recipes/nestjs-module/files/module.ts
new .cook/recipes/nestjs-module/files/module.e2e-spec.ts
add these recipes? [y/N] y
added nestjs-module from github:acme/cookbook@v1.2.0
$ git add .cook/recipes && git commit -m "chore: add the nestjs-module recipe"
```

The copy, with the `origin` that `add` wrote:

```yaml
# .cook/recipes/nestjs-module/recipe.yaml
schema: 1
name: nestjs-module
description: Add a NestJS module with its first route and an end-to-end test
origin:
  source: github:acme/cookbook
  version: v1.2.0
  sha256: 555b88509281b5b2248b73bcb446b151b3b46f93f741ccf9dc004b19d08ba93f
inputs:
  name: { type: slug, description: the module's directory name, such as invoice-export }
  class: { type: string, description: the prefix of the module's class names, such as InvoiceExport }
requires:
  contracts: [vcs, worker]
  roles: [unattended]
  lanes: [unit, e2e]
workspace: "module-{name}"
steps:
  - template: { from: module.ts, to: "src/modules/{name}/{name}.module.ts" }
  - template: { from: module.e2e-spec.ts, to: "test/{name}.e2e-spec.ts" }
  - attempt:
      prompt: >-
        Register {class}Module from src/modules/{name}/{name}.module.ts in the application module, give it its
        first route, and make test/{name}.e2e-spec.ts exercise that route. Finish by running ./cook verify with
        the unit and e2e lanes.
verify: { require: [unit, e2e] }
```

The team edits the copy to its own conventions, for example by requiring a `contract` lane. When the cookbook
publishes v1.3.0, the update keeps that edit wherever v1.3.0 did not change the same lines:

```sh
./cook recipe update nestjs-module --cookbook github:acme/cookbook@v1.3.0 --dry-run
./cook recipe update nestjs-module --cookbook github:acme/cookbook@v1.3.0   # exits 3 when it leaves conflicts
# resolve the conflicts the output lists; origin already names v1.3.0
git add .cook/recipes && git commit -m "chore: update the nestjs-module recipe to v1.3.0"
```

### A loop runner runs `implement-task` for each task

`acme-api` applied the [`agentic-coding` station](stations.md#the-agentic-coding-station), which added this recipe
and wrote the loop runner's configuration, `.acme-loop.toml`, to run it for each task:

```yaml
# .cook/recipes/implement-task/recipe.yaml
schema: 1
name: implement-task
description: Implement one task of the tracker in a workspace of its own
origin:
  source: github:acme/cookbook
  version: v1.2.0
  sha256: f584da225933b3a72ab0fc922ae90eae3872e629529fb7c356ebefcfedc5a88a
requires:
  contracts: [vcs, worker]
  roles: [unattended]
workspace: "task-{task}"
steps:
  - attempt: {}      # the only unattended worker, on the prompt the loop runner passes on stdin
```

```toml
# .acme-loop.toml, the line that names the command for each task
worker = ["./cook", "make", "implement-task", "--task", "{id}", "--unattended", "--commit", "--"]
```

For each ready task, such as `acme-api-7f2`, the loop runner, started in the primary checkout, runs
`./cook make implement-task --task acme-api-7f2 --unattended --commit --` with the task's text on stdin:

| Stage | What happens |
| --- | --- |
| Start | the workspace `task-acme-api-7f2` on `work/task-acme-api-7f2`, at the revision of the shared default line of work, bound to the task; `start` runs the station's `claim-task` action |
| Implement | `acme-agent` works on the task under the attempt contract and runs `./cook verify` with the lanes the change needs, for feedback |
| Verify | the run runs the lanes of that selection itself, under the lane rules of the repository's profile, and writes the receipt |
| Complete | the replay from the base, the commit, then `completed`, which runs `close-task`, and `deliver` for any automatic `deliver` action of scope `task` |
| A stop | `blocked`, which runs `release-task` with the reason; the workspace stays, and the loop runner sees a non-zero exit |

Each task becomes one verified commit on a branch of its own, delivered on its own or after judgement. A person picks a
stopped task up where it stopped:

```sh
./cook make --status task-acme-api-9c1      # the stage, the step and the reason
cd .work/workspaces/task-acme-api-9c1
# fix what stopped it, or run ./cook verify with the lane the message names
./cook make --continue
```

Tasks that must build on one another in a single branch run through [`the-cook attempt`](attempts.md) in one
workspace instead.

## Guidelines

- Do write a recipe for a change the repository makes often; make a one-off change through the stages by hand.
- Do read every `run` step of a recipe before adding it: it runs with your credentials, outside every worker's
  permission checks, each time anyone runs the recipe.
- Do tell the worker of an `attempt` step to run `./cook verify` with the lanes the change needs: its selection is
  where the run's Verify starts, and a lane the lane rules demand that nobody selected or skipped stops the run.
- Do put the check only a person can make into a `handoff`, and keep handoffs out of a recipe a loop runner runs: in
  an unattended run a handoff stops the run as blocked.
- Do pass `--unattended` wherever nobody drives the run, as in a loop runner's command: without it, a handoff pauses
  the run and waits for someone who is not there.
- Do leave `worker` out of a recipe meant for a cookbook: worker names are each repository's own.
- Do get a recipe onto the shared default line of work (for git, pushed and merged) before running it in a new
  workspace, and try a draft with `--here` in a managed workspace that holds it.
- Do take a newer version of an added recipe with `recipe update`, and resolve the conflicts it leaves before the next
  run of the recipe.
- Don't edit `origin` by hand: `recipe update` merges from the version it names.
- Don't put untrusted values into a prompt when fixed text does the job; the worker reads the changed files itself.
- Don't write delivery into a recipe: the repository's own `deliver` actions deliver every change, a recipe's included.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| `make` exits 1: no recipe of that name | The recipe is not at the revision of the shared default line of work this checkout last learned, where a new workspace starts from (for git: committed but not pushed, or pushed and not yet learned here), or not in the working tree a `--here` run reads | Get it onto the shared default line of work, and have the version control program learn what is shared; or create a workspace that holds it and run with `--here` |
| `make` exits 1 naming an input | The input is missing, unknown, or its value does not fit its type or begins with `-`; the message gives its description | Pass `--<input> <value>`; `./cook make <name> --help` lists every input |
| `make` exits 1: an input named like a flag | The recipe declares an input with the name of a flag of `make`, such as `task` or `here` | Rename the input in the recipe, with every placeholder that names it |
| `make` exits 1: the branch `work/<name>` exists | An earlier run with the same inputs, or a workspace of that name, kept its branch | Delete the branch if its work is done, or run with other inputs |
| `make` or `recipe add` exits 3 naming a contract, a role or a lane | The recipe requires it and the profile lacks it | Pin the ingredient, declare the worker or the lane, or apply a station that does; `./cook doctor` lists every recipe's unmet requirements |
| `make` exits 3 at a handoff | The run is attended, and the recipe hands over to whoever drives it here | Do what the text asks, then `./cook make --continue <workspace>` |
| A loop runner never counts a task done, and each run ends paused at a handoff | The loop runner's command lacks `--unattended`, so each run is attended: it pauses at the handoff, exits 3 and prints no completion text | Add `--unattended` to the command; keep handoffs out of recipes a loop runner runs |
| A handoff fired `blocked` | The run was started with `--unattended`, and a handoff needs a person | Expected: do what it asks, then continue; keep handoffs out of recipes a loop runner runs |
| A run stopped at a `run` step | The command exited non-zero | `./cook make --status <workspace>` names the step; fix the cause in the workspace, then `./cook make --continue <workspace>` |
| A run stopped at a `template` step | Its path exists, and a template never overwrites a file | Move the file aside or delete it, then continue |
| A run stopped at an `attempt` step | The worker exited non-zero, or the tamper check found a changed tool-owned file, a file under `.cook/` or a context file | Read the step's output; fix the cause, then continue |
| A run stopped at Verify naming a lane | The lane rules demand a lane no step's receipt selected or skipped, or a required lane without patterns has no `full` command | Run `./cook verify` in the workspace with the lane selected, then continue |
| `make` exits 2 | A lane failed at Verify or in the replay | Fix the change in the workspace, then continue: the run verifies again |
| An edit to a recipe does not reach a paused or stopped run | A run reads its recipe once, and `make --continue` runs the content its record holds | Expected: continue or abandon the run; the next run reads the edited recipe from the shared default line of work |
| `make` exits 1: a conflict marker | `recipe update` left a conflict in the recipe | Resolve it and commit the recipe; `origin` already names the newer version |
| `recipe update` exits 3 listing conflicts | The repository's copy and the newer version changed the same lines differently | Resolve the markers; nothing else is needed, because `origin` already moved |
| `recipe add` exits 1 naming `recipe update` | The repository already has a recipe of that name | `./cook recipe update <name> --cookbook <source>@<version>` |
| `recipe update` exits 1: no `origin` | The recipe is the repository's own | Nothing to update from; edit it |
| `recipe update` exits 1: the base cannot be fetched | The version the copy came from is not cached and its source is gone | Run `./cook fetch` on a machine that cached it; otherwise delete the copy, `recipe add` the newer version and carry your edits over by hand |
| No `completed` event after a run | The run had no task, and work of scope `change` has no `completed` | Expected: deliver with `./cook lifecycle run deliver` |
| The automatic `deliver` did not fire after a run with a task | The work was not concluded, often because the run had no `--commit` | Commit the work, then `./cook lifecycle run deliver` |
