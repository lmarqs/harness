return { kitchen, book, Map options ->
    def component = kitchen.ingredient(options.id ?: 'visualstudiocode')
    component.export('files.local') {
        [[path: '.vscode', scope: 'root', kind: 'directory']]
    }
    component
}
