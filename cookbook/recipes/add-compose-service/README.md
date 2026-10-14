# add-compose-service

Adds one service to a Compose file and declares a `stack:validate` mise task that checks the file with Docker Compose.

## Usage

```groovy
book.recipe('add-compose-service', kitchen, [
    service: 'api', configuration: [image: 'example/api:1'],
    path: 'compose.yaml'
]).mount()
```

## Arguments

| Argument | Required | Default | Meaning |
| --- | --- | --- | --- |
| `service` | yes | none | Service name, used as the key under `services`. |
| `configuration` | yes | none | Service definition, as a nonempty map. Any other value is rejected. |
| `path` | yes | none | Repository-relative path of the Compose file. |
| `id` | no | `add-compose-service` | Component ID of the recipe. |
| `mise` | no | a new `mise` ingredient | Existing mise component that receives the task. |
| `adopt` | no | `false` | Boolean `true` or string `'true'` lets the recipe adopt an existing unmanaged service key and, when it creates the mise component, an existing unmanaged `stack:validate` task. |

## Output

| Target | Format | Part the recipe owns |
| --- | --- | --- |
| `path` | YAML | The `services.<service>` key, set to `configuration`. |
| `mise.toml`, or the file of the supplied `mise` component | TOML | The `tasks."stack:validate"` key: `{ run = "docker compose -f '<path>' config" }`. |

## Inputs

Running the task needs mise and Docker Compose (`docker compose`).

## Behaviour

- Other services and top-level keys of the Compose file are left untouched.
- The recipe writes the service and the task declaration only; it does not run the task or start the service.
