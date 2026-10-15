# PRD: Cookbook units documented for readers without the source

## Overview

The cookbook under `cookbook/` is a design sketch in a Groovy-like pseudo language; nothing in it runs, and the
build excludes it from Spotless and CodeNarc. It shows the shape of recipes and ingredients (models). Today a unit's
README gives one sentence, one invocation and a writes table, which is not enough for someone who has the published
artifact and not the source. Simple type names such as `Compose` say nothing about where they come from, a recipe
and a model can share coordinates (`dev.thecook:ralph`), and 14 of 59 `@param` declarations have no `doc`.

This PRD moves every unit to the agreed shape: one directory per topic holding its models, recipes and resources;
packages that name the cookbook; documentation in doc comments that `--help` renders; names that describe a state.
Commit 70af445 on `main` holds exemplars: `cookbook/README.md` (rules and a rendered-help illustration),
`cookbook/ingredients/node/Node.groovy` (a documented model with private state), `cookbook/recipes/ralph/`
(a recipe with documented params and the six documentation sections in its README).

## Goals

- Every unit is understandable from its artifact alone: README, doc comments and annotations.
- A reader can tell which cookbook a type comes from by its import.
- A recipe and a model can never collide on coordinates.
- Every param, every public member and every annotated member is documented.
- Recipe names describe the state the destination gets.
- `docs/ux` no longer contradicts the sketch.

## Quality Gates

These commands must pass for every user story, run from the repository root:

- `git diff --check` - no whitespace errors
- `./gradlew spotlessCheck codenarc :docs:check` - formatting of linted sources, CodeNarc, and the docs link check

Every story ends with one commit on `main`, pushed to `origin` by fast-forward. Do not touch `.gitignore`,
`.agents/policy.yaml` or `.beads/`, which are uncommitted local state.

## User Stories

### US-001: Rules page and cookbook manifest
**Description:** As a cookbook author, I want `cookbook/README.md` to state every rule of the new shape so that each
later story follows a written rule instead of an exemplar.

**Acceptance Criteria:**
- [ ] `cookbook/README.md` keeps its opening ("design sketch, nothing runs") and has these sections in order: Units,
      Names, The author's API, Documentation, Layout.
- [ ] Units states: a cookbook directory is one artifact holding models, recipes, resources and one README; one
      release version per cookbook with one jar per directory listed in a BOM; a jar holds sources, resources and
      README; a recipe is addressed `group:artifact:version[:recipe]` and the recipe named like its directory is the
      apply default; exact release versions only, snapshots and ranges refused; a loose recipe declares
      `@cookbook('dev.thecook:0.1.0')` once per cookbook it uses; each recipe in an invocation is evaluated in its
      own classloader and composition is by file content; two versions of one artifact inside one recipe's tree is
      an error; `cook.lock` records the address and the jar's sha256; a committed `cook.toml` at the destination
      lists repositories, Maven Central by default; `--help` on `group:artifact:version` prints the artifact's front
      page and on `group:artifact:version:recipe` prints that recipe.
- [ ] Names states: package = group + directory (`cookbook/docker/Compose.groovy` is `dev.thecook.docker.Compose`
      in `dev.thecook:docker`); a recipe imports every type it injects; the engine imports its annotations,
      `groovy.json`, `groovy.yaml` and `java.nio.file` into every unit; recipe names are noun phrases naming the
      state the destination gets and do not repeat the directory's topic.
- [ ] The author's API keeps the two annotation tables and adds: `@cookbook` on a loose recipe; `doc` required on
      `@param`; a declaration member may throw to reject the request, after the guard that keeps a pristine
      instance declaring nothing; call order in the body is meaningful and a method that must follow others says
      so in its doc comment, as `Agents.connect` does.
