return { kitchen, book, Map options ->
  boolean adopt = options.adopt?.toString()?.toBoolean() ?: false
  def compose = kitchen.ingredient(options.id ?: 'docker-compose')
  if (options.docker) compose.requires(options.docker)
  compose.input('services')
  compose.input('config')
  if (options.services) compose.services.from(options.services instanceof Map ? [options.services] : options.services)
  if (options.config) compose.config.from(options.config instanceof Map ? [options.config] : options.config)
  def merge = book.script('ingredients/mise/inputs.groovy')
  compose.output(options.path ?: 'compose.yaml', [format: 'yaml']) {
    view {
      merge(compose.services.value, 'Compose service').each { name, service ->
        key(['services', name], [adopt: adopt]) { service }
      }
      merge(compose.config.value, 'Compose config').each { name, value ->
        if (name == 'services') throw new IllegalArgumentException('Use services input for Compose services')
        key([name], [adopt: adopt]) { value }
      }
    }
  }
  compose
}
