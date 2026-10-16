package thecook.cli

import groovy.json.JsonSlurper
import org.junit.Test
import org.tomlj.Toml
import java.nio.file.Files

class CookCommandTest {
  @Test void removalRequiresDeclaredHarnessAndRejectsUnusedArgumentsBeforeWrites() {
    workspace { root, repo, ignored ->
      Fixtures.copy('removable-permission', root)
      def source = new File(root, 'permission.groovy')
      def out = new ByteArrayOutputStream(); def err = new ByteArrayOutputStream()
      def cli = command(out, err)
      def base = ['-C', repo.path, '--remove', source.path]
      [
        [],
        ['--harness='],
        ['--harness=other'],
        ['--harness=claude', '--adopt=true'],
        ['--harness=claude', '--policy=one', '--policy=two']
      ].each { invalid ->
        err.reset()
        assert cli.execute((base + invalid) as String[]) != 0
        assert err.toString('UTF-8')
        assert repo.listFiles().length == 0
      }
      assert cli.execute((base + ['--harness=claude', '--policy=missing.yaml']) as String[]) == 0
      assert repo.listFiles().length == 0
    }
  }

  @Test void unrelatedLegacyFilenameCannotRemoveDeclaredApplication() {
    workspace { root, repo, ignored ->
      Fixtures.copy('removable-permission', root)
      def source = new File(root, 'permission.groovy')
      def policy = new File(repo, 'policy.yaml'); policy.text = 'managed\n'
      def out = new ByteArrayOutputStream(); def err = new ByteArrayOutputStream()
      def cli = command(out, err)
      assert cli.execute(['-C', repo.path, source.path, '--policy=policy.yaml', '--harness=claude'] as String[]) == 0
      def before = new File(repo, '.cook/state.json').text
      def unrelated = new File(root, 'unrelated/permission.groovy')
      unrelated.parentFile.mkdirs()
      unrelated.text = 'return { kitchen, book, options -> def c = kitchen.recipe("unrelated"); c.application("fixture/unrelated"); c }\n'
      assert cli.execute(['-C', repo.path, '--remove', unrelated.path, '--harness=claude'] as String[]) == 1
      assert err.toString('UTF-8').contains('no argument declaration')
      assert new File(repo, '.cook/state.json').text == before
      assert new File(repo, 'settings.json').exists()
    }
  }

  @Test void renamedDeclaredFactoryRemovesOnlyItsOwnApplication() {
    workspace { root, repo, ignored ->
      def source = new File(root, 'custom.groovy')
      source.text = '''import thecook.cookbook.DeclaredRecipe
import thecook.model.RecipeDeclaration
import thecook.model.RecipeInput
import thecook.model.RecipeRemoval
def target = new RecipeInput.RequiredChoices('target', ['one', 'two'])
return new DeclaredRecipe(new RecipeDeclaration('custom', [target],
    new RecipeRemoval(target, [one: ['fixture/one'], two: ['fixture/two']])), { kitchen, book, options ->
  def child = kitchen.recipe(options.target[0])
  child.application('fixture/' + options.target[0])
  child.output(options.target[0] + '.txt', [owns: true]) { text { 'owned' } }
  child
})
'''
      def out = new ByteArrayOutputStream(); def err = new ByteArrayOutputStream()
      def cli = command(out, err)
      def base = ['-C', repo.path, source.path]
      assert cli.execute((base + ['--target=one']) as String[]) == 0 : err.toString('UTF-8')
      assert cli.execute((base + ['--target=two']) as String[]) == 0 : err.toString('UTF-8')
      assert cli.execute((['-C', repo.path, '--remove', source.path, '--target=one']) as String[]) == 0 : err.toString('UTF-8')
      assert !new File(repo, 'one.txt').exists()
      assert new File(repo, 'two.txt').text == 'owned'
      assert new JsonSlurper().parse(new File(repo, '.cook/state.json')).applications.keySet() == ['fixture/two'] as Set
    }
  }

  @Test void removalPreviewsWithoutPolicyThenRestoresOnlySelectedApplication() {
    workspace { root, repo, ignored ->
      Fixtures.copy('removable-permission', root)
      def source = new File(root, 'permission.groovy')
      def policy = new File(repo, 'policy.yaml')
      policy.text = 'managed\n'
      def settings = new File(repo, 'settings.json')
      settings.text = '{"claude":"original","manual":true}\n'
      def out = new ByteArrayOutputStream(); def err = new ByteArrayOutputStream()
      def cli = command(out, err)
      def base = ['-C', repo.path, source.path]
      assert cli.execute((['-C', repo.path, '--remove', source.path, '--harness=claude']) as String[]) == 0 : err.toString('UTF-8')
      assert !new File(repo, '.cook').exists()
      out.reset()
      assert cli.execute((base + ['--policy=policy.yaml', '--harness=claude']) as String[]) == 0 : err.toString('UTF-8')
      assert cli.execute((base + ['--policy=policy.yaml', '--harness=codex']) as String[]) == 0 : err.toString('UTF-8')
      policy.delete()
      def sourceBytes = source.bytes
      def before = settings.text
      def ownership = new File(repo, '.cook/state.json').text
      out.reset()
      assert cli.execute((['-C', repo.path, '--remove', '--dry-run', source.path, '--policy=policy.yaml', '--harness=claude']) as String[]) == 0 : err.toString('UTF-8')
      assert out.toString('UTF-8').contains('Would remove settings.json')
      assert settings.text == before
      assert new File(repo, '.cook/state.json').text == ownership
      out.reset()
      assert cli.execute((['-C', repo.path, '--remove', source.path, '--harness=claude']) as String[]) == 0 : err.toString('UTF-8')
      assert new JsonSlurper().parse(settings) == [claude: 'original', manual: true, codex: 'managed']
      assert !policy.exists()
      assert source.bytes == sourceBytes
      out.reset()
      assert cli.execute((['-C', repo.path, '--remove', source.path, '--harness=claude']) as String[]) == 0 : err.toString('UTF-8')
      assert out.toString('UTF-8').trim() == 'Already removed.'
      def retained = new File(repo, '.cook/state.json').text
      settings.text = settings.text.replace('"managed"', '"edited"')
      err.reset()
      assert cli.execute((['-C', repo.path, '--remove', source.path, '--harness=codex']) as String[]) == 1
      assert err.toString('UTF-8').contains('Edited managed region')
      assert new File(repo, '.cook/state.json').text == retained
    }
  }

