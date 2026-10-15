# ralph

Models the files Ralph TUI reads and leaves behind: `.ralph-tui/config.toml`, its runner and the PRD it tracks.

## Model

- `configure(settings)` sets `agent`, `tracker`, `prd`, `workers`, `maxIterations`, `autoCommit` and `headless`, and
  turns the configuration on. Any other setting is rejected.
- `installRunner()` writes the runner at `.ralph-tui/run`.
- `focus(source, story, selected)` writes a copy of the JSON PRD at `source` reduced to one user story, at
  `selected`, and points `prd` at it. The source must hold exactly one story with that id.
- `tools`, `localFiles` and `ephemeralFiles` expose facts for mise and Git.
- `runScript()` is the runner, for a recipe that hands it to a task runner. It ships inside this artifact.

## Writes

| Target | Part |
| --- | --- |
| `.ralph-tui/config.toml` | Each top-level key, owned whole |
| `.ralph-tui/run` | The whole executable runner, after `installRunner()` |
| the PRD path | The whole file, after `focus` |
