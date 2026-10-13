package thecook.checks

import org.junit.Test
import java.nio.file.Files
import thecook.composition.ComponentId
import thecook.filesystem.TargetPath
import thecook.filesystem.Repository
import thecook.reconciliation.Reconciler
import thecook.rendering.RenderTree
import thecook.model.*

class BatchIdentityTest {
  @Test void batchAndContributorCanShareAName() {
    def repository = Files.createTempDirectory('cook-batch-identity-').toFile()
    try {
      Contributor contributor = new Contributor() {
            ComponentId getId() {
              ComponentId.of('git')
            }
            List<OutputSpec> outputs() {
              [new BlockOutput(id, TargetPath.of('.gitignore'), 'ignore', { ['/scratch/'] } as Provider)]
            }
          }
      def plan = new Reconciler().plan(new Repository(repository), RenderTree.from(new Batch('git').include(contributor)))
      assert !new File(repository, '.gitignore').exists()
      plan.apply()
      assert new File(repository, '.gitignore').text.contains('/scratch/')
    } finally {
      repository.deleteDir()
    }
  }
}
