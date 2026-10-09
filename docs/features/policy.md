# Policy: `.agents/policy.yaml`

The policy says what agents may do without asking (`allow`), only after a person confirms (`ask`), or never (`deny`),
plus what Ralph workers alone may not do. It is written once, agent-neutrally where the agents allow it, and rendered
into each agent's native permission format. A built-in base that holds the tool's invariants is always added and
cannot be removed.

## When to edit it

- Agents keep asking for a command that is local and reversible: add it to `shared.allow`.
- A command must never run from an agent session: add it to `shared.deny`.
- A person must confirm something every time, even in unattended sessions: add it to `shared.ask`, knowing it costs a
  prompt every time.
- Ralph workers must not run something that interactive sessions may: add it to `worker.deny`.
- A tool other than the shell needs a rule, such as a read-only MCP tool or a web domain: add a native rule string
  under the decision it needs.

Then run `harness render` and review the diff of the rendered files.

## How it works

### Sources and precedence

The effective policy is the union of:

1. the built-in base ([below](#the-built-in-base));
2. allow entries for the commands of each enabled module (for example `harness verify` and its wrapper);
3. entries from the preset named by `extends` ([presets](presets.md));
4. the entries in `.agents/policy.yaml`, applied after the preset's.

Lists are unions: no source removes an entry from another, and nothing removes a built-in invariant. When a command
matches entries with different decisions, the decision is taken in this order, in every agent:

```text
deny  >  ask  >  allow  >  (unlisted: the agent's own default)
```

A command no entry matches falls to the agent's default behaviour: Claude Code in manual mode prompts; Claude Code in
auto mode lets its classifier decide from the conversation, without prompting; Codex runs it inside its sandbox and
asks to escalate when it needs to leave it.

A matching `ask` prompts even in auto mode, and no `allow` from any settings scope overrides it, so every `ask` entry
costs a prompt in every unattended run. A matching `allow` skips the auto-mode classifier entirely.

### Entry forms

| Form | Example | Matches | Rendered for |
| --- | --- | --- | --- |
| 1. Prefix list | `[git, push]` | every shell command whose leading words are `git push` | Claude Code and Codex |
| 1. Prefix list with alternatives | `[gh, pr, [view, list, diff]]` | `gh pr view …`, `gh pr list …`, `gh pr diff …` | Claude Code and Codex |
| 2. Native rule string | `"WebFetch(domain:example.com)"`, `"Read(./secrets/**)"`, `"WebSearch"`, `"mcp__datadog__get_*"`, `"Bash(npm run test *)"` | what the agent's own rule syntax matches, globs included | Claude Code verbatim; Codex only for `Bash(<words> *)` with a plain word prefix |
| 3. Bare string | `"gh pr merge *--admin*"` | shorthand for `Bash(<string>)`, where `*` matches anything anywhere | Claude Code only |

A nested list is a set of alternatives for one word, one level deep. Use quotes for words YAML would otherwise parse,
such as `"db:restore"` or `"-f"`.

A string is a native rule string (form 2) when it has the form `Tool(specifier)`, names a tool on its own such as
`WebSearch`, or starts with `mcp__`. Every other string is form 3.

### How entries render

| Policy | Claude Code (`.claude/settings.json`) | Codex (`.codex/rules/harness.rules`) |
| --- | --- | --- |
| prefix list under `shared.allow` | `Bash(<words> *)` in `permissions.allow`, one rule per combination of alternatives | `prefix_rule(pattern=[…], decision="allow")` |
| prefix list under `shared.ask` | `Bash(<words> *)` in `permissions.ask` | `decision="prompt"` |
| prefix list under `shared.deny` | `Bash(<words> *)` in `permissions.deny` | `decision="forbidden"` |
| native rule string `Bash(<words> *)` with a plain word prefix | verbatim, in the same list | the equivalent `prefix_rule` |
| any other native rule string | verbatim, in the same list | not rendered: Claude-only |
| bare string | `Bash(<string>)` in the same list | not rendered: Claude-only |
| `worker.deny` | passed to Claude workers at launch as disallowed tools; also in `permissions.deny` when Claude's role is `worker` | `decision="forbidden"` when Codex's role is `worker` |

Every Claude-only entry is listed as such in the `policy-summary` block of `AGENTS.md`, so a reader sees which rules
Codex does not enforce. See [agents](agents.md#roles) for what roles change.

### The built-in base

The base holds only tool-generic invariants. It is not written in `.agents/policy.yaml`; it is added at render time
and shown in the `policy-summary` block of `AGENTS.md`.

| Invariant | Entries |
| --- | --- |
| No force push to the default branch | `shared.deny` entries equivalent to the list below, with `main` replaced by the branch `origin/HEAD` names |
| No mise `:::` chaining, which runs a second task in the same command and could carry an unlisted task past every rule | `shared.deny`: `"mise * ::: *"` (Claude-only: Codex cannot match mid-command) |
| Lifecycle actions never run inside a Ralph worker | `worker.deny`: `[harness, lifecycle]`; lifecycle actions run in the launcher process ([lifecycle](lifecycle.md)) |
| Workers never bypass permissions | not a policy entry: the Ralph worker refuses bypass flags; see [ralph](ralph.md#the-worker-contract) |

```yaml
# Equivalent force-push entries for a repository whose default branch is main
- [git, push, ["--force", "--force-with-lease", "-f"], origin, [main, "HEAD:main"]]
- [git, push, origin, ["+main", "+HEAD:main"]]
- "git push origin main --force*"
- "git push origin main -f*"
- "git push origin HEAD:main --force*"
- "git push origin HEAD:main -f*"
```

The prefix forms catch the flag-first spellings for both agents; the bare strings catch the flag-last spellings for
Claude Code.

Organization choices are not in the base: denying a tracker's remote sync, allowing deploy status tasks, MCP read
rules. They belong in the repository file or a preset.

### The seeded entry

`harness scaffold` seeds every new policy with one repository entry:

```yaml
ask:
  # Delivery runs the repository's lifecycle actions, outside the agent's own permission checks.
  - [harness, lifecycle, run, deliver]
```

Permitting an agent to run `harness lifecycle run deliver` permits everything the `deliver` actions do: they run as
child processes of `harness`, where the agent's per-command permissions do not reach. The seeded `ask` keeps a person
in the loop before delivery. It is the repository's entry, not an invariant: a repository whose `deliver` actions only
open draft pull requests may move it to `allow`. Lifecycle actions are committed configuration; review them like
code ([lifecycle](lifecycle.md), [safety](safety.md)).

### Adoption

`harness scaffold --adopt` imports existing permission rules into this file: Claude Code permissions, Codex rules and,
with `--import local` or `--import user`, personal settings. Each imported entry keeps its decision and carries a
provenance comment, and an equivalence check proves no project rule was lost. See
[scaffold](scaffold.md#adopt).

## Reference

| Field | Type | Default | Meaning |
| --- | --- | --- | --- |
| `schema` | integer | required | `1` |
| `extends` | string | none | A preset source whose policy entries are added; see [presets](presets.md#extends) |
| `shared.allow` | list of entries | `[]` | Run without a prompt, in every session |
| `shared.ask` | list of entries | `[]` | Always ask a person first |
| `shared.deny` | list of entries | `[]` | Never run |
| `worker.deny` | list of entries | `[]` | Never run by a Ralph worker, on top of `shared` |

`scaffold` seeds the file as:

```yaml
schema: 1
shared:
  allow: []
  ask:
    - [harness, lifecycle, run, deliver]
  deny: []
worker:
  deny: []
```

## Examples

### A Node API

```yaml
# .agents/policy.yaml
schema: 1
shared:
  allow:
    # Git is local or reaches a branch; every step is reversible. The default branch's protection guards what lands.
    - [git]
    # Reads, and pull-request writes a person can edit or close.
    - [gh, pr, [checkout, checks, comment, create, diff, edit, list, ready, view]]
    - [gh, run, [list, view, watch]]
    # Tasks whose effects stay in this clone.
    - [npm, run, [build, lint, test, "test:e2e", typecheck]]
    - [mise, run, ["db:reset", "docker:up", "docker:down"]]
    # Read-only observability tools.
    - "mcp__acme_observability__search_*"
  ask:
    # Merging with --admin skips the branch protection.
    - "gh pr merge *--admin*"
    # Delivery leaves the clone; a person confirms it.
    - [harness, lifecycle, run, deliver]
  deny:
    - [npm, publish]
    - "Read(./.env*)"
worker:
  deny:
    # A worker's result is verified after it exits, then committed by the worker wrapper or ralph-tui.
    # Anything that leaves the worker's worktree before that cannot be checked or undone.
    - [git, [am, cherry-pick, commit, merge, push, rebase, reset, stash, tag, update-ref, worktree]]
    - [git, ["-C", "-c"]]
    - [gh]
    - [harness, [ralph, worktree]]
    - [mise, run, ["db:reset", "docker:down"]]
```

`Read(./.env*)` and the two bare strings are Claude-only; the `policy-summary` block lists them so.

### A Terraform repository

```yaml
schema: 1
shared:
  allow:
    - [git]
    - [terraform, [fmt, validate, providers, version]]
    - [terraform, plan]
    - [tflint]
  ask:
    - [harness, lifecycle, run, deliver]
  deny:
    - [terraform, [apply, destroy, import, taint, untaint]]
    - [terraform, state, [mv, rm, push, replace-provider]]
worker: { deny: [[gh], [aws], [terraform, init, "-upgrade"]] }
```

### A Go repository

```yaml
schema: 1
shared:
  allow:
    - [git]
    - [go, [build, test, vet, mod, generate, run]]
    - [golangci-lint, run]
    - [gh, [issue, pr], [list, view]]
    - "mcp__github__get_*"
    - "mcp__github__list_*"
    - "WebFetch(domain:pkg.go.dev)"
  ask: []
  deny:
    - [goreleaser, release]
worker: { deny: [[git, [commit, push, worktree]], [gh]] }
```

This repository removed the seeded delivery entry: its `deliver` actions only push a branch and open a draft pull
request, so delivery falls to each agent's default for unlisted commands.

## Guidelines

Placing an entry:

- **allow** what is reversible, local or remote: a commit, a branch push, a pull request, a comment, tasks whose
  effects stay in the clone.
- **ask** only before bypassing a merge gate a person must own, such as merging past branch protection, or before
  actions that leave the clone when you want a person in the loop, such as `harness lifecycle run deliver`. Each
  `ask` costs a prompt in every unattended run.
- **deny** only what must never run from an agent session.
- **leave unlisted** production, cloud and repository-settings writes: whoever is present decides, an unattended
  Claude session's classifier judges them, and workers can be denied them through `worker.deny`.

More:

- Do comment each group of entries with the reason; the reason is what a reviewer evaluates.
- Do put organization-wide entries in a [preset](presets.md) rather than copying them into every repository.
- Do express a rule as a prefix list whenever Codex must also enforce it.
- Don't use `allow` entries broader than the reason you wrote for them.

[Safety guidelines](safety.md) explains the threat model behind these rules.

## Known gaps

Prefix matching, Claude-only entries, settings outside the policy and the other permission traps are in
[pitfalls](pitfalls.md#permissions), each with what to do about it. Hooks never decide permissions: there is no
hook-based allow or deny.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| An allowed command still prompts in Claude Code | An `ask` or a `deny` also matches; or the folder is not trusted | Check `policy-summary` in `AGENTS.md`; trust the folder |
| A deny works in Claude Code but not in Codex | The entry is Claude-only: a bare string or a native rule string other than a plain `Bash` prefix | Express it as a prefix list too |
| `render` rejects an entry | A nested list is deeper than one level, or an entry is neither a list nor a string | Use one level of alternatives per word |
| The force-push deny names the wrong branch | `origin/HEAD` points elsewhere or is unset | `git remote set-head origin --auto`, then `harness render` |
| An agent's `harness lifecycle run deliver` prompts every time | The seeded `ask` entry | Intended; move it to `allow` only if the `deliver` actions are safe to run unconfirmed |
