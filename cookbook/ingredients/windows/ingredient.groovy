return { kitchen, book, Map options ->
  def component = kitchen.ingredient(options.id ?: 'windows')
  component.export('files.local') {
    [[path: 'Thumbs.db', scope: 'anywhere', kind: 'file'], [path: 'Desktop.ini', scope: 'anywhere', kind: 'file']]
  }
  component
}
