package thecook.cookbook

/**
 * The files of one cookbook artifact, read from its jar: its README, its recipes, named in lower case, and its
 * models, named in upper case, each a `.groovy` file at the top of the artifact.
 */
final class CookbookArtifact {
  final Coordinates coordinates
  /** The sha256 of the jar the files were read from. */
  final String sha256
  private final Map<String, byte[]> files

  /** `files` holds the artifact's files keyed by their path below its package path. */
  CookbookArtifact(Coordinates coordinates, Map<String, byte[]> files, String sha256 = null) {
    this.coordinates = coordinates
    this.files = files
    this.sha256 = sha256
  }

  /** The artifact's front page, or `null` when its jar holds no `README.md`. */
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

  private String text(String path) {
    files[path] == null ? null : new String(files[path], 'UTF-8')
  }
}
