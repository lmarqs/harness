# go

Models a Go module: `go.mod` and the toolchain version. One instance per module directory.

## Model

- `version` is the toolchain version for mise.
- `require(module, version)` adds a requirement. A module already required at another version is rejected.
- `tools()` and `tidyCommand()` expose facts for mise; the command runs in the module directory.

## Writes

| Target | Part |
| --- | --- |
| `go.mod` | One `require` line per requirement, kept as written when already present at that version; the file must already exist |
