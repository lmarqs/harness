package thecook.recipes.agentic

import groovy.json.JsonSlurper
import java.nio.file.Files
import org.junit.Test
import thecook.composition.Kitchen
import thecook.cookbook.Cookbook
import thecook.groovy.NativePlanning
import thecook.model.RecipeArguments

class WorkflowFixtureTest {
  private final File fixtures = new File('src/test/resources/fixtures/workflow')
  private final Cookbook book = new Cookbook(new File('../../../'))

  private void workspace(String scenario, Closure body) {
    File root = Files.createTempDirectory('agentic-workflow-').toFile()
    try {
      copyTree(new File(fixtures, scenario + '/initial'), root)
      File links = new File(fixtures, scenario + '/initial.links')
      if (links.exists()) {
        links.readLines().findAll { it }.each { line ->
          List parts = line.split(' -> ', 2).toList()
          link(root, parts[0], parts[1])
        }
      }
      body(root)
    } finally {
      root.deleteDir()
    }
  }

  private static void copyTree(File source, File destination) {
    if (!source.exists()) {
      return
    }
    source.eachFileRecurse { file ->
      String relative = source.toPath().relativize(file.toPath()).toString().replace('__dot__', '.')
      File target = new File(destination, relative)
      if (file.isDirectory()) {
        target.mkdirs()
      } else {
        target.parentFile.mkdirs()
        target.text = file.getText('UTF-8').replace('${REPO}', destination.canonicalPath)
      }
    }
  }

  private static void link(File root, String path, String target) {
    File destination = new File(root, path)
    destination.parentFile.mkdirs()
    Files.createSymbolicLink(destination.toPath(), new File(target).toPath())
  }

  private void invoke(File root, String recipe, List harnesses, String policy = null, boolean adopt = false) {
    Kitchen kitchen = new Kitchen(root)
    Map options = [harness: harnesses]
    if (policy != null) {
      options.policy = '.agents/' + policy
    }
    if (adopt) {
      options.adopt = 'true'
    }
    book.recipe('agentic/' + recipe, kitchen, options).mount()
    kitchen.plan().apply()
  }

  private void remove(File root, String recipe, List harnesses) {
    def selected = RecipeArguments.removal(book.declaration('agentic/' + recipe), [harness: harnesses])
    new NativePlanning().remove(root.toPath(), selected).apply()
  }

  private static Map state(File root) {
    new JsonSlurper().parse(new File(root, '.cook/state.json')) as Map
  }

  private static Map snapshot(File root) {
    Map result = [:]
    root.toPath().toFile().eachFileRecurse { file ->
      String path = root.toPath().relativize(file.toPath()).toString().replace('\\', '/')
      if (Files.isSymbolicLink(file.toPath())) {
        result[path] = 'link:' + Files.readSymbolicLink(file.toPath())
      } else if (file.isFile()) {
        result[path] = file.bytes.encodeBase64().toString()
      }
    }
    result.sort()
  }

  private void assertTree(File root, String scenario, String step) {
    File expected = new File(fixtures, scenario + '/expected/' + step + '.paths')
    assert snapshot(root).keySet() == expected.readLines().findAll { it }.toSet()
    assert !new File(root, 'cook.groovy').exists()
  }

  private static void assertOwners(File root, Set expected) {
    Map ownership = state(root)
    assert ownership.applications.keySet() == expected +
    ['agentic.context/default', 'agentic.permissions/default'] as Set
    assert ownership.files.keySet().containsAll(ownership.applications.values().collectMany { it.keySet() })
    assert !ownership.files.containsKey('AGENTS.md')
  }

