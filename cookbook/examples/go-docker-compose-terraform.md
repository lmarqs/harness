# Go, Docker, Compose and Terraform in one repository

This is a **composition test**, not a starter template. It connects the [Go service](../stations/go-service.yaml), [Docker image](../stations/docker-image.yaml), [Compose development stack](../stations/compose-dev.yaml), and [LocalStack with Terraform](../stations/local-aws-stack.yaml) station candidates.

```text
go@<version> in mise.toml
      |                        Dockerfile builder uses a compatible Go version
      +--> go.mod             CI setup/cache reads the chosen version source
Dockerfile --> image
Compose --> local services and a per-worktree endpoint
Terraform --> resources at that exact endpoint, after Compose is ready
```

An initial setup can offer a Dockerfile and local service definitions, but the project supplies its module path, image stages, service names, ports, Terraform resources, and checks. `docker-image` and `compose-dev` reuse the same Docker selection. `local-aws-stack` must extend the Compose services rather than replace the file offered by `compose-dev`. If two stations each own the whole file, the combination fails before any command runs.

Changing `go@<version>` is a sharper test than initial setup. If Cook updates `mise.toml` but leaves `go.mod`, the Docker builder, or CI on an incompatible version, the cards did not compose. Likewise, changing the Compose port without updating the Terraform endpoint breaks the [local stack](local-stack-compose-terraform.md). A useful Cook diff would show all related edits and name any repo-specific part it cannot safely rewrite.

Terraform state and Compose runtime data remain local. Project-owned `*.tf`, Dockerfile and Compose services should be reviewed on adoption and on later cookbook migrations; a station being applied once does not make those files automatically updatable.
