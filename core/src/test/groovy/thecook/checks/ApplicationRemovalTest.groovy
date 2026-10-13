package thecook.checks

import groovy.json.JsonSlurper
import org.junit.Test
import java.nio.file.Files
import thecook.composition.Kitchen
import thecook.groovy.NativePlanning

class ApplicationRemovalTest {
  @Test void removesSelectedRegionsAndRestoresAdoptedValueWhileRetainingAnotherApplication() {
    def root = Files.createTempDirectory('cook-remove-').toFile()
    try {
      def settings = new File(root, 'settings.json')
      settings.text = '{"claude":"original","manual":true}\n'
      def claude = new Kitchen(root).recipe('claude')
      claude.application('agentic.permissions/claude')
      claude.output('settings.json', [format: 'json']) { key(['claude'], [adopt: true]) { 'managed' } }
      claude.mount(); claude.kitchen.plan().apply()
      def codex = new Kitchen(root).recipe('codex')
      codex.application('agentic.permissions/codex')
      codex.output('settings.json', [format: 'json']) { key(['codex']) { 'kept' } }
      codex.mount(); codex.kitchen.plan().apply()
      def planner = new NativePlanning()
      def preview = planner.remove(root.toPath(), new File(root, 'permission.groovy').toPath(), ['claude'])
      assert preview.changes == ['settings.json']
      assert new JsonSlurper().parse(settings).claude == 'managed'
      preview.apply()
      assert new JsonSlurper().parse(settings) == [claude: 'original', manual: true, codex: 'kept']
      def state = new JsonSlurper().parse(new File(root, '.cook/state.json'))
      assert state.applications.keySet() == ['agentic.permissions/codex'] as Set
      def stateBytes = new File(root, '.cook/state.json').bytes
      def absent = planner.remove(root.toPath(), new File(root, 'permission.groovy').toPath(), ['claude'])
      assert absent.changes.empty
      absent.apply()
      assert new File(root, '.cook/state.json').bytes == stateBytes
      assert state.files['settings.json'].regions.size() == 1
    } finally { root.deleteDir() }
  }

  @Test void driftRejectsRemovalBeforeAnyWriteAndInitSelectsKnownChildren() {
    def root = Files.createTempDirectory('cook-remove-drift-').toFile()
    try {
      def init = new Kitchen(root).recipe('init')
      init.application('agentic.init/git')
      init.output('managed.txt', [owns: true]) { text { 'original' } }
      init.mount(); init.kitchen.plan().apply()
      ['claude', 'codex'].each { name ->
        def child = new Kitchen(root).recipe(name)
        child.application('agentic.permissions/' + name)
        child.output(name + '.txt', [owns: true]) { text { name } }
        child.mount(); child.kitchen.plan().apply()
      }
      def planner = new NativePlanning()
      def source = new File(root, 'cookbook/recipes/cookbook/recipe.groovy').toPath()
      assert planner.remove(root.toPath(), source, ['claude']).changes == ['claude.txt', 'managed.txt']
      new File(root, 'managed.txt').text = 'edited'
      def state = new File(root, '.cook/state.json').text
      try { planner.remove(root.toPath(), source, ['claude']); assert false: 'Expected drift rejection' }
      catch (IllegalStateException expected) { assert expected.message.contains('Edited managed whole file') }
      assert new File(root, 'managed.txt').text == 'edited'
      assert new File(root, 'codex.txt').text == 'codex'
      assert new File(root, '.cook/state.json').text == state
    } finally { root.deleteDir() }
  }
}
