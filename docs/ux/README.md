# User-experience standards

## Objective

**Problem.** Developers, reviewers, interactive and unattended agents, CI pipelines and teammates who never chose the
tool all meet it through what it shows them and what it leaves in their repositories. When each feature decides on its
own how it treats them, what someone learns from one feature does not hold for the next, a new feature can impose on
people who never adopted the tool, and nobody can tell whether a behaviour they meet is intended or a defect.

**Goal.** Every feature, present and future, behaves the same predictable way toward each of these users, and anyone who
meets a behaviour can tell whether it is intended or a defect.

## Overview

These pages hold the rules for everything `harness` presents to the people, agents and systems that meet it:
commands, flags, output, exit codes, configuration files, generated content, skills and documentation. They are
normative, like architecture decision records, but they govern how harness is experienced rather than how it is built.

- Architecture decisions: [../adrs/](../adrs/)
- Individual features and their reference: [../features/](../features/)
- Terms (profile, policy, managed block, manifest, receipt, preset, lane, …): [glossary](../features/glossary.md)

A standard here applies to every feature, present and future. When the binary, a generated file, the harness skill or
a features page disagrees with a standard, that artifact is wrong and is corrected to match the standard. When a
standard disagrees with an ADR, the ADR wins and the standard is corrected, because the ADRs decide the architecture
the standards present to users.

These pages name commands `harness <command>`, the program's name. In a repository they run through the committed
wrapper as `./harnessw <command>` ([cli-standards.md](cli-standards.md#entry-points)).

## Who these standards protect

| User | Meets harness through | What the standards guarantee |
| --- | --- | --- |
| Developer in an adopted repository | `./harnessw`, task shims, generated files in pull requests | one pinned version on every machine, predictable commands, readable diffs, nothing written without being shown first |
| Interactive agent (Claude Code, Codex) | rendered settings, hooks, `AGENTS.md` blocks, injected standing context, the harness skill, `harness explain` | parseable output, exact next commands, text that matches the binary |
| Unattended agent (Ralph coordinator or worker, an `agent` action) | the CLI without a terminal, exit codes, JSON | no prompts, a distinct "blocked" outcome, no hidden state |
| CI | the optional `./harnessw render --check` and `./harnessw doctor` steps | deterministic checks with the pinned, checksum-verified version; nothing required |
| Repository owner or teammate who does not use harness | committed files only | a clone that builds, tests and runs agents without the binary; conventions written in plain language in `AGENTS.md` and `.gitignore` |
| Repository that stops using harness | `harness remove`, `harness remove --eject` | undo returns the repository to a working state without harness; eject keeps its conventions, permissions and hooks as the repository's own |
| Pull-request reviewer | diffs of rendered files, managed blocks and the pin | every generated change visible and traceable to a source file; every version change traceable to a signed release |
| Workflow maintainer | `fleet`, built-in presets and `harness preset apply`, declared actions, organization skills, schemas | one way to do each thing, safe across many repositories; nothing leaves a clone undeclared; nothing reaches a repository except as a reviewed change to its own files |
| Open-source user | any of the above | no organization-specific content in the binary, its skill or its docs |

## Normative language

The key words MUST, MUST NOT, REQUIRED, SHALL, SHALL NOT, SHOULD, SHOULD NOT, RECOMMENDED, MAY and OPTIONAL are
interpreted as described in BCP 14 (RFC 2119, RFC 8174) when, and only when, they appear in capitals. Lower-case uses
carry their ordinary English meaning.

- MUST / MUST NOT: a release that violates the rule is defective.
- SHOULD / SHOULD NOT: deviation needs a reason stated in the pull request that introduces it.
- MAY: an allowed option; no justification needed.

## Every rule carries its reason

Each normative rule in these pages states why it holds, in a "because" clause or a "Why:" sentence that follows it. A
rule whose reason is known can be applied to a case the text did not foresee, and can be judged when it seems to get
in the way; a rule without one can only be obeyed or ignored.

## Changing a standard

A standard is edited in place, in the same pull request that changes the behaviour it governs. Why: the standards
describe what harness is, so a standard and the behaviour it describes change together or not at all.

- The edited rule keeps its reason, updated when the reason changed.
- A change that would contradict an ADR changes the ADR first, in the same pull request, because the ADR is the
  authority the standard derives from.
- Typo fixes and clarifications that do not change a rule need no stated reason.
- The pages hold no status, date or record of earlier rules. Version control keeps the past; the page states what is
  true.

## Index

| Page | Governs |
| --- | --- |
| [principles.md](principles.md) | The user-facing principles every other standard derives from, and how they rank when they conflict |
| [cli-standards.md](cli-standards.md) | Entry points (the wrapper, a global install, implicit hooks), command grammar, naming, flags, output, exit codes, errors, confirmations, dry-run, non-interactive use, environment variables, help, version, deprecation |
| [when-cli-when-skill.md](when-cli-when-skill.md) | Where new behaviour goes: command, flag, profile field, declared action, standing-context entry, skill, built-in preset or the repository itself |
| [skills-standards.md](skills-standards.md) | The harness skill, organization skills and repository skills: when to create each, structure, use as standing context, evaluation |
| [configuration-standards.md](configuration-standards.md) | Where files live (`.harness/`, `.agents/`, state paths) and the user-editable files: the wrapper pin, inputs (only the repository's own files and its pinned release), schemas, defaults, validation, policy entry forms, declared actions and their kinds, standing context, applying presets, secrets, what never becomes a setting |
| [generated-content-standards.md](generated-content-standards.md) | Everything harness writes into a repository: headers, owned and seeded files, managed-block markers, ids and classes, the `AGENTS.md` budget, determinism, review, removal (undo and eject) |
| [messages-and-docs.md](messages-and-docs.md) | Voice and tone for CLI messages, agent-facing text, block text and documentation; the features page template |
| [compatibility-standards.md](compatibility-standards.md) | Versioning, breaking changes, presets across releases, adoption equivalence, deprecation windows, supported agents, coexistence, transparency, version pinning and release trust, update notices |
