# PRD: Documentation for per-repository setup, one engine path and git-first cookbooks

## Overview

A review of the tool from the user's side found three directions to change and several gaps to close:

- The README's objective promises a per-person way of working that leaves the repository clean. The engine is a
  per-repository shared-setup tool with a committed `cook.lock` and visible markers, and ADR 0028 already takes that
  position. Issues #5 and #6 were decided in #17 as recipe work outside the engine.
- The CLI has two engines: `.groovy` paths run the legacy core, coordinates run the new one. The architecture's
  FR-17 and FR-18 already define one path. Its grammar rule, "a word without `--` starts the next recipe", exists to
  protect a bare-name resolution the design no longer has, and it makes `--harness claude` an error.
- Shipping one recipe by coordinates needs a Gradle plugin, a Maven-layout repository and hosting. The engine
  compiles sources at apply and jars hold sources, so a git tag carries the same content as the jars.
- The session's benchmark of existing-file handling and bare flags is recorded nowhere in the repository.
- The README's "Current implementation" is one dense paragraph that mixes both engine paths, with no quick start.

This PRD changes documentation only. It states the intended design in the architecture, the ADRs and the
standards, and keeps every description of current behavior accurate for the code on `main`.

## Goals

- The README objective describes what the engine is for: one shared setup per repository, reviewed, owned and
  withdrawable, with nothing required of contributors who do not run Cook.
- The architecture, the CLI reference, the glossary and the standards define one recipe-operand rule and accept
  `--name value` for recipe arguments.
- A new ADR records git tags as the primary cookbook release, with Maven-layout jars as optional packaging of the
  same sources, and every document that names the jar as the only form follows it.
- The CLI standards carry the benchmark as prior art, with its sources.
- The README opens its current-implementation section with a quick start that runs as written.
- Issues track the code work these decisions create.

## Quality Gates

These commands must pass for every user story, from a checkout without `.worktrees/` (in the main checkout, Spotless
reads the ignored worktrees under `.worktrees/` and fails on their files; use a fresh clone of the commit instead):

- `git diff --check` - no whitespace errors
- `./gradlew spotlessCheck codenarc :docs:check` - formatting, CodeNarc and the docs link check

Every story ends with one commit on `main`, pushed to `origin`. Do not commit `.gitignore`, `.agents/policy.yaml` or
`.beads/`. The repository is public: name no private repositories, organizations or internal practices.

Follow the documentation rules in `docs/ux/messages-and-docs.md`, and for ADRs `docs/adrs/README.md`: present tense,
what things are, no history, no negative definitions.

## User Stories

### US-001: README objective states per-repository shared setup

**Description:** As a reader of the README, I want the objective to describe what the engine does so that I judge
the tool by the problem it solves.

**Acceptance Criteria:**

- [ ] `README.md` "## Objective" Problem names the per-repository problem: setup reused across repositories by
      copying drifts, a shared file replaced whole loses what others added, and nobody can tell which part came
      from where or remove it cleanly.
- [ ] Its Goal states: a setup learned once reaches every repository through one reviewed change, lives beside
      hand-written content in the same files, and is withdrawn the same way; contributors who do not run Cook need
      nothing, because programs read the native files and the lock and markers are reviewed like any other change.
- [ ] The phrases "Everyone keeps their own way of working", "only the work remains" and "never notice it" no
      longer appear in `README.md`.
- [ ] `docs/philosophy.md` "The problems" and "The goals" and `docs/features/README.md` "Overview" do not
      contradict the new objective; change them only where they do.

### US-002: One recipe-operand rule and `--name value` in the intended grammar

**Description:** As a user, I want `--harness claude` to work so that recipe arguments read like every other CLI's.

**Acceptance Criteria:**

- [ ] `docs/architecture.md` FR-18 states that a recipe operand is a word that contains `/`, ends with `.groovy`,
      starts with `https://` or contains `:`; a recipe file with none of these is given with `./`.
- [ ] FR-19 states that a recipe argument is `--name=value` or `--name value`, where the word after `--name` is its
      value unless it is a recipe operand; a value that looks like an operand uses `=`; a yes-or-no param is a bare
      `--name`, and `--name=false` turns it off.
