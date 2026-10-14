# Repository setup with recipes

## Objective

**Problem.** Setting up permissions, hooks, instructions and working conventions by hand leads to inconsistent
repositories. Existing configuration can contain important choices that a generic starting template would overwrite.

**Goal.** A repository gets reusable setup through a reviewed batch of declared file changes, with repository-specific
choices made deliberately.

## Setup is a file operation

Use trusted Groovy [recipes](recipes.md) supplied by a [cookbook](cookbooks.md#cookbooks) to propose native
configuration. [Ingredients](ingredients.md) hold the program models; Cook evaluates the recipes, composes their file
operations in order and displays the final combined terminal diff for whole-batch approval. The same flow applies to an
empty repository and one with existing files. The [glossary](glossary.md) defines these terms; see also [the
architecture](../architecture.md).

Setting up a repository is an ordinary apply of the chosen recipes. Existing permission or hook rules that a recipe's
keys could replace need review for what they allow; ownership recorded in `cook.lock` never blocks a change.

## Choosing and reviewing setup

Before applying a recipe that sets up a repository:

1. Read its executable source and README, including required inputs and consumed templates. Trust the
   recipe before evaluating it; batch approval does not sandbox its code.
2. Compare the declared configuration with the repository's actual programs, commands, paths and dependencies.
   A shared recipe is a starting choice, not proof that its checks fit this project.
3. Read existing native settings, instructions, hooks and skill locations. Decide which targets should change and
   which content should remain outside the declarations.
4. Review the actual diff for permissions, replaced hooks, file or symlink targets and instructions. Do not assume
   that translation between programs preserves permission meaning.

Personal settings should become shared configuration only when that is a deliberate team choice. Unknown settings
and rules that cannot be translated need program-specific investigation. See [program permissions](policy.md),
[native hooks](hooks.md), [configuration choices](profile.md) and [skills](skills.md).

## Invoking a setup recipe

The intended CLI accepts recipe paths inside the destination repository or `https://` URLs; a local recipe outside
the repository is refused. Assuming these trusted recipe files are available, invoke them from the directory whose
configuration you want to change. This is an illustration of the intended interaction:

```sh
cd /work/project
./cook apply cookbook/recipes/base.groovy https://example.com/cookbook/recipes/project.groovy
```

The destination is `/work/project`. The diff shows the combined result of both recipes against the current files in
`/work/project`; when both write the same target, the later recipe's value appears and that recipe becomes its owner.
Without `--batch-approve`, Cook asks before changing targets. See [CLI grammar](cli.md) and [apply](apply.md) for the
complete operating contract.

For the build and packaging entry path available today, use [installation](installation.md) and the
[root README](../../README.md).

## Files and later changes

A recipe can declare native settings, supporting scripts, instruction sections, skills, directories or symlinks where
applicable. Whole-file declarations affect the whole file; key and section declarations preserve unrelated content
outside their targets. See [declared outputs](apply.md#declared-outputs).

Run setup again when you want to propose its declared configuration against current content; every invocation
evaluates the supplied recipe versions. To undo a setup batch, [revert](revert.md#revert) it.

The [wrapper](wrapper.md) pins the engine version separately from cookbook builds and user-supplied recipe versions.
