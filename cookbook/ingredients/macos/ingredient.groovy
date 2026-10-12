return { kitchen, book, Map options ->
    def component = kitchen.ingredient(options.id ?: 'macos')
    component.export('files.local') {
        [[path: '.DS_Store', scope: 'anywhere', kind: 'file'],
         [path: '.AppleDouble', scope: 'anywhere', kind: 'file']]
    }
    component
}