- [ ] Documentation states: a recipe file opens with a `/** */` doc comment holding, in order, a lead, Before
      applying, Apply, Writes (columns Target, Part, When), After applying, With other recipes, empty sections
      omitted; a model's class doc comment holds a lead, Writes for, Instances, Use, With other models; every
      public method or property and every annotated member has a doc comment, an annotated member's stating the
      part written and when; state that is not API is `private`; a directory README is the artifact's front page,
      a paragraph on the topic and nothing that code holds; `--help` prints a recipe's doc comment then its params
      (name, type, default or required, choices, doc) then its ingredients, and a model's class doc then its API
      and writes rendered from doc comments and annotations; the engine refuses to load a unit with an undocumented
      param, public member or annotated member, naming it.
- [ ] The rendered-help illustration uses `dev.thecook:ralph:0.1.0`.
- [ ] Layout states `<dir>/<Model>.groovy`, `<dir>/<recipe>.groovy`, `<dir>/README.md`, resources beside them, and
      `cookbook.toml` at the cookbook root with `group`, `version`, the engine API version and other cookbooks at
      exact releases.
- [ ] `cookbook/cookbook.toml` exists with `group = "dev.thecook"`, `version = "0.1.0"` and `api = 1`.

### US-002: Replace the recipe README rule in docs/ux
**Description:** As a documentation reader, I want `docs/ux/messages-and-docs.md` to describe how units document
themselves so that the UX standards and the sketch agree.

**Acceptance Criteria:**
- [ ] The documentation-structure table row for recipe READMEs now reads: cookbook units under `cookbook/` document
      themselves in doc comments, and a directory README is the artifact's front page.
- [ ] The sentence under "Every document opens with its objective" about recipe READMEs now says that cookbook
      units open with the lead of their doc comment.
- [ ] The "Recipe READMEs" subsection is replaced by a "Cookbook units" subsection of at most six lines: a recipe
      documents itself in the doc comment that opens its file, a model in its class and member doc comments, a
      directory README is the artifact's front page, and the shape is defined in `cookbook/README.md` (relative
      link).
- [ ] No other line of the page mentions a recipe README or a README template.

### US-003: Move git
**Description:** As a recipe author, I want `dev.thecook.git.Git` documented in its file so that I can use it from
the artifact alone.

**Acceptance Criteria:**
- [ ] `cookbook/git/Git.groovy` exists with `package dev.thecook.git` on line 1; `cookbook/ingredients/git/` is gone.
- [ ] The class has a doc comment with lead, Writes for (Git ignore syntax), Instances (one per directory, a second
      `.gitignore` is an instance `at` another directory), Use (a recipe excerpt with the import), With other
      models (consumes ignore patterns other models expose).
- [ ] Both `ignore` overloads and the `@Section` member have doc comments; the member's states the part and when.
- [ ] `patterns` is `private`.
- [ ] `cookbook/git/README.md` is one paragraph on the topic.

### US-004: Move agents
**Description:** As a recipe author, I want `dev.thecook.agents` to carry `Agents`, `Harness`, `Skill`, `Policy` and
`Command` documented in their file.

**Acceptance Criteria:**
- [ ] `cookbook/agents/Agents.groovy` with `package dev.thecook.agents` on line 1 holds all five types;
      `cookbook/ingredients/agents/` is gone.
- [ ] `Agents` has the five-section class doc comment; every public method and every annotated member has a doc
      comment; `connect`'s says to call it after adding skills; `instructions`, `skills`, `instructionsUsed` and
      `policyUsed` are `private`.
- [ ] `Harness`, `Skill`, `Policy` and `Command` each have a class doc comment and a doc comment on every public
      member.
- [ ] `Skill.getFiles()` reads `resources(name)`.
- [ ] `cookbook/agents/permissions.groovy` (was agent-permissions) and `cookbook/agents/context.groovy` (was
      agent-context) exist with: a leading doc comment with the six sections (empty ones omitted), the Writes table
      with a When column, invocations `dev.thecook:agents:0.1.0:permissions` and `dev.thecook:agents:0.1.0:context`;
      imports for `Agents`, `Claude` (`dev.thecook.claude.Claude`) and `Codex` (`dev.thecook.codex.Codex`); `doc`
      on every `@param`. `cookbook/recipes/agent-permissions/` and `cookbook/recipes/agent-context/` are gone.
