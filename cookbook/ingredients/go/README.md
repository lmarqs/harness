# go

Models a Go module: `go.mod` and the toolchain version. One instance per module directory.

## Model

- `version` is the toolchain version for mise.
- `require(module, version)` adds a requirement. A module already required at another version is rejected.
- `tools()` and `tidyCommand()` expose facts for mise.

## Writes

| Target | Part |
| --- | --- |
| `go.mod` | The whole file, with the added `require` lines; the file must already exist |
