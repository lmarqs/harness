return { kitchen, book, Map options ->
  def station = kitchen.station(options.id ?: 'git-repository')
  station.includes(book.ingredient('git', kitchen, [ignore: options.ignore ?: [], path: options.ignorePath ?: '.gitignore']))
  if (options.agentsText != null) station.output('AGENTS.md', [owns: true, adopt: options.adopt ?: false]) {
    text {
      options.agentsText.toString()
    }
  }
  station
}
