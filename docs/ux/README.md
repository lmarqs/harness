# User-experience standards

## Objective

**Problem.** Developers, reviewers, automation and teammates encounter Cook through its commands, diffs and the native
files it changes. Inconsistent interactions make it hard to tell what will change, what approval covers and how to
recover from an operation.

**Goal.** Cook presents predictable commands and reviewable file changes, with clear approval and recovery behaviour
for both interactive and noninteractive use.

## Overview

These pages describe the interaction of the file-operation engine: commands, output, configuration, generated content
and documentation. Cook evaluates trusted Groovy recipes, presents their combined terminal diff, obtains whole-batch
approval, records recovery information and performs declared writes. Programs read their native files directly;
Cook does not run their development lifecycle. Recipe evaluation is trusted code, not sandboxed by the review flow.

## Authority

[Cook architecture](../architecture.md) states the intended design. These standards and the [ADRs](../adrs/) follow
it. Standards govern interactions within that design; when a standard or ADR conflicts with the architecture, correct
the supporting document. An obsolete rule does not override the agreed architecture.

Descriptions of current behaviour in the [root README](../../README.md), [core guide](../../core/README.md) and cookbook
usage guides must remain truthful. A requirement in these standards does not establish implementation, and a gap in
implementation does not invalidate the design. Implementation progress belongs in issues.

Background:

- Goals and tenets: [philosophy](../philosophy.md)
- Architecture reasoning: [ADRs](../adrs/)
- Feature navigation: [features](../features/)
- CLI grammar and argument scope: [CLI reference](../features/cli.md)
- Program data models: [ingredients](../features/ingredients.md)
- Trusted recipe sources: [recipes](../features/recipes.md)
- Vocabulary: [glossary](../features/glossary.md)
- Composition discussion: [stations](../features/stations.md)

Ingredient/recipe APIs, composition, station roles and lifecycle hooks need a separate discussion. Personal setup
backup (kit) and agent-session orchestration (cooking) remain future work in
[issue #5](https://github.com/lmarqs/the-cook/issues/5) and
[issue #6](https://github.com/lmarqs/the-cook/issues/6); these standards do not design them.

## Who these standards protect

| User | Meets Cook through | Intended interaction |
| --- | --- | --- |
| Developer | CLI, recipe arguments and native files | See the actual combined diff and approve the whole batch before target writes |
| Automation or CI | CLI without an interactive terminal | Use explicit `--batch-approve` for preapproval; noninteractive execution alone does not authorize writes |
| Reviewer | Recipe, configuration and wrapper-pin changes | Inspect requested contributions and explicit version/checksum changes |
| Teammate who does not use Cook | Native program files | Consume those files directly without invoking Cook |
| Cookbook author | Ingredients and trusted Groovy recipes | Maintain program models and setup separately from engine releases |
| User withdrawing or reversing setup | Discard and revert | Review deletion of declared contributions or restoration of a recorded run's prior values, preserving unrelated content |

## Normative language

The key words MUST, MUST NOT, REQUIRED, SHALL, SHALL NOT, SHOULD, SHOULD NOT, RECOMMENDED, MAY and OPTIONAL are
interpreted as described in BCP 14 (RFC 2119, RFC 8174) when, and only when, they appear in capitals. Lower-case uses
carry their ordinary English meaning. These terms apply within the architecture's agreed scope.

- MUST / MUST NOT: a release that violates the rule is defective.
- SHOULD / SHOULD NOT: deviation needs a reason stated in the pull request that introduces it.
- MAY: an allowed option; no justification needed.

## Index

| Page | Subject |
| --- | --- |
| [principles.md](principles.md) | Review, approval, recovery and trusted-recipe boundaries |
| [cli-standards.md](cli-standards.md) | Command grammar, output, errors, approval and noninteractive use |
| [when-cli-when-skill.md](when-cli-when-skill.md) | Where behaviour and guidance belong |
| [skills-standards.md](skills-standards.md) | Skill content and use |
| [configuration-standards.md](configuration-standards.md) | Native configuration, recipe inputs, destination paths and local history |
| [generated-content-standards.md](generated-content-standards.md) | Native files, markers, review and reversal |
| [messages-and-docs.md](messages-and-docs.md) | Message wording, engine vocabulary and documentation authority |
| [compatibility-standards.md](compatibility-standards.md) | Compatibility and distribution |

## Every rule carries its reason

Each normative rule states why it holds, in a "because" clause or a "Why:" sentence. A reason lets readers apply a rule
to an unforeseen case and judge whether it still serves its goal.

## Changing a standard

Edit standards in place to follow the architecture, preserving relevant reasons and useful cross-references. Correct
contradictory definitions rather than requiring implementation to catch up before documenting the agreed design.
When a product change is made, keep its implementation documentation accurate as well.

Review affected feature pages and ADRs when changing a standard. If a proposal changes the intended architecture,
make that decision explicit in the architecture and align the supporting documents. Earlier ADRs and standards do
not silently change the design. Cross-references may run in either direction; no acyclic reading order is required.

Git keeps earlier versions. These pages need no status banners or replacement registry.
