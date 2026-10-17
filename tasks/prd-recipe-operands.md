# PRD: Recipe operands are paths and coordinates; personal cookbooks through Maven local

## Overview

Issue #18 leaves open the short name of an `https://` recipe and how a renamed recipe's old output goes away.
Distribution already has wheels: cookbook releases in Maven-layout repositories (`MavenRepository`) and git tags
(`GitRepository`), cooked by coordinates. So recipe operands become exactly two kinds: a `.groovy` path inside the
kitchen (the kitchen's own recipe) and coordinates. An `https://` operand is refused. A developer who wants their own
recipes in any repository builds a cookbook, publishes it to Maven local, lists `~/.m2/repository` in that
kitchen's `cook.toml`, and cooks by coordinates. That needs `~/` paths in `cook.toml` and the
`dev.thecook.cookbook` Gradle plugin in Maven local; today the plugin loads only through `includeBuild('../plugin')`.

Short names (already implemented): a path's place in the kitchen without `.groovy` (`recipes/setup`, from
`RecipeLoader.name`); coordinates without the version (`dev.thecook:agents:instructions`, from
`Coordinates.getRecipeName`). A rename is cooking the new name, then `./cook discard <old>`.

## Goals

- `./cook`, `./cook help` and `./cook discard` refuse any `scheme://` operand with one message that points to
  `cook.toml` and coordinates.
- A test pins down rename-by-discard.
- `cook.toml` path entries starting with `~/` resolve from the user's home.
- `./gradlew -p plugin publishToMavenLocal` publishes the plugin, so a cookbook outside this repository can apply it.
- Docs and ADRs describe two operand kinds, the two short-name rules, rename-by-discard and the Maven local workflow,
  with no `https://` recipe operand left.

## Quality Gates

These commands must pass for every user story:
- `./gradlew check cliBuild jpackageTest` - checks, CLI build and packaged-image test
- `git diff --check` - whitespace

## User Stories

### US-001: Refuse URL recipe operands
**Description:** As a user, I want a URL operand refused with a pointer to repositories so that I learn how recipes
are distributed instead of seeing `Recipe file not found: https:/…`.

**Acceptance Criteria:**
- [ ] `RecipeLoader.relative()` in `core/src/main/groovy/thecook/cookbook/RecipeLoader.groovy` throws
  `IllegalArgumentException` for an operand containing `://`, message:
  `a URL is not a recipe; list its repository in cook.toml and cook the recipe by its coordinates`.
- [ ] `CookCommand.help` (`cli/src/main/groovy/thecook/cli/terminal/CookCommand.groovy`) resolves a path operand
  through `RecipeLoader.name(kitchen.toPath(), operand)` before reading the file, so `help https://…` gives the same
  message.
- [ ] `CookbookApplyTest`: cooking and discarding `https://example.com/notes.groovy` fail with that message.
- [ ] `CookCommandTest`: `./cook https://example.com/notes.groovy`, `help` and `discard` with it print the message
  on stderr and write nothing.

### US-002: Test renaming a recipe and discarding the old name
**Description:** As a maintainer, I want a test of moving a local recipe so that rename-by-discard stays true.

**Acceptance Criteria:**
- [ ] New test in `core/src/test/groovy/thecook/CookbookApplyTest.groovy`: cook `recipes/a.groovy` declaring one
  section and one key; move the file to `recipes/b.groovy` and cook it.
- [ ] After the second cook: the file holds both `recipes/a#…` and `recipes/b#…` sections, and `cook.lock` lists the
  key under `recipes/b` only.
- [ ] `discard recipes/a.groovy` (file already gone) removes only the `recipes/a` section; `cook.lock` lists only
  `recipes/b`.
- [ ] No production code changes.

### US-003: `~/` paths in cook.toml
**Description:** As a cookbook author, I want to list `~/.m2/repository` in `cook.toml` so that I can cook my
`publishToMavenLocal` build without writing my home directory into the file.

**Acceptance Criteria:**
- [ ] `CookToml.repositories` (`infra/src/main/groovy/thecook/infra/CookToml.groovy`) takes `File home` after
  `cache`; one helper resolves an entry that starts with `~/` against `home`, used for Maven path entries and for git
  path entries.
- [ ] `cli/src/main/groovy/thecook/cli/run/Main.groovy` passes `new File(System.getProperty('user.home'))`.
- [ ] `CookTomlTest`: `"~/.m2/repository"` resolves to `<home>/.m2/repository/`; `{ git = "~/kit" }` to
  `<home>/kit`; `"~user/x"` is not expanded.
- [ ] The `CookToml` doc comment and `infra/README.md` mention `~/` paths.

### US-004: Publish the cookbook plugin to Maven local
**Description:** As a developer with my own cookbook repository, I want `dev.thecook.cookbook` in Maven local so
that my cookbook builds jars without a checkout of this repository.

