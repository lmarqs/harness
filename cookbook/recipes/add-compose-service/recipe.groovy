return { kitchen, book, Map options ->
  def operations = book.script('recipes/operations.groovy')
  def recipe = kitchen.recipe(options.id ?: 'add-compose-service')
  def service = operations.required(options, 'service').toString()
  def configuration = operations.required(options, 'configuration')
  if (!(configuration instanceof Map) || configuration.isEmpty()) throw new IllegalArgumentException('Compose service requires a configuration map')
  def mise = options.mise ?: book.ingredient('mise', kitchen, [adopt: options.adopt])
  def compose = book.ingredient('docker-compose', kitchen, [services: [(service): configuration], path: operations.required(options, 'path'), mise: mise, adopt: options.adopt])
  mise { tasks += [[name: 'stack:validate', run: 'docker compose -f ' + operations.quote(options.path) + ' config']] }
  [mise, compose].each { recipe.includes(it) }
  recipe
}
