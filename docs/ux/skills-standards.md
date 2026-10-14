# Skills standards

## Objective

**Problem.** Reused agent guidance can load for the wrong task, repeat procedures that drift from commands, or teach
behavior unavailable in the release a repository uses. Shared copies can also overwrite repository-specific edits.

**Goal.** Skills are discoverable, scoped, maintainable and consistent with the commands and native programs they
describe. Their results can be reviewed without treating an agent's completion message as proof.

## Overview

A skill is a folder of instructions and supporting resources in the form its consuming program understands. A common
form uses `SKILL.md`, with metadata that helps an agent decide when to load it. Native location, metadata and loading
rules depend on the program.

Cook recipes can declare skill files and discovery configuration as ordinary file operations. The agent decides when
to load guidance and what to do with it. A successful Cook write does not prove that guidance was loaded or followed.
This standard follows the [architecture](../architecture.md) and [skills reference](../features/skills.md). See
[where behavior belongs](when-cli-when-skill.md) for the engine, cookbook and guidance boundaries.

## Sources and maintenance

| Source | Maintainer | Typical content | Maintenance choice |
| --- | --- | --- | --- |
| Cook usage guidance supplied by a cookbook | Cookbook authors | Supported commands, source trust, diff review, approval and recovery prerequisites | Update through that cookbook's chosen recipe or content process |
| Organization or personal skill | Its authors | Shared conventions and how to fit them to a repository | Use the team's installer, a recipe or reviewed repository copies |
| Repository skill | Repository contributors | Modules, risks, procedures and local vocabulary | Edit the repository's own content |

`.agents/skills/<name>/` is a useful shared location where native programs support it. Sources may be local or external;
document which copy to edit and how updates reach users, because competing maintenance processes can overwrite each
other. Engine upgrades do not automatically refresh cookbook or repository guidance.

Committing skill content makes it reviewable and available in each clone. Where an installer uses a lock file, review
it with the content it describes. If installation is required on each machine, document the prerequisites. Neither
choice guarantees that every agent discovers the skill.

## When to create a skill

| Situation | Suitable home | Reason |
| --- | --- | --- |
| Cook commands are misused across repositories | Shared Cook usage skill or focused reference | One explanation can describe the supported interface and its prerequisites |
| A convention applies across an organization | Organization skill | Authors maintain its reasoning once; applying it still needs repository-specific judgement |
| Knowledge is specific to one repository | Repository skill or contributor prose | Guidance is reviewed where it applies |
| Guidance is needed in sessions | Existing skill and the native program's discovery configuration | The consuming program controls loading |
| A reusable setup writes native files | Cookbook recipe using ingredients | The recipe declares file changes, and Cook presents their actual combined diff |
| A process requires judgement, external commands or publication | Skill and the repository's existing tools | These need judgement and external tools; Cook's batch holds file operations |
| An instruction applies to one request only | The session prompt | A persistent skill would outlive the need |

A skill should state its audience and scope rather than assume that every repository uses one organization, stack or
agent. Program-specific guidance can name the program it serves; ingredient models and cookbook content carry that
knowledge separately from the engine.

## Structure

For programs that use a `SKILL.md` convention, this is a useful layout:

```text
<name>/
  SKILL.md          # discovery metadata and core procedure
  references/       # detail loaded when relevant
  scripts/          # optional executable helpers
```

- Use a clear name consistent with the native program's naming rules. Lower-case, hyphen-separated names are useful
  when supported, because people can relate the invocation name to its folder.
- State what the skill does, when it applies and nearby situations where it does not apply. Discovery words should
  match the user's task, because vague descriptions can load too often or never load.
- Keep the core procedure short; around 500 lines is a useful upper bound. Put occasional detail in focused references
  and explain when to read each, because agents may load the whole body.
- Use numbered steps for a sequence. Name the actual command or edit for each state change and its prerequisites.
  Include review evidence appropriate to the task, because a completion claim alone does not establish the result.
- Review supporting scripts as executable code, because a short instruction can delegate consequential actions to them.

## Discovery and session guidance

Inspect the consuming program's locations, metadata, links and project trust rules. Existing real directories and
whole-directory links expose different content; choose targets deliberately rather than replacing a directory that
contains unrelated skills. Declared batch writes and deletions stay within the invocation directory, including
paths resolved through symlinks.

Guidance intended for frequent loading should be short and state its scope early, because unrelated always-on text uses
attention on every task. Link focused detail where useful. Text should remain clear when encountered twice, since an
agent may discover it through several paths.

