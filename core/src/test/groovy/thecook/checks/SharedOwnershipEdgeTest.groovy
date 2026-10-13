package thecook.checks

import groovy.json.JsonSlurper
import org.junit.Test
import org.tomlj.Toml
import org.yaml.snakeyaml.Yaml
import java.nio.file.Files
import thecook.composition.Kitchen
import static thecook.checks.ModelChecks.snapshot
import static thecook.checks.ModelChecks.write

class SharedOwnershipEdgeTest {
    private static void workspace(Closure body) {
        def root = Files.createTempDirectory('cook-shared-ownership-').toFile()
        try { body(root) } finally { root.deleteDir() }
    }

    private static def field(Kitchen kitchen, String owner, String path, String format,
                             List keyPath, Closure value, Map options = [:]) {
        def component = kitchen.ingredient(owner)
        component.output(path, [format: format]) { key(keyPath, options, value) }
        component.mount()
        component
    }

    private static def block(Kitchen kitchen, String owner, String path, Closure value) {
        def component = kitchen.ingredient(owner)
        component.output(path) { block('shared', value) }
        component.mount()
        component
    }

    private static void rejectsWithoutWrites(File root, Closure action) {
        def before = snapshot(root)
        try {
            action()
            assert false: 'Expected an ownership conflict or drift rejection'
        } catch (IllegalArgumentException | IllegalStateException expected) {
            assert expected.message
        }
        assert snapshot(root) == before: 'Rejected planning must preserve all targets and ownership state'
    }

    @Test void yamlOwnersShareCreatedParentAndRestoreOriginalAfterLastRemoval() {
        sharedParent('yaml', '# Team settings\nmanual: keep # user comment\n', false)
    }

    @Test void tomlOwnersShareCreatedParentAndRestoreOriginalAfterLastRemoval() {
        sharedParent('toml', '# Team settings\nmanual = "keep" # user comment\n', true)
    }

    private static void sharedParent(String format, String original, boolean removeSecondFirst) {
        workspace { root ->
            String path = "settings.${format}"
            write(root, path, original)
            def kitchen = new Kitchen(root)
            def node = field(kitchen, 'node', path, format, ['tools', 'node'], { '22' })
            def python = field(kitchen, 'python', path, format, ['tools', 'python'], { '3.13' })
            def before = snapshot(root)
            def plan = kitchen.plan()
            assert snapshot(root) == before
            plan.apply()
            assert readTools(root, path, format) == [node: '22', python: '3.13']
            assert new File(root, path).text.contains('# user comment')
            assert kitchen.plan().changes.empty

            def removed = removeSecondFirst ? python : node
            def remaining = removeSecondFirst ? node : python
            removed.unmount()
            kitchen.plan().apply()
            assert readTools(root, path, format) == (removeSecondFirst ? [node: '22'] : [python: '3.13'])
            assert new File(root, path).text.contains('# user comment')
            assert kitchen.plan().changes.empty

            remaining.unmount()
            kitchen.plan().apply()
            assert new File(root, path).text == original
            assert kitchen.plan().changes.empty
        }
    }

    private static Map readTools(File root, String path, String format) {
        String text = new File(root, path).text
        if (format == 'yaml') return new Yaml().load(text).tools
        def parsed = Toml.parse(text)
        assert !parsed.hasErrors()
        parsed.getTable('tools').toMap()
    }

    @Test void keyOwnershipTransferRequiresAnAppliedUnmountEvenWithAdoption() {
        workspace { root ->
            String original = '{"managed":"original", "manual":"keep"}\n'
            write(root, 'settings.json', original)
            def kitchen = new Kitchen(root)
            def previous = field(kitchen, 'previous', 'settings.json', 'json', ['managed'], { 'first' }, [adopt: true])
            kitchen.plan().apply()
            previous.unmount()
            def next = field(kitchen, 'next', 'settings.json', 'json', ['managed'], { 'second' }, [adopt: true])
            rejectsWithoutWrites(root) { kitchen.plan() }

            next.unmount()
            kitchen.plan().apply()
            assert new File(root, 'settings.json').text == original
            next.mount()
            kitchen.plan().apply()
            assert new JsonSlurper().parse(new File(root, 'settings.json')).managed == 'second'
            next.unmount()
            kitchen.plan().apply()
            assert new File(root, 'settings.json').text == original
        }
    }

