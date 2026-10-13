package thecook.cli

import groovy.json.JsonSlurper
import org.junit.Test
import org.tomlj.Toml
import java.nio.file.Files

class RepositoryRecipeTest {
  private void setup(Closure body) {
    def root = Files.createTempDirectory('cook-policy-').toFile()
    try {
      def recipe = new File(root, 'cookbook/recipes/cookbook/recipe.groovy')
      recipe.parentFile.mkdirs()
      def source = new File('../cookbook/recipes/cookbook/recipe.groovy')
      assert source.isFile(): 'The repository cookbook recipe is missing'
      recipe.text = source.text
      Fixtures.copy('agent-policy', root)
      def policy = new File(root, '.agents/policy.yaml')
      def skills = new File(root, 'shared-skills')
      def out = new ByteArrayOutputStream(); def err = new ByteArrayOutputStream()
      def loader = getClass().classLoader
      def service = loader.loadClass('thecook.application.CookService').newInstance(
          loader.loadClass('thecook.groovy.GroovyBatchLoader').newInstance(),
          loader.loadClass('thecook.groovy.NativePlanning').newInstance(),
          loader.loadClass('thecook.cli.filesystem.FileRepositoryInitializer').newInstance())
      def cli = loader.loadClass('thecook.cli.terminal.CookCommand').newInstance(service, new PrintWriter(out, true), new PrintWriter(err, true))
      def args = ['cookbook', '--repo', root.path, '--policy=.agents/policy.yaml', '--skills-home=' + skills.path]
      body(root, policy, cli, args, out, err)
    } finally {
      root.deleteDir()
    }
  }

  @Test void rendersBothTargetsPreservingUnrelatedSettingsAndRecooksWithoutChanges() {
    setup { root, policy, cli, args, out, err ->
      def codex = new File(root, '.codex/config.toml'); codex.parentFile.mkdirs()
      codex.text = 'model = "existing-model" # keep\n'
      def claude = new File(root, '.claude/settings.json'); claude.parentFile.mkdirs()
      claude.text = '{"model":"existing-model","permissions":{"additionalDirectories":["../shared"]}}\n'
      def originalPolicy = policy.text
      assert cli.execute((args + ['--dry-run']) as String[]) == 0 : err.toString('UTF-8')
      assert !new File(root, 'AGENTS.md').exists()
      assert !new File(root, '.cook').exists()
      assert cli.execute(args as String[]) == 0 : err.toString('UTF-8')
      assert policy.text == originalPolicy
      assert new File(root, 'AGENTS.md').text.contains('Preserve unrelated work.')
      assert new File(root, 'CLAUDE.md').text.contains('@AGENTS.md')
      assert Files.isSymbolicLink(new File(root, '.agents/skills/solid').toPath())
      assert Files.isSymbolicLink(new File(root, '.claude/skills/solid').toPath())
      def config = Toml.parse(codex.text)
      assert !config.hasErrors()
      assert config.getString('model') == 'existing-model'
      assert config.getString('sandbox_mode') == 'workspace-write'
      assert config.getString('approval_policy') == 'on-request'
      def settings = new JsonSlurper().parse(claude)
      assert settings.model == 'existing-model'
      assert settings.permissions.additionalDirectories == ['../shared']
      assert settings.permissions.deny.contains('Bash(git reset --hard *)')
      assert settings.permissions.allow.contains('Bash(git push origin main *)')
      assert settings.permissions.ask.contains('Bash(terraform apply *)')
      assert new File(root, '.codex/rules/cookbook.rules').text.contains('decision = "forbidden"')
      out.reset()
      assert cli.execute(args as String[]) == 0 : err.toString('UTF-8')
      assert out.toString('UTF-8').trim() == 'Already cooked.'
      Fixtures.copy('agent-policy-composition', root)
      out.reset()
      assert cli.execute(['cook', '--repo', root.path] as String[]) == 0 : err.toString('UTF-8')
      assert out.toString('UTF-8').trim() == 'Already cooked.'
    }
  }

