return { kitchen, book, Map options ->
    def operations = book.script('recipes/operations.groovy')
    def recipe = kitchen.recipe(options.id ?: 'update-docker-base')
    if (options.adopt != true) throw new IllegalArgumentException('Docker stage update requires adopt: true')
    def path = operations.required(options, 'path').toString()
    def stage = operations.required(options, 'stage').toString()
    def expected = operations.required(options, 'expectedImage').toString()
    def image = operations.required(options, 'image').toString()
    if (!(image ==~ /[a-zA-Z0-9_.:@\/-]+/)) throw new IllegalArgumentException('Image must be a literal image reference')
    def file = kitchen.repository.target(path)
    if (!file.isFile()) throw new IllegalArgumentException('Dockerfile must exist')
    def lines = file.getText('UTF-8').readLines()
    def matches = []
    lines.eachWithIndex { line, index ->
        def matcher = line =~ /(?i)^(\s*FROM\s+)(--platform=\S+\s+)?(\S+)(\s+AS\s+)(\S+)(\s*)$/
        if (matcher.matches() && matcher[0][5] == stage) matches << [index: index, match: matcher[0]]
    }
    if (matches.size() != 1 || !(matches[0].match[3] in [expected, image])) throw new IllegalArgumentException('Expected exactly one matching named Docker stage and old image')
    def selected = matches[0]
    lines[selected.index] = selected.match[1] + (selected.match[2] ?: '') + image + selected.match[4] + selected.match[5] + selected.match[6]
    operations.whole(recipe, path, lines.join('\n') + '\n', true)
    operations.task(kitchen, book, recipe, options, 'docker:verify', 'docker build -f ' + operations.quote(path) + ' --target ' + operations.quote(stage) + ' .')
    recipe
}
