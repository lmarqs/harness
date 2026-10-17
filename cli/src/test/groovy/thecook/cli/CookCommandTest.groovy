package thecook.cli

import org.junit.Test
import java.nio.file.Files

class CookCommandTest {
  @Test void missingUnknownAndRemovedOptionsAreUsageErrors() {
    def stdout = new ByteArrayOutputStream(); def stderr = new ByteArrayOutputStream()
    def cli = command(stdout, stderr)
    assert cli.execute(['--help'] as String[]) == 0
    assert stdout.toString('UTF-8').contains('Applies a recipe')
    [
      ['--mystery', 'x.groovy'],
      ['-C'],
      ['help', '--kitchen='],
      ['init'],
      ['cook'],
      ['plan'],
      ['apply'],
      ['--library', 'lib', 'x.groovy'],
      ['--remove', 'x.groovy'],
      ['notes'],
    ].each { arguments ->
      stderr.reset()
      assert cli.execute(arguments as String[]) == 2 : arguments
      assert stderr.toString('UTF-8').endsWith('\nRun ./cook help for usage.\n')
      assert !stderr.toString('UTF-8').contains('Usage:')
    }
    stderr.reset()
    assert cli.execute(['init'] as String[]) == 2
    assert stderr.toString('UTF-8').readLines().first() == 'Not a recipe: init. A recipe is coordinates or a .groovy path'
  }

  @Test void kitchenOptionCooksAsIfStartedThereAndGoesBeforeTheRecipe() {
    workspace { root, repo ->
      Fixtures.notesCookbook(repo)
      new File(repo, 'notes.groovy').text = """@cookbook('acme:1.2.3')
import acme.notes.Notes
@ingredient(Notes) def notes
notes.add('notes')
"""
      def notes = new File(repo, 'NOTES.md')
      def stdout = new ByteArrayOutputStream(); def stderr = new ByteArrayOutputStream()
      def cli = command(stdout, stderr)
      assert cli.execute(['-C', repo.path, 'notes.groovy', '-C', repo.path] as String[]) == 2
      assert cli.execute(['-C', repo.path, 'notes.groovy', '--kitchen', repo.path] as String[]) == 2
      assert cli.execute(['-C', repo.path, '--kitchen', repo.path, 'notes.groovy'] as String[]) == 2
      assert cli.execute(['--kitchen', new File(root, 'missing').path, 'notes.groovy'] as String[]) == 1
      assert cli.execute(['--kitchen=' + repo.path, '--dry-run', 'notes.groovy'] as String[]) == 0 : stderr.toString('UTF-8')
      assert !notes.exists()
      assert cli.execute(['--kitchen', repo.path, '--batch-approve', 'notes.groovy'] as String[]) == 0 : stderr.toString('UTF-8')
      assert notes.text == '<!-- cook:begin notes#notes -->\nnotes\n<!-- cook:end notes#notes -->\n'
      stdout.reset()
      assert cli.execute(['-C', repo.path, 'notes.groovy'] as String[]) == 0 : stderr.toString('UTF-8')
      assert stdout.toString('UTF-8') == 'Already cooked.\n'
    }
  }

