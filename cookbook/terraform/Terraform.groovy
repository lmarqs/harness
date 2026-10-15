package dev.thecook.terraform

/**
 * Models Terraform configuration files and supplies toolchain and plan-command facts for other models.
 *
 * ## Writes for
 *
 * Terraform HCL configuration files, owned as whole files. The plan command runs the Terraform CLI against
 * the directory containing a requested file; Mise receives the toolchain version.
 *
 * ## Instances
 *
 * One instance covers configuration files relative to its directory. Use a second instance `at` another
 * directory for a separate set of files or a plan command based there.
 *
 * ## Use
 *
 * ```groovy
 * import dev.thecook.mise.Mise
 * import dev.thecook.terraform.Terraform
 *
 * @ingredient(Terraform) def terraform
 * @ingredient(Mise) def mise
 *
 * terraform.version = '1.9.8'
 * terraform.file('infra/main.tf', 'resource "aws_sqs_queue" "jobs" { name = "jobs" }')
 * mise.tools(terraform.tools())
 * mise.task('infra:plan', terraform.planCommand('infra/main.tf'))
 * ```
 *
 * ## With other models
 *
 * Aws supplies local environment values that the recipe passes to `planCommand`. Mise consumes `tools`
 * and `planCommand` to select Terraform and render a plan task.
 */
@Ingredient
class Terraform {
  /** The Terraform toolchain version exposed by `tools`; unset means no toolchain entry. */
  String version
  private Map<String, String> files = [:]

  /**
   * Declares the whole file at `path`, relative to this instance's directory, with the given HCL content.
   * A second call for the same path replaces its content.
   */
  void file(String path, String hcl) { files[path] = hcl }

  /** Supplies the Terraform toolchain version for Mise, or an empty map when no version is set. */
  Map<String, String> tools() { version ? [terraform: version] : [:] }

  /**
   * Supplies a `terraform plan` command in the directory holding `path`, resolved relative to this instance's
   * directory. The optional environment map supplies task environment variables; it defaults to empty.
   */
  Map planCommand(String path, Map<String, String> env = [:]) {
    [run: [['terraform', 'plan']], dir: Path.of(directory, path).normalize().parent?.toString() ?: '.', env: env]
  }

  /** Each declared path as a whole file when `file` is called; declares nothing when no files are supplied. */
  @Files(path = '.')
  Map<String, String> hclFiles() { files ?: null }
}
