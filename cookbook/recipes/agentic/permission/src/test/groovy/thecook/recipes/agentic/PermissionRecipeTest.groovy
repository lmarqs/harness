package thecook.recipes.agentic

import groovy.json.JsonSlurper
import java.nio.file.Files
import org.junit.Test
import thecook.cookbook.Cookbook
import thecook.composition.Kitchen
import thecook.groovy.NativePlanning
import thecook.model.KeyContribution

class PermissionRecipeTest {
  private void workspace(Closure body) {
    def root = Files.createTempDirectory('permission-recipe-').toFile()
    try {
      def book = new Cookbook(new File('../../../'))
      body(root, book)
    } finally {
      root.deleteDir()
    }
  }

  private File policy(File root, String name, String command) {
    def source = new File(root, '.agents/' + name)
    source.parentFile.mkdirs()
    source.text = "version: 1\ncommands:\n  allow:\n    - [${command}]\n"
    source
  }

  private void apply(File root, Cookbook book, String name, List harnesses, boolean adopt = false) {
    def kitchen = new Kitchen(root)
    def options = [policy: '.agents/' + name, harness: harnesses]
    if (adopt) {
      options.adopt = 'true'
    }
    book.recipe('agentic/permission', kitchen, options).mount()
    kitchen.plan().apply()
  }

  @Test void separatePoliciesUpdateOnlySelectedApplicationsAndPreserveManualSettings() {
    workspace { root, book ->
      def interactive = policy(root, 'policy.interactive.yaml', 'mise, run, check')
      policy(root, 'policy.unattended.yaml', 'git, status')
      def claude = new File(root, '.claude/settings.json'); claude.parentFile.mkdirs()
      claude.text = '{"model":"manual"}\n'
      def codex = new File(root, '.codex/config.toml'); codex.parentFile.mkdirs()
      codex.text = 'model = "manual"\n'
      apply(root, book, interactive.name, ['claude'])
      def beforeClaude = claude.text
      def beforeClaudeOwner = new JsonSlurper().parse(new File(root, '.cook/state.json')).applications['agentic.permissions/claude']
      apply(root, book, 'policy.unattended.yaml', ['codex'])
      def selected = new JsonSlurper().parse(new File(root, '.cook/state.json')).provenance
      assert selected['agentic.permissions/claude'].inputs.policy == '.agents/policy.interactive.yaml'
      assert selected['agentic.permissions/codex'].inputs.policy == '.agents/policy.unattended.yaml'
      assert claude.text == beforeClaude
      assert new JsonSlurper().parse(new File(root, '.cook/state.json')).applications['agentic.permissions/claude'] == beforeClaudeOwner
      assert new JsonSlurper().parse(claude).model == 'manual'
      assert codex.text.contains('model = "manual"')
      assert codex.text.contains('sandbox_mode = "workspace-write"')
      assert new File(root, '.codex/rules/cookbook.rules').text.contains('"git","status"')
      assert !new File(root, 'CLAUDE.md').exists()
      assert !new File(root, 'AGENTS.md').exists()
      assert !new File(root, '.claude/skills').exists()
      assert !new File(root, '.codex/hooks.json').exists()
      def beforeCodex = codex.text
      interactive.text = interactive.text.replace('check', 'test')
      apply(root, book, interactive.name, ['claude'])
      selected = new JsonSlurper().parse(new File(root, '.cook/state.json')).provenance
      assert selected['agentic.permissions/claude'].inputs.policy == '.agents/policy.interactive.yaml'
      assert selected['agentic.permissions/codex'].inputs.policy == '.agents/policy.unattended.yaml'
      assert claude.text.contains('mise run test')
      assert codex.text == beforeCodex
      assert new File(root, '.codex/rules/cookbook.rules').text.contains('"status"')
      policy(root, 'policy.renamed.yaml', 'mise, run, test')
      apply(root, book, 'policy.renamed.yaml', ['claude'])
      selected = new JsonSlurper().parse(new File(root, '.cook/state.json')).provenance
      assert selected['agentic.permissions/claude'].inputs.policy == '.agents/policy.renamed.yaml'
      assert selected['agentic.permissions/codex'].inputs.policy == '.agents/policy.unattended.yaml'
      apply(root, book, 'policy.renamed.yaml', ['claude'])
      def kitchen = new Kitchen(root)
      book.recipe('agentic/permission', kitchen, [policy: '.agents/policy.renamed.yaml', harness: ['claude']]).mount()
      assert kitchen.plan().changes.empty
    }
  }

