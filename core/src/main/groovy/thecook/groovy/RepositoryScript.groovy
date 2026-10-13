package thecook.groovy

import groovy.transform.CompileStatic
import thecook.model.Batch
import thecook.composition.Component
import thecook.composition.Kitchen
import thecook.cookbook.Cookbook
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
      String relative = new File(cookbook, 'recipes').toPath().relativize(script.absoluteFile.parentFile.toPath()).toString().replace(File.separator, '/')
      if (!script.isFile() && !book.classes.getResource('META-INF/the-cook/recipes/' + relative + '/recipe.groovy'))
        throw new IllegalArgumentException('Recipe not found: ' + relative)
      book.recipe(relative, kitchen, preferences).mount()
      return kitchen.render()
    }
    File directory = script.absoluteFile.parentFile
    Closure<RenderTree> delegate = { String relative, Map arguments = preferences ->
      evaluate(new File(directory, relative), repository, arguments)
    }
    Object result = new GroovyShell(classes, new Binding([
      preferences: preferences, scriptDirectory: directory,
      repositoryDirectory: repository.absoluteFile, evaluateRecipe: delegate,
      book: book, kitchen: kitchen
    ])).evaluate(script)
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
    if (script.name != 'recipe.groovy') return null
    File parent = script.absoluteFile.parentFile
    while (parent != null) {
      if (parent.name == 'recipes') return parent.parentFile
      parent = parent.parentFile
    }
    null
  }
}