  private Object command(ByteArrayOutputStream stdout, ByteArrayOutputStream stderr, String input = null) {
    Class entrypoint
    try {
      entrypoint = getClass().classLoader.loadClass('thecook.cli.terminal.CookCommand')
    }
    catch (ClassNotFoundException ignored) {
      assert false: 'The CLI adapter is missing'
    }
    def service = getClass().classLoader.loadClass('thecook.application.CookService').newInstance(
        getClass().classLoader.loadClass('thecook.groovy.GroovyBatchLoader').newInstance(),
        getClass().classLoader.loadClass('thecook.groovy.NativePlanning').newInstance(),
        getClass().classLoader.loadClass('thecook.cli.filesystem.FileRepositoryInitializer').newInstance())
    def stdin = input == null ? null : new BufferedReader(new StringReader(input))
    entrypoint.newInstance(service, new PrintWriter(stdout, true), new PrintWriter(stderr, true), stdin)
  }

  private void workspace(Closure body) {
    def root = Files.createTempDirectory('cook-cli-').toFile()
    try {
      def repo = new File(root, 'repository'); repo.mkdirs()
      def script = new File(root, 'cook.groovy')
      Fixtures.copy('personal-tools', root)
      body(root, repo, script)
    } finally {
      root.deleteDir()
    }
  }

  @Test void cooksWithRepositoryDefaultsWithoutSavingAPlan() {
    workspace { root, repo, script ->
      def stdout = new ByteArrayOutputStream(); def stderr = new ByteArrayOutputStream()
      def cli = command(stdout, stderr)
      new File(repo, 'cook.groovy').text = script.text
      new File(repo, '.gitignore').text = '# manual\n'
      assert cli.execute(['-C', repo.path, 'cook', '--set', 'agent=claude'] as String[]) == 0
      assert Toml.parse(new File(repo, '.ralph-tui/config.toml').text).getString('defaultAgent') == 'claude'
      assert new File(repo, '.gitignore').text.startsWith('# manual\n')
      assert !new File(repo, '.cook/plan.json').exists()
      assert stdout.toString('UTF-8').contains('Cooked .gitignore')
      def configuration = new File(repo, '.ralph-tui/config.toml').text
      stdout.reset()
      assert cli.execute(['-C', repo.path, 'cook', '--set', 'agent=claude'] as String[]) == 0
      assert stdout.toString('UTF-8').trim() == 'Already cooked.'
      assert new File(repo, '.ralph-tui/config.toml').text == configuration
      assert stderr.toString('UTF-8').empty
    }
  }

  @Test void dryRunShowsChangesWithoutWritingNativeFilesOrMetadata() {
    workspace { root, repo, script ->
      def stdout = new ByteArrayOutputStream(); def stderr = new ByteArrayOutputStream()
      def cli = command(stdout, stderr)
      new File(repo, '.gitignore').text = '# manual\n'
      assert cli.execute(['-C', repo.path, 'cook', '--dry-run', '--script', script.path] as String[]) == 0
      assert stdout.toString('UTF-8').contains('Would cook .gitignore')
      assert new File(repo, '.gitignore').text == '# manual\n'
      assert !new File(repo, '.ralph-tui').exists()
      assert !new File(repo, '.cook').exists()
      assert stderr.toString('UTF-8').empty
    }
  }

  @Test void cookingRejectsEditsInsideAManagedRegionBeforeWritingOtherOutputs() {
    workspace { root, repo, script ->
      def stdout = new ByteArrayOutputStream(); def stderr = new ByteArrayOutputStream()
      def cli = command(stdout, stderr)
      def args = ['-C', repo.path, 'cook', '--script', script.path] as String[]
      assert cli.execute(args) == 0
      def ignore = new File(repo, '.gitignore')
      assert ignore.text.contains('/.ralph-tui/worktrees/')
      ignore.text = ignore.text.replace('/.ralph-tui/worktrees/', '/changed-by-hand/')
      def edited = ignore.text
      def config = new File(repo, '.ralph-tui/config.toml')
      def originalConfig = config.text
      stdout.reset()
      assert cli.execute((args.toList() + ['--set', 'agent=claude']) as String[]) == 1
      assert ignore.text == edited
      assert config.text == originalConfig
      assert stdout.toString('UTF-8').empty
      assert !stderr.toString('UTF-8').empty
    }
  }

  @Test void cookingRejectsPlanFlagsAndDryRunValues() {
    def cli = command(new ByteArrayOutputStream(), new ByteArrayOutputStream())
    assert cli.execute(['cook', '--plan', 'plan.json'] as String[]) == 2
    assert cli.execute(['cook', '--dry-run', 'false'] as String[]) == 2
    assert cli.execute(['cook', '--dry-run', '--dry-run'] as String[]) == 2
    assert cli.execute(['apply', '--dry-run'] as String[]) == 2
  }

  @Test void localGroovyEntrypointUsesCookWithTheSameFlags() {
    workspace { root, repo, script ->
      def stdout = new ByteArrayOutputStream(); def stderr = new ByteArrayOutputStream()
      def cli = command(stdout, stderr)
      assert cli.execute(['-C', repo.path, '--dry-run', script.path] as String[]) == 0
      assert !new File(repo, '.ralph-tui').exists()
      assert cli.execute(['-C', repo.path, script.path, '--agent=claude'] as String[]) == 0
      assert Toml.parse(new File(repo, '.ralph-tui/config.toml').text).getString('defaultAgent') == 'claude'
      assert cli.execute([script.path, '--script', script.path] as String[]) == 2
      assert stderr.toString('UTF-8').contains('Unknown option for the recipe: --script')
    }
  }

