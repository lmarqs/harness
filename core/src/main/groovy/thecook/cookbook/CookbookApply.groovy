package thecook.cookbook

import thecook.cookbook.api.Ingredient
import thecook.filesystem.TargetPath
import thecook.formats.BlockDocument
import thecook.formats.Document
import thecook.formats.Documents
import thecook.reconciliation.Permissions
import thecook.rendering.BlockLocation
import thecook.rendering.KeyLocation

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

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
        def name = "${coordinates.group}:${coordinates.artifact}:${recipe}"
        if (!applied.add(name)) {
          throw new IllegalArgumentException("${name} is applied twice; apply it once per invocation")
        }
        def loader = models.computeIfAbsent(coordinates.artifactAddress) { CookbookCompiler.models(artifact) }
        def compiled = CookbookCompiler.recipe(source, recipe, loader)
        def reads = [resource: { artifact.resource(it) }, resources: { artifact.resources(it) },
          current: { draft.read(it) }] as ModelReads
        def ingredients = RecipeRun.run(compiled, request.arguments) { Class type, String at -> model(type, at, reads) }
        ingredients*.getClass().unique().each { checkPristine(it, reads) }
        def declarations = ingredients.collectMany { Declarations.of(it) }
        apply(name, declarations, draft, lock)
        lock.record(name, coordinates.recipeAddress(recipe), artifact.sha256, declarations*.target(name).findAll())
        recipes.add(new Applied(coordinates.recipeAddress(recipe), RecipeSource.texts(source, "${recipe}.groovy")?.yieldText))
      } catch (RuntimeException error) {
        throw new IllegalArgumentException("${label}: ${error.message ?: error.class.simpleName}", error)
      }
    }
    draft.write(CookLock.PATH, lock.render())
    new Proposal(draft, recipes)
  }

  private static Object model(Class type, String at, ModelReads reads) {
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

  private static void checkPristine(Class type, ModelReads reads) {
    def declared = Declarations.of(model(type, '.', reads))
    if (declared) {
      throw new IllegalArgumentException("${declared.first().member} declares a ${declared.first().kind} of " +
      "${declared.first().file} on a new instance; a model declares only what a recipe asks for")
    }
  }

  private static void apply(String name, List<Declaration> declarations, Draft draft, CookLock lock) {
    def owned = declarations.findAll { it.target(name) }
    owned.eachWithIndex { first, index ->
      def second = owned.drop(index + 1).find { it.target(name).overlaps(first.target(name)) }
      if (second) {
        throw new IllegalArgumentException("${first.member} and ${second.member} both declare ${first.target(name)}")
      }
    }
    declarations.findAll { it.kind == 'seed' }.each { seed ->
      if (draft.read(seed.file) == null) {
        draft.write(seed.file, seed.value)
      }
    }
    def targets = owned*.target(name)
    lock.owned(name).findAll { !(it in targets) }.each { withdraw(it, draft) }
    owned.each { write(it, name, draft) }
    declarations.findAll { it.kind == 'delete' }.each { deletion ->
      def owner = lock.ownerOf(new Target('file', deletion.file, null), null) ?:
          (owned.any { it.file == deletion.file } ? name : null)
      if (owner) {
        throw new IllegalArgumentException("${deletion.member} deletes ${deletion.file}, which ${owner} owns")
      }
      draft.delete(deletion.file)
    }
  }

  private static void withdraw(Target target, Draft draft) {
    switch (target.kind) {
      case 'section':
        def comment = markerComment(draft.read(target.file), target.address.toString())
        if (comment != null) {
          edit(draft, target.file, 'section', comment) { Document document ->
            document.remove(target.address.toString(), new BlockLocation())
          }
        }
        break
      case 'key':
        edit(draft, target.file, 'key') { Document document ->
          document.remove(target.address.toString(), new KeyLocation((List<String>) target.address))
        }
        break
      default:
        draft.delete(target.file)
    }
  }

  /**
   * The comment that opens the begin marker of `id` in `text`, or `null` without one. The lock does not record a
   * section's comment syntax, so withdrawing a section reads it from the file.
   */
  private static String markerComment(String text, String id) {
    def marker = text?.readLines()?.find { it.contains("cook:begin ${id}") }
    marker == null ? null : marker.substring(0, marker.indexOf('cook:begin ')).trim()
  }

  private static void write(Declaration declaration, String name, Draft draft) {
    switch (declaration.kind) {
      case 'section':
        def marker = declaration.target(name).address.toString()
        edit(draft, declaration.file, 'section', declaration.comment) { Document document ->
          document.write(marker, new BlockLocation(), document.render(((List) declaration.value).join('\n')))
        }
        break
      case 'key':
        if (!format(declaration.file)) {
          throw new IllegalArgumentException("${declaration.member} declares a key in ${declaration.file}, " +
          'which is not a JSON, TOML or YAML file')
        }
        edit(draft, declaration.file, 'key') { Document document ->
          def location = new KeyLocation((List<String>) declaration.address)
          def rendered = document.render(declaration.value)
          if (!same(declaration.file, document.read(location.path.join('.'), location), rendered)) {
            document.write(location.path.join('.'), location, rendered)
          }
        }
        break
      case 'link':
        draft.link(declaration.file, declaration.value)
        break
      default:
        draft.write(declaration.file, declaration.value, declaration.mode ? Permissions.parse(declaration.mode) : null)
    }
  }

  /**
   * Opens `file` for a `section` or a `key`, applies `change` and puts the result back; a file left empty is deleted.
   */
  private static void edit(Draft draft, String file, String part, String comment = null, Closure change) {
    String text = draft.read(file)
    Document document = part == 'key' ? new Documents().open(format(file), text ?: '') : sections(file, text ?: '', comment)
    change(document)
    if (document.empty) {
      draft.delete(file)
    } else if (text == null && part == 'key' && format(file) == 'json') {
      draft.write(file, pretty(new JsonSlurper().parseText(document.text), '') + '\n')
    } else {
      draft.write(file, document.text)
    }
  }

  private static BlockDocument sections(String file, String text, String comment) {
    if (file ==~ /(?i).*\.(md|markdown)/ || comment == '<!--') {
      return new BlockDocument(text, '<!-- ', ' -->')
    }
    new BlockDocument(text, "${comment ?: '#'} ", '')
  }

  /** Whether a key already holds `rendered`, so its text, and the file's formatting, stay as they are. */
  private static boolean same(String file, String existing, String rendered) {
    if (existing == null) {
      return false
    }
    format(file) == 'json' ? new JsonSlurper().parseText(existing) == new JsonSlurper().parseText(rendered) : existing == rendered
  }

  private static String pretty(Object value, String indent) {
    def inner = indent + '  '
    if (value instanceof Map && value) {
      return '{\n' + value.collect { key, item -> inner + JsonOutput.toJson(key.toString()) + ': ' + pretty(item, inner) }
      .join(',\n') + '\n' + indent + '}'
    }
    if (value instanceof List && value) {
      return '[\n' + value.collect { inner + pretty(it, inner) }.join(',\n') + '\n' + indent + ']'
    }
    JsonOutput.toJson(value)
  }

  private static String format(String file) {
    def extension = file.contains('.') ? file.substring(file.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : ''
    [json: 'json', toml: 'toml', yaml: 'yaml', yml: 'yaml'][extension]
  }
}

/** One recipe to apply: its operand as written, its coordinates and its arguments by param name. */
final class RecipeRequest {
  final String operand
  final Coordinates coordinates
  final Map<String, List<String>> arguments

  RecipeRequest(String operand, Coordinates coordinates, Map<String, List<String>> arguments) {
    this.operand = operand
    this.coordinates = coordinates
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
