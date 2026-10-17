package thecook.cookbook

import org.tomlj.Toml
import org.tomlj.TomlTable

/**
 * The repositories that cookbook artifacts resolve from: the `repositories` list of the kitchen's `cook.toml`, or Maven
 * Central when the kitchen has none. An entry is a Maven-layout repository, an `https://` URL or a path relative to the
 * kitchen, or a git repository, `{ git = "<url>", dir = "<dir>" }`. They are tried in order and the first that holds
 * the artifact supplies it.
 */
final class CookbookRepositories {
  static final URI CENTRAL = URI.create('https://repo.maven.apache.org/maven2/')

  final List<CookbookRepository> repositories
  private final String origin

  private CookbookRepositories(List<CookbookRepository> repositories, String origin) {
    this.repositories = repositories
    this.origin = origin
  }

  /**
   * The repositories that `cook.toml` in `kitchen` lists, or Maven Central without that file. Git repositories keep
   * the tags they fetch in `cache`.
   */
  static CookbookRepositories at(File kitchen, File cache = defaultCache()) {
    def manifest = new File(kitchen, 'cook.toml')
    def central = [new MavenRepository(CENTRAL)] as List<CookbookRepository>
    if (!manifest.isFile()) {
      return new CookbookRepositories(central, "Maven Central; no cook.toml in ${kitchen} lists other repositories")
    }
    def toml = Toml.parse(manifest.toPath())
    if (toml.hasErrors()) {
      throw new IllegalArgumentException("Cannot read ${manifest}: ${toml.errors().first()}")
    }
    if (!toml.contains('repositories')) {
      return new CookbookRepositories(central, "Maven Central; ${manifest} lists no repositories")
    }
    if (!toml.isArray('repositories')) {
      throw misshapen(manifest)
    }
    def array = toml.getArray('repositories')
    List<CookbookRepository> repositories = (0..<array.size()).collect { index ->
      def entry = array.get(index)
      if (entry instanceof String) {
        return maven(entry, kitchen, manifest)
      }
      if (entry instanceof TomlTable) {
        return git(entry, kitchen, manifest, cache)
      }
      throw misshapen(manifest)
    }
    new CookbookRepositories(repositories, "the repositories in ${manifest}: ${repositories.join(', ')}")
  }

  /** `$XDG_CACHE_HOME/the-cook/git`, or `~/.cache/the-cook/git` when that variable is unset. */
  static File defaultCache() {
    new File(System.getenv('XDG_CACHE_HOME') ?: new File(System.getProperty('user.home'), '.cache').path, 'the-cook/git')
  }

  /** The artifact that `coordinates` name, read from the first repository holding it. */
  CookbookArtifact artifact(Coordinates coordinates) {
    for (CookbookRepository repository : repositories) {
      def artifact = repository.artifact(coordinates)
      if (artifact != null) {
        return artifact
      }
    }
    throw new IllegalArgumentException(
    "${coordinates.artifactAddress} not found in ${origin}; publish it there, or list its repository in cook.toml")
  }

  private static IllegalArgumentException misshapen(File manifest) {
    new IllegalArgumentException("${manifest}: repositories must be a list of https:// URLs, paths and " +
        '{ git = "<url>", dir = "<dir>" } tables')
  }

  private static MavenRepository maven(String entry, File kitchen, File manifest) {
    def uri = kitchen.toPath().toAbsolutePath().toUri().resolve(entry.endsWith('/') ? entry : "${entry}/")
    if (!(uri.scheme in ['https', 'file'])) {
      throw new IllegalArgumentException("${manifest}: repository ${entry} must be an https:// URL or a path")
    }
    new MavenRepository(uri)
  }

  private static GitRepository git(TomlTable entry, File kitchen, File manifest, File cache) {
    if (!entry.isString('git') || !(entry.keySet() - ['git', 'dir']).isEmpty() || (entry.contains('dir') && !entry.isString('dir'))) {
      throw new IllegalArgumentException("${manifest}: a git repository is { git = \"<url>\", dir = \"<dir>\" }, with dir optional")
    }
    def url = entry.getString('git')
    if (url ==~ /(?i)(http|git):\/\/.*/) {
      throw new IllegalArgumentException("${manifest}: git repository ${url} must use https://, ssh or a path; " +
      'http:// and git:// are not authenticated')
    }
    // As git does, a colon before the first slash makes `host:path` an ssh address rather than a path.
    if (!url.contains('://') && !(url ==~ /[^\/]+:.*/)) {
      url = kitchen.toPath().toAbsolutePath().resolve(url).normalize().toString()
    }
    def dir = entry.getString('dir')?.replaceAll(/\/+$/, '') ?: null
    if (dir != null && (dir.startsWith('/') || '..' in dir.split('/'))) {
      throw new IllegalArgumentException("${manifest}: dir ${dir} must be a directory inside the git repository")
    }
    new GitRepository(url, dir, cache)
  }
}

/** A repository that cookbook artifacts resolve from. Its `toString` names it in messages. */
interface CookbookRepository {
  /** The artifact that `coordinates` name, or `null` when this repository does not hold it. */
  CookbookArtifact artifact(Coordinates coordinates)
}

/** A Maven-layout repository at an `https://` or `file:` URI, holding one jar per artifact. */
final class MavenRepository implements CookbookRepository {
  private static final int TIMEOUT_MILLIS = 30_000

  final URI uri

  MavenRepository(URI uri) {
    this.uri = uri
  }

  CookbookArtifact artifact(Coordinates coordinates) {
    def jar = read(uri.resolve(coordinates.jarPath))
    jar == null ? null : CookbookArtifact.fromZip(coordinates, jar, coordinates.packagePath)
  }

  @Override String toString() {
    uri.toString()
  }

  private static byte[] read(URI jar) {
    try {
      def connection = jar.toURL().openConnection()
      connection.connectTimeout = TIMEOUT_MILLIS
      connection.readTimeout = TIMEOUT_MILLIS
      return connection.inputStream.withCloseable { it.readAllBytes() }
    } catch (FileNotFoundException ignored) {
      return null
    } catch (IOException error) {
      throw new IllegalArgumentException("Cannot read ${jar}: ${error.message}", error)
    }
  }
}
