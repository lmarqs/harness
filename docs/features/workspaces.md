# Workspace conventions and configuration

## Objective

**Problem.** Contributors and agents may need separate working copies for concurrent tasks. Without a shared
convention, working copies, dependencies and local state can be hard to find or easy to confuse.

**Goal.** Keep workspace guidance in the repository's native configuration and instructions, with working-copy
management left to version control and the tools or people using it.

## Native conventions

A [recipe](glossary.md) can declare a workspace convention in contributor instructions and the corresponding paths in a
version control ignore file. An ingredient models the program's native format; Cook reviews and writes the declared
changes, as described in [the architecture](../architecture.md). The convention remains useful to people and tools
independently of Cook.

For example, a repository that chooses `.worktrees/` for local working copies can request these sections:

```markdown
<!-- cook:begin git/workspaces#instructions -->
Keep local working copies under `.worktrees/`. Create and remove them with the version control system.
<!-- cook:end git/workspaces#instructions -->
```

```gitignore
# cook:begin git/workspaces#ignore
/.worktrees/
# cook:end git/workspaces#ignore
```

The path is an example of a repository choice. The markers follow [sections and markers](apply.md#sections-and-markers),
and the ordinary [apply flow](apply.md) and [revert](revert.md#revert) cover these sections. Neither operation manages
the working copies mentioned in the text unless file operations explicitly target them.

## Destination and local state

When Cook is invoked inside a separate working copy, that working copy is the destination: declared writes stay
within it, and its ignored `.cook/` directory holds its batch records. See
[destination and source paths](apply.md#destination-and-source-paths) and [batch record](apply.md#batch-record).

Nested working copies can interact with tools that search parent directories for configuration, dependencies or
tracker data. That behavior depends on those tools. Document dependency preparation and relevant tool behavior with
the repository's convention, for example whether each working copy installs its own dependencies. Before removing a
working copy, preserve work and local data through the tools that manage them, for example by committing or stashing
changes.
