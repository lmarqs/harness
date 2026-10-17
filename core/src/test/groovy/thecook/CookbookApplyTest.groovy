package thecook

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import thecook.cookbook.Argument
import thecook.cookbook.Coordinates
import thecook.cookbook.CookbookApply
import thecook.cookbook.ExistingSeed
import thecook.cookbook.RecipeRequest

import java.security.MessageDigest
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

class CookbookApplyTest {
  private static final File COOKBOOK = new File(System.getProperty('cook.cookbook'))

  @Rule public TemporaryFolder folder = new TemporaryFolder()

  @Test void appliesTheAgentsInstructionsAndRecordsOwnershipInTheLock() {
    def kitchen = kitchen()
    publishDirectory(kitchen, 'agents')

    def proposal = apply(kitchen, 'dev.thecook:agents:0.1.0:instructions', [harness: ['claude']])
    assert proposal.changes == ['AGENTS.md', 'CLAUDE.md', 'cook.lock']
    assert proposal.recipes*.address == ['dev.thecook:agents:0.1.0:instructions']
    assert proposal.recipes.first().yieldText.startsWith('Have an agent fill in `AGENTS.md`')
    assert !new File(kitchen, 'AGENTS.md').exists()
    proposal.commit()

    assert new File(kitchen, 'AGENTS.md').text == new File(COOKBOOK, 'agents/AGENTS.template.md').text
    assert new File(kitchen, 'CLAUDE.md').text == '''<!-- cook:begin dev.thecook:agents:instructions#instructions -->
@AGENTS.md
<!-- cook:end dev.thecook:agents:instructions#instructions -->
'''
    assert new File(kitchen, 'cook.lock').text == """# Written by the-cook; do not edit by hand.
version: 1
recipes:
  - name: "dev.thecook:agents:instructions"
    source: "dev.thecook:agents:0.1.0:instructions"
    sha256: "${sourcesSha256(new File(COOKBOOK, 'agents'))}"
    owns:
      sections:
        - file: "CLAUDE.md"
          id: "dev.thecook:agents:instructions#instructions"
"""
    assert apply(kitchen, 'dev.thecook:agents:0.1.0:instructions', [harness: ['claude']]).changes == []
    assert !new File(kitchen, '.cook/state.json').exists()

    def fewer = apply(kitchen, 'dev.thecook:agents:0.1.0:instructions', [:])
    assert fewer.changes == ['CLAUDE.md', 'cook.lock']
    fewer.commit()
    assert !new File(kitchen, 'CLAUDE.md').exists()
    assert new File(kitchen, 'cook.lock').text.endsWith('    owns: {}\n')
  }

  @Test void refusesToStartInstructionsOverAnExistingAgentsFile() {
    def kitchen = kitchen()
    publishDirectory(kitchen, 'agents')
    new File(kitchen, 'AGENTS.md').text = '# Ours\n'
    def error = shouldFail(IllegalArgumentException) { apply(kitchen, 'dev.thecook:agents:0.1.0:instructions', [:]) }
    assert error.message == 'AGENTS.md already exists; dev.thecook:agents:instructions writes it from a template on its ' +
    'first apply. Run with --force to replace it, or --skip to keep it.'
    assert !new File(kitchen, 'cook.lock').exists()

    new File(kitchen, 'AGENTS.md').delete()
    apply(kitchen, 'dev.thecook:agents:0.1.0:instructions', [:]).commit()
    new File(kitchen, 'AGENTS.md').text = '# Filled in\n'
    def again = apply(kitchen, 'dev.thecook:agents:0.1.0:instructions', [harness: ['claude']])
    assert again.changes == ['CLAUDE.md', 'cook.lock']
  }

