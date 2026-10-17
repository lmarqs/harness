package thecook.infra

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import thecook.cookbook.Coordinates
import thecook.cookbook.CookbookRepositories

import java.security.MessageDigest
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

class CookTomlTest {
  @Rule public TemporaryFolder folder = new TemporaryFolder()

  @Test void readsTheArtifactFromTheFirstListedRepositoryHoldingIt() {
    def kitchen = folder.newFolder('kitchen')
    publish(new File(kitchen, 'second'), [
      'acme/notes/README.md': 'Notes for the repository.',
      'acme/notes/notes.groovy': 'second notes',
      'acme/notes/review.groovy': 'review',
      'acme/notes/Notes.groovy': 'class Notes {}',
      'acme/notes/templates/NOTES.md': 'resource',
      'acme/other/other.groovy': 'another artifact',
    ])
    new File(kitchen, 'cook.toml').text = 'repositories = ["first", "second"]\n'
    new File(kitchen, 'first').mkdirs()

    def artifact = repositories(kitchen).artifact(Coordinates.parse('acme:notes:1.2.3'))

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
    def forward = repositories(kitchen).artifact(coordinates).sha256
    new File(kitchen, 'cook.toml').text = 'repositories = ["reverse"]\n'
    def reverse = repositories(kitchen).artifact(coordinates).sha256

    def lines = ['README.md', 'notes.groovy', 'templates/a b.md'].collect { path ->
      def text = files['acme/notes/' + path]
      "${digest(text.getBytes('UTF-8'))}  ${path}\n"
    }
    assert forward == digest(lines.join().getBytes('UTF-8'))
    assert reverse == forward
  }

  @Test void readsAnArtifactFromTheReleaseTagOfACookbookInADirectory() {
    def files = ['notes/README.md': 'Notes.', 'notes/notes.groovy': 'notes', 'notes/templates/NOTES.md': 'resource',
      'other/other.groovy': 'other']
    gitCookbook('source', 'cookbook', '1.2.3', files)
    def kitchen = folder.newFolder('kitchen')
    new File(kitchen, 'cook.toml').text = 'repositories = [{ git = "../source", dir = "cookbook" }]\n'

    def artifact = repositories(kitchen).artifact(Coordinates.parse('acme:notes:1.2.3'))

    assert artifact.readme == 'Notes.'
    assert artifact.recipes == ['notes']
    assert artifact.resources('templates') == ['NOTES.md': 'resource']
    publish(new File(kitchen, 'maven'), files.findAll { it.key.startsWith('notes/') }.collectEntries { path, text ->
      ['acme/' + path, text]
    })
    new File(kitchen, 'cook.toml').text = 'repositories = ["maven"]\n'
    assert repositories(kitchen).artifact(Coordinates.parse('acme:notes:1.2.3')).sha256 == artifact.sha256
  }

  @Test void leavesOutOfTheSourcesTheFilesAGradleJarLeavesOut() {
    def files = ['notes/notes.groovy': 'notes', 'notes/.gitignore': 'out/\n', 'notes/templates/.DS_Store': '',
      'notes/templates/NOTES.md~': 'backup', 'notes/.svn/entries': '']
    gitCookbook('source', null, '1.2.3', files)
    def kitchen = folder.newFolder('kitchen')
    publish(new File(kitchen, 'maven'), ['acme/notes/notes.groovy': 'notes'])
    new File(kitchen, 'cook.toml').text = 'repositories = [{ git = "../source" }]\n'
    def tag = repositories(kitchen).artifact(Coordinates.parse('acme:notes:1.2.3'))
    new File(kitchen, 'cook.toml').text = 'repositories = ["maven"]\n'

    assert tag.resources('templates') == [:]
    assert tag.sha256 == repositories(kitchen).artifact(Coordinates.parse('acme:notes:1.2.3')).sha256
  }

  @Test void readsACookbookAtTheRepositoryRootFromItsVersionTag() {
    def source = gitCookbook('source', null, '1.2.3', ['notes/notes.groovy': 'notes'])
    def kitchen = folder.newFolder('kitchen')
    new File(kitchen, 'cook.toml').text = 'repositories = [{ git = "file://' + source.absolutePath + '" }]\n'

    assert repositories(kitchen).artifact(Coordinates.parse('acme:notes:1.2.3')).recipeSource('notes') == 'notes'
  }

