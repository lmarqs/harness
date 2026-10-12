return { kitchen, book, Map options ->
    def yarn = kitchen.ingredient(options.id ?: 'yarn')
    yarn.version = (options.version ?: '4.17.0').toString()
    yarn.slot = options.slot ?: 'node-package-manager'
    if (options.node) yarn.requires(options.node)
    yarn.validate {
        if (!(yarn.version ==~ /[0-9]+\.[0-9]+\.[0-9]+/)) throw new IllegalArgumentException('Yarn version must be exact')
    }
    yarn.export('tools') { [[yarn: yarn.version]] }
    yarn.export('packageFields') { [[packageManager: 'yarn@' + yarn.version]] }
    yarn.export('files.ephemeral') {
        [[path: 'yarn-error.log*', scope: 'anywhere', kind: 'file'],
         [path: '.yarn/install-state.gz', scope: 'root', kind: 'file'],
         [path: '.yarn/unplugged', scope: 'root', kind: 'directory']]
    }
    yarn.export('tasks') {
        [[name: 'node:setup', script: book.text('ingredients/yarn/setup.sh')],
         [name: 'node:lock', script: book.text('ingredients/yarn/lock.sh')]]
    }
    yarn
}
