# add-local-aws-resource

Writes a Terraform file and declares a `localstack:resource` mise task that plans it against a local AWS endpoint.

## Usage

```groovy
book.recipe('add-local-aws-resource', kitchen, [
    path: 'infra/local/main.tf',
    terraformText: 'resource "aws_s3_bucket" "local" { bucket = "local-example" }\n',
    endpoint: 'http://localhost:4566'
]).mount()
```

## Arguments

| Argument | Required | Default | Meaning |
| --- | --- | --- | --- |
| `path` | yes | none | Repository-relative path of the Terraform file. |
| `terraformText` | yes | none | Content of the file, written as given. |
| `endpoint` | yes | none | `http://` URL with host `localhost`, `127.0.0.1` or `localstack` and an optional numeric port. Anything else, including `https://`, is rejected. |
| `id` | no | `add-local-aws-resource` | Component ID of the recipe. |
| `mise` | no | a new `mise` ingredient | Existing mise component that receives the task. |
| `adopt` | no | `false` | Boolean `true` lets the recipe adopt an existing unmanaged file at `path`; string `'true'` does not. The value is also passed to the mise component the recipe creates, where boolean `true` or string `'true'` lets it adopt an existing unmanaged `localstack:resource` task. |

## Output

| Target | Format | Part the recipe owns |
| --- | --- | --- |
| `path` | Terraform | The whole file. |
| `mise.toml`, or the file of the supplied `mise` component | TOML | The `tasks."localstack:resource"` key, which runs `AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_ENDPOINT_URL='<endpoint>' terraform -chdir='<directory>' plan`. |

`<directory>` is the parent directory of `path`, or `.` when `path` has none.

## Inputs

Running the task needs mise, Terraform, a Terraform directory that is already initialized, and a LocalStack-compatible
service listening at `endpoint`. The task runs only `terraform plan`; the recipe adds no provider or backend
configuration beyond `terraformText`.

## Behaviour

The recipe writes the file and the task declaration only; it does not run the task or create AWS resources.
