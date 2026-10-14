# Messages and documentation

## Objective

**Problem.** People and coding agents act on messages and documentation literally. Vague wording, inconsistent names
or a missing next step leads them to guess. Contradictory descriptions of commands and approval make it harder to
judge what Cook will change.

**Goal.** A reader understands the requested operation, what approval covers and what to do next. Documentation gives
the intended design and current execution instructions in the places where readers expect them.

## Overview

These rules cover CLI messages, recipe-declared text in native files and repository documentation. They follow the
[architecture](../architecture.md): trusted Groovy recipes produce ordered file operations, reviewed as one terminal
diff with whole-batch approval. The [glossary](../features/glossary.md) defines engine vocabulary.

## Voice

| Rule | Write | Avoid |
| --- | --- | --- |
| Present tense; describe the operation | `apply proposes additions and updates` | `apply now replaces render` |
| Concise: one fact per sentence | `settings.toml: cannot read file` | `It looks like there may be a problem reading your configuration` |
| Name the thing exactly | `settings.toml:14` | `your settings` |
| Imperative for instructions | `Move --batch-approve before ./recipe.groovy.` | `You may want to consider moving the option.` |
| Neutral, no blame | `settings.toml: invalid value` | `You broke settings.toml.` |
| Plain words and no marketing | `writes native configuration` | `seamlessly configures everything` |
| Consistent terms | the [names below](#names-for-engine-concepts) | alternating undo, rollback and revert for one operation |

The same rules apply to guidance read by coding agents, because ambiguity can become an unintended action.

## CLI messages

- Follow the [CLI output rules](cli-standards.md#streams-and-human-output). A diagnostic on stderr starts with `the-cook: `
  so readers can identify its source. On a suitable TTY, a message for a person may use the documented status icon
  instead; retain a status word because an icon alone cannot explain the result
  ([terminal presentation](cli-standards.md#color-emoji-and-terminal-control)).
- Put supporting detail on indented lines. Identify the operation and affected file or source, because readers need
  to distinguish a recipe input failure from a target write failure.
- Name destination files relative to the invocation directory, with `:line:column` when known. Identify source files
  by their original location or URL when needed; a snapshot path alone obscures which source to edit.
- Distinguish warnings from failures in words. Do not assign numeric exit codes in message guidance; the agreed design
  does not select an exit-code table.
- Do not emit ANSI escapes to non-TTY streams, because logs and transcripts must remain readable. Formatting and
  progress messages must not hide or replace the final batch diff.

## Error messages

Every actionable error explains three things, in this order:

1. **What failed:** the operation and object, with its path or name.
2. **Why:** the cause, including found and expected values when useful.
3. **Next:** the command, edit or decision that addresses it.

The following examples illustrate wording for the intended CLI:

```text
the-cook: apply: unknown option --batch-approve after ./recipe.groovy
  batch options precede the first recipe operand
  run: ./cook apply --batch-approve ./recipe.groovy
```

```text
the-cook: apply: target ../shared/settings.toml is outside the destination
  declared writes must stay inside the invocation directory, including symlink resolution
  edit ./recipe.groovy to target a path inside the destination
```

```text
the-cook: revert: no record for batch 2026-10-14T15-04-05Z
  reverting a batch requires its record in .cook/batches/
  run: ./cook batch list
```

```text
the-cook: revert: 2026-10-14T15-04-05Z conflicts with later edits; nothing was written
  settings.toml: test
  resolve the listed edits, then run the revert again
```

Do not suggest re-evaluating a recipe as an equivalent reversal: apply proposes a recipe's current declarations,
whereas revert reverses one recorded batch's changes.

When approval needs a caller's decision, name the choices: run interactively to review and answer the prompt, or
explicitly preapprove with `--batch-approve` before the operands. Neither a noninteractive terminal nor the presence of
existing content authorizes writes.

Keep normal diagnostics focused on information the caller can act on, rather than internal type names or paths inside
the package. When a target write fails, describe rollback and the retained batch record and failure accurately. Do not
claim recovery of side effects outside the declared batch.

## Output for automation and coding agents

Human prose is for readers; scripts must not treat its wording as a stable data format
([CLI output](cli-standards.md#json)).

Use the same operation names and approval scope in interactive and noninteractive guidance. Display the final diff even
with `--batch-approve`; that flag covers the whole batch. A coding agent reading a message must be able to tell whether
target writes occurred, whether rollback was needed and what information remains for recovery. Do not imply that batch
review sandboxes trusted recipe execution.

Cook writes native configuration that other programs consume. Guidance about their prompts, hooks or skill discovery
belongs with their cookbook configuration.

## Generated text in human files

A recipe may declare a marked section in a shared instruction file. Markers bound the section and name its owner,
`cook:begin <recipe>/<region>` and `cook:end <recipe>/<region>`
([generated-content-standards.md](generated-content-standards.md#managed-blocks)). Keep instructions concise, avoid
duplicating surrounding guidance, and link to supporting detail where useful. State conventions using the values that
apply to the repository, so people and programs can understand them directly.

A section may contain conventions, explanations or Cook commands as the recipe requires. Manual edits inside the
declared section appear in the next batch diff; unrelated text outside it remains intact. Use HTML comments for markers
in Markdown.

## Documentation structure

| Part | Purpose |
| --- | --- |
| `docs/architecture.md` | Intended responsibilities, operations, boundaries and distribution; supporting design pages follow it |
| `docs/philosophy.md` | Problems, goals and tenets motivating the design |
| `docs/features/` | Feature and concept references, including CLI grammar and operation semantics |
| `docs/adrs/` | Architectural decisions and their reasons within the architecture |
| `docs/ux/` | Interaction and documentation standards within the architecture |
| Root and core usage guides | Truthful instructions and limitations for current implementation |
| Recipe READMEs under `cookbook/recipes/` | What one recipe writes, reads, accepts and rejects |

When a standard or ADR conflicts with the architecture, correct the supporting document. Track a missing implementation
in issues. See the [UX authority](README.md#authority) and [ADR index](../adrs/README.md).

### Every document opens with its objective

Every page opens by making its purpose and scope clear. ADRs use an `## Objective` section immediately after the
title, following the [ADR writing guide](../adrs/README.md#the-objective-comes-first). For standards, feature pages
and top-level pages, use an Objective section when it helps explain the problem and outcome:

- **Problem.** Who has the problem and what goes wrong or is missing.
- **Goal.** The outcome that addresses it, stated so a reader can tell whether it holds.

Keep an Objective section independent of design details, so readers understand the purpose before judging a solution.
A concise introduction after the title or under `## Overview` may instead establish purpose, scope and intended-design
versus current-implementation context, as in the architecture and CLI reference. Do not add a redundant Objective
section to an introduction that already serves that purpose. ADR facts follow under `## Context`; reference material
and examples follow under their own headings, because readers use the outline to find each kind of information.

### Feature pages

Use these sections where relevant, in this order, so readers find the same kind of information in the same place.
Omit empty sections; an opening introduction may replace Objective and Overview as described above.

| Section | Content |
| --- | --- |
| Objective | Who has the problem and the outcome the feature serves |
| Overview | What it is and what it does |
| When to use | Situations it addresses and relevant alternatives |
| Reference | Decided commands, options, inputs, defaults and affected files |
| Examples | Complete invocations with prerequisites, or clearly identified design illustrations |
| Guidelines | Recommended practice and its reason |
| Troubleshooting | Likely symptom, cause and next action |

Do not fill reference tables with undecided exit codes, hook phases, patch formats, metadata schemas or model APIs.
An explanation of the guarantee is sufficient when its concrete representation needs separate design.

### Recipe READMEs

A recipe README explains its recipe and nothing else. Its reader already knows the core and the architecture, so it
does not explain or link them; a recipe stays understandable when its directory is read on its own. Write each one
from the [recipe README template](../../cookbook/recipes/TEMPLATE.md), which fixes the section order.

### Writing rules for all documentation

- Use present tense for the intended design. In implementation guides, describe what actually runs and identify relevant
  limitations. Keep implementation progress in issues; Git retains earlier text.
- Make each page understandable when reached from search: define a term or link to the
  [glossary](../features/glossary.md) at first use.
- Support claims with accessible evidence. Avoid references to unnamed repositories or private conversations.
  An intended requirement comes from the architecture; implementation claims need implementation evidence.
- Follow an abstract rule with a concrete example when its meaning would otherwise be unclear. Label illustrative
  commands, diffs and output as examples of the intended interaction. Do not present invented output as a real run.
- Use relative links for repository pages, tables when they aid lookup, and language-tagged fenced blocks for commands
  and output. Link where useful in either direction.
- Use Cook or `the-cook` for the engine and `./cook` for the repository entry point. Show the equivalent global entry
  point when useful ([entry points](cli-standards.md#entry-points)). Use one term per engine concept.
- Use generic organizations such as `acme` in made-up examples. Recipe operands must still be exact supported paths or
  URIs, such as `https://example.com/cookbook/recipe.groovy`; do not invent a cookbook-name lookup scheme.
- Explain program-specific files and settings when they clarify native configuration. Attribute those choices to
  cookbook ingredients and recipes rather than promising engine contracts for every program. Name the program rather
  than inventing a generic role in its place.
- Keep commands aligned with the [CLI reference](../features/cli.md). Show batch options before operands and recipe
  arguments after their recipe. Document any real prerequisites in current-usage examples.
- Review affected links and index descriptions when changing a page. Retain useful paths; correct contradictory text in
  place.

### Names for engine concepts

| Concept | Name |
| --- | --- |
| Engine | Recipe driven configuration engine |
| Program's native files and configuration model | Ingredient |
| Trusted executable Groovy source describing requested file operations | Recipe |
| Repository holding and shipping ingredients and recipes | Cookbook |
| Ordered file operations reviewed and approved together | Batch |
| Declared addition or update against current content, with removal of what a recipe's previous version owned | Apply |
| Removal of everything `cook.lock` lists for a recipe (deferred) | Discard |
| Reversal of one recorded batch's changes, using its reverse patches | Revert |
| Identifier of a recorded batch: its UTC start time, such as `2026-10-14T15-04-05Z` | Batch ID |
| Recording of one batch under ignored `.cook/batches/<batchId>/` | Batch record |
| Committed YAML file recording each applied recipe's source, sha256 and owned targets | `cook.lock` |
| Current working directory where declared writes and deletions stay | Destination |
| Recipe location inside the repository or URL used to resolve relative source references | Source origin |
| `cook:begin <recipe>/<region>` and `cook:end <recipe>/<region>` delimiters naming a section's owner | Markers |

Use these terms consistently with the [glossary](../features/glossary.md). A recipe is evaluated; a batch is reviewed,
approved and recorded. History never marks a recipe as already applied.
