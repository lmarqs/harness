package thecook

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.yaml.snakeyaml.Yaml
import thecook.cookbook.BatchRevert
import thecook.cookbook.Draft
import thecook.cookbook.Proposal
import thecook.cookbook.SeedOutcomes

import java.nio.file.Files
import java.nio.file.Path

class BatchRevertTest {
  @Rule public TemporaryFolder folder = new TemporaryFolder()

  @Test void restoresTheRecordedValueAndKeepsALaterEditBesideIt() {
    def kitchen = folder.newFolder()
    def settings = new File(kitchen, 'settings.toml')
    settings.text = 'test = "old"\nother = "x"\n'
    def id = batch(kitchen) { it.write('settings.toml', 'test = "new"\nother = "x"\n') }
    settings.text = 'test = "new"\nother = "edited"\n'

    def revert = new BatchRevert(kitchen).propose(id)

    assert revert.changes == ['settings.toml']
    def reverted = revert.commit()
    assert settings.text == 'test = "old"\nother = "edited"\n'
    def record = (Map) new Yaml().load(new File(kitchen, ".cook/batches/${reverted}/batch.yaml").text)
    assert record.subMap(['command', 'reverts', 'result']) == [command: 'revert', reverts: id, result: 'written']
  }

  @Test void writesNothingAndNamesEachFileAndHunkALaterEditOverlaps() {
    def kitchen = folder.newFolder()
    def settings = new File(kitchen, 'settings.toml')
    settings.text = 'test = "old"\n'
    def id = batch(kitchen) {
      it.write('settings.toml', 'test = "new"\n')
      it.write('notes.md', 'mine\n')
    }
    settings.text = 'test = "custom"\n'
    new File(kitchen, 'notes.md').text = 'mine\nand more\n'

    def error = shouldFail { new BatchRevert(kitchen).propose(id) }

    assert error.message == """Cannot revert ${id}: later edits overlap its changes. Resolve these and run it again:
notes.md
  changed since the batch wrote it
settings.toml
  @@ -1,1 +1,1 @@
  -test = "old"
  +test = "new\""""
    assert settings.text == 'test = "custom"\n'
  }

  @Test void deletesACreatedFileAndRestoresADeletedOneWithItsPermissions() {
    def kitchen = folder.newFolder()
    def script = new File(kitchen, 'run.sh')
    script.text = 'echo hi\n'
    script.setExecutable(true)
    def mode = Files.getPosixFilePermissions(script.toPath())
    def id = batch(kitchen) {
      it.write('notes.md', 'mine')
      it.delete('run.sh')
    }

    new BatchRevert(kitchen).propose(id).commit()

    assert !new File(kitchen, 'notes.md').exists()
    assert script.text == 'echo hi\n'
    assert Files.getPosixFilePermissions(script.toPath()) == mode
  }

  @Test void restoresALinkAndRevertsARevert() {
    def kitchen = folder.newFolder()
    def link = new File(kitchen, 'CLAUDE.md').toPath()
    Files.createSymbolicLink(link, Path.of('AGENTS.md'))
    def id = batch(kitchen) { it.write('CLAUDE.md', '@AGENTS.md\n') }

    def reverted = new BatchRevert(kitchen).propose(id).commit()
    assert Files.readSymbolicLink(link) == Path.of('AGENTS.md')

    new BatchRevert(kitchen).propose(reverted).commit()
    assert !Files.isSymbolicLink(link)
    assert link.text == '@AGENTS.md\n'
  }

  @Test void keepsLinesAddedSinceTheBatchToAFileThatWasALink() {
    def kitchen = folder.newFolder()
    def link = new File(kitchen, 'CLAUDE.md').toPath()
    Files.createSymbolicLink(link, Path.of('AGENTS.md'))
    def id = batch(kitchen) { it.write('CLAUDE.md', '@AGENTS.md\n') }
    link.text = '@AGENTS.md\nmine\n'

    def error = shouldFail { new BatchRevert(kitchen).propose(id) }

    assert error.message.endsWith(':\nCLAUDE.md\n  changed since the batch wrote it')
    assert link.text == '@AGENTS.md\nmine\n'
  }

  @Test void namesAFileDeletedOrReplacedByALinkSinceTheBatchChangedIt() {
    def kitchen = folder.newFolder()
    def notes = new File(kitchen, 'notes.md')
    notes.text = 'a'
    def script = new File(kitchen, 'run.sh')
    script.text = 'echo hi\n'
    def id = batch(kitchen) {
      it.write('notes.md', 'a\n')
      it.write('run.sh', 'echo hi\n', 'rwxr-xr-x')
    }
    notes.delete()
    Files.createSymbolicLink(notes.toPath(), Path.of('elsewhere'))
    script.delete()

    def error = shouldFail { new BatchRevert(kitchen).propose(id) }

    assert error.message.endsWith(':\nnotes.md\n  changed since the batch wrote it\nrun.sh\n  deleted since the batch wrote it')
    assert Files.readSymbolicLink(notes.toPath()) == Path.of('elsewhere')
  }

  @Test void keepsAFileWithoutItsLastNewlineAsItWas() {
    def kitchen = folder.newFolder()
    def notes = new File(kitchen, 'notes.md')
    notes.text = 'a\nb'
    def id = batch(kitchen) { it.write('notes.md', 'a\nB\n') }

    new BatchRevert(kitchen).propose(id).commit()

    assert notes.text == 'a\nb'
  }

  @Test void refusesABatchThatIsNotRecordedOrDidNotWrite() {
    def kitchen = folder.newFolder()

    assert shouldFail { new BatchRevert(kitchen).propose('2026-10-14T15-04-05Z') }.message ==
    'No batch 2026-10-14T15-04-05Z is recorded in .cook/batches; ./cook batch list shows the recorded batches'
    def failed = new File(kitchen, '.cook/batches/2026-10-14T15-04-05Z')
    failed.mkdirs()
    new File(failed, 'batch.yaml').text = 'result: failed\nfiles: []\n'
    assert shouldFail { new BatchRevert(kitchen).propose('2026-10-14T15-04-05Z') }.message ==
    'Batch 2026-10-14T15-04-05Z did not finish writing (failed); there is nothing of it to revert'
    new File(failed, 'batch.yaml').text = 'result: pending\nfiles: []\n'
    assert shouldFail { new BatchRevert(kitchen).propose('2026-10-14T15-04-05Z') }.message ==
    'Batch 2026-10-14T15-04-05Z did not finish writing (pending), so it may have written some of its files; ' +
    './cook batch inspect 2026-10-14T15-04-05Z shows them and their patches'
    assert shouldFail { new BatchRevert(kitchen).propose('batches\\..\\2026-10-14T15-04-05Z') }.message.startsWith('No batch ')
  }

  private static String batch(File kitchen, Closure changes) {
    def draft = new Draft(kitchen)
    changes(draft)
    new Proposal(draft, [], [], new SeedOutcomes(), [:]).commit()
  }

  private static Throwable shouldFail(Closure action) {
    try {
      action()
    } catch (IllegalArgumentException error) {
      return error
    }
    throw new AssertionError('Expected IllegalArgumentException')
  }
}
