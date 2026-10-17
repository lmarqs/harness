package thecook.cookbook

import org.tomlj.Toml
import org.tomlj.TomlArray

/**
 * The repositories that cookbook artifacts resolve from: the `repositories` list of the kitchen's `cook.toml`, or Maven
 * Central when the kitchen has none. An entry is a Maven-layout repository, an `https://` URL or a path relative to the
 * kitchen. They are tried in order and the first that holds the artifact supplies it.
 */
final class CookbookRepositories {
  static final URI CENTRAL = URI.create('https://repo.maven.apache.org/maven2/')

  final List<CookbookRepository> repositories
  private final String origin

  private CookbookRepositories(List<CookbookRepository> repositories, String origin) {
    this.repositories = repositories
    this.origin = origin
  }

  /** The repositories that `cook.toml` in `kitchen` lists, or Maven Central without that file. */
  static CookbookRepositories at(File kitchen) {
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
    if (!toml.isArray('repositories') || !entries(toml.getArray('repositories')).every { it instanceof String }) {
      throw new IllegalArgumentException("${manifest}: repositories must be a list of https:// URLs or paths")
    }
    def base = kitchen.toPath().toAbsolutePath().toUri()
    List<CookbookRepository> repositories = entries(toml.getArray('repositories')).collect { String entry ->
      def uri = base.resolve(entry.endsWith('/') ? entry : "${entry}/")
      if (!(uri.scheme in ['https', 'file'])) {
        throw new IllegalArgumentException("${manifest}: repository ${entry} must be an https:// URL or a path")
      }
      new MavenRepository(uri)
    }
    new CookbookRepositories(repositories, "the repositories in ${manifest}: ${repositories.join(', ')}")
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

  private static List<Object> entries(TomlArray array) {
    (0..<array.size()).collect { array.get(it) }
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
