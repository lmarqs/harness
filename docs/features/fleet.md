# fleet

## Objective

**Problem.** A maintainer of many repositories who wants to roll out a practice, move to a new release or withdraw
something has to visit each repository in turn: run the same steps, commit, and send the change for review. Some
repositories get missed or are left halfway, each change comes out slightly different, and nobody has one view of which
repositories succeeded.

**Goal.** A change made once reaches, or is withdrawn from, every repository the maintainer names, as a change each
repository reviews on its own, and the maintainer sees which repositories passed and which did not.

## Overview

`harness fleet` runs one `harness` command across an explicit list of repositories and prints a pass/fail summary.
With `--deliver`, it also commits each repository's changes on a new local branch and fires that repository's
[`deliver` event](lifecycle.md), so the change leaves the clone through the actions the repository declares. It is how
a `harness` change reaches many repositories at once, and how it is withdrawn.

`harness` itself never pushes, opens pull requests or writes to a hosting platform. What delivery means in a
repository (a push, a pull request, a CI trigger) is the `deliver` actions that repository declares in its own
`.harness/hooks.yaml`.

`fleet` runs the same `harness` command in every repository it lists. A choice that takes judgement in each
repository, such as an organization's MCP read patterns, deploy denies or delivery convention, is written into each
repository by a person or an agent, typically one following a skill the organization publishes, and reviewed there as
a diff; `fleet` does not run that agent ([ADR 0013](../adrs/0013-organization-content.md)).

Commands are named `harness <command>` on this page. The examples run `fleet` through an adopted repository's wrapper,
`./harnessw fleet …`; with a global install, `harness fleet …` is equivalent and runs from any directory
([wrapper](wrapper.md)).

## When to use it

| You want to | Command |
| --- | --- |
| Check every adopted repository | `harness fleet doctor` |
| Apply a stack's preset to every repository on that stack, delivered for review | `harness fleet preset apply node --yes --deliver --repos <file>` |
| Pick up a schema change everywhere | `harness fleet render --yes --deliver` |
| Move every repository to a new `harness` release | `harness fleet wrapper --version <tag> --deliver` |
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

