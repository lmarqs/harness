# Add a local AWS resource

Declare a whole Terraform file and a `localstack:resource` task.

```groovy
book.recipe('add-local-aws-resource', kitchen, [
    path: 'infra/local/main.tf',
    terraformText: 'resource "aws_s3_bucket" "local" { bucket = "local-example" }\n',
    endpoint: 'http://localhost:4566'
]).mount()
```

Required options are `path`, `terraformText`, and `endpoint`. The endpoint must use HTTP with host `localhost`, `127.0.0.1`, or `localstack`, with an optional numeric port. Optional options are `id` (default `add-local-aws-resource`), `mise`, and `adopt`; adopting an existing file requires the boolean `true`.

The task sets test AWS credentials and `AWS_ENDPOINT_URL`, then runs Terraform plan in the file's directory. Provide Terraform provider configuration appropriate to the local service.

See [factory usage](../README.md).
