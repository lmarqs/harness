# Add a Compose service

Declare a Compose service and a `stack:validate` mise task.

```groovy
book.recipe('add-compose-service', kitchen, [
    service: 'api', configuration: [image: 'example/api:1'],
    path: 'compose.yaml'
]).mount()
```

Required options are `service`, a nonempty `configuration` map, and `path`. Optional options are `id` (default `add-compose-service`), `mise` (an existing mise component), and `adopt` (passed to suppliers). The validation task runs `docker compose -f <path> config` when invoked.

See [factory usage](../README.md).
