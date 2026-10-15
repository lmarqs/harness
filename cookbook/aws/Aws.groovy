package dev.thecook.aws

/**
 * Supplies AWS credentials, permissions, local environment values and a session-check script for other models.
 * It writes nothing itself.
 *
 * ## Writes for
 *
 * GitHub Actions step inputs for `aws-actions/configure-aws-credentials@v6.3.0` and job permissions for OIDC,
 * AWS environment variables for a local endpoint, and a Bash mise file task that runs the AWS CLI's
 * `sts get-caller-identity` command. Consuming models own the resulting files and keys.
 *
 * ## Instances
 *
 * One instance supplies AWS facts for a recipe. Its outputs do not depend on a directory, so `at` is unnecessary.
 * The credentials action reference is configurable per instance.
 *
 * ## Use
 *
 * ```groovy
 * import dev.thecook.aws.Aws
 * import dev.thecook.mise.Mise
 * import dev.thecook.terraform.Terraform
 *
 * @ingredient(Aws) def aws
 * @ingredient(Mise) def mise
 * @ingredient(Terraform) def terraform
 *
 * mise.task('localstack:resource', terraform.planCommand('infra/local/queue.tf',
 *   aws.localEnvironment('http://localhost:4566')))
 * mise.script('aws:identity', aws.sessionCheckScript())
 * ```
 *
 * ## With other models
 *
 * Workflow consumes `credentialsStep` and `oidcPermissions` for an OIDC-enabled job. Terraform accepts
 * `localEnvironment` in its plan command, which Mise renders as a task. Mise also consumes the packaged
 * session-check script as a file task. The recipe wires these facts into the consuming models.
 */
@Ingredient
class Aws {
  /** The GitHub Actions credentials action reference used by `credentialsStep`, including its version. */
  String credentialsAction = 'aws-actions/configure-aws-credentials@v6.3.0'

  /** Supplies the `awsCredentials` step using the configured action, with the given AWS region and IAM role. */
  Map credentialsStep(String region, String role) {
    [id: 'awsCredentials', uses: credentialsAction, with: ['aws-region': region, 'role-to-assume': role]]
  }

  /** Supplies job permissions to read repository contents and request an OIDC token for role assumption. */
  Map oidcPermissions() { [contents: 'read', 'id-token': 'write'] }

  /**
   * Supplies test access and secret keys and `AWS_ENDPOINT_URL` for the given local endpoint. Accepts only
   * HTTP URLs whose host is `localhost`, `127.0.0.1` or `localstack`, with an optional numeric port and no path;
   * rejects every other endpoint.
   */
  Map<String, String> localEnvironment(String endpoint) {
    if (!(endpoint ==~ /http:\/\/(localhost|127\.0\.0\.1|localstack)(:[0-9]+)?/)) throw new IllegalArgumentException("Endpoint must be local: ${endpoint}")
    [AWS_ACCESS_KEY_ID: 'test', AWS_SECRET_ACCESS_KEY: 'test', AWS_ENDPOINT_URL: endpoint]
  }

  /** Supplies the packaged Bash mise task that prints the current AWS identity; requires the AWS CLI. */
  String sessionCheckScript() { resource('check.sh') }
}