  @Test void helpCommandReadsEachRecipeFromItsTextsWithoutRunningIt() {
    workspace { root, repo ->
      new File(repo, 'notes.groovy').text = """@help('''
Seeds `NOTES.md`.
''')
@confirm('''
Nothing outside the repository changes.
''')
@yield('''
Edit `NOTES.md`.
''')
import example.notes.Notes

@param(value = ['owner'], doc = 'Who keeps the notes', choices = ['me', 'team']) String owner

@ingredient(Notes) def notes

throw new IllegalStateException('the recipe ran')
"""
      def expected = '''notes.groovy

Seeds `NOTES.md`.

Parameters
  --owner=<String>
      Who keeps the notes; required; choices: me, team

Ingredients
  example.notes.Notes

Confirm
  Nothing outside the repository changes.

After applying
  Edit `NOTES.md`.
'''
      def stdout = new ByteArrayOutputStream(); def stderr = new ByteArrayOutputStream()
      def cli = command(stdout, stderr)
      assert cli.execute(['-C', repo.path, 'help', 'notes.groovy'] as String[]) == 0 : stderr.toString('UTF-8')
      assert stdout.toString('UTF-8') == expected
      stdout.reset()
      assert cli.execute(['help', '-C', repo.path, 'notes.groovy', 'notes.groovy'] as String[]) == 0 : stderr.toString('UTF-8')
      assert stdout.toString('UTF-8') == expected.trim() + '\n\n' + expected
      [['notes.groovy', '--help'], ['--help', 'notes.groovy'], ['help', 'notes.groovy', '--owner=me']].each { misplaced ->
        stderr.reset()
        assert cli.execute((['-C', repo.path] + misplaced) as String[]) == 2
        assert stderr.toString('UTF-8').readLines().first() in ['--help takes no other arguments; use help <recipe> to read a recipe', 'help takes recipes only: --owner=me']
      }
      new File(repo, 'bare.groovy').text = 'println "no help"\n'
      [
        [['help', 'bare.groovy'], 1, 'bare.groovy has no help: its first import carries no @help'],
        [['help', 'missing.groovy'], 1, 'Recipe file not found: missing.groovy'],
        [['help', 'notes'], 2, 'Not a recipe: notes. A recipe is coordinates or a .groovy path'],
      ].each { arguments, status, message ->
        stderr.reset()
        assert cli.execute((['-C', repo.path] + arguments) as String[]) == status
        assert stderr.toString('UTF-8').readLines().first() == message
      }
      stdout.reset()
      assert cli.execute(['help'] as String[]) == 0 && stdout.toString('UTF-8').startsWith('Usage:')
      assert repo.list() as Set == ['notes.groovy', 'bare.groovy'] as Set
    }
  }

  @Test void helpReadsRecipesAndFrontPagesFromTheKitchensRepositories() {
    workspace { root, repo ->
      def jar = new File(repo, 'repository/acme/notes/1.2.3/notes-1.2.3.jar'); jar.parentFile.mkdirs()
      new java.util.jar.JarOutputStream(jar.newOutputStream()).withCloseable { out ->
        ['acme/notes/README.md': 'Notes for the repository.\n', 'acme/notes/Notes.groovy': 'class Notes {}\n',
          'acme/notes/notes.groovy': """@help(\'\'\'
Seeds `NOTES.md`.
\'\'\')
import acme.notes.Notes

@ingredient(Notes) def notes
"""].each { path, text ->
          out.putNextEntry(new java.util.jar.JarEntry(path)); out.write(text.getBytes('UTF-8')); out.closeEntry()
        }
      }
      new File(repo, 'cook.toml').text = 'repositories = ["repository"]\n'
      def stdout = new ByteArrayOutputStream(); def stderr = new ByteArrayOutputStream()
      def cli = command(stdout, stderr)
      assert cli.execute(['-C', repo.path, 'help', 'acme:notes:1.2.3:notes', 'acme:notes:1.2.3'] as String[]) == 0 :
      stderr.toString('UTF-8')
      assert stdout.toString('UTF-8') == """acme:notes:1.2.3:notes

Seeds `NOTES.md`.

Ingredients
  acme.notes.Notes

acme:notes:1.2.3

Notes for the repository.

Recipes
  acme:notes:1.2.3:notes

Models
  acme.notes.Notes
"""
      assert cli.execute(['-C', repo.path, 'help', 'acme:notes:1.2.4'] as String[]) == 1
      assert stderr.toString('UTF-8').contains('acme:notes:1.2.4 not found in the repositories in')
      assert cli.execute(['-C', repo.path, 'help', 'acme:notes:latest'] as String[]) == 1
      assert stderr.toString('UTF-8').contains('Invalid recipe coordinates: acme:notes:latest')
      stderr.reset()
      assert cli.execute(['-C', repo.path, 'a:notes.groovy'] as String[]) == 1
      assert stderr.toString('UTF-8').contains('Recipe file not found: a:notes.groovy')
    }
  }

