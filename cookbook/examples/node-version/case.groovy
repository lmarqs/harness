import groovy.json.JsonSlurper
import org.tomlj.Toml
import thecook.composition.Kitchen

def verify = new GroovyShell(getClass().classLoader).evaluate(new File(System.getProperty('thecook.cookbook', 'cookbook'), 'examples/verify.groovy'))
verify('node-version') { book, root ->
  new File(root, 'mise.toml').text = '[settings]\nexperimental = true\n[tools]\nnode = "20"\npython = "3.13"\n'
  new File(root, 'package.json').text = '{"devDependencies":{"@types/node":"^20.0.0"}}\n'
  def unadopted = new Kitchen(root)
  book.recipe('development', unadopted, [nodeVersion: '22']).mount()
  try {
    unadopted.plan(); assert false: 'Existing pins must require adoption'
  }
  catch (IllegalStateException expected) {
    assert expected.message.contains('adopt')
  }
  def kitchen = new Kitchen(root)
  book.recipe('development', kitchen, [nodeVersion: '22', adopt: true]).mount()
  kitchen.plan().apply()
  def config = Toml.parse(new File(root, 'mise.toml').text)
  assert config.getString('tools.node') == '22'
  assert config.getString('tools.python') == '3.13'
  assert config.getBoolean('settings.experimental')
  assert new JsonSlurper().parse(new File(root, 'package.json')).devDependencies['@types/node'] == '^22.0.0'
  assert new File(root, '.gitignore').text.contains('node_modules/')
  assert new File(root, '.mise/tasks/node/lock').text.contains('npm install --package-lock-only')
  assert kitchen.plan().changes.empty
}
