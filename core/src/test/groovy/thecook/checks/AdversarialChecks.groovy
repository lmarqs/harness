package thecook.checks

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import org.tomlj.Toml
import org.yaml.snakeyaml.Yaml
import thecook.composition.Kitchen
import thecook.filesystem.Transaction
import thecook.reconciliation.ChangePlan
import static thecook.checks.ModelChecks.*

// Regression checks for boundary conditions in the public model, using real files.
class AdversarialChecks {
    static void run() {
        markers()
        dynamicViews()
        structuredDocuments()
        filesystemBoundaries()
        recovery()
    }

    private static void dynamicViews() {
        check('A dynamic whole-file view emits native content and keeps whole-file ownership') { root ->
            def kitchen = new Kitchen(root); def owner = kitchen.ingredient('owner')
            owner.output('Dockerfile', [owns: true]) { view { text { 'FROM scratch\n' } } }
            owner.mount(); kitchen.plan().apply()
            assert new File(root, 'Dockerfile').text == 'FROM scratch\n'
            assert kitchen.plan().changes.empty
            owner.unmount(); kitchen.plan().apply(); assert !new File(root, 'Dockerfile').exists()
        }
        check('Dynamic component views remove obsolete outputs without restoring deleted adopted files') { root ->
            write(root, 'Dockerfile', 'FROM original\n')
            def kitchen = new Kitchen(root); def owner = kitchen.ingredient('owner'); owner.enabled = true
            owner.view { if (enabled) output('Dockerfile', [owns: true, adopt: true]) { text { 'FROM scratch\n' } } }
            owner.mount(); kitchen.plan().apply(); new File(root, 'Dockerfile').delete(); owner.enabled = false
            unchangedAfterReject('Edited managed whole file', root) { kitchen.plan() }
        }
        check('Dynamic output views restore adopted keys when their condition becomes false') { root ->
            def original = '{"managed":"original","manual":"keep"}'; write(root, 'settings.json', original)
            def kitchen = new Kitchen(root); def owner = kitchen.ingredient('owner'); owner.enabled = true
            owner.output('settings.json', [format: 'json']) {
                view { if (owner.enabled) key(['managed'], [adopt: true]) { 'rendered' } }
            }
            owner.mount(); kitchen.plan().apply(); owner.enabled = false; kitchen.plan().apply()
            assert new File(root, 'settings.json').text == original
            assert kitchen.plan().changes.empty
        }
        check('A view supplier failure leaves both target and ownership state untouched') { root ->
            def kitchen = new Kitchen(root); def owner = kitchen.ingredient('owner')
            owner.output('first') { block('value') { 'one' } }
            owner.view { output('second') { block('value') { throw new IllegalStateException('Supplier failure') } } }
            owner.mount(); unchangedAfterReject('Supplier failure', root) { kitchen.plan() }
        }
    }

    private static void markers() {
        check('A marker identity that starts with another identity stays independent') { root ->
            def original = '# cook:begin git/ignored\nmanual/\n# cook:end git/ignored\n'
            write(root, '.gitignore', original)
            def kitchen = new Kitchen(root); def owner = git(kitchen)
            kitchen.plan().apply()
            assert new File(root, '.gitignore').text.startsWith(original)
            owner.unmount(); kitchen.plan().apply()
            assert new File(root, '.gitignore').text == original
        }
        check('Empty marker identities reject before writing') { root ->
            write(root, '.gitignore', '# cook:begin \nmanual/\n# cook:end \n')
            def kitchen = new Kitchen(root); git(kitchen)
            unchangedAfterReject('marker', root) { kitchen.plan() }
        }
        check('Marker text embedded in a manual line rejects before writing') { root ->
            write(root, '.gitignore', 'prefix # cook:begin git/ignore\nmanual/\n# cook:end git/ignore\n')
            def kitchen = new Kitchen(root); git(kitchen)
            unchangedAfterReject('marker', root) { kitchen.plan() }
        }
        check('Newlines in block identities reject at declaration or planning') { root ->
            unchangedAfterReject('region', root) {
                def kitchen = new Kitchen(root); def owner = kitchen.ingredient('owner')
                owner.output('ignore') { block('bad\nidentity') { 'manual/' } }
                owner.mount(); kitchen.plan()
            }
        }
        check('Deleting a managed block rejects unmount without restoring stale content') { root ->
            write(root, '.gitignore', 'manual/\n')
            def kitchen = new Kitchen(root); def owner = git(kitchen); kitchen.plan().apply()
            write(root, '.gitignore', 'manual/\n'); owner.unmount()
            unchangedAfterReject('Edited managed region', root) { kitchen.plan() }
        }
        check('Two block identities with a common prefix can coexist and reconcile') { root ->
            def kitchen = new Kitchen(root); def owner = kitchen.ingredient('owner')
            owner.output('ignore') { block('a') { 'one/' }; block('ab') { 'two/' } }
            owner.mount(); kitchen.plan().apply()
            assert kitchen.plan().changes.empty
            owner.unmount(); kitchen.plan().apply(); assert !new File(root, 'ignore').exists()
        }
    }

