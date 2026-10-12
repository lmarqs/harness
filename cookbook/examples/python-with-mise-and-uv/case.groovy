import org.tomlj.Toml
import thecook.composition.Kitchen

def verify = new GroovyShell(getClass().classLoader).evaluate(new File(System.getProperty('thecook.cookbook', 'cookbook'), 'examples/verify.groovy'))
verify('python-with-mise-and-uv') { book, root ->
    new File(root, 'requirements.txt').text = 'pytest==8.3.5\n'
    def compose = { version ->
        def kitchen = new Kitchen(root)
        def mise = book.ingredient('mise', kitchen)
        def python = book.ingredient('python', kitchen, [version: version, mise: mise])
        def uv = book.ingredient('uv-pip', kitchen, [python: python, mise: mise])
        def git = book.ingredient('git', kitchen)
        git.ignore += python.files.local
        git.ignore += python.files.ephemeral
        [mise, python, uv, git].each { it.mount() }
        kitchen
    }
    compose('3.13').plan().apply()
    def updated = compose('3.14')
    updated.plan().apply()
    def config = Toml.parse(new File(root, 'mise.toml').text)
    assert !config.hasErrors()
    assert config.getString('tools.python') == '3.14'
    assert config.getString('env._.python.venv.path') == '.venv'
    assert config.getBoolean('env._.python.venv.create')
    def installer = new File(root, '.mise/tasks/python/setup').text
    assert installer.contains('uv pip')
    assert installer.contains('requirements.txt')
    assert new File(root, '.gitignore').text.contains('/.venv/')
    assert new File(root, 'requirements.txt').text == 'pytest==8.3.5\n'
    assert !new File(root, '.venv').exists()
    assert updated.plan().changes.empty
}
