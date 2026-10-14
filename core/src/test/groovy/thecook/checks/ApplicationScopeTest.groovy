package thecook.checks

import org.junit.Test
import groovy.json.JsonSlurper
import java.nio.file.Files
import java.nio.file.LinkOption
import thecook.composition.Kitchen
import thecook.cookbook.Cookbook

class ApplicationScopeTest {
  private static void rejectsWithoutWrites(File root, String message, Closure action) {
    def state = new File(root, '.cook/state.json').bytes
    def outputs = root.listFiles().findAll { it.isFile() }.collectEntries { [(it.name): it.bytes] }
    try {
      action(); assert false: 'Expected rejection'
    }
    catch (IllegalArgumentException | IllegalStateException expected) {
      assert expected.message.contains(message)
    }
    assert new File(root, '.cook/state.json').bytes == state
    outputs.each { name, bytes -> assert new File(root, name).bytes == bytes }
  }

  @Test void sequentialApplicationsRetainSeparateJsonKeysAndDetectRetainedDrift() {
    def root = Files.createTempDirectory('cook-sequential-json-').toFile()
    try {
      def claude = new Kitchen(root).recipe('claude'); claude.application('fixture/claude')
      claude.output('settings.json', [format: 'json']) {
        key(['claude']) {
          'first'
        }
      }; claude.mount()
      claude.kitchen.plan().apply()
      def codex = new Kitchen(root).recipe('codex'); codex.application('fixture/codex')
      codex.output('settings.json', [format: 'json']) {
        key(['codex']) {
          'one'
        }
      }; codex.mount()
      codex.kitchen.plan().apply()
      assert new JsonSlurper().parse(new File(root, 'settings.json')) == [claude: 'first', codex: 'one']
      claude.definition.outputs[0].declaration.regions.values().first().supplier = { 'second' }
      claude.kitchen.plan().apply()
      assert new JsonSlurper().parse(new File(root, 'settings.json')) == [claude: 'second', codex: 'one']
      assert claude.kitchen.plan().changes.empty
      new File(root, 'settings.json').text = '{"claude":"second","codex":"handwritten"}\n'
      rejectsWithoutWrites(root, 'Edited managed region') { claude.kitchen.plan() }
    } finally {
      root.deleteDir()
    }
  }

  @Test void differentApplicationCannotClaimRetainedKeyOrExclusiveFileEvenWithSameValue() {
    def root = Files.createTempDirectory('cook-application-conflict-').toFile()
    try {
      def first = new Kitchen(root).recipe('first'); first.application('fixture/first')
      first.output('settings.json', [format: 'json']) {
        key(['shared']) {
          'same'
        }
      }
      first.output('owned.txt', [owns: true]) {
        text {
          'same'
        }
      }; first.mount()
      first.kitchen.plan().apply()
      def second = new Kitchen(root).recipe('second'); second.application('fixture/second')
      second.output('settings.json', [format: 'json']) {
        key(['shared']) {
          'same'
        }
      }; second.mount()
      rejectsWithoutWrites(root, 'already selected elsewhere') { second.kitchen.plan() }
      def other = new Kitchen(root).recipe('other'); other.application('fixture/other')
      other.output('owned.txt', [owns: true]) {
        text {
          'same'
        }
      }; other.mount()
      rejectsWithoutWrites(root, 'already selected elsewhere') { other.kitchen.plan() }
    } finally {
      root.deleteDir()
    }
  }

  @Test void separateTomlKeysAndBlocksSurviveSequentialUpdatesAndWithdrawal() {
    def root = Files.createTempDirectory('cook-sequential-formats-').toFile()
    try {
      new File(root, 'config.toml').text = '# manual\n'
      new File(root, 'notes.md').text = 'Manual intro\n'
      def first = new Kitchen(root).recipe('first'); first.application('fixture/first')
      first.output('config.toml', [format: 'toml']) {
        key(['first']) {
          'one'
        }
      }
      first.output('notes.md') {
        block('first') {
          'First'
        }
      }; first.mount()
      first.kitchen.plan().apply()
      def second = new Kitchen(root).recipe('second'); second.application('fixture/second')
      second.output('config.toml', [format: 'toml']) {
        key(['second']) {
          'two'
        }
      }
      second.output('notes.md') {
        block('second') {
          'Second'
        }
      }; second.mount()
      second.kitchen.plan().apply()
      assert new File(root, 'config.toml').text.contains('first = "one"')
      assert new File(root, 'config.toml').text.contains('second = "two"')
      assert new File(root, 'notes.md').text.contains('First')
      assert new File(root, 'notes.md').text.contains('Second')
      def withdraw = new Kitchen(root).recipe('withdraw'); withdraw.application('fixture/first'); withdraw.mount()
      withdraw.kitchen.plan().apply()
      assert new File(root, 'config.toml').text.contains('# manual')
      assert !new File(root, 'config.toml').text.contains('first =')
      assert new File(root, 'config.toml').text.contains('second = "two"')
      assert new File(root, 'notes.md').text.contains('Manual intro')
      assert !new File(root, 'notes.md').text.contains('First')
      assert new File(root, 'notes.md').text.contains('Second')
    } finally {
      root.deleteDir()
    }
  }

