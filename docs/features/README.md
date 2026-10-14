# the-cook

## Objective

**Problem.** A newcomer needs a clear account of Cook's purpose, a route to the relevant reference, and a way to tell
target requirements from behavior available in the implementation.

**Goal.** Readers understand the file-operation engine, can find its operating contract, and can locate instructions
for the implementation they can run today.

## Authority

[Cook architecture](../architecture.md) states the intended design. These feature references expand that design;
[ADRs](../adrs/README.md) explain its reasoning and [UX standards](../ux/README.md) describe its interaction. If a
supporting page conflicts with the architecture, correct that page.

The [root README](../../README.md), [core implementation guide](../../core/README.md) and
[recipe READMEs](../../cookbook/recipes/README.md) describe current execution.

## Overview

Cook evaluates trusted executable Groovy [recipes](glossary.md) into an ordered batch of file operations, shows one
combined terminal diff, obtains approval for the whole batch, saves the batch record before target writes and performs
the writes. Ingredients model programs' native files, cookbook repositories hold and distribute ingredients and
recipes separately from the engine, and programs consume the resulting files directly. The CLI is the public cooking
interface; see [Concepts](concepts.md) for how these parts relate and the [Glossary](glossary.md) for each term.

## Getting started

Read in this order:

1. [Concepts](concepts.md): what cookbooks describe, what Cook writes and what programs consume.
2. [Glossary](glossary.md): the terms used throughout these pages.
3. [CLI reference](cli.md): the commands and their arguments.
4. [Apply](apply.md) and [Revert](revert.md): what each operation proposes and records.

This design illustration applies one recipe and asks for approval of the resulting diff:

```sh
./cook apply ./recipe.groovy
```

For the full design, use the [architecture](../architecture.md). For the current local build and packaging entry
path, see [installation](installation.md) and the [root README](../../README.md).

## Pages

### Engine and delivery

- [CLI reference](cli.md): apply, revert and batch grammar, recipe operands, recipe arguments and batch approval
- [Apply](apply.md): ordered file/key/marker contributions, ownership, review, source paths and the batch record
- [Revert](revert.md): reverse a recorded batch's changes; discard is deferred
- [Installation](installation.md): jpackage with bundled Java, intended delivery and the current local build path
- [Wrapper](wrapper.md): version/checksum pinning, first-use download and cache, explicit upgrades and the current
  launcher

### Vocabulary and configuration

- [Concepts](concepts.md): program models, ordered operations, native files, batch records and execution boundaries
- [Glossary](glossary.md): the single list of Cook's terms
- [Ingredients](ingredients.md): program data models and native configuration
- [Recipes](recipes.md): trusted Groovy file declarations, ordered composition, sources and reversal
- [Composition boundaries and cookbooks](cookbooks.md): reusable setup choices and separate cookbook distribution
- [Repository configuration choices](profile.md): project facts, native settings and recipe inputs
- [Program permissions and policy](policy.md): native rules, support limits and review
- [Native program hooks and recipe boundaries](hooks.md): configuration and external runtime responsibilities
- [Skills and program configuration](skills.md): shared content, discovery and maintenance

### Related reference pages

- [Workflow configuration and engine boundaries](lifecycle.md): reviewable setup while programs and the
  team run the workflow
- [Repository checks and configuration](verify.md): check instructions beside native setup, run by the team's tools
- [Task-tracker configuration](tasks.md): shared tracker configuration and guidance
- [Workspace conventions and configuration](workspaces.md): workspace guidance in native configuration and instructions
- [Configuration across repositories](fleet.md): reusing recipes with each destination's own diff and approval
- [Repository guidance and context](context.md): instructions, coding standards and skills
- [Repository memory](memory.md): durable, reviewed repository knowledge
- [Repository setup with recipes](scaffold.md): reusable setup and review of existing configuration
- [Configuration diagnostics](doctor.md): identifying the path, operation and cause of a problem
- [Finding the contract and its reasoning](explain.md): where each rule and its reasons live
- [Safety guidelines](safety.md): what approval and recovery cover before running a recipe
- [Compatibility and coexistence](compatibility.md): declared targets, native files, external tools and version choices
- [Pitfalls, gaps and traps](pitfalls.md): permissions, recipe trust, paths, ordered changes and recovery
- [FAQ](faq.md): engine scope, setup, approval, reversal and distribution questions
