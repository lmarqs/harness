package thecook

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import thecook.cookbook.Argument
import thecook.cookbook.BatchRecord
import thecook.cookbook.RecipeRequest
import thecook.cookbook.RecordedFile
import thecook.cookbook.RecordedRecipe

import java.time.Instant

class BatchRecordTest {
  @Rule public TemporaryFolder folder = new TemporaryFolder()

  @Test void readsBackWhatItSaved() {
    def kitchen = folder.newFolder()
    def arguments = [Argument.bare('quiet'), Argument.inline('harness', 'claude'), Argument.separate('name', 'x')]
    def record = BatchRecord.create(kitchen)
    record.save('revert', '2026-10-14T15-04-05Z', [new RecordedRecipe(new RecipeRequest('a:b:1.0.0:c', arguments), 'a:b:1.0.0:c', 'f00')],
    [new RecordedFile('notes.md', 'create', null, [kind: 'file', mode: 'rw-r--r--', newline: true])],
    ['notes.md': ['--- /dev/null', '+++ b/notes.md', '@@ -0,0 +1,1 @@', '+mine']], [:])
    record.written()

    def read = BatchRecord.read(kitchen, record.id)

    assert [read.command, read.reverts, read.result] == ['revert', '2026-10-14T15-04-05Z', 'written']
    def recipe = read.recipes.first()
    assert [recipe.address, recipe.sha256, recipe.request.operand] == ['a:b:1.0.0:c', 'f00', 'a:b:1.0.0:c']
    assert recipe.request.arguments.collect { [it.name, it.value, it.form] } == arguments.collect { [it.name, it.value, it.form] }
    def file = read.files.first()
    assert [file.path, file.action, file.before, file.after] == ['notes.md', 'create', null, [kind: 'file', mode: 'rw-r--r--', newline: true]]
    assert read.patch('notes.md') == ['--- /dev/null', '+++ b/notes.md', '@@ -0,0 +1,1 @@', '+mine']
    assert read.failure == null
  }

  @Test void listsTheBatchesInTheOrderTheyStarted() {
    def kitchen = folder.newFolder()
    def second = Instant.parse('2026-10-14T15:04:05Z')
    def ids = [BatchRecord.create(kitchen, second.minusSeconds(1))] + (1..10).collect { BatchRecord.create(kitchen, second) }
    ids.each { it.save('apply', null, [], [], [:], [:]) }

    assert BatchRecord.list(kitchen)*.id == ['2026-10-14T15-04-04Z', '2026-10-14T15-04-05Z'] + (2..10).collect { "2026-10-14T15-04-05Z-${it}".toString() }
  }

  @Test void readsOnlyANameInTheBatchIdFormat() {
    def kitchen = folder.newFolder()
    def stray = new File(kitchen, '.cook/batches/notes')
    stray.mkdirs()
    new File(stray, 'batch.yaml').text = 'command: apply\nresult: written\n'

    assert BatchRecord.list(kitchen).isEmpty()
    def error = shouldFail { BatchRecord.read(kitchen, 'notes') }
    assert error.message == 'No batch notes is recorded in .cook/batches; ./cook batch list shows the recorded batches'
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
