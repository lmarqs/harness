package thecook.checks

import org.junit.Test

class CookbookExamplesTest {
    @Test void migratedExamplesVerifyAgainstDisposableRepositories() {
        def directory = new File('../cookbook').canonicalFile
        def prior = System.getProperty('thecook.cookbook')
        System.setProperty('thecook.cookbook', directory.path)
        try {
            def files = []
            new File(directory, 'examples').eachFileRecurse { file ->
                if (file.isFile() && file.name in ['case.groovy', 'cases.groovy']) files << file
            }
            assert files.size() >= 5
            files.sort { it.path }.each { file ->
                assert new GroovyShell(getClass().classLoader).evaluate(file) == true: file.path
            }
        } finally {
            if (prior == null) System.clearProperty('thecook.cookbook')
            else System.setProperty('thecook.cookbook', prior)
        }
    }
}
