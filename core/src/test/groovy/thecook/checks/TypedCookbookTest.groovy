package thecook.checks

import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.junit.Test
import org.tomlj.Toml
import java.nio.file.Files

class TypedCookbookTest {
    private final File cookbook = new File('../cookbook')

    private GroovyClassLoader suppliers() {
        Class compiler
        try { compiler = getClass().classLoader.loadClass('thecook.groovy.SupplierCompiler') }
        catch (ClassNotFoundException ignored) { assert false: 'The typed supplier compilation boundary is missing' }
        def loader = new GroovyClassLoader(getClass().classLoader, compiler.configuration())
        ['ingredients/git/Git.groovy', 'ingredients/ralph-tui/RalphConfig.groovy',
         'ingredients/ralph-tui/Ralph.groovy', 'recipes/agentic/runtime/AgenticRuntime.groovy'].each {
            loader.parseClass(new File(cookbook, it))
        }
        loader
    }

    private void repository(Closure body) {
        def root = Files.createTempDirectory('cook-typed-').toFile()
        try { body(root) } finally { root.deleteDir() }
    }

    private Object evaluate(GroovyClassLoader loader, String code, Map variables = [:]) {
        new GroovyShell(loader, new Binding(variables)).evaluate(code)
    }

    @Test void dynamicAndCompiledRecipesUseTheSameContractsAndReconcileManualContent() {
        def loader = suppliers()
        repository { root ->
            new File(root, '.gitignore').text = '# team entries\n.env.local\n'
            def compiled = evaluate(loader, '''
                import thecook.cookbook.ralph.RalphConfig
                import thecook.cookbook.recipes.AgenticRuntime
                new AgenticRuntime(new RalphConfig()).compose()
            ''')
            def adapter = getClass().classLoader.loadClass('thecook.groovy.BatchAdapter')
            def compiledPlan = adapter.plan(root, compiled)
            assert !new File(root, '.ralph-tui/config.toml').exists()
            assert new File(root, '.gitignore').text == '# team entries\n.env.local\n'
            compiledPlan.apply()
            def first = ModelChecks.snapshot(root)
            def dynamic = evaluate(loader, new File(cookbook, 'examples/typed-runtime/cook.groovy').text,
                [preferences: [agent: 'codex', excluded: []]])
            assert adapter.plan(root, dynamic).changes.empty
            assert ModelChecks.snapshot(root) == first
            assert new File(root, '.gitignore').text.startsWith('# team entries\n.env.local\n')
            assert new File(root, '.gitignore').text.contains('/.ralph-tui/session.json')
            assert Toml.parse(new File(root, '.ralph-tui/config.toml').text).getString('defaultAgent') == 'codex'
        }
    }

    @Test void supplierBuildRejectsMisspelledPropertiesWithoutAnOptInAnnotation() {
        def loader = suppliers()
        try {
            loader.parseClass('''
                import thecook.annotations.Recipe
                import thecook.model.*
                import thecook.cookbook.ralph.*
                @Recipe('broken')
                class Broken implements RecipeDefinition {
                    Batch compose() {
                        def ralph = new Ralph(new RalphConfig())
                        ralph.files.locla
                        new Batch('broken')
                    }
                }
            ''')
            assert false: 'Supplier compilation accepted an unknown export'
        } catch (MultipleCompilationErrorsException expected) {
            assert expected.message.contains('locla')
        }
    }

    @Test void supplierBuildRejectsAnIncompatibleConnection() {
        def loader = suppliers()
        try {
            loader.parseClass('''
                import thecook.annotations.Recipe
                import thecook.model.*
                import thecook.cookbook.git.Git
                @Recipe('broken-type')
                class BrokenType implements RecipeDefinition {
                    Batch compose() {
                        def git = new Git()
                        git.ignore.add('arbitrary text')
                        new Batch('broken-type').include(git)
                    }
                }
            ''')
            assert false: 'Supplier compilation accepted a string as a FileSet'
        } catch (MultipleCompilationErrorsException expected) {
            assert expected.message.contains('add')
        }
    }

    @Test void annotationRequiresThePublishedContractAndCannotDisableStaticChecking() {
        def loader = suppliers()
        ['''
            import thecook.annotations.Ingredient
            @Ingredient('fake') class Fake {}
        ''', '''
            import thecook.annotations.Recipe
            import thecook.model.*
            import groovy.transform.CompileDynamic
            @Recipe('unchecked') @CompileDynamic
            class Unchecked implements RecipeDefinition {
                Batch compose() { new Batch('unchecked') }
            }
        ''', '''
            import thecook.annotations.Recipe
            import thecook.model.*
            import groovy.transform.CompileDynamic
            @Recipe('unchecked-constructor')
            class UncheckedConstructor implements RecipeDefinition {
                @CompileDynamic UncheckedConstructor() { misspelledThing() }
                Batch compose() { new Batch('unchecked-constructor') }
            }
        ''', '''
            import thecook.annotations.Recipe
            import thecook.model.*
            import groovy.transform.CompileStatic
            import groovy.transform.TypeCheckingMode
            @Recipe('skipped-constructor')
            class SkippedConstructor implements RecipeDefinition {
                @CompileStatic(TypeCheckingMode.SKIP) SkippedConstructor() { misspelledThing() }
                Batch compose() { new Batch('skipped-constructor') }
            }
        ''', '''
            import thecook.annotations.Recipe
            import thecook.model.*
            class DynamicBase {
                Batch compose() { misspelledThing(); new Batch('unchecked-inheritance') }
            }
            @Recipe('unchecked-inheritance')
            class UncheckedInheritance extends DynamicBase implements RecipeDefinition {}
        '''].each { source ->
            try { loader.parseClass(source); assert false: 'Invalid published definition compiled' }
            catch (MultipleCompilationErrorsException expected) {
                assert expected.message.contains('published') || expected.message.contains('static')
            }
        }
        loader.parseClass('''
            import thecook.annotations.Recipe
            import thecook.model.*
            import groovy.transform.CompileStatic
            @CompileStatic class CheckedBase {
                Batch compose() { new Batch('checked-inheritance') }
            }
            @Recipe('checked-inheritance')
            class CheckedInheritance extends CheckedBase implements RecipeDefinition {}
        ''')
        assert loader.loadClass('CheckedInheritance').newInstance().compose().id.value == 'checked-inheritance'
    }

