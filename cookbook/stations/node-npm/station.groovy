return { kitchen, book, Map options ->
    def station = kitchen.station(options.id ?: 'node-npm')
    def recipe = book.recipe('development', kitchen, options + [manager: 'npm'])
    station.includes(recipe)
    station
}
