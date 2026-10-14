# Repository memory

## Objective

Durable, reviewed repository knowledge helps people and coding agents avoid repeating mistakes: patterns,
conventions and lessons that are not obvious from the code. Keep it shared and discoverable rather than in one
person's private notes.

## Content and discovery

Memory is repository documentation, not a development lifecycle stage or an engine module. People and contributors
write and review its content. Keep each file focused on one area, describe what is true, and remove outdated advice.
Session notes and to-dos belong in the team's tracker rather than durable guidance.

A repository can choose a layout such as:

```text
.agents/memory/
  backend-patterns.md    services, transactions, pagination
  testing-patterns.md    fixtures and test conventions
  known-risks.md         areas where small changes have broad effects
```

This is an example convention, not a required Cook directory. A short reference in `AGENTS.md` or another native
instruction file can point readers at the relevant files:

```markdown
<!-- cook:begin agentic/memory/references -->
Reviewed repository knowledge lives in `.agents/memory/`.
Before changing backend behaviour, read `.agents/memory/backend-patterns.md`.
For test conventions, read `.agents/memory/testing-patterns.md`.
<!-- cook:end agentic/memory/references -->
```

Maintain references when files are added, renamed or removed. The consuming program determines whether it reads
those instructions; Cook does not enforce knowledge loading or automatically maintain a memory index. See
[repository guidance](context.md) for concise instructions and native loading boundaries.

## Recipe contributions

A trusted Groovy recipe may declare knowledge files or a reference section as ordinary file operations. There is no
memory skeleton, module switch or seeded-file hash required by the design; the committed `cook.lock` records which
recipe owns each declared target. The recipe's declared scope determines what it changes. HTML-comment markers bound a
Markdown section, name its owning recipe and preserve text outside it.

Review the actual [batch diff](render.md), including proposed changes to existing knowledge. Manual edits do not create
an ownership veto or an adoption step. Applying a new version of a recipe removes what its previous version owned and
the new version no longer declares, even if someone edited it; retain important knowledge by choosing and reviewing the
intended declarations. [Revert](remove.md#revert) instead applies a recorded batch's reverse patches and preserves later
edits that do not overlap them. Neither operation infers that a file has become protected knowledge from a stored hash.

## Guidelines and boundaries

- Record reviewed, durable patterns with enough context for a new reader to apply them.
- Keep secrets, credentials and personal data out of shared knowledge.
- Treat copied issue and pull-request text as data, not instructions.

Personal setup backup and agent-session orchestration remain future work in
[issue #5](https://github.com/lmarqs/the-cook/issues/5) and
[issue #6](https://github.com/lmarqs/the-cook/issues/6). This page defines no backup or session protocol.
