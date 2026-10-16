package thecook.cookbook

/**
 * The files of one cookbook artifact, read from its jar: its README, its recipes, named in lower case, and its
 * models, named in upper case, each a `.groovy` file at the top of the artifact.
 */
final class CookbookArtifact {
  final Coordinates coordinates
  private final Map<String, byte[]> files

  /** `files` holds the artifact's files keyed by their path below its package path. */
  CookbookArtifact(Coordinates coordinates, Map<String, byte[]> files) {
    this.coordinates = coordinates
    this.files = files
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

  private List<String> sources() {
    files.keySet().findAll { !it.contains('/') && it.endsWith('.groovy') }*.replaceAll(/\.groovy$/, '').sort()
  }

  private String text(String path) {
    files[path] == null ? null : new String(files[path], 'UTF-8')
  }
}