  private static void assertManaged(File root, boolean claude, boolean codex) {
    Map ownership = state(root)
    Map applications = ownership.applications
    assert (applications['agentic.context/claude'] ?: [:]).containsKey('CLAUDE.md') == claude
    assert (applications['agentic.permissions/claude'] ?: [:]).containsKey('.claude/settings.json') == claude
    assert (applications['agentic.permissions/codex'] ?: [:]).containsKey('.codex/config.toml') == codex
    assert (applications['agentic.permissions/codex'] ?: [:]).containsKey('.codex/rules/cookbook.rules') == codex
    if (claude) {
      assert new File(root, 'CLAUDE.md').text.contains('# cook:begin claude-context/instructions')
      assert ownership.files['CLAUDE.md'].regions.containsKey('claude-context/instructions')
      assert ownership.files['.claude/settings.json'].regions.keySet().containsAll([
        'cookbook.claude/permissions/defaultMode',
        'cookbook.claude/permissions/allow',
        'cookbook.claude/permissions/ask',
        'cookbook.claude/permissions/deny'
      ])
    }
    if (codex) {
      assert new File(root, '.codex/rules/cookbook.rules').text.contains('# cook:begin cookbook.codex/commands')
      assert ownership.files['.codex/config.toml'].regions.keySet().containsAll([
        'cookbook.codex/sandbox_mode',
        'cookbook.codex/approval_policy',
        'cookbook.codex/sandbox_workspace_write/network_access'
      ])
    }
  }

  @Test void trackedFreshCloneRequiresExplicitAdoptionAndThenRecooksUnchanged() {
    workspace('tracked') { root ->
      Map tracked = snapshot(root)
      assert !new File(root, '.cook').exists()
      try {
        invoke(root, 'context', ['claude', 'codex'])
        assert false: 'Unmanaged context block accepted'
      } catch (IllegalStateException expected) {
        assert expected.message.contains('Unmanaged region already exists')
        assert snapshot(root) == tracked
      }
      invoke(root, 'context', ['claude', 'codex'], null, true)
      Map beforeClaude = snapshot(root)
      try {
        invoke(root, 'permission', ['claude'], 'policy.interactive.yaml')
        assert false: 'Unmanaged Claude permissions accepted'
      } catch (IllegalStateException expected) {
        assert expected.message.contains('Unmanaged region already exists')
        assert snapshot(root) == beforeClaude
      }
      invoke(root, 'permission', ['claude'], 'policy.interactive.yaml', true)
      Map beforeCodex = snapshot(root)
      try {
        invoke(root, 'permission', ['codex'], 'policy.unattended.yaml')
        assert false: 'Unmanaged Codex rules accepted'
      } catch (IllegalStateException expected) {
        assert expected.message.contains('Unmanaged region already exists')
        assert snapshot(root) == beforeCodex
      }
      invoke(root, 'permission', ['codex'], 'policy.unattended.yaml', true)
      Map cooked = snapshot(root)
      assert cooked.subMap(tracked.keySet()) == tracked
      assert state(root).applications.keySet() == [
        'agentic.context/default',
        'agentic.context/claude',
        'agentic.context/codex',
        'agentic.permissions/default',
        'agentic.permissions/claude',
        'agentic.permissions/codex'
      ] as Set
      assertManaged(root, true, true)
      assert state(root).provenance['agentic.context/claude'].recipe == 'recipes/agentic/context/recipe.groovy'
      assert state(root).provenance['agentic.context/codex'].recipe == 'recipes/agentic/context/recipe.groovy'
      assert state(root).provenance['agentic.permissions/claude'].inputs.policy == '.agents/policy.interactive.yaml'
      assert state(root).provenance['agentic.permissions/codex'].inputs.policy == '.agents/policy.unattended.yaml'
      invoke(root, 'context', ['claude', 'codex'])
      invoke(root, 'permission', ['claude'], 'policy.interactive.yaml')
      invoke(root, 'permission', ['codex'], 'policy.unattended.yaml')
      assert snapshot(root) == cooked
      File rules = new File(root, '.codex/rules/cookbook.rules')
      rules.text = rules.text.replace('"status"', '"drift"')
      Map drifted = snapshot(root)
      try {
        invoke(root, 'permission', ['codex'], 'policy.unattended.yaml', true)
        assert false: 'Adoption bypassed managed drift'
      } catch (IllegalStateException expected) {
        assert expected.message.contains('Edited managed region')
        assert snapshot(root) == drifted
      }
    }
  }

