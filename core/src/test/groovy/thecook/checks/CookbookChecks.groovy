package thecook.checks

import groovy.json.JsonSlurper
import org.tomlj.Toml
import thecook.composition.Kitchen
import thecook.cookbook.Cookbook
import static thecook.checks.ModelChecks.*

class CookbookChecks {
  static Cookbook cookbook() {
    def directory = new File(System.getProperty('thecook.cookbook', 'cookbook'))
    if (!directory.isDirectory()) directory = new File('../cookbook')
    new Cookbook(directory)
  }

  static void run() {
    check('Cookbook producers remain lazy and Git explicitly consumes ephemeral exports') { root ->
      def book = cookbook(); def kitchen = new Kitchen(root)
      def node = book.ingredient('node', kitchen, [version: '22'])
      def npm = book.ingredient('npm', kitchen)
      def git = book.ingredient('git', kitchen)
      git { ignore += node.files.ephemeral; ignore += npm.files.ephemeral }
      assert snapshot(root).isEmpty()
      git.mount(); kitchen.plan().apply()
      assert new File(root, '.gitignore').text.contains('node_modules/')
      assert !new File(root, 'package.json').exists()
      assert !new File(root, '.gitignore').text.contains('package-lock.json')
      assert !new File(root, '.gitignore').text.contains('yarn.lock')
      assert kitchen.plan().changes.empty
    }
    check('Package manager switching preserves manual package data and lockfiles with one stable writer') { root ->
      write(root, 'package.json', '{"scripts":{"test":"node test.js"},"devDependencies":{"@types/node":"^20.0.0"}}\n')
      write(root, 'package-lock.json', '{"lockfileVersion":3}\n')
      write(root, 'yarn.lock', '# durable Yarn lock\n')
      def book = cookbook(); def npm = new Kitchen(root)
      book.recipe('development', npm, [manager: 'npm', nodeVersion: '22', adopt: true]).mount()
      npm.plan().apply()
      def initial = new JsonSlurper().parse(new File(root, 'package.json'))
      assert initial.packageManager == 'npm@10.9.8'
      assert initial.devDependencies['@types/node'] == '^22.0.0'
      assert initial.scripts.test == 'node test.js'
      def yarn = new Kitchen(root)
      book.recipe('development', yarn, [manager: 'yarn', nodeVersion: '24']).mount()
      yarn.plan().apply()
      def switched = new JsonSlurper().parse(new File(root, 'package.json'))
      assert switched.packageManager == 'yarn@4.17.0'
      assert switched.devDependencies['@types/node'] == '^24.0.0'
      assert switched.scripts == initial.scripts
      assert new File(root, 'package-lock.json').text == '{"lockfileVersion":3}\n'
      assert new File(root, 'yarn.lock').text == '# durable Yarn lock\n'
      assert new File(root, '.mise/tasks/node/setup').text.contains('yarn install --immutable')
      assert yarn.plan().changes.empty
    }
    check('Node changes update mise and existing types without adding optional Node types') { root ->
      write(root, 'mise.toml', '# shared configuration\n[settings]\nexperimental = true\n[tools]\nnode = "20"\n')
      def book = cookbook(); def kitchen = new Kitchen(root)
      book.recipe('development', kitchen, [nodeVersion: '22', adopt: true]).mount()
      kitchen.plan().apply()
      assert new File(root, 'mise.toml').text.contains('node = "22"')
      assert new File(root, 'mise.toml').text.contains('experimental = true')
      assert !new File(root, 'package.json').text.contains('@types/node')
      assert new File(root, '.mise/tasks/node/setup').text.contains('package-lock.json')
      assert new File(root, '.mise/tasks/node/lock').text.contains('npm install --package-lock-only')
    }
    check('Projects can author an ingredient and recipe using the public cookbook API') { root ->
      def definitions = new File(root, 'definitions')
      write(definitions, 'ingredients/greeting/message.txt', 'Hello from a project ingredient.\n')
      write(definitions, 'ingredients/greeting/ingredient.groovy', '''
                return { kitchen, book, options ->
                    def greeting = kitchen.ingredient('greeting')
                    greeting.output('greeting.txt', [owns: true]) {
                        text { book.text('ingredients/greeting/message.txt') }
                    }
                    greeting
                }
            ''')
      write(definitions, 'recipes/project/recipe.groovy', '''
                return { kitchen, book, config ->
                    def recipe = kitchen.recipe('project')
                    recipe.includes(book.ingredient('greeting', kitchen, config))
                    recipe
                }
            ''')
      def kitchen = new Kitchen(new File(root, 'repository'))
      new Cookbook(definitions).recipe('project', kitchen).mount()
      assert !new File(root, 'repository/greeting.txt').exists()
      kitchen.plan().apply()
      assert new File(root, 'repository/greeting.txt').text == 'Hello from a project ingredient.\n'
      assert kitchen.plan().changes.empty
    }
    check('Typed Ralph supports live parameters and standalone to mise reconciliation') { root ->
      def book = cookbook(); def type = book.type('ingredients/ralph-tui/RalphTUI.groovy')
      def ralph = type.newInstance([mode: 'standalone', agent: 'codex', tracker: 'json', workers: 1])
      def kitchen = new Kitchen(root); ralph.mount(kitchen); kitchen.plan().apply()
      assert new File(root, '.ralph-tui/run').isFile()
      assert new File(root, '.ralph-tui/config.toml').text.contains('tracker = "json"')
      ralph.mode = 'mise'; ralph.workers = 3; ralph.tracker = 'beads'; ralph.agent = 'claude'
      kitchen.plan().apply()
      assert !new File(root, '.ralph-tui/run').exists()
      assert new File(root, '.mise/tasks/ralph/run').canExecute()
      def script = new File(root, '.mise/tasks/ralph/run').text
      assert script.contains('--epic')
      assert !script.contains('--mode')
      assert new File(root, '.ralph-tui/config.toml').text.contains('defaultAgent = "claude"')
      def nativeConfig = Toml.parse(new File(root, '.ralph-tui/config.toml').text)
      assert !nativeConfig.hasErrors()
      assert nativeConfig.getLong('parallel.maxWorkers') == 3
      assert nativeConfig.getString('parallel.mode') == 'auto'
      assert nativeConfig.getBoolean('autoCommit') == false
      assert !script.contains('git worktree')
      assert ralph.files.ephemeral.value.every { it.path.endsWith('.lock') }
      assert ralph.files.local.value.any { it.path.endsWith('session.json') }
      assert ralph.files.local.value.any { it.path.endsWith('progress.md') }
      assert kitchen.plan().changes.empty
    }
    check('Beads preserves shared tracker files while exporting local databases separately') { root ->
      write(root, '.beads/config.yaml', '# repository settings\nexport:\n  auto: true\n')
      write(root, '.beads/issues.jsonl', '{"id":"project-1"}\n')
      def book = cookbook(); def kitchen = new Kitchen(root)
      def beads = book.ingredient('beads', kitchen, [issuePrefix: 'project'])
      def git = book.ingredient('git', kitchen)
      git { ignore += beads.files.ephemeral; ignore += beads.files.local }
      beads.mount(); git.mount(); kitchen.plan().apply()
      assert new File(root, '.beads/config.yaml').text.contains('auto: true')
      assert new File(root, '.beads/config.yaml').text.contains('"issue-prefix": "project"')
      assert new File(root, '.beads/issues.jsonl').text == '{"id":"project-1"}\n'
      assert !new File(root, '.gitignore').text.contains('/.beads/\n')
      assert !beads.files.ephemeral.value.any { it.path.contains('dolt') || it.path.contains('.db') }
      assert kitchen.plan().changes.empty
    }
    check('Mise rejects conflicting tool pins before writing native files') { root ->
      def kitchen = new Kitchen(root); def mise = cookbook().ingredient('mise', kitchen)
      mise { tools += [[node: '22']]; tools += [[node: '24']] }
      mise.mount()
      rejects('Conflicting mise tools: node') { kitchen.plan() }
      assert snapshot(root).isEmpty()
    }
    check('Existing Node pins require explicit adoption before Cook claims the key') { root ->
      def original = '# existing native config\n[tools]\nnode = "20"\npython = "3.13"\n'
      write(root, 'mise.toml', original)
      def book = cookbook(); def kitchen = new Kitchen(root)
      book.recipe('development', kitchen, [nodeVersion: '22']).mount()
      def before = snapshot(root)
      rejects('explicit adopt required') { kitchen.plan() }
      assert snapshot(root) == before
      def adopting = new Kitchen(root)
      book.recipe('development', adopting, [nodeVersion: '22', adopt: true]).mount()
      adopting.plan().apply()
      def config = Toml.parse(new File(root, 'mise.toml').text)
      assert config.getString('tools.node') == '22'
      assert config.getString('tools.python') == '3.13'
      assert adopting.plan().changes.empty
    }
    check('Mise renders shared settings and rejects two commands for the same task') { root ->
      def book = cookbook(); def kitchen = new Kitchen(root)
      def mise = book.ingredient('mise', kitchen, [tools: [node: '22'], settings: [experimental: true],
        tasks: [[name: 'check', run: 'node --version', description: 'Check the selected runtime']]])
      mise.mount(); kitchen.plan().apply()
      def config = Toml.parse(new File(root, 'mise.toml').text)
      assert !config.hasErrors()
      assert config.getBoolean('settings.experimental')
      assert config.getString('tasks.check.run') == 'node --version'
      mise { tasks += [[name: 'check', script: '#!/usr/bin/env bash\nexit 0\n']] }
      rejects('Conflicting mise task: check') { kitchen.plan() }
    }
    check('Ralph launcher forwards Beads and agent arguments without starting a real agent') { root ->
      def book = cookbook(); def kitchen = new Kitchen(root)
      book.ingredient('ralph-tui', kitchen, [agent: 'claude', tracker: 'beads', headless: true]).mount()
      kitchen.plan().apply()
      def binary = new File(root, 'test-bin/ralph-tui')
      write(root, 'test-bin/ralph-tui', '#!/usr/bin/env bash\nprintf "%s\\n" "$@"\n')
      binary.setExecutable(true)
      def command = new ProcessBuilder('bash', '.ralph-tui/run', 'project-42', '--iterations', '5').directory(root)
      command.environment().put('PATH', binary.parent + File.pathSeparator + command.environment().get('PATH'))
      def process = command.start()
      assert process.waitFor() == 0: process.errorStream.text
      assert process.inputStream.text.readLines() == [
        'run',
        '--agent',
        'claude',
        '--tracker',
        'beads',
        '--epic',
        'project-42',
        '--headless',
        '--iterations',
        '5'
      ]
    }
    check('String Ralph options produce native boolean and numeric values') { root ->
      def kitchen = new Kitchen(root)
      cookbook().ingredient('ralph-tui', kitchen, [workers: '2', maxIterations: '5', autoCommit: 'true']).mount()
      kitchen.plan().apply()
      def config = Toml.parse(new File(root, '.ralph-tui/config.toml').text)
      assert !config.hasErrors()
      assert config.getLong('parallel.maxWorkers') == 2
      assert config.getLong('maxIterations') == 5
      assert config.getBoolean('autoCommit')
    }
    check('Ralph variants reject unsupported options before rendering any native file') { root ->
      def type = cookbook().type('ingredients/ralph-tui/RalphTUI.groovy')
      [[mode: 'daemon'], [agent: 'unknown'], [tracker: 'other'], [workers: 0], [workers: 33], [adopt: 'sometimes']].each { invalid ->
        def kitchen = new Kitchen(root)
        def ralph = type.newInstance(invalid)
        rejects('Ralph') { ralph.mount(kitchen); kitchen.plan() }
        assert snapshot(root).isEmpty()
      }
      rejects('Unknown Ralph parameter') { type.newInstance([unsupported: true]) }
    }
  }
}
