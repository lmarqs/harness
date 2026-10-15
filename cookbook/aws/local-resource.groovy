/**
 * Writes a Terraform file and declares a mise task that plans its directory against a LocalStack endpoint.
 *
 * ## Before applying
 *
 * The input Terraform file must exist in the destination. The recipe reads its text as it is before the
 * invocation. mise, Terraform and a running LocalStack endpoint are needed to run the plan task. The endpoint
 * must be an HTTP URL at `localhost`, `127.0.0.1` or `localstack`, with an optional numeric port and no path.
 * The Terraform configuration must use the supplied AWS endpoint and test credentials for local planning.
 *
 * ## Apply
 *
 * ```sh
 * ./cook dev.thecook:aws:0.1.0:local-resource --path=infra/local/queue.tf --from=setup/queue.tf
 * ./cook dev.thecook:aws:0.1.0:local-resource --path=infra/local/queue.tf --from=setup/queue.tf --endpoint=http://localstack:4566
 * ```
 *
 * ## Writes
 *
 * | Target | Part | When |
 * | --- | --- | --- |
 * | `<path>` | The whole Terraform file, using the input text | Always |
 * | `mise.toml` | `tasks."localstack:resource"`, owned whole, with the plan command and local AWS environment | Always |
 *
 * ## After applying
 *
 * Initialize Terraform in the directory holding the written file, then run `mise run localstack:resource`.
 * The task runs `terraform plan` in that directory with test credentials and the selected endpoint. It plans
 * all configuration in that directory and does not apply resources.
 *
 * ## With other recipes
 *
 * Recipes writing distinct Terraform files compose in the same directory. Each application of this recipe
 * declares `tasks."localstack:resource"`, so the last one in an invocation selects that task's directory and
 * endpoint. Another recipe writing the same file supplies its whole content when it comes last.
 */
import dev.thecook.aws.Aws
import dev.thecook.mise.Mise
import dev.thecook.terraform.Terraform

@param(value = ['path'], doc = 'Where the Terraform file goes') String path
@param(value = ['from'], doc = 'File holding the Terraform text') File from
@param(value = ['endpoint'], default = 'http://localhost:4566', doc = 'LocalStack endpoint') String endpoint

@ingredient(Terraform) def terraform
@ingredient(Aws) def aws
@ingredient(Mise) def mise

terraform.file(path, from.text)
mise.task('localstack:resource', terraform.planCommand(path, aws.localEnvironment(endpoint)))