  @Test void readsTheSourcesAsCommittedWhateverTheTagsExportAttributes() {
    def files = ['notes/notes.groovy': 'line\n$Format:%H$\n', 'notes/.gitattributes': '* export-subst text eol=crlf\n']
    gitCookbook('source', null, '1.2.3', files)
    def kitchen = folder.newFolder('kitchen')
    new File(kitchen, 'cook.toml').text = 'repositories = [{ git = "../source" }]\n'

    assert repositories(kitchen).artifact(Coordinates.parse('acme:notes:1.2.3')).recipeSource('notes') == 'line\n$Format:%H$\n'
  }

  @Test void neverReadsAGitUrlAsAnOption() {
    def marker = new File("cook-option-${System.nanoTime()}")
    def kitchen = folder.newFolder('kitchen')
    new File(kitchen, 'cook.toml').text = "repositories = [{ git = \"--upload-pack=touch ${marker.name};:\" }]\n"

    try {
      shouldFail(IllegalArgumentException) { repositories(kitchen).artifact(Coordinates.parse('acme:notes:1.2.3')) }
      assert !marker.exists()
    } finally {
      marker.delete()
    }
  }

  @Test void skipsAGitRepositoryWithoutTheReleaseOrForAnotherGroup() {
    gitCookbook('other', null, '1.2.3', ['notes/notes.groovy': 'another group'], 'other')
    gitCookbook('older', null, '1.2.2', ['notes/notes.groovy': 'an older release'])
    def kitchen = folder.newFolder('kitchen')
    publish(new File(kitchen, 'maven'), ['acme/notes/notes.groovy': 'from the jar'])
    new File(kitchen, 'cook.toml').text = 'repositories = [{ git = "../other" }, { git = "../older" }, "maven"]\n'

    assert repositories(kitchen).artifact(Coordinates.parse('acme:notes:1.2.3')).recipeSource('notes') == 'from the jar'
  }

  @Test void fetchesAMovedTagAgainAndReusesAFetchedReleaseOffline() {
    def source = gitCookbook('source', 'cookbook', '1.2.3', ['notes/notes.groovy': 'first'])
    def kitchen = folder.newFolder('kitchen')
    new File(kitchen, 'cook.toml').text = 'repositories = [{ git = "../source", dir = "cookbook" }]\n'
    def coordinates = Coordinates.parse('acme:notes:1.2.3')
    def first = repositories(kitchen).artifact(coordinates)

    new File(source, 'cookbook/notes/notes.groovy').text = 'second'
    release(source, 'cookbook/v1.2.3')
    def moved = repositories(kitchen).artifact(coordinates)
    assert moved.recipeSource('notes') == 'second'
    assert moved.sha256 != first.sha256

    assert source.renameTo(new File(folder.root, 'gone'))
    assert repositories(kitchen).artifact(coordinates).sha256 == moved.sha256
    def error = shouldFail(IllegalArgumentException) {
      repositories(kitchen).artifact(Coordinates.parse('acme:notes:1.2.4'))
    }
    assert error.message ==~ /acme:notes:1\.2\.4 is not cached, and \S+ cannot be read: fatal: [^;]+; connect to it and run again/
    assert error.message.contains("${source} cannot be read: ")
  }

  @Test void refusesGitRepositoriesThatAreUnauthenticatedMisshapedOrMisversioned() {
    def kitchen = folder.newFolder('kitchen')
    [
      '{ git = "http://example.com/book.git" }': 'git repository http://example.com/book.git must use https://, ssh or a path; ' +
      'http:// and git:// are not authenticated',
      '{ git = "git://example.com/book.git" }': 'git repository git://example.com/book.git must use https://, ssh or a path; ' +
      'http:// and git:// are not authenticated',
      '{ git = "../book", branch = "main" }': 'a git repository is { git = "<url>", dir = "<dir>" }, with dir optional',
      '{ git = "../book", dir = "../x" }': 'dir ../x must be a directory inside the git repository',
    ].each { entry, message ->
      new File(kitchen, 'cook.toml').text = "repositories = [${entry}]\n"
      def error = shouldFail(IllegalArgumentException) { repositories(kitchen) }
      assert error.message == "${new File(kitchen, 'cook.toml')}: ${message}"
    }

    def book = gitCookbook('book', null, '1.2.0', ['notes/notes.groovy': 'notes'])
    git(book, 'tag', 'v1.2.3')
    new File(kitchen, 'cook.toml').text = 'repositories = [{ git = "../book" }]\n'
    def error = shouldFail(IllegalArgumentException) { repositories(kitchen).artifact(Coordinates.parse('acme:notes:1.2.3')) }
    assert error.message == "the tag v1.2.3 in ${book} holds version 1.2.0 in cookbook.toml; tag each release with its own version"

    new File(book, 'cookbook.toml').text = 'group = \n'
    release(book, 'v1.2.3')
    error = shouldFail(IllegalArgumentException) { repositories(kitchen).artifact(Coordinates.parse('acme:notes:1.2.3')) }
    assert error.message.startsWith("Cannot read cookbook.toml at the tag v1.2.3 in ${book}: ")
  }

