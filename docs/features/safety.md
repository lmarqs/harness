# Safety guidelines

`harness` lets agents work in a repository, attended or not, without handing them more than the work needs. This page
explains what it defends against, which layers do the defending, and how to configure a repository so the layers
hold: where to place a policy entry, what changes when nobody is watching, why lifecycle actions are code, where
secrets must not go, and what to read in a rendered diff. It ends with a checklist for unattended runs.

The traps behind many of these guidelines are described one by one in [pitfalls](pitfalls.md).

## Threat model

| Actor or input | Trust | Why |
| --- | --- | --- |
| A person | trusted; decides | owns the repository, the credentials and the merge |
| An interactive agent | an untrusted contributor, with a person present | acts on instructions and on everything it reads; a person sees its prompts |
| A coordinating agent left alone | an untrusted contributor, with nobody present | the same agent, without the person who would catch a wrong step |
| A Ralph worker | the least trusted | unattended, driven by task text, and the one that writes the code its own checks run |
| Task, issue and pull-request text | untrusted input | anyone who can write it can address the agent that reads it |
| Lifecycle actions, raw hooks, validators, lane commands | code | they run with the person's credentials; `harness` runs them, it does not judge them |
| An `extends` preset | code from another repository | its policy entries, hooks and actions reach every adopter at render |

What must stay safe:

- the default branch and its protection;
- production, cloud and repository settings;
- credentials in the environment;
- the team's record: issues, pull requests, reviews;
- the clone's shared state: other worktrees, the task tracker, local databases and containers;
- the rules themselves: the rendered permissions and hooks an agent runs under.

