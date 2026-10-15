@Ingredient
class Mise {
  Map<String, String> tools = [:]
  Map env = [:]
  Map<String, Map> tasks = [:]        // inline tasks, written into mise.toml
  Map<String, String> scripts = [:]   // file tasks, written under .mise/tasks

  void tool(String name, String version) { tools[name] = version }

  void tools(Map<String, String> more) { tools += more }

  /**
   * Declares an inline task from a command, `[run: [step...], dir: String, env: Map]`, as other models return it.
   * A step is an argv list, quoted here, or shell text used as written. `dir` is relative to the destination.
   */
  void task(String name, Map command) {
    def steps = command.run.collect { step -> step instanceof List ? step.collect { quote(it) }.join(' ') : step }
    def task = [run: steps.size() == 1 ? steps[0] : steps]
    if (command.dir && command.dir != '.') task.dir = command.dir
    if (command.env) task.env = command.env
    tasks[valid(name)] = task
  }

  /** Declares an inline task that runs shell text as written. */
  void task(String name, String run) { task(name, [run: [run]]) }

  void script(String name, String body) { scripts[valid(name)] = body }

  void env(String name, Object value) { env[name] = value }

  @Keys(path = 'mise.toml', under = ['tools'])
  Map toolKeys() { tools ?: null }

  @Keys(path = 'mise.toml', under = ['tasks'])
  Map taskKeys() { tasks ?: null }

  @Keys(path = 'mise.toml', under = ['env'])
  Map envKeys() { env ?: null }

  @Files(path = '.mise/tasks', mode = '0755')
  Map<String, String> taskScripts() { scripts.collectEntries { name, body -> [name.replace(':', '/'), body] } ?: null }

  private static String quote(Object value) {
    def text = value.toString()
    text ==~ /[A-Za-z0-9_.\/:=@%+,-]+/ ? text : "'" + text.replace("'", "'\"'\"'") + "'"
  }

  private static String valid(String name) {
    if (!(name ==~ /[a-zA-Z0-9_-]+(:[a-zA-Z0-9_-]+)*/)) throw new IllegalArgumentException("Invalid mise task name: ${name}")
    name
  }
}