- [ ] `cookbook/agents/README.md` is one paragraph on the topic.

### US-005: Move skills
**Description:** As a recipe author, I want the three skills in one `dev.thecook:skills` artifact.

**Acceptance Criteria:**
- [ ] `cookbook/skills/Solid.groovy`, `HexagonalLayout.groovy` and `EffectiveJavaCore.groovy` exist with
      `package dev.thecook.skills` on line 1, each importing `dev.thecook.agents.Skill`, each with a class doc comment
      (lead, Use) and a doc comment on `name`.
- [ ] Skill files live at `cookbook/skills/solid/SKILL.md`, `cookbook/skills/hexagonal-layout/SKILL.md` and
      `cookbook/skills/effective-java-core/SKILL.md`, unchanged in content.
- [ ] `cookbook/ingredients/skills/` is gone.
- [ ] `cookbook/skills/README.md` is one paragraph on the topic.

### US-006: Move claude
**Description:** As a recipe author, I want `dev.thecook.claude.Claude` and the `settings` recipe documented in
their files.

**Acceptance Criteria:**
- [ ] `cookbook/claude/Claude.groovy` with `package dev.thecook.claude` on line 1 imports
      `dev.thecook.agents.Harness`, `dev.thecook.agents.Policy` and `dev.thecook.agents.Command`; five-section class
      doc comment, Writes for naming the Claude Code settings schema; doc comments on every public member and
      annotated member; `settings`, `instructions` and `skillLinks` are `private`; `localFiles` stays public with a
      doc comment.
- [ ] `cookbook/claude/settings.groovy` (was claude-settings) has the leading doc comment, imports, `doc` on every
      `@param`, invocation `dev.thecook:claude:0.1.0:settings`.
- [ ] `cookbook/ingredients/claude/` and `cookbook/recipes/claude-settings/` are gone; `cookbook/claude/README.md`
      is one paragraph.

### US-007: Move codex
**Description:** As a recipe author, I want `dev.thecook.codex.Codex` and the `settings` recipe documented in their
files.

**Acceptance Criteria:**
- [ ] `cookbook/codex/Codex.groovy` with `package dev.thecook.codex` on line 1, imports from `dev.thecook.agents`,
      five-section class doc comment with Writes for naming `config.toml`, `hooks.json` and the `prefix_rule` syntax;
      doc comments on every public member and annotated member; `config`, `hooks`, `rules`, `commands` and
      `instructions` are `private`.
- [ ] `cookbook/codex/settings.groovy` (was codex-settings) has the leading doc comment, imports, `doc` on every
      `@param`, invocation `dev.thecook:codex:0.1.0:settings`.
- [ ] `cookbook/ingredients/codex/` and `cookbook/recipes/codex-settings/` are gone; `cookbook/codex/README.md`
      is one paragraph.

### US-008: Move mise
**Description:** As a recipe author, I want `dev.thecook.mise.Mise` and the `runtime-version` and `grill` recipes
documented in their files.

**Acceptance Criteria:**
- [ ] `cookbook/mise/Mise.groovy` with `package dev.thecook.mise` on line 1; five-section class doc comment, Writes
      for naming the `mise.toml` task fields `run`, `dir`, `env` and the file-task directory; doc comments on every
      public member and annotated member; `tools`, `env`, `tasks` and `scripts` are `private`.
- [ ] `cookbook/mise/runtime-version.groovy` (was migrate-version-source) and `cookbook/mise/grill.groovy` exist
      with leading doc comments, imports (`dev.thecook.node.Node`, `dev.thecook.python.Python` where injected),
      `doc` on every `@param`, invocations `dev.thecook:mise:0.1.0:runtime-version` and `dev.thecook:mise:0.1.0:grill`.
- [ ] `cookbook/ingredients/mise/`, `cookbook/recipes/migrate-version-source/` and `cookbook/recipes/grill/` are
      gone; `cookbook/mise/README.md` is one paragraph.