  @Test void appliesRecipeCoordinatesLeftToRightAndPrintsWhatToDoNext() {
    workspace { root, repo ->
      Fixtures.notesCookbook(repo)
      def stdout = new ByteArrayOutputStream(); def stderr = new ByteArrayOutputStream()
      def cli = command(stdout, stderr)
      def notes = new File(repo, 'NOTES.md')

      def review = """Confirm acme:notes:1.2.3:notes
  Everyone reads `NOTES.md`.

--- /dev/null
+++ b/NOTES.md
@@ -0,0 +1,3 @@
+<!-- cook:begin acme:notes:notes#notes -->
+first
+<!-- cook:end acme:notes:notes#notes -->
"""
      assert cli.execute(['-C', repo.path, '--dry-run', 'acme:notes:1.2.3', '--line=first'] as String[]) == 0 :
      stderr.toString('UTF-8')
      assert stdout.toString('UTF-8').startsWith(review + '--- /dev/null\n+++ b/cook.lock\n')
      assert stdout.toString('UTF-8').endsWith('\n\nWould create NOTES.md\nWould create cook.lock\n')
      assert !notes.exists()

      stdout.reset()
      assert cli.execute(['-C', repo.path, 'acme:notes:1.2.3', '--line=first'] as String[]) == 1
      assert stderr.toString('UTF-8') ==
      'Cook asks before writing, and there is no terminal to ask in; run with --batch-approve to write without asking\n'
      assert !notes.exists()

      def declined = new ByteArrayOutputStream(); stderr.reset()
      assert command(declined, stderr, 'n\n').execute(['-C', repo.path, 'acme:notes:1.2.3', '--line=first'] as String[]) == 1
      assert declined.toString('UTF-8').endsWith('\n\nApply these changes? [y/N] ')
      assert stderr.toString('UTF-8') == 'Nothing written.\n'
      assert !notes.exists()

      def accepted = new ByteArrayOutputStream()
      assert command(accepted, stderr, 'y\n').execute(['-C', repo.path, 'acme:notes:1.2.3', '--line=first'] as String[]) == 0
      assert accepted.toString('UTF-8').startsWith(review)
      assert accepted.toString('UTF-8').endsWith('Apply these changes? [y/N] Created NOTES.md\nCreated cook.lock\n\n' +
      'After applying acme:notes:1.2.3:notes\n  Read `NOTES.md`.\n')

      stdout.reset()
      assert cli.execute(['-C', repo.path, '--batch-approve', 'acme:notes:1.2.3', '--line=first', 'acme:notes:1.2.3:more'] as String[]) == 0 :
      stderr.toString('UTF-8')
      assert stdout.toString('UTF-8').endsWith('Updated NOTES.md\nUpdated cook.lock\n\nAfter applying acme:notes:1.2.3:notes\n  Read `NOTES.md`.\n')
      assert notes.text == """<!-- cook:begin acme:notes:notes#notes -->
first
<!-- cook:end acme:notes:notes#notes -->
<!-- cook:begin acme:notes:more#notes -->
more
<!-- cook:end acme:notes:more#notes -->
"""
      stdout.reset()
      assert cli.execute(['-C', repo.path, 'acme:notes:1.2.3', '--line=first', 'acme:notes:1.2.3:more'] as String[]) == 0
      assert stdout.toString('UTF-8') == 'Already cooked.\n'
      stdout.reset()
      assert cli.execute(['-C', repo.path, '--batch-approve', 'acme:notes:1.2.3', '--line=second'] as String[]) == 0
      assert stdout.toString('UTF-8').contains('\n-first\n+second\n')
      assert stdout.toString('UTF-8').contains('\nUpdated NOTES.md\n\nAfter applying')
      stdout.reset()
      assert cli.execute(['-C', repo.path, '--dry-run', 'acme:notes:1.2.3', '--line=second', '--loud'] as String[]) == 0
      assert stdout.toString('UTF-8').contains('\n-second\n+SECOND\n')
      stdout.reset()
      assert cli.execute(['-C', repo.path, '--dry-run', 'acme:notes:1.2.3', '--line=second', '--loud=false'] as String[]) == 0
      assert stdout.toString('UTF-8') == 'Already cooked.\n'
      stdout.reset()
      assert cli.execute(['-C', repo.path, '--dry-run', 'acme:notes:1.2.3', '--line', 'second', '--loud'] as String[]) == 0
      assert stdout.toString('UTF-8').contains('\n-second\n+SECOND\n')
      new File(repo, 'local.groovy').text = """@cookbook('acme:1.2.3')
import acme.notes.Notes
@ingredient(Notes) def notes
notes.add('local')
"""
      stdout.reset()
      assert cli.execute(['-C', repo.path, '--dry-run', 'acme:notes:1.2.3', '--line', 'second', 'local.groovy'] as String[]) == 0 :
      stderr.toString('UTF-8')
      assert stdout.toString('UTF-8').contains('\n+<!-- cook:begin local#notes -->\n+local\n+<!-- cook:end local#notes -->\n')
      assert stdout.toString('UTF-8').endsWith('\nWould update NOTES.md\nWould update cook.lock\n')

      [
        [['acme:notes:1.2.3', '--line', 'a:b'], 2, 'Not a recipe: a:b. To give it to --line, write --line=a:b'],
        [['acme:notes:1.2.3', '--line', 'a', 'b'], 2, 'Not a recipe: b. Recipe arguments start with --'],
        [
          ['acme:notes:1.2.3', '--line=a', '--loud', 'false'],
          1,
          'acme:notes:1.2.3: --loud takes no separate value; write --loud=false'
        ],
        [['acme:notes:1.2.3', '--line'], 1, 'acme:notes:1.2.3: --line needs a value: write --line=<value>'],
        [['acme:notes:1.2.3', '--line='], 2, 'Missing value for --line'],
        [
          ['acme:notes:1.2.3', '--line=a', '--set=first'],
          1,
          'acme:notes:1.2.3: Unknown argument --set; its arguments are --line, --loud'
        ],
        [['acme:notes:1.2.3', 'line=first'], 2, 'Not a recipe: line=first. Recipe arguments start with --'],
        [['../outside.groovy'], 1, '../outside.groovy: the recipe is outside the kitchen; move it into the kitchen'],
        [['acme:notes:1.2.3', '--line=a', '--dry-run'], 2, '--dry-run goes before the first recipe'],
        [['acme:notes:1.2.3', '--line=a', '--batch-approve'], 2, '--batch-approve goes before the first recipe'],
        [['acme:notes:1.2.3', '--line=a', '--force'], 2, '--force goes before the first recipe'],
        [['acme:notes:1.2.3', '--line=a', '--skip'], 2, '--skip goes before the first recipe'],
        [['--force', '--skip', 'acme:notes:1.2.3', '--line=a'], 2, '--force and --skip exclude each other'],
        [
          ['acme:notes:1.2.3:more', 'acme:notes:1.2.3:more'],
          1,
          'recipe 2 (acme:notes:1.2.3:more): acme:notes:more is applied twice; apply it once per invocation'
        ],
        [['acme:notes:1.2.3'], 1, 'acme:notes:1.2.3: Missing --line: The line to add'],
      ].each { arguments, status, message ->
        stderr.reset()
        assert cli.execute((['-C', repo.path] + arguments) as String[]) == status
        assert stderr.toString('UTF-8').readLines().first() == message
      }
    }
  }

  private static Object command(ByteArrayOutputStream stdout, ByteArrayOutputStream stderr, String input = null) {
    def stdin = input == null ? null : new BufferedReader(new StringReader(input))
    new thecook.cli.terminal.CookCommand(new PrintWriter(stdout, true), new PrintWriter(stderr, true), stdin)
  }

  private static void workspace(Closure body) {
    def root = Files.createTempDirectory('cook-cli-').toFile()
    try {
      def repo = new File(root, 'repository'); repo.mkdirs()
      body(root, repo)
    } finally {
      root.deleteDir()
    }
  }
}
