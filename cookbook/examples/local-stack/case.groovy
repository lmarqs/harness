import org.tomlj.Toml
import thecook.composition.Kitchen

def verify = new GroovyShell(getClass().classLoader).evaluate(new File(System.getProperty('thecook.cookbook', 'cookbook'), 'examples/verify.groovy'))
verify('local-stack-reuse') { book, root ->
  // The same station definition and endpoint convention serve two unlike repos.
  def compose = { File repository, String language, int port ->
    def kitchen = new Kitchen(repository)
    def mise = book.ingredient('mise', kitchen)
    if (language == 'go') {
      book.station('go-service', kitchen, [module: 'example.org/service', version: '1.24', mise: mise]).mount()
    } else {
      book.ingredient('node', kitchen, [version: '22', mise: mise]).mount()
      mise.tools += [[node: '22']]
    }
    def endpoint = 'http://localhost:' + port
    book.station('local-aws-stack', kitchen, [mise: mise, version: '1.10.5', endpoint: endpoint,
      services: [localstack: [image: 'localstack/localstack:4.0', ports: [port + ':4566']]],
      terraformText: 'provider "aws" {\n  region = "us-east-1"\n  endpoints { s3 = "' + endpoint + '" }\n}\n']).mount()
    kitchen
  }
  ['go', 'node'].each { language ->
    def repository = new File(root, language)
    repository.mkdirs()
    new File(repository, 'README.txt').text = 'Repository content stays independent.\n'
    compose(repository, language, 4566).plan().apply()
    def updated = compose(repository, language, 4577)
    updated.plan().apply()
    assert new File(repository, 'compose.yaml').text.contains('4577:4566')
    assert new File(repository, 'localstack/terraform/provider.tf').text.contains('http://localhost:4577')
    assert !new File(repository, 'localstack/terraform/provider.tf').text.contains('localhost:4566')
    assert new File(repository, 'README.txt').text == 'Repository content stays independent.\n'
    assert Toml.parse(new File(repository, 'mise.toml').text).getString('tools.' + language) == (language == 'go' ? '1.24' : '22')
    assert updated.plan().changes.empty
  }
}