  @Test void namedRecipesReceiveArgumentsWithEitherEqualsOrSeparateValues() {
    workspace { root, repo, script ->
      Fixtures.copy('composed-recipe', repo)
      def stdout = new ByteArrayOutputStream(); def stderr = new ByteArrayOutputStream()
      def cli = command(stdout, stderr)
      assert cli.execute(['-C', repo.path, '--dry-run', 'example', '--agent=claude'] as String[]) == 0 : stderr.toString('UTF-8')
      assert !new File(repo, '.codex').exists()
      assert cli.execute(['-C', repo.path, 'example', '--agent', 'claude'] as String[]) == 0
      assert Toml.parse(new File(repo, '.codex/config.toml').text).getString('model') == 'claude'
      stdout.reset()
      assert cli.execute(['-C', repo.path, 'example', '--agent=claude'] as String[]) == 0
      assert stdout.toString('UTF-8').trim() == 'Already cooked.'
    }
  }

  @Test void declaredRecipeValidatesRawValuesAndUsesDestinationPolicy() {
    workspace { root, repo, script ->
      Fixtures.copy('declared-recipe', repo)
      def policy = new File(repo, '.agents/policy.yaml'); policy.parentFile.mkdirs()
      policy.text = 'destination\n'
      def supplier = new File(root, '.agents/policy.yaml'); supplier.parentFile.mkdirs()
      supplier.text = 'supplier\n'
      def stdout = new ByteArrayOutputStream(); def stderr = new ByteArrayOutputStream()
      def cli = command(stdout, stderr)
      def base = ['-C', repo.path, 'declared', '--policy=.agents/policy.yaml']
      for (invalid in [
            ['--harness=other'],
            ['--harness=codex', '--policy=again'],
            ['--harness=codex', '--unknown=x'],
            []
          ]) {
        stderr.reset()
        assert cli.execute((base + invalid) as String[]) == 1 : invalid
        assert !new File(repo, 'selection.json').exists()
        assert !new File(repo, '.cook').exists()
      }
      assert stderr.toString('UTF-8').contains('--harness')
      stderr.reset()
      assert cli.execute((base + ['--harness=claude', '--harness=codex', '--harness=claude']) as String[]) == 0 : stderr.toString('UTF-8')
      def output = new JsonSlurper().parse(new File(repo, 'selection.json'))
      assert output.policy == 'destination'
      assert output.harness == 'claude,codex'
      def external = new File(root, 'supplier/cookbook/recipes/declared/recipe.groovy')
      external.parentFile.mkdirs()
      external.text = new File(repo, 'cookbook/recipes/declared/recipe.groovy').text
      def externalRepo = new File(root, 'external-repository'); externalRepo.mkdirs()
      def externalPolicy = new File(externalRepo, '.agents/policy.yaml'); externalPolicy.parentFile.mkdirs()
      externalPolicy.text = 'destination\n'
      stderr.reset()
      assert cli.execute([
        '-C',
        externalRepo.path,
        external.path,
        '--policy',
        '.agents/policy.yaml',
        '--harness',
        'codex',
        '--harness',
        'claude'
      ] as String[]) == 0 : stderr.toString('UTF-8')
      output = new JsonSlurper().parse(new File(externalRepo, 'selection.json'))
      assert output.policy == 'destination'
      assert output.harness == 'codex,claude'
    }
  }

  @Test void nestedPackagedRecipeIsRunnableWithoutACookbookCheckout() {
    workspace { root, repo, script ->
      def stdout = new ByteArrayOutputStream(); def stderr = new ByteArrayOutputStream()
      def cli = command(stdout, stderr)
      assert cli.execute(['-C', repo.path, 'agentic/codex', '--instructions=Use tests.'] as String[]) == 0 : stderr.toString('UTF-8')
      assert new File(repo, 'AGENTS.md').text.contains('Use tests.')
      stdout.reset()
      assert cli.execute(['-C', repo.path, 'agentic/codex', '--instructions=Use tests.'] as String[]) == 0
      assert stdout.toString('UTF-8').trim() == 'Already cooked.'
    }
  }

  @Test void flatRecipeWorksByLocalPathAndNestedNameWithRepeatedArguments() {
    workspace { root, repo, ignored ->
      Fixtures.copy('flat-recipe', repo)
      def source = new File(repo, 'cookbook/recipes/agentic/permission.groovy')
      def policy = new File(repo, '.agents/policy.yaml'); policy.parentFile.mkdirs(); policy.text = 'destination\n'
      def out = new ByteArrayOutputStream(); def err = new ByteArrayOutputStream()
      def cli = command(out, err)
      def options = ['--policy=.agents/policy.yaml', '--harness=codex', '--harness', 'claude']
      assert cli.execute((['-C', repo.path, '--dry-run', source.path] + options) as String[]) == 0 : err.toString('UTF-8')
      assert !new File(repo, 'selection.json').exists()
      assert !new File(repo, '.cook').exists()
      assert cli.execute((['-C', repo.path, source.path] + options) as String[]) == 0 : err.toString('UTF-8')
      assert new JsonSlurper().parse(new File(repo, 'selection.json')).harness == 'codex,claude'
      out.reset()
      assert cli.execute((['-C', repo.path, 'agentic/permission'] + options) as String[]) == 0 : err.toString('UTF-8')
      assert out.toString('UTF-8').trim() == 'Already cooked.'
      out.reset()
      assert cli.execute([
        '-C',
        repo.path,
        'agentic/permission',
        '--policy',
        '.agents/policy.yaml',
        '--harness',
        'codex',
        '--harness',
        'claude'
      ] as String[]) == 0 : err.toString('UTF-8')
      assert out.toString('UTF-8').trim() == 'Already cooked.'
    }
  }

