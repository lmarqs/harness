# 0020. Guidance remains native program content

## Objective

**Problem.** Teams reuse coding guidance across repositories, but copying it by hand makes updates hard to share.
Putting every instruction in a frequently read file also makes narrow guidance cost attention wherever it is read.

**Goal.** Teams can review reusable guidance as native files and choose where it belongs without depending on Cook
being present in each agent session.

## Context

Repositories keep guidance in instruction files and skills. Some guidance applies broadly; other guidance concerns
particular files or topics. The original concern about placing all guidance in one instruction file still applies,
but it does not require Cook to supervise sessions or inject context.

The [architecture](../architecture.md#ingredients-recipes-and-cookbooks) and its
[requirements](../architecture.md#requirements) FR-1, FR-2 and FR-6 assign native configuration models to ingredients
and file operations to Cook. For example, acme can share general instructions and a SQL review skill through a cookbook
while choosing which native files each project receives.

## Decision

1. Ingredients model programs' native configuration and skills. Trusted Groovy recipes declare changes to those
   files; cookbook repositories hold and ship the models and recipes independently of the engine.
2. Guidance written into native files is consumed by the configured program directly. Cook does not promise when a
   program loads a skill, whether an instruction enters a session, or whether a worker follows it.
3. Shared instruction sections use declared begin/end markers with the file format's comment syntax. Markers name the
   owning recipe and region ([0007](0007-ownership.md)). Ordered operations propose changes against current content,
   preserve unrelated text, and receive whole-batch diff review, including when the section was edited manually.
4. Cook defines no standing-context trigger catalog, role budget, prompt injection channel or session cache here.
   Session orchestration remains future work in [issue #6](https://github.com/lmarqs/the-cook/issues/6).
   Ingredient/recipe APIs and hooks require the separate model discussion.

## Reasons

- **1.** FR-2 keeps reusable program knowledge with cookbook authors, while projects choose their instructions. The
  trade is maintaining native formats as programs evolve rather than one engine-owned context protocol.
- **2.** Native content remains usable without Cook, as explained in [0010](0010-dependence-on-the-binary.md).
  Separating file creation from program execution avoids promising delivery or compliance that a file operation
  cannot establish. The trade is that the program and its user determine loading and use.
- **3.** FR-7 through FR-11 give shared guidance the same reviewable operation contract as other configuration.
  Boundary markers preserve surrounding prose, and recorded ownership never blocks a change, so there is no
  manual-edit veto. The trade is that
  a declared replacement can change local guidance, and the actual diff must make that scope visible.
- **4.** FR-6 and FR-30 leave session execution and recipe hooks outside this design. The useful guidance concern
  remains, without turning a file configuration engine into a context-delivery service.

## Rejected alternatives

### Keep deterministic session, path and prompt triggers in Cook

Triggers could make guidance delivery less dependent on manual invocation or a model's relevance judgement. They
require session state, native-event mappings and worker execution contracts outside the agreed engine scope.

### Put all guidance in one instruction file

One native file can make broad instructions easy to find without Cook. Narrow guidance then costs attention each
time it is read. Recipes can configure separate native skills or files; this record mandates neither a universal
layout nor a context budget.

## Consequences

Costs:

- Cookbook authors maintain each program's native guidance formats.
- Writing guidance does not guarantee that a program loads or follows it.

Must be maintained:

- Readable native guidance and the distinction between reviewed file changes and session behavior.

## Confirmation

Review against FR-1, FR-2, FR-6, FR-7 through FR-11 and FR-30. In the acme example, a recipe changes the declared
instruction section or skill file while unrelated text stays intact. Native files remain readable without Cook;
no engine trigger, delivery budget or prompt channel is needed. These are design review criteria, not execution
checks or claims that deferred session behavior is implemented.
