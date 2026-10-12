package thecook.checks

import groovy.json.JsonSlurper
import thecook.composition.Kitchen
import static thecook.checks.ModelChecks.*

class ReconciliationChecks {
    static void run() {
        check('Manager switch removes obsolete fragments and preserves manual prefix and suffix') { root ->
            write(root, '.gitignore', '# Team\n.env.local\n')
            def kitchen = new Kitchen(root); def manager = kitchen.ingredient('manager')
            manager.paths = ['node_modules/', 'npm-debug.log*']; manager.export('files.ephemeral') { manager.paths }
            git(kitchen, '.gitignore', { manager.files.ephemeral.value }); kitchen.plan().apply()
            write(root, '.gitignore', new File(root, '.gitignore').text + '# Added later\nlocal-output/\n')
            manager.paths = ['node_modules/', '.yarn/cache/']; kitchen.plan().apply()
            def text = new File(root, '.gitignore').text
            assert text.startsWith('# Team\n.env.local\n') && text.endsWith('# Added later\nlocal-output/\n')
            assert !text.contains('npm-debug') && text.contains('.yarn/cache/')
            assert !text.contains('package-lock.json') && !text.contains('yarn.lock')
        }
        check('Unmount restores a missing final newline and retains manual additions') { root ->
            write(root, '.gitignore', '# Without newline')
            def kitchen = new Kitchen(root); def git = git(kitchen)
            kitchen.plan().apply(); git.unmount(); kitchen.plan().apply()
            assert new File(root, '.gitignore').text == '# Without newline'
            new File(root, '.gitignore').delete(); git.mount(); kitchen.plan().apply()
            write(root, '.gitignore', new File(root, '.gitignore').text + 'manual/\n')
            git.unmount(); kitchen.plan().apply()
            assert new File(root, '.gitignore').text == 'manual/\n'
        }
        check('CRLF, Unicode, and preexisting empty files survive mount and unmount') { root ->
            write(root, '.gitignore', '# Café\r\nmanual/\r\n')
            def kitchen = new Kitchen(root); def git = git(kitchen); kitchen.plan().apply()
            assert !new File(root, '.gitignore').text.replace('\r\n', '').contains('\n')
            git.unmount(); kitchen.plan().apply()
            assert new File(root, '.gitignore').text == '# Café\r\nmanual/\r\n'
            write(root, '.gitignore', ''); git.mount(); kitchen.plan().apply(); git.unmount(); kitchen.plan().apply()
            assert new File(root, '.gitignore').exists() && !new File(root, '.gitignore').text
        }
        check('Edited and deleted managed regions reject updates and unmount') { root ->
            def kitchen = new Kitchen(root); def git = git(kitchen); kitchen.plan().apply()
            write(root, '.gitignore', new File(root, '.gitignore').text.replace('node_modules/', 'manual-change/'))
            def original = snapshot(root)
            rejects('Edited managed region') { kitchen.plan() }; git.unmount()
            rejects('Edited managed region') { kitchen.plan() }
            assert snapshot(root) == original
        }
        check('Malformed, duplicate and nested ownership markers reject') { root ->
            ['# cook:begin x\n', '# cook:begin x\n# cook:end y\n',
             '# cook:begin x\n# cook:begin y\n# cook:end y\n# cook:end x\n',
             '# cook:begin x\n# cook:end x\n# cook:begin x\n# cook:end x\n'].each { text ->
                write(root, '.gitignore', text); def kitchen = new Kitchen(root); git(kitchen)
                rejects('marker') { kitchen.plan() }
                assert new File(root, '.gitignore').text == text
            }
        }
        check('Ejection keeps native content, strips markers and forgets ownership') { root ->
            write(root, '.gitignore', 'manual/\n')
            def kitchen = new Kitchen(root); git(kitchen); kitchen.plan().apply(); kitchen.eject().apply()
            assert new File(root, '.gitignore').text == 'manual/\nnode_modules/\n'
            assert new JsonSlurper().parse(new File(root, '.cook/state.json')).files.isEmpty()
        }
    }
}