  @Test void retainedBlockAndParentKeyCannotBeClaimedByAnotherApplication() {
    def root = Files.createTempDirectory('cook-application-regions-').toFile()
    try {
      def first = new Kitchen(root).recipe('writer'); first.application('fixture/first')
      first.output('notes.md') {
        block('shared') {
          'same'
        }
      }
      first.output('settings.json', [format: 'json']) {
        key(['service', 'mode']) {
          'same'
        }
      }; first.mount()
      first.kitchen.plan().apply()
      def blockWriter = new Kitchen(root).recipe('writer'); blockWriter.application('fixture/second')
      blockWriter.output('notes.md') {
        block('shared') {
          'same'
        }
      }; blockWriter.mount()
      rejectsWithoutWrites(root, 'already selected elsewhere') { blockWriter.kitchen.plan() }
      def keyWriter = new Kitchen(root).recipe('other'); keyWriter.application('fixture/second')
      keyWriter.output('settings.json', [format: 'json']) {
        key(['service']) {
          [mode: 'same']
        }
      }; keyWriter.mount()
      rejectsWithoutWrites(root, 'already selected elsewhere') { keyWriter.kitchen.plan() }
    } finally {
      root.deleteDir()
    }
  }

  @Test void omittedSymlinkApplicationSurvivesAndStaleTwoApplicationPlanWritesNothing() {
    def root = Files.createTempDirectory('cook-sequential-links-').toFile()
    try {
      def first = new Kitchen(root).recipe('first'); first.application('fixture/first')
      first.output('skill-link', [owns: true]) {
        symlink {
          'target-one'
        }
      }; first.mount()
      first.kitchen.plan().apply()
      def both = new Kitchen(root)
      def update = both.recipe('first'); update.application('fixture/first')
      update.output('skill-link', [owns: true]) {
        symlink {
          'target-two'
        }
      }; update.mount()
      def second = both.recipe('second'); second.application('fixture/second')
      second.output('second.txt', [owns: true]) {
        text {
          'second'
        }
      }; second.mount()
      def stale = both.plan()
      def stateBefore = new File(root, '.cook/state.json').bytes
      Files.delete(new File(root, 'skill-link').toPath())
      Files.createSymbolicLink(new File(root, 'skill-link').toPath(), java.nio.file.Path.of('handwritten'))
      try {
        stale.apply(); assert false: 'Stale plan applied'
      }
      catch (IllegalStateException expected) {
        assert expected.message.contains('Changed since preview')
      }
      assert !new File(root, 'second.txt').exists()
      assert Files.readSymbolicLink(new File(root, 'skill-link').toPath()).toString() == 'handwritten'
      assert new File(root, '.cook/state.json').bytes == stateBefore
      Files.delete(new File(root, 'skill-link').toPath())
      Files.createSymbolicLink(new File(root, 'skill-link').toPath(), java.nio.file.Path.of('target-one'))
      stale.apply()
      assert Files.readSymbolicLink(new File(root, 'skill-link').toPath()).toString() == 'target-two'
      assert new File(root, 'second.txt').text == 'second'
      def onlySecond = new Kitchen(root).recipe('second'); onlySecond.application('fixture/second')
      onlySecond.output('second.txt', [owns: true]) {
        text {
          'changed'
        }
      }; onlySecond.mount()
      onlySecond.kitchen.plan().apply()
      assert Files.exists(new File(root, 'skill-link').toPath(), LinkOption.NOFOLLOW_LINKS)
      assert Files.readSymbolicLink(new File(root, 'skill-link').toPath()).toString() == 'target-two'
    } finally {
      root.deleteDir()
    }
  }
  @Test void selectedEmptyApplicationWithdrawsOnlyItsOwnRegions() {
    def root = Files.createTempDirectory('cook-applications-').toFile()
    try {
      def kitchen = new Kitchen(root)
      def claude = kitchen.recipe('claude'); claude.application('agentic.permissions/claude')
      claude.output('settings.json', [format: 'json']) {
        key(['claude']) {
          'on'
        }
      }
      claude.mount()
      def codex = kitchen.recipe('codex'); codex.application('agentic.permissions/codex')
      codex.output('settings.json', [format: 'json']) {
        key(['codex']) {
          'on'
        }
      }
      codex.mount()
      kitchen.plan().apply()
      def selected = new Kitchen(root).recipe('claude'); selected.application('agentic.permissions/claude')
      selected.mount()
      selected.kitchen.plan().apply()
      assert new File(root, 'settings.json').text.contains('codex')
      assert !new File(root, 'settings.json').text.contains('claude')
      assert selected.kitchen.plan().changes.empty
    } finally {
      root.deleteDir()
    }
  }

