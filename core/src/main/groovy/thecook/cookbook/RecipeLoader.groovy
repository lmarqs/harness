package thecook.cookbook

import groovy.transform.CompileStatic

import java.nio.file.Files
import java.nio.file.Path

/**
 * Resolves recipe operands for one invocation: coordinates from the kitchen's repositories, and a path inside the
 * kitchen with models from the cookbook releases its `@cookbook` annotations name. Each artifact is read and its models
 * compiled once.
 */
final class RecipeLoader {
  private final Path kitchen
  private final CookbookRepositories repositories
  private final Map<String, CookbookArtifact> artifacts = [:]
  private final Map<String, ClassLoader> models = [:]
  private final Map<ClassLoader, CookbookArtifact> modelArtifacts = [:]

  RecipeLoader(File kitchen, CookbookRepositories repositories) {
    this.kitchen = kitchen.toPath().toRealPath()
    this.repositories = repositories
  }

  /** The recipe that `operand` names: coordinates when written as coordinates, otherwise a path in the kitchen. */
  LoadedRecipe load(String operand) {
    Coordinates.written(operand) ? fromCoordinates(Coordinates.parse(operand)) : fromPath(operand)
  }

  /**
   * The short name for markers and `cook.lock` of the recipe `operand` names, read from the operand alone: coordinates
   * without their version, or the path's place in `kitchen` without `.groovy`.
   */
  static String name(Path kitchen, String operand) {
    Coordinates.written(operand) ?
        Coordinates.parse(operand).recipeName :
        relative(kitchen, operand).replaceAll(/\.groovy$/, '')
  }

  /** What a model of `type` reads: its own artifact's packaged files, and `draft` as the current content. */
  ModelReads reads(Class type, Draft draft) {
    def artifact = modelArtifacts[type.classLoader]
    [
      resource: { String path -> artifact?.resource(path) },
      resources: { String path -> artifact?.resources(path) },
      current: { draft.read(it) },
    ] as ModelReads
  }

  private LoadedRecipe fromCoordinates(Coordinates coordinates) {
    def artifact = artifact(coordinates)
    def recipe = coordinates.recipe ?: coordinates.artifact

    if (!coordinates.recipe && !(recipe in artifact.recipes)) {
      throw new IllegalArgumentException("${coordinates} has no recipe named ${recipe} to cook by default; name one: " +
      artifact.recipes.collect { coordinates.recipeAddress(it) }.join(', '))
    }

    def source = artifact.recipeSource(recipe)
    Map<String, String> resources = RecipeSource.resources(source, "${recipe}.groovy").collectEntries { path ->
      def text = artifact.resource(path)

      if (text == null) {
        throw new IllegalArgumentException("No file ${path} in ${coordinates.artifactAddress}")
      }

      [(path): text]
    }
    new LoadedRecipe(coordinates.recipeName, coordinates.recipeAddress(recipe),
        artifact.sha256, source, recipe, models(coordinates), resources)
  }

  // A path's short name is its place in the kitchen, so moving the file renames the recipe.
  private LoadedRecipe fromPath(String operand) {
    def place = relative(kitchen, operand)
    def path = kitchen.resolve(place)

    if (!Files.isRegularFile(path)) {
      throw new IllegalArgumentException("Recipe file not found: ${operand}")
    }

    if (!path.toRealPath().startsWith(kitchen)) {
      throw new IllegalArgumentException('the recipe is outside the kitchen; move it into the kitchen')
    }

    def bytes = Files.readAllBytes(path)
    def source = new String(bytes, 'UTF-8')
    def file = path.fileName.toString()
    Map<String, byte[]> sources = [(place): bytes]
    Map<String, String> resources = RecipeSource.resources(source, file).collectEntries { declared ->
      def resource = resource(path.parent, declared)
      def content = Files.readAllBytes(resource)
      sources[kitchen.relativize(resource).join('/')] = content
      [(declared): new String(content, 'UTF-8')]
    }
    def loaders = RecipeSource.cookbookArtifacts(source, file).collect { models(it) }
    new LoadedRecipe(name(kitchen, operand), place, CookbookArtifact.sourcesSha256(sources), source,
        file.replaceAll(/\.groovy$/, ''), loaders.size() == 1 ? loaders.first() : new ModelLoaders(loaders), resources)
  }

  // A resource resolves from the recipe's directory and, like the recipe, stays inside the kitchen.
  private Path resource(Path directory, String declared) {
    def path = directory.resolve(declared).normalize()

    if (!path.startsWith(kitchen)) {
      throw new IllegalArgumentException('the resource is outside the kitchen; move it into the kitchen')
    }

    if (!Files.isRegularFile(path)) {
      throw new IllegalArgumentException("Resource file not found: ${kitchen.relativize(path).join('/')}")
    }

    if (!path.toRealPath().startsWith(kitchen)) {
      throw new IllegalArgumentException('the resource is outside the kitchen; move it into the kitchen')
    }

    path
  }

  private static String relative(Path kitchen, String operand) {
    if (operand.contains('://')) {
      throw new IllegalArgumentException(
      'a URL is not a recipe; list its repository in cook.toml and cook the recipe by its coordinates')
    }

    def path = kitchen.resolve(operand).normalize()

    if (!path.startsWith(kitchen)) {
      throw new IllegalArgumentException('the recipe is outside the kitchen; move it into the kitchen')
    }

    kitchen.relativize(path)
        .collect { it.toString() }
        .join('/')
  }

  private CookbookArtifact artifact(Coordinates coordinates) {
    artifacts.computeIfAbsent(coordinates.artifactAddress) { repositories.artifact(coordinates) }
  }

  private ClassLoader models(Coordinates coordinates) {
    models.computeIfAbsent(coordinates.artifactAddress) {
      def artifact = artifact(coordinates)
      def loader = CookbookCompiler.models(artifact)
      modelArtifacts[loader] = artifact
      loader
    }
  }

  /** Loads a class from the first of several artifacts' model loaders that defines it. */
  @CompileStatic
  private static final class ModelLoaders extends ClassLoader {
    private final List<ClassLoader> loaders

    ModelLoaders(List<ClassLoader> loaders) {
      super(CookbookCompiler.classLoader)
      this.loaders = loaders
    }

    @Override
    protected Class<?> findClass(String name) {
      for (ClassLoader loader : loaders) {
        try {
          return loader.loadClass(name)
        } catch (ClassNotFoundException ignored) {
          continue
        }
      }

      throw new ClassNotFoundException(name)
    }
  }
}

/**
 * A resolved recipe: its short name for markers and `cook.lock`, the address the lock records as its source and the
 * review shows, the sha256 of its sources, its text and file name, the class loader its model imports resolve from,
 * and the text of each file its `@resource` lines name, keyed by the path as written.
 */
final class LoadedRecipe {
  final String name
  final String address
  final String sha256
  final String source
  final String fileName
  final ClassLoader models
  final Map<String, String> resources

  LoadedRecipe(String name, String address, String sha256, String source, String fileName, ClassLoader models,
  Map<String, String> resources) {
    this.name = name
    this.address = address
    this.sha256 = sha256
    this.source = source
    this.fileName = fileName
    this.models = models
    this.resources = resources
  }
}
