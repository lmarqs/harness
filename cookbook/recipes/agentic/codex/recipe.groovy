import thecook.filesystem.TargetPath
import thecook.model.KeyContribution
import thecook.model.Provider

return { kitchen, book, Map options ->
  boolean adopt = options.adopt?.toString()?.toBoolean() ?: false
  def host = kitchen.recipe(options.id ?: 'codex')
  host.program = 'codex'
  host.input('config'); host.input('hooks')
  if (options.config) host.config.from(options.config instanceof Map ? [options.config] : options.config)
  if (options.hooks) host.hooks.from(options.hooks instanceof Map ? [options.hooks] : options.hooks)
  host.export('nativePaths') { ['AGENTS.md', '.codex/config.toml', '.codex/rules/', '.codex/hooks.json'] }
  // Source-mode compatibility; published recipes already carry these compiled classes.
  book.type('ingredients/agent-policy/Policy.groovy')
  def type = book.type('recipes/agentic/codex/CodexFiles.groovy')
  host.contribute {
    def target = type.newInstance(host.id.value, adopt)
    if (options.configPath) target.configPath = TargetPath.of(options.configPath)
    if (options.hooksPath) target.hooksPath = TargetPath.of(options.hooksPath)
    if (options.instructionsPath) target.instructionsPath = TargetPath.of(options.instructionsPath)
    host.config.merge('Codex config').each { name, value ->
      target.config.add(new KeyContribution([name], { value }, adopt))
    }
    host.hooks.merge('Codex hooks').each { name, value ->
      target.hooks.add(new KeyContribution([name], { value }, adopt))
    }
    if (options.instructions != null) target.instructions = { [options.instructions.toString()] } as Provider
    (options.rules ?: [:]).each { name, content -> target.rule(name.toString(), { content.toString() } as Provider) }
    target
  }
  host
}
