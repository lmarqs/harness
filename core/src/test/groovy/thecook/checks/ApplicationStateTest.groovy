package thecook.checks

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.junit.Test
import java.nio.file.Files
import thecook.composition.Kitchen
import thecook.cookbook.Cookbook
import thecook.groovy.GroovyBatchLoader
import thecook.groovy.NativePlanning
import thecook.reconciliation.StateSchema

class ApplicationStateTest {
  private static void workspace(Closure test) {
    def root = Files.createTempDirectory('cook-application-state-').toFile()
    try { test(root) } finally { root.deleteDir() }
  }

  private static Map readState(File root) {
    new JsonSlurper().parse(new File(root, '.cook/state.json')) as Map
  }

  private static void downgradeToVersionTwo(File root) {
    def state = readState(root)
    new File(root, '.cook/state.json').text = JsonOutput.toJson([version: 2, files: state.files])
  }

  @Test void versionTwoGitAndAgentSettingsKeepExplicitLegacyOwnership() {
    workspace { root ->
      new File(root, '.gitignore').text = 'manual/\n'
      new File(root, '.claude').mkdirs()
      new File(root, '.claude/settings.json').text = '{"permissions":{"mode":"original"},"manual":true}\n'
      def old = new Kitchen(root)
      def git = old.ingredient('git'); git.output('.gitignore') { block('ignore') { 'cache/' } }; git.mount()
      def agent = old.ingredient('agent'); agent.output('.claude/settings.json', [format: 'json']) {
        key(['permissions', 'mode'], [adopt: true]) { 'managed' }
      }; agent.mount()
      old.plan().apply()
      downgradeToVersionTwo(root)
      def before = new File(root, '.cook/state.json').bytes

      def next = new Kitchen(root)
      def recipe = next.recipe('new'); recipe.application('agentic.permissions/new')
      recipe.output('.claude/settings.json', [format: 'json']) { key(['permissions', 'allow']) { ['build'] } }
      recipe.mount()
      def plan = next.plan()
      assert new File(root, '.cook/state.json').bytes == before
      plan.apply()
      def state = readState(root)
      assert state.version == 3
      assert state.legacy['.gitignore'] == ['git/ignore']
      assert state.legacy['.claude/settings.json'] == ['agent/permissions/mode']
      assert state.applications['agentic.permissions/new']['.claude/settings.json'] == ['new/permissions/allow']
      assert state.files['.claude/settings.json'].regions['agent/permissions/mode'].original == '"original"'
      assert new File(root, '.gitignore').text.contains('cache/')
      assert new JsonSlurper().parse(new File(root, '.claude/settings.json')).permissions == [mode: 'managed', allow: ['build']]
      assert next.plan().changes.empty
    }
  }

  @Test void matchingLegacyContentAndRecipeNameCannotClaimOwnership() {
    workspace { root ->
      def old = new Kitchen(root)
      def writer = old.ingredient('git'); writer.output('.gitignore') { block('ignore') { 'cache/' } }; writer.mount()
      old.plan().apply()
      downgradeToVersionTwo(root)
      def beforeState = new File(root, '.cook/state.json').bytes
      def beforeOutput = new File(root, '.gitignore').bytes
      def next = new Kitchen(root)
      def recipe = next.recipe('git'); recipe.application('agentic.permissions/git')
      recipe.definition.provenance = [recipe: 'recipes/git/recipe.groovy', inputs: [policy: '.agents/policy.yaml']]
      recipe.output('.gitignore') { block('ignore') { 'cache/' } }; recipe.mount()
      try { next.plan(); assert false: 'Legacy contribution was reassigned' }
      catch (IllegalStateException expected) { assert expected.message.contains('Legacy ownership') && expected.message.contains('unmount') }
      assert new File(root, '.cook/state.json').bytes == beforeState
      assert new File(root, '.gitignore').bytes == beforeOutput
    }
  }

  @Test void versionTwoApplicationMembershipSurvivesAlongsideUnknownLegacy() {
    workspace { root ->
      def old = new Kitchen(root)
      def known = old.recipe('known'); known.application('fixture/known')
      known.output('.gitignore') { block('known') { 'known/' } }; known.mount()
      def unknown = old.ingredient('unknown')
      unknown.output('.gitignore') { block('unknown') { 'unknown/' } }; unknown.mount()
      old.plan().apply()
      def stateFile = new File(root, '.cook/state.json')
      def state = readState(root)
      stateFile.text = JsonOutput.toJson([version: 2, files: state.files, applications: state.applications])

      def next = new Kitchen(root)
      def withdraw = next.recipe('withdraw'); withdraw.application('fixture/known'); withdraw.mount()
      next.plan().apply()
      assert readState(root).legacy['.gitignore'] == ['unknown/unknown']
      assert readState(root).applications['fixture/known'].isEmpty()
      assert new File(root, '.gitignore').text.contains('unknown/')
      assert !new File(root, '.gitignore').text.contains('# cook:begin known/known')
    }
  }

  @Test void provenanceIsDiagnosticAndSerializationIsStable() {
    workspace { root ->
      def source = new File(root, 'source'); source.mkdirs()
      def file = new File(source, 'recipes/fixture/recipe.groovy'); file.parentFile.mkdirs()
      file.text = '''return { kitchen, book, options ->
  def recipe = kitchen.recipe('fixture')
  recipe.application('fixture/app')
  recipe.output('result.txt', [owns: true]) { text { 'ready' } }
  recipe
}'''
      def book = new Cookbook(source)
      def kitchen = new Kitchen(root)
      book.recipe('fixture', kitchen, [second: 'b', first: 'a']).mount()
      kitchen.plan().apply()
      def stateFile = new File(root, '.cook/state.json')
      def first = stateFile.bytes
      assert readState(root).provenance['fixture/app'] == [recipe: 'recipes/fixture/recipe.groovy', inputs: [first: 'a', second: 'b']]
      assert !new File(root, 'cook.groovy').exists()
      assert kitchen.plan().changes.empty
      kitchen.plan().apply()
      assert stateFile.bytes == first
      def loaded = new GroovyBatchLoader().load(root.toPath(), file.toPath(), [], [second: 'b', first: 'a'])
      new NativePlanning().plan(root.toPath(), loaded).apply()
      assert stateFile.bytes == first
      assert StateSchema.sorted([z: [b: 2, a: 1], a: 0]) == [a: 0, z: [a: 1, b: 2]]
    }
  }

  @Test void malformedAndFutureStateCannotChangeOutputs() {
    workspace { root ->
      new File(root, 'keep.txt').text = 'manual'
      def state = new File(root, '.cook/state.json'); state.parentFile.mkdirs()
      ['{"version":4,"files":{}}', '{"version":3,"files":{},"applications":{},"legacy":{"keep.txt":["*"]},"provenance":{}}',
       '{"version":3,"files":{},"applications":{},"legacy":{},"provenance":{},"unknown":true}'].each { invalid ->
        state.text = invalid
        def kitchen = new Kitchen(root)
        def recipe = kitchen.recipe('new'); recipe.application('fixture/new')
        recipe.output('new.txt', [owns: true]) { text { 'new' } }; recipe.mount()
        try { kitchen.plan(); assert false: 'Invalid state was accepted' }
        catch (IllegalArgumentException expected) { assert expected.message.contains('Unsupported or malformed') }
        assert state.text == invalid
        assert new File(root, 'keep.txt').text == 'manual'
        assert !new File(root, 'new.txt').exists()
      }
    }
  }
}
