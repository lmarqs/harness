return { kitchen, book, Map options ->
    if (!options.version) throw new IllegalArgumentException('go requires a version')
    def tool = kitchen.ingredient(options.id ?: 'go')
    tool.version = options.version.toString()
    tool.export('tools') { [[go: tool.version]] }
    if (options.mise) {
        tool.requires(options.mise)
        options.mise.tools.from(tool.tools)
    }
    tool
}
