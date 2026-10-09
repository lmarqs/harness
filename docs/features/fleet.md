# fleet

`harness fleet` runs one `harness` command across an explicit list of repositories and prints a pass/fail summary.
With `--deliver`, it also commits each repository's changes on a new local branch and fires that repository's
[`deliver` event](lifecycle.md), so the change leaves the clone through the actions the repository declares. It is how
a practice reaches many repositories at once, and how it is withdrawn.

`harness` itself never pushes, opens pull requests or writes to a hosting platform. What delivery means in a
repository (a push, a pull request, a CI trigger) is that repository's or its preset's declared `deliver` actions.

## When to use it

| You want to | Command |
| --- | --- |
| Check every adopted repository | `harness fleet doctor` |
| Pick up a newer preset commit everywhere, delivered for review | `harness fleet render --refresh --deliver` |
| Pick up a schema change everywhere | `harness fleet render --yes --deliver` |
| Install a skill source in every repository | `harness fleet skills sync --deliver` |
| Adopt `harness` in a set of repositories | `harness fleet scaffold --adopt --yes --deliver` |
| Withdraw a module everywhere | `harness fleet remove --module ralph --yes --deliver` |

## How it works

### The fleet file

The list of repositories is explicit: `harness` never discovers repositories by scanning directories, because a
machine also holds clones that must never adopt anything.

```yaml
# ~/.config/harness/fleet.yaml
repos:
  - ~/src/acme-api
  - ~/src/acme-cli
  - ~/src/acme-infra
branch_prefix: harness/
commit_message: "chore(harness): <command>"
```

| Field | Type | Default | Meaning |
| --- | --- | --- | --- |
| `repos` | list of paths | required | Repository roots, in the order they run. `~` expands to your home directory |
| `branch_prefix` | string | `harness/` | Prefix of the branch `--deliver` creates: `<branch_prefix><command>` |
| `commit_message` | string | `chore(harness): <command>` | Message of the commit `--deliver` creates; `<command>` is replaced by the command name |

`--repos <file>` uses another file with the same format.

A convention such as a CI-trigger commit or a commit trailer belongs in a `deliver` action of the repository or its
[preset](presets.md#lifecycle-actions-in-a-preset), not in the fleet file: it then applies to every delivery from that
repository, not only to fleet runs.

### For each repository

1. If the working tree has uncommitted changes, refuse it: the result is **blocked**, nothing runs there.
2. With `--deliver`: fetch `origin` and create a local branch `<branch_prefix><command>` from the fetched default
   branch. An existing branch with that name blocks the repository.
3. Run `harness <command> [command args...]` with the repository as its working directory and no terminal, so
   commands that confirm need `--yes`.
4. With `--deliver`, if the command changed files:
   1. commit them on the new branch with `commit_message`;
   2. run `harness verify` (no lanes are needed for a change outside `app_paths`), because the
      [`deliver` guards](lifecycle.md#built-in-guards) require a committed, clean branch that is not the default
      branch and, when the verify module is enabled, a valid receipt;
   3. fire `deliver` with scope `change`, which runs every `deliver` action declared for that scope, in order,
      whatever its `auto` flag.

   No change means no branch, commit or delivery.
5. Switch back to the branch the repository was on. The new branch stays in the clone.
6. Record the result.

A repository that declares no `deliver` actions keeps the committed branch locally, and the summary says
**committed, not delivered**. Someone delivers it later with `harness lifecycle run deliver` in that branch, or by
hand.

Without `--continue-on-error`, the fleet stops at the first repository that does not pass; the rest are reported as
not run. The summary is printed in every case.

### Summary

```text
repository          result                     exit  detail
~/src/acme-api      delivered                  0     push, open-pr
~/src/acme-cli      unchanged                  0
~/src/acme-web      committed, not delivered   0     harness/render
~/src/acme-infra    blocked                    3     uncommitted changes
```

With `--json`, the summary is a JSON document with one entry per repository, including the branch and the result of
each `deliver` action.

## Reference

```text
harness fleet <command> [command args...] [--repos <file>] [--deliver] [--continue-on-error]
```

| Flag | Default | Meaning |
| --- | --- | --- |
| `<command> [command args...]` | required | Any repository-level `harness` command and its arguments: `scaffold`, `render`, `remove`, `doctor`, `skills sync`, `explain`, `version` |
| `--repos <file>` | `~/.config/harness/fleet.yaml` | Fleet file |
| `--deliver` | off | Commit each changed repository on a new local branch and fire its `deliver` event (scope `change`) |
| `--continue-on-error` | off | Keep going after a repository fails or is blocked |

`--dry-run` is passed to each repository's command; with it, fleet creates no branch, commit or delivery.

| Exit code | Meaning |
| --- | --- |
| 0 | Every repository passed, including those committed but not delivered |
| 1, 2, 3 | The highest exit code among the repositories; a refused dirty repository counts as 3, a failing `deliver` action as 2, a `deliver` guard refusal as 3 |

Files read: the fleet file, each repository's files as the command reads them. Written: whatever the command writes
in each repository; with `--deliver`, a local branch, a commit, a receipt, `.work/lifecycle.jsonl`, and whatever the
repository's `deliver` actions do.

## Examples

Roll a newer preset commit out to every adopter:

```sh
harness fleet render --refresh --deliver --continue-on-error
```

Adopt three new repositories, Node, Go and Terraform, with a separate fleet file:

```yaml
# ~/fleets/new-adopters.yaml
repos: [~/src/acme-web, ~/src/acme-worker, ~/src/acme-network]
branch_prefix: harness/adopt-
commit_message: "chore: adopt harness"
```

```sh
harness fleet scaffold --adopt --yes --deliver --repos ~/fleets/new-adopters.yaml
```

The binary ships no actions, so a freshly scaffolded repository declares no `deliver` actions and ends as
**committed, not delivered**: deliver those branches by hand, or add `extends` to a preset that declares them and run
`harness lifecycle run deliver` on each branch.

Health check before a release:

```sh
harness fleet doctor --continue-on-error
```

## Guidelines

- Do keep the fleet file to repositories you are responsible for.
- Do run a command once by hand in one repository before running it across the fleet.
- Do use `--deliver` for anything that changes files, and declare `deliver` actions that put the change where each
  repository's owners review it, such as a pull request.
- Do read `harness lifecycle list` in one repository before a fleet delivery: `--deliver` runs every `deliver` action
  declared for scope `change`, not only the automatic ones.
- Don't run `fleet` with uncommitted work in those repositories; they are refused, by design.
- Don't declare a `deliver` action that pushes to the default branch: a review is where each repository's owners
  accept the change.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| A repository is blocked | Uncommitted changes, or the branch already exists | Commit or stash; delete or merge the old branch |
| `scaffold` exits 3 in every repository | It needs confirmation and the fleet runs without a terminal | Add `--yes` |
| A repository ends **committed, not delivered** | It declares no `deliver` actions | Add them to the repository or its preset, then `harness lifecycle run deliver` on that branch |
| A `deliver` action fails | The action's own tool failed, for example an unauthenticated CLI | Read the action's output in the summary or `.work/lifecycle.jsonl`; fix it, then `harness lifecycle run deliver` on that branch |
| The fleet stopped after the first failure | Default behaviour | Add `--continue-on-error` |
