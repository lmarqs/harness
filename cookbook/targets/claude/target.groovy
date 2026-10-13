import thecook.filesystem.TargetPath
import thecook.model.KeyContribution
import thecook.model.Provider

return { kitchen, book, Map options ->
  boolean adopt = options.adopt?.toString()?.toBoolean() ?: false
  def host = kitchen.target(options.id ?: 'claude')
  host.program = 'claude'
  host.input('settings')
  if (options.settings) host.settings.from(options.settings instanceof Map ? [options.settings] : options.settings)
  host.export('files.local') { [[path: '.claude/settings.local.json', scope: 'root', kind: 'file']] }
  host.export('nativePaths') { ['CLAUDE.md', '.claude/settings.json', '.claude/settings.local.json'] }
  book.type('ingredients/agent-policy/Policy.groovy')
  def type = book.type('targets/claude/Claude.groovy')
  def merge = book.script('ingredients/mise/inputs.groovy')
  host.contribute {
    def target = type.newInstance(host.id.value)
    if (options.settingsPath) target.settingsPath = TargetPath.of(options.settingsPath)
    if (options.instructionsPath) target.instructionsPath = TargetPath.of(options.instructionsPath)
    merge(host.settings.value, 'Claude settings').each { name, value ->
      target.settings.add(new KeyContribution([name], { value }, adopt))
    }
    if (options.instructions != null) target.instructions = { [options.instructions.toString()] } as Provider
    target
  }
  host
}
