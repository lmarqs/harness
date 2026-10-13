return { kitchen, book, Map options ->
  def operations = book.script('recipes/operations.groovy')
  def recipe = kitchen.recipe(options.id ?: 'migrate-version-source')
  operations.replacements(kitchen, recipe, options)
  def mise = options.mise ?: book.ingredient('mise', kitchen, [adopt: true])
  def runtime = operations.required(options, 'runtime').toString()
  def version = operations.required(options, 'version').toString()
  mise { tools += [[(runtime): version]] }
  recipe.includes(mise)
  recipe
}
