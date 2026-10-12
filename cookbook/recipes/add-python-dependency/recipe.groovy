return { kitchen, book, Map options ->
    def operations = book.script('recipes/operations.groovy')
    def recipe = kitchen.recipe(options.id ?: 'add-python-dependency')
    def dependency = operations.required(options, 'dependency').toString()
    def path = operations.required(options, 'manifest').toString()
    def installer = operations.required(options, 'installer').toString()
    if (!(installer in ['uv', 'pip'])) throw new IllegalArgumentException('Installer must be uv or pip')
    if (dependency.contains('\n') || dependency.contains('\r')) throw new IllegalArgumentException('Dependency must be one requirement')
    if (path.endsWith('.toml')) {
        def file = kitchen.repository.target(path)
        def parsed = file.exists() ? org.tomlj.Toml.parse(file.getText('UTF-8')) : null
        if (parsed?.hasErrors()) throw new IllegalArgumentException('Invalid pyproject TOML')
        def group = options.group
        def keyPath = group ? ['project', 'optional-dependencies', group.toString()] : ['project', 'dependencies']
        def existing = parsed?.getArray(keyPath)?.toList() ?: []
        def packageName = { String requirement ->
            def match = requirement =~ /^([A-Za-z0-9_.-]+)/
            match.find() ? match.group(1).toLowerCase().replaceAll(/[-_.]+/, '-') : null
        }
        if (!packageName(dependency)) throw new IllegalArgumentException('Dependency must begin with a package name')
        if (existing.any { it != dependency && packageName(it.toString()) == packageName(dependency) })
            throw new IllegalArgumentException('Dependency already exists with a different specification')
        def values = (existing + [dependency]).unique()
        recipe.output(path, [format: 'toml']) { key(keyPath, [adopt: options.adopt ?: false]) { values } }
        def environment = operations.required(options, 'environment').toString()
        operations.task(kitchen, book, recipe, options, 'dependency:python', (installer == 'uv' ? 'uv pip install --python ' + operations.quote(environment) : operations.quote(environment) + ' -m pip install') + ' -e ' + operations.quote(new File(path).parent ?: '.') + (group ? operations.quote('[' + group + ']') : ''))
    } else {
        recipe.output(path) { block('dependency') { [dependency] } }
        def environment = operations.required(options, 'environment').toString()
        operations.task(kitchen, book, recipe, options, 'dependency:python', (installer == 'uv' ? 'uv pip install --python ' + operations.quote(environment) : operations.quote(environment) + ' -m pip install') + ' -r ' + operations.quote(path))
    }
    recipe
}
