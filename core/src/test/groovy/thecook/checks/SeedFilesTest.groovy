package thecook.checks

import org.junit.Test
import java.nio.file.Files
import thecook.composition.Kitchen
import thecook.cookbook.Cookbook
import thecook.filesystem.AtomicFiles
import thecook.filesystem.Transaction

class SeedFilesTest {
  private static def seed(Kitchen kitchen, String path = 'policy.yaml', String value = 'version: 1\n') {
    def recipe = kitchen.recipe('starter')
    recipe.application('example/starter')
    recipe.output(path, [seed: true]) { text { value } }
    recipe.mount()
    recipe
  }

  @Test void createsAbsentSeedAndPreservesEditedAndEmptySourcesAcrossRerunsAndRemoval() {
    def root = Files.createTempDirectory('cook-seed-').toFile()
    try {
      def kitchen = new Kitchen(root)
      seed(kitchen)
      assert kitchen.plan().changes*.path == ['policy.yaml']
      assert !new File(root, 'policy.yaml').exists()
      kitchen.plan().apply()
      def source = new File(root, 'policy.yaml')
      assert source.text == 'version: 1\n'
      source.bytes = [0x65, 0x64, 0x69, 0x74, 0x0a] as byte[]
      assert kitchen.plan().changes.empty
      kitchen.plan().apply()
      assert source.bytes == ([0x65, 0x64, 0x69, 0x74, 0x0a] as byte[])
      source.bytes = new byte[0]
      kitchen.plan().apply()
      assert source.length() == 0
      def removal = new Kitchen(root).recipe('remove')
      removal.application('example/starter'); removal.mount()
      removal.kitchen.plan().apply()
      assert source.isFile() && source.length() == 0
    } finally { root.deleteDir() }
  }

  @Test void rejectsDirectorySymlinkAndManagedPathWithoutChangingAnything() {
    ['directory', 'symlink', 'managed'].each { shape ->
      def root = Files.createTempDirectory('cook-seed-shape-').toFile()
      try {
        def destination = new File(root, 'policy.yaml')
        if (shape == 'directory') destination.mkdir()
        if (shape == 'symlink') Files.createSymbolicLink(destination.toPath(), new File(root, 'elsewhere').toPath())
        if (shape == 'managed') {
          def managed = new Kitchen(root).recipe('managed')
          managed.application('example/managed')
          managed.output('policy.yaml', [owns: true]) { text { 'managed\n' } }
          managed.mount(); managed.kitchen.plan().apply()
        }
        def before = ModelChecks.snapshot(root)
        def kitchen = new Kitchen(root); seed(kitchen)
        try { kitchen.plan(); assert false: "Accepted ${shape}" }
        catch (IllegalArgumentException | IllegalStateException expected) { }
        assert ModelChecks.snapshot(root) == before
      } finally { root.deleteDir() }
    }
  }

  @Test void conflictingSeedsAndManagedDeclarationsFailBeforeWriting() {
    ['seed', 'managed'].each { second ->
      def root = Files.createTempDirectory('cook-seed-conflict-').toFile()
      try {
        def kitchen = new Kitchen(root); seed(kitchen)
        def other = kitchen.recipe('other')
        other.application('example/other')
        other.output('policy.yaml', second == 'seed' ? [seed: true] : [owns: true]) { text { 'other\n' } }
        other.mount()
        try { kitchen.plan(); assert false: 'Accepted duplicate destination' }
        catch (IllegalArgumentException expected) { assert expected.message.contains('policy.yaml') }
        assert ModelChecks.snapshot(root).isEmpty()
      } finally { root.deleteDir() }
    }
  }

