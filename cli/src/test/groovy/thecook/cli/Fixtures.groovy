package thecook.cli

class Fixtures {
  /** Writes into `kitchen` a Maven-layout repository holding `acme:notes:1.2.3` and a `cook.toml` listing it. */
  static void notesCookbook(File kitchen) {
    def jar = new File(kitchen, 'repository/acme/notes/1.2.3/notes-1.2.3.jar'); jar.parentFile.mkdirs()
    new java.util.jar.JarOutputStream(jar.newOutputStream()).withCloseable { out ->
      ['acme/notes/Notes.groovy': """package acme.notes

@Ingredient
class Notes {
  private List<String> lines = []
  void add(String line) { lines += line }
  @Section(path = 'NOTES.md', id = 'notes')
  List<String> section() { lines ?: null }
}
""", 'acme/notes/notes.groovy': """@help('Adds a line to `NOTES.md`.')
@confirm('Everyone reads `NOTES.md`.')
@yield('Read `NOTES.md`.')
import acme.notes.Notes

@param(value = ['line'], doc = 'The line to add') String line
@param(value = ['loud'], doc = 'Whether the line is upper case', default = ['false']) boolean loud
@ingredient(Notes) def notes

notes.add(loud ? line.toUpperCase() : line)
""", 'acme/notes/more.groovy': """@help('Adds another line.')
import acme.notes.Notes

@ingredient(Notes) def notes

notes.add('more')
"""].each { path, text ->
        out.putNextEntry(new java.util.jar.JarEntry(path)); out.write(text.getBytes('UTF-8')); out.closeEntry()
      }
    }
    new File(kitchen, 'cook.toml').text = 'repositories = ["repository"]\n'
  }
}