  @Test void flatRecipeHelpAndInvalidInputsDoNotReadPolicyOrChangeOwnership() {
    workspace { root, repo, ignored ->
      Fixtures.copy('flat-recipe', repo)
      def source = new File(repo, 'cookbook/recipes/agentic/permission.groovy')
      def out = new ByteArrayOutputStream(); def err = new ByteArrayOutputStream()
      def cli = command(out, err)
      assert cli.execute(['-C', repo.path, 'help', source.path] as String[]) == 0 : err.toString('UTF-8')
      assert out.toString('UTF-8').contains('--policy')
      assert out.toString('UTF-8').contains('required')
      assert out.toString('UTF-8').contains('codex, claude')
      assert out.toString('UTF-8').contains('--dry-run')
      out.reset()
      assert cli.execute(['-C', repo.path, 'help', 'agentic/permission'] as String[]) == 0 : err.toString('UTF-8')
      assert out.toString('UTF-8').contains('--harness')
      assert !new File(repo, '.cook').exists()
      def policy = new File(repo, '.agents/policy.yaml'); policy.parentFile.mkdirs(); policy.text = 'destination\n'
      def valid = ['-C', repo.path, source.path, '--policy=.agents/policy.yaml', '--harness=codex']
      assert cli.execute(valid as String[]) == 0 : err.toString('UTF-8')
      def before = new File(repo, 'selection.json').text
      def ownership = new File(repo, '.cook/state.json').text
      for (args in [
            valid + ['--set', 'policy=other'],
            valid + ['--harness=invalid'],
            ['-C', repo.path, new File(repo, 'missing.groovy').path, '--policy=x']
          ]) {
        err.reset()
        assert cli.execute(args as String[]) != 0 : args
        assert new File(repo, 'selection.json').text == before
        assert new File(repo, '.cook/state.json').text == ownership
      }
      assert err.toString('UTF-8').contains('missing.groovy')
    }
  }

  @Test void ambiguousFlatAndDirectoryRecipeSourcesAreReported() {
    workspace { root, repo, ignored ->
      Fixtures.copy('flat-recipe', repo)
      def directory = new File(repo, 'cookbook/recipes/agentic/permission')
      directory.mkdirs()
      new File(directory, 'recipe.groovy').text = new File(repo, 'cookbook/recipes/agentic/permission.groovy').text
      def err = new ByteArrayOutputStream()
      def cli = command(new ByteArrayOutputStream(), err)
      assert cli.execute(['-C', repo.path, 'help', 'agentic/permission'] as String[]) == 1
      assert err.toString('UTF-8').toLowerCase().contains('ambiguous')
      assert !new File(repo, '.cook').exists()
    }
  }

  @Test void directFactoryCanLiveOutsideCookbookRecipes() {
    workspace { root, repo, ignored ->
      Fixtures.copy('flat-recipe', repo)
      def source = new File(root, 'permission.groovy')
      source.text = new File(repo, 'cookbook/recipes/agentic/permission.groovy').text
      def policy = new File(repo, '.agents/policy.yaml'); policy.parentFile.mkdirs(); policy.text = 'destination\n'
      def out = new ByteArrayOutputStream(); def err = new ByteArrayOutputStream()
      def cli = command(out, err)
      assert cli.execute(['-C', repo.path, 'help', source.path] as String[]) == 0 : err.toString('UTF-8')
      assert out.toString('UTF-8').contains('--harness')
      assert cli.execute(['-C', repo.path, source.path, '--policy=.agents/policy.yaml', '--harness=codex', '--harness=claude'] as String[]) == 0 : err.toString('UTF-8')
      assert new JsonSlurper().parse(new File(repo, 'selection.json')).harness == 'codex,claude'
    }
  }

  @Test void relocatedPermissionFactoryKeepsDeclaredIdentityAndRemoval() {
    workspace { root, repo, ignored ->
      def original = new File(repo, 'cookbook/recipes/agentic/permission.groovy')
      original.parentFile.mkdirs()
      original.text = new File('../cookbook/recipes/agentic/permission.groovy').text
      def source = new File(repo, 'cookbook/recipes/renamed.groovy')
      source.parentFile.mkdirs()
      source.text = original.text
      def policy = new File(repo, '.agents/interactive.yaml')
      policy.parentFile.mkdirs()
      policy.text = 'version: 1\ncommands:\n  allow:\n    - [git, status]\n'
      def out = new ByteArrayOutputStream(); def err = new ByteArrayOutputStream()
      def cli = command(out, err)
      def args = ['-C', repo.path, source.path, '--policy=.agents/interactive.yaml', '--harness=claude']
      assert cli.execute((['-C', repo.path, 'agentic/permission'] + args.drop(3)) as String[]) == 0 : err.toString('UTF-8')
      def previous = new JsonSlurper().parse(new File(repo, '.cook/state.json')).applications['agentic.permissions/claude']
      assert cli.execute(['-C', repo.path, 'help', source.path] as String[]) == 0 : err.toString('UTF-8')
      assert out.toString('UTF-8').contains('agentic/permission')
      assert cli.execute(args as String[]) == 0 : err.toString('UTF-8')
      assert new File(repo, '.claude/settings.json').exists()
      def state = new JsonSlurper().parse(new File(repo, '.cook/state.json'))
      assert state.applications.containsKey('agentic.permissions/claude')
      assert state.applications['agentic.permissions/claude'] == previous
      assert state.provenance['agentic.permissions/claude'].recipe == 'recipes/renamed.groovy'
      assert state.provenance['agentic.permissions/claude'].inputs.policy == '.agents/interactive.yaml'
      out.reset()
      assert cli.execute(args as String[]) == 0 : err.toString('UTF-8')
      assert out.toString('UTF-8').trim() == 'Already cooked.'
      err.reset()
      assert cli.execute(['-C', repo.path, 'help', 'renamed'] as String[]) == 1
      assert err.toString('UTF-8').contains('Recipe identity does not match')
      assert cli.execute(['-C', repo.path, '--remove', source.path, '--harness=claude'] as String[]) == 0
      assert !new File(repo, '.claude/settings.json').exists()
    }
  }

  @Test void localRecipeOverridesPackagedFactory() {
    workspace { root, repo, script ->
      Fixtures.copy('recipe-override', repo)
      def stdout = new ByteArrayOutputStream(); def stderr = new ByteArrayOutputStream()
      def cli = command(stdout, stderr)
      assert cli.execute(['-C', repo.path, 'agentic/codex'] as String[]) == 0 : stderr.toString('UTF-8')
      assert new File(repo, 'CLAUDE.md').text.contains('Local recipe override.')
      assert !new File(repo, 'AGENTS.md').exists()
    }
  }

