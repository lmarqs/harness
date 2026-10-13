package thecook.cli

import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class JpackageTest {
  @Test void bundledRuntimeCooksRecipesOutsideTheSourceCheckout() {
    def root = Files.createTempDirectory('cook packaged test ').toFile()
    try {
      assert run(root, '--help').contains('Usage:')
      run(root, 'init')
      assert new File(root, 'cook.groovy').isFile()
      new File(root, 'cook.groovy').text = '''
        import thecook.cookbook.ingredients.git.Git
        import thecook.cookbook.ingredients.ralphtui.*
        import thecook.cookbook.ingredients.agentpolicy.Policy
        import thecook.cookbook.recipes.agentic.codex.CodexFiles
        import thecook.cookbook.recipes.agentic.claude.ClaudeFiles
        import thecook.model.Batch
        def ralph = new Ralph(new RalphConfig(agent: 'codex'))
        def git = new Git()
        git.ignore.add(ralph.files.local)
        def policy = new Policy([], [new Policy.Command(Policy.Decision.DENY, ['git', 'reset', '--hard'])])
        new Batch('packaged').include(git).include(ralph)
          .include(new CodexFiles(policy)).include(new ClaudeFiles(policy))
      '''
      new File(root, '.gitignore').text = '# manual\n'
      assert run(root, 'cook', '--dry-run').contains('Would cook .gitignore')
      assert !new File(root, '.ralph-tui').exists()
      assert run(root, 'cook').contains('Cooked .gitignore')
      assert new File(root, '.gitignore').text.startsWith('# manual\n')
      assert new File(root, '.ralph-tui/config.toml').text.contains('codex')
      assert new File(root, '.codex/config.toml').text.contains('workspace-write')
      assert new File(root, '.claude/settings.json').text.contains('Bash(git reset --hard *)')
      assert run(root, 'cook').trim() == 'Already cooked.'
      assert run(root, 'init', '--unknown', 'x', 2).contains('Unknown option')
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
