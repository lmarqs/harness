# Agents

`harness` supports two agents, Claude Code (`claude`) and Codex CLI (`codex`). Each is handled by an adapter that
knows where the agent reads its configuration, how its permissions and hooks are expressed, what its hook payloads
look like and how to launch it safely as a Ralph worker. A repository declares which agents it uses, and in which
role, in the [profile](profile.md#agents).

Commands are named `harness <command>` on this page. Run them through the repository's wrapper,
`./harnessw <command>`, from the root of a checkout; with a global install, `harness <command>` is equivalent
([wrapper](wrapper.md)).

## Declaring agents

```yaml
# .harness/config.yaml
agents:
  claude: { role: interactive }
  codex: { role: worker }
```

Only declared agents get native files. Removing an agent from `agents` and running `harness render` removes what was
rendered for it.

## Roles

| Role | Meaning | Native files carry | As a Ralph worker |
| --- | --- | --- | --- |
| `interactive` | people use this agent in the repository | the shared policy (`shared.*`) | Claude Code: `worker.deny`, including the built-in `[harness, lifecycle]` in each spelling `harness` renders, is added at launch. Codex: refused, because its denies can only come from rendered rules |
| `worker` | the agent runs here only as a Ralph worker | the shared policy plus `worker.deny`, including the built-in `[harness, lifecycle]` in each spelling `harness` renders | allowed |

A repository where people use Codex interactively and also want Codex workers chooses one: interactive Codex, or
Codex workers. Claude Code can be both, since its worker denies are passed at launch.

A Codex worker's denies live in files inside its own task checkout, which the agent can write. The worker's
[tamper check](ralph.md#the-worker-contract) closes that gap: before every launch it restores
`.codex/rules/harness.rules`, the owned `.codex/config.toml` keys and the harness entries of `.codex/hooks.json` from
the session's base commit, and after the agent exits it compares them again. An attempt whose agent changed any of
them fails, whatever the receipt says. The same check covers Claude Code's owned keys in `.claude/settings.json` and,
for every agent, the wrapper `harnessw` and its pin `.harness/wrapper.yaml`, so an agent cannot change the binary its
hooks and its next attempt run.

## What each adapter renders

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

Both adapters write the same hook command, `"$(git rev-parse --show-toplevel)/harnessw" --no-download hook <event>`. It runs
the pinned binary of the checkout the agent works in, from the cache, and never downloads: when the binary is not
cached it prints one line naming `./harnessw` and exits 0 ([hooks](hooks.md#the-missing-binary-guard)).

See [render](render.md#merge-rendering-native-files) for the merge rules: every key and entry the adapter does not own
is preserved byte for byte.

## Worker launch

| | Claude Code | Codex |
| --- | --- | --- |
| Started as | `ralph-worker claude` on the launcher's own binary, by absolute path | `ralph-worker codex` on the launcher's own binary, by absolute path |
| Permission mode | `--permission-mode auto` | `workspace-write` sandbox |
| Worker denies | `worker.deny` as disallowed tools | `worker.deny` in the rendered rules (`role: worker`) |
| Tamper check restores | the owned `permissions.*` keys and harness hook entries of `.claude/settings.json`; `harnessw`; `.harness/wrapper.yaml` | `.codex/rules/harness.rules`, owned `.codex/config.toml` keys, harness entries of `.codex/hooks.json`; `harnessw`; `.harness/wrapper.yaml` |
| Environment | `HARNESS_WORKER=1`: `harness` refuses lifecycle events, and notification hooks do nothing | the same |
| Standing context | entries for `role: worker`, read from the session's base commit | the same |
| Writable outside the task checkout | as permitted by its permissions | only the clone's shared state directory (`state.dir`, default `.work/`), mise's cache directory, and the clone's `.beads/` with Beads |
| Network | as permitted by its permissions | on |
| Refused flags | `--dangerously-skip-permissions`; `skipPermissions = true` in a ralph-tui config | `--dangerously-bypass-approvals-and-sandbox`, `--yolo`, the `danger-full-access` sandbox; `fullAuto = true` in a ralph-tui config |
| Model override | the full model id replaces the configured one | the model id replaces the configured one |
| Effort override | `--effort <level>` | `model_reasoning_effort` |

The full worker contract is on the [Ralph](ralph.md#the-worker-contract) page.

## Trust

Both agents load project configuration only for folders the person has trusted, and global or managed settings may
restrict project settings. `harness` never changes trust, global settings or credentials. Worktrees nested inside the
primary checkout inherit its trust; sibling worktrees outside it do not ([pitfalls](pitfalls.md)).

## The adapter contract

An adapter is the complete description of one agent. Supporting a new agent means providing every item below; a
partial adapter is not accepted, because each item backs an invariant.

| Item | The adapter defines |
| --- | --- |
| Identity | the agent's name in the profile, its CLI command, and its ralph-tui plugin name |
| Instructions | how the agent reaches `AGENTS.md` (reads it directly, or through an import in its own instruction file) |
| Permission rendering | how `allow`, `ask`, `deny`, prefix lists, alternatives, native rule strings, bare strings and, for `role: worker`, `worker.deny` map to the agent's native format, and which forms it cannot express (those are listed as agent-only in the `policy` block of `AGENTS.md`) |
| Owned native content | the exact files, keys and entries it writes; everything else is preserved; local override files are never written, and read only by `scaffold --import local` |
| Hook rendering | the events it supports, the matcher semantics, and how the wrapper's hook command (`harnessw --no-download hook <event>`) and `raw` entries are written; which native events carry `attention`, `turn_end` and each standing-context trigger, and which it lacks |
| Payload normalisation | how its hook payload maps to the normalised event, tool, edited and read files, working directory, the person's prompt text and the agent's notification message, and which tools are edits or reads |
| Skill location | where the harness skill link goes |
| Worker launch | the tool-owned files the tamper check restores and compares; the non-bypass permission mode it pins; how `worker.deny` applies (launch flags, or rendered rules, which then requires `role: worker`); the bypass flags and configuration keys it refuses; the sandbox roots it opens; how a model and an effort are substituted; how the prompt is passed |
| Compatibility | the agent versions it supports, which `harness doctor` checks |
| Contract tests | tests proving each invariant for this agent: bypass refused, base denies rendered, worker denies applied, tampered owned files (`harnessw` and `.harness/wrapper.yaml` included) fail the attempt, a hook exits 0 without downloading when the pinned binary is not cached |

Proposals for new agents are made to the `harness` project itself; a repository cannot add an adapter.

## Examples

A Node API where the team uses Claude Code and runs Codex workers:

```yaml
agents: { claude: { role: interactive }, codex: { role: worker } }
ralph: { config: managed, default_agent: codex, max_iterations: 5, agents: { codex: { model: gpt-5-codex, effort: high } } }
```

A Go repository where people use both agents interactively, and Ralph runs Claude workers:

```yaml
agents: { claude: { role: interactive }, codex: { role: interactive } }
ralph: { config: managed, default_agent: claude, max_iterations: 8, agents: { claude: { model: claude-sonnet-4-5, effort: medium } } }
```

A Terraform repository where only Claude Code is used:

```yaml
agents: { claude: { role: interactive } }
```

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| Codex ignores the project rules | The folder is not trusted, or Codex is not declared in `agents` | Trust the folder; declare it and render |
| Claude Code does not read `AGENTS.md` | `CLAUDE.md` lacks the import | `harness render` restores the `import` block |
| `harness ralph run --agent codex` refused | Codex has `role: interactive` | Use `role: worker`, or Claude workers |
| `doctor` reports an unsupported agent version | The agent changed a format this release does not know | Update `harness`, or use a supported agent version |