  @Test void compiledRuntimeRecipeRunsThroughTheSameFactoryEntryPoint() {
    workspace { root, repo, script ->
      def stdout = new ByteArrayOutputStream(); def stderr = new ByteArrayOutputStream()
      def cli = command(stdout, stderr)
      assert cli.execute(['-C', repo.path, 'agentic/runtime', '--agent=claude'] as String[]) == 0 : stderr.toString('UTF-8')
      assert Toml.parse(new File(repo, '.ralph-tui/config.toml').text).getString('defaultAgent') == 'claude'
      stdout.reset()
      assert cli.execute(['-C', repo.path, 'agentic/runtime', '--agent=claude'] as String[]) == 0
      assert stdout.toString('UTF-8').trim() == 'Already cooked.'
    }
  }

  @Test void recipeArgumentsRejectAmbiguityAndNamesCannotEscapeTheRecipeDirectory() {
    workspace { root, repo, script ->
      def stdout = new ByteArrayOutputStream(); def stderr = new ByteArrayOutputStream()
      def cli = command(stdout, stderr)
      for (args in [
            ['example', '--agent'],
            ['example', '--script', script.path],
            ['../example'],
            ['agentic/../example'],
            ['/example'],
            ['agentic//codex'],
            ['example', '--dry-run=true'],
            ['example', '--policy='],
            ]) assert cli.execute(args as String[]) == 2 : args
      assert cli.execute(['-C', repo.path, 'example', '--agent=a', '--agent=b'] as String[]) == 1
      assert cli.execute(['-C', repo.path, 'example', '--agent=a', '--set', 'agent=b'] as String[]) == 2
      assert cli.execute(['-C', repo.path, 'missing'] as String[]) == 1
      assert stderr.toString('UTF-8').contains('Recipe not found: missing')
      assert !new File(repo, '.cook').exists()
    }
  }

  @Test void agentSetupLinksSelectedSkillsAndRendersPolicy() {
    workspace { root, repo, script ->
      def source = new File(root, 'shared/solid'); source.mkdirs()
      new File(source, 'SKILL.md').text = 'Source skill'
      new File(repo, 'AGENTS.md').text = '# Team instructions\n'
      script.text = """
                import thecook.cookbook.ingredients.skills.Skills
                import thecook.cookbook.ingredients.agentpolicy.AgentInstructions
                import thecook.model.Batch
                def skills = new Skills()
                skills.link('solid', preferences.skill)
                def policy = new AgentInstructions()
                policy.instructions.add('Use ./the-cook cook after editing the batch.')
                new Batch('agent-setup').include(skills).include(policy)
            """
      def stdout = new ByteArrayOutputStream(); def stderr = new ByteArrayOutputStream()
      def cli = command(stdout, stderr)
      def args = ['-C', repo.path, script.path, '--skill=' + source.path] as String[]
      assert cli.execute(args) == 0 : stderr.toString('UTF-8')
      assert Files.isSymbolicLink(new File(repo, '.agents/skills/solid').toPath())
      assert new File(repo, '.agents/skills/solid/SKILL.md').text == 'Source skill'
      assert new File(repo, 'AGENTS.md').text.startsWith('# Team instructions\n')
      assert new File(repo, 'AGENTS.md').text.contains('Use ./the-cook cook')
      stdout.reset()
      assert cli.execute(args) == 0
      assert stdout.toString('UTF-8').trim() == 'Already cooked.'
      assert new File(source, 'SKILL.md').text == 'Source skill'
    }
  }

  @Test void theCookIngredientPublishesLocalStateWithoutImplicitlyCreatingGitFiles() {
    workspace { root, repo, script ->
      def stdout = new ByteArrayOutputStream(); def stderr = new ByteArrayOutputStream()
      def cli = command(stdout, stderr)
      script.text = '''
                import thecook.cookbook.ingredients.thecook.TheCook
                import thecook.model.Batch
                new Batch('the-cook').include(new TheCook())
            '''
      def args = ['-C', repo.path, script.path] as String[]
      assert cli.execute(args) == 0 : stderr.toString('UTF-8')
      assert repo.listFiles().length == 0
      new File(repo, '.gitignore').text = '# team entries\n'
      script.text = '''
                import thecook.cookbook.ingredients.thecook.TheCook
                import thecook.cookbook.ingredients.git.Git
                import thecook.model.Batch
                def cook = new TheCook()
                def git = new Git()
                git.ignore.add(cook.files.local)
                assert cook.files.ephemeral.files.empty
                new Batch('the-cook').include(cook).include(git)
            '''
      assert cli.execute(args) == 0 : stderr.toString('UTF-8')
      assert new File(repo, '.gitignore').text.startsWith('# team entries\n')
      assert new File(repo, '.gitignore').text.contains('/.cook/')
      def state = new File(repo, '.cook/state.json').text
      stdout.reset()
      assert cli.execute(args) == 0
      assert stdout.toString('UTF-8').trim() == 'Already cooked.'
      assert new File(repo, '.cook/state.json').text == state
    }
  }

  @Test void initializesWithoutOverwritingAndCooksUsingRepositoryDefaults() {
    workspace { root, repo, ignored ->
      def stdout = new ByteArrayOutputStream(); def stderr = new ByteArrayOutputStream()
      def cli = command(stdout, stderr)
      def script = new File(repo, 'cook.groovy')
      new File(repo, '.gitignore').text = '# manual\n'
      assert cli.execute(['-C', repo.path, 'init'] as String[]) == 0
      assert script.text.contains('new Batch(')
      assert new File(repo, '.gitignore').text == '# manual\n'
      script.text += '// keep this customization\n'
      def customized = script.text
      assert cli.execute(['-C', repo.path, 'init'] as String[]) == 0
      assert script.text == customized
      assert cli.execute(['-C', repo.path, 'plan'] as String[]) == 0
      def plan = new File(repo, '.cook/plan.json')
      assert new JsonSlurper().parse(plan).changes.empty
      assert cli.execute(['-C', repo.path, 'apply'] as String[]) == 0
      assert stderr.toString('UTF-8').empty
    }
  }

