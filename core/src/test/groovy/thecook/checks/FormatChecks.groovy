package thecook.checks

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import thecook.composition.Kitchen
import static thecook.checks.ModelChecks.*

class FormatChecks {
    static def field(Kitchen kitchen, String file, String format, List path, Closure value, Map options = [:]) {
        def component = kitchen.ingredient('owner')
        component.output(file, [format: format]) { key(path, options, value) }; component.mount(); component
    }
    static void run() {
        check('JSON changes only an adopted nested value and restores its original bytes') { root ->
            def original = '{\n "name" : "fixture", "devDependencies" : { "@types/node": "^20.0.0", "other" : "keep" }\n}\n'
            write(root, 'package.json', original)
            def kitchen = new Kitchen(root); def node = field(kitchen, 'package.json', 'json', ['devDependencies', '@types/node'], { '^22.0.0' }, [adopt: true])
            kitchen.plan().apply(); assert new File(root, 'package.json').text == original.replace('^20.0.0', '^22.0.0')
            node.unmount(); kitchen.plan().apply(); assert new File(root, 'package.json').text == original
        }
        check('JSON creates missing parents and preserves manual siblings on removal') { root ->
            write(root, 'package.json', '{ "name": "fixture" }\n')
            def kitchen = new Kitchen(root); def node = field(kitchen, 'package.json', 'json', ['devDependencies', '@types/node'], { '^22.0.0' })
            kitchen.plan().apply()
            def contents = new JsonSlurper().parse(new File(root, 'package.json')); contents.devDependencies.other = 'manual'
            write(root, 'package.json', JsonOutput.prettyPrint(JsonOutput.toJson(contents)) + '\n')
            node.unmount(); kitchen.plan().apply()
            assert new JsonSlurper().parse(new File(root, 'package.json')) == [name: 'fixture', devDependencies: [other: 'manual']]
        }
        check('JSON removes only the empty parents that Cook created') { root ->
            write(root, 'package.json', '{"name":"fixture"}')
            def kitchen = new Kitchen(root); def node = field(kitchen, 'package.json', 'json', ['devDependencies', '@types/node'], { '^22.0.0' })
            kitchen.plan().apply(); node.unmount(); kitchen.plan().apply()
            assert new JsonSlurper().parse(new File(root, 'package.json')) == [name: 'fixture']
        }
        check('JSON nulls, arrays, escaped strings and slash-containing keys are distinct') { root ->
            write(root, 'package.json', '{}'); def kitchen = new Kitchen(root); def settings = kitchen.ingredient('settings')
            settings.output('package.json', [format: 'json']) { key(['a/b']) { null }; key(['a', 'b']) { [true, false, 'quote"'] } }
            settings.mount(); kitchen.plan().apply()
            assert new JsonSlurper().parse(new File(root, 'package.json')) == ['a/b': null, a: [b: [true, false, 'quote"']]]
        }
        check('JSON collisions, duplicate keys and overlapping managed paths reject') { root ->
            write(root, 'package.json', '{"packageManager":"npm"}')
            def kitchen = new Kitchen(root); field(kitchen, 'package.json', 'json', ['packageManager'], { 'yarn' })
            rejects('explicit adopt required') { kitchen.plan() }
            write(root, 'package.json', '{"x":1,"x":2}'); rejects('Duplicate JSON key') { kitchen.plan() }
            write(root, 'package.json', '{}'); def overlap = new Kitchen(root); def owner = overlap.ingredient('owner')
            owner.output('package.json', [format: 'json']) { key(['a']) { [:] }; key(['a', 'b']) { 1 } }; owner.mount()
            rejects('Overlapping keys') { overlap.plan() }
        }
        check('Optional keys stay absent and manually changed owned keys reject') { root ->
            write(root, 'package.json', '{"name":"fixture"}')
            def kitchen = new Kitchen(root); field(kitchen, 'package.json', 'json', ['devDependencies', '@types/node'], { '^22.0.0' }, [adopt: true, whenPresent: true])
            kitchen.plan().apply(); assert new JsonSlurper().parse(new File(root, 'package.json')) == [name: 'fixture']
            write(root, 'package.json', '{"devDependencies":{"@types/node":"^20.0.0"}}'); kitchen.plan().apply()
            write(root, 'package.json', '{"devDependencies":{"@types/node":"^24.0.0"}}')
            rejects('Edited managed region') { kitchen.plan() }
        }
        check('TOML updates a pin without touching table comments or other keys') { root ->
            def original = '# Team\n[tools]\nnode = "20" # pin\npython = "3.13"\n\n[env]\nAPP_MODE = "dev"\n'
            write(root, 'mise.toml', original)
            def kitchen = new Kitchen(root); def mise = field(kitchen, 'mise.toml', 'toml', ['tools', 'node'], { '22' }, [adopt: true])
            kitchen.plan().apply(); assert new File(root, 'mise.toml').text == original.replace('node = "20"', 'node = "22"')
            mise.unmount(); kitchen.plan().apply(); assert new File(root, 'mise.toml').text == original
        }
        check('TOML creates inline venv configuration and removes its created tables') { root ->
            def original = '# Team\n[env]\nAPP_MODE = "dev"\n'; write(root, 'mise.toml', original)
            def kitchen = new Kitchen(root); def mise = kitchen.ingredient('mise')
            mise.output('mise.toml', [format: 'toml']) { key(['tools', 'python']) { '3.13' }; key(['env', '_', 'python', 'venv']) { [path: '.venv', create: true] } }
            mise.mount(); kitchen.plan().apply(); assert new File(root, 'mise.toml').text.contains('create = true')
            mise.unmount(); kitchen.plan().apply(); assert new File(root, 'mise.toml').text == original
        }
        check('TOML edits existing dotted keys without inventing another table') { root ->
            write(root, 'mise.toml', '[env]\n_.python.venv = { path = ".venv", create = false } # keep\n')
            def kitchen = new Kitchen(root); field(kitchen, 'mise.toml', 'toml', ['env', '_', 'python', 'venv'], { [path: '.venv', create: true] }, [adopt: true])
            kitchen.plan().apply()
            assert new File(root, 'mise.toml').text.contains('} # keep')
            assert !new File(root, 'mise.toml').text.contains('[env._.python]')
        }
    }
}