    @Test void driftInRemovedContributorRejectsChangesToEveryTargetAndState() {
        workspace { root ->
            write(root, 'ignore', '# Manual\n')
            def kitchen = new Kitchen(root)
            def removed = block(kitchen, 'first', 'ignore', { 'first-generated/' })
            String remainingValue = 'second-generated/'
            block(kitchen, 'second', 'ignore', { remainingValue })
            String separateValue = 'initial\n'
            def separate = kitchen.ingredient('separate')
            separate.output('separate.txt', [owns: true]) { text { separateValue } }
            separate.mount()
            kitchen.plan().apply()
            write(root, 'ignore', new File(root, 'ignore').text.replace('first-generated/', 'manual-edit/'))
            removed.unmount()
            remainingValue = 'updated-second/'
            separateValue = 'updated\n'
            rejectsWithoutWrites(root) { kitchen.plan() }
        }
    }

    @Test void caseVariantsOfSharedFilePathsStillCollideBeforeWriting() {
        workspace { root ->
            write(root, 'Settings.json', '{"manual":true}\n')
            def kitchen = new Kitchen(root)
            field(kitchen, 'first', 'Settings.json', 'json', ['first'], { 1 })
            field(kitchen, 'second', 'settings.json', 'json', ['second'], { 2 })
            rejectsWithoutWrites(root) { kitchen.plan() }
        }
    }

    @Test void ejectingBothBlockOwnersKeepsTheirContentAndRemovesEveryMarker() {
        workspace { root ->
            write(root, 'ignore', '# Manual\nmanual/\n')
            def kitchen = new Kitchen(root)
            block(kitchen, 'first', 'ignore', { 'first-generated/' })
            block(kitchen, 'second', 'ignore', { 'second-generated/' })
            kitchen.plan().apply()
            write(root, 'ignore', new File(root, 'ignore').text + '# Later\nlater/\n')
            kitchen.eject().apply()
            String text = new File(root, 'ignore').text
            assert text.startsWith('# Manual\nmanual/\n')
            assert text.endsWith('# Later\nlater/\n')
            assert text.readLines().findAll { it == 'first-generated/' }.size() == 1
            assert text.readLines().findAll { it == 'second-generated/' }.size() == 1
            assert !text.contains('cook:begin') && !text.contains('cook:end')
            assert new JsonSlurper().parse(new File(root, '.cook/state.json')).files.isEmpty()
        }
    }

    @Test void wholeFileAndPartialWriterRejectBeforeWriting() {
        exclusiveCollision(false)
    }

    @Test void symlinkAndPartialWriterRejectBeforeWriting() {
        exclusiveCollision(true)
    }

    private static void exclusiveCollision(boolean asLink) {
        workspace { root ->
            write(root, 'shared', 'manual content\n')
            write(root, 'source', 'source must survive\n')
            def kitchen = new Kitchen(root)
            def whole = kitchen.ingredient('whole')
            whole.output('shared', [owns: true, adopt: true]) {
                if (asLink) symlink { 'source' }
                else text { 'replacement\n' }
            }
            whole.mount()
            block(kitchen, 'partial', 'shared', { 'generated/' })
            block(kitchen, 'independent', 'first-output', { 'also pending/' })
            rejectsWithoutWrites(root) { kitchen.plan() }
        }
    }

    @Test void overlappingStructuredPathsFromDifferentOwnersRejectAtomically() {
        workspace { root ->
            write(root, 'settings.json', '{"manual":true}\n')
            def kitchen = new Kitchen(root)
            field(kitchen, 'parent', 'settings.json', 'json', ['tools'], { [node: '22'] })
            field(kitchen, 'child', 'settings.json', 'json', ['tools', 'python'], { '3.13' })
            block(kitchen, 'independent', 'first-output', { 'pending/' })
            rejectsWithoutWrites(root) { kitchen.plan() }
        }
    }
}
