# Generated-content standards

## Objective

**Problem.** Generated configuration shares files with developers and other tools. Broad replacements, noisy output,
and unclear deletion rules make it hard to judge a change or recover earlier values.

**Goal.** A developer can see the actual proposed change, approve its full scope, and reverse the changes of a
recorded [batch](../features/glossary.md). Content outside the declared targets stays intact.

## Overview

These standards follow the intended [architecture](../architecture.md). They cover Cook's declared batch operations. See
the [root README](../../README.md) for current execution. Cook evaluates trusted Groovy recipes; approval, boundaries,
and recovery do not sandbox arbitrary effects of recipe code.

## Kinds of generated content

A recipe declares its targets. Ingredients model programs' native files and configuration; cookbooks supply ingredients
and recipes independently of the engine.

| Target | Address | Review scope |
| --- | --- | --- |
| Whole file | File path | The entire declared file, including existing content it replaces |
| Structured content | File path and key path | The declared keys, with unrelated keys preserved |
| Shared text | File path and begin/end markers | The declared section, with text outside its boundaries preserved |
| Directory or symlink | Declared path, where applicable | The declared filesystem operation |

Native settings, instruction files, skills, and ignore entries may be recipe targets. A template supplies content.
Executing external commands is not a batch action; the program runs its configured native hooks.

## Header

When a recipe includes a generated-content notice, use the file's comment syntax and place it where the native
program permits it: after a script's shebang, for example, because a misplaced comment can break the file. Do not add
comments to JSON, because JSON has no comment syntax. A useful notice identifies the source so a reader can find the
recipe or template behind the output.

A notice is explanatory text; ownership is recorded in the committed `cook.lock`.

## Sections and markers

Markers bound a section in shared text and name the recipe that owns it and the region within that recipe:
`cook:begin <recipe>#<region>` and `cook:end <recipe>#<region>`. The
[architecture](../architecture.md#ordered-composition-and-declared-targets) defines section targets. Use the file
format's appropriate comment syntax:

```markdown
Text outside the section remains intact.

<!-- cook:begin recipes/setup#commands -->
Run the project's build command.
<!-- cook:end recipes/setup#commands -->
```

```gitignore
# cook:begin git#ignore
/.claude/settings.local.json
# cook:end git#ignore
```

Markdown uses HTML comments; ignore files, TOML, YAML, and shell files use hash comments where their formats permit
comments. A declared section update preserves unrelated text, comments, and layout outside its boundaries. Manual edits
inside the section appear in the next proposed diff; ownership never blocks a change.

### Recipe and region names

Each recipe has a short name that markers and `cook.lock` use. The region is a name the recipe chooses, after the `#`:
in `recipes/setup#commands`, the recipe is `recipes/setup` and the region is `commands`. Several recipes can
therefore keep separate sections in one file. `cook.lock` lists each recipe's sections by file and
`<recipe>#<region>`. Recipes declare the sections they address.

### Instruction files and conventions

Keep shared instructions concise, avoid duplicating existing guidance, and link to detail when useful. Why: programs
that load instructions repeatedly pay for every line. Content and size are recipe configuration choices.

State a convention where people and the native program can find it, using the values that actually apply. Programs
read the generated files directly, so they stay usable without Cook.

Guidance tells a program where to look; prefer focused files to repeated copies. For example, a section a recipe
proposes in `AGENTS.md` can tell readers to read `docs/database-conventions.md` before changing database migrations, or
point to shared skills such as `.agents/skills/coding-guidelines/SKILL.md`. Review guidance as instructions, because
anyone who changes it can steer the programs that read it, and review who supplied it and which programs read it.

Memory files hold the reviewed knowledge itself: patterns, conventions and lessons that are not obvious from the code,
so people and agents do not repeat mistakes once a session ends. A repository can choose a layout such as:

```text
.agents/memory/
  backend-patterns.md    services, transactions, pagination
  testing-patterns.md    fixtures and test conventions
  known-risks.md         areas where small changes have broad effects
```

Keep each memory file focused on one area, describe what is true with enough context for a new reader to apply it,
and remove outdated advice; session notes and to-dos belong in the team's tracker. Maintain references to guidance
and memory files when files are added, renamed, moved or removed, because a stale reference leads readers to the wrong
file.
Cooking a new version of a recipe removes the memory content its previous version owned and the new version omits,
even after someone edited it; keep important knowledge by choosing and reviewing the intended declarations.

## Keys in native files

A structured operation changes its declared key paths. Preserve unrelated keys and avoid unnecessary changes to their
order, comments, or formatting. Whole-file declarations have broader scope; review the entire replacement rather than
assuming key-level preservation.

For example, a recipe that sets `test` in `settings.toml` preserves an unrelated `other` key. A permission list or hook
setting has the scope the recipe declares. A program enforces its own native settings.

## Ordered operations and repeat application

Recipes and their operations compose left to right against current content, and the last write to a section, key, or
file wins; [ordered composition](../architecture.md#ordered-composition-and-declared-targets) works through an example.
Every run evaluates the supplied recipes again, so a manual edit appears in the next diff like any other difference.

## Stable output

Keep generated changes easy to review. Avoid incidental timestamps, usernames, hostnames, random values, or absolute
machine paths unless the requested configuration needs them, because such values differ between runs and machines and
hide the requested change in the diff. Preserve useful native formatting and avoid moving unrelated lines. Text
examples use UTF-8, LF line endings, and a final newline.

Follow declared operation order rather than sorting operations to eliminate overlap. Output depends on current
content, supplied sources, and arguments; trusted executable recipes do not imply identical bytes on every machine
or a network-free evaluation. Engine version pinning does not pin or manage recipe versions.

## Review

Cook displays the final combined diff in the terminal, including changes to existing or manually edited targets.
The CLI accepts the whole batch; choosing individual changes belongs to a separate interface decision. For example,
the first invocation asks before writing and the second is preapproved:

```sh
./cook ./base.groovy ./project.groovy
./cook --batch-approve ./base.groovy ./project.groovy
```

Noninteractive execution without `--batch-approve` does not authorize writes. See the
[CLI standard](cli-standards.md#batch-review-and-approval).

Review source trust as well as the resulting native settings. Arbitrary effects of recipe evaluation are outside the
batch diff and recovery guarantee. Commit and delivery decisions belong to the repository's own workflow.

## Destination

The current working directory is the destination, and declared writes and deletions stay within it
([destination and source origins](../architecture.md#destination-and-source-origins)). See
[configuration locations](configuration-standards.md#where-files-live).

## Discard

Discard removes what `cook.lock` lists for a recipe without evaluating it: each section together with its markers,
each key, each whole file and link. A file left empty is deleted; everything the recipe does not own stays, and no
earlier value is restored. The [discard](../features/discard.md) page works through an example. To keep native
configuration, leave it in place.
