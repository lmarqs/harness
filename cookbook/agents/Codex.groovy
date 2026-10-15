package dev.thecook.agents

/**
 * Models the project configuration, hooks, rules and instructions Codex reads in a repository.
 *
 * ## Writes for
 *
 * Top-level TOML keys in `.codex/config.toml`, top-level JSON keys in `.codex/hooks.json`, rule files under
 * `.codex/rules/` and Markdown instructions in `AGENTS.md`. Command rules use the Codex `prefix_rule` syntax:
 * `prefix_rule(pattern = ["git", "status"], decision = "allow")`, with `allow`, `prompt` or `forbidden` decisions.
 *
 * ## Instances
 *
 * One instance covers the Codex setup in one directory. Use `at` for a separate setup in another directory;
 * Codex reads shared instructions and skills only from that instance's own directory.
 *
 * ## Use
 *
 * ```groovy
 * import dev.thecook.agents.Agents
 * import dev.thecook.agents.Codex
 *
 * @ingredient(Agents) def agents
 * @ingredient(Codex) def codex
 *
 * agents.instruct('Run tests before committing.')
 * agents.permit([codex])
 * agents.connect([codex])
 * ```
 *
 * ## With other models
 *
 * `dev.thecook.agents.Agents` drives this harness through `permit`, `readInstructions` and `readSkills`.
 * It supplies the shared policy and paths; Codex translates the policy into its native settings and rules,
 * and checks that the shared paths are the ones it reads natively. The same command prefixes do not mean
 * identical enforcement across harnesses; review the generated rules against Codex's permission model.
 */
@Ingredient
class Codex implements Harness {
  /** The harness name `codex`, used to select Codex. */
  final String name = 'codex'
  private Map config = [:]
  private Map hooks = [:]
  private Map<String, String> rules = [:]
  private List<String> commands = []
  private List<String> instructions = []

  /**
   * Sets `sandbox_mode` to `workspace-write`, `approval_policy` to `on-request` and
   * `sandbox_workspace_write.network_access` to false. Replaces the command rules with one `prefix_rule` per
   * shared policy command: `allow` stays `allow`, `ask` becomes `prompt` and `deny` becomes `forbidden`.
   * These settings keys replace values supplied by an earlier `config` call.
   */
  void permit(Policy policy) {
    config += [sandbox_mode: 'workspace-write', approval_policy: 'on-request',
               sandbox_workspace_write: [network_access: false]]
    commands = policy.allow.collect { rule(it, 'allow') } +
      policy.ask.collect { rule(it, 'prompt') } +
      policy.deny.collect { rule(it, 'forbidden') }
  }

  /** Adds top-level configuration keys; a supplied key replaces its earlier value whole. */
  void config(Map more) { config += more }

  /** Adds top-level hooks keys; a supplied key replaces its earlier value whole. */
  void hooks(Map more) { hooks += more }

  /**
   * Adds a whole rule file with `body` under `.codex/rules/`, replacing an earlier rule with the same name.
   * The name accepts letters, digits, underscores, dots and hyphens; empty names, `.` and `..` are rejected.
   */
  void rule(String name, String body) {
    if (!(name ==~ /[A-Za-z0-9_.-]+/) || name in ['.', '..']) throw new IllegalArgumentException("Rule file needs a simple name: ${name}")
    rules[name] = body
  }

  /** Appends Markdown instruction lines to the `codex` section in `AGENTS.md`. */
  void instruct(String... lines) { instructions += lines }

  /** Checks that `path` is `AGENTS.md`, which Codex reads natively in its own directory; rejects any other path. */
  void readInstructions(String path) { readsNatively(path, 'AGENTS.md') }

  /**
   * Checks that `path` is `.agents/skills`; rejects any other path. Codex discovers the named skills there
   * natively in its own directory, so this call writes nothing and does not filter by `names`.
   */
  void readSkills(String path, List<String> names) { readsNatively(path, '.agents/skills') }

  /** Each supplied top-level key of `.codex/config.toml`, owned whole, when configuration or permissions are set. */
  @Keys(path = '.codex/config.toml')
  Map configKeys() { config ?: null }

  /** Each supplied top-level key of `.codex/hooks.json`, owned whole, when hooks keys are added. */
  @Keys(path = '.codex/hooks.json')
  Map hookKeys() { hooks ?: null }

  /** One whole file under `.codex/rules/` per named rule, when rules are added. */
  @Files(path = '.codex/rules')
  Map<String, String> ruleFiles() { rules ?: null }

  /** The `commands` section of `.codex/rules/cook.rules`, in `#` comment syntax, when the policy has command rules. */
  @Section(path = '.codex/rules/cook.rules', id = 'commands', comment = '#')
  List<String> commandRules() { commands ?: null }

  /** The `codex` section of `AGENTS.md`, when Codex-specific instruction lines are added. */
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
