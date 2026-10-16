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
  private final ExistingSeed existing

  /** `existing` decides what a recipe's first apply does with a file its `existing = 'fail'` seed writes. */
  CookbookApply(File destination, ExistingSeed existing = ExistingSeed.REFUSE) {
    this.destination = destination
    this.existing = existing
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
    def seeds = new SeedOutcomes()
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
        apply(name, declared, draft, lock, seeds)
        lock.record(name, coordinates.recipeAddress(recipe), artifact.sha256, declared.owned*.target)
        def texts = RecipeSource.texts(source, "${recipe}.groovy")
        recipes.add(new Applied(coordinates.recipeAddress(recipe), texts?.confirm, texts?.yieldText))
      } catch (Exception error) {
        def cause = error instanceof InvocationTargetException && error.cause instanceof Exception ? error.cause : error
        throw new IllegalArgumentException("${label}: ${cause.message ?: cause.class.simpleName}", cause)
      }
    }
    seeds.refuse()
    draft.write(CookLock.PATH, lock.render())
    new Proposal(draft, recipes, seeds)
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

  private void apply(String name, Declared declared, Draft draft, CookLock lock, SeedOutcomes seeds) {
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
        seeds.existing(existing, seed, name, draft)
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

/** One recipe to apply: its coordinates as written and its arguments by param name, `null` for a bare `--name`. */
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

/** A recipe that ran: its address, the consequence to confirm before the writes and what to do after them. */
final class Applied {
  final String address
  final String confirm
  final String yieldText

  Applied(String address, String confirm, String yieldText) {
    this.address = address
    this.confirm = confirm
    this.yieldText = yieldText
  }
}

/** What a recipe's first apply does with a file its `existing = 'fail'` seed writes. */
enum ExistingSeed {
  /** Fails, naming the file. */
  REFUSE,
  /** Writes the seed over the file, or over the link at its path. */
  REPLACE,
  /** Leaves the file as it is. */
  KEEP
}

/** The existing files that seeds refused, replaced or kept during one invocation. */
final class SeedOutcomes {
  final Set<String> replaced = new TreeSet<>()
  final Set<String> kept = new TreeSet<>()
  private final Map<String, String> refused = new TreeMap<>()

  void existing(ExistingSeed choice, Seeding seed, String recipe, Draft draft) {
    if (choice == ExistingSeed.REPLACE) {
      draft.write(seed.file, seed.text)
      replaced.add(seed.file)
    } else if (choice == ExistingSeed.KEEP) {
      kept.add(seed.file)
    } else {
      refused[seed.file] = recipe
    }
  }

  /** Fails when seeds refused existing files, naming each file, its recipe and the options that resolve them. */
  void refuse() {
    if (refused.size() == 1) {
      def only = refused.entrySet().first()
      throw new IllegalArgumentException("${only.key} already exists; ${only.value} writes it from a template on its first apply. " +
      'Run with --force to replace it, or --skip to keep it.')
    }
    if (refused) {
      throw new IllegalArgumentException((['These files already exist, and their recipes write them from a template on their first apply:'] +
      refused.collect { file, recipe -> "  ${file} (${recipe})" } + ['Run with --force to replace them, or --skip to keep them.']).join('\n'))
    }
  }
}

/** The changes recipes propose, written by `commit`. */
final class Proposal {
  private final Draft draft
  private final SeedOutcomes seeds
  final List<Applied> recipes

  Proposal(Draft draft, List<Applied> recipes, SeedOutcomes seeds) {
    this.draft = draft
    this.recipes = recipes
    this.seeds = seeds
  }

  /** The paths that change, in path order. */
  List<String> getChanges() {
    draft.changes
  }

  /**
   * Each changed or kept path, in path order, with what happens to it: `create`, `update`, `delete`, `replace` for an
   * existing file a seed wrote over, or `keep` for one it left.
   */
  Map<String, String> getActions() {
    def changes = draft.changes
    (changes + seeds.kept).unique().sort().collectEntries { path ->
      [(path): !(path in changes) ? 'keep' : path in seeds.replaced ? 'replace' : draft.action(path)]
    }
  }

  /** The text of a changed `path` before the apply; `null` when it is created. */
  String before(String path) {
    draft.before(path)
  }

  /** The text of a changed `path` after the apply; `null` when it is deleted. */
  String after(String path) {
    draft.after(path)
  }

  /** Writes the changes. */
  void commit() {
    draft.commit()
  }
}
