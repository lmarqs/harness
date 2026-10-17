# User-experience standards

## Objective

**Problem.** Developers, reviewers, automation and teammates encounter Cook through its commands,
[diffs](../features/glossary.md) and the native files it changes. Inconsistent interactions make it hard to tell what
will change, what approval covers and how to recover from an operation.

**Goal.** Cook presents predictable commands and reviewable file changes, with clear approval and recovery behavior
for both interactive and noninteractive use.

## Overview

These pages describe the interaction of the file-operation engine: commands, output, configuration, generated content
and documentation. Cook evaluates trusted Groovy recipes, presents their combined terminal diff, obtains whole-batch
approval, records ownership in `cook.lock` and performs declared writes. Programs read their native
files directly. Recipe evaluation is trusted code, not sandboxed by the review flow.

## Index

| Page | Subject |
| --- | --- |
| [principles.md](principles.md) | Review, approval, recovery and trusted-recipe boundaries |
| [cli-standards.md](cli-standards.md) | Command grammar, output, errors, approval and noninteractive use |
| [when-cli-when-skill.md](when-cli-when-skill.md) | Engine, cookbook, guidance and external-tool responsibilities |
| [skills-standards.md](skills-standards.md) | Skill sources, native discovery, command guidance and review |
| [configuration-standards.md](configuration-standards.md) | File locations, recipe inputs, defaults, validation, native values, permission rules, hooks and secrets |
| [generated-content-standards.md](generated-content-standards.md) | Native files, sections and markers, guidance and memory files, stable output, review and discard |
| [messages-and-docs.md](messages-and-docs.md) | Message wording, engine vocabulary and documentation authority |
| [compatibility-standards.md](compatibility-standards.md) | Compatibility effects, native coexistence, recovery and engine pinning |

## Authority

[Cook architecture](../architecture.md) states the intended design. These standards and the
[ADRs](../adrs/README.md) follow it. Standards govern interactions within that design; when a standard or ADR conflicts
with the architecture, correct the supporting document.

Descriptions of current behavior in the [root README](../../README.md), [core guide](../../core/README.md) and recipe
READMEs must remain truthful. A requirement in these standards does not establish implementation, and a gap in
implementation does not invalidate the design.

Background:

- Goals and tenets: [philosophy](../philosophy.md)
- Architecture reasoning: [ADRs](../adrs/README.md)
- Feature navigation: [features](../features/README.md)
- CLI grammar and argument scope: [CLI reference](../features/cli.md)
- Ingredients, recipes and cookbooks: [architecture](../architecture.md#ingredients-recipes-and-cookbooks)
- Vocabulary: [glossary](../features/glossary.md)

## Who these standards protect

| User | Meets Cook through | Intended interaction |
| --- | --- | --- |
| Developer | CLI, recipe arguments and native files | See the actual combined diff and approve the whole batch before target writes |
| Automation or CI | CLI without an interactive terminal | Use explicit `--batch-approve` for preapproval; noninteractive execution alone does not authorize writes |
| Reviewer | Recipe, configuration and wrapper-pin changes | Inspect requested contributions and explicit version/checksum changes |
| Teammate who does not use Cook | Native program files | Consume those files directly without invoking Cook |
| Cookbook author | Ingredients and trusted Groovy recipes | Maintain program models and setup separately from engine releases |
| User removing setup | Discard | Review the removal of what a recipe owns, preserving content it does not own |

## Normative language

Plain imperatives and "must" state rules; a release that violates one is defective. "Should" states a default;
deviation needs a reason stated in the pull request that introduces it. "May" states an allowed option.

## Every rule carries its reason

Each normative rule states why it holds, in a "because" clause or a "Why:" sentence. A reason lets readers apply a rule
to an unforeseen case and judge whether it still serves its goal.

## Changing a standard

Edit standards in place to follow the architecture, and keep relevant reasons and useful cross-references; Git keeps
earlier versions. Correct contradictory definitions and document the agreed design ahead of its implementation. When a
product change is made, keep its implementation documentation accurate as well.

Review affected feature pages and ADRs when changing a standard. If a proposal changes the intended architecture, make
that decision explicit in the architecture and align the supporting documents. Cross-references may run in either
direction.
