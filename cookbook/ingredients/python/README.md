# python

Models a Python project: `pyproject.toml` or `requirements.txt`, the virtual environment and the installer. One
instance per project directory.

## Model

- `version` is the interpreter version for mise.
- `installer` is `uv` or `pip`; `venv` is the environment directory.
- `dependency(requirement, group)` adds one requirement to `project.dependencies`, or to an optional group. A
  package already present with another specification is rejected; existing entries are kept.
- `requirement(line)` adds a line for a `requirements.txt` project.
- `dropVersionFile()` removes `.python-version` once mise owns the version.
- `tools()`, `env()`, `ephemeralFiles()`, `localFiles()` and `installCommand(target)` expose facts for mise and Git.

## Writes

| Target | Part |
| --- | --- |
| `pyproject.toml` | `project.dependencies`, and each touched `project.optional-dependencies.<group>` |
| `requirements.txt` | The section `dependencies` |
| `.python-version` | Deleted when `dropVersionFile()` was called |
