return { kitchen, book, Map options ->
    boolean adopt = options.adopt?.toString()?.toBoolean() ?: false
    def host = kitchen.target(options.id ?: 'codex')
    host.program = 'codex'
    host.input('config')
    host.input('hooks')
    if (options.config) host.config.from(options.config instanceof Map ? [options.config] : options.config)
    if (options.hooks) host.hooks.from(options.hooks instanceof Map ? [options.hooks] : options.hooks)
    host.export('nativePaths') { ['AGENTS.md', '.codex/config.toml', '.codex/rules/', '.codex/hooks.json'] }
    if (options.instructions != null) {
        host.output(options.instructionsPath ?: 'AGENTS.md') {
            block('instructions') { options.instructions.toString() }
        }
    }
    def merge = book.script('ingredients/mise/inputs.groovy')
    host.view {
        [['config', 'toml', options.configPath ?: '.codex/config.toml'],
         ['hooks', 'json', options.hooksPath ?: '.codex/hooks.json']].each { spec ->
            def contributions = merge(host.getProperty(spec[0]).value, "Codex ${spec[0]}")
            if (contributions) output(spec[2], [format: spec[1]]) {
                contributions.each { name, value -> key([name], [adopt: adopt]) { value } }
            }
        }
    }
    (options.rules ?: [:]).each { name, content ->
        if (!(name.toString() ==~ /[a-zA-Z0-9_.-]+/) || name in ['.', '..'])
            throw new IllegalArgumentException('Codex rule file requires a simple file name')
        host.output('.codex/rules/' + name, [owns: true, adopt: adopt]) {
            text { content.toString() }
        }
    }
    host
}