    @Test void dynamicMistakesAndInvalidConfigurationLeaveTheRepositoryUntouched() {
        def loader = suppliers()
        repository { root ->
            new File(root, 'keep.txt').text = 'manual\n'
            def before = ModelChecks.snapshot(root)
            ['''
                import thecook.cookbook.git.Git
                def git = new Git()
                git.ignore.add('wrong type')
            ''', '''
                import thecook.cookbook.ralph.*
                import thecook.cookbook.recipes.AgenticRuntime
                new AgenticRuntime(new RalphConfig(agent: 'unsupported')).compose()
            '''].each { source ->
                try {
                    def batch = evaluate(loader, source)
                    getClass().classLoader.loadClass('thecook.groovy.BatchAdapter').plan(root, batch)
                    assert false: 'Invalid repository composition produced a plan'
                } catch (Exception expected) {
                    assert ModelChecks.snapshot(root) == before
                }
            }
        }
    }

    @Test void consumingRuntimePathsDoesNotMountRalphOrCreateGitImplicitly() {
        def loader = suppliers()
        repository { root ->
            def adapter = getClass().classLoader.loadClass('thecook.groovy.BatchAdapter')
            def gitOnly = evaluate(loader, '''
                import thecook.model.Batch
                import thecook.cookbook.git.Git
                import thecook.cookbook.ralph.*
                def git = new Git()
                def ralph = new Ralph(new RalphConfig())
                git.ignore.add(ralph.files.local)
                new Batch('git-only').include(git)
            ''')
            adapter.plan(root, gitOnly).apply()
            assert new File(root, '.gitignore').isFile()
            assert !new File(root, '.ralph-tui').exists()
        }
        repository { root ->
            def ralphOnly = evaluate(loader, '''
                import thecook.model.Batch
                import thecook.cookbook.ralph.*
                new Batch('ralph-only').include(new Ralph(new RalphConfig()))
            ''')
            getClass().classLoader.loadClass('thecook.groovy.BatchAdapter').plan(root, ralphOnly).apply()
            assert new File(root, '.ralph-tui/config.toml').isFile()
            assert !new File(root, '.gitignore').exists()
        }
    }

    @Test void configurationStaysLiveUntilPlanningAndRetainedRuntimeDataSurvivesRemoval() {
        def loader = suppliers()
        repository { root ->
            def variables = evaluate(loader, '''
                import thecook.cookbook.ralph.*
                import thecook.cookbook.recipes.AgenticRuntime
                def config = new RalphConfig()
                [config: config, batch: new AgenticRuntime(config).compose()]
            ''')
            variables.config.agent = 'claude'
            def adapter = getClass().classLoader.loadClass('thecook.groovy.BatchAdapter')
            adapter.plan(root, variables.batch).apply()
            assert Toml.parse(new File(root, '.ralph-tui/config.toml').text).getString('defaultAgent') == 'claude'
            new File(root, '.ralph-tui/session.json').text = '{"task":"retained"}\n'
            def disabled = evaluate(loader, '''
                import thecook.cookbook.recipes.AgenticRuntime
                import thecook.cookbook.ralph.RalphConfig
                new AgenticRuntime(new RalphConfig(), false).compose()
            ''')
            adapter.plan(root, disabled).apply()
            assert new File(root, '.ralph-tui/session.json').text == '{"task":"retained"}\n'
            assert new File(root, '.gitignore').text.contains('/.ralph-tui/session.json')
            assert !new File(root, '.ralph-tui/config.toml').exists()
            assert !new File(root, '.ralph-tui/run').exists()
        }
    }

    @Test void typedCompositionRejectsMultipleWritersBeforeCreatingFiles() {
        def loader = suppliers()
        repository { root ->
            def batch = evaluate(loader, '''
                import thecook.model.Batch
                import thecook.cookbook.git.Git
                new Batch('collision').include(new Git('first')).include(new Git('second'))
            ''')
            ModelChecks.rejects('Multiple writers') {
                getClass().classLoader.loadClass('thecook.groovy.BatchAdapter').plan(root, batch)
            }
            assert ModelChecks.snapshot(root).isEmpty()
        }
    }
}
