return { kitchen, book, Map options ->
  def operations = book.script('recipes/operations.groovy')
  def recipe = kitchen.recipe(options.id ?: 'grill')
  def script = operations.required(options, 'script').toString()
  operations.task(kitchen, book, recipe, options, 'grill', script)
  recipe
}
