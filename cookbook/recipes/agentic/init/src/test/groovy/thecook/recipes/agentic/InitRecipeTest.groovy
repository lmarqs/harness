package thecook.recipes.agentic

import groovy.json.JsonSlurper
import java.nio.file.Files
import org.junit.Test
import thecook.composition.Kitchen
import thecook.cookbook.Cookbook
import thecook.groovy.NativePlanning

class InitRecipeTest {
  private void workspace(Closure body) {
    def root = Files.createTempDirectory('init-recipe-').toFile()
    try {
      body(root, new Cookbook(new File('../../../')))
    } finally {
      root.deleteDir()
    }
  }

  private void apply(File root, Cookbook book, List harnesses) {
    def kitchen = new Kitchen(root)
    book.recipe('agentic/init', kitchen, [harness: harnesses]).mount()
    kitchen.plan().apply()
  }

  private void permission(File root, Cookbook book, String policy, List harnesses) {
    def kitchen = new Kitchen(root)
    book.recipe('agentic/permission', kitchen, [policy: '.agents/' + policy, harness: harnesses]).mount()
    kitchen.plan().apply()
  }

  private File policy(File root, String name, String command) {
    def source = new File(root, '.agents/' + name)
    source.parentFile.mkdirs()
    source.text = "version: 1\ncommands:\n  allow:\n    - [${command}]\n"
    source
  }

  @Test void freshSetupSeedsMinimalPolicyAndRerunIsStable() {
    workspace { root, book ->
      apply(root, book, ['codex', 'claude'])
      assert new File(root, '.agents/policy.yaml').text == 'version: 1\ninstructions: []\ncommands: {}\n'
      assert new File(root, 'AGENTS.md').text == '# Agent instructions\n'
      assert new File(root, 'CLAUDE.md').text.contains('@AGENTS.md')
      assert new File(root, '.codex/config.toml').text.contains('sandbox_mode = "workspace-write"')
      assert new JsonSlurper().parse(new File(root, '.claude/settings.json')).permissions.allow == []
      assert !new File(root, 'cook.groovy').exists()
      def kitchen = new Kitchen(root)
      book.recipe('agentic/init', kitchen, [harness: ['codex', 'claude']]).mount()
      assert kitchen.plan().changes.empty
      permission(root, book, 'policy.yaml', ['claude'])
    }
  }

  @Test void existingSeparatePoliciesSurviveInitAndMissingStarterPolicy() {
    workspace { root, book ->
      policy(root, 'interactive.yaml', 'mise, run, check')
      policy(root, 'unattended.yaml', 'git, status')
      permission(root, book, 'interactive.yaml', ['claude'])
      permission(root, book, 'unattended.yaml', ['codex'])
      def claude = new File(root, '.claude/settings.json').bytes
      def codex = new File(root, '.codex/rules/cookbook.rules').bytes
      apply(root, book, ['claude', 'codex'])
      assert new File(root, '.claude/settings.json').bytes == claude
      assert new File(root, '.codex/rules/cookbook.rules').bytes == codex
      assert !new File(root, '.agents/policy.yaml').exists()
      assert new File(root, 'CLAUDE.md').text.contains('@AGENTS.md')
    }
  }

  @Test void addingCodexPreservesClaudeAndExistingInstructions() {
    workspace { root, book ->
      policy(root, 'interactive.yaml', 'mise, run, check')
      permission(root, book, 'interactive.yaml', ['claude'])
      def instructions = new File(root, 'AGENTS.md')
      instructions.text = '# Team instructions\nDo the checks.\n'
      def claude = new File(root, '.claude/settings.json').bytes
      apply(root, book, ['codex'])
      assert instructions.text == '# Team instructions\nDo the checks.\n'
      assert new File(root, '.claude/settings.json').bytes == claude
      assert new File(root, '.agents/policy.yaml').exists()
      assert new File(root, '.codex/config.toml').exists()
    }
  }

  @Test void emptyStarterPolicyFailsBeforeAnySeedOrRender() {
    workspace { root, book ->
      def source = new File(root, '.agents/policy.yaml')
      source.parentFile.mkdirs()
      source.text = ''
      try {
        apply(root, book, ['claude', 'codex'])
        assert false: 'Empty policy accepted'
      } catch (IllegalArgumentException expected) {
        assert expected.message.contains('.agents/policy.yaml')
        assert expected.message.contains('version: 1')
      }
      assert !new File(root, 'AGENTS.md').exists()
      assert !new File(root, 'CLAUDE.md').exists()
      assert !new File(root, '.cook').exists()
      assert !new File(root, '.codex').exists()
      assert !new File(root, '.claude').exists()
    }
  }

  @Test void removingSelectedHarnessLeavesOtherHarness() {
    workspace { root, book ->
      apply(root, book, ['codex', 'claude'])
      def rules = new File(root, '.codex/rules/cookbook.rules').text
      def script = new File(root, 'cookbook/recipes/agentic/init.groovy')
      new NativePlanning().remove(root.toPath(), script.toPath(), ['claude']).apply()
      assert !new File(root, '.claude/settings.json').exists()
      assert !new File(root, 'CLAUDE.md').exists()
      assert new File(root, '.codex/rules/cookbook.rules').text == rules
      assert new JsonSlurper().parse(new File(root, '.cook/state.json')).applications.containsKey('agentic.permissions/codex')
    }
  }
}
