return { kitchen, book, Map options ->
  def mise = kitchen.ingredient(options.id ?: 'mise')
  mise.adopt = options.adopt?.toString()?.toBoolean() ?: false
  ['tools', 'settings', 'tasks', 'env'].each { mise.input(it) }
  ['tools', 'settings', 'tasks', 'env'].each { name ->
    if (options[name]) {
      def contributions = options[name] instanceof Map ? [options[name]] : options[name]
      mise.getProperty(name).from(contributions)
    }
  }
  mise.validate {
    def environments = [:]
    mise.env.value.each { entry ->
      if (!(entry instanceof Map) || !(entry.path instanceof List) || entry.path.empty ||
          entry.path.any { !(it instanceof String) || it.empty })
        throw new IllegalArgumentException('Mise environment contributions require a nonempty key path')
      def path = entry.path.toList()
      if (environments.containsKey(path) && environments[path] != entry.value)
        throw new IllegalArgumentException("Conflicting mise environment: ${path}")
      environments[path] = entry.value
    }
    def tasks = [:]
    mise.tasks.value.each { task ->
      if (!(task instanceof Map) || !(task.name?.toString() ==~ /[a-zA-Z0-9_-]+(:[a-zA-Z0-9_-]+)*/))
        throw new IllegalArgumentException("Invalid mise task: ${task}")
      def name = task.name.toString()
      if (tasks.containsKey(name) && tasks[name] != task)
        throw new IllegalArgumentException("Conflicting mise task: ${name}")
      if (task.script && task.run) throw new IllegalArgumentException("Conflicting mise task: ${name}")
      tasks[name] = task
    }
  }
  book.script('ingredients/mise/mise.toml.groovy').call(mise, book, options)
  mise
}
