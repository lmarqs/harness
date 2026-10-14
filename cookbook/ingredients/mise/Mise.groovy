@Ingredient
class Mise {
  Map<String, String> tools = [:]
  Map env = [:]
  Map<String, Map> tasks = [:]        // inline tasks, written into mise.toml
  Map<String, String> scripts = [:]   // file tasks, written under .mise/tasks

  void tool(String name, String version) { tools[name] = version }

  void tools(Map<String, String> more) { tools += more }

  void task(String name, String run) { tasks[valid(name)] = [run: run] }

  void script(String name, String body) { scripts[valid(name)] = body }

  void env(String name, Object value) { env[name] = value }

  static String quote(Object value) { "'" + value.toString().replace("'", "'\"'\"'") + "'" }

  @Keys(path = 'mise.toml', under = ['tools'])
  Map toolKeys() { tools ?: null }

  @Keys(path = 'mise.toml', under = ['tasks'])
  Map taskKeys() { tasks ?: null }

  @Keys(path = 'mise.toml', under = ['env'])
  Map envKeys() { env ?: null }

  @Files(path = '.mise/tasks', mode = '0755')
  Map<String, String> taskScripts() { scripts.collectEntries { name, body -> [name.replace(':', '/'), body] } ?: null }

  private static String valid(String name) {
    if (!(name ==~ /[a-zA-Z0-9_-]+(:[a-zA-Z0-9_-]+)*/)) throw new IllegalArgumentException("Invalid mise task name: ${name}")
    name
  }
}