### US-009: Move node
**Description:** As a recipe author, I want `dev.thecook.node.Node` and the `project` and `dependency` recipes
documented in their files.

**Acceptance Criteria:**
- [ ] `cookbook/node/Node.groovy` is the exemplar from `cookbook/ingredients/node/Node.groovy` with its package
      changed to `dev.thecook.node` and the class doc comment extended to the five sections, taking Writes for,
      Instances, Use and With other models from the old README.
- [ ] `cookbook/node/npm/` and `cookbook/node/yarn/` hold the four scripts unchanged.
- [ ] `cookbook/node/project.groovy` (was node-project) and `cookbook/node/dependency.groovy` (was
      add-node-dependency) exist with leading doc comments, imports (`dev.thecook.mise.Mise`, `dev.thecook.git.Git`
      where injected), `doc` on every `@param`, invocations `dev.thecook:node:0.1.0:project` and
      `dev.thecook:node:0.1.0:dependency`.
- [ ] `cookbook/ingredients/node/`, `cookbook/recipes/node-project/` and `cookbook/recipes/add-node-dependency/`
      are gone; `cookbook/node/README.md` is one paragraph.

### US-010: Move python
**Description:** As a recipe author, I want `dev.thecook.python.Python` and the `dependency` recipe documented in
their files.

**Acceptance Criteria:**
- [ ] `cookbook/python/Python.groovy` with `package dev.thecook.python` on line 1; five-section class doc comment;
      doc comments on every public member and annotated member; state that is not API is `private`.
- [ ] `cookbook/python/dependency.groovy` (was add-python-dependency) with leading doc comment, imports, `doc` on
      every `@param`, invocation `dev.thecook:python:0.1.0:dependency`.
- [ ] `cookbook/ingredients/python/` and `cookbook/recipes/add-python-dependency/` are gone;
      `cookbook/python/README.md` is one paragraph.

### US-011: Move go
**Description:** As a recipe author, I want `dev.thecook.go.Go` and the `dependency` recipe documented in their
files.

**Acceptance Criteria:**
- [ ] `cookbook/go/Go.groovy` with `package dev.thecook.go` on line 1; five-section class doc comment; doc comments
      on every public member and annotated member; state that is not API is `private`.
- [ ] `cookbook/go/dependency.groovy` (was add-go-dependency) with leading doc comment, imports, `doc` on every
      `@param`, invocation `dev.thecook:go:0.1.0:dependency`.
- [ ] `cookbook/ingredients/go/` and `cookbook/recipes/add-go-dependency/` are gone; `cookbook/go/README.md` is
      one paragraph.

### US-012: Move beads
**Description:** As a recipe author, I want `dev.thecook.beads.Beads` documented in its file.

**Acceptance Criteria:**
- [ ] `cookbook/beads/Beads.groovy` with `package dev.thecook.beads` on line 1 and `cookbook/beads/ready.sh`
      unchanged; five-section class doc comment; doc comments on every public member and annotated member;
      `tracked` and `issuePrefix` are `private`; `localFiles` and `ephemeralFiles` stay public with doc comments.
- [ ] `cookbook/ingredients/beads/` is gone; `cookbook/beads/README.md` is one paragraph.

### US-013: Move ralph
**Description:** As a recipe author, I want `dev.thecook.ralph.Ralph` and the `ralph`, `prd-story` and
`bead-task` recipes documented in their files.

**Acceptance Criteria:**
- [ ] `cookbook/ralph/Ralph.groovy` with `package dev.thecook.ralph` on line 1 and `cookbook/ralph/run.sh`
      unchanged; five-section class doc comment with Writes for naming Ralph TUI 0.12.0 and config version 2.1; doc
      comments on every public member and annotated member; `configured`, `standalone`, the seven settings and
      `focusedPrd` are `private`; `tools`, `localFiles` and `ephemeralFiles` stay public with doc comments.
