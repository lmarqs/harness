package dev.thecook.ralph

/**
 * Models Ralph TUI's loop configuration, runner and selected JSON PRD.
 *
 * ## Writes for
 *
 * Ralph TUI 0.12.0, with config version 2.1 in `.ralph-tui/config.toml`. The packaged Bash runner invokes
 * `ralph-tui run` with the selected agent and JSON PRD or Beads epic tracker.
 *
 * ## Instances
 *
 * One instance covers one repository's Ralph configuration and runner, and can write one selected PRD.
 * A repository in another directory uses an instance `at` that directory, with paths relative to it.
 *
 * ## Use
 *
 * ```groovy
 * import dev.thecook.ralph.Ralph
 *
 * @ingredient(Ralph) def ralph
 *
 * ralph.configure(agent: 'codex', tracker: 'json')
 * ralph.focus('tasks/prd.json', 'US-007', '.ralph-tui/selected-prd.json')
 * ralph.installRunner()
 * ```
 *
 * ## With other models
 *
 * `tools` supplies Mise's tool pins, and `runScript()` supplies its file-task script. `localFiles` and
 * `ephemeralFiles` supply Git ignore patterns. Beads supplies tracker configuration and a ready-task script
 * when the recipe selects that tracker. The recipe wires these models together.
 */
@Ingredient
class Ralph {
  private static final List<String> SETTINGS = ['agent', 'tracker', 'prd', 'workers', 'maxIterations', 'autoCommit', 'headless']

  private boolean configured = false
  private boolean standalone = false
  private String agent = 'codex'
  private String tracker = 'json'       // json | beads
  private String prd = 'tasks/prd.json'
  private String focusedPrd             // the reduced PRD written at `prd`
  private int workers = 1
  private int maxIterations = 10
  private boolean autoCommit = false
  private boolean headless = false

  /** Bun and Ralph TUI version pins for a recipe to pass to Mise. */
  final Map<String, String> tools = ['bun': '1.3.6', 'npm:ralph-tui': '0.12.0']

  /** Git ignore patterns for Ralph's local worktrees, iteration output, reports, images and session files. */
  final List<String> localFiles = ['/.ralph-tui/worktrees/', '/.ralph-tui/iterations/', '/.ralph-tui/reports/',
                                   '/.ralph-tui/images/', '/.ralph-tui/session.json', '/.ralph-tui/session-meta.json',
                                   '/.ralph-tui/progress.md']

  /** Git ignore patterns for Ralph's transient lock file, rooted in this instance's directory. */
  final List<String> ephemeralFiles = ['/.ralph-tui/ralph.lock']

  /**
   * Enables configuration and sets any of `agent`, `tracker`, `prd`, `workers`, `maxIterations`, `autoCommit`
   * and `headless`. Defaults are `codex`, `json`, `tasks/prd.json`, 1, 10, false and false respectively.
   * Unspecified settings keep their values; unknown names and trackers other than `json` or `beads` are rejected.
   * More than one worker enables parallel mode. Call before `focus()` or obtaining `runScript()`.
   */
  void configure(Map settings) {
    def unknown = settings.keySet() - SETTINGS
    if (unknown) throw new IllegalArgumentException("Unknown Ralph setting: ${unknown}")
    settings.each { name, value -> this[name] = value }
    if (!(tracker in ['json', 'beads'])) throw new IllegalArgumentException("Tracker must be json or beads: ${tracker}")
    configured = true
  }

  /** Requests the executable runner at `.ralph-tui/run`, for a repository without a task runner. */
  void installRunner() { standalone = true }

  /**
   * Requests a copy of the JSON PRD at `source` reduced to one story with the supplied id, at `selected`,
   * and points Ralph at it. Paths are relative to this instance's directory. Reads the source as earlier recipes
   * in the invocation leave it; a missing source or anything other than exactly one matching story is rejected.
   * Call after `configure()` when both are used, and before obtaining `runScript()` for the selected PRD.
   */
  void focus(String source, String story, String selected) {
    def text = current(source)
    if (text == null) throw new IllegalArgumentException("${source} must exist")
    def document = new JsonSlurper().parseText(text)
    def stories = document.userStories.findAll { it.id == story }
    if (stories.size() != 1) throw new IllegalArgumentException("${source} must contain exactly one story ${story}")
    prd = selected
    focusedPrd = JsonOutput.prettyPrint(JsonOutput.toJson(document + [userStories: stories])) + '\n'
  }

  /**
   * The packaged Bash runner with this instance's agent, tracker, PRD path and headless option substituted.
   * Call after `configure()` and any `focus()` call, then pass the script to a task runner such as Mise.
   */
  String runScript() {
    resource('run.sh')
      .replace('@AGENT@', agent).replace('@TRACKER@', tracker).replace('@PRD@', prd)
      .replace('@HEADLESS@', headless ? 'ARGS+=(--headless)' : '')
  }

  /**
   * Each top-level key in `.ralph-tui/config.toml`, owned whole, when `configure()` is called.
   * Includes config version 2.1, agent and tracker settings, iteration and commit settings, and parallel mode.
   * A pristine instance declares nothing.
   */
  @Keys(path = '.ralph-tui/config.toml')
  Map config() {
    if (!configured) return null
    [configVersion: '2.1', defaultAgent: agent, tracker: tracker,
     agents: [[name: agent, plugin: agent, options: [:]]],
     trackerOptions: tracker == 'json' ? [path: prd] : [beadsDir: '.beads'],
     maxIterations: maxIterations, autoCommit: autoCommit,
     parallel: [mode: workers > 1 ? 'auto' : 'never', maxWorkers: workers]]
  }

  /** The whole executable `.ralph-tui/run` file when `installRunner()` is called; otherwise declares nothing. */
  @File(path = '.ralph-tui/run', mode = '0755')
  String runner() { standalone ? runScript() : null }

  /** The whole file at the current PRD path, holding the reduced document after `focus()`; otherwise declares nothing. */
  @File(path = '${prd}')
  String prdFile() { focusedPrd }
}