- [ ] `docs/features/cli.md` "Recipe arguments and ordering": the paragraph and the table show `--agent codex` as
      the value `codex`, `--source ./b.groovy` as a second recipe that leaves `--source` without a value, and a
      word with none of the operand marks, such as `agent=codex`, as refused with a message naming the operand rule.
- [ ] `docs/ux/cli-standards.md` "Command grammar" says that recipe arguments take both forms, with the reason: the
      space form is what callers type in every other CLI, and `=` is needed for a value that looks like an operand.
- [ ] `docs/features/glossary.md` rows Recipe argument and Recipe operand follow FR-18 and FR-19, and a
      Coordinates row defines `group:artifact:version[:recipe]`.
- [ ] `docs/features/apply.md`, `docs/ux/configuration-standards.md` and `docs/ux/when-cli-when-skill.md` no
      longer say that recipe arguments use `--name=value` only.
- [ ] `README.md` "Current implementation" is unchanged by this story, because it describes the code on `main`.

### US-003: ADR 0029 records how cookbooks are released and resolved

**Description:** As a cookbook author, I want to release a cookbook by tagging its repository so that sharing a
recipe needs no Maven repository.

**Acceptance Criteria:**

- [ ] `docs/adrs/0029-cookbook-distribution.md` follows the template and rules in `docs/adrs/README.md`, with the
      title "Cookbooks release by git tag, with jars as optional packaging" or another present-tense sentence of at
      most 12 words.
- [ ] Its Decision states:
      1. a cookbook release is a git tag on the repository holding the cookbook: `v<version>` when `cookbook.toml`
         is at the repository root, `<dir>/v<version>` when the cookbook is in directory `<dir>`;
      2. a `cook.toml` repository entry is a Maven-layout repository (an `https://` URL or a path) or a git
         repository, written `{ git = "<url>", dir = "<dir>" }` with `dir` optional;
      3. the engine reads `cookbook.toml` at the tag, and a repository whose `group` differs from the coordinate's
         is skipped; an artifact is a directory of that cookbook;
      4. a Maven-layout jar holds the same sources, and publishing jars is optional packaging;
      5. `cook.lock` records the sha256 of the artifact's sources, each path and its content in path order, so one
         release has one hash from either kind of repository; a different hash under a recorded version is shown
         as a changed source before the diff;
      6. coordinates name exact releases; branches, commits, snapshots and ranges are refused;
      7. the engine fetches with the machine's `git`, using the credentials git already has, and caches fetched
         sources by commit for offline reuse;
      8. without a `cook.toml`, the repository is Maven Central.
- [ ] Every Decision point has a reason; rejected alternatives cover "Maven repository only", "a branch or commit
      as the version" and "a bundled git client".
- [ ] Context facts are checkable from this repository or public sources; prior art (Go modules' tag convention
      for a module in a subdirectory) is named without a version.
- [ ] `docs/adrs/README.md` index has the row for 0029.

### US-004: Architecture, cookbook rules and glossary follow ADR 0029

**Description:** As a reader of the design, I want every page that describes cookbook distribution to agree with
ADR 0029 so that no page names the jar as the only form.

**Acceptance Criteria:**

- [ ] `docs/architecture.md` "Ingredients, recipes, and cookbooks" links ADR 0029; "Ownership and the lock" says
      the sha256 is the source file's for a path or URL and the artifact's sources' for coordinates; FR-14 matches.
- [ ] `docs/architecture.md` "Engine distribution" or a new "Cookbook distribution" subsection states the release
      and resolution rules in two short paragraphs and links ADR 0029.
- [ ] `docs/architecture.md` "Destination and source origins" names coordinates as the third origin.
- [ ] `cookbook/README.md` "Units" describes a release as a git tag, the jars as optional packaging, and shows a
      `cook.toml` example with one git entry and one Maven-layout entry.
- [ ] `docs/adrs/0013-organization-content.md` Decision 3 and its Context name coordinates beside paths and URLs.
- [ ] `docs/features/glossary.md` Cookbook and `cook.lock` rows follow ADR 0029.
- [ ] `README.md` "Current implementation" keeps describing jar resolution, which is the code on `main`.

### US-005: Prior art for existing files and bare flags in the CLI standards

**Description:** As a contributor, I want the benchmark behind `--force`, `--skip`, the status lines and bare
yes-or-no arguments recorded so that the next change starts from evidence.

