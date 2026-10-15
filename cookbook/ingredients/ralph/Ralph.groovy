@Ingredient
class Ralph {
  static final List<String> SETTINGS = ['agent', 'tracker', 'prd', 'workers', 'maxIterations', 'autoCommit', 'headless']

  boolean configured = false
  boolean standalone = false
  String agent = 'codex'
  String tracker = 'json'       // json | beads
  String prd = 'tasks/prd.json'
  String focusedPrd             // the reduced PRD written at `prd`
  int workers = 1
  int maxIterations = 10
  boolean autoCommit = false
  boolean headless = false

  final Map<String, String> tools = ['bun': '1.3.6', 'npm:ralph-tui': '0.12.0']
  final List<String> localFiles = ['/.ralph-tui/worktrees/', '/.ralph-tui/iterations/', '/.ralph-tui/reports/',
                                   '/.ralph-tui/images/', '/.ralph-tui/session.json', '/.ralph-tui/session-meta.json',
                                   '/.ralph-tui/progress.md']
  final List<String> ephemeralFiles = ['/.ralph-tui/ralph.lock']

  /** Sets any of `SETTINGS` and turns the configuration on. */
  void configure(Map settings) {
    def unknown = settings.keySet() - SETTINGS
    if (unknown) throw new IllegalArgumentException("Unknown Ralph setting: ${unknown}")
    settings.each { name, value -> this[name] = value }
    if (!(tracker in ['json', 'beads'])) throw new IllegalArgumentException("Tracker must be json or beads: ${tracker}")
    configured = true
  }

  /** Installs the runner at `.ralph-tui/run`, for a repository without a task runner. */
  void installRunner() { standalone = true }

  /** Writes a copy of the JSON PRD at `source` reduced to one user story, at `selected`, and points Ralph at it. */
  void focus(String source, String story, String selected) {
    def text = current(source)
    if (text == null) throw new IllegalArgumentException("${source} must exist")
    def document = new JsonSlurper().parseText(text)
    def stories = document.userStories.findAll { it.id == story }
    if (stories.size() != 1) throw new IllegalArgumentException("${source} must contain exactly one story ${story}")
    prd = selected
    focusedPrd = JsonOutput.prettyPrint(JsonOutput.toJson(document + [userStories: stories])) + '\n'
  }

  String runScript() {
    resource('run.sh')
      .replace('@AGENT@', agent).replace('@TRACKER@', tracker).replace('@PRD@', prd)
      .replace('@HEADLESS@', headless ? 'ARGS+=(--headless)' : '')
  }

  @Keys(path = '.ralph-tui/config.toml')
  Map config() {
    if (!configured) return null
    [configVersion: '2.1', defaultAgent: agent, tracker: tracker,
     agents: [[name: agent, plugin: agent, options: [:]]],
     trackerOptions: tracker == 'json' ? [path: prd] : [beadsDir: '.beads'],
     maxIterations: maxIterations, autoCommit: autoCommit,
     parallel: [mode: workers > 1 ? 'auto' : 'never', maxWorkers: workers]]
  }

  @File(path = '.ralph-tui/run', mode = '0755')
  String runner() { standalone ? runScript() : null }

  @File(path = '${prd}')
  String prdFile() { focusedPrd }
}