  @Test void forceReplacesAndSkipKeepsAnExistingAgentsFileOnTheFirstApply() {
    def kitchen = kitchen()
    publishDirectory(kitchen, 'agents')
    def template = new File(COOKBOOK, 'agents/AGENTS.template.md').text
    def agents = new File(kitchen, 'AGENTS.md')
    agents.text = '# Ours\n'

    def kept = new CookbookApply(kitchen, ExistingSeed.KEEP).propose([request('dev.thecook:agents:0.1.0:instructions', [harness: ['claude']])])
    assert kept.actions == ['AGENTS.md': 'keep', 'CLAUDE.md': 'create', 'cook.lock': 'create']
    kept.commit()
    assert agents.text == '# Ours\n'
    assert new File(kitchen, 'cook.lock').text.contains('"dev.thecook:agents:instructions"')
    assert new CookbookApply(kitchen, ExistingSeed.REPLACE).propose([request('dev.thecook:agents:0.1.0:instructions', [harness: ['claude']])])
    .actions == [:]

    new File(kitchen, 'cook.lock').delete()
    new File(kitchen, 'CLAUDE.md').delete()
    def replaced = new CookbookApply(kitchen, ExistingSeed.REPLACE).propose([request('dev.thecook:agents:0.1.0:instructions', [:])])
    assert replaced.actions == ['AGENTS.md': 'replace', 'cook.lock': 'create']
    replaced.commit()
    assert agents.text == template

    new File(kitchen, 'cook.lock').delete()
    agents.delete()
    def ours = new File(kitchen, 'OURS.md'); ours.text = '# Ours\n'
    java.nio.file.Files.createSymbolicLink(agents.toPath(), java.nio.file.Paths.get('OURS.md'))
    new CookbookApply(kitchen, ExistingSeed.REPLACE).propose([request('dev.thecook:agents:0.1.0:instructions', [:])]).commit()
    assert !java.nio.file.Files.isSymbolicLink(agents.toPath())
    assert agents.text == template
    assert ours.text == '# Ours\n'
  }

  @Test void refusesEveryExistingSeededFileOfTheCommandAtOnce() {
    def kitchen = kitchen()
    publish(kitchen, 'acme:starters:1.0.0', [
      'Starters.groovy': '''package acme.starters

@Ingredient
class Starters {
  String first
  String second
  @Seed(path = 'FIRST.md', existing = 'fail')
  String firstStarter() { first }
  @Seed(path = 'SECOND.md', existing = 'fail')
  String secondStarter() { second }
}
''',
      'one.groovy': '''@help('Seeds FIRST.md.')
import acme.starters.Starters
@ingredient(Starters) def starters
starters.first = 'first'
''',
      'two.groovy': '''@help('Seeds SECOND.md.')
import acme.starters.Starters
@ingredient(Starters) def starters
starters.second = 'second'
''',
    ])
    new File(kitchen, 'FIRST.md').text = 'ours\n'
    new File(kitchen, 'SECOND.md').text = 'ours\n'
    def error = shouldFail(IllegalArgumentException) {
      new CookbookApply(kitchen).propose([request('acme:starters:1.0.0:one', [:]), request('acme:starters:1.0.0:two', [:])])
    }
    assert error.message == '''These files already exist, and their recipes write them from a template on their first apply:
  FIRST.md (acme:starters:one)
  SECOND.md (acme:starters:two)
Run with --force to replace them, or --skip to keep them.'''
    assert !new File(kitchen, 'cook.lock').exists()
  }

