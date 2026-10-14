# Repository configuration choices

## Objective

**Problem.** Every repository knows how its project is prepared, built and checked, which programs it uses and what
contributors should read. Copying another repository's setup without reviewing those facts leaves misleading commands
and configuration.

**Goal.** Repository-specific choices remain visible and reviewable in the files the relevant programs consume.

## Configuration responsibilities

Cook evaluates trusted Groovy [recipes](recipes.md) into ordered file operations. [Ingredients](ingredients.md) model
programs' native files; cookbook recipes express setup choices using those models. The engine handles the resulting
diff, whole-batch approval, recovery and writes. See [the architecture](../architecture.md).

A cookbook can document its own recipe inputs. Recipe arguments follow their recipe operand using `--name=value`;
their CLI scope is defined in [the CLI reference](cli.md).

## Facts worth recording

Keep executable choices in the task runner, build tool, CI or program configuration that consumes them. Contributor
instructions can point to those choices and explain when to use them.

| Repository fact | Useful place and review question |
| --- | --- |
| Dependency preparation | Project scripts or package manager files; does the setup fit a fresh checkout? |
| Formatting, linting and building | Existing task runner or build configuration; do the commands name the actual project paths? |
| Test families and other checks | Test runner and CI configuration; does a selection run meaningful checks rather than none? |
| Agent permissions and hooks | Native settings; what can the program enforce and what runtime dependencies are needed? |
| Contributor guidance and skills | Instruction and skill files; can each intended program discover them? |
| Temporary files and working copies | Native ignore rules and repository conventions; which paths should external tools exclude? |

For example, a Node project might document `npm ci` and its existing package scripts; a Go project might document
package-based test selection; an infrastructure repository might keep validation and environment-specific planning
commands in its task runner. These are project choices, and the programs that consume them run the commands. See
[repository checks](verify.md) and [workspace conventions](workspaces.md).

Keep commands and script dependencies readable, avoid machine-specific paths and secrets in shared configuration, and
explain any required environment settings. Keep globs and command quoting in the syntax of the program that reads
them.

## Composing and changing setup

Recipes and their operations compose left to right. Later instructions can replace earlier values at the same target,
and Cook presents one final diff against current content. Applying a project recipe after a base recipe can override
a setting while leaving the base recipe's unrelated files in place. A later invocation does not withdraw earlier
outputs merely because their recipe was omitted.

Choose whole-file, structured-key or marked-section targets to match the intended extent of each change. Existing or
manually edited files follow the ordinary diff and approval flow; ownership recorded in `cook.lock` never blocks a
change. Every apply invocation evaluates the supplied recipes again; history does not mark setup as already applied.
See [apply](apply.md) and [revert](revert.md).

The invocation directory is the destination. Relative source references resolve from each recipe's original location.
Batch records live in the destination's ignored `.cook/batches/<batchId>/` directory. They record patches and sources;
apply needs no previous history, while reverting a batch needs its record.

## Guidance and personal choices

Keep shared instructions concise and point to focused skills or topic files. Cook can declare those files; the program
decides what it loads and follows. See [repository guidance](context.md), [skills](skills.md) and
[program permissions](policy.md).
