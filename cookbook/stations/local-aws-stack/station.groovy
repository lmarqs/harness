return { kitchen, book, Map options ->
  def operations = book.script('recipes/operations.groovy')
  def endpoint = operations.required(options, 'endpoint').toString()
  if (!(endpoint ==~ /http:\/\/(localhost|127\.0\.0\.1|localstack)(:[0-9]+)?/)) throw new IllegalArgumentException('LocalStack endpoint must be local HTTP')
  def services = operations.required(options, 'services')
  def terraformText = operations.required(options, 'terraformText').toString()
  if (!terraformText.contains(endpoint)) throw new IllegalArgumentException('Terraform configuration must contain the selected LocalStack endpoint')
  def station = kitchen.station(options.id ?: 'local-aws-stack')
  def mise = options.mise ?: book.ingredient('mise', kitchen, [adopt: options.adopt])
  def stack = book.station('compose-dev', kitchen, [services: services, path: options.composePath ?: 'compose.yaml', mise: mise, docker: options.docker ?: [:], adopt: options.adopt])
  def terraform = book.ingredient('terraform', kitchen, [version: operations.required(options, 'version'), mise: mise, adopt: options.adopt])
  def directory = options.terraformDirectory ?: 'localstack/terraform'
  operations.whole(station, directory + '/provider.tf', terraformText, options.adopt == true)
  mise {
    tasks += [
      [name: 'localstack:provision', run: 'AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test terraform -chdir=' + operations.quote(directory) + ' init && AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test terraform -chdir=' + operations.quote(directory) + ' apply']
    ]
  }
  [stack, terraform, mise].each { station.includes(it) }
  station
}