A convention such as a CI-trigger commit or a commit trailer belongs in a `deliver` action in the repository's own
`.harness/hooks.yaml`, not in the fleet file: it then applies to every delivery from that repository, not only to
fleet runs ([lifecycle](lifecycle.md#an-organization-convention-a-ci-trigger-commit-before-the-pull-request)).

### For each repository

1. If the working tree has uncommitted changes, refuse it: the result is **blocked**, nothing runs there.
2. With `--deliver`: fetch `origin` and create a local branch `<branch_prefix><command>` from the fetched default
   branch. An existing branch with that name blocks the repository.
3. Run `harness <command> [command args...]` with the repository as its working directory and no terminal, so
   commands that confirm need `--yes`. In a repository with `.harness/wrapper.yaml`, the command runs the version that
   repository pins, downloaded and verified when this machine has not cached it yet; in a repository without one, such
   as one `scaffold` is adopting, it runs the fleet's own binary. A command that exits 3 there fires that repository's
   `blocked` [event](lifecycle.md), with the refusal in `HARNESS_REASON`; a repository the fleet refuses in step 1 or
   2 runs nothing, `blocked` included.
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
| `<command> [command args...]` | required | Any repository-level `harness` command and its arguments: `scaffold`, `render`, `preset`, `remove`, `doctor`, `skills sync`, `wrapper`, `explain`, `version` |
| `--repos <file>` | `~/.config/harness/fleet.yaml` | Fleet file |
| `--deliver` | off | Commit each changed repository on a new local branch and fire its `deliver` event (scope `change`) |
| `--continue-on-error` | off | Keep going after a repository fails or is blocked |

`--dry-run` is passed to each repository's command; with it, fleet creates no branch, commit or delivery.

| Exit code | Meaning |
| --- | --- |
| 0 | Every repository passed, including those committed but not delivered |
| 1, 2, 3 | The highest exit code among the repositories; a refused dirty repository counts as 3, a failing `deliver` action as 2, a `deliver` guard refusal as 3 |

Files read: the fleet file, each repository's files as the command reads them. Written: whatever the command writes
in each repository; with `--deliver`, a local branch, a commit, a receipt, the
[lifecycle log](lifecycle.md#the-lifecycle-log), and whatever the repository's `deliver` actions do.

## Examples

Move every repository to a new release, delivered for review:

```sh
./harnessw fleet wrapper --version <tag> --deliver --continue-on-error
```

Each repository gets a branch `harness/wrapper` whose commit changes `version` and `checksums` in
`.harness/wrapper.yaml`, and `harnessw` when the new release brings a new wrapper. In each repository, the release it
pinned so far verified the new release's signed checksum file with its embedded keys before writing anything, so a
branch exists only for a release the project signed; reviewers check the version they expected
([safety](safety.md#the-pinned-binary)). The `verify` that `--deliver`
runs after the commit already uses the new version. Once these branches land, `fleet render --deliver` picks up what
the new release renders differently ([wrapper](wrapper.md#many-repositories)).

A newer release's preset entries reach a repository only when someone applies the preset again. With a fleet file
that lists the Node repositories, read what each would receive, then deliver it for review:

```yaml
# ~/fleets/node.yaml
repos: [~/src/acme-api, ~/src/acme-web]
```

```sh
./harnessw fleet preset apply node --dry-run --repos ~/fleets/node.yaml
./harnessw fleet preset apply node --yes --deliver --continue-on-error --repos ~/fleets/node.yaml
```

Each repository runs the release it pins, so it receives the entries of that release's `node` preset that it lacks;
every entry and item it already holds stays as it is, and a repository with nothing to add ends **unchanged**
([presets](presets.md#how-apply-works)).

Adopt three new repositories, Node, Go and Terraform, with a separate fleet file:

```yaml
# ~/fleets/new-adopters.yaml
repos: [~/src/acme-web, ~/src/acme-worker, ~/src/acme-network]
branch_prefix: harness/adopt-
commit_message: "chore: adopt harness"
```

```sh
./harnessw fleet scaffold --adopt --yes --deliver --repos ~/fleets/new-adopters.yaml
```

Each repository gets the presets of the stacks `scaffold` detects in it. The binary ships no actions and no preset
holds one, so a freshly scaffolded repository declares no `deliver` actions and ends as **committed, not
delivered**: deliver those branches by hand, or declare `deliver` actions in each branch's `.harness/hooks.yaml`,
commit them, and run `harness lifecycle run deliver` there.

Health check before a release:

```sh
./harnessw fleet doctor --continue-on-error
```

## Guidelines

- Do keep the fleet file to repositories you are responsible for.
- Do run a command once by hand in one repository before running it across the fleet.
- Do keep one fleet file per stack for `preset apply`: a preset holds one stack's commands, lanes and hooks, and in a
  repository on another stack its entries do not fit.
- Do give a change that takes judgement in each repository, such as an organization's convention, to a person or an
  agent working in that repository: `fleet` runs the same command everywhere and cannot adapt it.
- Do use `--deliver` for anything that changes files, and declare `deliver` actions that put the change where each
  repository's owners review it, such as a pull request.
- Do read `harness lifecycle list` in one repository before a fleet delivery: `--deliver` runs every `deliver` action
  declared for scope `change`, not only the automatic ones. An `agent` action among them starts one agent per
  repository.
- Don't run `fleet` with uncommitted work in those repositories; they are refused, by design.
- Don't declare a `deliver` action that pushes to the default branch: a review is where each repository's owners
  accept the change.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| A repository is blocked | Uncommitted changes, or the branch already exists | Commit or stash; delete or merge the old branch |
| `scaffold` or `preset apply` exits 3 in every repository | It needs confirmation and the fleet runs without a terminal | Add `--yes`, after reading the `--dry-run` output |
| A repository ends **committed, not delivered** | It declares no `deliver` actions | Declare them in its `.harness/hooks.yaml`, then `harness lifecycle run deliver` on that branch |
| A `deliver` action fails | The action's own tool failed, for example an unauthenticated CLI | Read the action's output in the summary or the lifecycle log; fix it, then `harness lifecycle run deliver` on that branch |
| A repository fails right after `fleet wrapper` | The new release cannot be downloaded or fails its checksum on this machine | See [pitfalls](pitfalls.md#the-first-run-needs-the-network) and [the checksum entry](pitfalls.md#a-checksum-mismatch-refuses-the-download) |
| The fleet stopped after the first failure | Default behaviour | Add `--continue-on-error` |