  @Test void gitCanIgnoreCookMetadataWithoutOwningIt() {
    workspace { root, repo, script ->
      def cli = command(new ByteArrayOutputStream(), new ByteArrayOutputStream())
      script.text = """
                import thecook.cookbook.ingredients.git.Git
                import thecook.model.*
                def git = new Git()
                git.ignore.add(FileSet.of(FileReference.directory('.cook')))
                new Batch('repository').include(git)
            """
      def plan = new File(root, 'plan.json')
      assert cli.execute(['-C', repo.path, 'plan', '--script', script.path, '--plan', plan.path] as String[]) == 0
      assert cli.execute(['-C', repo.path, 'apply', '--plan', plan.path] as String[]) == 0
      assert new File(repo, '.gitignore').text.contains('/.cook/')
      try {
        getClass().classLoader.loadClass('thecook.filesystem.TargetPath').of('.cook/owned')
        assert false: 'Cook metadata must remain protected from output ownership'
      } catch (IllegalArgumentException expected) { }
    }
  }

  @Test void initRejectsMissingRepositoryAndUnsupportedOptions() {
    workspace { root, repo, ignored ->
      def cli = command(new ByteArrayOutputStream(), new ByteArrayOutputStream())
      assert cli.execute(['-C', new File(root, 'missing').path, 'init'] as String[]) == 1
      assert !new File(root, 'missing').exists()
      assert cli.execute(['init', '--plan', 'x'] as String[]) == 2
    }
  }

  @Test void plansAndAppliesAReviewedCompositionWithoutRunningToolBinaries() {
    workspace { root, repo, script ->
      def stdout = new ByteArrayOutputStream(); def stderr = new ByteArrayOutputStream()
      def cli = command(stdout, stderr)
      def saved = new File(root, 'review/plan.json')
      new File(repo, '.gitignore').text = '# maintained by the team\n'
      assert cli.execute(['-C', repo.path, 'plan', '--script', script.path, '--plan', saved.path, '--set', 'agent=claude'] as String[]) == 0
      assert saved.isFile()
      assert !new File(repo, '.ralph-tui/config.toml').exists()
      assert new File(repo, '.gitignore').text == '# maintained by the team\n'
      assert new JsonSlurper().parse(saved).changes*.path.toSet() ==
      ['.gitignore', '.ralph-tui/config.toml', '.ralph-tui/run'].toSet()
      assert cli.execute(['-C', repo.path, 'apply', '--plan', saved.path] as String[]) == 0
      assert Toml.parse(new File(repo, '.ralph-tui/config.toml').text).getString('defaultAgent') == 'claude'
      assert new File(repo, '.gitignore').text.startsWith('# maintained by the team\n')
      assert stderr.toString('UTF-8').empty
    }
  }

  @Test void rejectsDriftRatherThanRecomputingTheReviewedPlan() {
    workspace { root, repo, script ->
      def stdout = new ByteArrayOutputStream(); def stderr = new ByteArrayOutputStream()
      def cli = command(stdout, stderr)
      def saved = new File(root, 'plan.json')
      assert cli.execute(['-C', repo.path, 'plan', '--script', script.path, '--plan', saved.path] as String[]) == 0
      new File(repo, '.gitignore').text = '# changed after review\n'
      assert cli.execute(['-C', repo.path, 'apply', '--plan', saved.path] as String[]) == 1
      assert new File(repo, '.gitignore').text == '# changed after review\n'
      assert !new File(repo, '.ralph-tui/config.toml').exists()
      assert stderr.toString('UTF-8').contains('Changed since preview')
    }
  }

  @Test void invalidCompositionProducesNeitherNativeFilesNorAPlan() {
    workspace { root, repo, script ->
      def stdout = new ByteArrayOutputStream(); def stderr = new ByteArrayOutputStream()
      def cli = command(stdout, stderr)
      def saved = new File(root, 'plan.json')
      script.text = '42\n'
      assert cli.execute(['-C', repo.path, 'plan', '--script', script.path, '--plan', saved.path] as String[]) == 1
      assert repo.listFiles().length == 0
      assert !saved.exists()
      assert stderr.toString('UTF-8').contains('must return a Batch')
    }
  }

  @Test void missingAndUnknownOptionsAreUsageErrors() {
    def stdout = new ByteArrayOutputStream(); def stderr = new ByteArrayOutputStream()
    def cli = command(stdout, stderr)
    assert cli.execute(['--help'] as String[]) == 0
    assert stdout.toString('UTF-8').contains('Save the proposed changes as a plan.')
    assert cli.execute(['plan', '--script'] as String[]) == 2
    assert cli.execute(['apply', '--mystery', 'x'] as String[]) == 2
    assert cli.execute(['apply', '--script', 'cook.groovy', '--plan', 'plan.json'] as String[]) == 2
    assert cli.execute(['-C'] as String[]) == 2
    assert cli.execute(['plan', '--kitchen='] as String[]) == 2
  }

  @Test void kitchenOptionCooksAsIfStartedThereAndGoesBeforeTheRecipe() {
    workspace { root, repo, ignored ->
      new File(repo, 'notes.groovy').text = '''return { kitchen, book, options ->
  def recipe = kitchen.recipe('notes')
  recipe.application('fixture/notes')
  recipe.output('NOTES.md', [seed: true]) {
    text {
      'notes\\n'
    }
  }
  recipe
}
'''
      def notes = new File(repo, 'NOTES.md')
      def stdout = new ByteArrayOutputStream(); def stderr = new ByteArrayOutputStream()
      def cli = command(stdout, stderr)
      assert cli.execute(['-C', repo.path, 'notes.groovy', '-C', repo.path] as String[]) == 2
      assert cli.execute(['-C', repo.path, 'notes.groovy', '--kitchen', repo.path] as String[]) == 2
      assert cli.execute(['-C', repo.path, 'notes.groovy', '--repo', repo.path] as String[]) == 2
      assert cli.execute(['-C', repo.path, '--kitchen', repo.path, 'notes.groovy'] as String[]) == 2
      assert cli.execute(['--kitchen', new File(root, 'missing').path, 'notes.groovy'] as String[]) == 1
      assert cli.execute(['--kitchen=' + repo.path, '--dry-run', 'notes.groovy'] as String[]) == 0 : stderr.toString('UTF-8')
      assert !notes.exists()
      assert cli.execute(['plan', '-C', repo.path, '--script', 'notes.groovy'] as String[]) == 0 : stderr.toString('UTF-8')
      assert new File(repo, '.cook/plan.json').isFile() && !notes.exists()
      assert cli.execute(['--kitchen', repo.path, 'apply'] as String[]) == 0 : stderr.toString('UTF-8')
      assert notes.text == 'notes\n'
      stdout.reset()
      assert cli.execute(['-C', repo.path, 'notes.groovy'] as String[]) == 0 : stderr.toString('UTF-8')
      assert stdout.toString('UTF-8').trim() == 'Already cooked.'
    }
  }

