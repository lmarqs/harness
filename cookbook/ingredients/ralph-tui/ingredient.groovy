return { kitchen, book, Map options ->
  def type = book.type('ingredients/ralph-tui/RalphTUI.groovy')
  def ralph = type.newInstance(options)
  def component = ralph.declare(kitchen, book)
  component.runner = ralph
  component
}
