# github

Models a GitHub Actions workflow file. One instance per workflow; the recipe names the file.

## Model

- `file` is the workflow path under `.github/workflows/`.
- `job(name)` selects a job:
  - `permissions(map)` sets permission grants.
  - `insertStep(step, placement)` inserts a step at `start`, `end`, `before:<id>` or `after:<id>`. A duplicate step
    id is rejected.
  - `stepWith(stepId, key, value)` changes one `with` entry of an existing step.

## Writes

| Target | Part |
| --- | --- |
| the workflow file | `jobs.<job>.permissions.<grant>` each on its own; `jobs.<job>.steps` owned whole once touched |
