# fleet

## Objective

**Problem.** A maintainer of many repositories who wants to roll out a practice, move to a new release or withdraw
something has to visit each repository in turn: run the same steps, commit, and send the change for review. Some
repositories get missed or are left halfway, each change comes out slightly different, and nobody has one view of which
repositories succeeded.

**Goal.** A change made once reaches, or is withdrawn from, every repository the maintainer names, as a change each
repository reviews on its own, and the maintainer sees which repositories passed and which did not.

## Overview

`fleet` serves no single stage of the development lifecycle: it runs one `the-cook` command in many
repositories, and with `--deliver` it takes each repository's change through that repository's Deliver stage.

`the-cook fleet` runs one `the-cook` command across an explicit list of repositories and prints a pass/fail summary.
With `--deliver`, it also commits each repository's changes on a new local branch and fires that repository's
`deliver` event, so the change leaves the clone through the actions the repository declares. It is how
a `the-cook` change reaches many repositories at once, and how it is withdrawn.

`the-cook` itself never pushes, opens pull requests or writes to a hosting platform. What delivery means in a
repository (a push, a pull request, a CI trigger) is the `deliver` actions that repository declares in its own
`.cook/hooks.yaml`.

`fleet` runs the same `the-cook` command in every repository it lists. A choice that takes judgement in each
repository, such as which services an organization lets coding agents read through MCP, which deploy commands it denies
or how its changes are delivered, is written into each repository by a person or a worker, typically one following a
skill the organization publishes. It is reviewed there as a diff, and `fleet` does not run that worker. For example,
acme denies deploys to workers everywhere, but
`acme/api` deploys with `npm run deploy` and `acme/infra` with `terraform apply`, so a coding agent following acme's
skill writes a different `deny` entry into each repository's `.cook/policy.yaml`.

Commands are named `the-cook <command>` on this page. The examples run `fleet` through an adopted repository's wrapper,
`./cook fleet …`; with a global install, `the-cook fleet …` is equivalent and runs from any directory
([wrapper](wrapper.md)).

## When to use it

| You want to | Command |
| --- | --- |
| Check every adopted repository | `the-cook fleet doctor` |
| Apply a stack station, or a newer version of it, to every repository on that stack, delivered for review | `the-cook fleet station apply node --cookbook github:acme/cookbook@v1.3.0 --yes --deliver --repos <file>` |
| Move an ingredient's pin, such as a newer git ingredient, in every repository that uses it, delivered for review | `the-cook fleet ingredient pin git --cookbook github:acme/cookbook@v1.3.0 --yes --deliver --repos <file>` |
| Pick up a schema change everywhere | `the-cook fleet render --yes --deliver` |
| Move every repository to a new `the-cook` release | `the-cook fleet wrapper --version <tag> --deliver` |
| Bring a way of working, such as `agentic-coding`, to a set of repositories | `the-cook fleet station apply agentic-coding --cookbook github:acme/cookbook@v1.2.0 --yes --deliver --repos <file>` |
| Give every repository that makes one kind of change a recipe for it, delivered for review | `the-cook fleet recipe add nestjs-module --cookbook github:acme/cookbook@v1.2.0 --yes --deliver --repos <file>` |
| Take a newer version of an added recipe in every repository that has it | `the-cook fleet recipe update nestjs-module --cookbook github:acme/cookbook@v1.3.0 --yes --deliver --repos <file>` |
| Adopt `the-cook` in a set of repositories | `the-cook fleet scaffold --adopt --cookbook github:acme/cookbook@v1.2.0 --yes --deliver` |
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
fleet runs.

### For each repository

1. If the working tree has uncommitted changes, refuse it: the result is **blocked**, nothing runs there.
2. With `--deliver`: through the version-control contract, which the repository's version-control ingredient fills,
   learn what is shared with its `update` operation, then create a local branch `<branch_prefix><command>` at the
   revision of the shared default line of work that its `default_revision` reports. When the ingredient has no
   `update`, the fleet prints one note for the repository and starts the branch from what the checkout knows. When
   `update` fails, the repository is **blocked**, with the failure as the reason, so no branch is delivered from a
   stale base. An existing branch with that name blocks the repository.
3. Run `the-cook <command> [command args...]` with the repository as its working directory and no terminal, so commands
   that confirm need `--yes`. In a repository with `.cook/wrapper.yaml`, the command runs the version that repository
   pins, downloaded and verified when this machine has not cached it yet. In a repository without one, such as one
   `scaffold` is adopting, it runs the fleet's own binary. A command that exits 3 there fires that repository's
   `blocked` event, with the refusal in `COOK_REASON`. A repository the fleet refuses in step 1 or 2
   runs nothing, `blocked` included.