    private static void structuredDocuments() {
        ['json', 'toml', 'yaml'].each { format ->
            check("Preexisting empty ${format} files restore as existing zero-byte files") { root ->
                def path = "settings.${format}"; write(root, path, '')
                def kitchen = new Kitchen(root)
                def owner = FormatChecks.field(kitchen, path, format, ['a', 'b'], { 'value' })
                kitchen.plan().apply(); owner.unmount(); kitchen.plan().apply()
                assert new File(root, path).exists() && new File(root, path).length() == 0
            }
        }
        check('JSON nested arrays and repeated keys in separate objects remain independent') { root ->
            def original = '{"a":{"x":1},"b":{"x":[[1,2],[3]]},"text":"escaped \\\" quote"}\r\n'
            write(root, 'settings.json', original)
            def kitchen = new Kitchen(root)
            def owner = FormatChecks.field(kitchen, 'settings.json', 'json', ['a', 'x'], { 2 }, [adopt: true])
            kitchen.plan().apply(); assert new File(root, 'settings.json').text == original.replace('"x":1', '"x":2')
            owner.unmount(); kitchen.plan().apply(); assert new File(root, 'settings.json').text == original
        }
        ['{"x":"\\q"}', '{"x":"\\u12XZ"}', '{"x":1,}', '{"x":[1,]}', '{"x":NaN}', '{"x":01}'].eachWithIndex { invalid, index ->
            check("Invalid JSON syntax ${index + 1} rejects without touching the original") { root ->
                write(root, 'settings.json', invalid); def kitchen = new Kitchen(root)
                FormatChecks.field(kitchen, 'settings.json', 'json', ['managed'], { 1 })
                unchangedAfterReject('JSON', root) { kitchen.plan() }
            }
        }
        ['null', '1', '[]'].each { parent ->
            check("JSON scalar or array parent ${parent} cannot be silently replaced") { root ->
                write(root, 'settings.json', '{"a":' + parent + '}')
                def kitchen = new Kitchen(root); FormatChecks.field(kitchen, 'settings.json', 'json', ['a', 'b'], { 1 })
                unchangedAfterReject('JSON object', root) { kitchen.plan() }
            }
        }
        check('Deleting an adopted JSON leaf rejects unmount without recreating its original') { root ->
            write(root, 'settings.json', '{"x":"original"}')
            def kitchen = new Kitchen(root)
            def owner = FormatChecks.field(kitchen, 'settings.json', 'json', ['x'], { 'managed' }, [adopt: true])
            kitchen.plan().apply(); write(root, 'settings.json', '{}'); owner.unmount()
            unchangedAfterReject('Edited managed region', root) { kitchen.plan() }
        }
        check('JSON adoption keeps the initial original across several value changes') { root ->
            def original = '{"x":"original", "manual":true}'; write(root, 'settings.json', original)
            def kitchen = new Kitchen(root); def value = 'first'
            def owner = FormatChecks.field(kitchen, 'settings.json', 'json', ['x'], { value }, [adopt: true])
            kitchen.plan().apply(); value = 'second'; kitchen.plan().apply(); owner.unmount(); kitchen.plan().apply()
            assert new File(root, 'settings.json').text == original
        }
        check('TOML quoted equals keys address their actual value') { root ->
            def original = '[tools]\n"node=release" = "20" # pin\n'; write(root, 'mise.toml', original)
            def kitchen = new Kitchen(root)
            def owner = FormatChecks.field(kitchen, 'mise.toml', 'toml', ['tools', 'node=release'], { '22' }, [adopt: true])
            kitchen.plan().apply(); assert new File(root, 'mise.toml').text == original.replace('"20"', '"22"')
            owner.unmount(); kitchen.plan().apply(); assert new File(root, 'mise.toml').text == original
        }
        check('TOML array comments containing closing brackets do not truncate the value') { root ->
            def original = 'values = [\n  "one", # ] is only a comment\n  "two",\n]\nmanual = "keep"\n'
            write(root, 'settings.toml', original)
            def kitchen = new Kitchen(root)
            def owner = FormatChecks.field(kitchen, 'settings.toml', 'toml', ['values'], { ['changed'] }, [adopt: true])
            kitchen.plan().apply(); assert Toml.parse(new File(root, 'settings.toml').text).getString('manual') == 'keep'
            owner.unmount(); kitchen.plan().apply(); assert new File(root, 'settings.toml').text == original
        }
        check('TOML multiline strings preserve their following comment and restore exactly') { root ->
            def original = 'message = """line one\nline two""" # note\nmanual = true\n'
            write(root, 'settings.toml', original)
            def kitchen = new Kitchen(root)
            def owner = FormatChecks.field(kitchen, 'settings.toml', 'toml', ['message'], { 'replacement' }, [adopt: true])
            kitchen.plan().apply(); assert new File(root, 'settings.toml').text.endsWith(' # note\nmanual = true\n')
            owner.unmount(); kitchen.plan().apply(); assert new File(root, 'settings.toml').text == original
        }
        check('TOML duplicate tables reject before editing') { root ->
            write(root, 'settings.toml', '[tools]\nnode="20"\n[tools]\npython="3.13"\n')
            def kitchen = new Kitchen(root); FormatChecks.field(kitchen, 'settings.toml', 'toml', ['tools', 'node'], { '22' }, [adopt: true])
            unchangedAfterReject('TOML', root) { kitchen.plan() }
        }
        check('TOML preexisting comments survive cleanup of a created table') { root ->
            write(root, 'settings.toml', '# Team\n')
            def kitchen = new Kitchen(root)
            def owner = FormatChecks.field(kitchen, 'settings.toml', 'toml', ['tools', 'node'], { '22' })
            kitchen.plan().apply(); new File(root, 'settings.toml').append('# Manual table comment\n')
            owner.unmount(); kitchen.plan().apply()
            assert new File(root, 'settings.toml').text.contains('# Manual table comment')
            assert !Toml.parse(new File(root, 'settings.toml').text).hasErrors()
        }
        ['null', '1', '[]'].each { parent ->
            check("YAML scalar or sequence parent ${parent} rejects without replacing it") { root ->
                write(root, 'settings.yaml', 'a: ' + parent + '\n')
                def kitchen = new Kitchen(root); FormatChecks.field(kitchen, 'settings.yaml', 'yaml', ['a', 'b'], { 1 })
                unchangedAfterReject('YAML mapping', root) { kitchen.plan() }
            }
        }
        check('YAML CRLF and a supplementary Unicode character preserve value offsets') { root ->
            def original = '# 😀 Team\r\nservices:\r\n  api:\r\n    image: node:20 # pin\r\n'
            write(root, 'settings.yaml', original)
            def kitchen = new Kitchen(root)
            def owner = FormatChecks.field(kitchen, 'settings.yaml', 'yaml', ['services', 'api', 'image'], { 'node:22' }, [adopt: true])
            kitchen.plan().apply(); assert new File(root, 'settings.yaml').text == original.replace('node:20', '"node:22"')
            owner.unmount(); kitchen.plan().apply(); assert new File(root, 'settings.yaml').text == original
        }
        check('YAML created parents retain manually added comments on unmount') { root ->
            write(root, 'settings.yaml', 'manual: keep\n')
            def kitchen = new Kitchen(root)
            def owner = FormatChecks.field(kitchen, 'settings.yaml', 'yaml', ['a', 'b'], { 1 })
            kitchen.plan().apply()
            write(root, 'settings.yaml', new File(root, 'settings.yaml').text.replace('"a":', '# Parent note\n"a":'))
            owner.unmount(); kitchen.plan().apply()
            assert new File(root, 'settings.yaml').text.contains('# Parent note')
            assert new Yaml().load(new File(root, 'settings.yaml').text) == [manual: 'keep']
        }
    }

