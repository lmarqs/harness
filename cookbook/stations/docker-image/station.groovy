return { kitchen, book, Map options ->
    def operations = book.script('recipes/operations.groovy')
    def dockerfile = operations.required(options, 'dockerfileText').toString()
    def image = operations.required(options, 'image').toString()
    def station = kitchen.station(options.id ?: 'docker-image')
    station.includes(book.ingredient('docker', kitchen, options.docker ?: [:]))
    def path = options.path ?: 'Dockerfile'
    operations.whole(station, path, dockerfile, options.adopt == true)
    operations.task(kitchen, book, station, options, 'docker:build', 'docker build -f ' + operations.quote(path) + ' -t ' + operations.quote(image) + ' .')
    station
}