- [ ] `cookbook/ralph/ralph.groovy` is the exemplar `cookbook/recipes/ralph/recipe.groovy` with the sections of
      `cookbook/recipes/ralph/README.md` moved into a leading doc comment, imports rewritten to
      `dev.thecook.beads.Beads`, `dev.thecook.git.Git`, `dev.thecook.mise.Mise`, `dev.thecook.ralph.Ralph`, and
      invocations `dev.thecook:ralph:0.1.0`.
- [ ] `cookbook/ralph/prd-story.groovy` (was implement-prd-story) and `cookbook/ralph/bead-task.groovy` (was
      implement-bead) exist with leading doc comments, imports, `doc` on every `@param`, invocations
      `dev.thecook:ralph:0.1.0:prd-story` and `dev.thecook:ralph:0.1.0:bead-task`; the With other recipes
      sections name each other and `ralph` by the new addresses.
- [ ] `cookbook/ingredients/ralph/`, `cookbook/recipes/ralph/`, `cookbook/recipes/implement-prd-story/` and
      `cookbook/recipes/implement-bead/` are gone; `cookbook/ralph/README.md` is one paragraph.

### US-014: Move docker
**Description:** As a recipe author, I want `dev.thecook.docker.Docker`, `dev.thecook.docker.Compose` and the
`compose-service` and `base-image` recipes documented in their files.

**Acceptance Criteria:**
- [ ] `cookbook/docker/Docker.groovy` and `cookbook/docker/Compose.groovy` with `package dev.thecook.docker` on
      line 1; five-section class doc comments; doc comments on every public member and annotated member; state that
      is not API is `private`.
- [ ] `cookbook/docker/compose-service.groovy` (was add-compose-service) and `cookbook/docker/base-image.groovy`
      (was update-docker-base) with leading doc comments, imports, `doc` on every `@param`, invocations
      `dev.thecook:docker:0.1.0:compose-service` and `dev.thecook:docker:0.1.0:base-image`.
- [ ] `cookbook/ingredients/docker/`, `cookbook/recipes/add-compose-service/` and
      `cookbook/recipes/update-docker-base/` are gone; `cookbook/docker/README.md` is one paragraph.

### US-015: Move aws
**Description:** As a recipe author, I want `dev.thecook.aws.Aws` and the `local-resource` recipe documented in
their files.

**Acceptance Criteria:**
- [ ] `cookbook/aws/Aws.groovy` with `package dev.thecook.aws` on line 1 and `cookbook/aws/check.sh` unchanged;
      five-section class doc comment; doc comments on every public member and annotated member; state that is not
      API is `private`.
- [ ] `cookbook/aws/local-resource.groovy` (was add-local-aws-resource) with leading doc comment, imports
      (`dev.thecook.terraform.Terraform`, `dev.thecook.mise.Mise`), `doc` on every `@param`, invocation
      `dev.thecook:aws:0.1.0:local-resource`.
- [ ] `cookbook/ingredients/aws/` and `cookbook/recipes/add-local-aws-resource/` are gone;
      `cookbook/aws/README.md` is one paragraph.

### US-016: Move terraform
**Description:** As a recipe author, I want `dev.thecook.terraform.Terraform` documented in its file.

**Acceptance Criteria:**
- [ ] `cookbook/terraform/Terraform.groovy` with `package dev.thecook.terraform` on line 1; five-section class doc
      comment; doc comments on every public member and annotated member; state that is not API is `private`.
- [ ] `cookbook/ingredients/terraform/` is gone; `cookbook/terraform/README.md` is one paragraph.

### US-017: Move github
**Description:** As a recipe author, I want `dev.thecook.github.Workflow` and the `aws-oidc` and `cache-key`
recipes documented in their files.

**Acceptance Criteria:**
- [ ] `cookbook/github/Workflow.groovy` with `package dev.thecook.github` on line 1; five-section class doc
      comment with Writes for naming the GitHub Actions workflow schema; doc comments on every public member and
      annotated member; state that is not API is `private`.
