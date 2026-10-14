# beads

Models the files the Beads tracker reads and leaves behind.

## Model

- `track(issuePrefix)` turns the tracker on for this repository.
- `localFiles` and `ephemeralFiles` name what Beads writes locally, for an ignore consumer.
- `readyScript()` is a mise task script that lists ready tasks. It ships inside this artifact.

## Writes

| Target | Part |
| --- | --- |
| `.beads/config.yaml` | `json` and, when given, `issue-prefix` |
