# add-node-dependency

Records one dependency in a package manifest and declares a mise task that installs it.

```sh
./cook dev.thecook:add-node-dependency --package=zod --version=^3.23.0 --dir=packages/api
```

| Target | Part |
| --- | --- |
| `<dir>/package.json` | `<kind>.<package>` |
| `mise.toml` | `tasks."dependency:node"` |
