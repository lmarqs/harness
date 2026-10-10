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

Each agent is served by a [harness plugin](plugins.md#the-harness-contract): a plugin that says where the agent reads
its configuration, how its permissions and hooks are expressed, what its hook payloads look like and how to start it
safely as a worker, the agent [`harness worker`](ralph.md#the-worker-contract) runs headless for one task, the command a
loop runner such as ralph-tui names as its agent command. The binary contains code for no agent. The release ships
harness plugins for two, Claude Code (the [`claude`](plugins.md#claude) plugin) and Codex CLI (the
[`codex`](plugins.md#codex) plugin), and a repository may declare a [fetched](plugins.md#fetched-plugins) harness plugin
for another. A repository declares which agents it uses, in which role and with which plugin, in the
[profile](profile.md#agents).

Commands are named `harness <command>` on this page. Run them through the repository's wrapper,
`./harnessw <command>`, from the root of a checkout; with a global install, `harness <command>` is equivalent
([wrapper](wrapper.md)).

## Declaring agents

```yaml
# .harness/config.yaml
plugins: [git, claude, codex]
vcs: git
agents:
  claude: { role: interactive }
  codex: { role: worker }
```

Each agent's harness plugin is the one its `plugin` field names, by default the plugin named like the agent, and it
must be in `plugins:`. Applying the `claude` or `codex` plugin declares its agent with `role: interactive` when the
profile does not ([plugins](plugins.md#claude)). Only declared agents get native files. Removing an agent from `agents`
and running `harness render` removes what was rendered for it.

## Roles

A role decides which parts of the [policy](policy.md) an agent's native files carry. `shared` holds the `allow`, `ask`
and `deny` entries for every session; `worker.deny` holds what workers alone must never run, such as
`[git, push]`. `worker.deny` always holds `[harness, lifecycle]`, which the binary renders itself for its own command,
once for each spelling, such as `harness lifecycle` and `./harnessw lifecycle`
([policy](policy.md#entries-for-harness-commands)).

| Role | Meaning | Native files carry | Under `harness worker` |
| --- | --- | --- | --- |
| `interactive` | people use this agent in the repository | the shared policy (`shared.*`) and the floor rules of every plugin in use | Claude Code: `worker.deny`, including `[harness, lifecycle]` in each spelling `harness` renders, is added at launch. Codex: refused, because its denies can only come from rendered rules |
| `worker` | the agent runs here only unattended, under `harness worker` or an `agent` action | the shared policy, the floor rules of every plugin in use, and `worker.deny`, including `[harness, lifecycle]` in each spelling `harness` renders | allowed |

A repository where people use Codex interactively and also want Codex workers chooses one: interactive Codex, or Codex
workers. Claude Code can be both, since its worker denies are passed at launch. Which of the two a harness plugin does
is part of its [launch](plugins.md#the-harness-contract).

A Codex worker's denies live in files inside its own task checkout, which the agent can write. The worker's
[tamper check](ralph.md#the-worker-contract) closes that gap. Before every launch it restores
`.codex/rules/harness.rules`, the owned `.codex/config.toml` keys and the harness entries of `.codex/hooks.json` from
the base commit, the `HEAD` of the checkout when `harness worker` started, and after the agent exits it compares them
again. Those are the codex plugin's [renders](plugins.md#renders); for any agent, the tamper check covers every render
of every plugin in use. An attempt whose agent changed any of them fails, whatever its [receipt](verify.md#the-receipt)
(the record of a passing `harness verify`) says. The same check covers Claude Code's owned keys in
`.claude/settings.json` and, for every agent, the wrapper `harnessw` and its pin `.harness/wrapper.yaml`, so an agent
cannot change the binary its hooks and its next attempt run. After the agent exits, the comparison also covers every
file under `.harness/` and every file and skill a `context` entry names, and a failed attempt returns the checkout to
the base commit, keeping its changes as `last-attempt.patch` in the state directory.

## What each harness plugin renders

| | Claude Code | Codex |
| --- | --- | --- |
| Instructions | `CLAUDE.md` gets the `import` block (`@AGENTS.md`); Claude Code reads `AGENTS.md` through it | reads the root `AGENTS.md` directly |
| Permissions | `.claude/settings.json`: `permissions.allow`, `ask`, `deny` | `.codex/rules/harness.rules`: `prefix_rule` entries with `allow`, `prompt`, `forbidden` |
| Prefix lists | `Bash(<words> *)` | `prefix_rule` |
| Native rule strings (`WebFetch(...)`, `Read(...)`, `mcp__...`) | rendered verbatim | only `Bash(<words> *)` with a plain word prefix converts; the rest are Claude-only |
| Bare strings | rendered as `Bash(<string>)` | skipped: Claude-only |
| Hooks | `.claude/settings.json` `hooks`: `SessionStart`; `PostToolUse` with matcher `Edit\|Write\|MultiEdit`, plus `Read` when `when.paths` context exists; `UserPromptSubmit` when `when.prompt` context exists | `.codex/hooks.json`: `SessionStart`, `PostToolUse` (every tool; `harness hook` filters edits) and, when `when.prompt` context exists, `UserPromptSubmit`; `.codex/config.toml`: the keys that enable project hooks |
| Notification hooks | `Notification` for `attention`, `Stop` for `turn_end` | `notify` in `.codex/config.toml` for `turn_end`, when the key is free ([compatibility](compatibility.md#native-agent-files)); Codex has no event for `attention`, so nothing is rendered for it and `doctor` notes it |
| [Standing context](context.md) | `always` at `SessionStart`, which Claude Code fires again after compaction; `when.paths` at `PostToolUse` after `Read`, `Edit`, `Write` or `MultiEdit`; `when.prompt` at `UserPromptSubmit` | `always` at `SessionStart`; `when.paths` at `PostToolUse` after `apply_patch` only, because Codex reads files through shell commands `harness hook` cannot attribute; `when.prompt` at `UserPromptSubmit` |
| Edit tools recognised by `harness hook` | `Edit`, `Write`, `MultiEdit` | `apply_patch` |
| Harness skill | `.claude/skills/harness` | `.codex/skills/harness` |
| Never written | `.claude/settings.local.json` (read only by `scaffold --import local`), `.claude/worktrees/` (never scanned) | `*.local.toml` (read only by `scaffold --import local`) |

Both plugins render the same [hook command](render.md#the-hook-command), which finds the checkout's root through the
version-control plugin's `root` operation; with the git plugin it is
`"$(git rev-parse --show-toplevel)/harnessw" --no-download hook <event>`. It runs the pinned binary of the checkout the
agent works in, from the cache, and never downloads. When the binary is not cached, it prints one line naming
`./harnessw` and exits 0 ([hooks](hooks.md#the-missing-binary-guard)).

See [render](render.md#merge-rendering-native-files) for the merge rules: every key and entry the plugin does not own
is preserved byte for byte, because a plugin's renders own only the keys and entries they name.

## Worker launch

| | Claude Code | Codex |
| --- | --- | --- |
| Started as | `harness worker claude [--commit] [--withhold <text>] [-- <agent args>]` | `harness worker codex [--commit] [--withhold <text>] [-- <agent args>]` |
| Permission mode | `--permission-mode auto` | `workspace-write` sandbox |
| Worker denies | `worker.deny` as disallowed tools | `worker.deny` in the rendered rules (`role: worker`) |
| Tamper check restores | the owned `permissions.*` keys and harness hook entries of `.claude/settings.json`; `harnessw`; `.harness/wrapper.yaml` | `.codex/rules/harness.rules`, owned `.codex/config.toml` keys, harness entries of `.codex/hooks.json`; `harnessw`; `.harness/wrapper.yaml` |
| Environment | `HARNESS_WORKER=1`: `harness` refuses lifecycle events, and notification hooks do nothing | the same |
| Standing context | entries for `role: worker`, read from the base commit | the same |
| Writable outside the task checkout | as permitted by its permissions | only the clone's state directory (`state.dir`, default `.work/`) and the directories the profile lists in `agents.codex.writable_roots`, such as `.beads` ([profile](profile.md#agents)) |
| Network | as permitted by its permissions | on |
| Refused arguments, among those `harness worker` receives: the plugin's [floor](plugins.md#the-floor) bypass arguments | `--dangerously-skip-permissions`, `--allow-dangerously-skip-permissions`, the `bypassPermissions` permission mode | `--dangerously-bypass-approvals-and-sandbox`, `--yolo`, the `danger-full-access` sandbox |
| Model and effort of an `agent` action | `agent.model` as the full model id; `agent.effort` as `--effort <level>` | `agent.model` as the model id; `agent.effort` as `model_reasoning_effort` |

`harness worker` passes the agent the arguments it does not recognise itself, so a loop runner's model and effort flags
reach the agent as the loop runner wrote them. It reads no loop runner's configuration: a bypass flag a loop runner adds
to the arguments is refused when the worker starts, at the first task, with exit 1 naming the flag. Before it starts the
agent, the worker resolves the version-control plugin and the agent's harness plugin from the release or the cache and
uses them for the whole attempt; a fetched plugin that is not cached fails the attempt with exit 3. The full worker
contract is on the [Ralph](ralph.md#the-worker-contract) page.

## Trust

Both agents load project configuration only for folders the person has trusted, and global or managed settings may
restrict project settings. `harness` never changes trust, global settings or credentials. Worktrees nested inside the
primary checkout inherit its trust; sibling worktrees outside it do not. For example, with `~/src/api` trusted, the
managed worktree `~/src/api/.work/worktrees/fix-login` is trusted too, and a sibling checkout `~/src/api-fix-login` is
not ([pitfalls](pitfalls.md#claude-code-ignores-project-permissions-in-an-untrusted-folder)).

## The harness contract

A harness plugin is the complete description of one agent, in data: the binary interprets it and contains nothing
about any agent. Supporting another agent means writing a harness plugin that fills every item of the
[harness contract](plugins.md#the-harness-contract); a partial plugin fails validation, because each item backs an
invariant.

| Item | The harness plugin declares |
| --- | --- |
| Launch | the program; the arguments that start it unattended with its checks on; how `worker.deny` reaches it, as launch arguments or as rendered rules, which then require `role: worker`; the sandbox-root arguments for the state directory and `agents.<name>.writable_roots`; how an `agent` action's prompt, model and effort are passed |
| Floor | the bypass arguments `harness worker` and `agent` actions refuse ([the floor](plugins.md#the-floor)) |
| Hook payload mapping | the JSON paths to the neutral event, the tool, the edited and read files, the working directory, the person's prompt text and the agent's notification message; which tools are edits or reads; which native events carry `attention`, `turn_end` and each standing-context trigger, and which it lacks |
| Instructions | how the agent reaches `AGENTS.md`: directly, or through an import line in its own instruction file |
| Skill location | where the harness skill link goes |
| Native rule import | which native keys hold permission rules, which `scaffold --adopt` copies into the policy |
| Renders | the exact files, keys and entries it owns, rendered from the policy, the floor rules of every plugin in use and the hooks file; everything else is preserved; local override files are never written, and read only by `scaffold --import local` |
| Checks | the agent versions it supports, which `harness doctor` checks |

The release's CI tests the `claude` and `codex` plugins against the agent versions this page names, proving each
invariant for each agent: bypass refused, floor rules rendered, worker denies applied, tampered renders (`harnessw` and
`.harness/wrapper.yaml` included) failing the attempt, and a hook exiting 0 without downloading when the pinned binary
is not cached. A repository may use a fetched harness plugin for another agent, declared in `plugins:` and named by
`agents.<name>.plugin`; its author owns those proofs, and `harness doctor` notes it as `not verified by the release`
([fetched plugins](plugins.md#fetched-plugins)).

## Examples

A Node API where the team uses Claude Code and runs Codex workers on tasks it tracks in Beads:

```yaml
agents: { claude: { role: interactive }, codex: { role: worker, writable_roots: [.beads] } }
```

```sh
echo "Fix the invoice rounding described in acme-api-7f2." | ./harnessw worker codex --commit -- --model gpt-5-codex
```

A Go repository where people use both agents interactively, and unattended runs use Claude workers, whose worker denies
are passed at launch:

```yaml
agents: { claude: { role: interactive }, codex: { role: interactive } }
```

A Terraform repository where only Claude Code is used:

```yaml
agents: { claude: { role: interactive } }
```

A repository where acme's own review agent runs as a worker through a fetched harness plugin, beside Claude Code:

```yaml
plugins:
  - git
  - claude
  - name: acme-review
    source: github:acme/harness-plugins
    version: v0.3.0
    sha256: 00656e7d94b5b56f73085a7aa1877dac25fee6f230f5117eb99e5cd66e168843
vcs: git
agents:
  claude: { role: interactive }
  reviewer: { role: worker, plugin: acme-review }
```

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| Codex ignores the project rules | The folder is not trusted, or Codex is not declared in `agents` | Trust the folder; declare it and render |
| Claude Code does not read `AGENTS.md` | `CLAUDE.md` lacks the import | `harness render` restores the `import` block |
| `harness worker codex` refused | Codex has `role: interactive` | Use `role: worker`, or Claude workers |
| `harness worker` exits 1 naming `--dangerously-skip-permissions`, `--yolo` or another bypass flag | The loop runner's configuration adds the flag to the agent's arguments | Remove it there; for ralph-tui, keep `skipPermissions = false` in `.ralph-tui/config.toml` |
| A Codex worker cannot write a directory outside its checkout | Only the state directory and `agents.codex.writable_roots` are writable | Add the directory to `writable_roots`, relative to the primary checkout's root |
| `doctor` reports an unsupported agent version | The agent's version is outside the range its harness plugin's check supports | Update `harness`, whose release ships newer builtin plugins, or use a supported agent version |
| Exit 1: an agent's `plugin` is not in `plugins:` | The harness plugin was never applied, or was deleted from `plugins:` | `./harnessw plugin apply <name>`, or add the fetched plugin's declaration |
| `harness worker` or a hook exits 3 naming `./harnessw plugin fetch` | The agent's harness plugin is fetched and not in this machine's cache | Run `./harnessw plugin fetch` |
