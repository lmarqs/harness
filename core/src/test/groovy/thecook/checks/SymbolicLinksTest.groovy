package thecook.checks

import groovy.json.JsonOutput
import org.junit.Test
import java.nio.file.Files
import java.nio.file.LinkOption
import thecook.composition.Kitchen
import thecook.filesystem.AtomicFiles
import thecook.filesystem.Transaction
import thecook.reconciliation.ChangePlan

class SymbolicLinksTest {
  private void workspace(Closure body) {
    def root = Files.createTempDirectory('cook-links-').toFile()
    try {
      body(root)
    } finally {
      root.deleteDir()
    }
  }

  private static Object link(Kitchen kitchen, String path, Closure destination, boolean adopt = false) {
    def owner = kitchen.ingredient('skills')
    owner.output(path, [owns: true, adopt: adopt]) { symlink(destination) }
    owner.mount()
    owner
  }

  private static void rejects(String message, Closure action) {
    try {
      action(); assert false: 'Expected rejection: ' + message
    }
    catch (IllegalArgumentException | IllegalStateException expected) {
      assert expected.message.contains(message)
    }
  }

  @Test void plansLinksWithoutWritingAndUpdatesOnlyTheLink() {
    workspace { root ->
      def source = new File(root, 'shared/solid'); source.mkdirs()
      new File(source, 'SKILL.md').text = 'Keep the source'
      def kitchen = new Kitchen(root)
      String destination = '../../shared/solid'
      def owner = link(kitchen, '.agents/skills/solid', { destination })
      def plan = kitchen.plan()
      assert !new File(root, '.agents').exists()
      plan.apply()
      def path = new File(root, '.agents/skills/solid').toPath()
      assert Files.isSymbolicLink(path)
      assert Files.readString(path.resolve('SKILL.md')) == 'Keep the source'
      assert kitchen.plan().changes.empty
      destination = '../../shared/not-installed-yet'
      kitchen.plan().apply()
      assert Files.readSymbolicLink(path).toString() == destination
      assert kitchen.plan().changes.empty
      owner.unmount(); kitchen.plan().apply()
      assert !Files.exists(path, LinkOption.NOFOLLOW_LINKS)
      assert new File(source, 'SKILL.md').text == 'Keep the source'
    }
  }

  @Test void existingLinksRequireAdoptionAndAreRestoredOnUnmount() {
    workspace { root ->
      def path = new File(root, 'skill').toPath()
      Files.createSymbolicLink(path, java.nio.file.Path.of('original-missing-target'))
      def kitchen = new Kitchen(root)
      def owner = link(kitchen, 'skill', { 'new-target' })
      rejects('explicit adopt required') { kitchen.plan() }
      owner.definition.outputs[0].declaration.adopt = true
      kitchen.plan().apply()
      assert Files.readSymbolicLink(path).toString() == 'new-target'
      owner.unmount(); kitchen.plan().apply()
      assert Files.readSymbolicLink(path).toString() == 'original-missing-target'
    }
  }

  @Test void changedOrReplacedLinksRejectWithoutFollowingTheirTargets() {
    workspace { root ->
      def outside = new File(root, 'source'); outside.mkdirs()
      def kitchen = new Kitchen(root)
      link(kitchen, 'skill', { outside.path })
      kitchen.plan().apply()
      def path = new File(root, 'skill').toPath()
      Files.delete(path); Files.createSymbolicLink(path, java.nio.file.Path.of('manual-target'))
      rejects('Edited managed symlink') { kitchen.plan() }
      Files.delete(path); Files.writeString(path, 'manual file')
      rejects('regular file') { kitchen.plan() }
      assert Files.readString(path) == 'manual file'
      assert outside.listFiles().length == 0
    }
  }

  @Test void symlinkParentsAndFileCollisionsStillReject() {
    workspace { root ->
      def outside = new File(root, 'outside'); outside.mkdirs()
      Files.createSymbolicLink(new File(root, '.agents').toPath(), outside.toPath())
      def kitchen = new Kitchen(root)
      link(kitchen, '.agents/skills/solid', { '/some/skill' })
      rejects('Symlink target') { kitchen.plan() }
      assert outside.listFiles().length == 0
      Files.delete(new File(root, '.agents').toPath())
      new File(root, '.agents/skills').mkdirs()
      new File(root, '.agents/skills/solid').text = 'manual'
      rejects('regular file') { kitchen.plan() }
    }
  }

  @Test void savedPlansDetectLinkDriftIncludingLinksReplacedByFiles() {
    workspace { root ->
      def kitchen = new Kitchen(root)
      link(kitchen, 'skill', { 'target' })
      kitchen.plan().apply()
      def plan = kitchen.plan()
      def saved = new File(root, 'review.json'); plan.save(saved)
      def path = new File(root, 'skill').toPath()
      Files.delete(path); Files.writeString(path, 'target')
      rejects('Changed since preview') { ChangePlan.load(kitchen.repository, saved).apply() }
      assert Files.readString(path) == 'target'
    }
  }

  @Test void failedTransactionsAndInterruptedWritesRestoreLinksWithoutTouchingSources() {
    workspace { root ->
      def kitchen = new Kitchen(root)
      link(kitchen, 'a-link', { 'untouched-target' })
      def other = kitchen.ingredient('policy')
      other.output('z-policy', [owns: true]) {
        text {
          'policy'
        }
      }; other.mount()
      def proposal = kitchen.plan().proposal
      def failure = new AtomicFiles() {
            @Override void write(File file, String content, String mode) {
              if (file.name == 'z-policy' && content != null) throw new IOException('simulated write failure')
              super.write(file, content, mode)
            }
          }
      try {
        new Transaction(kitchen.repository, failure).apply(proposal); assert false
      }
      catch (IOException expected) {
        assert expected.message == 'simulated write failure'
      }
      assert !Files.exists(new File(root, 'a-link').toPath(), LinkOption.NOFOLLOW_LINKS)
      new File(root, '.cook/transaction.json').text = JsonOutput.toJson(proposal)
      Files.createSymbolicLink(new File(root, 'a-link').toPath(), java.nio.file.Path.of('untouched-target'))
      new Transaction(kitchen.repository).recover()
      assert !Files.exists(new File(root, 'a-link').toPath(), LinkOption.NOFOLLOW_LINKS)
      assert !new File(root, '.cook/transaction.json').exists()
    }
  }
}
