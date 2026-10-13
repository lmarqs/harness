# Update a Docker base image

Replace the image in one named Dockerfile stage and declare a `docker:verify` mise task.

```groovy
book.recipe('update-docker-base', kitchen, [
    adopt: true, path: 'Dockerfile', stage: 'build',
    expectedImage: 'node:20', image: 'node:22'
]).mount()
```

Required options are the boolean `adopt: true`, `path`, `stage`, `expectedImage`, and `image`. The file must exist and contain exactly one matching `FROM ... AS <stage>` line, with either the expected or desired image. The new image must be a literal image reference. Optional `id` defaults to `update-docker-base`; `mise` can supply an existing component.

The recipe manages the whole Dockerfile and preserves the selected line's platform flag and stage suffix. The task runs `docker build -f <path> --target <stage> .` when invoked.

See [factory usage](../README.md).
