package thecook.cookbook

import org.codehaus.groovy.control.SourceUnit
import thecook.composition.Kitchen
import thecook.filesystem.TargetPath

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
  Object recipe(String name, Kitchen kitchen, Map config = [:]) {
    factory("recipes/${name}/recipe.groovy", kitchen, config)
  }
  private Object factory(String relative, Kitchen kitchen, Map options) {
    def factory = script(relative)
    if (!(factory instanceof Closure)) throw new IllegalArgumentException("Cookbook definition must return a factory: ${relative}")
    factory.call(kitchen, this, options)
  }
}
