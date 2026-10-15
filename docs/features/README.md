# the-cook

## Objective

**Problem.** A newcomer needs a clear account of Cook's purpose, a route to the relevant reference, and a way to tell
target requirements from behavior available in the implementation.

**Goal.** Readers understand the file-operation engine, can find its operating contract, and can locate instructions
for the implementation they can run today.

## Overview

Cook evaluates trusted executable Groovy [recipes](glossary.md) into an ordered batch of file operations, shows one
combined terminal diff, obtains approval for the whole batch, saves the batch record before target writes and performs
the writes. Ingredients model programs' native files, cookbook repositories hold and distribute ingredients and
recipes separately from the engine, and programs consume the resulting files directly. The CLI is the public cooking
interface.

[Cook architecture](../architecture.md) states the intended design. These pages expand it;
[ADRs](../adrs/README.md) explain its reasoning and [UX standards](../ux/README.md) describe its interaction. If a
supporting page conflicts with the architecture, correct that page. The [root README](../../README.md) and
[core implementation guide](../../core/README.md) describe current execution. The [cookbook](../../cookbook/README.md)
sketches how recipes and ingredients are written and distributed.

## Reading path

1. [Architecture](../architecture.md): responsibilities, ordered composition, ownership, batch records and
   distribution.
2. [Glossary](glossary.md): the terms used throughout these pages.
3. [CLI reference](cli.md): the commands and their arguments.
4. [Apply](apply.md): what applying recipes proposes and records.
5. [Revert](revert.md): how a recorded batch is reversed.
6. [Wrapper](wrapper.md): how a repository pins and obtains the engine.
7. [Pitfalls](pitfalls.md): traps in permissions, recipe trust, paths, ordered changes and recovery.

## Pages

| Page | Subject |
| --- | --- |
| [Glossary](glossary.md) | The single list of Cook's terms |
| [CLI reference](cli.md) | Apply, revert and batch grammar, recipe operands, recipe arguments and batch approval |
| [Apply](apply.md) | Ordered file, key and marker contributions, ownership, review, source paths and the batch record |
| [Revert](revert.md) | Reversing a recorded batch's changes; discard is deferred |
| [Wrapper](wrapper.md) | Version and checksum pinning, first-use download and cache, explicit upgrades and the current launcher |
| [Pitfalls, gaps and traps](pitfalls.md) | Symptoms, causes, Cook's responsibility and what to review |
| [This repository's agent setup](agentic-dogfood.md) | How this checkout applies its own agent recipes with the current CLI |

## Related documentation

- [UX standards](../ux/README.md): commands, configuration, generated content, skills, compatibility and where new
  behavior goes.
- [ADRs](../adrs/README.md): the decisions behind the architecture and their reasons.