  @Test void helpCommandReadsEachRecipeFromItsTextsWithoutRunningIt() {
    workspace { root, repo, ignored ->
      new File(repo, 'notes.groovy').text = """@help('''
Seeds `NOTES.md`.
''')
@confirm('''
Nothing outside the repository changes.
''')
@yield('''
Edit `NOTES.md`.
''')
import example.notes.Notes

@param(value = ['owner'], doc = 'Who keeps the notes', choices = ['me', 'team']) String owner

@ingredient(Notes) def notes

throw new IllegalStateException('the recipe ran')
"""
      def expected = '''notes

Seeds `NOTES.md`.

Parameters
  --owner=<String>
      Who keeps the notes; required; choices: me, team

Ingredients
  example.notes.Notes

Confirm
  Nothing outside the repository changes.

After applying
  Edit `NOTES.md`.
'''
      def stdout = new ByteArrayOutputStream(); def stderr = new ByteArrayOutputStream()
      def cli = command(stdout, stderr)
      assert cli.execute(['-C', repo.path, 'help', 'notes.groovy'] as String[]) == 0 : stderr.toString('UTF-8')
      assert stdout.toString('UTF-8') == expected
      stdout.reset()
      assert cli.execute(['help', '-C', repo.path, 'notes.groovy', 'notes.groovy'] as String[]) == 0 : stderr.toString('UTF-8')
      assert stdout.toString('UTF-8') == expected.trim() + '\n\n' + expected
      [['notes.groovy', '--help'], ['--help', 'notes.groovy'], ['help', 'notes.groovy', '--owner=me']].each { misplaced ->
        stderr.reset()
        assert cli.execute((['-C', repo.path] + misplaced) as String[]) == 2
        assert stderr.toString('UTF-8').readLines().first() in ['--help takes no other arguments; use help <recipe> to read a recipe', 'help takes recipes only: --owner=me']
      }
      stdout.reset()
      assert cli.execute(['help'] as String[]) == 0 && stdout.toString('UTF-8').startsWith('Usage:')
      assert repo.list() as Set == ['notes.groovy'] as Set
    }
  }

  @Test void helpReadsRecipesAndFrontPagesFromTheKitchensRepositories() {
    workspace { root, repo, ignored ->
      def jar = new File(repo, 'repository/acme/notes/1.2.3/notes-1.2.3.jar'); jar.parentFile.mkdirs()
      new java.util.jar.JarOutputStream(jar.newOutputStream()).withCloseable { out ->
        ['acme/notes/README.md': 'Notes for the repository.\n', 'acme/notes/Notes.groovy': 'class Notes {}\n',
          'acme/notes/notes.groovy': """@help(\'\'\'
Seeds `NOTES.md`.
\'\'\')
import acme.notes.Notes

@ingredient(Notes) def notes
"""].each { path, text ->
          out.putNextEntry(new java.util.jar.JarEntry(path)); out.write(text.getBytes('UTF-8')); out.closeEntry()
        }
      }
      new File(repo, 'cook.toml').text = 'repositories = ["repository"]\n'
      def stdout = new ByteArrayOutputStream(); def stderr = new ByteArrayOutputStream()
      def cli = command(stdout, stderr)
      assert cli.execute(['-C', repo.path, 'help', 'acme:notes:1.2.3:notes', 'acme:notes:1.2.3'] as String[]) == 0 :
      stderr.toString('UTF-8')
      assert stdout.toString('UTF-8') == """acme:notes:1.2.3:notes

Seeds `NOTES.md`.

Ingredients
  acme.notes.Notes

acme:notes:1.2.3

Notes for the repository.

Recipes
  acme:notes:1.2.3:notes

Models
  acme.notes.Notes
"""
      assert cli.execute(['-C', repo.path, 'help', 'acme:notes:1.2.4'] as String[]) == 1
      assert stderr.toString('UTF-8').contains('acme:notes:1.2.4 not found in the repositories in')
      assert cli.execute(['-C', repo.path, 'help', 'acme:notes:latest'] as String[]) == 1
      assert stderr.toString('UTF-8').contains('Invalid recipe coordinates: acme:notes:latest')
      stderr.reset()
      assert cli.execute(['-C', repo.path, 'a:notes.groovy'] as String[]) == 1
      assert stderr.toString('UTF-8').contains('Recipe file not found: a:notes.groovy')
    }
  }

