# Do these pieces actually compose?

This is a **design probe, not a supported schema**. The ten [station candidates](../stations/) and ten new [recipe candidates](../recipes/) below are deliberately tested in combinations. Their names do not make them composable. The project has no binary yet.

## One repository, several choices

Imagine a Go service with a Docker image, a local Docker Compose stack, LocalStack resources defined by Terraform, Beads tasks, ralph-tui, and both Codex and Claude available as workers. A developer may later switch the runner's task source to `prd.json`, change the Go version, or replace the local stack.

| Combination | Expected result | What the current sketch cannot guarantee |
| --- | --- | --- |
| Go + Docker | One Go version is reflected in `mise.toml`, `go.mod` when appropriate, Dockerfile builder stage, and CI cache inputs. | The Go ingredient edits one TOML key; Dockerfile and CI remain independent. |
| Docker + Compose + Terraform | Compose starts the service; Terraform points at its actual endpoint after it is ready; local state stays local. | File fragments do not convey an endpoint, readiness, or state lifecycle. See [local stack](local-stack-compose-terraform.md). |
| Beads + ralph-tui | A run can find the Beads database from its worktree, select ready tasks, and preserve committed Beads config while excluding local state. | A tracker name in `config.toml` does not set up worktree access or decide what under `.beads/` is shared. |
| `prd.json` + ralph-tui | The runner reads a task file supplied for this run, without adding a second persistent task source. | `prd.json` is data, not an installed tool. Calling every selectable thing an ingredient obscures this difference. |
| Codex + Claude | Both can be available; a run chooses one worker. Shared policy is translated into each native format, with unsupported rules reported. | Two agent cards with `files: {}` contribute no settings; separate native permission formats cannot be merged as plain lines. |
| Beads + `prd.json` | Switching the runner's source removes the old selection or explicitly permits two named runs. | Selecting both as an unordered list leaves `tracker` ambiguous. |
| `node-npm` + `node-pnpm` stations | A conflict is reported with a way to replace the package manager choice. | The documented station rule keeps the first value, so application order silently decides. |
| `ralph-beads` + `ralph-prd` stations | A conflict is reported for the runner's default task source; Codex and Claude remain available. | Both want `.ralph-tui/config.toml`; one-time copies do not define a safe upgrade or withdrawal. |
| New AWS or cache practice + old repositories | A reviewed migration recognizes old workflow steps and updates callers, cache keys, and related tasks. | A fresh template cannot identify and retire unmanaged old content. See [AWS migration](aws-credential-migration.md). |

## Where the current nouns break

An **ingredient** currently means both a program (`codex`, `terraform`) and a selectable repository choice (`npm`, `git`). `prd.json` is a task file, yet the runner needs to select it the same way it selects Beads. A **station** is described as one-time copied setup whose first value wins; that is a preset, not a component with reliable merge behavior. A **recipe** is a named session; some proposed recipes also migrate persistent files. Those can be valid sessions, but the recipe alone cannot supply continuing ownership of the files it changes.

The newly added cards with empty `files` make this visible. Naming ralph-tui, Codex, or Claude in a YAML file does not wire them together. Conversely, generating a whole `.ralph-tui/config.toml` in each station makes the two runner stations collide. Ten more presets would multiply combinations rather than solve them.

## Minimum rules a composable model would need

1. A selection declares what it **provides** and what it **needs**. The runner needs a task source and at least one worker; local Terraform needs an endpoint from the Compose stack. File paths alone do not express these relationships.
2. Values with one authority are selected explicitly: one package manager per Node workspace, one default task source per runner, one owner of a whole file. Codex and Claude can both be available because they are alternatives chosen per run, not competing defaults.
3. Two contributions to the same file merge only at identified keys, list entries, or marked blocks. A conflicting value is an error with both sources named. “First station wins” is not a composition rule.
4. A station may propose a starting selection and repo-owned templates. Applying another station must report incompatible selections before writing. Updating a station needs a three-way view of old template, new template, and repo edits; recording only that it was once applied is insufficient.
5. A recipe may perform setup, migration, or a work session, but must state what persists. Persistent changes need an owner or an explicit handoff to the repository; otherwise a later `kitchen apply` may undo them.
6. A migration must recognize the old form or stop. A new desired state cannot safely delete arbitrary old workflow steps, shell snippets, Docker stages, or cache keys.

These are **requirements exposed by the examples**, not implemented capabilities. If the first working slice cannot compose Go + Docker + Compose + Terraform without repository-specific glue, the honest product is a file reconciler with useful templates and migrations. It should not promise arbitrary ingredient composition.

## Three honest product choices

| Choice | What Cook would promise | Cost shown by these examples |
| --- | --- | --- |
| File reconciler plus explicit migrations | Combine independent file contributions, report collisions, and run reviewed migrations for coupled changes. | The Go/Docker and Compose/Terraform relationships remain authored per repository. Call this reuse, not general composition. |
| Typed composition engine | Define outputs and inputs such as `localstack.endpoint`, one-of selections for task source, and dependency-aware updates across files. | Cook must model program operations, ordering, worktree state and failure recovery. That is far beyond the current `files` sketch. |
| One station for every combination | Publish ready stacks such as Go + Docker + Compose + Terraform + Beads + Codex. | Variants multiply as soon as a repo changes worker, task source, package manager or local stack. Updating old stations still requires migrations. |

The first choice is the smallest credible implementation. It does **not** satisfy the stronger ambition of arbitrary composition. Before claiming that ambition, prove one reused local-stack definition in two unlike repositories: apply it with only explicit parameters, change its endpoint convention once, and produce correct, reviewable diffs in both without a hand-written repo-specific migration. The existing examples give no basis to claim that test passes.
