package thecook.checks

import org.junit.Test
import java.nio.file.Files
import thecook.composition.Kitchen
import thecook.cookbook.Cookbook
import thecook.filesystem.Repository
import thecook.filesystem.TargetPath
import thecook.model.*
import thecook.reconciliation.Reconciler
import thecook.rendering.RenderTree

class AgentRecipesTest {
  @Test void artifactBackedPolicyKeepsItsClassIdentityAcrossLegacyAndTypedRecipes() {
    def root = Files.createTempDirectory('cook-policy-artifact-').toFile()
    try {
      def policySource = new File('../cookbook/ingredients/agent-policy/Policy.groovy')
      def compiled = new File(root, 'classes')
      def config = new org.codehaus.groovy.control.CompilerConfiguration(targetDirectory: compiled)
      def compiler = new org.codehaus.groovy.control.CompilationUnit(config)
      compiler.addSource(policySource); compiler.compile()
      def artifact = new File(root, 'policy.jar')
      artifact.withOutputStream { stream ->
        def jar = new java.util.jar.JarOutputStream(stream)
        compiled.eachFileRecurse { file ->
          if (!file.isFile()) return
            jar.putNextEntry(new java.util.jar.JarEntry(compiled.toPath().relativize(file.toPath()).toString()))
          jar.write(file.bytes); jar.closeEntry()
        }
        jar.putNextEntry(new java.util.jar.JarEntry('META-INF/the-cook/ingredients/agent-policy/Policy.groovy'))
        jar.write(policySource.bytes); jar.closeEntry(); jar.close()
      }
      def book = new Cookbook(new File('../cookbook'), [artifact])
      def policyType = book.classes.loadClass('thecook.cookbook.ingredients.agentpolicy.Policy')
      def policy = policyType.newInstance([], [])
      def destination = new File(root, 'repository'); destination.mkdirs()
      def kitchen = new Kitchen(destination)
      book.recipe('agentic/codex', kitchen)
      book.recipe('agentic/claude', kitchen)
      def target = book.type('recipes/agentic/claude/ClaudeFiles.groovy').newInstance(policy)
      new Reconciler().plan(new Repository(destination), RenderTree.from(new Batch('policy').include(target))).apply()
      assert new File(destination, 'CLAUDE.md').text.contains('@AGENTS.md')
      assert book.type('ingredients/agent-policy/Policy.groovy').is(policyType)
    } finally {
      root.deleteDir()
    }
  }

  @Test void recipesRenderFromPackagedSourcesWithoutACookbookCheckout() {
    def root = Files.createTempDirectory('cook-recipe-artifacts-').toFile()
    try {
      def artifact = new File(root, 'recipes.jar')
      artifact.withOutputStream { stream ->
        def jar = new java.util.jar.JarOutputStream(stream)
        def sources = ['ingredients/agent-policy/Policy.groovy']
        ['codex', 'claude'].each { name ->
          sources.add("recipes/agentic/${name}/recipe.groovy".toString())
          sources.add("recipes/agentic/${name}/${name.capitalize()}Files.groovy".toString())
        }
        sources.each { path ->
          jar.putNextEntry(new java.util.jar.JarEntry('META-INF/the-cook/' + path))
          jar.write(new File('../cookbook', path).bytes); jar.closeEntry()
        }
        jar.close()
      }
      def book = new Cookbook(new File(root, 'absent'), [artifact])
      def destination = new File(root, 'repository')
      def kitchen = new Kitchen(destination)
      book.recipe('agentic/codex', kitchen, [config: [[model: 'example'], [model: 'example']]]).mount()
      def claude = book.recipe('agentic/claude', kitchen, [settings: [permissions: [allow: ['Bash(check *)']]]])
      claude.mount()
      kitchen.plan().apply()
      assert new File(destination, '.codex/config.toml').text.contains('example')
      assert new File(destination, '.claude/settings.json').text.contains('Bash(check *)')
      assert kitchen.plan().changes.empty
      def before = ModelChecks.snapshot(destination)
      claude.settings.from([[permissions: [allow: ['conflicting']]]])
      try {
        kitchen.plan(); assert false: 'Conflicting settings were accepted'
      } catch (IllegalArgumentException expected) {
        assert expected.message.contains('Conflicting Claude settings: permissions')
      }
      assert ModelChecks.snapshot(destination) == before
    } finally {
      root.deleteDir()
    }
  }

