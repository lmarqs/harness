return { kitchen, book, Map options ->
    def station = kitchen.station(options.id ?: 'ralph-prd')
    def composition = book.recipe('agentic/ralph', kitchen, options.findAll { it.key != 'id' } + [tracker: 'json', mode: options.mode ?: 'mise'])
    station.includes(composition)
    station
}
