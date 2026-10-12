return { kitchen, book, Map options ->
    def operations = book.script('recipes/operations.groovy')
    operations.required(options, 'services')
    def station = kitchen.station(options.id ?: 'compose-dev')
    def mise = options.mise ?: book.ingredient('mise', kitchen, [adopt: options.adopt])
    def docker = book.ingredient('docker', kitchen, options.docker ?: [:])
    def compose = book.ingredient('docker-compose', kitchen, [services: options.services, path: options.path ?: 'compose.yaml', mise: mise, adopt: options.adopt])
    mise { tasks += [[name: 'stack:up', run: 'docker compose -f ' + operations.quote(options.path ?: 'compose.yaml') + ' up -d'], [name: 'stack:down', run: 'docker compose -f ' + operations.quote(options.path ?: 'compose.yaml') + ' down']] }
    [mise, docker, compose].each { station.includes(it) }
    station
}
