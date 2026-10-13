return { kitchen, book, Map options ->
  def station = kitchen.station(options.id ?: 'beads-tracker')
  def beads = book.ingredient('beads', kitchen, [issuePrefix: options.issuePrefix, adopt: options.adopt])
  station.includes(beads)
  if (options.ignoreLocal == true) {
    def git = options.git ?: book.ingredient('git', kitchen)
    git { ignore += beads.files.local; ignore += beads.files.ephemeral }
    station.includes(git)
  }
  station
}