  private void workspace(Closure body) {
    def root = Files.createTempDirectory('cook-native-targets-').toFile()
    try {
      def book = new Cookbook(new File('../cookbook'))
      book.type('ingredients/agent-policy/Policy.groovy')
      body(root, book)
    } finally {
      root.deleteDir()
    }
  }

  @Test void codexDeclarationPathsShareRenderingIncludingCustomPathsHooksAndWholeRules() {
    workspace { root, book ->
      def target = book.type('recipes/agentic/codex/CodexFiles.groovy').newInstance('selected.codex', true)
      target.configPath = TargetPath.of('agent/config.toml')
      target.hooksPath = TargetPath.of('agent/hooks.json')
      target.instructionsPath = TargetPath.of('instructions.txt')
      target.config.add(new KeyContribution(['model'], { 'selected-model' }, true))
      target.hooks.add(new KeyContribution(['hooks'], { [:] }, true))
      target.instructions = { ['Use project tasks.'] } as Provider
      target.rule('project.rules', { 'custom_rule()\n' } as Provider)
      def config = new File(root, 'agent/config.toml'); config.parentFile.mkdirs()
      def original = 'model = "original" # keep\nmanual = true\n'
      config.text = original
      new Reconciler().plan(new Repository(root), RenderTree.from(new Batch('native').include(target))).apply()
      def kitchen = new Kitchen(root)
      def legacy = book.recipe('agentic/codex', kitchen, [id: 'selected.codex', adopt: true,
        configPath: 'agent/config.toml', hooksPath: 'agent/hooks.json', instructionsPath: 'instructions.txt',
        config: [model: 'selected-model'], hooks: [hooks: [:]], instructions: 'Use project tasks.',
        rules: ['project.rules': 'custom_rule()\n']])
      legacy.mount()
      try { kitchen.plan(); assert false: 'Legacy ownership was claimed' }
      catch (IllegalStateException expected) { assert expected.message.contains('Legacy ownership') }
      new Reconciler().plan(new Repository(root), new RenderTree([])).apply()
      kitchen.plan().apply()
      assert kitchen.plan().changes.empty
      assert new File(root, '.codex/rules/project.rules').text == 'custom_rule()\n'
      assert !new File(root, '.codex/config.toml').exists()
      legacy.unmount()
      def withdraw = kitchen.recipe('withdraw'); withdraw.application('agentic.permissions/codex'); withdraw.mount()
      kitchen.plan().apply()
      assert config.text == original
      assert !new File(root, 'instructions.txt').exists()
      assert !new File(root, '.codex/rules/project.rules').exists()
    }
  }

  @Test void claudeDeclarationPathsPreserveTopLevelOwnershipAndResolveLateInputs() {
    workspace { root, book ->
      def target = book.type('recipes/agentic/claude/ClaudeFiles.groovy').newInstance('selected.claude')
      target.settingsPath = TargetPath.of('agent/settings.json')
      target.instructionsPath = TargetPath.of('instructions.txt')
      target.settings.add(new KeyContribution(['permissions'], { [allow: ['Bash(check *)']] }, true))
      target.instructions = { ['Use project tasks.'] } as Provider
      def settings = new File(root, 'agent/settings.json'); settings.parentFile.mkdirs()
      def original = '{"permissions":{"allow":["original"]},"model":"manual"}\n'
      settings.text = original
      new Reconciler().plan(new Repository(root), RenderTree.from(new Batch('native').include(target))).apply()
      def kitchen = new Kitchen(root)
      def legacy = book.recipe('agentic/claude', kitchen, [id: 'selected.claude', adopt: true,
        settingsPath: 'agent/settings.json', instructionsPath: 'instructions.txt', instructions: 'Use project tasks.'])
      def source = kitchen.ingredient('rules'); source.rules = [allow: ['wrong']]
      source.export('settings') { [[permissions: source.rules]] }
      legacy.settings.from(source.settings)
      source.rules = [allow: ['Bash(check *)']]
      legacy.mount()
      try { kitchen.plan(); assert false: 'Legacy ownership was claimed' }
      catch (IllegalStateException expected) { assert expected.message.contains('Legacy ownership') }
      new Reconciler().plan(new Repository(root), new RenderTree([])).apply()
      kitchen.plan().apply()
      assert kitchen.plan().changes.empty
      assert !new File(root, '.claude/settings.local.json').exists()
      legacy.unmount()
      def withdraw = kitchen.recipe('withdraw'); withdraw.application('agentic.permissions/claude'); withdraw.mount()
      kitchen.plan().apply()
      assert settings.text == original
    }
  }
}
