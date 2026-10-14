@param(['service'], doc = 'Service name') String service
@param(['from'], doc = 'YAML file holding the service definition') File from
@param(['file'], default = 'compose.yaml', doc = 'The Compose file') String file

@ingredient(Compose) def compose
@ingredient(Mise) def mise

compose.file = file
compose.service(service, new YamlSlurper().parse(from))
mise.task('stack:validate', compose.validateCommand())