  @Test void changedPolicyFilenameKeepsOwnershipAndBothHarnessesApplyTogether() {
    workspace { root, book ->
      policy(root, 'first.yaml', 'git, status')
      policy(root, 'second.yaml', 'mise, run, check')
      apply(root, book, 'first.yaml', ['claude', 'codex'])
      apply(root, book, 'second.yaml', ['claude', 'codex'])
      assert new File(root, '.claude/settings.json').text.contains('mise run check')
      assert new File(root, '.codex/rules/cookbook.rules').text.contains('"mise","run","check"')
      def state = new JsonSlurper().parse(new File(root, '.cook/state.json'))
      assert state.applications.keySet().containsAll(['agentic.permissions/claude', 'agentic.permissions/codex'])
      assert state.applications.keySet().size() == 3
      def rules = new File(root, '.codex/rules/cookbook.rules')
      rules.text = rules.text.replace('"check"', '"changed"')
      def beforeClaude = new File(root, '.claude/settings.json').text
      try {
        apply(root, book, 'first.yaml', ['claude', 'codex'])
        assert false: 'Managed drift accepted'
      } catch (IllegalStateException expected) {
        assert expected.message.contains('Edited managed region')
      }
      assert new File(root, '.claude/settings.json').text == beforeClaude
    }
  }

  @Test void invalidPolicyAndConflictingKeysLeaveRepositoryUntouchedUntilAdopted() {
    workspace { root, book ->
      def source = policy(root, 'policy.yaml', 'git, status')
      def claude = new File(root, '.claude/settings.json'); claude.parentFile.mkdirs()
      claude.text = '{"permissions":{"defaultMode":"plan"},"model":"manual"}\n'
      for (invalid in ['', 'version: 2\n', 'version: 1\ncommands: {allow: [[git, "*"]]}\n']) {
        source.text = invalid
        try {
          apply(root, book, source.name, ['claude', 'codex'])
          assert false: 'Invalid policy accepted'
        } catch (Exception expected) {
          assert !new File(root, '.cook').exists()
        }
      }
      source.delete()
      try {
        apply(root, book, source.name, ['claude'])
        assert false: 'Missing policy accepted'
      } catch (IllegalArgumentException expected) {
        assert expected.message.contains('Policy file not found')
      }
      source = policy(root, 'policy.yaml', 'git, status')
      try {
        apply(root, book, source.name, ['claude'])
        assert false: 'Unmanaged key replaced'
      } catch (IllegalStateException expected) {
        assert expected.message.contains('Unmanaged region already exists')
      }
      assert claude.text.contains('"plan"')
      apply(root, book, source.name, ['claude'], true)
      assert new JsonSlurper().parse(claude).permissions.defaultMode == 'acceptEdits'
      assert new JsonSlurper().parse(claude).model == 'manual'
    }
  }

  @Test void removingClaudeLeavesCodexAndItsOwnershipIntact() {
    workspace { root, book ->
      policy(root, 'policy.yaml', 'git, status')
      apply(root, book, 'policy.yaml', ['claude', 'codex'])
      def rules = new File(root, '.codex/rules/cookbook.rules').text
      new NativePlanning().remove(root.toPath(), ['agentic.permissions/claude'] as Set).apply()
      assert !new File(root, '.claude/settings.json').exists()
      assert new File(root, '.codex/rules/cookbook.rules').text == rules
      assert new JsonSlurper().parse(new File(root, '.cook/state.json')).applications.containsKey('agentic.permissions/codex')
    }
  }

  @Test void anotherApplicationOwningTheSamePermissionKeyBlocksSelection() {
    workspace { root, book ->
      policy(root, 'policy.yaml', 'git, status')
      def kitchen = new Kitchen(root)
      def other = kitchen.recipe('other-claude')
      other.application('other/claude')
      def renderer = book.type('recipes/agentic/claude/ClaudeFiles.groovy').newInstance('other-claude')
      renderer.settings.add(new KeyContribution(['permissions', 'defaultMode'], { 'plan' }, false))
      other.contribute { renderer }
      other.mount()
      kitchen.plan().apply()
      def before = new File(root, '.claude/settings.json').text
      try {
        apply(root, book, 'policy.yaml', ['claude', 'codex'], true)
        assert false: 'Cross-application conflict accepted'
      } catch (IllegalArgumentException expected) {
        assert expected.message.contains('Application contribution already selected elsewhere')
      }
      assert new File(root, '.claude/settings.json').text == before
      assert !new File(root, '.codex/config.toml').exists()
    }
  }
}
