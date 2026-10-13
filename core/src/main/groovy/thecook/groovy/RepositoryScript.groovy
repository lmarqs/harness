package thecook.groovy

import groovy.transform.CompileStatic
import thecook.model.Batch

/** Repository authors may use dynamic Groovy over the same compiled contracts. */
@CompileStatic
class RepositoryScript {
  private final GroovyClassLoader classes

  RepositoryScript(List<File> artifacts = []) {
    classes = new GroovyClassLoader(RepositoryScript.classLoader)
    for (File artifact : artifacts) classes.addURL(artifact.toURI().toURL())
  }

  Batch evaluate(File script, File repository, Map preferences = [:]) {
    File directory = script.absoluteFile.parentFile
    Closure<Batch> delegate = { String relative, Map arguments = preferences ->
      evaluate(new File(directory, relative), repository, arguments)
    }
    Object result = new GroovyShell(classes, new Binding([
      preferences: preferences, scriptDirectory: directory,
      repositoryDirectory: repository.absoluteFile, evaluateRecipe: delegate
    ])).evaluate(script)
    if (!(result instanceof Batch)) throw new IllegalArgumentException('Repository script must return a Batch')
    (Batch) result
  }
}
