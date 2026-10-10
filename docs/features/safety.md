# Safety guidelines

## Objective

**Problem.** An agent acts on everything it reads, including task and issue text anyone can write, and runs commands
with the credentials of the person who started it. A developer who lets agents work in a repository, above all
unattended, has to keep the default branch, production systems, credentials, shared local state and the agents' own
rules out of their reach. Each available protection, from an agent's permission system to code review, stops some of
this and misses the rest, so a setup that looks safe can still leave one of these open.

**Goal.** Before leaving agents alone in a repository, a developer knows what protects each thing that must stay safe
and what that protection does not stop, and every gap left open is one they chose.

## Overview

`the-cook` lets agents work in a repository, attended or not, without handing them more than the work needs. The
guidelines cover what it defends against, which layers do the defending, and how to configure a repository so the layers
hold. That configuration covers where to place a policy entry, what changes when nobody is watching, why lifecycle
actions are code, what an `agent` action may do, where [standing context](context.md) may come from, where secrets must
not go, and what to read in a rendered diff. A checklist for unattended runs closes the page.

[Pitfalls](pitfalls.md) describes the traps behind many of these guidelines one by one.

Commands are named `the-cook <command>` on this page. Run them through the repository's wrapper,
`./cook <command>`, from the root of a checkout; with a global install, `the-cook <command>` is equivalent
([wrapper](wrapper.md)).

## Threat model

