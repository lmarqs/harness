# Where new behaviour goes

Every new capability has exactly one home. This page is the rule for choosing it. It applies to proposals for harness
itself and to the advice harness gives repositories through its skill.

## Destinations

| Destination | What it is | Who maintains it |
| --- | --- | --- |
| **Built-in invariant** | a rule compiled into the binary that no file, preset or flag can remove | harness |
| **New command** | a new verb or noun-verb in the binary | harness |
| **Flag on an existing command** | a variation of an operation the binary already performs | harness |
| **Profile field** | a per-repository value that parametrises a deterministic binary behaviour (`harness.yaml`; permissions in `policy.yaml`, hooks in `hooks.yaml`) | the repository |
| **Lifecycle action** (declared in a repository or a preset) | a deterministic command declared on a lifecycle event under `lifecycle:` in `.agents/hooks.yaml` or in a preset, run by harness when the event fires | the repository, or the organization through its preset |
| **Harness skill or explain topic** | guidance an agent follows to do judgement work around harness | harness (embedded, shipped with the binary) |
| **Preset** | organization or personal data pulled by `extends`: policy entries, hook declarations, lifecycle actions, block text, lane defaults | the organization |
| **Organization or personal skill** | guidance for judgement work specific to an organization's conventions | the organization |
| **Left to the repository** | a mise task, a repository skill, prose in `AGENTS.md`, memory | the repository |

## Criteria

| Criterion | Question |
| --- | --- |
| Determinism | Given the same inputs, is there exactly one correct output? |
| Repetition | Is it needed, in the same form, in many unrelated repositories? |
| Safety relevance | Would getting it wrong, or switching it off, let an agent escape its permissions or claim unverified work? |
| Organization specificity | Is it true only for one organization's conventions, tools or vendors? |
| Judgement | Does it require reading prose, weighing options, or writing text? |
| Reach | Does it change something outside the clone (a push, a pull request, a CI trigger, a notification), or react to something that happened there (a merge)? |
| Lifecycle point | Does it belong at a point of the development lifecycle: `start`, `verified`, `completed`, `deliver` or `landed`? |

## Decision table

