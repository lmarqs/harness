package thecook.groovy

import groovy.transform.CompileStatic
import java.nio.file.Path
import thecook.application.CookbookLoader
import thecook.model.Batch

@CompileStatic
class GroovyBatchLoader implements CookbookLoader {
    @Override
    Batch load(Path script, List<Path> libraries, Map<String, String> preferences) {
        new RepositoryScript(libraries.collect { Path library -> library.toFile() })
            .evaluate(script.toFile(), preferences)
    }
}
