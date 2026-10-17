@help('''
Adds or replaces one service in a Compose file and declares a mise task that validates the file.

## Before cooking

The input YAML file must exist in the destination and hold one non-empty service definition map, without
a surrounding `services` key. The recipe reads that input as it is before the invocation. mise and Docker
with Compose must be available to run the validation task.

## Cook

```sh
./cook dev.thecook:docker:0.1.0:compose-service --service=redis --from=setup/redis.yaml
./cook dev.thecook:docker:0.1.0:compose-service --service=redis --from=setup/redis.yaml --file=compose.dev.yaml
```

## Writes

| Target | Part | When |
| --- | --- | --- |
| The selected Compose file | `services.<service>`, owned whole | Always |
| `mise.toml` | `tasks."stack:validate"`, owned whole, checking the selected Compose file | Always |

The task runs `docker compose config`; it does not start the services.

## With other recipes

Distinct service keys compose in the same file; the last recipe writing a service key supplies its whole
definition. Each application declares `tasks."stack:validate"`, so the last one in an invocation selects
the Compose file that task checks. `dev.thecook:docker:0.1.0:base-image` declares a separate verification task.
''')
@yield('''
Run `mise run stack:validate` to check the Compose configuration.
''')
import dev.thecook.docker.Compose
import dev.thecook.mise.Mise

@param(value = ['service'], doc = 'Service name') String service
@param(value = ['from'], doc = 'YAML file holding the service definition') File from
@param(value = ['file'], default = 'compose.yaml', doc = 'The Compose file') String file

@ingredient(Compose) def compose
@ingredient(Mise) def mise

compose.file = file
compose.service(service, new YamlSlurper().parse(from))
mise.task('stack:validate', compose.validateCommand())
