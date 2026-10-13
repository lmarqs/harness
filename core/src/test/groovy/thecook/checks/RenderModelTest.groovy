package thecook.checks

import org.junit.Test
import java.nio.file.Files
import thecook.composition.ComponentId
import thecook.composition.Kitchen
import thecook.filesystem.Repository
import thecook.filesystem.TargetPath
import thecook.model.*
import thecook.reconciliation.Reconciler
import thecook.rendering.RenderTree

class RenderModelTest {
  @Test void allDynamicChecksRunBeforeAnyOutputProvider() {
    def root = Files.createTempDirectory('cook-check-order-').toFile()
    try {
      def kitchen = new Kitchen(root)
      def first = kitchen.ingredient('first'); first.mount()
      def second = kitchen.ingredient('second'); second.mount()
      def events = []
      first.validate { events.add('first check') }
      second.validate { events.add('second check') }
      first.output('result', [owns: true]) {
        text {
          events.add('provider'); 'result'
        }
      }
      kitchen.plan()
      assert events == ['first check', 'second check', 'provider']
    } finally {
      root.deleteDir()
    }
  }

  @Test void allTypedDeclarationsResolveBeforeAnyOutputProvider() {
    def root = Files.createTempDirectory('cook-declaration-order-').toFile()
    try {
      def events = []
      def firstId = ComponentId.of('first')
      def first = [getId: { firstId }, outputs: {
          events.add('first declaration')
          [new FileOutput(firstId, TargetPath.of('result'), { events.add('provider'); 'result' })]
        }] as Contributor
      def second = [getId: { ComponentId.of('second') }, outputs: {
          events.add('second declaration'); []
        }] as Contributor
      new Reconciler().plan(new Repository(root), RenderTree.from(new Batch('ordered').include(first).include(second)))
      assert events == ['first declaration', 'second declaration', 'provider']
    } finally {
      root.deleteDir()
    }
  }

  @Test void nullDynamicSymlinkTargetRejectsBeforeWriting() {
    def root = Files.createTempDirectory('cook-null-link-').toFile()
    try {
      def kitchen = new Kitchen(root)
      def writer = kitchen.ingredient('links'); writer.mount()
      writer.output('link', [owns: true]) {
        symlink {
          null
        }
      }
      try {
        kitchen.plan(); assert false: 'Null symlink target was accepted'
      }
      catch (IllegalArgumentException expected) {
        assert expected.message
      }
      assert root.listFiles().length == 0
    } finally {
      root.deleteDir()
    }
  }

  @Test void typedAndDynamicDeclarationsProduceTheSameOwnedFiles() {
    def root = Files.createTempDirectory('cook-render-model-').toFile()
    try {
      def owner = ComponentId.of('writer')
      int evaluations = 0
      def contributor = [getId: { owner }, outputs: {
          [
            new BlockOutput(owner, TargetPath.of('ignore'), 'cache', { evaluations++; ['cache/'] }),
            new StructuredOutput(owner, TargetPath.of('config.json'), DocumentFormat.JSON, [new KeyContribution(['tools', 'node'], { '22' })]),
            new FileOutput(owner, TargetPath.of('run'), { '#!/bin/sh\n' }, true),
            new SymbolicLinkOutput(owner, TargetPath.of('linked'), 'run')
          ]
        }] as Contributor
      def batch = new Batch('typed').include(contributor)
      assert evaluations == 0
      def typed = RenderTree.from(batch)
      assert evaluations == 1
      new Reconciler().plan(new Repository(root), typed).apply()
      def kitchen = new Kitchen(root)
      def writer = kitchen.ingredient('writer')
      writer.output('ignore') {
        block('cache') {
          ['cache/']
        }
      }
      writer.output('config.json', [format: 'json']) {
        key(['tools', 'node']) {
          '22'
        }
      }
      writer.output('run', [owns: true, mode: '0755']) {
        text {
          '#!/bin/sh\n'
        }
      }
      writer.output('linked', [owns: true]) {
        symlink {
          'run'
        }
      }
      writer.mount()
      assert kitchen.plan().changes.empty
      writer.unmount(); kitchen.plan().apply()
      assert !new File(root, 'ignore').exists()
      assert !new File(root, 'config.json').exists()
      assert !new File(root, 'run').exists()
      assert !Files.exists(new File(root, 'linked').toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)
    } finally {
      root.deleteDir()
    }
  }
}
