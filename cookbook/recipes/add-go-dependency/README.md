# add-go-dependency

Adds one `require` line to an existing `go.mod` and declares a mise task that tidies and tests the module.

```sh
./cook dev.thecook:add-go-dependency --module=github.com/google/uuid --version=v1.6.0
```

| Target | Part |
| --- | --- |
| `<dir>/go.mod` | The whole file |
| `mise.toml` | `tasks."dependency:go"` |
