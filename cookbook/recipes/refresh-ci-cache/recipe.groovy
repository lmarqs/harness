return { kitchen, book, Map options ->
    def operations = book.script('recipes/operations.groovy')
    def recipe = kitchen.recipe(options.id ?: 'refresh-ci-cache')
    operations.replacements(kitchen, recipe, options)
    recipe
}
