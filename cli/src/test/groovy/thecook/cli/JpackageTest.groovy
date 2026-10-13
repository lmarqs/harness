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
