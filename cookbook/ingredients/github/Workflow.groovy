@Ingredient
class Workflow {
  String file                         // .github/workflows/ci.yml, named by the recipe
  Map<List<String>, Object> changes = [:]

  Job job(String name) { new Job(name) }

  class Job {
    final String name
    Job(String name) { this.name = name }

    void permissions(Map grants) { grants.each { key, value -> changes[['jobs', name, 'permissions', key]] = value } }

    /** Inserts a step at `start`, `end`, `before:<id>` or `after:<id>`. The job's step list becomes owned whole. */
    void insertStep(Map step, String placement) {
      def steps = currentSteps()
      if (steps.any { it.id == step.id }) throw new IllegalArgumentException("${file}: job ${name} already has step ${step.id}")
      def index = placement == 'start' ? 0 : placement == 'end' ? steps.size() : anchored(steps, placement)
      steps.add(index, step)
      changes[['jobs', name, 'steps']] = steps
    }

    /** Changes one `with` entry of an existing step, such as a cache key. */
    void stepWith(String stepId, String key, Object value) {
      def steps = currentSteps()
      def step = steps.find { it.id == stepId }
      if (!step) throw new IllegalArgumentException("${file}: job ${name} has no step ${stepId}")
      step.with = (step.with ?: [:]) + [(key): value]
      changes[['jobs', name, 'steps']] = steps
    }

    private List<Map> currentSteps() {
      def workflow = new YamlSlurper().parse(new File(directory, file))
      def steps = workflow?.jobs?.get(name)?.steps
      if (!(steps instanceof List)) throw new IllegalArgumentException("${file}: job ${name} has no steps")
      steps.collect { new LinkedHashMap(it) }
    }

    private int anchored(List<Map> steps, String placement) {
      def (where, id) = placement.tokenize(':')
      def at = steps.findIndexOf { it.id == id }
      if (at < 0 || !(where in ['before', 'after'])) throw new IllegalArgumentException("Placement must be start, end, before:<id> or after:<id>: ${placement}")
      where == 'after' ? at + 1 : at
    }
  }

  @Keys(path = '${file}')
  Map keys() { changes ?: null }
}
