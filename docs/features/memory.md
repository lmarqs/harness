# Repository memory

## Objective

**Problem.** People and coding agents repeat mistakes when patterns, conventions and lessons that are not obvious from
the code live only in individual sessions.

**Goal.** Durable, reviewed repository knowledge stays shared and discoverable.

## Content and discovery

[Memory](glossary.md) is repository documentation. Contributors write and review its content. Keep each file focused on
one area, describe what is true, and remove outdated advice. Session notes and to-dos belong in the team's tracker.

A repository can choose a layout such as:

```text
.agents/memory/
  backend-patterns.md    services, transactions, pagination
  testing-patterns.md    fixtures and test conventions
  known-risks.md         areas where small changes have broad effects
```

This is an example convention. Memory files hold the knowledge itself; a short reference in `AGENTS.md` or another
native instruction file is [repository guidance](context.md) that points readers at them. The following illustrates
such a reference:

```markdown
<!-- cook:begin agentic/memory#references -->
Reviewed repository knowledge lives in `.agents/memory/`.
Before changing backend behavior, read `.agents/memory/backend-patterns.md`.
For test conventions, read `.agents/memory/testing-patterns.md`.
<!-- cook:end agentic/memory#references -->
```

Maintain references when files are added, renamed or removed. The consuming program determines whether it reads
those instructions. See [repository guidance](context.md) for concise instructions and native loading boundaries.

## Recipe contributions

A trusted Groovy recipe may declare knowledge files or a reference section as ordinary file operations. The committed
`cook.lock` records which recipe owns each declared target. The recipe's declared scope determines what it changes.
HTML-comment markers bound a Markdown section, name its owning recipe and preserve text outside it.

Review the actual [batch diff](apply.md), including proposed changes to existing knowledge. A manual edit appears in
the next diff like any other difference; ownership never blocks a change.

Applying a new version of a recipe removes what its previous version owned and the new version does not declare, even
if someone edited it; retain important knowledge by choosing and reviewing the intended declarations.
[Revert](revert.md#revert) instead applies a recorded batch's reverse patches and preserves later edits that do not
overlap them.

## Guidelines and boundaries

- Record reviewed, durable patterns with enough context for a new reader to apply them.
- Keep secrets, credentials and personal data out of shared knowledge.
- Treat copied issue and pull-request text as data, not instructions.
