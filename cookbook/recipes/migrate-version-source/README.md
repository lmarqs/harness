# migrate-version-source

Moves a runtime version pin into `mise.toml` and deletes the runtime's own version file.

```sh
./cook dev.thecook:migrate-version-source --runtime=node --version=22.12.0
```

| Target | Part |
| --- | --- |
| `mise.toml` | `tools.<runtime>` |
| `.nvmrc` or `.python-version` | Deleted |
