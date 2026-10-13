return { kitchen, book, Map options ->
  def operations = book.script('recipes/operations.groovy')
  def recipe = kitchen.recipe(options.id ?: 'implement-prd-story')
  def path = operations.required(options, 'prdPath').toString()
  def story = operations.required(options, 'storyId').toString()
  def file = kitchen.repository.target(path)
  if (!file.isFile()) throw new IllegalArgumentException('Supplied PRD must exist')
  def prd = new groovy.json.JsonSlurper().parseText(file.getText('UTF-8'))
  if (!(prd instanceof Map) || !(prd.userStories instanceof List)) throw new IllegalArgumentException('PRD requires userStories list')
  def selected = prd.userStories.findAll { it.id?.toString() == story }
  if (selected.size() != 1) throw new IllegalArgumentException('PRD must contain exactly one selected story')
  def selectedPath = options.selectedPrdPath ?: '.ralph-tui/selected-prd.json'
  operations.whole(recipe, selectedPath, groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(prd + [userStories: selected])) + '\n', options.adopt == true)
  def composition = book.recipe('agentic/ralph', kitchen, options.findAll { it.key != 'id' } + [tracker: 'json', prdPath: selectedPath, mode: options.mode ?: 'mise'])
  recipe.includes(composition)
  recipe
}
