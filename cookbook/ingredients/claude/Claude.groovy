@Ingredient
class Claude implements Harness {
  final String name = 'claude'
  Map settings = [:]
  List<String> instructions = []
  Map<String, String> skillLinks = [:]
  final List<String> localFiles = ['/.claude/settings.local.json']

  /** Translates the shared policy into Claude's permission model. */
  void permit(Policy policy) {
    settings.permissions = [
      defaultMode: 'acceptEdits',
      allow: policy.allow.collect { rule(it) },
      ask: policy.ask.collect { rule(it) },
      deny: policy.deny.collect { rule(it) },
    ]
  }

  void settings(Map more) { settings += more }

  void instruct(String... lines) { instructions += lines }

  /** Claude reads `CLAUDE.md`; an import line points it at the shared instructions. */
  void readInstructions(String path) { instruct("@${path}") }

  /** Claude discovers skills under `.claude/skills`; each shared skill is linked there. */
  void readSkills(String path, List<String> names) {
    names.each { skillLinks[it] = "../../${path}/${it}" }
  }

  @Keys(path = '.claude/settings.json')
  Map settingsKeys() { settings ?: null }

  @Section(path = 'CLAUDE.md', id = 'instructions')
  List<String> instructionsSection() { instructions ?: null }

  @Links(path = '.claude/skills')
  Map<String, String> links() { skillLinks ?: null }

  private static String rule(Command command) { "Bash(${command.prefix.join(' ')} *)" }
}
