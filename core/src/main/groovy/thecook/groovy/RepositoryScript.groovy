package thecook.groovy

import groovy.transform.CompileStatic
import thecook.model.Batch
import thecook.composition.Component
import thecook.composition.Kitchen
import thecook.cookbook.Cookbook
import thecook.cookbook.DeclaredRecipe
import thecook.cookbook.RawRecipeArguments
import thecook.rendering.RenderTree

/** Repository authors may use dynamic Groovy over the same compiled contracts. */
@CompileStatic
class RepositoryScript {
  private final GroovyClassLoader classes
  private final List<File> artifacts

  RepositoryScript(List<File> artifacts = []) {
    this.artifacts = List.copyOf(artifacts)
    classes = new GroovyClassLoader(RepositoryScript.classLoader)
    for (File artifact : artifacts) classes.addURL(artifact.toURI().toURL())
  }

  RenderTree evaluate(File script, File repository, Map preferences = [:]) {
    File cookbook = findCookbook(script)
    Kitchen kitchen = new Kitchen(repository)
    Cookbook book = new Cookbook(cookbook ?: new File(repository, 'cookbook'), artifacts)
    if (cookbook != null) {
      String relative = new File(cookbook, 'recipes').toPath().relativize(script.absoluteFile.toPath()).toString().replace(File.separator, '/')
      String name = relative.endsWith('/recipe.groovy') ? relative.substring(0, relative.length() - '/recipe.groovy'.length()) :
          relative.substring(0, relative.length() - '.groovy'.length())
      String source = 'recipes/' + relative
      if (!script.isFile() && !book.classes.getResource('META-INF/the-cook/' + source))
        throw new IllegalArgumentException('Recipe not found: ' + name)
      book.recipeFile(source, null, kitchen, preferences.values().every { it instanceof List } ?
      new RawRecipeArguments(preferences) : preferences).mount()
      return kitchen.render()
    }
    File directory = script.absoluteFile.parentFile
    Closure<RenderTree> delegate = { String relative, Map arguments = preferences ->
      evaluate(new File(directory, relative), repository, arguments)
    }
    Map scalarPreferences = preferences.collectEntries { key, value ->
      [(key): value instanceof List ? (value.isEmpty() ? '' : value[0]) : value]
    }
    Object result = new GroovyShell(classes, new Binding([
      preferences: scalarPreferences, scriptDirectory: directory,
      repositoryDirectory: repository.absoluteFile, evaluateRecipe: delegate,
      book: book, kitchen: kitchen
    ])).evaluate(script)
    if (result instanceof DeclaredRecipe || result instanceof Closure) {
      book.recipeValue(result, kitchen, preferences.values().every { it instanceof List } ?
      new RawRecipeArguments(preferences) : preferences, script.path).mount()
      return kitchen.render()
    }
    if (preferences.values().any { it instanceof List && it.size() != 1 })
      throw new IllegalArgumentException('Argument may only be supplied once to a repository script')
    if (result instanceof RenderTree) return result
    if (result instanceof Component) {
      if (!result.kitchen.is(kitchen)) throw new IllegalArgumentException('Recipe component belongs to another kitchen')
      result.mount()
      return kitchen.render()
    }
    if (!(result instanceof Batch)) throw new IllegalArgumentException('Repository script must return a Batch')
    RenderTree.from((Batch) result)
  }

  private static File findCookbook(File script) {
    File parent = script.absoluteFile.parentFile
    while (parent != null) {
      if (parent.name == 'recipes') return parent.parentFile
      parent = parent.parentFile
    }
    null
  }
}
