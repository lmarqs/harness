# Cookbook recipe factories

Each `recipe.groovy` returns a factory `{ kitchen, book, options -> component }`. The same factory is executable through the CLI and composable through `book.recipe(name, kitchen, options)`.

```sh
./cook cookbook --policy=.agents/policy.yaml --dry-run
./cook the-cook --dry-run
./cook agentic/codex --instructions='Run tests before committing.' --dry-run
```

Names resolve to `cookbook/recipes/<name>/recipe.groovy`; when no local definition exists, the loader uses packaged cookbook resources. Explicit paths to those files work too. Local definitions take precedence. The CLI preserves repeated options for declared recipes. A `DeclaredRecipe` wraps a `RecipeDeclaration` and a factory. The declaration lists required single paths and required repeatable choices. Validation runs before the factory: path inputs reach it as strings and choice inputs as ordered lists with identical duplicates removed. Legacy closure factories continue to receive scalar string options; composition code can pass typed Groovy maps and lists. Running a recipe mounts its returned component and reconciles the complete desired batch, including its children.

A supplier can expose metadata through `book.declaration(name)` without mounting or rendering the recipe. Declarations use the typed `thecook.model.RecipeDeclaration` and `RecipeInput` contracts. A path such as `--policy=.agents/policy.yaml` is interpreted by the factory relative to `kitchen.repository.directory`, including when the recipe file comes from another directory. `--repo`, `--dry-run`, and `--remove` are reserved CLI options and cannot be declaration names. Existing version 1 policy syntax remains literal prefix based.

A repository's optional `cook.groovy` can return `book.recipe('cookbook', kitchen, [policy: '.agents/policy.yaml'] + preferences)`. Typed scripts returning `Batch` remain supported. There is no separate root `recipes/` convention.

`--remove` withdraws a recipe's selected applications using saved ownership. It can be combined with `--dry-run` and `--harness=claude` or `--harness=codex`. Removing `cookbook` or `init` also selects its known init children. The policy file is not read during removal; managed edits still reject the entire operation.

For example, in a Groovy program with the core and required cookbook artifacts on its classpath:

```groovy
import thecook.composition.Kitchen
import thecook.cookbook.Cookbook

def book = new Cookbook(new File('cookbook'))
def kitchen = new Kitchen(new File('/path/to/repository'))
book.recipe('grill', kitchen, [script: 'git status']).mount()
def plan = kitchen.plan()
println plan.changes
// Review the plan before calling plan.apply().
```

Each directory documents its option map and behavior:

- This repository: [agent policy and skills](cookbook/README.md), [local Cook state](the-cook/README.md).

- Dependencies: [Node](add-node-dependency/README.md), [Python](add-python-dependency/README.md), [Go](add-go-dependency/README.md).
- Environment: [development](development/README.md), [Compose service](add-compose-service/README.md), [local AWS resource](add-local-aws-resource/README.md).
- Migrations: [AWS credentials](migrate-aws-credentials/README.md), [version source](migrate-version-source/README.md), [CI cache](refresh-ci-cache/README.md), [Docker base](update-docker-base/README.md).
- Agent work: [agentic](agentic/README.md), [Beads issue](implement-bead/README.md), [PRD story](implement-prd-story/README.md), [grill task](grill/README.md).

`operations.groovy` is a shared helper map for required values, shell quoting, task composition, whole-file outputs, and guarded replacements. It is not a recipe factory.

Task recipes declare commands in generated mise tasks. Applying a kitchen plan writes those declarations; it does not execute the declared commands.
