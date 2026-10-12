package thecook.checks

import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import thecook.composition.Kitchen
import thecook.reconciliation.ChangePlan
import static thecook.checks.ModelChecks.*

class TransactionChecks {
    static void run() {
        check('Whole-file templates set executable mode and disappear on unmount') { root ->
            def kitchen = new Kitchen(root); def task = kitchen.ingredient('task')
            task.output('.mise/tasks/verify', [owns: true, mode: '0755']) { text { '#!/bin/sh\nexit 0\n' } }
            task.mount(); kitchen.plan().apply(); assert new File(root, '.mise/tasks/verify').canExecute()
            task.unmount(); kitchen.plan().apply(); assert !new File(root, '.mise/tasks/verify').exists()
        }
        check('Whole-file adoption restores both original content and permissions') { root ->
            write(root, 'Dockerfile', 'FROM original\n')
            Files.setPosixFilePermissions(new File(root, 'Dockerfile').toPath(), PosixFilePermissions.fromString('rw-r-----'))
            def kitchen = new Kitchen(root); def docker = kitchen.ingredient('docker')
            def output = docker.output('Dockerfile', [owns: true]) { text { 'FROM scratch\n' } }; docker.mount()
            rejects('explicit adopt required') { kitchen.plan() }
            output.declaration.adopt = true; kitchen.plan().apply(); docker.unmount(); kitchen.plan().apply()
            assert new File(root, 'Dockerfile').text == 'FROM original\n'
            assert PosixFilePermissions.toString(Files.getPosixFilePermissions(new File(root, 'Dockerfile').toPath())) == 'rw-r-----'
        }
        check('Whole-file manual edits and owner transfers reject') { root ->
            def kitchen = new Kitchen(root); def docker = kitchen.ingredient('docker')
            docker.output('Dockerfile', [owns: true]) { text { 'FROM scratch\n' } }; docker.mount(); kitchen.plan().apply()
            write(root, 'Dockerfile', 'FROM manual\n'); rejects('Edited managed whole file') { kitchen.plan() }
            write(root, 'Dockerfile', 'FROM scratch\n'); docker.unmount()
            def other = kitchen.ingredient('other'); other.output('Dockerfile', [owns: true]) { text { 'FROM other\n' } }; other.mount()
            rejects('Output identity changed') { kitchen.plan() }
        }
        check('Saved plans bind to the target and detect file drift before all writes') { root ->
            def kitchen = new Kitchen(root); git(kitchen)
            def docker = kitchen.ingredient('docker'); docker.output('.dockerignore') { block('ignore') { 'node_modules/' } }; docker.mount()
            def plan = kitchen.plan(); def saved = new File(root.parentFile, root.name + '.plan.json')
            try {
                plan.save(saved); write(root, '.gitignore', 'edited after preview\n'); def before = snapshot(root)
                rejects('Changed since preview') { ChangePlan.load(kitchen.repository, saved).apply() }
                assert snapshot(root) == before
                rejects('another repository') { ChangePlan.load(new Kitchen(new File(root, 'other')).repository, saved) }
            } finally { saved.delete() }
        }
        check('State drift and permission drift invalidate a reviewed plan') { root ->
            def kitchen = new Kitchen(root); git(kitchen); kitchen.plan().apply()
            def plan = kitchen.plan(); new File(root, '.cook/state.json').append(' ')
            rejects('Changed since preview: Cook state') { plan.apply() }
            plan = kitchen.plan()
            Files.setPosixFilePermissions(new File(root, '.gitignore').toPath(), PosixFilePermissions.fromString('rw-r-----'))
            rejects('Changed since preview') { plan.apply() }
        }
        check('Symlink targets, directories and invalid UTF-8 fail without following links') { root ->
            def outside = Files.createTempDirectory('cook-outside-').toFile()
            try {
                write(outside, 'secret', 'keep'); def path = new File(root, '.gitignore').toPath()
                Files.createSymbolicLink(path, new File(outside, 'secret').toPath())
                def kitchen = new Kitchen(root); git(kitchen); rejects('Symlink target') { kitchen.plan() }
                Files.delete(path); Files.createDirectory(path); rejects('Not a regular file') { kitchen.plan() }
                Files.delete(path); Files.write(path, [(byte) 0xff] as byte[]); rejects('UTF-8') { kitchen.plan() }
                assert new File(outside, 'secret').text == 'keep'
            } finally { outside.deleteDir() }
        }
        check('Unsupported state versions fail before generating files') { root ->
            write(root, '.cook/state.json', '{"version":999,"files":{}}')
            def kitchen = new Kitchen(root); git(kitchen); rejects('Unsupported or malformed') { kitchen.plan() }
            assert !new File(root, '.gitignore').exists()
        }
    }
}
