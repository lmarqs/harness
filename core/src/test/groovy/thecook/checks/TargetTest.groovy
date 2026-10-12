package thecook.checks

import org.junit.Test
import java.nio.file.Files
import thecook.composition.Kitchen
import thecook.cookbook.Cookbook

class TargetTest {
    @Test void repositoryTargetsAreLoadedSeparatelyFromIngredients() {
        def directory = Files.createTempDirectory('cook-target-').toFile()
        try {
            def definition = new File(directory, 'book/targets/worker/target.groovy')
            definition.parentFile.mkdirs()
            definition.text = '''
                return { kitchen, book, Map options ->
                    def target = kitchen.target('worker')
                    target.output('.worker/settings.json', [format: 'json']) {
                        key(['model']) { options.model }
                    }
                    target
                }
            '''
            def kitchen = new Kitchen(new File(directory, 'repository'))
            def target = new Cookbook(new File(directory, 'book')).target('worker', kitchen, [model: 'chosen'])
            assert target.definition.kind == 'target'
            target.mount()
            def plan = kitchen.plan()
            assert !new File(directory, 'repository/.worker/settings.json').exists()
            plan.apply()
            assert new File(directory, 'repository/.worker/settings.json').text.contains('chosen')
        } finally { directory.deleteDir() }
    }
}
