package dev.thecook.docker

/**
 * Models services in a Docker Compose file.
 *
 * ## Writes for
 *
 * The Compose YAML format's `services` mapping. Each requested service key is owned whole; other services
 * and top-level keys remain outside that declaration.
 *
 * ## Instances
 *
 * One instance covers one selected Compose file, `compose.yaml` by default, in one directory. Use `at` for
 * a Compose project in another directory. The validation command runs in the instance's directory.
 *
 * ## Use
 *
 * ```groovy
 * import dev.thecook.docker.Compose
 * import dev.thecook.mise.Mise
 *
 * @ingredient(Compose) def compose
 * @ingredient(Mise) def mise
 *
 * compose.service('redis', [image: 'redis:7'])
 * mise.task('stack:validate', compose.validateCommand())
 * ```
 *
 * ## With other models
 *
 * `validateCommand()` supplies the Compose configuration check for Mise. The recipe passes the command
 * to the task runner; Compose owns only the requested service definitions.
 */
@Ingredient
class Compose {
  /** The Compose file path inside the instance's directory; `compose.yaml` by default. */
  String file = 'compose.yaml'
  private Map<String, Map> services = [:]

  /** Adds or replaces a service's whole definition map; an empty or null definition is rejected. */
  void service(String name, Map definition) {
    if (!definition) throw new IllegalArgumentException("Service ${name} needs a definition")
    services[name] = definition
  }

  /** Supplies a command to check the selected Compose file with `docker compose config` in the instance's directory. */
  Map validateCommand() { [run: [['docker', 'compose', '-f', file, 'config']], dir: directory] }

  /** Each `services.<name>` key in the selected Compose file, owned whole, when service definitions are supplied. */
  @Keys(path = '${file}', under = ['services'])
  Map serviceKeys() { services ?: null }
}