4. With `--deliver`, if the command exited 0 and changed files:
   1. commit them on the new branch with `commit_message`;
   2. run `the-cook verify` (no lanes are needed for a change outside `app_paths`), because the
      `deliver` guards require a committed, clean branch that is not the default branch
      and, when the verify module is enabled, a valid receipt;
   3. fire `deliver` with scope `change`, which runs every `deliver` action declared for that scope, in order,
      whatever its `auto` flag.

   No change means no branch, commit or delivery. A command that exits non-zero is never delivered: this step does not
   run, and the summary shows the exit code.
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
| `<command> [command args...]` | required | Any repository-level `the-cook` command and its arguments: `scaffold`, `render`, `station`, `recipe`, `ingredient`, `fetch`, `remove`, `doctor`, `wrapper`, `explain`, `version` |
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
lifecycle log, and whatever the repository's `deliver` actions do.

## Examples

Move every repository to a new release, delivered for review:

```sh
./cook fleet wrapper --version <tag> --deliver --continue-on-error
```

Each repository gets a branch `the-cook/wrapper` whose commit changes `version` and `checksums` in
`.cook/wrapper.yaml`, and `cook` when the new release brings a new wrapper. In each repository, the release it
pinned verified the new release's signed checksum file with its embedded keys before writing anything, so a
branch exists only for a release the project signed. Reviewers check the version they expected
([safety](safety.md#the-pinned-binary)). The `verify` that `--deliver` runs after the commit already uses the new
version. Once these branches land, `fleet render --deliver` picks up what the new release renders differently.

A newer cookbook tag reaches a repository in two ways. What an ingredient renders, and its floor, change when its pin
moves. A station's entries and templates reach a repository only when someone applies that station, and applying a newer
version adds what the repository lacks. With a fleet file that lists the Node repositories, read what each would
receive from `node` v1.3.0, then deliver it for review:

```yaml
# ~/fleets/node.yaml
repos: [~/src/acme-api, ~/src/acme-web]
```

```sh
./cook fleet station apply node --cookbook github:acme/cookbook@v1.3.0 --dry-run --repos ~/fleets/node.yaml
./cook fleet station apply node --cookbook github:acme/cookbook@v1.3.0 --yes --deliver --continue-on-error \
  --repos ~/fleets/node.yaml
```

Each repository receives the entries, templates and recipes of that version that it lacks, and its `stations:` entry for
`node` moves to v1.3.0. A recipe the repository already holds in `.cook/recipes/` stays as it is, and the command
reports it; `fleet recipe update` takes a newer version of it. Every ingredient the station lists is pinned; an
ingredient the repository already pins at another sha256 keeps the repository's pin, and the command reports the
difference. Every entry and item the repository already holds stays as it is ([stations](stations.md#how-apply-works)).

A recipe is copied, not pinned: `recipe add` writes it into each repository's `.cook/recipes/<name>/` with its
`origin`, and each copy is then that repository's own ([recipes](recipes.md#the-cook-recipe-add)). Give a
recipe to the repositories that make its kind of change, then take a newer version of it everywhere:

```yaml
# ~/fleets/nestjs.yaml
repos: [~/src/acme-api, ~/src/acme-billing]
```

```sh
./cook fleet recipe add nestjs-module --cookbook github:acme/cookbook@v1.2.0 --yes --deliver \
  --repos ~/fleets/nestjs.yaml
./cook fleet recipe update nestjs-module --cookbook github:acme/cookbook@v1.3.0 --dry-run --repos ~/fleets/nestjs.yaml
./cook fleet recipe update nestjs-module --cookbook github:acme/cookbook@v1.3.0 --yes --deliver --continue-on-error \
  --repos ~/fleets/nestjs.yaml
```

`recipe add` checks the recipe's requirements in each repository first: a repository that lacks a contract, role or
lane it requires exits 3 and gets nothing. `recipe update` merges three ways, per repository: that repository's copy,
the version it came from and the newer version. Clean hunks apply, and `origin` moves to the newer version in every
repository. A repository that edited its copy where the newer version changed too gets the conflicts marked in its
files, and `recipe update` exits 3 there, so the fleet never delivers that repository
([`recipe update`](recipes.md#the-cook-recipe-update)). A recipe with conflict markers cannot run (exit 1) until a
person or a worker resolves them in that repository and delivers the result; nothing else is left to do there, because
`origin` already names the newer version.

To move only what renders, such as a newer `git` ingredient, move its pin:

```sh
./cook fleet ingredient pin git --cookbook github:acme/cookbook@v1.3.0 --yes --deliver --continue-on-error
```

Each repository's branch holds the moved pin and what the new version renders differently. In a repository that does
not pin the ingredient yet, the same command adds the pin, so run it only with a fleet file whose repositories all use
the program.

Adopt three new repositories, Node, Go and Terraform, with a separate fleet file:

```yaml
# ~/fleets/new-adopters.yaml
repos: [~/src/acme-web, ~/src/acme-worker, ~/src/acme-network]
branch_prefix: the-cook/adopt-
commit_message: "chore: adopt the-cook"
```

```sh
./cook fleet scaffold --adopt --cookbook github:acme/cookbook@v1.2.0 --yes --deliver --repos ~/fleets/new-adopters.yaml
```

Each repository applies the stations of that cookbook tag whose detect rule matches in it, such as `agentic-coding` and
the station for its stack, pins the ingredients they list and adds the recipes they list, such as `implement-task`. No
action runs that a repository does not declare: a repository whose applied stations hold no `deliver` action declares
none, and ends as **committed, not delivered**. Deliver those branches by hand, or declare `deliver` actions in each
branch's `.cook/hooks.yaml`, commit them, and run `the-cook lifecycle run deliver` there.

Health check before a release:

```sh
./cook fleet doctor --continue-on-error
```

## Guidelines

- Do keep the fleet file to repositories you are responsible for.
- Do run a command once by hand in one repository before running it across the fleet.
- Do list in a fleet file for `fleet recipe add` only the repositories that make the recipe's kind of change: the
  recipe becomes each one's own file, and a repository that never runs it carries it for nothing.
- Do keep one fleet file per stack for a stack station: it holds one stack's commands, lanes and hooks, and in a
  repository on another stack its entries do not fit. Any other station fits a repository that works the way it
  describes, and an ingredient fits a repository that uses its program, so their fleet files list those repositories.
- Do give a change that takes judgement in each repository, such as an organization's convention, to a person or a
  worker in that repository: `fleet` runs the same command everywhere and cannot adapt it.
- Do use `--deliver` for anything that changes files, and declare `deliver` actions that put the change where each
  repository's owners review it, such as a pull request.
- Do read `the-cook lifecycle list` in one repository before a fleet delivery: `--deliver` runs every `deliver` action
  declared for scope `change`, not only the automatic ones. An `attempt` action among them
  starts one worker per repository.
- Don't run `fleet` with uncommitted work in the fleet's repositories; `fleet` refuses them by design.
- Don't declare a `deliver` action that pushes to the default branch: a review is where each repository's owners
  accept the change.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| A repository is blocked | Uncommitted changes, or the branch already exists | Commit or stash; delete or merge the old branch |
| `scaffold` or `station apply` exits 3 in every repository | It needs confirmation and the fleet runs without a terminal | Add `--yes`, after reading the `--dry-run` output |
| `render` or `doctor` exits 3 in a repository | An ingredient it pins is not cached on this machine | `the-cook fleet fetch`, then run the command again |
| A repository ends **committed, not delivered** | It declares no `deliver` actions | Declare them in its `.cook/hooks.yaml`, then `the-cook lifecycle run deliver` on that branch |
| A `deliver` action fails | The action's own tool failed, for example an unauthenticated CLI | Read the action's output in the summary or the lifecycle log; fix it, then `the-cook lifecycle run deliver` on that branch |
| A repository fails right after `fleet wrapper` | The new release cannot be downloaded or fails its checksum on this machine | See [pitfalls](pitfalls.md#the-first-run-needs-the-network) and [the checksum entry](pitfalls.md#a-checksum-mismatch-refuses-the-download) |
| The fleet stopped after the first failure | Default behaviour | Add `--continue-on-error` |
| `recipe add` exits 3 in a repository | The repository lacks a contract, role or lane the recipe requires; the message names it | Pin or declare what it names in that repository, or leave the repository out of the fleet file |
| `recipe update` exits 3 in a repository, which is not delivered | That repository edited its copy where the newer version changed too, and the merge left the conflicts marked, with `origin` already moved; `the-cook make` refuses the recipe with exit 1 until they are resolved | Resolve the conflicts in that repository, then commit and deliver the result there; no further `recipe update` is needed |
| `recipe add` exits 1 in a repository, naming `recipe update` | The repository already holds a recipe of that name, added before or by a station | Leave it out of the fleet file, or take the newer version with `fleet recipe update` |
