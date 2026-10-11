# verify

## Objective

**Problem.** A developer, a reviewer, or an automated step that delivers a change needs to know whether the change was
checked. A coding agent's word that the tests pass does not say which checks ran, whether they were the ones the
change needs, or whether the code changed after they passed. Coding agents skip checks, run unrelated ones and keep
editing after a passing run, and nobody downstream can tell.

**Goal.** A change counts as checked only when the checks it needs passed on exactly its current content, and any later
edit makes it unchecked again.

## Overview

`the-cook verify` serves the Verify stage of the [development lifecycle](lifecycle.md): it writes the proof a change
carries into the stages after it.

`the-cook verify` runs the checks a change needs and, when they pass, writes a receipt: a small record bound to the
change's base commit and to the exact content of every changed file. Any later edit invalidates it. The checks are the
lint command, validators, build and test **lanes** the [profile](profile.md#verify) declares; a lane is a named family
of test commands, such as `unit` or `e2e`, run with the test patterns you give it (`--lane unit=invoice`). The
[`completed` guard](tasks.md#the-completed-guard) on a task and [`the-cook attempt`](attempts.md), which runs a worker
unattended, require a valid receipt; `the-cook verify --check` tells you whether you have one. A
[recipe run](recipes.md#the-run) runs the lanes itself at its Verify stage: every lane the change selects and every lane
its recipe requires ([lanes a recipe requires](#lanes-a-recipe-requires)).

Commands are named `the-cook <command>` on this page. The examples run them through the repository's wrapper,
`./cook <command>`, from the root of a checkout; with a global install, `the-cook <command>` is equivalent
([wrapper](wrapper.md)).

## When to use it

- Before calling a change done: lint, build and the relevant test lanes in one command.
- Before firing `completed` for a task, which the [`completed` guard](tasks.md#the-completed-guard) refuses without a
  valid receipt.
- In a worker that [`the-cook attempt`](attempts.md) runs, as the last step before the worker exits: the attempt
  replays what the receipt selects.
- With `--full`, before merging a change that needs every suite.

In a recipe run, the worker of an `attempt` step or a person at a handoff runs `the-cook verify` with the lanes the
change needs, as for any change: it is feedback for them, and the selection the run starts from. After the recipe's
last step, the run's Verify stage runs those lanes itself, with every lane the recipe requires added, and that run,
replayed from the base, is the proof ([recipes](recipes.md#verify)).

While you iterate, run single tests with the repository's own commands, and run `the-cook verify` once the change is
ready.

## How it works

### Base and changed files

The base is the commit the change is measured against. `the-cook verify` reads it, and the changed files, through the
[version-control contract](ingredients.md#the-version-control-contract), which an ingredient fills:

| Where you run it | Base |
| --- | --- |
| A [managed workspace](workspaces.md) | the commit the workspace started from, recorded in its session |
| Any other checkout | the merge-base of the head commit with the revision of the shared default line of work, as the version-control ingredient's `default_revision` reports it from what the checkout last learned |
| With `--base <ref>` | that commit; in a managed workspace, only the recorded base or one of its ancestors |

A base may only move earlier, never later: an earlier base adds files to the check, a later one would leave changes
out.

Changed files are the files that differ from the base (committed or not) plus untracked files that are not ignored, as
the version-control ingredient lists them.

### The run

1. Delete any existing receipt.
2. Fingerprint the change: a hash over the base and, for each changed file, its path, mode and content (a symlink's
   target; a marker for a deleted file).
3. Check the [lane rules](#lane-rules); stop with an explanation if the selection is not allowed.
4. Lint each changed file that matches `verify.lint_paths` and still exists, with `commands.lint`.
5. Run each validator whose `when` matches a changed file.
6. Build with `commands.build` when the change is an application change, or with `--full`.
7. Run the lanes: `select` once per pattern, or `full` with `--full`.
8. Fingerprint again. If any file changed while the checks ran, fail: the checks did not see the final content.
9. Write the receipt to `verified.json` in the checkout's state directory (`state.dir`, default `.work`).
10. Fire the `verified` [lifecycle event](lifecycle.md): scope `task` in a workspace bound to a task, `change`
    otherwise. Actions declared with `auto: true` run; `COOK_RECEIPT` and `COOK_SUMMARY` carry the receipt's path and
    its one-line summary. A failing automatic event is reported on stderr and recorded in the
    [lifecycle log](lifecycle.md#the-lifecycle-log). The receipt stays written and the exit code is the verification's.

`the-cook verify --check` never fires `verified`.

All commands come from the [profile](profile.md#verify) and run from the checkout root.

### Lane rules

A change is an **application change** when a changed file matches `verify.app_paths`. For an application change, without
`--full`:

1. Every lane must be either selected (`--lane <name>=<pattern>`) or skipped with a reason (`--skip <name>=<reason>`).
2. A lane whose `required_when` matches a changed file cannot be skipped.
3. At least one lane must be selected. A reason is free text that no check can judge, so it may skip a lane, never all
   of them.

For example, take the Node API in the [profile examples](profile.md#a-node-api), whose `e2e` lane is required when a
changed file matches `src/**/*.controller.ts`. A change to `src/invoices/invoice.service.ts` passes the rules with
`--lane unit=invoice --skip e2e="no route, controller or guard changed"`. Rule 1 refuses `--lane unit=invoice` alone,
and rule 3 refuses skipping both lanes. Rule 2 refuses any `--skip e2e=<reason>` for a change to
`src/invoices/invoices.controller.ts`.

With `--full`, every lane that has a `full` command runs it; a lane without one still follows rules 1 and 2.

A change that is not an application change (documentation, configuration outside `app_paths`) needs no lane and no
build; lint and validators still run, and you may still select lanes.

A lane named twice, both selected and skipped, or not declared in the profile is an error.

### Lanes a recipe requires

A [recipe](recipes.md#the-recipe-file) names the lanes its kind of change always needs under `verify.require`, such as
`unit` and `migrations` for a migration. At its Verify stage a recipe run runs the lanes itself, as this command does:
every lane the change selects and every one of those, and the lane rules above hold for it as for any change:

- the change's selection, the lanes with their patterns and the skip reasons, is the one of the receipt a step left in
  the workspace, and with no such receipt the change selects no lane. A `verify` a worker runs inside an `attempt` step
  is feedback for the worker, not the proof;
- a required lane runs and is never skipped, whatever its `required_when` says and whether or not the change is an
  application change: with the patterns the selection gives it, or in full, with its `full` command, when the
  selection gives it none or skips it;
- when the lane rules demand a lane the selection neither selects nor skips, or a required lane without patterns has
  no `full` command, the run stops at Verify with exit 3 and names the lane: run `./cook verify` with it selected in
  the workspace, then `./cook make --continue`;
- a required lane the profile does not declare is a missing requirement: the run exits 3 naming it before any step
  runs, and `the-cook recipe add` refuses the recipe the same way;
- a lane that fails, at Verify or in the replay, stops the run with exit 2.

The run's receipt records, for each lane, the patterns it ran or that it ran in full ([the receipt](#the-receipt)), and
the run's Complete stage replays it from the base.

A recipe can only add lanes to what the rules above demand; it cannot skip one or loosen a rule
([guards](recipes.md#guards)).

### The receipt

```json
{
  "base": "4f1c2a9e0b7d3c5a8e6f1b2d4c7a9e0f3b5d8c1a",
  "fingerprint": "a3f5…",
  "lanes": { "unit": { "patterns": ["invoice", "rounding"], "full": false } },
  "skipped": { "e2e": "no route, controller or guard changed" },
  "full": false
}
```

| Field | Meaning |
| --- | --- |
| `base` | the commit the change was measured against |
| `fingerprint` | the hash of the change's content at verification time |
| `lanes` | for each lane that ran: `patterns`, the patterns it ran, and `full`, whether it ran its `full` command instead, with `--full` or as a lane a recipe requires that the change selected nothing for; `patterns` is absent when `full` is true |
| `skipped` | for each skipped lane, the reason given |
| `full` | whether `--full` ran: the build, and every lane that has a `full` command in full |
| `recipe` | for a receipt a recipe run recorded: the recipe's name and digest and the run's inputs, which actions read as `COOK_RECIPE` and `COOK_INPUT_<NAME>` ([lifecycle environment](lifecycle.md#environment)) |

The receipt lives in the checkout's state directory, which the version-control ingredient's renders ignore, so it is
local to that checkout.

### Freshness

`the-cook verify --check` accepts the receipt only if:

- it exists and is well formed;
- in a managed workspace, its base is the recorded base or an ancestor of it;
- the fingerprint recomputed now, against the receipt's base, equals the recorded one.

| After verifying, you… | Receipt |
| --- | --- |
| commit the verified files | still valid: content against the base is unchanged |
| edit, add, delete, `chmod` a changed file, or retarget a symlink | invalid |
| add an untracked, non-ignored file | invalid |
| rebase or merge in other commits | invalid: the content against the base changed |
| edit only ignored files, such as those `.gitignore` lists | still valid |

### Who relies on it

| Consumer | Uses the receipt to |
| --- | --- |
| The [`completed` guard](tasks.md#the-completed-guard) | refuse `completed` with scope `task` without a valid receipt, when the verify module is enabled |
| [`the-cook attempt`](attempts.md#the-attempt-contract) | read which lanes and skip reasons the worker chose, then **replay** them itself from a tree the worker cannot write. The receipt only selects the checks, and the replay is the proof |
| The [`deliver` guard](lifecycle.md#built-in-guards) | refuse delivery of work without a valid receipt for its base, when the verify module is enabled |
| [`the-cook make`](recipes.md#the-run) | read the selection a step's receipt left, then run every lane it selects and every lane the recipe requires itself, write the run's receipt, and complete the run only once that receipt is replayed from the base, like an attempt's |

### The `verify` block in `AGENTS.md`

With the verify module on, `the-cook render` writes the repository's checks into the `verify` block of `AGENTS.md`, in
plain language, from the profile's `commands` and `verify` sections, and says how to record them with
`./cook verify`. For the Node API in the [profile examples](profile.md#a-node-api):

```markdown
<!-- cook:verify -->
A change is done when its checks pass. Run them from the root of the checkout; `{file}` stands for one changed file
and `{pattern}` for a test pattern.

- Lint each changed file that matches `src/**/*.{ts,js}` or `test/**/*.ts`: `npx eslint --fix {file}`.
- A change to a file that matches `src/**`, `test/**`, `package.json`, `package-lock.json`, `tsconfig*.json` or
  `Dockerfile` is an application change. Build it with `npm run build`, and give every test lane either patterns that
  select the tests exercising the change or a skip reason a reviewer can judge. At least one lane runs.
  - `unit`: `npx vitest run {pattern}`
  - `e2e`: `npx vitest run --config vitest.e2e.config.ts {pattern}`. It cannot be skipped when a changed file matches
    `src/**/*.controller.ts`, `src/**/routes/**` or `src/**/guards/**`.
- When a changed file matches `openapi/**` or `src/**/*.dto.ts`, run `npm run openapi:check`.

A missing service or dependency is blocked work, never a reason to skip a lane. Record the result with
`./cook verify --lane <lane>=<pattern>` or `--skip <lane>=<reason>`; `./cook explain verify` has the rules.
<!-- /cook:verify -->
```

`verify` is a [tool block](concepts.md#convention-blocks-and-tool-blocks): the lane rules, the receipt and its replay
exist only while `the-cook` manages the repository, so the block describes how to use `the-cook` here and names
`./cook verify`. An eject ([`the-cook remove --eject`](remove.md#eject)) removes it, as does
`the-cook remove --module verify`. A repository that wants its checks written down after an eject keeps them in
`AGENTS.md` prose outside the markers.

## Reference

```text
the-cook verify [--lane <name>=<pattern>]... [--skip <name>=<reason>]... [--full] [--base <ref>] [--check]
```

| Flag | Default | Meaning |
| --- | --- | --- |
| `--lane <name>=<pattern>` | none | Run lane `<name>`'s `select` command with `<pattern>`. Repeat for more patterns or lanes |
| `--skip <name>=<reason>` | none | Skip lane `<name>`, recording `<reason>` |
| `--full` | off | Run every lane's `full` command and the build |
| `--base <ref>` | see [Base](#base-and-changed-files) | Measure against this commit |
| `--check` | off | Check the existing receipt; run nothing |

| Exit code | Meaning |
| --- | --- |
| 0 | Passed and the receipt is written; or, with `--check`, the receipt is valid |
| 1 | Error: a lane rule is not met, unknown lane, invalid `--base`, files changed during verification |
| 2 | A check failed (lint, validator, build or lane), so no receipt was written; or, with `--check`, no valid receipt |

Files read: the profile, the hooks file, the history and the changed files through the version-control ingredient,
`session.json` in the checkout's state directory. Files written: `verified.json` in the checkout's state directory,
`lifecycle.jsonl` in the primary checkout's state directory; whatever the repository's commands and `verified` actions
write.

## Examples

### A Node API

```sh
# a service change, covered by unit tests; no HTTP surface touched
./cook verify --lane unit=invoice --skip e2e="no route, controller or guard changed"

# a controller change: e2e cannot be skipped
./cook verify --lane unit=invoice --lane e2e=invoices

# a documentation-only change
./cook verify

# before merging something broad
./cook verify --full
```

### A Terraform repository

```sh
./cook verify --lane validate=envs/staging --lane plan=envs/staging
# a change under modules/** requires the plan lane:
./cook verify --lane validate=envs/staging --skip plan="no backend access"   # refused
```

### A Go repository

```sh
./cook verify --lane test=./internal/billing/... --lane vet=./internal/billing/...
./cook verify --check && echo "receipt valid"
```

## Guidelines

- Do select the tests that exercise what you changed. Unrelated tests that pass prove nothing about the change.
- Do write skip reasons a reviewer can judge: "no route, controller or guard changed", not "n/a".
- Do treat a missing service or dependency as blocked work and say so; never skip a lane to get around it.
- Do run `verify` last: any edit after it invalidates the receipt.
- Don't edit files while `verify` runs; it fails rather than certify content it did not see.
- Don't commit the receipt (`.work/verified.json` by default); it is ignored on purpose.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| Refused before running: a lane has no selection | An application change with neither `--lane` nor `--skip` for that lane | Add one for every lane |
| Refused before running: a lane cannot be skipped | A changed file matches that lane's `required_when` | Select patterns for it |
| `--check` fails right after a pass | A formatter or the editor touched a file after verification | Run `verify` again |
| `--base` refused in a workspace | The ref is later than the workspace's recorded start | Use the start commit or an earlier one |
| A lane passes with zero tests | The test runner exits 0 when nothing matches | Configure the runner to fail on no tests, or fix the pattern |
| The build runs for a docs change | A doc path matches `verify.app_paths` | Narrow `app_paths` |
| A recipe run stops at Verify naming a lane | The lane rules demand a lane no step's receipt selected or skipped, or a lane the recipe requires has no patterns and no `full` command | Run `./cook verify` in the workspace with that lane selected, then `./cook make --continue` |
| A recipe run exits 3 naming a lane | The recipe requires a lane the profile does not declare | Declare the lane in the profile, or change the recipe's `requires.lanes` and `verify.require` |

## Coexistence

`verify` runs the repository's own commands; it adds no dependency to them and CI does not need it. A `verify` task the
repository already has in its task runner is the repository's own and stays as it is.
