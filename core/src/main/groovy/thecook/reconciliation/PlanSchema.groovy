package thecook.reconciliation

import groovy.json.JsonSlurper
import thecook.filesystem.TargetPath

class PlanSchema {
  static void validate(Map plan, String label = 'Invalid plan') {
    try {
      require(plan.version == 1 && plan.root instanceof String && plan.observed instanceof Map && plan.changes instanceof List)
      StateSchema.snapshot(plan.stateBefore); StateSchema.validate(plan.stateAfter)
      require(plan.stateBefore.kind == null)
      plan.observed.each { path, snapshot -> TargetPath.of(path); StateSchema.snapshot(snapshot) }
      def seen = new HashSet()
      plan.changes.each { change ->
        TargetPath.of(change.path)
        require(seen.add(change.path) && plan.observed.containsKey(change.path) && change.before == plan.observed[change.path])
        StateSchema.snapshot(change.before)
        require(change.content == null || change.content instanceof String)
        require(change.mode == null || change.mode ==~ /[r-][w-][x-][r-][w-][x-][r-][w-][x-]/)
        require(change.kind == null || change.kind == 'symlink')
        if (change.kind == 'symlink') {
          require(change.mode == null && (!change.before.exists || change.before.kind == 'symlink'))
          if (change.content != null) StateSchema.linkTarget(change.content)
        } else require(change.before.kind == null)
      }
      def oldState = plan.stateBefore.exists ? new JsonSlurper().parseText(plan.stateBefore.content) : [version: 1, files: [:]]
      StateSchema.validate(oldState)
      def oldFiles = oldState.files.keySet()
      require((oldFiles + plan.stateAfter.files.keySet()).every { plan.observed.containsKey(it) })
    } catch (Exception error) {
      throw new IllegalArgumentException(label, error)
    }
  }
  private static void require(boolean condition) {
    if (!condition) throw new IllegalArgumentException('Invalid plan shape')
  }
}