  @Test void refusesToWriteASectionOrKeyThroughASymbolicLink() {
    def kitchen = kitchen()
    publishDirectory(kitchen, 'agents')
    def link = { String path, String target ->
      def file = new File(kitchen, path); file.parentFile.mkdirs()
      java.nio.file.Files.createSymbolicLink(file.toPath(), java.nio.file.Paths.get(target))
    }
    link('CLAUDE.md', 'AGENTS.md')
    def section = shouldFail(IllegalArgumentException) { apply(kitchen, 'dev.thecook:agents:0.1.0:instructions', [harness: ['claude']]) }
    assert section.message == 'dev.thecook:agents:0.1.0:instructions: CLAUDE.md is a symbolic link to AGENTS.md; ' +
    'Cook writes sections and keys only into regular files'

    new File(kitchen, '.claude').mkdirs()
    new File(kitchen, '.claude/shared.json').text = '{}\n'
    link('.claude/settings.json', 'shared.json')
    def key = shouldFail(IllegalArgumentException) { apply(kitchen, 'dev.thecook:agents:0.1.0:permissions', [harness: ['claude']]) }
    assert key.message == 'dev.thecook:agents:0.1.0:permissions: .claude/settings.json is a symbolic link to shared.json; ' +
    'Cook writes sections and keys only into regular files'
    assert new File(kitchen, '.claude/shared.json').text == '{}\n'
    assert !new File(kitchen, 'cook.lock').exists()
    assert java.nio.file.Files.isSymbolicLink(new File(kitchen, 'CLAUDE.md').toPath())
  }

  @Test void refusesASecondAgentsImportInClaudeInstructions() {
    def kitchen = kitchen()
    publishDirectory(kitchen, 'agents')
    def claude = new File(kitchen, 'CLAUDE.md')
    [
      '# Notes\n@AGENTS.md\n': 2,
      '# cook:begin claude-context/instructions\n@AGENTS.md\n# cook:end claude-context/instructions\n': 2,
    ].each { text, line ->
      claude.text = text
      def error = shouldFail(IllegalArgumentException) { apply(kitchen, 'dev.thecook:agents:0.1.0:instructions', [harness: ['claude']]) }
      assert error.message == "dev.thecook:agents:0.1.0:instructions: CLAUDE.md already imports @AGENTS.md on line ${line}; " +
      'remove that line so Cook owns the import'
      assert claude.text == text
      assert !new File(kitchen, 'cook.lock').exists()
    }

    claude.text = '# Notes\n'
    apply(kitchen, 'dev.thecook:agents:0.1.0:instructions', [harness: ['claude']]).commit()
    assert claude.text.contains('@AGENTS.md')
    assert apply(kitchen, 'dev.thecook:agents:0.1.0:instructions', [harness: ['claude']]).changes == []
  }

  @Test void composesPermissionsAndInstructionsInOneInvocation() {
    def kitchen = kitchen()
    publishDirectory(kitchen, 'agents')
    def proposal = new CookbookApply(kitchen).propose([
      request('dev.thecook:agents:0.1.0:permissions', [harness: ['claude', 'codex']]),
      request('dev.thecook:agents:0.1.0:instructions', [harness: ['claude']]),
    ])
    assert proposal.changes == ['.agents/policy.yaml', '.claude/settings.json', '.codex/config.toml', 'AGENTS.md', 'CLAUDE.md', 'cook.lock']
    proposal.commit()
    assert new File(kitchen, '.agents/policy.yaml').text == 'version: 1\ninstructions: []\ncommands: {}\n'
    assert new groovy.json.JsonSlurper().parse(new File(kitchen, '.claude/settings.json')) ==
    [permissions: [defaultMode: 'acceptEdits', allow: [], ask: [], deny: []]]
    assert new File(kitchen, '.codex/config.toml').text.contains('sandbox_mode = "workspace-write"')
    assert new File(kitchen, '.claude/settings.json').text == '''{
  "permissions": {
    "defaultMode": "acceptEdits",
    "allow": [],
    "ask": [],
    "deny": []
  }
}
'''
    def same = new CookbookApply(kitchen).propose([
      request('dev.thecook:agents:0.1.0:permissions', [harness: ['claude', 'codex']]),
      request('dev.thecook:agents:0.1.0:instructions', [harness: ['claude']]),
    ])
    assert same.changes == []

    def withoutCodex = apply(kitchen, 'dev.thecook:agents:0.1.0:permissions', [harness: ['claude']])
    assert withoutCodex.changes == ['.codex/config.toml', 'cook.lock']
    withoutCodex.commit()
    assert !new File(kitchen, '.codex').exists()
  }

