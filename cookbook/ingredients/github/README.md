# github

Models a GitHub Actions workflow file. One instance per workflow; the recipe names the file.

## Model

- `file` is the workflow path under `.github/workflows/`.
- `job(name)` selects a job:
  - `permissions(map)` sets permission grants.
  - `step(step, placement)` declares one step by its `id`. An absent step is inserted at `start`, `end`,
    `before:<id>` or `after:<id>`; a present one is replaced, so applying again changes nothing.
  - `stepWith(stepId, key, value)` changes one `with` entry of an existing step.

## Writes

| Target | Part |
| --- | --- |
| the workflow file | `jobs.<job>.permissions.<grant>`, `jobs.<job>.steps[id=<id>]` and `jobs.<job>.steps[id=<id>].with.<key>`, each owned on its own |