  @Test void freshInitDirectPoliciesEditsRerunsAndSelectedRemoval() {
    workspace('fresh') { root ->
      invoke(root, 'init', ['claude', 'codex'])
      assertTree(root, 'fresh', 'init')
      assert new File(root, '.agents/policy.yaml').text == 'version: 1\ninstructions: []\ncommands: {}\n'
      assert new File(root, 'AGENTS.md').text == '# Agent instructions\n'
      assert new File(root, 'CLAUDE.md').text.contains('@AGENTS.md')
      assert new JsonSlurper().parse(new File(root, '.claude/settings.json')).permissions.defaultMode == 'acceptEdits'
      assert new File(root, '.codex/config.toml').text.contains('sandbox_mode = "workspace-write"')
      assertOwners(root, [
        'agentic.init/default',
        'agentic.context/claude',
        'agentic.context/codex',
        'agentic.permissions/claude',
        'agentic.permissions/codex'
      ] as Set)
      assertManaged(root, true, true)

      new File(root, '.agents/interactive.yaml').text = new File(fixtures, 'fresh/sources/interactive.yaml').text
      new File(root, '.agents/unattended.yaml').text = new File(fixtures, 'fresh/sources/unattended.yaml').text
      invoke(root, 'permission', ['claude'], 'interactive.yaml')
      invoke(root, 'permission', ['codex'], 'unattended.yaml')
      assertTree(root, 'fresh', 'policies')
      assert new JsonSlurper().parse(new File(root, '.claude/settings.json')).permissions.allow == ['Bash(mise run check *)']
      assert new File(root, '.codex/rules/cookbook.rules').text.contains('"git","status"')
      assertOwners(root, [
        'agentic.init/default',
        'agentic.context/claude',
        'agentic.context/codex',
        'agentic.permissions/claude',
        'agentic.permissions/codex'
      ] as Set)
      assertManaged(root, true, true)
      Map before = snapshot(root)
      invoke(root, 'permission', ['claude'], 'interactive.yaml')
      invoke(root, 'permission', ['codex'], 'unattended.yaml')
      assert snapshot(root) == before

      File interactive = new File(root, '.agents/interactive.yaml')
      interactive.text = interactive.text.replace('check', 'test')
      invoke(root, 'permission', ['claude'], 'interactive.yaml')
      assertTree(root, 'fresh', 'policies')
      assert new File(root, '.claude/settings.json').text.contains('mise run test')
      assert new File(root, '.codex/rules/cookbook.rules').text.contains('"git","status"')
      assertManaged(root, true, true)
      remove(root, 'init', ['claude'])
      assertTree(root, 'fresh', 'remove-claude')
      assert !new File(root, 'CLAUDE.md').exists()
      assert !new File(root, '.claude/settings.json').exists()
      assert new File(root, '.codex/config.toml').exists()
      assertOwners(root, ['agentic.init/default', 'agentic.context/codex', 'agentic.permissions/codex'] as Set)
      assertManaged(root, false, true)
    }
  }

  @Test void combinedSelectionMatchesTwoSingleHarnessInvocations() {
    workspace('fresh') { combined ->
      workspace('fresh') { separate ->
        new File(combined, '.agents').mkdirs()
        new File(separate, '.agents').mkdirs()
        new File(combined, '.agents/policy.yaml').text = new File(fixtures, 'fresh/sources/interactive.yaml').text
        new File(separate, '.agents/policy.yaml').text = new File(fixtures, 'fresh/sources/interactive.yaml').text
        invoke(combined, 'init', ['claude', 'codex'])
        invoke(separate, 'init', ['claude'])
        invoke(separate, 'init', ['codex'])
        assert snapshot(combined).findAll { path, value -> path != '.cook/state.json' } ==
        snapshot(separate).findAll { path, value -> path != '.cook/state.json' }
        assert state(combined).applications == state(separate).applications
      }
    }
  }