| Actor or input | Trust | Why |
| --- | --- | --- |
| A person | trusted; decides | owns the repository, the credentials and the merge |
| An interactive agent | an untrusted contributor, with a person present | acts on instructions and on everything it reads; a person sees its prompts |
| A coordinating agent left alone | an untrusted contributor, with nobody present | the same agent, coordinating work such as an unattended [worker](worker.md) run and its delivery, without the person who would catch a wrong step |
| A worker: the agent `the-cook worker` starts | the least trusted | unattended, driven by task text, and the one that writes the code its own checks run |
| A loop runner and its configuration | the person's tool | `the-cook` has no code for it and reads none of its configuration. When its agent command is `the-cook worker`, the worker contract holds whoever calls it; any other agent command is outside `the-cook` |
| Task, issue and pull-request text | untrusted input | anyone who can write it can address the agent that reads it |
| Lifecycle actions, notification actions, raw hooks, validators, lane commands | code | they run with the person's credentials; `the-cook` runs them without judging them |
| An `agent` action | an unattended agent | it runs headless on a prompt, often with values written by people and agents in it; it runs under the [worker contract](worker.md#the-worker-contract) |
| Standing context: the skills and files `context` entries name | instructions | every matching session reads them as guidance; whoever can change them steers the agents |
| `COOK_MESSAGE`, `COOK_REASON`, task titles and summaries | untrusted input | agents and task text shape them; they reach notifications and `agent` prompts |
| A [recipe](recipes.md), pinned from a cookbook | data from an outside source, pinned in the profile by version and sha256 | pinning trusts the cookbook's `checksums.txt` as downloaded over HTTPS at that moment, and the pin enters the repository only through a reviewed diff; from then on every archive is verified against its pin on download and on every load, and no hook or worker attempt fetches one. Its operations, renders and floor are what its author wrote, and no release key vouches for them: reading the recipe (`the-cook recipe show`) before approving the pin is the trust decision for everything it runs. Its entries write commands, lanes, validators and post-edit hooks into the repository's files, which run them with the credentials of whoever triggers them; `apply` shows the additions as a diff before writing |
| The pinned release: `cook` and `.cook/wrapper.yaml` | code fetched from the network | the wrapper downloads the release the pin names and runs it with your credentials; the checksums committed next to the pin are what make the download trustworthy |

What must stay safe:

- the default branch and its protection;
- production, cloud and repository settings;
- credentials in the environment;
- the team's record: issues, pull requests, reviews;
- the clone's shared state: other workspaces, the task tracker, local databases and containers;
- the rules themselves: the rendered permissions and hooks an agent runs under, the standing context it is given, and
  the pinned binary that enforces them;
- secrets used by actions, such as webhook URLs and tokens.

Outside the model: a person with commit access who means harm, a compromised agent binary, anything the operating system
lets a process reach beyond what the agent's own permission system or sandbox controls, and whatever a person runs
outside `the-cook`. A loop runner configured with another agent command, a team's own wrapper around a loop runner, or
an agent started directly runs without the worker contract; that is the person's choice, and `the-cook` neither detects
nor prevents it. The guarantees on this page hold for what `the-cook` renders and for every agent `the-cook` starts.
Permissions constrain the commands an agent starts, not the code those commands run: an agent that can edit a test and
run the test runner runs code it wrote ([pitfalls](pitfalls.md#a-task-runner-runs-whatever-its-tasks-say)).

## Layers of defence

| Layer | Stops | Does not stop |
| --- | --- | --- |
| Native permissions rendered from the [policy](policy.md) | commands the policy denies or asks about, in every agent that renders them | commands no entry matches (the agent's default decides), spellings a prefix does not match, settings outside the policy |
| Invariants in the binary ([concepts](concepts.md#built-in-invariants)) | completion without a replayed proof, delivery of unsafe work, lifecycle actions inside workers, `agent` actions outside the worker contract, a worker started on recipes resolved from its own checkout | anything a repository allows on purpose |
| Floor rules of the recipes in use ([policy](policy.md#the-floor)) | permission bypass in workers (each harness recipe's bypass arguments), force push to the default branch (a version-control recipe), command chaining a task runner offers (a task-runner recipe), whatever a policy entry tries to weaken them with | a program whose recipe is not in the profile's `recipes:`; `doctor` reports one it detects |
| Guards in the binary ([spellings](#guards-live-in-the-binary)) | `the-cook lifecycle run` inside a worker, unsafe `deliver`, whatever spelling of the command an agent uses | a command `the-cook` does not run |
| The worker contract ([worker](worker.md#the-worker-contract)) | the floor bypass arguments of the agent's harness recipe among the arguments `the-cook worker` receives, whoever calls it; `worker.deny` commands; a worker committing or integrating its own work; a loop runner reading a failed attempt as done | commands `worker.deny` does not list; an agent started without `the-cook worker` |
| The tamper check ([below](#the-worker-contract-and-the-tamper-check)) | a worker editing rendered rules, owned settings keys, hook entries, `cook`, `.cook/wrapper.yaml`, any file under `.cook/` or a file or skill `context` names, to widen its next attempt or rewrite its rules | edits to other files, which the replay and review judge |
| Receipt replay ([verify](verify.md#who-relies-on-it)) | a worker claiming checks it did not run, or weakening the checks it is measured by | tests that pass without exercising the change |
| Lifecycle guards ([lifecycle](lifecycle.md#built-in-guards)) | delivery from the default branch or of uncommitted work and, when the verify module is enabled, of work without a valid receipt; completion of a task without a valid receipt; actions run by a worker's agent | what a declared action does once it runs |
| The pinned, verified binary ([wrapper](wrapper.md)) | a download that does not match the checksums committed in the repository; a pin of a release its publisher did not sign; an agent hook fetching code; two versions of `the-cook` in one repository | a compromised signing key; `COOK_BINARY`, which bypasses the pin by design |
| Committed context sources ([context](context.md#sources-and-trust)) | context fetched when a hook runs; a worker editing its own instructions between attempts | a skill or file a reviewer accepted without reading |
| Review and branch protection (external) | anything reaching the default branch without a person's approval | anything that does not go through the default branch: production, cloud, other systems |

Hooks are not a layer: they give feedback, notify and inject context, never decide permissions, and skip on any
machine where the pinned binary is not cached yet.

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
| `allow` | what is reversible, local or remote | version control on branches, a branch push, a draft pull request, tasks whose effects stay in the clone, read-only MCP tools |
| `ask` | only what bypasses a gate a person must own, or what leaves the clone when you want a person in the loop | merging past branch protection; `the-cook lifecycle run deliver` (seeded by `scaffold`) |
| `deny` | what must never run from an agent session | publishing a package, applying infrastructure changes, a tracker's remote sync |
| unlisted | production, cloud and repository-settings writes | a cluster deploy, a cloud CLI write, a repository-settings change through the hosting platform's CLI |
| `worker.deny` | everything that leaves the worker's workspace before `the-cook worker` verifies it | commits, pushes, refs and workspaces, version-control options that act on another checkout or override its configuration, the hosting platform's CLI, a tracker command an action runs after the replay (closing a task), shared local services, production and cloud |

Production stays unlisted rather than denied because a `deny` would also stop the person who means it, such as a
developer who asks the agent to run a planned deploy. Each kind of session handles it in its own way. A person at the
terminal decides case by case. An unattended session falls to its agent's own default for unlisted commands, which its
harness recipe's page describes, such as a classifier that judges the command from the conversation. Workers are
denied it through `worker.deny`.

Write the reason above each group of entries. The reason is what a reviewer evaluates, and an entry broader than its
reason is a finding.

### Guards live in the binary

An agent can invoke `the-cook` as `./cook <command>`, `the-cook <command>` or `cook <command>`. A policy entry for a
`the-cook` command is written once, `[the-cook, lifecycle, run, deliver]`, and `render` writes it for every one of those
spellings ([policy](policy.md#entries-for-the-cook-commands)). A prefix rule still matches leading words only, so an
absolute path to the wrapper or `sh cook …` matches none of them.

The guards that matter therefore never rest on policy entries. The binary refuses `the-cook lifecycle run` inside a
worker's agent on its own: `the-cook worker` and `agent` actions set `COOK_WORKER=1` for the agent they start, and the
binary checks it. The binary also enforces the `deliver` guards whoever runs the command and however it is spelled. The
entries are defence in depth: they make an agent stop or ask before it runs the command at all
([ADR 0011](../adrs/0011-safety-invariants.md)).

## Unattended runs

| Session | Runs as | What to give it |
| --- | --- | --- |
| Interactive | the mode the person chooses | the shared policy |
| Coordinating agent left alone | its agent's unattended mode, as its harness recipe's page describes it | `allow` entries for everything the agreed delivery needs; the boundary of delivery stated by the person at the start (branch, pull request, CI trigger, auto-merge) |
| Worker | pinned by `the-cook worker` ([agents](agents.md#worker-launch)) | the shared policy plus `worker.deny` |
| Loop runner | its own process, started by a person, a coordinating agent or a script | a configuration whose agent command is `./cook worker` for every agent ([how a loop runner calls it](worker.md#how-a-loop-runner-calls-it)) |

Two properties of the agent decide how an unattended session behaves; its harness recipe's page in its cookbook states
both:

- **Whether an `ask` entry still prompts in its unattended mode.** Where it does, every `ask` the run reaches stalls it
  until a person answers.
- **Whether an `allow` entry skips the agent's own judgement.** Where it does, the command runs without judgement. A
  broad `allow` for the version control program lets a push to the default branch run directly: branch protection,
  not the agent, is then the gate.

Before leaving a session alone, check that every `ask` it reaches is either acceptable as a stop or moved, and that
every `allow` is one you accept with no judgement applied.

For example, a coordinating session told to open a pull request when the run ends, in an agent whose `ask` entries
prompt in its unattended mode, reaches the seeded `ask` on `[the-cook, lifecycle, run, deliver]` and waits there until
someone answers. Either that stop is what you want, or the
entry moves to `allow` once you accept every `deliver` action running unasked
([lifecycle actions are code](#lifecycle-actions-are-code)).

## The worker contract and the tamper check

A worker is the least trusted actor, so `the-cook` does not rely on its permissions alone. The contract is enforced
inside `the-cook worker`, so it holds whoever calls it: a loop runner, a script or a person.

1. Before anything else runs, `the-cook worker` resolves the version-control recipe and the agent's harness recipe
   from the verified cache, never from the checkout, and uses them for every read, comparison, reset and launch of the
   attempt. It never fetches: a declared recipe that is not cached exits 3.
2. It refuses to start with uncommitted changes, and records the base: the head commit of the checkout, as the
   version-control recipe reports it. A base that cannot be read fails the attempt before the agent starts.
3. It refuses the bypass arguments the agent's harness recipe marks as floor among the arguments it receives, with
   exit 1, naming the argument. It reads no loop runner's configuration: a bypass setting there surfaces as a refused
   argument at the first task.
4. It pins the permission mode with the harness recipe's launch arguments, and applies `worker.deny` as the recipe
   passes it: as launch arguments, or in the rendered rules the tamper check keeps intact.
5. Before the agent starts it restores the tool-owned files (rendered rules, owned settings keys, hook entries, the
   wrapper `cook` and its pin `.cook/wrapper.yaml`) from the base commit. After the agent exits it compares
   with the base those files, every file under `.cook/` and every file and skill a `context` entry names; any
   difference fails the attempt, withholds completion and is reported by path.
6. It reads the profile, the policy, the hooks file and the [standing context](#standing-context) from the base
   commit, so the checks it replays and the instructions the agent receives are the ones committed there.
7. It replays the lanes and skip reasons the agent's receipt selected. The receipt lives in the worker's writable
   tree, so it only selects; the replay is the proof.
8. The agent never commits or integrates its own work: `the-cook worker --commit` commits a verified attempt in its
   checkout, and a loop runner that runs tasks in parallel merges those commits itself. In a workspace bound to a task,
   `the-cook worker` fires `completed` and `deliver` in its own process after the agent exited, with the actions read
   from the base commit.
9. On a failed attempt it saves the attempt's changes as `last-attempt.patch` and its output as `last-worker-output`
   in the checkout's state directory, returns the checkout to the base commit, and removes the completion signal it
   was given with `--withhold` from the output. A loop runner never reads a failed attempt as done, and the next
   attempt starts from files the worker checked, `./cook` included.

What remains yours: `worker.deny` must list what leaves the workspace, and the tasks must not ask for anything the
worker cannot be trusted with. A change to the rules reaches a run only through a commit a person made: the tamper check
fails an attempt that changed them. And what runs outside `the-cook worker` is yours: an agent a loop runner, a script
or a person starts any other way runs without this contract, and `the-cook` does not look for it.

## Lifecycle actions are code

Lifecycle actions ([lifecycle](lifecycle.md)) run as child processes of `the-cook`, with the environment of the process
that fired them, outside the agent's per-command permission checks.

- **Permitting an event permits its actions.** An agent allowed to run `the-cook lifecycle run deliver` runs every
  `deliver` action, including commands the policy denies to the agent itself.
- **Permitting a trigger point permits its automatic actions.** An agent allowed to run `the-cook worker` in a workspace
  bound to a task fires every `completed` and `deliver` action with `auto: true`; the same holds for `the-cook verify`,
  `the-cook workspace create`, `the-cook workspace remove` and `the-cook fleet --deliver`. `the-cook doctor` never fires
  events ([lifecycle](lifecycle.md#trigger-points)).
- **The hooks file is the complete list.** Every action is declared in the repository's own `.cook/hooks.yaml`, and
  nothing outside the repository adds one ([ADR 0013](../adrs/0013-organization-content.md)). A recipe that declares
  actions writes them there as entries only when a person applies it, and the diff shows them. Reviewing that file
  reviews everything an event can run.
- **Every action sees the whole environment.** A webhook URL exported for one `http` action is visible to every `run`
  and `agent` action of every event fired from that shell.

In practice:

- Review `lifecycle` changes in `.cook/hooks.yaml` like code, including those an agent wrote while following a
  skill an organization publishes, such as one installed from `acme/agent-skills` with a skills installer.
- Keep the seeded `ask` on `[the-cook, lifecycle, run, deliver]` until the actions are ones you accept an agent running
  unasked. Moving it to `allow` is a decision about the actions, not about the command.
- Check `the-cook lifecycle list` for `auto: true` before granting an agent a command with a trigger point.
- Keep actions to delivery and notification. `the-cook` itself never writes the team's record, but an action can, with
  the credentials it runs with. Declare such an action only when the team decided it, and never one that touches
  production ([ADR 0012](../adrs/0012-write-boundary.md)).
- `the-cook` is never an MCP client. An integration that needs an MCP tool is an `agent` action, which uses the agent's
  own MCP connections and their credentials under the worker contract; a deterministic one uses the service's API
  through `http` or its CLI through `run` ([ADR 0019](../adrs/0019-action-kinds.md)).

## Agent actions and prompt injection

An `agent` action ([lifecycle](lifecycle.md#agent)) starts an agent headless on a prompt. It is an unattended agent,
so it runs under the same contract as `the-cook worker`:

- the unattended launch arguments of the agent's harness recipe, the binary's `[the-cook, lifecycle]` deny and the
  repository's `worker.deny`, and the recipe's floor bypass arguments refused;
- the tamper check, with the checkout's head commit as base, as the version-control recipe reports it: a changed
  tool-owned file fails the action and is restored;
- no commit, no push, no lifecycle event: its edits stay uncommitted, and the output and the lifecycle log list them
  for a person to review;
- the `worker` role's standing context.

A prompt that includes `$COOK_TASK`, `$COOK_REASON`, `$COOK_SUMMARY` or any value written by people and
agents carries that text to the agent as part of its instructions. Anyone who can write a task title can address
the agent. The contract bounds what the agent can do with it; the prompt decides how much untrusted text it reads.

- Prefer fixed prompts that tell the agent where to look ("read the change merged from `$COOK_BRANCH`") over
  prompts that paste untrusted text into the instructions.
- Keep `agent` actions manual unless their effect is one you accept on every trigger. `the-cook doctor` lists every
  automatic `agent` action as a note.
- Review an `agent` action's edits like any other contribution before committing them.

`attention` and `turn_end` accept only `run` and `http`, so no agent hook starts another agent.
`COOK_MESSAGE`, the agent's own notification text, is untrusted: send it to a person, never into another agent's
prompt.

## Standing context

Every session that [standing context](context.md) reaches reads it as instructions, so its sources are controlled:

- Entries name only repository files and skills under `.agents/skills/`. Nothing is fetched when a hook runs.
- A skill is delivered as the file committed under `.agents/skills/`. Whether a person wrote it or a skills installer
  added it, the commit is what a reviewer reads ([skills](skills.md)).
- For a worker, the declarations and every file they name are read from its base commit, so a worker cannot rewrite its
  own instructions between attempts.
- A change to an injected file or skill is a change to every agent session's instructions. Review it like policy.
- Do not inject files untrusted text can reach, such as generated reports or copies of issue text.

## The pinned binary

Every clone runs the release of `the-cook` that `.cook/wrapper.yaml` pins, through the committed wrapper `cook`
([wrapper](wrapper.md)).

- **Downloads are verified.** The wrapper fetches the release over HTTPS and checks its sha256 against the checksum
  for the platform in `wrapper.yaml`. A mismatch exits 1 and caches nothing.
- **Implicit entry points never download.** Rendered hooks run the wrapper from the checkout's root, found through
  the version-control recipe's `root` operation, with `--no-download` ([the hook command](render.md#the-hook-command)).
  An agent starting a session or editing a file runs a binary already in the cache, or skips the hook, and never fetches
  code. Hooks never fetch a recipe either. A download happens only when a person or an agent runs an explicit command,
  such as `./cook verify`.
- **Pins are signed.** `./cook wrapper --version <tag>` and `the-cook scaffold` write a pin's checksums only after
  the running binary verifies the release's signed checksum file with the release keys it embeds. A
  [pending pin](wrapper.md#a-pending-pin), written by `scaffold --offline`, has no checksums and admits no download.
  Trust passes from the first binary a person installs to every later pin
  ([the trust chain](wrapper.md#the-trust-chain)).
- **An upgrade is a diff.** Review the change to `wrapper.yaml` like a dependency bump: the version is the one you
  meant, and `source` did not move.
- **One pin.** `the-cook` reads its version only from `wrapper.yaml`. Keep `the-cook` out of a task runner's tool list,
  so two files never answer the same question.
- **`COOK_BINARY` bypasses the pin.** It makes the wrapper run any binary, unverified. Keep it for air-gapped
  machines and for development of `the-cook` itself; never set it in a shared shell profile or in CI.
- **Recipes are pinned by hash.** Every [recipe](recipes.md) comes from a cookbook and is downloaded only by an
  explicit `./cook recipe pin`, `recipe fetch`, `recipe apply` or `scaffold --cookbook`, cached outside the repository
  and verified against the `sha256` in the profile on download and on every load; a mismatch exits 1 and the archive
  is unused. Review a change to a recipe's `source`, `version` or `sha256` like a dependency bump, and read the recipe
  with `./cook recipe show <name>` before approving it. Two `recipes:` entries with the same name are a validation
  error.
- **Workers cannot swap the binary.** The tamper check restores `cook` and `wrapper.yaml` before the agent starts
  and fails an attempt that changed them, and a failed attempt returns the checkout to the base commit, so the next
  worker a loop runner starts through `./cook` runs the pinned release.

## Secrets

- Never write a secret in the profile, the policy, the hooks file or an action. They are committed, rendered into
  files every clone has, and shown in diffs.
- Actions, hooks, validators, lanes and agents inherit the environment. Start unattended runs from a shell that holds
  only the credentials the run needs, without production tokens or a cloud administrator profile.
- An `http` action reads its URL and header values only from the variables `url_env` and `headers_env` name, never
  from the file. `the-cook` never prints or logs them: `--dry-run` shows the variable names, and the
  [lifecycle log](lifecycle.md#the-lifecycle-log) holds no environment values, URLs, headers, bodies or prompts.
- Every action inherits the environment, so a secret exported for one action is readable by all of them. Export only
  what the declared actions need, in the shell or CI job that fires them.
- A command that looks local can follow an environment file to a remote system, such as a migration whose `.env`
  points at a shared database. Check what the environment files point at before allowing such a command
  ([pitfalls](pitfalls.md#a-local-command-follows-an-environment-file-to-a-remote-system)).
- `the-cook` does not manage MCP server definitions, which often carry tokens. Keep them in each person's own settings.

## Importing personal scopes

`the-cook scaffold --adopt --import local,user` reads each declared agent's local override files and user-scope
permission files, as its harness recipe names them ([scaffold](scaffold.md#adopt)). What it imports becomes committed
and shared with everyone who clones the repository.

Personal settings accumulate entries nobody chose for a team:

- exact commands approved once, which can contain a token, a host name or a personal path;
- broad allows a person accepted for convenience;
- rules for tools only that person has.

Every entry from these scopes is marked for review in the [to-do](scaffold.md#the-to-do). Run with `--dry-run` first,
read each marked entry, delete what is personal, and keep only what you would have written for the team. The source
files are only read; the person's own settings keep applying to them.

## Reviewing rendered diffs

Rendered files are committed so every permission change is a diff. After `the-cook render`, `the-cook recipe apply`
or an upgrade, read:

| File | Look for |
| --- | --- |
| Each agent's rendered permissions and hook entries, as its harness recipe's [renders](recipes.md#renders) name them | new `allow` entries and their breadth; entries that left `deny` or `ask`; new hook commands and the keys that enable them; a deny present in one agent's file and missing in another's is an entry that agent cannot express |
| The `policy` block in `AGENTS.md` | entries marked as not enforced in an agent; the floor rules, each with the recipe it comes from |
| `recipes` and `vcs` in `.cook/config.yaml` | a recipe added or removed: removing one removes its renders and its floor rules; a recipe's `source`, `version` or `sha256` changing, read with `./cook recipe show <name>` before approving |
| `.cook/hooks.yaml` | new or changed `post_edit` hooks and `lifecycle`, `attention` and `turn_end` actions, especially `auto: true`, `agent` actions and their prompts, and the variables `http` actions read |
| `commands`, `verify.lanes` and `verify.validators` in `.cook/config.yaml` | new or changed lint and build commands, lanes and validators, such as those `the-cook recipe apply` adds: `the-cook verify` and the agent hooks run them with the credentials of the person or agent at work |
| `context` in `.cook/config.yaml`, the `context` block in `AGENTS.md` | new or changed entries, the files and skills they inject, and which roles they reach |
| `.agents/skills/` | a skill's content changing, from the source you expect |
| A file a recipe's template seeded, such as a loop runner's configuration | every agent's command still runs `./cook worker`, and no setting turns permission checks off. `the-cook` reads none of it, so this review is the check before the first task |
| `.cook/wrapper.yaml` | a version change you intended, with checksums that match the release's published checksum file; a `source` that moved |
| `cook` | changes only together with an upgrade whose release brings a new wrapper |

Add `./cook render --check` to CI ([render](render.md#drift-check)) so a hand edit to a rendered file fails review
instead of shipping.

## Checklist before running unattended

- [ ] Everything is committed, `.cook/` and `.agents/` changes included; `./cook render --check` exits 0.
- [ ] `./cook version` succeeds on the machine that runs, so hooks find the pinned binary in the cache;
      `COOK_BINARY` is unset.
- [ ] `the-cook doctor` reports no finding: the versions of the programs the recipes in use describe, the version
      control program and the agents included; a contract no declared recipe fills; programs the declared commands
      and actions name; a program detected while its recipe, and so its floor, is not in use; drift.
- [ ] Every recipe the run uses is in the cache (`./cook recipe fetch`); a worker never fetches.
- [ ] The loop runner's configuration names `./cook worker` as every agent's command and adds no bypass flag.
      What runs outside `the-cook worker` is yours to check; `the-cook` does not.
- [ ] The folder is trusted by the agent; the run's workspaces are nested in the primary checkout.
- [ ] Settings outside the policy are known: local override files, user and managed settings neither widen nor
      narrow what the run needs.
- [ ] Every `ask` entry the run reaches is a stop you want.
- [ ] Every `allow` entry is one you accept with no judgement applied.
- [ ] `worker.deny` lists commits, pushes, refs and workspaces, version-control options that act on another checkout
      or override its configuration, the hosting platform's CLI, shared local services, production and cloud.
- [ ] `the-cook lifecycle list` shows only automatic actions you accept, for the scopes the run reaches, and no
      automatic `agent` action you have not chosen.
- [ ] A `blocked` action tells a person when the run stops, whatever drives the run fires it
      (`./cook lifecycle run blocked --scope run`), and the variables it reads are set in that shell.
- [ ] `./cook context show --role worker` shows the standing context you expect the workers to receive.
- [ ] The shell holds no production or administrator credentials; environment files point at local services.
- [ ] Tasks are scoped to one set the loop runner takes, self-contained, and their text was written or read by a
      person.
- [ ] Parallel workers do not share ports, databases or containers they would collide on.
- [ ] Delivery is decided before launch: per task, per run, or after judgement by a coordinating agent, and the
      boundary is stated (branch, pull request, CI trigger, auto-merge).
- [ ] The loop runner's iteration budget is set.
