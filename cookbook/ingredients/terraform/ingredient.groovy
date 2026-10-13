return { kitchen, book, Map options ->
  if (!options.version) throw new IllegalArgumentException('terraform requires a version')
  def tool = kitchen.ingredient(options.id ?: 'terraform')
  tool.version = options.version.toString()
  tool.export('tools') { [[terraform: tool.version]] }
  if (options.mise) {
    tool.requires(options.mise)
    options.mise.tools.from(tool.tools)
  }
  tool
}