Instructions that must remain readable without Cook belong in native files the program can read directly. Markdown
section boundaries use HTML comments, and a pointer does not guarantee that an agent follows it. See
[repository guidance](../features/context.md) and [coexistence](compatibility-standards.md#coexistence).

## Skills and recipes

A skill can guide a person or agent in choosing trusted recipes, fitting arguments to a repository and reviewing the
result. A recipe evaluates to ordered file operations.

Use the intended CLI grammar when documenting the design:

```sh
./cook ./recipes/project.groovy --instructions='Keep existing comments.'
./cook apply ./recipes/base.groovy --agent=codex ./recipes/project.groovy --agent=claude
./cook batch list
./cook revert 2026-10-14T15-04-05Z
```

The batch ID is the recorded batch's UTC start time. These examples establish argument scope. For current execution,
consult the [root README](../../README.md) and the [core implementation guide](../../core/README.md).

Batch options precede operands. Only use `--batch-approve` when preapproval is intended: it displays the diff and
proceeds without prompting. Otherwise Cook asks before target writes. Noninteractive execution alone does not authorize
writes, and the CLI accepts the whole batch. A skill must not infer preapproval merely because an agent is driving the
command. See [CLI approval](../features/cli.md#batch-options-and-approval).

Trust the recipe source before evaluation. Recipes are executable Groovy; approval and recovery govern declared
batch changes, not arbitrary effects of recipe code. A skill or native permission setting is not a sandbox supplied
by Cook.

## Organization guidance that changes configuration

An organization skill can explain which native permission rules, instructions, task definitions or CI conventions
fit a repository. State the reason for each choice and how to handle existing settings, because applying shared
conventions needs judgement. Ingredients describe native formats; cookbook recipes can declare the requested changes.

For example, a team may allow an observability search tool in an agent's project settings. Its skill should explain the
scope and reason, inspect relevant project and personal settings, and review the actual permission effect. If the team's
CI requires a particular commit message, that convention belongs in repository guidance and the team's publication
process. Cook may configure those programs.

For recipe-maintained content, prefer updating the source and applying the supplied recipe through the normal diff and
approval flow. Repository-authored content can be edited directly. Manual edits are allowed; a later explicit apply may
propose replacing them. See [configuration review](configuration-standards.md#ordered-changes-and-approval).

## Explain the reasoning

Each rule should carry a concrete reason, because readers need to apply it to cases the author did not foresee.

- Good: "Update this recipe's skill source before applying it, because applying the old source can replace the local
  correction; review the resulting batch diff."
- Incomplete: "Never edit skills directly."

Capitalized commands without reasons do not explain scope or tradeoffs. State when a rule applies and what evidence
supports it, rather than relying on emphasis.

## Call commands without duplicating their implementation

A skill should call the supported command for an operation and link its reference, because a parallel procedure can
drift from the command's actual behavior. Explain the intent, prerequisites and review decisions; do not teach an
agent to imitate Cook's file writes or recovery records by hand when asking it to cook.

Avoid copying full flag catalogs into skills. Link the [CLI reference](../features/cli.md) for the intended design and
version-appropriate usage guidance for actual execution. Do not cite a command the CLI does not have. Existing content
follows ordinary review.

A skill for committing, pushing, opening pull requests or firing delivery events uses the repository's external tools
and applicable authorization; file-diff approval does not authorize publication.

## Keeping guidance and commands in sync

When an interface changes, update the guidance that describes it and state its version and prerequisites, because an
agent can act on stale instructions. Check quoted commands against the interface the skill actually targets.

A skill describing intended architecture must distinguish its examples from current usage. Useful documentation
cross-references may run in either direction.

## Reviewing skill outcomes

Use realistic tasks and near-miss prompts when evaluating a skill. Trigger review asks whether the right requests load
it; task review inspects the files and actions it produces; regression review considers previously useful cases. These
are review techniques.

Choose evidence appropriate to the task: inspect changed native files, command results, scope, permission effects and
unrelated content. Use an existing relevant check where the task calls for one, without treating an agent's prose as
proof.

Apply evaluates the supplied recipes against current content every time and needs neither batch history nor
`cook.lock`. Applying another recipe never removes skill files a different recipe owns. Revert reverses only one
recorded batch's changes, preserving later non-overlapping edits, and shows the actual diff for whole-batch approval.
Revert does not reverse actions an agent took after reading the skill. See [revert](../features/revert.md).