- [ ] `cookbook/github/aws-oidc.groovy` (was migrate-aws-credentials) and `cookbook/github/cache-key.groovy` (was
      refresh-ci-cache) with leading doc comments, imports (`dev.thecook.aws.Aws` where injected), `doc` on every
      `@param`, invocations `dev.thecook:github:0.1.0:aws-oidc` and `dev.thecook:github:0.1.0:cache-key`.
- [ ] `cookbook/ingredients/github/`, `cookbook/recipes/migrate-aws-credentials/` and
      `cookbook/recipes/refresh-ci-cache/` are gone; `cookbook/github/README.md` is one paragraph.

### US-018: Move cook
**Description:** As a recipe author, I want the engine's own footprint modeled as `dev.thecook.cook.Cook` with the
`cook` and `setup` recipes documented in their files.

**Acceptance Criteria:**
- [ ] `cookbook/cook/Cook.groovy` (was `TheCook`) with `package dev.thecook.cook` on line 1; class doc comment with
      lead, Writes for, Use, With other models; doc comment on `localFiles`.
- [ ] `cookbook/cook/cook.groovy` (was the-cook) with leading doc comment, imports `dev.thecook.cook.Cook` and
      `dev.thecook.git.Git`, invocation `dev.thecook:cook:0.1.0`.
- [ ] `cookbook/cook/setup.groovy` (was the-cook-setup) with leading doc comment whose With other recipes section
      says to apply `dev.thecook:cook:0.1.0` with it; imports for `Agents`, `Claude`, `Codex`, `Git`, `Solid`,
      `HexagonalLayout` and `EffectiveJavaCore` from their new packages; invocation `dev.thecook:cook:0.1.0:setup`.
- [ ] `cookbook/ingredients/the-cook/`, `cookbook/recipes/the-cook/` and `cookbook/recipes/the-cook-setup/` are
      gone; `cookbook/cook/README.md` is one paragraph.
- [ ] `docs/features/agentic-dogfood.md` links to `cookbook/cook/setup.groovy` and names the recipe
      `dev.thecook:cook:0.1.0:setup`, in the same commit, so the docs link check stays green.

### US-019: Final sweep
**Description:** As a maintainer, I want no trace of the old layout so that the sketch and the docs agree.

**Acceptance Criteria:**
- [ ] `cookbook/ingredients/` and `cookbook/recipes/` do not exist.
- [ ] `grep -rn "cookbook/recipes\|cookbook/ingredients" README.md docs/features docs/ux cookbook` returns nothing.
- [ ] `grep -rn "@param" cookbook | grep -v "doc ="` returns nothing.
- [ ] Every `cookbook/*/*.groovy` whose class carries `@Ingredient` has `package dev.thecook.<dir>` on line 1, and
      every recipe file (no `@Ingredient`) starts with `/**`.
- [ ] Every `@ingredient(Type)` in a recipe has a matching `import dev.thecook.<dir>.Type` line.
- [ ] Every directory under `cookbook/` has a `README.md`.
- [ ] A scratch script parses every `cookbook/**/*.groovy` with Groovy 4 to the CONVERSION phase without error
      (syntax only; imports are not resolved).

## Functional Requirements

- FR-1: A cookbook directory is one artifact holding its models, recipes, resources and one README.
- FR-2: A model's package is the cookbook group plus the directory name: `dev.thecook.<dir>`.
- FR-3: A recipe is `<dir>/<name>.groovy`: a leading `/** */` doc comment, imports for every injected type,
  `@param` lines each with `doc`, `@ingredient` lines, then the body.
- FR-4: A recipe is addressed `group:artifact:version[:recipe]`; the recipe named like its directory is the apply
  default. All invocations in docs use `dev.thecook:<dir>:0.1.0[:<recipe>]`.
