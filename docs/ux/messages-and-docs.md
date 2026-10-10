# Messages and documentation

## Objective

**Problem.** Developers, reviewers and agents act on the text the tool prints, puts into agent sessions and ships as
documentation. When a message is vague, blames its reader, names the wrong thing or leaves out what to do next, a person
has to search for the next step, and an agent, which reads literally, takes a wrong action. When documentation pages
each take their own shape, or describe how things used to be, a reader looks in the wrong place or acts on what is no
longer true.

**Goal.** A person or an agent reading any message knows what to do next without searching, and both understand it the
same way. A reader of any documentation page finds what is true now, in the place they expect to find it.

## Overview

These rules set the voice, tone and structure of every text `the-cook` produces or ships: CLI messages, hook output,
`doctor` findings, injected standing context, the shipped skill, `the-cook explain`, managed-block content and the
documentation in this repository. Terms such as standing context, managed block, module and shipped skill are defined in
the [glossary](../features/glossary.md).

## Voice

| Rule | Write | Not |
| --- | --- | --- |
| Present tense, describe what is | `render writes .claude/settings.json` | `render will write…`, `render now writes…` |
| Concise: one fact per sentence, no filler | `2 files drifted` | `It looks like there may be a couple of files that have drifted` |
| Name the thing exactly | `.cook/policy.yaml:14` | `your policy file` |
| Imperative for instructions | `run: ./cook render` | `you may want to consider running render` |
| Neutral, no blame, no apology | `.claude/settings.json was edited by hand` | `you broke settings.json`, `sorry, …` |
| Plain words | `refused`, `skipped`, `drift` | `oops`, `whoops`, exclamation marks, any emoji other than the TTY prefix 😎 ([cli-standards.md](cli-standards.md#color-emoji-and-terminal-control)) |
| No marketing | `renders permissions for each agent` | `seamlessly`, `powerful`, `blazing fast` |
| Consistent terms | the terms in the [glossary](../features/glossary.md), one term per concept | alternating `profile`/`config`/`settings` for one file |

The same rules apply to text written for agents. Agents read it literally: an ambiguity a person would resolve from
context becomes a wrong action.

## CLI messages

- The first line of every message on stderr starts with `the-cook: `, so it can be told apart from the output of
  commands `the-cook` runs; a result on stdout written to a pipe or file carries no prefix. On a TTY, under the
  conditions that allow color, a message written for a person starts with 😎 and one space instead; text for an agent, a
  parser or a loop runner never does ([cli-standards.md](cli-standards.md#color-emoji-and-terminal-control)). A message
  that needs detail continues on indented lines below it.
- Warnings start with `warning: `; they never change the exit code, because a warning informs and must not fail a
  script.
- A message about a file names it relative to the repository root, with `:line:column` when known, so an editor or
  agent can open it directly.
- Messages contain no ANSI escapes when the stream is not a TTY ([cli-standards.md](cli-standards.md#color-emoji-and-terminal-control)),
  so logs and transcripts stay readable.

## Error messages

Every error has three parts, in this order, because a reader needs to know what broke, then why, then what to do:

1. **What failed**: the command and the object, with its path or name.
2. **Why**: the cause in one sentence, with the found and the expected value when they exist.
3. **Next**: the exact command or edit that moves forward, copyable as is.

```text
the-cook: render --check: 2 files drifted from .cook/policy.yaml
  .claude/settings.json: permissions.allow differs (edited by hand)
  .codex/rules/the-cook.rules: content differs
  run: ./cook render
```

```text
the-cook: remove: .codex/rules/the-cook.rules was edited by hand and was not removed
  its content no longer matches the manifest
  review the file, then run: ./cook remove --force
```

```text
the-cook: wrapper: v1.4.0 refused: the signature of its checksum file does not verify
  the release was not signed by a key this binary trusts
  keep the current pin, or confirm the release with its publisher
```

```text
the-cook: v0.9.0 not downloaded, hook skipped; run: ./cook version
```

The last example is the complete message a rendered hook prints when the pinned version is not in the machine's cache of
downloaded releases. The hook exits 0 and the session continues. The next step names `./cook`, the explicit entry
point that downloads the pinned version, because the hook itself never downloads.

The examples show each message as it reads in a pipe, a log or CI. On a TTY, the first line of a message written for a
person starts with 😎 instead of `the-cook: `, and the rest is unchanged:

```text
😎 render --check: 2 files drifted from .cook/policy.yaml
  run: ./cook render
```

```text
the-cook: render --check: 2 files drifted from .cook/policy.yaml
  run: ./cook render
```

The status word (`drifted`) is in both, because the emoji only decorates. The hook message never carries the emoji,
because it enters an agent's session.

- The next step names a command when one exists, in the documented invocation `./cook <command>`
  ([cli-standards.md](cli-standards.md#entry-points)). It never says only "check your configuration", because that
  leaves the reader where they started.
- When the next step is a person's decision (exit 3), the message says so and names the choices, so an agent stops and
  asks instead of guessing. For example, when `scaffold` needs a confirmation and no terminal is attached, its message
  names both ways forward: run it again with `--yes`, or run it from a terminal.
- Errors never print stack traces, internal type names or file paths inside the binary unless `--verbose` is given,
  because they mean nothing to the person who must act.

## Agent-readable output

- `--json` is the interface for agents and scripts. Human text may change wording between releases, but JSON fields may
  not ([cli-standards.md](cli-standards.md#json)). Why: a program that parses prose breaks on the next rewording.
- Each to-do item and `doctor` finding names the file, the problem and the next action, and is stable across runs for
  the same state, so an agent can track it.
- Hook output that reaches an agent's context is short: what ran, what failed, the command to reproduce it, because
  every line of it is paid in the agent's context.
- Session-start context lists commands and rules, not explanations an agent can load from the skill on demand, for
  the same reason.
- When the pinned binary is not cached, the session-start hook prints its stderr skip line and also adds exactly one
  line to the agent's context:
  `the-cook <version> is not cached on this machine; run ./cook version once to fetch it.` One line per session
  explains every missing hook, notification and injection without repeating on each tool call. The wrapper prints it
  from its template, because no binary is present.
- An injected standing-context pointer is exactly one line: `Apply the <name> skill now: read <path>`, so the agent
  learns what applies and where to read it, at the cost of one line.

## Generated text in human files

Managed blocks hold derived facts: the conventions a module establishes, stated in plain language with their configured
values, command tables, an index of memory files, a summary of the effective permissions, static directives (`static`
standing-context entries), an import line (`@AGENTS.md` in `CLAUDE.md`). Blocks contain no prose that reads as a
person's opinion, and nothing that duplicates prose outside the block, because the binary writes only what it can
derive. Explanations of a repository's choices are written by people or agents outside the markers.

The two block classes ([generated-content-standards.md](generated-content-standards.md#block-classes)) are written
differently:

- **Convention blocks** state facts that hold without `the-cook`, in the voice of the repository: "Worktrees in this
  project are created in `.work/worktrees/<name>`, on branch `work/<name>`, from committed code." They never name a
  `the-cook` command, the wrapper or `.cook/`, and never say they are managed beyond their marker. Why:
  `the-cook remove --eject` keeps their text, which must still be true and usable afterwards.
- **Tool blocks** explain how to use `the-cook` and name its commands in the documented form, `./cook <command>`.
  Why: eject removes them, so they may depend on the tool.
- Every block stays within the [`AGENTS.md` budget](../features/render.md#the-agentsmd-budget): one fact per line,
  detail left to `the-cook explain` and the skill, because agents read `AGENTS.md` in every session.

## Documentation structure

The documentation in this repository has four parts, each with one job:

| Part | Holds | Changes through |
| --- | --- | --- |
| `docs/philosophy.md` | the problems the project solves, its goals and its tenets; it decides nothing | an edit, in the same pull request as every ADR and standard the change affects |
| `docs/features/` | what each feature is and how to use it | ordinary pull requests |
| `docs/adrs/` | architecture decisions and their reasons; the authority over every other part | an edit to the ADR |
| `docs/ux/` | user-facing standards (this directory) | an edit to the standard, with its reason ([README.md](README.md#changing-a-standard)) |

Why: a reader who knows which question a part answers finds the answer without reading the others. ADRs follow their
own naming and title rules, stated in the [ADR index](../adrs/README.md).

### Every document opens with its objective

Every ADR, user-experience standard, feature page and top-level page opens, right after its title, with an
`## Objective` section in two parts:

- **Problem.** Who has the problem, and what goes wrong for them or what they need and lack.
- **Goal.** The outcome that holds once the problem is solved, stated so that a reader can tell whether it holds.

The section is a few plain sentences that a reader understands without any data. It names no solution: data, examples,
design and reference come after it. Why: a reader has to know why a subject is on the table before reading how it is
handled, or they judge it against a purpose they assumed. Something whose objective nobody can state should not exist
([philosophy](../philosophy.md#start-from-the-objective)).

The Objective section holds only those two parts. What the document is and what it covers follows under its own
heading: `## Overview` on a features page or a standard, `## Context` in an ADR. Why: a reader scanning the outline,
or an agent handed one section, takes everything under a heading as part of it, so an introduction left under the
objective reads as part of the problem or the goal.

### Features pages

Each page in `docs/features/` describes one command, file or concept, with these sections in this order, so a reader
moving between pages always finds the reference and the troubleshooting in the same place:

| Section | Content |
| --- | --- |
| Objective | the problem the feature solves and the goal it reaches ([above](#every-document-opens-with-its-objective)) |
| Overview | what it is and what it does, in two to five sentences |
| When to use | the situations it is for, and the closest situations it is not for, with the alternative |
| Reference | commands, flags, fields, defaults, allowed values, exit codes, files read and written, as tables |
| Examples | complete, runnable invocations or files, with the output they produce |
| Guidelines | recommended practice and the reason for each recommendation |
| Troubleshooting | symptom, cause, fix, one row per failure a user is likely to meet |

A section with no content is omitted rather than left empty.

### Writing rules for all documentation

- Present tense; describe what things are and how they behave. No page in `docs/` records history, ADRs included: no
  statuses, dates, release notes, roadmap or removed features. Why: a reader needs what is true now, and version
  control keeps the past.
- Each page stands alone: define a term on first use or link it to the [glossary](../features/glossary.md), because
  readers arrive from search and links, not from page one.
- A page states only what any reader can check: facts about `the-cook`, public facts about third-party tools, and worked
  examples set in a made-up organization. It never points at context the reader does not have, such as a survey of
  unnamed repositories, an earlier implementation, a conversation or "the original". Why: a claim that depends on
  unseen context reads as an unexplained reference to an outsider, and a concrete example makes the same point to
  everyone.
- An abstract rule is followed by a concrete example when the rule alone leaves a reader asking what it looks like,
  because most readers learn the rule from the example.
- Pages link to each other with relative links, use tables for reference material, and put commands, YAML and output in
  fenced code blocks with a language tag, because a relative link keeps working wherever the pages are read, a table is
  where a reader looks up a value, and a tagged block renders and copies exactly as written.
- Prose names the tool `the-cook`, code-formatted, wherever it means the tool, because that is the name a reader types:
  the same word reads the same in a sentence and in a command, and it never takes a capital letter, even at the start of
  a sentence. The generic idea keeps its plain words: an agent harness, a meta-harness, a harness recipe.
- No organization names in examples or in content `the-cook` carries; examples use `acme` for an organization and
  `acme/api` for a repository, because `the-cook` is open source and its docs must fit any organization. Third-party
  products (Claude Code, Codex, GitHub, ralph-tui, Beads, the `skills` CLI) are named where their behaviour matters,
  because a reader needs to know which tool a rule is about.
- What `the-cook` does with a program is attributed to the recipe that describes it: "the version-control recipe reports
  the default branch", "a floor rule of the git recipe". A program's own commands appear only as examples of what its
  recipe runs ("with the git recipe, `git worktree add`"). Why: the binary contains code for no program, so a page that
  says `the-cook` runs git describes a binary that does not exist ([recipes](../features/recipes.md),
  [ADR 0022](../adrs/0022-tool-integration.md)).
- Example commands are complete and run as written; example output is copied from a real run of the documented
  release, so a reader can trust what they copy.
- Commands that run inside a repository are written as `./cook <command>`, with `the-cook <command>` shown as the
  equivalent when `the-cook` is installed globally ([cli-standards.md](cli-standards.md#entry-points)). The standards in
  `docs/ux/` name commands `the-cook <command>`, the program's name.
- `the-cook explain <topic>` and the shipped skill follow the same rules; they are documentation shipped in the binary.
