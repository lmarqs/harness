package thecook.cookbook

import thecook.cookbook.api.Ingredient
import thecook.filesystem.TargetPath

import java.lang.reflect.InvocationTargetException

/**
 * Applies recipes by their coordinates, left to right, into one draft of the destination: each recipe withdraws what
 * it owned and no longer declares, then writes what it declares, taking ownership from the recipes that held it.
 * `cook.lock` records the result.
 */
final class CookbookApply {
  private final File destination

  CookbookApply(File destination) {
    this.destination = destination
  }

  /** Evaluates `requests` in order and returns the proposed changes, written only by `Proposal.commit`. */
  Proposal propose(List<RecipeRequest> requests) {
    def repositories = CookbookRepositories.at(destination)
    def draft = new Draft(destination)
    def lock = CookLock.parse(draft.read(CookLock.PATH))
    Map<String, CookbookArtifact> artifacts = [:]
    Map<String, ClassLoader> models = [:]
    Set<String> applied = []
    List<Applied> recipes = []
    requests.eachWithIndex { request, index ->
      def label = requests.size() > 1 ? "recipe ${index + 1} (${request.operand})" : request.operand
      try {
        def coordinates = request.coordinates
        def artifact = artifacts.computeIfAbsent(coordinates.artifactAddress) { repositories.artifact(coordinates) }
        def recipe = coordinates.recipe ?: coordinates.artifact
        if (!coordinates.recipe && !(recipe in artifact.recipes)) {
          throw new IllegalArgumentException("${coordinates} has no recipe named ${recipe} to apply by default; name one: " +
          artifact.recipes.collect { coordinates.recipeAddress(it) }.join(', '))
        }
        def source = artifact.recipeSource(recipe)
        String name = "${coordinates.group}:${coordinates.artifact}:${recipe}"
        if (!applied.add(name)) {
          throw new IllegalArgumentException("${name} is applied twice; apply it once per invocation")
        }
        def loader = models.computeIfAbsent(coordinates.artifactAddress) { CookbookCompiler.models(artifact) }
        def compiled = CookbookCompiler.recipe(source, recipe, loader)
        def reads = [resource: { artifact.resource(it) }, resources: { artifact.resources(it) },
          current: { draft.read(it) }] as ModelReads
        def ingredients = RecipeRun.run(compiled, request.arguments) { Class type, String at -> model(type, at, reads) }
        ingredients*.getClass().unique().each { checkPristine(it, reads, name) }
        def declared = Declarations.of(ingredients, name)
        apply(name, declared, draft, lock)
        lock.record(name, coordinates.recipeAddress(recipe), artifact.sha256, declared.owned*.target)
        recipes.add(new Applied(coordinates.recipeAddress(recipe), RecipeSource.texts(source, "${recipe}.groovy")?.yieldText))
      } catch (Exception error) {
        def cause = error instanceof InvocationTargetException && error.cause instanceof Exception ? error.cause : error
        throw new IllegalArgumentException("${label}: ${cause.message ?: cause.class.simpleName}", cause)
      }
    }
    draft.write(CookLock.PATH, lock.render())
    new Proposal(draft, recipes)
  }

  private static CookbookModel model(Class type, String at, ModelReads reads) {
    if (!type.getAnnotation(Ingredient) || !CookbookModel.isAssignableFrom(type)) {
      throw new IllegalArgumentException("${type.simpleName} is not a model; a model class is marked @Ingredient")
    }
    if (at != '.') {
      TargetPath.of(at)
    }
    def instance = (CookbookModel) type.getDeclaredConstructor().newInstance()
    CookbookModel.attach(instance, at, reads)
    instance
  }

  private static void checkPristine(Class type, ModelReads reads, String name) {
    def declared = Declarations.of([model(type, '.', reads)], name).all
    if (declared) {
      throw new IllegalArgumentException("${declared.first()} on a new instance; a model declares only what a recipe asks for")
    }
  }

  private static void apply(String name, Declared declared, Draft draft, CookLock lock) {
    def owned = declared.owned
    owned.eachWithIndex { first, index ->
      def second = owned.drop(index + 1).find { it.target.overlaps(first.target) }
      if (second) {
        throw new IllegalArgumentException("${first.member} and ${second.member} both declare ${first.target}")
      }
    }
    declared.seeds.each { seed ->
      if (draft.read(seed.file) == null) {
        draft.write(seed.file, seed.text)
      } else if (seed.failsOnExisting && !lock.applied(name)) {
        throw new IllegalArgumentException("${seed.file} already exists, and this recipe writes it from a template on its first " +
        'apply. Move the file away and apply again.')
      }
    }
    def targets = owned*.target
    lock.owned(name).findAll { !(it in targets) }.each { it.withdraw(draft) }
    owned.each { it.write(draft) }
    declared.deletes.each { deletion ->
      def owner = lock.ownerOf(new FileTarget(deletion.file), name) ?: (targets.any { it.file == deletion.file } ? name : null)
      if (owner) {
        throw new IllegalArgumentException("${deletion.member} deletes ${deletion.file}, which ${owner} owns")
      }
      draft.delete(deletion.file)
    }
  }
}

/** One recipe to apply: its coordinates as written and its arguments by param name. */
final class RecipeRequest {
  final String operand
  final Coordinates coordinates
  final Map<String, List<String>> arguments

  RecipeRequest(String operand, Map<String, List<String>> arguments) {
    this.operand = operand
    this.coordinates = Coordinates.parse(operand)
    this.arguments = arguments
  }
}

/** A recipe that ran: its address and what to do after the writes. */
final class Applied {
  final String address
  final String yieldText

  Applied(String address, String yieldText) {
    this.address = address
    this.yieldText = yieldText
  }
}

/** The changes recipes propose, written by `commit`. */
final class Proposal {
  private final Draft draft
  final List<Applied> recipes

  Proposal(Draft draft, List<Applied> recipes) {
    this.draft = draft
    this.recipes = recipes
  }

  /** The paths that change, in path order. */
  List<String> getChanges() {
    draft.changes
  }

  /** Writes the changes. */
  void commit() {
    draft.commit()
  }
}
