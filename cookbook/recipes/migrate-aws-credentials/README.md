# Migrate AWS CI credentials

Replace selected workflow job steps with AWS role credentials and permissions.

```groovy
book.recipe('migrate-aws-credentials', kitchen, [
    adopt: true,
    jobs: [[workflow: '.github/workflows/build.yaml', job: 'build',
        expectedSteps: oldSteps, steps: replacementSteps,
        region: 'us-east-1', role: 'arn:aws:iam::123456789012:role/build',
        placement: 'start']]
]).mount()
```

`adopt: true` and a nonempty `jobs` list are required. Each job requires `workflow`, `job`, `expectedSteps`, `steps`, `region`, `role`, and `placement`. `expectedSteps` must equal the current YAML job steps; `steps` is the complete list into which credentials are inserted. `placement` accepts `start`, `end`, `[before: stepId]`, or `[after: stepId]`; selectors must match exactly one step. Optional `action` overrides the supplier's AWS action reference.

Optional `id` defaults to `migrate-aws-credentials`. Optional `files` uses guarded whole-file replacements with `[path, expected, content]` strings. A replacement cannot own the same workflow as a credential job.

The supplier manages the job's steps, `contents: read`, and `id-token: write` permissions. See [factory usage](../README.md).
