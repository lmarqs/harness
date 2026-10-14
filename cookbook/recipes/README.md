# Cookbook recipes

Each directory holds one recipe: its `recipe.groovy` entry point (or a flat `<name>.groovy` beside the directory) and a
`README.md` that explains that recipe alone, written from the [recipe README template](TEMPLATE.md).

## Recipes

- This repository: [agent policy and skills](cookbook/README.md), [ignore local Cook state](the-cook/README.md).
- Agents: [agentic recipes](agentic/README.md), [Beads issue](implement-bead/README.md),
  [PRD story](implement-prd-story/README.md), [grill task](grill/README.md).
- Dependencies: [Node](add-node-dependency/README.md), [Python](add-python-dependency/README.md),
  [Go](add-go-dependency/README.md).
- Environment: [development](development/README.md), [Compose service](add-compose-service/README.md),
  [local AWS resource](add-local-aws-resource/README.md).
- Migrations: [AWS credentials](migrate-aws-credentials/README.md), [version source](migrate-version-source/README.md),
  [CI cache](refresh-ci-cache/README.md), [Docker base](update-docker-base/README.md).

## Shared helpers

`operations.groovy` is a helper map for required values, shell quoting, task composition, whole-file outputs and
guarded replacements. It is not a recipe.
