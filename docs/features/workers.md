# Workers

## Objective

**Problem.** Teams often have more than one program change a repository for them: coding agents above all, and also
bots and codemods. Each coding agent reads its permissions, hooks and instructions from its own files, in its own
syntax, with its own events. Kept by hand, one set per program, these files drift apart, so one repository lets one
coding agent run a command it forbids another, and a coding agent left to work unattended can be started in a mode that
skips its permission checks. A maintainer who adds another program has to write the whole setup again.

**Goal.** Every program that changes a repository follows the same permissions, hooks and instructions from one set of
declarations, as far as the program has them, and none runs unattended with its permission checks off. Supporting
another program changes no repository's declarations.

## Overview

A **worker** is any program that changes the repository for someone: a coding agent, a bot, a codemod. Workers act in
the Implement stage of the lifecycle, in one of two roles: interactively, in
a session with a person, or unattended, as an attempt, one run on one task under the guards of the
attempt contract. A repository declares its workers in the profile's [`workers`](profile.md#workers), each with its
role and the ingredient that describes its program. A [recipe](recipes.md) names the roles it requires and, in each
`attempt` step, a declared worker with role `unattended` to start, so a recipe's steps reach a program only through
the workers the repository declares.

A worker's ingredient fills the [worker contract](#the-worker-contract): how the binary starts the program and, when
the program has them, where it reads its configuration, how its permissions and hooks are expressed, what its hook
payloads look like and how it reaches `AGENTS.md`. Every item except the launch is optional, so a bot with no
configuration files of its own is a worker in the same way as a coding agent. The binary contains code and data for no
program. Every worker ingredient comes from a cookbook and is pinned by version and sha256 in the profile's
`ingredients:` ([ingredients](ingredients.md)).

Commands are named `the-cook <command>` on this page. Run them through the repository's wrapper,
`./cook <command>`, from the root of a checkout; with a global install, `the-cook <command>` is equivalent
([wrapper](wrapper.md)).

## Declaring workers

```yaml
# .cook/config.yaml
ingredients:
  - name: git
    source: github:acme/cookbook
    version: v1.2.0
    sha256: 9f258251faccf596a0780da3215a96c9d69f04f244a05b71f179008e4e9b2a36
  - name: claude
    source: github:acme/cookbook
    version: v1.2.0
    sha256: a90f021dd04c28e28172aa28048fccbe2b818e04dc2a3e179e21c792b6e3f65b
  - name: codex
    source: github:acme/cookbook
    version: v1.2.0
    sha256: 9e0c6a176f7724248e9c29096af6ed0b6b0d267274522a6245becfd7f034146d
vcs: git
workers:
  claude: { role: interactive }
  codex: { role: unattended }
```

