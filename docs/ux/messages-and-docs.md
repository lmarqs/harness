# Messages and documentation

Voice, tone and structure for every text harness produces or ships: CLI messages, hook output, `doctor` findings, the
harness skill, `harness explain`, managed-block content and the documentation in this repository.

## Voice

| Rule | Write | Not |
| --- | --- | --- |
| Present tense, describe what is | `render writes .claude/settings.json` | `render will write…`, `render now writes…` |
| Concise: one fact per sentence, no filler | `2 files drifted` | `It looks like there may be a couple of files that have drifted` |
| Name the thing exactly | `.agents/policy.yaml:14` | `your policy file` |
| Imperative for instructions | `run: harness render` | `you may want to consider running render` |
| Neutral, no blame, no apology | `.claude/settings.json was edited by hand` | `you broke settings.json`, `sorry, …` |
| Plain words | `refused`, `skipped`, `drift` | `oops`, `whoops`, exclamation marks, emoji |
| No marketing | `renders permissions for each agent` | `seamlessly`, `powerful`, `blazing fast` |
| Consistent terms | the terms in the [glossary](../features/glossary.md), one term per concept | alternating `profile`/`config`/`settings` for one file |

The same rules apply to text written for agents. Agents read it literally: an ambiguity a person would resolve from
context becomes a wrong action.

## CLI messages

- Every message on stderr starts with `harness: `, so it can be told apart from the output of commands harness runs.
  A message that needs detail continues on indented lines below it.
- Warnings start with `warning: `; they never change the exit code.
- A message about a file names it relative to the repository root, with `:line:column` when known.
- Messages contain no ANSI escapes when the stream is not a TTY ([cli-standards.md](cli-standards.md#color-and-terminal-control)).

## Error messages

Every error has three parts, in order:

1. **What failed**: the command and the object, with its path or name.
2. **Why**: the cause in one sentence, with the found and the expected value when they exist.
3. **Next**: the exact command or edit that moves forward, copyable as is.

```text
harness: render --check: 2 files drifted from .agents/policy.yaml
  .claude/settings.json: permissions.allow differs (edited by hand)
  .codex/rules/harness.rules: content differs
  run: harness render
```

```text
harness: remove: .mise/tasks/verify was edited by hand and was not removed
  its content no longer matches the manifest
  review the file, then run: harness remove --force
```

```text
harness: not installed, hook skipped
```

The last example is the complete message a rendered hook prints when the binary is missing. It has no next step
because the hook exits 0 and the session continues.

- The next step names a command when one exists. It never says only "check your configuration".
- When the next step is a person's decision (exit 3), the message says so and names the choices.
- Errors never print stack traces, internal type names or file paths inside the binary unless `--verbose` is given.

## Agent-readable output

- `--json` is the interface for agents and scripts; human text may change wording between releases, JSON fields may
  not ([cli-standards.md](cli-standards.md#json)).
- Each to-do item and `doctor` finding names the file, the problem and the next action, and is stable across runs for
  the same state, so an agent can track it.
- Hook output that reaches an agent's context is short: what ran, what failed, the command to reproduce it.
- Session-start context lists commands and rules, not explanations an agent can load from the skill on demand.

## Generated text in human files

Managed blocks hold derived facts: command tables, file tables, an index of memory files, a policy summary, an
import line. They contain no prose that reads as a person's opinion, and nothing that duplicates prose outside the
block. Explanations of a repository's choices are written by people or agents outside the markers.

## Documentation structure

The documentation in this repository has three parts, each with one job:

| Directory | Holds | Changes through |
| --- | --- | --- |
| `docs/features/` | what each feature is and how to use it | ordinary pull requests |
| `docs/adrs/` | architecture decisions and their reasons | a new ADR |
| `docs/ux/` | user-facing standards (this directory) | a UX record |

### Features pages

Each page in `docs/features/` describes one command, file or concept, with these sections in this order:

| Section | Content |
| --- | --- |
| Overview | what it is and what it does, in two to five sentences |
| When to use | the situations it is for, and the closest situations it is not for, with the alternative |
| Reference | commands, flags, fields, defaults, allowed values, exit codes, files read and written, as tables |
| Examples | complete, runnable invocations or files, with the output they produce |
| Guidelines | recommended practice and the reason for each recommendation |
| Troubleshooting | symptom, cause, fix, one row per failure a user is likely to meet |

A section with no content is omitted rather than left empty.

### Writing rules for all documentation

- Present tense; describe what things are and how they behave. No history, release notes, roadmap or removed features
  outside ADRs and UX records, which keep their history by design.
- Each page stands alone: define a term on first use or link it to the [glossary](../features/glossary.md).
- Relative links between pages. Tables for reference material. Fenced code blocks with a language tag for commands,
  YAML and output.
- No organization or company names. Examples use `acme` for an organization and `acme/api` for a repository.
- Example commands are complete and run as written; example output is copied from a real run of the documented
  release.
- `harness explain <topic>` and the harness skill follow the same rules; they are documentation shipped in the binary.
