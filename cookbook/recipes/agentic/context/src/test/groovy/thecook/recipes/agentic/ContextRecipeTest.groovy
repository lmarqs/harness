package thecook.recipes.agentic

import groovy.json.JsonSlurper
import java.nio.file.Files
import org.junit.Test
import thecook.cookbook.Cookbook
import thecook.composition.Kitchen
import thecook.groovy.NativePlanning

class ContextRecipeTest {
  private void workspace(Closure body) {
    def root = Files.createTempDirectory('context-recipe-').toFile()
    try {
      body(root, new Cookbook(new File('../../../')))
    } finally {
      root.deleteDir()
    }
  }

  private void apply(File root, Cookbook book, List harnesses, boolean adopt = false) {
    def kitchen = new Kitchen(root)
    book.recipe('agentic/context', kitchen, [harness: harnesses, adopt: adopt.toString()]).mount()
    kitchen.plan().apply()
  }

  private void remove(File root, List harnesses) {
    def script = new File(root, 'cookbook/recipes/agentic/context.groovy')
    new NativePlanning().remove(root.toPath(), script.toPath(), harnesses).apply()
  }

  @Test void bothInvocationOrdersShareOnlyASeedAndRerunsAreStable() {
    for (order in [['claude', 'codex'], ['codex', 'claude']]) {
      workspace { root, book ->
        apply(root, book, [order[0]])
        apply(root, book, [order[1]])
        assert new File(root, 'AGENTS.md').text == '# Agent instructions\n'
        def claude = new File(root, 'CLAUDE.md')
        assert claude.text.count('@AGENTS.md') == 1
        def state = new JsonSlurper().parse(new File(root, '.cook/state.json'))
        assert state.applications.keySet().containsAll(['agentic.context/claude', 'agentic.context/codex'])
        assert !state.files.containsKey('AGENTS.md')
        def before = claude.bytes
        apply(root, book, order)
        assert claude.bytes == before
        def kitchen = new Kitchen(root)
        book.recipe('agentic/context', kitchen, [harness: order]).mount()
        assert kitchen.plan().changes.empty
      }
    }
  }

  @Test void existingInstructionsIncludingEmptyFileRemainUntouched() {
    for (content in ['', '# Team rules\r\nKeep this.\r\n']) {
      workspace { root, book ->
        def agents = new File(root, 'AGENTS.md')
        agents.bytes = content.getBytes('UTF-8')
        def before = agents.bytes
        def claude = new File(root, 'CLAUDE.md')
        claude.text = 'Handwritten guidance\n'
        apply(root, book, ['claude', 'codex'])
        assert agents.bytes == before
        assert claude.text.startsWith('Handwritten guidance\n')
        assert claude.text.count('@AGENTS.md') == 1
        remove(root, ['claude'])
        assert claude.text == 'Handwritten guidance\n'
        assert agents.bytes == before
        remove(root, ['codex'])
        assert agents.bytes == before
      }
    }
  }

  @Test void unmanagedBlockNeedsExplicitAdoptionAndRemovalRestoresIt() {
    workspace { root, book ->
      def claude = new File(root, 'CLAUDE.md')
      def original = 'Before\n# cook:begin claude-context/instructions\nOld import\n# cook:end claude-context/instructions\nAfter\n'
      claude.text = original
      try {
        apply(root, book, ['claude'])
        assert false: 'Unmanaged block accepted'
      } catch (IllegalStateException expected) {
        assert expected.message.contains('Unmanaged region already exists')
      }
      assert claude.text == original
      apply(root, book, ['claude'], true)
      assert claude.text.contains('@AGENTS.md')
      remove(root, ['claude'])
      assert claude.text == original
      assert new File(root, 'AGENTS.md').exists()
    }
  }

  @Test void managedDriftIsRejectedAndUnrelatedFilesAreNotCreated() {
    workspace { root, book ->
      apply(root, book, ['claude', 'codex'])
      def claude = new File(root, 'CLAUDE.md')
      claude.text = claude.text.replace('@AGENTS.md', '@OTHER.md')
      try {
        apply(root, book, ['claude'], true)
        assert false: 'Managed drift accepted'
      } catch (IllegalStateException expected) {
        assert expected.message.contains('Edited managed region')
      }
      ['agent-context.md', 'cook.groovy', '.claude/skills', '.codex/hooks.json', '.ralph-tui'].each {
        assert !new File(root, it).exists()
      }
    }
  }
}
