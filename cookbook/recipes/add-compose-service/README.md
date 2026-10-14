# add-compose-service

Adds one service to a Compose file and declares a mise task that validates the file.

```sh
./cook dev.thecook:add-compose-service --service=redis --from=setup/redis.yaml
```

| Target | Part |
| --- | --- |
| the Compose file | `services.<service>` |
| `mise.toml` | `tasks."stack:validate"` |
