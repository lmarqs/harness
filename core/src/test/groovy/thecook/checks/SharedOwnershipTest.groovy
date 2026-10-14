package thecook.checks

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.junit.Test
import java.nio.file.Files
import thecook.composition.Kitchen
import thecook.filesystem.Repository
import thecook.reconciliation.StateSchema

class SharedOwnershipTest {
  @Test void legacyInsertedSeparatorSurvivesRecookingBeforeWithdrawal() {
    repository { root ->
      def file = new File(root, 'ignore')
      file.text = 'manual/\n# cook:begin git/ignore\ncache/\n# cook:end git/ignore\n'
      def state = new File(root, '.cook/state.json'); state.parentFile.mkdirs()
      state.text = JsonOutput.toJson([version: 1, files: [ignore: [owner: 'git', kind: 'blocks',
            created: false, originallyEmpty: false, metadata: [:], regions: [ignore: [rendered: 'cache/\n',
                original: null, definition: [separator: true]]]]]])
      def kitchen = new Kitchen(root)
      def git = kitchen.ingredient('git')
      git.output('ignore') {
        block('ignore') {
          'cache/'
        }
      }; git.mount()
      kitchen.plan().apply()
      git.unmount(); kitchen.plan().apply()
      assert file.text == 'manual/'
    }
  }
  private void repository(Closure test) {
    def root = Files.createTempDirectory('cook-shared-').toFile()
    try {
      test(root)
    } finally {
      root.deleteDir()
    }
  }

  @Test void separateIngredientsShareBlocksAndRemoveIndependently() {
    repository { root ->
      def file = new File(root, '.gitignore'); file.text = '# user\n.env\n'
      def kitchen = new Kitchen(root)
      def git = kitchen.ingredient('git'); git.output('.gitignore') {
        block('ignore') {
          ['node_modules/']
        }
      }; git.mount()
      def personal = kitchen.ingredient('personal'); personal.output('.gitignore') {
        block('ignore') {
          ['.ralph-tui/session.json']
        }
      }; personal.mount()
      kitchen.plan().apply()
      assert file.text.contains('# cook:begin git/ignore')
      assert file.text.contains('# cook:begin personal/ignore')
      assert kitchen.plan().changes.empty
      personal.unmount(); kitchen.plan().apply()
      assert file.text.contains('node_modules/') && !file.text.contains('personal/ignore')
      git.unmount(); kitchen.plan().apply()
      assert file.text == '# user\n.env\n'
    }
  }

  @Test void jsonContributionsPreserveAdoptedOriginalAndSharedParents() {
    repository { root ->
      def file = new File(root, 'settings.json'); file.text = '{"permissions":{"mode":"original"},"model":"user"}\n'
      def kitchen = new Kitchen(root)
      def first = kitchen.ingredient('first'); first.output('settings.json', [format:'json']) {
        key(['permissions', 'mode'], [adopt:true]) {
          'managed'
        }
      }; first.mount()
      def second = kitchen.ingredient('second'); second.output('settings.json', [format:'json']) {
        key(['permissions', 'allow']) {
          ['build']
        }
      }; second.mount()
      kitchen.plan().apply()
      first.unmount(); kitchen.plan().apply()
      assert new JsonSlurper().parse(file).permissions == [mode:'original', allow:['build']]
      second.unmount(); kitchen.plan().apply()
      assert file.text == '{"permissions":{"mode":"original"},"model":"user"}\n'
    }
  }

  @Test void collidingStructuredKeysRejectEvenWithEqualValues() {
    repository { root ->
      def kitchen = new Kitchen(root)
      ['first', 'second'].each { name ->
        def writer = kitchen.ingredient(name)
        writer.output('settings.json', [format:'json']) {
          key(['permissions', 'mode']) {
            'same'
          }
        }; writer.mount()
      }
      try {
        kitchen.plan(); assert false
      } catch (IllegalArgumentException expected) {
        assert expected.message.contains('settings.json')
        assert expected.message.contains('first') && expected.message.contains('second')
      }
      assert !new File(root,'settings.json').exists() && !new File(root,'.cook/state.json').exists()
    }
  }

  @Test void versionOneMigrationPreservesMarkersAndAdoptedValuesWithoutPreviewWrites() {
    repository { root ->
      def file = new File(root,'settings.json'); file.text = '{"mode":"managed"}\n'
      def state = new File(root,'.cook/state.json'); state.parentFile.mkdirs()
      state.text = JsonOutput.toJson([version:1, files:['settings.json':[owner:'first',kind:'json',created:false,
            originallyEmpty:false,metadata:[createdParents:[]], regions:[mode:[rendered:'"managed"', original:'"original"', definition:[path:['mode'],adopt:true,whenPresent:false]]]]]])
      def original = state.text
      def kitchen = new Kitchen(root)
      def writer = kitchen.ingredient('first'); writer.output('settings.json',[format:'json']) {
        key(['mode']) {
          'managed'
        }
      }; writer.mount()
      def plan = kitchen.plan()
      assert plan.changes.empty && state.text == original
      plan.apply()
      assert new JsonSlurper().parse(state).version == 3
      writer.unmount(); kitchen.plan().apply()
      assert new JsonSlurper().parse(file).mode == 'original'
    }
  }

  @Test void migrationDoesNotRewriteExistingBlockMarkers() {
    repository { root ->
      def file = new File(root,'.gitignore'); file.text = '# user\n# cook:begin git/ignore\ncache/\n# cook:end git/ignore\n'
      def state = new File(root,'.cook/state.json'); state.parentFile.mkdirs()
      state.text = JsonOutput.toJson([version:1, files:['.gitignore':[owner:'git',kind:'blocks',created:false,
            originallyEmpty:false,metadata:[:],regions:[ignore:[rendered:'cache/\n',original:null,definition:[:]]]]]])
      def original = file.text
      def kitchen = new Kitchen(root)
      def writer = kitchen.ingredient('git'); writer.output('.gitignore') {
        block('ignore') {
          ['cache/']
        }
      }; writer.mount()
      kitchen.plan().apply()
      assert file.text == original
      assert new JsonSlurper().parse(state).files['.gitignore'].regions['git/ignore'].owner == 'git'
    }
  }

  @Test void sharedBlocksRestoreAnOriginalWithoutAFinalNewline() {
    repository { root ->
      def file = new File(root,'ignore'); file.text = 'manual/'
      def kitchen = new Kitchen(root)
      def first = kitchen.ingredient('first'); first.output('ignore') {
        block('ignore') {
          'first/'
        }
      }; first.mount()
      def second = kitchen.ingredient('second'); second.output('ignore') {
        block('ignore') {
          'second/'
        }
      }; second.mount()
      kitchen.plan().apply()
      first.unmount(); kitchen.plan().apply()
      assert file.text.startsWith('manual/\n# cook:begin second/ignore\n')
      second.unmount(); kitchen.plan().apply()
      assert file.text == 'manual/'
    }
  }
}
