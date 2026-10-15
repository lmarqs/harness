@Ingredient
class Codex implements Harness {
  final String name = 'codex'
  Map config = [:]
  Map hooks = [:]
  Map<String, String> rules = [:]
  List<String> commands = []
  List<String> instructions = []

  /** Translates the shared policy into Codex's sandbox settings and prefix rules. */
  void permit(Policy policy) {
    config += [sandbox_mode: 'workspace-write', approval_policy: 'on-request',
               sandbox_workspace_write: [network_access: false]]
    commands = policy.allow.collect { rule(it, 'allow') } +
      policy.ask.collect { rule(it, 'prompt') } +
      policy.deny.collect { rule(it, 'forbidden') }
  }

  void config(Map more) { config += more }

  void hooks(Map more) { hooks += more }

  void rule(String name, String body) {
    if (!(name ==~ /[A-Za-z0-9_.-]+/) || name in ['.', '..']) throw new IllegalArgumentException("Rule file needs a simple name: ${name}")
    rules[name] = body
  }

  void instruct(String... lines) { instructions += lines }

  /** Codex reads `AGENTS.md` in its own directory natively. */
  void readInstructions(String path) { readsNatively(path, 'AGENTS.md') }

  /** Codex discovers skills under `.agents/skills` in its own directory natively. */
  void readSkills(String path, List<String> names) { readsNatively(path, '.agents/skills') }

  @Keys(path = '.codex/config.toml')
  Map configKeys() { config ?: null }

  @Keys(path = '.codex/hooks.json')
  Map hookKeys() { hooks ?: null }

  @Files(path = '.codex/rules')
  Map<String, String> ruleFiles() { rules ?: null }

  @Section(path = '.codex/rules/cook.rules', id = 'commands', comment = '#')
  List<String> commandRules() { commands ?: null }

  @Section(path = 'AGENTS.md', id = 'codex')
  List<String> instructionsSection() { instructions ?: null }

  private static void readsNatively(String path, String expected) {
    if (path != expected) throw new IllegalArgumentException("Codex reads ${expected} in its own directory, not ${path}")
  }

  private static String rule(Command command, String decision) {
    def pattern = command.prefix.collect { "\"${it}\"" }.join(', ')
    "prefix_rule(pattern = [${pattern}], decision = \"${decision}\")"
  }
}
