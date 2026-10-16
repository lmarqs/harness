package dev.thecook.agents

/**
 * Models the project settings, instructions and skill links Claude Code reads in a repository.
 *
 * ## Writes for
 *
 * The Claude Code settings schema in `.claude/settings.json`: top-level JSON keys, including hooks and
 * `permissions` with `defaultMode` and `allow`, `ask` and `deny` lists of `Bash(<prefix> *)` rules.
 * Markdown instructions in `CLAUDE.md` and symbolic links under `.claude/skills/` complete the project setup.
 *
 * ## Instances
 *
 * One instance covers the Claude Code setup in one directory. Use `at` for a separate setup in another
 * directory; shared instruction and skill paths are relative to that instance's directory.
 *
 * ## Use
 *
 * ```groovy
 * import dev.thecook.agents.Agents
 * import dev.thecook.agents.Claude
 *
 * @ingredient(Agents) def agents
 * @ingredient(Claude) def claude
 *
 * agents.instruct('Run tests before committing.')
 * agents.permit([claude])
 * agents.connect([claude])
 * ```
 *
 * ## With other models
 *
 * `dev.thecook.agents.Agents` drives this harness through `permit`, `readInstructions` and `readSkills`.
 * It supplies the shared policy and paths; Claude translates them into its native files. The same command
 * prefixes do not mean identical enforcement across harnesses; review the generated permissions against
 * Claude's permission model. `localFiles` supplies ignore patterns for `dev.thecook.git.Git`.
 */
@Ingredient
class Claude implements Harness {
  /** The harness name `claude`, used to select Claude Code. */
  final String name = 'claude'
  private Map settings = [:]
  private List<String> instructions = []
  private Map<String, String> skillLinks = [:]

  /** Destination-root ignore patterns for the local settings file Claude Code writes. */
  final List<String> localFiles = ['/.claude/settings.local.json']

  /**
   * Sets `permissions` from the shared policy: `acceptEdits` and one `Bash(<prefix> *)` entry per command
   * in its `allow`, `ask` or `deny` list. Replaces any permissions supplied by an earlier `settings` call.
   */
  void permit(Policy policy) {
    settings.permissions = [
      defaultMode: 'acceptEdits',
      allow: policy.allow.collect { rule(it) },
      ask: policy.ask.collect { rule(it) },
      deny: policy.deny.collect { rule(it) },
    ]
  }

  /** Adds top-level settings keys, hooks included; a supplied key replaces its earlier value whole. */
  void settings(Map more) { settings += more }

  /** Appends Markdown instruction lines to the managed section in `CLAUDE.md`. */
  void instruct(String... lines) { instructions += lines.toList() }

  /** Adds `@<path>` to `CLAUDE.md` to read shared instructions; `path` is relative to this instance's directory. */
  void readInstructions(String path) { instruct("@${path}") }

  /** Links each named shared skill under `.claude/skills/`; `path` is relative to this instance's directory. */
  void readSkills(String path, List<String> names) {
    names.each { skillLinks[it] = "../../${path}/${it}" }
  }

  /** Each supplied top-level key of `.claude/settings.json`, owned whole, when settings or permissions are set. */
  @Keys(path = '.claude/settings.json')
  Map settingsKeys() { settings ?: null }

  /** The `instructions` section of `CLAUDE.md`, when instruction lines or a shared instruction path are added. */
  @Section(path = 'CLAUDE.md', id = 'instructions')
  List<String> instructionsSection() { instructions ?: null }

  /** One relative link under `.claude/skills/` per named shared skill, when skills are connected. */
  @Links(path = '.claude/skills')
  Map<String, String> links() { skillLinks ?: null }

  private static String rule(Command command) { "Bash(${command.prefix.join(' ')} *)" }
}
