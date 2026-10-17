package thecook.infra

import thecook.cookbook.Coordinates
import thecook.cookbook.CookbookArtifact
import thecook.cookbook.CookbookRepository

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
