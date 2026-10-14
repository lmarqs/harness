@Ingredient
class Claude {
  Map settings = [:]
  List<String> instructions = []
  List<String> skillNames = []
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

  /** Claude reads `CLAUDE.md`; this points it at the shared `AGENTS.md`. */
  void importAgents() { instruct('@AGENTS.md') }

  /** Claude discovers skills under `.claude/skills`; link each shared skill there. */
  void skills(Agents agents) { skillNames += agents.skills*.name }

  @Keys(path = '.claude/settings.json')
  Map settingsKeys() { settings ?: null }

  @Section(path = 'CLAUDE.md', id = 'instructions')
  List<String> instructionsSection() { instructions ?: null }

  @Links(path = '.claude/skills')
  Map<String, String> skillLinks() { skillNames.collectEntries { [it, "../../.agents/skills/${it}"] } ?: null }

  private static String rule(Command command) { "Bash(${command.prefix.join(' ')} *)" }
}
