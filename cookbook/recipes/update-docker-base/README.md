# update-docker-base

Replaces the base image of one named stage in a Dockerfile and declares a mise task that builds that stage. The
stage must exist exactly once and currently use the expected image.

```sh
./cook dev.thecook:update-docker-base --stage=build --expected=eclipse-temurin:17 --image=eclipse-temurin:21
```

| Target | Part |
| --- | --- |
| the Dockerfile | The whole file, with the stage's image replaced |
| `mise.toml` | `tasks."docker:verify"` |
