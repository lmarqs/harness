return { kitchen, book, Map config ->
    def recipe = kitchen.recipe(config.id ?: 'agentic-ralph')
    def parameters = config.findAll { it.key in ['mode', 'agent', 'tracker', 'workers', 'maxIterations',
        'prdPath', 'headless', 'model', 'agentOptions', 'autoCommit', 'adopt'] && it.value != null }
    def type = book.type('ingredients/ralph-tui/RalphTUI.groovy')
    def ralph = type.newInstance(parameters)
    ralph.validateParameters()
    recipe.ralph = ralph.declare(kitchen, book)
    recipe.includes(recipe.ralph)
    if (ralph.tracker != 'json') {
        recipe.beads = book.ingredient('beads', kitchen, [issuePrefix: config.issuePrefix, adopt: config.adopt])
        recipe.ralph.requires(recipe.beads); recipe.includes(recipe.beads)
    }
    if (ralph.mode == 'mise') {
        recipe.mise = config.mise ?: book.ingredient('mise', kitchen, [adopt: config.adopt])
        recipe.mise.call { tools += recipe.ralph.tools }
        recipe.ralph.requires(recipe.mise); recipe.includes(recipe.mise)
    }
    recipe
}