  @Test void appliesRecipeCoordinatesLeftToRightAndPrintsWhatToDoNext() {
    workspace { root, repo, ignored ->
      def jar = new File(repo, 'repository/acme/notes/1.2.3/notes-1.2.3.jar'); jar.parentFile.mkdirs()
      new java.util.jar.JarOutputStream(jar.newOutputStream()).withCloseable { out ->
        ['acme/notes/Notes.groovy': """package acme.notes

@Ingredient
class Notes {
  private List<String> lines = []
  void add(String line) { lines += line }
  @Section(path = 'NOTES.md', id = 'notes')
  List<String> section() { lines ?: null }
}
""", 'acme/notes/notes.groovy': """@help('Adds a line to `NOTES.md`.')
@confirm('Everyone reads `NOTES.md`.')
@yield('Read `NOTES.md`.')
import acme.notes.Notes

@param(value = ['line'], doc = 'The line to add') String line
@ingredient(Notes) def notes

notes.add(line)
""", 'acme/notes/more.groovy': """@help('Adds another line.')
import acme.notes.Notes

@ingredient(Notes) def notes

notes.add('more')
"""].each { path, text ->
          out.putNextEntry(new java.util.jar.JarEntry(path)); out.write(text.getBytes('UTF-8')); out.closeEntry()
        }
      }
      new File(repo, 'cook.toml').text = 'repositories = ["repository"]\n'
      def stdout = new ByteArrayOutputStream(); def stderr = new ByteArrayOutputStream()
      def cli = command(stdout, stderr)
      def notes = new File(repo, 'NOTES.md')

      def review = """Confirm acme:notes:1.2.3:notes
  Everyone reads `NOTES.md`.

--- /dev/null
+++ b/NOTES.md
@@ -0,0 +1,3 @@
+<!-- cook:begin acme:notes:notes#notes -->
+first
+<!-- cook:end acme:notes:notes#notes -->
"""
      assert cli.execute(['-C', repo.path, '--dry-run', 'acme:notes:1.2.3', '--line=first'] as String[]) == 0 :
      stderr.toString('UTF-8')
      assert stdout.toString('UTF-8').startsWith(review + '--- /dev/null\n+++ b/cook.lock\n')
      assert stdout.toString('UTF-8').endsWith('\n\nWould create NOTES.md\nWould create cook.lock\n')
      assert !notes.exists()

      stdout.reset()
      assert cli.execute(['-C', repo.path, 'acme:notes:1.2.3', '--line=first'] as String[]) == 1
      assert stderr.toString('UTF-8') ==
      'Cook asks before writing, and there is no terminal to ask in; run with --batch-approve to write without asking\n'
      assert !notes.exists()

      def declined = new ByteArrayOutputStream(); stderr.reset()
      assert command(declined, stderr, 'n\n').execute(['-C', repo.path, 'acme:notes:1.2.3', '--line=first'] as String[]) == 1
      assert declined.toString('UTF-8').endsWith('\n\nApply these changes? [y/N] ')
      assert stderr.toString('UTF-8') == 'Nothing written.\n'
      assert !notes.exists()

      def accepted = new ByteArrayOutputStream()
      assert command(accepted, stderr, 'y\n').execute(['-C', repo.path, 'acme:notes:1.2.3', '--line=first'] as String[]) == 0
      assert accepted.toString('UTF-8').startsWith(review)
      assert accepted.toString('UTF-8').endsWith('Apply these changes? [y/N] Created NOTES.md\nCreated cook.lock\n\n' +
      'After applying acme:notes:1.2.3:notes\n  Read `NOTES.md`.\n')

      stdout.reset()
      assert cli.execute(['-C', repo.path, '--batch-approve', 'acme:notes:1.2.3', '--line=first', 'acme:notes:1.2.3:more'] as String[]) == 0 :
      stderr.toString('UTF-8')
      assert stdout.toString('UTF-8').endsWith('Updated NOTES.md\nUpdated cook.lock\n\nAfter applying acme:notes:1.2.3:notes\n  Read `NOTES.md`.\n')
      assert notes.text == """<!-- cook:begin acme:notes:notes#notes -->
first
<!-- cook:end acme:notes:notes#notes -->
<!-- cook:begin acme:notes:more#notes -->
more
<!-- cook:end acme:notes:more#notes -->
"""
      stdout.reset()
      assert cli.execute(['-C', repo.path, 'acme:notes:1.2.3', '--line=first', 'acme:notes:1.2.3:more'] as String[]) == 0
      assert stdout.toString('UTF-8') == 'Already cooked.\n'
      stdout.reset()
      assert cli.execute(['-C', repo.path, '--batch-approve', 'acme:notes:1.2.3', '--line=second'] as String[]) == 0
      assert stdout.toString('UTF-8').contains('\n-first\n+second\n')
      assert stdout.toString('UTF-8').contains('\nUpdated NOTES.md\n\nAfter applying')

      [
        [['acme:notes:1.2.3', '--line', 'first'], 2, '--line needs a value: write --line=<value>'],
        [['acme:notes:1.2.3', '--set=first'], 2, 'Unknown option for the recipe: --set=first'],
        [['acme:notes:1.2.3', 'line=first'], 2, 'Not a recipe: line=first. Recipe arguments start with --'],
        [
          ['acme:notes:1.2.3', '--line=a', 'notes.groovy'],
          2,
          'Not supported yet: the path notes.groovy together with recipe coordinates; apply it in its own invocation'
        ],
        [['acme:notes:1.2.3', '--line=a', '--dry-run'], 2, '--dry-run goes before the first recipe'],
        [['acme:notes:1.2.3', '--line=a', '--batch-approve'], 2, '--batch-approve goes before the first recipe'],
        [['acme:notes:1.2.3', '--line=a', '--force'], 2, '--force goes before the first recipe'],
        [['acme:notes:1.2.3', '--line=a', '--skip'], 2, '--skip goes before the first recipe'],
        [['--force', '--skip', 'acme:notes:1.2.3', '--line=a'], 2, '--force and --skip exclude each other'],
        [['--force', 'notes.groovy'], 2, '--force and --skip apply to recipe coordinates'],
        [['--skip', 'notes.groovy'], 2, '--force and --skip apply to recipe coordinates'],
        [
          ['acme:notes:1.2.3:more', 'acme:notes:1.2.3:more'],
          1,
          'recipe 2 (acme:notes:1.2.3:more): acme:notes:more is applied twice; apply it once per invocation'
        ],
        [['acme:notes:1.2.3'], 1, 'acme:notes:1.2.3: Missing --line: The line to add'],
      ].each { arguments, status, message ->
        stderr.reset()
        assert cli.execute((['-C', repo.path] + arguments) as String[]) == status
        assert stderr.toString('UTF-8').readLines().first() == message
      }
    }
  }

  @Test void usageErrorsPointToHelpInsteadOfPrintingTheUsage() {
    def stdout = new ByteArrayOutputStream(); def stderr = new ByteArrayOutputStream()
    def cli = command(stdout, stderr)
    assert cli.execute(['apply', '--mystery', 'x'] as String[]) == 2
    assert stderr.toString('UTF-8').endsWith('\nRun ./cook help for usage.\n')
    assert !stderr.toString('UTF-8').contains('Usage:')
  }
}
