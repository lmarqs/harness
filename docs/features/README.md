# the-cook

## Objective

**Problem.** A newcomer needs a clear account of Cook's purpose and a route to the relevant reference, without
mistaking a previous workflow proposal or a target requirement for behaviour available in the implementation.

**Goal.** Readers understand the file-operation engine, can find its operating contract, and can locate instructions
for the implementation they can run today.

## Authority

[Cook architecture](../architecture.md) states the intended design. These feature references expand that design;
[ADRs](../adrs/) explain its reasoning and [UX standards](../ux/) describe its interaction. If a supporting page
conflicts with the architecture, correct that page. Earlier lifecycle, ownership or adoption requirements do not
override the agreed design. Useful cross-references can run in either direction.

A target requirement does not establish that its behaviour is implemented. The [root README](../../README.md),
[core implementation guide](../../core/README.md) and cookbook usage guides describe current execution. Implementation
progress belongs in issues.

## Overview

Cook evaluates trusted executable Groovy recipes into an ordered batch of file operations. It shows one combined
terminal diff, obtains approval for the whole batch, saves recovery information before target writes and performs the
writes. Operations can include directories and symlinks; executing external commands is not a batch action. Approval
and output boundaries govern declared operations and do not sandbox arbitrary recipe code.

Ingredients model programs' native files and configuration. Cookbook repositories hold and distribute ingredients
and recipes, and can have builds separate from the engine. Programs consume the resulting files directly; configuring
an agent or loop runner does not make Cook its lifecycle controller.

The CLI is the public cooking interface; Gradle builds and packages the engine and cookbooks. The intended CLI offers
apply (the default), discard of supplied recipes' declared contributions, and revert of a recorded run. Existing or
edited content uses the same actual diff and batch approval, with no `--adopt` or separate adoption gate. See the
[CLI reference](cli.md), [apply](render.md), and [discard and revert](remove.md).

Personal setup backup (kit) and agent-session orchestration (cooking) are future capabilities tracked in
[issue #5](https://github.com/lmarqs/the-cook/issues/5) and
[issue #6](https://github.com/lmarqs/the-cook/issues/6). The engine does not own a fixed development lifecycle, workers,
verification receipts or jails. Ingredient/recipe APIs, composition, station roles and hooks remain a separate model
discussion.

## Getting started

For the design, use [architecture](../architecture.md) and the operation references below. For the current local
build and packaging entry path, see [installation](installation.md) and the [root README](../../README.md).
The wrapper's pinned-download design does not establish that published releases or that wrapper implementation exist.

## Pages

Engine and delivery

- [CLI reference](cli.md): apply, discard and revert grammar, exact recipe operands, recipe arguments and batch approval
- [Apply](render.md): ordered file/key/marker contributions, review, source paths and local history
- [Discard and revert](remove.md): delete declared contributions or restore a recorded run's prior values
- [Installation](installation.md): jpackage with bundled Java, intended delivery and the current local build path
- [Wrapper](wrapper.md): version/checksum pinning, first-use download and cache, explicit upgrades and the current launcher

Vocabulary and configuration

- [Concepts](concepts.md): program models, ordered operations, native files, history and execution boundaries
- [Glossary](glossary.md): vocabulary for the agreed engine responsibilities
- [Ingredients](ingredients.md): program data models and native configuration
- [Recipes](recipes.md): trusted Groovy file declarations, ordered composition, sources and reversal
- [Composition boundaries and cookbooks](stations.md): deferred station role and separate cookbook distribution
- [Repository configuration choices](profile.md): project facts, native settings and recipe inputs
- [Program permissions and policy](policy.md): native rules, support limits and review
- [Native program hooks and recipe boundaries](hooks.md): configuration and external runtime responsibilities
- [Skills and program configuration](skills.md): shared content, discovery and maintenance

Related reference pages

- [Workflow configuration and engine boundaries](lifecycle.md)
- [Repository checks and configuration](verify.md)
- [Task-tracker configuration](tasks.md)
- [Workspace conventions and configuration](workspaces.md)
- [Agent and worker configuration](workers.md)
- [Configuration across repositories](fleet.md)
- [Repository guidance and context](context.md)
- [Repository memory](memory.md)
- [Repository setup with recipes](scaffold.md): reusable setup and review of existing configuration
- [Configuration diagnostics](doctor.md)
- [Finding the contract and its reasoning](explain.md)
- [Safety guidelines](safety.md)
- [Compatibility](compatibility.md)
- [Pitfalls, gaps and traps](pitfalls.md)
- [FAQ](faq.md)
