package thecook

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import thecook.cookbook.Coordinates
import thecook.cookbook.CookbookRepositories

import java.security.MessageDigest
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

class CookbookRepositoriesTest {
  @Rule public TemporaryFolder folder = new TemporaryFolder()

  @Test void parsesCoordinatesWithAnExactReleaseVersion() {
    def coordinates = Coordinates.parse('dev.thecook:agents:0.1.0:instructions')
    assert [coordinates.group, coordinates.artifact, coordinates.version, coordinates.recipe] ==
    ['dev.thecook', 'agents', '0.1.0', 'instructions']
    assert coordinates.jarPath == 'dev/thecook/agents/0.1.0/agents-0.1.0.jar'
    assert Coordinates.parse('dev.thecook:agents:0.1.0').recipe == null
    ['dev.thecook:agents', 'dev.thecook:agents:0.1', 'dev.thecook:agents:0.1.0-SNAPSHOT', 'dev.thecook:agents:[0.1,)']
    .each { text ->
      def error = shouldFail(IllegalArgumentException) { Coordinates.parse(text) }
      assert error.message.startsWith("Invalid recipe coordinates: ${text}")
    }
    assert Coordinates.written('acme:notes:1.2.3')
    assert !Coordinates.written('https://example.com/notes.groovy')
    assert !Coordinates.written('notes.groovy')
  }

  @Test void readsTheArtifactFromTheFirstListedRepositoryHoldingIt() {
    def kitchen = folder.newFolder('kitchen')
    publish(new File(kitchen, 'second'), [
      'acme/notes/README.md'  : 'Notes for the repository.',
      'acme/notes/notes.groovy': 'second notes',
      'acme/notes/review.groovy': 'review',
      'acme/notes/Notes.groovy': 'class Notes {}',
      'acme/notes/templates/NOTES.md': 'resource',
      'acme/other/other.groovy': 'another artifact',
    ])
    new File(kitchen, 'cook.toml').text = 'repositories = ["first", "second"]\n'
    new File(kitchen, 'first').mkdirs()

    def artifact = CookbookRepositories.at(kitchen).artifact(Coordinates.parse('acme:notes:1.2.3'))

    assert artifact.readme == 'Notes for the repository.'
    assert artifact.recipes == ['notes', 'review']
    assert artifact.models == ['Notes']
    assert artifact.recipeSource('notes') == 'second notes'
    def error = shouldFail(IllegalArgumentException) { artifact.recipeSource('other') }
    assert error.message == 'No recipe other in acme:notes:1.2.3; its recipes are: notes, review'
  }

  @Test void hashesTheArtifactsSourcesWhateverTheJarsEntryOrder() {
    def kitchen = folder.newFolder('kitchen')
    def files = ['acme/notes/notes.groovy': 'notes', 'acme/notes/templates/a b.md': 'resource', 'acme/notes/README.md': '']
    publish(new File(kitchen, 'forward'), files)
    publish(new File(kitchen, 'reverse'), files.entrySet().toList().reverse().collectEntries())
    def coordinates = Coordinates.parse('acme:notes:1.2.3')
    new File(kitchen, 'cook.toml').text = 'repositories = ["forward"]\n'
    def forward = CookbookRepositories.at(kitchen).artifact(coordinates).sha256
    new File(kitchen, 'cook.toml').text = 'repositories = ["reverse"]\n'
    def reverse = CookbookRepositories.at(kitchen).artifact(coordinates).sha256

    def lines = ['README.md', 'notes.groovy', 'templates/a b.md'].collect { path ->
      def text = files['acme/notes/' + path]
      "${digest(text.getBytes('UTF-8'))}  ${path}\n"
    }
    assert forward == digest(lines.join().getBytes('UTF-8'))
    assert reverse == forward
  }

  @Test void missingArtifactNamesTheRepositoriesSearched() {
    def kitchen = folder.newFolder('kitchen')
    new File(kitchen, 'cook.toml').text = 'repositories = ["repository"]\n'
    def error = shouldFail(IllegalArgumentException) {
      CookbookRepositories.at(kitchen).artifact(Coordinates.parse('acme:notes:1.2.4'))
    }
    assert error.message.startsWith("acme:notes:1.2.4 not found in the repositories in ${new File(kitchen, 'cook.toml')}")
    assert error.message.endsWith('publish it there, or list its repository in cook.toml')
  }

  @Test void withoutCookTomlTheRepositoryIsMavenCentral() {
    assert CookbookRepositories.at(folder.newFolder('kitchen')).repositories*.toString() == [CookbookRepositories.CENTRAL.toString()]
  }

  @Test void refusesRepositoriesThatAreNeitherHttpsNorPaths() {
    def kitchen = folder.newFolder('kitchen')
    new File(kitchen, 'cook.toml').text = 'repositories = ["http://example.com/maven2"]\n'
    def error = shouldFail(IllegalArgumentException) { CookbookRepositories.at(kitchen) }
    assert error.message.endsWith('repository http://example.com/maven2 must be an https:// URL or a path')
    new File(kitchen, 'cook.toml').text = 'repositories = "repository"\n'
    error = shouldFail(IllegalArgumentException) { CookbookRepositories.at(kitchen) }
    assert error.message.endsWith('repositories must be a list of https:// URLs or paths')
  }

  private static void publish(File repository, Map<String, String> files) {
    def jar = new File(repository, 'acme/notes/1.2.3/notes-1.2.3.jar')
    jar.parentFile.mkdirs()
    new JarOutputStream(jar.newOutputStream()).withCloseable { out ->
      files.each { path, text ->
        out.putNextEntry(new JarEntry(path))
        out.write(text.getBytes('UTF-8'))
        out.closeEntry()
      }
    }
  }

  private static String digest(byte[] bytes) {
    MessageDigest.getInstance('SHA-256').digest(bytes).encodeHex().toString()
  }

  private static Throwable shouldFail(Class<? extends Throwable> type, Closure action) {
    try {
      action()
    } catch (Throwable error) {
      assert type.isInstance(error)
      return error
    }
    throw new AssertionError("Expected ${type.simpleName}")
  }
}