| Determinism | Repetition | Safety relevant | Org specific | Judgement | Destination |
| --- | --- | --- | --- | --- | --- |
| yes | yes | yes | no | no | built-in invariant |
| yes | yes | no | no | no | command, flag, or profile field (see below) |
| yes | any | no | yes | no | preset |
| yes | any | no | yes, or one repository | no | lifecycle action, when it reaches outside the clone at a lifecycle point ([below](#lifecycle-actions)) |
| yes | no | no | no | no | left to the repository (a mise task or a profile value) |
| no | yes | any | no | yes | harness skill, with a binary check that proves the outcome |
| no | any | any | yes | yes | organization skill |
| no | no | any | no | yes | repository skill or prose |

A safety-relevant rule that is organization specific (a deploy deny, a protected branch other than the default) is a
preset entry. It is enforced by the rendered policy like any other entry, but it is not a built-in invariant, because
the binary contains no organization-specific content.

## Lifecycle actions

A behaviour is a lifecycle action when all of these hold:

1. It is a deterministic command: the same state produces the same effect, and no step needs judgement.
2. It reaches outside the clone, or reacts to something that happened outside it.
3. It is organization or repository specific: it names a hosting platform, a CI convention, a notification channel
   or a cleanup habit that not every repository shares.
4. It is triggered at a lifecycle point, by a person, a coordinating agent or automatically (`auto: true`).

Where it is declared:

- In a **preset** when it holds for an organization's repositories (open a pull request on a given hosting platform,
  add the organization's CI-trigger commit).
- Under `lifecycle:` in the repository's **`hooks.yaml`** when it holds for one repository.

Never in the binary. The binary contains no hosting-platform logic and ships no actions; a command or flag that pushes,
opens a pull request or calls a hosting platform is a design error, whatever the convenience
([principles.md](principles.md#7-nothing-leaves-the-clone-undeclared)).

When the behaviour needs judgement before the side effect (which commits belong together, how to word them), split
it: an agent guided by a skill does the judgement, then runs `harness lifecycle run <event>`, so the side effect stays
declared, visible and recorded.

## Choosing among command, flag and profile field

For deterministic, repeated, organization-neutral behaviour:

1. **Profile field** when repositories need different values for the same behaviour (a lane's command, the lint
   command, the tracker). The default MUST preserve existing behaviour.
2. **Flag** when the same repository needs the behaviour sometimes, per invocation (`--full`, `--base <ref>`,
   `--no-bootstrap`). A flag that every invocation in a repository would pass is a profile field instead.
3. **New command** when the operation has a different object or a different effect from every existing command. A
   behaviour that changes what an existing command writes is a flag or a field, not a new command.

## When behaviour splits

Most capabilities have a deterministic half and a judgement half. Split them:

- The binary does the deterministic half and exposes a check.
- The skill tells the agent how to do the judgement half and ends with the command that checks it.
- The binary never performs the judgement half by heuristics; the skill never re-implements the deterministic half
  in prose.

Adoption is the model: `harness scaffold --adopt` imports what it can map and lists the rest as a to-do; the harness
skill guides the agent through the to-do; `harness doctor` confirms the result.

## Worked examples

Each row shows a request and where it goes.

| Request | Analysis | Destination |
| --- | --- | --- |
| Render permissions for Claude Code and Codex from one policy | deterministic, every repository, safety relevant | binary: `harness render` |
| Deny force pushes to the default branch | deterministic, every repository, safety relevant, organization neutral | built-in invariant |
| Allow `mise run deploy:status` in one repository | deterministic, one repository | `policy.yaml` in that repository |
| Read-only patterns for an organization's observability MCP server | deterministic, organization specific | preset |
| Open a pull request when work is concluded | deterministic command, leaves the clone, hosting-platform specific, belongs at `deliver` | a preset's `deliver` action (for example push, then the hosting platform's pull-request command) |
| Add a CI trigger commit before the pull request | deterministic, leaves the clone with the push, organization convention, belongs at `deliver` | an organization preset's `deliver` action, declared before the pull-request action |
| Choose which Ralph task commits go into which pull request and reword them | judgement (grouping, wording), conventions vary by organization | an agent guided by an organization or personal skill; the agent then runs `harness lifecycle run deliver` |
| Remove the worktree after its branch is merged | deterministic, reacts to a merge, every repository | binary: `harness doctor` lists the landed branch and prints the command; `harness worktree remove` fires `landed`, then removes the worktree |
| Delete the remote branch once the work has landed | deterministic, leaves the clone, repository habit, belongs at `landed` | a `landed` action in `hooks.yaml` (`git push origin --delete "$HARNESS_BRANCH"`), `auto: true` if it should run whenever `worktree remove` or `ralph clean` handles a landed branch |
| Pick which tests to run for a change | judgement (which code is affected) | agent; the repository declares lanes and `required_when` globs in the profile; `harness verify` records the choice and replays it |
| Lint a file after every edit | deterministic, per-repository command | `hooks.yaml` `post_edit` entry |
| Support a new agent CLI | deterministic translation of the same policy and hooks | adapter in the binary |
| Finish an adoption: reconcile a colliding `verify` task, move a hook the importer could not model | judgement | harness skill, checked by `harness doctor` |
| Summarise the policy in `AGENTS.md` | deterministic, derived from `policy.yaml` | binary: the `policy-summary` managed block |
| Explain in `AGENTS.md` why the team keeps e2e tests mandatory for routes | judgement, one repository | repository prose, outside managed blocks |
| Record a lesson learned during a session | judgement | agent, in the repository's memory; never the binary |
| Open an issue for each to-do item | judgement, writes to the record of work | agent with its own tools; the binary never writes issues |
| Report worktrees whose branch was merged | deterministic, every repository | a `doctor` check |
| Install an organization's shared skills in every repository | deterministic, organization data | `skills.sources` in each profile plus `harness skills sync`, run across repositories with `harness fleet` |
| Deliver a harness upgrade to every repository | deterministic tool-owned changes, many repositories | `harness fleet render --deliver`: fleet commits on a local branch and fires each repository's own `deliver` actions |

## Anti-patterns

- A command that writes prose, memory or skill content.
- A setting that turns off an invariant, or a preset that removes one.
- A heuristic in the binary that guesses where an agent should decide.
- A skill that lists the steps a command performs instead of calling the command.
- An organization name, vendor or convention as a binary default.
- A new command whose only difference from an existing one is a default value.
- A command or flag that pushes, opens a pull request, or calls a hosting platform's API instead of a declared
  lifecycle action.
- A lifecycle action that needs judgement to run correctly; the judgement belongs to an agent before the action fires.
