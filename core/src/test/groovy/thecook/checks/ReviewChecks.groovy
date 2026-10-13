package thecook.checks

import groovy.json.JsonOutput
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import org.tomlj.Toml
import thecook.composition.Kitchen
import thecook.filesystem.Repository
import thecook.filesystem.Transaction
import thecook.reconciliation.Reconciler
import static thecook.checks.ModelChecks.*

class ReviewChecks {
  static void run() {
    check('YAML multiline mappings and block scalars preserve boundaries and restore exactly') { root ->
      ['a:\n  x: old\nmanual: keep\n', 'a: |\n  old text\nmanual: keep\n'].each { original ->
        write(root, 'settings.yaml', original)
        def kitchen = new Kitchen(root)
        def owner = FormatChecks.field(kitchen, 'settings.yaml', 'yaml', ['a'], { [x: 'new'] }, [adopt: true])
        kitchen.plan().apply(); assert new File(root, 'settings.yaml').text.contains('\nmanual: keep\n')
        owner.unmount(); kitchen.plan().apply(); assert new File(root, 'settings.yaml').text == original
      }
    }
    check('Inline TOML leaf adoption preserves siblings and restores the original') { root ->
      def original = 'a = { x = "old", manual = "keep" }\n'; write(root, 'settings.toml', original)
      def kitchen = new Kitchen(root)
      def owner = FormatChecks.field(kitchen, 'settings.toml', 'toml', ['a', 'x'], { 'new' }, [adopt: true])
      kitchen.plan().apply(); assert new File(root, 'settings.toml').text == original.replace('"old"', '"new"')
      owner.unmount(); kitchen.plan().apply(); assert new File(root, 'settings.toml').text == original
    }
    check('TOML supplementary Unicode is valid native TOML without surrogate escapes') { root ->
      def kitchen = new Kitchen(root)
      FormatChecks.field(kitchen, 'settings.toml', 'toml', ['😀'], { '😀' }); kitchen.plan().apply()
      def text = new File(root, 'settings.toml').text
      assert !text.contains('\\ud83d') && text.contains('😀')
      assert Toml.parse(text).getString(['😀']) == '😀'
    }
    check('Editing a manual prefix cannot crash removal of its inserted separator') { root ->
      write(root, '.gitignore', 'a'); def kitchen = new Kitchen(root); def owner = git(kitchen)
      kitchen.plan().apply(); write(root, '.gitignore', new File(root, '.gitignore').text.substring(1))
      owner.unmount(); kitchen.plan().apply(); assert new File(root, '.gitignore').text == ''
    }
    check('Recovery refuses manual permission edits to persistent Cook state') { root ->
      def kitchen = new Kitchen(root); git(kitchen); kitchen.plan().apply()
      write(root, '.cook/transaction.json', JsonOutput.toJson(kitchen.plan().proposal))
      def state = new File(root, '.cook/state.json')
      Files.setPosixFilePermissions(state.toPath(), PosixFilePermissions.fromString('rw-r-----'))
      def before = snapshot(root)
      rejects('Changed after interrupted transaction') { new Transaction(kitchen.repository).recover() }
      assert snapshot(root) == before && Repository.permissions(state) == 'rw-r-----'
    }
    check('A state-only eject during planning invalidates the captured state snapshot') { root ->
      write(root, 'settings.json', '{"x":"original"}')
      def kitchen = new Kitchen(root)
      def owner = FormatChecks.field(kitchen, 'settings.json', 'json', ['x'], { 'managed' }, [adopt: true])
      kitchen.plan().apply(); def eject = kitchen.eject(); owner.unmount()
      def racing = new RacingRepository(root); racing.hook = { eject.apply() }
      def plan = new Reconciler().plan(racing, kitchen.render())
      rejects('Changed since preview') { plan.apply() }
      assert new File(root, 'settings.json').text == '{"x":"managed"}'
    }
  }
  static class RacingRepository extends Repository {
    Closure hook
    RacingRepository(File root) {
      super(root)
    }
    @Override Map snapshot(File file) {
      def captured = super.snapshot(file)
      if (file.name == 'state.json' && hook) {
        def action = hook; hook = null; action.call()
      }
      captured
    }
  }
}
