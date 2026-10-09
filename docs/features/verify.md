# verify

`harness verify` runs the checks a change needs and, when they pass, writes a receipt: a small record bound to the
change's base commit and to the exact content of every changed file. Any later edit invalidates it. Task completion
and Ralph workers require a valid receipt; `harness verify --check` tells you whether you have one.

## When to use it

- Before calling a change done: lint, build and the relevant test lanes in one command.
- Before `harness task complete`, which refuses without a valid receipt.
- In a Ralph worker, as the last step before signalling completion.
- With `--full`, before merging a change that needs every suite.

`harness verify` is not a replacement for running a single test while you iterate; use the repository's own commands
for that, and `verify` once the change is ready.

## How it works

### Base and changed files

The base is the commit the change is measured against:

| Where you run it | Base |
| --- | --- |
| A [managed worktree](worktrees.md) | the commit the worktree started from, recorded in its session |
| Any other checkout | the merge-base of `HEAD` with the default branch (`origin/HEAD`), or the local default branch in a clone without a remote |
| With `--base <ref>` | that commit; in a managed worktree, only the recorded base or one of its ancestors |

A base may only move earlier, never later: an earlier base adds files to the check, a later one would leave changes
out.

Changed files are the files that differ from the base (committed or not) plus untracked files that are not ignored.

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
9. Write the receipt to `.work/verified.json` in the checkout.
10. Fire the `verified` [lifecycle event](lifecycle.md): scope `task` in a worktree bound to a task, `change`
    otherwise. Actions declared with `auto: true` run; `HARNESS_RECEIPT` and `HARNESS_SUMMARY` carry the receipt's
    path and its one-line summary. A failing automatic event is reported on stderr and recorded in `.work/lifecycle.jsonl`;
    the receipt stays written and the exit code is the verification's.

`harness verify --check` never fires `verified`.

All commands come from the [profile](profile.md#verify) and run from the checkout root.

### Lane rules

A change is an **application change** when a changed file matches `verify.app_paths`. For an application change, without
`--full`:

1. Every lane must be either selected (`--lane <name>=<pattern>`) or skipped with a reason (`--skip <name>=<reason>`).
2. A lane whose `required_when` matches a changed file cannot be skipped.
3. At least one lane must be selected. A reason is free text that no check can judge, so it may skip a lane, never all
   of them.

With `--full`, every lane that has a `full` command runs it; a lane without one still follows rules 1 and 2.

A change that is not an application change (documentation, configuration outside `app_paths`) needs no lane and no
build; lint and validators still run, and you may still select lanes.

A lane named twice, both selected and skipped, or not declared in the profile is an error.

### The receipt

```json
{
  "base": "4f1c2a9e0b7d3c5a8e6f1b2d4c7a9e0f3b5d8c1a",
  "fingerprint": "a3f5…",
  "lanes": { "unit": ["invoice", "rounding"] },
  "skipped": { "e2e": "no route, controller or guard changed" },
  "full": false
}
```

| Field | Meaning |
| --- | --- |
| `base` | the commit the change was measured against |
| `fingerprint` | the hash of the change's content at verification time |
| `lanes` | for each selected lane, the patterns run |
| `skipped` | for each skipped lane, the reason given |
| `full` | whether `--full` ran |

The receipt lives in the checkout's `.work/`, so it is gitignored and local to that checkout.

### Freshness

`harness verify --check` accepts the receipt only if:

- it exists and is well formed;
- in a managed worktree, its base is the recorded base or an ancestor of it;
- the fingerprint recomputed now, against the receipt's base, equals the recorded one.

| After verifying, you… | Receipt |
| --- | --- |
| commit the verified files | still valid: content against the base is unchanged |
| edit, add, delete, `chmod` a changed file, or retarget a symlink | invalid |
| add an untracked, non-ignored file | invalid |
| rebase or merge in other commits | invalid: the content against the base changed |
| edit only gitignored files | still valid |

### Who relies on it

| Consumer | Uses the receipt to |
| --- | --- |
| [`harness task complete`](tasks.md) | refuse completion without a valid receipt |
| The [Ralph worker](ralph.md#the-worker-contract) | read which lanes and skip reasons the agent chose, then **replay** them itself from a tree the agent cannot write. The receipt only selects; the replay is the proof |
| The [`deliver` guard](lifecycle.md#built-in-guards) | refuse delivery of work without a valid receipt for its base, when the verify module is enabled |

## Reference

```text
harness verify [--lane <name>=<pattern>]... [--skip <name>=<reason>]... [--full] [--base <ref>] [--check]
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

Files read: the profile, the hooks file, the git history and index, the changed files, `.work/session.json`.
Files written: `.work/verified.json`, `.work/lifecycle.jsonl`; whatever the repository's commands and `verified`
actions write.

## Examples

### A Node API

```sh
# a service change, covered by unit tests; no HTTP surface touched
harness verify --lane unit=invoice --skip e2e="no route, controller or guard changed"

# a controller change: e2e cannot be skipped
harness verify --lane unit=invoice --lane e2e=invoices

# a documentation-only change
harness verify

# before merging something broad
harness verify --full
```

### A Terraform repository

```sh
harness verify --lane validate=envs/staging --lane plan=envs/staging
# a change under modules/** requires the plan lane:
harness verify --lane validate=envs/staging --skip plan="no backend access"   # refused
```

### A Go repository

```sh
harness verify --lane test=./internal/billing/... --lane vet=./internal/billing/...
harness verify --check && echo "receipt valid"
```

## Guidelines

- Do select the tests that exercise what you changed. Unrelated tests that pass prove nothing about the change.
- Do write skip reasons a reviewer can judge: "no route, controller or guard changed", not "n/a".
- Do treat a missing service or dependency as blocked work and say so; never skip a lane to get around it.
- Do run `verify` last: any edit after it invalidates the receipt.
- Don't edit files while `verify` runs; it fails rather than certify content it did not see.
- Don't commit `.work/verified.json`; it is gitignored on purpose.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| Refused before running: a lane has no selection | An application change with neither `--lane` nor `--skip` for that lane | Add one for every lane |
| Refused before running: a lane cannot be skipped | A changed file matches that lane's `required_when` | Select patterns for it |
| `--check` fails right after a pass | A formatter or the editor touched a file after verification | Run `verify` again |
| `--base` refused in a worktree | The ref is later than the worktree's recorded start | Use the start commit or an earlier one |
| A lane passes with zero tests | The test runner exits 0 when nothing matches | Configure the runner to fail on no tests, or fix the pattern |
| The build runs for a docs change | A doc path matches `verify.app_paths` | Narrow `app_paths` |

## Coexistence

`verify` runs the repository's own commands; it adds no dependency to them and CI does not need it. A repository that
already has a `verify` mise task keeps it; the wrapper is named `harness:verify` until the two are reconciled
([render](render.md#mise-wrappers)).
