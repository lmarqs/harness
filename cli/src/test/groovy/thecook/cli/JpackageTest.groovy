package thecook.cli

import org.junit.Test
import groovy.json.JsonSlurper
import org.tomlj.Toml
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class JpackageTest {
  @Test void bundledRuntimeCooksRecipesOutsideTheSourceCheckout() {
    def root = Files.createTempDirectory('cook packaged test ').toFile()
    try {
      assert run(root, '--help').contains('Usage:')
      run(root, 'init')
      assert new File(root, 'cook.groovy').isFile()
      Fixtures.copy('packaged-repository', root)
      assert run(root, 'cook', '--dry-run').contains('Would cook .gitignore')
      assert !new File(root, '.ralph-tui').exists()
      assert run(root, 'cook').contains('Cooked .gitignore')
      assert new File(root, '.gitignore').text.startsWith('# manual\n')
      assert new File(root, '.ralph-tui/config.toml').text.contains('codex')
      assert new File(root, '.codex/config.toml').text.contains('workspace-write')
      assert new File(root, '.claude/settings.json').text.contains('Bash(git reset --hard *)')
      assert run(root, 'cook').trim() == 'Already cooked.'
      assert run(root, 'init', '--unknown', 'x', 2).contains('Unknown option')
      def nested = new File(root, 'nested'); nested.mkdirs()
      assert run(nested, 'agentic/codex', '--instructions=Use tests.').contains('Cooked AGENTS.md')
      assert run(nested, 'agentic/codex', '--instructions=Use tests.').trim() == 'Already cooked.'
    } finally {
      root.deleteDir()
    }
  }

  @Test void packagedAgenticRecipesKeepIndependentApplications() {
    def root = Files.createTempDirectory('cook packaged agentic ').toFile()
    try {
      Fixtures.copy('packaged-agentic', root)
      def policy = new File(root, '.agents/policy.yaml')
      def codex = new File(root, '.codex/config.toml')
      def claude = new File(root, '.claude/settings.json')
      assert run(root, 'help', 'agentic/permission').contains('--harness CHOICE  required, repeatable')
      assert run(root, 'agentic/permission', '--policy=.agents/policy.yaml',
      '--harness=codex', '--harness', 'claude').contains('Cooked .codex/config.toml')
      assert Toml.parse(codex.text).getString('model') == 'existing-model'
      assert new JsonSlurper().parse(claude).model == 'existing-model'
      assert run(root, 'agentic/context', '--harness=codex', '--harness=claude').contains('Cooked AGENTS.md')
      assert new File(root, 'AGENTS.md').isFile()
      assert run(root, 'agentic/context', '--harness=codex', '--harness=claude').trim() == 'Already cooked.'
      def claudeBefore = claude.text
      policy.text = policy.text.replace('status', 'diff')
      assert run(root, 'agentic/permission', '--policy=.agents/policy.yaml', '--harness=codex')
      .contains('Cooked .codex/rules/cookbook.rules')
      assert new File(root, '.codex/rules/cookbook.rules').text.contains('"diff"')
      assert claude.text == claudeBefore
      assert run(root, 'agentic/permission', '--policy=.agents/policy.yaml', '--harness=codex').trim() == 'Already cooked.'
      assert run(root, 'agentic/permission', '--harness=claude', '--remove').contains('Removed .claude/settings.json')
      assert new JsonSlurper().parse(claude).model == 'existing-model'
      assert new File(root, 'CLAUDE.md').text.contains('@AGENTS.md')
      assert run(root, 'agentic/permission', '--harness=claude', '--remove').trim() == 'Already removed.'
      assert run(root, 'agentic/init', '--harness=codex', '--harness=claude').contains('Cooked .claude/settings.json')
      assert new JsonSlurper().parse(claude).model == 'existing-model'
      assert run(root, 'agentic/init', '--harness=codex', '--harness=claude').trim() == 'Already cooked.'
      def state = new JsonSlurper().parse(new File(root, '.cook/state.json'))
      assert state.applications.keySet().containsAll(['agentic.permissions/codex', 'agentic.permissions/claude', 'agentic.context/codex', 'agentic.context/claude'])

      Fixtures.copy('legacy-direct', root)
      assert run(root, 'cook').contains('Cooked .gitignore')
      assert run(root, 'cook').trim() == 'Already cooked.'
      assert run(root, new File(root, 'cook.groovy').path).trim() == 'Already cooked.'
      new File(root, 'cook.groovy').text = "import thecook.model.Batch\nnew Batch('legacy')\n"
      assert run(root, 'cook').contains('Cooked .gitignore')
      assert !new File(root, '.gitignore').exists()
      assert new File(root, 'CLAUDE.md').text.contains('@AGENTS.md')
      assert new JsonSlurper().parse(new File(root, '.cook/state.json')).applications.keySet().containsAll(
      ['agentic.permissions/codex', 'agentic.permissions/claude', 'agentic.context/claude'])

      def local = new File(root, 'local'); local.mkdirs()
      Fixtures.copy('flat-recipe', local)
      new File(local, 'policy.yaml').text = 'selected\n'
      def flat = new File(local, 'cookbook/recipes/agentic/permission.groovy')
      assert run(local, flat.path, '--policy=policy.yaml', '--harness=codex', '--harness=claude')
      .contains('Cooked selection.json')
      assert new JsonSlurper().parse(new File(local, 'selection.json')).harness == 'codex,claude'
      assert run(local, 'agentic/permission', '--policy=policy.yaml', '--harness=codex', '--harness=claude')
      .trim() == 'Already cooked.'
    } finally {
      root.deleteDir()
    }
  }

  private String run(File directory, Object... arguments) {
    def args = arguments.toList()
    def expected = args.last() instanceof Integer ? args.removeLast() : 0
    def launcher = new File(System.getProperty('cook.packagedLauncher'))
    assert launcher.isFile(): "Packaged launcher missing: ${launcher}"
    def output = new File(directory, 'process-output.txt')
    def builder = new ProcessBuilder(([launcher.absolutePath] + args) as String[])
    .directory(directory).redirectErrorStream(true).redirectOutput(output)
    ['JAVA_HOME', 'JRE_HOME', 'CLASSPATH', 'JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS'].each {
      builder.environment().remove(it)
    }
    builder.environment().put('PATH', '')
    def process = builder.start()
    if (!process.waitFor(60, TimeUnit.SECONDS)) {
      process.destroyForcibly()
      assert false: 'Packaged CLI timed out'
    }
    assert process.exitValue() == expected: output.text
    output.getText('UTF-8')
  }
}
