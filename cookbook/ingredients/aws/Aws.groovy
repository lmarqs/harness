/** AWS knowledge other models consume. Writes nothing. */
@Ingredient
class Aws {
  String credentialsAction = 'aws-actions/configure-aws-credentials@v6.3.0'

  Map credentialsStep(String region, String role) {
    [id: 'awsCredentials', uses: credentialsAction, with: ['aws-region': region, 'role-to-assume': role]]
  }

  Map oidcPermissions() { [contents: 'read', 'id-token': 'write'] }

  Map<String, String> localEnvironment(String endpoint) {
    if (!(endpoint ==~ /http:\/\/(localhost|127\.0\.0\.1|localstack)(:[0-9]+)?/)) throw new IllegalArgumentException("Endpoint must be local: ${endpoint}")
    [AWS_ACCESS_KEY_ID: 'test', AWS_SECRET_ACCESS_KEY: 'test', AWS_ENDPOINT_URL: endpoint]
  }

  String sessionCheckScript() { resource('check.sh') }
}
