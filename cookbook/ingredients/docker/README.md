# docker

Two models in one artifact.

`Docker` models a Dockerfile and `.dockerignore`:

- `dockerfile` names the file, `Dockerfile` by default.
- `baseImage(stage, image, expected)` replaces the base image of one named stage. The stage must exist exactly once
  and currently use `expected`.
- `ignore(patterns)` declares the ignore file.
- `buildCommand(stage)` exposes a verification command for mise, run in the instance's directory.

| Target | Part |
| --- | --- |
| the Dockerfile | The stage's `FROM` line |
| `.dockerignore` | The whole file |

`Compose` models a Compose file:

- `file` names it, `compose.yaml` by default.
- `service(name, definition)` adds or replaces one service.
- `validateCommand()` exposes a check command for mise, run in the instance's directory.

| Target | Part |
| --- | --- |
| the Compose file | `services.<name>`, each owned whole |
