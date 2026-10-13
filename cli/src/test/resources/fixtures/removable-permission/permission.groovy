return { kitchen, book, Map options ->
  def source = new File(kitchen.repository.directory, options.policy)
  def selected = options.harness ?: 'claude'
  def component = kitchen.recipe(selected)
  component.application('agentic.permissions/' + selected)
  component.output('settings.json', [format: 'json']) {
    key([selected], [adopt: true]) { source.text.trim() }
  }
  component
}
