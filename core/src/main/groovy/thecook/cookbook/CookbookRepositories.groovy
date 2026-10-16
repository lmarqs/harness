package thecook.cookbook

import org.tomlj.Toml
import org.tomlj.TomlArray
import thecook.filesystem.Repository

import java.util.zip.ZipInputStream

/**
 * The Maven-layout repositories that cookbook artifacts resolve from: the `repositories` list of the kitchen's
 * `cook.toml`, or Maven Central when the kitchen has none. An entry is an `https://` URL or a path relative to the
 * kitchen. They are tried in order and the first that holds the jar supplies it.
 */
final class CookbookRepositories {
  static final URI CENTRAL = URI.create('https://repo.maven.apache.org/maven2/')
  private static final int TIMEOUT_MILLIS = 30_000

  final List<URI> uris
  private final String origin

  private CookbookRepositories(List<URI> uris, String origin) {
    this.uris = uris
    this.origin = origin
  }

  /** The repositories that `cook.toml` in `kitchen` lists, or Maven Central without that file. */
  static CookbookRepositories at(File kitchen) {
    def manifest = new File(kitchen, 'cook.toml')
    if (!manifest.isFile()) {
      return new CookbookRepositories([CENTRAL], "Maven Central; no cook.toml in ${kitchen} lists other repositories")
    }
    def toml = Toml.parse(manifest.toPath())
    if (toml.hasErrors()) {
      throw new IllegalArgumentException("Cannot read ${manifest}: ${toml.errors().first()}")
    }
    if (!toml.contains('repositories')) {
      return new CookbookRepositories([CENTRAL], "Maven Central; ${manifest} lists no repositories")
    }
    if (!toml.isArray('repositories') || !entries(toml.getArray('repositories')).every { it instanceof String }) {
      throw new IllegalArgumentException("${manifest}: repositories must be a list of https:// URLs or paths")
    }
    def base = kitchen.toPath().toAbsolutePath().toUri()
    def uris = entries(toml.getArray('repositories')).collect { String entry ->
      def uri = base.resolve(entry.endsWith('/') ? entry : "${entry}/")
      if (!(uri.scheme in ['https', 'file'])) {
        throw new IllegalArgumentException("${manifest}: repository ${entry} must be an https:// URL or a path")
      }
      uri
    }
    new CookbookRepositories(uris, "the repositories in ${manifest}: ${uris.join(', ')}")
  }

  /** The artifact that `coordinates` name, read from the first repository holding its jar. */
  CookbookArtifact artifact(Coordinates coordinates) {
    for (URI repository : uris) {
      def jar = read(repository.resolve(coordinates.jarPath))
      if (jar != null) {
        return new CookbookArtifact(coordinates, unpack(jar, coordinates.packagePath), Repository.digest(jar))
      }
    }
    throw new IllegalArgumentException(
    "${coordinates.artifactAddress} not found in ${origin}; publish it there, or list its repository in cook.toml")
  }

  private static List<Object> entries(TomlArray array) {
    (0..<array.size()).collect { array.get(it) }
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

  private static Map<String, byte[]> unpack(byte[] jar, String packagePath) {
    Map<String, byte[]> files = [:]
    new ZipInputStream(new ByteArrayInputStream(jar)).withCloseable { zip ->
      for (def entry = zip.nextEntry; entry != null; entry = zip.nextEntry) {
        if (!entry.directory && entry.name.startsWith(packagePath)) {
          files[entry.name.substring(packagePath.length())] = zip.readAllBytes()
        }
      }
    }
    files
  }
}
