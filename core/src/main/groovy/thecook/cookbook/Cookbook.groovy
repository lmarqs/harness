package thecook.cookbook

import groovy.json.JsonOutput
import org.codehaus.groovy.control.SourceUnit
import thecook.composition.Kitchen
import thecook.composition.Component
import thecook.filesystem.TargetPath
import thecook.model.RecipeArguments
import thecook.model.RecipeDeclaration

// Cookbook files are trusted executable Groovy; the core knows no tool names.
class Cookbook {
  final File directory
  final GroovyClassLoader classes
  private final Map<String, Class> types = [:]

  Cookbook(File directory, List<File> artifacts = []) {
    this.directory = directory.canonicalFile
    classes = new GroovyClassLoader(getClass().classLoader)
    artifacts.each { classes.addURL(it.toURI().toURL()) }
  }
  Object script(String relative) {
    new GroovyShell(classes).evaluate(text(relative), relative)
  }
  Class type(String relative) {
    TargetPath.of(relative)
    if (types.containsKey(relative)) return types[relative]
    String content = text(relative)
    def sourceUnit = SourceUnit.create(relative, content)
    sourceUnit.parse(); sourceUnit.completePhase(); sourceUnit.nextPhase(); sourceUnit.convert()
    Class resolved
    try {
      resolved = classes.loadClass(sourceUnit.AST.mainClassName)
    }
    catch (ClassNotFoundException missing) {
      resolved = classes.getResource('META-INF/the-cook/' + relative) ?
          classes.parseClass(content, relative) : classes.parseClass(source(relative))
    }
    types[relative] = resolved
    resolved
  }
  String text(String relative) {
    TargetPath.of(relative)
    if (new File(directory, relative).isFile()) return source(relative).getText('UTF-8')
    def resource = classes.getResource('META-INF/the-cook/' + relative)
    if (resource) return resource.getText('UTF-8')
    source(relative).getText('UTF-8')
  }
  private File source(String relative) {
    TargetPath.of(relative)
    def file = new File(directory, relative).canonicalFile
    if (!file.toPath().startsWith(directory.toPath()) || !file.isFile())
      throw new IllegalArgumentException("Cookbook source not found: ${relative}")
    file
  }
  Object ingredient(String name, Kitchen kitchen, Map options = [:]) {
    factory("ingredients/${name}/ingredient.groovy", kitchen, options)
  }
  Object station(String name, Kitchen kitchen, Map options = [:]) {
    factory("stations/${name}/station.groovy", kitchen, options)
  }
  RecipeDeclaration declaration(String name) {
    declarationFile("recipes/${name}/recipe.groovy", name)
  }
  RecipeDeclaration declarationFile(String relative, String name) {
    def definition = script(relative)
    if (!(definition instanceof DeclaredRecipe))
      throw new IllegalArgumentException("Recipe has no argument declaration: ${name}")
    if (name != null && definition.declaration.name != name)
      throw new IllegalArgumentException("Recipe identity does not match: ${name}")
    definition.declaration
  }
  Component recipe(String name, Kitchen kitchen, Map config = [:]) {
    recipeFile("recipes/${name}/recipe.groovy", name, kitchen, config)
  }
  Component recipeFile(String relative, String name, Kitchen kitchen, Map config = [:]) {
    def component = recipeComponent(factory(relative, name, kitchen, config), kitchen)
    component.definition.provenance = provenance(relative, config)
    component
  }
  Component recipeValue(Object definition, Kitchen kitchen, Map config = [:], String source = '<direct recipe>') {
    def component = recipeComponent(factoryDefinition(definition, '<direct recipe>', null, kitchen, config), kitchen)
    component.definition.provenance = provenance(source, config)
    component
  }
  private static Map provenance(String recipe, Map config) {
    [recipe: recipe, inputs: config.keySet().sort().collectEntries { key ->
      def value = config[key]
      [(key.toString()): value instanceof Map || value instanceof List ?
          JsonOutput.toJson(canonicalInput(value)) : String.valueOf(value)]
    }]
  }
  private static Object canonicalInput(Object value) {
    if (value instanceof Map) return value.keySet().sort().collectEntries { key -> [(key.toString()): canonicalInput(value[key])] }
    if (value instanceof List) return value.collect { canonicalInput(it) }
    value
  }
  private Component recipeComponent(Object result, Kitchen kitchen) {
    if (!(result instanceof Component) || !result.kitchen.is(kitchen))
      throw new IllegalArgumentException('Recipe factory must return a component from its kitchen')
    result
  }
  private Object factory(String relative, Kitchen kitchen, Map options) {
    factory(relative, null, kitchen, options)
  }
  private Object factory(String relative, String name, Kitchen kitchen, Map options) {
    factoryDefinition(script(relative), relative, name, kitchen, options)
  }
  private Object factoryDefinition(Object definition, String relative, String name, Kitchen kitchen, Map options) {
    if (definition instanceof DeclaredRecipe) {
      if (name != null && definition.declaration.name != name)
        throw new IllegalArgumentException("Recipe identity does not match: ${name}")
      def raw = options.collectEntries { key, value ->
        [(key.toString()): value instanceof List ? value.collect { it.toString() } : [value.toString()]]
      }
      return definition.factory.call(kitchen, this, RecipeArguments.validate(definition.declaration, raw))
    }
    if (!(definition instanceof Closure)) throw new IllegalArgumentException("Cookbook definition must return a factory: ${relative}")
    if (!(options instanceof RawRecipeArguments)) return definition.call(kitchen, this, options)
    def scalar = options.collectEntries { key, value ->
      if (value instanceof List) {
        if (value.size() != 1) throw new IllegalArgumentException("Argument may only be supplied once: ${key}")
        return [(key): value[0]]
      }
      [(key): value]
    }
    definition.call(kitchen, this, scalar)
  }
}
