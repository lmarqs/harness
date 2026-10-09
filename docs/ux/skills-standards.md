# Skills standards

A skill is a directory with a `SKILL.md` that an agent loads when its description matches the task. Skills carry the
judgement half of the work; the binary carries the deterministic half
([when-cli-when-skill.md](when-cli-when-skill.md)). This page covers the skill harness ships and the rules harness
expects of the skills around it.

## Three kinds of skill

| Kind | Lives in | Written by | Reaches a repository by | Holds |
| --- | --- | --- | --- | --- |
| **Harness skill** | the binary; installed at `.agents/skills/harness/` | harness maintainers | `harness render` (tool-owned, listed in the manifest) | how to edit the three repository files, finish an adoption, read `doctor` findings, when to run `harness lifecycle run <event>`, and why the invariants exist |
| **Organization skill** | an organization's skills repository | the organization | `skills.sources` in the profile, installed by `harness skills sync` through the `skills` CLI | conventions shared by one organization's repositories: delivery, review, observability, domain vocabulary |
| **Repository skill** | `.agents/skills/<name>/` in the repository | people and agents in that repository | committed directly | knowledge of one repository: its modules, risks, procedures |

The binary writes only the harness skill. Organization and repository skills are knowledge; the binary never creates,
edits or deletes them ([principles.md](principles.md#3-sets-the-rules-never-plays)).

## When to create each

| Situation | Kind |
| --- | --- |
| Agents misuse a harness file or command in many unrelated repositories | harness skill (change ships in a release) |
| A new `harness explain` topic is needed | harness skill reference with the same text |
| A convention holds across one organization's repositories and needs judgement | organization skill |
| A procedure or body of knowledge is specific to one repository | repository skill |
| The behaviour is deterministic | not a skill: a command, flag, profile field or preset |
| A one-off instruction for a single session | not a skill: the prompt |

The harness skill names no organization, vendor or company convention. Anything that would need one belongs in an
organization skill or a preset.

## Structure

```text
<name>/
  SKILL.md          # frontmatter + the core procedure
  references/       # detail loaded on demand
  scripts/          # optional helpers the procedure runs
```

- `name` in the frontmatter matches the directory name: lower case, hyphen-separated.
- `description` is the trigger. It states what the skill does and when to use it, in the words users and agents
  actually use for the task, and names the closest situations where it does not apply.
- The body of `SKILL.md` SHOULD stay under 500 lines. It holds the procedure an agent follows every time; anything
  needed only in some cases moves to `references/` with a one-line pointer saying when to read it.
- Procedures are numbered steps. Each step that changes state names the command that performs it. The last step is the
  check that proves the result (`harness render --check`, `harness doctor`, `harness verify --check`).

## Explain the reasoning

Each rule in a skill carries its reason in one sentence. An agent that knows why a rule exists applies it to cases
the text did not foresee and does not drop it when it seems inconvenient.

- Good: "Edit `.agents/policy.yaml`, never `.claude/settings.json`: the settings file is rendered from the policy and
  `render --check` fails on hand edits."
- Not enough: "Never edit `.claude/settings.json`."

Rules phrased only as capitalised commands (ALWAYS, NEVER) without a reason SHOULD be rewritten.

## Never duplicate a CLI behaviour in prose

- A skill calls a command; it never lists the steps the command performs so that an agent could perform them by hand.
- A skill never tells an agent to edit a rendered file, edit inside a managed block, or bypass permissions.
- A skill that ends in delivery does its judgement steps (grouping commits, wording messages), then runs
  `harness lifecycle run deliver`. It never repeats the declared actions (a push, a pull-request command) by hand, so
  every side effect outside the clone stays declared, guarded and recorded.
- Flag tables, schemas and exit codes are not copied into skills. A skill points to `harness <command> --help` or
  `harness explain <topic>` for them.

## Keeping the harness skill and the CLI in sync

- `harness explain <topic>` prints the same text as the harness skill's reference for that topic. Both come from one
  embedded source, so they cannot disagree.
- Every topic listed by `harness explain` has a reference in the skill, and every reference is reachable from
  `SKILL.md`.
- Every command line quoted in the harness skill MUST exist in the CLI surface of the same release. The release
  process checks this.
- A change to a command's behaviour, a file schema or an invariant updates the matching skill text in the same pull
  request.

## Evaluation before release

A skill is released only after it is evaluated. For the harness skill this is required; organization and repository
skills SHOULD follow the same practice.

| Evaluation | What it checks | Pass condition |
| --- | --- | --- |
| Trigger | prompts that should load the skill, and near-miss prompts that should not | the skill loads for the first set and not for the second |
| Task | realistic requests run by an agent in fixture repositories of each supported stack ("allow the deploy tasks", "finish the adoption", "add a lane for integration tests") | the binary's own checks pass afterwards (`render --check`, `doctor`, `verify --check`) and no file outside the expected ones changed |
| Regression | the task set from the previous release | no task that passed now fails |

Evaluations judge outcomes by deterministic checks wherever possible, not by reading the agent's prose.
