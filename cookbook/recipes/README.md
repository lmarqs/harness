# Cookbook recipe factories

These `recipe.groovy` files return factories for `Cookbook.recipe(name, kitchen, options)`. Their maps are Groovy values, including nested maps and lists. They are used through the composition API; the CLI's repository entry points return a `Batch` instead.

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

- Dependencies: [Node](add-node-dependency/README.md), [Python](add-python-dependency/README.md), [Go](add-go-dependency/README.md).
- Environment: [development](development/README.md), [Compose service](add-compose-service/README.md), [local AWS resource](add-local-aws-resource/README.md).
- Migrations: [AWS credentials](migrate-aws-credentials/README.md), [version source](migrate-version-source/README.md), [CI cache](refresh-ci-cache/README.md), [Docker base](update-docker-base/README.md).
- Agent work: [agentic](agentic/README.md), [Beads issue](implement-bead/README.md), [PRD story](implement-prd-story/README.md), [grill task](grill/README.md).

`operations.groovy` is a shared helper map for required values, shell quoting, task composition, whole-file outputs, and guarded replacements. It is not a recipe factory.

Task recipes declare commands in generated mise tasks. Applying a kitchen plan writes those declarations; it does not execute the declared commands.
