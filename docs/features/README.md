# the-cook

## Objective

**Problem.** A newcomer needs a clear account of Cook's purpose, a route to the relevant reference, and a way to tell
target requirements from behavior available in the implementation.

**Goal.** Readers understand the file-operation engine, can find its operating contract, and can locate instructions
for the implementation they can run today.

## Authority

[Cook architecture](../architecture.md) states the intended design. These feature references expand that design;
[ADRs](../adrs/) explain its reasoning and [UX standards](../ux/) describe its interaction. If a supporting page
conflicts with the architecture, correct that page. Useful cross-references can run in either direction.

The [root README](../../README.md), [core implementation guide](../../core/README.md) and recipe READMEs describe
current execution. Implementation progress belongs in issues.

## Overview

Cook evaluates trusted executable Groovy recipes into an ordered batch of file operations. It shows one combined
terminal diff, obtains approval for the whole batch, saves recovery information before target writes and performs the
writes. Operations can include directories and symlinks; executing external commands is not a batch action. Approval
and output boundaries govern declared operations and do not sandbox arbitrary recipe code.

Ingredients model programs' native files and configuration. Cookbook repositories hold and distribute ingredients
and recipes, and can have builds separate from the engine. Programs consume the resulting files directly.

The CLI is the public cooking interface; Gradle builds and packages the engine and cookbooks. The intended CLI offers
apply (the default), revert of a recorded batch, and read-only batch listing and inspection. The committed `cook.lock`
records which recipe owns each section, key and file; ownership never blocks a change. Existing or edited content uses
the same actual diff and batch approval. See the [CLI reference](cli.md), [apply](apply.md), and
[revert](revert.md).

Personal setup backup (kit) and agent-session orchestration (cooking) are future capabilities tracked in
[issue #5](https://github.com/lmarqs/the-cook/issues/5) and
[issue #6](https://github.com/lmarqs/the-cook/issues/6).

## Getting started

For the design, use [architecture](../architecture.md) and the operation references below. For the current local
build and packaging entry path, see [installation](installation.md) and the [root README](../../README.md).

## Pages

Engine and delivery

- [CLI reference](cli.md): apply, revert and batch grammar, recipe operands, recipe arguments and batch approval
- [Apply](apply.md): ordered file/key/marker contributions, ownership, review, source paths and local history
- [Revert](revert.md): reverse a recorded batch's changes; discard is deferred
- [Installation](installation.md): jpackage with bundled Java, intended delivery and the current local build path
- [Wrapper](wrapper.md): version/checksum pinning, first-use download and cache, explicit upgrades and the current launcher

Vocabulary and configuration

- [Concepts](concepts.md): program models, ordered operations, native files, history and execution boundaries
- [Glossary](glossary.md): vocabulary for the agreed engine responsibilities
- [Ingredients](ingredients.md): program data models and native configuration
- [Recipes](recipes.md): trusted Groovy file declarations, ordered composition, sources and reversal
- [Composition boundaries and cookbooks](cookbooks.md): deferred station role and separate cookbook distribution
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
- [Compatibility and coexistence](compatibility.md): declared targets, native files, external tools and version choices
- [Pitfalls, gaps and traps](pitfalls.md): permissions, recipe trust, paths, ordered changes and recovery
- [FAQ](faq.md): engine scope, setup, approval, reversal and distribution questions
