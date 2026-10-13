package thecook.checks

import thecook.composition.Kitchen
import static thecook.checks.ModelChecks.*

class CompositionChecks {
    static void run() {
        check('Unmounted producers do not materialize their own files') { root ->
            def kitchen = new Kitchen(root); def npm = kitchen.ingredient('npm')
            npm.export('files.ephemeral') { ['node_modules/'] }
            npm.output('package.json', [owns: true]) { text { '{}' } }
            git(kitchen, '.gitignore', { npm.files.ephemeral.value })
            kitchen.plan().apply()
            assert !new File(root, 'package.json').exists()
        }
        check('Lazy connections serve multiple consumers and deduplicate paths') { root ->
            def kitchen = new Kitchen(root); def npm = kitchen.ingredient('npm')
            npm.paths = ['node_modules/']; npm.export('files.ephemeral') { npm.paths }
            def git = kitchen.ingredient('git'); git.input('ignore')
            git { ignore += npm.files.ephemeral; ignore += npm.files.ephemeral }
            git.output('.gitignore') { block('ignore') { git.ignore.value } }
            def docker = kitchen.ingredient('docker')
            docker.output('.dockerignore') { block('ignore') { git.ignore.value } }
            git.mount(); docker.mount(); npm.paths = ['node_modules/', 'npm-debug.log*']
            kitchen.plan().apply()
            assert new File(root, '.gitignore').text.count('node_modules/') == 1
            assert new File(root, '.dockerignore').text.contains('npm-debug.log*')
        }
        check('Recipe and station diamonds mount and unmount once') { root ->
            def kitchen = new Kitchen(root); def git = git(kitchen); git.unmount()
            def station = kitchen.station('station'); station.includes(git)
            def recipe = kitchen.recipe('recipe'); recipe.includes(station); recipe.includes(git); recipe.mount()
            assert kitchen.render().outputs.size() == 1
            kitchen.plan().apply(); recipe.unmount(); kitchen.plan().apply()
            assert !new File(root, '.gitignore').exists()
        }
        check('Dependencies and exclusive slots reject incomplete or incompatible composition') { root ->
            def kitchen = new Kitchen(root); def node = kitchen.ingredient('node')
            def npm = kitchen.ingredient('npm'); npm.requires(node); npm.slot = 'package-manager'; npm.mount()
            rejects('requires mounted node') { kitchen.plan() }
            node.mount(); def yarn = kitchen.ingredient('yarn'); yarn.slot = 'package-manager'; yarn.mount()
            rejects('Multiple components in slot') { kitchen.plan() }
        }
        check('Composition cycles and lazy export cycles fail explicitly') { root ->
            def kitchen = new Kitchen(root); def a = kitchen.recipe('a'); def b = kitchen.station('b')
            a.includes(b); b.includes(a); a.mount()
            rejects('Cyclic composition') { kitchen.render() }
            a.unmount(); a.export('x') { b.x.value }; b.export('x') { a.x.value }
            rejects('Cyclic export') { a.x.value }
        }
        check('Duplicate identities and regions fail while distinct block owners share a file') { root ->
            def kitchen = new Kitchen(root); def git = git(kitchen)
            rejects('Duplicate component') { kitchen.ingredient('git') }
            rejects('Duplicate region') { git.output('other') { block('x') { 'a' }; block('x') { 'b' } } }
            def other = kitchen.ingredient('other'); other.output('.gitignore') { block('x') { 'b' } }; other.mount()
            def plan = kitchen.plan()
            assert snapshot(root).isEmpty()
            plan.apply()
            def contents = new File(root, '.gitignore').text
            assert contents.contains('# cook:begin git/ignore')
            assert contents.contains('# cook:begin other/x')
            assert contents.contains('node_modules/') && contents.readLines().contains('b')
            assert kitchen.plan().changes.empty
        }
        check('Cross-kitchen mounted components fail') { root ->
            def kitchen = new Kitchen(root); def other = new Kitchen(new File(root, 'other'))
            rejects('another kitchen') { kitchen.mount(other.ingredient('git')) }
        }
        check('Unsafe and reserved output paths fail before writing') { root ->
            ['/tmp/escape', '../escape', 'a/../b', '.cook/state.json', 'a\\b', 'C:escape', 'a//b'].each { path ->
                def kitchen = new Kitchen(root); git(kitchen, path)
                rejects('Unsafe output path') { kitchen.plan() }
            }
            assert snapshot(root).isEmpty()
        }
        check('Case collisions and file-directory collisions fail before writing') { root ->
            def kitchen = new Kitchen(root); git(kitchen, 'Config')
            def other = kitchen.ingredient('other'); other.output('config') { block('x') { 'b' } }; other.mount()
            rejects('Multiple writers') { kitchen.plan() }
            other.unmount(); def nested = kitchen.ingredient('nested'); nested.output('Config/x') { block('x') { 'b' } }; nested.mount()
            rejects('File-directory collision') { kitchen.plan() }
        }
    }
}