`the-cook ingredient pin claude codex --cookbook github:acme/cookbook@v1.2.0` writes the `ingredients:` entries above
after showing them as a diff. A station that declares workers in its [stages](stations.md#stages), such as an
`agentic-coding` station, writes its `workers` entries and pins their ingredients in the one diff
`the-cook station apply` shows; from then on the entries are the repository's own ([stations](stations.md)).

| Field | Meaning |
| --- | --- |
| `ingredient` | the ingredient that describes the program, by default the ingredient named like the worker. It must be pinned in `ingredients:` and fill the worker contract |
| `role` | `interactive` or `unattended` ([roles](#roles)) |
| `writable_roots` | for a worker whose ingredient opens sandbox roots or whose attempts run in a jail, the directories an attempt may write besides its checkout, relative to the primary checkout's root; for a worker with a jail, none may contain the version-control metadata or the per-user cache |
| `network` | whether the worker's attempts may use the network, default `false`; `true` passes the ingredient's network arguments at every unattended launch |
| `jail` | the [jail](#jails) the worker's attempts run in: `ingredient`, a pinned ingredient that fills the jail contract, and `args`, the jail's options this repository chooses; absent: the attempts run unconfined by a jail |
| `model`, `effort` | passed at every launch the binary makes, as the ingredient's launch says; a worker whose ingredient passes no model or effort ignores them, and `doctor` notes it |

The repository decides `network`, and the program defines the arguments that carry it: the worker's ingredient maps
`network: true` to its `launch.network` arguments, as it maps a model or an effort. A worker declared
with `network: true` whose ingredient has no `launch.network` is a validation error, exit 1, as `writable_roots` is
without `launch.sandbox_roots`. With `network: false` the binary passes nothing about the network, and the program does
what it does by default, which the ingredient's page in its cookbook explains. For a worker with a jail, `network: true`
also needs the jail's `network` arguments, which open the network around the worker
([the jail contract](ingredients.md#the-jail-contract)).

A worker whose ingredient is not pinned in `ingredients:`, or whose ingredient fills no worker contract, is a
validation error, exit 1. Only declared workers get native files. Removing a worker from `workers` and running
`the-cook render` removes what was rendered for it. The [profile](profile.md#workers) reference gives every field's
type and default.

## Roles

A role decides which parts of the [policy](policy.md) a worker's native files carry. `shared` holds the `allow`, `ask`
and `deny` entries for every session; `unattended.deny` holds what attempts alone must never run, such as a push.
`unattended.deny` always holds `[the-cook, lifecycle]`, which the binary renders itself for its own command, once for
each spelling, such as `the-cook lifecycle` and `./cook lifecycle` ([policy](policy.md#entries-for-the-cook-commands)).

| Role | Meaning | Native files carry | Under `the-cook attempt` |
| --- | --- | --- | --- |
| `interactive` | a session with a person: people work with this worker in the repository | the shared policy (`shared.*`) and the floor rules of every ingredient in use | **Allowed** when the ingredient passes `unattended.deny` as launch arguments, which add it at launch. **Refused** when the ingredient passes it only through rendered rules, or in neither way |
| `unattended` | an attempt: the worker runs here only under `the-cook attempt`, an `attempt` action or a recipe's `attempt` step | the shared policy, the floor rules of every ingredient in use, and `unattended.deny`, including `[the-cook, lifecycle]` in each spelling `the-cook` renders | **Allowed** when the ingredient passes `unattended.deny` at launch or renders permissions. Otherwise **refused**, exit 3 |

How an ingredient passes `unattended.deny` is part of its [launch](#the-worker-contract). A worker whose ingredient
passes it as launch arguments can be declared `interactive` and still run attempts. A worker whose ingredient passes it
only through rendered rules is one or the other: a repository where people use it in sessions and also want it for
attempts chooses one. A worker whose ingredient neither passes deny arguments at launch nor renders permissions has no
way to receive `unattended.deny`, so it cannot run attempts: `the-cook attempt`, `attempt` actions and `attempt` steps
refuse it with exit 3, naming the gap, because the binary's own deny on lifecycle actions and the repository's
unattended denies hold in every attempt. Such a worker may still be
declared `interactive`, and the `policy` block marks the entries it does not receive as not enforced in it.

A worker whose unattended denies live in rendered rules reads them from files inside its own checkout, which it can
write. The tamper check closes that gap. Before every launch it restores the
[renders](ingredients.md#renders) of every ingredient in use (rendered rule files, owned keys of native settings files,
`the-cook hook` entries), the wrapper `cook` and its pin `.cook/wrapper.yaml` from the base commit, the head commit of
the checkout when `the-cook attempt` started. After the worker exits it compares them again, so a worker cannot change
the rules it runs under or the binary its hooks and its next attempt run. The comparison after the worker exits also
covers every file under `.cook/` and every file and skill a `context` entry names. An attempt whose worker changed any
of them fails, whatever its receipt (the record of a passing `the-cook verify`) says, and a
failed attempt returns the checkout to the base commit, keeping its changes as `last-attempt.patch` in the state
directory.

## What a worker ingredient renders

A worker ingredient's [renders](ingredients.md#renders) turn the repository's declarations into its program's native
files. The ingredient's page in its cookbook names the exact files and keys. Each output below exists only when the
ingredient declares the [contract item](#the-worker-contract) behind it, so a worker whose ingredient declares none of
them, such as a codemod, gets no native files:

| Output | What the worker ingredient decides |
| --- | --- |
| Instructions | whether its program reads `AGENTS.md` directly, or through an `import` block in its own instruction file |
| Permissions | the native file and keys that hold `allow`, `ask` and `deny`, and how each [entry form](policy.md#entry-forms) renders: prefix lists for every worker whose ingredient renders permissions; native rule strings only for a worker whose ingredient declares a `native_rules` pattern they match; bare strings only for a worker whose program matches whole shell commands by glob. An entry a worker does not render is marked in the `policy` block as not enforced in it |
| Hooks | the native events that carry session start, post-edit, prompt submission and the [notification](hooks.md#worker-notifications) events, and the key that enables project hooks where its program needs one |
| [Standing context](context.md) | which native events deliver `always`, `when.paths` and `when.prompt` entries, and which tools count as reads and edits for `when.paths` |
| Shipped skill | the location of the link to `.agents/skills/the-cook` |
| Never written | the program's local override files, read only by `scaffold --import local` |

A neutral event an ingredient maps to no native event gets nothing rendered for that worker, and `the-cook doctor`
notes the gap.

Every worker ingredient that maps hook payloads renders the same hook command, which
finds the checkout's root through the version-control ingredient's `root` operation. It runs the pinned binary of the
checkout the worker works in, from the cache, and never downloads. When the binary is not cached, it prints one line
naming `./cook` and exits 0 ([hooks](hooks.md#the-missing-binary-guard)).

See [render](render.md#merge-rendering-native-files) for the merge rules: every key and entry the ingredient does not
own is preserved byte for byte, because an ingredient's renders own only the keys and entries they name.

## Attempt launch

`the-cook attempt <worker> [--commit] [--withhold <text>] [-- <worker args>]` starts the worker with the launch of its
ingredient:

| | What the attempt does |
| --- | --- |
| Permission mode | the ingredient's unattended arguments, which start the program with its permission checks on; without them, the launch command and the caller's arguments only |
| Unattended denies | `unattended.deny` as the ingredient passes it: as launch arguments, or in the rendered rules, which then require `role: unattended`. A worker that can receive it in neither way is refused, exit 3, naming the gap |
| Tamper check restores | the renders of every ingredient in use; `cook`; `.cook/wrapper.yaml` |
| Environment | `COOK_ATTEMPT=1`: `the-cook` refuses lifecycle events, and notification hooks do nothing |
| Standing context | entries for the `unattended` role, read from the base commit; when the prompt arrives on stdin, the `always` entries go in the prompt, before the task text, and `when` entries come through hooks ([context](context.md#delivery-in-the-prompt)) |
| Writable outside the checkout | for a worker with a sandbox or a jail, only the directories the profile lists in `workers.<name>.writable_roots` ([profile](profile.md#workers)), never the clone's state directory, which holds every managed workspace; otherwise what its permissions permit |
| Network | with `workers.<name>.network: true`, the ingredient's `launch.network` arguments; with `false`, nothing, and the program's default holds |
| Jail | with `workers.<name>.jail`, the worker starts inside the jail, in the checkout, with the checkout and `writable_roots` writable, the version-control metadata and the per-user cache read-only, the `COOK_*` marks and `COOK_HOME` passed, and the replay run in the same jail ([jails](#jails)) |
| Refused arguments, among those `the-cook attempt` receives | the ingredient's [floor](ingredients.md#the-floor) bypass arguments |
| Model and effort | `workers.<name>.model` and `effort`, or an `attempt` action's `attempt.model` and `attempt.effort`, or a recipe `attempt` step's `model` and `effort`, which take precedence, passed as the ingredient's launch says |
| Prompt | read from stdin or from the worker arguments, whichever the caller uses; the prompt of an `attempt` action or of a recipe's `attempt` step passed as the ingredient's launch says |

`the-cook attempt` passes the worker the arguments it does not recognise itself, after those the launch pins, so a loop
runner's model and effort flags reach the worker as the loop runner wrote them. It reads no loop runner's configuration:
a bypass flag a loop runner adds to the arguments is refused when the attempt starts, at the first task, with exit 1
naming the flag. Before it starts the worker, `the-cook attempt` resolves the version-control ingredient, the worker's
ingredient and its jail's from the verified cache and uses them for the whole attempt; a pinned ingredient that is not
cached fails the attempt with exit 3.

## Jails

A jail is a program that starts another program confined to the directories it shares, the variables it passes and the
network it opens, running the host's own programs at their own paths. A worker declared with `jail` runs every attempt
inside it, whether `the-cook attempt`, an `attempt` action or a recipe's `attempt` step starts it; the jail's facts, the
arguments that carry each share, variable and the network, are its ingredient's, and `jail.args` holds the options this
repository chooses, such as how the worker program's login reaches the jail:

```yaml
workers:
  acme-agent:
    role: unattended
    jail: { ingredient: acme-jail, args: [--keep-env, ACME_AGENT_TOKEN] }
```

| | Inside the jail |
| --- | --- |
| Writable | the attempt's checkout, which is the jail's working directory, and `workers.<name>.writable_roots`; never the clone's state directory, which holds every managed workspace |
| Read-only | the version-control metadata the version-control ingredient's `metadata` operation reports, and the per-user cache (`$COOK_HOME`) the hooks and `./cook verify` load the pinned release and the ingredients from |
| Variables | the `COOK_*` marks the binary sets for the worker, such as `COOK_ATTEMPT`, and `COOK_HOME`; any other only through the jail's own configuration or `jail.args` |
| Network | opened with `network: true`, through the jail's `network` arguments; otherwise the jail's default |
| Runs there | the worker, and from then until the attempt ends every command the binary starts on the checkout: the replay's lanes and validators; in a recipe run, the later `run` steps, Verify and the replay of Complete too |
| Runs outside | the profile's `bootstrap`, the version-control operations, the tamper check, the commit and lifecycle actions |

The binary never starts an interactive session, so a jail applies only to attempts. A person who wants a session
confined starts it inside the jail, and the rendered files apply there as without one. Nothing else of the person's
home reaches the jail, and a lane that needs a variable or a service the jail withholds passes only outside an attempt
until `jail.args` lets it through. The jail contract, the composed launch and an example are on the
[ingredients](ingredients.md#the-jail-contract) page.

## Trust

Some workers, coding agents among them, load project configuration only for folders the person has trusted, and their
global or managed settings may restrict project settings. `the-cook` never changes trust, global settings or
credentials. Whether a folder inside a trusted one is trusted too is the program's behaviour, which its ingredient's
page in its cookbook describes. Managed workspaces are nested in the primary checkout by default, under
[`workspaces.dir`](profile.md#state-paths), so a program that extends trust to nested folders trusts them with the
primary checkout.

## The worker contract

A worker ingredient is the complete description of one program, in data: the binary interprets it and contains nothing
about any program. Supporting another program means writing an ingredient that declares
`contract: { name: worker, version: 1 }` and fills the [worker contract](ingredients.md#the-worker-contract). Only the
launch command is required. Every other item is optional: an ingredient declares what its program has; for an item it
omits, the binary renders nothing and assumes nothing, and the `policy` block or `the-cook doctor` says what the worker
lacks.

| Item | Keys | The ingredient declares | When the ingredient omits it |
| --- | --- | --- | --- |
| Launch | `launch.command` | the program the binary starts | required: the ingredient fails validation, exit 1 |
| Unattended arguments | `launch.unattended` | the arguments that start the program unattended with its permission checks on | the program starts with `launch.command` and its caller's arguments only, which suits a program with no permission checks |
| Unattended denies | `launch.unattended_deny` | how `unattended.deny` reaches the program as arguments at launch | `unattended.deny` reaches the program only through the rendered rules, which then require `role: unattended`. With no rendered permissions either, it cannot reach the program, and the worker cannot run attempts: `the-cook attempt`, `attempt` actions and `attempt` steps refuse it with exit 3. The worker may still be `interactive` |
| Sandbox roots | `launch.sandbox_roots` | the arguments that open a directory beyond the checkout for writing: each entry of `workers.<name>.writable_roots` | the binary opens no directory for the program, and `writable_roots` on the worker is a validation error unless the worker has a jail, whose write shares carry them |
| Network | `launch.network` | the arguments that let the program use the network, passed at every unattended launch of a worker declared with `network: true` | `network: true` on the worker is a validation error; with `network: false` the ingredient states nothing about the network, and its page says what the program does by default |
| Model and effort | `launch.model`, `launch.effort` | how a model and an effort, from `workers.<name>`, an `attempt` action or a recipe's `attempt` step, are passed | a model or an effort set for the worker is ignored, and `doctor` notes it |
| Prompt | `launch.prompt` | how the binary passes the prompt of an `attempt` action or of a recipe's `attempt` step: on stdin, as arguments, or both | an `attempt` action or step that names the worker fails validation |
| Floor bypass arguments | `floor.bypass` | the arguments that would turn the program's permission checks off, refused at every launch ([the floor](ingredients.md#the-floor)) | nothing is refused for that worker |
| Hook payload mapping | `payload` | the JSON paths to the neutral event, session, tool, files, working directory, prompt text and notification message. Which tools are edits or reads. Which native events carry `session-start`, `post-tool-use`, `prompt-submit`, `attention` and `turn-end` | no hook entry is rendered for the worker: no post-edit feedback, hook-delivered standing context or notifications. `doctor` notes the gap |
| Instructions file | `instructions` | the import line, in a block of the program's own instruction file, through which it reaches `AGENTS.md` | nothing is rendered: the program reads `AGENTS.md` directly, or reads no instruction file and so receives neither the `AGENTS.md` blocks nor static context |
| Skill location | `skills` | where the link to the shipped skill goes | no link is rendered |
| Native rules | `native_rules.patterns` | the patterns the program's own rule strings match, as regular expressions in RE2 syntax (that of Go's `regexp` package), each matched against the whole string: a policy string that matches one is a native rule string for this worker ([entry forms](policy.md#entry-forms)) | no policy string is a native rule string for this worker |
| Native rule import | `native_rules`, its other keys | which files and keys hold the program's permission rules in each [import scope](scaffold.md#import-scopes), the native rule shape that reads back as a command prefix, and the keys that change permission behaviour without being rules, which adoption reports as notes; [`scaffold --adopt`](scaffold.md#adopt) imports from them | adoption imports nothing from this program |
| Renders | `renders` | the exact files, keys and entries it owns, rendered from the policy, the floor rules of every ingredient in use and the hooks file. Everything else is preserved. Local override files are never written, and read only by `scaffold --import local` | the worker gets no native files. Every policy entry it does not receive at launch is marked in the `policy` block as not enforced in it. Without `launch.unattended_deny` either, it cannot run attempts |

Besides the contract, the ingredient's [checks](ingredients.md#checks) name the program versions it supports, which
`the-cook doctor` checks.

A worker ingredient declares the contract version it fills and the ingredient schema it is written in; the binary
refuses an ingredient whose schema is newer than it supports ([ingredients](ingredients.md#the-ingredient-file)).
`the-cook`'s own tests prove the core's side of every invariant with stand-in ingredients that describe no real
program: bypass refused, floor rules rendered, unattended denies applied, tampered renders (`cook` and
`.cook/wrapper.yaml` included) failing the attempt, an ingredient with only a launch running an attempt under the same
contract, and a hook exiting 0 without downloading when the pinned binary is not cached. The cookbook that publishes a
worker ingredient tests it against the program versions its checks name. Pinning the ingredient trusts that cookbook:
a reviewer reads it with `the-cook ingredient show <name>` before approving the diff that pins it.

## Examples

The first examples use worker ingredients named `claude` and `codex` from `github:acme/cookbook`, pinned as in
[Declaring workers](#declaring-workers).

A repository where the team uses one coding agent interactively and runs the other for attempts:

```yaml
workers: { claude: { role: interactive }, codex: { role: unattended, writable_roots: [.tasks] } }
```

```sh
echo "Fix the invoice rounding described in task 7f2." | ./cook attempt codex --commit
```

A repository where people use both coding agents interactively, and attempts use the worker whose ingredient passes
`unattended.deny` at launch:

```yaml
workers: { claude: { role: interactive }, codex: { role: interactive } }
```

A repository where acme's own review agent runs attempts through an ingredient from acme's own cookbook, beside one
interactive coding agent:

```yaml
ingredients:
  - name: git
    source: github:acme/cookbook
    version: v1.2.0
    sha256: 9f258251faccf596a0780da3215a96c9d69f04f244a05b71f179008e4e9b2a36
  - name: claude
    source: github:acme/cookbook
    version: v1.2.0
    sha256: a90f021dd04c28e28172aa28048fccbe2b818e04dc2a3e179e21c792b6e3f65b
  - name: acme-review
    source: https://cookbook.acme.example
    version: v0.3.0
    sha256: 2631021b8b679d990f51f13655f7286c4111a2cb963a801764f3c9d9c887591b
vcs: git
workers:
  claude: { role: interactive }
  reviewer: { ingredient: acme-review, role: unattended, effort: high }
```

A repository where acme's dependency-upgrade bot runs as an unattended worker. Its ingredient declares a launch, the
deny argument the bot accepts, and nothing else a coding agent has:

```yaml
# ingredient.yaml of acme-upgrade, in https://cookbook.acme.example
schema: 1
name: acme-upgrade
contract: { name: worker, version: 1 }
operations:
  launch:
    command: [acme-upgrade, apply]
    unattended_deny:
      flag: --deny
      item: "{{#prefix}}{{words}}{{/prefix}}{{#native}}{{string}}{{/native}}{{#bare}}{{string}}{{/bare}}"
```

```yaml
# .cook/config.yaml, with acme-upgrade pinned in ingredients:
workers:
  upgrader: { ingredient: acme-upgrade, role: unattended }
```

```sh
./cook attempt upgrader --commit -- http-client@5
```

The bot gets no native files, hooks or standing context. Every attempt passes it `unattended.deny`, `[the-cook,
lifecycle]` in each spelling included, as `--deny` arguments, and the rest of the attempt contract holds as for any
worker: the base read before the launch, the tamper check, the replay of the receipt and completion withheld until the
replay passes, so the commit exists only if `the-cook verify` passed on the upgraded code. Without `unattended_deny`,
the ingredient would describe a worker that cannot run attempts, and `./cook attempt upgrader` would exit 3 naming the
gap.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| A worker ignores the project rules | The folder is not trusted by the program, or the worker is not declared in `workers` | Trust the folder; declare the worker and render |
| A worker does not read `AGENTS.md` | Its instruction file lacks the import its ingredient renders, or its ingredient declares no instructions item | `the-cook render` restores the `import` block; for an ingredient without the item, pin an ingredient that fills `instructions`, or have the worker read `AGENTS.md` directly |
| `the-cook attempt <worker>` refused, naming rendered rules | The worker has `role: interactive`, and its ingredient passes `unattended.deny` only through rendered rules | Use `role: unattended`, or a worker whose ingredient passes `unattended.deny` at launch |
| `the-cook attempt` exits 1 naming a bypass flag | The loop runner's configuration adds the flag to the worker's arguments | Remove it from the loop runner's configuration |
| A sandboxed or jailed worker cannot write a directory outside its checkout | Only the checkout and `workers.<name>.writable_roots` are writable | Add the directory to `writable_roots`, relative to the primary checkout's root |
| Exit 1: a `writable_roots` entry of a worker with a jail contains the version-control metadata or the per-user cache | The binary never shares a writable directory over one it shares read-only, because a jail may drop one of the two without failing | Name a narrower directory in `writable_roots` |
| Exit 1: `network: true` on a worker | Its ingredient has no `launch.network` arguments, so the binary cannot pass the choice to the program | Remove the field, or pin an ingredient that fills `launch.network`; its page in its cookbook says what the program does without it |
| Exit 1: `network: true` on a worker with a jail | The jail's ingredient has no `network` arguments, so the network cannot be opened around the worker | Remove the field, or use a jail whose ingredient fills `network`; its page says what the jail does by default |
| Exit 1: a worker with a jail, naming `metadata` | The version-control ingredient has no `metadata` operation, so the binary cannot share the version-control metadata read-only inside the jail | Pin a version-control ingredient that fills `metadata`, or remove the worker's `jail` |
| Exit 1 naming a jail argument in `jail.args` | The argument is a floor bypass argument of the jail: it would turn the confinement off | Remove it from `jail.args` |
| A lane passes outside an attempt and fails inside one of a jailed worker | The jail withholds a variable or a service the lane needs, such as a database's address or socket | Pass it through `jail.args`, as the jail ingredient's page explains, or give the lane what it needs inside the checkout |
| An attempt of a jailed worker exits 3 naming the jail's program or `./cook fetch` | The jail's program is not installed, or its ingredient is not cached | Install the program, or run `./cook fetch` |
| An attempt's worker cannot reach the network | The worker is declared with `network: false`, the default, and the program's default keeps it off the network | Set `workers.<name>.network: true` for a worker whose ingredient fills `launch.network` |
| `the-cook attempt <worker>` exits 3 naming `unattended.deny` | Its ingredient passes no deny arguments at launch and renders no permissions, so the denies cannot reach the program | Declare the worker `interactive`, or pin an ingredient that passes the denies at launch or renders permissions |
| `doctor` reports an unsupported program version | The program's version is outside the range its ingredient's check supports | Pin a cookbook version whose ingredient supports it, `./cook ingredient pin <name> --cookbook <source>@<version>`, or use a supported program version |
| Exit 1: a worker's `ingredient` is not in `ingredients:` | The ingredient was never pinned, or was deleted from `ingredients:` | `./cook ingredient pin <name> --cookbook <source>@<version>` |
| `the-cook attempt` or a hook names `./cook fetch` | The worker's ingredient is pinned and not in this machine's cache | Run `./cook fetch` |
