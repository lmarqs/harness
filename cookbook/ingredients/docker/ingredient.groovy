return { kitchen, book, Map options ->
    boolean adopt = options.adopt?.toString()?.toBoolean() ?: false
    def docker = kitchen.ingredient(options.id ?: 'docker')
    docker.input('ignore')
    docker.ignore.from(options.ignore ?: ['.git/', '.beads/', '.ralph-tui/', '.codex/', '.claude/'])
    docker.output(options.path ?: '.dockerignore', [owns: true, adopt: adopt]) {
        text { docker.ignore.value.unique().join('\n') + '\n' }
    }
    docker
}