    private static void filesystemBoundaries() {
        check('A repository opened through a symlink refuses to follow that root') { root ->
            def actual = new File(root, 'actual'); actual.mkdirs()
            def link = new File(root, 'link'); Files.createSymbolicLink(link.toPath(), actual.toPath())
            unchangedAfterReject('Symlink', root) { def kitchen = new Kitchen(link); git(kitchen); kitchen.plan() }
            assert actual.listFiles().length == 0
        }
        check('A regular file used as an output parent rejects during preview') { root ->
            write(root, 'parent', 'manual')
            def kitchen = new Kitchen(root); git(kitchen, 'parent/ignore')
            unchangedAfterReject('regular file', root) { kitchen.plan() }
        }
        ['.git/config', '.git/HEAD', '.git/hooks/pre-commit'].each { path ->
            check("Repository control path ${path} is reserved") { root ->
                def kitchen = new Kitchen(root); git(kitchen, path)
                unchangedAfterReject('Unsafe output path', root) { kitchen.plan() }
            }
        }
        check('Format changes reject while a file remains owned') { root ->
            def kitchen = new Kitchen(root); def owner = FormatChecks.field(kitchen, 'settings', 'json', ['x'], { 1 })
            kitchen.plan().apply(); owner.definition.outputs[0].declaration.kind = 'yaml'
            unchangedAfterReject('Output identity changed', root) { kitchen.plan() }
        }
    }

