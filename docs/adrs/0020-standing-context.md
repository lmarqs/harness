# 0020. Guidance is native program content

## Objective

**Problem.** Teams reuse coding guidance across repositories, but copying it by hand makes updates hard to share.
Putting every instruction in a frequently read file also makes narrow guidance cost attention wherever it is read.

**Goal.** Teams can review reusable guidance as native files and choose where it belongs without depending on Cook
being present in each agent session.

## Context

Repositories keep guidance in instruction files and skills. Some guidance applies broadly; other guidance concerns
particular files or topics.

The [architecture](../architecture.md#ingredients-recipes-and-cookbooks) and its
[requirements](../architecture.md#requirements) FR-1 and FR-2 assign native configuration models to
[ingredients](../features/glossary.md) and file operations to Cook. For example, acme can share general instructions
and a SQL review skill through a cookbook while choosing which native files each project receives.

## Decision

1. Ingredients model programs' native configuration and skills, and trusted Groovy recipes declare changes to those
   files. Guidance written into native files is consumed by the configured program directly
   ([0010](0010-dependence-on-the-binary.md)). Cook does not promise when a program loads a skill, whether an
   instruction enters a session, or whether an agent follows it.
2. Shared instruction sections use declared begin/end markers with the file format's comment syntax. Markers name the
   owning recipe and region ([0007](0007-ownership.md)). Ordered operations propose changes against current content,
   preserve unrelated text, and receive whole-batch diff review, including when the section was edited manually.

## Reasons

- **1.** FR-2 keeps reusable program knowledge with cookbook authors, while projects choose their instructions. The
  trade is maintaining native formats as programs evolve rather than one engine-owned context protocol. Native content
  remains usable without Cook, as explained in [0010](0010-dependence-on-the-binary.md). Separating file creation from
  program execution avoids promising delivery or compliance that a file operation cannot establish. The trade is that
  the program and its user determine loading and use.
- **2.** FR-7 through FR-11 give shared guidance the same reviewable operation contract as other configuration.
  Boundary markers preserve surrounding prose, and recorded ownership never blocks a change. The trade is that a
  declared replacement can change local guidance, and the actual diff must make that scope visible.

## Rejected alternatives

### Cook decides when guidance reaches a session

At its best, Cook loads guidance into an agent's session when the session starts, a path is touched or a prompt
matches, so delivery depends less on manual invocation or a model's relevance judgment. It loses because it needs
session state, mappings to each program's events and contracts for running agents, which make Cook run programs
rather than configure them ([0019](0019-action-kinds.md)).

### Put all guidance in one instruction file

At its best, one native file makes broad instructions easy to find without Cook. It loses because narrow guidance
then costs attention each time it is read. Recipes can configure separate native skills or files; this record
mandates neither a universal layout nor a context budget.

## Consequences

Costs:

- Cookbook authors maintain each program's native guidance formats.
- Writing guidance does not guarantee that a program loads or follows it.

Must be maintained:

- Readable native guidance and the distinction between reviewed file changes and session behavior.

## Confirmation

- Review a guidance change against FR-1, FR-2 and FR-7 through FR-11: in the acme example, does the recipe change only
  the declared instruction section or skill file and leave unrelated text intact?
- Read the resulting native files without Cook: is the guidance readable as it stands?
