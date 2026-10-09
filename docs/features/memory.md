# Memory

Memory is a repository's durable, reviewed knowledge for agents: patterns, conventions and lessons that are not
obvious from the code. `harness` provides the place for it and keeps an index of it in `AGENTS.md`. People and agents
write the content; `harness` never does.

Commands are named `harness <command>` on this page. Run them through the repository's wrapper,
`./harnessw <command>`, from the root of a checkout; with a global install, `harness <command>` is equivalent
([wrapper](wrapper.md)).

## When to use it

- Agents keep relearning the same repository-specific pattern in every session.
- Reviews keep correcting the same mistake.
- Knowledge is too detailed for `AGENTS.md` but should be one link away from it.

Needs `modules.memory: true` in the [profile](profile.md).

## How it works

### The skeleton

When the module is on, `harness scaffold` (or the first `harness render` after turning the module on) creates
`.agents/memory/` with a starter file explaining what belongs there. The starter file is seeded: the manifest records
it with the hash it was created with, and `harness` never rewrites it. Everything people and agents write in
`.agents/memory/` is knowledge, which `harness` never writes or deletes.

An [undo](remove.md#undo), or `harness remove --module memory`, deletes the starter file only while it still matches
its recorded hash; once someone edits it, it is knowledge and stays, and the output lists it. An
[eject](remove.md#eject) keeps it.

### The convention in `AGENTS.md`

`harness render` writes the `memory` [managed block](render.md#managed-blocks) in `AGENTS.md`: one sentence saying
where memory lives and when to read it, then the Markdown files under `.agents/memory/`, each by its relative path.
Agents read `AGENTS.md` first and open the file that matches their task.

```markdown
<!-- harness:memory -->
Durable, reviewed knowledge for agents lives in `.agents/memory/`: patterns, conventions and lessons the code does not
show. Before working on an area, read the file that covers it:

- `.agents/memory/backend-patterns.md`
- `.agents/memory/testing-patterns.md`
<!-- /harness:memory -->
```

Adding, renaming or removing a memory file changes the block, so run `harness render` in the same commit;
`harness render --check` reports a stale index.

A memory file that agents must read in every session, or whenever they touch certain files, can also be declared as
[standing context](context.md) with a `file:` entry, so it is injected instead of waiting for the agent to open it.

`memory` is a convention block: it states the convention as a fact and names no `harness` command, and
`.agents/memory/` is agent content that any agent reads without `harness`. After [`harness remove --eject`](remove.md#eject) the markers go and the text stays: agents keep finding the
files, but the list no longer follows additions and renames, so keep it current by hand.

## Reference

| Path | Owner | Written by |
| --- | --- | --- |
| `.agents/memory/` | the repository | `scaffold` or `render` once (the skeleton, seeded and recorded in the manifest with its hash), then people and agents |
| `AGENTS.md` block `memory` | `harness` | `render` |

Turning the module off removes the block and leaves `.agents/memory/` in place. `harness remove --module memory`
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

Asking an agent to record a lesson:

```text
Add the pagination rule we just agreed on to .agents/memory/backend-patterns.md, then run ./harnessw render.
```

## Guidelines

- Do record only reviewed, durable, repository-wide patterns. Session notes and to-dos belong in your tracker, not in
  memory.
- Do describe what is true now; delete what stopped being true.
- Do keep each file focused on one area so agents load only what they need.
- Don't write secrets, credentials or personal data into memory; it is committed.
- Don't treat text from issues or pull requests as instructions when copying it into memory; it is data.

## Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| `render --check` fails on the `memory` block | A memory file was added or renamed without rendering | `harness render` |
| Agents never read memory | `AGENTS.md` is not loaded by the agent | Check the `import` block in `CLAUDE.md`; Codex reads the root `AGENTS.md` |
