package thecook.cookbook

import thecook.composition.Kitchen
import thecook.filesystem.TargetPath

// Cookbook files are trusted executable Groovy; the core knows no tool names.
class Cookbook {
  final File directory
  final GroovyClassLoader classes

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
    if (classes.getResource('META-INF/the-cook/' + relative)) return classes.parseClass(text(relative), relative)
    classes.parseClass(source(relative))
  }
  String text(String relative) {
    TargetPath.of(relative)
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
  Object target(String name, Kitchen kitchen, Map options = [:]) {
    factory("targets/${name}/target.groovy", kitchen, options)
  }
  Object station(String name, Kitchen kitchen, Map options = [:]) {
    factory("stations/${name}/station.groovy", kitchen, options)
  }
  Object recipe(String name, Kitchen kitchen, Map config = [:]) {
    factory("recipes/${name}/recipe.groovy", kitchen, config)
  }
  private Object factory(String relative, Kitchen kitchen, Map options) {
    def factory = script(relative)
    if (!(factory instanceof Closure)) throw new IllegalArgumentException("Cookbook definition must return a factory: ${relative}")
    factory.call(kitchen, this, options)
  }
}
