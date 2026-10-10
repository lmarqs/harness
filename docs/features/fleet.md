# fleet

## Objective

**Problem.** A maintainer of many repositories who wants to roll out a practice, move to a new release or withdraw
something has to visit each repository in turn: run the same steps, commit, and send the change for review. Some
repositories get missed or are left halfway, each change comes out slightly different, and nobody has one view of which
repositories succeeded.

**Goal.** A change made once reaches, or is withdrawn from, every repository the maintainer names, as a change each
repository reviews on its own, and the maintainer sees which repositories passed and which did not.

## Overview

`the-cook fleet` runs one `the-cook` command across an explicit list of repositories and prints a pass/fail summary.
With `--deliver`, it also commits each repository's changes on a new local branch and fires that repository's
[`deliver` event](lifecycle.md), so the change leaves the clone through the actions the repository declares. It is how
a `the-cook` change reaches many repositories at once, and how it is withdrawn.

`the-cook` itself never pushes, opens pull requests or writes to a hosting platform. What delivery means in a
repository (a push, a pull request, a CI trigger) is the `deliver` actions that repository declares in its own
`.cook/hooks.yaml`.

`fleet` runs the same `the-cook` command in every repository it lists. A choice that takes judgement in each repository,
such as which services an organization lets agents read through MCP, which deploy commands it denies or how its changes
are delivered, is written into each repository by a person or an agent, typically one following a skill the organization
publishes. It is reviewed there as a diff, and `fleet` does not run that agent
([ADR 0013](../adrs/0013-organization-content.md)). For example, acme denies deploys to agents everywhere, but
`acme/api` deploys with `npm run deploy` and `acme/infra` with `terraform apply`, so an agent following acme's skill
writes a different `deny` entry into each repository's `.cook/policy.yaml`.

Commands are named `the-cook <command>` on this page. The examples run `fleet` through an adopted repository's wrapper,
`./cook fleet …`; with a global install, `the-cook fleet …` is equivalent and runs from any directory
([wrapper](wrapper.md)).

## When to use it

| You want to | Command |
| --- | --- |
| Check every adopted repository | `the-cook fleet doctor` |
| Apply a stack's recipe to every repository on that stack, delivered for review | `the-cook fleet recipe apply node --yes --deliver --repos <file>` |
| Pick up a schema change everywhere | `the-cook fleet render --yes --deliver` |
| Move every repository to a new `the-cook` release | `the-cook fleet wrapper --version <tag> --deliver` |
| Wire a program, such as Beads, into every repository that uses it | `the-cook fleet recipe apply beads --yes --deliver --repos <file>` |
| Adopt `the-cook` in a set of repositories | `the-cook fleet scaffold --adopt --yes --deliver` |
| Withdraw a module everywhere | `the-cook fleet remove --module memory --yes --deliver` |

## How it works

### The fleet file

The list of repositories is explicit: `the-cook` never discovers repositories by scanning directories, because a
machine also holds clones that must never adopt anything.

```yaml
# ~/.config/the-cook/fleet.yaml
repos:
  - ~/src/acme-api
  - ~/src/acme-cli
  - ~/src/acme-infra
branch_prefix: the-cook/
commit_message: "chore(the-cook): <command>"
```

| Field | Type | Default | Meaning |
| --- | --- | --- | --- |
| `repos` | list of paths | required | Repository roots, in the order they run. `~` expands to your home directory |
| `branch_prefix` | string | `the-cook/` | Prefix of the branch `--deliver` creates: `<branch_prefix><command>` |
| `commit_message` | string | `chore(the-cook): <command>` | Message of the commit `--deliver` creates; `<command>` is replaced by the command name |

`--repos <file>` uses another file with the same format.

