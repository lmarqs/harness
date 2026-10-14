# Agent configuration

## Objective

**Problem.** Coding agents, bots and codemods use different native files and configuration. Keeping shared instructions
and program settings consistent by hand repeats work and can leave programs with different guidance or permissions.

**Goal.** Share the configuration those programs consume while keeping responsibility for their execution with the
people and tools that run them.

## Native program files

This page covers coding agents and the other programs that change a repository for someone, such as bots and
codemods.

Cookbook [ingredients](glossary.md) model each program's native files and configuration, as described in
[Cook's architecture](../architecture.md). Recipes use those models to declare file operations, including settings,
permission rules, shared instructions and skills where the program supports them. Programs consume the resulting files
directly.

For example, a recipe can own the `permissions.allow` key in `.claude/settings.json`; the batch diff shows that key's
change, and unrelated keys stay as they are. Sections in shared instruction files follow
[sections and markers](apply.md#sections-and-markers), and existing or edited content follows the same
[apply flow](apply.md).

Program-specific knowledge belongs with the ingredient and its cookbook documentation: which files and keys the
program reads, how it expresses permissions, how it loads instructions and skills, and which settings it supports.
Writing a rule or instruction does not guarantee that a program loads or obeys it. A program's unsupported settings
cannot be made effective merely by expressing them in shared guidance.

## Trust and execution boundaries

Some programs load project settings only for trusted folders, and global or managed settings can restrict project
configuration. Document those prerequisites with the program's ingredient. Whether trust extends to nested working
copies is program-specific; nesting is not a portable trust guarantee. See [workspace conventions](workspaces.md).

Permission enforcement, credentials, network access and runtime confinement belong to the program and its execution
environment. Cook's approval, destination boundaries and rollback cover declared file operations; trusted Groovy recipe
code is not sandboxed.
