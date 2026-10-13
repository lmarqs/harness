package thecook.cookbook.ingredients.ralphtui

import thecook.composition.Component
import thecook.composition.Kitchen

// A reusable typed component: public parameters stay live until each render.
class RalphTUI {
  final Map parameters
  final Map lifecycle = [:]

  RalphTUI(Map options = [:]) {
    def defaults = [id: 'ralph-tui', mode: 'standalone', agent: 'codex', tracker: 'json',
      workers: 1, maxIterations: 10, prdPath: 'prd.json', headless: false,
      model: null, agentOptions: [:], autoCommit: false, adopt: false]
    def unknown = options.keySet().findAll { !defaults.containsKey(it) }
    if (unknown) throw new IllegalArgumentException("Unknown Ralph parameter: ${unknown.join(', ')}")
    parameters = defaults + options
  }
  Object propertyMissing(String name) {
    if (parameters.containsKey(name)) return parameters[name]
    component.getProperty(name)
  }
  void propertyMissing(String name, Object value) {
    if (!parameters.containsKey(name)) throw new IllegalArgumentException("Unknown Ralph parameter: ${name}")
    parameters[name] = value
  }
  Component getComponent() {
    if (!lifecycle.component) throw new IllegalStateException('Ralph must be declared in a kitchen first')
    lifecycle.component
  }
  Component declare(Kitchen kitchen, def cookbook = null) {
    if (lifecycle.component) {
      if (!component.kitchen.is(kitchen)) throw new IllegalArgumentException('Ralph belongs to another kitchen')
      return component
    }
    def ralph = this
    def read = { String name -> readTemplate(name, cookbook) }
    def result = kitchen.ingredient(parameters.id.toString())
    lifecycle.component = result
    result.validate { ralph.validateParameters() }
    result.export('tools') { ralph.mode == 'mise' ? [[bun: '1.3.6', 'npm:ralph-tui': '0.12.0']] : [] }
    result.export('files.local') {
      ['iterations', 'worktrees', 'reports', 'images'].collect { [path: '.ralph-tui/' + it, scope: 'root', kind: 'directory'] } +
      [
        [path: '.ralph-tui/session.json', scope: 'root', kind: 'file'],
        [path: '.ralph-tui/session-meta.json', scope: 'root', kind: 'file'],
        [path: '.ralph-tui/progress.md', scope: 'root', kind: 'file']
      ]
    }
    result.export('files.ephemeral') { [[path: '.ralph-tui/ralph.lock', scope: 'root', kind: 'file']] }
    result.output('.ralph-tui/config.toml', [format: 'toml']) {
      view {
        ralph.nativeConfig().each { name, value ->
          key([name], [adopt: ralph.adopt.toString().toBoolean()]) {
            value
          }
        }
      }
    }
    result.view {
      def path = ralph.mode == 'mise' ? '.mise/tasks/ralph/run' : '.ralph-tui/run'
      output(path, [owns: true, mode: '0755', adopt: ralph.adopt.toString().toBoolean()]) {
        text {
          ralph.runner(read('run.sh'))
        }
      }
    }
    result
  }
  Component mount(Kitchen kitchen) {
    def result = declare(kitchen); result.mount(); result
  }
  void unmount() {
    component.unmount()
  }
  private String readTemplate(String name, def cookbook) {
    if (cookbook) return cookbook.text('ingredients/ralph-tui/' + name)
    def stream = getClass().getResourceAsStream('/META-INF/the-cook/ingredients/ralph-tui/' + name)
    if (stream) return stream.withCloseable { it.getText('UTF-8') }
    def directory = new File(getClass().protectionDomain.codeSource.location.toURI()).parentFile
    new File(directory, name).getText('UTF-8')
  }
  void validateParameters() {
    if (!(mode in ['standalone', 'mise'])) throw new IllegalArgumentException('Ralph mode is standalone or mise')
    if (!(agent in ['codex', 'claude'])) throw new IllegalArgumentException('Ralph agent is codex or claude')
    if (!(tracker in ['json', 'beads', 'beads-bv'])) throw new IllegalArgumentException('Ralph tracker is json, beads or beads-bv')
    if (!(workers.toString() ==~ /[0-9]+/) || workers.toString().toInteger() < 1 || workers.toString().toInteger() > 32)
      throw new IllegalArgumentException('Ralph workers must be between 1 and 32')
    if (!(maxIterations.toString() ==~ /[0-9]+/) || maxIterations.toString().toInteger() > 1000)
      throw new IllegalArgumentException('Ralph maxIterations must be between 0 and 1000')
    if (!(prdPath ==~ /[a-zA-Z0-9_.\/-]+/) || prdPath.startsWith('/') || prdPath.tokenize('/').contains('..'))
      throw new IllegalArgumentException('Ralph prdPath must be a repository path')
    if (!(autoCommit?.toString() in ['true', 'false']) || !(headless?.toString() in ['true', 'false']) || !(adopt?.toString() in ['true', 'false']))
      throw new IllegalArgumentException('Ralph autoCommit, headless and adopt must be booleans')
    if (!(agentOptions instanceof Map)) throw new IllegalArgumentException('Ralph agentOptions must be a map')
  }
  Map nativeConfig() {
    def options = [:] + agentOptions
    if (model) options.model = model
    [configVersion: '2.1', defaultAgent: agent, tracker: tracker,
      agents: [[name: agent, plugin: agent, options: options]],
      trackerOptions: tracker == 'json' ? [path: prdPath] : [beadsDir: '.beads'],
      maxIterations: maxIterations.toString().toInteger(),
      autoCommit: autoCommit.toString().toBoolean(),
      parallel: [mode: workers.toString().toInteger() == 1 ? 'never' : 'auto',
        maxWorkers: workers.toString().toInteger()]]
  }
  String runner(String template) {
    template.replace('@AGENT@', agent.toString()).replace('@TRACKER@', tracker.toString())
        .replace('@PRD@', prdPath.toString())
        .replace('@HEADLESS@', headless.toString().toBoolean() ? 'ARGS+=(--headless)' : '')
  }
}
