return { kitchen, book, options ->
  book.recipe('agentic/claude', kitchen, [instructions: 'Local recipe override.'])
}
