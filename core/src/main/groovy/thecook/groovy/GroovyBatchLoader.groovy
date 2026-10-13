package thecook.groovy

import groovy.transform.CompileStatic
import java.nio.file.Path
import thecook.application.CookbookLoader
import thecook.rendering.RenderedFile

@CompileStatic
class GroovyBatchLoader implements CookbookLoader {
  @Override
  List<RenderedFile> load(Path repository, Path script, List<Path> libraries, Map<String, String> preferences) {
    new RepositoryScript(libraries.collect { Path library -> library.toFile() })
    .evaluate(script.toFile(), repository.toFile(), preferences).outputs
  }
}
