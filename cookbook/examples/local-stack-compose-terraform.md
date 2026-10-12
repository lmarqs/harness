# Local stack: Docker Compose plus Terraform

This is a **conceptual example**, based on the separate Compose and LocalStack Terraform files in `../medprev-analytics-etl` and `../medprev-rest-api`. It is not a runnable stack or a supported Cook schema.

A local-stack station cannot merely select `docker-compose` and `terraform`. They must agree on a service endpoint and the order of operations:

```text
docker-compose.yaml  --publishes-->  STACK_LOCALSTACK_PORT
mise task: stack:up --waits for--> LocalStack health
mise task: localstack:provision --passes endpoint to--> Terraform provider
Terraform state --stays in--> clone-local state path
mise task: stack:down --stops--> the same Compose project
```

An abbreviated pair of native files makes the shared value visible. These lines are adapted from `../medprev-analytics-etl`; the image pin and resource set are that repository's choices:

```yaml
# docker-compose.yaml
services:
  localstack:
    profiles: [glue-deps]
    image: localstack/localstack:4.14.0
    environment:
      SERVICES: s3,secretsmanager
    healthcheck:
      test: ["CMD", "curl", "-sf", "http://localhost:4566/_localstack/health"]
    ports:
      - 127.0.0.1:${STACK_LOCALSTACK_PORT}:4566
```

```hcl
// localstack/terraform/provider.tf
variable "localstack_endpoint" { type = string }
provider "aws" {
  region                      = "us-east-1"
  access_key                  = "test"
  secret_key                  = "test"
  skip_credentials_validation = true
  skip_metadata_api_check     = true
  skip_requesting_account_id  = true
  endpoints { s3 = var.localstack_endpoint }
}
```

The task that starts Compose must pass `TF_VAR_localstack_endpoint="http://127.0.0.1:${STACK_LOCALSTACK_PORT}"` to Terraform. The port is assigned per worktree. Neither file alone expresses that connection to Cook today.

The station could propose these initial files:

```text
docker-compose.yaml                 team-owned service and profile definitions
localstack/terraform/provider.tf    team-owned provider endpoint expression
localstack/terraform/*.tf           resources this project actually needs
.mise/tasks/stack/up                start/wait command
.mise/tasks/localstack/provision    init/apply command using that endpoint
.gitignore and .dockerignore       local state excluded by the relevant owners
```

`medprev-analytics-etl` uses a port variable and a mise task that starts the stack before applying Terraform. `medprev-rest-api` has a separate Compose stack and LocalStack setup task. Their resource definitions and stack profiles differ; a shared station cannot safely paste one project's Terraform into the other.

The composability test is a *change*: move the local port from one value to another. If Cook edits only Compose and leaves the Terraform endpoint or task environment behind, composition failed. If a second worktree uses the same fixed port or Compose project name, isolation failed. If `stack:down` deletes shared state from another worktree, the station is unsafe. The intended resolution is explicit per-worktree values and a named endpoint output consumed by the Terraform step; that connection is absent from today's `files` sketch.

Terraform provider and resource files, Compose services, and the project's task scripts become repo-owned after setup. A later cookbook improvement needs a reviewed migration of those files. It cannot be delivered by silently reapplying the original station.
