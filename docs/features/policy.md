# Program permissions and policy

## Objective

**Problem.** A team wants reviewed permission choices across its coding agents, but each program reads its own
configuration and interprets rules differently. Copying a rule between formats can lose its meaning or leave a gap.

**Goal.** Permission configuration expresses the team's choices in each program's native format, with support limits
visible to reviewers.

## Responsibilities

[Ingredients](ingredients.md) model a program's native configuration, including permission settings where supported.
Trusted Groovy [recipes](recipes.md) declare the file changes that express the repository's choices. Cook composes
those changes in order, displays the actual batch diff and obtains whole-batch approval before writes, as defined by
[the architecture](../architecture.md). The configured program interprets and enforces its own permissions.

There is no required engine policy file or shared permission schema. A cookbook can provide policy inputs for its
recipes; their format and prerequisites belong in that cookbook's usage guide. The engine does not impose a safety
floor, worker roles, unattended-launch rules or a universal decision order across programs.

## Reviewing permission choices

Where a program supports allow, ask and deny decisions, use them to express what the team intends to permit, confirm
or prohibit. Record the reason for each group of rules, and review the effects of commands rather than assuming a
whole executable is harmless. A task runner can invoke other programs, and a broad prefix rule can permit more than
its author intended.

For each configured program, establish:

- What a rule matches: command prefixes, complete commands, paths, tools or another native form.
- How overlapping rules and unlisted actions are handled.
- Whether confirmation remains available in the mode in which the program runs.
- Which project, local, user or organization settings also affect the decision.
- Whether project trust or another setting is needed before the configuration is loaded.

A rule supported by one program may have no equivalent in another. Keep that limitation explicit in cookbook guidance
and repository instructions; do not present prose as enforcement or promise lossless translation. Program-specific
support belongs with the [program model](ingredients.md), rather than in the engine.

## Existing settings and changes

Review existing native rules before choosing file, key or section targets. A whole-file declaration affects the whole
file; a key or marked section can preserve unrelated content. Existing and manually edited targets use the same actual
diff and approval flow. There is no `--adopt`, adoption alias, separate import gate or ownership-state prerequisite.

Do not copy personal permission settings into shared repository configuration without deciding whether the team wants
those choices. A program can continue to read personal settings independently of the files a recipe declares.

Applying another recipe does not remove rules an earlier recipe owns. Applying a new version of a recipe removes the
rules its previous version owned and the new version no longer declares, and the diff shows that removal.
[Revert](remove.md#revert) applies a recorded batch's reverse patches against current content. Neither operation
proves that the resulting permission configuration is appropriate for every program or user.

A recipe may also write a short permission explanation in contributor instructions. Use format-appropriate markers
for a shared section, including HTML comments in Markdown. Describe the native settings and their limitations;
markers name the owning recipe and region (`cook:begin <recipe>/<region>`) and do not protect the rules from
editing.

## Trust and runtime limits

Trusting a recipe is separate from approving its declared file changes. Recipes are executable code: their arbitrary
side effects are outside batch review and recovery. Permission settings written for an agent do not sandbox recipe
evaluation. See [safety guidelines](safety.md).

[Native hooks](hooks.md) can provide program-specific feedback, but this design defines no hook-based permission
engine. Agent-session orchestration is future work in [issue #6](https://github.com/lmarqs/the-cook/issues/6), not a
permission-enforcement responsibility of Cook.
