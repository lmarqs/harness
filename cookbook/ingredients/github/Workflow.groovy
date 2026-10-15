@Ingredient
class Workflow {
  String file                         // .github/workflows/ci.yml, named by the recipe
  Map<List, Object> changes = [:]

  Job job(String name) { new Job(name) }

  class Job {
    final String name
    Job(String name) { this.name = name }

    void permissions(Map grants) { grants.each { key, value -> changes[['jobs', name, 'permissions', key]] = value } }

    /** Declares one step, identified by its `id`. When absent it is inserted at `start`, `end`, `before:<id>` or `after:<id>`. */
    void step(Map step, String placement = 'end') {
      if (!step.id) throw new IllegalArgumentException("${file}: a declared step needs an id")
      changes[['jobs', name, 'steps', [id: step.id, place: placement]]] = step
    }

    /** Changes one `with` entry of an existing step, such as a cache key. */
    void stepWith(String stepId, String key, Object value) {
      changes[['jobs', name, 'steps', [id: stepId], 'with', key]] = value
    }
  }

  @Keys(path = '${file}')
  Map keys() { changes ?: null }
}
