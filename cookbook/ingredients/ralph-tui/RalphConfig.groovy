package thecook.cookbook.ingredients.ralphtui

import groovy.transform.CompileStatic

@CompileStatic
class RalphConfig {
  String agent = 'codex'
  String tracker = 'json'
  String prdPath = 'prd.json'
  String mode = 'standalone'
  int maxIterations = 10
  int workers = 1
  boolean autoCommit = false
  boolean adopt = false

  void validate() {
    if (!(agent in ['codex', 'claude'])) throw new IllegalArgumentException('Ralph agent is codex or claude')
    if (!(tracker in ['json', 'beads', 'beads-bv'])) throw new IllegalArgumentException('Ralph tracker is json, beads or beads-bv')
    if (!(mode in ['standalone', 'mise'])) throw new IllegalArgumentException('Ralph mode is standalone or mise')
    if (workers < 1 || workers > 32) throw new IllegalArgumentException('Ralph workers must be between 1 and 32')
    if (maxIterations < 0 || maxIterations > 1000) throw new IllegalArgumentException('Ralph maxIterations must be between 0 and 1000')
    if (!(prdPath ==~ /[a-zA-Z0-9_.\/-]+/) || prdPath.startsWith('/') || prdPath.tokenize('/').contains('..'))
      throw new IllegalArgumentException('Ralph prdPath must be a repository path')
  }

  Map<String, Object> nativeConfig() {
    validate()
    [configVersion: '2.1', defaultAgent: agent, tracker: tracker,
      trackerOptions: tracker == 'json' ? [path: prdPath] : [beadsDir: '.beads'],
      maxIterations: maxIterations, autoCommit: autoCommit,
      parallel: [mode: workers == 1 ? 'never' : 'auto', maxWorkers: workers]] as Map<String, Object>
  }
}
