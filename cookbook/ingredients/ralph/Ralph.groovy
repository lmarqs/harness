@Ingredient
class Ralph {
  boolean configured = false
  String agent = 'codex'
  String tracker = 'json'       // json | beads
  String prd = 'tasks/prd.json'
  String prdContent             // set when a recipe writes the PRD Ralph will read
  int workers = 1
  int maxIterations = 10
  boolean autoCommit = false
  boolean headless = false
  String mode = 'standalone'    // standalone | mise

  final Map<String, String> tools = ['bun': '1.3.6', 'npm:ralph-tui': '0.12.0']
  final List<String> localFiles = ['/.ralph-tui/worktrees/', '/.ralph-tui/iterations/', '/.ralph-tui/reports/',
                                   '/.ralph-tui/images/', '/.ralph-tui/session.json', '/.ralph-tui/session-meta.json',
                                   '/.ralph-tui/progress.md']
  final List<String> ephemeralFiles = ['/.ralph-tui/ralph.lock']

  void configure(Map settings) {
    settings.each { name, value -> this[name] = value }
    if (!(tracker in ['json', 'beads'])) throw new IllegalArgumentException("Tracker must be json or beads: ${tracker}")
    if (!(mode in ['standalone', 'mise'])) throw new IllegalArgumentException("Mode must be standalone or mise: ${mode}")
    configured = true
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
  String runner() { configured && mode == 'standalone' ? runScript() : null }

  @File(path = '${prd}')
  String prdFile() { prdContent }
}