A convention such as a CI-trigger commit or a commit trailer belongs in a `deliver` action in the repository's own
`.cook/hooks.yaml`, not in the fleet file: it then applies to every delivery from that repository, not only to
fleet runs ([lifecycle](lifecycle.md#an-organization-convention-a-ci-trigger-commit-before-the-pull-request)).

### For each repository

1. If the working tree has uncommitted changes, refuse it: the result is **blocked**, nothing runs there.
2. With `--deliver`: through the repository's version-control recipe, update from the remote and create a local
   branch `<branch_prefix><command>` from the default branch the recipe reports (with the git recipe, after
   `git fetch origin`). An existing branch with that name blocks the repository.
3. Run `the-cook <command> [command args...]` with the repository as its working directory and no terminal, so commands
   that confirm need `--yes`. In a repository with `.cook/wrapper.yaml`, the command runs the version that repository
   pins, downloaded and verified when this machine has not cached it yet. In a repository without one, such as one
   `scaffold` is adopting, it runs the fleet's own binary. A command that exits 3 there fires that repository's
   `blocked` [event](lifecycle.md), with the refusal in `COOK_REASON`. A repository the fleet refuses in step 1 or 2
   runs nothing, `blocked` included.
4. With `--deliver`, if the command changed files:
   1. commit them on the new branch with `commit_message`;
   2. run `the-cook verify` (no lanes are needed for a change outside `app_paths`), because the
      [`deliver` guards](lifecycle.md#built-in-guards) require a committed, clean branch that is not the default branch
      and, when the verify module is enabled, a valid [receipt](verify.md#the-receipt);
   3. fire `deliver` with scope `change`, which runs every `deliver` action declared for that scope, in order,
      whatever its `auto` flag.

   No change means no branch, commit or delivery.
5. Switch back to the branch the repository was on. The new branch stays in the clone.
6. Record the result.

A repository that declares no `deliver` actions keeps the committed branch locally, and the summary says
**committed, not delivered**. Someone delivers it later with `the-cook lifecycle run deliver` in that branch, or by
hand.

Without `--continue-on-error`, the fleet stops at the first repository that does not pass; the rest are reported as
not run. The summary is printed in every case.

### Summary

```text
repository          result                     exit  detail
~/src/acme-api      delivered                  0     push, open-pr
~/src/acme-cli      unchanged                  0
~/src/acme-web      committed, not delivered   0     the-cook/render
~/src/acme-infra    blocked                    3     uncommitted changes
```

With `--json`, the summary is a JSON document with one entry per repository, including the branch and the result of
each `deliver` action.

## Reference

```text
the-cook fleet <command> [command args...] [--repos <file>] [--deliver] [--continue-on-error]
```

| Flag | Default | Meaning |
| --- | --- | --- |
| `<command> [command args...]` | required | Any repository-level `the-cook` command and its arguments: `scaffold`, `render`, `recipe`, `remove`, `doctor`, `wrapper`, `explain`, `version` |
| `--repos <file>` | `~/.config/the-cook/fleet.yaml` | Fleet file |
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
./cook fleet wrapper --version <tag> --deliver --continue-on-error
```

Each repository gets a branch `the-cook/wrapper` whose commit changes `version` and `checksums` in
`.cook/wrapper.yaml`, and `cook` when the new release brings a new wrapper. In each repository, the release it
pinned so far verified the new release's signed checksum file with its embedded keys before writing anything, so a
branch exists only for a release the project signed. Reviewers check the version they expected
([safety](safety.md#the-pinned-binary)). The `verify` that `--deliver` runs after the commit already uses the new
version. Once these branches land, `fleet render --deliver` picks up what the new release renders differently
([wrapper](wrapper.md#many-repositories)).

A newer release's recipe entries reach a repository only when someone applies the recipe again; its renders change
with the pin alone. With a fleet file
that lists the Node repositories, read what each would receive, then deliver it for review:

```yaml
# ~/fleets/node.yaml
repos: [~/src/acme-api, ~/src/acme-web]
```

```sh
./cook fleet recipe apply node --dry-run --repos ~/fleets/node.yaml
./cook fleet recipe apply node --yes --deliver --continue-on-error --repos ~/fleets/node.yaml
```

Each repository runs the release it pins, so it receives the entries of that release's `node` recipe that it lacks.
Every entry and item it already holds stays as it is, and a repository with nothing to add ends **unchanged**
([recipes](recipes.md#how-apply-works)).

Adopt three new repositories, Node, Go and Terraform, with a separate fleet file:

```yaml
# ~/fleets/new-adopters.yaml
repos: [~/src/acme-web, ~/src/acme-worker, ~/src/acme-network]
branch_prefix: the-cook/adopt-
commit_message: "chore: adopt the-cook"
```

```sh
./cook fleet scaffold --adopt --yes --deliver --repos ~/fleets/new-adopters.yaml
```

Each repository gets the recipes whose detect rule matches in it, such as `git`, `claude` and its stack's recipe. No
action runs that a repository does not declare, and no recipe `scaffold` applies holds a `deliver` action, so a freshly
scaffolded repository declares no `deliver` actions and ends as **committed, not delivered**. Deliver those branches by
hand, or declare `deliver` actions in each branch's `.cook/hooks.yaml`, commit them, and run
`the-cook lifecycle run deliver` there.

Health check before a release:

```sh
./cook fleet doctor --continue-on-error
```

## Guidelines

- Do keep the fleet file to repositories you are responsible for.
- Do run a command once by hand in one repository before running it across the fleet.
- Do keep one fleet file per stack for a stack recipe: it holds one stack's commands, lanes and hooks, and in a
  repository on another stack its entries do not fit. Any other recipe fits a repository that uses its program, so its
  fleet file lists those repositories.
- Do give a change that takes judgement in each repository, such as an organization's convention, to a person or an
  agent working in that repository: `fleet` runs the same command everywhere and cannot adapt it.
- Do use `--deliver` for anything that changes files, and declare `deliver` actions that put the change where each
  repository's owners review it, such as a pull request.
- Do read `the-cook lifecycle list` in one repository before a fleet delivery: `--deliver` runs every `deliver` action
  declared for scope `change`, not only the automatic ones. An [`agent` action](lifecycle.md#agent) among them starts
  one agent per repository.
- Don't run `fleet` with uncommitted work in the fleet's repositories; `fleet` refuses them by design.
- Don't declare a `deliver` action that pushes to the default branch: a review is where each repository's owners
  accept the change.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| A repository is blocked | Uncommitted changes, or the branch already exists | Commit or stash; delete or merge the old branch |
| `scaffold` or `recipe apply` exits 3 in every repository | It needs confirmation and the fleet runs without a terminal | Add `--yes`, after reading the `--dry-run` output |
| A repository ends **committed, not delivered** | It declares no `deliver` actions | Declare them in its `.cook/hooks.yaml`, then `the-cook lifecycle run deliver` on that branch |
| A `deliver` action fails | The action's own tool failed, for example an unauthenticated CLI | Read the action's output in the summary or the lifecycle log; fix it, then `the-cook lifecycle run deliver` on that branch |
| A repository fails right after `fleet wrapper` | The new release cannot be downloaded or fails its checksum on this machine | See [pitfalls](pitfalls.md#the-first-run-needs-the-network) and [the checksum entry](pitfalls.md#a-checksum-mismatch-refuses-the-download) |
| The fleet stopped after the first failure | Default behaviour | Add `--continue-on-error` |
