# Repository guidance and context

## Objective

Repository instructions, coding standards and skills help people and coding agents find the guidance relevant to a
task. Repeating that guidance in every prompt makes it hard to maintain; putting everything in one instruction file
can crowd out the task itself.

## Native guidance files

In [Cook's architecture](../architecture.md), ingredients model programs' native files and configuration. A trusted
Groovy recipe can declare instruction files, skill files or references that a program supports. Cook reviews and
writes those declared file operations; the program decides how and when to load the resulting guidance.

Keep broadly applicable instructions short and link to focused guidance for particular areas. For example, a recipe
can propose a section in a repository's `AGENTS.md`:

```markdown
<!-- cook:begin agentic/context/guidance -->
Before changing database migrations, read `docs/database-conventions.md`.
Shared coding guidance lives in `.agents/skills/coding-guidelines/SKILL.md`.
<!-- cook:end agentic/context/guidance -->
```

These paths and the section name are repository choices. The HTML comments bound the text and name its owning recipe
and region; ownership never blocks a change. A recipe targeting that section preserves text outside it; its changes to
existing or edited content use the ordinary [batch diff and approval](render.md). Writing a pointer does not guarantee
that a program reads it or follows its instructions. Program-specific loading rules belong with the ingredient's
cookbook reference; see [agent configuration](workers.md).

## Sources and trust

Review guidance as instructions: anyone who changes it can steer the programs that read it. Keep issue text,
generated reports and other untrusted material clearly identified as data rather than copying it into directives.
Review shared skills and their source before distributing them. Keep secrets and personal settings out of shared
instruction files.

Prefer focused files to repeated copies, and maintain their links when files move. [Memory](memory.md) covers durable,
reviewed repository knowledge. [Safety](safety.md) explains the separate trust decision for executable recipes:
approving their file diff does not sandbox their evaluation.

## Session boundary

Ingredient/recipe APIs and hooks require a separate discussion.