**Acceptance Criteria:**
- [ ] `plugin/build.gradle` applies `maven-publish`; `./gradlew -p plugin publishToMavenLocal
  -Dmaven.repo.local=<tmp>` writes the plugin jar and the marker
  `dev/thecook/cookbook/dev.thecook.cookbook.gradle.plugin/0.1.0/` under `<tmp>`.
- [ ] A check (Gradle TestKit test in `plugin/src/test` or a script run by `check`) builds a throwaway cookbook in a
  temp directory whose `settings.gradle` has `pluginManagement { repositories { mavenLocal(); mavenCentral() } }`
  and `plugins { id 'dev.thecook.cookbook' version '0.1.0' }`, against the same `maven.repo.local`, and runs its
  `publishToMavenLocal`, producing the cookbook's jar. Nothing is written to the real `~/.m2`.
- [ ] `./gradlew -p plugin check` passes.
- [ ] `cookbook/README.md` gains a section "A cookbook outside this repository": `cookbook.toml`, `settings.gradle`
  as above, `./gradlew publishToMavenLocal`, then `repositories = ["~/.m2/repository"]` in the kitchen's
  `cook.toml` and `./cook <group>:<artifact>:<version>:<recipe>`.

### US-005: Docs: two operand kinds, short names, rename, distribution
**Description:** As a reader, I want the docs to describe the operands and distribution as they are so that I don't
try URL recipes or guess a short name.

**Acceptance Criteria:**
- [ ] `docs/architecture.md`: the short-name paragraph (~l.32) gives both rules with `recipes/setup` and
  `dev.thecook:agents:instructions`; the sources section (~l.249–270), FR-14, FR-18 and FR-22 drop `https://` URLs
  and the URL rows of the example table; the `cook.toml` paragraph (~l.296) adds `~/` paths and says to list
  `~/.m2/repository` last; one sentence says a rename is cooking the new name and discarding the old.
- [ ] `docs/adrs/0029-cookbook-distribution.md` gains rejected alternative "A recipe at an `https://` URL" (no
  release identity: the short name changes with a version in the URL or collides across hosts; templates need
  fetching one by one; repositories and tags already distribute versioned sources).
- [ ] `docs/adrs/0025-recipes.md` decision 3 states both short-name rules and rename-by-discard, with rejected
  alternative "rename detection by sha256" (breaks when a file is moved and edited together).
- [ ] `https://` is no longer a recipe operand or source in ADRs 0007, 0012, 0013, 0016, 0026,
  `docs/features/cook.md`, `cli.md`, `glossary.md`, `discard.md` (gains the rename example),
  `docs/ux/cli-standards.md`, `configuration-standards.md`, `principles.md`, `messages-and-docs.md` (l.197 example
  becomes coordinates), `README.md:84`, `core/README.md:67`.
- [ ] `git grep -n 'agentic/context' -- ':!.worktrees'` returns nothing; `./gradlew :docs:check` passes.
- [ ] Docs describe what is, with no history; ADRs are edited in place.

### US-006: Issue tracking
**Description:** As the maintainer, I want the issues to match the result so that the tracker shows what is left.

**Acceptance Criteria:**
- [ ] #18 has a comment with the decision and the commits, and is closed.
- [ ] #15 task 5 says `https://` operands left the design and FR-22 relative references from a local recipe remain.

## Functional Requirements

- FR-1: A recipe operand containing `://` is refused by cooking, `help` and `discard` with the message in US-001.
- FR-2: A local path operand's short name is its place in the kitchen without `.groovy`; a coordinates operand's
  short name is `group:artifact:recipe`.
- FR-3: Discarding a short name removes only what `cook.lock` still lists for it.
- FR-4: A `cook.toml` path entry starting with `~/` resolves against the user's home directory; `~user/` is not
  expanded.
- FR-5: `./gradlew -p plugin publishToMavenLocal` publishes the `dev.thecook.cookbook` plugin and its marker.

## Non-Goals

- Publishing the plugin to the Gradle Plugin Portal or any remote repository.
- A per-user list of repositories that applies without a `cook.toml` (issue #5, Kit).
- Automatic rename detection.
- FR-22 relative template references (stays in #15 task 5).
- Reading Maven `settings.xml` or `maven.repo.local` for `~/.m2`.

## Technical Considerations

- `java-gradle-plugin` plus `maven-publish` creates the plugin and marker publications; the plugin depends on
  `org.tomlj:tomlj`, so a consumer's `pluginManagement` needs `mavenCentral()` besides `mavenLocal()`.
- Coordinates need exact release versions; republishing one version to Maven local shows a `Changed source` notice
  on the next cook.
- Do not commit `.gitignore`, `.claude/settings.json`, `.agents/`, `.beads/` or `cookbook/agents/AGENTS.template.md`
  edits that are the user's own. The repository is public: name no private organizations.

## Success Metrics

- A cookbook in a temp directory builds against the Maven-local plugin and is cooked by coordinates from
  `~/.m2/repository` in a scratch kitchen.
- No doc lists `https://` as a recipe operand.

## Open Questions

- None.
