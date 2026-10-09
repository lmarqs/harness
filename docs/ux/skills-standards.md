# Skills standards

A skill is a directory with a `SKILL.md` that an agent loads when its description matches the task, or that harness
injects through standing context. Skills carry the judgement half of the work; the binary carries the deterministic
half ([when-cli-when-skill.md](when-cli-when-skill.md)). This page covers the skill harness ships and the rules harness
expects of the skills around it.

## Three kinds of skill

| Kind | Lives in | Written by | Reaches a repository by | Holds |
| --- | --- | --- | --- | --- |
| **Harness skill** | the binary; installed at `.agents/skills/harness/` | harness maintainers | `harness render` (tool-owned, listed in the manifest) | how to run harness (`./harnessw <command>`), edit the repository files in `.harness/`, finish an adoption, read `doctor` findings, when to run `harness lifecycle run <event>`, and why the invariants exist |
| **Organization skill** | an organization's skills repository | the organization | `skills.sources` in the profile, installed by `harness skills sync` through the `skills` CLI | conventions shared by one organization's repositories, and how to write them into one repository's files: delivery actions, policy entries (observability read patterns, deploy denies), review, domain vocabulary |
| **Repository skill** | `.agents/skills/<name>/` in the repository | people and agents in that repository | committed directly | knowledge of one repository: its modules, risks, procedures |

