@Ingredient
class Compose {
  String file = 'compose.yaml'
  Map<String, Map> services = [:]

  void service(String name, Map definition) {
    if (!definition) throw new IllegalArgumentException("Service ${name} needs a definition")
    services[name] = definition
  }

  String validateCommand() { "docker compose -f ${Mise.quote(file)} config" }

  @Keys(path = '${file}', under = ['services'])
  Map serviceKeys() { services ?: null }
}
