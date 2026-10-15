# terraform

Models Terraform configuration files and the toolchain version.

## Model

- `version` is the toolchain version for mise.
- `file(path, hcl)` declares one configuration file.
- `tools()` and `planCommand(path, env)` expose facts for mise; the plan runs in the directory that holds `path`.

## Writes

| Target | Part |
| --- | --- |
| each declared path | The whole file |
