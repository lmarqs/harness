# Add a local AWS resource

This guide describes the current implementation. The [architecture](../../../docs/architecture.md) defines the
intended design; the [factory guide](../README.md) explains current CLI limits, source prerequisites and ownership
reconciliation. Existing composition APIs and adoption gates do not settle the deferred model or hooks.

Declare a whole Terraform file and a `localstack:resource` task.

```groovy
book.recipe('add-local-aws-resource', kitchen, [
    path: 'infra/local/main.tf',
    terraformText: 'resource "aws_s3_bucket" "local" { bucket = "local-example" }\n',
    endpoint: 'http://localhost:4566'
]).mount()
```

Required options are `path`, `terraformText`, and `endpoint`. The endpoint must use HTTP with host `localhost`, `127.0.0.1`, or `localstack`, with an optional numeric port. Optional options are `id` (default `add-local-aws-resource`), `mise`, and `adopt`; adopting an existing file requires the boolean `true`.

The task sets test AWS credentials and `AWS_ENDPOINT_URL`, then runs Terraform plan in the file's directory. Provide Terraform provider configuration appropriate to the local service and initialize that Terraform directory
before running the task separately. It requires mise, Terraform and a reachable compatible local AWS service.
Cooking writes the file and task, not a resource. Existing whole-file adoption requires typed Groovy composition;
CLI `--adopt=true` does not supply boolean `true`. The mise task also has its own unmanaged-key and drift checks.

See [factory usage](../README.md).
