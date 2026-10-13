package thecook.groovy

import groovy.transform.CompileStatic
import java.nio.file.Path
import thecook.application.CookbookLoader
import thecook.rendering.RenderedPlan

@CompileStatic
class GroovyBatchLoader implements CookbookLoader {
  @Override
  RenderedPlan load(Path repository, Path script, List<Path> libraries, Map<String, String> preferences) {
    def tree = new RepositoryScript(libraries.collect { Path library -> library.toFile() })
        .evaluate(script.toFile(), repository.toFile(), preferences)
    new RenderedPlan(tree.outputs, tree.applications, tree.provenance)
  }
}