  @Test void linksPackagedSkillsAndRemovesWhatALaterVersionNoLongerDeclares() {
    def kitchen = kitchen()
    publishDirectory(kitchen, 'agents', ['skill-check.groovy': """@help('Links one skill.')
import dev.thecook.agents.Agents
import dev.thecook.agents.Claude
@param(value = ['skill'], doc = 'The skill', default = []) String[] skill
@ingredient(Agents) def agents
@ingredient(Claude) def claude
agents.skills(skill)
agents.connect([claude])
"""])
    apply(kitchen, 'dev.thecook:agents:0.1.0:skill-check', [skill: ['solid']]).commit()
    assert new File(kitchen, '.agents/skills/solid/SKILL.md').text ==
    new File(COOKBOOK, 'agents/skills/solid/SKILL.md').text
    def link = new File(kitchen, '.claude/skills/solid').toPath()
    assert java.nio.file.Files.readSymbolicLink(link).toString() == '../../.agents/skills/solid'
    assert new File(kitchen, '.claude/skills/solid/SKILL.md').isFile()

    def fewer = apply(kitchen, 'dev.thecook:agents:0.1.0:skill-check', [:])
    assert fewer.changes == ['.agents/skills/solid/SKILL.md', '.claude/skills/solid', 'cook.lock']
    fewer.commit()
    assert !new File(kitchen, '.agents/skills').exists()
    assert !new File(kitchen, '.claude/skills').exists()
    assert new File(kitchen, 'CLAUDE.md').isFile()
  }

  @Test void appliesARecipePathWithModelsFromTheCookbookItDeclares() {
    def kitchen = kitchen()
    publishDirectory(kitchen, 'agents')
    def recipe = recipe(kitchen, 'recipes/skills.groovy', """@help('Links one skill.')
@cookbook('dev.thecook:0.1.0')
import dev.thecook.agents.Agents
import dev.thecook.agents.Claude
@ingredient(Agents) def agents
@ingredient(Claude) def claude
agents.skills('solid')
agents.connect([claude])
""")

    apply(kitchen, 'recipes/skills.groovy', [:]).commit()

    assert new File(kitchen, '.agents/skills/solid/SKILL.md').text ==
    new File(COOKBOOK, 'agents/skills/solid/SKILL.md').text
    assert new File(kitchen, 'cook.lock').text.contains("""  - name: "recipes/skills"
    source: "recipes/skills.groovy"
    sha256: "${sha256(recipe)}"
""")
    assert apply(kitchen, './recipes/skills.groovy', [:]).changes.empty
  }

  @Test void reportsAChangedSourceUnderARecordedVersionButNotAnEditedPath() {
    def kitchen = kitchen()
    publishDirectory(kitchen, 'agents')
    def path = recipe(kitchen, 'recipes/skills.groovy', """@help('Links one skill.')
@cookbook('dev.thecook:0.1.0')
import dev.thecook.agents.Agents
@ingredient(Agents) def agents
agents.skills('solid')
""")
    def requests = [request('dev.thecook:agents:0.1.0:instructions', [:]), request('recipes/skills.groovy', [:])]
    def first = new CookbookApply(kitchen).propose(requests)
    assert first.changedSources.empty
    first.commit()

    publishDirectory(kitchen, 'agents', ['NOTICE.md': 'changed under the same version'])
    path.text += '// edited\n'
    def again = new CookbookApply(kitchen).propose(requests)

    assert again.changedSources*.address == ['dev.thecook:agents:0.1.0:instructions']
    def changed = again.changedSources.first()
    assert changed.recorded == sourcesSha256(new File(COOKBOOK, 'agents'))
    assert changed.current != changed.recorded
    assert again.after('cook.lock').contains(changed.current)
  }

