# Update a Docker base image

This guide describes the current implementation. The [architecture](../../../docs/architecture.md) defines the
intended design; the [factory guide](../README.md) explains current CLI limits, source prerequisites and ownership
reconciliation. Existing composition APIs and adoption gates do not settle the deferred model or hooks.

Replace the image in one named Dockerfile stage and declare a `docker:verify` mise task.

```groovy
book.recipe('update-docker-base', kitchen, [
    adopt: true, path: 'Dockerfile', stage: 'build',
    expectedImage: 'node:20', image: 'node:22'
]).mount()
```

The Dockerfile must already exist in the destination. Use initialized Groovy composition for this example;
CLI `--adopt=true` remains a string and cannot satisfy this factory's boolean check.

Required options are the boolean `adopt: true`, `path`, `stage`, `expectedImage`, and `image`. The file must exist and contain exactly one matching `FROM ... AS <stage>` line, with either the expected or desired image. The new image must be a literal image reference. Optional `id` defaults to `update-docker-base`; `mise` can supply an existing component.

The recipe manages the whole Dockerfile and preserves the selected line's platform flag and stage suffix. The task runs `docker build -f <path> --target <stage> .` when invoked separately. It requires mise, Docker and a
usable build context in the task's working directory, plus access to the image or its local cache. Cooking does
not pull an image or build it. Managed edits can still fail reconciliation after the explicit stage/image guard.

See [factory usage](../README.md).
