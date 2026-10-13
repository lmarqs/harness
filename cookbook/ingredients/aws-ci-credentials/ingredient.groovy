return { kitchen, book, Map options ->
  boolean adopt = options.adopt?.toString()?.toBoolean() ?: false
  ['workflow', 'job', 'region', 'role', 'placement'].each { name ->
    if (!options[name]) throw new IllegalArgumentException("AWS CI credentials requires ${name}")
  }
  if (!(options.steps instanceof List))
    throw new IllegalArgumentException('AWS CI credentials requires the complete job steps list')
  def credentials = kitchen.ingredient(options.id ?: 'aws-ci-credentials')
  credentials.input('steps')
  credentials.steps.from(options.steps)
  credentials.export('step') {
    [
      [id: 'awsCredentials', uses: options.action ?: 'aws-actions/configure-aws-credentials@v6.3.0',
        with: ['aws-region': options.region, 'role-to-assume': options.role]]
    ]
  }
  credentials.export('permissions') { [[contents: 'read', 'id-token': 'write']] }
  credentials.export('configuredSteps') {
    def steps = credentials.steps.value.collect { step ->
      if (!(step instanceof Map)) throw new IllegalArgumentException('Job steps must be maps')
      new LinkedHashMap(step)
    }
    if (steps.any { it.id == 'awsCredentials' })
      throw new IllegalArgumentException('Job already has an awsCredentials step')
    def placement = options.placement
    int index
    if (placement == 'start') index = 0
    else if (placement == 'end') index = steps.size()
    else if (placement instanceof Map && placement.size() == 1 && (placement.containsKey('before') || placement.containsKey('after'))) {
      def selector = placement.before ?: placement.after
      def matches = (0..<steps.size()).findAll { steps[it].id == selector }
      if (matches.size() != 1) throw new IllegalArgumentException("AWS credential placement must match one step: ${selector}")
      index = matches[0] + (placement.containsKey('after') ? 1 : 0)
    } else throw new IllegalArgumentException('AWS credential placement must be start, end, before or after a step id')
    steps.add(index, credentials.step.value[0])
    steps
  }
  credentials.output(options.workflow.toString(), [format: 'yaml']) {
    view {
      credentials.permissions.value[0].each { name, value ->
        key(['jobs', options.job.toString(), 'permissions', name], [adopt: adopt]) { value }
      }
      key(['jobs', options.job.toString(), 'steps'], [adopt: adopt]) { credentials.configuredSteps.value }
    }
  }
  credentials
}
