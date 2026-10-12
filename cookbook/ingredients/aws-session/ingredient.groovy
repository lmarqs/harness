return { kitchen, book, Map options ->
    def session = kitchen.ingredient(options.id ?: 'aws-session')
    session.export('tasks') {
        [[name: options.task ?: 'aws:check', script: book.text('ingredients/aws-session/check.sh')]]
    }
    if (options.mise) {
        session.requires(options.mise)
        options.mise.tasks.from(session.tasks)
    }
    session
}
