# mise

Models the files mise reads: `mise.toml` and the task scripts under `.mise/tasks/`. Most recipes hand mise the
tools and commands other models know about.

## Model

- `tool(name, version)` and `tools(map)` pin tool versions.
- `task(name, run)` declares an inline task; `script(name, body)` declares a file task.
- `env(name, value)` sets an environment entry.
- `quote(value)` shell-quotes a value for a command line.

## Writes

| Target | Part |
| --- | --- |
| `mise.toml` | `tools.<name>`, `tasks.<name>` and `env.<name>`, each owned on its own |
| `.mise/tasks/<group>/<name>` | The whole script, executable, one per file task |