    private static void recovery() {
        check('Recovery refuses permission changes made after an interrupted write') { root ->
            def kitchen = new Kitchen(root); git(kitchen); def proposal = kitchen.plan().proposal
            write(root, '.cook/transaction.json', JsonOutput.toJson(proposal))
            write(root, '.gitignore', proposal.changes[0].content)
            Files.setPosixFilePermissions(new File(root, '.gitignore').toPath(), PosixFilePermissions.fromString('rw-------'))
            unchangedAfterReject('Changed after interrupted transaction', root) { new Transaction(kitchen.repository).recover() }
            assert new File(root, '.gitignore').exists()
        }
        check('Recovery validates all changed files before rolling back any of them') { root ->
            def kitchen = new Kitchen(root); git(kitchen)
            def other = kitchen.ingredient('other'); other.output('zignore') { block('ignore') { 'generated/' } }; other.mount()
            def proposal = kitchen.plan().proposal
            write(root, '.cook/transaction.json', JsonOutput.toJson(proposal))
            proposal.changes.each { write(root, it.path, it.content) }
            write(root, 'zignore', 'manual after interruption\n')
            unchangedAfterReject('Changed after interrupted transaction', root) { new Transaction(kitchen.repository).recover() }
        }
        check('Recovery refuses a journal whose before content contradicts its hash') { root ->
            def kitchen = new Kitchen(root); git(kitchen); def proposal = kitchen.plan().proposal
            proposal.changes[0].before.content = 'forged original\n'
            write(root, '.cook/transaction.json', JsonOutput.toJson(proposal))
            write(root, '.gitignore', proposal.changes[0].content)
            unchangedAfterReject('Invalid transaction journal', root) { new Transaction(kitchen.repository).recover() }
        }
        check('Loading a plan refuses a change omitted from its observed snapshots') { root ->
            def kitchen = new Kitchen(root); git(kitchen); def proposal = kitchen.plan().proposal
            proposal.observed.clear()
            def saved = new File(root, 'reviewed.json'); write(root, 'reviewed.json', JsonOutput.toJson(proposal))
            write(root, '.gitignore', 'manual after review\n')
            unchangedAfterReject('Invalid', root) { ChangePlan.load(kitchen.repository, saved).apply() }
        }
        check('Malformed state entries reject explicitly before planning writes') { root ->
            write(root, '.cook/state.json', '{"version":1,"files":{"settings.json":{"owner":"x","kind":"json"}}}')
            unchangedAfterReject('malformed Cook state', root) { new Kitchen(root).plan() }
        }
    }

    private static void unchangedAfterReject(String message, File root, Closure body) {
        def before = snapshot(root)
        rejects(message, body)
        assert snapshot(root) == before
    }
}