- FR-5: One release version per cookbook, declared in `cookbook/cookbook.toml` with `group`, `version` and `api`.
- FR-6: Recipe names are noun phrases naming the state the destination gets, without repeating the directory's
  topic. The mapping is fixed:

  | Today | Directory | Recipe file |
  | --- | --- | --- |
  | the-cook | `cook` (model `Cook`) | `cook.groovy` |
  | the-cook-setup | `cook` | `setup.groovy` |
  | ralph | `ralph` | `ralph.groovy` |
  | implement-prd-story | `ralph` | `prd-story.groovy` |
  | implement-bead | `ralph` | `bead-task.groovy` |
  | node-project | `node` | `project.groovy` |
  | add-node-dependency | `node` | `dependency.groovy` |
  | add-python-dependency | `python` | `dependency.groovy` |
  | add-go-dependency | `go` | `dependency.groovy` |
  | migrate-version-source | `mise` | `runtime-version.groovy` |
  | grill | `mise` | `grill.groovy` |
  | add-compose-service | `docker` | `compose-service.groovy` |
  | update-docker-base | `docker` | `base-image.groovy` |
  | add-local-aws-resource | `aws` | `local-resource.groovy` |
  | migrate-aws-credentials | `github` | `aws-oidc.groovy` |
  | refresh-ci-cache | `github` | `cache-key.groovy` |
  | agent-permissions | `agents` | `permissions.groovy` |
  | agent-context | `agents` | `context.groovy` |
  | claude-settings | `claude` | `settings.groovy` |
  | codex-settings | `codex` | `settings.groovy` |
  | skills/* | `skills` | none |

- FR-7: A recipe's doc comment has, in order, a lead, Before applying, Apply, Writes (Target, Part, When), After
  applying, With other recipes; empty sections are omitted.
- FR-8: A model's class doc comment has a lead, Writes for, Instances, Use, With other models.
- FR-9: Every public method or property and every annotated member of a model has a doc comment; an annotated
  member's states the part written and when.
- FR-10: Model state that is not API is `private`.
- FR-11: A directory README is one paragraph on the topic and holds nothing that code holds.
- FR-12: Doc comments and READMEs describe what things are, in present tense, with no history and no mention of
  former names or layouts.
- FR-13: The body of a recipe calls model methods in a meaningful order; a method that must follow others says so
  in its doc comment.
- FR-14: Behaviour of the sketch does not change: the same declarations, the same params with the same names,
  defaults and choices, the same resources.

## Non-Goals

- No engine implementation: the sketch stays pseudo-language and nothing under `cookbook/` runs or is built.
- No change to `docs/architecture.md`, `docs/adrs/`, `docs/features/glossary.md` or `core/README.md`; the
  amendments for coordinates as an operand kind, `cook.toml`, the lock source form and entry and line targets are
  a later design-docs step.
- No lifecycle hooks, no automatic re-push in `Agents`, no rename of `connect`.
- No fix for the 47 red core and CLI tests; they belong to the #15 engine work.
- No GitHub issue comments; the CLI is blocked in this environment.
- No change to `.gitignore`, `.agents/policy.yaml` or `.beads/`.

## Technical Considerations

- The cookbook is excluded from Spotless and CodeNarc in `build.gradle`; the quality gates lint the rest of the
  repository and check docs links.
- The main checkout holds an ignored worktree under `.worktrees/` that pollutes CodeNarc and Spotless runs there;
  a fresh worktree or clone does not have it.
- `docs/features/agentic-dogfood.md` links to `cookbook/recipes/the-cook-setup/README.md`; US-018 moves that file
  and updates the link in the same commit, so the docs link check never breaks.
- Use `git mv` so history follows each file.
- Groovy 4 accepts standard `/** */` doc comments on classes, methods and fields; no `/**@` marker is needed.

## Success Metrics

- All nineteen stories merged on `main`, each with a green gate.
- `cookbook/` contains only topic directories, `README.md` and `cookbook.toml`.
- Zero undocumented params, public members or annotated members under `cookbook/`.
- The docs link check passes.

## Open Questions

- Whether the version `0.1.0` in invocations should be replaced by a placeholder once a release scheme exists.
