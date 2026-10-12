return { kitchen, book, Map options ->
    boolean adopt = options.adopt?.toString()?.toBoolean() ?: false
    def host = kitchen.target(options.id ?: 'claude')
    host.program = 'claude'
    host.input('settings')
    if (options.settings) host.settings.from(options.settings instanceof Map ? [options.settings] : options.settings)
    host.export('files.local') { [[path: '.claude/settings.local.json', scope: 'root', kind: 'file']] }
    host.export('nativePaths') { ['CLAUDE.md', '.claude/settings.json', '.claude/settings.local.json'] }
    if (options.instructions != null) {
        host.output(options.instructionsPath ?: 'CLAUDE.md') {
            block('instructions') { options.instructions.toString() }
        }
    }
    def merge = book.script('ingredients/mise/inputs.groovy')
    host.view {
        def settings = merge(host.settings.value, 'Claude settings')
        if (settings) output(options.settingsPath ?: '.claude/settings.json', [format: 'json']) {
            settings.each { name, value -> key([name], [adopt: adopt]) { value } }
        }
    }
    host
}
