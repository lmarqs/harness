return { kitchen, book, Map options ->
  def operations = book.script('recipes/operations.groovy')
  def recipe = kitchen.recipe(options.id ?: 'migrate-aws-credentials')
  if (options.adopt != true) throw new IllegalArgumentException('AWS credential migration requires adopt: true')
  def jobs = operations.required(options, 'jobs')
  if (!(jobs instanceof List) || jobs.empty) throw new IllegalArgumentException('Explicit credential jobs required')
  if (options.files) {
    if (options.files.any { change ->
          jobs.any {
            it.workflow == change.path
          }
        })
      throw new IllegalArgumentException('Workflow replacement and credential job cannot own the same workflow')
    operations.replacements(kitchen, recipe, options)
  }
  jobs.eachWithIndex { config, index ->
    if (!(config.expectedSteps instanceof List)) throw new IllegalArgumentException('Credential migration requires expectedSteps for each job')
    def file = kitchen.repository.target(config.workflow.toString())
    if (!file.isFile()) throw new IllegalArgumentException('Credential workflow must exist')
    def yaml = new org.yaml.snakeyaml.Yaml(new org.yaml.snakeyaml.constructor.SafeConstructor(new org.yaml.snakeyaml.LoaderOptions()))
    def workflow = yaml.load(file.getText('UTF-8'))
    if (workflow?.jobs?.get(config.job)?.steps != config.expectedSteps)
      throw new IllegalArgumentException("Credential migration source differs: ${config.workflow}/${config.job}")
    recipe.includes(book.ingredient('aws-ci-credentials', kitchen, config + [id: 'aws-ci-credentials-' + index, adopt: true]))
  }
  recipe
}
