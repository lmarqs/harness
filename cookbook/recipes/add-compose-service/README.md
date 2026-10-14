# Add a Compose service

This guide describes the current implementation. The [architecture](../../../docs/architecture.md) defines the
intended design; the [factory guide](../README.md) explains current CLI limits, source prerequisites and ownership
reconciliation. Existing composition APIs and adoption gates do not settle the deferred model or hooks.

Declare a Compose service and a `stack:validate` mise task.

```groovy
book.recipe('add-compose-service', kitchen, [
    service: 'api', configuration: [image: 'example/api:1'],
    path: 'compose.yaml'
]).mount()
```

Required options are `service`, a nonempty `configuration` map, and `path`. Optional options are `id` (default `add-compose-service`), `mise` (an existing mise component), and `adopt` (passed to suppliers). The validation task runs `docker compose -f <path> config` when invoked separately. Its `configuration` must be a
Groovy map, so this example requires composition rather than CLI string options. The selected service key and
mise task must be absent or managed, or explicitly adopted using the existing supplier options. Cooking writes
configuration; running the task requires mise and Docker Compose and does not start the service.

See [factory usage](../README.md).
