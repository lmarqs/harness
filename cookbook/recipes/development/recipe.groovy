// Minimal composition example; projects can author their own recipes with the same API.
return { kitchen, book, Map config ->
    def selected = config.manager ?: 'npm'
    if (!(selected in ['npm', 'yarn'])) throw new IllegalArgumentException('Node package manager is npm or yarn')
    def recipe = kitchen.recipe('development')
    def adopt = config.adopt?.toString()?.toBoolean() ?: false
    def mise = book.ingredient('mise', kitchen, [adopt: adopt])
    def node = book.ingredient('node', kitchen, [version: config.nodeVersion ?: '22', mise: mise, adopt: adopt])
    def manager = book.ingredient(selected, kitchen, [node: node, version: config[selected + 'Version']])
    def git = book.ingredient('git', kitchen)
    node { packageFields += manager.packageFields }
    mise { tools += node.tools; tools += manager.tools; tasks += manager.tasks }
    git { ignore += node.files.ephemeral; ignore += manager.files.ephemeral }
    [mise, node, manager, git].each { recipe.includes(it) }
    recipe
}