The binary writes only the harness skill. Organization and repository skills are knowledge; the binary never creates,
edits or deletes them ([principles.md](principles.md#3-sets-the-rules-never-plays)), because knowledge needs judgement
the binary does not have.

In a repository, every skill lives under `.agents/skills/`, the agent-content directory other tools share, so the
`skills` CLI, the agents and people find them in one place. harness's own configuration lives in `.harness/` and never
among skills ([configuration-standards.md](configuration-standards.md#where-files-live)).

## When to create each

| Situation | Kind | Why |
| --- | --- | --- |
| Agents misuse a harness file or command in many unrelated repositories | harness skill (change ships in a release) | the fix reaches each repository in the diff that moves its pin to that release |
| A new `harness explain` topic is needed | harness skill reference with the same text | both come from one embedded source |
| A convention holds across one organization's repositories, as judgement or as entries harness enforces (a deny, a delivery action) | organization skill that guides an agent to write it into each repository's files ([below](#organization-skills-that-write-repository-entries)) | the organization owns and reviews the skill once; applying it to a repository takes judgement, and each repository reviews the result |
| A procedure or body of knowledge is specific to one repository | repository skill | it is reviewed where it applies |
| Known guidance must apply every session, or whenever certain files or words appear | an existing skill plus a standing-context entry ([configuration-standards.md](configuration-standards.md#standing-context)) | the trigger is deterministic; the skill stays the single text |
| The behaviour is deterministic and organization neutral | not a skill: a command, flag, profile field or built-in preset | prose would drift from the behaviour |
| A one-off instruction for a single session | not a skill: the prompt | a skill outlives the session it was written for |

The harness skill names no organization, vendor or company convention, because it ships to every user of an open-source
tool. Anything that would need one belongs in an organization skill, and from there in each repository's files.

## Structure

```text
<name>/
  SKILL.md          # frontmatter + the core procedure
  references/       # detail loaded on demand
  scripts/          # optional helpers the procedure runs
```

- `name` in the frontmatter matches the directory name: lower case, hyphen-separated, so the name a person types and
  the directory an agent reads agree.
- `description` is the trigger. It states what the skill does and when to use it, in the words users and agents
  actually use for the task, and names the closest situations where it does not apply. Why: the agent matches the
  description against the task, so the wrong words mean the skill never loads or loads where it does not belong.
- The body of `SKILL.md` SHOULD stay under 500 lines. It holds the procedure an agent follows every time; anything
  needed only in some cases moves to `references/` with a one-line pointer saying when to read it. Why: the body is
  loaded in full whenever the skill is used.
- Procedures are numbered steps. Each step that changes state names the command that performs it. The last step is the
  check that proves the result (`harness render --check`, `harness doctor`, `harness verify --check`), so the outcome
  of the agent's judgement is verified by the binary.

## Skills used as standing context

A skill named in a standing-context entry is injected without the agent choosing it: as content (the `SKILL.md` body,
front matter stripped) or as a one-line pointer to its path. It may also still load by its description. A skill meant
for standing context:

- keeps its body short and self-contained, because an always-on entry is paid in every session and counts against
  `context.budget`;
- reads correctly without the description that normally introduces it, because injected content arrives without its
  front matter;
- states in its first lines what it applies to, so a pointer line ("Apply the <name> skill now: read <path>") and the
  first screen of the body are enough to act on;
- reads correctly when loaded twice, once injected and once by description, because the agent's own matching is left
  as it is ([compatibility-standards.md](compatibility-standards.md#coexistence));
- is installed under `.agents/skills/` and, when it comes from a skills source, locked in `skills-lock.json`, because
  harness injects a locked skill only when its content matches the locked hash.

A directive that must apply even without the binary is declared `static`, so it is written into `AGENTS.md` and does
not depend on a hook.

## Organization skills that write repository entries

An organization's choices that harness enforces (observability read patterns, deploy denies, a CI-trigger commit in
delivery, tracker rules) reach a repository only as entries in its own files, written by a person or by an agent
following the organization's skill. Why: no repository file can name a source outside the repository, and the
organization's choices differ between its repositories, so applying them takes judgement
([ADR 0013](../adrs/0013-organization-content.md)). Such a skill:

- states each entry with the reason the organization holds it, and how to fit it to the repository in front of the
  agent: which entries apply, and what to do with an entry the repository already has, because the agent decides per
  repository;
- has the agent edit `.harness/policy.yaml`, `.harness/hooks.yaml` or `.harness/config.yaml`, never a rendered file or
  a managed block, because those are replaced by the next render;
- ends by running `harness render` and then `harness doctor`, so the entries and their rendered effect reach review as
  one diff and the binary checks the result.

## Explain the reasoning

Each rule in a skill carries its reason in one sentence. An agent that knows why a rule exists applies it to cases
the text did not foresee and does not drop it when it seems inconvenient.

- Good: "Edit `.harness/policy.yaml`, never `.claude/settings.json`: the settings file is rendered from the policy and
  `render --check` fails on hand edits."
- Not enough: "Never edit `.claude/settings.json`."

Rules phrased only as capitalised commands (ALWAYS, NEVER) without a reason SHOULD be rewritten, because emphasis
without a reason gives the agent nothing to apply to a new case.

## Never duplicate a CLI behaviour in prose

Why: a description of a command drifts from the command, and an agent that follows the description instead of
running the command skips the command's checks.

- A skill calls a command; it never lists the steps the command performs so that an agent could perform them by hand.
- A skill never tells an agent to edit a rendered file, edit inside a managed block, or bypass permissions, because
  each of those is undone by the next render or refused by the invariants.
- A skill that ends in delivery does its judgement steps (grouping commits, wording messages), then runs
  `harness lifecycle run deliver`. It never repeats the declared actions (a push, a pull-request command, a webhook)
  by hand, so every side effect outside the clone stays declared, guarded and recorded.
- Flag tables, schemas and exit codes are not copied into skills. A skill points to `harness <command> --help` or
  `harness explain <topic>` for them.

## Keeping the harness skill and the CLI in sync

Why: an agent acts on the skill; when the skill and the binary disagree, the agent acts on wrong rules.

- `harness explain <topic>` prints the same text as the harness skill's reference for that topic. Both come from one
  embedded source, so they cannot disagree.
- Every topic listed by `harness explain` has a reference in the skill, and every reference is reachable from
  `SKILL.md`.
- Every command line quoted in the harness skill MUST exist in the CLI surface of the same release. The release
  process checks this.
- The harness skill writes commands as `./harnessw <command>` and shows `harness <command>` as the equivalent when
  harness is installed globally ([cli-standards.md](cli-standards.md#entry-points)).
- A change to a command's behaviour, a file schema or an invariant updates the matching skill text in the same pull
  request.

## Evaluation before release

A skill is released only after it is evaluated. For the harness skill this is required; organization and repository
skills SHOULD follow the same practice. Why: a skill's effect is seen only in what agents do with it, and reading the
text does not show that.

| Evaluation | What it checks | Pass condition |
| --- | --- | --- |
| Trigger | prompts that should load the skill, and near-miss prompts that should not | the skill loads for the first set and not for the second |
| Task | realistic requests run by an agent in fixture repositories of each supported stack ("allow the deploy tasks", "finish the adoption", "add a lane for integration tests") | the binary's own checks pass afterwards (`render --check`, `doctor`, `verify --check`) and no file outside the expected ones changed |
| Regression | the task set from the previous release | no task that passed now fails |

Evaluations judge outcomes by deterministic checks wherever possible, not by reading the agent's prose, because the
same prose can come with a wrong result.
