import groovy.json.JsonSlurper
import thecook.composition.Kitchen

def verify = new GroovyShell(getClass().classLoader).evaluate(new File(System.getProperty('thecook.cookbook', 'cookbook'), 'examples/verify.groovy'))
verify('package-manager-switch') { book, root ->
    new File(root, 'package.json').text = '{"scripts":{"test":"node test.js"},"devDependencies":{"@types/node":"^20.0.0"}}\n'
    new File(root, 'package-lock.json').text = '{"lockfileVersion":3}\n'
    new File(root, 'yarn.lock').text = '# existing Yarn lock\n'
    def npm = new Kitchen(root)
    book.recipe('development', npm, [manager: 'npm', nodeVersion: '22', adopt: true]).mount()
    npm.plan().apply()
    assert new File(root, '.gitignore').text.contains('npm-debug.log*')
    def yarn = new Kitchen(root)
    book.recipe('development', yarn, [manager: 'yarn', nodeVersion: '22']).mount()
    yarn.plan().apply()
    def manifest = new JsonSlurper().parse(new File(root, 'package.json'))
    assert manifest.packageManager == 'yarn@4.17.0'
    assert manifest.scripts.test == 'node test.js'
    def ignore = new File(root, '.gitignore').text
    assert !ignore.contains('npm-debug.log*')
    assert ignore.readLines().count { it == 'yarn-error.log*' } == 1
    assert !ignore.contains('yarn.lock')
    assert new File(root, '.mise/tasks/node/setup').text.contains('yarn install --immutable')
    assert new File(root, 'package-lock.json').text == '{"lockfileVersion":3}\n'
    assert new File(root, 'yarn.lock').text == '# existing Yarn lock\n'
    assert yarn.plan().changes.empty
}