  @Test void composesARecipePathWithCoordinatesUnderItsShortName() {
    def kitchen = kitchen()
    publishDirectory(kitchen, 'agents')
    publish(kitchen, 'acme:notes:1.0.0', ['Notes.groovy': '''package acme.notes

@Ingredient
class Notes {
  private List<String> lines = []
  void add(String line) { lines += line }
  @Section(path = 'NOTES.md', id = 'notes')
  List<String> section() { lines ?: null }
}
'''])
    recipe(kitchen, 'notes.groovy', """@cookbook('acme:1.0.0')
import acme.notes.Notes
@param(value = ['line'], doc = 'The line') String line
@ingredient(Notes) def notes
notes.add(line)
""")

    def proposal = new CookbookApply(kitchen).propose([
      request('dev.thecook:agents:0.1.0:instructions', [harness: ['claude']]),
      request('notes.groovy', [line: ['first']]),
    ])

    assert proposal.recipes*.address == ['dev.thecook:agents:0.1.0:instructions', 'notes.groovy']
    proposal.commit()
    assert new File(kitchen, 'NOTES.md').text ==
    '<!-- cook:begin notes#notes -->\nfirst\n<!-- cook:end notes#notes -->\n'
    def lock = new File(kitchen, 'cook.lock').text
    assert lock.contains('- name: "dev.thecook:agents:instructions"')
    assert lock.contains('- name: "notes"\n    source: "notes.groovy"')
  }

  @Test void refusesARecipePathOutsideTheKitchenOrWithTwoReleasesOfACookbook() {
    def kitchen = kitchen()
    publishDirectory(kitchen, 'agents')
    def outside = new File(kitchen.parentFile, 'outside.groovy'); outside.text = "@help('Outside.')\nimport java.util.List\n"
    java.nio.file.Files.createSymbolicLink(new File(kitchen, 'linked.groovy').toPath(), outside.toPath())
    recipe(kitchen, 'twice.groovy', """@cookbook('dev.thecook:0.1.0')
import dev.thecook.agents.Agents
@cookbook('dev.thecook:0.2.0')
import dev.thecook.agents.Claude
""")
    [
      '../outside.groovy': '../outside.groovy: the recipe is outside the kitchen; move it into the kitchen',
      'linked.groovy': 'linked.groovy: the recipe is outside the kitchen; move it into the kitchen',
      'missing.groovy': 'missing.groovy: Recipe file not found: missing.groovy',
      'twice.groovy': 'twice.groovy: @cookbook names dev.thecook at 0.1.0 and 0.2.0; use one release per cookbook',
    ].each { operand, message ->
      def error = shouldFail(IllegalArgumentException) { apply(kitchen, operand, [:]) }
      assert error.message == message
    }
  }

