# Migrate AWS CI credentials

This guide describes the current implementation. The [architecture](../../../docs/architecture.md) defines the
intended design; the [factory guide](../README.md) explains current CLI limits, source prerequisites and ownership
reconciliation. Existing composition APIs and adoption gates do not settle the deferred model or hooks.

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

Define `oldSteps` as the exact current YAML steps list and `replacementSteps` as the complete desired list before
this example; they are caller-supplied Groovy lists, not repository variables. The workflow must already exist.
Boolean `adopt: true` and a nonempty typed `jobs` list are required through Groovy composition; CLI strings do not
satisfy these checks. Each job requires `workflow`, `job`, `expectedSteps`, `steps`, `region`, `role`, and `placement`. `expectedSteps` must equal the current YAML job steps; `steps` is the complete list into which credentials are inserted. `placement` accepts `start`, `end`, `[before: stepId]`, or `[after: stepId]`; selectors must match exactly one step. The replacement list must not already contain an `awsCredentials` step. Optional `action` overrides the supplier's
AWS action reference.

Optional `id` defaults to `migrate-aws-credentials`. Optional `files` uses guarded whole-file replacements with `[path, expected, content]` strings. A replacement cannot own the same workflow as a credential job.

The supplier manages the job's steps, `contents: read`, and `id-token: write` permissions. Cooking does not run CI
or validate AWS trust. The eventual workflow needs an AWS role/trust setup appropriate for its runner. Current
ownership and managed-edit checks still apply; repeating a migration also requires `expectedSteps` to match the
then-current workflow, so the initial map is not a general idempotence guarantee. See [factory usage](../README.md).
