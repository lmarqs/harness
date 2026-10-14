# Repository setup with recipes

## Objective

**Problem.** Setting up permissions, hooks, instructions and working conventions by hand leads to inconsistent
repositories. Existing configuration can contain important choices that a generic starting template would overwrite.

**Goal.** A repository gets reusable setup through a reviewed batch of declared file changes, with repository-specific
choices made deliberately.

## Setup is a file operation

Use trusted Groovy [recipes](recipes.md) supplied by a [cookbook](stations.md#cookbooks) to propose native configuration.
[Ingredients](ingredients.md) hold the program models; Cook evaluates the recipes, composes their file operations in
order and displays the final combined terminal diff for whole-batch approval. The same flow applies to an empty
repository and one with existing files. See [the architecture](../architecture.md).

There is no separate `scaffold` command, station-detection protocol, required profile skeleton or adoption workflow in
the target CLI. No `--adopt`, compatibility alias, import-equivalence gate or ownership database is required before
changing existing content. Existing rules need review because of their meaning, not because Cook must take ownership
of them.

## Choosing and reviewing setup

Before applying a setup recipe:

1. Read its executable source and cookbook usage guide, including required inputs and consumed templates. Trust the
   recipe before evaluating it; batch approval does not sandbox its code.
2. Compare the declared configuration with the repository's actual programs, commands, paths and dependencies.
   A stack template is a starting choice, not proof that its checks fit this project.
3. Read existing native settings, instructions, hooks and skill locations. Decide which targets should change and
   which content should remain outside the declarations.
4. Review the actual diff for permissions, replaced hooks, file or symlink targets and instructions. Do not assume
   that translation between programs preserves permission meaning.

Personal settings should become shared configuration only when that is a deliberate team choice. Unknown settings
and rules that cannot be translated need program-specific investigation; Cook does not promise a lossless import or
automatic to-do resolution. See [program permissions](policy.md), [native hooks](hooks.md),
[configuration choices](profile.md) and [skills](skills.md).

## Invoking a setup recipe

The intended CLI accepts exact recipe paths or supported source URIs. Assuming these trusted recipe files are
available, invoke them from the directory whose configuration you want to change:

```sh
cd /work/project
/path/to/the-cook/bin/the-cook apply /home/dev/cookbook/recipes/base.groovy /home/dev/cookbook/recipes/project.groovy
```

The destination is `/work/project`, regardless of the executable or recipe location. Relative recipe and template
references resolve from their original source locations. The two recipes compose left to right; later values can
replace earlier ones at the same target. This example chooses no model, nesting or injection API.

Without preapproval, Cook asks before changing targets. `--batch-approve`, placed before the recipe operands, displays
the final diff and proceeds without prompting. Noninteractive execution without that flag does not authorize writes.
See [CLI grammar](cli.md) and [apply](render.md) for the complete operating contract.

These are target examples. For the build and packaging entry path available today, use [installation](installation.md)
and the [root README](../../README.md); writing this design does not establish that the target CLI is implemented.

## Files and later changes

A recipe can declare native settings, supporting scripts, instruction sections, skills, directories or symlinks where
applicable. Declared writes and deletions stay within the invocation directory, including resolution through symlinks.
Whole-file declarations affect the whole file; key and marked-section declarations preserve unrelated content outside
their targets. Use the file format's comment syntax for markers, including HTML comments in Markdown.

Run setup again when you want to propose its declared configuration against current content. Every invocation
evaluates the supplied recipe versions; it can produce an empty diff, but history does not skip an already-applied
recipe. Applying a different recipe does not implicitly remove earlier unrelated output. Manual edits receive the
same actual diff review, without a drift veto.

[Discard](remove.md#discard) proposes deleting the supplied recipe version's declared contributions, even if edited.
[Revert](remove.md#revert) instead restores a recorded operation's prior values from history. Cook saves recovery
patches before target writes, rolls back writes already made if a write fails, and retains recovery and failure
information in the destination's ignored `.cook/` directory. That history is not a committed setup manifest.

The [wrapper design](wrapper.md) provides a committed engine version and checksum pin, first-use package download and
local cache, and explicit upgrades. It does not establish a scaffold-generated wrapper, a published release, a
cookbook registry or automatic recipe upgrades. Cookbook builds and user-supplied recipe versions remain separate
from engine delivery.

## Future boundaries

Personal setup backup is kit work in [issue #5](https://github.com/lmarqs/the-cook/issues/5). Agent-session orchestration
is tracked in [issue #6](https://github.com/lmarqs/the-cook/issues/6). This setup guidance does not design either
capability, a fixed development lifecycle or recipe hooks.
