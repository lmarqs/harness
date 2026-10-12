return { kitchen, book, Map options ->
    def pip = kitchen.ingredient(options.id ?: 'uv-pip')
    if (options.python) pip.requires(options.python)
    pip.requirements = (options.requirements ?: 'requirements.txt').toString()
    if (!(pip.requirements ==~ /[a-zA-Z0-9_.\/-]+/) || pip.requirements.startsWith('/') || pip.requirements.tokenize('/').contains('..'))
        throw new IllegalArgumentException('requirements must be a relative project path')
    pip.export('tasks') {
        [[name: options.task ?: 'python:setup', script: book.text('ingredients/uv-pip/setup.sh').replace('@REQUIREMENTS@', pip.requirements)]]
    }
    if (options.mise) {
        pip.requires(options.mise)
        options.mise.tasks.from(pip.tasks)
    }
    pip
}
