return { kitchen, book, Map options ->
    def operations = book.script('recipes/operations.groovy')
    def recipe = kitchen.recipe(options.id ?: 'add-local-aws-resource')
    def path = operations.required(options, 'path').toString()
    def content = operations.required(options, 'terraformText').toString()
    def endpoint = operations.required(options, 'endpoint').toString()
    if (!(endpoint ==~ /http:\/\/(localhost|127\.0\.0\.1|localstack)(:[0-9]+)?/)) throw new IllegalArgumentException('Resource endpoint must be LocalStack')
    operations.whole(recipe, path, content, options.adopt == true)
    def directory = new File(path).parent ?: '.'
    operations.task(kitchen, book, recipe, options, 'localstack:resource', 'AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_ENDPOINT_URL=' + operations.quote(endpoint) + ' terraform -chdir=' + operations.quote(directory) + ' plan')
    recipe
}
