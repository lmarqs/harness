# Policy: `.cook/policy.yaml`

## Objective

**Problem.** A team decides what agents in a repository may do on their own, what needs a person's confirmation first
and what must never happen, but every agent reads its permissions in its own format. Kept by hand in each format, the
rules drift: one agent runs what another is refused, a limit such as never force-pushing the default branch disappears
in an edit, and unattended runs stall on a question nobody answers or do what nobody allowed. A team that already wrote
rules by hand can lose one when it changes how they are kept, and nobody notices until it matters.

**Goal.** Every agent in a repository works under the same reviewed permissions, every rule the team had already written
carries over, and the limits that must always hold stay in force whatever anyone edits.

## Overview

The policy says what agents may do without asking (`allow`), only after a person confirms (`ask`), or never (`deny`),
plus what is denied only to workers, the agents [`the-cook worker`](ralph.md#the-worker-contract) runs headless, one per
task, and that `agent` actions start. It is written once, agent-neutrally where the agents allow it, and rendered into
each agent's native permission format. The [floor](#the-floor) is always added and cannot be removed or weakened: the
floor rules of the recipes in use, such as the git recipe's deny of a force push to the default branch, and the binary's
own entries for its commands.

Commands are named `the-cook <command>` on this page. Run them through the repository's wrapper,
`./cook <command>`, from the root of a checkout; with a global install, `the-cook <command>` is equivalent
([wrapper](wrapper.md)).

## When to edit it

- Agents keep asking for a command that is local and reversible: add it to `shared.allow`.
- A command must never run from an agent session: add it to `shared.deny`.
- A person must confirm something every time, even in unattended sessions: add it to `shared.ask`, knowing it costs a
  prompt every time.
- Workers must not run something that interactive sessions may: add it to `worker.deny`.
- A tool other than the shell needs a rule, such as a read-only MCP tool or a web domain: add a native rule string
  under the decision it needs.

Then run `the-cook render` and review the diff of the rendered files.

## How it works

### Sources and precedence

The effective policy is the union of:

1. the floor ([below](#the-floor)): the floor rules of every recipe in the profile's `recipes:` and the binary's own
   entries for its commands;
2. allow entries for the commands of each enabled [module](profile.md#modules), rendered for each spelling `the-cook`
   renders ([below](#entries-for-the-cook-commands));
3. the entries in `.cook/policy.yaml`, including those a recipe's entries added when it was applied, which are the
   repository's own from then on ([recipes](recipes.md#entries)).

Nothing else contributes an entry: no file outside the repository is read
([ADR 0013](../adrs/0013-organization-content.md)). Lists are unions: no source removes an entry from another, and
nothing removes a floor rule. An entry in the policy file that would weaken a floor rule, such as an `allow` for a force
push to the default branch, is not a validation error. `render` drops it from the output, renders the floor rule and
reports the entry. [`doctor`](doctor.md) reports it as a finding until it is removed
([ADR 0011](../adrs/0011-safety-invariants.md)). When a command matches entries with different decisions, the decision
is taken in this order, in every agent:

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
| 2. Native rule string | `"WebFetch(domain:example.com)"`, `"Read(./secrets/**)"`, `"WebSearch"`, `"mcp__acme-observability__search_*"`, `"Bash(npm run test *)"` | what the agent's own rule syntax matches, globs included | Claude Code verbatim; Codex only for `Bash(<words> *)` with a plain word prefix |
| 3. Bare string | `"gh pr merge *--admin*"` | shorthand for `Bash(<string>)`, where `*` matches anything anywhere | Claude Code only |

A nested list is a set of alternatives for one word, one level deep. Use quotes for words YAML would otherwise parse,
such as `"db:restore"` or `"-f"`.

A string is a native rule string (form 2) when it has the form `Tool(specifier)`, names a tool on its own such as
`WebSearch`, or starts with `mcp__`. Every other string is form 3.

### How entries render

| Policy | Claude Code (`.claude/settings.json`) | Codex (`.codex/rules/the-cook.rules`) |
| --- | --- | --- |
| prefix list under `shared.allow` | `Bash(<words> *)` in `permissions.allow`, one rule per combination of alternatives | `prefix_rule(pattern=[…], decision="allow")` |
| prefix list under `shared.ask` | `Bash(<words> *)` in `permissions.ask` | `decision="prompt"` |
| prefix list under `shared.deny` | `Bash(<words> *)` in `permissions.deny` | `decision="forbidden"` |
| native rule string `Bash(<words> *)` with a plain word prefix | verbatim, in the same list | the equivalent `prefix_rule` |
| any other native rule string | verbatim, in the same list | not rendered: Claude-only |
| bare string | `Bash(<string>)` in the same list | not rendered: Claude-only |
| `worker.deny` | passed by `the-cook worker` to a Claude worker at launch as disallowed tools; also in `permissions.deny` when Claude's role is `worker` | `decision="forbidden"` when Codex's role is `worker` |

Every Claude-only entry is listed as such in the [`policy` block](#the-convention-in-agentsmd) of `AGENTS.md`, so a
reader sees which rules Codex does not enforce. See [agents](agents.md#roles) for what roles change.

### Entries for `the-cook` commands

An agent can invoke a `the-cook` command in several spellings: `./cook <command>`, the form `AGENTS.md` teaches;
`the-cook <command>` with a global install; and `cook <command>` when the repository root is on `PATH`. An entry for
a `the-cook` command names the program once, as `the-cook`:

```yaml
- [the-cook, lifecycle, run, deliver]
```

`render` writes such an entry for each rendered spelling: `the-cook …`, `./cook …` and `cook …`. The binary's
floor entry for its own commands, the module entries and the [seeded entry](#the-seeded-entry) are written the same way.

A prefix rule still matches only leading words, so an absolute path to the wrapper, or `sh cook …`, matches none of
the rendered spellings ([pitfalls](pitfalls.md#the-wrapper-is-a-second-spelling-of-every-the-cook-command)). The guards
that matter therefore never rest on these entries: the binary itself refuses `the-cook lifecycle run` inside a worker's
agent, and it enforces the [`deliver` guards](lifecycle.md#built-in-guards) whoever runs the command. The entries are
defence in depth: they make an agent stop or ask before it runs the command at all.

### The floor

The floor holds the rules no repository may lose. A recipe marks the facts about its program that the
[safety invariants](../adrs/0011-safety-invariants.md) depend on as floor ([recipes](recipes.md#the-floor)); the binary
adds entries for its own commands. The floor is never written in `.cook/policy.yaml`: `render` adds it to every
agent's output on every render, as long as the recipe is in the profile's `recipes:`, and shows it in the `policy` block
of `AGENTS.md` with the recipe each rule comes from.

| Rule | From | Entries |
| --- | --- | --- |
| No force push to the default branch | a floor rule of the `git` recipe | `shared.deny` entries equivalent to the list below, with `main` replaced by the default branch the version-control recipe reports (the git recipe reads it from `origin/HEAD`) |
| No task chaining with mise's `:::`, which runs a second task in the same command and could carry an unlisted task past every rule | a floor rule of the `mise` recipe | `shared.deny`: `"mise * ::: *"` (Claude-only: Codex cannot match mid-command) |
| Lifecycle actions never run inside a worker's agent | the binary | `worker.deny`: `[the-cook, lifecycle]`, rendered for each spelling `the-cook` renders. The binary refuses `the-cook lifecycle run` inside the agent on its own; `the-cook worker` fires `completed` and `deliver` in its own process ([lifecycle](lifecycle.md#built-in-guards)) |
| Workers never bypass permissions | the floor bypass arguments of each harness recipe | not a policy entry: `the-cook worker` and `agent` actions refuse them among the arguments they receive; see [ralph](ralph.md#the-worker-contract) |

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

A floor rule exists only while its recipe is in use. A repository that runs mise without the mise recipe has no `:::`
deny, and [`doctor`](doctor.md) reports it:
`mise detected; its recipe and its floor are not in use; run ./cook recipe apply mise`.

Organization choices are not in the floor: denying the remote sync of a [task tracker](tasks.md), allowing deploy status
tasks, MCP read rules. They belong in each repository's policy file, written there by a person or an agent and reviewed
like any other change ([organization choices](recipes.md#organization-choices)).

### The seeded entry

`the-cook scaffold` seeds every new policy with one repository entry:

```yaml
ask:
  # Delivery runs the repository's lifecycle actions, outside the agent's own permission checks.
  - [the-cook, lifecycle, run, deliver]
```

`render` writes it for each spelling `the-cook` renders. Permitting an agent to run `the-cook lifecycle run deliver`
permits everything the `deliver` actions do: they run as child processes of `the-cook`, where the agent's per-command
permissions do not reach. The seeded `ask` keeps a person in the loop before delivery. It is a seeded entry, which the
repository owns and may change; it is not an invariant ([ADR 0015](../adrs/0015-development-lifecycle.md)). A repository
whose `deliver` actions only open draft pull requests may move it to `allow`. Lifecycle actions are committed
configuration; review them like code ([lifecycle](lifecycle.md), [safety](safety.md)).

### Adoption

`the-cook scaffold --adopt` imports existing permission rules into this file: Claude Code permissions, Codex rules and,
with `--import local` or `--import user`, personal settings. Each imported entry keeps its decision and carries a
provenance comment naming its source, such as `# imported from .claude/settings.json (project)`, and an equivalence
check proves no project rule was lost. See [scaffold](scaffold.md#adopt).

### The convention in `AGENTS.md`

`the-cook render` writes the effective permissions, in plain language, into the `policy` block of `AGENTS.md`: the
decision order, then every entry grouped by decision, with each floor rule marked with the recipe it comes from and each
Claude-only entry marked.

`policy` is a convention block ([concepts](concepts.md#conventions-outlive-the-tool)): it describes the permissions the
agents' native settings enforce, and stays true when `the-cook` is gone. Entries for `the-cook`'s own commands are
therefore left out of it, since those commands do not exist after an [eject](remove.md#eject)
(`the-cook remove --eject`). `./cook explain policy` prints the complete effective policy, those entries included.
For the [Node API](#a-node-api) below, with every module on:

```markdown
<!-- cook:policy -->
Agents in this repository work under these permissions. A denied command never runs, an asked one waits for a person,
an allowed one runs without a prompt; deny wins over ask, and ask over allow. A command no entry matches is left to
the agent's own default. Entries marked *Claude Code only* are not enforced in Codex.

Denied:
- force pushes to `main`: `git push --force origin main` and its other spellings (*floor rule of the git recipe*)
- `mise * ::: *` (*floor rule of the mise recipe*; *Claude Code only*)
- `npm publish`
- `Read(./.env*)` (*Claude Code only*)

Ask a person first:
- `gh pr merge *--admin*` (*Claude Code only*)

Allowed:
- `git`
- `gh pr checkout|checks|comment|create|diff|edit|list|ready|view`
- `gh run list|view|watch`
- `npm run build|lint|test|test:e2e|typecheck`
- `mise run db:reset|docker:up|docker:down`
- `mcp__acme_observability__search_*` (*Claude Code only*)

Denied to workers, in addition:
- `git am|cherry-pick|commit|merge|push|rebase|reset|stash|tag|update-ref|worktree`
- `git -C`, `git -c`
- `gh`
- `bd close`
- `mise run db:reset|docker:down`
<!-- /cook:policy -->
```

The block states the permissions as facts. After [`the-cook remove --eject`](remove.md#eject) the markers go and the
text stays. The rendered permissions stay in each agent's settings as the repository's own, so agents keep working under
the same rules and the block keeps describing them. From then on nothing renders them, so a change to one settings file
has to be made by hand in the other agent's file and in this text.

## Reference

| Field | Type | Default | Meaning |
| --- | --- | --- | --- |
| `schema` | integer | required | `1` |
| `shared.allow` | list of entries | `[]` | Run without a prompt, in every session |
| `shared.ask` | list of entries | `[]` | Always ask a person first |
| `shared.deny` | list of entries | `[]` | Never run |
| `worker.deny` | list of entries | `[]` | Never run by a worker (`the-cook worker` or an `agent` action), on top of `shared` |

`scaffold` seeds the file as:

```yaml
schema: 1
shared:
  allow: []
  ask:
    - [the-cook, lifecycle, run, deliver]
  deny: []
worker:
  deny: []
```

## Examples

### A Node API

```yaml
# .cook/policy.yaml
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
    - [the-cook, lifecycle, run, deliver]
  deny:
    - [npm, publish]
    - "Read(./.env*)"
worker:
  deny:
    # A worker's result is verified after it exits, then committed by the-cook worker --commit.
    # Anything that leaves the worker's worktree before that cannot be checked or undone.
    - [git, [am, cherry-pick, commit, merge, push, rebase, reset, stash, tag, update-ref, worktree]]
    - [git, ["-C", "-c"]]
    - [gh]
    - [the-cook, worktree]
    # The completed action of the beads recipe closes the task after the replay; the agent does not.
    - [bd, close]
    - [mise, run, ["db:reset", "docker:down"]]
```

`Read(./.env*)`, the MCP rule and the bare string are Claude-only; the `policy` block lists them so.

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
    - [the-cook, lifecycle, run, deliver]
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
  actions that leave the clone when you want a person in the loop, such as `the-cook lifecycle run deliver`. Each
  `ask` costs a prompt in every unattended run.
- **deny** only what must never run from an agent session.
- **leave unlisted** production, cloud and repository-settings writes: whoever is present decides, an unattended
  Claude session's classifier judges them, and workers can be denied them through `worker.deny`.

More:

- Do comment each group of entries with the reason; the reason is what a reviewer evaluates.
- Do write an organization's entries into each repository's policy through a reviewed change, for example by an agent
  following a skill the organization publishes, such as one installed with `npx skills add acme/agent-skills`: each
  repository reviews them, and they are readable where they apply
  ([organization choices](recipes.md#organization-choices)).
- Do express a rule as a prefix list whenever Codex must also enforce it.
- Do write an entry for a `the-cook` command once, as `[the-cook, …]`; `render` writes it for each spelling it renders,
  and the binary's own guards catch the others.
- Don't use `allow` entries broader than the reason you wrote for them.

[Safety guidelines](safety.md) explains the threat model behind these rules.

## Known gaps

Prefix matching, Claude-only entries, settings outside the policy and the other permission traps are in
[pitfalls](pitfalls.md#permissions), each with what to do about it. Hooks never decide permissions: there is no
hook-based allow or deny.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| An allowed command still prompts in Claude Code | An `ask` or a `deny` also matches; or the folder is not trusted | Check the `policy` block in `AGENTS.md`; trust the folder |
| `/abs/path/cook lifecycle run deliver` or `sh cook lifecycle run deliver` runs without the prompt | Prefix rules match leading words; those spellings match none of the rendered ones | Expected: the `deliver` guards are enforced by the binary whatever the spelling. Keep agents on `./cook`, the form `AGENTS.md` teaches |
| A deny works in Claude Code but not in Codex | The entry is Claude-only: a bare string or a native rule string other than a plain `Bash` prefix | Express it as a prefix list too |
| `render` rejects an entry | A nested list is deeper than one level, or an entry is neither a list nor a string | Use one level of alternatives per word |
| The force-push deny names the wrong branch | The version-control recipe reports another default branch: with the git recipe, `origin/HEAD` points elsewhere or is unset | `git remote set-head origin --auto`, then `the-cook render` |
| `render` reports a dropped entry | A policy entry tries to remove or weaken a floor rule, such as an `allow` for a force push to the default branch | Remove the entry; the floor rule stays in force whatever the policy says |
| No `mise * ::: *` deny in the rendered output | The mise recipe is not in the profile's `recipes:` | `the-cook recipe apply mise`, then review the diff |
| An agent's `the-cook lifecycle run deliver` prompts every time | The seeded `ask` entry | Intended; move it to `allow` only if the `deliver` actions are safe to run unconfirmed |