  @Test void theLastRecipeToWriteAKeyOwnsIt() {
    def kitchen = kitchen()
    publish(kitchen, 'acme:notes:1.0.0', [
      'Notes.groovy': '''package acme.notes

@Ingredient
class Notes {
  private Map values = [:]
  void set(Map more) { values += more }
  @Keys(path = 'settings.json')
  Map keys() { values ?: null }
}
''',
      'first.groovy': '''@help('Sets test.')
import acme.notes.Notes
@param(value = ['value'], doc = 'The value') String value
@ingredient(Notes) def notes
notes.set(test: value, other: 'first')
''',
      'second.groovy': '''@help('Sets test.')
import acme.notes.Notes
@ingredient(Notes) def notes
notes.set(test: 'second')
''',
    ])
    new File(kitchen, 'settings.json').text = '{"test": "old", "kept": true}\n'

    apply(kitchen, 'acme:notes:1.0.0:first', [value: ['A']]).commit()
    assert new File(kitchen, 'settings.json').text == '{"test": "A", "kept": true, "other":"first"}\n'
    apply(kitchen, 'acme:notes:1.0.0:second', [:]).commit()
    assert new File(kitchen, 'settings.json').text == '{"test": "second", "kept": true, "other":"first"}\n'
    def lock = new File(kitchen, 'cook.lock').text
    assert lock.contains('''  - name: "acme:notes:first"
    source: "acme:notes:1.0.0:first"''')
    assert lock.contains('''        - file: "settings.json"
          path: ["other"]''')
    assert lock.contains('''  - name: "acme:notes:second"''')
    assert lock.indexOf('path: ["test"]') > lock.indexOf('acme:notes:second')

    def failures = [
      [['acme:notes:1.0.0:first', [:]]]: 'acme:notes:1.0.0:first: Missing --value: The value',
      [['acme:notes:1.0.0:first', [value: ['A'], other: ['x']]]]:
      'acme:notes:1.0.0:first: Unknown argument --other; its arguments are --value',
      [['acme:notes:1.0.0:second', [:]], ['acme:notes:1.0.0:second', [:]]]:
      'recipe 2 (acme:notes:1.0.0:second): acme:notes:second is applied twice; apply it once per invocation',
      [['acme:notes:1.0.0', [:]]]:
      'acme:notes:1.0.0: acme:notes:1.0.0 has no recipe named notes to apply by default; name one: ' +
      'acme:notes:1.0.0:first, acme:notes:1.0.0:second',
    ]
    failures.each { requests, message ->
      def error = shouldFail(IllegalArgumentException) {
        new CookbookApply(kitchen).propose(requests.collect { request(it[0], it[1]) })
      }
      assert error.message == message
    }
  }

  @Test void withdrawsASectionInTheCommentSyntaxItWasWrittenIn() {
    def kitchen = kitchen()
    publish(kitchen, 'acme:script:1.0.0', [
      'Script.groovy': '''package acme.script

@Ingredient
class Script {
  private List<String> lines = []
  void add(String line) { lines += line }
  @Section(path = 'build.js', id = 'steps', comment = '//')
  List<String> steps() { lines ?: null }
}
''',
      'script.groovy': '''@help('Adds build steps.')
import acme.script.Script
@param(value = ['step'], doc = 'A step', default = [], many = true) List<String> step
@ingredient(Script) def script
step.each { script.add(it) }
''',
    ])
    new File(kitchen, 'build.js').text = 'run();\n'
    apply(kitchen, 'acme:script:1.0.0', [step: ['lint();']]).commit()
    assert new File(kitchen, 'build.js').text == '''run();
// cook:begin acme:script:script#steps
lint();
// cook:end acme:script:script#steps
'''
    apply(kitchen, 'acme:script:1.0.0', [:]).commit()
    assert new File(kitchen, 'build.js').text == 'run();\n'
  }

  @Test void deletesAFileTheRecipeOwnedAndNoLongerDeclares() {
    def kitchen = kitchen()
    publish(kitchen, 'acme:config:1.0.0', [
      'Config.groovy': '''package acme.config

@Ingredient
class Config {
  Boolean migrated
  @File(path = 'old.cfg')
  String old() { migrated == false ? 'old' : null }
  @Delete(path = 'old.cfg')
  Boolean gone() { migrated ?: null }
}
''',
      'config.groovy': '''@help('Writes or retires old.cfg.')
import acme.config.Config
@param(value = ['migrated'], doc = 'Whether old.cfg is retired', default = ['false']) boolean migrated
@ingredient(Config) def config
config.migrated = migrated
''',
    ])
    apply(kitchen, 'acme:config:1.0.0', [:]).commit()
    assert new File(kitchen, 'old.cfg').text == 'old'
    apply(kitchen, 'acme:config:1.0.0', [migrated: ['true']]).commit()
    assert !new File(kitchen, 'old.cfg').exists()
  }

