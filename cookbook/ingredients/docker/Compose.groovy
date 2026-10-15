@Ingredient
class Compose {
  String file = 'compose.yaml'
  Map<String, Map> services = [:]

  void service(String name, Map definition) {
    if (!definition) throw new IllegalArgumentException("Service ${name} needs a definition")
    services[name] = definition
  }

  Map validateCommand() { [run: [['docker', 'compose', '-f', file, 'config']], dir: directory] }

  @Keys(path = '${file}', under = ['services'])
  Map serviceKeys() { services ?: null }
}