  @Test void brownfieldSurvivesPolicyMovesFailuresAndSelectedRemoval() {
    workspace('brownfield') { root ->
      Map baseline = snapshot(root)
      invoke(root, 'init', ['claude', 'codex'])
      assertTree(root, 'brownfield', 'init')
      assert new JsonSlurper().parse(new File(root, '.claude/settings.json')).hooks.PreToolUse == [[matcher: 'Bash', hooks: [[type: 'command', command: 'echo handwritten']]]]
      assert new JsonSlurper().parse(new File(root, '.claude/settings.json')).model == 'manual'
      assert new File(root, 'AGENTS.md').text == '# Team instructions\nKeep this file.\n'
      assert new File(root, 'CLAUDE.md').text.startsWith('Handwritten guidance.\n')
      assert new File(root, '.codex/config.toml').text.contains('model = "manual"')
      assert new File(root, '.codex/hooks.json').text == '{"after_run":[{"command":"echo handwritten"}]}\n'
      assert new File(root, '.gitignore').text == '# team ignore\n# cook:begin git/ignore\nlegacy/\n# cook:end git/ignore\n'
      assert state(root).legacy['.gitignore'] == ['git/ignore']
      assertManaged(root, true, true)
      assert snapshot(root)['.claude/settings.local.json'] == 'link:../local-settings.json'

      invoke(root, 'permission', ['claude'], 'interactive.yaml')
      invoke(root, 'permission', ['codex'], 'unattended.yaml')
      assertTree(root, 'brownfield', 'policies')
      assert new File(root, '.claude/settings.json').text.contains('mise run check')
      assert new File(root, '.codex/rules/cookbook.rules').text.contains('"git","status"')
      assertManaged(root, true, true)
      File moved = new File(root, '.agents/interactive-renamed.yaml')
      Files.move(new File(root, '.agents/interactive.yaml').toPath(), moved.toPath())
      invoke(root, 'permission', ['claude'], moved.name)
      assertTree(root, 'brownfield', 'renamed')
      assert new File(root, '.claude/settings.json').text.contains('mise run check')
      assertOwners(root, [
        'agentic.init/default',
        'agentic.context/claude',
        'agentic.context/codex',
        'agentic.permissions/claude',
        'agentic.permissions/codex'
      ] as Set)
      assertManaged(root, true, true)

      File bad = new File(root, '.agents/invalid.yaml')
      bad.text = 'version: 2\n'
      Map before = snapshot(root)
      try {
        invoke(root, 'permission', ['claude', 'codex'], bad.name)
        assert false: 'Malformed policy accepted'
      } catch (IllegalArgumentException expected) {
        assert snapshot(root) == before
      }
      File rules = new File(root, '.codex/rules/cookbook.rules')
      rules.text = rules.text.replace('"status"', '"drift"')
      before = snapshot(root)
      try {
        invoke(root, 'permission', ['claude', 'codex'], moved.name)
        assert false: 'Managed drift accepted'
      } catch (IllegalStateException expected) {
        assert snapshot(root) == before
      }
      rules.text = rules.text.replace('"drift"', '"status"')
      remove(root, 'init', ['claude'])
      assertTree(root, 'brownfield', 'remove-claude')
      assert new File(root, '.gitignore').text.contains('legacy/')
      assert snapshot(root)['.claude/settings.local.json'] == baseline['.claude/settings.local.json']
      Map claude = new JsonSlurper().parse(new File(root, '.claude/settings.json')) as Map
      assert claude.model == 'manual'
      assert claude.hooks.PreToolUse[0].hooks[0].command == 'echo handwritten'
      assert !claude.containsKey('permissions')
      assert new File(root, 'CLAUDE.md').text == 'Handwritten guidance.\n'
      assert new File(root, 'AGENTS.md').text == '# Team instructions\nKeep this file.\n'
      assert new File(root, '.codex/config.toml').text.contains('model = "manual"')
      assert new File(root, '.codex/hooks.json').text == '{"after_run":[{"command":"echo handwritten"}]}\n'
      assert state(root).legacy['.gitignore'] == ['git/ignore']
      assertManaged(root, false, true)
    }
  }

  @Test void conflictOnClaudeLeavesCodexAndStateUntouched() {
    workspace('brownfield') { root ->
      File settings = new File(root, '.claude/settings.json')
      settings.text = '{"model":"manual","permissions":{"defaultMode":"plan"}}\n'
      Map before = snapshot(root)
      try {
        invoke(root, 'permission', ['claude', 'codex'], 'interactive.yaml')
        assert false: 'Unmanaged Claude key accepted'
      } catch (IllegalStateException expected) {
        assert expected.message.contains('Unmanaged region already exists')
      }
      assert snapshot(root) == before
    }
  }
}
