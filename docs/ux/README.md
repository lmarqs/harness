# User-experience standards

These pages hold the rules for everything `harness` presents to the people, agents and systems that meet it:
commands, flags, output, exit codes, configuration files, generated content, skills and documentation. They are
normative and durable, like architecture decision records, but they govern how harness is experienced rather than how
it is built.

- Architecture decisions: [../adrs/](../adrs/)
- Individual features and their reference: [../features/](../features/)
- Terms (profile, policy, managed block, manifest, receipt, preset, lane, …): [glossary](../features/glossary.md)

A standard here applies to every feature, present and future. When the binary, a generated file, the harness skill or
a features page disagrees with a standard, that artifact is wrong until a UX record changes the standard.

## Who these standards protect

| User | Meets harness through | What the standards guarantee |
| --- | --- | --- |
| Developer in an adopted repository | the CLI, mise wrappers, generated files in pull requests | predictable commands, readable diffs, nothing written without being shown first |
| Interactive agent (Claude Code, Codex) | rendered settings, hooks, `AGENTS.md` blocks, the harness skill, `harness explain` | parseable output, exact next commands, text that matches the binary |
| Unattended agent (Ralph coordinator or worker) | the CLI without a terminal, exit codes, JSON | no prompts, a distinct "blocked" outcome, no hidden state |
| CI | the optional `harness render --check` and `harness doctor` steps | offline, deterministic checks; nothing required |
| Repository owner or teammate who does not use harness | committed files only | a clone that builds, tests and runs agents without the binary |
| Pull-request reviewer | diffs of rendered files and managed blocks | every generated change visible and traceable to a source file |
| Workflow maintainer | `fleet`, presets, lifecycle actions, schemas | one way to do each thing, safe across many repositories; nothing leaves a clone undeclared |
| Open-source user | any of the above | no organization-specific content in the binary, its skill or its docs |

## Normative language

The key words MUST, MUST NOT, REQUIRED, SHALL, SHALL NOT, SHOULD, SHOULD NOT, RECOMMENDED, MAY and OPTIONAL are
interpreted as described in BCP 14 (RFC 2119, RFC 8174) when, and only when, they appear in capitals. Lower-case uses
carry their ordinary English meaning.

- MUST / MUST NOT: a release that violates the rule is defective.
- SHOULD / SHOULD NOT: deviation needs a reason stated in the pull request that introduces it.
- MAY: an allowed option; no justification needed.

## Proposing a change: the UX record

A standard changes only through a UX record merged in the same pull request as the edited standard. Typo fixes and
clarifications that do not change a rule need no record.

A UX record is a file `records/<NNNN>-<slug>.md` in this directory, numbered in sequence. Like an ADR, it keeps its
history: a record is never edited after acceptance except to set its status to `superseded by <NNNN>`.

| Section | Content |
| --- | --- |
| Status | `proposed`, `accepted`, `superseded by <NNNN>` |
| Standards changed | the pages and rules affected, by heading |
| Users affected | rows from the table above |
| Problem | what users experience under the current standard, with evidence (a transcript, an issue, a failing agent run) |
| Rule | the new normative text, exactly as it will appear in the standard |
| Compatibility | whether the change is breaking under [compatibility-standards.md](compatibility-standards.md), and the deprecation path if so |
| Alternatives | the options considered and why each was not chosen |

A record is accepted when its pull request is merged. Code that implements the new rule MAY land in the same pull
request or later; until it lands, the standard describes the target and the gap is tracked as an issue.

## Index

| Page | Governs |
| --- | --- |
| [principles.md](principles.md) | The user-facing principles every other standard derives from, and how they rank when they conflict |
| [cli-standards.md](cli-standards.md) | Command grammar, naming, flags, output, exit codes, errors, confirmations, dry-run, non-interactive use, environment variables, help, version, deprecation |
| [when-cli-when-skill.md](when-cli-when-skill.md) | Where new behaviour goes: command, flag, profile field, lifecycle action, skill, preset or the repository itself |
| [skills-standards.md](skills-standards.md) | The harness skill, organization skills and repository skills: when to create each, structure, evaluation |
| [configuration-standards.md](configuration-standards.md) | User-editable files: schemas, defaults, validation, policy entry forms, lifecycle actions, presets, evolution, what never becomes a setting |
| [generated-content-standards.md](generated-content-standards.md) | Everything harness writes into a repository: headers, markers, determinism, review, removal |
| [messages-and-docs.md](messages-and-docs.md) | Voice and tone for CLI messages, agent-facing text and documentation; the features page template |
| [compatibility-standards.md](compatibility-standards.md) | Versioning, breaking changes, preset resolution, adoption equivalence, deprecation windows, supported agents, coexistence, transparency, update notices |
