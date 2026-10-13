return { kitchen, book, Map options ->
  def operations = book.script('recipes/operations.groovy')
  def recipe = kitchen.recipe(options.id ?: 'implement-bead')
  def issue = operations.required(options, 'issue').toString()
  def run = operations.required(options, 'runCommand').toString()
  if (!run.contains(issue)) throw new IllegalArgumentException('runCommand must explicitly select the supplied Beads issue')
  def composition = book.recipe('agentic/ralph', kitchen, options.findAll { it.key != 'id' } + [tracker: 'beads', mode: 'mise'])
  recipe.includes(composition)
  operations.task(kitchen, book, recipe, options + [mise: composition.mise], 'ralph:bead', 'bd show ' + operations.quote(issue) + ' && ' + run)
  recipe
}
