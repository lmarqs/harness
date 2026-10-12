return { kitchen, book, Map options ->
    def operations = book.script('recipes/operations.groovy')
    def recipe = kitchen.recipe(options.id ?: 'add-go-dependency')
    def module = operations.required(options, 'module').toString()
    def version = operations.required(options, 'version').toString()
    def path = operations.required(options, 'manifest').toString()
    if (!(module ==~ /[a-zA-Z0-9_.\/-]+/) || !(version ==~ /v[0-9][a-zA-Z0-9_.+-]*/)) throw new IllegalArgumentException('Go module and version must be explicit valid tokens')
    def file = kitchen.repository.target(path)
    if (!file.isFile()) throw new IllegalArgumentException('Selected go.mod must exist')
    def original = file.getText('UTF-8')
    def declaration = original.readLines().find { it.trim().startsWith(module + ' ') || it.trim().startsWith('require ' + module + ' ') }
    if (declaration && !(declaration.trim() in [module + ' ' + version, 'require ' + module + ' ' + version]))
        throw new IllegalArgumentException('Module already exists with another version; update its existing declaration explicitly')
    operations.whole(recipe, path, declaration ? original : original + (original.endsWith('\n') ? '' : '\n') + 'require ' + module + ' ' + version + '\n', options.adopt == true)
    operations.task(kitchen, book, recipe, options, 'dependency:go', 'cd ' + operations.quote(new File(path).parent ?: '.') + ' && go mod tidy && go test ./...')
    recipe
}
