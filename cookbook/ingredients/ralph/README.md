# ralph

Models the files Ralph TUI reads and leaves behind: `.ralph-tui/config.toml`, its runner and the PRD it tracks.

## Model

- `configure(settings)` sets `agent`, `tracker`, `prd`, `workers`, `maxIterations`, `autoCommit`, `headless` and
  `mode`, and turns the model on.
- `prdContent` is the PRD text a recipe wants written at `prd`.
- `tools`, `localFiles` and `ephemeralFiles` expose facts for mise and Git.
- `runScript()` is the runner, for a mise task in `mise` mode. It ships inside this artifact.

## Writes

| Target | Part |
| --- | --- |
| `.ralph-tui/config.toml` | Each top-level key, owned whole |
| `.ralph-tui/run` | The whole executable runner, in `standalone` mode |
| the PRD path | The whole file, when `prdContent` was set |
