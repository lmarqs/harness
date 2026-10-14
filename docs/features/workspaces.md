# Workspace conventions and configuration

## Objective

**Problem.** Contributors and agents may need separate working copies for concurrent tasks. Without a shared
convention, working copies, dependencies and local state can be hard to find or easy to confuse.

**Goal.** Keep workspace guidance in the repository's native configuration and instructions, with working-copy
management left to version control and the tools or people using it.

## Native conventions

A recipe can declare a workspace convention in contributor instructions and the corresponding paths in a version
control ignore file. An ingredient models the program's native format; Cook reviews and writes the declared changes,
as described in [the architecture](../architecture.md). The convention remains useful to people and tools
independently of Cook.

For example, a repository that chooses `.worktrees/` for local working copies can request these sections:

```markdown
<!-- cook:begin git/workspaces/instructions -->
Keep local working copies under `.worktrees/`. Create and remove them with the version control system.
<!-- cook:end git/workspaces/instructions -->
```

```gitignore
# cook:begin git/workspaces/ignore
/.worktrees/
# cook:end git/workspaces/ignore
```

The path is an example of a repository choice. Markers name the owning recipe and region and use each format's
comment syntax; ownership never blocks a change. The ordinary [apply flow](render.md) shows the actual batch diff for
existing or edited files and preserves unrelated sections. [Reverting](remove.md#revert) a recorded batch applies its
reverse patches against current content. Neither operation manages the working copies mentioned in the text unless
file operations explicitly target them.

## Destination and local state

Cook's destination is the directory where it is invoked. When invoked inside a separate working copy, declared writes
and deletions stay within that directory, including paths resolved through symlinks. Relative source references
resolve from the referencing recipe's original location; local recovery history belongs in the destination's ignored
`.cook/` directory, where batch records hold file patches and recovery information. See
[paths, sources and history](../architecture.md#paths-source-snapshots-and-local-history).

Nested working copies can interact with tools that search parent directories for configuration, dependencies or
tracker data. That behavior depends on those tools. Document dependency preparation and relevant tool behavior with
the repository's convention. Before removing a working copy, preserve work and local data that matter through the
tools that manage them.