**Acceptance Criteria:**

- [ ] `docs/ux/cli-standards.md` has a "## Prior art" section with a table: tool, behavior when the file exists,
      override, keep-existing option, per-file status. Rows: Rails generators (Thor), Angular `ng generate`,
      Yeoman, Copier, cookiecutter, chezmoi, GNU Stow, Kubernetes server-side apply, Ansible `copy`, Go `flag`.
- [ ] Cells the tool's documentation does not state read "Not documented".
- [ ] Below the table, the section states what Cook takes from it: `--force` and `--skip` as tool options, the
      template declaring how each file is treated, one status line per file under `--dry-run` too, and bare
      yes-or-no arguments with `=false` to turn them off.
- [ ] The section lists its sources as links: the Go flag package, the Rails generators guide, Thor
      `create_file.rb` and `shell/basic.rb`, Angular `ng generate`, Yeoman file system, Copier configuring,
      cookiecutter CLI options, chezmoi `apply` and global flags, the GNU Stow man page, Kubernetes server-side
      apply, and the Ansible `copy` module source.
- [ ] `docs/features/cli.md` "Existing files" links the section.

### US-006: README quick start and a short account of what runs

**Description:** As a newcomer, I want five commands that apply a recipe so that I see the tool work before reading
the design.

**Acceptance Criteria:**

- [ ] `README.md` "## Current implementation" opens with "### Quick start": build the CLI and publish the cookbook
      (`./gradlew cliBuild :cookbook:publish`), read the artifact and a recipe with `./cook help`, write a
      `cook.toml` in a scratch directory naming this checkout's `cookbook/build/repository` by absolute path,
      apply `permissions` and `instructions` with `--harness=claude` through `-C`, and apply again to see
      `Already cooked.`.
- [ ] Every command in the quick start, run as written from a fresh clone, gives the output the section says.
- [ ] The section then lists what runs on coordinates (review, approval, `--force` and `--skip`, `cook.lock`,
      status lines), links `docs/features/cli.md` for each, and lists what the design has that does not run yet:
      revert and batch records, `https://` operands, `@cookbook`, git repositories.
- [ ] One short paragraph describes the `.groovy` path as it runs today, and links issue #15.
- [ ] No fact in the README contradicts `docs/features/cli.md` for behavior that runs today.

### US-007: Issues for the code work

**Description:** As a maintainer, I want the decisions tracked as issues so that the code work has an owner.

**Acceptance Criteria:**

- [ ] Issue #15 has a comment stating that `.groovy` paths run through the coordinates engine, and listing what
      that removes: `init`, `cook`, `plan`, saved-plan `apply`, `--library`, the legacy `--remove`, bare-name
      resolution and the tests that exercise them; it links the FR-18 and FR-19 commit.
- [ ] Issue #18 has a comment stating the lock hash rule from ADR 0029 and the derivation still open for an
      `https://` or path source.
- [ ] A new issue "Cookbooks resolve from git tags" links ADR 0029 and lists the engine work.
- [ ] A new issue "Release the jpackage image per OS" names the wrapper (FR-26) and the README install section.
- [ ] The issue texts name no private repositories, organizations or internal practices.

## Functional Requirements

- FR-1: Documentation that describes current behavior matches the code on `main`.
- FR-2: Documentation that describes intended design follows `docs/architecture.md`; the ADRs and standards follow it.
- FR-3: Each story is one commit on `main`, pushed.

## Non-Goals

- No code changes: the engine, the CLI, the cookbook sources and the tests stay as they are.
- No per-person kit design; issues #5 and #6 stay as decided in #17.
- No product-level comparison with other tools; the benchmark recorded is the one that was run.

## Technical Considerations

- `./gradlew :docs:check` fails on a relative link to a missing file or heading anchor; renaming a heading breaks
  links to it.
- Spotless's misc format requires a final newline and no trailing whitespace in Markdown.

## Success Metrics

- No page in `docs/` contradicts another on the operand rule or on cookbook distribution.
- The quick start runs as written.

## Open Questions

- The git entry shape in `cook.toml` and the tag convention for a cookbook in a subdirectory are proposals in
  ADR 0029; implementation may refine them.
