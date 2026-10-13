package thecook.checks

import org.junit.Test
import java.nio.file.Files
import thecook.composition.Kitchen
import thecook.cookbook.Cookbook

class ApplicationScopeTest {
  @Test void selectedEmptyApplicationWithdrawsOnlyItsOwnRegions() {
    def root = Files.createTempDirectory('cook-applications-').toFile()
    try {
      def kitchen = new Kitchen(root)
      def claude = kitchen.recipe('claude'); claude.application('agentic.permissions/claude')
      claude.output('settings.json', [format: 'json']) { key(['claude']) { 'on' } }
      claude.mount()
      def codex = kitchen.recipe('codex'); codex.application('agentic.permissions/codex')
      codex.output('settings.json', [format: 'json']) { key(['codex']) { 'on' } }
      codex.mount()
      kitchen.plan().apply()
      def selected = new Kitchen(root).recipe('claude'); selected.application('agentic.permissions/claude')
      selected.mount()
      selected.kitchen.plan().apply()
      assert new File(root, 'settings.json').text.contains('codex')
      assert !new File(root, 'settings.json').text.contains('claude')
      assert selected.kitchen.plan().changes.empty
    } finally { root.deleteDir() }
  }

  @Test void duplicateApplicationWithDifferentInputsFailsBeforeWriting() {
    def root = Files.createTempDirectory('cook-duplicate-app-').toFile()
    try {
      def kitchen = new Kitchen(root)
      def first = kitchen.recipe('first'); first.application('agentic.permissions/claude')
      first.output('first.txt', [owns: true]) { text { 'first' } }; first.mount()
      def second = kitchen.recipe('second'); second.application('agentic.permissions/claude')
      second.output('second.txt', [owns: true]) { text { 'second' } }; second.mount()
      try { kitchen.plan(); assert false: 'Duplicate application accepted' }
      catch (IllegalArgumentException expected) { assert expected.message.contains('Duplicate application') }
      assert !new File(root, '.cook/state.json').exists()
    } finally { root.deleteDir() }
  }

  @Test void childIdentitiesAreStableAcrossSourceMovesPolicyPathsAndComposition() {
    def root = Files.createTempDirectory('cook-app-identity-').toFile()
    try {
      def source = new File('../cookbook')
      def moved = new File(root, 'moved-cookbook')
      ['ingredients/agent-policy/Policy.groovy',
       'recipes/agentic/codex/CodexFiles.groovy',
       'recipes/agentic/claude/ClaudeFiles.groovy'].each { path ->
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
        ['ingredients/agent-policy/Policy.groovy', 'ingredients/agent-policy/PolicyFile.groovy',
         'ingredients/agent-policy/AgentInstructions.groovy', 'ingredients/git/Git.groovy',
         'ingredients/skills/Skills.groovy', 'ingredients/the-cook/TheCook.groovy',
         'recipes/agentic/codex/CodexFiles.groovy', 'recipes/agentic/claude/ClaudeFiles.groovy'].each { book.type(it) }
        def kitchen = new Kitchen(root)
        book.recipe('cookbook', kitchen, [policy: path, 'skills-home': skills.path]).mount()
        def composed = kitchen.render()
        assert composed.applications.keySet().containsAll(direct.keySet())
        assert composed.applications['agentic.permissions/codex']*.path*.value ==
            ['.codex/config.toml', '.codex/rules/cookbook.rules']
        assert composed.applications['agentic.permissions/claude']*.path*.value ==
            ['.claude/settings.json', 'CLAUDE.md']
      }
    } finally { root.deleteDir() }
  }
}
