# Agents

## Objective

**Problem.** Teams often use more than one coding agent, and each agent reads its permissions, hooks and instructions
from its own files, in its own syntax, with its own events. Kept by hand, one set per agent, these files drift apart, so
one repository lets one agent run a command it forbids another, and an agent left to work unattended can be started in a
mode that skips its permission checks. A maintainer who adds another agent has to write the whole setup again.

**Goal.** Every agent a repository uses follows the same permissions, hooks and instructions from one set of
declarations, and none of them runs unattended with its permission checks off. Supporting another agent changes no
repository's declarations.

## Overview

Each agent is served by a [harness recipe](recipes.md#the-harness-contract): a recipe that says where the agent reads
its configuration, how its permissions and hooks are expressed, what its hook payloads look like and how to start it
safely as a worker, the agent [`the-cook worker`](worker.md#the-worker-contract) runs headless for one task, the command
a loop runner names as its agent command. The binary contains code and data for no agent. Every harness recipe comes
from a cookbook and is declared, pinned by version and sha256, in the profile's `recipes:` ([recipes](recipes.md)). A
repository declares which agents it uses, in which role and with which recipe, in the [profile](profile.md#agents).

Commands are named `the-cook <command>` on this page. Run them through the repository's wrapper,
`./cook <command>`, from the root of a checkout; with a global install, `the-cook <command>` is equivalent
([wrapper](wrapper.md)).

## Declaring agents

```yaml
# .cook/config.yaml
recipes:
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
agents:
  claude: { role: interactive }
  codex: { role: worker }
```

`the-cook recipe pin claude codex --cookbook github:acme/cookbook@v1.2.0` writes the `recipes:` entries above after
showing them as a diff. Each agent's harness recipe is the one its `recipe` field names, by default the recipe named
like the agent, and it must be declared in `recipes:`: an agent whose recipe is not declared is a validation error,
exit 1. A harness recipe's [entries](recipes.md#entries) may declare its agent, with `role: interactive`, when the
profile does not. Only declared agents get native files. Removing an agent from `agents` and running `the-cook render`
removes what was rendered for it.

## Roles

A role decides which parts of the [policy](policy.md) an agent's native files carry. `shared` holds the `allow`, `ask`
and `deny` entries for every session; `worker.deny` holds what workers alone must never run, such as a push.
`worker.deny` always holds `[the-cook, lifecycle]`, which the binary renders itself for its own command, once for each
spelling, such as `the-cook lifecycle` and `./cook lifecycle` ([policy](policy.md#entries-for-the-cook-commands)).

| Role | Meaning | Native files carry | Under `the-cook worker` |
| --- | --- | --- | --- |
| `interactive` | people use this agent in the repository | the shared policy (`shared.*`) and the floor rules of every recipe in use | allowed when the harness recipe passes `worker.deny` as launch arguments, which add it, `[the-cook, lifecycle]` in each spelling included, at launch; refused when the recipe passes `worker.deny` only through rendered rules |
| `worker` | the agent runs here only unattended, under `the-cook worker` or an `agent` action | the shared policy, the floor rules of every recipe in use, and `worker.deny`, including `[the-cook, lifecycle]` in each spelling `the-cook` renders | allowed |

Which of the two ways a harness recipe passes `worker.deny` is part of its
[launch](recipes.md#the-harness-contract). An agent whose recipe passes it as launch arguments can be both interactive
and a worker. An agent whose recipe passes it only through rendered rules is one or the other: a repository where
people use it interactively and also want it as a worker chooses one.

An agent whose worker denies live in rendered rules reads them from files inside its own task checkout, which the agent
can write. The worker's [tamper check](worker.md#the-worker-contract) closes that gap. Before every launch it restores
the [renders](recipes.md#renders) of every recipe in use (rendered rule files, owned keys of native settings files,
`the-cook hook` entries), the wrapper `cook` and its pin `.cook/wrapper.yaml` from the base commit, the head commit of
the checkout when `the-cook worker` started, and after the agent exits it compares them again, so an agent cannot change
the rules it runs under or the binary its hooks and its next attempt run. After the agent exits, the comparison also
covers every file under `.cook/` and every file and skill a `context` entry names. An attempt whose agent changed any
of them fails, whatever its [receipt](verify.md#the-receipt) (the record of a passing `the-cook verify`) says, and a
failed attempt returns the checkout to the base commit, keeping its changes as `last-attempt.patch` in the state
directory.

## What a harness recipe renders

A harness recipe's [renders](recipes.md#renders) turn the repository's declarations into its agent's native files. The
recipe's page in its cookbook names the exact files and keys. Every harness recipe renders the same kinds of output:

| Output | What the harness recipe decides |
| --- | --- |
| Instructions | whether its agent reads `AGENTS.md` directly, or through an `import` block in its own instruction file |
| Permissions | the native file and keys that hold `allow`, `ask` and `deny`, and how each [entry form](policy.md#entry-forms) renders: prefix lists in every agent; native rule strings and bare strings only in an agent whose syntax expresses them, otherwise skipped and marked in the `policy` block as not enforced in that agent |
| Hooks | the native events that carry session start, post-edit, prompt submission and the [notification](hooks.md#agent-notifications) events, and the key that enables project hooks where its agent needs one |
| [Standing context](context.md) | which native events deliver `always`, `when.paths` and `when.prompt` entries, and which tools count as reads and edits for `when.paths` |
| Shipped skill | the location of the link to `.agents/skills/the-cook` |
| Never written | the agent's local override files, read only by `scaffold --import local` |

A neutral event a harness recipe maps to no native event gets nothing rendered for that agent, and `the-cook doctor`
notes the gap.

Every harness recipe renders the same [hook command](render.md#the-hook-command), which finds the checkout's root
through the version-control recipe's `root` operation. It runs the pinned binary of the checkout the agent works in,
from the cache, and never downloads. When the binary is not cached, it prints one line naming `./cook` and exits 0
([hooks](hooks.md#the-missing-binary-guard)).

See [render](render.md#merge-rendering-native-files) for the merge rules: every key and entry the recipe does not own
is preserved byte for byte, because a recipe's renders own only the keys and entries they name.

## Worker launch

`the-cook worker <agent> [--commit] [--withhold <text>] [-- <agent args>]` starts the agent with the launch operation
of its harness recipe:

| | What the worker does |
| --- | --- |
| Permission mode | the recipe's unattended arguments, which start the agent with its permission checks on |
| Worker denies | `worker.deny` as the recipe passes it: as launch arguments, or in the rendered rules, which then require `role: worker` |
| Tamper check restores | the renders of every recipe in use; `cook`; `.cook/wrapper.yaml` |
| Environment | `COOK_WORKER=1`: `the-cook` refuses lifecycle events, and notification hooks do nothing |
| Standing context | entries for `role: worker`, read from the base commit |
| Writable outside the task checkout | for an agent with a sandbox, only what the recipe's sandbox-root arguments name: the clone's state directory (`state.dir`, default `.work/`) and the directories the profile lists in `agents.<name>.writable_roots` ([profile](profile.md#agents)); otherwise what its permissions permit |
| Refused arguments, among those `the-cook worker` receives | the recipe's [floor](recipes.md#the-floor) bypass arguments |
| Model and effort of an `agent` action | `agent.model` and `agent.effort`, passed as the recipe's launch operation says |

`the-cook worker` passes the agent the arguments it does not recognise itself, so a loop runner's model and effort flags
reach the agent as the loop runner wrote them. It reads no loop runner's configuration: a bypass flag a loop runner adds
to the arguments is refused when the worker starts, at the first task, with exit 1 naming the flag. Before it starts the
agent, the worker resolves the version-control recipe and the agent's harness recipe from the verified cache and uses
them for the whole attempt; a declared recipe that is not cached fails the attempt with exit 3. The full worker contract
is on the [worker](worker.md#the-worker-contract) page.

## Trust

An agent may load project configuration only for folders the person has trusted, and its global or managed settings
may restrict project settings. `the-cook` never changes trust, global settings or credentials. Whether a folder inside
a trusted one is trusted too is the agent's behaviour, which its harness recipe's page in its cookbook describes.
Managed workspaces are nested in the primary checkout by default, under [`workspaces.dir`](profile.md#state-paths), so
an agent that extends trust to nested folders trusts them with the primary checkout.

## The harness contract

A harness recipe is the complete description of one agent, in data: the binary interprets it and contains nothing
about any agent. Supporting another agent means writing a harness recipe that fills every item of the
[harness contract](recipes.md#the-harness-contract); a partial recipe fails validation, because each item backs an
invariant.

| Item | The harness recipe declares |
| --- | --- |
| Launch | the program; the arguments that start it unattended with its checks on; how `worker.deny` reaches it, as launch arguments or as rendered rules, which then require `role: worker`; the sandbox-root arguments for the state directory and `agents.<name>.writable_roots`; how an `agent` action's prompt, model and effort are passed |
| Floor | the bypass arguments `the-cook worker` and `agent` actions refuse ([the floor](recipes.md#the-floor)) |
| Hook payload mapping | the JSON paths to the neutral event, the tool, the edited and read files, the working directory, the person's prompt text and the agent's notification message; which tools are edits or reads; which native events carry `attention`, `turn_end` and each standing-context trigger, and which it lacks |
| Instructions | how the agent reaches `AGENTS.md`: directly, or through an import line in its own instruction file |
| Skill location | where the shipped skill link goes |
| Native rule import | which native keys hold permission rules, which `scaffold --adopt` copies into the policy |
| Renders | the exact files, keys and entries it owns, rendered from the policy, the floor rules of every recipe in use and the hooks file; everything else is preserved; local override files are never written, and read only by `scaffold --import local` |
| Checks | the agent versions it supports, which `the-cook doctor` checks |

A harness recipe declares the contract version it fills and the recipe schema it is written in; the binary refuses a
recipe whose schema is newer than it supports ([recipes](recipes.md#the-recipe-file)). `the-cook`'s own tests prove
the core's side of every invariant with stand-in recipes that describe no real agent: bypass refused, floor rules
rendered, worker denies applied, tampered renders (`cook` and `.cook/wrapper.yaml` included) failing the attempt, and
a hook exiting 0 without downloading when the pinned binary is not cached. The cookbook that publishes a harness recipe
tests it against the agent versions its checks name. Pinning the recipe trusts that cookbook: a reviewer reads the
recipe with `the-cook recipe show <name>` before approving the diff that pins it.

## Examples

The examples use harness recipes named `claude` and `codex` from `github:acme/cookbook`, declared as in
[Declaring agents](#declaring-agents).

A repository where the team uses one agent interactively and runs the other as workers:

```yaml
agents: { claude: { role: interactive }, codex: { role: worker, writable_roots: [.tasks] } }
```

```sh
echo "Fix the invoice rounding described in task 7f2." | ./cook worker codex --commit
```

A repository where people use both agents interactively, and unattended runs use the agent whose recipe passes
`worker.deny` at launch:

```yaml
agents: { claude: { role: interactive }, codex: { role: interactive } }
```

A repository where acme's own review agent runs as a worker through its own harness recipe, beside one interactive
agent:

```yaml
recipes:
  - name: git
    source: github:acme/cookbook
    version: v1.2.0
    sha256: 9f258251faccf596a0780da3215a96c9d69f04f244a05b71f179008e4e9b2a36
  - name: claude
    source: github:acme/cookbook
    version: v1.2.0
    sha256: a90f021dd04c28e28172aa28048fccbe2b818e04dc2a3e179e21c792b6e3f65b
  - name: acme-review
    source: https://recipes.acme.example/review
    version: v0.3.0
    sha256: 2631021b8b679d990f51f13655f7286c4111a2cb963a801764f3c9d9c887591b
vcs: git
agents:
  claude: { role: interactive }
  reviewer: { role: worker, recipe: acme-review }
```

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| An agent ignores the project rules | The folder is not trusted by the agent, or the agent is not declared in `agents` | Trust the folder; declare the agent and render |
| An agent does not read `AGENTS.md` | Its instruction file lacks the import its harness recipe renders | `the-cook render` restores the `import` block |
| `the-cook worker <agent>` refused | The agent has `role: interactive`, and its harness recipe passes `worker.deny` only through rendered rules | Use `role: worker`, or an agent whose recipe passes `worker.deny` at launch |
| `the-cook worker` exits 1 naming a bypass flag | The loop runner's configuration adds the flag to the agent's arguments | Remove it from the loop runner's configuration |
| A sandboxed worker cannot write a directory outside its checkout | Only the state directory and `agents.<name>.writable_roots` are writable | Add the directory to `writable_roots`, relative to the primary checkout's root |
| `doctor` reports an unsupported agent version | The agent's version is outside the range its harness recipe's check supports | Pin a cookbook version whose recipe supports it, `./cook recipe pin <name> --cookbook <source>@<version>`, or use a supported agent version |
| Exit 1: an agent's `recipe` is not in `recipes:` | The harness recipe was never pinned, or was deleted from `recipes:` | `./cook recipe pin <name> --cookbook <source>@<version>` |
| `the-cook worker` or a hook names `./cook recipe fetch` | The agent's harness recipe is declared and not in this machine's cache | Run `./cook recipe fetch` |
