package thecook.cli

import groovy.json.JsonSlurper
import org.junit.Test
import org.tomlj.Toml
import java.nio.file.Files

class CookCommandTest {
  private Object command(ByteArrayOutputStream stdout, ByteArrayOutputStream stderr) {
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
    entrypoint.newInstance(service, new PrintWriter(stdout, true), new PrintWriter(stderr, true))
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
      assert cli.execute(['cook', '--repo', repo.path, '--set', 'agent=claude'] as String[]) == 0
      assert Toml.parse(new File(repo, '.ralph-tui/config.toml').text).getString('defaultAgent') == 'claude'
      assert new File(repo, '.gitignore').text.startsWith('# manual\n')
      assert !new File(repo, '.cook/plan.json').exists()
      assert stdout.toString('UTF-8').contains('Cooked .gitignore')
      def configuration = new File(repo, '.ralph-tui/config.toml').text
      stdout.reset()
      assert cli.execute(['cook', '--repo', repo.path, '--set', 'agent=claude'] as String[]) == 0
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
      assert cli.execute(['cook', '--dry-run', '--repo', repo.path, '--script', script.path] as String[]) == 0
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
      def args = ['cook', '--repo', repo.path, '--script', script.path] as String[]
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
      assert cli.execute([script.path, '--repo', repo.path, '--dry-run'] as String[]) == 0
      assert !new File(repo, '.ralph-tui').exists()
      assert cli.execute([script.path, '--repo', repo.path, '--set', 'agent=claude'] as String[]) == 0
      assert Toml.parse(new File(repo, '.ralph-tui/config.toml').text).getString('defaultAgent') == 'claude'
      assert cli.execute([script.path, '--script', script.path] as String[]) == 2
      assert stderr.toString('UTF-8').contains('once')
    }
  }

  @Test void namedRecipesReceiveArgumentsWithEitherEqualsOrSeparateValues() {
    workspace { root, repo, script ->
      Fixtures.copy('composed-recipe', repo)
      def stdout = new ByteArrayOutputStream(); def stderr = new ByteArrayOutputStream()
      def cli = command(stdout, stderr)
      assert cli.execute(['example', '--repo', repo.path, '--agent=claude', '--dry-run'] as String[]) == 0 : stderr.toString('UTF-8')
      assert !new File(repo, '.codex').exists()
      assert cli.execute(['example', '--repo', repo.path, '--agent', 'claude'] as String[]) == 0
      assert Toml.parse(new File(repo, '.codex/config.toml').text).getString('model') == 'claude'
      stdout.reset()
      assert cli.execute(['example', '--repo', repo.path, '--set', 'agent=claude'] as String[]) == 0
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
      def base = ['declared', '--repo', repo.path, '--policy=.agents/policy.yaml']
      for (invalid in [
            ['--harness=other'], ['--harness=codex', '--policy=again'],
            ['--harness=codex', '--unknown=x'], []
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
      assert cli.execute([external.path, '--repo', externalRepo.path, '--set',
          'policy=.agents/policy.yaml', '--set', 'harness=codex', '--set', 'harness=claude'] as String[]) == 0 : stderr.toString('UTF-8')
      output = new JsonSlurper().parse(new File(externalRepo, 'selection.json'))
      assert output.policy == 'destination'
      assert output.harness == 'codex,claude'
    }
  }

  @Test void nestedPackagedRecipeIsRunnableWithoutACookbookCheckout() {
    workspace { root, repo, script ->
      def stdout = new ByteArrayOutputStream(); def stderr = new ByteArrayOutputStream()
      def cli = command(stdout, stderr)
      assert cli.execute(['agentic/codex', '--repo', repo.path, '--instructions=Use tests.'] as String[]) == 0 : stderr.toString('UTF-8')
      assert new File(repo, 'AGENTS.md').text.contains('Use tests.')
      stdout.reset()
      assert cli.execute(['agentic/codex', '--repo', repo.path, '--instructions=Use tests.'] as String[]) == 0
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
      def options = ['--repo', repo.path, '--policy=.agents/policy.yaml', '--harness=codex', '--harness', 'claude']
      assert cli.execute(([source.path] + options + ['--dry-run']) as String[]) == 0 : err.toString('UTF-8')
      assert !new File(repo, 'selection.json').exists()
      assert !new File(repo, '.cook').exists()
      assert cli.execute(([source.path] + options) as String[]) == 0 : err.toString('UTF-8')
      assert new JsonSlurper().parse(new File(repo, 'selection.json')).harness == 'codex,claude'
      out.reset()
      assert cli.execute((['agentic/permission'] + options) as String[]) == 0 : err.toString('UTF-8')
      assert out.toString('UTF-8').trim() == 'Already cooked.'
      out.reset()
      assert cli.execute(['agentic/permission', '--repo', repo.path, '--set', 'policy=.agents/policy.yaml',
          '--set', 'harness=codex', '--set', 'harness=claude'] as String[]) == 0 : err.toString('UTF-8')
      assert out.toString('UTF-8').trim() == 'Already cooked.'
    }
  }

  @Test void flatRecipeHelpAndInvalidInputsDoNotReadPolicyOrChangeOwnership() {
    workspace { root, repo, ignored ->
      Fixtures.copy('flat-recipe', repo)
      def source = new File(repo, 'cookbook/recipes/agentic/permission.groovy')
      def out = new ByteArrayOutputStream(); def err = new ByteArrayOutputStream()
      def cli = command(out, err)
      assert cli.execute([source.path, '--repo', repo.path, '--help'] as String[]) == 0 : err.toString('UTF-8')
      assert out.toString('UTF-8').contains('--policy')
      assert out.toString('UTF-8').contains('required')
      assert out.toString('UTF-8').contains('codex, claude')
      assert out.toString('UTF-8').contains('--dry-run')
      out.reset()
      assert cli.execute(['agentic/permission', '--repo', repo.path, '--help'] as String[]) == 0 : err.toString('UTF-8')
      assert out.toString('UTF-8').contains('--harness')
      assert !new File(repo, '.cook').exists()
      def policy = new File(repo, '.agents/policy.yaml'); policy.parentFile.mkdirs(); policy.text = 'destination\n'
      def valid = [source.path, '--repo', repo.path, '--policy=.agents/policy.yaml', '--harness=codex']
      assert cli.execute(valid as String[]) == 0 : err.toString('UTF-8')
      def before = new File(repo, 'selection.json').text
      def ownership = new File(repo, '.cook/state.json').text
      for (args in [
          valid + ['--set', 'policy=other'],
          valid + ['--harness=invalid'],
          [new File(repo, 'missing.groovy').path, '--repo', repo.path, '--policy=x']
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
      assert cli.execute(['agentic/permission', '--repo', repo.path, '--help'] as String[]) == 1
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
      assert cli.execute([source.path, '--repo', repo.path, '--help'] as String[]) == 0 : err.toString('UTF-8')
      assert out.toString('UTF-8').contains('--harness')
      assert cli.execute([source.path, '--repo', repo.path, '--policy=.agents/policy.yaml',
          '--harness=codex', '--harness=claude'] as String[]) == 0 : err.toString('UTF-8')
      assert new JsonSlurper().parse(new File(repo, 'selection.json')).harness == 'codex,claude'
    }
  }

  @Test void localRecipeOverridesPackagedFactory() {
    workspace { root, repo, script ->
      Fixtures.copy('recipe-override', repo)
      def stdout = new ByteArrayOutputStream(); def stderr = new ByteArrayOutputStream()
      def cli = command(stdout, stderr)
      assert cli.execute(['agentic/codex', '--repo', repo.path] as String[]) == 0 : stderr.toString('UTF-8')
      assert new File(repo, 'CLAUDE.md').text.contains('Local recipe override.')
      assert !new File(repo, 'AGENTS.md').exists()
    }
  }

  @Test void compiledRuntimeRecipeRunsThroughTheSameFactoryEntryPoint() {
    workspace { root, repo, script ->
      def stdout = new ByteArrayOutputStream(); def stderr = new ByteArrayOutputStream()
      def cli = command(stdout, stderr)
      assert cli.execute(['agentic/runtime', '--repo', repo.path, '--agent=claude'] as String[]) == 0 : stderr.toString('UTF-8')
      assert Toml.parse(new File(repo, '.ralph-tui/config.toml').text).getString('defaultAgent') == 'claude'
      stdout.reset()
      assert cli.execute(['agentic/runtime', '--repo', repo.path, '--agent=claude'] as String[]) == 0
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
      assert cli.execute(['example', '--agent=a', '--agent=b', '--repo', repo.path] as String[]) == 1
      assert cli.execute(['example', '--agent=a', '--set', 'agent=b', '--repo', repo.path] as String[]) == 1
      assert cli.execute(['missing', '--repo', repo.path] as String[]) == 1
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
      def args = [script.path, '--repo', repo.path, '--set', 'skill=' + source.path] as String[]
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
      def args = [script.path, '--repo', repo.path] as String[]
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
      assert cli.execute(['init', '--repo', repo.path] as String[]) == 0
      assert script.text.contains('new Batch(')
      assert new File(repo, '.gitignore').text == '# manual\n'
      script.text += '// keep this customization\n'
      def customized = script.text
      assert cli.execute(['init', '--repo', repo.path] as String[]) == 0
      assert script.text == customized
      assert cli.execute(['plan', '--repo', repo.path] as String[]) == 0
      def plan = new File(repo, '.cook/plan.json')
      assert new JsonSlurper().parse(plan).changes.empty
      assert cli.execute(['apply', '--repo', repo.path] as String[]) == 0
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
      assert cli.execute(['plan', '--repo', repo.path, '--script', script.path, '--plan', plan.path] as String[]) == 0
      assert cli.execute(['apply', '--repo', repo.path, '--plan', plan.path] as String[]) == 0
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
      assert cli.execute(['init', '--repo', new File(root, 'missing').path] as String[]) == 1
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
      assert cli.execute(['plan', '--repo', repo.path, '--script', script.path, '--plan', saved.path, '--set', 'agent=claude'] as String[]) == 0
      assert saved.isFile()
      assert !new File(repo, '.ralph-tui/config.toml').exists()
      assert new File(repo, '.gitignore').text == '# maintained by the team\n'
      assert new JsonSlurper().parse(saved).changes*.path.toSet() ==
      ['.gitignore', '.ralph-tui/config.toml', '.ralph-tui/run'].toSet()
      assert cli.execute(['apply', '--repo', repo.path, '--plan', saved.path] as String[]) == 0
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
      assert cli.execute(['plan', '--repo', repo.path, '--script', script.path, '--plan', saved.path] as String[]) == 0
      new File(repo, '.gitignore').text = '# changed after review\n'
      assert cli.execute(['apply', '--repo', repo.path, '--plan', saved.path] as String[]) == 1
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
      assert cli.execute(['plan', '--repo', repo.path, '--script', script.path, '--plan', saved.path] as String[]) == 1
      assert repo.listFiles().length == 0
      assert !saved.exists()
      assert stderr.toString('UTF-8').contains('must return a Batch')
    }
  }

  @Test void missingAndUnknownOptionsAreUsageErrors() {
    def stdout = new ByteArrayOutputStream(); def stderr = new ByteArrayOutputStream()
    def cli = command(stdout, stderr)
    assert cli.execute(['--help'] as String[]) == 0
    assert stdout.toString('UTF-8').contains('cook plan')
    assert cli.execute(['plan', '--script'] as String[]) == 2
    assert cli.execute(['apply', '--mystery', 'x'] as String[]) == 2
    assert cli.execute(['apply', '--script', 'cook.groovy', '--plan', 'plan.json'] as String[]) == 2
  }
}
