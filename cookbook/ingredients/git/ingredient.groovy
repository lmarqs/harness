return { kitchen, book, Map options ->
  def git = kitchen.ingredient(options.id ?: 'git')
  git.input('ignore')
  git.ignore += options.ignore ?: []
  def pattern = book.script('ingredients/git/ignore.groovy')
  git.output(options.path ?: '.gitignore') {
    block('ignore') { git.ignore.value.collect { pattern(it) }.unique() }
  }
  git
}
