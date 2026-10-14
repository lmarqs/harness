# Skills and program configuration

## Objective

**Problem.** Useful instructions copied into many repositories go stale, and a coding agent can only use guidance it
can discover and read. Sharing a skill folder alone does not ensure that every program loads it or follows it.

**Goal.** Skill content and discovery configuration remain reviewable, with clear responsibilities for maintaining
sources and for loading the instructions.

## Skills as native files

A skill is a folder of instructions and supporting resources in the form its consuming program understands. A team
can keep shared skills under `.agents/skills/<name>/SKILL.md`, but that location and any required metadata depend on
the programs it uses; it is not an engine-mandated layout.

[Ingredients](ingredients.md) model native program configuration, including skills where applicable. Trusted Groovy
[recipes](recipes.md) can declare operations that supply skill files and configure their discovery, including directory
or symlink operations where applicable. Cook reviews the combined file changes and writes them after whole-batch
approval. The program decides when to load the skill and what to do with its instructions.

A skill guides an agent; it does not guarantee compliance. Cook does not assign skills to fixed development stages,
launch workers, inject skills into sessions or enforce a deterministic matching rule. Recipe APIs and injection remain
separate model discussion work. See [repository guidance and context](context.md) and
[the architecture](../architecture.md).

## Sharing and maintaining content

Choose how to maintain a skill: repository-authored content, an external installer or a cookbook recipe can provide
its files. Record its source and update process so reviewers know where a change belongs. Avoid maintaining the same
copy through competing processes that overwrite each other's edits.

Committing skill content makes that content available in each clone and lets reviewers inspect changes without
running an installer. Include an installer's lock file when it is part of the team's chosen process. If an installer
is required on each machine instead, document its access and setup prerequisites. Availability does not guarantee
that every agent will discover or load the files.

For externally maintained skills, update the source and then refresh the repository's copy through the chosen process.
For repository-authored skills, edit the repository content directly. A cookbook recipe that supplies a skill uses the
ordinary batch diff and approval flow; no dedicated skill registry, updater or installer API is defined here.

## Discovery and coexistence

Review where each program looks for skills, whether it accepts links, and what project trust or settings it requires.
A link to a shared directory can expose several skills; an individual link exposes one. An existing real directory
can contain unrelated skills, so choose the declared targets deliberately rather than assuming it can be replaced.
Declared symlink targets must respect Cook's destination boundary, including paths resolved through symlinks.

Keep shared instructions short and point to focused skill files when useful. Program-specific descriptions and
metadata can help discovery, but a pointer does not ensure loading and an instruction does not establish enforcement.
This design defines no profile `context` triggers, once-per-session cache or hook delivery API.

There is no mandatory shipped `.agents/skills/the-cook/SKILL.md`, release-embedded skill identity or drift-checked skill
ownership contract. If a cookbook supplies Cook usage guidance, keep its commands consistent with the
[CLI reference](cli.md) and state the relevant version and prerequisites. An engine upgrade does not automatically
refresh cookbook or repository skill content.

## Reviewing changes and removal

Review skill text, supporting scripts and links as part of the actual batch diff. Skills can contain executable
resources or tell an agent to take actions; review those consequences as well as the instruction text. Native program
permissions remain the program's responsibility. See [program permissions](policy.md) and [safety guidelines](safety.md).

Cook proposes declared changes against existing or manually edited content without adoption or ownership gates.
Applying another recipe does not remove unrelated skills. [Discard](remove.md#discard) deletes the supplied recipe
version's declared skill contributions, even after edits; [revert](remove.md#revert) restores a recorded operation's
prior values using history. Neither reverses actions an agent has taken after reading a skill.

When a program cannot find a skill, inspect its expected location, links, metadata and project trust settings. When it
finds a skill but does not use it, investigate that program's loading behaviour and the relevance of the description;
Cook's successful file write is not evidence that the skill was loaded.

Personal setup backup remains future kit work in [issue #5](https://github.com/lmarqs/the-cook/issues/5), and agent-session
orchestration remains future work in [issue #6](https://github.com/lmarqs/the-cook/issues/6). Neither supplies a skill
injection protocol here.
