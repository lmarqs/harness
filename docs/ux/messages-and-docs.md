# Messages and documentation

## Objective

**Problem.** Developers, reviewers and coding agents act on the text the tool prints, puts into coding agents' sessions
and ships as documentation. When a message is vague, blames its reader, names the wrong thing or leaves out what to do
next, a person has to search for the next step, and a coding agent, which reads literally, takes a wrong action. When
documentation pages each take their own shape, or describe how things used to be, a reader looks in the wrong place or
acts on what is no longer true.

**Goal.** A person or a coding agent reading any message knows what to do next without searching, and both understand
it the same way. A reader of any documentation page finds what is true now, in the place they expect to find it.

## Overview

These rules set the voice, tone and structure of every text `the-cook` produces or ships: CLI messages, hook output,
`doctor` findings, injected standing context, the shipped skill, `the-cook explain`, managed-block content and the
documentation in this repository, and the names the documentation gives the parts of the lifecycle. Terms such as
standing context, managed block, module and shipped skill are defined in the [glossary](../features/glossary.md).

## Voice

| Rule | Write | Not |
| --- | --- | --- |
| Present tense, describe what is | `render writes AGENTS.md` | `render will write…`, `render now writes…` |
| Concise: one fact per sentence, no filler | `2 files drifted` | `It looks like there may be a couple of files that have drifted` |
| Name the thing exactly | `.cook/policy.yaml:14` | `your policy file` |
| Imperative for instructions | `run: ./cook render` | `you may want to consider running render` |
| Neutral, no blame, no apology | `AGENTS.md: block policy was edited by hand` | `you broke AGENTS.md`, `sorry, …` |
| Plain words | `refused`, `skipped`, `drift` | `oops`, `whoops`, exclamation marks, any emoji outside the icon set ([cli-standards.md](cli-standards.md#icons)) |
| No marketing | `renders permissions for each worker` | `seamlessly`, `powerful`, `blazing fast` |
| Consistent terms | the terms in the [glossary](../features/glossary.md), one term per concept ([names](#names-for-the-parts-of-the-lifecycle)) | alternating `profile`/`config`/`settings` for one file |

The same rules apply to text written for workers. A coding agent reads it literally: an ambiguity a person would resolve
from context becomes a wrong action.

## CLI messages

- The first line of every message on stderr starts with `the-cook: `, so it can be told apart from the output of
  commands `the-cook` runs; a result on stdout written to a pipe or file carries no prefix. On a TTY, under the
  conditions that allow color, a message written for a person starts with the icon of its kind and one space instead;
  text for a worker, a parser or a loop runner never does
  ([cli-standards.md](cli-standards.md#color-emoji-and-terminal-control)). A message that needs detail continues on
  indented lines below it.
- Warnings start with `warning: `; they never change the exit code, because a warning informs and must not fail a
  script.
- A message about a file names it relative to the repository root, with `:line:column` when known, so an editor or
  a worker can open it directly.
- Messages contain no ANSI escapes when the stream is not a TTY
  ([cli-standards.md](cli-standards.md#color-emoji-and-terminal-control)), so logs and transcripts stay readable.

## Error messages

Every error has three parts, in this order, because a reader needs to know what broke, then why, then what to do:

1. **What failed**: the command and the object, with its path or name.
2. **Why**: the cause in one sentence, with the found and the expected value when they exist.
3. **Next**: the exact command or edit that moves forward, copyable as is.

```text
the-cook: render --check: 2 files drifted from .cook/policy.yaml
  AGENTS.md: block policy differs (edited by hand)
  .agents/skills/the-cook/SKILL.md: content differs
  run: ./cook render
```

```text
the-cook: remove: .agents/skills/the-cook/SKILL.md was edited by hand and was not removed
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
person starts with the icon of its kind ([icons](cli-standards.md#icons)) instead of `the-cook: `, and the rest is
unchanged:

```text
🔥 render --check: 2 files drifted from .cook/policy.yaml
  run: ./cook render
```

```text
the-cook: render --check: 2 files drifted from .cook/policy.yaml
  run: ./cook render
```

The status word (`drifted`) is in both, because the emoji only decorates. The hook message never carries the emoji,
because it enters a worker's session.

- The next step names a command when one exists, in the documented invocation `./cook <command>`
  ([cli-standards.md](cli-standards.md#entry-points)). It never says only "check your configuration", because that
  leaves the reader where they started.
- When the next step is a person's decision (exit 3), the message says so and names the choices, so a worker stops and
  asks instead of guessing. For example, when `scaffold` needs a confirmation and no terminal is attached, its message
  names both ways forward: run it again with `--yes`, or run it from a terminal.
- Errors never print stack traces, internal type names or file paths inside the binary unless `--verbose` is given,
  because they mean nothing to the person who must act.

## Output for workers and scripts

- `--json` is the interface for workers and scripts. Human text may change wording between releases, but JSON fields may
  not ([cli-standards.md](cli-standards.md#json)). Why: a program that parses prose breaks on the next rewording.
- Each to-do item and `doctor` finding names the file, the problem and the next action, and is stable across runs for
  the same state, so a worker can track it.
- Hook output that reaches a worker's context is short: what ran, what failed, the command to reproduce it, because
  every line of it is paid in the worker's context.
- Session-start context lists commands and rules, not explanations a worker can load from the skill on demand, for
  the same reason.
- When the pinned binary is not cached, the session-start hook prints its stderr skip line and also adds exactly one
  line to the worker's context:
  `the-cook <version> is not cached on this machine; run ./cook version once to fetch it.` One line per session
  explains every missing hook, notification and injection without repeating on each tool call. The wrapper prints it
  from its template, because no binary is present.
- An injected standing-context pointer is exactly one line: `Apply the <name> skill now: read <path>`, so the worker
  learns what applies and where to read it, at the cost of one line.
- A handoff prints the recipe's text as the recipe wrote it, inputs substituted, followed by the exact command that
  resumes the run (`./cook make --continue <workspace>`). Why: the text is the repository's instruction to whoever
  drives the run, a person or a worker, and the command is the one next step `the-cook` itself can name. A recipe writes
  its handoffs and prompts by the voice rules above, because a worker reads them as literally as any message.

## Generated text in human files

Managed blocks hold derived facts: the conventions a module establishes, stated in plain language with their configured
values, command tables, an index of memory files, a summary of the effective permissions, static directives (`static`
standing-context entries), an import line that makes a worker's instructions file read `AGENTS.md`. Blocks contain no
prose that reads as a person's opinion, and nothing that duplicates prose outside the block, because the binary writes
only what it can derive. Explanations of a repository's choices are written by people or workers outside the markers.

The two block classes ([generated-content-standards.md](generated-content-standards.md#block-classes)) are written
differently:

- **Convention blocks** state facts that hold without `the-cook`, in the voice of the repository: "Workspaces in this
  project are created in `.work/workspaces/<name>`, on branch `work/<name>`, from committed code." They never name a
  `the-cook` command, the wrapper or `.cook/`, and never say they are managed beyond their marker. Why:
  `the-cook remove --eject` keeps their text, which must still be true and usable afterwards.
- **Tool blocks** explain how to use `the-cook` and name its commands in the documented form, `./cook <command>`.
  Why: eject removes them, so they may depend on the tool.
- Every block stays within the [`AGENTS.md` budget](../features/render.md#the-agentsmd-budget): one fact per line,
  detail left to `the-cook explain` and the skill, because workers read `AGENTS.md` in every session.

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
or a coding agent handed one section, takes everything under a heading as part of it, so an introduction left under the
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
- Prose names the tool `the-cook`, code-formatted, wherever it means the tool, and never capitalises it, even at the
  start of a sentence. Why: that is the name a reader types, so the word reads the same in a sentence and in a command.
  Its other parts take the names [below](#names-for-the-parts-of-the-lifecycle).
- No organization names in examples or in content `the-cook` carries; examples use `acme` for an organization and
  `acme/api` for a repository, and cookbooks in examples are `github:acme/cookbook`, because `the-cook` is open source
  and its docs must fit any organization.
- Feature and standards pages describe the lifecycle, its core and its three contracts. They name programs by role: the
  version control system, a worker, a jail, a loop runner, a task tracker, a task runner, a skills installer.
- A product appears only in a short example that plainly uses an ingredient from a cookbook, such as a worker `claude`
  declared with `ingredient: claude` pinned from `github:acme/cookbook`, never as a description of that program's
  behaviour, flags, file keys, versions or pitfalls. That knowledge belongs to the page of the cookbook that describes
  the program.
- An ADR names a product only in its Context, as checkable evidence of what exists. Why: the binary contains code and
  data for no program and ships no ingredient, no station and no recipe, so a page that describes a program's behaviour
  documents something `the-cook` does not hold and goes stale with that program's releases
  ([ingredients](../features/ingredients.md), [ADR 0022](../adrs/0022-tool-integration.md)).
- What `the-cook` does with a program is attributed to the ingredient that fills its role: "the version-control
  ingredient reports the default branch", "a floor rule of the version-control ingredient". Why: a page that says
  `the-cook` runs a particular program describes a binary that does not exist.
- Version control is described in the contract's terms: the shared default line of work, what is shared, learning what
  is shared, the default revision.
- One system's terms, such as a remote, `origin` or a fetch, appear only in an example of that system's ingredient, such
  as the `origin/<branch>` a git ingredient answers as the default revision. Why: `the-cook` depends on no version
  control system and asks as little of one as it can, and a page written in one system's words describes a contract the
  others seem unable to fill ([the version-control contract](../features/ingredients.md#the-version-control-contract)).
- Example commands are complete and run as written; example output is copied from a real run of the documented
  release, so a reader can trust what they copy.
- Commands that run inside a repository are written as `./cook <command>`, with `the-cook <command>` shown as the
  equivalent when `the-cook` is installed globally ([cli-standards.md](cli-standards.md#entry-points)). The standards in
  `docs/ux/` name commands `the-cook <command>`, the program's name.
- `the-cook explain <topic>` and the shipped skill follow the same rules; they are documentation shipped in the binary.

### Names for the parts of the lifecycle

Every page and message uses these names, one per concept, so that a reader who learns them on one page reads every
other page the same way:

| Concept | Name |
| --- | --- |
| what `the-cook` is | a declared software development lifecycle: the stages a change goes through, the events it fires between them, the guards that hold between stages and the proof a change carries |
| a program that changes the repository for someone, such as a coding agent, a bot or a codemod | a **worker**, declared under `workers:` with the role `interactive` (a session with a person) or `unattended` (an attempt) |
| one unattended run of a worker on one task, under the guards | an **attempt**, run by `the-cook attempt <worker>`; the rules it runs under are the **attempt contract** |
| the interface a worker's ingredient fills: renders, launch, unattended arguments, network arguments, bypass arguments, hook payload mapping, instructions file, skill location, native rule import | the **worker contract** |
| an unattended run of a loop runner whose command for each task is `the-cook attempt <worker>` or `the-cook make <recipe> --unattended` | a **loop run** |
| a worker session that drives `the-cook` commands for a person | a **coordinating worker** |
| the events a worker fires in a session: session start, post edit, prompt submit, attention, turn end | **worker events** |
| a named, versioned description of one program | an **ingredient**: a worker ingredient, the version-control ingredient, a loop runner's ingredient |
| what one way of working needs in place: the ingredients to pin, workers and their roles, entries, templates and the recipes to add | a **station**, which a repository applies once |
| one kind of change, made many times: inputs, requirements, steps mapped onto the lifecycle's stages, required lanes | a **recipe**, which a repository holds as its own copy in `.cook/recipes/<name>/` and runs |
| one pass of one change through the lifecycle's stages, as a recipe describes it | a **recipe run**, or the run, started by `the-cook make <recipe>` |
| a recipe run a person drives, at a terminal or through the worker in their session | an **attended** recipe run, the default |
| a recipe run nobody drives, which its caller, such as a loop runner, marks `--unattended` | an **unattended** recipe run |
| one entry of a recipe's `steps`, at Implement | a **step**, of kind `run`, `template`, `attempt` or `handoff` |
| a step that hands the recipe run to whoever drives it, a person or the interactive worker | a **handoff** |
| a source that publishes ingredients, stations and recipes by tag | a **cookbook** |
| the line of work every clone shares as its default | the **shared default line of work**: its name is the **default branch**, and the revision this checkout last learned it at is the **default revision** |
| what a checkout does to find out where the shared lines of work are | it **learns what is shared**, through the version-control contract's `update` |

- The concept is a worker whatever kind of program fills it, because the core treats a coding agent, a bot and a
  codemod the same way. "Coding agent" names the main example, and a product name appears only in a short example of a
  worker declared with an ingredient pinned from `github:acme/cookbook`.
- An ingredient renders and runs; a station is applied; a recipe is added, updated or created, and made: `make` runs
  it, and one execution is a recipe run. A page never says that a station renders, denies or runs anything, because a
  station has no renders and no floor: what it wrote is the repository's own, and the ingredients it pinned do the
  rendering. A page never says that a repository pins or applies a recipe, because the repository holds a copy of it,
  not a pin, and a recipe adds no entries; a station pins the recipes it adds only so that the copies it writes are
  verified.
- "Recipe" names only that unit. The one other use is the kitchen sentence that explains the tool's name. Why: in a tool
  named `the-cook`, a reader takes every "recipe" as a term, and a figurative one would send them to the wrong page.
- A recipe run is a "recipe run", or "the run" where nothing else on the page runs; a loop runner's pass over many
  tasks, the scope `run`, is "a loop runner's run". The command is `make`, never "run": `the-cook make <recipe>`.
  Why: `run` also names a step kind, an action kind, a scope and `lifecycle run`, so the noun needs its qualifier
  wherever two of them meet.
- Stages belong to the lifecycle and steps to a recipe: a recipe's steps run at the Implement stage, and its
  `workspace` and `verify` keys map onto Start and Verify. A page never calls a stage a step, or a step a stage. Why: a
  reader who mixes them looks for guards on a step or for a recipe's keys on a stage.
- A handoff pauses an attended run, or stops an unattended one as blocked; a page never says that a handoff fails,
  because it is a step the recipe asks for, not an error. Whether a run is attended is what its caller declares, never
  whether a terminal is attached.
- `AGENTS.md`, `.agents/`, model and effort keep their names. `AGENTS.md` and `.agents/` are file conventions of the
  agent ecosystem that a worker ingredient names; model and effort are settings a worker ingredient maps, and a worker
  whose program has no model ignores them. Why: a reader looks for these names where other tools already use them.
- Agentic coding is described as the main example of a station, never as the definition of `the-cook`. Why: a page
  that defines `the-cook` by one station makes every other way of running the lifecycle look like a misuse.
