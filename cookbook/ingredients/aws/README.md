# aws

Knowledge about AWS that other models consume. This model writes nothing.

## Model

- `credentialsStep(region, role)` is the GitHub Actions step that assumes an IAM role through OIDC.
- `oidcPermissions()` is the job permission set that step needs.
- `localEnvironment(endpoint)` is the environment for a LocalStack endpoint; a non-local endpoint is rejected.
- `sessionCheckScript()` is a mise task script that prints the current identity. It ships inside this artifact.
