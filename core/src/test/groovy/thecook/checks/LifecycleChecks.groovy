package thecook.checks

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import thecook.composition.Kitchen
import thecook.filesystem.AtomicFiles
import thecook.filesystem.Transaction
import static thecook.checks.ModelChecks.*

class LifecycleChecks {
    static void run() {
        check('An empty kitchen is a filesystem no-op') { root ->
            new Kitchen(root).plan().apply(); assert root.listFiles().length == 0
        }
        check('Created JSON files disappear on unmount while manual root keys survive') { root ->
            def kitchen = new Kitchen(root)
            def owner = FormatChecks.field(kitchen, 'settings.json', 'json', ['x'], { 1 })
            kitchen.plan().apply(); owner.unmount(); kitchen.plan().apply()
            assert !new File(root, 'settings.json').exists()
        }
        check('Created parents remain owned when one of several contributions is removed') { root ->
            write(root, 'package.json', '{}'); def kitchen = new Kitchen(root); def owner = kitchen.ingredient('owner')
            def output = owner.output('package.json', [format: 'json']) { key(['a', 'b']) { 1 }; key(['a', 'c']) { 2 } }
            owner.mount(); kitchen.plan().apply()
            output.declaration.regions.remove('a/b'); kitchen.plan().apply()
            owner.unmount(); kitchen.plan().apply()
            assert new JsonSlurper().parse(new File(root, 'package.json')) == [:]
        }
        check('YAML named leaves preserve unrelated comments and restore adoption') { root ->
            def original = '# Team\nservices:\n  api:\n    image: node:20 # pin\n    environment:\n      CUSTOM: keep\n'
            write(root, 'compose.yaml', original)
            def kitchen = new Kitchen(root)
            def owner = FormatChecks.field(kitchen, 'compose.yaml', 'yaml', ['services', 'api', 'image'], { 'node:22' }, [adopt: true])
            kitchen.plan().apply()
            assert new File(root, 'compose.yaml').text == original.replace('node:20', '"node:22"')
            owner.unmount(); kitchen.plan().apply(); assert new File(root, 'compose.yaml').text == original
        }
        check('YAML adds and removes a Compose service without disturbing existing services') { root ->
            def original = '# Team\nservices:\n  api:\n    image: node:22\n'
            write(root, 'compose.yaml', original)
            def kitchen = new Kitchen(root)
            def owner = FormatChecks.field(kitchen, 'compose.yaml', 'yaml', ['services', 'localstack'], { [image: 'localstack/localstack:4.0', ports: ['4566:4566']] })
            kitchen.plan().apply(); assert new File(root, 'compose.yaml').text.contains('localstack/localstack:4.0')
            owner.unmount(); kitchen.plan().apply(); assert new File(root, 'compose.yaml').text == original
        }
        check('YAML anchors, merges and duplicate keys fail explicitly') { root ->
            ['x: &base 1\ny: *base\n', 'x: 1\nx: 2\n', 'x:\n  <<: {y: 1}\n'].each { contents ->
                write(root, 'compose.yaml', contents); def kitchen = new Kitchen(root)
                FormatChecks.field(kitchen, 'compose.yaml', 'yaml', ['x'], { 2 }, [adopt: true])
                rejects('YAML') { kitchen.plan() }
            }
        }
        check('A reviewed plan stays frozen after ingredient parameters change') { root ->
            def kitchen = new Kitchen(root); def node = kitchen.ingredient('node'); node.version = '20'
            node.output('mise.toml', [format: 'toml']) { key(['tools', 'node']) { node.version } }; node.mount()
            def plan = kitchen.plan(); node.version = '22'; plan.apply()
            assert new File(root, 'mise.toml').text.contains('"20"')
            kitchen.plan().apply(); assert new File(root, 'mise.toml').text.contains('"22"')
        }
        check('Interrupted transactions refuse further rendering until recovery') { root ->
            def kitchen = new Kitchen(root); git(kitchen); def proposal = kitchen.plan().proposal
            write(root, '.cook/transaction.json', JsonOutput.toJson(proposal))
            rejects('Incomplete transaction') { kitchen.plan() }
            new Transaction(kitchen.repository).recover()
            assert !new File(root, '.cook/transaction.json').exists() && !new File(root, '.gitignore').exists()
        }
        check('Recovery rolls back partial writes and refuses subsequent manual edits') { root ->
            def kitchen = new Kitchen(root); git(kitchen); def proposal = kitchen.plan().proposal
            write(root, '.cook/transaction.json', JsonOutput.toJson(proposal))
            write(root, '.gitignore', proposal.changes[0].content)
            new Transaction(kitchen.repository).recover(); assert !new File(root, '.gitignore').exists()
            write(root, '.cook/transaction.json', JsonOutput.toJson(proposal)); write(root, '.gitignore', 'manual after crash\n')
            rejects('Changed after interrupted transaction') { new Transaction(kitchen.repository).recover() }
            assert new File(root, '.gitignore').text == 'manual after crash\n'
        }
        check('An apply failure rolls back earlier writes and leaves no completed state') { root ->
            def kitchen = new Kitchen(root); git(kitchen)
            def docker = kitchen.ingredient('docker'); docker.output('.dockerignore') { block('ignore') { 'node_modules/' } }; docker.mount()
            def plan = kitchen.plan()
            rejects('Injected filesystem failure') { new Transaction(kitchen.repository, new FailingFiles()).apply(plan.proposal) }
            assert !new File(root, '.gitignore').exists() && !new File(root, '.dockerignore').exists()
            assert !new File(root, '.cook/state.json').exists()
        }
        check('A held repository lock rejects a concurrent apply') { root ->
            def kitchen = new Kitchen(root); git(kitchen); def plan = kitchen.plan()
            def lockFile = kitchen.repository.metadata('lock'); lockFile.parentFile.mkdirs()
            FileChannel.open(lockFile.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE).withCloseable { channel ->
                def lock = channel.lock()
                try { rejects('Another Cook apply') { plan.apply() } } finally { lock.release() }
            }
        }
        check('Symlink parents, including metadata directories, are refused') { root ->
            def other = Files.createTempDirectory('cook-external-').toFile()
            try {
                Files.createSymbolicLink(new File(root, 'nested').toPath(), other.toPath())
                def kitchen = new Kitchen(root); git(kitchen, 'nested/ignore')
                rejects('Symlink target') { kitchen.plan() }
                Files.createSymbolicLink(new File(root, '.cook').toPath(), other.toPath())
                rejects('Symlink target') { kitchen.plan() }
                assert other.listFiles().length == 0
            } finally { other.deleteDir() }
        }
    }
    static class FailingFiles extends AtomicFiles {
        boolean failed = false
        @Override void write(File file, String content, String mode) {
            if (file.name == '.gitignore' && !failed) { failed = true; throw new IOException('Injected filesystem failure') }
            super.write(file, content, mode)
        }
    }
}
