package dev.thecook.github

/**
 * Models jobs, permissions and steps in a GitHub Actions workflow file.
 *
 * ## Writes for
 *
 * The GitHub Actions workflow YAML schema: individual job permission grants, whole steps selected by `id`,
 * and individual `with` inputs on existing steps. Each requested key or step is owned on its own.
 *
 * ## Instances
 *
 * One instance covers one workflow file, whose path the recipe sets through `file`. Use another instance
 * for a second workflow, or an instance `at` another directory for workflow paths relative to that directory.
 *
 * ## Use
 *
 * ```groovy
 * import dev.thecook.aws.Aws
 * import dev.thecook.github.Workflow
 *
 * @ingredient(Workflow) def ci
 * @ingredient(Aws) def aws
 *
 * ci.file = '.github/workflows/deploy.yml'
 * def job = ci.job('deploy')
 * job.step(aws.credentialsStep('us-east-1', 'arn:aws:iam::123456789012:role/deploy'), 'after:checkout')
 * job.permissions(aws.oidcPermissions())
 * ```
 *
 * ## With other models
 *
 * Aws supplies the credentials step and OIDC permission grants. The recipe passes those facts to the selected
 * job; Workflow owns the resulting workflow keys and step.
 */
@Ingredient
class Workflow {
  /** The workflow path relative to the instance's directory, such as `.github/workflows/ci.yml`; set before declaring writes. */
  String file
  private Map<List, Object> changes = [:]

  /** Selects a job by its workflow key; selecting it alone declares nothing. */
  Job job(String name) { new Job(name) }

  /** A job selected by name in this Workflow instance; its calls accumulate independent workflow declarations. */
  class Job {
    /** The job key under the workflow's `jobs` mapping. */
    final String name

    /** Selects the given job key within the enclosing Workflow instance without declaring any writes. */
    Job(String name) { this.name = name }

    /** Declares each permission grant separately, using permission names as keys and grant levels as values. */
    void permissions(Map grants) { grants.each { key, value -> changes[['jobs', name, 'permissions', key]] = value } }

    /**
     * Declares one whole step identified by its `id`; rejects a step without an id. An existing step is replaced.
     * An absent step is inserted at `start`, `end` (the default), `before:<id>` or `after:<id>`.
     */
    void step(Map step, String placement = 'end') {
      if (!step.id) throw new IllegalArgumentException("${file}: a declared step needs an id")
      changes[['jobs', name, 'steps', [id: step.id, place: placement]]] = step
    }

    /**
     * Declares one `with` input of the existing step identified by `stepId`, using the supplied key and value.
     * The selected step must exist in the composed workflow content.
     */
    void stepWith(String stepId, String key, Object value) {
      changes[['jobs', name, 'steps', [id: stepId], 'with', key]] = value
    }
  }

  /**
   * Each requested `jobs.<job>.permissions.<grant>`, whole `jobs.<job>.steps[id=<id>]` or
   * `jobs.<job>.steps[id=<id>].with.<key>` in the selected file, when a job declares a grant, step or input.
   * Declares nothing when no changes are requested.
   */
  @Keys(path = '${file}')
  Map keys() { changes ?: null }
}
