return { kitchen, book, Map options ->
  def operations = book.script('recipes/operations.groovy')
  def recipe = kitchen.recipe(options.id ?: 'add-node-dependency')
  def packageName = operations.required(options, 'package').toString()
  def version = operations.required(options, 'version').toString()
  def manager = operations.required(options, 'manager').toString()
  if (!(manager in ['npm', 'pnpm', 'yarn'])) throw new IllegalArgumentException('Manager must be npm, pnpm or yarn')
  def kind = options.kind ?: 'dependencies'
  if (!(kind in ['dependencies', 'devDependencies', 'optionalDependencies', 'peerDependencies'])) throw new IllegalArgumentException('Invalid dependency kind')
  if (!(packageName ==~ /(@[a-zA-Z0-9_.-]+\/)?[a-zA-Z0-9_.-]+/)) throw new IllegalArgumentException('Invalid package name')
  def path = operations.required(options, 'manifest').toString()
  recipe.output(path, [format: 'json']) {
    key([kind, packageName], [adopt: options.adopt ?: false]) {
      version
    }
  }
  def directory = new File(path).parent ?: '.'
  operations.task(kitchen, book, recipe, options, 'dependency:node', 'cd ' + operations.quote(directory) + ' && ' + manager + ' install')
  recipe
}
