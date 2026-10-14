# PRD: Align documentation with the agreed Cook architecture

## Introduction / Overview

The repository contains definitions from several rounds of design. Its architecture, philosophy, ADRs, feature reference, UX standards, and implementation documentation can describe different products. Issue #1 needs a bounded documentation cleanup that makes the intended design clear while implementation continues.

This work documents the decisions reached in the architecture grilling and reconciles existing definitions case by case. It does not implement the target behavior. Missing implementation is a gap, not evidence that an agreed design is stale.

`docs/architecture.md` describes the intended architecture. Implementation progress belongs in issues. Do not introduce document lifecycle labels, supersession chains, an archive framework, or an implemented/planned/undecided status system.

Related issues:
- [#1: Design documentation](https://github.com/lmarqs/the-cook/issues/1)
- [#9: Design review and proposed implementation cleanup](https://github.com/lmarqs/the-cook/issues/9)

## Goals

- Establish one coherent intended architecture in `docs/architecture.md`.
- Correct or remove conflicting definitions throughout the affected documentation.
- Preserve the distinction between intended behavior and descriptions of the implementation.
- Specify the agreed CLI, ordered operations, review, history, reversal, boundaries, and distribution.
- Preserve ingredients as program data models without settling the deferred recipe/model API.
- Produce a concise closeout note for #1 and identify implementation work for separate tracking.
- Keep the change limited to documentation.

## Quality Gates

None. The user explicitly requested no mandatory quality gates or check commands.

Do not add required build, test, lint, link-check, graph-check, or CI commands. The acceptance criteria below define the documentation deliverables; they do not authorize implementing the described features.

## Agreed Architecture Requirements

These requirements describe the intended product to document, not functionality to implement in this PRD.

### Responsibilities and scope

- **FR-1:** Cook evaluates trusted Groovy recipes into an ordered batch of file operations. Cook presents the resulting diff, obtains approval, records recovery information, and performs the writes.
- **FR-2:** Ingredients model a program's native files and configuration, such as Git's ignore file or Codex's configuration and skills. Cookbooks hold and ship ingredients and recipes through their repositories; their builds can be separate from Cook's build.
- **FR-3:** Batches contain file operations, including directory and symlink operations where applicable. Executing external commands is not a batch action.
- **FR-4:** Recipes are trusted executable code. Approval, output boundaries, and rollback govern declared batch operations; they are not a sandbox or a guarantee against arbitrary side effects in recipe code.
- **FR-5:** The CLI is the public cooking interface. Gradle builds and packages Cook and cookbooks; it is not a second public cooking interface.
- **FR-6:** Personal setup backup (kit) and agent-session orchestration (cooking) are future capabilities. A fixed software development lifecycle, workers, receipts, jails, and session orchestration are not current Cook engine responsibilities.

### Ordered operations and review

- **FR-7:** Multiple recipes and their operations execute in declared order, left to right. Overlap is intentional composition; later instructions can replace earlier values. Show the combined result as one batch diff.
- **FR-8:** Applying a batch affects its declared targets. Applying recipe B does not implicitly remove unrelated outputs from recipe A.
- **FR-9:** Cook proposes the requested changes against current content without ownership-state reconciliation, adoption flags, or a refusal merely because a developer edited the target. The target CLI has no `--adopt` flag, compatibility alias, or separate adoption step; existing content uses the same batch diff and approval flow.
- **FR-10:** Whole-file targets use file paths; structured targets use key paths; sections in shared text files use begin/end markers. Markers identify boundaries. Use the file format's appropriate comment syntax, including HTML comments for Markdown.
- **FR-11:** Display the final diff in the terminal. Approval covers the whole batch; partial selection of files or hunks is outside this design.
- **FR-12:** Without preapproval, ask before changing target files. With `--batch-approve`, display the diff and proceed without prompting. Noninteractive execution without this flag does not silently authorize writes.
- **FR-13:** Save recovery patches before the first target write. On a write failure, roll back changes already made by the batch and retain the patches and failure record.

### Apply, discard, and revert

- **FR-14:** Applying a recipe proposes its declared additions and updates. Discarding proposes deleting its declared contributions, even when their current values differ from what the recipe would generate. Deleting a declared `test` key preserves unrelated keys.
- **FR-15:** Discard evaluates the supplied recipe version. Cook does not track a persistent recipe identity or infer an earlier version. Users manage recipe versioning and supply the version they intend to apply or discard.
- **FR-16:** Reverting a run reverses the recorded operation using its history. It restores that operation's prior values rather than evaluating a recipe again. Later edits do not create an ownership/drift veto: propose the reversal against current content, show the actual diff, and preserve unrelated content.

### CLI grammar

- **FR-17:** Support these intended forms:

```sh
./cook [batch-options] <recipe> [recipe-arguments] [<recipe> [recipe-arguments]]...
./cook apply [batch-options] <recipe> [recipe-arguments] [<recipe> [recipe-arguments]]...
./cook discard [batch-options] <recipe> [recipe-arguments] [<recipe> [recipe-arguments]]...
./cook revert [batch-options] <run-id>
```

`apply` is the default. With no arguments, show help. Initial bare command names select an operation; a file named `revert`, for example, can be supplied as `./revert`.

- **FR-18:** Recipe operands are exact file paths or supported source URIs, including local paths, `file://`, and `https://`. Ordinary relative paths such as `recipe.groovy` are accepted. There is no implicit cookbook-name lookup.
- **FR-19:** Batch options precede recipe operands, or the run ID for `revert`. Recipe arguments follow their recipe and use `--name=value`, including shell-quoted values. A misplaced batch option after a recipe receives the normal unknown-option error.
- **FR-20:** Use `discard`, not the earlier tentative `--discard` form. Use the final spelling `--batch-approve`, not `--batch-approved`, `--approve`, or `--yes`.

Examples:

```sh
./cook ./recipe.groovy
./cook apply --batch-approve ./base.groovy --agent=codex ./project.groovy --agent=claude
./cook discard --batch-approve ./recipe.groovy
./cook revert --batch-approve <run-id>
./cook apply ./a.groovy --source=./b.groovy
./cook apply ./a.groovy ./b.groovy
```

In the last two examples, `b.groovy` is respectively an argument value and a second recipe.

### Paths, snapshots, and history

- **FR-21:** The current working directory is the destination, regardless of the recipe or executable location. Declared writes and deletions stay within that directory, including paths resolved through symlinks.
- **FR-22:** Relative references to source recipes and templates resolve from the referencing recipe's original location. HTTPS references resolve against the source URL. Saving a snapshot must not silently change relative source resolution.
- **FR-23:** Keep local operation history under the destination's ignored `.cook/` directory, with a separate run record. Save one reversible patch per touched file, the recipe sources actually evaluated, consumed recipe/template sources, origins, arguments, execution order, operation, and result or failure log.
- **FR-24:** Capture local sources as well as downloaded sources. A single patch supports forward and reverse application; separate forward/reverse copies are unnecessary. Do not specify a metadata serialization format or complete storage schema in this documentation task.
- **FR-25:** Apply and discard do not require an ownership database or previous run history. Losing `.cook/` loses local recovery information, not the ability to cook. Reverting a recorded run requires that run's history. Every apply/discard invocation evaluates the supplied recipes against current content; history must not skip a recipe as already applied. An unchanged result may produce an empty diff.

### Distribution and documentation authority

- **FR-26:** Retain the JVM-based distribution using jpackage, with a platform-specific package containing Java. A committed `./cook` wrapper pins a version and checksum, fetches it on first use, and caches it locally. Upgrades are explicit.
- **FR-27:** `docs/architecture.md` states the intended architecture. Supporting docs follow it. Describe actual implementation behavior truthfully where a document expressly covers the implementation.
- **FR-28:** Correct contradictory definitions in place and remove obsolete material when its subject no longer belongs. Preserve relevant reasoning; Git retains historical versions. Do not automatically delete entire document families or invent missing rationale.
- **FR-29:** Drop the acyclic-documentation requirement. Useful cross-references are allowed. Do not introduce an ordered graph map, numbered directories, graph enforcement, or broad file moves solely to satisfy that former requirement.
- **FR-30:** Ingredient/recipe APIs, nesting, lifecycle hooks, decorators, parameter injection, and annotations need their own grilling. The sketches using `Recipe(...)`, `Batch().withRecipe(...)`, `@pwd`, `@param`, and `@before` are exploratory and must not become normative APIs in this work.

## User Stories

The acceptance criteria below are documentation changes only. Stories should be converted into separate tasks, with the execution order described under Technical Considerations.

### US-001: Establish the architecture's responsibilities

**Description:** As a contributor, I want one authoritative explanation of Cook's responsibilities so that I do not build features from an obsolete product definition.

**Acceptance Criteria:**
- [ ] Rewrite the scope, responsibilities, and boundaries in `docs/architecture.md` around FR-1 through FR-6.
- [ ] Remove the Go-only, no-code, fixed-SDLC-core, and three-current-product-parts claims from the intended architecture.
- [ ] Preserve ingredients as program data models and cookbook distribution as separate from the engine.
- [ ] Keep model APIs and hooks explicitly outside the decisions made by this document, without adopting exploratory syntax.
- [ ] Remove document lifecycle/status machinery; architecture states the design rather than an implementation progress table.

### US-002: Specify ordered file operations

**Description:** As a recipe author, I want the architecture to define exactly what Cook applies and discards so that overlap and manual edits have predictable results.

**Acceptance Criteria:**
- [ ] Document FR-7 through FR-16 in the architecture.
- [ ] Include an example where recipe A sets a key and recipe B overrides it; the final preview shows B's result.
- [ ] Include an example where applying B leaves A's unrelated output intact.
- [ ] Contrast discard and revert using a key changed from `old` to `new`, then manually to `custom`: discard proposes deletion; reverting the original run proposes `custom` to `old`.
- [ ] Explain markers as text boundaries and remove ownership/adoption/drift enforcement from the target operation contract.
- [ ] State that apply/discard always evaluate the supplied recipes against current content, even after an earlier identical invocation; history does not mark recipes as already applied.

### US-003: Document the CLI grammar

**Description:** As a developer, I want one unambiguous command reference so that I can compose recipes and approve a batch without guessing argument scope.

**Acceptance Criteria:**
- [ ] Align the architecture's CLI section and `docs/features/cli.md` with FR-17 through FR-20.
- [ ] Show default and explicit apply, discard, revert, multiple recipes, and per-recipe arguments.
- [ ] Explain exact paths/URIs, reserved initial command names, and a recipe path such as `./revert`.
- [ ] Include the argument-value versus second-recipe example and the misplaced batch-option error.
- [ ] Remove obsolete target CLI commands and tentative flag spellings rather than promising compatibility aliases.
- [ ] Explicitly exclude `--adopt`, adoption aliases, and separate adoption steps from the target CLI; existing content follows the same diff and batch-approval flow.

### US-004: Document history, sources, and destination boundaries

**Description:** As a developer, I want to know what a run records and how paths resolve so that I can inspect and reverse an operation.

**Acceptance Criteria:**
- [ ] Document FR-21 through FR-25 in the architecture. Review all of `docs/features/render.md` and `docs/features/remove.md` against FR-7 through FR-25, replacing obsolete render/remove/eject contracts, flags, ownership gates, and restoration rules with apply/discard/revert, whole-batch approval, and stateless operations. Preserve useful paths and repair affected references.
- [ ] Show local and remote recipes using the invocation directory as their destination while resolving source references from the recipe origin.
- [ ] State that all consumed recipe/template sources are captured, including local ones.
- [ ] Explain one patch per touched file, saved before target writes, and one recorded operation for the whole batch.
- [ ] Distinguish optional prior history for apply/discard from required history for reverting a particular run; do not prohibit JSON metadata merely because ownership state is removed.

### US-005: Align distribution and cookbook documentation

**Description:** As a user, I want the installation design to describe the chosen delivery mechanism so that I understand what must be installed and versioned.

**Acceptance Criteria:**
- [ ] Align the distribution portions of the architecture, `docs/features/installation.md`, and `docs/features/wrapper.md` with FR-26.
- [ ] Describe jpackage, bundled Java, the pinned wrapper, checksums, first-use download/cache, and explicit upgrades.
- [ ] Keep cookbook repositories and builds distinct from engine release responsibilities.
- [ ] Do not introduce recipe-version management, a registry, or an updater not agreed in the grilling.
- [ ] Do not claim a published release or working pinned-download wrapper exists unless the implementation evidence supports that claim.

### US-006: Align philosophy and documentation authority

**Description:** As a reader, I want the project's goals and reading path to agree with the architecture so that earlier decisions cannot silently override it.

**Acceptance Criteria:**
- [ ] Update `docs/philosophy.md` to reflect the file-operation engine, trusted recipes, separate cookbooks, and deferred kit/session responsibilities.
- [ ] Update authority statements in `docs/adrs/README.md`, `docs/features/README.md`, and `docs/ux/README.md` to follow `docs/architecture.md`.
- [ ] Remove instructions that make an obsolete ADR or standard override the agreed architecture.
- [ ] Retain existing directories and useful cross-references; do not introduce status banners, a supersession registry, or an acyclic reading-order scheme.

### US-007: Reconcile ADRs about role, stack, and boundaries

**Description:** As a maintainer, I want architectural reasoning to match the chosen responsibilities so that old constraints do not direct new implementation.

**Acceptance Criteria:**
- [ ] Review ADRs 0001, 0002, 0003, 0004, 0006, 0012, and 0028 individually against the agreed architecture.
- [ ] Retain valid objectives and reasons, rewrite conflicting decisions, and remove records whose entire subject is obsolete.
- [ ] Reconcile data-only repository claims with trusted Groovy recipes and Go-only claims with the jpackage/JVM distribution.
- [ ] Scope output-boundary guarantees to Cook's batch operations without claiming arbitrary recipe code is sandboxed.
- [ ] Update references and index entries affected by these edits or removals.

### US-008: Reconcile ADRs about file changes and ownership

**Description:** As a maintainer, I want file-editing decisions to support explicit review instead of persistent ownership reconciliation.

**Acceptance Criteria:**
- [ ] Review ADRs 0005, 0007, 0008, 0009, 0011, 0016, and 0018 individually.
- [ ] Remove target requirements for committed/local ownership manifests, automatic withdrawal, adopted-original restoration during discard, and drift vetoes.
- [ ] Retain relevant format-preservation and native-file reasoning where consistent with ordered operations.
- [ ] Remove obsolete safety-floor obligations and avoid replacing them with an unrequested sandbox or enforcement system.
- [ ] Repair affected references without mechanically banning historical words inside necessary explanations.

### US-009: Reconcile ADRs about distribution and composition boundaries

**Description:** As a maintainer, I want distribution and cookbook decisions to reflect the agreed architecture without prematurely deciding the model API.

**Acceptance Criteria:**
- [ ] Review ADRs 0010, 0013, 0014, 0017, 0021, 0022, 0023, 0024, and 0026 individually.
- [ ] Align wrapper/release reasoning with jpackage and explicit version/checksum pinning.
- [ ] Replace conflicting program-knowledge and ownership-storage requirements using the agreed responsibilities.
- [ ] Preserve the ingredient concept while leaving recipe/ingredient APIs, station semantics, and lifecycle mechanics for the separate model discussion.
- [ ] Do not infer a decision to delete dynamic composition or compiled suppliers from issue #9.

### US-010: Remove obsolete lifecycle architecture from active references

**Description:** As a contributor, I want the reference pages to stop presenting a retired lifecycle engine as Cook's contract.

**Acceptance Criteria:**
- [ ] Review ADRs 0015, 0019, 0020, 0025, and 0027 and the feature pages for lifecycle, attempts, verification, tasks, workers, workspaces, fleet, context, memory, doctor, explain, and safety.
- [ ] Remove obsolete lifecycle-engine material; retain or relocate a passage only when it supports an agreed responsibility.
- [ ] Keep future kit/session work as a brief boundary/reference to its existing issue, without designing it here.
- [ ] Review the remaining feature pages, including concepts, glossary, ingredients, recipes, stations, policy, hooks, profile, scaffold, skills, compatibility, FAQ, and pitfalls, for definitions inherited from that engine.
- [ ] Preserve useful material case by case and repair affected navigation; neither deleting everything nor keeping everything is the default.

### US-011: Align UX standards with the agreed interaction

**Description:** As a user, I want the standards to describe the same commands and approval flow as the architecture.

**Acceptance Criteria:**
- [ ] Align `docs/ux/cli-standards.md`, `generated-content-standards.md`, and `configuration-standards.md` with terminal diffs, whole-batch approval, explicit operations, ordered overlap, and local history.
- [ ] Align the remaining UX pages with the same scope, retaining only relevant guidance.
- [ ] Update `docs/ux/messages-and-docs.md` where its terminology or rules prescribe the old lifecycle engine.
- [ ] Remove acyclic-link obligations wherever present; do not add graph tooling or directory-order requirements.
- [ ] Avoid specifying new exit codes, hook phases, patch formats, or metadata schemas not decided in this session.

### US-012: Keep implementation documentation truthful

**Description:** As a developer using the repository today, I want implementation documentation to distinguish what runs from what the architecture requires.

**Acceptance Criteria:**
- [ ] Review `README.md`, `core/README.md`, and affected cookbook READMEs for claims about current execution.
- [ ] Preserve accurate descriptions of existing behavior when explicitly documenting implementation, and link to the intended architecture without presenting existing behavior as its authority.
- [ ] Do not claim the target CLI, stateless operations, snapshots, or pinned wrapper are implemented merely because the architecture specifies them.
- [ ] Replace the broken fresh-clone quickstart with an honest entry path supported by the implementation, such as build/help instructions; do not require untracked policy or personal skill files without explaining the prerequisites.
- [ ] Do not change source, tests, build files, recipes, or fixtures to make the documentation examples true.

### US-013: Prepare the issue #1 closeout

**Description:** As the maintainer, I want a concise closure assessment so that documentation work and implementation work remain separately accountable.

**Acceptance Criteria:**
- [ ] Produce `tasks/issue-1-closeout.md` listing the documentation changes and any remaining documentation blockers.
- [ ] Identify implementation gaps for separate tracking under #9 or successor work, without making completion of those features a condition of #1.
- [ ] State that the acyclic-link requirement was withdrawn and no mandatory quality gates were requested.
- [ ] Identify the ingredient/recipe model and hooks as a separate discussion; do not treat exploratory sketches as approved APIs.
- [ ] Record material corrections to #9 after checking current evidence, including CLI reachability of dynamic factories and whether the BatchAdapter reference is still broken.
- [ ] Treat the note as a draft handoff; do not post comments, edit issues, or close #1 automatically.
- [ ] Review the completed architecture, feature references, ADRs, UX standards, and implementation guides together for contradictions and affected cross-references. Record unresolved documentation conflicts as closeout blockers; no new check tooling or mandatory command is required.
- [ ] Explicitly hand off removal of `--adopt` wherever exposed and removal of underlying adoption gates as required product implementation work. The replacement behavior is review and approval of the actual batch diff for existing or edited content, with no adoption alias or ownership-state prerequisite. Documentation completion alone does not complete this code change.

## Non-Goals

- Implementing any CLI, ownership, rendering, history, patch, source-loading, wrapper, or distribution changes.
- Deleting either composition implementation, the Gradle cooking tasks, or cookbook definitions as code cleanup.
- Designing the ingredient/recipe API, annotations, nesting, decorators, injection, or lifecycle hooks.
- Designing a sandbox for trusted Groovy recipes.
- Implementing kit backup or agent-session orchestration.
- Introducing recipe identity/version management.
- Adding documentation lifecycle labels, supersession tracking, archive infrastructure, or an acyclic link graph.
- Adding tests, check scripts, quality-gate commands, or CI.
- Editing existing `tasks/prd.json`, local agent settings, Beads data, or unrelated working-tree changes.
- Publishing or closing GitHub issues.

## Technical Considerations

- Implement US-001 first; US-002 through US-005 complete the architecture's operating contract. They edit shared files and should run sequentially.
- Use the resulting architecture to drive US-006 through US-011. Keep cross-reference updates within the story that changes or removes their target.
- US-012 documents current behavior from inspected code. US-013 follows the completed documentation changes.
- Treat issue #9 as review input, not an unquestioned deletion plan. Its cited revision predates later CLI composition changes.
- The decisions in this PRD take precedence over earlier proposals in issues or local inventory branches. Do not merge an old inventory wholesale.
- Missing code does not invalidate intended architecture. Conversely, writing a target requirement does not establish that it is implemented.
- Keep existing paths when useful. Any page removal is a case-by-case documentation decision, with its meaningful content and references accounted for.
- Patches for binary files, file modes, symlinks, empty directories, and reversals after overlapping edits need later implementation design. Do not invent those mechanisms while documenting the agreed guarantees.
- The JSON tasks split US-007 through US-012 into focused page groups while preserving their acceptance criteria. Split cookbook implementation guides into agent-setup guides and the cookbook index plus remaining recipe guides; the final closeout follows both groups.

## Success Metrics

- One architecture document expresses the agreed design without implementation-status machinery.
- No active design/reference page presents a conflicting architecture as authoritative.
- Implementation documentation makes no unsupported claim that the target behavior already exists.
- The final CLI examples use one grammar and distinguish apply, discard, and revert.
- Ingredients remain meaningful domain concepts; no exploratory model API is silently finalized.
- A concise closeout note explains whether #1's documentation work is complete and separates remaining implementation work.
- The resulting changes contain documentation only and introduce no mandatory verification tooling.

## Open Questions

These are intentionally outside this documentation PRD and do not block it:

- The ingredient/recipe model, composition API, station role, annotations, parameter injection, and lifecycle hooks.
- The concrete patch representation, run metadata schema, history management, and mechanics for reversing overlapping edits.
- Implementation sequencing and issue breakdown for bringing the code into line with the intended architecture.

Do not resolve these by inference while editing the documentation.