  @Test void namesTheRecipeWhoseModelFailsToConstruct() {
    def kitchen = kitchen()
    publish(kitchen, 'acme:broken:1.0.0', [
      'Broken.groovy': '''package acme.broken

@Ingredient
class Broken {
  Broken() { throw new IllegalStateException('cannot start') }
}
''',
      'broken.groovy': '''@help('Fails.')
import acme.broken.Broken
@ingredient(Broken) def broken
''',
    ])
    def error = shouldFail(IllegalArgumentException) { apply(kitchen, 'acme:broken:1.0.0', [:]) }
    assert error.message == 'acme:broken:1.0.0: cannot start'
  }

  @Test void refusesAModelThatDeclaresOutputOnANewInstance() {
    def kitchen = kitchen()
    publish(kitchen, 'acme:noisy:1.0.0', [
      'Noisy.groovy': '''package acme.noisy

@Ingredient
class Noisy {
  @Seed(path = 'NOTES.md')
  String notes() { 'always' }
}
''',
      'noisy.groovy': '''@help('Seeds notes.')
import acme.noisy.Noisy
@ingredient(Noisy) def noisy
''',
    ])
    def error = shouldFail(IllegalArgumentException) { apply(kitchen, 'acme:noisy:1.0.0', [:]) }
    assert error.message == 'acme:noisy:1.0.0: Noisy.notes() declares a seed of NOTES.md on a new instance; ' +
    'a model declares only what a recipe asks for'
  }

  private File kitchen() {
    def kitchen = folder.newFolder('kitchen')
    new File(kitchen, 'cook.toml').text = 'repositories = ["repository"]\n'
    kitchen
  }

  private static File recipe(File kitchen, String path, String text) {
    def file = new File(kitchen, path)
    file.parentFile.mkdirs()
    file.text = text
    file
  }

  private static def apply(File kitchen, String operand, Map<String, List<String>> arguments) {
    new CookbookApply(kitchen).propose([request(operand, arguments)])
  }

  private static RecipeRequest request(String operand, Map<String, List<String>> arguments) {
    new RecipeRequest(operand, arguments.collectMany { name, values ->
      values.collect {
        Argument.inline(name, it)
      }
    })
  }

  private static File publishDirectory(File kitchen, String name, Map<String, Object> extra = [:]) {
    def directory = new File(COOKBOOK, name)
    Map<String, Object> files = [:] + extra
    directory.eachFileRecurse { file ->
      if (file.isFile()) {
        files[directory.toPath().relativize(file.toPath()).toString()] = file.bytes
      }
    }
    publish(kitchen, "dev.thecook:${name}:0.1.0", files)
  }

  private static File publish(File kitchen, String address, Map<String, Object> files) {
    def coordinates = Coordinates.parse(address)
    def jar = new File(kitchen, "repository/${coordinates.jarPath}")
    jar.parentFile.mkdirs()
    new JarOutputStream(new FileOutputStream(jar)).withCloseable { out ->
      files.each { path, content ->
        out.putNextEntry(new JarEntry("${coordinates.packagePath}${path}"))
        out.write(content instanceof byte[] ? content : content.toString().getBytes('UTF-8'))
        out.closeEntry()
      }
    }
    jar
  }

  private static String sha256(File file) {
    sha256(file.bytes)
  }

  private static String sha256(byte[] bytes) {
    MessageDigest.getInstance('SHA-256').digest(bytes).encodeHex().toString()
  }

  // The definition in docs/architecture.md: one line `<sha256 of the file>  <path>` per file, in path order.
  private static String sourcesSha256(File directory) {
    List<String> paths = []
    directory.eachFileRecurse(groovy.io.FileType.FILES) { paths << directory.toPath().relativize(it.toPath()).toString() }
    sha256(paths.sort().collect { "${sha256(new File(directory, it))}  ${it}\n" }.join().getBytes('UTF-8'))
  }

  private static Throwable shouldFail(Class<? extends Throwable> type, Closure action) {
    try {
      action()
    } catch (Throwable error) {
      assert type.isInstance(error)
      return error
    }
    throw new AssertionError("Expected ${type.simpleName}")
  }
}