  @Test void staleSeedPreviewAndFailureElsewhereLeaveSeedAbsent() {
    def root = Files.createTempDirectory('cook-seed-transaction-').toFile()
    try {
      def kitchen = new Kitchen(root); seed(kitchen)
      def other = kitchen.recipe('other')
      other.application('example/other')
      other.output('other.txt', [owns: true]) { text { 'managed' } }
      other.mount()
      def preview = kitchen.plan()
      assert preview.changes*.path == ['other.txt', 'policy.yaml']
      assert !new File(root, 'policy.yaml').exists()
      new File(root, 'policy.yaml').text = 'user edit\n'
      try { preview.apply(); assert false: 'Accepted stale seed snapshot' }
      catch (IllegalStateException expected) { assert expected.message.contains('Changed since preview') }
      assert new File(root, 'policy.yaml').text == 'user edit\n'
      assert !new File(root, 'other.txt').exists()
      new File(root, 'policy.yaml').delete()
      new File(root, 'other.txt').text = 'foreign'
      try { kitchen.plan(); assert false: 'Accepted conflicting managed output' }
      catch (IllegalStateException expected) { }
      assert !new File(root, 'policy.yaml').exists()
    } finally { root.deleteDir() }
  }

  @Test void initValidatesAbsentPolicySeedBeforeWritingAnyOutput() {
    def root = Files.createTempDirectory('cook-policy-seed-').toFile()
    try {
      def skills = new File(root, 'shared-skills')
      ['solid', 'hexagonal-layout', 'effective-java-core'].each { name ->
        def skill = new File(skills, name + '/SKILL.md'); skill.parentFile.mkdirs(); skill.text = 'skill\n'
      }
      def kitchen = new Kitchen(root)
      def book = new Cookbook(new File('../cookbook'))
      ['ingredients/agent-policy/Policy.groovy', 'ingredients/agent-policy/PolicyFile.groovy',
       'ingredients/agent-policy/AgentInstructions.groovy', 'ingredients/git/Git.groovy',
       'ingredients/skills/Skills.groovy', 'ingredients/the-cook/TheCook.groovy',
       'recipes/agentic/claude/ClaudeFiles.groovy', 'recipes/agentic/codex/CodexFiles.groovy'].each { book.type(it) }
      book.recipe('cookbook', kitchen, [policy: '.agents/policy.yaml', 'skills-home': skills.path]).mount()
      assert !new File(root, '.agents/policy.yaml').exists()
      assert kitchen.plan().changes*.path.contains('.agents/policy.yaml')
      kitchen.plan().apply()
      def source = new File(root, '.agents/policy.yaml')
      assert source.text.contains('version: 1')
      source.text = 'version: 1\ninstructions:\n  - Keep local notes.\n'
      def rerun = new Kitchen(root)
      book.recipe('cookbook', rerun, [policy: '.agents/policy.yaml', 'skills-home': skills.path]).mount()
      rerun.plan().apply()
      assert source.text == 'version: 1\ninstructions:\n  - Keep local notes.\n'
      source.text = ''
      try { book.recipe('cookbook', new Kitchen(root), [policy: '.agents/policy.yaml', 'skills-home': skills.path]); assert false: 'Accepted empty policy' }
      catch (IllegalArgumentException expected) { }
      assert source.text == ''
    } finally { root.deleteDir() }
  }

  @Test void seedCreationRollsBackWhenLaterTransactionWriteFails() {
    def root = Files.createTempDirectory('cook-seed-rollback-').toFile()
    try {
      def kitchen = new Kitchen(root); seed(kitchen)
      def other = kitchen.recipe('other')
      other.application('example/other')
      other.output('z-other.txt', [owns: true]) { text { 'managed' } }
      other.mount()
      def plan = kitchen.plan()
      def failingFiles = new AtomicFiles() {
        @Override void write(File file, String content, String mode) {
          if (file.name == 'z-other.txt' && content != null) throw new IOException('Injected write failure')
          super.write(file, content, mode)
        }
      }
      try { new Transaction(kitchen.repository, failingFiles).apply(plan.proposal); assert false: 'Expected write failure' }
      catch (IOException expected) { assert expected.message == 'Injected write failure' }
      assert !new File(root, 'policy.yaml').exists()
      assert !new File(root, 'z-other.txt').exists()
      assert !new File(root, '.cook/state.json').exists()
      assert !new File(root, '.cook/transaction.json').exists()
    } finally { root.deleteDir() }
  }
}
