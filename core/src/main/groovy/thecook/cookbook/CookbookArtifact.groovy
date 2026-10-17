package thecook.cookbook

import thecook.filesystem.Repository

import java.util.zip.ZipInputStream

/**
 * The files of one cookbook artifact: its README, its recipes, named in lower case, and its models, named in upper
 * case, each a `.groovy` file at the top of the artifact.
 */
final class CookbookArtifact {
  final Coordinates coordinates
  /**
   * The sha256 of the artifact's sources: of one line `<sha256 of the file>  <path>` per file, in the byte order of the
   * paths, so a jar and a git tag holding the same files have the same hash.
   */
  final String sha256
  private final Map<String, byte[]> files

  /** `files` holds the artifact's files keyed by their path below the artifact's directory. */
  CookbookArtifact(Coordinates coordinates, Map<String, byte[]> files) {
    this.coordinates = coordinates
    this.files = files
    this.sha256 = sourcesSha256(files)
  }

  /** The artifact whose files are the entries of the ZIP archive `zip` below `directory`. */
  static CookbookArtifact fromZip(Coordinates coordinates, byte[] zip, String directory) {
    Map<String, byte[]> files = [:]
    new ZipInputStream(new ByteArrayInputStream(zip)).withCloseable { entries ->
      for (def entry = entries.nextEntry; entry != null; entry = entries.nextEntry) {
        if (!entry.directory && entry.name.startsWith(directory)) {
          files[entry.name.substring(directory.length())] = entries.readAllBytes()
        }
      }
    }
    new CookbookArtifact(coordinates, files)
  }

  /** The artifact's front page, or `null` when it holds no `README.md`. */
  String getReadme() {
    text('README.md')
  }

  List<String> getRecipes() {
    sources().findAll { Character.isLowerCase(it.charAt(0)) }
  }

  List<String> getModels() {
    sources().findAll { Character.isUpperCase(it.charAt(0)) }
  }

  /** The source of the recipe `name`, rejecting a name the artifact does not hold and listing the ones it does. */
  String recipeSource(String name) {
    if (!(name in recipes)) {
      throw new IllegalArgumentException(
      "No recipe ${name} in ${coordinates.artifactAddress}; its recipes are: ${recipes.join(', ') ?: 'none'}")
    }
    text("${name}.groovy")
  }

  /** The source of the model `name`. */
  String modelSource(String name) {
    text("${name}.groovy")
  }

  /** The text of the packaged file at `path`, or `null` when the artifact has none. */
  String resource(String path) {
    text(path)
  }

  /** Every packaged file under the directory `path`, keyed by its path below it. */
  Map<String, String> resources(String path) {
    def prefix = path.endsWith('/') ? path : "${path}/"
    files.keySet().findAll { it.startsWith(prefix) }.sort().collectEntries { [(it.substring(prefix.length())): text(it)] }
  }

  private List<String> sources() {
    files.keySet().findAll { !it.contains('/') && it.endsWith('.groovy') }*.replaceAll(/\.groovy$/, '').sort()
  }

  private static String sourcesSha256(Map<String, byte[]> files) {
    def paths = files.keySet().sort { a, b -> Arrays.compareUnsigned(a.getBytes('UTF-8'), b.getBytes('UTF-8')) }
    Repository.digest(paths.collect { "${Repository.digest(files[it])}  ${it}\n" }.join().getBytes('UTF-8'))
  }

  private String text(String path) {
    files[path] == null ? null : new String(files[path], 'UTF-8')
  }
}