  @Test void missingArtifactNamesTheRepositoriesSearched() {
    def kitchen = folder.newFolder('kitchen')
    new File(kitchen, 'cook.toml').text = 'repositories = ["repository"]\n'
    def error = shouldFail(IllegalArgumentException) {
      repositories(kitchen).artifact(Coordinates.parse('acme:notes:1.2.4'))
    }
    assert error.message.startsWith("acme:notes:1.2.4 not found in the repositories in ${new File(kitchen, 'cook.toml')}")
    assert error.message.endsWith('publish it there, or list its repository in cook.toml')
  }

  @Test void withoutCookTomlTheRepositoryIsMavenCentral() {
    assert repositories(folder.newFolder('kitchen')).repositories*.toString() == [CookToml.CENTRAL.toString()]
  }

  @Test void readsPathsStartingWithTildeSlashFromTheUsersHome() {
    def kitchen = folder.newFolder('kitchen')
    def home = folder.newFolder('home')
    new File(kitchen, 'cook.toml').text = 'repositories = ["~/.m2/repository", { git = "~/kit" }, "~user/x"]\n'

    assert repositories(kitchen).repositories*.toString() == [
      new File(home, '.m2/repository').toURI().toString().replaceAll(/\/?$/, '/'),
      new File(home, 'kit').path,
      new File(kitchen, '~user/x').toURI().toString().replaceAll(/\/?$/, '/'),
    ]
  }

  @Test void refusesRepositoriesThatAreNeitherHttpsNorPaths() {
    def kitchen = folder.newFolder('kitchen')
    new File(kitchen, 'cook.toml').text = 'repositories = ["http://example.com/maven2"]\n'
    def error = shouldFail(IllegalArgumentException) { repositories(kitchen) }
    assert error.message.endsWith('repository http://example.com/maven2 must be an https:// URL or a path')
    new File(kitchen, 'cook.toml').text = 'repositories = "repository"\n'
    error = shouldFail(IllegalArgumentException) { repositories(kitchen) }
    assert error.message.endsWith('repositories must be a list of https:// URLs, paths and { git = "<url>", dir = "<dir>" } tables')
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

  private CookbookRepositories repositories(File kitchen) {
    CookToml.repositories(kitchen, new File(folder.root, 'cache'), new File(folder.root, 'home'))
  }

  /** A git repository holding a cookbook of `group` in `dir`, or at its root, with `files` below it, tagged as `version`. */
  private File gitCookbook(String name, String dir, String version, Map<String, String> files, String group = 'acme') {
    def root = folder.newFolder(name)
    git(root, 'init', '-q')
    def cookbook = dir ? new File(root, dir) : root
    (['cookbook.toml': "group = \"${group}\"\nversion = \"${version}\"\napi = 1\n".toString()] + files).each { path, text ->
      def file = new File(cookbook, path)
      file.parentFile.mkdirs()
      file.text = text
    }
    release(root, (dir ? dir + '/' : '') + 'v' + version)
    root
  }

  private static void release(File root, String tag) {
    git(root, 'add', '-A')
    git(root, 'commit', '-q', '-m', tag)
    git(root, 'tag', '-f', '-a', '-m', tag, tag)
  }

  private static String git(File directory, String... arguments) {
    def settings = ['user.name=Test', 'user.email=test@example.com', 'commit.gpgsign=false', 'tag.gpgsign=false']
    def command = ['git'] + settings.collectMany { ['-c', it] } + arguments.toList()
    def process = new ProcessBuilder(command).directory(directory).redirectErrorStream(true).start()
    def output = process.inputStream.text
    assert process.waitFor() == 0 : output
    output
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
