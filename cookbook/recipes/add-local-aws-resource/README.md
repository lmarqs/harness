# add-local-aws-resource

Writes a Terraform file and declares a mise task that plans it against a LocalStack endpoint.

```sh
./cook dev.thecook:add-local-aws-resource --path=infra/local/queue.tf --from=setup/queue.tf
```

| Target | Part |
| --- | --- |
| `<path>` | The whole file |
| `mise.toml` | `tasks."localstack:resource"` |
