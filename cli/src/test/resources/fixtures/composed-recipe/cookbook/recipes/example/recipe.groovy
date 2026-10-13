return { kitchen, book, options ->
  def host = kitchen.recipe('example')
  host.includes(book.recipe('agentic/codex', kitchen, [config: [model: options.agent]]))
  host
}
