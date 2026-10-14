# Ingredients

## Objective

**Problem.** A change passes through stages that need programs: a version control system records the base a change
starts from and the history it lands in, workers such as coding agents, bots or codemods change the code, and a loop
runner, a task tracker or a task runner may take part. Each program has its own files, its own commands, and
arguments that must never reach it unattended, and each team picks different programs. A tool with code for particular
programs makes every repository depend on choices its team did not make. A program's setup copied into a repository
loses its safety rules the first time someone edits it, and a description resolved from outside the repository leaves
a reviewer unable to tell what runs.

**Goal.** Every program a repository's lifecycle reaches is described in data the repository pins by hash and a
reviewer can read before approving it. The program's safety rules hold on every render, whatever the repository's own
files say, and a team can describe a new program without waiting for a release of `the-cook`.

## Overview

`the-cook` declares a software development lifecycle: the stages a change goes through, the events fired between them,
the guards that hold between stages and the proof a change carries ([lifecycle](lifecycle.md)). The binary knows no
program. A stage that needs one reaches it through one of the three [contracts](#contracts) the binary declares, or
through an action the repository declares. An ingredient is a named, versioned description of one program, in data the
binary interprets ([ADR 0022](../adrs/0022-tool-integration.md)). It holds up to four parts:

| Part | What it is | When it acts |
| --- | --- | --- |
| [Operations](#operations) | the commands and mappings that fill one of the three contracts, or none | whenever the binary needs an answer from the program or starts it |
| [Renders](#renders) | owned files, keys, list entries and blocks | on every `the-cook render`, recorded in the manifest under the ingredient's name |
| [Floor](#the-floor) | deny rules and bypass arguments no repository data can remove | rendered on every render, refused at every launch |
| [Checks](#checks) | a version command with a supported range, and a detect rule | `doctor` runs the version command; `ingredient list --cookbook` uses the detect rule |

An ingredient has no entries and no templates: it never writes a file the repository then owns. Facts about programs
are pinned and rendered; practices are copied and become the repository's own. What a repository sets up once and
keeps, such as its lanes, its unattended denies and a loop runner's configuration file, comes from a
[station](stations.md), one way of running the lifecycle, which also pins the ingredients it uses; a kind of change it
makes many times is a [recipe](recipes.md), copied into `.cook/recipes/` and run through the lifecycle.

The profile pins ingredients under `ingredients:` ([profile](profile.md#ingredients)). `vcs:` names the ingredient that
fills the version-control contract, each worker's `ingredient` names the one that fills its worker contract, and a
worker's `jail.ingredient`, when it has one, the one that fills the jail contract around its attempts
([profile](profile.md#workers)). Applying a station pins the ingredients it lists, and
[`the-cook ingredient pin`](#the-cook-ingredient-pin) adds or moves pins on its own. Every render writes the renders
and floor of every pinned ingredient; deleting a pin and rendering removes exactly what that ingredient rendered.

Commands are named `the-cook <command>` on this page. The examples run them through the repository's wrapper,
`./cook <command>`, from the root of a checkout; with a global install, `the-cook <command>` is equivalent
([wrapper](wrapper.md)). The examples use a cookbook published at `github:acme/cookbook` and a coding agent,
`acme-agent`, that it describes.

## When to use it

| Situation | Command |
| --- | --- |
| See which ingredients the repository pins, what names each, and whether this machine has them | `./cook ingredient list` |
| See which ingredients a cookbook tag holds and which match this repository's files | `./cook ingredient list --cookbook github:acme/cookbook@v1.2.0` |
| Read everything an ingredient holds before pinning it | `./cook ingredient show <name> --cookbook github:acme/cookbook@v1.2.0` |
| Move an ingredient to a newer tag, such as a newer `git` ingredient | `./cook ingredient pin git --cookbook github:acme/cookbook@v1.3.0` |
| Take up a whole way of working, with the ingredients it uses | a station: `./cook station apply <name> --cookbook <source>@<version>` ([stations](stations.md)) |
| Use a program no applied station lists | `./cook ingredient pin <name> --cookbook <source>@<version>`, then name it in `vcs:`, a worker's `ingredient` or a worker's `jail.ingredient` when it fills a contract ([Examples](#a-program-no-station-lists)) |
| Confine a worker's attempts | pin a jail ingredient and name it in the worker's `jail` ([the jail contract](#the-jail-contract), [workers](workers.md#jails)) |
| Cache every pinned ingredient and applied station, and the version every added recipe came from, after cloning or in CI | `./cook fetch` ([Fetching](#fetching)) |
| Stop using a program | delete its pin from `ingredients:`, and the `vcs:` or worker that names it, then run `./cook render` |
| Describe a program no cookbook describes | write an ingredient and publish it in a cookbook ([cookbooks](stations.md#cookbooks)) |

### Writing one

**Authoring path:** [the ingredient file](#the-ingredient-file) (operations, renders, floor, checks) →
[render forms](#render-forms) (whole, keys, entries, block) → [the neutral model](#the-neutral-model) (values for
templates) → [the template language](ingredients.md#the-template-language) → test against sample repositories →
[cookbooks](stations.md#cookbooks) (build archive, checksums.txt, publish).

## Parts of an ingredient

### Operations

Operations are what the binary asks of a program and how it starts one. They fill one of the three
[contracts](#contracts) the binary declares ([ADR 0023](../adrs/0023-core-and-contracts.md)):

- the [version-control contract](#the-version-control-contract), filled by one ingredient per repository, the one the
  profile's `vcs:` names;
- the [worker contract](#the-worker-contract), filled by one ingredient per declared worker, the one
  `workers.<name>.ingredient` names;
- the [jail contract](#the-jail-contract), filled by the ingredient a worker's `jail.ingredient` names, for a worker
  whose attempts run confined.

An ingredient fills at most one contract, and many fill none: a loop runner's, a tracker's or a task runner's
ingredient has no operations. The lifecycle reaches those programs through the actions and the templates a station
writes into the repository's files, and the ingredient adds their renders, floor and checks. Every operation is a
command with placeholders and a declared output kind, or a mapping the binary reads as data. The binary never parses
a program's free text: an answer is a string, a list of lines, bytes or an exit status.

### Renders

A render is content the binary owns and writes on every `the-cook render`, from the repository's declared data and
the floor of every pinned ingredient: a whole file, keys of a JSON or TOML file, entries in a list such a file shares
with others, or a [managed block](render.md#managed-blocks). The manifest records each render with the ingredient's
name as its owner; `render --check` reports a hand edit to it as drift, the next render overwrites the edit, and
`the-cook remove` deletes it ([concepts](concepts.md#ownership-and-the-manifest)). A worker ingredient renders its
worker's native files, the link to the shipped skill and the import of `AGENTS.md` into the worker's own instruction
file, when it needs one, and renders them only for the workers that name it; a version-control ingredient renders the
ignore rules for the [state paths](profile.md#state-paths).

The binary renders its own content with no ingredient: `cook`, the shipped skill, the blocks of `AGENTS.md` and
`README.md`, and the manifest. When an ingredient leaves
`ingredients:`, or a worker leaves `workers`, the next render removes what was rendered for it.

### The floor

An ingredient may mark deny rules and bypass arguments as floor. Floor rules are part of its renders:

- Every floor deny rule of every pinned ingredient is rendered into the native permissions of every declared worker
  whose ingredient renders permissions, on every render. It is never written into `.cook/policy.yaml`, and the policy
  cannot remove or weaken it: an entry that tries, such as an `allow` for a command a floor rule denies, is dropped
  from the output and reported by `render` and `doctor` ([policy](policy.md#the-floor)).
- A worker ingredient's floor bypass arguments are refused by `the-cook attempt` and by `attempt` actions wherever
  they appear among the worker's arguments, with exit 1 naming the argument
  ([attempts](attempts.md#the-attempt-contract)).
- A jail ingredient's floor bypass arguments, the ones that would turn its confinement off, are refused wherever they
  appear in the launch of a jailed attempt, the worker declaration's `jail.args` included
  ([the jail contract](#the-jail-contract)).

Every floor rule of a program comes from the ingredient the repository pinned. A version-control ingredient typically
denies a force push to the default branch, writing `{default_branch}` where the branch goes; a worker ingredient marks
the arguments that would turn its worker's permission checks off; a jail ingredient marks the arguments that would turn
its confinement off; a task runner's ingredient may deny a way of chaining commands that would carry an unlisted command
past every rule.

The process that applies the floor belongs to the binary, because it is about the lifecycle and not about a program:
refusing bypass arguments before a launch, rendering the deny rules into every worker's output, replaying from the
base, withholding completion, the tamper check, and its own `unattended.deny` entry `[the-cook, lifecycle]`, which
names the binary's own command ([concepts](concepts.md#built-in-invariants),
[ADR 0011](../adrs/0011-safety-invariants.md)). The `policy` block of `AGENTS.md` lists each floor rule with the
ingredient it comes from.

A floor protects only while its ingredient is pinned: deleting an ingredient from `ingredients:` removes its floor
with it. Keep every program the repository uses pinned; `./cook ingredient list --cookbook <source>@<version>` shows
which ingredients of a tag match the repository's files.

Pinning an ingredient is the trust decision for its floor as for everything else it runs: no release of `the-cook`
vouches for an ingredient, and its floor is what its author marked ([Trust](#trust)).

### Checks

| Check | What it is | Used by |
| --- | --- | --- |
| `version` | a command that prints the program's version, and the range of versions the ingredient supports | [`doctor`](doctor.md): a version outside the range is a finding |
| `detect` | file globs whose match shows the program is in use | `ingredient list --cookbook` shows which ingredients of a tag match, so a repository finds a program it uses whose ingredient, and floor, it does not pin |

Both checks are optional. An ingredient without a version check supports any version of its program. Whether or not
an ingredient describes it, `doctor` reports a program that an ingredient, a declared command or an action names and
that is not installed.

## Contracts

Three needs take a program the binary cannot do without: version control at Start, Verify, Complete and Landed,
workers at Implement, and a jail around a worker's attempts, for a worker whose attempts the repository confines
([lifecycle](lifecycle.md)). For each, the binary declares a contract, and an ingredient's operations fill it. The
version-control and worker contracts serve every repository that runs those stages; the jail contract is optional, and
only a worker declaration that names a jail uses it. Each contract has a version, which an ingredient declares in its
file ([The ingredient file](#the-ingredient-file)).

### The version-control contract

The ingredient the profile's `vcs:` names fills this contract. Everything the binary reads from or changes in the
repository's history goes through it: the base commit of a managed workspace or of an attempt, the files the tamper
check restores from that base, the changes `verify` fingerprints, the branches `doctor` reports as landed, and the
default branch a floor rule names. The binary contains none of the commands that fill it; each version control system
is an ingredient with the same operations, and the contract asks as little of that system as it can.

| Operation | Placeholders | Output | Answers |
| --- | --- | --- | --- |
| `head` | | string | the commit the checkout is at |
| `root` | | string | the root of the current checkout |
| `primary_root` | | string | the root of the primary checkout, from the primary checkout or any workspace |
| `branch` | | string | the current branch |
| `default_branch` | | string | the name of the repository's default branch, the shared default line of work |
| `default_revision` | | string | the revision the shared default line of work is at, as this checkout last learned it; optional |
| `show` | `{rev}`, `{path}` | bytes | the content of `{path}` at `{rev}` |
| `clean` | | status | true when no tracked file is changed and no untracked file is present that is not ignored |
| `tracked` | `{path}` | status | true when the path is tracked |
| `changed` | `{rev}` | lines | the paths changed since `{rev}`, untracked files that are not ignored included |
| `patch` | `{rev}` | bytes | a patch from `{rev}` to the checkout's current content, new files included |
| `merge_base` | `{a}`, `{b}` | string | the merge base of `{a}` and `{b}` |
| `landed` | `{branch}`, `{target}` | lines | the commits of `{branch}` whose changes are not yet in `{target}`, compared by patch, so a cherry-pick, a rebase or a squash of the same changes counts as landed |
| `reset` | `{rev}` | status | the checkout returned to `{rev}`, untracked files removed except ignored ones, so the state directory and the `last-attempt.patch` saved there stay |
| `commit` | `{message}` | status | every change recorded as one commit with `{message}` |
| `update` | | status | the checkout has learned what is shared: where each shared line of work is, the default one included; optional |
| `branch_create` | `{branch}`, `{rev}` | status | `{branch}` created at `{rev}` |
| `switch` | `{branch}` | status | the checkout switched to `{branch}` |
| `workspace_add` | `{path}`, `{branch}`, `{rev}` | status | a new workspace at `{path}`, on a new `{branch}` starting at `{rev}` |
| `workspace_remove` | `{path}` | status | the workspace at `{path}` removed |
| `metadata` | | lines | the absolute paths that hold the checkout's version-control metadata, its history, its configuration and the hooks the program runs, which a worker must not write; optional |

| Output kind | The binary reads |
| --- | --- |
| string | the output's first line, without surrounding whitespace |
| lines | one item per non-empty line |
| bytes | the output as printed |
| status | the exit status only: 0 is true, as for `clean`, or success |

Every operation is required except `default_revision`, `update` and `metadata`. The binary uses `default_branch` where
it needs the default branch as a name: the `{default_branch}` placeholder of floor rules and of the
[neutral model](#the-neutral-model), `COOK_DEFAULT_BRANCH`, the [delivery guard](lifecycle.md#built-in-guards) against
the default branch and the refusals of `deliver`. It uses `default_revision` where it needs the default branch as a
commit: the base of the workspace a [recipe run](recipes.md#the-run) creates and the commit the run reads its recipe,
the profile, the policy and the hooks file from; the base of the fresh workspace an `attempt` action on `landed` runs
in; the `{target}` of `landed`, when `workspace remove` and `doctor` detect landed work; the commit `fleet --deliver`
starts its branch from; and the commit `verify` takes the merge-base with outside a managed workspace. Without
`default_revision`, the binary uses `default_branch` as the revision. Each ingredient answers in its own system's terms:
a git ingredient answers `origin/<branch>`, an ingredient for a centralized system the server's head, and one for a
system with no shared copies the branch itself.

A checkout learns what is shared only through `update`, and only where a command or the repository asks for it:
`fleet --deliver` runs it before it creates its branch ([fleet](fleet.md#for-each-repository)), and
`workspace remove` runs it before it detects landed work when the profile sets `workspaces.update_before_landed`
([workspaces](workspaces.md#remove)). Nothing else runs it: `make`, `workspace create` and `doctor` use what the
checkout knows. Where a command calls `update` and the ingredient has none, the command prints one note and goes on
with what the checkout knows.

The binary uses `metadata` only to share those paths read-only inside a [jail](#the-jail-contract). A worker declared
with a jail is a validation error, exit 1, when the version-control ingredient has no `metadata`: without it the worker
could write the configuration and the hooks the binary's own commit runs, outside the jail. A git ingredient answers
the checkout's git directory, the common directory and, in a linked workspace, its `.git` file.

Version-control operations run outside any jail, with the person's environment, on files a worker may have written.
An operation that would run code the checkout defines, such as hooks a version control program reads from a tracked
directory, turns that code off where the program allows it ([ADR 0027](../adrs/0027-worker-jails.md)).

The binary finds the repository root itself, as the nearest directory upward that holds `.cook/config.yaml`, so
reading the profile never needs this contract; workspace, base and history operations do. A command that needs the
contract while `vcs:` names no pinned ingredient exits 3 and names both ways to fill it:
`the-cook station apply` with a station whose stages name a version-control ingredient, or `the-cook ingredient pin`
followed by `vcs:` in the profile. The version-control ingredient's program missing is a missing prerequisite too: the
command exits 3 and names the program.

### The worker contract

One ingredient per declared worker fills this contract: the ingredient `workers.<name>.ingredient` names, by default
the ingredient named like the worker. A worker is any program that changes the repository for someone, such as a
coding agent, a bot or a codemod. Adding a worker means pinning an ingredient that fills this contract and declaring
the worker; the policy, the hooks file and the other ingredients do not change
([ADR 0009](../adrs/0009-agent-support.md)). Every item is optional except `launch.command`, because a program with no
permission prompts, hooks or instruction file still runs as a worker:

| Item | Declares | When the ingredient leaves it out |
| --- | --- | --- |
| `launch.command` | the program the binary starts | required |
| `launch.unattended` | the arguments that start the worker unattended with its permission checks on | the worker starts with `launch.command` and its caller's arguments only |
| `launch.unattended_deny` | how `unattended.deny` reaches the worker as arguments at launch | it reaches the worker only through the rendered rules, which then require `role: unattended` ([workers](workers.md#roles)); with no rendered permissions either, the worker cannot run attempts: `the-cook attempt` and `attempt` actions naming it exit 3, and it may still be an interactive worker |
| `launch.sandbox_roots` | the arguments that open a directory beyond the checkout for writing: each entry of `workers.<name>.writable_roots` | `writable_roots` on the worker is a validation error, unless the worker has a jail, whose write shares then carry them |
| `launch.network` | the arguments that let the worker's attempts use the network, passed at every unattended launch of a worker declared with `network: true` | `network: true` on the worker is a validation error; with `network: false` the ingredient states nothing about the network, and its page in its cookbook says what the program does by default |
| `launch.model`, `launch.effort` | how a worker's model and effort, from `workers.<name>` or an `attempt` action, are passed | a model or an effort set for the worker is ignored, and `doctor` notes it |
| `launch.prompt` | how the binary passes the prompt of an `attempt` action or a recipe's `attempt` step | an `attempt` action or step naming the worker fails validation |
| `floor.bypass` | the arguments that would turn the worker's permission checks off, refused at every launch ([the floor](#the-floor)) | nothing is refused beyond the binary's own rules |
| `payload` | the JSON paths in the worker's hook payload that give the neutral event, the session, the tool, the edited and read files, the working directory, the person's prompt text and the worker's notification message; which tools are edits and which are reads; the native event that carries each worker event: `session-start`, `post-tool-use`, `prompt-submit`, `attention`, `turn-end` | no hook is rendered for the worker, standing context reaches it only through `static` entries and, in an attempt whose prompt arrives on stdin, through the prompt ([context](context.md#delivery-in-the-prompt)), and `doctor` notes each event it lacks |
| `instructions` | how the worker reaches `AGENTS.md`: through an import line in a block of its own instruction file | the worker reads `AGENTS.md` directly, or no instruction file |
| `skills` | where the link to the shipped skill goes | no skill link |
| `native_rules` | the patterns that recognise a policy string as one of the worker's native rule strings; which files and keys hold the worker's permission rules in each [import scope](scaffold.md#import-scopes), the native rule shape that reads back as a command prefix, and the keys that change permission behaviour without being rules, which adoption reports as notes; [`scaffold --adopt`](scaffold.md#how-each-source-becomes-an-entry) imports from them | no policy string is native for the worker, and adoption imports nothing for it |
| renders | the worker's native files, keys and entries, rendered from the neutral model ([renders](#renders)) | nothing is rendered for the worker, and every policy entry it does not receive at launch is marked in the `policy` block of `AGENTS.md` as not enforced in it |
| checks | the worker versions the ingredient supports | any version |

An item absent is a capability the worker lacks, and the rest of the
[attempt contract](attempts.md#the-attempt-contract) holds without it: the tamper check, the replay from the base and
the withheld completion apply to every attempt. The one exception is `unattended.deny`: a worker it cannot reach, at
launch or through rendered rules, runs no attempt at all. A worker declaration whose ingredient is not in
`ingredients:`, or does not fill this contract, fails validation with exit 1. The
[example ingredient](#an-example-ingredient) fills this contract for a coding agent named `acme-agent`; with its
mapping, the policy entry `[git, push]` becomes `Run(git push *)` in `permissions.deny`, and under
`the-cook attempt acme-agent` an `unattended.deny` entry `[acme-deploy]` is passed as `--deny 'Run(acme-deploy *)'`.

### The jail contract

A jail is a program that starts another program confined to what it is given: the directories it shares read-only or
read-write, the environment variables it passes and the network it opens. It runs the host's own programs at the host's
own paths, so the worker program, the version-control program and `the-cook` inside it are the same files as outside.
A worker declaration that names a jail, `workers.<name>.jail` ([workers](workers.md#jails)), has the binary start the
worker of each of its attempts through that jail: under `the-cook attempt`, an `attempt` action and a recipe's
`attempt` step. The binary never starts an interactive session, so no jail wraps one: a person who wants a session
confined starts it inside the jail, and the rendered files apply there as they do without one
([ADR 0027](../adrs/0027-worker-jails.md)).

A container image and another machine are not jails. An image brings its own copies of the programs, so the version
checks, the floor and the renders would describe a program other than the one that runs, and the binary would need a
build for the image's platform; another machine has its own files, so the tamper check, the replay and the commit could
not see what the worker changed. A repository that wants its workers in a container or on another machine runs the
whole recipe run or loop run there, with `the-cook` installed there, such as a loop runner whose command for each task
starts `./cook make <recipe> --task {id} --unattended` inside the container. The core needs nothing for that.

The ingredient `workers.<name>.jail.ingredient` names fills this contract, declared as
`contract: { name: jail, version: 1 }`:

| Item | Declares | When the ingredient leaves it out |
| --- | --- | --- |
| `launch.command` | the jail program and its fixed arguments | required |
| `launch.separator` | the arguments that end the jail's own and begin the confined command, such as `[--]` | the confined command follows the jail's arguments directly |
| `share.workdir` | the arguments that share the jail's working directory read-write and start the confined command in it, `{path}` replaced | the jail shares the directory it was started in read-write by itself |
| `share.write` | the arguments passed once per further directory the confined command may write, `{path}` replaced | required |
| `share.read` | the arguments passed once per directory it may only read, `{path}` replaced | required |
| `env.pass` | the arguments passed once per variable, `{name}` and `{value}` replaced | required unless `env.inherited` |
| `env.inherited` | `true`: the jail keeps the environment of the process that starts it | required unless `env.pass`; exactly one of the two |
| `network` | the arguments that open the network to the confined command, passed for a worker declared with `network: true` | `network: true` on a worker in this jail is a validation error; with `network: false` the jail's own default holds, which its page in its cookbook explains |
| `floor.bypass` | the jail's arguments that would turn its confinement off, refused wherever they appear ([the floor](#the-floor)) | nothing is refused beyond the worker's floor |
| renders, checks | as for any ingredient: [renders](#renders), [checks](#checks) | as for any ingredient |

The contract also requires what no item can declare, which the ingredient's page in its cookbook states for its
program: the jail passes stdin, stdout and stderr through unchanged, exits with the confined command's exit status, and
a read share inside the working directory takes precedence over it, so that path stays read-only inside the read-write
checkout. The binary never passes a write share that contains a read share, because a jail may drop such a share
without failing.

#### The jailed launch

The binary composes the launch of a jailed attempt in this order:

1. the jail's `launch.command`;
2. the jail's `share.workdir`, with the attempt's checkout;
3. one `share.write` per directory in `workers.<name>.writable_roots`;
4. one `share.read` per directory the confined command may only read: first each path the version-control ingredient's
   `metadata` operation reports, then the per-user cache, `$COOK_HOME`, which holds the pinned release and the cached
   ingredients that `./cook verify` and the hooks inside load;
5. one `env.pass` per variable, unless the jail declares `env.inherited`: every `COOK_*` variable the binary sets for
   the worker (`COOK_ATTEMPT`, and `COOK_CONTEXT_IN_PROMPT` when it is set), `COOK_NO_CONTEXT` when the attempt itself
   runs with it, and `COOK_HOME` set to the cache's absolute path, so the wrapper inside finds the shared cache whatever
   home the jail gives it;
6. the jail's `network` arguments, when the worker is declared with `network: true`;
7. the worker declaration's `jail.args`, the repository's choice of the jail's options;
8. the jail's `launch.separator`;
9. the worker's launch exactly as without a jail: its command, unattended arguments, `unattended.deny`, sandbox roots,
   its own network arguments, model and effort, and the prompt arguments.

The binary starts the jail in the attempt's checkout, with every variable it passes set in the jail's own environment
too, so a jail whose `env.pass` names a variable without `{value}` and takes the value from its environment gets the
right one. The checkout is the jail's working directory and never a `share.write`: the writable set is the checkout
and `writable_roots`, and the clone's state directory is not in it ([attempts](attempts.md#the-attempt-contract)). It
refuses the floor bypass arguments of both ingredients wherever they appear in the whole launch, the caller's arguments
and `jail.args` included: a jail bypass argument in the profile's `jail.args` is a validation error, exit 1, and any
other bypass argument is refused at launch with exit 1, naming it. Nothing else of the person's home is shared: how the
worker program's own login and settings reach the jail is the jail's own configuration or `jail.args`, which the jail
ingredient's page explains.

Inside a jail the cache is read-only. `the-cook` commands run there, such as `./cook verify` and the hooks, skip the
update check and every other cache write, and never need a download, because the attempt resolved every ingredient
before it started the jail.

#### What runs in the jail

From the moment the worker starts until the attempt ends, every command the binary starts on the attempt's checkout runs
in the worker's jail, with the same shares, variables, network and `jail.args`, the confined command being that command
instead of the worker: the replay's lane commands and validators. In a recipe run, once an `attempt` step of a worker
with a jail has run, the later `run` steps, the Verify stage and the replay of Complete run in that jail too
([recipes](recipes.md#attempt)). What runs before the worker starts, the profile's `bootstrap` on the base, and the
binary's own work outside the checkout's code, the version-control operations, the tamper check, the commit and the
lifecycle actions, runs outside the jail with the person's environment.

A worker with a jail fails validation, exit 1, when:

- `jail.ingredient` is not pinned in `ingredients:` or does not fill the jail contract;
- the jail ingredient declares neither or both of `env.pass` and `env.inherited`, or lacks `share.read` or
  `share.write`;
- the worker is declared with `network: true` and its jail has no `network`;
- `jail.args` holds a floor bypass argument of the jail;
- an entry of `writable_roots` contains a path the version-control ingredient's `metadata` reports, or the per-user
  cache, because a write share over a read share can make the jail drop one of them without failing;
- the version-control ingredient has no `metadata` operation.

The jail's program not found and the jail ingredient not cached are missing prerequisites: the attempt exits 3, naming
the program or `./cook fetch`, and without a terminal fires `blocked`. `doctor` checks the jail's version against its
ingredient's range and reports a jail program that is not installed, as for a worker.

#### An example jail ingredient

`acme-jail` is a jail described in `github:acme/cookbook`:

```yaml
schema: 1
name: acme-jail
version: v1.2.0
description: acme-jail, which runs a program confined to the directories and variables it is given
contract: { name: jail, version: 1 }
checks:
  version: { run: [acme-jail, --version], range: ">=2.0 <3.0" }
floor:
  bypass: [--unconfined]
operations:
  launch:
    command: [acme-jail, run]
    separator: [--]
  share:
    workdir: [--project, "{path}"]
    write: [--rw, "{path}"]
    read: [--ro, "{path}"]
  env:
    pass: [--env, "{name}={value}"]
  network: [--net]
```

A repository declares the [example worker ingredient](#an-example-ingredient) `acme-agent` in it, and passes its
login token by name with an option of the jail:

```yaml
# .cook/config.yaml, with acme-agent and acme-jail pinned in ingredients:
workers:
  acme-agent:
    role: unattended
    writable_roots: [.tasks]
    network: true
    jail: { ingredient: acme-jail, args: [--keep-env, ACME_AGENT_TOKEN] }
```

In the managed workspace `run-invoice` of `/home/dev/src/acme-api`, with `unattended.deny` holding `[git, push]` and the
prompt on stdin, `the-cook attempt acme-agent` starts:

```text
acme-jail run \
  --project /home/dev/src/acme-api/.work/workspaces/run-invoice \
  --rw /home/dev/src/acme-api/.tasks \
  --ro /home/dev/src/acme-api/.git/worktrees/run-invoice \
  --ro /home/dev/src/acme-api/.git \
  --ro /home/dev/src/acme-api/.work/workspaces/run-invoice/.git \
  --ro /home/dev/.cache/the-cook \
  --env COOK_ATTEMPT=1 --env COOK_CONTEXT_IN_PROMPT=1 --env COOK_HOME=/home/dev/.cache/the-cook \
  --net \
  --keep-env ACME_AGENT_TOKEN \
  -- \
  acme-agent --mode unattended \
  --deny 'Run(the-cook lifecycle *)' --deny 'Run(./cook lifecycle *)' --deny 'Run(cook lifecycle *)' \
  --deny 'Run(git push *)' \
  --writable /home/dev/src/acme-api/.tasks \
  --network allow --print
```

The workspace's `.git` file is a read share inside the read-write checkout, so it stays read-only there. The replay of
the receipt the worker leaves runs each lane command in the place of the `acme-agent` part, after the same `--`, with
the same shares, variables and options. `--unconfined`, the jail's floor bypass argument, fails validation in
`jail.args` and is refused at launch among the caller's arguments.

## Pinning

Every entry of `ingredients:` has the same four fields:

```yaml
# .cook/config.yaml
ingredients:
  - name: git
    source: github:acme/cookbook
    version: v1.2.0
    sha256: d2f0d562418c7d176d0393d8694cc6f6f569f2dff3f7ede3788a2075d3ff5c3f
```

| Field | Meaning |
| --- | --- |
| `name` | the ingredient's name; it must equal the `name` in its `ingredient.yaml`, and is unique within `ingredients:` |
| `source` | the [cookbook](stations.md#cookbooks) |
| `version` | the cookbook tag; it must equal the `version` in its `ingredient.yaml` |
| `sha256` | the sha256 of the archive, `ingredient-<name>.tar.gz`, as the tag's `checksums.txt` states it |

Two commands write these entries, and each shows them as a diff and asks before writing:

- [`the-cook station apply`](stations.md#how-apply-works) pins every ingredient the applied stations list, at the
  sha256 each station lists, in the same diff as the station's entries and templates. An ingredient the repository
  already pins keeps the repository's pin; when the sha256 differs, the difference is reported:
  `kept ingredients.git: pinned at v1.1.0, station agentic-coding lists v1.2.0`.
- [`the-cook ingredient pin`](#the-cook-ingredient-pin) adds or moves pins only, from one cookbook tag, and renders.
  Pinning a name already in `ingredients:` at another version or from another cookbook moves its pin.

Pinning declares an ingredient and renders its renders and floor; it declares no worker and writes no `vcs:`. A
station's stages do that when it is applied, and a person can write them in the profile. The profile fails validation
with exit 1, for every command that reads it, when two entries of `ingredients:` share a name, when `vcs:` names an
ingredient that is not pinned or does not fill the version-control contract, and when a worker's `ingredient` is not
pinned or does not fill the worker contract.

### Trust

Pinning from a tag trusts the cookbook's `checksums.txt` as downloaded over HTTPS at that moment. An ingredient pinned,
or a recipe copied, by applying a station is trusted through the station: the station's archive is verified against its
tag's `checksums.txt`, and the sha256 it lists for each ingredient and each recipe is what that archive must match.
Either way the pins enter the repository only through a reviewed diff, and from then on the binary verifies every
archive against its pin, on download and on every load. No release key vouches for an ingredient. An ingredient is data,
so a reviewer can read all of it with `the-cook ingredient show <name> --cookbook <source>@<version>` before approving
the pin: that review is the trust decision for everything the ingredient runs, its operations, renders and floor
included.

## Fetching

The binary stores each ingredient, station and recipe archive in the per-user cache as
`$COOK_HOME/cookbook/<sha256>.tar.gz`, outside the repository, and checks its sha256 every time it loads it: a
mismatch, on download or on load, stops the command with exit 1, and the archive is not used.

Only these commands download, always over HTTPS:

| Command | Downloads |
| --- | --- |
| `the-cook station apply --cookbook` | the tag's `checksums.txt`, the named stations, every ingredient they list that is not cached, and every recipe they add that `.cook/recipes/` lacks |
| `the-cook station apply` | a recorded station it applies, and an ingredient that station lists, that is not cached; a recipe the station adds that `.cook/recipes/` lacks, verified against the sha256 the station lists |
| `the-cook ingredient pin` | the tag's `checksums.txt` and the named ingredients |
| `the-cook recipe add` | the tag's `checksums.txt` and the named recipes |
| `the-cook recipe update` | the tag's `checksums.txt`, the newer recipe, and the version the copy came from when it is not cached |
| `the-cook fetch` | every pinned ingredient, every applied station and the version every added recipe came from, that is not cached |
| `the-cook scaffold --cookbook` | the tag's `checksums.txt`, its stations, the ingredients the chosen stations list and the recipes they add |
| `the-cook ingredient list --cookbook`, `the-cook ingredient show --cookbook`, `the-cook station list --cookbook`, `the-cook station show --cookbook`, `the-cook recipe list --cookbook`, `the-cook recipe show --cookbook` | the tag's `checksums.txt` and the archives they read |

Nothing else fetches. `render`, `doctor`, the hooks a worker fires, `the-cook attempt`, `the-cook make` and `attempt`
actions read an ingredient only from the cache. When a pinned ingredient is not there, `render` exits 3 naming
`./cook fetch`, an attempt fails with exit 3 before any worker starts, a recipe run exits 3 before it creates anything,
a hook prints one line on stderr and exits 0, and `doctor` reports a finding. A fresh clone works offline once its
ingredients are cached, and a CI job runs `./cook fetch` before `render --check` or `doctor`. An attempt resolves the
version-control ingredient, the worker's ingredient and its jail's from the cache before anything else and uses that
resolution for every read, comparison, reset and launch of the attempt ([attempts](attempts.md#the-attempt-contract)).
Nothing rendered depends on a station, so a station missing from the cache stops only `station show` and `station apply`
without `--cookbook`, which fetch it. Nothing a run reads comes from a recipe's origin either: a recipe runs from
`.cook/recipes/`, and the version it came from serves only `recipe update`, which fetches it when it is not cached.

## Reference

```text
the-cook ingredient list [--cookbook <source>@<version>]
the-cook ingredient show <name> [--cookbook <source>@<version>]
the-cook ingredient pin <name>... --cookbook <source>@<version> [--yes] [--dry-run]
the-cook fetch
```

`ingredient` follows the `the-cook <noun> <verb>` shape; `fetch` serves ingredients, stations and recipes alike. An
ingredient leaves `ingredients:` by an edit and a render ([Overview](#overview)). Global flags `-C <dir>`, `--json` and
`--verbose` apply to every subcommand; see [CLI reference](cli.md).

**Jump to:** [`list`](#the-cook-ingredient-list) | [`show`](#the-cook-ingredient-show) |
[`pin`](#the-cook-ingredient-pin) | [`fetch`](#the-cook-fetch) | [flags](#the-cook-ingredient-list) |
[exit codes](#the-cook-ingredient-list) | [ingredient file](#the-ingredient-file) |
[render forms](#render-forms) | [neutral model](#the-neutral-model)

### `the-cook ingredient list`

Without `--cookbook`, prints the ingredients `ingredients:` pins, each with its version and source, the contract it
fills, what names it (`vcs` or the workers whose `ingredient` it is), and whether it is cached on this machine. It
reads only the cache: an ingredient that is not cached is listed as `not cached`, without its contract.

With `--cookbook <source>@<version>`, prints the ingredients that tag publishes, each with the contract it fills,
whether its detect rule matches in this repository, and the version `ingredients:` pins it at, if any. It works in a
repository that is not scaffolded yet.

| Flag | Default | Meaning |
| --- | --- | --- |
| `--cookbook <source>@<version>` | none | List that cookbook tag's ingredients instead of the pinned ones |

| Exit code | Meaning |
| --- | --- |
| 0 | Listed |
| 1 | With `--cookbook`: the source cannot be reached, the tag has no `checksums.txt`, or an archive does not match it |

Files read: `.cook/config.yaml` when present, `$COOK_HOME/cookbook/`; with `--cookbook`, the tag's `checksums.txt` and
archives and the files each detect rule names. Files written: with `--cookbook`, each verified archive into
`$COOK_HOME/cookbook/`.

### `the-cook ingredient show`

Prints the ingredient's `ingredient.yaml`, its operations, every render with its target, its form and its template,
its floor rules and its checks. Without `--cookbook` the ingredient is a pinned one; with it, the ingredient of that
name in that tag. It does not compare anything with the repository; `render --dry-run` shows what rendering would
change.

| Flag | Default | Meaning |
| --- | --- | --- |
| `<name>` | required | The ingredient |
| `--cookbook <source>@<version>` | none | Read the ingredient from that cookbook tag instead of the pinned one |

| Exit code | Meaning |
| --- | --- |
| 0 | Printed |
| 1 | A name `ingredients:` does not pin, which names `--cookbook`; a cached archive that does not match its `sha256`; with `--cookbook`, a name the tag does not publish, a source that cannot be reached, or an archive that does not match the tag's `checksums.txt` |
| 3 | A pinned ingredient that is not cached: run `./cook fetch` |

Files read: `.cook/config.yaml`, when present; `$COOK_HOME/cookbook/<sha256>.tar.gz`; with `--cookbook`, the tag's
`checksums.txt` and the archive. Files written: with `--cookbook`, the verified archive into `$COOK_HOME/cookbook/`.

### `the-cook ingredient pin`

Adds or moves ingredient pins from one cookbook tag ([Pinning](#pinning)): downloads the tag's `checksums.txt` and the
named archives over HTTPS, verifies each archive against it, checks each `ingredient.yaml` (a supported schema and
contract version, the name and the version), shows the `ingredients:` entries to add or move as a diff and asks, then
writes them, caches the archives and renders, so the pins and their rendered effect are one diff. It writes nothing
else into the profile.

| Flag | Default | Meaning |
| --- | --- | --- |
| `<name>...` | required | One or more ingredients the tag publishes |
| `--cookbook <source>@<version>` | required | The cookbook and the tag to pin from |
| `--yes` | off | Pin without asking; required without a terminal |
| `--dry-run` | off | Print the entries to add or move; write nothing |

With `--json`, the output lists each entry added or moved, with its previous version for a move.

| Exit code | Meaning |
| --- | --- |
| 0 | Pinned, moved, or already pinned at that version; `--dry-run` printed |
| 1 | Error: a repository that is not scaffolded, an invalid repository file, no network or a source that cannot be reached, a name the tag does not publish, an archive that does not match the tag's `checksums.txt`, an invalid `ingredient.yaml`, one whose name or version differs from the request, one whose schema or contract version is newer than the running release supports (the message names the release needed), or a moved pin whose new version does not fill the contract `vcs:` or a worker needs. Nothing is written |
| 3 | Confirmation needed and there is no terminal: rerun with `--yes`. Nothing is written |

Files read: `.cook/config.yaml`, the tag's `checksums.txt` and the named archives, and everything
[`render`](render.md#reference) reads. Files written: `$COOK_HOME/cookbook/<sha256>.tar.gz` for each verified archive;
`ingredients:` in `.cook/config.yaml`; then every file `render` writes.

### `the-cook fetch`

Downloads every ingredient `ingredients:` pins, every station `stations:` records, and the version every recipe in
`.cook/recipes/` with an `origin` came from, that is not in the cache. It checks each archive's sha256 against its pin,
or against the recipe's `origin.sha256`, checks its `ingredient.yaml`, `station.yaml` or `recipe.yaml` (a supported
schema and contract version, the pinned name and version) and stores it in the cache, so `recipe update` can merge
offline. An archive already cached is checked and left. Run it once per machine after cloning, after the pins or an
`origin` change, and in CI before `render --check` or `doctor`.

It takes no arguments.

| Exit code | Meaning |
| --- | --- |
| 0 | Every pinned ingredient, every applied station and the version every added recipe came from is cached and verified, or there was nothing to fetch |
| 1 | No network, or a source that cannot be reached; an archive whose sha256 differs from its pin or its `origin` (both hashes are printed); an invalid `ingredient.yaml`, `station.yaml` or `recipe.yaml`, one whose name or version differs from its pin, or one whose schema or contract version is newer than the running release supports. A failing archive is not stored; the others are |

Files read: `.cook/config.yaml`, the `origin` of each recipe in `.cook/recipes/`, `$COOK_HOME/cookbook/`. Files written:
`$COOK_HOME/cookbook/<sha256>.tar.gz`, on a verified download only.

### The ingredient file

An ingredient is a directory:

```text
<name>/
  ingredient.yaml  the ingredient's description
  files/           render templates, when the ingredient keeps them outside ingredient.yaml
```

| Key of `ingredient.yaml` | Type | Required | Meaning |
| --- | --- | --- | --- |
| `schema` | integer | yes | Format version of the ingredient file: `1` |
| `name` | string | yes | Lowercase letters, digits and `-`: the name `ingredients:`, `vcs:`, `workers.<name>.ingredient`, `workers.<name>.jail.ingredient` and a station's `ingredients` use |
| `version` | string | yes | The cookbook tag the ingredient is published under, which `ingredients:` pins |
| `description` | string | no | One line, printed by `ingredient list` |
| `contract.name` | `vcs` \| `worker` \| `jail` | with `operations` | The contract `operations` fills; `contract` is absent when the ingredient has no operations |
| `contract.version` | integer | with `contract.name` | The version of that contract the operations are written for: `1` for each of the three contracts in this release line |
| `operations` | map | with `contract` | The operations of the [version-control contract](#the-version-control-contract), or the items of the [worker contract](#the-worker-contract) or of the [jail contract](#the-jail-contract) |
| `renders` | list | no | [Renders](#renders): `file`, the target relative to the repository root, and exactly one of `whole`, `keys`, `entries`, `block` ([render forms](#render-forms)) |
| `floor.deny` | list of policy entries | no | Floor deny rules, in the [policy entry forms](policy.md#entry-forms); `{default_branch}` is replaced by what the version-control ingredient reports |
| `floor.bypass` | list of arguments | no | Floor bypass arguments; only with `contract.name: worker` or `contract.name: jail` |
| `checks.version` | map | no | `run`, a command that prints the version, and `range`, the supported versions, such as `">=2.1 <2.2"`. The first dotted number in the output is the version |
| `checks.detect` | list of globs | no | Paths relative to the repository root whose match shows the program is in use; a trailing `/` matches a directory |

The keys under `operations` for the worker contract:

| Key | Type | Meaning |
| --- | --- | --- |
| `launch.command` | command | The program and its fixed arguments |
| `launch.unattended` | list of arguments | Added at every unattended launch |
| `launch.unattended_deny` | `{ flag, item }` | `flag` is passed once per entry of `unattended.deny`, followed by `item`, a template rendered with that entry ([the neutral model](#the-neutral-model)) |
| `launch.sandbox_roots` | list of arguments | Passed once per writable directory, with `{path}` replaced by it |
| `launch.network` | list of arguments | Passed at every unattended launch of a worker declared with `network: true` |
| `launch.model`, `launch.effort` | list of arguments | Passed when a model or an effort is set, with `{model}` or `{effort}` replaced by it |
| `launch.prompt` | `{ stdin, args }` | `stdin: true` passes the prompt of an `attempt` action or a recipe's `attempt` step on stdin, with the `always` entries of the `unattended` role before it ([context](context.md#delivery-in-the-prompt)); `args` are added with it |
| `payload` | map | `event`, `session`, `tool`, `file`, `cwd`, `prompt` and `message`, each a dotted JSON path in the payload; `edit_tools` and `read_tools`, lists of tool names; `events`, a map from each worker event to the native event that carries it |
| `instructions` | `{ file, block, line }` | The worker's own instruction file, the id of the managed block rendered into it, and the import line the block holds |
| `skills` | path | Where the link to `.agents/skills/the-cook` is rendered |
| `native_rules` | map | `patterns`, a list of RE2 regular expressions (Go `regexp` syntax), each matched against the whole policy string; `keys`, the native keys that hold `allow`, `ask` and `deny`; `project`, `local` and `user`, the files of each import scope; `prefix`, the native rule shape that reads back as a command prefix; `notes`, the keys adoption reports as not policy |

The keys under `operations` for the jail contract ([the jailed launch](#the-jailed-launch)):

| Key | Type | Meaning |
| --- | --- | --- |
| `launch.command` | command | The jail program and its fixed arguments |
| `launch.separator` | list of arguments | Passed between the jail's arguments and the confined command |
| `share.workdir` | list of arguments | Passed once, with `{path}` replaced by the attempt's checkout, which the jail shares read-write and starts the confined command in |
| `share.write` | list of arguments | Passed once per entry of `writable_roots`, with `{path}` replaced by it |
| `share.read` | list of arguments | Passed once per directory the confined command may only read, with `{path}` replaced by it |
| `env.pass` | list of arguments | Passed once per variable, with `{name}` and `{value}` replaced; exactly one of `env.pass` and `env.inherited` |
| `env.inherited` | boolean | `true`: the jail keeps the environment of the process that starts it, and the binary passes no `env.pass` arguments |
| `network` | list of arguments | Passed for a worker declared with `network: true` |

`native_rules.patterns` decide the form of every string in the [policy](policy.md#entry-forms): a string is a native
rule string for each declared worker whose ingredient has a pattern it matches, and is rendered verbatim for those
workers. A string that matches a pattern of any declared worker is never a bare string; one that matches no pattern of
any declared worker is a bare string, shorthand for a shell command rule. The binary holds no rule syntax of its own.

The binary refuses an ingredient whose `schema` or `contract.version` is newer than it supports, with exit 1 naming
the release needed, and reads older ones inside its support window.
`the-cook version` prints the ingredient and station schemas and the contract versions the running release supports.

Unknown keys are an error, and so are `entries` and `templates`, which only a station holds. A command, in `checks` or
in an operation, is a list of arguments run without a shell from the root of the checkout the binary works in. A
placeholder such as `{rev}` is replaced inside the argument it appears in, so a value never splits into more arguments
or reaches a shell. An operation that needs a shell names `sh` and passes values as positional parameters:
`run: [sh, -c, 'tool diff --names "$1" && tool status --untracked', sh, "{rev}"]`.

#### Render forms

| Form | Fields | Owns |
| --- | --- | --- |
| `whole` | `whole: true`, `template` | the whole file |
| `keys` | a map from a dotted key path to `{ each, item }`, a list with one `item` per element of the model list `each`, or to `{ value }` | those keys of a JSON or TOML file; every other key is preserved byte for byte ([render](render.md#merge-rendering-native-files)) |
| `entries` | a dotted path to a list, and `{ each, item }`; the path may name values of the element, such as `"hooks.{{event}}"`, so the entries spread over lists keyed by a value | its own entries in the lists the file shares with others, such as a worker's hook entries kept in one list per event |
| `block` | a block id, and `template` | a [managed block](render.md#managed-blocks) between the `cook:<id>` markers |

In `keys` and `entries`, `item` is a template string or a YAML value whose strings are templates, rendered once per
element of `each`, so an entry can be an object such as `{ matcher: "{{matcher}}", command: "{{command}}" }`.

#### The template language

Render templates, the templates of a [station](stations.md#templates) and the `template` steps of a
[recipe](recipes.md#template) use one logic-free language: `{{name}}` inserts a value of the neutral model below,
`{{#name}}...{{/name}}` repeats its text for each element of a list, or renders it once when a value is present, and
`{{^name}}...{{/name}}` renders its text when the value is empty or absent. There are no comparisons, functions or
includes. A template's text is inline in `ingredient.yaml` or in a file under `files/`, named by its path. A recipe's
`template` step reads the run's inputs and `task` as its values instead of the neutral model.

A tag `{{=<% %>=}}` sets other delimiters for the rest of the template, as in Mustache, here `<%name%>` and
`<%#name%>...<%/name%>`, so a template whose output itself holds `{{`, such as a CI workflow with `${{ ... }}` or
another program's prompt template, writes those braces as text. A section or delimiter tag alone on its line leaves no
blank line in the output, as in Mustache. A name that the template's values do not define is an error that names the
template and the tag, so a misspelt name fails when the ingredient, the station or the recipe is read, never by writing
an empty value.

#### The neutral model

The values a template reads:

| Value | Holds |
| --- | --- |
| `workers` | the declared workers, each with `name`, `role` and `ingredient` |
| `worker` | in a worker ingredient's renders: the worker being rendered, with `name` and `role` |
| `allow`, `ask`, `deny` | the policy entries for `worker`'s role, alternatives expanded, each with exactly one of `prefix` (with `words`, the words joined by spaces, and `argv`, the words as a quoted list), `native` or `bare` (with `string`). `deny` holds every floor rule of every pinned ingredient and, for `role: unattended`, `unattended.deny` |
| `hooks` | the worker events the declarations need, each with `event` (the native event the ingredient's payload mapping names), `matcher` (its edit tools, and its read tools when `when.paths` context exists, joined with `\|`) and `command` |
| `raw` | the `raw` hook entries, each with `event`, `matcher` and `run` |
| `state_dir`, `workspaces_dir` | `state.dir` and `workspaces.dir` from the profile |
| `default_branch` | the default branch the version-control ingredient reports |

A hook's `command` is the hook command, which finds the wrapper through the
version-control ingredient's `root` operation.

#### An example ingredient

`acme-agent` is a coding agent described in `github:acme/cookbook`. Its ingredient fills the worker contract and shows
every part: operations, renders inline and from `files/`, floor arguments and both checks.

```text
acme-agent/
  ingredient.yaml
  files/
    hooks.toml
```

```yaml
schema: 1
name: acme-agent
version: v1.2.0
description: acme-agent, a coding agent
contract: { name: worker, version: 1 }
checks:
  version: { run: [acme-agent, --version], range: ">=3.1 <3.2" }
  detect: [.acme/, ACME.md]
floor:
  bypass: [--skip-permissions, --mode=unrestricted]
operations:
  launch:
    command: [acme-agent]
    unattended: [--mode, unattended]
    unattended_deny:
      flag: --deny
      item: &rule "{{#prefix}}Run({{words}} *){{/prefix}}{{#native}}{{string}}{{/native}}{{#bare}}Run({{string}}){{/bare}}"
    sandbox_roots: [--writable, "{path}"]
    network: [--network, allow]
    model: [--model, "{model}"]
    effort: [--effort, "{effort}"]
    prompt: { stdin: true, args: [--print] }
  payload:
    event: event
    session: session.id
    tool: tool.name
    file: tool.input.path
    cwd: cwd
    prompt: prompt
    message: message
    edit_tools: [edit, write]
    read_tools: [read]
    events:
      session-start: SessionStart
      post-tool-use: AfterTool
      prompt-submit: PromptSubmit
      attention: WaitingForUser
      turn-end: TurnEnd
  instructions: { file: ACME.md, block: import, line: "@AGENTS.md" }
  skills: .acme/skills/the-cook
  native_rules:
    patterns: ['^[A-Z][A-Za-z]*(\(.*\))?$', '^mcp__']
    keys: { allow: permissions.allow, ask: permissions.ask, deny: permissions.deny }
    project: [.acme/settings.json]
    local: [.acme/settings.local.json]
    user: [~/.acme/settings.json]
    prefix: "Run({{words}} *)"
    notes: [permissions.mode]
renders:
  - file: .acme/settings.json
    keys:
      permissions.allow: { each: allow, item: *rule }
      permissions.ask: { each: ask, item: *rule }
      permissions.deny: { each: deny, item: *rule }
  - file: .acme/hooks.toml
    whole: true
    template: files/hooks.toml
  - file: .gitignore
    block: acme-agent
    template: |
      /.acme/cache/
```

`files/hooks.toml`, rendered on every render:

```toml
# rendered by the-cook from .cook/hooks.yaml; edit that file instead
{{#hooks}}
[[hook]]
event = "{{event}}"
match = "{{matcher}}"
run = "{{command}}"
{{/hooks}}
```

In a repository that pins `acme-agent` and declares a worker named `acme-agent` that uses it, `render` writes the policy
into the three `permissions` keys of `.acme/settings.json`, the hooks into `.acme/hooks.toml`, the import line into the
`import` block of `ACME.md`, the skill link and the `.gitignore` block. `the-cook attempt acme-agent` refuses
`--skip-permissions` wherever it appears among the worker's arguments, and `doctor` reports an `acme-agent` outside 3.1.

## Examples

### Read an ingredient before pinning it

```text
$ cd ~/src/acme-web
$ ./cook ingredient list --cookbook github:acme/cookbook@v1.2.0
ingredient  contract  detected  pinned
acme-agent  worker    yes       -
acme-jail   jail      no        -
acme-loop   -         no        -
acme-tasks  -         no        -
claude      worker    no        -
git         vcs       yes       v1.2.0
$ ./cook ingredient show acme-agent --cookbook github:acme/cookbook@v1.2.0
```

`acme-agent` matches the repository's `.acme/` directory but is not pinned, so its floor does not hold here yet.

### A program no station lists

The team adopts `acme-agent` as a second interactive worker:

```text
$ ./cook ingredient pin acme-agent --cookbook github:acme/cookbook@v1.2.0
.cook/config.yaml
+  - name: acme-agent
+    source: github:acme/cookbook
+    version: v1.2.0
+    sha256: 6d2f26296cf399e1f470582106e0def36fcb3979b54422ecde46767a4b996ea7
pin these ingredients? [y/N] y
pinned acme-agent v1.2.0 from github:acme/cookbook: sha256 verified; no worker names it yet
$ $EDITOR .cook/config.yaml      # under workers: add  acme-agent: { role: interactive }
$ ./cook render
$ git add -A && git commit -m "chore: add the acme-agent worker"
```

A worker ingredient renders only for the workers that name it, so the worker's native files appear in the render
after the profile declares it. The worker's `ingredient` defaults to its name.

### Move an ingredient to a newer tag

```sh
./cook ingredient pin git acme-agent --cookbook github:acme/cookbook@v1.3.0 --dry-run   # the pins to move
./cook ingredient pin git acme-agent --cookbook github:acme/cookbook@v1.3.0 --yes       # moves them, renders
git diff
```

The renders and floor of the new versions are in the same diff. Entries and templates are not: those come from a
station, applied again ([applying again](stations.md#applying-again)).

### Cache after cloning, and in CI

```sh
git clone https://github.com/acme/acme-web && cd acme-web
./cook fetch            # every pinned ingredient and station, and each recipe's origin, verified
./cook render --check
```

Until a teammate runs `./cook fetch`, their `render` exits 3 naming it and their worker hooks skip with one line;
neither downloads anything.

### Stop using a program

```sh
# delete the "name: acme-tasks" entry from ingredients: in .cook/config.yaml
./cook render        # removes the acme-tasks ingredient's renders and floor
git diff             # the lifecycle actions a station wrote for it stay, as the repository's own
```

## Guidelines

- Do read an ingredient with `the-cook ingredient show <name> --cookbook <source>@<version>` before pinning it: its
  operations, renders and floor run in every session and every attempt, and the review of the pin is the only thing
  that vouches for them.
- Do keep every program the repository uses pinned: an ingredient's floor holds only while it is pinned.
- Do move ingredient pins with `ingredient pin`, and apply a station again for its entries: applying never moves a pin
  the repository already holds.
- Do commit the pins together with the files `ingredient pin` rendered, so the diff explains itself.
- Do run `./cook fetch` in CI before `render --check` and `doctor`.
- Don't copy a floor rule into `.cook/policy.yaml` to keep it after removing its ingredient: an entry there is
  editable, which is what the floor exists to avoid. Keep the ingredient pinned.
- Don't change a pin's `sha256` by hand to match a download: the hash is what the review approved.
- Don't put a program's facts into the repository's own files to avoid an ingredient: a bypass argument written as a
  policy entry is removed by a single edit.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| `render` or `the-cook attempt` exits 3 naming `./cook fetch`, a hook prints one line naming it, or `doctor` reports an ingredient not cached | A pinned ingredient is not in this machine's cache, and only the commands in [Fetching](#fetching) download | Run `./cook fetch` |
| A command exits 3 naming `the-cook station apply` and `the-cook ingredient pin` | It needs the version-control contract or a worker contract, and no pinned ingredient fills it | Apply a station whose stages fill it, or pin an ingredient that fills it and name it in `vcs:` or a worker's `ingredient`; `./cook doctor` lists each unfilled contract and the commands it blocks |
| Exit 1: an archive's sha256 differs | The archive at the source is not the one the profile pins: a new upload under the same version, a different `source`, or tampering | Do not change the `sha256` to match. Ask the cookbook's publisher; pin a tag whose archives match its `checksums.txt` |
| Exit 1: a worker's `jail.ingredient` does not fill the jail contract, or the jail ingredient declares both or neither of `env.pass` and `env.inherited`, or lacks `share.read` or `share.write` | The pin names another kind of ingredient, or the jail ingredient is incomplete | Name a pinned ingredient that fills the jail contract; report an incomplete one to its cookbook's publisher |
| Exit 1: a worker with a jail, and the version-control ingredient has no `metadata` | The binary could not share the version-control metadata read-only in the jail | Pin a version-control ingredient that fills `metadata`, or remove the worker's `jail` |
| Exit 1: two `ingredients:` entries with the same name | A name is unique within `ingredients:` | Delete one; `ingredient pin` moves an existing pin instead of adding a second |
| Exit 1: `vcs:` or a worker's `ingredient` names an ingredient not pinned, or one that fills another contract | The pin was deleted, never written, or names the wrong ingredient | Pin it, or point `vcs:` or the worker at a pinned ingredient that fills the contract |
| Exit 1: an ingredient's schema or contract version is newer than the running release supports | The cookbook tag was written for a newer `the-cook` | Move the release pin with `./cook wrapper --version <tag>` to the release the message names, or pin an older tag of the ingredient |
| Exit 3 naming a program | The version-control ingredient's program, or a worker's, is not installed on this machine | Install it |
| `ingredient pin` exits 1: the tag does not publish the name | The name is wrong, the name is a station's or a recipe's, or the cookbook added the ingredient in a later tag | `./cook ingredient list --cookbook <source>@<version>` prints the tag's ingredients |
| `kept ingredients.<name>: pinned at <version>` after `station apply` | The repository already pins the ingredient, and applying never moves a pin | Nothing, if the pinned version is right. To take the station's, `./cook ingredient pin <name> --cookbook <source>@<version>` |
| `the-cook attempt` exits 3: `unattended.deny` cannot reach the worker | Its ingredient passes no deny arguments at launch and renders no permissions | Use the worker interactively only, or use an ingredient that fills `launch.unattended_deny` or renders permissions |
| `fetch` exits 1 for a station whose source is gone | `fetch` also downloads the stations `stations:` records | Nothing rendered depends on a station: delete its entry from `stations:` |
| `fetch` exits 1 for a recipe's origin whose source is gone | `fetch` also downloads the version each added recipe came from | Nothing runs from it: only `recipe update` needs it. When the cookbook is gone for good, delete the recipe's `origin`, and the recipe is the repository's own |
