# Memory

## Objective

**Problem.** A coding agent starts every session without what the team has learned about the repository: the patterns,
conventions and lessons the code does not show. It repeats mistakes that reviewers already corrected, so reviewers
correct them again, and what one session learns is lost to the next. Knowledge kept in one person's head or private
notes never reaches the rest of the team or their coding agents.

**Goal.** What the team has reviewed and agreed about a repository reaches every coding agent working on the area it
covers, and reviewers do not correct the same mistake twice.

## Overview

Memory serves the Implement stage of the [development lifecycle](lifecycle.md): it is what a worker reads about an area
before it changes it, and the lessons of a change go into it once the change has landed.

Memory is a repository's durable, reviewed knowledge for the workers that read instructions, such as coding agents:
patterns, conventions and lessons that are not obvious from the code, such as "amounts are rounded once, at the domain
boundary" or "list endpoints paginate with a cursor, never an offset". `the-cook` provides the place for it and keeps an
index of it in `AGENTS.md`. People and workers write the content; `the-cook` never does.

Commands are named `the-cook <command>` on this page. Run them through the repository's wrapper,
`./cook <command>`, from the root of a checkout; with a global install, `the-cook <command>` is equivalent
([wrapper](wrapper.md)).

## When to use it

- Coding agents keep relearning the same repository-specific pattern in every session.
- Reviews keep correcting the same mistake.
- Knowledge is too detailed for `AGENTS.md` but should be one link away from it.

The module needs `modules.memory: true` in the [profile](profile.md).

## How it works

### The skeleton

When the module is on, `the-cook scaffold` (or the first `the-cook render` after turning the module on) creates
`.agents/memory/` with a starter file explaining what belongs there. The starter file is seeded: the
[manifest](concepts.md#ownership-and-the-manifest) (`.cook/manifest.json`) records it with the hash it was created
with, and `the-cook` never rewrites it. Everything people and workers write in `.agents/memory/` is knowledge, which
`the-cook` never writes or deletes.

An undo, or `the-cook remove --module memory`, deletes the starter file only while it still matches
its recorded hash. Once someone edits it, it is knowledge: it stays, and the output lists it. An
eject keeps it.

### The convention in `AGENTS.md`

`the-cook render` writes the `memory` [managed block](render.md#managed-blocks) in `AGENTS.md`: one sentence saying
where memory lives and when to read it, then the Markdown files under `.agents/memory/`, each by its relative path. A
worker that reads `AGENTS.md` reads it first and opens the file that matches its task.

```markdown
<!-- cook:memory -->
Durable, reviewed knowledge about this repository lives in `.agents/memory/`: patterns, conventions and lessons the
code does not show. Before working on an area, read the file that covers it:

- `.agents/memory/backend-patterns.md`
- `.agents/memory/testing-patterns.md`
<!-- /cook:memory -->
```

Adding, renaming or removing a memory file changes the block, so run `the-cook render` in the same commit;
`the-cook render --check` reports a stale index.

A memory file that workers must read in every session, or whenever they touch certain files, can also be declared as
[standing context](context.md) with a `file:` entry, so it is injected instead of waiting for the worker to open it.

`memory` is a [convention block](concepts.md#convention-blocks-and-tool-blocks). It states the convention as a fact and
names no `the-cook` command, and `.agents/memory/` follows the file conventions of the agent ecosystem, so a worker
that follows them finds it without `the-cook`. After `the-cook remove --eject` the markers go and the
text stays. Workers keep finding the files, but nothing updates the list on additions and renames, so keep it current
by hand.

## Reference

| Path | Owner | Written by |
| --- | --- | --- |
| `.agents/memory/` | the repository | `scaffold` or `render` once (the skeleton, seeded and recorded in the manifest with its hash), then people and workers |
| `AGENTS.md` block `memory` | `the-cook` | `render` |

Turning the module off removes the block and leaves `.agents/memory/` in place. `the-cook remove --module memory`
removes the block and also deletes the starter file while it is unedited; every other file stays.

## Examples

A Node API's memory:

```text
.agents/memory/
  README.md              what goes here (from the skeleton)
  backend-patterns.md    services, repositories, transactions
  testing-patterns.md    one behaviour per test, fixtures, e2e conventions
  known-risks.md         areas where a small change has a large blast radius
```

A Terraform repository's memory holds files such as `state-layout.md` and `module-conventions.md`; a Go repository's,
`error-handling.md` and `concurrency.md`.

Asking a coding agent to record a lesson, in the session where the person and the coding agent settled on a pagination
rule:

```text
Add the pagination rule to .agents/memory/backend-patterns.md, then run ./cook render.
```

## Guidelines

- Do record only reviewed, durable, repository-wide patterns. Session notes and to-dos belong in your task tracker
  ([tasks](tasks.md)), not in memory.
- Do describe what is true; delete what is false.
- Do keep each file focused on one area so workers load only what they need.
- Don't write secrets, credentials or personal data into memory; it is committed.
- Don't treat text from issues or pull requests as instructions when copying it into memory; it is data.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| `render --check` fails on the `memory` block | A memory file was added or renamed without rendering | `the-cook render` |
| A worker never reads memory | The worker does not load `AGENTS.md` | Check how its worker ingredient points it at `AGENTS.md` (`the-cook ingredient show <name>`), and that the ingredient is in `ingredients:` |
