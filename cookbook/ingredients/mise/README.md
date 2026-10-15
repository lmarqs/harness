# mise

Models the files mise reads: `mise.toml` and the task scripts under `.mise/tasks/`. Most recipes hand mise the
tools and commands other models know about.

## Model

- `tool(name, version)` and `tools(map)` pin tool versions.
- `task(name, command)` declares an inline task from a command another model returns: `run`, then `dir` and `env`
  as the task's own fields. Argument lists are quoted here, so no other model quotes.
- `task(name, run)` declares an inline task from shell text; `script(name, body)` declares a file task.
- `env(name, value)` sets an environment entry.

## Writes

| Target | Part |
| --- | --- |
| `mise.toml` | `tools.<name>`, `tasks.<name>` and `env.<name>`, each owned on its own |
| `.mise/tasks/<group>/<name>` | The whole script, executable, one per file task |
