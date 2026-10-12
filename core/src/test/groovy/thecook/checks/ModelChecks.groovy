package thecook.checks

import groovy.json.JsonOutput
import java.nio.file.Files
import thecook.composition.Kitchen

// Executable behavioral checks; real components, repositories and edits, no mocks.
class ModelChecks {
    static final List results = []
    static void check(String name, Closure body) {
        def root = Files.createTempDirectory('cook-model-').toFile()
        try { body(root); results.add([name: name, passed: true]); println "PASS ${name}" }
        catch (Throwable error) { results.add([name: name, passed: false, error: error.toString()]); println "FAIL ${name}: ${error}" }
        finally { root.deleteDir() }
    }
    static void rejects(String message, Closure body) {
        try { body(); throw new AssertionError("Expected rejection: ${message}") }
        catch (Exception error) { assert error.message?.contains(message): error.toString() }
    }
    static void write(File root, String path, String text) {
        def file = new File(root, path); file.parentFile.mkdirs(); file.setText(text, 'UTF-8')
    }
    static Map snapshot(File root) {
        def result = [:]
        root.eachFileRecurse { if (it.isFile() && it.name != 'lock') result[it.absolutePath] = it.bytes.encodeHex().toString() }
        result
    }
    static def git(Kitchen kitchen, String path = '.gitignore', Closure value = { ['node_modules/'] }) {
        def component = kitchen.ingredient('git')
        component.output(path) { block('ignore', value) }
        component.mount()
        component
    }
    static void main(String[] args) {
        def root = Files.createTempDirectory('cook-model-').toFile()
        try {
            def kitchen = new Kitchen(root)
            def npm = kitchen.ingredient('npm')
            npm.export('files.ephemeral') { ['node_modules/', 'npm-debug.log*'] }
            def git = kitchen.ingredient('git')
            git.output('.gitignore') { block('ignore') { npm.files.ephemeral.value } }
            assert !new File(root, '.gitignore').exists(): 'Declaring components must not write files'
            git.mount()
            def plan = kitchen.plan()
            assert !new File(root, '.gitignore').exists(): 'Planning must not write files'
            plan.apply()
            assert new File(root, '.gitignore').text.contains('node_modules/')
            assert !new File(root, '.gitignore').text.contains('package-lock.json')
            assert kitchen.plan().changes.empty: 'Second render must be idempotent'
            println 'PASS exports connect to mounted outputs; planning is read-only; rendering is idempotent'
        } finally { root.deleteDir() }
        CompositionChecks.run()
        ReconciliationChecks.run()
        FormatChecks.run()
        TransactionChecks.run()
        LifecycleChecks.run()
        AdversarialChecks.run()
        ReviewChecks.run()
        CookbookChecks.run()
        def report = [passed: 1 + results.count { it.passed }, failed: results.count { !it.passed }, checks: results]
        def target = new File(args[0]); target.parentFile.mkdirs()
        target.setText(JsonOutput.prettyPrint(JsonOutput.toJson(report)) + '\n', 'UTF-8')
        if (report.failed) throw new AssertionError("${report.failed} model checks failed; see ${target}")
        println "${report.passed} model checks passed"
    }
}