Outside the model: a person with commit access who means harm, a compromised agent binary, and anything the operating
system lets a process reach beyond what the agent's own permission system or sandbox controls. Permissions constrain
the commands an agent starts, not the code those commands run: an agent that can edit a test and run the test runner
runs code it wrote ([pitfalls](pitfalls.md#a-task-runner-runs-whatever-its-tasks-say)).

## Layers of defence

| Layer | Stops | Does not stop |
| --- | --- | --- |
| Native permissions rendered from the [policy](policy.md) | commands the policy denies or asks about, in every agent that renders them | commands no entry matches (the agent's default decides), spellings a prefix does not match, settings outside the policy |
| [Built-in invariants](concepts.md#built-in-invariants) | permission bypass in workers, force push to the default branch, task-runner chaining, completion without a replayed proof, delivery of unsafe work, lifecycle actions inside workers | anything a repository allows on purpose |
| The worker contract ([Ralph](ralph.md#the-worker-contract)) | bypass flags from any configuration, `worker.deny` commands, a worker committing or integrating its own work | commands `worker.deny` does not list |
| The tamper check | a worker editing rendered rules, owned settings keys or hook entries to widen its next attempt | edits to files `harness` does not own |
| Receipt replay ([verify](verify.md#who-relies-on-it)) | a worker claiming checks it did not run, or weakening the checks it is measured by | tests that pass without exercising the change |
| Lifecycle guards ([lifecycle](lifecycle.md#built-in-guards)) | delivery from the default branch or of uncommitted work and, when the verify module is enabled, of work without a valid receipt; actions run by a worker | what a declared action does once it runs |
| Review and branch protection (external) | anything reaching the default branch without a person's approval | anything that does not go through the default branch: production, cloud, other systems |

Hooks are not a layer: they give feedback and never decide permissions, and they do not run for a teammate without
the binary.

## Placing a policy entry

The decision order is the same in every agent: `deny`, then `ask`, then `allow`; an unlisted command falls to the
agent's own default ([policy](policy.md)).

```mermaid
flowchart TD
  c["A command an agent may run"] --> n{"Must it never run<br/>from an agent session?"}
  n -- yes --> d["deny"]
  n -- no --> w{"Must a person be in the loop<br/>every time, even unattended?"}
  w -- yes --> k["ask"]
  w -- no --> r{"Reversible, and its effects<br/>stay in the clone or on a branch?"}
  r -- yes --> l["allow"]
  r -- no --> u["leave unlisted<br/>whoever is present decides"]
```

| Decision | Put here | Examples |
| --- | --- | --- |
| `allow` | what is reversible, local or remote | `git` on branches, a branch push, a draft pull request, tasks whose effects stay in the clone, read-only MCP tools |
| `ask` | only what bypasses a gate a person must own, or what leaves the clone when you want a person in the loop | merging past branch protection; `harness lifecycle run deliver` (seeded by `scaffold`) |
| `deny` | what must never run from an agent session | publishing a package, `terraform apply`, a tracker's remote sync |
| unlisted | production, cloud and repository-settings writes | `kubectl apply`, `aws …`, `gh repo edit` |
| `worker.deny` | everything that leaves the worker's worktree before the worker wrapper verifies it | commits, pushes, refs and worktrees, `git -C`/`git -c`, the hosting platform's CLI, shared local services, production and cloud |

Why production stays unlisted rather than denied: a person at the terminal decides case by case; an unattended
Claude Code session in auto mode leaves it to its classifier, which judges it from the conversation; workers are denied
it through `worker.deny`. A `deny` would also stop the person who means it.

Write the reason above each group of entries. The reason is what a reviewer evaluates, and an entry broader than its
reason is a finding.

## Unattended runs

| Session | Runs as | What to give it |
| --- | --- | --- |
| Interactive | the mode the person chooses | the shared policy |
| Coordinating agent left alone | Claude Code with `--permission-mode auto`, or Codex in its sandbox | `allow` entries for everything the agreed delivery needs; the boundary of delivery stated by the person at the start (branch, pull request, CI trigger, auto-merge) |
| Ralph worker | pinned by the worker wrapper ([agents](agents.md#worker-launch)) | the shared policy plus `worker.deny` |

Two rules decide how an unattended session behaves:

- **An `ask` entry prompts even in auto mode**, and no `allow` from any settings scope overrides it. Every `ask` the
  run reaches stalls it until a person answers.
- **An `allow` entry skips the auto-mode classifier.** The command runs without judgement. A broad `allow`, such as
  `[git]`, lets a push to the default branch run directly: branch protection, not the agent, is then the gate.

So, before leaving a session alone: every `ask` it reaches is either acceptable as a stop or moved, and every
`allow` is one you accept with no judgement applied.

## The worker contract and the tamper check

A Ralph worker is the least trusted actor, so `harness` does not rely on its permissions alone:

1. The worker wrapper refuses permission-bypass flags from arguments and from inherited or global ralph-tui
   configuration, and names the key that injected one.
2. It pins the permission mode: Claude Code in `auto` with `worker.deny` as disallowed tools; Codex in the
   `workspace-write` sandbox with `worker.deny` in the rendered rules.
3. Before every agent launch it restores the tool-owned files (rendered rules, owned settings keys, hook entries)
   from the session's base commit. After the agent exits it compares them; any difference fails the attempt,
   withholds completion and is reported.
4. It reads the profile, the policy and the hooks file from the base commit, so the checks it replays are the ones
   the run started with.
5. It replays the lanes and skip reasons the agent's receipt selected. The receipt lives in the worker's writable
   tree, so it only selects; the replay is the proof.
6. The agent never commits or integrates its own work: the worker wrapper (sequential mode) or ralph-tui's own
   integration (parallel mode) lands the verified commit on the run branch. The launcher fires lifecycle events and
   runs their actions in its own process, never inside the worker, reading the actions from the base commit.

What remains yours: `worker.deny` must list what leaves the worktree, and the tasks must not ask for anything the
worker cannot be trusted with. A change to `.agents/` reaches a run only through a commit a person made before
launching it.

## Lifecycle actions are code

Lifecycle actions ([lifecycle](lifecycle.md)) run as child processes of `harness`, with the environment of the process
that fired them, outside the agent's per-command permission checks.

- **Permitting an event permits its actions.** An agent allowed to run `harness lifecycle run deliver` runs every
  `deliver` action, including commands the policy denies to the agent itself.
- **Permitting a trigger point permits its automatic actions.** An agent allowed to run `harness task complete` fires
  every `completed` and `deliver` action with `auto: true`; the same holds for `harness verify`,
  `harness worktree create`, `harness task claim`, `harness worktree remove`, `harness doctor` and `harness ralph`.
- **Actions from a preset run with your credentials.** Pin presets with `@<ref>` and review a bump like a dependency
  update.

What follows:

- Review `lifecycle` changes in `.agents/hooks.yaml` and in presets like code.
- Keep the seeded `ask` on `[harness, lifecycle, run, deliver]` until the actions are ones you accept an agent running
  unasked. Moving it to `allow` is a decision about the actions, not about the command.
- Check `harness lifecycle list` for `auto: true` before granting an agent a command with a trigger point.
- Keep actions to delivery. An action never writes the team's record and never touches production.

## Secrets

- Never write a secret in the profile, the policy, the hooks file, a preset or an action. They are committed, rendered
  into files every clone has, and shown in diffs.
- Actions, hooks, validators, lanes and agents inherit the environment. Start unattended runs from a shell that holds
  only the credentials the run needs: no production tokens, no cloud administrator profile.
- `harness lifecycle run --dry-run` prints only the `HARNESS_*` variables, and `.work/lifecycle.jsonl` holds no
  environment values.
- A command that looks local can follow an environment file to a remote system, such as a migration whose `.env`
  points at a shared database. Check what the environment files point at before allowing such a command
  ([pitfalls](pitfalls.md#a-local-command-follows-an-environment-file-to-a-remote-system)).
- MCP server definitions, which often carry tokens, are not managed by `harness`. Keep them in each person's own
  settings.

## Importing personal scopes

`harness scaffold --adopt --import local,user` reads `.claude/settings.local.json`, `.codex/*.local.toml`,
`~/.claude/settings.json` and `~/.codex/rules/` ([scaffold](scaffold.md#adopt)). What it imports becomes committed
and shared with everyone who clones the repository.

Personal settings accumulate entries nobody chose for a team:

- exact commands approved once, which can contain a token, a host name or a personal path;
- broad allows a person accepted for convenience;
- rules for tools only that person has.

Every entry from these scopes is marked for review in the to-do. Run with `--dry-run` first, read each marked entry,
delete what is personal, and keep only what you would have written for the team. The source files are only read; the
person's own settings keep applying to them.

## Reviewing rendered diffs

Rendered files are committed so every permission change is a diff. After `harness render`, `render --refresh` or a
preset bump, read:

| File | Look for |
| --- | --- |
| `.claude/settings.json` (`permissions`, hook entries) | new `allow` entries and their breadth; entries that left `deny` or `ask`; new hook commands |
| `.codex/rules/harness.rules` | the same decisions as Claude Code; a deny present for Claude Code but missing here is Claude-only |
| `.codex/hooks.json`, `.codex/config.toml` | hook entries and the keys that enable them |
| The `policy-summary` block in `AGENTS.md` | Claude-only entries; the built-in base |
| `.agents/hooks.yaml` and the preset's `hooks.yaml` | new or changed `lifecycle` actions, especially `auto: true` |
| `.agents/.harness/manifest.json` | a preset's recorded commit moving: read the upstream diff between the two commits |

Add `harness render --check` to CI ([render](render.md#drift-check)) so a hand edit to a rendered file fails review
instead of shipping.

## Checklist before running unattended

- [ ] Everything is committed, `.agents/` changes included; `harness render --check` exits 0.
- [ ] `harness doctor` reports no finding: tool versions, bypass settings, drift.
- [ ] The folder is trusted by the agent; the run's worktrees are nested in the primary checkout.
- [ ] Settings outside the policy are known: `settings.local.json`, user and managed settings neither widen nor
      narrow what the run needs.
- [ ] Every `ask` entry the run reaches is a stop you want.
- [ ] Every `allow` entry is one you accept with no judgement applied.
- [ ] `worker.deny` lists commits, pushes, refs and worktrees, `git -C`/`git -c`, the hosting platform's CLI, shared
      local services, production and cloud.
- [ ] `harness lifecycle list` shows only automatic actions you accept, for the scopes the run reaches.
- [ ] The shell holds no production or administrator credentials; environment files point at local services.
- [ ] Tasks are scoped (`--epic`), self-contained, and their text was written or read by a person.
- [ ] Parallel workers do not share ports, databases or containers they would collide on.
- [ ] Delivery is decided before launch: automatic per run, or after judgement by a coordinating agent, and the
      boundary is stated (branch, pull request, CI trigger, auto-merge).
- [ ] The iteration budget is set.
