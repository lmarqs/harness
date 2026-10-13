package thecook.checks

import org.junit.Test
import thecook.cookbook.Cookbook
import thecook.composition.Kitchen
import java.nio.file.Files

class CompositionFactoriesTest {
  final Cookbook book = new Cookbook(new File('../cookbook'))

  private void repository(Closure body) {
    def root = Files.createTempDirectory('cook-composition-').toFile()
    try {
      body(root)
    } finally {
      root.deleteDir()
    }
  }

  @Test void nodeDependencyPreservesOtherManifestFieldsAndEmitsInstallTask() {
    repository { root ->
      new File(root, 'package.json').text = '{"name":"existing","scripts":{"test":"custom"}}\n'
      def kitchen = new Kitchen(root)
      book.recipe('add-node-dependency', kitchen, [package: 'example', version: '^1.2.0', manager: 'pnpm', manifest: 'package.json']).mount()
      def plan = kitchen.plan()
      assert new File(root, 'package.json').text.contains('existing')
      plan.apply()
      def manifest = new groovy.json.JsonSlurper().parse(new File(root, 'package.json'))
      assert manifest.name == 'existing'
      assert manifest.scripts.test == 'custom'
      assert manifest.dependencies.example == '^1.2.0'
      assert new File(root, 'mise.toml').text.contains('pnpm install')
    }
  }

  @Test void goDependencyPreservesModuleAndUsesValidGoComments() {
    repository { root ->
      new File(root, 'go.mod').text = 'module example.org/service\n\ngo 1.24\n'
      def kitchen = new Kitchen(root)
      book.recipe('add-go-dependency', kitchen, [module: 'example.org/library', version: 'v1.0.0', manifest: 'go.mod', adopt: true]).mount()
      kitchen.plan().apply()
      assert new File(root, 'go.mod').text == 'module example.org/service\n\ngo 1.24\nrequire example.org/library v1.0.0\n'
      assert !new File(root, 'go.mod').text.contains('# cook:')
    }
  }

  @Test void migrationsRejectUnapprovedOrUnexpectedSourcesWithoutWriting() {
    repository { root ->
      new File(root, 'pin').text = 'old\n'
      [
        [files: [[path: 'pin', expected: 'old\n', content: 'new\n']]],
        [adopt: true, files: [[path: 'pin', expected: 'wrong\n', content: 'new\n']]]
      ].each { options ->
        try {
          book.recipe('refresh-ci-cache', new Kitchen(root), options)
          assert false: 'Invalid migration accepted'
        } catch (IllegalArgumentException expected) {
          assert new File(root, 'pin').text == 'old\n'
          assert !new File(root, '.cook').exists()
        }
      }
    }
  }

  @Test void dockerStageChangeLeavesOtherStagesUnchanged() {
    repository { root ->
      new File(root, 'Dockerfile').text = 'FROM node:20 AS builder\nRUN npm install\nFROM nginx:stable AS runtime\n'
      def kitchen = new Kitchen(root)
      book.recipe('update-docker-base', kitchen, [adopt: true, path: 'Dockerfile', stage: 'builder', expectedImage: 'node:20', image: 'node:22']).mount()
      kitchen.plan().apply()
      assert new File(root, 'Dockerfile').text == 'FROM node:22 AS builder\nRUN npm install\nFROM nginx:stable AS runtime\n'
    }
  }
  @Test void selectedPrdContainsOnlyRequestedStoryAndDoesNotRewriteSource() {
    repository { root ->
      def source = '{"userStories":[{"id":"US-001"},{"id":"US-002"}]}\n'
      new File(root, 'prd.json').text = source
      def kitchen = new Kitchen(root)
      book.recipe('implement-prd-story', kitchen, [prdPath: 'prd.json', storyId: 'US-002']).mount()
      kitchen.plan().apply()
      assert new File(root, 'prd.json').text == source
      def selected = new groovy.json.JsonSlurper().parse(new File(root, '.ralph-tui/selected-prd.json'))
      assert selected.userStories*.id == ['US-002']
    }
  }
}
