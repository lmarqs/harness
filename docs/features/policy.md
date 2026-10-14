# Policy: `.cook/policy.yaml`

## Objective

**Problem.** A team decides what coding agents and other programs in a repository may do on their own, what needs a
person's confirmation first and what must never happen, but every coding agent reads its permissions in its own
format. Kept by hand in each format, the rules drift: one coding agent runs what another is refused, a limit such as
never force-pushing the default branch disappears in an edit, and unattended runs stall on a question nobody answers or
do what nobody allowed. A team that already wrote rules by hand can lose one when it changes how they are kept, and
nobody notices until it matters.

**Goal.** Every worker in a repository works under the same reviewed permissions, as far as its program can enforce
them, every rule the team had already written carries over, and the limits that must always hold stay in force
whatever anyone edits.

## Overview

The policy says what [workers](workers.md) may do without asking (`allow`), only after a person confirms (`ask`), or
never (`deny`), plus what is denied only to [attempts](attempts.md#the-attempt-contract), the unattended runs
`the-cook attempt`, `attempt` actions and the `attempt` steps of [recipes](recipes.md#step-kinds) start. It is written
once, neutrally where the workers allow it, and each worker's [ingredient](workers.md#the-worker-contract) renders it
into that program's native permission format. The [floor](#the-floor) is always added and cannot be removed or weakened:
the floor rules of the ingredients in use, such as a version-control ingredient's deny of a force push to the default
branch, and the binary's own entries for its commands.

Jump to [field reference](#reference), [examples](#examples), [guidelines](#guidelines),
[troubleshooting](#troubleshooting).

Commands are named `the-cook <command>` on this page. Run them through the repository's wrapper,
`./cook <command>`, from the root of a checkout; with a global install, `the-cook <command>` is equivalent
([wrapper](wrapper.md)).

## When to edit it

- Workers keep asking for a command that is local and reversible: add it to `shared.allow`.
- A command must never run from a worker's session: add it to `shared.deny`.
- A person must confirm something every time, even in unattended sessions: add it to `shared.ask`, knowing it costs a
  prompt every time.
- Attempts must not run something that interactive sessions may: add it to `unattended.deny`.
- A tool other than the shell needs a rule, such as a read-only MCP tool or a web domain: add a native rule string, in
  the syntax of a worker whose ingredient declares native rule patterns, under the decision it needs.

Then run `the-cook render` and review the diff of the rendered files.

## How it works

### Sources and precedence

The effective policy is the union of:

1. the floor ([below](#the-floor)): the floor rules of every ingredient in the profile's `ingredients:` and the
   binary's own entries for its commands;
2. allow entries for the commands of each enabled [module](profile.md#modules), rendered for each spelling `the-cook`
   renders ([below](#entries-for-the-cook-commands));
3. the entries in `.cook/policy.yaml`, including those a station's entries added when it was applied, which are the
   repository's own from then on ([stations](stations.md#entries)).

Nothing else contributes an entry: no file outside the repository is read
([ADR 0013](../adrs/0013-organization-content.md)). Lists are unions: no source removes an entry from another, and
nothing removes a floor rule. An entry in the policy file that would weaken a floor rule, such as an `allow` for a force
push to the default branch, is not a validation error. `render` drops it from the output, renders the floor rule and
reports the entry. [`doctor`](doctor.md) reports it as a finding until it is removed. When a command matches entries
with different decisions, the decision
is taken in this order, in every worker:

```text
deny  >  ask  >  allow  >  (unlisted: the worker's own default)
```

A command no entry matches falls to the worker's default behaviour, which depends on the program and its mode: a
coding agent may prompt, let a classifier decide from the conversation, or run the command in a sandbox and ask to
leave it. Whether a matching `ask` still prompts in the program's unattended mode, and whether a matching `allow` skips
the program's own judgement, are properties of the program too. The worker ingredient's page in its cookbook states all
three, and [safety](safety.md#unattended-runs) explains what they mean for an unattended run.

### Entry forms

| Form | Example | Matches | Rendered for |
| --- | --- | --- | --- |
| 1. Prefix list | `[git, push]` | every shell command whose leading words are `git push` | every worker whose ingredient renders permissions |
| 1. Prefix list with alternatives | `[gh, pr, [view, list, diff]]` | `gh pr view …`, `gh pr list …`, `gh pr diff …` | every worker whose ingredient renders permissions |
| 2. Native rule string | `"Read(./secrets/**)"`, `"WebSearch"`, `"mcp__acme_observability__search_*"` | what the program's own rule syntax matches, globs included | each worker whose ingredient declares a `native_rules` pattern the string matches, verbatim or converted as its renders say; no other worker |
| 3. Bare string | `"gh pr merge *--admin*"` | a whole shell command, where `*` matches anything anywhere | each worker whose program matches whole shell commands by glob, as its ingredient's renders say; no other worker |

A nested list is a set of alternatives for one word, one level deep. Use quotes for words YAML would otherwise parse,
such as `"db:restore"` or `"-f"`.

The binary knows no program's rule syntax, so the worker ingredients say which strings are native rule strings. Each
worker ingredient declares, in the `native_rules.patterns` item of the
[worker contract](workers.md#the-worker-contract), the regular expressions its program's own rule strings match, in RE2
syntax (that of Go's `regexp` package), each against the whole string. A string is a native rule string (form 2) for
every declared worker whose ingredient has a pattern it matches. A string that matches no pattern of any declared
worker's ingredient is a bare string (form 3), and a string that matches one is never a bare string, for any worker.
Declaring or removing a worker can therefore change a string's form; the [`policy` block](#the-convention-in-agentsmd)
shows the result.

For example, the `claude` ingredient of `github:acme/cookbook` declares patterns for its program's tool rules and MCP
tool names, so `"Read(./secrets/**)"`, `"WebSearch"` and `"mcp__acme_observability__search_*"` are native rule
strings for a `claude` worker, while `"gh pr merge *--admin*"` matches none of its patterns and is a bare string.
`./cook ingredient show <name>` prints an ingredient's patterns.

### How entries render

Each worker ingredient's [renders](ingredients.md#renders) write the entries into its program's native permission
file; its page in its cookbook shows the exact output:

| Policy | Rendered by each worker ingredient as |
| --- | --- |
| prefix list under `shared.allow`, `shared.ask`, `shared.deny` | a rule with the program's allow, ask or deny decision, one per combination of alternatives |
| native rule string | for a worker whose ingredient's pattern it matches, the rule verbatim, or converted into the program's own form; otherwise not rendered for that worker |
| bare string | a whole-command glob rule, when the program has one; otherwise not rendered for that worker |
| `unattended.deny`, when the ingredient passes it as launch arguments | as launch arguments `the-cook attempt` adds, and in the deny rules when the worker's role is `unattended` |
| `unattended.deny`, when the ingredient passes it only through rendered rules | only in the rendered deny rules, when the worker's role is `unattended` |
| `unattended.deny`, when the ingredient does neither | the worker cannot run attempts: `the-cook attempt` and `attempt` actions refuse it with exit 3 |

Every entry a worker does not enforce is listed as such in the [`policy` block](#the-convention-in-agentsmd) of
`AGENTS.md`, so a reader sees which rules each worker leaves out. See [workers](workers.md#roles) for what roles
change.

### Entries for `the-cook` commands

A worker can invoke a `the-cook` command in several spellings: `./cook <command>`, the form `AGENTS.md` teaches;
`the-cook <command>` with a global install; and `cook <command>` when the repository root is on `PATH`. An entry for
a `the-cook` command names the program once, as `the-cook`:

```yaml
- [the-cook, lifecycle, run, deliver]
```

`render` writes such an entry for each rendered spelling: `the-cook …`, `./cook …` and `cook …`. The binary's
floor entry for its own commands, the module entries and the [seeded entry](#the-seeded-entry) are written the same way.

A prefix rule still matches only leading words, so an absolute path to the wrapper, or `sh cook …`, matches none of
the rendered spellings ([pitfalls](pitfalls.md#the-wrapper-is-a-second-spelling-of-every-the-cook-command)). The guards
that matter therefore never rest on these entries: the binary itself refuses `the-cook lifecycle run` inside an
attempt's worker, and it enforces the [`deliver` guards](lifecycle.md#built-in-guards) whoever runs the command. The
entries are defence in depth: they make a worker stop or ask before it runs the command at all.

### The floor

The floor holds the rules no repository may lose. An ingredient marks the facts about its program that the
safety invariants depend on as floor ([ingredients](ingredients.md#the-floor)); the
binary adds entries for its own commands. The floor is never written in `.cook/policy.yaml`: `render` adds it to every
worker's output on every render, as long as the ingredient is in the profile's `ingredients:`, and shows it in the
`policy` block of `AGENTS.md` with the ingredient each rule comes from.

| Rule | From | Entries |
| --- | --- | --- |
| Whatever an ingredient marks as floor about its program, such as no force push to the default branch | the floor rules of each ingredient in use, here a version-control ingredient | `shared.deny` or `unattended.deny` entries the ingredient declares, with values the ingredient's operations report, such as the default branch the version-control ingredient reports |
| Lifecycle actions never run inside an attempt's worker | the binary | `unattended.deny`: `[the-cook, lifecycle]`, rendered for each spelling `the-cook` renders. The binary refuses `the-cook lifecycle run` inside the worker on its own; `the-cook attempt` fires `completed` and `deliver` in its own process ([lifecycle](lifecycle.md#built-in-guards)) |
| Attempts never bypass permissions | the floor bypass arguments of each worker ingredient | not a policy entry: `the-cook attempt`, `attempt` actions and recipes' `attempt` steps refuse them among the arguments they receive; see [attempts](attempts.md#the-attempt-contract) |

Every floor rule an ingredient marks comes from the cookbook the repository pinned it from; a reviewer reads them with
`./cook ingredient show <name>` before approving the pin. A floor rule exists only while its ingredient is pinned in the
profile's `ingredients:`: a repository that uses a program without its ingredient has none of that ingredient's floor
rules.

Organization choices are not in the floor: denying the remote sync of a [task tracker](tasks.md), allowing deploy status
tasks, MCP read rules. They belong in each repository's policy file, written there by a person, by a worker, or by the
entries of a station the repository applied, and reviewed like any other change ([stations](stations.md)).

### The seeded entry

`the-cook scaffold` seeds every new policy with one repository entry:

```yaml
ask:
  # Delivery runs the repository's lifecycle actions, outside the worker's own permission checks.
  - [the-cook, lifecycle, run, deliver]
```

`render` writes it for each spelling `the-cook` renders. Permitting a worker to run `the-cook lifecycle run deliver`
permits everything the `deliver` actions do: they run as child processes of `the-cook`, where the worker's per-command
permissions do not reach. The seeded `ask` keeps a person in the loop before delivery. It is a seeded entry, which the
repository owns and may change; it is not an invariant ([ADR 0015](../adrs/0015-development-lifecycle.md)). A repository
whose `deliver` actions only open draft pull requests may move it to `allow`. Lifecycle actions are committed
configuration; review them like code ([lifecycle](lifecycle.md), [safety](safety.md)).

### Adoption

`the-cook scaffold --adopt` imports existing permission rules into this file: the native keys each declared worker's
ingredient names for [native rule import](workers.md#the-worker-contract) and, with `--import local` or
`--import user`, personal settings. Each imported entry keeps its decision and carries a provenance comment naming its
source, such as `# imported from <file> (project)`, and an equivalence check proves no project rule was lost. See
[scaffold](scaffold.md#adopt).

### The convention in `AGENTS.md`

`the-cook render` writes the effective permissions, in plain language, into the `policy` block of `AGENTS.md`: the
decision order, then every entry grouped by decision, with each floor rule marked with the ingredient it comes from and
each entry a worker does not enforce marked with that worker's name.

`policy` is a convention block ([concepts](concepts.md#conventions-outlive-the-tool)): it describes the permissions the
workers' native settings enforce, and stays true when `the-cook` is gone. Entries for `the-cook`'s own commands are
therefore left out of it, since those commands do not exist after an eject
(`the-cook remove --eject`). `./cook explain policy` prints the complete effective policy, those entries included.
For the [Node API](#a-node-api) below, with every module on and workers `claude` and `codex`, where only the `claude`
ingredient declares native rule patterns and renders bare strings:

```markdown
<!-- cook:policy -->
Workers in this repository work under these permissions. A denied command never runs, an asked one waits for a person,
an allowed one runs without a prompt; deny wins over ask, and ask over allow. A command no entry matches is left to
the worker's own default. An entry marked *not enforced in* a worker is left out of that worker's rules.

Denied:
- force pushes to `main`: `git push --force origin main` and its other spellings (*floor rule of the git ingredient*)
- `npm publish`
- `Read(./.env*)` (*not enforced in codex*)

Ask a person first:
- `gh pr merge *--admin*` (*not enforced in codex*)

Allowed:
- `git`
- `gh pr checkout|checks|comment|create|diff|edit|list|ready|view`
- `gh run list|view|watch`
- `npm run build|lint|test|test:e2e|typecheck`
- `mcp__acme_observability__search_*` (*not enforced in codex*)

Denied to unattended runs, in addition:
- `git am|cherry-pick|commit|merge|push|rebase|reset|stash|tag|update-ref|worktree`
- `git -C`, `git -c`
- `gh`
<!-- /cook:policy -->
```

The block states the permissions as facts. After `the-cook remove --eject` the markers go and the
text stays. The rendered permissions stay in each worker's settings as the repository's own, so workers keep working
under the same rules and the block keeps describing them. From then on nothing renders them, so a change to one
settings file has to be made by hand in the other worker's file and in this text.

## Reference

| Field | Type | Default | Meaning |
| --- | --- | --- | --- |
| `schema` | integer | required | `1` |
| `shared.allow` | list of entries | `[]` | Run without a prompt, in every session |
| `shared.ask` | list of entries | `[]` | Always ask a person first |
| `shared.deny` | list of entries | `[]` | Never run |
| `unattended.deny` | list of entries | `[]` | Never run in an attempt (`the-cook attempt`, an `attempt` action or a recipe's `attempt` step), on top of `shared` |

`scaffold` seeds the file with the structure above, an empty `allow` and `deny`, and
[the seeded entry](#the-seeded-entry) in `ask`.

## Examples

### A Node API

The repository pins its `git`, `claude` and `codex` ingredients from `github:acme/cookbook`
([workers](workers.md#declaring-workers)).

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
unattended:
  deny:
    # An attempt's result is verified after the worker exits, then committed by the-cook attempt --commit.
    # Anything that leaves the attempt's workspace before that cannot be checked or undone.
    - [git, [am, cherry-pick, commit, merge, push, rebase, reset, stash, tag, update-ref, worktree]]
    - [git, ["-C", "-c"]]
    - [gh]
    - [the-cook, workspace]
```

`Read(./.env*)` and the MCP rule match a native rule pattern of the `claude` ingredient, and the bare string is
rendered only for the worker whose program matches whole commands by glob; the `policy` block lists all three as not
enforced in `codex`.

## Guidelines

Placing an entry:

- **allow** what is reversible, local or remote: a commit, a branch push, a pull request, a comment, tasks whose
  effects stay in the clone.
- **ask** only before bypassing a merge gate a person must own, such as merging past branch protection, or before
  actions that leave the clone when you want a person in the loop, such as `the-cook lifecycle run deliver`. In a
  worker whose `ask` prompts in its unattended mode, each `ask` costs a prompt in every unattended run.
- **deny** only what must never run from a worker's session.
- **leave unlisted** production, cloud and repository-settings writes: whoever is present decides, an unattended
  session's own default handles them, and attempts can be denied them through `unattended.deny`.

More:

- Do comment each group of entries with the reason; the reason is what a reviewer evaluates.
- Do write an organization's entries into each repository's policy through a reviewed change, for example by a coding
  agent following a skill the organization publishes, such as one installed from `acme/agent-skills`, or by applying a
  station from the organization's cookbook: each repository reviews them, and they are readable where they apply
  ([stations](stations.md)).
- Do express a rule as a prefix list whenever every worker must enforce it.
- Do write an entry for a `the-cook` command once, as `[the-cook, …]`; `render` writes it for each spelling it renders,
  and the binary's own guards catch the others.
- Do allow workers only the recipes you have read, by name, such as `[the-cook, make, migration]`: a recipe's `run`
  steps run as child processes of `the-cook`, outside the worker's per-command checks, like lifecycle actions
  ([pitfalls](pitfalls.md#permitting-the-cook-make-permits-its-steps)).
- Don't use `allow` entries broader than the reason you wrote for them.

[Safety guidelines](safety.md) explains the threat model behind these rules.

## Known gaps

Prefix matching, entries a worker does not enforce, settings outside the policy and the other permission traps are in
[pitfalls](pitfalls.md#permissions), each with what to do about it. Hooks never decide permissions: there is no
hook-based allow or deny.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| An allowed command still prompts in a worker | An `ask` or a `deny` also matches; or the program does not trust the folder | Check the `policy` block in `AGENTS.md`; trust the folder |
| `/abs/path/cook lifecycle run deliver` or `sh cook lifecycle run deliver` runs without the prompt | Prefix rules match leading words; those spellings match none of the rendered ones | Expected: the binary enforces the `deliver` guards whatever the spelling. Keep workers on `./cook`, the form `AGENTS.md` teaches |
| A deny works in one worker but not in another | The entry is a bare string the other worker's program cannot match, or a native rule string only the first worker's ingredient declares a pattern for; the `policy` block marks it | Express it as a prefix list too |
| A string meant as a shell command is marked as a native rule string | It matches a `native_rules` pattern of a declared worker's ingredient | Write it as a prefix list |
| `render` rejects an entry | A nested list is deeper than one level, or an entry is neither a list nor a string | Use one level of alternatives per word |
| The force-push deny names the wrong branch | The version-control ingredient reports another default branch | Set the default branch the version control program reports, as the ingredient's page explains, then `the-cook render` |
| `render` reports a dropped entry | A policy entry tries to remove or weaken a floor rule, such as an `allow` for a force push to the default branch | Remove the entry; the floor rule stays in force whatever the policy says |
| A floor rule you expect is missing from the rendered output | Its ingredient is not pinned in the profile's `ingredients:` | `the-cook ingredient pin <name> --cookbook <source>@<version>`, then review the diff |
| A worker's `the-cook lifecycle run deliver` prompts every time | The seeded `ask` entry | Intended; move it to `allow` only if the `deliver` actions are safe to run unconfirmed |
