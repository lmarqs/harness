package dev.thecook.mise

/**
 * Models the tools, environment and tasks mise uses in a project.
 *
 * ## Writes for
 *
 * TOML keys under `tools`, `env` and `tasks` in `mise.toml`. Inline tasks use the task fields `run`, `dir`
 * and `env`; file tasks are executable scripts under `.mise/tasks/`.
 *
 * ## Instances
 *
 * One instance covers the mise configuration in one directory. Use `at` for a separate configuration and
 * file-task directory in a subproject. A command's `dir` is relative to the destination.
 *
 * ## Use
 *
 * ```groovy
 * import dev.thecook.mise.Mise
 * import dev.thecook.node.Node
 *
 * @ingredient(Mise) def mise
 * @ingredient(Node) def node
 *
 * node.version = '22.12.0'
 * mise.tools(node.tools())
 * mise.task('dependency:node', node.installCommand())
 * ```
 *
 * ## With other models
 *
 * Consumes tool versions, environment entries, commands and script content exposed by other models. The recipe
 * wires these facts into mise; models supply argument lists without quoting, and Mise quotes them for shell use.
 */
@Ingredient
class Mise {
  private Map<String, String> tools = [:]
  private Map env = [:]
  private Map<String, Map> tasks = [:]        // inline tasks, written into mise.toml
  private Map<String, String> scripts = [:]   // file tasks, written under .mise/tasks

  /** Pins the named mise tool at the version string as supplied, replacing an earlier pin for that name. */
  void tool(String name, String version) { tools[name] = version }

  /** Adds mise tool pins, replacing earlier versions for the supplied tool names. */
  void tools(Map<String, String> more) { tools += more }

  /**
   * Declares an inline task from a command, `[run: [step...], dir: String, env: Map]`, as other models return it.
   * A step is an argv list, quoted here, or shell text used as written. `dir` is relative to the destination.
   * The task replaces an earlier task of the same name. Names are colon-separated groups of letters, digits,
   * underscores or hyphens; empty names and other characters are rejected.
   */
  void task(String name, Map command) {
    def steps = command.run.collect { step -> step instanceof List ? step.collect { quote(it) }.join(' ') : step }
    def task = [run: steps.size() == 1 ? steps[0] : steps]
    if (command.dir && command.dir != '.') task.dir = command.dir
    if (command.env) task.env = command.env
    tasks[valid(name)] = task
  }

  /**
   * Declares an inline task that runs shell text as written, replacing an earlier inline task of the same name.
   * Names follow the same colon-separated syntax as `task(name, command)`; invalid names are rejected.
   */
  void task(String name, String run) { task(name, [run: [run]]) }

  /**
   * Declares an executable file task with `body` as its whole content, replacing an earlier script of the same
   * name. Colons in the name become directory separators under `.mise/tasks/`. Names use the same syntax as
   * inline tasks; invalid names are rejected.
   */
  void script(String name, String body) { scripts[valid(name)] = body }

  /** Sets a mise environment entry to the supplied value, replacing an earlier value for that name. */
  void env(String name, Object value) { env[name] = value }

  /** Each `tools.<name>` key in `mise.toml`, owned whole, when tool versions are supplied. */
  @Keys(path = 'mise.toml', under = ['tools'])
  Map toolKeys() { tools ?: null }

  /** Each `tasks.<name>` key in `mise.toml`, owned whole with its task fields, when inline tasks are supplied. */
  @Keys(path = 'mise.toml', under = ['tasks'])
  Map taskKeys() { tasks ?: null }

  /** Each `env.<name>` key in `mise.toml`, owned whole, when environment entries are supplied. */
  @Keys(path = 'mise.toml', under = ['env'])
  Map envKeys() { env ?: null }

  /** One whole executable file under `.mise/tasks/` per supplied script, with colons in its name becoming slashes. */
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