  @Test void changingThePolicyUpdatesBothTargetsAndManagedDriftRejectsAtomically() {
    setup { root, policy, cli, args, out, err ->
      assert cli.execute(args as String[]) == 0 : err.toString('UTF-8')
      policy.text = policy.text.replace('[terraform, apply]', '[terraform, destroy]')
      assert cli.execute(args as String[]) == 0 : err.toString('UTF-8')
      assert new File(root, '.claude/settings.json').text.contains('terraform destroy')
      def rules = new File(root, '.codex/rules/cookbook.rules')
      assert rules.text.contains('"destroy"')
      def beforeRules = rules.text
      def config = new File(root, '.codex/config.toml')
      config.text = config.text.replace('workspace-write', 'danger-full-access')
      policy.text = policy.text.replace('Preserve unrelated work.', 'Changed instructions.')
      assert cli.execute(args as String[]) == 1
      assert rules.text == beforeRules
      assert !new File(root, 'AGENTS.md').text.contains('Changed instructions.')
    }
  }

  @Test void missingUnknownAndMalformedInputsFailBeforeWriting() {
    setup { root, policy, cli, args, out, err ->
      assert cli.execute(['cookbook', '--repo', root.path] as String[]) == 1
      assert err.toString('UTF-8').contains('Required argument: --policy')
      assert cli.execute((args + ['--polciy=x']) as String[]) == 1
      assert err.toString('UTF-8').contains('Unknown recipe argument: polciy')
      for (invalid in [
            'version: 2\n',
            'version: 1\ncommands: {allow: ["git status"]}\n',
            'version: 1\nversion: 1\n',
            '!!java.net.URL ["https://example.com"]',
            'version: 1\ncommands: {allow: [[git, "*"]]}\n'
          ]) {
        policy.text = invalid
        assert cli.execute(args as String[]) == 1 : invalid
        assert !new File(root, 'AGENTS.md').exists()
        assert !new File(root, '.cook').exists()
      }
    }
  }

  @Test void existingPermissionValuesRequireExplicitAdoption() {
    setup { root, policy, cli, args, out, err ->
      def config = new File(root, '.codex/config.toml'); config.parentFile.mkdirs()
      config.text = 'sandbox_mode = "read-only"\n'
      assert cli.execute(args as String[]) == 1
      assert config.text == 'sandbox_mode = "read-only"\n'
      assert !new File(root, 'AGENTS.md').exists()
      assert cli.execute((args + ['--adopt=true']) as String[]) == 0 : err.toString('UTF-8')
      assert Toml.parse(config.text).getString('sandbox_mode') == 'workspace-write'
    }
  }

  @Test void externalRecipeUsesDestinationPolicyAndRelativeSkills() {
    setup { root, policy, cli, args, out, err ->
      def supplier = new File(root, 'supplier')
      def external = new File(supplier, 'cookbook/recipes/cookbook/recipe.groovy')
      external.parentFile.mkdirs()
      external.text = new File(root, 'cookbook/recipes/cookbook/recipe.groovy').text
      def supplierPolicy = new File(supplier, '.agents/policy.yaml')
      supplierPolicy.parentFile.mkdirs()
      supplierPolicy.text = policy.text.replace('Preserve unrelated work.', 'Wrong supplier instructions.')
      def selected = [
        external.path,
        '--repo',
        root.path,
        '--set',
        'policy=.agents/policy.yaml',
        '--set',
        'skills-home=shared-skills'
      ]
      assert cli.execute((selected + ['--dry-run']) as String[]) == 0 : err.toString('UTF-8')
      assert !new File(root, 'AGENTS.md').exists()
      assert cli.execute(selected as String[]) == 0 : err.toString('UTF-8')
      assert new File(root, 'AGENTS.md').text.contains('Preserve unrelated work.')
      assert !new File(root, 'AGENTS.md').text.contains('Wrong supplier instructions.')
      assert Files.readSymbolicLink(new File(root, '.agents/skills/solid').toPath()).toString() ==
      new File(root, 'shared-skills/solid').absolutePath
      assert !new File(supplier, '.cook').exists()
    }
  }
}
