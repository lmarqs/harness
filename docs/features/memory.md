# Memory

Memory is a repository's durable, reviewed knowledge for agents: patterns, conventions and lessons that are not
obvious from the code. `harness` provides the place for it and keeps an index of it in `AGENTS.md`. People and agents
write the content; `harness` never does.

## When to use it

- Agents keep relearning the same repository-specific pattern in every session.
- Reviews keep correcting the same mistake.
- Knowledge is too detailed for `AGENTS.md` but should be one link away from it.

Needs `modules.memory: true` in the [profile](profile.md).

## How it works

### The skeleton

When the module is on, `harness scaffold` (or the first `harness render` after turning the module on) creates
`.agents/memory/` with a starter file explaining what belongs there. From then on the directory and everything in it
is knowledge: `harness` does not own it, does not list it in the manifest, and never rewrites or deletes it.

### The index block

`harness render` writes the `memory-index` [managed block](render.md#managed-blocks) in `AGENTS.md`: a list of the
Markdown files under `.agents/memory/`, each linked by its relative path. Agents read `AGENTS.md` first and follow the
link that matches their task.

```markdown
<!-- harness:begin memory-index -->
- `.agents/memory/backend-patterns.md`
- `.agents/memory/testing-patterns.md`
<!-- harness:end memory-index -->
```

Adding, renaming or removing a memory file changes the block, so run `harness render` in the same commit;
`harness render --check` reports a stale index.

## Reference

| Path | Owner | Written by |
| --- | --- | --- |
| `.agents/memory/` | the repository | `scaffold` or `render` once (the skeleton), then people and agents |
| `AGENTS.md` block `memory-index` | `harness` | `render` |

Turning the module off, or `harness remove --module memory`, removes the block and leaves `.agents/memory/` in place.

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
Add the pagination rule we just agreed on to .agents/memory/backend-patterns.md, then run harness render.
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
| `render --check` fails on `memory-index` | A memory file was added or renamed without rendering | `harness render` |
| Agents never read memory | `AGENTS.md` is not loaded by the agent | Check the `import` block in `CLAUDE.md`; Codex reads the root `AGENTS.md` |
