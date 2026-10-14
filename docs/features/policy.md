# Program permissions and policy

## Objective

**Problem.** A team wants reviewed permission choices across its coding agents, but each program reads its own
configuration and interprets rules differently. Copying a rule between formats can lose its meaning or leave a gap.

**Goal.** Permission configuration expresses the team's choices in each program's native format, with support limits
visible to reviewers.

## Responsibilities

[Ingredients](ingredients.md) model a program's native configuration, including permission settings where supported.
Trusted Groovy [recipes](recipes.md) declare the file changes that express the repository's choices. Cook composes those
changes in order, displays the actual [batch](glossary.md) diff and obtains whole-batch approval before writes, as
defined by [the architecture](../architecture.md). The configured program interprets and enforces its own permissions.

A cookbook can provide policy inputs for its recipes; their format and prerequisites belong in that cookbook's usage
guide.

## Reviewing permission choices

Where a program supports allow, ask and deny decisions, use them to express what the team intends to permit, confirm
or prohibit. Record the reason for each group of rules, and review the effects of commands rather than assuming a
whole executable is harmless. A task runner can invoke other programs, and a broad prefix rule can permit more than
its author intended; see [a prefix matches the leading words only](pitfalls.md#a-prefix-matches-the-leading-words-only)
for a concrete case.

For each configured program, establish:

- What a rule matches: command prefixes, complete commands, paths, tools or another native form.
- How overlapping rules and unlisted actions are handled.
- Whether confirmation remains available in the mode in which the program runs.
- Which project, local, user or organization settings also affect the decision.
- Whether project trust or another setting is needed before the configuration is loaded.

A rule supported by one program may have no equivalent in another. Keep that limitation explicit in cookbook guidance
and repository instructions; do not present prose as enforcement or promise lossless translation. Program-specific
support belongs with the [program model](ingredients.md).

## Existing settings and changes

Review existing native rules before choosing file, key or section targets; see
[declared targets](../architecture.md#ordered-composition-and-declared-targets).

Do not copy personal permission settings into shared repository configuration without deciding whether the team wants
those choices. A program can continue to read personal settings independently of the files a recipe declares.

[Apply](apply.md) and [revert](revert.md#revert) change the declared permission files; neither operation proves that
the resulting permission configuration is appropriate for every program or user.

A recipe may also write a short permission explanation in a section of contributor instructions, bounded by
`cook:begin <recipe>#<region>` markers. Describe the native settings and their limitations.

## Trust and runtime limits

Trusting a recipe is separate from approving its declared file changes; see [safety guidelines](safety.md).
Permission settings written for an agent do not sandbox recipe evaluation.

[Native hooks](hooks.md) can provide program-specific feedback. They run under the consuming program with its
credentials, so review them alongside permission rules.