  @Test void duplicateApplicationWithDifferentInputsFailsBeforeWriting() {
    def root = Files.createTempDirectory('cook-duplicate-app-').toFile()
    try {
      def kitchen = new Kitchen(root)
      def first = kitchen.recipe('first'); first.application('agentic.permissions/claude')
      first.output('first.txt', [owns: true]) {
        text {
          'first'
        }
      }; first.mount()
      def second = kitchen.recipe('second'); second.application('agentic.permissions/claude')
      second.output('second.txt', [owns: true]) {
        text {
          'second'
        }
      }; second.mount()
      try {
        kitchen.plan(); assert false: 'Duplicate application accepted'
      }
      catch (IllegalArgumentException expected) {
        assert expected.message.contains('Duplicate application')
      }
      assert !new File(root, '.cook/state.json').exists()
    } finally {
      root.deleteDir()
    }
  }

  @Test void childIdentitiesAreStableAcrossSourceMovesPolicyPathsAndComposition() {
    def root = Files.createTempDirectory('cook-app-identity-').toFile()
    try {
      def source = new File('../cookbook')
      def moved = new File(root, 'moved-cookbook')
      [
        'ingredients/agent-policy/Policy.groovy',
        'recipes/agentic/codex/CodexFiles.groovy',
        'recipes/agentic/claude/ClaudeFiles.groovy'
      ].each { path ->
        def destination = new File(moved, path); destination.parentFile.mkdirs()
        destination.bytes = new File(source, path).bytes
      }
      ['codex', 'claude'].each { name ->
        def destination = new File(moved, "recipes/moved-${name}/recipe.groovy")
        destination.parentFile.mkdirs()
        destination.bytes = new File(source, "recipes/agentic/${name}/recipe.groovy").bytes
      }
      def selected = { Cookbook book, List<String> names ->
        def kitchen = new Kitchen(root)
        names.each { name ->
          def options = name.endsWith('codex') ? [config: [model: 'fixture']] :
          [settings: [permissions: [allow: ['Bash(check *)']]]]
          book.recipe(name, kitchen, options).mount()
        }
        kitchen.render().applications
      }
      def original = new Cookbook(source)
      def direct = selected(original, ['agentic/codex', 'agentic/claude'])
      assert direct.keySet() == ['agentic.permissions/codex', 'agentic.permissions/claude'] as Set
      assert selected(original, ['agentic/codex'])['agentic.permissions/codex']*.path == direct['agentic.permissions/codex']*.path
      assert selected(original, ['agentic/claude'])['agentic.permissions/claude']*.path == direct['agentic.permissions/claude']*.path
      assert selected(original, ['agentic/codex'])['agentic.permissions/codex']*.regions*.value ==
      direct['agentic.permissions/codex']*.regions*.value
      assert selected(original, ['agentic/claude'])['agentic.permissions/claude']*.regions*.value ==
      direct['agentic.permissions/claude']*.regions*.value
      assert selected(new Cookbook(moved), ['moved-codex', 'moved-claude']).keySet() == direct.keySet()

      def skills = new File(root, 'skills')
      ['solid', 'hexagonal-layout', 'effective-java-core'].each { name ->
        def file = new File(skills, name + '/SKILL.md'); file.parentFile.mkdirs(); file.text = '# fixture\n'
      }
      ['one/policy.yaml', 'two/policy.yaml'].each { path ->
        def policy = new File(root, path); policy.parentFile.mkdirs()
        policy.text = 'version: 1\ninstructions: []\ncommands: {}\n'
        def book = new Cookbook(source)
        [
          'ingredients/agent-policy/Policy.groovy',
          'ingredients/agent-policy/PolicyFile.groovy',
          'ingredients/agent-policy/AgentInstructions.groovy',
          'ingredients/git/Git.groovy',
          'ingredients/skills/Skills.groovy',
          'ingredients/the-cook/TheCook.groovy',
          'recipes/agentic/codex/CodexFiles.groovy',
          'recipes/agentic/claude/ClaudeFiles.groovy'
        ].each { book.type(it) }
        def kitchen = new Kitchen(root)
        book.recipe('cookbook', kitchen, [policy: path, 'skills-home': skills.path]).mount()
        def composed = kitchen.render()
        assert composed.applications.keySet().containsAll(direct.keySet())
        assert composed.applications['agentic.permissions/codex']*.path*.value ==
        ['.codex/config.toml', '.codex/rules/cookbook.rules']
        assert composed.applications['agentic.permissions/claude']*.path*.value ==
        ['.claude/settings.json', 'CLAUDE.md']
      }
    } finally {
      root.deleteDir()
    }
  }
}
