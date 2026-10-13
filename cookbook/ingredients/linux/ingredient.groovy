return { kitchen, book, Map options ->
  def component = kitchen.ingredient(options.id ?: 'linux')
  component.export('files.local') {
    [[path: '*~', scope: 'anywhere', kind: 'file'], [path: '.nfs*', scope: 'anywhere', kind: 'file']]
  }
  component
}
