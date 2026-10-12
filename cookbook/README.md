# Cookbook concept sketch

This is an **illustration, not a supported schema or complete ignore template**. The project has no binary yet. The files under [`ingredients/`](ingredients/) show a proposed composition flow for the kitchen; the [stress test](examples/composability-stress-test.md) shows where that proposal currently breaks:

```sh
the-cook init
./cook ingredient add git node@22 npm
./cook kitchen apply
```

Each ingredient declares contributions under `files`, keyed by target path. The [Git ingredient](ingredients/git.yaml) declares `owns: true` for `.gitignore`: it can create the file when missing. Node and npm contribute lines but do not create it. Without Git selected, they can contribute to an existing `.gitignore`, but cannot create a missing one. The same rule applies to any target path; `.gitignore` has no dedicated field in the model.

`owns: false` is the default. `owns: true` makes an ingredient responsible for the file's existence, not for every line or key inside it. Every ingredient manages only its declared contributions; the kitchen preserves unrelated content in an existing file. If the ingredient that created a file is removed, the kitchen must preserve any manual content instead of deleting the whole file. The selections are stored in the repository so `./cook kitchen apply` can reproduce the same result later.

`append` is a sketch of the operation for a line file. A real implementation must replace each ingredient's previous contribution on repeat or removal, rather than append duplicate lines. It must also define where the managed content sits when line order affects meaning. Other file types can use other operations under the same `files` mapping.

For example, that selection would contribute these lines, with the reasons kept beside them:

```gitignore
# the-cook: begin (illustrative marker)
# node: Installed dependencies.
node_modules/
# npm: npm debug logs.
npm-debug.log*
# npm: Ignore an untracked Yarn lockfile while npm is selected.
/yarn.lock
# the-cook: end
```

`npm` and [Yarn](ingredients/yarn.yaml) occupy one proposed `package-manager` slot. Selecting Yarn replaces npm and removes npm's contributions instead of leaving an `ignore-yarn-lock` selection behind. The [switch example](examples/package-manager-switch.md) shows the intended two-command flow and the migration work the current sketch cannot perform. An ignore pattern also does not prevent someone from force-adding a file or remove a file Git already tracks.

The [Node with mise example](examples/node-version-with-mise.md) shows a parameterized ingredient contributing to `mise.toml` and, when already present, `@types/node` in `package.json`. It also identifies the unresolved lockfile and manual-edit cases. `files.set` in that example is a proposed key-level operation, just as `append` above is a proposed line operation.

The [Python with mise and uv example](examples/python-with-mise-and-uv.md) uses `../medprev-rest-api` to separate the interpreter version, `.venv` creation, and dependency installation. [mise](ingredients/mise.yaml) owns `mise.toml`'s existence so Node and Python can both contribute keys without claiming the same file. Python's `venv: mise` option contributes its own virtualenv setting; there is no family of `mise-node`, `mise-python`, and `mise-go` ingredients.

The [AWS credential migration example](examples/aws-credential-migration.md) tests a harder case: applying a newly learned practice to older repositories with different workflows, mise tasks, and caches. Its [CI ingredient](ingredients/aws-ci-credentials.yaml) and [local session ingredient](ingredients/aws-session.yaml) represent continuing setup; its [recipe](recipes/migrate-aws-credentials.yaml) represents the one-time transition. All three are concept cards. The existing `files.set` sketch cannot yet adopt and remove unmanaged YAML list entries or inline shell checks safely.

The [composition stress test](examples/composability-stress-test.md) challenges the model with [ten station candidates](stations/README.md) and [ten new recipe candidates](recipes/README.md). It includes [Beads, ralph-tui, `prd.json`, Codex and Claude](examples/beads-ralph-prd-codex-claude.md), plus [Go, Docker, Compose and Terraform](examples/go-docker-compose-terraform.md) and a [Compose-hosted local stack](examples/local-stack-compose-terraform.md). These cards expose unresolved collisions and dependencies; they are not a claim that the pieces already compose.

`requires` in these sketches selects prerequisite ingredients transitively. Thus `node@22` or `python@3.13` pulls in `mise` without adding it to the command, while `mise` remains the only ingredient with `owns: true` for `mise.toml`.

The YAML files hold a few example patterns each; a real cookbook would need reviewed, complete entries and a defined update policy. A station can still package several choices for reuse, and a recipe can run a named session, but neither is required for this flow.

This design also changes the draft definition of an ingredient from “one program” to “one selectable source of contributions”: an editor, operating system, runtime, package manager, or repository choice can each be one. That change needs to be resolved in the architecture before implementation.
